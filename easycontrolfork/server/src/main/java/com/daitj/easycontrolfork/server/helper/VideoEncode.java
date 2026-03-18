/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 * 
 * 视频编码优化版本 - 支持动态码率、自适应分辨率、关键帧优化
 */
package com.daitj.easycontrolfork.server.helper;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.view.Surface;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import com.daitj.easycontrolfork.server.Server;
import com.daitj.easycontrolfork.server.entity.Device;
import com.daitj.easycontrolfork.server.entity.Options;
import com.daitj.easycontrolfork.server.wrappers.SurfaceControl;

public final class VideoEncode {
  private static MediaCodec encedec;
  private static MediaFormat encodecFormat;
  public static boolean isHasChangeConfig = false;
  private static boolean useH265;
  
  // 编码器配置选项
  private static boolean useDynamicBitrate = true;
  private static boolean useLowLatencyMode = true;
  private static int bitrateMode = BITRATE_MODE_VBR; // VBR 或 CBR
  
  // 码率模式常量
  private static final int BITRATE_MODE_VBR = 0; // 可变码率
  private static final int BITRATE_MODE_CBR = 1; // 恒定码率
  private static final int BITRATE_MODE_CQ = 2;  // 恒定质量

  private static IBinder display;
  
  // 当前编码参数
  private static int currentBitrate;
  private static int currentFps;
  private static int currentIFrameInterval;

  public static void init() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
    useH265 = Options.supportH265 && EncodecTools.isSupportH265();
    useDynamicBitrate = Options.enableDynamicBitrate;
    useLowLatencyMode = Options.enableLowLatencyMode;
    
    // 初始化当前参数
    currentBitrate = Options.maxVideoBit;
    currentFps = Options.maxFps;
    currentIFrameInterval = Options.iFrameInterval;
    
    // 初始化动态码率控制器
    if (useDynamicBitrate) {
      BitrateController.init(currentBitrate, 
          Options.minVideoBit, 
          Options.maxVideoBit);
    }
    
    ByteBuffer byteBuffer = ByteBuffer.allocate(9);
    byteBuffer.put((byte) (useH265 ? 1 : 0));
    byteBuffer.putInt(Device.videoSize.first);
    byteBuffer.putInt(Device.videoSize.second);
    byteBuffer.flip();
    Server.writeVideo(byteBuffer);
    
    // 创建显示器
    display = SurfaceControl.createDisplay("easycontrol", Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(Build.VERSION.CODENAME)));
    
    // 创建Codec
    createEncodecFormat();
    startEncode();
  }

  private static void createEncodecFormat() throws IOException {
    String codecMime = useH265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
    encedec = MediaCodec.createEncoderByType(codecMime);
    encodecFormat = new MediaFormat();
    encodecFormat.setString(MediaFormat.KEY_MIME, codecMime);
    
    // === 基础参数 ===
    encodecFormat.setInteger(MediaFormat.KEY_BIT_RATE, currentBitrate);
    encodecFormat.setInteger(MediaFormat.KEY_FRAME_RATE, currentFps);
    encodecFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, currentIFrameInterval);
    
    // === 低延迟优化 ===
    if (useLowLatencyMode) {
      configureLowLatency(encodecFormat);
    }
    
    // === 码率控制模式 ===
    configureBitrateMode(encodecFormat, codecMime);
    
    // === Intra Refresh (Android N+) ===
    // 使用渐进式刷新代替大型关键帧，减少瞬时峰值
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      int intraRefreshPeriod = currentFps * 3; // 每3秒完成一次刷新
      encodecFormat.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, intraRefreshPeriod);
    }
    
    // === 编码质量优化 ===
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // 设置质量优先（对 VBR 模式有效）
      try {
        encodecFormat.setInteger(MediaFormat.KEY_QUALITY, 90); // 质量 0-100
      } catch (Exception ignored) {}
    }
    
    // 其他参数
    encodecFormat.setFloat("max-fps-to-encoder", currentFps);
    encodecFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 50_000);
    encodecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    
    // === Profile 和 Level 设置 ===
    configureProfileAndLevel(encodecFormat, codecMime);
  }
  
  /**
   * 配置低延迟模式参数
   */
  private static void configureLowLatency(MediaFormat format) {
    // Android 11+ 支持低延迟模式
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
      } catch (Exception ignored) {}
    }
    
    // 减少编码延迟
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        // 降低延迟的临时方案
        format.setInteger("priority", 0); // 实时优先级
      } catch (Exception ignored) {}
    }
    
    // 设置编码预览模式（减少缓冲）
    try {
      format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
    } catch (Exception ignored) {}
  }
  
  /**
   * 配置码率控制模式
   */
  private static void configureBitrateMode(MediaFormat format, String codecMime) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        // VBR 模式 - 根据场景复杂度动态调整
        // BITRATE_MODE_VBR = 1
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, 
            bitrateMode == BITRATE_MODE_VBR ? 
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR :
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
      } catch (Exception e) {
        // 回退：某些设备不支持该参数
      }
    }
  }
  
  /**
   * 配置编码 Profile 和 Level
   */
  private static void configureProfileAndLevel(MediaFormat format, String codecMime) {
    try {
      if (useH265) {
        // HEVC Main Profile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          format.setInteger(MediaFormat.KEY_PROFILE, 
              MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
          // Level 4.1 - 支持 4K@30fps 或 1080p@120fps
          format.setInteger(MediaFormat.KEY_LEVEL, 
              MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41);
        }
      } else {
        // AVC High Profile - 更好的压缩效率
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          format.setInteger(MediaFormat.KEY_PROFILE, 
              MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
          // Level 4.1
          format.setInteger(MediaFormat.KEY_LEVEL, 
              MediaCodecInfo.CodecProfileLevel.AVCLevel41);
        }
      }
    } catch (Exception ignored) {
      // 某些设备不支持特定 Profile
    }
  }

  // 初始化编码器
  private static Surface surface;

  public static void startEncode() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, ErrnoException {
    ControlPacket.sendVideoSizeEvent();
    encodecFormat.setInteger(MediaFormat.KEY_WIDTH, Device.videoSize.first);
    encodecFormat.setInteger(MediaFormat.KEY_HEIGHT, Device.videoSize.second);
    encedec.configure(encodecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    // 绑定Display和Surface
    surface = encedec.createInputSurface();
    setDisplaySurface(display, surface);
    // 启动编码
    encedec.start();
  }

  public static void stopEncode() {
    encedec.stop();
    encedec.reset();
    surface.release();
  }

  private static void setDisplaySurface(IBinder display, Surface surface) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    SurfaceControl.openTransaction();
    try {
      SurfaceControl.setDisplaySurface(display, surface);
      SurfaceControl.setDisplayProjection(display, 0, new Rect(0, 0, Device.displayInfo.width, Device.displayInfo.height), new Rect(0, 0, Device.videoSize.first, Device.videoSize.second));
      SurfaceControl.setDisplayLayerStack(display, Device.displayInfo.layerStack);
    } finally {
      SurfaceControl.closeTransaction();
    }
  }

  private static final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  
  // 用于动态码率调整的时间戳
  private static long lastBitrateAdjustTime = 0;
  private static final int BITRATE_ADJUST_INTERVAL = 2000; // 2秒调整一次

  public static void encodeOut() throws IOException {
    try {
      // 找到已完成的输出缓冲区
      int outIndex;
      do outIndex = encedec.dequeueOutputBuffer(bufferInfo, -1); while (outIndex < 0);
      ByteBuffer buffer = encedec.getOutputBuffer(outIndex);
      if (buffer == null) return;
      
      // 记录帧信息用于动态调整
      if (useDynamicBitrate) {
        BitrateController.recordFrameTime(bufferInfo.presentationTimeUs);
        BitrateController.recordFrameSize(bufferInfo.size, 
            (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
        
        // 检查是否需要请求关键帧
        if (BitrateController.shouldForceKeyFrame()) {
          requestKeyFrame();
        }
        
        // 定期检查并调整码率
        long currentTime = SystemClock.uptimeMillis();
        if (currentTime - lastBitrateAdjustTime >= BITRATE_ADJUST_INTERVAL) {
          adjustBitrateIfNeeded();
          lastBitrateAdjustTime = currentTime;
        }
      }
      
      ControlPacket.sendVideoEvent(bufferInfo.presentationTimeUs, buffer);
      encedec.releaseOutputBuffer(outIndex, false);
    } catch (IllegalStateException ignored) {
    }
  }
  
  /**
   * 请求关键帧
   */
  private static void requestKeyFrame() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        android.os.Bundle params = new android.os.Bundle();
        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        encedec.setParameters(params);
      } catch (Exception ignored) {}
    }
  }
  
  /**
   * 动态调整码率
   */
  private static void adjustBitrateIfNeeded() {
    int newBitrate = BitrateController.getCurrentBitrate();
    
    if (newBitrate != currentBitrate) {
      try {
        // 使用 Bundle 方式动态调整码率（不需要重启编码器）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          android.os.Bundle params = new android.os.Bundle();
          params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrate);
          encedec.setParameters(params);
          currentBitrate = newBitrate;
        }
      } catch (Exception e) {
        // 某些设备不支持动态码率调整，需要重新配置编码器
        isHasChangeConfig = true;
      }
    }
    
    // 检查是否需要调整 I 帧间隔
    int newIFrameInterval = BitrateController.getSuggestedIFrameInterval();
    if (newIFrameInterval != currentIFrameInterval && 
        Math.abs(newIFrameInterval - currentIFrameInterval) > 2) {
      currentIFrameInterval = newIFrameInterval;
      isHasChangeConfig = true; // 触发重新配置
    }
  }
  
  /**
   * 获取当前码率
   */
  public static int getCurrentBitrate() {
    return currentBitrate;
  }
  
  /**
   * 获取编码统计信息
   */
  public static String getStatistics() {
    if (useDynamicBitrate) {
      return BitrateController.getStatistics();
    }
    return "Dynamic bitrate disabled";
  }

  public static void release() {
    try {
      stopEncode();
      encedec.release();
      SurfaceControl.destroyDisplay(display);
    } catch (Exception ignored) {
    }
  }

}
