package com.daitj.easycontrolfork.app.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.daitj.easycontrolfork.app.R;

/**
 * EasyTierVpnService
 *
 * 职责：
 * 1. 建立 Android TUN 虚拟网卡（通过 VpnService.Builder）
 * 2. 拉起 easytier-core 进程，传入 TUN fd 和连接参数
 * 3. 解析进程输出，获取分配的虚拟 IP 后回调
 * 4. 维持前台服务保活
 *
 * easytier-core 启动参数说明（no-tun 模式暂不支持，这里走标准 tun 模式）：
 *   easytier-core -p <peer> --network-name <key> --ipv4 dhcp --tun-fd <fd>
 */
public class EasyTierVpnService extends VpnService {

  private static final String TAG = "EasyTierVpnService";
  public static final String ACTION_STOP = "com.daitj.easycontrolfork.STOP_EASYTIER";
  public static final String EXTRA_HOST = "extra_host";
  public static final String EXTRA_PORT = "extra_port";
  public static final String EXTRA_KEY = "extra_key";
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
    int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 11010) : 11010;
    String key = intent != null ? intent.getStringExtra(EXTRA_KEY) : "";

    startForeground(NOTIF_ID, buildNotification());

    new Thread(() -> startEasyTier(host, port, key)).start();

    return START_STICKY;
  }

  private void startEasyTier(String host, int port, String key) {
    try {
      // 1. 准备二进制
      File binary = EasyTierManager.prepareBinary(this);
      Log.d(TAG, "binary 就绪: " + binary.getAbsolutePath());

      // 2. 建立 TUN 设备
      VpnService.Builder builder = new VpnService.Builder();
      builder.setMtu(1500);
      // 先给一个临时地址，easytier-core 会通过 DHCP 动态分配实际地址
      // 这里占位用 10.0.0.1/24，实际运行后 easytier 会修改路由
      builder.addAddress("10.26.0.1", 24);
      builder.addRoute("10.26.0.0", 24);
      builder.addDnsServer("8.8.8.8");
      builder.setSession("EasyTier");
      builder.setBlocking(true);
      tunFd = builder.establish();

      if (tunFd == null) {
        throw new Exception("TUN 设备创建失败，请确认已授予 VPN 权限");
      }
      Log.d(TAG, "TUN fd = " + tunFd.getFd());

      // 3. 构建 easytier-core 启动命令
      // --tun-fd 参数让 easytier-core 直接使用我们建好的 TUN fd
      List<String> cmd = new ArrayList<>();
      cmd.add(binary.getAbsolutePath());
      cmd.add("--peer");
      cmd.add("tcp://" + host + ":" + port);
      cmd.add("--network-name");
      cmd.add(key != null && !key.isEmpty() ? key : "default");
      cmd.add("--network-secret");
      cmd.add(key != null && !key.isEmpty() ? key : "");
      cmd.add("--ipv4");
      cmd.add("dhcp");
      cmd.add("--tun-fd");
      cmd.add(String.valueOf(tunFd.getFd()));
      cmd.add("--no-listener"); // 不监听端口，只作为客户端加入网络
      cmd.add("--log-level");
      cmd.add("info");

      Log.d(TAG, "启动命令: " + cmd);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      pb.environment().put("RUST_LOG", "info");

      protect(tunFd.getFd()); // 防止 VPN 流量回环

      easyTierProcess = pb.start();

      // 4. 监听输出，等待虚拟 IP 分配
      monitorThread = new Thread(() -> monitorOutput());
      monitorThread.start();

    } catch (Exception e) {
      Log.e(TAG, "EasyTier 启动失败: " + e.getMessage(), e);
      if (sCallback != null) sCallback.onError(e.getMessage());
      stopSelf();
    }
  }

  /**
   * 监听 easytier-core 的标准输出，解析虚拟 IP。
   * easytier-core 在成功加入网络后会打印类似：
   *   [INFO] ... assigned virtual ip: 10.26.0.x
   */
  private void monitorOutput() {
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(easyTierProcess.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Log.d(TAG, "[easytier] " + line);
        // 解析虚拟 IP
        String vip = parseVirtualIp(line);
        if (vip != null && sCallback != null) {
          Log.d(TAG, "虚拟 IP 已分配: " + vip);
          sCallback.onVirtualIpReady(vip);
          sCallback = null; // 只回调一次
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "监听输出失败: " + e.getMessage());
    }

    Log.d(TAG, "easytier-core 进程已退出");
    stopSelf();
  }

  /**
   * 从 easytier-core 日志行中解析虚拟 IP。
   * 匹配形如 "assigned virtual ip: 10.26.x.x" 或 "my_ipv4: 10.26.x.x" 的行。
   */
  private String parseVirtualIp(String line) {
    String lower = line.toLowerCase();
    // 常见关键词：assigned, my_ipv4, virtual ip
    for (String kw : new String[]{"assigned virtual ip:", "my_ipv4:", "virtual_ip:", "ipv4 addr:"}) {
      int idx = lower.indexOf(kw);
      if (idx >= 0) {
        String rest = line.substring(idx + kw.length()).trim();
        // 取第一个 token（IP 地址部分）
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

    if (monitorThread != null) monitorThread.interrupt();

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

    EasyTierManager.stop(this);
  }

  private Notification buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID, "EasyTier VPN", NotificationManager.IMPORTANCE_LOW
      );
      NotificationManager nm = getSystemService(NotificationManager.class);
      if (nm != null) nm.createNotificationChannel(channel);
    }
    return new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("EasyTier 虚拟组网")
      .setContentText("正在连接虚拟网络...")
      .setSmallIcon(R.drawable.chevron_left) // 先用现有图标占位
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build();
  }
}
