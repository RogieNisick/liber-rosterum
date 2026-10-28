package com.medoedy.liber;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import static com.medoedy.liber.Ui.dp;

/**
 * Поиск картинок юнита в Google прямо в приложении.
 * Удерживайте любую картинку — она станет портретом юнита.
 */
final class ImageSearchScreen extends Screen {
    final String entryId, unit, faction;
    final Runnable done;
    WebView web;
    ProgressBar bar;
    int mode = 0;
    String lastQuery;
    LinearLayout chipRow;

    static final String[] MODES = {"Миниатюра", "Покрас легиона", "Арт", "Forge World", "Конверсии"};

    ImageSearchScreen(MainActivity a, String entryId, String unit, String faction, Theme t, Runnable done) {
        super(a);
        this.entryId = entryId;
        this.unit = unit;
        this.faction = faction == null ? "" : faction.replaceAll("^[IVXL]+ - ", "");
        this.done = done;
        theme = t;
    }

    String query(int m) {
        switch (m) {
            case 1: return unit + " " + faction + " painted miniature";
            case 2: return unit + " Horus Heresy artwork";
            case 3: return unit + " Forge World Horus Heresy";
            case 4: return unit + " " + faction + " conversion kitbash";
            default: return unit + " Horus Heresy miniature";
        }
    }

    View build() {
        LinearLayout c = Ui.col(a);
        c.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout hd = a.header(unit, "Удерживайте картинку — она станет портретом", theme, true);
        LinearLayout hrow = (LinearLayout) hd.getChildAt(0);
        TextView ext = Ui.button(a, "↗", Ui.steelButton(theme, dp(8)), Ui.GOLD, 20, new View.OnClickListener() {
            public void onClick(View v) {
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url(lastQuery)))); } catch (Exception e) { a.toast("Нет браузера"); }
            }
        });
        hrow.addView(ext, Ui.lp(dp(46), dp(46)));
        c.addView(hd);

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = Ui.row(a);
        hs.addView(chipRow);
        c.addView(hs, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        chips();

        FrameLayout box = new FrameLayout(a);
        box.setBackground(Ui.steelFlat(theme, dp(10)));
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        web = new WebView(a);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        web.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { return grab(); }
        });
        box.addView(web, new FrameLayout.LayoutParams(-1, -1));
        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        box.addView(bar, new FrameLayout.LayoutParams(-1, dp(4)));
        LinearLayout.LayoutParams lp = Ui.lpm(-1, 0, 0, 0, 0, dp(10));
        lp.weight = 1;
        c.addView(box, lp);
        load();
        return c;
    }

    void chips() {
        chipRow.removeAllViews();
        for (int i = 0; i < MODES.length; i++) {
            final int m = i;
            boolean on = m == mode;
            TextView ch = Ui.button(a, MODES[i], on ? Ui.brass(dp(6)) : Ui.steelButton(theme, dp(6)), on ? 0xFF1A140C : Ui.BONE, 14, new View.OnClickListener() {
                public void onClick(View v) {
                    if (mode == m) return;
                    Ui.haptic(Ui.TICK);
                    Sfx.play("click");
                    mode = m;
                    chips();
                    load();
                }
            });
            ch.setPadding(dp(14), 0, dp(14), dp(2));
            chipRow.addView(ch, Ui.lpm(-2, dp(38), 0, 0, dp(6), 0));
        }
    }

    static String url(String q) {
        try { return "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(q, "UTF-8"); } catch (Exception e) { return "https://www.google.com/"; }
    }

    void load() {
        lastQuery = query(mode);
        web.loadUrl(url(lastQuery));
    }

    @Override
    boolean handleBack() {
        if (web != null && web.canGoBack() && web.getUrl() != null && !web.getUrl().contains("tbm=isch&q=")) { web.goBack(); return true; }
        return false;
    }

    /** Долгое нажатие: достаём адрес картинки под пальцем. */
    boolean grab() {
        WebView.HitTestResult hr = web.getHitTestResult();
        if (hr == null) return false;
        int t = hr.getType();
        if (t == WebView.HitTestResult.IMAGE_TYPE) { fetch(hr.getExtra()); return true; }
        if (t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            Handler h = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    String src = msg.getData().getString("src");
                    if (src == null) src = msg.getData().getString("url");
                    fetch(src);
                }
            };
            web.requestImageRef(h.obtainMessage());
            return true;
        }
        return false;
    }

    void fetch(final String src) {
        if (src == null || src.isEmpty()) { a.toast("Не удалось взять картинку"); return; }
        Ui.haptic(Ui.DOUBLE);
        a.toast("Забираем картинку…");
        new Thread(new Runnable() {
            public void run() {
                Bitmap b = null;
                String err = null;
                try {
                    byte[] bytes;
                    if (src.startsWith("data:")) {
                        int comma = src.indexOf(',');
                        bytes = android.util.Base64.decode(src.substring(comma + 1), android.util.Base64.DEFAULT);
                    } else {
                        HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                        c.setInstanceFollowRedirects(true);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LiberRosterum");
                        InputStream in = c.getInputStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                        in.close();
                        bytes = bo.toByteArray();
                    }
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                    int sample = 1;
                    while (Math.max(o.outWidth, o.outHeight) / sample > 1200) sample *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                    if (b == null) err = "Это не картинка";
                } catch (Exception e) {
                    err = "Не скачалось: " + e.getMessage();
                }
                final Bitmap fb = b;
                final String fe = err;
                a.h.post(new Runnable() {
                    public void run() {
                        if (fb == null) { a.toast(fe); return; }
                        confirm(fb);
                    }
                });
            }
        }).start();
    }

    void confirm(final Bitmap b) {
        LinearLayout body = a.openSheet("Сделать портретом?", unit, theme);
        ImageView iv = new ImageView(a);
        iv.setImageBitmap(b);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setMaxHeight(dp(320));
        FrameLayout fr = new FrameLayout(a);
        fr.setBackground(Ui.steelFlat(theme, dp(8)));
        fr.setPadding(dp(6), dp(6), dp(6), dp(6));
        fr.addView(iv, new FrameLayout.LayoutParams(-1, -2));
        body.addView(fr);
        if (b.getWidth() < 200) {
            TextView tip = Ui.text(a, "Это маленькое превью. Для чёткой картинки сначала коснитесь её в выдаче, а потом удерживайте крупную версию.", 13, Theme.body, Ui.BONE_DIM);
            tip.setPadding(dp(4), dp(8), dp(4), 0);
            body.addView(tip);
        }
        body.addView(Ui.button(a, "Скрепить печатью", Ui.brass(dp(8)), 0xFF1A140C, 18, new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    a.savePhoto(entryId, b);
                    a.closeSheetNow();
                    Sfx.play("seal");
                    Ui.haptic(Ui.DOUBLE);
                    a.toast("Портрет сохранён");
                    if (done != null) done.run();
                    a.back();
                } catch (Exception e) { a.toast("Ошибка: " + e.getMessage()); }
            }
        }), Ui.lpm(-1, dp(50), 0, dp(10), 0, 0));
    }
}
