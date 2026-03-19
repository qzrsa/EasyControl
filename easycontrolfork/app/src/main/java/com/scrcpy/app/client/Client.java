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
import com.scrcpy.app.helper.PublicTools;
import com.scrcpy.app.helper.ViewTools;

public class Client {
  private static final HashMap<String, Client> allClient = new HashMap<>();
  private boolean isClosed = false;

  private ClientStream clientStream = null;
  private ClientController clientController = null;
  private ClientPlayer clientPlayer = null;
  private Device device;

  public Client(Device device) {
    if (allClient.containsKey(device.uuid)) return;
    this.device = device;
    Pair<ItemLoadingBinding, Dialog> loading = ViewTools.createLoading(AppData.mainActivity);
    loading.second.show();

    clientStream = new ClientStream(device, bool -> {
      if (bool) {
        allClient.put(device.uuid, this);

        clientController = new ClientController(device, clientStream, () -> clientPlayer = new ClientPlayer(device.uuid, clientStream));

        boolean isTempDevice = device.isTempDevice();

        clientController.handleAction(device.changeToFullOnConnect ? "changeToFull" : "changeToSmall", null, 0);

        if (device.customResolutionOnConnect) clientController.handleAction("writeByteBuffer", ControlPacket.createChangeResolutionEvent(device.customResolutionWidth, device.customResolutionHeight), 0);
        if (!isTempDevice && device.wakeOnConnect) clientController.handleAction("buttonWake", null, 0);
        if (!isTempDevice && device.lightOffOnConnect) clientController.handleAction("buttonLightOff", null, 2000);
      }
      if (loading.second.isShowing()) loading.second.cancel();
    });
  }

  public static void startDevice(Device device) {
    if (device == null) return;
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
    if (action.equals("start")) {
      for (Device device : AdbTools.devicesList) if (Objects.equals(device.uuid, uuid)) startDevice(device);
    } else {
      Client client = allClient.get(uuid);
      if (client == null) return;
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

    boolean isTempDevice = device.isTempDevice();

    if (!isTempDevice) AppData.dbHelper.update(device);
    allClient.remove(device.uuid);

    if (!isTempDevice && device.lockOnClose) clientController.handleAction("buttonLock", null, 0);
    else if (!isTempDevice && device.lightOnClose) clientController.handleAction("buttonLight", null, 0);

    if (clientPlayer != null) clientPlayer.close();
    if (clientController != null) clientController.close();
    if (clientStream != null) clientStream.close();

    if (byteBuffer != null) {
      PublicTools.logToast("Client", new String(byteBuffer.array()), true);
      if (device.reconnectOnClose) startDevice(device);
    }
  }

}
