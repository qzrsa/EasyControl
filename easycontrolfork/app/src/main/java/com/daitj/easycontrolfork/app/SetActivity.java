package com.daitj.easycontrolfork.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.daitj.easycontrolfork.app.databinding.ActivitySetBinding;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.helper.PublicTools;
import com.daitj.easycontrolfork.app.helper.ViewTools;

public class SetActivity extends Activity {
  private ActivitySetBinding activitySetBinding;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    activitySetBinding = ActivitySetBinding.inflate(this.getLayoutInflater());
    setContentView(activitySetBinding.getRoot());
    drawUi();
    setButtonListener();
  }

  private void drawUi() {
    // 原有设置项
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, getString(R.string.set_other_ip), () -> startActivity(new Intent(this, IpActivity.class))).getRoot());
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, getString(R.string.set_other_custom_key), () -> startActivity(new Intent(this, AdbKeyActivity.class))).getRoot());
    activitySetBinding.setOther.addView(ViewTools.createTextCard(this, getString(R.string.set_other_reset_key), () -> {
      AppData.keyPair = PublicTools.reGenerateAdbKeyPair();
      Toast.makeText(this, getString(R.string.toast_success), Toast.LENGTH_SHORT).show();
    }).getRoot());

    // ===== 新增：连接模式与服务器设置 =====

    // 连接模式选择
    activitySetBinding.setOther.addView(
      ViewTools.createSpinnerCard(this, "默认连接模式", "选择所有设备默认使用的连接方式",
        getModeLabel(AppData.setting.getDefaultConnMode()),
        new android.widget.ArrayAdapter<>(AppData.applicationContext, R.layout.item_spinner_item,
          new String[]{"直接连接", "自动切换：先直连，失败后走服务器", "强制服务器中转"}),
        str -> {
          int mode = Device.CONN_DIRECT;
          if (str.startsWith("自动")) mode = Device.CONN_AUTO;
          else if (str.startsWith("强制")) mode = Device.CONN_RELAY;
          AppData.setting.setDefaultConnMode(mode);
          updateRelayConfigVisibility(mode);
        }).getRoot()
    );

    // 服务器配置区域（用 LinearLayout 包裹，方便整体显隐）
    LinearLayout relayLayout = new LinearLayout(this);
    relayLayout.setId(View.generateViewId());
    relayLayout.setOrientation(LinearLayout.VERTICAL);
    int mode = AppData.setting.getDefaultConnMode();
    relayLayout.setVisibility(mode == Device.CONN_DIRECT ? View.GONE : View.VISIBLE);

    // 服务器地址
    EditText editRelayHost = new EditText(this);
    editRelayHost.setHint("服务器地址（如 example.com 或 1.2.3.4）");
    editRelayHost.setText(AppData.setting.getDefaultRelayHost());
    editRelayHost.setPadding(32, 16, 32, 16);

    // 服务器端口
    EditText editRelayPort = new EditText(this);
    editRelayPort.setHint("服务器端口（如 25167）");
    editRelayPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    int savedPort = AppData.setting.getDefaultRelayPort();
    editRelayPort.setText(savedPort > 0 ? String.valueOf(savedPort) : "");
    editRelayPort.setPadding(32, 16, 32, 16);

    // 服务器密钥
    EditText editRelayKey = new EditText(this);
    editRelayKey.setHint("服务器密钥（可为空）");
    editRelayKey.setText(AppData.setting.getDefaultRelayKey());
    editRelayKey.setPadding(32, 16, 32, 16);

    relayLayout.addView(editRelayHost);
    relayLayout.addView(editRelayPort);
    relayLayout.addView(editRelayKey);

    // 保存服务器配置按钮
    relayLayout.addView(ViewTools.createTextCard(this, "保存服务器配置", () -> {
      String host = editRelayHost.getText().toString().trim();
      String portStr = editRelayPort.getText().toString().trim();
      String key = editRelayKey.getText().toString().trim();
      if (host.isEmpty()) {
        Toast.makeText(this, "请填写服务器地址", Toast.LENGTH_SHORT).show();
        return;
      }
      int port = 25167;
      try { port = Integer.parseInt(portStr); } catch (Exception ignored) {}
      AppData.setting.setDefaultRelayHost(host);
      AppData.setting.setDefaultRelayPort(port);
      AppData.setting.setDefaultRelayKey(key);
      Toast.makeText(this, "服务器配置已保存", Toast.LENGTH_SHORT).show();
    }).getRoot());

    // 重置密钥
    relayLayout.addView(ViewTools.createTextCard(this, "重置服务器密钥", () -> {
      String newKey = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
      editRelayKey.setText(newKey);
      AppData.setting.setDefaultRelayKey(newKey);
      Toast.makeText(this, "密钥已重置：" + newKey, Toast.LENGTH_LONG).show();
    }).getRoot());

    activitySetBinding.setOther.addView(relayLayout);

    // 保存 relayLayout 引用供模式切换时使用
    activitySetBinding.getRoot().setTag(relayLayout);
  }

  private String getModeLabel(int mode) {
    switch (mode) {
      case Device.CONN_AUTO: return "自动切换：先直连，失败后走服务器";
      case Device.CONN_RELAY: return "强制服务器中转";
      default: return "直接连接";
    }
  }

  private void updateRelayConfigVisibility(int mode) {
    Object tag = activitySetBinding.getRoot().getTag();
    if (tag instanceof LinearLayout) {
      ((LinearLayout) tag).setVisibility(mode == Device.CONN_DIRECT ? View.GONE : View.VISIBLE);
    }
  }

  private void setButtonListener() {
    activitySetBinding.backButton.setOnClickListener(v -> finish());
  }
}
