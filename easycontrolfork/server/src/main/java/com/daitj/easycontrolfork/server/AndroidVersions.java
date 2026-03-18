/*
 * 本项目大量借鉴学习了开源投屏软件：Scrcpy，在此对该项目表示感谢
 * 移植自官方 scrcpy v3.3.4
 */
package com.daitj.easycontrolfork.server;

/**
 * Android version code constants, done right.
 * 移植自 scrcpy v3.3.4
 */
public final class AndroidVersions {

    private AndroidVersions() {
        // not instantiable
    }

    public static final int API_21_ANDROID_5_0 = android.os.Build.VERSION_CODES.LOLLIPOP;
    public static final int API_22_ANDROID_5_1 = android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
    public static final int API_23_ANDROID_6_0 = android.os.Build.VERSION_CODES.M;
    public static final int API_24_ANDROID_7_0 = android.os.Build.VERSION_CODES.N;
    public static final int API_25_ANDROID_7_1 = android.os.Build.VERSION_CODES.N_MR1;
    public static final int API_26_ANDROID_8_0 = android.os.Build.VERSION_CODES.O;
    public static final int API_27_ANDROID_8_1 = android.os.Build.VERSION_CODES.O_MR1;
    public static final int API_28_ANDROID_9 = android.os.Build.VERSION_CODES.P;
    public static final int API_29_ANDROID_10 = android.os.Build.VERSION_CODES.Q;
    public static final int API_30_ANDROID_11 = android.os.Build.VERSION_CODES.R;
    public static final int API_31_ANDROID_12 = android.os.Build.VERSION_CODES.S;
    public static final int API_32_ANDROID_12L = android.os.Build.VERSION_CODES.S_V2;
    public static final int API_33_ANDROID_13 = android.os.Build.VERSION_CODES.TIRAMISU;
    public static final int API_34_ANDROID_14 = android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    public static final int API_35_ANDROID_15 = android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

}
