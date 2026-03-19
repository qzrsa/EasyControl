package com.daitj.easycontrolfork.app.entity;

import java.util.Objects;

public class Device {
  public static final int TYPE_NETWORK = 1;
  public static final int TYPE_LINK = 2;

  // 连接模式
  public static final int CONN_DIRECT = 0;   // 直接连接（默认）
  public static final int CONN_AUTO = 1;     // 先直连，失败后走 EasyTier 虚拟组网
  public static final int CONN_RELAY = 2;    // 强制 EasyTier 虚拟组网（暂保留常量名，避免大范围改名）

  public final String uuid;
  public final int type;
  public String name;
  public String address = "";
  public String startApp = "";
  public int adbPort = 5555;
  public int serverPort = 25166;
  public boolean listenClip = true;
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

  // EasyTier 配置
  // 当前阶段先沿用 relay 字段名，以兼容数据库字段、旧调用链和设置项。
  // 语义上已切换为 EasyTier 虚拟组网配置。
  public int connMode = CONN_DIRECT;
  public boolean useGlobalRelay = true; // 是否使用全局 EasyTier 配置
  public String relayHost = "";         // EasyTier 节点地址
  public int relayPort = 11010;         // EasyTier 默认端口
  public String relayKey = "";          // EasyTier 网络密钥/网络标识占位

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

  public Device clone(String uuid) {
    Device newDevice = new Device(uuid, type);
    newDevice.name = name;
    newDevice.address = address;
    newDevice.startApp = startApp;
    newDevice.adbPort = adbPort;
    newDevice.serverPort = serverPort;
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

    newDevice.connMode = connMode;
    newDevice.useGlobalRelay = useGlobalRelay;
    newDevice.relayHost = relayHost;
    newDevice.relayPort = relayPort;
    newDevice.relayKey = relayKey;
    return newDevice;
  }
}
