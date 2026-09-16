package com.local.adfence;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private static final int REQ_VPN = 1;
    private static final int REQ_SAF = 2;

    private TextView status;
    private TextView counters;
    private TextView cleanerInfo;
    private TextView probeText;
    private TextView modeText;
    private Button toggle;
    private Button cleanToggle;
    private Button permBtn;
    private Button onlyBtn;
    private Button libBtn;
    private Button askBtn;
    private SharedPreferences sp;

    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        sp = getSharedPreferences("adfence", MODE_PRIVATE);
        CacheCleaner.enabled = sp.getBoolean("autoclean", true);
        FenceVpnService.onlyTarget = sp.getBoolean("onlyTarget", false);
        Rules.libEnabled = sp.getBoolean("libEnabled", true);
        Rules.loadUser(new File(getFilesDir(), "user-rules.txt"));
        Rules.loadWhite(new File(getFilesDir(), "user-white.txt"));
        CacheCleaner.setContext(this);
        CacheCleaner.askFirst = sp.getBoolean("askFirst", true);
        Config.load(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("AdFence v" + Util.versionName(this));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);

        TextView sub = new TextView(this);
        sub.setText("广告域名拦截 + 广告缓存清理（本地 VPN，无需 root）");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setTextColor(Color.parseColor("#666666"));
        sub.setPadding(0, dp(6), 0, dp(16));

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, 0, 0, dp(6));

        counters = new TextView(this);
        counters.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        counters.setTextColor(Color.parseColor("#333333"));

        cleanerInfo = new TextView(this);
        cleanerInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cleanerInfo.setTextColor(Color.parseColor("#555555"));
        cleanerInfo.setPadding(0, dp(6), 0, 0);

        modeText = new TextView(this);
        modeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        modeText.setTextColor(Color.parseColor("#B03030"));
        modeText.setPadding(0, dp(6), 0, 0);

        probeText = new TextView(this);
        probeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        probeText.setTextColor(Color.parseColor("#777777"));
        probeText.setPadding(0, dp(4), 0, 0);

        toggle = mkBtn("启动拦截");
        Button detail = mkBtn("查看拦截详情");
        Button rules = mkBtn("拦截规则（加/删/例外）");
        cleanToggle = mkBtn("");
        permBtn = mkBtn("");
        onlyBtn = mkBtn("");
        libBtn = mkBtn("");
        Button change = mkBtn("更新内容 / 版本说明");
        Button gallery = mkBtn("广告样本（看原广告）");
        Button hist = mkBtn("清理记录 / 标记记录");
        Button cfgBtn = mkBtn("设置（目标 App / 上游 DNS）");
        askBtn = mkBtn("");

        cfgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ConfigActivity.class));
            }
        });

        hist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        askBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CacheCleaner.askFirst = !CacheCleaner.askFirst;
                sp.edit().putBoolean("askFirst", CacheCleaner.askFirst).apply();
                refresh();
                Toast.makeText(MainActivity.this, CacheCleaner.askFirst
                        ? "已开启：只列清单，你确认后才删（在「清理记录」里确认）"
                        : "已关闭：发现广告缓存就直接删（每次删除仍会留痕）", Toast.LENGTH_LONG).show();
            }
        });

        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GalleryActivity.class));
            }
        });

        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ChangelogActivity.class));
            }
        });

        detail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LogActivity.class));
            }
        });
        rules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, RulesActivity.class));
            }
        });
        cleanToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CacheCleaner.enabled = !CacheCleaner.enabled;
                sp.edit().putBoolean("autoclean", CacheCleaner.enabled).apply();
                refresh();
            }
        });
        permBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Saf.tree(MainActivity.this) == null) {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        i.putExtra("android.provider.extra.INITIAL_URI",
                                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
                        startActivityForResult(i, REQ_SAF);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "打不开文件选择器", Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                probeText.setText(CacheCleaner.probe());
                long[] r = CacheCleaner.cleanNow();
                Toast.makeText(MainActivity.this, r[1] > 0
                                ? "已清理 " + r[1] + " 项 / " + CacheCleaner.fmtBytes(r[0])
                                : (CacheCleaner.askFirst ? "已扫描，请在「清理记录」里确认" : "没有发现需要清理的东西"),
                        Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
        onlyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Config.hasTarget()) {
                    Toast.makeText(MainActivity.this,
                            "先在「设置」里填目标 App 的包名，才能用这个模式", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(MainActivity.this, ConfigActivity.class));
                    return;
                }
                FenceVpnService.onlyTarget = !FenceVpnService.onlyTarget;
                sp.edit().putBoolean("onlyTarget", FenceVpnService.onlyTarget).apply();
                refresh();
                if (FenceVpnService.running) {
                    Intent stop = new Intent(MainActivity.this, FenceVpnService.class);
                    stop.setAction(FenceVpnService.ACTION_STOP);
                    startService(stop);
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            start();
                            refresh();
                        }
                    }, 1200);
                }
                Toast.makeText(MainActivity.this, FenceVpnService.onlyTarget
                        ? "已开启：只过滤目标 App 的 DNS，其它 App 暂时不受管（诊断用）"
                        : "已关闭：恢复过滤所有 App", Toast.LENGTH_LONG).show();
            }
        });
        libBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Rules.libEnabled = !Rules.libEnabled;
                sp.edit().putBoolean("libEnabled", Rules.libEnabled).apply();
                refresh();
                Toast.makeText(MainActivity.this, Rules.libEnabled
                        ? "内置广告域名库：已启用（" + Rules.libCount() + " 条，立即生效）"
                        : "内置广告域名库：已停用（自加规则不受影响）", Toast.LENGTH_SHORT).show();
            }
        });
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FenceVpnService.running) {
                    Intent i = new Intent(MainActivity.this, FenceVpnService.class);
                    i.setAction(FenceVpnService.ACTION_STOP);
                    startService(i);
                } else {
                    Intent i = VpnService.prepare(MainActivity.this);
                    if (i != null) startActivityForResult(i, REQ_VPN);
                    else start();
                }
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 800);
            }
        });

        TextView note = new TextView(this);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(Color.parseColor("#888888"));
        note.setPadding(0, dp(16), 0, 0);
        note.setText("只拦 DNS：把假 DNS 服务器 10.66.66.2 的路由拉进本地 tun，命中规则就回 0.0.0.0，"
                + "其余查询原样转发，其他流量完全不经过本 App。\n"
                + "自动清理每 30 秒扫一次「你授权的那个目录」（默认 Download），只删三类东西：\n"
                + "　① .csj / .jsc / ksadsdk 这类已知广告目录　② 名字以广告 SDK 前缀开头的英文目录\n"
                + "　③ 命中广告关键字、且是图片/视频/apk/db 这类广告产物扩展名的文件\n"
                + "zip/txt/doc/pdf 一律不碰；中文名目录一律不碰。删之前图片视频会先留样，每次删除都写日志。\n"
                + "目标 App 自己的 Android/data 被系统 FUSE 封死，本机删不了，由电脑端脚本删+上锁。\n"
                + "非图片文件删除前会先放进回收站（24 小时内可在「清理记录」里找回）；"
                + "留副本失败时会跳过删除、不硬删。\n"
                + "⚠ 广告**目录**是整树删除、不进回收站，不可撤销（目录名已被严格限定为广告 SDK 目录）。\n"
                + "「只抓目标App」是诊断开关：开启后日志里只有目标 App 的域名，"
                + "方便看清它的广告/跳转走哪条链；平时关掉。\n\n"
                + "隐私：日志写在应用私有目录（其他应用读不到），默认只记被拦域名；"
                + "需要看全部查询时在「拦截详情」里手动开启，用完记得关。");

        root.addView(title);
        root.addView(sub);
        root.addView(status);
        root.addView(counters);
        root.addView(cleanerInfo);
        root.addView(modeText);
        root.addView(toggle, lp(16));
        root.addView(detail, lp(8));
        root.addView(rules, lp(8));
        root.addView(gallery, lp(8));
        root.addView(hist, lp(8));
        root.addView(cfgBtn, lp(8));
        root.addView(cleanToggle, lp(8));
        root.addView(askBtn, lp(8));
        root.addView(permBtn, lp(8));
        root.addView(onlyBtn, lp(8));
        root.addView(libBtn, lp(8));
        root.addView(change, lp(8));
        root.addView(probeText);
        root.addView(note);
        scroll.addView(root);
        setContentView(scroll);
        refresh();
    }

    private void start() {
        Intent i = new Intent(this, FenceVpnService.class);
        i.setAction(FenceVpnService.ACTION_START);
        startForegroundService(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SAF) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                Uri u = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(u,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                Saf.save(this, u);
                Toast.makeText(this, "已授权目录：" + Saf.treeName(this), Toast.LENGTH_SHORT).show();
            }
            refresh();
            return;
        }
        if (req == REQ_VPN && res == RESULT_OK) start();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 600);
    }

    private void refresh() {
        boolean on = FenceVpnService.running;
        status.setText(on ? "状态：● 拦截中" : "状态：○ 已停止");
        status.setTextColor(on ? Color.parseColor("#127A2E") : Color.parseColor("#999999"));
        toggle.setText(on ? "停止拦截" : "启动拦截");

        boolean saf = Saf.tree(this) != null;
        counters.setText("已拦截：" + Stats.blocked.get() + " 次　已放行：" + Stats.allowed.get() + " 次\n"
                + "规则 合计 " + Rules.size() + " 条 = 域名库 " + Rules.libCount()
                + " + 手工 " + (Rules.count("block") + Rules.count("log2"))
                + " + 自加 " + Rules.userCount() + "　例外 " + Rules.whiteCount());
        cleanerInfo.setText("广告缓存清理：" + (CacheCleaner.enabled ? "开" : "关")
                + "　授权目录：" + Saf.treeName(this)
                + "　已清 " + Stats.cleanRuns.get() + " 次 / " + CacheCleaner.fmtBytes(Stats.cleanBytes.get())
                + "　留样 " + Stats.sampleSaved.get() + " 份\n"
                + (saf ? "只动这一个目录里的广告产物，每次删除都留痕（清理记录）"
                       : "没授权任何目录 —— 点下面按钮选 Download（只授权这一个目录，不用「所有文件访问」）"));
        modeText.setText(FenceVpnService.onlyTarget
                ? "⚠ 诊断模式：只过滤 " + Config.targetPkg + "，其它 App 不受管"
                : "");
        cleanToggle.setText(CacheCleaner.enabled ? "自动清理：开（点一下关）" : "自动清理：关（点一下开）");
        permBtn.setText(saf ? "立即清理（授权目录内）" : "授权清理目录（选 Download）");
        permBtn.setTextColor(saf ? Color.parseColor("#127A2E") : Color.parseColor("#B03030"));
        askBtn.setText(CacheCleaner.askFirst ? "清理前先问我：开" : "清理前先问我：关");
        onlyBtn.setText(FenceVpnService.onlyTarget
                ? "只抓目标App：开（点一下恢复全量）"
                : (Config.hasTarget() ? "只抓目标App：关（诊断用）" : "只抓目标App：关（需先在设置里填包名）"));
        libBtn.setText(Rules.libEnabled
                ? "内置广告域名库：开（" + Rules.libCount() + " 条，点一下停用）"
                : "内置广告域名库：关（点一下启用）");
        libBtn.setTextColor(Rules.libEnabled ? Color.parseColor("#127A2E") : Color.parseColor("#999999"));
    }

    private Button mkBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return b;
    }

    private LinearLayout.LayoutParams lp(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        p.topMargin = dp(topMargin == 0 ? 16 : topMargin);
        return p;
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
