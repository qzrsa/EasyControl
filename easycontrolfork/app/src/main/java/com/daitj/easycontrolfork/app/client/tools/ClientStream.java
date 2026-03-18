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
        PublicTools.logToast("stream", "【超时】" + AppData.applicationContext.getString(R.string.toast_timeout), true);
        handle.run(false);
        if (connectThread != null) connectThread.interrupt();
      } catch (InterruptedException e) {
        PublicTools.logToast("stream", "【超时线程结束】" + e, true);
      }
    });

    connectThread = new Thread(() -> {
      try {
        PublicTools.logToast("stream", "【1】开始连接，device=" + device.name + " uuid=" + device.uuid, true);

        PublicTools.logToast("stream", "【2】开始 ADB 连接，address=" + device.address + " adbPort=" + device.adbPort, true);
        adb = AdbTools.connectADB(device);
        PublicTools.logToast("stream", "【3】ADB 连接成功", true);

        PublicTools.logToast("stream", "【4】开始启动服务端 jar，serverPort=" + device.serverPort, true);
        startServer(device);
        PublicTools.logToast("stream", "【5】服务端启动命令已发送", true);

        PublicTools.logToast("stream", "【6】开始连接数据流", true);
        connectServer(device);
        PublicTools.logToast("stream", "【7】数据流连接完成，connectDirect=" + connectDirect, true);

        handle.run(true);
      } catch (Exception e) {
        PublicTools.logToast("stream", "【失败异常】" + e.getClass().getSimpleName() + ": " + e.getMessage(), true);
        PublicTools.logToast("stream", "【失败详情】" + e.toString(), true);
        handle.run(false);
      } finally {
        timeOutThread.interrupt();
      }
    });

    connectThread.start();
    timeOutThread.start();
  }

  private void startServer(Device device) throws Exception {
    PublicTools.logToast("stream", "【startServer】检查服务端文件是否存在", true);
    String lsResult = adb.runAdbCmd("ls /data/local/tmp/easycontrolfork_*");
    PublicTools.logToast("stream", "【startServer】ls结果=" + lsResult, true);

    if (BuildConfig.ENABLE_DEBUG_FEATURE || !lsResult.contains(serverName)) {
      PublicTools.logToast("stream", "【startServer】准备重新推送服务端文件", true);
      adb.runAdbCmd("rm /data/local/tmp/easycontrolfork_* ");
      adb.pushFile(AppData.applicationContext.getResources().openRawResource(R.raw.easycontrolfork_server), serverName, null);
      PublicTools.logToast("stream", "【startServer】服务端文件推送完成", true);
    } else {
      PublicTools.logToast("stream", "【startServer】服务端文件已存在，跳过推送", true);
    }

    shell = adb.getShell();
    PublicTools.logToast("stream", "【startServer】shell 已获取，开始发送启动命令", true);

    String cmd = "app_process -Djava.class.path=" + serverName + " / com.daitj.easycontrolfork.server.Server"
      + " serverPort=" + device.serverPort
      + " listenClip=" + (device.listenClip ? 1 : 0)
      + " isAudio=" + (device.isAudio ? 1 : 0)
      + " maxSize=" + device.maxSize
      + " maxFps=" + device.maxFps
      + " maxVideoBit=" + device.maxVideoBit
      + " keepAwake=" + (device.keepWakeOnRunning ? 1 : 0)
      + " supportH265=" + ((device.useH265 && supportH265) ? 1 : 0)
      + " supportOpus=" + (supportOpus ? 1 : 0)
      + " startApp=" + device.startApp + " \n";

    PublicTools.logToast("stream", "【startServer命令】" + cmd, true);
    shell.write(ByteBuffer.wrap(cmd.getBytes()));
    PublicTools.logToast("stream", "【startServer】启动命令发送完成", true);
  }

  private int getEffectiveConnMode(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        int mode = AppData.setting.getDefaultConnMode();
        PublicTools.logToast("stream", "【配置】使用全局连接模式=" + mode, true);
        return mode;
      } catch (Exception e) {
        PublicTools.logToast("stream", "【配置】读取全局连接模式失败=" + e, true);
      }
    }
    PublicTools.logToast("stream", "【配置】使用设备连接模式=" + device.connMode, true);
    return device.connMode;
  }

  private String getEffectiveRelayHost(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String host = AppData.setting.getDefaultRelayHost();
        if (host != null && !host.isEmpty()) {
          PublicTools.logToast("stream", "【配置】使用全局 relayHost=" + host, true);
          return host;
        }
      } catch (Exception e) {
        PublicTools.logToast("stream", "【配置】读取全局 relayHost 失败=" + e, true);
      }
    }
    String host = device.relayHost != null ? device.relayHost : "";
    PublicTools.logToast("stream", "【配置】使用设备 relayHost=" + host, true);
    return host;
  }

  private int getEffectiveRelayPort(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        int port = AppData.setting.getDefaultRelayPort();
        if (port > 0) {
          PublicTools.logToast("stream", "【配置】使用全局 relayPort=" + port, true);
          return port;
        }
      } catch (Exception e) {
        PublicTools.logToast("stream", "【配置】读取全局 relayPort 失败=" + e, true);
      }
    }
    int port = device.relayPort > 0 ? device.relayPort : 25167;
    PublicTools.logToast("stream", "【配置】使用设备 relayPort=" + port, true);
    return port;
  }

  private String getEffectiveRelayKey(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String key = AppData.setting.getDefaultRelayKey();
        if (key != null) {
          PublicTools.logToast("stream", "【配置】使用全局 relayKey（长度）=" + key.length(), true);
          return key;
        }
      } catch (Exception e) {
        PublicTools.logToast("stream", "【配置】读取全局 relayKey 失败=" + e, true);
      }
    }
    String key = device.relayKey != null ? device.relayKey : "";
    PublicTools.logToast("stream", "【配置】使用设备 relayKey（长度）=" + key.length(), true);
    return key;
  }

  private void connectServer(Device device) throws Exception {
    Thread.sleep(50);
    int reTry = 40;
    int reTryTime = timeoutDelay / reTry;

    int mode = getEffectiveConnMode(device);
    String relayHost = getEffectiveRelayHost(device);
    int relayPort = getEffectiveRelayPort(device);
    String relayKey = getEffectiveRelayKey(device);

    PublicTools.logToast("stream",
      "【connectServer】mode=" + mode
        + " address=" + device.address
        + " serverPort=" + device.serverPort
        + " relayHost=" + relayHost
        + " relayPort=" + relayPort
        + " useGlobalRelay=" + device.useGlobalRelay
        + " isLinkDevice=" + device.isLinkDevice(),
      true);

    if (mode == Device.CONN_DIRECT) {
      PublicTools.logToast("stream", "【connectServer】进入直连模式", true);
      connectDirectOrAdb(device, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_AUTO) {
      PublicTools.logToast("stream", "【connectServer】进入自动模式", true);
      if (!device.isLinkDevice()) {
        try {
          PublicTools.logToast("stream", "【connectServer】自动模式先尝试直连", true);
          connectDirectOnly(device);
          PublicTools.logToast("stream", "【connectServer】自动模式直连成功", true);
          return;
        } catch (Exception e) {
          PublicTools.logToast("stream", "【connectServer】自动模式直连失败=" + e, true);
        }
      }
      PublicTools.logToast("stream", "【connectServer】自动模式转中转", true);
      connectRelay(relayHost, relayPort, device.uuid, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_RELAY) {
      PublicTools.logToast("stream", "【connectServer】进入强制中转模式", true);
      connectRelay(relayHost, relayPort, device.uuid, reTry, reTryTime);
      return;
    }

    PublicTools.logToast("stream", "【connectServer】未知模式，兜底走直连", true);
    connectDirectOrAdb(device, reTry, reTryTime);
  }

  private void connectDirectOrAdb(Device device, int reTry, int reTryTime) throws Exception {
    if (!device.isLinkDevice()) {
      PublicTools.logToast("stream", "【直连】开始，目标=" + device.address + ":" + device.serverPort, true);
      long startTime = System.currentTimeMillis();
      boolean mainConn = false;
      InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);

      for (int i = 0; i < reTry; i++) {
        try {
          PublicTools.logToast("stream", "【直连】第" + (i + 1) + "次", true);
          if (!mainConn) {
            mainSocket = new Socket();
            mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
            mainConn = true;
            PublicTools.logToast("stream", "【直连】mainSocket连接成功", true);
          }

          videoSocket = new Socket();
          videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
          PublicTools.logToast("stream", "【直连】videoSocket连接成功", true);

          mainOutputStream = mainSocket.getOutputStream();
          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
          connectDirect = true;

          PublicTools.logToast("stream", "【直连】输入输出流初始化完成", true);
          return;
        } catch (Exception e) {
          PublicTools.logToast("stream", "【直连失败】第" + (i + 1) + "次: " + e.getClass().getSimpleName() + ": " + e.getMessage(), true);
          try {
            if (mainSocket != null) mainSocket.close();
          } catch (Exception closeE) {
            PublicTools.logToast("stream", "【直连】关闭mainSocket失败=" + closeE, true);
          }
          try {
            if (videoSocket != null) videoSocket.close();
          } catch (Exception closeE) {
            PublicTools.logToast("stream", "【直连】关闭videoSocket失败=" + closeE, true);
          }

          if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) {
            PublicTools.logToast("stream", "【直连】超过半程超时，停止继续尝试", true);
            i = reTry;
          } else {
            Thread.sleep(reTryTime);
          }
        }
      }
    }

    PublicTools.logToast("stream", "【ADB转发】开始尝试 tcpForward", true);
    for (int i = 0; i < reTry; i++) {
      try {
        PublicTools.logToast("stream", "【ADB转发】第" + (i + 1) + "次", true);
        if (mainBufferStream == null) mainBufferStream = adb.tcpForward(device.serverPort);
        if (videoBufferStream == null) videoBufferStream = adb.tcpForward(device.serverPort);
        PublicTools.logToast("stream", "【ADB转发】tcpForward成功", true);
        return;
      } catch (Exception e) {
        PublicTools.logToast("stream", "【ADB转发失败】第" + (i + 1) + "次: " + e.getClass().getSimpleName() + ": " + e.getMessage(), true);
        Thread.sleep(reTryTime);
      }
    }

    throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
  }

  private void connectDirectOnly(Device device) throws Exception {
    PublicTools.logToast("stream", "【直连Only】目标=" + device.address + ":" + device.serverPort, true);
    InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);

    mainSocket = new Socket();
    mainSocket.connect(inetSocketAddress, 5000);
    PublicTools.logToast("stream", "【直连Only】mainSocket成功", true);

    videoSocket = new Socket();
    videoSocket.connect(inetSocketAddress, 5000);
    PublicTools.logToast("stream", "【直连Only】videoSocket成功", true);

    mainOutputStream = mainSocket.getOutputStream();
    mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
    videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
    connectDirect = true;

    PublicTools.logToast("stream", "【直连Only】完成", true);
  }

  private void connectRelay(String relayHost, int relayPort, String uuid, int reTry, int reTryTime) throws Exception {
    if (relayHost == null || relayHost.isEmpty()) {
      throw new Exception("服务器地址未配置，请在设置中填写服务器地址");
    }

    PublicTools.logToast("stream", "【中转】开始连接 relayHost=" + relayHost + " relayPort=" + relayPort + " uuid=" + uuid, true);

    for (int i = 0; i < reTry; i++) {
      try {
        PublicTools.logToast("stream", "【中转】第" + (i + 1) + "次", true);

        mainSocket = new Socket();
        mainSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
        PublicTools.logToast("stream", "【中转】mainSocket连接成功", true);

        videoSocket = new Socket();
        videoSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
        PublicTools.logToast("stream", "【中转】videoSocket连接成功", true);

        mainOutputStream = mainSocket.getOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = true;

        PublicTools.logToast("stream", "【中转】输入输出流初始化完成", true);
        return;
      } catch (Exception e) {
        PublicTools.logToast("stream", "【中转失败】第" + (i + 1) + "次: " + e.getClass().getSimpleName() + ": " + e.getMessage(), true);
        try {
          if (mainSocket != null) mainSocket.close();
        } catch (Exception closeE) {
          PublicTools.logToast("stream", "【中转】关闭mainSocket失败=" + closeE, true);
        }
        try {
          if (videoSocket != null) videoSocket.close();
        } catch (Exception closeE) {
          PublicTools.logToast("stream", "【中转】关闭videoSocket失败=" + closeE, true);
        }
        Thread.sleep(reTryTime);
      }
    }

    throw new Exception("服务器中转连接失败，请检查服务器地址和端口");
  }

  public String runShell(String cmd) throws Exception {
    PublicTools.logToast("stream", "【runShell】cmd=" + cmd, true);
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
    } else {
      return mainBufferStream.readByteArray(size);
    }
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

    try {
      if (shell != null) {
        PublicTools.logToast("server", new String(shell.readByteArrayBeforeClose().array()), false);
      }
    } catch (Exception e) {
      PublicTools.logToast("stream", "【close】读取shell输出失败=" + e, true);
    }

    if (connectDirect) {
      try {
        if (mainOutputStream != null) mainOutputStream.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭mainOutputStream失败=" + e, true);
      }
      try {
        if (videoDataInputStream != null) videoDataInputStream.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭videoDataInputStream失败=" + e, true);
      }
      try {
        if (mainDataInputStream != null) mainDataInputStream.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭mainDataInputStream失败=" + e, true);
      }
      try {
        if (mainSocket != null) mainSocket.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭mainSocket失败=" + e, true);
      }
      try {
        if (videoSocket != null) videoSocket.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭videoSocket失败=" + e, true);
      }
    } else {
      try {
        if (mainBufferStream != null) mainBufferStream.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭mainBufferStream失败=" + e, true);
      }
      try {
        if (videoBufferStream != null) videoBufferStream.close();
      } catch (Exception e) {
        PublicTools.logToast("stream", "【close】关闭videoBufferStream失败=" + e, true);
      }
    }
  }
}
