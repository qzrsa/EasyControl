package com.scrcpy.app.adb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import com.scrcpy.app.helper.Logger;

public class TcpChannel implements AdbChannel {
  private static final String TAG = "TcpChannel";
  
  private final Socket socket;
  private final InputStream inputStream;
  private final OutputStream outputStream;

  public TcpChannel(String host, int port) throws IOException {
    Logger.d(TAG, "Creating TcpChannel to " + host + ":" + port);
    socket = new Socket();
    socket.setTcpNoDelay(true);
    socket.setKeepAlive(true);
    
    Logger.d(TAG, "Connecting socket...");
    socket.connect(new InetSocketAddress(host, port), 10000);
    Logger.d(TAG, "Socket connected to " + host + ":" + port);
    
    inputStream = socket.getInputStream();
    outputStream = socket.getOutputStream();
    Logger.d(TAG, "TcpChannel created successfully");
  }

  @Override
  public void write(ByteBuffer data) throws IOException {
    outputStream.write(data.array());
  }

  @Override
  public void flush() throws IOException {
    outputStream.flush();
  }

  @Override
  public ByteBuffer read(int size) throws IOException {
    byte[] buffer = new byte[size];
    int bytesRead = 0;
    while (bytesRead < size) {
      int read = inputStream.read(buffer, bytesRead, size - bytesRead);
      if (read == -1) {
        Logger.w(TAG, "Socket closed while reading (" + bytesRead + "/" + size + ")");
        break;
      }
      bytesRead += read;
    }
    return ByteBuffer.wrap(buffer);
  }

  @Override
  public void close() {
    Logger.d(TAG, "Closing TcpChannel");
    try {
      outputStream.close();
      inputStream.close();
      socket.close();
      Logger.d(TAG, "TcpChannel closed");
    } catch (Exception e) {
      Logger.e(TAG, "Error closing TcpChannel: " + e.getMessage());
    }
  }
}
