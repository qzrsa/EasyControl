package com.daitj.easycontrolfork.app.entity;

import android.content.SharedPreferences;

import java.util.UUID;

public final class Setting {
  private final SharedPreferences sharedPreferences;
  private final SharedPreferences.Editor editor;

  public String getLocale() {
    return sharedPreferences.getString("locale", "");
  }

  public void setLocale(String value) {
    editor.putString("locale", value);
    editor.apply();
  }

  public boolean getAutoRotate() {
    return sharedPreferences.getBoolean("autoRotate", true);
  }

  public void setAutoRotate(boolean value) {
    editor.putBoolean("autoRotate", value);
    editor.apply();
  }

  public String getLocalUUID() {
    if (!sharedPreferences.contains("UUID")) {
      editor.putString("UUID", UUID.randomUUID().toString());
      editor.apply();
    }
    return sharedPreferences.getString("UUID", "");
  }
  
  // ========== 连接模式相关设置 ==========
  
  /**
   * 获取默认连接模式
   */
  public int getDefaultConnectMode() {
    return sharedPreferences.getInt("defaultConnectMode", Device.CONNECT_MODE_ADB);
  }
  
  /**
   * 设置默认连接模式
   */
  public void setDefaultConnectMode(int mode) {
    editor.putInt("defaultConnectMode", mode);
    editor.apply();
  }
  
  /**
   * 获取默认中继服务器地址
   */
  public String getDefaultRelayServer() {
    return sharedPreferences.getString("defaultRelayServer", "");
  }
  
  /**
   * 设置默认中继服务器地址
   */
  public void setDefaultRelayServer(String server) {
    editor.putString("defaultRelayServer", server);
    editor.apply();
  }
  
  /**
   * 获取默认中继服务器端口
   */
  public int getDefaultRelayPort() {
    return sharedPreferences.getInt("defaultRelayPort", 25167);
  }
  
  /**
   * 设置默认中继服务器端口
   */
  public void setDefaultRelayPort(int port) {
    editor.putInt("defaultRelayPort", port);
    editor.apply();
  }
  
  /**
   * 获取连接超时时间（毫秒）
   */
  public int getConnectTimeout() {
    return sharedPreferences.getInt("connectTimeout", 15000);
  }
  
  /**
   * 设置连接超时时间（毫秒）
   */
  public void setConnectTimeout(int timeout) {
    editor.putInt("connectTimeout", timeout);
    editor.apply();
  }
  
  /**
   * 是否自动尝试P2P（当中转模式下）
   */
  public boolean getAutoTryP2P() {
    return sharedPreferences.getBoolean("autoTryP2P", true);
  }
  
  /**
   * 设置是否自动尝试P2P
   */
  public void setAutoTryP2P(boolean value) {
    editor.putBoolean("autoTryP2P", value);
    editor.apply();
  }

  public Setting(SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
    this.editor = sharedPreferences.edit();
  }
}
