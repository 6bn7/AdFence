package com.local.adfence;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SAF（存储访问框架）访问层——替代 "所有文件访问(MANAGE_EXTERNAL_STORAGE)" 权限。
 * 用户只在系统弹窗里授权一个目录（如 Download），本 App 就只能碰这一个目录。
 * 只依赖系统框架的 DocumentsContract，不引入任何第三方库。
 */
public final class Saf {

    private static final String SP = "adfence";
    private static final String KEY = "safTree";

    public static final class Doc {
        public String id;
        public String name;
        public String mime;
        public long size;
        public long lastModified;
        public boolean dir;

        public String toString() {
            return name;
        }
    }

    /** 用户授权的目录 URI（没授权返回 null） */
    public static Uri tree(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE);
            String s = sp.getString(KEY, null);
            if (s == null) return null;
            Uri u = Uri.parse(s);
            // 校验授权还在（用户可能在系统设置里撤销了）
            boolean granted = false;
            for (UriPermission p : c.getContentResolver().getPersistedUriPermissions()) {
                if (p.getUri().equals(u) && p.isWritePermission()) granted = true;
            }
            if (!granted) return null;
            // 实证校验：真的能读到这个目录才认（防止只有"记录"没有"权限"）
            if (!usable(c, u)) return null;
            return u;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 试着读取目录本身的名称，读得到才算授权可用 */
    private static boolean usable(Context c, Uri u) {
        Cursor cur = null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(u);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(u, docId);
            cur = c.getContentResolver().query(doc, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            return cur != null && cur.moveToFirst();
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (cur != null) cur.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void save(Context c, Uri u) {
        try {
            c.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
                    .putString(KEY, u == null ? null : u.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void clear(Context c) {
        save(c, null);
    }

    /** 该目录的显示名，给界面用 */
    public static String treeName(Context c) {
        Uri u = tree(c);
        if (u == null) return "未授权";
        try {
            String id = DocumentsContract.getTreeDocumentId(u);
            int i = id.lastIndexOf(':');
            return i >= 0 ? id.substring(i + 1) : id;
        } catch (Throwable t) {
            return "已授权";
        }
    }

    /** 列某个目录下的直接子项；parentDocId 为 null 时列根 */
    public static List<Doc> children(Context c, String parentDocId) {
        List<Doc> out = new ArrayList<Doc>();
        Uri u = tree(c);
        if (u == null) return out;
        Cursor cur = null;
        try {
            String docId = parentDocId == null ? DocumentsContract.getTreeDocumentId(u) : parentDocId;
            Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(u, docId);
            cur = c.getContentResolver().query(childUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            }, null, null, null);
            if (cur == null) return out;
            while (cur.moveToNext()) {
                Doc d = new Doc();
                d.id = cur.getString(0);
                d.name = cur.getString(1);
                d.mime = cur.getString(2);
                d.size = cur.isNull(3) ? 0 : cur.getLong(3);
                d.lastModified = cur.isNull(4) ? 0 : cur.getLong(4);
                d.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(d.mime);
                out.add(d);
            }
        } catch (Throwable t) {
            // 授权失效/目录被删，静默返回空
        } finally {
            try {
                if (cur != null) cur.close();
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static boolean delete(Context c, String docId) {
        Uri u = tree(c);
        if (u == null) return false;
        try {
            return DocumentsContract.deleteDocument(c.getContentResolver(),
                    DocumentsContract.buildDocumentUriUsingTree(u, docId));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 递归删除（目录先清空子项） */
    public static void deleteTree(Context c, Doc d, int depth) {
        if (d == null || depth > 6) return;
        if (d.dir) {
            for (Doc k : children(c, d.id)) deleteTree(c, k, depth + 1);
        }
        delete(c, d.id);
    }

    public static InputStream open(Context c, String docId) {
        Uri u = tree(c);
        if (u == null) return null;
        try {
            return c.getContentResolver().openInputStream(
                    DocumentsContract.buildDocumentUriUsingTree(u, docId));
        } catch (Throwable t) {
            return null;
        }
    }

    public static Uri docUri(Context c, String docId) {
        Uri u = tree(c);
        return u == null ? null : DocumentsContract.buildDocumentUriUsingTree(u, docId);
    }
}
