package com.local.adfence;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 广告产物清理（v2.4 改造版）
 *
 * 1) 不再使用 "所有文件访问(MANAGE_EXTERNAL_STORAGE)"：改为 SAF —— 用户只授权一个目录（如 Download），
 *    本 App 就只能碰这一个目录。
 * 2) 匹配收窄：文件必须"命中广告关键字 **且** 是广告素材/下载器产物扩展名"才删；
 *    目录必须"是已知广告目录名 或 以广告 SDK 前缀开头"，且名字纯 ASCII（中文名一律不碰）。
 *    像 pangle_notes.zip / csj_备份 这种一律不动。
 * 3) 全部留痕：每次删除写 deleted.log（时间 + 名称 + 大小 + 类型），界面可查。
 * 4) 可设「清理前先问我」：只列清单不删，用户确认后才删。
 */
public final class CacheCleaner {


    /** 精确目录名（广告 SDK 的缓存骨架） */
    private static final String[] DIR_EXACT = {
            ".csj", ".jsc", "ksadsdk", "kwad_ex", "bddownload", "adgain",
            "AdGainImageCache", "com_qq_e_download",
    };
    /** 目录名前缀（广告 SDK 目录都带这些前缀） */
    private static final String[] DIR_PREFIX = {
            "pangle", "ksad", "kwad", "kwimgs", "gdtimg", "adgain", "adkwai",
            "bddownload", "com_qq_e_download", "ugdtimg", "yximgs",
    };
    /** 文件名关键字（必须同时满足扩展名条件） */
    private static final String KW = "adgain|pangle|pangolin|ksad|kwad|kwimgs|gdtimg|adkwai|\\.csj";
    /** 只对这些扩展名的文件动手 —— 广告素材 / 下载器产物 */
    private static final String[] FILE_EXT = {
            "jpg", "jpeg", "png", "webp", "gif", "bmp",
            "mp4", "m4v", "3gp", "apk", "db", "journal", "cookie", "bin",
    };

    public static volatile boolean enabled = true;
    /**
     * true = 只列清单不删，等用户确认。
     * 审计 H-3：默认改为 true —— 首次运行即"演练模式"，避免工具自己删错东西。
     */
    public static volatile boolean askFirst = true;

    private static volatile Context APP;
    private static int ticks = 0;

    public static void setContext(Context c) {
        APP = c == null ? null : c.getApplicationContext();
    }

    public static boolean ready() {
        return APP != null && Saf.tree(APP) != null;
    }

    // ---------------- 待确认清单 ----------------

    public static final class Pend {
        public final String id, name, path;
        public final long size;
        public final boolean dir;
        public final boolean media;

        Pend(String id, String name, String path, long size, boolean dir, boolean media) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.size = size;
            this.dir = dir;
            this.media = media;
        }
    }

    private static final List<Pend> PENDING = new ArrayList<Pend>();

    public static synchronized List<Pend> pending() {
        return new ArrayList<Pend>(PENDING);
    }

    public static synchronized void clearPending() {
        PENDING.clear();
    }

    /** 用户确认后执行待删清单 */
    public static synchronized long[] commitPending() {
        long[] acc = new long[2];
        List<Pend> ps = new ArrayList<Pend>(PENDING);
        PENDING.clear();
        for (Pend p : ps) removeNow(p, acc);
        return acc;
    }

    // ---------------- 主循环 ----------------

    public static void loop() {
        while (FenceVpnService.running) {
            try {
                tick();
                ticks++;
                if (ticks % 2 == 1) android.util.Log.i(FenceVpnService.TAG, "CLEAN " + probe());
            } catch (Throwable t) {
                android.util.Log.w(FenceVpnService.TAG, "clean: " + t);
            }
            try {
                // 审计 N-9：轮询从 3 秒放宽到 30 秒（清理不需要秒级响应，省电省 binder 查询）
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 扫一遍授权的目录（只看第一层，够用且安全） */
    public static long[] tick() {
        long[] acc = new long[2];
        if (!enabled || APP == null) return acc;
        Uri tree = Saf.tree(APP);
        if (tree == null) return acc;

        List<Saf.Doc> kids = Saf.children(APP, null);
        List<Pend> found = new ArrayList<Pend>();
        for (Saf.Doc d : kids) {
            if (!shouldRemove(d)) continue;
            found.add(new Pend(d.id, d.name, Saf.treeName(APP) + "/" + d.name,
                    Math.max(d.size, 0), d.dir, isMedia(d)));
        }
        if (found.isEmpty()) return acc;

        if (askFirst) {
            synchronized (CacheCleaner.class) {
                for (Pend p : found) {
                    boolean dup = false;
                    for (Pend q : PENDING) if (q.id.equals(p.id)) dup = true;
                    if (!dup) PENDING.add(p);
                }
            }
            return acc;
        }
        for (Pend p : found) removeNow(p, acc);
        return acc;
    }

    public static long[] cleanNow() {
        return tick();
    }

    private static void removeNow(Pend p, long[] acc) {
        try {
            // 审计 N-3：兜底失败就**不删**（失败安全）——不能让"可找回"的承诺静默失效
            if (!p.dir) {
                boolean backed = p.media ? archive(p) : trash(p);
                if (!backed) {
                    Stats.skipped.incrementAndGet();
                    auditSkip(p);
                    return;
                }
            }
            if (p.dir) {
                // 目录已收紧为「精确 SDK 目录名」或「前缀+内含 SDK 特征文件」，整树删除
                for (Saf.Doc k : Saf.children(APP, p.id)) Saf.deleteTree(APP, k, 0);
            }
            boolean ok = Saf.delete(APP, p.id);
            if (ok) {
                acc[0] += p.size;
                acc[1] += 1;
                audit(p);
            }
        } catch (Throwable t) {
            android.util.Log.w(FenceVpnService.TAG, "remove: " + t);
        }
    }

    /** 删之前留样（图片/视频），让用户能回看"原广告"。返回 true = 已存下副本 */
    private static boolean archive(Pend p) {
        InputStream in = Saf.open(APP, p.id);
        if (in == null) return false;
        if (SampleStore.archiveStream(APP, p.name, in)) {
            Stats.sampleSaved.incrementAndGet();
            return true;
        }
        return false;
    }

    /** 非媒体文件删除前先复制进回收站（审计 H-3 的"可撤销"要求）。返回 true = 已存下副本 */
    private static boolean trash(Pend p) {
        InputStream in = Saf.open(APP, p.id);
        if (in == null) return false;
        if (SampleStore.trashStream(APP, p.name, in)) {
            Stats.trashed.incrementAndGet();
            return true;
        }
        return false;
    }

    /** 留样/回收站失败 → 本次跳过删除，写进记录让用户看得见 */
    private static void auditSkip(Pend p) {
        try {
            File log = new File(APP.getFilesDir(), "deleted.log");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(log, true), "UTF-8");
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date())
                    + "\t跳过（留副本失败）\t" + p.size + "\t" + p.path + "\n");
            w.close();
        } catch (Exception ignored) {
        }
        android.util.Log.w(FenceVpnService.TAG, "CLEAN 跳过（留副本失败）" + p.path);
    }

    /** 审计留痕：时间 + 名称 + 大小 + 类型 */
    private static void audit(Pend p) {
        Stats.cleanRuns.incrementAndGet();
        Stats.cleanFiles.addAndGet(1);
        Stats.cleanBytes.addAndGet(p.size);
        try {
            File log = new File(APP.getFilesDir(), "deleted.log");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(log, true), "UTF-8");
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date())
                    + "\t" + (p.dir ? "目录" : "文件") + "\t" + p.size + "\t" + p.path + "\n");
            w.close();
        } catch (Exception ignored) {
        }
        android.util.Log.i(FenceVpnService.TAG, "CLEAN 删除 " + p.path + " (" + p.size + "B)");
    }

    /** 访问的都是被授权目录里的东西，名称只作显示 */
    public static String lastDeleted(int max) {
        try {
            File log = new File(APP.getFilesDir(), "deleted.log");
            if (!log.exists()) return "";
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(log), "UTF-8"));
            List<String> lines = new ArrayList<String>();
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
            br.close();
            StringBuilder sb = new StringBuilder();
            int from = Math.max(0, lines.size() - max);
            for (int i = lines.size() - 1; i >= from; i--) sb.append(lines.get(i)).append('\n');
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------------- 判定 ----------------

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return false;
        return true;
    }

    private static boolean isMedia(Saf.Doc d) {
        try {
            return d.mime != null && (d.mime.startsWith("image/") || d.mime.startsWith("video/"));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 特征判定结果缓存（审计 N-9：避免每次轮询都对每个目录再发一轮子目录查询） */
    private static final java.util.HashMap<String, long[]> SIG_CACHE = new java.util.HashMap<String, long[]>();
    private static final long SIG_TTL = 10 * 60 * 1000L;

    /** 前缀命中的目录，内部还要有广告 SDK 的特征文件才算（审计 H-3 收紧前缀匹配） */
    private static boolean dirHasSdkSignature(Saf.Doc d) {
        synchronized (SIG_CACHE) {
            long[] hit = SIG_CACHE.get(d.id);
            if (hit != null && System.currentTimeMillis() - hit[1] < SIG_TTL) {
                return hit[0] == 1;
            }
        }
        boolean ok = false;
        try {
            for (Saf.Doc k : Saf.children(APP, d.id)) {
                String n = k.name == null ? "" : k.name.toLowerCase(Locale.ROOT);
                if (n.matches(".*(" + KW + ").*")) { ok = true; break; }
                if (n.equals("journal") || n.equals("cookie") || n.indexOf("kssig") >= 0
                        || n.endsWith(".db") || n.endsWith(".apk")) { ok = true; break; }
            }
        } catch (Throwable ignored) {
        }
        synchronized (SIG_CACHE) {
            if (SIG_CACHE.size() > 500) SIG_CACHE.clear();
            SIG_CACHE.put(d.id, new long[]{ok ? 1 : 0, System.currentTimeMillis()});
        }
        return ok;
    }

    /** 收窄后的判定：宁可漏，不可误删 */
    static boolean shouldRemove(Saf.Doc d) {
        String n = d.name == null ? "" : d.name.toLowerCase(Locale.ROOT);
        if (n.length() == 0) return false;
        if (d.dir) {
            if (!isAscii(n)) return false;              // 中文名 = 用户自己建的，不碰
            for (String e : DIR_EXACT) if (n.equals(e.toLowerCase(Locale.ROOT))) return true;
            // 审计 H-3：前缀匹配收紧 —— 光名字像还不够，目录内必须有广告 SDK 的特征文件
            for (String p : DIR_PREFIX) {
                if (n.startsWith(p)) return dirHasSdkSignature(d);
            }
            return false;
        }
        int dot = n.lastIndexOf('.');
        if (dot < 0 || dot == n.length() - 1) return false;
        String ext = n.substring(dot + 1);
        boolean extOk = false;
        for (String e : FILE_EXT) if (e.equals(ext)) extOk = true;
        if (!extOk) return false;                        // zip/txt/doc/pdf… 一律不碰
        return n.matches(".*(" + KW + ").*");
    }

    public static String probe() {
        StringBuilder sb = new StringBuilder();
        sb.append("enabled=").append(enabled)
          .append(" askFirst=").append(askFirst)
          .append(" saf=").append(APP == null ? "noctx" : Saf.treeName(APP))
          .append(" pending=").append(pending().size());
        return sb.toString();
    }

    public static String fmtBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", b / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", b / 1048576.0);
    }
}
