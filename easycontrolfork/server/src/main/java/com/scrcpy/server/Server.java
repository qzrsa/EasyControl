package com.scrcpy.server;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.IInterface;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.scrcpy.server.entity.Device;
import com.scrcpy.server.entity.Options;
import com.scrcpy.server.helper.AudioEncode;
import com.scrcpy.server.helper.ControlPacket;
import com.scrcpy.server.helper.VideoEncode;
import com.scrcpy.server.wrappers.ClipboardManager;
import com.scrcpy.server.wrappers.DisplayManager;
import com.scrcpy.server.wrappers.InputManager;
import com.scrcpy.server.wrappers.SurfaceControl;
import com.scrcpy.server.wrappers.WindowManager;

public final class Server {
  private static final String TAG = "Server";
  
  private static Socket mainSocket;
  private static Socket videoSocket;
  private static OutputStream mainOutputStream;
  private static OutputStream videoOutputStream;
  public static DataInputStream mainInputStream;

  private static final Object object = new Object();

  private static final int timeoutDelay = 1000 * 20;
  
  private static void log(String message) {
    System.out.println("[" + TAG + "] " + message);
  }

  public static void main(String... args) {
    try {
      log("Server starting...");
      Thread timeOutThread = new Thread(() -> {
        try {
          Thread.sleep(timeoutDelay);
          log("Timeout, releasing...");
          release();
        } catch (InterruptedException ignored) {
        }
      });
      timeOutThread.start();

      Options.parse(args);
      log("Options parsed - serverPort: " + Options.serverPort);

      setManagers();
      log("Managers initialized");
      
      Device.init();
      log("Device initialized");

      log("Waiting for client connection on port " + Options.serverPort);
      connectClient();
      log("Client connected!");

      boolean canAudio = AudioEncode.init();
      log("Audio init: " + canAudio);
      VideoEncode.init();
      log("Video initialized");

      ArrayList<Thread> threads = new ArrayList<>();
      threads.add(new Thread(Server::executeVideoOut));
      if (canAudio) {
        threads.add(new Thread(Server::executeAudioIn));
        threads.add(new Thread(Server::executeAudioOut));
      }
      threads.add(new Thread(Server::executeControlIn));
      for (Thread thread : threads) thread.setPriority(Thread.MAX_PRIORITY);
      for (Thread thread : threads) thread.start();
      log("All threads started, server running");

      timeOutThread.interrupt();
      synchronized (object) {
        object.wait();
      }

      for (Thread thread : threads) thread.interrupt();
    } catch (Exception e) {
      log("Error: " + e.getMessage());
      e.printStackTrace();
    } finally {

      release();
    }
  }

  private static Method GET_SERVICE_METHOD;

  @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
  private static void setManagers() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);

    WindowManager.init(getService("window", "android.view.IWindowManager"));

    DisplayManager.init(Class.forName("android.hardware.display.DisplayManagerGlobal").getDeclaredMethod("getInstance").invoke(null));

    Class<?> inputManagerClass;
    try {
      inputManagerClass = Class.forName("android.hardware.input.InputManagerGlobal");
    } catch (ClassNotFoundException e) {
      inputManagerClass = android.hardware.input.InputManager.class;
    }
    InputManager.init(inputManagerClass.getDeclaredMethod("getInstance").invoke(null));

    ClipboardManager.init(getService("clipboard", "android.content.IClipboard"));

    SurfaceControl.init();
  }

  private static IInterface getService(String service, String type) {
    try {
      IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
      Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
      return (IInterface) asInterfaceMethod.invoke(null, binder);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void connectClient() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(Options.serverPort)) {
      mainSocket = serverSocket.accept();
      videoSocket = serverSocket.accept();
      mainOutputStream = mainSocket.getOutputStream();
      videoOutputStream = videoSocket.getOutputStream();
      mainInputStream = new DataInputStream(mainSocket.getInputStream());
    }
  }

  private static void executeVideoOut() {
    try {
      int frame = 0;
      while (!Thread.interrupted()) {
        if (VideoEncode.isHasChangeConfig) {
          VideoEncode.isHasChangeConfig = false;
          VideoEncode.stopEncode();
          VideoEncode.startEncode();
        }
        VideoEncode.encodeOut();
        frame++;
        if (frame > 120) {
          if (System.currentTimeMillis() - lastKeepAliveTime > timeoutDelay) throw new IOException("");
          frame = 0;
        }
      }
    } catch (Exception e) {
      errorClose(e);
    }
  }

  private static void executeAudioIn() {
    while (!Thread.interrupted()) AudioEncode.encodeIn();
  }

  private static void executeAudioOut() {
    try {
      while (!Thread.interrupted()) AudioEncode.encodeOut();
    } catch (Exception e) {
      errorClose(e);
    }
  }

  private static long lastKeepAliveTime = System.currentTimeMillis();

  private static void executeControlIn() {
    try {
      while (!Thread.interrupted()) {
        switch (Server.mainInputStream.readByte()) {
          case 1:
            ControlPacket.handleTouchEvent();
            break;
          case 2:
            ControlPacket.handleKeyEvent();
            break;
          case 3:
            ControlPacket.handleClipboardEvent();
            break;
          case 4:
            lastKeepAliveTime = System.currentTimeMillis();
            break;
          case 5:
            Device.changeResolution(mainInputStream.readFloat());
            break;
          case 6:
            Device.rotateDevice();
            break;
          case 7:
            Device.changeScreenPowerMode(mainInputStream.readByte());
            break;
          case 8:
            Device.changePower(mainInputStream.readInt());
            break;
          case 9:
            Device.changeResolution(mainInputStream.readInt(), mainInputStream.readInt());
            break;
        }
      }
    } catch (Exception e) {
      errorClose(e);
    }
  }

  public synchronized static void writeMain(ByteBuffer byteBuffer) throws IOException {
    mainOutputStream.write(byteBuffer.array());
  }

  public static void writeVideo(ByteBuffer byteBuffer) throws IOException {
    videoOutputStream.write(byteBuffer.array());
  }

  public static void errorClose(Exception e) {
    e.printStackTrace();
    synchronized (object) {
      object.notify();
    }
  }

  private static void release() {
    for (int i = 0; i < 4; i++) {
      try {
        switch (i) {
          case 0:
            mainInputStream.close();
            mainSocket.close();
            videoSocket.close();
            break;
          case 1:
            VideoEncode.release();
            AudioEncode.release();
            break;
          case 2:
            Device.fallbackResolution();
            Device.fallbackScreenLightTimeout();
          case 3:
            Runtime.getRuntime().exit(0);
            break;
        }
      } catch (Exception ignored) {
      }
    }
  }

}
