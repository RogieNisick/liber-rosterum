package com.medoedy.liber;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.medoedy.liber.engine.Json;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Перевод текстов правил на русский через онлайн-переводчик с сохранением в кеш. */
final class Translator {
    interface Done { void got(String ru); }

    static boolean russian = false;
    private static File cacheFile;
    private static final Map<String, String> cache = new HashMap<String, String>();
    private static final ExecutorService ex = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static int dirty;

    @SuppressWarnings("unchecked")
    static void init(Context c) {
        if (cacheFile != null) return;
        cacheFile = new File(c.getFilesDir(), "translations.json");
        try {
            if (cacheFile.exists()) {
                Map<String, Object> m = (Map<String, Object>) Json.parse(Store.read(cacheFile));
                for (Map.Entry<String, Object> e : m.entrySet()) cache.put(e.getKey(), String.valueOf(e.getValue()));
            }
        } catch (Exception ignored) { }
    }

    static String cached(String en) {
        if (en == null) return null;
        synchronized (cache) { return cache.get(key(en)); }
    }

    private static final java.util.Set<String> pending = new java.util.HashSet<String>();

    /** Тихо перевести заранее (в фоне), чтобы по кнопке перевод появился мгновенно. */
    static void prefetch(final String en) {
        if (en == null || en.trim().isEmpty() || cached(en) != null) return;
        synchronized (pending) { if (!pending.add(key(en))) return; }
        ex.submit(new Runnable() {
            public void run() {
                fetch(en);
                synchronized (pending) { pending.remove(key(en)); }
            }
        });
    }

    private static String key(String en) { return Integer.toHexString(en.hashCode()) + ":" + en.length(); }

    /** Перевести (из кеша сразу, иначе в фоне). */
    static void translate(final String en, final Done done) {
        if (en == null || en.trim().isEmpty()) { done.got(en); return; }
        String c = cached(en);
        if (c != null) { done.got(c); return; }
        ex.submit(new Runnable() {
            public void run() {
                final String ru = fetch(en);
                main.post(new Runnable() { public void run() { done.got(ru != null ? ru : en); } });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static String fetch(String en) {
        try {
            StringBuilder out = new StringBuilder();
            for (String chunk : split(en, 1800)) {
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ru&dt=t&q=" + URLEncoder.encode(chunk, "UTF-8");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (c.getResponseCode() != 200) return null;
                String body = new String(Store.readAll(c.getInputStream()), "UTF-8");
                c.disconnect();
                List<Object> root = (List<Object>) Json.parse(body);
                List<Object> parts = (List<Object>) root.get(0);
                for (Object p : parts) {
                    List<Object> seg = (List<Object>) p;
                    if (seg.get(0) != null) out.append(seg.get(0));
                }
            }
            String ru = out.toString();
            synchronized (cache) {
                cache.put(key(en), ru);
                if (++dirty % 5 == 0) save();
            }
            return ru;
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.List<String> split(String s, int max) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        while (s.length() > max) {
            int cut = s.lastIndexOf(". ", max);
            if (cut < max / 2) cut = max;
            out.add(s.substring(0, cut + 1));
            s = s.substring(cut + 1);
        }
        out.add(s);
        return out;
    }

    static void save() {
        if (cacheFile == null) return;
        try {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            synchronized (cache) { m.putAll(cache); }
            Store.write(cacheFile, Json.write(m));
        } catch (Exception ignored) { }
    }

    static int size() { return cache.size(); }

    static void clear() {
        synchronized (cache) { cache.clear(); }
        if (cacheFile != null) cacheFile.delete();
    }
}
