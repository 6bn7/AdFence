package com.local.adfence;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 纯 DNS 层拦截的 VpnService。
 * 设计要点：只把「假 DNS 服务器 IP」这一条路由进 tun，其余流量完全不进 VPN，
 * 所以不需要用户态 TCP/IP 栈（不做 NAT、不转发 TCP），实现小、崩溃面小。
 * 被拦域名用 A=0.0.0.0 应答；放行的查询用 protect() 过的 socket 转给上游 DNS。
 */
public class FenceVpnService extends VpnService implements Runnable {

    public static final String TAG = "AdFence";
    public static final String ACTION_START = "com.local.adfence.START";
    public static final String ACTION_STOP = "com.local.adfence.STOP";

    private static final String TUN_ADDR = "10.66.66.1";
    private static final String DNS_ADDR = "10.66.66.2";

    private static final int NOTI_ID = 1001;

    public static volatile boolean running = false;
    /** 诊断模式：只把目标 App 的 DNS 拉进 tun，日志里就只剩它一个人的域名 */
    public static volatile boolean onlyTarget = false;

    private ParcelFileDescriptor tun;
    /** 转发线程池：避免单个上游卡住把整台设备的 DNS 拖死（审计 M-4） */
    private java.util.concurrent.ExecutorService pool;
    private final java.util.concurrent.atomic.AtomicInteger inflight =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final int MAX_INFLIGHT = 64;
    private final Object tunWrite = new Object();
    private Thread worker;
    private Thread cleaner;
    private FileWriter logWriter;
    private long logged = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopFence();
            return START_NOT_STICKY;
        }
        if (!running) {
            startForeground(NOTI_ID, buildNotification());
            if (!establish()) {
                Log.e(TAG, "建立 tun 失败");
                stopFence();
                return START_NOT_STICKY;
            }
            running = true;
            Stats.startedAt = System.currentTimeMillis();
            worker = new Thread(this, "adfence-loop");
            worker.start();
            cleaner = new Thread(new Runnable() {
                @Override
                public void run() {
                    CacheCleaner.loop();
                }
            }, "adfence-clean");
            CacheCleaner.setContext(getApplicationContext());
            Stats.logAll = getSharedPreferences("adfence", MODE_PRIVATE).getBoolean("logAll", false);
            if (pool == null) pool = java.util.concurrent.Executors.newFixedThreadPool(6);
            cleaner.start();
            // 域名库体积大，放后台线程载入，不阻塞主线程
            if (Rules.libCount() == 0) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Rules.libEnabled = getSharedPreferences("adfence", MODE_PRIVATE)
                                    .getBoolean("libEnabled", true);
                            int n = Rules.loadLibrary(getAssets().open("adlib.txt"));
                            Log.i(TAG, "域名库载入 " + n + " 条 (启用=" + Rules.libEnabled + ")");
                            logLine("# 域名库 " + n + " 条, 启用=" + Rules.libEnabled);
                        } catch (Throwable t) {
                            Log.w(TAG, "域名库载入失败: " + t);
                        }
                    }
                }, "adfence-lib").start();
            } else {
                Rules.libEnabled = getSharedPreferences("adfence", MODE_PRIVATE)
                        .getBoolean("libEnabled", true);
            }
            logLine("# AdFence started, rules=" + Rules.size());
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN 授权被系统回收");
        stopFence();
    }

    @Override
    public void onDestroy() {
        stopFence();
        super.onDestroy();
    }

    private Notification buildNotification() {
        String ch = "adfence";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(ch) == null) {
                nm.createNotificationChannel(new NotificationChannel(ch, "AdFence", NotificationManager.IMPORTANCE_LOW));
            }
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setContentTitle("AdFence 运行中")
                .setContentText("已拦截 " + Stats.blocked.get() + " / 放行 " + Stats.allowed.get())
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    private boolean establish() {
        ParcelFileDescriptor p = tryEstablish(onlyTarget);
        if (p == null && onlyTarget) {
            Log.w(TAG, "只抓目标App 模式建立失败，退回全量");
            p = tryEstablish(false);
        }
        tun = p;
        if (tun == null) return false;
        return afterEstablish();
    }

    private ParcelFileDescriptor tryEstablish(boolean onlyTargetApp) {
        Builder b = new Builder();
        b.setSession("AdFence");
        b.setMtu(1500);
        b.addAddress(TUN_ADDR, 32);
        b.addDnsServer(DNS_ADDR);
        b.addRoute(DNS_ADDR, 32);      // 只有发往假 DNS 的包进 tun
        b.setBlocking(true);
        if (onlyTargetApp) {
            try {
                if (!Config.hasTarget()) return null;      // 没设置目标 App 就用不了这个模式
                b.addAllowedApplication(Config.targetPkg);
            } catch (Exception e) {
                Log.w(TAG, "addAllowedApplication 失败: " + e);
                return null;
            }
        }
        try {
            return b.establish();
        } catch (Exception e) {
            Log.e(TAG, "establish: " + e);
            return null;
        }
    }

    private boolean afterEstablish() {
        try {
            // 审计 M-1：日志改写入应用私有目录（外部存储路径在 Android ≤10 对其他应用可读）
            File f = new File(getFilesDir(), "adfence.log");
            if (f.exists() && f.length() > 3 * 1024 * 1024) f.delete();   // 超过 3MB 重新开始
            logWriter = new FileWriter(f, true);
        } catch (Exception e) {
            Log.w(TAG, "日志文件打不开: " + e);
        }
        return true;
    }

    private void stopFence() {
        running = false;
        if (tun != null) {
            try { tun.close(); } catch (Exception ignored) { }
            tun = null;
        }
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        if (cleaner != null) {
            cleaner.interrupt();
            cleaner = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        try { if (logWriter != null) logWriter.close(); } catch (Exception ignored) { }
        logWriter = null;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void run() {
        byte[] buf = new byte[32767];
        FileInputStream in;
        try {
            in = new FileInputStream(tun.getFileDescriptor());
        } catch (Exception e) {
            Log.e(TAG, "读取 tun 失败: " + e);
            return;
        }
        while (running) {
            int len;
            try {
                len = in.read(buf);
            } catch (Exception e) {
                if (running) Log.w(TAG, "read: " + e);
                break;
            }
            if (len <= 0) continue;
            try {
                handle(buf, len);
            } catch (Exception e) {
                Log.w(TAG, "handle: " + e);
            }
        }
    }

    // ---------- 包处理 ----------

    private void handle(byte[] p, int len) throws Exception {
        if (len < 28) return;
        if ((p[0] & 0xf0) != 0x40) return;                  // 只看 IPv4
        int ihl = (p[0] & 0x0f) * 4;
        if (ihl < 20 || len < ihl + 8) return;
        if ((p[9] & 0xff) != 17) return;                     // 只看 UDP
        int ipTotal = u16(p, 2);
        if (ipTotal > len) ipTotal = len;

        int sport = u16(p, ihl);
        int dport = u16(p, ihl + 2);
        if (dport != 53) return;                             // 只管 DNS
        int ulen = u16(p, ihl + 4);
        if (ulen < 8) return;
        int dnsOff = ihl + 8;
        int dnsLen = Math.min(ulen - 8, ipTotal - dnsOff);
        if (dnsLen < 12) return;

        // 只处理标准查询：QR=0、恰好一个问题段、名称无压缩指针且字符干净（审计 L-2 / L-3）
        if ((p[dnsOff + 2] & 0x80) != 0) return;
        if (u16(p, dnsOff + 4) != 1) return;                 // QDCOUNT 必须为 1
        int qEnd = nameEnd(p, dnsOff + 12, dnsOff + dnsLen);
        if (qEnd < 0 || qEnd + 4 > dnsOff + dnsLen) return;
        String qname = readName(p, dnsOff + 12, dnsOff + dnsLen);
        if (qname == null || qname.length() == 0 || !isSafeName(qname)) return;
        int qtype = u16(p, qEnd);

        int qTotal = (qEnd + 4) - (dnsOff + 12);             // 问题段长度

        if (Rules.isBlocked(qname)) {
            Stats.add(qname, Stats.KIND_BLOCK);
            logLine("BLOCK " + qname);
            int anLen = (qtype == 1 || qtype == 255) ? 16 : 0;
            byte[] resp = new byte[12 + qTotal + anLen];
            resp[0] = p[dnsOff];
            resp[1] = p[dnsOff + 1];                          // 事务 ID
            put16(resp, 2, 0x8180);                           // QR=1 RD=1 RA=1 NOERROR
            put16(resp, 4, 1);
            put16(resp, 6, anLen > 0 ? 1 : 0);
            System.arraycopy(p, dnsOff + 12, resp, 12, qTotal); // 原样回问题段
            if (anLen > 0) {                                   // A -> 0.0.0.0
                int o = 12 + qTotal;
                resp[o] = (byte) 0xc0; resp[o + 1] = 0x0c;
                put16(resp, o + 2, 1);      // TYPE A
                put16(resp, o + 4, 1);      // CLASS IN
                put16(resp, o + 6, 0); put16(resp, o + 8, 60);   // TTL 60
                put16(resp, o + 10, 4);
                resp[o + 12] = 0; resp[o + 13] = 0; resp[o + 14] = 0; resp[o + 15] = 0;
            }
            writeIpUdp(p, ihl, sport, resp, 0, resp.length);
            return;
        }

        Stats.add(qname, Stats.KIND_ALLOW);
        if (Stats.logAll) logLine("ALLOW " + qname);

        // 放行：转发放到线程池异步做，读循环不再被上游超时阻塞（审计 M-4）
        if (pool == null || inflight.get() >= MAX_INFLIGHT) return;   // 背压：超出上限直接丢弃
        final byte[] query = new byte[dnsLen];
        System.arraycopy(p, dnsOff, query, 0, dnsLen);
        final byte[] orig = new byte[ihl + 8];                // 回包只需要 IP 头 + UDP 头
        System.arraycopy(p, 0, orig, 0, ihl + 8);
        final int fIhl = ihl, fSport = sport;
        inflight.incrementAndGet();
        try {
            pool.execute(new Runnable() {
                public void run() {
                    try {
                        byte[] reply = forward(query);
                        if (reply != null) writeIpUdp(orig, fIhl, fSport, reply, 0, reply.length);
                    } catch (Throwable t) {
                        Log.w(TAG, "forward: " + t);
                    } finally {
                        inflight.decrementAndGet();
                    }
                }
            });
        } catch (Throwable t) {
            inflight.decrementAndGet();
        }
    }

    /** 名称只允许 DNS 合法字符：顺带挡掉日志注入（审计 L-2） */
    private static boolean isSafeName(String n) {
        if (n.length() > 253) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    /**
     * 用 protect() 过的**已连接** socket 把查询转给上游 DNS。
     * 审计 H-2：s.connect() 让内核只投递该对端的报文，再显式校验事务 ID 与 QR 位，
     * 避免链路上任何中间人伪造应答被原样回写给发起查询的应用。
     */
    private byte[] forward(byte[] query) {
        if (query.length < 12) return null;
        int id = u16(query, 0);
        for (String up : Config.upstreams()) {
            DatagramSocket s = null;
            try {
                InetAddress ia = InetAddress.getByName(up);
                s = new DatagramSocket();
                protect(s);
                s.connect(ia, 53);              // 只收该对端的包
                s.setSoTimeout(1500);           // 单个上游 1.5 秒（审计 M-4：收紧总超时）
                s.send(new DatagramPacket(query, query.length));
                byte[] r = new byte[4096];
                DatagramPacket rp = new DatagramPacket(r, r.length);
                s.receive(rp);
                int n = rp.getLength();
                if (n < 12) continue;                                   // 报文过短
                if (rp.getAddress() == null || !rp.getAddress().equals(ia)) continue;
                if (rp.getPort() != 53) continue;
                if (u16(r, 0) != id) continue;                          // 事务 ID 必须一致
                if ((r[2] & 0x80) == 0) continue;                       // 必须是应答
                byte[] out = new byte[n];
                System.arraycopy(r, 0, out, 0, n);
                return out;
            } catch (Exception e) {
                Log.w(TAG, "上游 " + up + " 失败: " + e);
            } finally {
                if (s != null) s.close();
            }
        }
        return null;
    }

    /** 把 DNS 应答包成 IP/UDP 回写给 tun（源/目的对调，UDP 校验置 0，IPv4 合法） */
    private void writeIpUdp(byte[] q, int ihl, int qSport, byte[] payload, int off, int plen) throws Exception {
        int udpLen = 8 + plen;
        int ipLen = 20 + udpLen;
        byte[] o = new byte[ipLen];
        o[0] = 0x45;
        put16(o, 2, ipLen);
        put16(o, 4, 0x2000 | (int) (System.nanoTime() & 0x1fff));
        put16(o, 6, 0x4000);
        o[8] = 64;
        o[9] = 17;
        System.arraycopy(q, 16, o, 12, 4);   // 源 = 原目的（假 DNS）
        System.arraycopy(q, 12, o, 16, 4);   // 目的 = 原源
        put16(o, 10, ipChecksum(o));
        put16(o, 20, u16(q, ihl + 2));       // 源端口 = 原目的端口 (53)
        put16(o, 22, qSport);                // 目的端口 = 原源端口
        put16(o, 24, udpLen);
        put16(o, 26, 0);
        System.arraycopy(payload, off, o, 28, plen);
        // 多线程转发后需要串行化写入（审计 M-4 的配套），否则包会互相穿插
        synchronized (tunWrite) {
            FileOutputStream out = new FileOutputStream(tun.getFileDescriptor());
            out.write(o);
            out.close();
        }
    }

    private void logLine(String s) {
        Log.i(TAG, s);
        if (logWriter != null && logged < 50000) {
            try {
                logWriter.write(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " " + s + "\n");
                logWriter.flush();
                logged++;
            } catch (Exception ignored) { }
        }
    }

    // ---------- 小工具 ----------

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xff) << 8) | (b[o + 1] & 0xff);
    }

    private static void put16(byte[] b, int o, int v) {
        b[o] = (byte) ((v >> 8) & 0xff);
        b[o + 1] = (byte) (v & 0xff);
    }

    private static int ipChecksum(byte[] b) {
        int sum = 0;
        for (int i = 0; i < 20; i += 2) sum += ((b[i] & 0xff) << 8) | (b[i + 1] & 0xff);
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        return (~sum) & 0xffff;
    }

    private static int nameEnd(byte[] d, int off, int limit) {
        int o = off;
        while (o < limit) {
            int l = d[o] & 0xff;
            if (l == 0) return o + 1;
            if ((l & 0xc0) == 0xc0) return o + 2 > limit ? -1 : o + 2;
            o += 1 + l;
        }
        return -1;
    }

    private static String readName(byte[] d, int off, int limit) {
        StringBuilder sb = new StringBuilder();
        int o = off;
        while (o < limit) {
            int l = d[o] & 0xff;
            if (l == 0) break;
            // 审计 L-3：查询报文本不应含压缩指针，出现即视为畸形，直接丢弃
            if ((l & 0xc0) != 0) return null;
            if (l > 63) return null;
            if (sb.length() > 0) sb.append('.');
            for (int i = 1; i <= l; i++) {
                if (o + i >= limit) return null;
                sb.append((char) (d[o + i] & 0xff));
            }
            o += 1 + l;
        }
        return sb.toString();
    }
}
