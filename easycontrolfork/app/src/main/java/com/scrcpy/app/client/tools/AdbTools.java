package com.scrcpy.app.client.tools;

import android.hardware.usb.UsbDevice;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

import com.scrcpy.app.adb.Adb;
import com.scrcpy.app.entity.AppData;
import com.scrcpy.app.entity.Device;
import com.scrcpy.app.entity.MyInterface;
import com.scrcpy.app.helper.PublicTools;

public class AdbTools {
  private static final HashMap<String, Adb> allAdbConnect = new HashMap<>();
  public static final ArrayList<Device> devicesList = new ArrayList<>();
  public static final HashMap<String, UsbDevice> usbDevicesList = new HashMap<>();

  public static Adb connectADB(Device device) throws Exception {
    String addressId = device.isLinkDevice() ? device.address : device.address + ":" + device.adbPort;
    Adb adb = allAdbConnect.get(addressId);
    if (adb == null || adb.isClosed()) {
      if (device.isLinkDevice()) adb = new Adb(usbDevicesList.get(addressId), AppData.keyPair);
      else adb = new Adb(PublicTools.getIp(device.address), device.adbPort, AppData.keyPair);
      allAdbConnect.put(addressId, adb);
    }
    return adb;
  }

  public static void runOnceCmd(Device device, String cmd, MyInterface.MyFunctionBoolean handle) {
    new Thread(() -> {
      try {
        Adb adb = connectADB(device);
        adb.runAdbCmd(cmd);
        handle.run(true);
      } catch (Exception ignored) {
        handle.run(false);
      }
    }).start();
  }

  public static void restartOnTcpip(Device device, MyInterface.MyFunctionBoolean handle) {
    new Thread(() -> {
      try {
        Adb adb = connectADB(device);
        String output = adb.restartOnTcpip(5555);
        handle.run(output.contains("restarting"));
      } catch (Exception ignored) {
        handle.run(false);
      }
    }).start();
  }

  public static void pushFile(Device device, InputStream file, String fileName, MyInterface.MyFunctionInt handleProcess) {
    new Thread(() -> {
      try {
        String tempFileName = fileName;
        Adb adb = connectADB(device);

        if (!Pattern.compile("^[a-zA-Z0-9\\(\\)\\-\\_\\[\\]\\.]+$").matcher(tempFileName).matches()) {
          int dotIndex = tempFileName.lastIndexOf(".");
          tempFileName = UUID.randomUUID() + (dotIndex == -1 ? "" : tempFileName.substring(dotIndex));
        }
        adb.pushFile(file, "/sdcard/Download/Easycontrol/" + tempFileName, handleProcess);
      } catch (Exception ignored) {
        handleProcess.run(-1);
      }
    }).start();
  }
}
