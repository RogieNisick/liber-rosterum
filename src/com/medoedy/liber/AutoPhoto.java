package com.medoedy.liber;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.medoedy.liber.engine.Json;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Автофото: при первом открытии юнита тихо ищет его в Google Картинках
 * (в невидимом браузере) и закрепляет первую картинку как портрет.
 */
final class AutoPhoto {
    private AutoPhoto() { }

    interface Done { void got(boolean ok); }

    static final class Job { String entryId, query; Done done; boolean pageFailed; }

    static MainActivity a;
    static WebView web;
    static final ArrayDeque<Job> queue = new ArrayDeque<Job>();
    static Job cur;
    static int token;

    static boolean tried(MainActivity act, String entryId) { return act.prefs.getBoolean("auto_" + entryId, false); }

    static boolean busy(String entryId) {
        if (cur != null && cur.entryId.equals(entryId)) return true;
        for (Job j : queue) if (j.entryId.equals(entryId)) return true;
        return false;
    }

    /** Запросить автофото; ничего не делает, если фото уже есть или уже искали. */
    static boolean request(MainActivity act, String entryId, String unit, String faction, Done d) {
        a = act;
        if (entryId == null || act.photo(entryId) != null || tried(act, entryId)) return false;
        if (busy(entryId)) return true;
        Job j = new Job();
        j.entryId = entryId;
        String fac = faction == null ? "" : faction.replaceAll("^[IVXL]+ - ", "");
        j.query = unit + " " + fac + " Horus Heresy miniature";
        j.done = d;
        queue.add(j);
        pump();
        return true;
    }

    static void ensureWeb() {
        if (web != null) return;
        web = new WebView(a);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setAlpha(0f);
        web.setTranslationX(-100000f);
        web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                final int t = token;
                a.h.postDelayed(new Runnable() { public void run() { if (t == token && cur != null) extract(t); } }, 1500);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame() && cur != null) cur.pageFailed = true;
            }
        });
        a.addContentView(web, new FrameLayout.LayoutParams(Ui.dp(360), Ui.dp(640)));
    }

    static void pump() {
        if (cur != null || queue.isEmpty()) return;
        cur = queue.poll();
        if (a.photo(cur.entryId) != null) { finish(true, false); return; }
        ensureWeb();
        final int t = ++token;
        web.loadUrl(ImageSearchScreen.url(cur.query));
        a.h.postDelayed(new Runnable() { public void run() { if (t == token && cur != null) finish(false, false); } }, 25000);
    }

    static final String JS = "(function(){var h=document.documentElement.innerHTML,out=[],re=/\\[\"(https?:\\/\\/[^\"]+?)\",(\\d+),(\\d+)\\]/g,m;"
            + "while((m=re.exec(h))&&out.length<4){var u=m[1];if(/gstatic|google\\.|googleusercontent|ggpht/.test(u))continue;if(+m[2]<200||+m[3]<200)continue;"
            + "u=u.replace(/\\\\u003d/g,'=').replace(/\\\\u0026/g,'&');if(out.indexOf(u)<0)out.push(u);}"
            + "var th=[],im=document.images;for(var i=0;i<im.length&&th.length<3;i++){var s=im[i].src||'',w=im[i].naturalWidth||im[i].width;"
            + "if(w<60)continue;if(s.indexOf('data:image/jpeg')==0||s.indexOf('data:image/png')==0||s.indexOf('data:image/webp')==0||s.indexOf('encrypted-tbn')>0)th.push(s);}"
            + "return JSON.stringify({f:out,t:th});})()";

    @SuppressWarnings("unchecked")
    static void extract(final int t) {
        web.evaluateJavascript(JS, new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String value) {
                if (t != token || cur == null) return;
                final List<String> cands = new ArrayList<String>();
                try {
                    Object o = Json.parse(value);
                    Map<String, Object> m = (Map<String, Object>) Json.parse(String.valueOf(o));
                    for (Object u : (List<Object>) m.get("f")) cands.add(String.valueOf(u));
                    for (Object u : (List<Object>) m.get("t")) cands.add(String.valueOf(u));
                } catch (Exception ignored) { }
                if (cands.isEmpty()) { finish(false, !cur.pageFailed); return; }
                final Job job = cur;
                new Thread(new Runnable() {
                    public void run() {
                        Bitmap got = null;
                        for (String u : cands) {
                            got = download(u, 1000);
                            if (got != null && Math.min(got.getWidth(), got.getHeight()) >= 64) break;
                            got = null;
                        }
                        final Bitmap fb = got;
                        a.h.post(new Runnable() {
                            public void run() {
                                if (cur != job) return;
                                if (fb != null) {
                                    try { a.savePhoto(job.entryId, fb); } catch (Exception e) { finish(false, false); return; }
                                    finish(true, true);
                                } else finish(false, true);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    static void finish(boolean ok, boolean markTried) {
        Job j = cur;
        cur = null;
        token++;
        if (j != null) {
            if (markTried || ok) a.prefs.edit().putBoolean("auto_" + j.entryId, true).apply();
            if (j.done != null) j.done.got(ok);
        }
        if (queue.isEmpty() && web != null) web.loadUrl("about:blank");
        pump();
    }

    /** Скачать картинку по адресу (или data:), уменьшив до maxSide. */
    static Bitmap download(String src, int maxSide) {
        try {
            byte[] bytes;
            if (src.startsWith("data:")) {
                bytes = android.util.Base64.decode(src.substring(src.indexOf(',') + 1), android.util.Base64.DEFAULT);
            } else {
                HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                if (c.getResponseCode() != 200) return null;
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); total += n; if (total > 12 * 1024 * 1024) break; }
                in.close();
                bytes = bo.toByteArray();
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (Throwable e) {
            return null;
        }
    }
}
