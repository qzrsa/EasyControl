package com.daitj.easycontrolfork.app.client.tools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import com.daitj.easycontrolfork.app.entity.Device;

/**
 * 中继/P2P 客户端
 * 支持：
 * 1. P2P 打洞连接
 * 2. 中转服务器转发
 */
public class RelayClient {
    private static final String TAG = "RelayClient";
    
    // 协议命令
    private static final byte CMD_REGISTER = 1;      // 注册设备
    private static final byte CMD_CONNECT = 2;       // 请求连接
    private static final byte CMD_PUNCH = 3;         // 打洞请求
    private static final byte CMD_RELAY = 4;         // 中转模式
    private static final byte CMD_HEARTBEAT = 5;     // 心跳
    
    // 响应状态
    private static final byte STATUS_OK = 0;
    private static final byte STATUS_ERROR = 1;
    private static final byte STATUS_PENDING = 2;
    
    private final Device device;
    private Socket controlSocket;
    private DataOutputStream controlOut;
    private DataInputStream controlIn;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private Thread heartbeatThread;
    
    // P2P 打洞结果
    private String p2pHost;
    private int p2pPort;
    
    public RelayClient(Device device) {
        this.device = device;
    }
    
    /**
     * 连接到中继服务器
     */
    public boolean connectToRelay() throws IOException {
        if (device.relayServer == null || device.relayServer.isEmpty()) {
            throw new IOException("中继服务器地址未配置");
        }
        
        controlSocket = new Socket();
        controlSocket.connect(new InetSocketAddress(device.relayServer, device.relayPort), 5000);
        controlOut = new DataOutputStream(controlSocket.getOutputStream());
        controlIn = new DataInputStream(controlSocket.getInputStream());
        
        // 发送注册请求
        controlOut.writeByte(CMD_REGISTER);
        writeString(device.relayToken);
        controlOut.flush();
        
        byte status = controlIn.readByte();
        if (status != STATUS_OK) {
            controlSocket.close();
            throw new IOException("中继服务器注册失败");
        }
        
        isConnected.set(true);
        startHeartbeat();
        return true;
    }
    
    /**
     * 请求 P2P 打洞
     * @return 打洞成功返回 true
     */
    public boolean requestP2P() throws IOException {
        if (!isConnected.get()) {
            throw new IOException("未连接到中继服务器");
        }
        
        controlOut.writeByte(CMD_PUNCH);
        writeString(device.address);  // 目标设备标识
        controlOut.flush();
        
        byte status = controlIn.readByte();
        if (status == STATUS_OK) {
            // 读取打洞得到的公网地址
            p2pHost = readString();
            p2pPort = controlIn.readInt();
            return true;
        } else if (status == STATUS_PENDING) {
            // 等待对方响应
            // 在实际实现中需要异步处理
            return false;
        }
        return false;
    }
    
    /**
     * 请求中转模式
     * @return 中转服务器分配的端口
     */
    public int requestRelay() throws IOException {
        if (!isConnected.get()) {
            throw new IOException("未连接到中继服务器");
        }
        
        controlOut.writeByte(CMD_RELAY);
        writeString(device.address);  // 目标设备标识
        controlOut.flush();
        
        byte status = controlIn.readByte();
        if (status == STATUS_OK) {
            return controlIn.readInt();  // 返回中转端口
        }
        throw new IOException("中转模式请求失败");
    }
    
    /**
     * 建立 P2P 连接
     */
    public Socket createP2PSocket() throws IOException {
        if (p2pHost == null || p2pPort <= 0) {
            throw new IOException("P2P 打洞未成功");
        }
        
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(p2pHost, p2pPort), 10000);
        return socket;
    }
    
    /**
     * 建立中转连接
     */
    public Socket createRelaySocket(int relayPort) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(device.relayServer, relayPort), 10000);
        return socket;
    }
    
    /**
     * 启动心跳线程
     */
    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (isConnected.get() && !Thread.interrupted()) {
                try {
                    Thread.sleep(30000);  // 30秒心跳
                    synchronized (controlOut) {
                        controlOut.writeByte(CMD_HEARTBEAT);
                        controlOut.flush();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    isConnected.set(false);
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        isConnected.set(false);
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}
    }
    
    public boolean isConnected() {
        return isConnected.get();
    }
    
    public String getP2PHost() {
        return p2pHost;
    }
    
    public int getP2PPort() {
        return p2pPort;
    }
    
    // 辅助方法
    private void writeString(String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        controlOut.writeInt(bytes.length);
        controlOut.write(bytes);
    }
    
    private String readString() throws IOException {
        int len = controlIn.readInt();
        byte[] bytes = new byte[len];
        controlIn.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
}
