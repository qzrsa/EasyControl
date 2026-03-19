package com.scrcpy.app.adb;

import android.hardware.usb.UsbDevice;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import com.scrcpy.app.buffer.BufferStream;
import com.scrcpy.app.entity.MyInterface;
import com.scrcpy.app.helper.Logger;

public class Adb {
  private static final String TAG = "Adb";
  
  private boolean isClosed = false;
  private final AdbChannel channel;
  private int localIdPool = 1;
  private int MAX_DATA = AdbProtocol.CONNECT_MAXDATA;
  private final ConcurrentHashMap<Integer, BufferStream> connectionStreams = new ConcurrentHashMap<>(10);
  private final ConcurrentHashMap<Integer, BufferStream> openStreams = new ConcurrentHashMap<>(5);

  private final Thread handleInThread = new Thread(this::handleIn);

  public Adb(String address,int port, AdbKeyPair keyPair) throws Exception {
    Logger.method(TAG, "Adb(String address, int port)");
    Logger.d(TAG, "Connecting to " + address + ":" + port);
    channel = new TcpChannel(address, port);
    connect(keyPair);
  }

  public Adb(UsbDevice usbDevice, AdbKeyPair keyPair) throws Exception {
    Logger.method(TAG, "Adb(UsbDevice)");
    if (usbDevice == null) {
      Logger.e(TAG, "USB device is null");
      throw new IOException("no usb connect");
    }
    channel = new UsbChannel(usbDevice);
    connect(keyPair);
  }

  private void connect(AdbKeyPair keyPair) throws Exception {
    Logger.method(TAG, "connect");
    
    Logger.d(TAG, "Sending ADB connect message");
    channel.write(AdbProtocol.generateConnect());
    AdbProtocol.AdbMessage message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
    Logger.d(TAG, "Received message command: 0x" + Integer.toHexString(message.command));
    
    if (message.command == AdbProtocol.CMD_AUTH) {
      Logger.d(TAG, "Authentication required, sending signature");
      channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE, keyPair.signPayload(message.payload)));
      message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
      Logger.d(TAG, "Auth response command: 0x" + Integer.toHexString(message.command));
      
      if (message.command == AdbProtocol.CMD_AUTH) {
        Logger.d(TAG, "Signature rejected, sending RSA public key");
        channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_RSA_PUBLIC, keyPair.publicKeyBytes));
        message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
        Logger.d(TAG, "RSA auth response command: 0x" + Integer.toHexString(message.command));
      }
    }
    
    if (message.command != AdbProtocol.CMD_CNXN) {
      Logger.e(TAG, "Connection failed, expected CMD_CNXN (0x" + Integer.toHexString(AdbProtocol.CMD_CNXN) + 
                 ") but got 0x" + Integer.toHexString(message.command));
      channel.close();
      throw new Exception("ADB连接失败");
    }
    
    MAX_DATA = message.arg1;
    Logger.i(TAG, "ADB connection established, MAX_DATA: " + MAX_DATA);

    handleInThread.setPriority(Thread.MAX_PRIORITY);
    handleInThread.start();
    Logger.d(TAG, "handleInThread started");
  }

  private BufferStream open(String destination, boolean canMultipleSend) throws InterruptedException {
    Logger.d(TAG, "Opening stream to: " + destination);
    int localId = localIdPool++ * (canMultipleSend ? 1 : -1);
    writeToChannel(AdbProtocol.generateOpen(localId, destination));
    BufferStream bufferStream;
    do {
      synchronized (this) {
        wait();
      }
      bufferStream = openStreams.get(localId);
    } while (!isClosed && bufferStream == null);
    openStreams.remove(localId);
    Logger.d(TAG, "Stream opened: " + destination + " (localId: " + localId + ")");
    return bufferStream;
  }

  public String restartOnTcpip(int port) throws InterruptedException {
    Logger.i(TAG, "restartOnTcpip: " + port);
    BufferStream bufferStream = open("tcpip:" + port, false);
    do {
      synchronized (this) {
        wait();
      }
    } while (!bufferStream.isClosed());
    String result = new String(bufferStream.readByteArrayBeforeClose().array());
    Logger.i(TAG, "restartOnTcpip result: " + result);
    return result;
  }

  public void pushFile(InputStream file, String remotePath, MyInterface.MyFunctionInt handleProcess) throws Exception {
    Logger.i(TAG, "pushFile to: " + remotePath);
    BufferStream bufferStream = open("sync:", false);

    String sendString = remotePath + ",33206";
    byte[] bytes = sendString.getBytes();
    bufferStream.write(AdbProtocol.generateSyncHeader("SEND", sendString.length()));
    bufferStream.write(ByteBuffer.wrap(bytes));

    byte[] byteArray = new byte[10240 - 8];
    int hasSendLen = 0;
    int allNeedSendLen = file.available();
    int lastProcess = 0;
    int len = file.read(byteArray, 0, byteArray.length);
    do {
      bufferStream.write(AdbProtocol.generateSyncHeader("DATA", len));
      bufferStream.write(ByteBuffer.wrap(byteArray, 0, len));
      hasSendLen += len;
      int newProcess = (int) (((float) hasSendLen / allNeedSendLen) * 100);
      if (newProcess != lastProcess) {
        lastProcess = newProcess;
        if (handleProcess != null) handleProcess.run(lastProcess);
      }
      len = file.read(byteArray, 0, byteArray.length);
    } while (len > 0);
    file.close();

    bufferStream.write(AdbProtocol.generateSyncHeader("DONE", 1704038400));
    bufferStream.write(AdbProtocol.generateSyncHeader("QUIT", 0));
    do {
      synchronized (this) {
        wait();
      }
    } while (!bufferStream.isClosed());
    Logger.i(TAG, "pushFile completed: " + remotePath);
  }

  public String runAdbCmd(String cmd) throws Exception {
    Logger.d(TAG, "runAdbCmd: " + cmd);
    BufferStream bufferStream = open("shell:" + cmd, true);
    do {
      synchronized (this) {
        wait();
      }
    } while (!bufferStream.isClosed());
    String result = new String(bufferStream.readByteArrayBeforeClose().array());
    Logger.d(TAG, "runAdbCmd result length: " + result.length());
    return result;
  }

  public BufferStream getShell() throws InterruptedException {
    Logger.d(TAG, "getShell");
    return open("shell:", true);
  }

  public BufferStream tcpForward(int port) throws IOException, InterruptedException {
    Logger.d(TAG, "tcpForward: " + port);
    BufferStream bufferStream = open("tcp:" + port, true);
    if (bufferStream.isClosed()) {
      Logger.e(TAG, "tcpForward failed for port: " + port);
      throw new IOException("error forward");
    }
    Logger.d(TAG, "tcpForward success for port: " + port);
    return bufferStream;
  }

  public BufferStream localSocketForward(String socketName) throws IOException, InterruptedException {
    Logger.d(TAG, "localSocketForward: " + socketName);
    BufferStream bufferStream = open("localabstract:" + socketName, true);
    if (bufferStream.isClosed()) {
      Logger.e(TAG, "localSocketForward failed for: " + socketName);
      throw new IOException("error forward");
    }
    Logger.d(TAG, "localSocketForward success for: " + socketName);
    return bufferStream;
  }

  private void handleIn() {
    Logger.method(TAG, "handleIn");
    try {
      while (!Thread.interrupted()) {
        AdbProtocol.AdbMessage message = AdbProtocol.AdbMessage.parseAdbMessage(channel);
        BufferStream bufferStream = connectionStreams.get(message.arg1);
        boolean isNeedNotify = bufferStream == null;

        if (isNeedNotify) bufferStream = createNewStream(message.arg1, message.arg0, message.arg1 > 0);
        switch (message.command) {
          case AdbProtocol.CMD_OKAY:
            bufferStream.setCanWrite(true);
            break;
          case AdbProtocol.CMD_WRTE:
            bufferStream.pushSource(message.payload);
            writeToChannel(AdbProtocol.generateOkay(message.arg1, message.arg0));
            break;
          case AdbProtocol.CMD_CLSE:
            bufferStream.close();
            isNeedNotify = true;
            break;
        }
        if (isNeedNotify) {
          synchronized (this) {
            notifyAll();
          }
        }
      }
    } catch (Exception e) {
      Logger.e(TAG, "handleIn error: " + e.getMessage(), e);
      close();
    }
  }

  private void writeToChannel(ByteBuffer byteBuffer) {
    synchronized (channel) {
      try {
        channel.write(byteBuffer);
      } catch (Exception e) {
        Logger.e(TAG, "writeToChannel error: " + e.getMessage());
        close();
      }
    }
  }

  private BufferStream createNewStream(int localId, int remoteId, boolean canMultipleSend) throws Exception {
    return new BufferStream(false, canMultipleSend, new BufferStream.UnderlySocketFunction() {
      @Override
      public void connect(BufferStream bufferStream) {
        connectionStreams.put(localId, bufferStream);
        openStreams.put(localId, bufferStream);
      }

      @Override
      public void write(BufferStream bufferStream, ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
          byte[] byteArray = new byte[Math.min(MAX_DATA - 128, buffer.remaining())];
          buffer.get(byteArray);
          writeToChannel(AdbProtocol.generateWrite(localId, remoteId, byteArray));
        }
      }

      @Override
      public void flush(BufferStream bufferStream) {
        writeToChannel(AdbProtocol.generateOkay(localId, remoteId));
      }

      @Override
      public void close(BufferStream bufferStream) {
        connectionStreams.remove(localId);
        writeToChannel(AdbProtocol.generateClose(localId, remoteId));
      }
    });
  }

  public boolean isClosed() {
    return isClosed;
  }

  public void close() {
    if (isClosed) return;
    isClosed = true;
    Logger.i(TAG, "Closing ADB connection");
    handleInThread.interrupt();
    for (Object bufferStream : connectionStreams.values().toArray()) ((BufferStream) bufferStream).close();
    channel.close();
    synchronized (this) {
      notifyAll();
    }
    Logger.i(TAG, "ADB connection closed");
  }

}
