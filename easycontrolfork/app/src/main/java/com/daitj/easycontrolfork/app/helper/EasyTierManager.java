package com.daitj.easycontrolfork.app.helper;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.daitj.easycontrolfork.app.entity.AppData;

/**
 * EasyTierManager
 * 负责：
 * 1. 首次运行时把 assets/easytier-core 解压到私有目录
 * 2. 启动 EasyTierVpnService（VpnService 子类），由它负责建立 TUN 设备并拉起 easytier-core 进程
 * 3. 提供 stop() 方法关闭 VPN 服务
 */
public class EasyTierManager {

  private static final String TAG = "EasyTierManager";
  public static final String BINARY_NAME = "easytier-core";

  // VPN 服务当前是否正在运行
  private static volatile boolean running = false;

  /**
   * 准备 easytier-core 二进制文件（从 assets 拷贝到应用私有目录）。
   * 如果文件已存在且可执行则跳过。
   */
  public static File prepareBinary(Context context) throws Exception {
    File destDir = context.getFilesDir();
    File dest = new File(destDir, BINARY_NAME);

    // 每次都重新拷贝，确保版本最新
    try (InputStream in = context.getAssets().open(BINARY_NAME);
         OutputStream out = new FileOutputStream(dest)) {
      byte[] buf = new byte[8192];
      int len;
      while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
    }

    // 设置可执行权限
    if (!dest.setExecutable(true, true)) {
      Log.w(TAG, "setExecutable 失败，尝试 chmod");
      Runtime.getRuntime().exec(new String[]{"chmod", "700", dest.getAbsolutePath()}).waitFor();
    }

    Log.d(TAG, "easytier-core 已就绪: " + dest.getAbsolutePath());
    return dest;
  }

  /**
   * 获取 easytier-core 二进制路径（不做拷贝）。
   */
  public static String getBinaryPath(Context context) {
    return new File(context.getFilesDir(), BINARY_NAME).getAbsolutePath();
  }

  /**
   * 启动 EasyTier VPN 服务。
   * 调用前必须已经通过 VpnService.prepare() 获得用户授权。
   *
   * @param host     EasyTier peer 节点地址（如 47.105.67.198）
   * @param port     EasyTier peer 节点端口（如 11010）
   * @param key      EasyTier 网络密钥
   * @param virtualIpCallback 虚拟 IP 分配成功后的回调（在子线程调用）
   */
  public static void start(
    Context context,
    String host,
    int port,
    String key,
    VirtualIpCallback virtualIpCallback
  ) {
    if (running) {
      Log.d(TAG, "EasyTier 已在运行，跳过重复启动");
      return;
    }
    Intent intent = new Intent(context, EasyTierVpnService.class);
    intent.putExtra(EasyTierVpnService.EXTRA_HOST, host);
    intent.putExtra(EasyTierVpnService.EXTRA_PORT, port);
    intent.putExtra(EasyTierVpnService.EXTRA_KEY, key);
    EasyTierVpnService.setVirtualIpCallback(virtualIpCallback);
    context.startForegroundService(intent);
    running = true;
    Log.d(TAG, "EasyTierVpnService 已启动");
  }

  /**
   * 停止 EasyTier VPN 服务。
   */
  public static void stop(Context context) {
    Intent intent = new Intent(context, EasyTierVpnService.class);
    intent.setAction(EasyTierVpnService.ACTION_STOP);
    context.startService(intent);
    running = false;
    Log.d(TAG, "EasyTierVpnService 已发送停止指令");
  }

  public static boolean isRunning() {
    return running;
  }

  public interface VirtualIpCallback {
    /** 虚拟 IP 分配成功 */
    void onVirtualIpReady(String virtualIp);
    /** 启动失败 */
    void onError(String reason);
  }
}
