package com.medoedy.liber;

import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.medoedy.liber.Ui.dp;

/** Сервочереп-наставник: обучение при первом запуске и подсказки на новых экранах. */
final class Guide {
    /** Имя, которым сервочереп обращается к игроку (задаётся перед обучением). */
    static String name(MainActivity a) {
        String n = a.prefs.getString("user_name", "").trim();
        return n.isEmpty() ? "Командир" : n;
    }

    static String personalize(MainActivity a, String text) { return text == null ? null : text.replace("Глеб", name(a)); }

    /** Спросить имя; затем выполнить then. */
    static void askName(final MainActivity a, final Runnable then) {
        LinearLayout body = a.openSheet("Как к тебе обращаться?", "Сервочереп запомнит имя", Theme.DEFAULT);
        LinearLayout row = Ui.row(a);
        row.addView(new Ui.ServoSkull(a), Ui.lp(dp(64), dp(84)));
        TextView t = Ui.text(a, "Приветствую! Прежде чем начнём — назови себя, и я буду обращаться к тебе по имени.", 14, Theme.body, Ui.BONE_DIM);
        t.setPadding(dp(8), 0, 0, 0);
        row.addView(t, Ui.weight(1));
        body.addView(row);
        final android.widget.EditText in = a.input(a.prefs.getString("user_name", ""), "Твоё имя", false);
        body.addView(in, Ui.lpm(-1, -2, 0, dp(8), 0, dp(8)));
        body.addView(Ui.button(a, "Так меня и зовут", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) {
                String n = in.getText().toString().trim();
                if (n.length() > 24) n = n.substring(0, 24);
                a.prefs.edit().putString("user_name", n).apply();
                a.hideKeyboard();
                a.closeSheetNow();
                Sfx.play("seal");
                if (then != null) a.h.postDelayed(then, 250);
            }
        }), Ui.lp(-1, dp(50)));
    }

    static final class Step {
        final String text, action;
        final Runnable run;
        Step(String text) { this(text, null, null); }
        Step(String text, String action, Runnable run) { this.text = text; this.action = action; this.run = run; }
    }

    private final MainActivity a;
    private final List<Step> steps;
    private final String doneKey;
    private int i;
    private FrameLayout root;
    private TextView body, counter;
    private LinearLayout buttons;
    private final Runnable[] typing = new Runnable[1];

    private Guide(MainActivity a, List<Step> steps, String doneKey) { this.a = a; this.steps = steps; this.doneKey = doneKey; }

    // ================================================================ сценарии
    /** Большое обучение при первом запуске. */
    static void welcome(final MainActivity a, boolean force) {
        if (!force && a.prefs.getBoolean("guide_welcome", false)) {
            if (!a.prefs.contains("user_name")) askName(a, null); // уже прошедшим обучение — один раз спросить имя
            return;
        }
        if (!a.prefs.contains("user_name")) { askName(a, new Runnable() { public void run() { welcome(a, true); } }); return; }
        final String NAME = name(a);
        final int rosters = a.store.rosters().size();
        List<Step> s = new ArrayList<Step>();
        s.add(new Step("Приветствую, " + NAME + ". Я — сервочереп-писарь этого когитатора. Позволь показать, как здесь всё устроено. Это займёт минуту."));
        s.add(new Step("Liber Rosterum — книга ростеров Ереси Хоруса, 3-я редакция. Очки, слоты детачментов, снаряжение и правила берутся из данных сообщества BSData — тех же, что в New Recruit и BattleScribe."));
        s.add(new Step("Первым делом, " + NAME + ", перенеси сюда свои ростеры. В New Recruit открой ростер → Export → BattleScribe (.rosz). Файл можно открыть прямо из Telegram или файлового менеджера — выбери Liber Rosterum, — или нажми «Импорт» в «Ростерах армий»."
                + (rosters > 0 ? " Сейчас в архиве ростеров: " + rosters + "." : ""),
                "⇩ Импортировать сейчас", new Runnable() { public void run() { a.pickRosterFile(); } }));
        s.add(new Step("Это важно для «Умного ростера». Я изучаю все твои ростеры, чтобы понять, какие миниатюры у тебя есть, с каким снаряжением и в каком количестве. Чем больше ростеров ты перенесёшь, " + NAME + ", тем точнее я соберу армию."));
        s.add(new Step("«Умный ростер» — в главном меню. Выбери противника и, если знаешь, чем он играет: много техники, орда, элита. Я соберу армию из твоих моделей, предложу прокси на похожие миниатюры и объясню, почему выбрал именно так. Неподходящий юнит можно исключить крестиком."));
        s.add(new Step("В ростере: нажми на юнит, чтобы выбрать снаряжение, а долгое нажатие — дублировать или удалить. Красная печать вверху — проверка ростера. Нажми её, и я покажу, что не так и как это исправить."));
        s.add(new Step("Фотоаппарат рядом с юнитом ищет его картинки в Google. Удерживай понравившуюся — и она станет портретом. А при первом открытии юнита я сам подберу ему изображение."));
        s.add(new Step("Режим «Битва» считает победные очки по ходам и миссиям. В настройках — шестерёнка в меню — можно включить русский перевод правил, голос глашатая и вибрацию, а кнопкой с луной сменить тему: «Когитатор» или «Пергамент»."));
        s.add(new Step("Вот и всё, " + NAME + ". Если забудешь — в настройках есть «Повторить обучение». Да направит Омниссия твои кости."));
        new Guide(a, s, "guide_welcome").show();
    }

    /** Подсказка один раз на экране. */
    static void tip(MainActivity a, String key, String text) {
        if (a.prefs.getBoolean("tip_" + key, false) || !a.prefs.getBoolean("guide_welcome", false)) return;
        List<Step> s = new ArrayList<Step>();
        s.add(new Step(personalize(a, text)));
        new Guide(a, s, "tip_" + key).show();
    }

    // ================================================================ фразы сервочерепа в меню
    private static List<String> phrases;

    static List<String> phrases(MainActivity a) {
        if (phrases != null) return phrases;
        phrases = new ArrayList<String>();
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(a.getAssets().open("phrases.txt"), "UTF-8"));
            String l;
            while ((l = br.readLine()) != null) if (!l.trim().isEmpty()) phrases.add(l.trim());
            br.close();
        } catch (Exception ignored) { }
        if (phrases.isEmpty()) phrases.add("Магнус не передавал!!!");
        return phrases;
    }

    /** Следующая фраза: все по кругу в случайном порядке, без повторов, пока не кончатся. */
    static String nextPhrase(MainActivity a) {
        List<String> ph = phrases(a);
        long seed = a.prefs.getLong("phr_seed", 0);
        if (seed == 0) { seed = System.currentTimeMillis(); a.prefs.edit().putLong("phr_seed", seed).apply(); }
        int i = a.prefs.getInt("phr_i", 0);
        if (i >= ph.size()) { i = 0; seed++; a.prefs.edit().putLong("phr_seed", seed).apply(); }
        List<Integer> order = new ArrayList<Integer>();
        for (int k = 0; k < ph.size(); k++) order.add(k);
        java.util.Collections.shuffle(order, new java.util.Random(seed));
        a.prefs.edit().putInt("phr_i", i + 1).apply();
        return personalize(a, ph.get(order.get(i)));
    }

    // ================================================================ показ
    static boolean showing;

    private void show() {
        if (showing) return;
        showing = true;
        root = new FrameLayout(a);
        root.setBackgroundColor(0x99000000);
        root.setClickable(true);
        LinearLayout panel = Ui.col(a);
        panel.setBackground(Ui.steel(Theme.DEFAULT));
        panel.setPadding(dp(14), dp(14), dp(14), dp(12));
        panel.setClickable(true);
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        final Ui.ServoSkull sk = new Ui.ServoSkull(a);
        row.addView(sk, Ui.lpm(dp(78), dp(104), 0, 0, dp(8), 0));
        LinearLayout bubble = Ui.col(a);
        bubble.setBackground(Ui.parchment());
        bubble.setPadding(dp(14), dp(10), dp(14), dp(12));
        TextView who = Ui.text(a, "СЕРВОЧЕРЕП-ПИСАРЬ", 10, Theme.bodyBold, Ui.INK_RED);
        who.setLetterSpacing(0.12f);
        bubble.addView(who);
        body = Ui.text(a, "", 15, Theme.body, Ui.INK);
        body.setLineSpacing(dp(2), 1f);
        body.setMinLines(4);
        bubble.addView(body, Ui.lpm(-1, -2, 0, dp(4), 0, 0));
        row.addView(bubble, Ui.weight(1));
        panel.addView(row);
        buttons = Ui.row(a);
        panel.addView(buttons, Ui.lpm(-1, -2, 0, dp(10), 0, 0));
        counter = Ui.text(a, "", 11, Theme.bodyBold, Ui.GOLD_DIM);
        counter.setGravity(Gravity.CENTER);
        panel.addView(counter, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(8), 0, dp(8), dp(14));
        root.addView(panel, lp);
        a.overlay.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(200).start();
        panel.setTranslationY(dp(240));
        panel.animate().translationY(0).setDuration(360).setInterpolator(new OvershootInterpolator(1.1f)).start();
        sk.setTranslationX(-dp(80));
        sk.animate().translationX(0).setStartDelay(150).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
        step(0);
    }

    private void step(int k) {
        i = k;
        final Step s = steps.get(k);
        Sfx.play("servo", 0.7f, 0.9f + (k % 3) * 0.1f);
        Ui.haptic(Ui.TICK);
        counter.setText(steps.size() > 1 ? (k + 1) + " / " + steps.size() : "");
        // печатаем текст, как когитатор
        if (typing[0] != null) a.h.removeCallbacks(typing[0]);
        final String full = s.text;
        final int[] pos = {0};
        typing[0] = new Runnable() {
            public void run() {
                pos[0] = Math.min(full.length(), pos[0] + 3);
                body.setText(full.substring(0, pos[0]));
                if (pos[0] < full.length()) a.h.postDelayed(this, 16);
            }
        };
        body.setText("");
        a.h.post(typing[0]);
        body.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { a.h.removeCallbacks(typing[0]); body.setText(full); } });

        buttons.removeAllViews();
        boolean last = k == steps.size() - 1;
        if (!last) {
            TextView skip = Ui.button(a, "Пропустить", Ui.steelButton(Theme.DEFAULT, dp(6)), Ui.BONE_DIM, 14, new View.OnClickListener() {
                public void onClick(View v) { close(); }
            });
            buttons.addView(skip, weighted(0.8f));
        }
        if (s.action != null) {
            TextView act = Ui.button(a, s.action, Ui.heraldic(Theme.DEFAULT, dp(6)), Theme.onColor(Theme.DEFAULT.accent), 14, new View.OnClickListener() {
                public void onClick(View v) { close(); s.run.run(); }
            });
            buttons.addView(act, weighted(1.3f));
        }
        TextView next = Ui.button(a, last ? (steps.size() > 1 ? "Начать" : "Понятно") : "Далее ›", Ui.brass(dp(6)), 0xFF1A140C, 15, new View.OnClickListener() {
            public void onClick(View v) {
                if (i + 1 < steps.size()) step(i + 1);
                else { Sfx.play("seal"); close(); }
            }
        });
        buttons.addView(next, weighted(1f));
    }

    private LinearLayout.LayoutParams weighted(float w) {
        LinearLayout.LayoutParams lp = Ui.lpm(0, dp(44), dp(3), 0, dp(3), 0);
        lp.weight = w;
        return lp;
    }

    private void close() {
        showing = false;
        a.prefs.edit().putBoolean(doneKey, true).apply();
        if (doneKey.equals("guide_welcome")) a.prefs.edit().putBoolean("guide_welcome", true).apply();
        if (typing[0] != null) a.h.removeCallbacks(typing[0]);
        final View r = root;
        r.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() { public void run() { a.overlay.removeView(r); } }).start();
    }
}
