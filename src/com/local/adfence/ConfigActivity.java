package com.local.adfence;

import android.app.Activity;
import android.graphics.Color;
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

/** 设置：目标 App 包名 + 上游 DNS（开源版不预置任何具体 App） */
public class ConfigActivity extends Activity {

    private EditText pkgBox, dnsBox;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Config.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(16));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        TextView h1 = head("目标 App 包名（可留空）");
        TextView d1 = desc("用于「只抓目标App」诊断模式和逐 App 排查。\n"
                + "填了它，就只盯这一个 App 的 DNS，方便看清它的广告走哪条链。\n"
                + "查包名：系统设置 → 应用管理 → 该 App → 应用详情（或长按图标看详情）。\n"
                + "例：com.tencent.mm（微信）");

        pkgBox = box(Config.targetPkg, InputType.TYPE_TEXT_VARIATION_URI,
                "com.example.app（留空 = 不启用）");

        TextView h2 = head("上游 DNS");
        TextView d2 = desc("命中的域名会被拦，其余查询原样转发给这台解析器。\n"
                + "默认 223.5.5.5（阿里公共 DNS）。可换成本地/自建解析器，例如 192.168.1.1。");

        dnsBox = box(Config.upstreamDns, InputType.TYPE_TEXT_VARIATION_URI, "223.5.5.5");

        Button save = new Button(this);
        save.setText("保存（立即生效）");
        save.setAllCaps(false);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Config.save(ConfigActivity.this, pkgBox.getText().toString(), dnsBox.getText().toString());
                Toast.makeText(ConfigActivity.this,
                        "已保存\n目标App：" + (Config.hasTarget() ? Config.targetPkg : "（未设置）")
                                + "\n上游 DNS：" + Config.upstreamDns, Toast.LENGTH_LONG).show();
                finish();
            }
        });

        Button back = new Button(this);
        back.setText("返回");
        back.setAllCaps(false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ScrollView sv = new ScrollView(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.addView(h1);
        inner.addView(d1);
        inner.addView(pkgBox);
        inner.addView(h2);
        inner.addView(d2);
        inner.addView(dnsBox);
        sv.addView(inner);

        root.addView(title);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(save, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(back, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        setContentView(root);
    }

    private TextView head(String t) {
        TextView v = new TextView(this);
        v.setText("\n" + t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        v.setPadding(0, dp(12), 0, dp(4));
        return v;
    }

    private TextView desc(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTextColor(Color.parseColor("#666666"));
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private EditText box(String val, int type, String hint) {
        EditText e = new EditText(this);
        e.setText(val);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return e;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
