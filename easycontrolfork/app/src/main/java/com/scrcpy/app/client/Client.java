package com.scrcpy.app.client;

import android.app.Dialog;
import android.util.Pair;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Objects;

import com.scrcpy.app.client.tools.AdbTools;
import com.scrcpy.app.client.tools.ClientController;
import com.scrcpy.app.client.tools.ClientPlayer;
import com.scrcpy.app.client.tools.ClientStream;
import com.scrcpy.app.client.tools.ControlPacket;
import com.scrcpy.app.databinding.ItemLoadingBinding;
import com.scrcpy.app.entity.AppData;
import com.scrcpy.app.entity.Device;
import com.scrcpy.app.helper.Logger;
import com.scrcpy.app.helper.PublicTools;
import com.scrcpy.app.helper.ViewTools;

public class Client {
  private static final String TAG = "Client";
  
  private static final HashMap<String, Client> allClient = new HashMap<>();
  private boolean isClosed = false;

  private ClientStream clientStream = null;
  private ClientController clientController = null;
  private ClientPlayer clientPlayer = null;
  private Device device;

  public Client(Device device) {
    if (allClient.containsKey(device.uuid)) {
      Logger.w(TAG, "Client already exists for device: " + device.uuid);
      return;
    }
    
    this.device = device;
    Logger.i(TAG, "Creating Client for device: " + device.name + " (UUID: " + device.uuid + ")");
    
    Pair<ItemLoadingBinding, Dialog> loading = ViewTools.createLoading(AppData.mainActivity);
    loading.second.show();

    clientStream = new ClientStream(device, bool -> {
      if (bool) {
        Logger.i(TAG, "Connection successful, initializing controller and player");
        allClient.put(device.uuid, this);

        clientController = new ClientController(device, clientStream, () -> clientPlayer = new ClientPlayer(device.uuid, clientStream));

        boolean isTempDevice = device.isTempDevice();

        clientController.handleAction(device.changeToFullOnConnect ? "changeToFull" : "changeToSmall", null, 0);
        Logger.d(TAG, "View mode: " + (device.changeToFullOnConnect ? "Full" : "Small"));

        if (device.customResolutionOnConnect) {
          clientController.handleAction("writeByteBuffer", ControlPacket.createChangeResolutionEvent(device.customResolutionWidth, device.customResolutionHeight), 0);
          Logger.d(TAG, "Custom resolution: " + device.customResolutionWidth + "x" + device.customResolutionHeight);
        }
        if (!isTempDevice && device.wakeOnConnect) {
          clientController.handleAction("buttonWake", null, 0);
          Logger.d(TAG, "Sending wake command");
        }
        if (!isTempDevice && device.lightOffOnConnect) {
          clientController.handleAction("buttonLightOff", null, 2000);
          Logger.d(TAG, "Sending light off command");
        }
        
        Logger.operation(TAG, "Client creation", true);
      } else {
        Logger.e(TAG, "Connection failed for device: " + device.name);
      }
      if (loading.second.isShowing()) loading.second.cancel();
    });
  }

  public static void startDevice(Device device) {
    if (device == null) {
      Logger.w(TAG, "startDevice called with null device");
      return;
    }
    Logger.i(TAG, "Starting device: " + device.name);
    new Client(device);
  }

  public static Device getDevice(String uuid) {
    Client client = allClient.get(uuid);
    if (client == null) return null;
    return client.device;
  }

  public static ClientController getClientController(String uuid) {
    Client client = allClient.get(uuid);
    if (client == null) return null;
    return client.clientController;
  }

  public static void sendAction(String uuid, String action, ByteBuffer byteBuffer, int delay) {
    if (action == null || uuid == null) return;
    
    Logger.d(TAG, "sendAction - uuid: " + uuid + ", action: " + action + ", delay: " + delay);
    
    if (action.equals("start")) {
      for (Device device : AdbTools.devicesList) if (Objects.equals(device.uuid, uuid)) startDevice(device);
    } else {
      Client client = allClient.get(uuid);
      if (client == null) {
        Logger.w(TAG, "Client not found for uuid: " + uuid);
        return;
      }
      if (action.equals("close")) {
        client.close(byteBuffer);
      } else {
        if (client.clientController == null) return;
        client.clientController.handleAction(action, byteBuffer, delay);
      }
    }
  }

  private void close(ByteBuffer byteBuffer) {
    if (isClosed) return;
    isClosed = true;

    Logger.i(TAG, "Closing client for device: " + device.name);

    boolean isTempDevice = device.isTempDevice();

    if (!isTempDevice) AppData.dbHelper.update(device);
    allClient.remove(device.uuid);

    if (!isTempDevice && device.lockOnClose) {
      clientController.handleAction("buttonLock", null, 0);
      Logger.d(TAG, "Sending lock command on close");
    }
    else if (!isTempDevice && device.lightOnClose) {
      clientController.handleAction("buttonLight", null, 0);
      Logger.d(TAG, "Sending light on command on close");
    }

    if (clientPlayer != null) clientPlayer.close();
    if (clientController != null) clientController.close();
    if (clientStream != null) clientStream.close();

    if (byteBuffer != null) {
      String message = new String(byteBuffer.array());
      Logger.i(TAG, "Close message: " + message);
      PublicTools.logToast("Client", message, true);
      if (device.reconnectOnClose) {
        Logger.i(TAG, "Auto-reconnecting...");
        startDevice(device);
      }
    }
    
    Logger.operation(TAG, "Client close", true);
  }

}
