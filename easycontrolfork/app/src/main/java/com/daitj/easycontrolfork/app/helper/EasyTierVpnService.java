package com.daitj.easycontrolfork.app.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.daitj.easycontrolfork.app.R;

public class EasyTierVpnService extends VpnService {

  private static final String TAG = "EasyTierVpnService";
  public static final String ACTION_STOP = "com.daitj.easycontrolfork.STOP_EASYTIER";
  public static final String EXTRA_HOST = "extra_host";
  public static final String EXTRA_PORT = "extra_port"; // 兼容旧调用，当前已不再使用
  public static final String EXTRA_KEY = "extra_key";
  public static final String EXTRA_NETWORK_NAME = "extra_network_name";
  private static final String CHANNEL_ID = "easytier_vpn";
  private static final int NOTIF_ID = 1001;

  private static volatile EasyTierManager.VirtualIpCallback sCallback;

  private ParcelFileDescriptor tunFd;
  private Process easyTierProcess;
  private Thread monitorThread;

  public static void setVirtualIpCallback(EasyTierManager.VirtualIpCallback cb) {
    sCallback = cb;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && ACTION_STOP.equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }

    String host = intent != null ? intent.getStringExtra(EXTRA_HOST) : "";
    String networkName = intent != null ? intent.getStringExtra(EXTRA_NETWORK_NAME) : "";
    String key = intent != null ? intent.getStringExtra(EXTRA_KEY) : "";

    startForeground(NOTIF_ID, buildNotification());

    new Thread(() -> startEasyTier(host, networkName, key)).start();

    return START_STICKY;
  }

  private void startEasyTier(String host, String networkName, String key) {
    try {
      File binary = EasyTierManager.prepareBinary(this);
      Log.d(TAG, "binary 就绪: " + binary.getAbsolutePath());

      VpnService.Builder builder = new VpnService.Builder();
      builder.setSession("EasyTier");
      builder.setMtu(1500);
      builder.addAddress("10.26.0.1", 24);
      builder.addRoute("0.0.0.0", 0);
      builder.addDnsServer("8.8.8.8");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        builder.setMetered(false);
      }

      tunFd = builder.establish();
      if (tunFd == null) {
        throw new Exception("TUN 设备创建失败，请确认已授予 VPN 权限");
      }

      Log.d(TAG, "TUN fd = " + tunFd.getFd());

      if (host == null || host.trim().isEmpty()) {
        throw new Exception("EasyTier 服务器不能为空");
      }

      String finalNetworkName = (networkName != null && !networkName.trim().isEmpty())
        ? networkName.trim()
        : "default";
      String finalKey = key != null ? key.trim() : "";

      List<String> cmd = new ArrayList<>();
      cmd.add(binary.getAbsolutePath());
      cmd.add("--peers");
      cmd.add(host.trim());
      cmd.add("--network-name");
      cmd.add(finalNetworkName);
      cmd.add("--network-secret");
      cmd.add(finalKey);
      cmd.add("--dhcp");
      cmd.add("--tun-fd");
      cmd.add(String.valueOf(tunFd.getFd()));
      cmd.add("--no-listener");
      cmd.add("--log-level");
      cmd.add("info");

      Log.d(TAG, "启动命令: " + cmd);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      pb.environment().put("RUST_LOG", "info");

      easyTierProcess = pb.start();

      monitorThread = new Thread(this::monitorOutput);
      monitorThread.start();

    } catch (Exception e) {
      Log.e(TAG, "EasyTier 启动失败: " + e.getMessage(), e);
      if (sCallback != null) sCallback.onError(e.getMessage());
      stopSelf();
    }
  }

  private void monitorOutput() {
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(easyTierProcess.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Log.d(TAG, "[easytier] " + line);
        String vip = parseVirtualIp(line);
        if (vip != null && sCallback != null) {
          Log.d(TAG, "虚拟 IP 已分配: " + vip);
          sCallback.onVirtualIpReady(vip);
          sCallback = null;
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "监听输出失败: " + e.getMessage(), e);
    }

    Log.d(TAG, "easytier-core 进程已退出");
    stopSelf();
  }

  private String parseVirtualIp(String line) {
    if (line == null) return null;

    String lower = line.toLowerCase();
    String[] keywords = new String[]{
      "assigned virtual ip:",
      "my_ipv4:",
      "virtual_ip:",
      "ipv4 addr:",
      "virtual ipv4:",
      "dhcp ip:"
    };

    for (String kw : keywords) {
      int idx = lower.indexOf(kw);
      if (idx >= 0) {
        String rest = line.substring(idx + kw.length()).trim();
        String[] parts = rest.split("[\\s/,]");
        if (parts.length > 0 && parts[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
          return parts[0];
        }
      }
    }
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "EasyTierVpnService 销毁");

    if (monitorThread != null) {
      monitorThread.interrupt();
      monitorThread = null;
    }

    if (easyTierProcess != null) {
      easyTierProcess.destroy();
      easyTierProcess = null;
    }

    try {
      if (tunFd != null) {
        tunFd.close();
        tunFd = null;
      }
    } catch (Exception e) {
      Log.e(TAG, "关闭 TUN fd 失败", e);
    }
  }

  private Notification buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID,
        "EasyTier VPN",
        NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("EasyTier 虚拟组网前台服务");

      NotificationManager nm = getSystemService(NotificationManager.class);
      if (nm != null) {
        nm.createNotificationChannel(channel);
      }
    }

    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder = new Notification.Builder(this, CHANNEL_ID);
    } else {
      builder = new Notification.Builder(this);
      builder.setPriority(Notification.PRIORITY_LOW);
    }

    builder
      .setContentTitle("EasyTier 虚拟组网")
      .setContentText("正在连接虚拟网络...")
      .setSmallIcon(R.drawable.chevron_left)
      .setOngoing(true);

    return builder.build();
  }
}
