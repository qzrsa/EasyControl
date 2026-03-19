package com.scrcpy.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;

import com.scrcpy.app.client.Client;
import com.scrcpy.app.client.tools.AdbTools;
import com.scrcpy.app.databinding.ActivityMainBinding;
import com.scrcpy.app.entity.AppData;
import com.scrcpy.app.entity.Device;
import com.scrcpy.app.helper.DeviceListAdapter;
import com.scrcpy.app.helper.MyBroadcastReceiver;
import com.scrcpy.app.helper.ViewTools;

public class MainActivity extends Activity {

  private ActivityMainBinding activityMainBinding;
  public DeviceListAdapter deviceListAdapter;

  private final MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppData.init(this);
    ViewTools.setStatusAndNavBar(this);
    ViewTools.setLocale(this);
    activityMainBinding = ActivityMainBinding.inflate(this.getLayoutInflater());
    setContentView(activityMainBinding.getRoot());

    deviceListAdapter = new DeviceListAdapter(this);
    activityMainBinding.devicesList.setAdapter(deviceListAdapter);
    myBroadcastReceiver.setDeviceListAdapter(deviceListAdapter);

    setButtonListener();

    myBroadcastReceiver.register(this);

    myBroadcastReceiver.resetUSB();

    AppData.uiHandler.postDelayed(() -> {
      for (Device device : AdbTools.devicesList) if (device.connectOnStart) Client.startDevice(device);
    }, 2000);
  }

  @Override
  protected void onDestroy() {
    myBroadcastReceiver.unRegister(this);
    super.onDestroy();
  }

  private void setButtonListener() {
    activityMainBinding.buttonAdd.setOnClickListener(v -> startActivity(new Intent(this, DeviceDetailActivity.class)));
    activityMainBinding.buttonSet.setOnClickListener(v -> startActivity(new Intent(this, SetActivity.class)));
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == RESULT_OK && requestCode == 1) {
      Uri uri = data.getData();
      if (uri == null) deviceListAdapter.pushFile(null, null);
      ;
      try {
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
        String fileName = "easycontrolfork_push_file";
        if (documentFile != null && documentFile.getName() != null) {
          fileName = documentFile.getName();
        }
        InputStream inputStream = getContentResolver().openInputStream(uri);
        deviceListAdapter.pushFile(inputStream, fileName);
      } catch (IOException ignored) {
        deviceListAdapter.pushFile(null, null);
        ;
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

}