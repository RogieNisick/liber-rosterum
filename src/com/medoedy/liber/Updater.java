package com.medoedy.liber;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Json;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

/**
 * Обновления «по воздуху»: публичная папка на Яндекс Диске с файлами version.json и APK.
 * Приложение при запуске смотрит version.json, и если там versionCode больше своего —
 * предлагает скачать и ставит через системный установщик (PackageInstaller).
 */
final class Updater {
    private Updater() { }

    /** Публичная папка с обновлениями (можно сменить в настройках). */
    static final String DEFAULT_FOLDER = "https://github.com/RogieNisick/liber-rosterum";

    static final class Info {
        int code;
        String name, notes, apk = "LiberRosterum.apk";
        long size;
    }

    static volatile Info available;
    static volatile boolean checked;

    static String folder(MainActivity a) {
        String f = a.prefs.getString("update_folder", "").trim();
        return f.isEmpty() ? DEFAULT_FOLDER : f;
    }

    static int myCode(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    static String myName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    interface Done { void got(Info i, String error); }

    /** Проверка в фоне; done — в главном потоке (i == null — обновлений нет). */
    static void check(final MainActivity a, final Done done) {
        final String f = folder(a);
        if (f.isEmpty()) { if (done != null) done.got(null, "Не задана папка обновлений"); return; }
        new Thread(new Runnable() {
            public void run() {
                Info res = null;
                String err = null;
                try {
                    String json = new String(fetch(directLink(f, "/version.json"), null), "UTF-8");
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) Json.parse(json);
                    Info i = new Info();
                    i.code = (int) Data.num(m, "versionCode", 0);
                    i.name = Data.str(m, "versionName");
                    i.notes = Data.str(m, "notes");
                    // изменения всех пропущенных версий, от новой к старой
                    List<Map<String, Object>> hist = Data.list(m, "history");
                    int mine = myCode(a);
                    StringBuilder all = new StringBuilder();
                    int missed = 0;
                    for (int k = hist.size() - 1; k >= 0; k--) {
                        Map<String, Object> hv = hist.get(k);
                        if ((int) Data.num(hv, "versionCode", Data.num(hv, "code", 0)) <= mine) continue;
                        if (all.length() > 0) all.append("\n\n");
                        all.append("ВЕРСИЯ ").append(Data.str(hv, "name")).append("\n").append(Data.str(hv, "notes"));
                        missed++;
                    }
                    if (missed >= 1) i.notes = all.toString();
                    else if (Data.str(m, "latest") != null) i.notes = Data.str(m, "latest");
                    if (Data.str(m, "apk") != null) i.apk = Data.str(m, "apk");
                    i.size = (long) Data.num(m, "size", 0);
                    if (i.code > myCode(a)) res = i;
                } catch (Exception e) { err = "Не удалось проверить обновления: " + e.getMessage(); }
                checked = true;
                available = res;
                final Info r = res;
                final String er = err;
                a.h.post(new Runnable() { public void run() { if (done != null) done.got(r, er); } });
            }
        }).start();
    }

    /** Прямая ссылка на файл из публичной папки Яндекс Диска (или сама ссылка, если это обычный URL на файл). */
    static String directLink(String folder, String path) throws Exception {
        String gh = githubRepo(folder);
        if (gh != null) {
            // основной путь: ветка updates репозитория (файлы лежат в корне ветки)
            String raw = "https://raw.githubusercontent.com/" + gh + "/updates/" + path.replaceFirst("^/", "");
            if (exists(raw)) return raw;
            // запасной путь: вложения последнего релиза GitHub
            @SuppressWarnings("unchecked") Map<String, Object> rel = (Map<String, Object>) Json.parse(new String(fetch("https://api.github.com/repos/" + gh + "/releases/latest", null), "UTF-8"));
            String want = path.replaceFirst("^/", "");
            for (Map<String, Object> as : Data.list(rel, "assets")) if (want.equals(Data.str(as, "name"))) return Data.str(as, "browser_download_url");
            throw new Exception("в последнем релизе нет файла " + want);
        }
        if (!folder.contains("disk.yandex") && !folder.contains("yadi.sk")) {
            String base = folder.endsWith("/") ? folder : folder + "/";
            return base + path.replaceFirst("^/", "");
        }
        String api = "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key=" + URLEncoder.encode(folder, "UTF-8") + "&path=" + URLEncoder.encode(path, "UTF-8");
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) Json.parse(new String(fetch(api, null), "UTF-8"));
        String href = Data.str(m, "href");
        if (href == null) throw new Exception("файл " + path + " не найден в папке");
        return href;
    }

    static boolean exists(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "LiberRosterum");
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) { return false; }
    }

    /** «owner/repo» из ссылки на GitHub или записи вида github:owner/repo. */
    static String githubRepo(String f) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:github\\.com/|^github:)([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/.*)?$").matcher(f.trim());
        return m.find() ? m.group(1) + "/" + m.group(2) : null;
    }

    interface Progress { void at(long done, long total); }

    /** Скачать (с редиректами) в память или в файл. */
    static byte[] fetch(String url, File to) throws Exception { return fetch(url, to, null); }

    static byte[] fetch(String url, File to, Progress pr) throws Exception {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "LiberRosterum");
            if (url.contains("api.github.com")) c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("пустой редирект");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) { c.disconnect(); throw new Exception("HTTP " + code); }
            long total = c.getContentLength();
            InputStream in = c.getInputStream();
            OutputStream out = to == null ? new ByteArrayOutputStream() : new FileOutputStream(to);
            byte[] buf = new byte[1 << 16];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (pr != null) pr.at(done, total);
            }
            in.close();
            out.close();
            c.disconnect();
            return to == null ? ((ByteArrayOutputStream) out).toByteArray() : null;
        }
        throw new Exception("слишком много редиректов");
    }

    // ================================================================ интерфейс
    /** Окно обновления: что нового, кнопка «Скачать и установить» с прогрессом. */
    static void show(final MainActivity a, final Info i, final Theme t) {
        LinearLayout body = a.openSheet("Новая версия " + (i.name == null ? "" : i.name), "У вас " + myName(a) + " · сервочереп принёс свежий свиток", t);
        LinearLayout sh = Ui.col(a);
        sh.setBackground(Ui.parchment());
        sh.setPadding(dp(14), dp(10), dp(14), dp(12));
        TextView n = Ui.text(a, i.notes == null || i.notes.isEmpty() ? "Описание изменений не приложено." : i.notes, 14, Theme.body, Ui.INK);
        n.setLineSpacing(dp(2), 1f);
        sh.addView(n);
        body.addView(sh);
        final TextView status = Ui.text(a, i.size > 0 ? "Размер: " + (i.size / 1024 / 1024) + " МБ" : "", 13, Theme.body, Ui.BONE_DIM);
        status.setPadding(0, dp(8), 0, dp(4));
        body.addView(status);
        final Ui.Gauge g = new Ui.Gauge(a, t);
        g.set(0, 100);
        body.addView(g, Ui.lpm(-1, dp(12), 0, dp(2), 0, dp(6)));
        final TextView go = Ui.button(a, "⤓  Скачать и установить", Ui.brass(dp(8)), 0xFF1A140C, 17, null);
        go.setOnClickListener(new View.OnClickListener() {
            boolean busy;
            public void onClick(View v) {
                if (busy) return;
                if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
                    a.toast("Разрешите Liber Rosterum устанавливать приложения и нажмите кнопку ещё раз");
                    try { a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); } catch (Exception ignored) { }
                    return;
                }
                busy = true;
                go.setText("Скачиваю…");
                download(a, i, status, g, new Runnable() { public void run() { busy = false; go.setText("⤓  Попробовать ещё раз"); } });
            }
        });
        body.addView(go, Ui.lpm(-1, dp(52), 0, dp(4), 0, 0));
    }

    static void download(final MainActivity a, final Info i, final TextView status, final Ui.Gauge g, final Runnable failed) {
        final File f = new File(a.getCacheDir(), "update.apk");
        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = directLink(folder(a), "/" + i.apk);
                    fetch(url, f, new Progress() {
                        long lastPost;
                        public void at(final long done, final long total) {
                            long now = System.currentTimeMillis();
                            if (now - lastPost < 120 && done != total) return;
                            lastPost = now;
                            a.h.post(new Runnable() {
                                public void run() {
                                    long tot = total > 0 ? total : i.size;
                                    status.setText("Скачано " + (done / 1024 / 1024) + (tot > 0 ? " из " + (tot / 1024 / 1024) : "") + " МБ");
                                    if (tot > 0) g.set(done * 100.0 / tot, 100);
                                }
                            });
                        }
                    });
                    a.h.post(new Runnable() { public void run() { status.setText("Открываю установщик…"); g.set(100, 100); } });
                    install(a, f);
                } catch (final Exception e) {
                    a.h.post(new Runnable() { public void run() { status.setText("Ошибка: " + e.getMessage()); failed.run(); } });
                }
            }
        }).start();
    }

    static final String ACTION = "com.medoedy.liber.INSTALL_STATUS";
    private static BroadcastReceiver receiver;

    /** Установка через PackageInstaller: система покажет обычное окно «Обновить приложение?». */
    static void install(final MainActivity a, File apk) throws Exception {
        if (receiver == null) {
            receiver = new BroadcastReceiver() {
                public void onReceive(Context c, Intent in) {
                    int st = in.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
                    if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent confirm = (Intent) in.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(confirm); }
                    } else if (st != PackageInstaller.STATUS_SUCCESS) {
                        String msg = in.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                        a.toast("Установка не удалась" + (msg == null ? "" : ": " + msg));
                    }
                }
            };
            IntentFilter flt = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) a.getApplicationContext().registerReceiver(receiver, flt, 4 /* RECEIVER_NOT_EXPORTED */);
            else a.getApplicationContext().registerReceiver(receiver, flt);
        }
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(a.getPackageName());
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        InputStream in = new FileInputStream(apk);
        OutputStream out = s.openWrite("liber", 0, apk.length());
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        s.fsync(out);
        in.close();
        out.close();
        Intent cb = new Intent(ACTION);
        cb.setPackage(a.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 /* FLAG_MUTABLE */ : 0);
        PendingIntent pend = PendingIntent.getBroadcast(a, id, cb, flags);
        s.commit(pend.getIntentSender());
        s.close();
    }

    /** Настройка папки обновлений. */
    static void askFolder(final MainActivity a, final Theme t) {
        LinearLayout body = a.openSheet("Источник обновлений", "Репозиторий GitHub (github.com/…/…) или публичная папка Яндекс Диска", t);
        final android.widget.EditText e = a.input(folder(a), "https://github.com/имя/репозиторий", false);
        body.addView(e, Ui.lpm(-1, -2, 0, dp(4), 0, dp(8)));
        body.addView(Ui.button(a, "Сохранить и проверить", Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
            public void onClick(View v) {
                a.prefs.edit().putString("update_folder", e.getText().toString().trim()).apply();
                a.closeSheet();
                manual(a, t);
            }
        }), Ui.lp(-1, dp(48)));
    }

    /** Ручная проверка из настроек. */
    static void manual(final MainActivity a, final Theme t) {
        a.toast("Проверяю обновления…");
        check(a, new Done() {
            public void got(Info i, String error) {
                if (error != null) { a.toast(error); return; }
                if (i == null) { a.toast("У вас последняя версия: " + myName(a)); return; }
                show(a, i, t);
            }
        });
    }
}
