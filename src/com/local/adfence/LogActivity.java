package com.local.adfence;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 拦截详情：逐条 DNS 记录，**点任意一条可看人话结论并标记**。
 * 标记会写进 user-rules.txt / user-white.txt（立即生效）并留一份 domain-marks.txt 备查。
 */
public class LogActivity extends Activity {

    private static final int SHOW_MAX = 300;
    private static final int EXPORT_REQ = 7;

    private TextView summary;
    private LinearLayout rows;
    private Button onlyBtn;
    private Button logAllBtn;
    private boolean onlyBlocked = true;

    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            h.postDelayed(this, 2000);
        }
    };

    private File userStore() {
        return new File(getFilesDir(), "user-rules.txt");
    }

    private File whiteStore() {
        return new File(getFilesDir(), "user-white.txt");
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Rules.loadUser(userStore());
        Rules.loadWhite(whiteStore());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(26), dp(14), dp(10));

        TextView title = new TextView(this);
        title.setText("拦截详情");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

        summary = new TextView(this);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        summary.setTextColor(Color.parseColor("#333333"));
        summary.setPadding(0, dp(8), 0, dp(6));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        onlyBtn = mkBtn("只看拦截");
        Button all = mkBtn("全部查询");
        Button clear = mkBtn("清空列表");
        bar.addView(onlyBtn, lp(1));
        bar.addView(all, lp(1));
        bar.addView(clear, lp(1));

        LinearLayout bar2 = new LinearLayout(this);
        bar2.setOrientation(LinearLayout.HORIZONTAL);
        Button export = mkBtn("导出文件");
        Button top = mkBtn("被拦最多");
        logAllBtn = mkBtn("");
        logAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Stats.logAll = !Stats.logAll;
                getSharedPreferences("adfence", MODE_PRIVATE).edit()
                        .putBoolean("logAll", Stats.logAll).apply();
                render();
                Toast.makeText(LogActivity.this, Stats.logAll
                        ? "已开启全量记录：所有域名都会写进日志（含未拦截的），排查完请关掉"
                        : "已关闭全量记录：日志只记被拦域名", Toast.LENGTH_LONG).show();
            }
        });
        bar2.addView(export, lp(1));
        bar2.addView(top, lp(1));
        bar2.addView(logAllBtn, lp(1));

        TextView hint = new TextView(this);
        hint.setText("点任意一条记录 → 看人话结论、标记「是广告」或「不是广告（放行）」");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setPadding(0, dp(6), 0, dp(4));

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        sv.addView(rows);

        root.addView(title);
        root.addView(summary);
        root.addView(bar);
        root.addView(bar2);
        root.addView(hint);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        onlyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onlyBlocked = !onlyBlocked;
                render();
            }
        });
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onlyBlocked = false;
                render();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Stats.clear();
                render();
            }
        });
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TITLE, "adfence-log.txt");
                try {
                    startActivityForResult(i, EXPORT_REQ);
                } catch (Exception e) {
                    Toast.makeText(LogActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                }
            }
        });
        top.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTop();
            }
        });
        render();
    }

    private void render() {
        long up = Stats.startedAt == 0 ? 0 : (System.currentTimeMillis() - Stats.startedAt) / 1000;
        summary.setText(String.format(Locale.ROOT,
                "状态：%s　运行 %d 分 %d 秒\n查询 %d　拦截 %d　放行 %d\n"
                        + "规则合计 %d = 域名库 %d + 手工 %d + 自加 %d　例外 %d",
                FenceVpnService.running ? "拦截中" : "已停止", up / 60, up % 60,
                Stats.total.get(), Stats.blocked.get(), Stats.allowed.get(),
                Rules.size(), Rules.libCount(),
                Rules.count("block") + Rules.count("log2"), Rules.count("user"), Rules.count("white")));
        onlyBtn.setText(onlyBlocked ? "只看拦截（当前）" : "只看拦截");
        logAllBtn.setText(Stats.logAll ? "全量记录：开" : "全量记录：关");

        rows.removeAllViews();
        List<Stats.Ev> evs = Stats.recent(SHOW_MAX * 3);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
        int shown = 0;
        for (Stats.Ev e : evs) {
            if (onlyBlocked && e.k != Stats.KIND_BLOCK) continue;
            TextView t = new TextView(this);
            t.setText(f.format(new Date(e.t)) + "  " + (e.k == Stats.KIND_BLOCK ? "拦截" : "放行") + "  " + e.d);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setTextColor(e.k == Stats.KIND_BLOCK ? Color.parseColor("#B03030") : Color.parseColor("#555555"));
            t.setPadding(0, dp(7), 0, dp(7));
            final Stats.Ev ev = e;
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDetail(ev);
                }
            });
            rows.addView(t);
            if (++shown >= SHOW_MAX) break;
        }
        if (shown == 0) {
            TextView t = new TextView(this);
            t.setText("（暂无记录）");
            t.setTextColor(Color.parseColor("#888888"));
            rows.addView(t);
        }
    }

    /** 人话结论文本 */
    private String humanize(Stats.Ev ev) {
        StringBuilder sb = new StringBuilder();
        sb.append("处置：").append(ev.k == Stats.KIND_BLOCK ? "已拦截" : "已放行")
          .append("\n")
          .append("来源：").append(Rules.sourceOf(ev.d)).append("\n\n");

        String sentence = AdHints.sentence(ev.d);
        if (sentence != null) sb.append("· ").append(sentence).append("\n");

        List<String> comp = new ArrayList<String>();
        int adLike = 0;
        for (Stats.Ev e : Stats.recent(600)) {
            if (Math.abs(e.t - ev.t) > 2000 || e.d.equals(ev.d)) continue;
            if (comp.contains(e.d)) continue;
            comp.add(e.d);
            if (AdHints.adLike(e.d)) adLike++;
            if (comp.size() >= 6) break;
        }
        if (!comp.isEmpty()) {
            sb.append("· 同一时刻还在查：\n");
            for (String d : comp) {
                sb.append("    ").append(d).append(AdHints.adLike(d) ? "（已知广告特征）" : "").append("\n");
            }
        }
        sb.append("\n");
        if (sentence != null || adLike > 0) {
            sb.append("结论：像广告投放/素材域名。");
        } else {
            sb.append("结论：没有明显广告特征，也没跟已知广告域同时活动。"
                    + "如果它拦了你的正常功能，点下面的「不是广告」放行。");
        }
        return sb.toString();
    }

    private void showDetail(final Stats.Ev ev) {
        final boolean blocked = ev.k == Stats.KIND_BLOCK;
        new AlertDialog.Builder(this)
                .setTitle(ev.d)
                .setMessage(humanize(ev))
                .setPositiveButton("标为广告", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        markAd(ev.d);
                    }
                })
                .setNegativeButton("不是广告 → 放行", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        markNotAd(ev.d);
                    }
                })
                .setNeutralButton("关闭", null)
                .show();
    }

    private void markAd(String domain) {
        Rules.removeAny(domain, userStore(), whiteStore());   // 先清掉例外
        int r = Rules.addUser(domain, userStore());
        mark(domain, "广告");
        String msg = r == 0 ? "已标记为广告并加入拦截" : (r == 1 ? "已标记为广告（规则里已有）" : "已标记为广告");
        Toast.makeText(this, msg + "：" + domain, Toast.LENGTH_SHORT).show();
    }

    private void markNotAd(String domain) {
        Rules.removeAny(domain, userStore(), whiteStore());
        Rules.addWhite(domain, whiteStore());
        mark(domain, "放行");
        Toast.makeText(this, "已放行（例外优先级最高）：" + domain, Toast.LENGTH_SHORT).show();
    }

    private void mark(String domain, String label) {
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new java.io.FileOutputStream(new File(getFilesDir(), "domain-marks.txt"), true), "UTF-8");
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date())
                    + "\t" + label + "\t" + domain + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }

    private void showTop() {
        Map<String, Integer> m = Stats.topBlocked();
        List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(m.entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 30; i++) {
            sb.append(l.get(i).getValue()).append(" 次  ").append(l.get(i).getKey()).append("\n");
        }
        if (sb.length() == 0) sb.append("（还没有拦截记录）");
        new AlertDialog.Builder(this).setTitle("被拦最多").setMessage(sb.toString())
                .setPositiveButton("关闭", null).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == EXPORT_REQ && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                OutputStream out = getContentResolver().openOutputStream(uri);
                OutputStreamWriter w = new OutputStreamWriter(out, "UTF-8");
                SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);
                w.write(summary.getText().toString() + "\n\n");
                for (Stats.Ev e : Stats.recent(5000)) {
                    if (onlyBlocked && e.k != Stats.KIND_BLOCK) continue;
                    w.write(f.format(new Date(e.t)) + " " + (e.k == Stats.KIND_BLOCK ? "BLOCK" : "ALLOW")
                            + " " + e.d + "\n");
                }
                w.close();
                Toast.makeText(this, "已导出", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "导出失败：" + e, Toast.LENGTH_LONG).show();
            }
        }
    }

    private Button mkBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lp(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), w);
        p.setMargins(dp(3), 0, dp(3), 0);
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
