package com.medoedy.liber;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.medoedy.liber.Ui.dp;

/** Вести Ереси: карточки с картинкой статьи, источником и переводом. */
final class NewsScreen extends Screen {
    final long seenBefore;
    static final ExecutorService pool = Executors.newFixedThreadPool(3);
    static final android.util.LruCache<String, Bitmap> mem = new android.util.LruCache<String, Bitmap>(24);

    NewsScreen(MainActivity a) {
        super(a);
        seenBefore = a.prefs.getLong("news_seen", 0);
        a.prefs.edit().putLong("news_seen", System.currentTimeMillis()).apply();
    }

    static int color(String src) {
        if (src.startsWith("Goonhammer")) return 0xFF6A2A8A;
        if (src.startsWith("Bell")) return 0xFF1F5A8A;
        if (src.startsWith("Spikey")) return 0xFFB0501A;
        if (src.startsWith("Natfka")) return 0xFF3A6A2A;
        if (src.startsWith("Reddit")) return 0xFFD04020;
        if (src.startsWith("Google")) return 0xFF2A4A6A;
        return 0xFF6A5420;
    }

    @Override
    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        long last = a.prefs.getLong("news_fetched", 0);
        c.addView(a.header("Вести Ереси", last == 0 ? "Ещё не обновлялось" : "Обновлено " + new SimpleDateFormat("d MMM, HH:mm", new Locale("ru")).format(new Date(last)), theme, true));
        c.addView(Ui.button(a, News.busy ? "Сервочереп собирает свитки…" : "⟳  Обновить сейчас", Ui.steelButton(theme, dp(8)), Ui.BONE, 15, new View.OnClickListener() {
            public void onClick(View v) {
                Sfx.play("servo");
                News.refresh(a, true, new Runnable() { public void run() { if (a.stack.peek() == NewsScreen.this) rebuild(); } });
                rebuild();
            }
        }), Ui.lpm(-1, dp(46), 0, dp(2), 0, dp(8)));
        List<News.Item> items = News.load(a);
        if (items.isEmpty() && !News.busy) {
            News.refresh(a, true, new Runnable() { public void run() { if (a.stack.peek() == NewsScreen.this) rebuild(); } });
            c.addView(Ui.text(a, "Загружаю новости…", 14, Theme.body, Ui.BONE));
        }
        SimpleDateFormat df = new SimpleDateFormat("d MMMM", new Locale("ru"));
        int i = 0;
        for (final News.Item it : items) {
            boolean big = i++ == 0;
            c.addView(card(it, big, df), Ui.lpm(-1, -2, 0, dp(5), 0, dp(5)));
        }
        f.addView(a.scroll(c));
        return f;
    }

    View card(final News.Item it, boolean big, SimpleDateFormat df) {
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.parchment());
        card.setPadding(dp(8), dp(8), dp(8), dp(12));
        // картинка статьи
        FrameLayout pic = new FrameLayout(a);
        android.graphics.drawable.GradientDrawable ph = new android.graphics.drawable.GradientDrawable();
        ph.setColor(0x22000000);
        ph.setCornerRadius(dp(6));
        pic.setBackground(ph);
        final ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (android.os.Build.VERSION.SDK_INT >= 21) { iv.setClipToOutline(true); iv.setOutlineProvider(new android.view.ViewOutlineProvider() { public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(6)); } }); }
        pic.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        pic.addView(new RosterScreen.GlyphView(a, "scroll", 0x558A6A3A), new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        // источник — плашкой поверх картинки
        TextView src = Ui.text(a, it.source, 11, Theme.bodyBold, 0xFFFFF4E0);
        android.graphics.drawable.GradientDrawable sg = new android.graphics.drawable.GradientDrawable();
        sg.setColor(color(it.source));
        sg.setCornerRadius(dp(10));
        src.setBackground(sg);
        src.setPadding(dp(9), dp(3), dp(9), dp(4));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        sl.setMargins(dp(8), dp(8), 0, 0);
        pic.addView(src, sl);
        boolean fresh = it.date > seenBefore && seenBefore > 0;
        if (fresh) {
            TextView nw = Ui.text(a, "НОВОЕ", 11, Theme.bodyBold, 0xFFFFF4E0);
            android.graphics.drawable.GradientDrawable ng = new android.graphics.drawable.GradientDrawable();
            ng.setColor(0xFFC02020);
            ng.setCornerRadius(dp(10));
            nw.setBackground(ng);
            nw.setPadding(dp(9), dp(3), dp(9), dp(4));
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            nl.setMargins(0, dp(8), dp(8), 0);
            pic.addView(nw, nl);
        }
        card.addView(pic, Ui.lp(-1, dp(big ? 200 : 150)));
        load(it, iv);
        LinearLayout tx = Ui.col(a);
        tx.setPadding(dp(6), dp(8), dp(6), 0);
        if (it.date > 0) tx.addView(Ui.text(a, df.format(new Date(it.date)), 12, Theme.bodyBold, Ui.INK_DIM));
        TextView t = Ui.text(a, "", big ? 19 : 16, Theme.names, Ui.INK);
        t.setPadding(0, dp(2), 0, 0);
        tx.addView(t);
        TrText.bind(t, it.title);
        if (it.text != null) {
            TextView x = Ui.text(a, "", 13, Theme.body, Ui.INK);
            x.setPadding(0, dp(5), 0, 0);
            x.setMaxLines(big ? 6 : 4);
            x.setEllipsize(android.text.TextUtils.TruncateAt.END);
            x.setLineSpacing(dp(1), 1f);
            tx.addView(x);
            TrText.bind(x, it.text);
        }
        card.addView(tx);
        card.setClickable(true);
        Ui.press(card);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.link))); } catch (Exception e) { a.toast("Не открылось: " + it.link); }
            }
        });
        return card;
    }

    /** Картинка: из ленты, иначе og:image со страницы; кэш на диске и в памяти. */
    void load(final News.Item it, final ImageView iv) {
        final String key = Integer.toHexString(it.link.hashCode());
        Bitmap b = mem.get(key);
        if (b != null) { iv.setImageBitmap(b); return; }
        pool.execute(new Runnable() {
            public void run() {
                try {
                    File dir = new File(a.getCacheDir(), "news_img");
                    dir.mkdirs();
                    File f = new File(dir, key + ".jpg");
                    if (!f.exists()) {
                        String u = it.img;
                        if (u == null) {
                            String oldLink = it.link;
                            String real = News.resolveGoogle(it.link);
                            if (real != null && !real.equals(oldLink)) it.link = real;
                            u = News.ogImage(it.link);
                            if (u != null) it.img = u;
                            News.remember(a, oldLink, it.link, it.img);
                        }
                        if (u == null) return;
                        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(12000);
                        c.setInstanceFollowRedirects(true);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/124.0 Mobile");
                        if (c.getResponseCode() != 200) return;
                        byte[] data = Store.readAll(c.getInputStream());
                        c.disconnect();
                        FileOutputStream o = new FileOutputStream(f);
                        o.write(data);
                        o.close();
                    }
                    BitmapFactory.Options op = new BitmapFactory.Options();
                    op.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getPath(), op);
                    int s = 1;
                    while (op.outWidth / (s * 2) >= 720) s *= 2;
                    op = new BitmapFactory.Options();
                    op.inSampleSize = s;
                    final Bitmap bm = BitmapFactory.decodeFile(f.getPath(), op);
                    if (bm == null) { f.delete(); return; }
                    mem.put(key, bm);
                    a.h.post(new Runnable() { public void run() { iv.setImageBitmap(bm); iv.setAlpha(0f); iv.animate().alpha(1f).setDuration(250).start(); } });
                } catch (Throwable ignored) { }
            }
        });
    }
}
