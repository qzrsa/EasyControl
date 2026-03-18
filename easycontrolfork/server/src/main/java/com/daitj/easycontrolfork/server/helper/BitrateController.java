/*
 * 动态码率控制器 - 根据网络状况和场景复杂度自动调整码率
 * 借鉴自 Scrcpy 开源项目
 */
package com.daitj.easycontrolfork.server.helper;

import android.os.Build;
import android.os.SystemClock;

import java.util.LinkedList;
import java.util.Queue;

public final class BitrateController {
    
    // 码率范围
    private static int minBitrate;
    private static int maxBitrate;
    private static int currentBitrate;
    
    // 网络状态检测
    private static final int LATENCY_HISTORY_SIZE = 30;
    private static final Queue<Long> latencyHistory = new LinkedList<>();
    private static long lastFrameTime = 0;
    private static long lastCalculateTime = 0;
    
    // 场景复杂度检测
    private static final int COMPLEXITY_HISTORY_SIZE = 10;
    private static final Queue<Integer> complexityHistory = new LinkedList<>();
    private static int lastFrameSize = 0;
    
    // 动态调整参数
    private static final int ADJUST_INTERVAL_MS = 1000; // 每1秒调整一次
    private static final float GOOD_LATENCY_THRESHOLD = 33f; // 低于33ms认为网络良好
    private static final float BAD_LATENCY_THRESHOLD = 100f; // 高于100ms认为网络差
    private static final float BITRATE_INCREASE_FACTOR = 1.1f; // 增加码率10%
    private static final float BITRATE_DECREASE_FACTOR = 0.85f; // 减少码率15%
    
    // 关键帧控制
    private static long lastKeyFrameTime = 0;
    private static boolean forceKeyFrame = false;
    
    // 统计信息
    private static long totalFrames = 0;
    private static long totalBytes = 0;
    private static long startTime = 0;
    
    public static void init(int initialBitrate, int minBit, int maxBit) {
        currentBitrate = initialBitrate;
        minBitrate = minBit;
        maxBitrate = maxBit;
        startTime = SystemClock.uptimeMillis();
        lastCalculateTime = startTime;
        latencyHistory.clear();
        complexityHistory.clear();
    }
    
    /**
     * 记录帧发送时间，用于计算延迟
     */
    public static void recordFrameTime(long presentationTimeUs) {
        long currentTime = SystemClock.uptimeMillis();
        
        if (lastFrameTime > 0) {
            long latency = currentTime - lastFrameTime;
            addLatencySample(latency);
        }
        
        lastFrameTime = currentTime;
        totalFrames++;
        
        // 定期调整码率
        if (currentTime - lastCalculateTime >= ADJUST_INTERVAL_MS) {
            calculateAndAdjust();
            lastCalculateTime = currentTime;
        }
    }
    
    /**
     * 记录帧大小，用于场景复杂度判断
     */
    public static void recordFrameSize(int size, boolean isKeyFrame) {
        if (isKeyFrame) {
            lastKeyFrameTime = SystemClock.uptimeMillis();
        }
        
        // 计算复杂度指标（帧大小相对于当前码率的比值）
        if (currentBitrate > 0) {
            int expectedFrameSize = (int)((currentBitrate / 8.0) / 60); // 假设60fps
            int complexity = expectedFrameSize > 0 ? (size * 100 / expectedFrameSize) : 100;
            addComplexitySample(complexity);
        }
        
        lastFrameSize = size;
        totalBytes += size;
    }
    
    /**
     * 强制请求关键帧（用于场景切换检测）
     */
    public static boolean shouldForceKeyFrame() {
        if (forceKeyFrame) {
            forceKeyFrame = false;
            return true;
        }
        
        // 检测场景突变：当前帧大小与上一帧差异超过50%
        if (lastFrameSize > 0) {
            // 这里可以添加更复杂的场景切换检测逻辑
        }
        
        return false;
    }
    
    /**
     * 请求强制关键帧
     */
    public static void requestKeyFrame() {
        forceKeyFrame = true;
    }
    
    /**
     * 获取当前码率
     */
    public static int getCurrentBitrate() {
        return currentBitrate;
    }
    
    /**
     * 获取建议的 I 帧间隔（秒）
     */
    public static int getSuggestedIFrameInterval() {
        // 根据场景复杂度动态调整 I 帧间隔
        // 低复杂度场景可以用更长的 I 帧间隔
        float avgComplexity = getAverageComplexity();
        
        if (avgComplexity < 80) {
            return 15; // 低复杂度，减少关键帧
        } else if (avgComplexity < 120) {
            return 10; // 正常复杂度
        } else {
            return 5; // 高复杂度，增加关键帧以提高容错
        }
    }
    
    /**
     * 获取建议的帧率
     */
    public static int getSuggestedFrameRate(int maxFps) {
        float avgLatency = getAverageLatency();
        
        if (avgLatency > BAD_LATENCY_THRESHOLD * 2) {
            // 网络很差，降低帧率
            return Math.max(15, maxFps / 2);
        } else if (avgLatency > BAD_LATENCY_THRESHOLD) {
            // 网络较差，适度降低帧率
            return Math.max(30, maxFps * 3 / 4);
        }
        
        return maxFps;
    }
    
    /**
     * 判断是否应该降低分辨率
     */
    public static boolean shouldReduceResolution() {
        float avgLatency = getAverageLatency();
        return avgLatency > BAD_LATENCY_THRESHOLD * 2 && currentBitrate <= minBitrate * 1.2;
    }
    
    /**
     * 获取统计信息
     */
    public static String getStatistics() {
        long elapsed = SystemClock.uptimeMillis() - startTime;
        if (elapsed <= 0) return "No data";
        
        float avgBitrate = (totalBytes * 8f * 1000) / elapsed;
        float fps = (totalFrames * 1000f) / elapsed;
        
        return String.format("Bitrate: %.1f Mbps, FPS: %.1f, Current: %.1f Mbps, Latency: %.1f ms",
                avgBitrate / 1_000_000, fps, currentBitrate / 1_000_000f, getAverageLatency());
    }
    
    // ============ 私有方法 ============
    
    private static void addLatencySample(long latency) {
        latencyHistory.offer(latency);
        if (latencyHistory.size() > LATENCY_HISTORY_SIZE) {
            latencyHistory.poll();
        }
    }
    
    private static void addComplexitySample(int complexity) {
        complexityHistory.offer(complexity);
        if (complexityHistory.size() > COMPLEXITY_HISTORY_SIZE) {
            complexityHistory.poll();
        }
    }
    
    private static float getAverageLatency() {
        if (latencyHistory.isEmpty()) return 0;
        
        long sum = 0;
        for (Long l : latencyHistory) {
            sum += l;
        }
        return (float) sum / latencyHistory.size();
    }
    
    private static float getAverageComplexity() {
        if (complexityHistory.isEmpty()) return 100;
        
        int sum = 0;
        for (Integer c : complexityHistory) {
            sum += c;
        }
        return (float) sum / complexityHistory.size();
    }
    
    private static void calculateAndAdjust() {
        if (latencyHistory.size() < 10) return; // 样本不足
        
        float avgLatency = getAverageLatency();
        float avgComplexity = getAverageComplexity();
        
        // 根据网络延迟调整码率
        if (avgLatency < GOOD_LATENCY_THRESHOLD) {
            // 网络良好，可以尝试提高码率
            if (avgComplexity > 100) {
                // 场景复杂，提高码率
                currentBitrate = Math.min(maxBitrate, 
                        (int)(currentBitrate * BITRATE_INCREASE_FACTOR));
            }
        } else if (avgLatency > BAD_LATENCY_THRESHOLD) {
            // 网络较差，降低码率
            currentBitrate = Math.max(minBitrate, 
                    (int)(currentBitrate * BITRATE_DECREASE_FACTOR));
            
            // 如果码率已经很低但延迟仍然很高，请求关键帧以刷新
            if (currentBitrate <= minBitrate * 1.5 && avgLatency > BAD_LATENCY_THRESHOLD * 1.5) {
                forceKeyFrame = true;
            }
        }
        
        // 根据场景复杂度微调
        if (avgComplexity < 60) {
            // 低复杂度场景，可以降低码率节省带宽
            currentBitrate = Math.max(minBitrate, 
                    (int)(currentBitrate * 0.95));
        }
    }
}
