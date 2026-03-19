package com.daitj.easycontrolfork.app.client.tools;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import com.daitj.easycontrolfork.app.BuildConfig;
import com.daitj.easycontrolfork.app.R;
import com.daitj.easycontrolfork.app.adb.Adb;
import com.daitj.easycontrolfork.app.buffer.BufferStream;
import com.daitj.easycontrolfork.app.client.decode.DecodecTools;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.entity.MyInterface;
import com.daitj.easycontrolfork.app.helper.PublicTools;

/**
 * 客户端连接流
 * 支持多种连接模式：
 * - ADB模式：通过ADB协议连接（USB/网络ADB）
 * - 直连模式：直接TCP连接到设备Server端口
 * - P2P模式：通过中继服务器打洞建立直连
 * - 中转模式：所有流量通过中继服务器转发
 */
public class ClientStream {
  private boolean isClose = false;
  private boolean connectDirect = false;
  private Adb adb;
  private Socket mainSocket;
  private Socket videoSocket;
  private OutputStream mainOutputStream;
  private DataInputStream mainDataInputStream;
  private DataInputStream videoDataInputStream;
  private BufferStream mainBufferStream;
  private BufferStream videoBufferStream;
  private BufferStream shell;
  private Thread connectThread = null;
  private static final String serverName = "/data/local/tmp/easycontrolfork_server_" + BuildConfig.VERSION_CODE + ".jar";
  private static final boolean supportH265 = DecodecTools.isSupportH265();
  private static final boolean supportOpus = DecodecTools.isSupportOpus();

  private static final int timeoutDelay = 1000 * 15;
  
  // 中继客户端
  private RelayClient relayClient;

  // 统计信息覆盖层
  private final StatsOverlay statsOverlay = new StatsOverlay();

  public StatsOverlay getStatsOverlay() {
    return statsOverlay;
  }

  public ClientStream(Device device, MyInterface.MyFunctionBoolean handle) {
    Thread timeOutThread = new Thread(() -> {
      try {
        Thread.sleep(timeoutDelay);
        PublicTools.logToast("stream", AppData.applicationContext.getString(R.string.toast_timeout), true);
        handle.run(false);
        if (connectThread != null) connectThread.interrupt();
      } catch (InterruptedException ignored) {
      }
    });
    connectThread = new Thread(() -> {
      try {
        // 根据连接模式选择连接方式
        switch (device.connectMode) {
          case Device.CONNECT_MODE_DIRECT:
            connectDirectMode(device);
            break;
          case Device.CONNECT_MODE_P2P:
            connectP2PMode(device);
            break;
          case Device.CONNECT_MODE_RELAY:
            connectRelayMode(device);
            break;
          case Device.CONNECT_MODE_ADB:
          default:
            connectAdbMode(device);
            break;
        }
        handle.run(true);
      } catch (Exception e) {
        PublicTools.logToast("stream", e.toString(), true);
        handle.run(false);
      } finally {
        timeOutThread.interrupt();
      }
    });
    connectThread.start();
    timeOutThread.start();
  }
  
  /**
   * ADB 模式连接（默认）
   */
  private void connectAdbMode(Device device) throws Exception {
    adb = AdbTools.connectADB(device);
    startServer(device);
    connectServer(device);
  }
  
  /**
   * 直连模式
   * 直接TCP连接到设备Server端口，不需要ADB
   */
  private void connectDirectMode(Device device) throws Exception {
    // 直连模式需要先确保Server已启动
    // 可以通过之前ADB连接过，或设备端设置为开机自启
    connectDirectTcp(device, device.address, device.serverPort);
  }
  
  /**
   * P2P 模式
   * 通过中继服务器打洞建立直连
   */
  private void connectP2PMode(Device device) throws Exception {
    relayClient = new RelayClient(device);
    relayClient.connectToRelay();
    
    if (relayClient.requestP2P()) {
      // P2P 打洞成功
      String host = relayClient.getP2PHost();
      int port = relayClient.getP2PPort();
      connectDirectTcp(device, host, port);
    } else {
      // P2P 失败，回退到中转模式
      PublicTools.logToast("stream", "P2P打洞失败，回退到中转模式", true);
      int relayPort = relayClient.requestRelay();
      Socket relaySocket = relayClient.createRelaySocket(relayPort);
      setupRelayConnection(relaySocket);
    }
  }
  
  /**
   * 中转模式
   * 所有流量通过中继服务器转发
   */
  private void connectRelayMode(Device device) throws Exception {
    relayClient = new RelayClient(device);
    relayClient.connectToRelay();
    int relayPort = relayClient.requestRelay();
    Socket relaySocket = relayClient.createRelaySocket(relayPort);
    setupRelayConnection(relaySocket);
  }
  
  /**
   * 建立直接TCP连接
   */
  private void connectDirectTcp(Device device, String host, int port) throws Exception {
    Thread.sleep(50);
    int reTry = 40;
    int reTryTime = timeoutDelay / reTry;
    
    long startTime = System.currentTimeMillis();
    InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
    
    for (int i = 0; i < reTry; i++) {
      try {
        mainSocket = new Socket();
        mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
        videoSocket = new Socket();
        videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
        
        mainOutputStream = mainSocket.getOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = true;
        return;
      } catch (Exception ignored) {
        if (mainSocket != null) mainSocket.close();
        if (videoSocket != null) videoSocket.close();
        if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) i = reTry;
        else Thread.sleep(reTryTime);
      }
    }
    throw new Exception("直连模式连接失败");
  }
  
  /**
   * 设置中转连接
   */
  private void setupRelayConnection(Socket socket) throws IOException {
    mainSocket = socket;
    mainOutputStream = mainSocket.getOutputStream();
    mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
    videoDataInputStream = mainDataInputStream;  // 中转模式共用一个连接
    connectDirect = true;
  }

  private void startServer(Device device) throws Exception {
    if (BuildConfig.ENABLE_DEBUG_FEATURE || !adb.runAdbCmd("ls /data/local/tmp/easycontrolfork_*").contains(serverName)) {
      adb.runAdbCmd("rm /data/local/tmp/easycontrolfork_* ");
      adb.pushFile(AppData.applicationContext.getResources().openRawResource(R.raw.easycontrolfork_server), serverName, null);
    }
    shell = adb.getShell();
    shell.write(ByteBuffer.wrap(("app_process -Djava.class.path=" + serverName + " / com.daitj.easycontrolfork.server.Server"
      + " serverPort=" + device.serverPort
      + " listenClip=" + (device.listenClip ? 1 : 0)
      + " isAudio=" + (device.isAudio ? 1 : 0)
      + " maxSize=" + device.maxSize
      + " maxFps=" + device.maxFps
      + " maxVideoBit=" + device.maxVideoBit
      + " keepAwake=" + (device.keepWakeOnRunning ? 1 : 0)
      + " supportH265=" + ((device.useH265 && supportH265) ? 1 : 0)
      + " supportOpus=" + (supportOpus ? 1 : 0)
      + " startApp=" + device.startApp + " \n").getBytes()));
  }

  private void connectServer(Device device) throws Exception {
    Thread.sleep(50);
    int reTry = 40;
    int reTryTime = timeoutDelay / reTry;
    if (!device.isLinkDevice()) {
      long startTime = System.currentTimeMillis();
      boolean mainConn = false;
      InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);
      for (int i = 0; i < reTry; i++) {
        try {
          if (!mainConn) {
            mainSocket = new Socket();
            mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
            mainConn = true;
          }
          videoSocket = new Socket();
          videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
          mainOutputStream = mainSocket.getOutputStream();
          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
          connectDirect = true;
          return;
        } catch (Exception ignored) {
          if (mainSocket != null) mainSocket.close();
          if (videoSocket != null) videoSocket.close();
          if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) i = reTry;
          else Thread.sleep(reTryTime);
        }
      }
    }
    for (int i = 0; i < reTry; i++) {
      try {
        if (mainBufferStream == null) mainBufferStream = adb.tcpForward(device.serverPort);
        if (videoBufferStream == null) videoBufferStream = adb.tcpForward(device.serverPort);
        return;
      } catch (Exception ignored) {
        Thread.sleep(reTryTime);
      }
    }
    throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
  }

  public String runShell(String cmd) throws Exception {
    return adb.runAdbCmd(cmd);
  }

  public byte readByteFromMain() throws IOException, InterruptedException {
    if (connectDirect) return mainDataInputStream.readByte();
    else return mainBufferStream.readByte();
  }

  public byte readByteFromVideo() throws IOException, InterruptedException {
    if (connectDirect) return videoDataInputStream.readByte();
    else return videoBufferStream.readByte();
  }

  public int readIntFromMain() throws IOException, InterruptedException {
    if (connectDirect) return mainDataInputStream.readInt();
    else return mainBufferStream.readInt();
  }

  public int readIntFromVideo() throws IOException, InterruptedException {
    if (connectDirect) return videoDataInputStream.readInt();
    else return videoBufferStream.readInt();
  }

  public ByteBuffer readByteArrayFromMain(int size) throws IOException, InterruptedException {
    if (connectDirect) {
      byte[] buffer = new byte[size];
      mainDataInputStream.readFully(buffer);
      return ByteBuffer.wrap(buffer);
    } else return mainBufferStream.readByteArray(size);
  }

  public ByteBuffer readByteArrayFromVideo(int size) throws IOException, InterruptedException {
    if (connectDirect) {
      byte[] buffer = new byte[size];
      videoDataInputStream.readFully(buffer);
      return ByteBuffer.wrap(buffer);
    }
    return videoBufferStream.readByteArray(size);
  }

  public ByteBuffer readFrameFromMain() throws Exception {
    if (!connectDirect) mainBufferStream.flush();
    return readByteArrayFromMain(readIntFromMain());
  }

  public ByteBuffer readFrameFromVideo() throws Exception {
    if (!connectDirect) videoBufferStream.flush();
    int size = readIntFromVideo();
    return readByteArrayFromVideo(size);
  }

  public void writeToMain(ByteBuffer byteBuffer) throws Exception {
    if (connectDirect) mainOutputStream.write(byteBuffer.array());
    else mainBufferStream.write(byteBuffer);
  }

  /**
   * 发送 keepAlive 并测量 RTT 延迟，结果上报给 StatsOverlay
   */
  public void writeToMainWithLatency(ByteBuffer byteBuffer) throws Exception {
    long start = System.currentTimeMillis();
    writeToMain(byteBuffer);
    long latency = System.currentTimeMillis() - start;
    statsOverlay.onLatency(latency);
  }

  public void close() {
    if (isClose) return;
    isClose = true;
    if (shell != null) PublicTools.logToast("server", new String(shell.readByteArrayBeforeClose().array()), false);
    // 关闭中继客户端
    if (relayClient != null) {
      relayClient.close();
    }
    if (connectDirect) {
      try {
        mainOutputStream.close();
        videoDataInputStream.close();
        mainDataInputStream.close();
        mainSocket.close();
        if (videoSocket != null && videoSocket != mainSocket) {
          videoSocket.close();
        }
      } catch (Exception ignored) {
      }
    } else {
      mainBufferStream.close();
      videoBufferStream.close();
    }
  }
  
  /**
   * 获取当前连接模式名称
   */
  public String getConnectModeName() {
    return Device.CONNECT_MODE_NAMES[connectDirect ? Device.CONNECT_MODE_DIRECT : Device.CONNECT_MODE_ADB];
  }
}
