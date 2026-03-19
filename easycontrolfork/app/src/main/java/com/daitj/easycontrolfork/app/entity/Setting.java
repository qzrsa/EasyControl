package com.daitj.easycontrolfork.app.entity;

import android.content.SharedPreferences;

import java.util.UUID;

public final class Setting {
  private final SharedPreferences sharedPreferences;
  private final SharedPreferences.Editor editor;

  public Setting(SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
    this.editor = sharedPreferences.edit();
  }

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

  public int getDefaultConnMode() {
    return sharedPreferences.getInt("defaultConnMode", Device.CONN_DIRECT);
  }

  public void setDefaultConnMode(int value) {
    editor.putInt("defaultConnMode", value);
    editor.apply();
  }

  public String getDefaultRelayHost() {
    return sharedPreferences.getString("defaultRelayHost", "");
  }

  public void setDefaultRelayHost(String value) {
    editor.putString("defaultRelayHost", value);
    editor.apply();
  }

  public int getDefaultRelayPort() {
    return sharedPreferences.getInt("defaultRelayPort", 11010);
  }

  public void setDefaultRelayPort(int value) {
    editor.putInt("defaultRelayPort", value);
    editor.apply();
  }

  public String getDefaultRelayNetworkName() {
    return sharedPreferences.getString("defaultRelayNetworkName", "");
  }

  public void setDefaultRelayNetworkName(String value) {
    editor.putString("defaultRelayNetworkName", value);
    editor.apply();
  }

  public String getDefaultRelayKey() {
    return sharedPreferences.getString("defaultRelayKey", "");
  }

  public void setDefaultRelayKey(String value) {
    editor.putString("defaultRelayKey", value);
    editor.apply();
  }
}
