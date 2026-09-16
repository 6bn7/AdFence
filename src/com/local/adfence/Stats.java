package com.local.adfence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 运行时统计 + 事件环形缓冲（供界面显示，不落盘） */
public final class Stats {

    public static final int CAP = 4000;
    public static final int KIND_BLOCK = 0;
    public static final int KIND_ALLOW = 1;

    public static final AtomicLong total = new AtomicLong();
    public static final AtomicLong blocked = new AtomicLong();
    public static final AtomicLong allowed = new AtomicLong();

    /** 自动清理广告缓存的统计 */
    public static final AtomicLong cleanRuns = new AtomicLong();
    public static final AtomicLong cleanFiles = new AtomicLong();
    public static final AtomicLong cleanBytes = new AtomicLong();
    /** 留样数量（清理广告缓存前存下来的素材份数） */
    public static final AtomicLong sampleSaved = new AtomicLong();
    /** 进回收站的文件数（删除前兜底，24 小时内可找回） */
    public static final AtomicLong trashed = new AtomicLong();
    /** 因留副本失败而跳过的删除数（审计 N-3 的失败安全） */
    public static final AtomicLong skipped = new AtomicLong();

    /**
     * 是否把「放行」的查询也写进日志文件。
     * 审计 M-1：默认 false —— 默认只记被拦的域名，避免把整机上网足迹落盘；
     * 需要排查时由用户显式开启（界面里的「记录全部查询」）。
     */
    public static volatile boolean logAll = false;
    public static volatile long startedAt = 0;

    public static final class Ev {
        public final long t;
        public final String d;
        public final int k;

        Ev(long t, String d, int k) {
            this.t = t;
            this.d = d;
            this.k = k;
        }
    }

    private static final Ev[] ring = new Ev[CAP];
    private static int head = 0;
    private static int cnt = 0;
    private static final HashMap<String, Integer> blockedBy = new HashMap<String, Integer>();

    public static synchronized void add(String dom, int kind) {
        total.incrementAndGet();
        if (kind == KIND_BLOCK) {
            blocked.incrementAndGet();
            if (blockedBy.size() > 1000) blockedBy.clear();   // 审计 L-4：加上限，避免被撑爆
            Integer c = blockedBy.get(dom);
            blockedBy.put(dom, c == null ? 1 : c + 1);
        } else {
            allowed.incrementAndGet();
        }
        ring[head] = new Ev(System.currentTimeMillis(), dom, kind);
        head = (head + 1) % CAP;
        if (cnt < CAP) cnt++;
    }

    public static synchronized List<Ev> recent(int max) {
        List<Ev> out = new ArrayList<Ev>();
        for (int i = 0; i < cnt && out.size() < max; i++) {
            int idx = (head - 1 - i + CAP * 2) % CAP;
            Ev e = ring[idx];
            if (e != null) out.add(e);
        }
        return out;
    }

    public static synchronized Map<String, Integer> topBlocked() {
        return new HashMap<String, Integer>(blockedBy);
    }

    public static synchronized void clear() {
        for (int i = 0; i < CAP; i++) ring[i] = null;
        head = 0;
        cnt = 0;
        blockedBy.clear();
        total.set(0);
        blocked.set(0);
        allowed.set(0);
        cleanRuns.set(0);
        cleanFiles.set(0);
        cleanBytes.set(0);
        sampleSaved.set(0);
        trashed.set(0);
        skipped.set(0);
    }
}
