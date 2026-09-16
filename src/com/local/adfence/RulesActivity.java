package com.local.adfence;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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
import java.util.List;

/**
 * 规则管理：加拦截 / 加例外（放行）/ 删除。
 * 例外优先级最高——误伤了就在这儿填域名点「加例外」，立刻恢复，不用改代码。
 */
public class RulesActivity extends Activity {

    private EditText input;
    private TextView summary;
    private TextView listView;
    private Activity me;

    private File userStore() {
        return new File(me.getFilesDir(), "user-rules.txt");
    }

    private File whiteStore() {
        return new File(me.getFilesDir(), "user-white.txt");
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        me = this;
        Rules.loadUser(userStore());
        Rules.loadWhite(whiteStore());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText("拦截规则");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        summary = new TextView(this);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summary.setTextColor(Color.parseColor("#333333"));
        summary.setPadding(0, dp(10), 0, dp(6));

        input = new EditText(this);
        input.setHint("输入域名，如 ad.example.com");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(6), 0, 0);
        Button add = mkBtn("加拦截");
        Button white = mkBtn("加例外(放行)");
        Button del = mkBtn("删除");
        bar.addView(add, lp(1));
        bar.addView(white, lp(1.3f));
        bar.addView(del, lp(1));

        LinearLayout bar2 = new LinearLayout(this);
        bar2.setOrientation(LinearLayout.HORIZONTAL);
        bar2.setPadding(0, dp(6), 0, dp(6));
        Button clr = mkBtn("清空自加");
        Button clrw = mkBtn("清空例外");
        bar2.addView(clr, lp(1));
        bar2.addView(clrw, lp(1));

        TextView hint = new TextView(this);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setText("匹配该域名本身及其所有子域，改完立即生效，不用重启拦截。\n"
                + "例：加拦截 kwimgs.com → 连 p66.a.kwimgs.com 一起拦；\n"
                + "哪条拦错了、把 App 功能弄坏了 → 填域名点「加例外」，马上恢复。\n"
                + "删除按钮：自加拦截和例外里都删。");

        listView = new TextView(this);
        listView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        listView.setTypeface(Typeface.MONOSPACE);
        listView.setPadding(0, dp(12), 0, 0);

        ScrollView sv = new ScrollView(this);
        sv.addView(listView);

        root.addView(title);
        root.addView(summary);
        root.addView(input);
        root.addView(bar);
        root.addView(bar2);
        root.addView(hint);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String raw = input.getText().toString();
                int r = Rules.addUser(raw, userStore());
                String msg;
                if (r == 0) {
                    msg = "已加拦截：" + Rules.norm(raw);
                    input.setText("");
                } else if (r == 1) msg = "内置规则里已经有这个域名了";
                else if (r == 2) msg = "已经加过了";
                else if (r == 4) msg = "它在例外列表里，先删掉例外或直接点「加例外」";
                else msg = "格式不对，要像 ad.example.com 这样";
                Toast.makeText(me, msg, Toast.LENGTH_SHORT).show();
                render();
            }
        });
        white.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String raw = input.getText().toString();
                int r = Rules.addWhite(raw, whiteStore());
                String msg;
                if (r == 0) {
                    msg = "已加例外（放行）：" + Rules.norm(raw);
                    input.setText("");
                } else if (r == 2) msg = "已经在例外里了";
                else msg = "格式不对，要像 example.com 这样";
                Toast.makeText(me, msg, Toast.LENGTH_SHORT).show();
                render();
            }
        });
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String raw = input.getText().toString().trim();
                if (raw.length() == 0) {
                    List<String> a = Rules.userList();
                    List<String> b = Rules.whiteList();
                    if (a.isEmpty() && b.isEmpty()) {
                        Toast.makeText(me, "自加和例外都是空的", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    input.setText(b.isEmpty() ? a.get(a.size() - 1) : b.get(b.size() - 1));
                    return;
                }
                boolean ok = Rules.removeAny(raw, userStore(), whiteStore());
                Toast.makeText(me, ok ? "已删除：" + Rules.norm(raw) : "自加/例外里都没有这条",
                        Toast.LENGTH_SHORT).show();
                render();
            }
        });
        clr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Rules.clearUser(userStore());
                Toast.makeText(me, "已清空自加拦截", Toast.LENGTH_SHORT).show();
                render();
            }
        });
        clrw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Rules.clearWhite(whiteStore());
                Toast.makeText(me, "已清空例外", Toast.LENGTH_SHORT).show();
                render();
            }
        });
        render();
    }

    private void render() {
        summary.setText("内置广告规则 " + (Rules.count("block") + Rules.count("log2")) + " 条"
                + "　自加 " + Rules.userCount() + " 条"
                + "　例外 " + Rules.whiteCount() + " 条");

        List<String> ul = Rules.userList();
        List<String> wl = Rules.whiteList();
        StringBuilder sb = new StringBuilder();
        sb.append("【例外 · 放行】").append(wl.size()).append(" 条（优先级最高）\n");
        if (wl.isEmpty()) sb.append("  （空）\n");
        for (String d : wl) sb.append("  ").append(d).append('\n');
        sb.append("\n【自加 · 拦截】").append(ul.size()).append(" 条\n");
        if (ul.isEmpty()) sb.append("  （空）\n");
        for (String d : ul) sb.append("  ").append(d).append('\n');
        listView.setText(sb.toString());
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
