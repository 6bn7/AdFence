package com.local.adfence;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 广告素材留样 + 删除回收站。
 *
 * - samples/：清理广告素材前留的图片/视频，用于"看到原广告"再决定拦或放
 * - trash/  ：删除的非媒体文件先复制一份到这里，24 小时内可找回（审计 H-3 建议）
 *
 * 审计 M-3：文件名来自第三方 DocumentsProvider 的 DISPLAY_NAME，**不可信**，
 * 因此一律先 basename 净化 + 校验最终路径确实落在目标目录内（防路径穿越与配额绕过）。
 */
public final class SampleStore {

    public static final int MAX_SAMPLES = 24;
    public static final long MAX_SAMPLES_TOTAL = 48L * 1024 * 1024;
    public static final int MAX_TRASH = 60;
    public static final long MAX_TRASH_TOTAL = 64L * 1024 * 1024;
    public static final long TRASH_TTL_MS = 24L * 3600 * 1000;
    private static final long MAX_ONE = 12L * 1024 * 1024;

    public static File dir(Context c) {
        return ensure(new File(c.getExternalFilesDir(null), "samples"));
    }

    public static File trashDir(Context c) {
        return ensure(new File(c.getExternalFilesDir(null), "trash"));
    }

    private static File ensure(File d) {
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ---------------- 判定 ----------------

    public static boolean isMedia(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
                || n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".3gp");
    }

    public static boolean isVideo(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".3gp");
    }

    /**
     * 审计 M-3：把不可信的名称净化成安全文件名。
     * 只取 basename（去掉任何路径成分），拒绝空/./..，把非常规字符替换为下划线。
     */
    static String safeName(String raw) {
        if (raw == null) return null;
        String base = new File(raw.trim()).getName().trim();
        if (base.length() == 0 || base.equals(".") || base.equals("..")) return null;
        if (base.length() > 120) base = base.substring(base.length() - 120);
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '-' || ch == '_' || ch == ' ';
            sb.append(ok ? ch : '_');
        }
        String out = sb.toString().trim();
        return out.length() == 0 ? null : out;
    }

    // ---------------- 写入 ----------------

    /** 保存到 samples/（图片视频留样） */
    public static synchronized boolean archiveStream(Context c, String name, InputStream in) {
        return saveInto(dir(c), name, in, MAX_SAMPLES, MAX_SAMPLES_TOTAL);
    }

    /** 保存到 trash/（删除前兜底，24 小时可找回） */
    public static synchronized boolean trashStream(Context c, String name, InputStream in) {
        purgeTrash(c);
        return saveInto(trashDir(c), name, in, MAX_TRASH, MAX_TRASH_TOTAL);
    }

    private static boolean saveInto(File target, String name, InputStream in,
                                    int maxFiles, long maxTotal) {
        if (target == null || name == null || in == null) return false;
        try {
            String safe = safeName(name);
            if (safe == null) return false;
            File dst = new File(target, safe);
            // 二次确认：最终路径必须真的在目标目录内（防穿越）
            if (!dst.getCanonicalPath().startsWith(target.getCanonicalPath() + File.separator)) {
                return false;
            }
            if (dst.exists()) {
                in.close();
                return false;
            }
            File[] cur = target.listFiles();
            int n = cur == null ? 0 : cur.length;
            long total = 0;
            if (cur != null) for (File f : cur) total += f.length();
            if (n >= maxFiles || total > maxTotal) {
                in.close();
                return false;
            }
            OutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[1 << 16];
            int k;
            long written = 0;
            while ((k = in.read(buf)) > 0) {
                out.write(buf, 0, k);
                written += k;
                if (written > MAX_ONE) {          // 单个文件超限就放弃
                    out.close();
                    dst.delete();
                    in.close();
                    return false;
                }
            }
            out.flush();
            out.close();
            in.close();
            return dst.exists() && dst.length() > 0;
        } catch (Throwable t) {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /** 从文件路径复制（内部调用，源在应用可读范围内） */
    public static synchronized boolean archive(Context c, File src) {
        try {
            if (src == null || !src.isFile() || !isMedia(src.getName())) return false;
            if (src.length() <= 0 || src.length() > MAX_ONE) return false;
            return saveInto(dir(c), src.getName(), new FileInputStream(src), MAX_SAMPLES, MAX_SAMPLES_TOTAL);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------- 读取 / 清理 ----------------

    public static List<File> list(Context c) {
        return listFiles(dir(c), true);
    }

    public static List<File> trashList(Context c) {
        return listFiles(trashDir(c), false);
    }

    private static List<File> listFiles(File d, boolean mediaOnly) {
        List<File> out = new ArrayList<File>();
        File[] f = d.listFiles();
        if (f != null) {
            Arrays.sort(f, new java.util.Comparator<File>() {
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
            for (File x : f) {
                if (x.isFile() && (!mediaOnly || isMedia(x.getName()))) out.add(x);
            }
        }
        return out;
    }

    public static long totalBytes(Context c) {
        long t = 0;
        for (File f : list(c)) t += f.length();
        return t;
    }

    public static long trashBytes(Context c) {
        long t = 0;
        for (File f : trashList(c)) t += f.length();
        return t;
    }

    public static synchronized void delete(Context c, File f) {
        try {
            if (f != null) f.delete();
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void deleteAll(Context c) {
        for (File f : list(c)) f.delete();
    }

    public static synchronized void purgeTrash(Context c) {
        long now = System.currentTimeMillis();
        for (File f : trashList(c)) {
            if (now - f.lastModified() > TRASH_TTL_MS) f.delete();
        }
    }

    public static synchronized void clearTrash(Context c) {
        for (File f : trashList(c)) f.delete();
    }
}
