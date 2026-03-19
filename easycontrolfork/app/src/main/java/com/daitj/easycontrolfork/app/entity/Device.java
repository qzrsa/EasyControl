package com.daitj.easycontrolfork.app.entity;

import java.util.Objects;

public class Device {
  // 设备类型
  public static final int TYPE_NETWORK = 1;
  public static final int TYPE_LINK = 2;
  
  // 连接模式
  public static final int CONNECT_MODE_ADB = 0;      // ADB模式（默认）
  public static final int CONNECT_MODE_DIRECT = 1;   // 直连模式
  public static final int CONNECT_MODE_P2P = 2;      // P2P模式
  public static final int CONNECT_MODE_RELAY = 3;    // 中转模式
  
  public static final String[] CONNECT_MODE_NAMES = {
    "ADB模式", "直连模式", "P2P模式", "中转模式"
  };
  
  public final String uuid;
  public final int type;
  public String name;
  public String address = "";
  public String startApp = "";
  public int adbPort = 5555;
  public int serverPort = 25166;
  
  // 连接模式
  public int connectMode = CONNECT_MODE_ADB;
  
  // 中继服务器配置
  public String relayServer = "";      // 中继服务器地址
  public int relayPort = 25167;        // 中继服务器端口
  public String relayToken = "";       // 连接令牌（P2P/中转用）
  
  public boolean listenClip=true;
  public boolean isAudio = true;
  public int maxSize = 1600;
  public int maxFps = 60;
  public int maxVideoBit = 4;
  public boolean useH265 = true;
  public boolean connectOnStart = false;
  public boolean customResolutionOnConnect = false;
  public boolean wakeOnConnect = true;
  public boolean lightOffOnConnect = false;
  public boolean showNavBarOnConnect = true;
  public boolean changeToFullOnConnect = false;
  public boolean keepWakeOnRunning = true;
  public boolean changeResolutionOnRunning = false;
  public boolean smallToMiniOnRunning = false;
  public boolean fullToMiniOnRunning = true;
  public boolean miniTimeoutOnRunning = false;
  public boolean lockOnClose = true;
  public boolean lightOnClose = false;
  public boolean reconnectOnClose = false;
  public int customResolutionWidth = 1080;
  public int customResolutionHeight = 2400;
  public int smallX = 200;
  public int smallY = 200;
  public int smallLength = 800;
  public int smallXLan = 200;
  public int smallYLan = 200;
  public int smallLengthLan = 800;
  public int miniY = 200;

  public Device(String uuid, int type) {
    this.uuid = uuid;
    this.type = type;
    this.name = uuid;
  }

  public boolean isNetworkDevice() {
    return type == TYPE_NETWORK;
  }

  public boolean isLinkDevice() {
    return type == TYPE_LINK;
  }

  public boolean isTempDevice() {
    return Objects.equals(name, "----");
  }
  
  /**
   * 获取连接模式名称
   */
  public String getConnectModeName() {
    if (connectMode >= 0 && connectMode < CONNECT_MODE_NAMES.length) {
      return CONNECT_MODE_NAMES[connectMode];
    }
    return CONNECT_MODE_NAMES[CONNECT_MODE_ADB];
  }
  
  /**
   * 是否需要中继服务器
   */
  public boolean needsRelayServer() {
    return connectMode == CONNECT_MODE_P2P || connectMode == CONNECT_MODE_RELAY;
  }
  
  /**
   * 是否使用直接TCP连接（直连/P2P/中转都走TCP）
   */
  public boolean useDirectTcp() {
    return connectMode != CONNECT_MODE_ADB;
  }

  public Device clone(String uuid) {
    Device newDevice = new Device(uuid, type);
    newDevice.name = name;
    newDevice.address = address;
    newDevice.startApp = startApp;
    newDevice.adbPort = adbPort;
    newDevice.serverPort = serverPort;
    newDevice.connectMode = connectMode;
    newDevice.relayServer = relayServer;
    newDevice.relayPort = relayPort;
    newDevice.relayToken = relayToken;
    newDevice.listenClip = listenClip;
    newDevice.isAudio = isAudio;
    newDevice.maxSize = maxSize;
    newDevice.maxFps = maxFps;
    newDevice.maxVideoBit = maxVideoBit;
    newDevice.useH265 = useH265;
    newDevice.connectOnStart = connectOnStart;
    newDevice.customResolutionOnConnect = customResolutionOnConnect;
    newDevice.wakeOnConnect = wakeOnConnect;
    newDevice.lightOffOnConnect = lightOffOnConnect;
    newDevice.showNavBarOnConnect = showNavBarOnConnect;
    newDevice.changeToFullOnConnect = changeToFullOnConnect;
    newDevice.keepWakeOnRunning = keepWakeOnRunning;
    newDevice.changeResolutionOnRunning = changeResolutionOnRunning;
    newDevice.smallToMiniOnRunning = smallToMiniOnRunning;
    newDevice.fullToMiniOnRunning = fullToMiniOnRunning;
    newDevice.miniTimeoutOnRunning = miniTimeoutOnRunning;
    newDevice.lockOnClose = lockOnClose;
    newDevice.lightOnClose = lightOnClose;
    newDevice.reconnectOnClose = reconnectOnClose;

    newDevice.customResolutionWidth = customResolutionWidth;
    newDevice.customResolutionHeight = customResolutionHeight;
    newDevice.smallX = smallX;
    newDevice.smallY = smallY;
    newDevice.smallLength = smallLength;
    newDevice.smallXLan = smallXLan;
    newDevice.smallYLan = smallYLan;
    newDevice.smallLengthLan = smallLengthLan;
    newDevice.miniY = miniY;
    return newDevice;
  }
}
