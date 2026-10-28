package com.medoedy.liber;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

public class MainActivity extends Activity {

    Store store;
    FrameLayout root, stage, overlay;
    View bg;
    Cogitator.Fx fx;
    Cogitator.Gate gate;
    int insetLeft, insetRight;
    boolean gateOn = true;
    long lastNav;
    final ArrayDeque<Screen> stack = new ArrayDeque<Screen>();
    final Handler h = new Handler(Looper.getMainLooper());
    int insetTop, insetBottom;
    SharedPreferences prefs;

    /** Содержимое — внутри «экрана» когитатора, но не под системными панелями. */
    void applyPads() {
        int w = root.getWidth(), h = root.getHeight();
        stage.setPadding(insetLeft, insetTop, insetRight, insetBottom);
    }

    /** Отступы прокрутки, чтобы в начале и в конце контент не прятался под крышками рамки. */
    int[] capPads() {
        if (true) return new int[]{0, 0};
        android.util.DisplayMetrics m = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        android.graphics.Rect in = Cogitator.interior(m.widthPixels, m.heightPixels);
        return new int[]{Math.max(0, in.top - insetTop), Math.max(0, m.heightPixels - in.bottom - insetBottom)};
    }

    void setBgTheme(Theme t) {
        if (bg instanceof Cogitator.Display) ((Cogitator.Display) bg).setTheme(t);
        else if (bg instanceof Ui.Backdrop) ((Ui.Backdrop) bg).setTheme(t);
    }

    /** Панели нового экрана выезжают по очереди. */
    void revealIn(View v) {
        java.util.List<ScrollView> sv = new java.util.ArrayList<ScrollView>();
        Screen.scrolls(v, sv);
        if (!sv.isEmpty() && sv.get(0).getChildCount() > 0 && sv.get(0).getChildAt(0) instanceof android.view.ViewGroup)
            Ui.reveal((android.view.ViewGroup) sv.get(0).getChildAt(0));
    }

    void toggleDark() {
        prefs.edit().putBoolean("dark", !Ui.dark).apply();
        Sfx.play("gong", 0.6f, 1f);
        Ui.haptic(Ui.HEAVY);
        recreate();
    }

    /** Переход через шлюз (если включён и это не первый экран). */
    private boolean viaGate(Runnable r, boolean quick) {
        long now = android.os.SystemClock.uptimeMillis();
        boolean use = false; // шлюз-переход отключён: анимируются сами панели
        lastNav = now;
        if (!use) return false;
        gate.run(r, quick);
        return true;
    }

    // ================================================================ навигация
    void show(final Screen s) {
        if (viaGate(new Runnable() { public void run() { showNow(s, false); } }, false)) return;
        showNow(s, true);
    }

    private void showNow(Screen s, boolean animate) {
        Screen cur = stack.peek();
        stack.push(s);
        s.view = s.build();
        stage.addView(s.view, new FrameLayout.LayoutParams(-1, -1));
        setBgTheme(s.theme);
        revealIn(s.view);
        if (animate) {
            s.view.setTranslationX(dp(50));
            s.view.setAlpha(0f);
            s.view.animate().translationX(0).alpha(1f).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
            Sfx.play("page", 0.7f, 1f);
        }
        if (cur != null) {
            final View old = cur.view;
            if (animate) old.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() { public void run() { old.setVisibility(View.GONE); } }).start();
            else old.setVisibility(View.GONE);
        }
    }

    void replaceTop(final Screen s) {
        Runnable r = new Runnable() { public void run() { Screen top = stack.pop(); stage.removeView(top.view); showNow(s, false); } };
        if (!viaGate(r, false)) { Screen top = stack.pop(); stage.removeView(top.view); showNow(s, true); }
    }

    void replaceAll(Screen s) {
        if (s instanceof MenuScreen && pendingImport != null) h.postDelayed(new Runnable() { public void run() { runPendingImport(); } }, 400);
        stack.clear();
        stage.removeAllViews();
        show(s);
    }

    void back() {
        if (overlay.getChildCount() > 0) { closeSheet(); return; }
        if (!stack.isEmpty() && stack.peek().handleBack()) return;
        if (stack.size() <= 1) { finish(); return; }
        if (viaGate(new Runnable() { public void run() { backNow(false); } }, true)) return;
        backNow(true);
    }

    private void backNow(boolean animate) {
        if (stack.size() <= 1) return;
        final Screen top = stack.pop();
        Screen under = stack.peek();
        under.view.setVisibility(View.VISIBLE);
        under.view.setAlpha(1f);
        under.refresh();
        setBgTheme(under.theme);
        if (animate) {
            Sfx.play("clunk", 0.5f, 1.2f);
            top.view.animate().translationX(dp(50)).alpha(0f).setDuration(180).withEndAction(new Runnable() {
                public void run() { stage.removeView(top.view); }
            }).start();
        } else stage.removeView(top.view);
        hideKeyboard();
    }

    @Override
    public void onBackPressed() { back(); }

    /** История изменений, встроенная в приложение (assets/notes_history.json). */
    @SuppressWarnings("unchecked")
    void changelog(Theme t) { changelog(t, 0); }

    void changelog(Theme t, int after) {
        LinearLayout body = openSheet(after > 0 ? "Обновление установлено" : "Что нового", after > 0 ? "Что изменилось с прошлой вашей версии" : "У вас версия " + Updater.myName(this), t);
        java.util.List<Object> hist = new java.util.ArrayList<Object>();
        try {
            java.io.InputStream in = getAssets().open("notes_history.json");
            hist = (java.util.List<Object>) com.medoedy.liber.engine.Json.parse(new String(Store.readAll(in), "UTF-8"));
        } catch (Exception ignored) { }
        int mine = Updater.myCode(this);
        for (int k = hist.size() - 1; k >= 0; k--) {
            java.util.Map<String, Object> h = (java.util.Map<String, Object>) hist.get(k);
            int code = (int) Data.num(h, "code", 0);
            if (code <= after) continue;
            LinearLayout card = Ui.col(this);
            card.setBackground(Ui.parchment());
            card.setPadding(dp(14), dp(10), dp(14), dp(12));
            TextView ttl = Ui.text(this, "Версия " + Data.str(h, "name") + (code == mine ? "  ·  установлена" : ""), 17, Theme.names, code == mine ? Ui.INK_RED : Ui.INK);
            card.addView(ttl);
            TextView n = Ui.text(this, String.valueOf(Data.str(h, "notes")), 14, Theme.body, Ui.INK);
            n.setLineSpacing(dp(2), 1f);
            card.addView(n, Ui.lpm(-1, -2, 0, dp(4), 0, 0));
            body.addView(card, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        }
        if (hist.isEmpty()) body.addView(Ui.text(this, "История изменений не найдена.", 14, Theme.body, Ui.BONE));
    }

    /** Если приложение упало — сохранить причину и показать её при следующем запуске (чтобы можно было переслать). */
    private void installCrashLog() {
        final File log = new File(getFilesDir(), "crash.txt");
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable ex) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    ex.printStackTrace(new java.io.PrintWriter(sw));
                    String txt = sw.toString();
                    if (txt.length() > 6000) txt = txt.substring(0, 6000);
                    String ver = "?";
                    try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable ignored) { }
                    Store.write(log, "Liber Rosterum " + ver + "\n" + txt);
                } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, ex);
            }
        });
        if (!log.exists()) return;
        final String txt;
        try { txt = Store.read(log); } catch (Exception e) { log.delete(); return; }
        log.delete();
        h.postDelayed(new Runnable() {
            public void run() {
                String head = txt.length() > 700 ? txt.substring(0, 700) + "…" : txt;
                confirm("В прошлый раз приложение вылетело", "Скопируй отчёт и пришли разработчику:\n\n" + head, "Скопировать", Theme.DEFAULT, new Runnable() {
                    public void run() {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", txt));
                        toast("Отчёт скопирован");
                    }
                });
            }
        }, 1500);
    }

    // ================================================================ старт
    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Ui.initHaptics(this);
        installCrashLog();
        prefs = getSharedPreferences("liber", MODE_PRIVATE);
        File oldAi = new File(getFilesDir(), "ai");
        if (oldAi.isDirectory()) { File[] fs = oldAi.listFiles(); if (fs != null) for (File x : fs) x.delete(); oldAi.delete(); }
        Sfx.on = prefs.getBoolean("sound", true);
        Voice.on = prefs.getBoolean("voice", true);
        Ui.hapticsOn = prefs.getBoolean("haptics", true);
        Sfx.init(this);
        Voice.init(this);
        Translator.init(this);
        store = new Store(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xFF0A0B09);
        root = new FrameLayout(this);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | (!prefs.getBoolean("dark", true) && android.os.Build.VERSION.SDK_INT >= 23 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0));
        Art.init(this);
        Ui.setDark(prefs.getBoolean("dark", true));
        bg = Ui.dark ? new Cogitator.Display(this, Theme.DEFAULT) : new Ui.Backdrop(this, Theme.DEFAULT);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        if (Ui.dark) {
            fx = new Cogitator.Fx(this);
            root.addView(fx, new FrameLayout.LayoutParams(-1, -1));
        }
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) applyPads();
            }
        });
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                insetTop = in.getSystemWindowInsetTop();
                insetBottom = in.getSystemWindowInsetBottom();
                insetLeft = in.getSystemWindowInsetLeft();
                insetRight = in.getSystemWindowInsetRight();
                applyPads();
                overlay.setPadding(in.getSystemWindowInsetLeft(), insetTop, in.getSystemWindowInsetRight(), insetBottom);
                return in.consumeSystemWindowInsets();
            }
        });
        setContentView(root);
        Eggs.applyChaos(this);
        // после обновления — один раз показать, что изменилось
        final int seen = prefs.getInt("seen_code", 0), mineCode = Updater.myCode(this);
        if (seen != mineCode) {
            prefs.edit().putInt("seen_code", mineCode).apply();
            if (seen > 0 && seen < mineCode) h.postDelayed(new Runnable() { public void run() { if (overlay.getChildCount() == 0) changelog(Theme.DEFAULT, seen); } }, 2200);
        }
        takeImportIntent(getIntent());
        if (Store.data != null) replaceAll(new MenuScreen(this));
        else if (store.hasData()) startLoading();
        else replaceAll(new DataScreen(this));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        takeImportIntent(i);
        if (Store.data != null) runPendingImport();
    }

    // ================================================================ импорт BattleScribe
    static final int PICK_ROS = 43, PICK_BOOKS = 44;
    Library.Progress pendingBooks;
    Uri pendingImport;

    @SuppressWarnings("deprecation")
    void takeImportIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) { Object o = i.getParcelableExtra(Intent.EXTRA_STREAM); if (o instanceof Uri) u = (Uri) o; }
        if (u != null) { pendingImport = u; i.setAction(Intent.ACTION_MAIN); }
    }

    void runPendingImport() {
        Uri u = pendingImport;
        pendingImport = null;
        if (u != null && Store.data != null) importRoster(u);
    }

    void pickRosterFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "Файл ростера .ros / .rosz"), PICK_ROS); } catch (Exception e) { toast("Нет приложения для выбора файлов"); }
    }

    /** Выбор PDF/ZIP книг правил (можно несколько). */
    void pickBooks(Library.Progress pr) {
        pendingBooks = pr;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "application/zip", "application/x-zip-compressed", "application/octet-stream"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, PICK_BOOKS); } catch (Exception e) { toast("Нет приложения для выбора файлов"); }
    }

    void confirmInfo(String title, String text, String yes, Theme t, final Runnable onYes) {
        LinearLayout body = openSheet(title, t);
        if (text != null) {
            TextView tv = Ui.text(this, text, 15, Theme.body, Ui.BONE_DIM);
            tv.setPadding(0, 0, 0, dp(10));
            body.addView(tv);
        }
        body.addView(Ui.button(this, yes, Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() { public void onClick(View v) { closeSheetNow(); onYes.run(); } }), Ui.lpm(-1, dp(48), 0, 0, 0, dp(4)));
        sheetItem(body, null, "Отмена", null, t, false, new View.OnClickListener() { public void onClick(View v) { closeSheet(); } });
    }

    void importRoster(final Uri u) {
        toast("Читаем ростер…");
        new Thread(new Runnable() {
            public void run() {
                com.medoedy.liber.engine.RosImport.Result res;
                try {
                    InputStream in = getContentResolver().openInputStream(u);
                    byte[] b = com.medoedy.liber.engine.RosImport.readAll(in);
                    in.close();
                    res = new com.medoedy.liber.engine.RosImport(Store.data).parse(b);
                } catch (Exception e) {
                    res = new com.medoedy.liber.engine.RosImport.Result();
                    res.error = "Не удалось открыть файл: " + e.getMessage();
                }
                final com.medoedy.liber.engine.RosImport.Result fr = res;
                h.post(new Runnable() { public void run() { importDone(fr); } });
            }
        }, "ros-import").start();
    }

    void importDone(final com.medoedy.liber.engine.RosImport.Result res) {
        if (res.error != null) {
            Sfx.play("error");
            LinearLayout body = openSheet("Импорт не удался", Theme.DEFAULT);
            body.addView(Ui.text(this, res.error, 15, Theme.body, Ui.BONE));
            return;
        }
        final com.medoedy.liber.engine.Roster r = res.roster;
        store.save(r);
        Sfx.play("seal");
        Ui.haptic(Ui.DOUBLE);
        Data.Cat cat = Store.data.cats.get(r.catalogueId);
        final Theme t = Theme.forName(cat == null ? null : cat.name);
        LinearLayout body = openSheet("Ростер импортирован", r.name, t);
        body.addView(Ui.text(this, (cat == null ? "" : cat.name + " · ") + res.units + " " + plural(res.units, "юнит", "юнита", "юнитов")
                + " · " + res.forces + " " + plural(res.forces, "сила", "силы", "сил") + " · лимит " + Engine.fmt(r.limit), 15, Theme.body, Ui.BONE));
        if (!res.missing.isEmpty()) {
            LinearLayout sheet = Ui.col(this);
            sheet.setBackground(Ui.parchment());
            sheet.setPadding(dp(14), dp(10), dp(14), dp(12));
            sheet.addView(Ui.text(this, "Не нашлось в текущих данных (" + res.missing.size() + "):", 14, Theme.bodyBold, Ui.INK_RED));
            int k = 0;
            for (String m : res.missing) { if (++k > 12) { sheet.addView(Ui.text(this, "…и ещё " + (res.missing.size() - 12), 13, Theme.body, Ui.INK_DIM)); break; } sheet.addView(Ui.text(this, "• " + m, 13, Theme.body, Ui.INK)); }
            sheet.addView(Ui.text(this, "Обычно это значит, что ростер собран на другой ревизии данных BSData. Добавьте эти выборы вручную.", 12, Theme.body, Ui.INK_DIM));
            body.addView(sheet, Ui.lpm(-1, -2, 0, dp(10), 0, 0));
        }
        body.addView(Ui.button(this, "Открыть ростер", Ui.brass(dp(8)), 0xFF1A140C, 18, new View.OnClickListener() {
            public void onClick(View v) {
                closeSheetNow();
                if (!(stack.peek() instanceof RosterListScreen)) show(new RosterListScreen(MainActivity.this));
                Data.Cat cc = Store.data.cats.get(r.catalogueId);
                if (cc != null) Voice.say(Voice.proclamation(cc.name));
                show(new RosterScreen(MainActivity.this, r));
            }
        }), Ui.lpm(-1, dp(50), 0, dp(12), 0, 0));
        if (stack.peek() instanceof RosterListScreen) stack.peek().rebuild();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Translator.save();
        try { android.hardware.SensorManager sm = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE); if (sm != null) sm.unregisterListener(shake); } catch (Throwable ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            android.hardware.SensorManager sm = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
            android.hardware.Sensor acc = sm == null ? null : sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
            if (acc != null) sm.registerListener(shake, acc, android.hardware.SensorManager.SENSOR_DELAY_UI);
        } catch (Throwable ignored) { }
    }

    /** Тряска телефона на главном экране — пасхалка «Экстерминатус». */
    private final android.hardware.SensorEventListener shake = new android.hardware.SensorEventListener() {
        long firstPeak;
        int peaks;
        public void onSensorChanged(android.hardware.SensorEvent ev) {
            float x = ev.values[0], y = ev.values[1], z = ev.values[2];
            double g = Math.sqrt(x * x + y * y + z * z) / 9.81;
            if (g < 2.6) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - firstPeak > 900) { firstPeak = now; peaks = 0; }
            if (++peaks >= 3) {
                peaks = 0;
                Screen top = stack.peek();
                if (top instanceof MenuScreen && overlay.getChildCount() == 0) ((MenuScreen) top).shaken();
            }
        }
        public void onAccuracyChanged(android.hardware.Sensor s, int acc) { }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Voice.shutdown();
    }

    void setPref(String k, boolean v) { prefs.edit().putBoolean(k, v).apply(); }

    void startLoading() {
        final LoadingScreen ls = new LoadingScreen(this);
        replaceAll(ls);
        new Thread(new Runnable() {
            public void run() {
                try {
                    store.load(new Store.Progress() {
                        public void step(final String what, final int done, final int total) {
                            h.post(new Runnable() { public void run() { ls.progress(what, done, total); } });
                        }
                    });
                    h.post(new Runnable() { public void run() { Sfx.play("organ", 0.6f, 1f); replaceAll(new MenuScreen(MainActivity.this)); } });
                } catch (final Throwable e) {
                    h.post(new Runnable() {
                        public void run() {
                            DataScreen ds = new DataScreen(MainActivity.this);
                            ds.error = "Не удалось прочитать данные: " + e.getMessage() + ". Скачайте их заново.";
                            replaceAll(ds);
                        }
                    });
                }
            }
        }, "load").start();
    }

    // ================================================================ общие элементы
    LinearLayout column() {
        LinearLayout c = Ui.col(this);
        c.setPadding(dp(14), dp(6), dp(14), dp(18));
        return c;
    }

    /** Шапка: стальная кнопка «назад», заголовок, подзаголовок, череп-разделитель. */
    LinearLayout header(String title, String sub, Theme t, boolean backBtn) {
        LinearLayout box = Ui.col(this);
        LinearLayout h = Ui.row(this);
        h.setPadding(0, dp(4), 0, 0);
        if (backBtn) {
            TextView b = Ui.button(this, "‹", Ui.steelButton(t, dp(8)), Ui.GOLD, 26, new View.OnClickListener() {
                public void onClick(View v) { back(); }
            });
            b.setPadding(0, 0, 0, dp(4));
            h.addView(b, Ui.lpm(dp(46), dp(46), 0, 0, dp(10), 0));
        }
        LinearLayout tt = Ui.col(this);
        TextView tv = Ui.title(this, title, 22, Ui.BONE);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setShadowLayer(dp(6), 0, dp(1), 0xFF000000);
        tt.addView(tv);
        if (sub != null) {
            TextView sv = Ui.text(this, sub, 13, Theme.body, Ui.GOLD);
            sv.setSingleLine(true);
            sv.setEllipsize(TextUtils.TruncateAt.END);
            tt.addView(sv);
        }
        h.addView(tt, Ui.weight(1));
        box.addView(h);
        box.addView(Ui.skullDivider(this, Ui.GOLD_DIM));
        return box;
    }

    View ribbonTitle(String s, Theme t) {
        LinearLayout w = Ui.row(this);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView r = Ui.ribbon(this, s, Theme.mix(t.accent, Color.BLACK, 0.15f));
        w.addView(r);
        w.setPadding(0, dp(10), 0, dp(4));
        return w;
    }

    TextView label(String s, int color) {
        TextView tv = Ui.title(this, s, 14, color);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(dp(2), dp(8), 0, dp(2));
        return tv;
    }

    ScrollView scroll(View content) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(content);
        int[] cp = capPads();
        if (cp[0] > 0 || cp[1] > 0) {
            sv.setClipToPadding(false);
            sv.setPadding(0, cp[0], 0, cp[1]);
        }
        return sv;
    }

    EditText input(String value, String hint, boolean number) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(0x802B1E12);
        e.setTextColor(Ui.INK);
        e.setTypeface(Theme.bodyBold);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setBackground(new Ui.Parchment(dp(6), true, 0, false));
        e.setPadding(dp(14), dp(10), dp(14), dp(12));
        return e;
    }

    void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    // ================================================================ листы
    LinearLayout openSheet(String title, Theme t) { return openSheet(title, null, t); }

    /** Лист снизу в стальной раме. Возвращает контейнер для содержимого. */
    LinearLayout openSheet(String title, String sub, Theme t) {
        closeSheetNow();
        final FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0xB0000000);
        dim.setClickable(true);
        dim.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { closeSheet(); } });
        LinearLayout panel = Ui.col(this);
        panel.setBackground(Ui.steel(t));
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setClickable(true);
        TextView tv = Ui.title(this, title, 20, Ui.BONE);
        tv.setShadowLayer(dp(4), 0, dp(1), 0xFF000000);
        LinearLayout trow = Ui.row(this);
        trow.addView(tv, Ui.weight(1));
        Ui.Candles c1 = new Ui.Candles(this, 2, title.length());
        c1.skulls = false;
        trow.addView(c1, Ui.lp(dp(48), dp(70)));
        TextView x = Ui.button(this, "✕", Ui.steelButton(t, dp(6)), Ui.GOLD, 16, new View.OnClickListener() { public void onClick(View v) { closeSheet(); } });
        x.setPadding(0, 0, 0, dp(2));
        trow.addView(x, Ui.lpm(dp(38), dp(38), dp(6), 0, 0, 0));
        panel.addView(trow);
        if (sub != null) panel.addView(Ui.text(this, sub, 13, Theme.body, Ui.GOLD));
        panel.addView(Ui.skullDivider(this, Ui.GOLD_DIM));
        LinearLayout body = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        panel.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(6), dp(50), dp(6), dp(6));
        dim.addView(panel, lp);
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(220));
        panel.animate().translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        Ui.haptic(Ui.TICK);
        Sfx.play("page", 0.8f, 1.1f);
        return body;
    }

    void closeSheet() {
        if (overlay.getChildCount() == 0) return;
        final View v = overlay.getChildAt(overlay.getChildCount() - 1);
        v.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() { public void run() { overlay.removeView(v); } }).start();
        hideKeyboard();
    }

    void closeSheetNow() { overlay.removeAllViews(); }

    /** Строка-действие в листе: гекс-значок (необязательно), заголовок, подзаголовок. */
    LinearLayout sheetItem(LinearLayout body, String glyph, String label, String sub, Theme t, boolean danger, final View.OnClickListener l) {
        LinearLayout r = Ui.row(this);
        r.setBackground(danger ? Ui.danger(dp(8)) : Ui.steelButton(t, dp(8)));
        r.setPadding(dp(10), dp(9), dp(14), dp(10));
        if (glyph != null) {
            Ui.Hex hx = new Ui.Hex(this, t, glyph);
            r.addView(hx, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
        }
        LinearLayout txt = Ui.col(this);
        TextView tv = Ui.text(this, label, 17, Theme.names, danger ? 0xFFFFC0B0 : Ui.BONE);
        txt.addView(tv);
        if (sub != null) txt.addView(Ui.text(this, sub, 13, Theme.body, Ui.GOLD));
        r.addView(txt, Ui.weight(1));
        r.setClickable(true);
        Ui.press(r);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("click"); l.onClick(v); } });
        body.addView(r, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        return r;
    }

    void confirm(String title, String text, String yes, Theme t, final Runnable onYes) {
        LinearLayout body = openSheet(title, t);
        if (text != null) {
            TextView tv = Ui.text(this, text, 15, Theme.body, Ui.BONE_DIM);
            tv.setPadding(0, 0, 0, dp(10));
            body.addView(tv);
        }
        sheetItem(body, null, yes, null, t, true, new View.OnClickListener() { public void onClick(View v) { closeSheet(); Sfx.play("burn"); onYes.run(); } });
        sheetItem(body, null, "Отмена", null, t, false, new View.OnClickListener() { public void onClick(View v) { closeSheet(); } });
    }

    void toast(String s) {
        final TextView tv = Ui.text(this, s, 15, Theme.bodyBold, Ui.INK);
        tv.setBackground(new Ui.Parchment(dp(8), true, 0, false));
        tv.setPadding(dp(18), dp(10), dp(18), dp(12));
        tv.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(90) + insetBottom;
        root.addView(tv, lp);
        tv.setAlpha(0f);
        tv.setTranslationY(dp(20));
        tv.animate().alpha(1f).translationY(0).setDuration(180).start();
        h.postDelayed(new Runnable() {
            public void run() { tv.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() { public void run() { root.removeView(tv); } }).start(); }
        }, 2200);
    }

    static String plural(int n, String one, String few, String many) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    static String pts(double v) { return Engine.fmt(v) + " оч."; }

    // ================================================================ настройки
    void openSettings(final Theme t, final Runnable after) {
        show(new SettingsScreen(this, t, after));
    }

    /** Настройки — отдельный экран: диалоги всплывают поверх него, а не закрывают его. */
    static final class SettingsScreen extends Screen {
        final Runnable after;
        LinearLayout body;
        SettingsScreen(MainActivity a, Theme t, Runnable after) { super(a); theme = t; this.after = after; }
        View build() {
            FrameLayout f = new FrameLayout(a);
            LinearLayout c = a.column();
            c.addView(a.header("Настройки", "Версия " + Updater.myName(a), theme, true));
            body = Ui.col(a);
            c.addView(body);
            a.fillSettings(body, theme, after);
            f.addView(a.scroll(c));
            return f;
        }
        @Override
        void refresh() { if (body != null) a.fillSettings(body, theme, after); }
    }

    void fillSettings(final LinearLayout body, final Theme t, final Runnable after) {
        body.removeAllViews();
        final Data dd = Store.data;
        sheetItem(body, "fort", "Архивы данных", dd == null ? "BSData — не скачаны" : "BSData · ревизия " + dd.revision + " · " + dd.playable().size() + " фракций", t, false, new View.OnClickListener() {
            public void onClick(View v) { closeSheet(); h.postDelayed(new Runnable() { public void run() { show(new DataScreen(MainActivity.this)); } }, 200); }
        });
        sheetItem(body, "book", "Что нового", "История изменений всех версий", t, false, new View.OnClickListener() {
            public void onClick(View v) { closeSheetNow(); changelog(t); }
        });
        sheetItem(body, "scroll", "Проверить обновления", "Версия " + Updater.myName(this) + (Updater.folder(this).isEmpty() ? " · источник не задан" : ""), t, false, new View.OnClickListener() {
            public void onClick(View v) { closeSheetNow(); Updater.manual(MainActivity.this, t); }
        });
        sheetItem(body, "fort", "Источник обновлений", Updater.folder(this).isEmpty() ? "Не задан" : Updater.folder(this), t, false, new View.OnClickListener() {
            public void onClick(View v) { closeSheetNow(); Updater.askFolder(MainActivity.this, t); }
        });
        toggleItem(body, t, "Тема «Когитатор» (тёмная)", Ui.dark ? "Сейчас: Когитатор" : "Сейчас: Пергамент", Ui.dark, new Runnable() {
            public void run() { toggleDark(); }
        });
        toggleItem(body, t, "Звуки интерфейса", "Щелчки, пергамент, колокола", Sfx.on, new Runnable() {
            public void run() { Sfx.on = !Sfx.on; setPref("sound", Sfx.on); Sfx.play("bell"); fillSettings(body, t, after); }
        });
        toggleItem(body, t, "Голос глашатая", "Название легиона и девиз при выборе", Voice.on, new Runnable() {
            public void run() { Voice.on = !Voice.on; setPref("voice", Voice.on); if (Voice.on) Voice.say("The herald stands ready."); fillSettings(body, t, after); }
        });
        sheetItem(body, "psyker", "Проверить голос", Voice.status, t, false, new View.OnClickListener() {
            public void onClick(View v) {
                if (!Voice.on) { toast("Включите «Голос глашатая»"); return; }
                Voice.say("The Sixteenth Legion. Sons of Horus. Where the Warmaster leads, we follow.");
                if (!Voice.working()) toast(Voice.status);
                else toast("Если не слышно — прибавьте громкость мультимедиа");
            }
        });
        sheetItem(body, "crown", "Имя: " + Guide.name(this), "Как к тебе обращается сервочереп", t, false, new View.OnClickListener() {
            public void onClick(View v) { closeSheetNow(); Guide.askName(MainActivity.this, null); }
        });
        sheetItem(body, "skull", "Повторить обучение", "Сервочереп снова всё расскажет", t, false, new View.OnClickListener() {
            public void onClick(View v) {
                closeSheetNow();
                for (String k : new String[]{"guide_welcome", "tip_roster", "tip_unit", "tip_smart", "tip_smart_few"}) prefs.edit().remove(k).apply();
                Guide.welcome(MainActivity.this, true);
            }
        });
        toggleItem(body, t, "Вибрация", null, Ui.hapticsOn, new Runnable() {
            public void run() { Ui.hapticsOn = !Ui.hapticsOn; setPref("haptics", Ui.hapticsOn); Ui.haptic(Ui.DOUBLE); fillSettings(body, t, after); }
        });
        if (Translator.size() > 0) sheetItem(body, null, "Очистить кеш перевода", null, t, true, new View.OnClickListener() {
            public void onClick(View v) { Translator.clear(); fillSettings(body, t, after); }
        });
    }

    /** Картинка юнита: своё или найденное фото. */
    Bitmap unitArt(String entryId, String faction) { return photo(entryId); }

    /** Полноэкранный просмотр картинки. */
    void viewImage(Bitmap b, String title, Theme t) {
        LinearLayout body = openSheet(title, t);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageBitmap(b);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.steelFlat(t, dp(8)));
        frame.setPadding(dp(6), dp(6), dp(6), dp(6));
        frame.addView(iv, new FrameLayout.LayoutParams(-1, -2));
        body.addView(frame);
    }

    void toggleItem(LinearLayout body, Theme t, String label, String sub, boolean on, final Runnable flip) {
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.steelButton(t, dp(8)));
        r.setPadding(dp(14), dp(10), dp(12), dp(10));
        LinearLayout txt = Ui.col(this);
        txt.addView(Ui.text(this, label, 17, Theme.names, Ui.BONE));
        if (sub != null) txt.addView(Ui.text(this, sub, 13, Theme.body, Ui.GOLD));
        r.addView(txt, Ui.weight(1));
        r.addView(new Toggle(this, on, false, t), Ui.lp(dp(44), dp(44)));
        r.setClickable(true);
        Ui.press(r);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); flip.run(); } });
        body.addView(r, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
    }

    /** Латунный флажок/радиокнопка. */
    static final class Toggle extends View {
        final boolean on, radio;
        final Theme theme;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        boolean onParchment;

        Toggle(android.content.Context c, boolean on, boolean radio, Theme t) { super(c); this.on = on; this.radio = radio; this.theme = t; }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), hh = getHeight(), cx = w / 2, cy = hh / 2, r = Math.min(w, hh) / 2 - dp(6);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(onParchment ? 0x33FFFFFF : 0xFF0C100E);
            if (radio) c.drawCircle(cx, cy, r, p); else c.drawRect(cx - r, cy - r, cx + r, cy + r, p);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.6f));
            p.setColor(onParchment ? 0xFF6A4A22 : Ui.GOLD);
            if (radio) c.drawCircle(cx, cy, r, p); else c.drawRect(cx - r, cy - r, cx + r, cy + r, p);
            if (!on) return;
            if (radio) {
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setShader(new android.graphics.RadialGradient(cx - r * 0.2f, cy - r * 0.2f, r, onParchment ? 0xFFC03A2A : 0xFFFFE6A0, onParchment ? 0xFF5A0E08 : 0xFFB07A2A, android.graphics.Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r * 0.58f, p);
                p.setShader(null);
            } else {
                android.graphics.Path ck = new android.graphics.Path();
                p.setStrokeWidth(dp(3));
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                p.setColor(onParchment ? 0xFF8A1C14 : 0xFFF0D08A);
                float ir = r * 0.62f;
                ck.moveTo(cx - ir, cy); ck.lineTo(cx - ir * 0.2f, cy + ir * 0.8f); ck.lineTo(cx + ir, cy - ir * 0.75f);
                c.drawPath(ck, p);
            }
        }
    }

    // ================================================================ фото юнитов
    private static final int PICK = 42;
    private String pendingPhoto;
    private Runnable pendingDone;
    final LruCache<String, Bitmap> photoCache = new LruCache<String, Bitmap>(24);

    File photoFile(String key) { return new File(new File(getFilesDir(), "photos"), safe(key) + ".jpg"); }

    static String safe(String k) { return k.replaceAll("[^a-zA-Z0-9-]", "_"); }

    Bitmap photo(String key) {
        if (key == null) return null;
        Bitmap b = photoCache.get(key);
        if (b != null) return b;
        File f = photoFile(key);
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b != null) photoCache.put(key, b);
        return b;
    }

    void pickPhoto(String key, Runnable done) {
        pendingPhoto = key;
        pendingDone = done;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        Intent ch = Intent.createChooser(i, "Фото миниатюры");
        try {
            // камера: снимок пишется в наш файл через PhotoProvider
            PhotoProvider.shot(this).delete();
            Intent cam = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, PhotoProvider.uri());
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cam.setClipData(android.content.ClipData.newRawUri("photo", PhotoProvider.uri()));
            if (cam.resolveActivity(getPackageManager()) != null) ch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
        } catch (Exception ignored) { }
        try { startActivityForResult(ch, PICK); } catch (Exception e) { toast("Нет приложения для выбора фото"); }
    }

    /** Загрузить картинку с учётом поворота из EXIF и ужать до maxSide. */
    Bitmap loadImage(Uri u, File f, int maxSide) throws java.io.IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = f != null ? new java.io.FileInputStream(f) : getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > maxSide * 2) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        in = f != null ? new java.io.FileInputStream(f) : getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, o);
        in.close();
        if (b == null) return null;
        int rot = 0;
        try {
            android.media.ExifInterface ex = null;
            if (f != null) ex = new android.media.ExifInterface(f.getPath());
            else if (android.os.Build.VERSION.SDK_INT >= 24) { InputStream ei = getContentResolver().openInputStream(u); ex = new android.media.ExifInterface(ei); ei.close(); }
            if (ex != null) {
                int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
            }
        } catch (Exception ignored) { }
        float sc = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        if (rot != 0 || sc < 1f) {
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postScale(sc, sc);
            m.postRotate(rot);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            b = r;
        }
        return b;
    }

    /** Сохранить картинку как фото юнита. */
    void savePhoto(String key, Bitmap b) throws java.io.IOException {
        File f = photoFile(key);
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        out.close();
        photoCache.put(key, b);
    }

    /** Маленькая кнопка-фотоаппарат рядом с юнитом. */
    View cameraButton(final String entryId, final String unitName, final String faction, final Theme t, final Runnable done) {
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Ui.states(new Ui.Steel(0xFF2A3430, 0xFF121815, Ui.GOLD_DIM, 0, dp(6), false, false), new Ui.Steel(0xFF3A4440, 0xFF1A2220, Ui.GOLD, 0, dp(6), false, true)));
        b.addView(new RosterScreen.GlyphView(this, "camera", Ui.GOLD), new FrameLayout.LayoutParams(-1, -1));
        b.setClickable(true);
        Ui.press(b);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("click"); searchImages(entryId, unitName, faction, t, done); }
        });
        return b;
    }

    /** Поиск картинок юнита (как камера в New Recruit, но внутри приложения). */
    void searchImages(String entryId, String unitName, String faction, Theme t, Runnable done) {
        show(new ImageSearchScreen(this, entryId, unitName, faction, t, done));
    }

    void removePhoto(String key) {
        photoFile(key).delete();
        photoCache.remove(key);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_BOOKS) {
            final Library.Progress pr = pendingBooks;
            pendingBooks = null;
            if (res != RESULT_OK || data == null || pr == null) return;
            java.util.List<Uri> us = new java.util.ArrayList<Uri>();
            if (data.getClipData() != null) for (int k = 0; k < data.getClipData().getItemCount(); k++) us.add(data.getClipData().getItemAt(k).getUri());
            else if (data.getData() != null) us.add(data.getData());
            if (us.isEmpty()) return;
            pr.step("Начинаю: файлов " + us.size());
            Library.importUris(this, us, pr);
            return;
        }
        if (req == PICK_ROS) { if (res == RESULT_OK && data != null && data.getData() != null) importRoster(data.getData()); return; }
        if (req != PICK || res != RESULT_OK || pendingPhoto == null) return;
        try {
            Uri u = data == null ? null : data.getData();
            File shot = PhotoProvider.shot(this);
            boolean camera = (u == null || PhotoProvider.AUTH.equals(u.getAuthority())) && shot.exists() && shot.length() > 0;
            if (u == null && !camera) return;
            Bitmap b = loadImage(camera ? null : u, camera ? shot : null, 1400);
            if (b == null) { toast("Не удалось открыть изображение"); return; }
            File f = photoFile(pendingPhoto);
            f.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, 86, out);
            out.close();
            photoCache.put(pendingPhoto, b);
            Sfx.play("seal");
            Ui.haptic(Ui.DOUBLE);
            if (pendingDone != null) pendingDone.run();
        } catch (Exception e) {
            toast("Ошибка: " + e.getMessage());
        }
    }

    // ================================================================ загрузка
    static final class LoadingScreen extends Screen {
        TextView status;
        View bar;
        FrameLayout barBox;

        LoadingScreen(MainActivity a) { super(a); }

        View build() {
            FrameLayout f = new FrameLayout(a);
            LinearLayout c = Ui.col(a);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(30), 0, dp(30), dp(100));
            View hx = new Ui.ServoSkull(a);
            c.addView(hx, Ui.lp(dp(130), dp(170)));
            TextView t = Ui.text(a, "LIBER ROSTERUM", 30, Theme.namesBlack, Ui.BONE);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(dp(8), 0, dp(2), 0xFF000000);
            c.addView(t, Ui.lpm(-2, -2, 0, dp(14), 0, 0));
            status = Ui.text(a, "Вскрываем архивы…", 15, Theme.body, Ui.GOLD);
            status.setGravity(Gravity.CENTER);
            c.addView(status, Ui.lpm(-1, -2, 0, dp(18), 0, dp(8)));
            barBox = new FrameLayout(a);
            barBox.setBackground(Ui.steelFlat(theme, dp(4)));
            bar = new View(a);
            bar.setBackground(new Ui.Metal(0xFFE8C67A, 0xFF8E6E32, 0x00000000, dp(4)));
            barBox.addView(bar, new FrameLayout.LayoutParams(0, -1));
            c.addView(barBox, Ui.lp(-1, dp(12)));
            f.addView(c, new FrameLayout.LayoutParams(-1, -1));
            f.addView(new Ui.Candles(a, 7, 3), new FrameLayout.LayoutParams(-1, dp(210), Gravity.BOTTOM));
            hx.setScaleX(0.5f);
            hx.setScaleY(0.5f);
            hx.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(new OvershootInterpolator(2f)).start();
            return f;
        }

        void progress(String what, int done, int total) {
            status.setText("Читаем: " + what);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            lp.width = total == 0 ? 0 : barBox.getWidth() * done / total;
            bar.setLayoutParams(lp);
        }
    }

    // ================================================================ данные
    static final class DataScreen extends Screen {
        String error;
        TextView status, btn;
        boolean busy;

        DataScreen(MainActivity a) { super(a); }

        View build() {
            FrameLayout f = new FrameLayout(a);
            LinearLayout c = a.column();
            boolean has = a.store.hasData();
            boolean nav = has && Store.data != null;
            if (nav) c.addView(a.header("Архивы данных", "Каталоги BSData", theme, true));
            LinearLayout box = Ui.col(a);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.addView(new Ui.Hex(a, theme, "skull"), Ui.lpm(dp(110), dp(110), 0, dp(16), 0, 0));
            TextView t = Ui.text(a, "LIBER ROSTERUM", 28, Theme.namesBlack, Ui.BONE);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(dp(8), 0, dp(2), 0xFF000000);
            box.addView(t, Ui.lpm(-2, -2, 0, dp(10), 0, 0));
            TextView sub = Ui.title(a, "ростеры и битвы · Ересь, 3-я редакция", 16, Ui.GOLD);
            sub.setGravity(Gravity.CENTER);
            box.addView(sub);
            box.addView(Ui.skullDivider(a, Ui.GOLD_DIM));
            LinearLayout card = Ui.col(a);
            card.setBackground(Ui.parchment());
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            card.addView(Ui.text(a, "Юниты, очки и правила берутся из открытых данных сообщества BSData — тех же, на которых работает New Recruit. "
                    + "Их нужно один раз скачать (около 22 МБ), затем обновлять, когда сообщество вносит правки.\n\nИсточник: github.com/" + Store.REPO, 15, Theme.body, Ui.INK));
            Map<String, Object> meta = a.store.meta();
            String st;
            if (has) {
                double up = Data.num(meta, "updated", 0);
                st = "Скачано: " + (up > 0 ? new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("ru")).format(new Date((long) up)) : "—");
                if (Store.data != null) st += " · ревизия " + Store.data.revision;
            } else st = "Данные ещё не скачаны";
            if (error != null) st = error;
            status = Ui.text(a, st, 15, Theme.bodyBold, error != null ? Ui.INK_RED : Ui.INK);
            status.setPadding(0, dp(12), 0, dp(12));
            card.addView(status);
            btn = Ui.button(a, has ? "Проверить обновления" : "Скачать данные", Ui.brass(dp(8)), 0xFF1A140C, 19, new View.OnClickListener() {
                public void onClick(View v) { download(); }
            });
            card.addView(btn, Ui.lp(-1, dp(52)));
            box.addView(card, Ui.lpm(-1, -2, 0, dp(8), 0, 0));
            c.addView(box);
            c.setPadding(c.getPaddingLeft(), c.getPaddingTop(), c.getPaddingRight(), dp(120));
            f.addView(a.scroll(c), new FrameLayout.LayoutParams(-1, -1));
            f.addView(new Ui.Candles(a, 6, 9), new FrameLayout.LayoutParams(-1, dp(200), Gravity.BOTTOM));
            return f;
        }

        void download() {
            if (busy) return;
            busy = true;
            btn.setText("Загрузка…");
            new Thread(new Runnable() {
                public void run() {
                    try {
                        final int n = a.store.download(new Store.Progress() {
                            public void step(final String what, final int done, final int total) {
                                a.h.post(new Runnable() { public void run() { status.setTextColor(Ui.INK); status.setText(what + (total > 1 ? "  (" + done + "/" + total + ")" : "")); } });
                            }
                        });
                        a.h.post(new Runnable() {
                            public void run() {
                                busy = false;
                                Ui.haptic(Ui.DOUBLE);
                                Sfx.play("bell");
                                if (n == 0 && Store.data != null) { a.toast("Данные уже актуальны"); btn.setText("Проверить обновления"); return; }
                                Store.data = null;
                                a.startLoading();
                            }
                        });
                    } catch (final Exception e) {
                        a.h.post(new Runnable() {
                            public void run() {
                                busy = false;
                                Ui.haptic(Ui.HEAVY);
                                Sfx.play("error");
                                status.setTextColor(Ui.INK_RED);
                                status.setText("Не получилось: " + e.getMessage() + ". Проверьте интернет.");
                                btn.setText("Повторить");
                            }
                        });
                    }
                }
            }, "download").start();
        }
    }
}
