package com.scrcpy.app.client.tools;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import com.scrcpy.app.entity.AppData;

public class StatsOverlay {
  private final TextView textView;
  private final WindowManager.LayoutParams params;
  private boolean isAdded = false;

  private int frameCount = 0;
  private long byteCount = 0;
  private int fps = 0;
  private float speedKbps = 0f;
  private long latencyMs = -1;

  private long lastUpdateTime = System.currentTimeMillis();

  public StatsOverlay() {
    textView = new TextView(AppData.applicationContext);
    textView.setTextColor(Color.WHITE);
    textView.setTextSize(11f);
    textView.setShadowLayer(3f, 1f, 1f, Color.BLACK);
    textView.setPadding(12, 6, 12, 6);
    textView.setBackgroundColor(0x88000000);

    params = new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        : WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    );
    params.gravity = Gravity.TOP | Gravity.START;
    params.x = 16;
    params.y = 80;
  }

  public void show() {
    AppData.uiHandler.post(() -> {
      if (!isAdded) {
        AppData.windowManager.addView(textView, params);
        isAdded = true;
      }
    });
  }

  public void hide() {
    AppData.uiHandler.post(() -> {
      if (isAdded) {
        AppData.windowManager.removeView(textView);
        isAdded = false;
      }
    });
  }

  public void onVideoFrame(int bytes) {
    frameCount++;
    byteCount += bytes;
    long now = System.currentTimeMillis();
    long elapsed = now - lastUpdateTime;
    if (elapsed >= 1000) {
      fps = (int) (frameCount * 1000L / elapsed);
      speedKbps = byteCount * 1000f / elapsed / 1024f;
      frameCount = 0;
      byteCount = 0;
      lastUpdateTime = now;
      updateText();
    }
  }

  public void onLatency(long ms) {
    latencyMs = ms;
    updateText();
  }

  private void updateText() {
    String latencyStr = latencyMs < 0 ? "--" : latencyMs + "ms";
    String speedStr = speedKbps >= 1024
      ? String.format("%.1fMB/s", speedKbps / 1024f)
      : String.format("%.0fKB/s", speedKbps);
    String text = fps + "fps  " + speedStr + "  " + latencyStr;
    AppData.uiHandler.post(() -> textView.setText(text));
  }
}
