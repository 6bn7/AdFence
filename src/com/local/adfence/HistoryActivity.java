package com.local.adfence;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 清理记录 / 标记记录 / 待确认删除清单 —— 全部可查，解决"删了什么查不出来"的问题。
 */
public class HistoryActivity extends Activity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        CacheCleaner.setContext(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText("清理记录 / 标记记录");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);

        Button back = new Button(this);
        back.setText("返回");
        back.setAllCaps(false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        root.addView(title);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(back, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        setContentView(root);
        render();
    }

    private void render() {
        box.removeAllViews();

        // ① 待确认清单
        List<CacheCleaner.Pend> pend = CacheCleaner.pending();
        if (!pend.isEmpty()) {
            long total = 0;
            StringBuilder sb = new StringBuilder();
            for (CacheCleaner.Pend p : pend) {
                total += p.size;
                sb.append("· ").append(p.path).append("　").append(CacheCleaner.fmtBytes(p.size)).append('\n');
            }
            section("待你确认的清理（" + pend.size() + " 项 / " + CacheCleaner.fmtBytes(total) + "）", sb.toString(),
                    "全部删除", new Runnable() {
                        public void run() {
                            long[] r = CacheCleaner.commitPending();
                            Toast.makeText(HistoryActivity.this,
                                    "已删除 " + r[1] + " 项 / " + CacheCleaner.fmtBytes(r[0]),
                                    Toast.LENGTH_SHORT).show();
                            render();
                        }
                    },
                    "先不删", new Runnable() {
                        public void run() {
                            CacheCleaner.clearPending();
                            render();
                        }
                    });
        }

        // ② 删除记录
        String del = CacheCleaner.lastDeleted(100);
        section("最近删除记录（deleted.log）",
                del.length() == 0 ? "（还没有删除记录）\n每条都会记下：时间 / 类型 / 字节数 / 完整路径" : del,
                null, null, null, null);

        // ③ 标记记录
        StringBuilder mk = new StringBuilder();
        mk.append("— 域名标记（domain-marks.txt）—\n").append(tail(new File(getFilesDir(), "domain-marks.txt"), 60));
        mk.append("\n— 样本标记（sample-marks.txt）—\n").append(tail(new File(getFilesDir(), "sample-marks.txt"), 40));
        section("你的标记记录（是广告 / 不是广告）", mk.toString(), null, null, null, null);

        // ④ 授权目录管理
        section("当前授权的清理目录", Saf.treeName(this) + "\n（只授权这一个目录，App 不持有「所有文件访问」）",
                "取消目录授权", new Runnable() {
                    public void run() {
                        Saf.clear(HistoryActivity.this);
                        Toast.makeText(HistoryActivity.this, "已取消授权（回主界面可重新选目录）",
                                Toast.LENGTH_SHORT).show();
                        render();
                    }
                }, null, null);

        // ⑤ 回收站（审计 H-3：删除可撤销）
        SampleStore.purgeTrash(this);
        section("回收站（删除的非媒体文件，24 小时内可找回）",
                "当前 " + SampleStore.trashList(this).size() + " 个 / "
                        + CacheCleaner.fmtBytes(SampleStore.trashBytes(this))
                        + "\n超过 24 小时会自动清除。",
                "清空回收站", new Runnable() {
                    public void run() {
                        SampleStore.clearTrash(HistoryActivity.this);
                        Toast.makeText(HistoryActivity.this, "已清空回收站", Toast.LENGTH_SHORT).show();
                        render();
                    }
                }, null, null);
    }

    private void section(String head, String body, String okText, final Runnable ok,
                         String noText, final Runnable no) {
        TextView h = new TextView(this);
        h.setText("\n" + head);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        h.setTextColor(Color.parseColor("#111111"));
        h.setPadding(0, dp(10), 0, dp(4));
        box.addView(h);

        TextView t = new TextView(this);
        t.setText(body);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(Color.parseColor("#444444"));
        box.addView(t);

        if (okText != null) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button b1 = new Button(this);
            b1.setText(okText);
            b1.setAllCaps(false);
            b1.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (ok != null) ok.run();
                }
            });
            row.addView(b1, new LinearLayout.LayoutParams(0, dp(46), 1f));
            if (noText != null) {
                Button b2 = new Button(this);
                b2.setText(noText);
                b2.setAllCaps(false);
                b2.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (no != null) no.run();
                    }
                });
                row.addView(b2, new LinearLayout.LayoutParams(0, dp(46), 1f));
            }
            box.addView(row);
        }
    }

    private String tail(File f, int max) {
        List<String> lines = new ArrayList<String>();
        try {
            if (!f.exists()) return "（空）\n";
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
            br.close();
        } catch (Throwable t) {
            return "（读取失败）\n";
        }
        if (lines.isEmpty()) return "（空）\n";
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, lines.size() - max);
        for (int i = lines.size() - 1; i >= from; i--) sb.append(lines.get(i)).append('\n');
        return sb.toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
