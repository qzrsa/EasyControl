    // ===== 新增：连接模式与服务器配置 =====
    activityDeviceDetailBinding.layoutOptionSub.addView(
      ViewTools.createSwitchCard(this, "使用全局默认服务器", "开启后使用全局设置中的连接模式和服务器配置",
        device.useGlobalRelay, isChecked -> {
          device.useGlobalRelay = isChecked;
          updateDeviceRelayVisibility(isChecked);
        }).getRoot()
    );

    // 连接模式
    ArrayAdapter<String> connModeAdapter = new ArrayAdapter<>(AppData.applicationContext, R.layout.item_spinner_item,
      new String[]{"直接连接", "自动切换：先直连，失败后走服务器", "强制服务器中转"});
    activityDeviceDetailBinding.layoutOptionSub.addView(
      ViewTools.createSpinnerCard(this, "连接模式", "为该设备单独选择连接方式",
        getConnModeLabel(device.connMode), connModeAdapter, str -> {
          if (str.startsWith("自动")) device.connMode = Device.CONN_AUTO;
          else if (str.startsWith("强制")) device.connMode = Device.CONN_RELAY;
          else device.connMode = Device.CONN_DIRECT;
        }).getRoot()
    );

    // 服务器地址
    android.widget.EditText editDeviceRelayHost = new android.widget.EditText(this);
    editDeviceRelayHost.setHint("服务器地址");
    editDeviceRelayHost.setText(device.relayHost);
    editDeviceRelayHost.setPadding(32, 16, 32, 16);
    activityDeviceDetailBinding.layoutOptionSub.addView(editDeviceRelayHost);

    // 服务器端口
    android.widget.EditText editDeviceRelayPort = new android.widget.EditText(this);
    editDeviceRelayPort.setHint("服务器端口");
    editDeviceRelayPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    editDeviceRelayPort.setText(device.relayPort > 0 ? String.valueOf(device.relayPort) : "");
    editDeviceRelayPort.setPadding(32, 16, 32, 16);
    activityDeviceDetailBinding.layoutOptionSub.addView(editDeviceRelayPort);

    // 服务器密钥
    android.widget.EditText editDeviceRelayKey = new android.widget.EditText(this);
    editDeviceRelayKey.setHint("服务器密钥");
    editDeviceRelayKey.setText(device.relayKey);
    editDeviceRelayKey.setPadding(32, 16, 32, 16);
    activityDeviceDetailBinding.layoutOptionSub.addView(editDeviceRelayKey);
