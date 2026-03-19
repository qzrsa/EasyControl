package com.daitj.easycontrolfork.app.client.tools;

import android.content.Intent;
import android.net.VpnService;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.daitj.easycontrolfork.app.BuildConfig;
import com.daitj.easycontrolfork.app.R;
import com.daitj.easycontrolfork.app.adb.Adb;
import com.daitj.easycontrolfork.app.buffer.BufferStream;
import com.daitj.easycontrolfork.app.client.decode.DecodecTools;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.entity.MyInterface;
import com.daitj.easycontrolfork.app.helper.EasyTierManager;
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

  public StatsOverlay getStatsOverlay() {
    return statsOverlay;
  }

  private static String getLogDir() {
    try {
      File dir = AppData.applicationContext.getExternalFilesDir(null);
      if (dir != null) return dir.getAbsolutePath() + "/";
    } catch (Exception ignored) {}
    return "/storage/emulated/0/Download/";
  }

  private static synchronized void ensureLogFile() {
    try {
      if (logFile != null) return;
      if (firstLogTime == null) firstLogTime = fileSdf.format(new Date());
      File dir = new File(getLogDir());
      if (!dir.exists()) dir.mkdirs();
      logFile = new File(dir, "clientstream_" + firstLogTime + ".log");
      if (!logFile.exists()) logFile.createNewFile();
    } catch (Exception ignored) {}
  }

  private static synchronized void writeLogToFile(String line) {
    try {
      ensureLogFile();
      if (logFile == null) return;
      try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
        fos.write((line + "\n").getBytes());
        fos.flush();
      }
    } catch (Exception ignored) {}
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
          addDebugLog("【配置】使用全局 EasyTier 节点地址=" + host);
          return host;
        }
      } catch (Exception e) {
        addDebugLog("【配置】读取全局 EasyTier 节点地址失败=" + e);
      }
    }
    String host = device.relayHost != null ? device.relayHost : "";
    addDebugLog("【配置】使用设备 EasyTier 节点地址=" + host);
    return host;
  }

  private int getEffectiveRelayPort(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        int port = AppData.setting.getDefaultRelayPort();
        if (port > 0) {
          addDebugLog("【配置】使用全局 EasyTier 端口=" + port);
          return port;
        }
      } catch (Exception e) {
        addDebugLog("【配置】读取全局 EasyTier 端口失败=" + e);
      }
    }
    int port = device.relayPort > 0 ? device.relayPort : 11010;
    addDebugLog("【配置】使用设备 EasyTier 端口=" + port);
    return port;
  }

  private String getEffectiveRelayKey(Device device) {
    if (device.useGlobalRelay && AppData.setting != null) {
      try {
        String key = AppData.setting.getDefaultRelayKey();
        if (key != null) {
          addDebugLog("【配置】使用全局 EasyTier 网络密鑐长度=" + key.length());
          return key;
        }
      } catch (Exception e) {
        addDebugLog("【配置】读取全局 EasyTier 网络密鑐失败=" + e);
      }
    }
    String key = device.relayKey != null ? device.relayKey : "";
    addDebugLog("【配置】使用设备 EasyTier 网络密鑐长度=" + key.length());
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
      + " easyTierHost=" + relayHost
      + " easyTierPort=" + relayPort
      + " easyTierKeyLen=" + relayKey.length()
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
      addDebugLog("【connectServer】自动模式切换到 EasyTier 虚拟组网");
      connectViaEasyTier(relayHost, relayPort, relayKey, device.serverPort, reTry, reTryTime);
      return;
    }

    if (mode == Device.CONN_RELAY) {
      addDebugLog("【connectServer】进入强制 EasyTier 虚拟组网模式");
      connectViaEasyTier(relayHost, relayPort, relayKey, device.serverPort, reTry, reTryTime);
      return;
    }

    addDebugLog("【connectServer】未知模式，包底走直连");
    connectDirectOrAdb(device, reTry, reTryTime);
  }

  /**
   * 通过 EasyTier 虚拟组网连接被控端。
   * 流程：
   * 1. 检查 VPN 权限是否已授予
   * 2. 启动 EasyTierVpnService，等待虚拟 IP 分配（最多等 12 秒）
   * 3. 用虚拟 IP + serverPort 连接被控端的 scrcpy server
   */
  private void connectViaEasyTier(
    String relayHost, int relayPort, String relayKey,
    int serverPort, int reTry, int reTryTime
  ) throws Exception {
    if (relayHost == null || relayHost.isEmpty()) {
      throw new Exception(AppData.applicationContext.getString(R.string.error_easytier_host_empty));
    }

    addDebugLog("【EasyTier】开始启动 VPN 服务 host=" + relayHost + " port=" + relayPort);

    // 检查 VPN 权限
    Intent vpnIntent = VpnService.prepare(AppData.applicationContext);
    if (vpnIntent != null) {
      // 需要用户授权，暂时抛异常提示（后续在 Activity 层面处理弹出请求）
      throw new Exception("需要用户授予 VPN 权限，请在设置界面开启 VPN 权限后重试");
    }

    // 启动 EasyTier VPN 服务，等待虚拟 IP
    final String[] virtualIp = {null};
    final String[] errorMsg = {null};
    final CountDownLatch latch = new CountDownLatch(1);

    EasyTierManager.start(
      AppData.applicationContext,
      relayHost,
      relayPort,
      relayKey,
      new EasyTierManager.VirtualIpCallback() {
        @Override
        public void onVirtualIpReady(String ip) {
          addDebugLog("【EasyTier】虚拟 IP 已分配: " + ip);
          virtualIp[0] = ip;
          latch.countDown();
        }
        @Override
        public void onError(String reason) {
          addDebugLog("【EasyTier】启动失败: " + reason);
          errorMsg[0] = reason;
          latch.countDown();
        }
      }
    );

    // 最多等 12 秒等待虚拟 IP
    addDebugLog("【EasyTier】等待虚拟 IP 分配...");
    boolean assigned = latch.await(12, TimeUnit.SECONDS);

    if (!assigned || virtualIp[0] == null) {
      String err = errorMsg[0] != null ? errorMsg[0] : "等待虚拟 IP 超时";
      throw new Exception(AppData.applicationContext.getString(R.string.error_easytier_connect_fail) + ": " + err);
    }

    String targetIp = virtualIp[0];
    addDebugLog("【EasyTier】用虚拟 IP 连接 scrcpy server: " + targetIp + ":" + serverPort);

    InetSocketAddress addr = new InetSocketAddress(targetIp, serverPort);
    for (int i = 0; i < reTry; i++) {
      try {
        addDebugLog("【EasyTier】连接第" + (i + 1) + "次");

        mainSocket = new Socket();
        mainSocket.connect(addr, 5000);
        addDebugLog("【EasyTier】mainSocket 成功");

        videoSocket = new Socket();
        videoSocket.connect(addr, 5000);
        addDebugLog("【EasyTier】videoSocket 成功");

        mainOutputStream = mainSocket.getOutputStream();
        mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
        videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
        connectDirect = true;

        addDebugLog("【EasyTier】连接完成");
        return;
      } catch (Exception e) {
        addDebugLog("【EasyTier失败】第" + (i + 1) + "次: " + e.getMessage());
        try { if (mainSocket != null) mainSocket.close(); } catch (Exception ignored) {}
        try { if (videoSocket != null) videoSocket.close(); } catch (Exception ignored) {}
        Thread.sleep(reTryTime);
      }
    }

    throw new Exception(AppData.applicationContext.getString(R.string.error_easytier_connect_fail));
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
          try { if (mainSocket != null) mainSocket.close(); } catch (Exception closeE) { addDebugLog("【直连】关闭mainSocket失败=" + closeE); }
          try { if (videoSocket != null) videoSocket.close(); } catch (Exception closeE) { addDebugLog("【直连】关闭videoSocket失败=" + closeE); }

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
      try { if (mainOutputStream != null) mainOutputStream.close(); } catch (Exception e) { addDebugLog("【close】关闭mainOutputStream失败=" + e); }
      try { if (videoDataInputStream != null) videoDataInputStream.close(); } catch (Exception e) { addDebugLog("【close】关闭videoDataInputStream失败=" + e); }
      try { if (mainDataInputStream != null) mainDataInputStream.close(); } catch (Exception e) { addDebugLog("【close】关闭mainDataInputStream失败=" + e); }
      try { if (mainSocket != null) mainSocket.close(); } catch (Exception e) { addDebugLog("【close】关闭mainSocket失败=" + e); }
      try { if (videoSocket != null) videoSocket.close(); } catch (Exception e) { addDebugLog("【close】关闭videoSocket失败=" + e); }
    } else {
      try { if (mainBufferStream != null) mainBufferStream.close(); } catch (Exception e) { addDebugLog("【close】关闭mainBufferStream失败=" + e); }
      try { if (videoBufferStream != null) videoBufferStream.close(); } catch (Exception e) { addDebugLog("【close】关闭videoBufferStream失败=" + e); }
    }
  }
}
