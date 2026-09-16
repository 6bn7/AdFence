package com.local.adfence;

import java.util.Locale;

/**
 * 域名/文件名 → 人话结论。
 * 注意：这里只是「名字特征」的提示，用于帮人判断，不作为单独拦截依据
 * （曾经凭名字猜 iydsj 是广告，误伤了 App 自家业务域）。
 */
public final class AdHints {

    /** 关键字 → {广告体系名, 建议域名} */
    private static final String[][] MAP = {
            {"kwimgs", "快手素材 CDN", "kwimgs.com"},
            {"ksadsdk", "快手广告 SDK 的素材缓存", "open.e.kuaishou.com"},
            {"kwad", "快手广告 SDK", "open.e.kuaishou.com"},
            {"pglstatp", "穿山甲 CDN", "pglstatp-toutiao.com"},
            {"pangolin", "穿山甲（字节广告）", "pangolin-sdk-toutiao.com"},
            {"pangle", "穿山甲（字节广告）", "pangle.cn"},
            {"csj", "穿山甲（CSJ）下载目录", "pangolin-sdk-toutiao.com"},
            {"gdtimg", "优量汇素材 CDN", "gdtimg.com"},
            {"ugdtimg", "优量汇素材 CDN", "ugdtimg.com"},
            {"gdt", "优量汇（腾讯广告）", "gdt.qq.com"},
            {"bddownload", "百度联盟广告下载", "union.baidu.com"},
            {"mobads", "百度联盟广告", "mobads.baidu.com"},
            {"adgain", "AdGain 下载类广告", "adgain.cn"},
            {"tianmu", "天目广告 SDK", "sdk.tianmu.mobi"},
            {"yfan", "YFanAds 广告 SDK", "yfanads.com"},
            {"zztfly", "广告投放服务", "zztfly.com"},
            {"oceanengine", "巨量引擎（字节广告平台）", "oceanengine.com"},
            {"guangtuikeji", "光推科技 adx", "guangtuikeji.com"},
            {"bytesmanager", "广告 APK 下载 CDN", "bytesmanager.com"},
            {"adkwai", "快手广告", "adkwai.com"},
            {"kwaizt", "快手广告", "kwaizt.com"},
            {"kuaishouzt", "快手广告", "kuaishouzt.com"},
            {"yximgs", "快手 CDN", "yximgs.com"},
            {"etoote", "快手 CDN", "etoote.com"},
            {"umeng", "友盟统计上报", "ulogs.umeng.com"},
            {"snssdk", "字节（头条/穿山甲体系）", "log.snssdk.com"},
            {"adx", "广告交易平台（ADX）", ""},
            {"tracker", "追踪上报", ""},
            {"xlog", "埋点日志", ""},
    };

    private static String[] find(String s) {
        if (s == null) return null;
        String t = s.toLowerCase(Locale.ROOT);
        for (String[] m : MAP) {
            if (t.contains(m[0])) return m;
        }
        return null;
    }

    /** 命中的广告体系名；没有就返回 null */
    public static String sdkOf(String s) {
        String[] m = find(s);
        return m == null ? null : m[1];
    }

    /** 建议一起拦的域名；没有就返回 null */
    public static String suggestedDomain(String s) {
        String[] m = find(s);
        return m == null || m[2].length() == 0 ? null : m[2];
    }

    public static boolean adLike(String s) {
        return find(s) != null;
    }

    /** 把技术信息翻成人话 */
    public static String sentence(String domain) {
        String[] m = find(domain);
        if (m == null) return null;
        String core = m[0].length() <= 3 ? m[0].toUpperCase(Locale.ROOT) : m[0];
        return "名字里带「" + core + "」，属于「" + m[1] + "」的域名特征。";
    }
}
