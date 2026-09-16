package com.local.adfence;

import android.content.Context;
import android.content.pm.PackageInfo;

/** 小工具：版本号只从 APK 清单读，避免界面里再硬编码一份 */
public final class Util {

    public static String versionName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName == null ? "?" : pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static int versionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }
}
