package com.local.adfence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 规则表。判定顺序：先看 WHITE（例外，最高优先），再看 USER，最后看内置。
 *
 * 收录原则（吃过一次亏，收紧）：
 *   只收「能定位到具体广告 SDK / 广告投放平台」的域名，归属存疑的一律不默认拦。
 *   能定位的证据两种：APK 里 SDK 的域名常量，或运行日志里出现在某广告 SDK 调用链上的域名。
 *   疑似会误伤的（HTTPDNS、大厂通用 API、App 自家业务域）一律不默认拦，放在 rules.txt 注释里按需启用。
 */
public final class Rules {

    /** 来源于 APK dex 的广告 SDK 域名 */
    private static final String[] BLOCK = {
            // 穿山甲 / GroMore（字节广告 SDK）
            "pglstatp-toutiao.com",
            "pangolin-sdk-toutiao.com",
            "pangolin-sdk-toutiao-b.com",
            "pangolin-sdk-toutiao1.com",
            "pangle.cn",
            "toutiaopage.com",
            "ad.toutiao.com",
            "ctobsnssdk.com",
            "applog.bytedance.net",
            "scc.bytedance.com",
            // 百度联盟
            "union.baidu.com",
            "cpro.baidustatic.com",
            "cpro.baidu.com",
            "mobads.baidu.com",
            // 快手联盟
            "open.e.kuaishou.com",
            "open.e.kuaishou.cn",
            "ad.kuaishou.com",
            // AdGain（下载类广告）
            "adgain.cn",
            // OPPO 广告(heytap)
            "uapi.ads.heytapmobi.com",
            "stg-data.ads.heytapmobi.com",
            "adx.ads.heytapmobi.com",
            "ssp-adx.ads.heytapmobi.com",
            "push-ads-cn.heytapmobi.com",
            // 优量汇（GDT）
            "gdt.qq.com",
            "adx.3g.qq.com",
            "mi.gdt.qq.com",
            "v.gdt.qq.com",
            // 友盟统计上报（只拦日志上报，不碰推送）
            "ulogs.umeng.com",
            "ulogs.umengcloud.com",
            "alogus.umeng.com",
            "alogsus.umeng.com",
            "plbslog.umeng.com",
            "errlog.umeng.com",
            "errlogos.umeng.com",
            "errnewlog.umeng.com",
            "errnewlogos.umeng.com",
            "pslog.umeng.com",
            "ccs.umeng.com",
            "ucc.umeng.com",
            "aspect-upush.umeng.com",
            // 腾讯 X5 内核日志（纯上报）
            "log.tbs.qq.com",
            "debugtbs.qq.com",
            "debugx5.qq.com",
            // 广告标识上报（卓信 ID）—— 只到子域，不碰 mobileservice.cn 整个域
            "zxid-m.mobileservice.cn",
            "zxid.mobileservice.cn",
    };

    /** 来自实跑日志、且能确认属于广告体系的域名 */
    private static final String[] LOG2 = {
            "gdtimg.com",                 // 优量汇素材 CDN（gdt img）
            "ugdtimg.com",                // adsmind.ugdtimg.com
            "kwimgs.com",                 // 快手素材 CDN：p66.a.kwimgs.com
            "adkwai.com",                 // 快手广告：p66-ad.adkwai.com
            "yximgs.com",                 // 快手系 CDN：*.pull.yximgs.com
            "etoote.com",                 // 快手系 CDN：*.pull.etoote.com
            "tianmu.mobi",                // sdk.tianmu.mobi 天目广告 SDK
            "yfanads.com",                // dex 里有 com.yfanads.android.libs.* —— 广告 SDK 本体
            "impdsp.meituan.com",         // 美团广告 DSP
            "dspadlogger.waimai.meituan.com",
            "dsp-x.jd.com",               // 京东广告位
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
    };

    /*
     * 审计 L-1：VPN 工作线程无锁读这些集合，而域名库载入 / 规则增删在另一线程写。
     * HashSet 在扩容窗口内可能漏判（连带造成拦截绕过），改用并发集合。
     */
    private static final Set<String> SET = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> USER = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 例外（白名单）：命中即放行，优先级最高 */
    private static final Set<String> WHITE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 内置广告域名库（社区清单编译而来，随版本更新） */
    private static final Set<String> LIB = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 域名库总开关：误伤时一键停用，不用改规则 */
    public static volatile boolean libEnabled = true;

    static {
        for (String d : BLOCK) SET.add(d);
        for (String d : LOG2) SET.add(d);
    }

    // ---------- 规范化 ----------

    public static String norm(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("*.")) s = s.substring(2);
        int i = s.indexOf("://");
        if (i >= 0) s = s.substring(i + 3);
        i = s.indexOf('/');
        if (i >= 0) s = s.substring(0, i);
        i = s.indexOf('?');
        if (i >= 0) s = s.substring(0, i);
        i = s.indexOf(':');
        if (i >= 0) s = s.substring(0, i);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.length() < 4 || !s.contains(".") || s.contains(" ") || s.startsWith(".")) return null;
        return s;
    }

    // ---------- 自加拦截 ----------

    /** 0 成功，1 已在内置，2 已存在，3 格式不对，4 已在例外里 */
    public static synchronized int addUser(String raw, File store) {
        String d = norm(raw);
        if (d == null) return 3;
        if (matches(WHITE, d)) return 4;
        if (matches(SET, d)) return 1;
        if (!USER.add(d)) return 2;
        save(USER, store);
        return 0;
    }

    public static synchronized List<String> userList() {
        List<String> l = new ArrayList<String>(USER);
        Collections.sort(l);
        return l;
    }

    public static synchronized int userCount() {
        return USER.size();
    }

    // ---------- 例外（白名单） ----------

    /** 0 成功，1 是内置规则里的广告域（仍允许加例外，返回 0 但提示），2 已存在，3 格式不对 */
    public static synchronized int addWhite(String raw, File store) {
        String d = norm(raw);
        if (d == null) return 3;
        if (!WHITE.add(d)) return 2;
        save(WHITE, store);
        return 0;
    }

    public static synchronized List<String> whiteList() {
        List<String> l = new ArrayList<String>(WHITE);
        Collections.sort(l);
        return l;
    }

    public static synchronized int whiteCount() {
        return WHITE.size();
    }

    /** 从自加拦截和例外里都删掉 */
    public static synchronized boolean removeAny(String raw, File s1, File s2) {
        String d = norm(raw);
        if (d == null) d = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        boolean a = USER.remove(d);
        boolean b = WHITE.remove(d);
        if (a) save(USER, s1);
        if (b) save(WHITE, s2);
        return a || b;
    }

    public static synchronized void clearUser(File store) {
        USER.clear();
        save(USER, store);
    }

    public static synchronized void clearWhite(File store) {
        WHITE.clear();
        save(WHITE, store);
    }

    public static synchronized void loadUser(File f) {
        load(USER, f);
    }

    public static synchronized void loadWhite(File f) {
        load(WHITE, f);
    }

    private static void load(Set<String> into, File f) {
        try {
            if (f == null || !f.exists()) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("#")) continue;   // 注释行
                String d = norm(line);
                if (d != null) into.add(d);
            }
            br.close();
        } catch (Exception ignored) {
        }
    }

    private static void save(Set<String> from, File f) {
        try {
            if (f == null) return;
            List<String> l = new ArrayList<String>(from);
            Collections.sort(l);
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
            for (String d : l) w.write(d + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }

    /** 从外部 rules.txt 追加（adb push 用法） */
    public static synchronized int loadFile(File f) {
        int added = 0;
        try {
            if (f == null || !f.exists()) return 0;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("#")) continue;   // 注释行
                String d = norm(line);
                if (d == null) continue;
                if (SET.add(d)) added++;
            }
            br.close();
        } catch (Exception ignored) {
        }
        return added;
    }

    // ---------- 查询 ----------

    public static int size() {
        return SET.size() + USER.size() + (libEnabled ? LIB.size() : 0);
    }

    /** 从 assets/adlib.txt 载入域名库（一行一个域，可含 # 注释） */
    public static synchronized int loadLibrary(java.io.InputStream in) {
        if (in == null) return 0;
        int n = 0;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"), 1 << 16);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                String d = norm(line);
                if (d != null && LIB.add(d)) n++;
            }
            br.close();
        } catch (Exception ignored) {
        }
        return n;
    }

    public static int libCount() {
        return LIB.size();
    }

    public static int count(String kind) {
        if ("block".equals(kind)) return BLOCK.length;
        if ("log2".equals(kind)) return LOG2.length;
        if ("user".equals(kind)) return USER.size();
        if ("white".equals(kind)) return WHITE.size();
        return 0;
    }

    /** 后缀匹配：a.b.example.com 命中 example.com */
    private static boolean matches(Set<String> set, String d) {
        while (d.length() > 0) {
            if (set.contains(d)) return true;
            int i = d.indexOf('.');
            if (i < 0) return false;
            d = d.substring(i + 1);
        }
        return false;
    }

    public static boolean isBlocked(String qname) {
        if (qname == null) return false;
        String d = qname.toLowerCase(Locale.ROOT);
        if (matches(WHITE, d)) return false;      // 例外优先
        if (matches(SET, d) || matches(USER, d)) return true;
        return libEnabled && matches(LIB, d);
    }

    /** 这条域名是"谁"决定的 —— 给界面显示用 */
    public static String sourceOf(String qname) {
        if (qname == null) return "未知";
        String d = qname.toLowerCase(Locale.ROOT);
        if (matches(WHITE, d)) return "例外（你放行的）";
        if (matches(USER, d)) return "自加规则（你加的）";
        if (matches(SET, d)) return "手工规则（按 APK / 日志实锤加的）";
        if (libEnabled && matches(LIB, d)) return "内置广告域名库";
        return "未命中规则";
    }

    public static boolean isInWhite(String qname) {
        return qname != null && matches(WHITE, qname.toLowerCase(Locale.ROOT));
    }

    public static boolean isUserBlocked(String qname) {
        return qname != null && matches(USER, qname.toLowerCase(Locale.ROOT));
    }
}
