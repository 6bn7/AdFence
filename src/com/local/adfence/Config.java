package com.local.adfence;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 可配置项。开源版本里**不硬编码任何具体 App**：
 *  - targetPkg：要"单独盯住"的目标 App 包名（用于「只抓目标App」诊断模式和逐 App 排查），留空表示不启用
 *  - upstreamDns：DNS 转发用的上游解析器（默认公共 DNS，可在设置里换成本地/自建）
 */
public final class Config {

    private static final String SP = "adfence";
    private static final String K_PKG = "targetPkg";
    private static final String K_DNS = "upstreamDns";

    public static final String DEF_DNS = "223.5.5.5";

    public static volatile String targetPkg = "";
    public static volatile String upstreamDns = DEF_DNS;

    public static void load(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE);
            targetPkg = nz(sp.getString(K_PKG, ""));
            upstreamDns = nzDns(sp.getString(K_DNS, DEF_DNS));
        } catch (Throwable ignored) {
        }
    }

    public static void save(Context c, String pkg, String dns) {
        targetPkg = nz(pkg);
        upstreamDns = nzDns(dns);
        try {
            c.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
                    .putString(K_PKG, targetPkg)
                    .putString(K_DNS, upstreamDns)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nzDns(String s) {
        String d = nz(s);
        return d.length() == 0 ? DEF_DNS : d;
    }

    /** 是否设置了一个看起来有效的包名 */
    public static boolean hasTarget() {
        return targetPkg.length() > 2 && targetPkg.contains(".") && !targetPkg.contains(" ");
    }

    /** 上游列表：设置的优先，其后是公共兜底（去重） */
    public static String[] upstreams() {
        String a = upstreamDns;
        String[] def = {DEF_DNS, "119.29.29.29", "180.76.76.76"};
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add(a);
        for (String d : def) if (!d.equals(a)) out.add(d);
        return out.toArray(new String[0]);
    }
}
