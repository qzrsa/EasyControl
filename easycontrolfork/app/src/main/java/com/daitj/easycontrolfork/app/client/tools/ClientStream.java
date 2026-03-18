package com.daitj.easycontrolfork.app.client.tools;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

  private static final String[] debugLogs = new String[10];
  private static int debugLogIndex = 0;
  private static int debugLogCount = 0;

  private static final SimpleDateFormat lineSdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
  private static final SimpleDateFormat fileSdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());

  private static File logFile = null;
  private static String firstLogTime = null;
  private static final String LOG_DIR = "/storage/emulated/0/Android/data/QZRS/Scrcpy/files/";

  public StatsOverlay getStatsOverlay() {
    return statsOverlay;
  }

  private static synchronized void ensureLogFile() {
    try {
      if (logFile != null) return;

      if (firstLogTime == null) firstLogTime = fileSdf.format(new Date());

      File dir = new File(LOG_DIR);
      if (!dir.exists()) dir.mkdirs();

      logFile = new File(dir, "clientstream_" + firstLogTime + ".log");
      if (!logFile.exists()) logFile.createNewFile();
    } catch (Exception ignored) {
    }
  }

  private static synchronized void writeLogToFile(String line) {
    try {
      ensureLogFile();
      if (logFile == null) return;
      try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
        fos.write((line + "\n").getBytes());
        fos.flush();
      }
    } catch (Exception ignored) {
    }
  }

  private static synchronized void addDebugLog(String msg) {
    String line = "[" + lineSdf.format(new Date()) + "] " + msg;
    debugLogs[debugLogIndex] = line;
    debugLogIndex = (debugLogIndex + 1) % 10;
    if (debugLogCount < 10) debugLogCount++;

    writeLogToFile(line);
    PublicTools.logToast("stream", line, true);
  }

  public static synchronized String getDebugLogs() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < debugLogCount; i++) {
      int idx = (debugLogIndex - debugLogCount + i + 10) % 10;
      sb.append(debugLogs[idx]).append("\n");
    }
    return sb.toString();
  }

  private static synchronized void clearDebugLogs() {
    for (int i = 0; i < 10; i++) debugLogs[i] = null;
    debugLogIndex = 0;
    debugLogCount = 0;
    logFile = null;
    firstLogTime = null;
  }

  public ClientStream(Device device, MyInterface.MyFunctionBoolean handle) {
    clearDebugLogs();

    Thread timeOutThread = new Thread(() -> {
      try {
        Thread.sleep(timeoutDelay);
        addDebugLog("【超时】" + AppData.applicationContext.getString(R.string.toast_timeout));
        handle.run(false);
        if (connectThread != null) connectThread.interrupt();
      } catch (InterruptedException e) {
        addDebugLog("【超时线程结束】" + e);
      }
    });

    connectThread = new Thread(() -> {
      try {
        addDebugLog("【1】开始连接，device=" + device.name + " uuid=" + device.uuid);
        addDebugLog("【2】开始 ADB 连接，address=" + device.address + " adbPort=" + device.adbPort);
        adb = AdbTools.connectADB(device);
        addDebugLog("【3】ADB 连接成功");

        addDebugLog("【4】开始启动服务端 jar，serverPort=" + device.serverPort);
        startServer(device);
        addDebugLog("【5】服务端启动流程完成");

        addDebugLog("【6】开始连接数据流");
        connectServer(device);
        addDebugLog("【7】数据流连接完成，connectDirect=" + connectDirect);

        handle.run(true);
      } catch (Exception e) {
        addDebugLog("【失败异常】" + e.getClass().getSimpleName() + ": " + e.getMessage());
        addDebugLog("【失败详情】" + e.toString());
        handle.run(false);
      } finally {
        timeOutThread.interrupt();
      }
    });

    connectThread.start();
    timeOutThread.start();
  }

  private void startServer(Device device) throws Exception {
    addDebugLog("【startServer】直接重推服务端文件");

    try {
      adb.runAdbCmd("rm /data/local/tmp/easycontrolfork_* ");
      addDebugLog("【startServer】旧文件删除完成");
    } catch (Exception e) {
      addDebugLog("【startServer】删除旧文件失败: " + e);
    }

    adb.pushFile(
      AppData.applicationContext.getResources().openRawResource(R.raw.easycontrolfork_server),
      serverName,
      null
    );
    addDebugLog("【startServer】服务端文件推送完成");

    shell = adb.getShell();
    addDebugLog("【startServer】shell 已获取");

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

    addDebugLog("【startServer】发送启动命令");
    shell.write(ByteBuffer.wrap(cmd.getBytes()));
    addDebugLog("【startServer】启动命令已发送");
  }

  private int getEffectiveConnMode(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        int mode = AppData.setting.getDefaultConnMode();
        addDebugLog("【配置】使用全局连接模式=" + mode);
        return mode;
      } catch (Exception e) {
        addDebugLog("【配置】读取全局连接模式失败=" + e);
      }
    }
    addDebugLog("【配置】使用设备连接模式=" + device.connMode);
    return device.connMode;
  }

  private String getEffectiveRelayHost(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String host = AppData.setting.getDefaultRelayHost();
        if (host != null && !host.isEmpty()) {
          addDebugLog("【配置】使用全局 relayHost=" + host);
          return host;
        }
      } catch (Exception e) {
        addDebugLog("【配置】读取全局 relayHost 失败=" + e);
      }
    }
    String host = device.relayHost != null ? device.relayHost : "";
    addDebugLog("【配置】使用设备 relayHost=" + host);
    return host;
  }

  private int getEffectiveRelayPort(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        int port = AppData.setting.getDefaultRelayPort();
        if (port > 0) {
          addDebugLog("【配置】使用全局 relayPort=" + port);
          return port;
        }
      } catch (Exception e) {
        addDebugLog("【配置】读取全局 relayPort 失败=" + e);
      }
    }
    int port = device.relayPort > 0 ? device.relayPort : 25167;
    addDebugLog("【配置】使用设备 relayPort=" + port);
    return port;
  }

  private String getEffectiveRelayKey(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String key = AppData.setting.getDefaultRelayKey();
        if (key != null) {
          addDebugLog("【配置】使用全局 relayKey 长度=" + key.length());
          return key;
        }
      } catch (Exception e) {
        addDebugLog("【配置】读取全局 relayKey 失败=" + e);
      }
    }
    String key = device.relayKey != null ? device.relayKey : "";
    addDebugLog("【配置】使用设备 relayKey 长度=" + key.length());
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

    addDebugLog("【connectServer】mode=" + mode
      + " address=" + device.address
      + " serverPort=" + device.serverPort
      + " relayHost=" + relayHost
      + " relayPort=" + relayPort
      + " relayKeyLen=" + relayKey.length()
      + " useGlobalRelay=" + device.useGlobalRelay
      + " isLinkDevice=" + device.isLinkDevice());

    if (mode == Device.CONN_DIRECT) {
      addDebugLog("【connectServer】进入直连模式");
      connectDirectOrAdb(device, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_AUTO) {
      addDebugLog("【connectServer】进入自动模式");
      if (!device.isLinkDevice()) {
        try {
          addDebugLog("【connectServer】自动模式先尝试直连");
          connectDirectOnly(device);
          addDebugLog("【connectServer】自动模式直连成功");
          return;
        } catch (Exception e) {
          addDebugLog("【connectServer】自动模式直连失败=" + e);
        }
      }
      addDebugLog("【connectServer】自动模式转中转");
      connectRelay(relayHost, relayPort, device.uuid, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_RELAY) {
      addDebugLog("【connectServer】进入强制中转模式");
      connectRelay(relayHost, relayPort, device.uuid, reTry, reTryTime);
      return;
    }

    addDebugLog("【connectServer】未知模式，兜底走直连");
    connectDirectOrAdb(device, reTry, reTryTime);
  }

  private void connectDirectOrAdb(Device device, int reTry, int reTryTime) throws Exception {
    if (!device.isLinkDevice()) {
      addDebugLog("【直连】开始，目标=" + device.address + ":" + device.serverPort);
      long startTime = System.currentTimeMillis();
      boolean mainConn = false;
      InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);

      for (int i = 0; i < reTry; i++) {
        try {
          addDebugLog("【直连】第" + (i + 1) + "次");
          if (!mainConn) {
            mainSocket = new Socket();
            mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
            mainConn = true;
            addDebugLog("【直连】mainSocket连接成功");
          }

          videoSocket = new Socket();
          videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
          addDebugLog("【直连】videoSocket连接成功");

          mainOutputStream = mainSocket.getOutputStream();
          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
          connectDirect = true;

          addDebugLog("【直连】输入输出流初始化完成");
          return;
        } catch (Exception e) {
          addDebugLog("【直连失败】第" + (i + 1) + "次: " + e.getClass().getSimpleName() + ": " + e.getMessage());
          try {
            if (mainSocket != null) mainSocket.close();
          } catch (Exception closeE) {
            addDebugLog("【直连】关闭mainSocket失败=" + closeE);
          }
          try {
            if (videoSocket != null) videoSocket.close();
          } catch (Exception closeE) {
            addDebugLog("【直连】关闭videoSocket失败=" + closeE);
          }

          if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) {
            addDebugLog("【直连】超过半程超时，停止继续尝试");
            i = reTry;
          } else {
            Thread.sleep(reTryTime);
          }
        }
      }
    }

    addDebugLog("【ADB转发】开始尝试 tcpForward");
    for (int i = 0; i < reTry; i++) {
      try {
        addDebugLog("【ADB转发】第" + (i + 1) + "次");
        if (mainBufferStream == null) mainBufferStream = adb.tcpForward(device.serverPort);
        if (videoBufferStream == null) videoBufferStream = adb.tcpForward(device.serverPort);
        addDebugLog("【ADB转发】tcpForward成功");
        return;
      } catch (Exception e) {
        addDebugLog("【ADB转发失败】第" + (i + 1) + "次: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        Thread.sleep(reTryTime);
      }
    }

    throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
  }

  private void connectDirectOnly(Device device) throws Exception {
    addDebugLog("【直连Only】目标=" + device.address + ":" + device.serverPort);
    InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);

    mainSocket = new Socket();
    mainSocket.connect(inetSocketAddress, 5000);
    addDebugLog("【直连Only】mainSocket成功");

    videoSocket = new Socket();
    videoSocket.connect(inetSocketAddress, 5000);
    addDebugLog("【直连Only】videoSocket成功");

    mainOutputStream = mainSocket.getOutputStream();
    mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
    videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
    connectDirect = true;

    addDebugLog("【直连Only】完成");
  }

  private void connectRelay(String relayHost, int relayPort, String uuid, int reTry, int reTryTime) throws Exception {
    if (relayHost == null || relayHost.isEmpty()) {
      throw new Exception("服务器地址未配置，请在设置中填写服务器地址");
    }

    addDebugLog("【中转】开始连接 relayHost=" + relayHost + " relayPort=" + relayPort + " uuid=" + uuid);

    for (int i = 0; i < reTry; i++) {
      try {
        addDebugLog("【中转】第" + (i + 1) + "次");

        mainSocket = new Socket();
        mainSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
        addDebugLog("【中转】mainSocket连接成功");

        videoSocket = new Socket();
        videoSocket.connect(new InetSocketAddress(relayHost, relayPort), 5000);
        addDebugLog("【中转】videoSocket连接成功");

        mainOutputStream = mainSocket.getOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = true;

        addDebugLog("【中转】输入输出流初始化完成");
        return;
      } catch (Exception e) {
        addDebugLog("【中转失败】第" + (i + 1) + "次: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        try {
          if (mainSocket != null) mainSocket.close();
        } catch (Exception closeE) {
          addDebugLog("【中转】关闭mainSocket失败=" + closeE);
        }
        try {
          if (videoSocket != null) videoSocket.close();
        } catch (Exception closeE) {
          addDebugLog("【中转】关闭videoSocket失败=" + closeE);
        }
        Thread.sleep(reTryTime);
      }
    }

    throw new Exception("服务器中转连接失败，请检查服务器地址和端口");
  }

  public String runShell(String cmd) throws Exception {
    addDebugLog("【runShell】cmd=" + cmd);
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
      addDebugLog("【close】读取shell输出失败=" + e);
    }

    if (connectDirect) {
      try {
        if (mainOutputStream != null) mainOutputStream.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭mainOutputStream失败=" + e);
      }
      try {
        if (videoDataInputStream != null) videoDataInputStream.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭videoDataInputStream失败=" + e);
      }
      try {
        if (mainDataInputStream != null) mainDataInputStream.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭mainDataInputStream失败=" + e);
      }
      try {
        if (mainSocket != null) mainSocket.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭mainSocket失败=" + e);
      }
      try {
        if (videoSocket != null) videoSocket.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭videoSocket失败=" + e);
      }
    } else {
      try {
        if (mainBufferStream != null) mainBufferStream.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭mainBufferStream失败=" + e);
      }
      try {
        if (videoBufferStream != null) videoBufferStream.close();
      } catch (Exception e) {
        addDebugLog("【close】关闭videoBufferStream失败=" + e);
      }
    }
  }
}
