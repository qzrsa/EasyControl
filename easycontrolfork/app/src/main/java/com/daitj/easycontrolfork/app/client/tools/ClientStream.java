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
        PublicTools.logToast("stream", "【1】开始连接ADB...", true);
        adb = AdbTools.connectADB(device);
        PublicTools.logToast("stream", "【2】ADB连接成功，推送服务端...", true);
        startServer(device);
        PublicTools.logToast("stream", "【3】服务端启动成功，开始TCP连接...", true);
        connectServer(device);
        PublicTools.logToast("stream", "【4】全部完成！", true);
        handle.run(true);
      } catch (Exception e) {
        PublicTools.logToast("stream", "【失败】" + e.toString(), true);
        handle.run(false);
      } finally {
        timeOutThread.interrupt();
      }
    });
    connectThread.start();
    timeOutThread.start();
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

  // ===== 安全的配置解析 =====

  private int getEffectiveConnMode(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        return AppData.setting.getDefaultConnMode();
      } catch (Exception ignored) {
      }
    }
    return device.connMode;
  }

  private String getEffectiveRelayHost(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String host = AppData.setting.getDefaultRelayHost();
        if (host != null && !host.isEmpty()) return host;
      } catch (Exception ignored) {
      }
    }
    return device.relayHost != null ? device.relayHost : "";
  }

  private int getEffectiveRelayPort(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        int port = AppData.setting.getDefaultRelayPort();
        if (port > 0) return port;
      } catch (Exception ignored) {
      }
    }
    return device.relayPort > 0 ? device.relayPort : 25167;
  }

  private String getEffectiveRelayKey(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String key = AppData.setting.getDefaultRelayKey();
        if (key != null) return key;
      } catch (Exception ignored) {
      }
    }
    return device.relayKey != null ? device.relayKey : "";
  }

  // ===== 连接主逻辑 =====

  private void connectServer(Device device) throws Exception {
    Thread.sleep(50);
    int reTry = 40;
    int reTryTime = timeoutDelay / reTry;

    int mode = getEffectiveConnMode(device);
    PublicTools.logToast("stream", "【连接】模式=" + mode
      + " 地址=" + device.address
      + " 服务器=" + getEffectiveRelayHost(device) + ":" + getEffectiveRelayPort(device)
      + " useGlobal=" + device.useGlobalRelay, true);

    if (mode == Device.CONN_DIRECT) {
      PublicTools.logToast("stream", "【连接】走直连模式", true);
      connectDirectOrAdb(device, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_AUTO) {
      PublicTools.logToast("stream", "【连接】走自动模式，先尝试直连", true);
      if (!device.isLinkDevice()) {
        try {
          connectDirectOnly(device);
          PublicTools.logToast("stream", "【连接】自动模式直连成功", true);
          return;
        } catch (Exception e) {
          PublicTools.logToast("stream", "【连接】直连失败切换服务器: " + e.getMessage(), true);
        }
      }
      connectRelay(getEffectiveRelayHost(device), getEffectiveRelayPort(device), device.uuid, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_RELAY) {
      PublicTools.logToast("stream", "【连接】走强制中转模式", true);
      connectRelay(getEffectiveRelayHost(device), getEffectiveRelayPort(device), device.uuid, reTry, reTryTime);
      return;
    }

    PublicTools.logToast("stream", "【连接】兜底走直连", true);
    connectDirectOrAdb(device, reTry, reTryTime);
  }

  // 原有直连 + ADB tcpForward
  private void connectDirectOrAdb(Device device, int reTry, int reTryTime) throws Exception {
    if (!device.isLinkDevice()) {
      PublicTools.logToast("stream", "【直连】目标=" + device.address + ":" + device.serverPort, true);
      long startTime = System.currentTimeMillis();
      boolean mainConn = false;
      InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);
      for (int i = 0; i < reTry; i++) {
        try {
          if (!mainConn) {
            mainSocket = new Socket();
            mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
            mainConn = true;
            PublicTools.logToast("stream", "【直连】mainSocket成功", true);
          }
          videoSocket = new Socket();
          videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
          mainOutputStream = mainSocket.getOutputStream();
          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
          connectDirect = true;
          PublicTools.logToast("stream", "【直连】完成", true);
          return;
        } catch (Exception e) {
          PublicTools.logToast("stream", "【直连】第" + (i + 1) + "次失败: " + e.getMessage(), true);
          if (mainSocket != null) mainSocket.close();
          if (videoSocket != null) videoSocket.close();
          if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) i = reTry;
          else Thread.sleep(reTryTime);
        }
      }
    }
    PublicTools.logToast("stream", "【直连】全部失败，尝试ADB tcpForward", true);
    for (int i = 0; i < reTry; i++) {
      try {
        if (mainBufferStream == null) mainBufferStream = adb.tcpForward(device.serverPort);
        if (videoBufferStream == null) videoBufferStream = adb.tcpForward(device.serverPort);
        PublicTools.logToast("stream", "【ADB】tcpForward成功", true);
        return;
      } catch (Exception e) {
        PublicTools.logToast("stream", "【ADB】第" + (i + 1) + "次失败: " + e.getMessage(), true);
        Thread.sleep(reTryTime);
      }
    }
    throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
  }

  // 仅直连，不走 ADB tcpForward
  private void connectDirectOnly(Device device) throws Exception {
    PublicTools.logToast("stream", "【直连Only】目标=" + device.address + ":" + device.serverPort, true);
    InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);
    mainSocket = new Socket();
    mainSocket.connect(inetSocketAddress, 5000);
    videoSocket = new Socket();
    videoSocket.connect(inetSocketAddress, 5000);
    mainOutputStream = mainSocket.getOutputStream();
    mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
    videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
    connectDirect = true;
  }

  // 纯 TCP 转发模式（配合 frp 使用）
  private void connectRelay(String relayHost, int relayPort, String uuid, int reTry, int reTryTime) throws Exception {
    if (relayHost == null || relayHost.isEmpty()) {
      throw new Exception("服务器地址未配置，请在设置中填写服务器地址");
    }
    PublicTools.logToast("stream", "【中转】开始连接 " + relayHost + ":" + relayPort, true);
    for (int i = 0; i < reTry; i++) {
      try {
        PublicTools.logToast("stream", "【中转】第" + (i + 1) + "次尝试", true);
        mainSocket = new Socket();
        mainSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
        PublicTools.logToast("stream", "【中转】mainSocket成功", true);

        videoSocket = new Socket();
        videoSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
        PublicTools.logToast("stream", "【中转】videoSocket成功", true);

        mainOutputStream = mainSocket.getOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = true;
        PublicTools.logToast("stream", "【中转】连接完成", true);
        return;
      } catch (Exception e) {
        PublicTools.logToast("stream", "【中转】第" + (i + 1) + "次失败: " + e.getMessage(), true);
        if (mainSocket != null) mainSocket.close();
        if (videoSocket != null) videoSocket.close();
        Thread.sleep(reTryTime);
      }
    }
    throw new Exception("服务器中转连接失败，请检查服务器地址和端口");
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
    if (connectDirect) {
      try {
        mainOutputStream.close();
        videoDataInputStream.close();
        mainDataInputStream.close();
        mainSocket.close();
        videoSocket.close();
      } catch (Exception ignored) {
      }
    } else {
      mainBufferStream.close();
      videoBufferStream.close();
    }
  }
}
