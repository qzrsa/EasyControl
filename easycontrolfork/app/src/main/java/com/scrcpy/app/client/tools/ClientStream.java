package com.scrcpy.app.client.tools;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import com.scrcpy.app.BuildConfig;
import com.scrcpy.app.R;
import com.scrcpy.app.adb.Adb;
import com.scrcpy.app.buffer.BufferStream;
import com.scrcpy.app.client.decode.DecodecTools;
import com.scrcpy.app.entity.AppData;
import com.scrcpy.app.entity.Device;
import com.scrcpy.app.entity.MyInterface;
import com.scrcpy.app.helper.Logger;
import com.scrcpy.app.helper.PublicTools;

public class ClientStream {
  private static final String TAG = "ClientStream";
  
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
  private final Device device;

  public StatsOverlay getStatsOverlay() {
    return statsOverlay;
  }

  public ClientStream(Device device, MyInterface.MyFunctionBoolean handle) {
    this.device = device;
    
    Logger.i(TAG, "Creating ClientStream for device: " + device.name + " (" + device.address + ")");
    Logger.i(TAG, "Device settings - maxSize: " + device.maxSize + ", maxFps: " + device.maxFps + ", maxVideoBit: " + device.maxVideoBit);
    
    Thread timeOutThread = new Thread(() -> {
      try {
        Thread.sleep(timeoutDelay);
        Logger.e(TAG, "Connection timeout after " + timeoutDelay + "ms");
        PublicTools.logToast("stream", AppData.applicationContext.getString(R.string.toast_timeout), true);
        handle.run(false);
        if (connectThread != null) connectThread.interrupt();
      } catch (InterruptedException ignored) {
      }
    });
    connectThread = new Thread(() -> {
      try {
        Logger.method(TAG, "connectThread");
        adb = AdbTools.connectADB(device);
        Logger.i(TAG, "ADB connected successfully");
        
        startServer(device);
        Logger.i(TAG, "Server started on device");
        
        connectServer(device);
        Logger.i(TAG, "Connected to server - direct: " + connectDirect);
        
        handle.run(true);
        Logger.operation(TAG, "ClientStream initialization", true);
      } catch (Exception e) {
        Logger.e(TAG, "Connection failed: " + e.getMessage(), e);
        PublicTools.logToast("stream", e.toString(), true);
        handle.run(false);
      } finally {
        timeOutThread.interrupt();
      }
    });
    connectThread.start();
    timeOutThread.start();
  }

  private void startServer(Device device) throws Exception {
    Logger.method(TAG, "startServer");
    
    // Force push server every time due to package name change (com.daitj.easycontrolfork -> com.scrcpy)
    boolean needPush = true;
    Logger.d(TAG, "Need to push server: " + needPush + " (forced for package migration)");
    
    if (needPush) {
      adb.runAdbCmd("rm /data/local/tmp/easycontrolfork_* ");
      Logger.d(TAG, "Pushing server file: " + serverName);
      adb.pushFile(AppData.applicationContext.getResources().openRawResource(R.raw.easycontrolfork_server), serverName, null);
      Logger.i(TAG, "Server file pushed successfully");
    }
    
    shell = adb.getShell();
    
    // Start server via runAdbCmd to capture output
    String cmd = "/system/bin/app_process -Djava.class.path=" + serverName + " / com.scrcpy.server.Server"
      + " serverPort=" + device.serverPort
      + " listenClip=" + (device.listenClip ? 1 : 0)
      + " isAudio=" + (device.isAudio ? 1 : 0)
      + " maxSize=" + device.maxSize
      + " maxFps=" + device.maxFps
      + " maxVideoBit=" + device.maxVideoBit
      + " keepAwake=" + (device.keepWakeOnRunning ? 1 : 0)
      + " supportH265=" + ((device.useH265 && supportH265) ? 1 : 0)
      + " supportOpus=" + (supportOpus ? 1 : 0)
      + " startApp=" + device.startApp;
    
    Logger.d(TAG, "Starting server via runAdbCmd: " + cmd);
    
    // Run server in background, redirect output to log file
    try {
      String result = adb.runAdbCmd(cmd + " > /data/local/tmp/server.log 2>&1 &");
      Logger.d(TAG, "Server start result: " + (result != null ? result : "null"));
    } catch (Exception e) {
      Logger.e(TAG, "Error starting server: " + e.getMessage());
    }
    
    // Wait for server to start
    Thread.sleep(1000);
    Logger.i(TAG, "Server command sent, waiting for startup...");
    
    // Try to read any error output
    try {
      String errorLog = adb.runAdbCmd("cat /data/local/tmp/server.log 2>/dev/null || echo 'No log file'");
      if (errorLog != null && !errorLog.isEmpty()) {
        Logger.e(TAG, "Server log: " + errorLog);
      }
    } catch (Exception e) {
      Logger.d(TAG, "Could not read server log: " + e.getMessage());
    }
  }

  private void connectServer(Device device) throws Exception {
    Logger.method(TAG, "connectServer");
    
    // Wait longer for server to start (increased from 50ms to 500ms)
    Thread.sleep(500);
    int reTry = 40;
    int reTryTime = timeoutDelay / reTry;
    
    if (!device.isLinkDevice()) {
      long startTime = System.currentTimeMillis();
      boolean mainConn = false;
      InetSocketAddress inetSocketAddress = new InetSocketAddress(PublicTools.getIp(device.address), device.serverPort);
      Logger.d(TAG, "Connecting to: " + inetSocketAddress.getAddress() + ":" + inetSocketAddress.getPort());
      
      for (int i = 0; i < reTry; i++) {
        try {
          if (!mainConn) {
            mainSocket = new Socket();
            mainSocket.connect(inetSocketAddress, timeoutDelay / 2);
            mainConn = true;
            Logger.d(TAG, "Main socket connected (attempt " + (i+1) + ")");
          }
          videoSocket = new Socket();
          videoSocket.connect(inetSocketAddress, timeoutDelay / 2);
          mainOutputStream = mainSocket.getOutputStream();
          mainDataInputStream = new DataInputStream(mainSocket.getInputStream());
          videoDataInputStream = new DataInputStream(videoSocket.getInputStream());
          connectDirect = true;
          Logger.i(TAG, "Direct TCP connection established");
          return;
        } catch (Exception e) {
          Logger.d(TAG, "Connection attempt " + (i+1) + " failed: " + e.getMessage());
          if (mainSocket != null) mainSocket.close();
          if (videoSocket != null) videoSocket.close();
          if (System.currentTimeMillis() - startTime >= timeoutDelay / 2 - 1000) i = reTry;
          else Thread.sleep(reTryTime);
        }
      }
    }
    
    Logger.d(TAG, "Trying ADB tunnel connection");
    for (int i = 0; i < reTry; i++) {
      try {
        if (mainBufferStream == null) mainBufferStream = adb.tcpForward(device.serverPort);
        if (videoBufferStream == null) videoBufferStream = adb.tcpForward(device.serverPort);
        Logger.i(TAG, "ADB tunnel connection established");
        return;
      } catch (Exception e) {
        Logger.d(TAG, "ADB tunnel attempt " + (i+1) + " failed: " + e.getMessage());
        Thread.sleep(reTryTime);
      }
    }
    
    Logger.e(TAG, "Failed to connect to server after " + reTry + " attempts");
    throw new Exception(AppData.applicationContext.getString(R.string.toast_connect_server));
  }

  public String runShell(String cmd) throws Exception {
    Logger.d(TAG, "runShell: " + cmd);
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
    
    Logger.i(TAG, "Closing ClientStream for device: " + (device != null ? device.name : "unknown"));
    
    if (shell != null) {
      String serverOutput = new String(shell.readByteArrayBeforeClose().array());
      Logger.d(TAG, "Server output: " + serverOutput);
      PublicTools.logToast("server", serverOutput, false);
    }
    
    if (connectDirect) {
      try {
        mainOutputStream.close();
        videoDataInputStream.close();
        mainDataInputStream.close();
        mainSocket.close();
        videoSocket.close();
        Logger.d(TAG, "Direct sockets closed");
      } catch (Exception e) {
        Logger.e(TAG, "Error closing sockets: " + e.getMessage());
      }
    } else {
      mainBufferStream.close();
      videoBufferStream.close();
      Logger.d(TAG, "ADB tunnels closed");
    }
    
    Logger.i(TAG, "ClientStream closed");
  }
}
