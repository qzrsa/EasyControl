package com.scrcpy.app.buffer;

import java.io.IOException;

public class Buffer {
  private final int capacity;
  private final byte[] buffer;
  private int head = 0;
  private int tail = 0;

  private final Object writeLock = new Object();
  private final Object readLock = new Object();

  public Buffer(int capacity) {
    this.capacity = capacity;
    this.buffer = new byte[capacity];
  }

  public void write(byte[] data) {
    synchronized (writeLock) {
      int remainingBytes = capacity - tail;
      if (data.length < remainingBytes) {

        System.arraycopy(data, 0, buffer, tail, data.length);
        tail += data.length;
      } else {

        System.arraycopy(data, 0, buffer, tail, remainingBytes);
        tail = data.length - remainingBytes;
        System.arraycopy(data, remainingBytes, buffer, 0, tail);
      }
      synchronized (buffer) {
        buffer.notify();
      }
    }
  }

  public byte[] read(int size) throws InterruptedException, IOException {
    require(size);
    byte[] data = new byte[size];
    synchronized (readLock) {
      int remainingBytes = capacity - head;
      if (size < remainingBytes) {

        System.arraycopy(buffer, head, data, 0, size);
        head += size;
      } else {

        System.arraycopy(buffer, head, data, 0, remainingBytes);
        head = size - remainingBytes;
        System.arraycopy(buffer, 0, data, remainingBytes, head);
      }
    }
    return data;
  }

  private void require(long byteCount) throws InterruptedException {
    while (true) {
      if (getSize() >= byteCount) {
        break;
      } else {
        synchronized (buffer) {
          buffer.wait();
        }
      }
    }
  }

  public int getSize() {
    if (tail >= head) return tail - head;
    else return capacity - head + tail;
  }

}
