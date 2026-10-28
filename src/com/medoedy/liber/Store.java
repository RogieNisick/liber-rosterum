package com.medoedy.liber;

import android.content.Context;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Json;
import com.medoedy.liber.engine.Roster;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Файлы данных BSData и сохранённые ростеры. */
final class Store {
    static final String REPO = "BSData/horus-heresy-3rd-edition";
    static final String BRANCH = "main";
    static final String SYSTEM_FILE = "Horus Heresy 3rd Edition.json";

    final File dataDir, rosterDir, metaFile;
    static Data data;

    Store(Context c) {
        dataDir = new File(c.getFilesDir(), "bsdata");
        rosterDir = new File(c.getFilesDir(), "rosters");
        metaFile = new File(dataDir, "_meta.json");
        dataDir.mkdirs();
        rosterDir.mkdirs();
    }

    boolean hasData() { return new File(dataDir, SYSTEM_FILE).exists(); }

    @SuppressWarnings("unchecked")
    Map<String, Object> meta() {
        try {
            if (metaFile.exists()) return (Map<String, Object>) Json.parse(read(metaFile));
        } catch (Exception ignored) { }
        return new LinkedHashMap<String, Object>();
    }

    // ================================================================ загрузка из сети
    interface Progress {
        void step(String what, int done, int total);
    }

    /** Скачивает изменившиеся файлы. Возвращает число обновлённых файлов. */
    @SuppressWarnings("unchecked")
    int download(Progress pr) throws IOException {
        pr.step("Запрос списка файлов", 0, 1);
        String listing = new String(get("https://api.github.com/repos/" + REPO + "/contents?ref=" + BRANCH), "UTF-8");
        List<Object> items = (List<Object>) Json.parse(listing);
        Map<String, Object> meta = meta();
        Map<String, Object> shas = meta.get("sha") instanceof Map ? (Map<String, Object>) meta.get("sha") : new LinkedHashMap<String, Object>();
        List<Map<String, Object>> want = new ArrayList<Map<String, Object>>();
        for (Object o : items) {
            Map<String, Object> it = (Map<String, Object>) o;
            String name = Data.str(it, "name");
            if (name == null || !name.endsWith(".json") || !"file".equals(Data.str(it, "type"))) continue;
            String sha = Data.str(it, "sha");
            File f = new File(dataDir, name);
            if (f.exists() && sha != null && sha.equals(shas.get(name))) continue;
            want.add(it);
        }
        int done = 0;
        for (Map<String, Object> it : want) {
            String name = Data.str(it, "name");
            pr.step(name.replace(".json", ""), done, want.size());
            String url = Data.str(it, "download_url");
            if (url == null) url = "https://raw.githubusercontent.com/" + REPO + "/" + BRANCH + "/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            byte[] body = get(url);
            File tmp = new File(dataDir, name + ".part");
            FileOutputStream out = new FileOutputStream(tmp);
            try { out.write(body); } finally { out.close(); }
            File dst = new File(dataDir, name);
            if (dst.exists()) dst.delete();
            tmp.renameTo(dst);
            shas.put(name, Data.str(it, "sha"));
            done++;
        }
        // удалённые из репозитория файлы
        java.util.Set<String> present = new java.util.HashSet<String>();
        for (Object o : items) present.add(Data.str((Map<String, Object>) o, "name"));
        File[] local = dataDir.listFiles();
        if (local != null) for (File f : local) {
            if (f.getName().endsWith(".json") && !f.getName().startsWith("_") && !present.contains(f.getName())) { f.delete(); shas.remove(f.getName()); }
        }
        meta.put("sha", shas);
        meta.put("updated", (double) System.currentTimeMillis());
        write(metaFile, Json.write(meta));
        pr.step("Готово", want.size(), want.size());
        return want.size();
    }

    static byte[] get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "LiberRosterum-Android");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code == 403) throw new IOException("GitHub временно ограничил запросы (лимит). Попробуйте через час.");
        if (code != 200) throw new IOException("Ошибка сервера: " + code);
        InputStream in = c.getInputStream();
        try { return readAll(in); } finally { in.close(); c.disconnect(); }
    }

    // ================================================================ разбор
    Data load(Progress pr) throws IOException {
        Data d = new Data();
        File[] files = dataDir.listFiles();
        List<File> cats = new ArrayList<File>();
        if (files != null) for (File f : files) if (f.getName().endsWith(".json") && !f.getName().startsWith("_") && !f.getName().equals(SYSTEM_FILE)) cats.add(f);
        int total = cats.size() + 1, i = 0;
        pr.step("Игровая система", i++, total);
        d.loadSystem(read(new File(dataDir, SYSTEM_FILE)));
        for (File f : cats) {
            pr.step(f.getName().replace(".json", ""), i++, total);
            d.loadCatalogue(read(f), f.getName());
        }
        d.finish();
        data = d;
        return d;
    }

    // ================================================================ ростеры
    List<Roster> rosters() {
        List<Roster> out = new ArrayList<Roster>();
        File[] fs = rosterDir.listFiles();
        if (fs != null) for (File f : fs) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) Json.parse(read(f));
                out.add(Roster.fromJson(m));
            } catch (Exception ignored) { }
        }
        Collections.sort(out, new Comparator<Roster>() {
            public int compare(Roster a, Roster b) { return Long.compare(b.updated, a.updated); }
        });
        return out;
    }

    void save(Roster r) {
        if (r.id == null) r.id = Long.toString(System.currentTimeMillis(), 36);
        r.updated = System.currentTimeMillis();
        try { write(new File(rosterDir, r.id + ".json"), Json.write(r.toJson())); } catch (IOException ignored) { }
    }

    void delete(Roster r) {
        new File(rosterDir, r.id + ".json").delete();
    }

    // ================================================================ файлы
    static String read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try { return new String(readAll(in), "UTF-8"); } finally { in.close(); }
    }

    static void write(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try { out.write(s.getBytes("UTF-8")); } finally { out.close(); }
        if (f.exists()) f.delete();
        tmp.renameTo(f);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 15];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
