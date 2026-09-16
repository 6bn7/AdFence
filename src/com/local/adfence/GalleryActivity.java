package com.local.adfence;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * 广告样本：清理广告缓存前留的图/视频，让用户直接看到"原广告"，
 * 并对每一张做标记（是广告 → 保持/加强拦截；不是广告 → 删样本并考虑放行来源域）。
 */
public class GalleryActivity extends Activity {

    private LinearLayout listBox;
    private TextView head;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText("广告样本");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        head = new TextView(this);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        head.setTextColor(Color.parseColor("#666666"));
        head.setPadding(0, dp(8), 0, dp(8));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = new Button(this);
        clear.setText("清空样本");
        clear.setAllCaps(false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SampleStore.deleteAll(GalleryActivity.this);
                Toast.makeText(GalleryActivity.this, "已清空样本", Toast.LENGTH_SHORT).show();
                render();
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
        bar.addView(clear, lp(1));
        bar.addView(back, lp(1));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        sv.addView(listBox);

        root.addView(title);
        root.addView(head);
        root.addView(bar);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
    }

    private void render() {
        listBox.removeAllViews();
        List<File> fs = SampleStore.list(this);
        head.setText("共 " + fs.size() + " 个样本 / " + CacheCleaner.fmtBytes(SampleStore.totalBytes(this))
                + "\n清理广告缓存前会自动留一份，用来核对「这到底是不是广告」。"
                + "\n（只留图片和视频，最多 24 个）");
        if (fs.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("\n还没有样本。\n\n出现广告素材被清理时（比如 Download 里的 .csj、AdGain 图片包），"
                    + "这里就会出现对应的图/视频，你可以直接看到原广告。");
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTextColor(Color.parseColor("#888888"));
            listBox.addView(t);
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);
        for (final File file : fs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(10), 0, dp(14));

            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(dp(260));
            Bitmap bm = SampleStore.isVideo(file.getName()) ? videoFrame(file) : image(file);
            if (bm != null) iv.setImageBitmap(bm);
            card.addView(iv);

            TextView info = new TextView(this);
            info.setText(file.getName() + "\n" + CacheCleaner.fmtBytes(file.length())
                    + "　" + f.format(new Date(file.lastModified())));
            info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            info.setTextColor(Color.parseColor("#666666"));
            card.addView(info);

            final String hint = AdHints.suggestedDomain(file.getName());
            if (hint != null) {
                TextView h = new TextView(this);
                h.setText("名字特征像「" + AdHints.sdkOf(file.getName()) + "」");
                h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                h.setTextColor(Color.parseColor("#B03030"));
                card.addView(h);
            }

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button yes = new Button(this);
            yes.setText("是广告");
            yes.setAllCaps(false);
            yes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StringBuilder msg = new StringBuilder("已标记为广告");
                    if (hint != null) {
                        int r = Rules.addUser(hint, new File(getFilesDir(), "user-rules.txt"));
                        if (r == 0) msg.append("，并把 ").append(hint).append(" 加入拦截");
                        else if (r == 1) msg.append("（").append(hint).append(" 已在拦截里）");
                    }
                    mark(file, "广告");
                    Toast.makeText(GalleryActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
            Button no = new Button(this);
            no.setText("不是广告");
            no.setAllCaps(false);
            no.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SampleStore.delete(GalleryActivity.this, file);
                    mark(file, "非广告");
                    if (hint == null) {
                        Toast.makeText(GalleryActivity.this, "已删除该样本", Toast.LENGTH_SHORT).show();
                        render();
                        return;
                    }
                    new AlertDialog.Builder(GalleryActivity.this)
                            .setTitle("已删除样本")
                            .setMessage("这张对应的来源域是 " + hint + "。\n要不要把它放行（加入例外）？")
                            .setPositiveButton("放行 " + hint, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) {
                                    Rules.addWhite(hint, new File(getFilesDir(), "user-white.txt"));
                                    Toast.makeText(GalleryActivity.this, "已放行：" + hint, Toast.LENGTH_SHORT).show();
                                    render();
                                }
                            })
                            .setNegativeButton("仍然拦着", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) {
                                    render();
                                }
                            })
                            .show();
                }
            });
            btns.addView(yes, lp(1));
            btns.addView(no, lp(1));
            card.addView(btns);

            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#DDDDDD"));
            card.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            listBox.addView(card);
        }
    }

    /** 标记记录落盘，方便以后回看/汇总 */
    private void mark(File f, String label) {
        try {
            File log = new File(getFilesDir(), "sample-marks.txt");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(log, true), "UTF-8");
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date())
                    + "\t" + label + "\t" + f.getName() + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }

    private Bitmap image(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int scale = 1;
            while (o.outWidth / scale > 720 || o.outHeight / scale > 720) scale *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap videoFrame(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            return r.getFrameAtTime(0);
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private LinearLayout.LayoutParams lp(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), w);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
