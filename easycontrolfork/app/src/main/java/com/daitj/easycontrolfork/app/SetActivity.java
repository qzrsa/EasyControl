package com.daitj.easycontrolfork.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.daitj.easycontrolfork.app.databinding.ActivitySetBinding;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.helper.ViewTools;

public class SetActivity extends Activity {
  private ActivitySetBinding activitySetBinding;

  private EditText editEasyTierHost;
  private EditText editEasyTierPort;
  private EditText editEasyTierKey;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setLocale(this);
    activitySetBinding = ActivitySetBinding.inflate(getLayoutInflater());
    setContentView(activitySetBinding.getRoot());

    drawUI();
    setListener();
  }

  private void drawUI() {
    activitySetBinding.backButton.setOnClickListener(v -> finish());

    LinearLayout root = activitySetBinding.setLayout;
    root.removeAllViews();

    ArrayAdapter<String> connModeAdapter = new ArrayAdapter<>(
      AppData.applicationContext,
      R.layout.item_spinner_item,
      new String[]{
        getString(R.string.device_conn_mode_direct),
        getString(R.string.device_conn_mode_auto),
        getString(R.string.device_conn_mode_easytier)
      }
    );

    root.addView(ViewTools.createSpinnerCard(
      this,
      getString(R.string.set_default_conn_mode),
      getString(R.string.set_default_conn_mode_detail),
      getConnModeLabel(AppData.setting.getDefaultConnMode()),
      connModeAdapter,
      str -> {
        if (str.equals(getString(R.string.device_conn_mode_auto))) {
          AppData.setting.setDefaultConnMode(Device.CONN_AUTO);
        } else if (str.equals(getString(R.string.device_conn_mode_easytier))) {
          AppData.setting.setDefaultConnMode(Device.CONN_RELAY);
        } else {
          AppData.setting.setDefaultConnMode(Device.CONN_DIRECT);
        }
      }
    ).getRoot());

    editEasyTierHost = new EditText(this);
    editEasyTierHost.setHint(getString(R.string.device_easytier_host_hint));
    editEasyTierHost.setText(AppData.setting.getDefaultRelayHost());
    editEasyTierHost.setPadding(32, 24, 32, 24);
    root.addView(editEasyTierHost);

    editEasyTierPort = new EditText(this);
    editEasyTierPort.setHint(getString(R.string.device_easytier_port_hint));
    editEasyTierPort.setInputType(InputType.TYPE_CLASS_NUMBER);
    int savedPort = AppData.setting.getDefaultRelayPort();
    editEasyTierPort.setText(savedPort > 0 ? String.valueOf(savedPort) : "11010");
    editEasyTierPort.setPadding(32, 24, 32, 24);
    root.addView(editEasyTierPort);

    editEasyTierKey = new EditText(this);
    editEasyTierKey.setHint(getString(R.string.device_easytier_key_hint));
    editEasyTierKey.setText(AppData.setting.getDefaultRelayKey());
    editEasyTierKey.setPadding(32, 24, 32, 24);
    root.addView(editEasyTierKey);

    root.addView(ViewTools.createTextCard(
      this,
      getString(R.string.set_easytier_note)
    ).getRoot());
  }

  private void setListener() {
    activitySetBinding.ok.setOnClickListener(v -> {
      String host = editEasyTierHost.getText().toString().trim();
      String portText = editEasyTierPort.getText().toString().trim();
      String key = editEasyTierKey.getText().toString().trim();

      int port = 11010;
      if (!portText.isEmpty()) {
        try {
          port = Integer.parseInt(portText);
        } catch (Exception e) {
          Toast.makeText(this, getString(R.string.toast_invalid_port_fallback), Toast.LENGTH_SHORT).show();
          port = 11010;
        }
      }

      AppData.setting.setDefaultRelayHost(host);
      AppData.setting.setDefaultRelayPort(port);
      AppData.setting.setDefaultRelayKey(key);

      Toast.makeText(this, getString(R.string.toast_setting_saved), Toast.LENGTH_SHORT).show();
      finish();
    });
  }

  private String getConnModeLabel(int mode) {
    switch (mode) {
      case Device.CONN_AUTO:
        return getString(R.string.device_conn_mode_auto);
      case Device.CONN_RELAY:
        return getString(R.string.device_conn_mode_easytier);
      default:
        return getString(R.string.device_conn_mode_direct);
    }
  }
}
