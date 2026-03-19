package com.daitj.easycontrolfork.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

import com.daitj.easycontrolfork.app.databinding.ActivityDeviceDetailBinding;
import com.daitj.easycontrolfork.app.databinding.ItemLoadingBinding;
import com.daitj.easycontrolfork.app.databinding.ItemScanAddressListBinding;
import com.daitj.easycontrolfork.app.databinding.ItemTextBinding;
import com.daitj.easycontrolfork.app.entity.AppData;
import com.daitj.easycontrolfork.app.entity.Device;
import com.daitj.easycontrolfork.app.helper.MyBroadcastReceiver;
import com.daitj.easycontrolfork.app.helper.PublicTools;
import com.daitj.easycontrolfork.app.helper.ViewTools;

public class DeviceDetailActivity extends Activity {
  private ActivityDeviceDetailBinding activityDeviceDetailBinding;
  private boolean isNew;
  private Device device;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setLocale(this);
    activityDeviceDetailBinding = ActivityDeviceDetailBinding.inflate(this.getLayoutInflater());
    setContentView(activityDeviceDetailBinding.getRoot());
    // 获取Device
    String uuid = getIntent().getStringExtra("uuid");
    isNew = uuid == null;
    if (isNew) device = new Device(UUID.randomUUID().toString(), Device.TYPE_NETWORK);
    else device = AppData.dbHelper.getByUUID(uuid);
    // 绘制UI
    drawUI();
    // 设置监听
    setListener();
  }

  // 绘制UI
  private static final String[] maxSizeList = new String[]{"2560", "1920", "1600", "1280", "1024", "800"};
  private static final String[] maxFpsList = new String[]{"90", "60", "40", "30", "20", "10"};
  private static final String[] maxVideoBitList = new String[]{"12", "8", "4", "2", "1"};

  private void drawUI() {
    // UUID
    activityDeviceDetailBinding.uuid.setOnClickListener(v -> {
      AppData.clipBoard.setPrimaryClip(ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, device.uuid));
      Toast.makeText(this, getString(R.string.toast_copy), Toast.LENGTH_SHORT).show();
    });
    // 预填写参数
    activityDeviceDetailBinding.name.setText(device.name);
    activityDeviceDetailBinding.address.setText(device.address);
    activityDeviceDetailBinding.startApp.setText(device.startApp);
    activityDeviceDetailBinding.adbPort.setText(String.valueOf(device.adbPort));
    activityDeviceDetailBinding.serverPort.setText(String.valueOf(device.serverPort));
    activityDeviceDetailBinding.customResolution.setVisibility(device.customResolutionOnConnect ? View.VISIBLE : View.GONE);
    activityDeviceDetailBinding.customResolutionWidth.setText(String.valueOf(device.customResolutionWidth));
    activityDeviceDetailBinding.customResolutionHeight.setText(String.valueOf(device.customResolutionHeight));
    
    // ===== 连接模式设置 =====
    addConnectModeSettings();
    
    // 连接时操作
    activityDeviceDetailBinding.layoutOnConnect.setOnClickListener(v -> activityDeviceDetailBinding.layoutOnConnectSub.setVisibility(activityDeviceDetailBinding.layoutOnConnectSub.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    activityDeviceDetailBinding.layoutOnConnectSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_custom_resolution_on_connect), getString(R.string.device_custom_resolution_on_connect_detail), device.customResolutionOnConnect, isChecked -> activityDeviceDetailBinding.customResolution.setVisibility(isChecked ? View.VISIBLE : View.GONE)).getRoot(), 0);
    activityDeviceDetailBinding.layoutOnConnectSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_wake_on_connect), getString(R.string.device_wake_on_connect_detail), device.wakeOnConnect, isChecked -> device.wakeOnConnect = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnConnectSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_light_off_on_connect), getString(R.string.device_light_off_on_connect_detail), device.lightOffOnConnect, isChecked -> device.lightOffOnConnect = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnConnectSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_show_nav_bar_on_connect), getString(R.string.device_show_nav_bar_on_connect_detail), device.showNavBarOnConnect, isChecked -> device.showNavBarOnConnect = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnConnectSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_change_to_full_on_connect), getString(R.string.device_change_to_full_on_connect_detail), device.changeToFullOnConnect, isChecked -> device.changeToFullOnConnect = isChecked).getRoot());
    // 运行时操作
    activityDeviceDetailBinding.layoutOnRunning.setOnClickListener(v -> activityDeviceDetailBinding.layoutOnRunningSub.setVisibility(activityDeviceDetailBinding.layoutOnRunningSub.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    activityDeviceDetailBinding.layoutOnRunningSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_listen_clip_on_running), getString(R.string.device_listen_clip_on_running_detail), device.listenClip, isChecked -> device.listenClip = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnRunningSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_keep_wake_on_running), getString(R.string.device_keep_wake_on_running_detail), device.keepWakeOnRunning, isChecked -> device.keepWakeOnRunning = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnRunningSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_change_resolution_on_running), getString(R.string.device_change_resolution_on_running_detail), device.changeResolutionOnRunning, isChecked -> device.changeResolutionOnRunning = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnRunningSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_small_to_mini_on_running), getString(R.string.device_small_to_mini_on_running_detail), device.smallToMiniOnRunning, isChecked -> device.smallToMiniOnRunning = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnRunningSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_full_to_mini_on_running), getString(R.string.device_full_to_mini_on_running_detail), device.fullToMiniOnRunning, isChecked -> device.fullToMiniOnRunning = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnRunningSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_mini_timeout_on_running), getString(R.string.device_mini_timeout_on_running_detail), device.miniTimeoutOnRunning, isChecked -> device.miniTimeoutOnRunning = isChecked).getRoot());
    // 断开时操作
    activityDeviceDetailBinding.layoutOnClose.setOnClickListener(v -> activityDeviceDetailBinding.layoutOnCloseSub.setVisibility(activityDeviceDetailBinding.layoutOnCloseSub.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    activityDeviceDetailBinding.layoutOnCloseSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_lock_on_close), getString(R.string.device_lock_on_close_detail), device.lockOnClose, isChecked -> device.lockOnClose = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnCloseSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_light_on_close), getString(R.string.device_light_on_close_detail), device.lightOnClose, isChecked -> device.lightOnClose = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOnCloseSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_reconnect_on_close), getString(R.string.device_reconnect_on_close_detail), device.reconnectOnClose, isChecked -> device.reconnectOnClose = isChecked).getRoot());
    // 参数
    activityDeviceDetailBinding.layoutOption.setOnClickListener(v -> activityDeviceDetailBinding.layoutOptionSub.setVisibility(activityDeviceDetailBinding.layoutOptionSub.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    ArrayAdapter<String> maxSizeAdapter = new ArrayAdapter<>(AppData.applicationContext, R.layout.item_spinner_item, maxSizeList);
    ArrayAdapter<String> maxFpsAdapter = new ArrayAdapter<>(AppData.applicationContext, R.layout.item_spinner_item, maxFpsList);
    ArrayAdapter<String> maxVideoBitAdapter = new ArrayAdapter<>(AppData.applicationContext, R.layout.item_spinner_item, maxVideoBitList);
    activityDeviceDetailBinding.layoutOptionSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_is_audio), getString(R.string.device_is_audio_detail), device.isAudio, isChecked -> device.isAudio = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOptionSub.addView(ViewTools.createSpinnerCard(this, getString(R.string.device_max_size), getString(R.string.device_max_size_detail), String.valueOf(device.maxSize), maxSizeAdapter, str -> device.maxSize = Integer.parseInt(str)).getRoot());
    activityDeviceDetailBinding.layoutOptionSub.addView(ViewTools.createSpinnerCard(this, getString(R.string.device_max_fps), getString(R.string.device_max_fps_detail), String.valueOf(device.maxFps), maxFpsAdapter, str -> device.maxFps = Integer.parseInt(str)).getRoot());
    activityDeviceDetailBinding.layoutOptionSub.addView(ViewTools.createSpinnerCard(this, getString(R.string.device_max_video_bit), getString(R.string.device_max_video_bit_detail), String.valueOf(device.maxVideoBit), maxVideoBitAdapter, str -> device.maxVideoBit = Integer.parseInt(str)).getRoot());
    activityDeviceDetailBinding.layoutOptionSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_use_h265), getString(R.string.device_use_h265_detail), device.useH265, isChecked -> device.useH265 = isChecked).getRoot());
    activityDeviceDetailBinding.layoutOptionSub.addView(ViewTools.createSwitchCard(this, getString(R.string.device_connect_on_start), getString(R.string.device_connect_on_start_detail), device.connectOnStart, isChecked -> device.connectOnStart = isChecked).getRoot());
  }
  
  /**
   * 添加连接模式设置
   */
  private ViewGroup relaySettingsLayout;
  
  private void addConnectModeSettings() {
    // 创建连接模式选择卡片
    View modeCard = createConnectModeCard();
    activityDeviceDetailBinding.layoutOptionSub.addView(modeCard, 0);
  }
  
  /**
   * 创建连接模式选择卡片
   */
  private View createConnectModeCard() {
    // 创建容器
    ViewGroup container = (ViewGroup) getLayoutInflater().inflate(R.layout.item_connect_mode, null);
    
    // 标题
    TextView titleView = container.findViewById(R.id.text_title);
    if (titleView != null) {
      titleView.setText("连接模式");
    }
    
    // 说明
    TextView detailView = container.findViewById(R.id.text_detail);
    if (detailView != null) {
      detailView.setText("选择设备连接方式");
    }
    
    // RadioGroup
    RadioGroup radioGroup = container.findViewById(R.id.radio_group_connect_mode);
    if (radioGroup != null) {
      // 添加各个模式选项
      String[] modeNames = Device.CONNECT_MODE_NAMES;
      String[] modeDescriptions = {
        "通过ADB协议连接，支持USB和网络ADB（默认）",
        "直接TCP连接到设备Server端口",
        "通过中继服务器打洞建立直连",
        "所有流量通过中继服务器转发"
      };
      
      for (int i = 0; i < modeNames.length; i++) {
        RadioButton rb = new RadioButton(this);
        rb.setText(modeNames[i] + " - " + modeDescriptions[i]);
        rb.setId(View.generateViewId());
        rb.setTag(i);
        radioGroup.addView(rb);
        
        if (i == device.connectMode) {
          rb.setChecked(true);
        }
      }
      
      radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
        RadioButton rb = group.findViewById(checkedId);
        if (rb != null && rb.getTag() != null) {
          device.connectMode = (int) rb.getTag();
          updateRelaySettingsVisibility();
        }
      });
    }
    
    // 中继设置区域
    relaySettingsLayout = container.findViewById(R.id.layout_relay_settings);
    updateRelaySettingsVisibility();
    
    // 中继服务器地址
    TextView relayServerInput = container.findViewById(R.id.input_relay_server);
    if (relayServerInput != null) {
      relayServerInput.setText(device.relayServer);
    }
    
    // 中继端口
    TextView relayPortInput = container.findViewById(R.id.input_relay_port);
    if (relayPortInput != null) {
      relayPortInput.setText(String.valueOf(device.relayPort));
    }
    
    // 连接令牌
    TextView relayTokenInput = container.findViewById(R.id.input_relay_token);
    if (relayTokenInput != null) {
      relayTokenInput.setText(device.relayToken);
    }
    
    return container;
  }
  
  /**
   * 更新中继设置区域的可见性
   */
  private void updateRelaySettingsVisibility() {
    if (relaySettingsLayout != null) {
      boolean visible = device.needsRelayServer();
      relaySettingsLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  // 设置监听
  private void setListener() {
    activityDeviceDetailBinding.backButton.setOnClickListener(v -> finish());
    activityDeviceDetailBinding.buttonScan.setOnClickListener(v -> scanAddress());
    // 设置确认按钮监听
    activityDeviceDetailBinding.ok.setOnClickListener(v -> {
      String name = String.valueOf(activityDeviceDetailBinding.name.getText());
      String address = String.valueOf(activityDeviceDetailBinding.address.getText());
      if (name.equals("----") || name.equals("") || address.equals("")) {
        Toast.makeText(this, getString(R.string.toast_config), Toast.LENGTH_SHORT).show();
        return;
      }
      device.name = name;
      device.address = device.isLinkDevice() ? device.uuid : address;
      device.startApp = String.valueOf(activityDeviceDetailBinding.startApp.getText());
      device.adbPort = Integer.parseInt(String.valueOf(activityDeviceDetailBinding.adbPort.getText()));
      device.serverPort = Integer.parseInt(String.valueOf(activityDeviceDetailBinding.serverPort.getText()));
      
      // 读取中继设置
      if (relaySettingsLayout != null && relaySettingsLayout.getVisibility() == View.VISIBLE) {
        TextView relayServerInput = relaySettingsLayout.findViewById(R.id.input_relay_server);
        TextView relayPortInput = relaySettingsLayout.findViewById(R.id.input_relay_port);
        TextView relayTokenInput = relaySettingsLayout.findViewById(R.id.input_relay_token);
        
        if (relayServerInput != null) {
          device.relayServer = String.valueOf(relayServerInput.getText());
        }
        if (relayPortInput != null) {
          try {
            device.relayPort = Integer.parseInt(String.valueOf(relayPortInput.getText()));
          } catch (NumberFormatException e) {
            device.relayPort = 25167;
          }
        }
        if (relayTokenInput != null) {
          device.relayToken = String.valueOf(relayTokenInput.getText());
        }
      }
      
      // 自定义分辨率
      String width = String.valueOf(activityDeviceDetailBinding.customResolutionWidth.getText());
      String height = String.valueOf(activityDeviceDetailBinding.customResolutionHeight.getText());
      device.customResolutionOnConnect = false;
      if (activityDeviceDetailBinding.customResolution.getVisibility() != View.GONE && !width.equals("") && !height.equals("") && Integer.parseInt(width) >= 500 && Integer.parseInt(height) >= 500) {
        device.customResolutionOnConnect = true;
        device.customResolutionWidth = Integer.parseInt(width);
        device.customResolutionHeight = Integer.parseInt(height);
      }
      // 更新数据库
      if (isNew) AppData.dbHelper.insert(device);
      else AppData.dbHelper.update(device);
      Intent intent = new Intent();
      intent.setAction(MyBroadcastReceiver.ACTION_UPDATE_DEVICE_LIST);
      sendBroadcast(intent);
      finish();
    });
  }

  // 扫描局域网地址
  private void scanAddress() {
    Pair<ItemLoadingBinding, Dialog> loading = ViewTools.createLoading(this);
    loading.second.show();
    new Thread(() -> {
      ArrayList<String> scannedAddresses = PublicTools.scanAddress();
      loading.second.cancel();
      AppData.uiHandler.post(() -> {
        ItemScanAddressListBinding scanAddressListView = ItemScanAddressListBinding.inflate(LayoutInflater.from(this));
        Dialog dialog = ViewTools.createDialog(this, true, scanAddressListView.getRoot());
        for (String i : scannedAddresses) {
          ItemTextBinding text = ViewTools.createTextCard(this, i, () -> {
            activityDeviceDetailBinding.address.setText(i);
            dialog.cancel();
          });
          scanAddressListView.list.addView(text.getRoot());
        }
        dialog.show();
      });
    }).start();
  }
}
