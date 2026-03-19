package com.daitj.easycontrolfork.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
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
        "直接连接",
        "自动切换：先直连，失败后走 EasyTier 虚拟组网",
        "强制 EasyTier 虚拟组网"
      }
    );

    root.addView(ViewTools.createSpinnerCard(
      this,
      "默认连接模式",
      "用于未单独指定连接方式的设备。EasyTier 是去中心化虚拟组网，不是传统固定服务器中转。",
      getConnModeLabel(AppData.setting.getDefaultConnMode()),
      connModeAdapter,
      str -> {
        if (str.startsWith("自动")) {
          AppData.setting.setDefaultConnMode(Device.CONN_AUTO);
        } else if (str.startsWith("强制")) {
          AppData.setting.setDefaultConnMode(Device.CONN_RELAY);
        } else {
          AppData.setting.setDefaultConnMode(Device.CONN_DIRECT);
        }
      }
    ).getRoot());

    editEasyTierHost = new EditText(this);
    editEasyTierHost.setHint("EasyTier 节点地址");
    editEasyTierHost.setText(AppData.setting.getDefaultRelayHost());
    editEasyTierHost.setPadding(32, 24, 32, 24);
    root.addView(editEasyTierHost);

    editEasyTierPort = new EditText(this);
    editEasyTierPort.setHint("EasyTier 端口");
    editEasyTierPort.setInputType(InputType.TYPE_CLASS_NUMBER);
    int savedPort = AppData.setting.getDefaultRelayPort();
    editEasyTierPort.setText(savedPort > 0 ? String.valueOf(savedPort) : "11010");
    editEasyTierPort.setPadding(32, 24, 32, 24);
    root.addView(editEasyTierPort);

    editEasyTierKey = new EditText(this);
    editEasyTierKey.setHint("EasyTier 网络密钥");
    editEasyTierKey.setText(AppData.setting.getDefaultRelayKey());
    editEasyTierKey.setPadding(32, 24, 32, 24);
    root.addView(editEasyTierKey);

    root.addView(ViewTools.createTextCard(
      this,
      "说明：当前版本这里只是为 EasyTier 接入预留配置项。现阶段语义已切换，但应用内还没有真正内置或拉起 EasyTier 节点能力。"
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
          Toast.makeText(this, "端口格式错误，已使用默认值 11010", Toast.LENGTH_SHORT).show();
          port = 11010;
        }
      }

      AppData.setting.setDefaultRelayHost(host);
      AppData.setting.setDefaultRelayPort(port);
      AppData.setting.setDefaultRelayKey(key);

      Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
      finish();
    });
  }

  private String getConnModeLabel(int mode) {
    switch (mode) {
      case Device.CONN_AUTO:
        return "自动切换：先直连，失败后走 EasyTier 虚拟组网";
      case Device.CONN_RELAY:
        return "强制 EasyTier 虚拟组网";
      default:
        return "直接连接";
    }
  }
}
