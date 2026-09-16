package com.local.adfence;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

/** 更新内容：从 assets/changelog.txt 读，避免在代码里硬编码历史 */
public class ChangelogActivity extends Activity {

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("更新内容");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        TextView ver = new TextView(this);
        ver.setText("当前版本 " + Util.versionName(this));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ver.setTextColor(Color.parseColor("#666666"));
        ver.setPadding(0, dp(6), 0, dp(12));

        TextView body = new TextView(this);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextColor(Color.parseColor("#222222"));
        body.setText(readAssets("changelog.txt"));

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
        inner.addView(body);
        sv.addView(inner);
        sv.setPadding(0, 0, 0, dp(8));

        root.addView(title);
        root.addView(ver);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        setContentView(root);
    }

    private String readAssets(String name) {
        try {
            InputStream in = getAssets().open(name);
            byte[] buf = new byte[in.available() > 0 ? in.available() : 8192];
            int n = in.read(buf);
            in.close();
            return new String(buf, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) {
            return "（读不到 " + name + "：" + e + "）";
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
