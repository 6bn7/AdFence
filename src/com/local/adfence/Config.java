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
        if (d.length() == 0) return DEF_DNS;
        // 审计 N-2：只接受 IPv4 字面量。填主机名会导致自递归
        // （系统解析该主机名 → 走上游 → 又是本应用 → 池线程被层层占满）
        return isIpv4(d) ? d : DEF_DNS;
    }

    /** 严格 IPv4 字面量校验（不接受主机名、不接受 IPv6、不接受端口） */
    public static boolean isIpv4(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 7 || t.length() > 15) return false;
        String[] p = t.split("\\.", -1);
        if (p.length != 4) return false;
        for (String x : p) {
            if (x.length() == 0 || x.length() > 3) return false;
            for (int i = 0; i < x.length(); i++) {
                if (!Character.isDigit(x.charAt(i))) return false;
            }
            try {
                if (Integer.parseInt(x) > 255) return false;
            } catch (Throwable t2) {
                return false;
            }
        }
        return true;
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
        if (isIpv4(a)) out.add(a);                       // 审计 N-2：兜底列表同样只放合法 IP
        for (String d : def) if (!d.equals(a)) out.add(d);
        return out.toArray(new String[0]);
    }
}
