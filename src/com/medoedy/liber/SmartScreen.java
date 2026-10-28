package com.medoedy.liber;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Smart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

/**
 * «Умный ростер»: когитатор изучает ваши ростеры (что есть в коллекции),
 * учитывает противника и собирает армию — из вашего, с прокси на похожие миниатюры и, по желанию, с новинками.
 */
final class SmartScreen extends Screen {
    final Smart.Params p = new Smart.Params();
    Smart.Result result;
    /** Сравнение: три варианта под один запрос. */
    Smart.Result[] variants;
    int variantIdx;
    static final String[][] VARIANTS = {{"", "Как задано"}, {"armour", "Броня"}, {"infantry", "Пехота и маркеры"}};
    String prefsFor;
    boolean busy;
    final List<Roster> all;

    static final String[][] TRAITS = {{"armour", "Много техники"}, {"horde", "Орда"}, {"elite", "Элита, терминаторы"}, {"melee", "Рукопашники"}, {"shoot", "Стрелки"}, {"fast", "Быстрые"}};
    static final String[][] MODES = {{"Только моё", "Юниты из ваших ростеров"}, {"Моё + прокси", "Плюс похожие на ваши миниатюры"}, {"Свободно", "Можно предложить новое"}};

    SmartScreen(MainActivity a) {
        super(a);
        all = a.store.rosters();
        p.myCat = Smart.favouriteFaction(all);
        p.limit = 3000;
        if (p.myCat != null) {
            Data.Cat c = Store.data.cats.get(p.myCat);
            theme = Theme.forName(c == null ? null : c.name);
            for (Roster r : all) if (p.myCat.equals(r.catalogueId)) { p.limit = r.limit; break; }
        }
    }

    View build() {
        a.h.postDelayed(new Runnable() {
            public void run() {
                if (a.stack.peek() != SmartScreen.this) return;
                int n = all.size();
                Guide.tip(a, n < 3 ? "smart_few" : "smart", n < 3
                        ? "Глеб, у тебя пока " + n + " " + MainActivity.plural(n, "ростер", "ростера", "ростеров") + " — по ним я плохо узнаю твою коллекцию. Импортируй из New Recruit всё, чем играешь (Export → BattleScribe .rosz), и мои советы станут точнее."
                        : "Глеб, выбери свою армию, противника и лимит. «Только моё» — строго из твоих моделей, «Моё + прокси» — ещё и то, что можно собрать из похожих миниатюр. Нажми «Вычислить армию».");
            }
        }, 700);
        loadPrefs();
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Умный ростер", "Когитатор соберёт армию под противника", theme, true));

        LinearLayout intro = Ui.row(a);
        intro.addView(new Ui.ServoSkull(a), Ui.lp(dp(70), dp(92)));
        TextView it = Ui.text(a, all.isEmpty() ? Guide.name(a) + ", твоих ростеров пока нет — значит, соберу армию с нуля. Выбери фракцию и характер армии, а я подберу юниты, детачменты и снаряжение. Импортируешь ростеры из New Recruit — начну учитывать и твою коллекцию."
                : "Я изучил " + all.size() + " " + MainActivity.plural(all.size(), "ваш ростер", "ваших ростера", "ваших ростеров")
                + ". Беру прежде всего те юниты, что у вас уже есть, и аккуратно предлагаю то, что можно собрать или проксить из похожих миниатюр.", 14, Theme.body, Ui.INK);
        it.setBackground(new Ui.Bubble());
        it.setPadding(dp(22), dp(9), dp(12), dp(11));
        it.setLineSpacing(dp(1), 1f);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.addView(it, Ui.weight(1));
        c.addView(intro, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));

        // ---------- ваша армия
        c.addView(a.label("ВАША АРМИЯ", Ui.GOLD));
        Map<String, Integer> mine = new LinkedHashMap<String, Integer>();
        for (Roster r : all) if (r.catalogueId != null) mine.put(r.catalogueId, (mine.containsKey(r.catalogueId) ? mine.get(r.catalogueId) : 0) + 1);
        for (Minis.Unit u : Minis.all(a)) if (u.catId != null && !mine.containsKey(u.catId) && u.entryId != null) mine.put(u.catId, 0);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout fr = Ui.row(a);
        for (final Map.Entry<String, Integer> en : mine.entrySet()) {
            Data.Cat cat = Store.data.cats.get(en.getKey());
            if (cat == null) continue;
            boolean on = en.getKey().equals(p.myCat);
            fr.addView(chip(short_(cat.name) + (en.getValue() > 0 ? "  ×" + en.getValue() : "  ✦"), on, new Runnable() {
                public void run() { p.myCat = en.getKey(); Data.Cat cc = Store.data.cats.get(p.myCat); theme = Theme.forName(cc == null ? null : cc.name); a.setBgTheme(theme); result = null; rebuild(); }
            }), Ui.lpm(-2, dp(40), 0, 0, dp(6), 0));
        }
        fr.addView(chip(p.myCat != null && !mine.containsKey(p.myCat) ? short_(Store.data.cats.get(p.myCat).name) + " ›" : "Другая фракция ›", p.myCat != null && !mine.containsKey(p.myCat), new Runnable() {
            public void run() { pickMine(); }
        }), Ui.lpm(-2, dp(40), 0, 0, dp(6), 0));
        hs.addView(fr);
        c.addView(hs);
        final boolean scratch = p.myCat == null || !mine.containsKey(p.myCat);
        // коллекция «Мои миниатюры»
        final int coll = Minis.unitsOf(a, p.myCat);
        LinearLayout cr = Ui.row(a);
        cr.setBackground(Ui.steelButton(theme, dp(8)));
        cr.setPadding(dp(12), dp(8), dp(12), dp(8));
        cr.addView(Ui.text(a, coll > 0 ? "✦ Учитываю «Мои миниатюры»: " + coll + " " + MainActivity.plural(coll, "юнит", "юнита", "юнитов") + " этой фракции — беру реально имеющиеся, прокси только на свободные похожие модели"
                : "✦ «Мои миниатюры» этой фракции пусты — ориентируюсь на ваши ростеры. Добавьте коллекцию, чтобы я знал, что у вас реально есть", 13, Theme.body, coll > 0 ? Ui.BONE : Ui.BONE_DIM), Ui.weight(1));
        cr.addView(Ui.text(a, " ›", 18, Theme.bodyBold, Ui.GOLD));
        cr.setClickable(true);
        Ui.press(cr);
        cr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { result = null; a.show(new MinisScreen(a)); } });
        c.addView(cr, Ui.lpm(-1, -2, 0, dp(6), 0, dp(4)));
        // мой союзник
        c.addView(a.label("МОЙ СОЮЗНИК (Allied Detachment)", Ui.GOLD));
        Ui.Flow myA = new Ui.Flow(a);
        myA.gap = dp(6);
        if (p.myAlly != null && Store.data.cats.get(p.myAlly) != null) {
            myA.addView(chip("✕  " + short_(Store.data.cats.get(p.myAlly).name), true, new Runnable() { public void run() { p.myAlly = null; result = null; rebuild(); } }));
        } else {
            myA.addView(chip("＋ Союзник", false, new Runnable() { public void run() { pickMyAlly(); } }));
        }
        c.addView(myA, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        if (scratch) {
            c.addView(a.label("ХАРАКТЕР АРМИИ (с нуля)", Ui.GOLD));
            Ui.Flow sf = new Ui.Flow(a);
            sf.gap = dp(6);
            for (final String[] st : Smart.STYLES) {
                final boolean on = p.style.contains(st[0]);
                sf.addView(chip(st[1], on, new Runnable() {
                    public void run() { if (on) p.style.remove(st[0]); else p.style.add(st[0]); result = null; rebuild(); }
                }));
            }
            c.addView(sf, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        }

        // ---------- противник
        c.addView(a.label("ПРОТИВНИК", Ui.GOLD));
        Data.Cat ec = p.enemyCat == null ? null : Store.data.cats.get(p.enemyCat);
        final Theme et = Theme.forName(ec == null ? null : ec.name);
        LinearLayout enemy = Ui.row(a);
        enemy.setBackground(Ui.steelButton(et, dp(8)));
        enemy.setPadding(dp(10), dp(8), dp(14), dp(8));
        Ui.Hex ex = new Ui.Hex(a, et, et.numeral.isEmpty() ? "skull" : null);
        ex.label = et.numeral.isEmpty() ? null : et.numeral;
        enemy.addView(ex, Ui.lpm(dp(44), dp(44), 0, 0, dp(12), 0));
        LinearLayout etx = Ui.col(a);
        etx.addView(Ui.text(a, ec == null ? "Не выбран" : short_(ec.name), 18, Theme.names, Ui.BONE));
        etx.addView(Ui.text(a, ec == null ? "Нажмите, чтобы выбрать фракцию соперника" : "Сменить ›", 13, Theme.body, Ui.GOLD));
        enemy.addView(etx, Ui.weight(1));
        enemy.setClickable(true);
        Ui.press(enemy);
        enemy.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickEnemy(); } });
        c.addView(enemy, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        if (ec != null) {
            c.addView(a.label("СОЮЗНИКИ ПРОТИВНИКА", Ui.GOLD));
            Ui.Flow af = new Ui.Flow(a);
            af.gap = dp(6);
            for (final String id : new java.util.ArrayList<String>(p.enemyAllies)) {
                Data.Cat ac = Store.data.cats.get(id);
                if (ac == null) continue;
                af.addView(chip("✕  " + short_(ac.name), true, new Runnable() { public void run() { p.enemyAllies.remove(id); result = null; rebuild(); } }));
            }
            if (p.enemyAllies.size() < 2) af.addView(chip("＋ Союзник", false, new Runnable() { public void run() { pickAlly(); } }));
            c.addView(af, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        }
        c.addView(a.label("МИССИЯ (если знаете)", Ui.GOLD));
        Ui.Flow mf = new Ui.Flow(a);
        mf.gap = dp(6);
        for (final String[] m : Smart.MISSIONS) {
            final boolean on = m[0].equals(p.mission);
            mf.addView(chip(m[1], on, new Runnable() { public void run() { p.mission = on ? null : m[0]; result = null; rebuild(); } }));
        }
        c.addView(mf, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        c.addView(a.label("ЧЕМ ОН ИГРАЕТ (если знаете)", Ui.GOLD));
        Ui.Flow tf = new Ui.Flow(a);
        tf.gap = dp(6);
        for (final String[] t : TRAITS) {
            final boolean on = p.enemyTraits.contains(t[0]);
            tf.addView(chip(t[1], on, new Runnable() {
                public void run() { if (on) p.enemyTraits.remove(t[0]); else p.enemyTraits.add(t[0]); result = null; rebuild(); }
            }));
        }
        c.addView(tf, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));

        // ---------- лимит и режим
        c.addView(a.label("ЛИМИТ ОЧКОВ", Ui.GOLD));
        LinearLayout lr = Ui.row(a);
        int[] lims = {1000, 1500, 2000, 3000, 4000};
        for (final int l : lims) {
            TextView ch = chip(String.valueOf(l), p.limit == l, new Runnable() { public void run() { p.limit = l; result = null; rebuild(); } });
            ch.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = Ui.lpm(0, dp(40), 0, 0, dp(5), 0);
            lp.weight = 1;
            lr.addView(ch, lp);
        }
        c.addView(lr);
        if (!scratch) c.addView(a.label("ИЗ ЧЕГО СОБИРАТЬ", Ui.GOLD));
        for (int i = 0; i < (scratch ? 0 : MODES.length); i++) {
            final int m = i;
            boolean on = p.mode == i;
            LinearLayout row = Ui.row(a);
            row.setBackground(on ? Ui.heraldic(theme, dp(6)) : Ui.steelButton(theme, dp(6)));
            row.setPadding(dp(12), dp(8), dp(12), dp(9));
            LinearLayout tt = Ui.col(a);
            tt.addView(Ui.text(a, MODES[i][0], 16, Theme.names, on ? Theme.onColor(theme.accent) : Ui.BONE));
            tt.addView(Ui.text(a, MODES[i][1], 12, Theme.body, on ? Theme.onColor(theme.accent) : Ui.GOLD));
            row.addView(tt, Ui.weight(1));
            row.addView(Ui.text(a, on ? "◉" : "○", 20, Theme.bodyBold, on ? Theme.onColor(theme.accent) : Ui.GOLD_DIM));
            row.setClickable(true);
            Ui.press(row);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("servo"); p.mode = m; result = null; rebuild(); }
            });
            c.addView(row, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }

        // ---------- разнообразие
        c.addView(a.label("РАЗНООБРАЗИЕ", Ui.GOLD));
        Ui.Flow dv = new Ui.Flow(a);
        dv.gap = dp(6);
        final int[] ms = {0, 3, 2, 1};
        final String[] ml = {"Без ограничений", "≤ 3 одинаковых", "≤ 2 одинаковых", "Все разные"};
        for (int i = 0; i < ms.length; i++) {
            final int v = ms[i];
            dv.addView(chip(ml[i], p.maxSame == v, new Runnable() { public void run() { p.maxSame = v; savePrefs(); result = null; variants = null; rebuild(); } }));
        }
        c.addView(dv, Ui.lpm(-1, -2, 0, dp(2), 0, dp(2)));

        // ---------- обязательно взять
        c.addView(a.label("ОБЯЗАТЕЛЬНО ВЗЯТЬ", Ui.GOLD));
        Ui.Flow pins = new Ui.Flow(a);
        pins.gap = dp(6);
        for (final String id : new ArrayList<String>(p.include)) {
            Map<String, Object> en = Store.data.entries.get(id);
            String nm = en == null ? "?" : Data.clean(Data.str(en, "name"));
            pins.addView(chip("📌  " + nm + "   ✕", true, new Runnable() { public void run() { p.include.remove(id); savePrefs(); result = null; rebuild(); } }));
        }
        pins.addView(chip(p.include.isEmpty() ? "📌  Выбрать юниты…" : "＋  Ещё", false, new Runnable() { public void run() { pickPins(); } }));
        c.addView(pins, Ui.lpm(-1, -2, 0, dp(2), 0, dp(2)));
        if (p.include.isEmpty()) {
            TextView ph = Ui.text(a, "Отметьте любимцев — когитатор поставит их первыми и соберёт армию вокруг них.", 12, Theme.body, Ui.BONE);
            ph.setAlpha(0.8f);
            c.addView(ph, Ui.lpm(-1, -2, dp(2), 0, 0, dp(4)));
        }

        TextView go = Ui.button(a, busy ? "Когитатор вычисляет…" : result == null ? "⚙  Вычислить армию" : "⚙  Пересобрать вариант", Ui.brass(dp(8)), 0xFF1A140C, 19, new View.OnClickListener() {
            public void onClick(View v) { compute(result != null); }
        });
        c.addView(go, Ui.lpm(-1, dp(56), 0, dp(14), 0, dp(6)));
        if (!busy) c.addView(Ui.button(a, "⚖  Сравнить 3 варианта", Ui.steelButton(theme, dp(8)), Ui.BONE, 16, new View.OnClickListener() {
            public void onClick(View v) { compareVariants(); }
        }), Ui.lpm(-1, dp(48), 0, 0, 0, dp(6)));
        if (variants != null) c.addView(compareCard(), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));

        if (result != null) showResult(c);
        f.addView(a.scroll(c));
        return f;
    }

    /** «Обязательно» и «исключённые» — запоминаются для каждой фракции. */
    void loadPrefs() {
        if (p.myCat == null || p.myCat.equals(prefsFor)) return;
        prefsFor = p.myCat;
        p.include.clear();
        p.exclude.clear();
        String inc = a.prefs.getString("smart_inc_" + p.myCat, ""), exc = a.prefs.getString("smart_exc_" + p.myCat, "");
        for (String x : inc.split(",")) if (!x.isEmpty()) p.include.add(x);
        for (String x : exc.split(",")) if (!x.isEmpty()) p.exclude.add(x);
        p.maxSame = a.prefs.getInt("smart_maxsame", 0);
    }

    void savePrefs() {
        if (p.myCat == null) return;
        a.prefs.edit().putString("smart_inc_" + p.myCat, android.text.TextUtils.join(",", p.include))
                .putString("smart_exc_" + p.myCat, android.text.TextUtils.join(",", p.exclude))
                .putInt("smart_maxsame", p.maxSame).apply();
    }

    static Smart.Params copyParams(Smart.Params p) {
        Smart.Params q = new Smart.Params();
        q.myCat = p.myCat; q.enemyCat = p.enemyCat; q.enemyTraits = new HashSet<String>(p.enemyTraits); q.limit = p.limit; q.mode = p.mode;
        q.seed = p.seed; q.exclude = new HashSet<String>(p.exclude); q.style = new HashSet<String>(p.style); q.enemyAllies = new ArrayList<String>(p.enemyAllies);
        q.mission = p.mission; q.myAlly = p.myAlly; q.minis = p.minis; q.include = new HashSet<String>(p.include); q.maxSame = p.maxSame;
        return q;
    }

    static String short_(String n) { return n == null ? "" : n.replaceAll("^[IVXL]+ - ", ""); }

    TextView chip(String label, boolean on, final Runnable r) {
        TextView t = Ui.text(a, label, 14, Theme.bodyBold, on ? Theme.onColor(theme.accent) : Ui.BONE);
        t.setBackground(on ? Ui.heraldic(theme, dp(5)) : Ui.steelButton(theme, dp(5)));
        t.setPadding(dp(12), dp(8), dp(12), dp(9));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setClickable(true);
        Ui.press(t);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("tick"); r.run(); } });
        return t;
    }

    /** Любая фракция для своей армии (если её ростеров нет — соберём с нуля). */
    void pickMine() {
        LinearLayout body = a.openSheet("Ваша армия", "Без ростеров этой фракции соберу армию с нуля", theme);
        for (final Data.Cat cat : Store.data.playable()) {
            Theme t = Theme.forName(cat.name);
            LinearLayout it = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, short_(cat.name), null, t, false, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheet();
                    p.myCat = cat.id;
                    theme = Theme.forName(cat.name);
                    a.setBgTheme(theme);
                    result = null;
                    Voice.say(Voice.proclamation(cat.name));
                    rebuild();
                }
            });
            if (!t.numeral.isEmpty()) {
                Ui.Hex hx = new Ui.Hex(a, t, null);
                hx.label = t.numeral;
                it.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
            }
        }
    }

    /** Выбор юнитов «обязательно взять»: сначала то, что есть в коллекции и ростерах. */
    void pickPins() {
        LinearLayout body = a.openSheet("Обязательно взять", "Когитатор поставит их первыми", theme);
        List<String> cats = new ArrayList<String>();
        if (p.myCat != null) cats.add(p.myCat);
        if (p.myAlly != null) cats.add(p.myAlly);
        for (final String cid : cats) {
            Data.Cat cat = Store.data.cats.get(cid);
            if (cat == null) continue;
            Set<String> owned = new HashSet<String>();
            for (Minis.Unit u : Minis.all(a)) if (cid.equals(u.catId) && u.entryId != null) owned.add(u.entryId);
            for (Roster r : all) {
                List<Roster.Force> fs = new ArrayList<Roster.Force>();
                r.allForces(fs);
                for (Roster.Force fo : fs) if (cid.equals(fo.catalogueId)) for (Roster.Sel sl : fo.sels) owned.add(sl.entryId);
            }
            body.addView(a.label(short_(cat.name).toUpperCase() + (cid.equals(p.myAlly) ? " · СОЮЗНИК" : ""), Ui.GOLD));
            List<MinisScreen.Entry> es = MinisScreen.entries(cid);
            List<MinisScreen.Entry> rest = new ArrayList<MinisScreen.Entry>();
            for (int pass = 0; pass < 2; pass++) for (final MinisScreen.Entry en : es) {
                boolean mine = owned.contains(en.id);
                if ((pass == 0) != mine) continue;
                if (p.include.contains(en.id)) continue;
                a.sheetItem(body, mine ? "star" : null, en.name, mine ? "есть у вас" : "нет в коллекции — возьму как новый", theme, false, new View.OnClickListener() {
                    public void onClick(View v) { a.closeSheet(); p.include.add(en.id); savePrefs(); result = null; Sfx.play("seal"); rebuild(); }
                });
            }
        }
    }

    void pickMyAlly() {
        LinearLayout body = a.openSheet("Мой союзник", "До половины очков армии, по правилам Allied Detachment", theme);
        for (final Data.Cat cat : Store.data.playable()) {
            if (cat.id.equals(p.myCat)) continue;
            Theme t = Theme.forName(cat.name);
            int n = Minis.unitsOf(a, cat.id);
            LinearLayout it = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, short_(cat.name), n > 0 ? "в коллекции: " + n : null, t, false, new View.OnClickListener() {
                public void onClick(View v) { a.closeSheet(); p.myAlly = cat.id; result = null; rebuild(); }
            });
            if (!t.numeral.isEmpty()) {
                Ui.Hex hx = new Ui.Hex(a, t, null);
                hx.label = t.numeral;
                it.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
            }
        }
    }

    void pickAlly() {
        LinearLayout body = a.openSheet("Союзник противника", "Allied Detachment в его армии", theme);
        for (final Data.Cat cat : Store.data.playable()) {
            if (cat.id.equals(p.enemyCat) || p.enemyAllies.contains(cat.id)) continue;
            Theme t = Theme.forName(cat.name);
            LinearLayout it = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, short_(cat.name), null, t, false, new View.OnClickListener() {
                public void onClick(View v) { a.closeSheet(); p.enemyAllies.add(cat.id); result = null; rebuild(); }
            });
            if (!t.numeral.isEmpty()) {
                Ui.Hex hx = new Ui.Hex(a, t, null);
                hx.label = t.numeral;
                it.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
            }
        }
    }

    void pickEnemy() {
        LinearLayout body = a.openSheet("Противник", "Против кого собираем армию", theme);
        a.sheetItem(body, "skull", "Не знаю", "Универсальный состав", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); p.enemyCat = null; p.enemyAllies.clear(); result = null; rebuild(); }
        });
        for (final Data.Cat cat : Store.data.playable()) {
            Theme t = Theme.forName(cat.name);
            LinearLayout it = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, short_(cat.name), null, t, false, new View.OnClickListener() {
                public void onClick(View v) { a.closeSheet(); p.enemyCat = cat.id; p.enemyAllies.remove(cat.id); result = null; Voice.say(Voice.proclamation(cat.name)); rebuild(); }
            });
            if (!t.numeral.isEmpty()) {
                Ui.Hex hx = new Ui.Hex(a, t, null);
                hx.label = t.numeral;
                it.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
            }
        }
    }

    @Override
    void refresh() { rebuild(); }

    void compute(boolean reroll) {
        if (busy) return;
        if (p.myCat == null) { a.toast("Выберите фракцию армии"); pickMine(); return; }
        busy = true;
        variants = null;
        if (reroll) p.seed = System.currentTimeMillis();
        Sfx.play("servo");
        Sfx.play("hum", 0.6f, 1f);
        Ui.pulses(new int[]{0, 120, 240}, new int[]{Ui.TICK});
        rebuild();
        p.minis = Minis.forSmart(a);
        final Smart.Params pp = p;
        new Thread(new Runnable() {
            public void run() {
                final Smart.Result r = new Smart(Store.data).build(all, pp);
                a.h.post(new Runnable() {
                    public void run() {
                        busy = false;
                        result = r;
                        if (r.error != null) { a.toast(r.error); Sfx.play("error"); }
                        else { Sfx.play("seal"); Ui.haptic(Ui.DOUBLE); }
                        rebuild();
                    }
                });
            }
        }, "smart").start();
    }

    void showResult(LinearLayout c) {
        final Smart.Result r = result;
        if (r.error != null) {
            TextView t = Ui.text(a, r.error, 15, Theme.body, Ui.RED_BAD);
            t.setPadding(dp(6), dp(10), dp(6), dp(10));
            c.addView(t);
            return;
        }
        Engine e = new Engine(Store.data, r.roster);
        double pts = e.rosterCost(Store.data.pointsId);
        c.addView(a.ribbonTitle("ПРЕДЛОЖЕНИЕ КОГИТАТОРА", theme));

        // сводка
        LinearLayout sum = Ui.row(a);
        sum.setBackground(Ui.steel(theme));
        sum.setPadding(dp(14), dp(12), dp(12), dp(12));
        LinearLayout left = Ui.col(a);
        TextView big = Ui.text(a, Engine.fmt(pts) + "  / " + Engine.fmt(r.roster.limit), 26, Theme.namesBlack, Ui.BONE);
        left.addView(big);
        Ui.Gauge g = new Ui.Gauge(a, theme);
        g.set(pts, r.roster.limit);
        left.addView(g, Ui.lpm(-1, dp(12), 0, dp(6), 0, dp(4)));
        int own = 0, prox = 0, nw = 0;
        for (Smart.Pick k : r.picks) if (k.status == Smart.OWNED) own++; else if (k.status == Smart.PROXY) prox++; else nw++;
        left.addView(Ui.text(a, own + " из коллекции · " + prox + " прокси" + (nw > 0 ? " · " + nw + " новых" : ""), 13, Theme.body, Ui.GOLD));
        sum.addView(left, Ui.weight(1));
        Ui.Seal seal = new Ui.Seal(a);
        seal.set(r.errors);
        sum.addView(seal, Ui.lpm(dp(56), dp(56), dp(10), 0, 0, 0));
        c.addView(sum, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));

        // стратегия — наглядно: архетип, ядро, маркеры, задача, слабое место, план
        if (r.archetype != null) c.addView(strategyCard(r, e), Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        // профиль армии
        c.addView(profileCard(r), Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        // почему так — свёрнуто
        c.addView(whyCard(r), Ui.lpm(-1, -2, 0, 0, 0, dp(6)));

        // юниты
        c.addView(a.label("СОСТАВ  ·  📌 — обязательно  ·  ✕ — исключить", Ui.GOLD));
        for (final Smart.Pick k : r.picks) {
            LinearLayout row = Ui.row(a);
            int tint = k.status == Smart.OWNED ? 0 : k.status == Smart.PROXY ? 0xFFB08A20 : 0xFFB02020;
            row.setBackground(new Ui.Parchment(dp(6), false, tint, false));
            row.setPadding(dp(8), dp(7), dp(8), dp(8));
            Ui.Hex hx = new Ui.Hex(a, theme, Units.kind(e, e.entryOf(k.sel), e.linkOf(k.sel)));
            hx.photo = a.unitArt(k.sel.entryId, short_(Store.data.cats.get(r.roster.catalogueId).name));
            row.addView(hx, Ui.lpm(dp(42), dp(42), 0, 0, dp(10), 0));
            LinearLayout mid = Ui.col(a);
            mid.addView(Ui.text(a, k.name, 16, Theme.names, Ui.INK));
            String tag = (k.pinned ? "📌 " : "") + (k.status == Smart.OWNED ? "✓ В коллекции" + (k.rosters > 1 ? " · в " + k.rosters + " ростерах" : "")
                    : k.status == Smart.PROXY ? "≈ Прокси: можно собрать из «" + k.proxyOf + "»" : "✦ Новый юнит");
            mid.addView(Ui.text(a, tag + " · " + Units.slotRu(k.slot), 12, Theme.bodyBold, k.status == Smart.OWNED ? 0xFF3E6A22 : k.status == Smart.PROXY ? 0xFF8A5A10 : Ui.INK_RED));
            row.addView(mid, Ui.weight(1));
            row.setClickable(true);
            Ui.press(row);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); explainSheet(k); } });
            row.addView(Ui.text(a, Engine.fmt(k.pts), 18, Theme.namesBlack, Ui.INK_RED), Ui.lpm(-2, -2, dp(6), 0, dp(6), 0));
            final boolean pinned = p.include.contains(k.entryId);
            TextView pin = Ui.button(a, "📌", pinned ? Ui.brass(dp(5)) : Ui.steelButton(theme, dp(5)), Ui.BONE, 14, new View.OnClickListener() {
                public void onClick(View v) {
                    if (pinned) { p.include.remove(k.entryId); savePrefs(); a.toast("«" + k.name + "» больше не обязателен"); }
                    else { p.include.add(k.entryId); savePrefs(); a.toast("«" + k.name + "» — обязательно в следующих вариантах"); Sfx.play("seal"); }
                    rebuild();
                }
            });
            pin.setPadding(0, 0, 0, dp(2));
            if (!pinned) pin.setAlpha(0.75f);
            row.addView(pin, Ui.lpm(dp(36), dp(36), 0, 0, dp(6), 0));
            TextView x = Ui.button(a, "✕", Ui.steelButton(theme, dp(5)), Ui.RED_BAD, 14, new View.OnClickListener() {
                public void onClick(View v) { p.exclude.add(k.entryId); p.include.remove(k.entryId); savePrefs(); Sfx.play("burn"); compute(false); }
            });
            x.setPadding(0, 0, 0, dp(2));
            row.addView(x, Ui.lp(dp(36), dp(36)));
            c.addView(row, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
        if (!p.exclude.isEmpty()) {
            c.addView(Ui.button(a, "Вернуть исключённые (" + p.exclude.size() + ")", Ui.steelButton(theme, dp(6)), Ui.BONE_DIM, 14, new View.OnClickListener() {
                public void onClick(View v) { p.exclude.clear(); savePrefs(); compute(false); }
            }), Ui.lpm(-1, dp(44), 0, dp(6), 0, 0));
        }
        c.addView(Ui.button(a, "Скрепить печатью и открыть ростер", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) {
                r.roster.updated = System.currentTimeMillis();
                a.store.save(r.roster);
                Sfx.play("seal");
                Ui.haptic(Ui.DOUBLE);
                Data.Cat cc = Store.data.cats.get(r.roster.catalogueId);
                if (cc != null) Voice.say(Voice.proclamation(cc.name));
                a.replaceTop(new RosterScreen(a, r.roster));
            }
        }), Ui.lpm(-1, dp(54), 0, dp(12), 0, dp(8)));
    }

    // ================================================================ наглядный план
    static final int[] ROLE_COL = {0xFFB0501A, 0xFF3E6A22, 0xFF7A2A8A, 0xFF2A5A8A, 0xFFA02020, 0xFF1F7A7A};

    static String archGlyph(String arch) {
        if (arch == null) return "shield";
        if (arch.startsWith("Броне")) return "tank";
        if (arch.startsWith("Стена")) return "walker";
        if (arch.startsWith("Элит")) return "crown";
        if (arch.startsWith("Мобил")) return "bike";
        if (arch.startsWith("Пехот")) return "infantry";
        return "shield";
    }

    TextView pill(String text, int color) {
        TextView t = Ui.text(a, text, 12, Theme.bodyBold, 0xFFFFF4E0);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setPadding(dp(9), dp(3), dp(9), dp(4));
        return t;
    }

    TextView softPill(String text) {
        TextView t = Ui.text(a, text, 13, Theme.bodyBold, Ui.INK);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x22000000);
        g.setStroke(dp(1), 0x558A6A3A);
        g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setPadding(dp(9), dp(3), dp(9), dp(4));
        return t;
    }

    TextView cap(String s) {
        TextView t = Ui.text(a, s, 11, Theme.bodyBold, Ui.INK_RED);
        t.setLetterSpacing(0.12f);
        return t;
    }

    /** Строка-блок: иконка в кружке слева, заголовок, текст, чипы. */
    LinearLayout infoRow(String glyph, int col, String title, String text, List<String> chips) {
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        android.widget.FrameLayout ic = new android.widget.FrameLayout(a);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(col);
        ic.setBackground(g);
        ic.addView(new RosterScreen.GlyphView(a, glyph, 0xFFFFF1D8), new android.widget.FrameLayout.LayoutParams(-1, -1));
        ic.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.addView(ic, Ui.lpm(dp(34), dp(34), 0, dp(2), dp(10), 0));
        LinearLayout col2 = Ui.col(a);
        col2.addView(cap(title));
        if (text != null) {
            TextView t = Ui.text(a, text, 14, Theme.body, Ui.INK);
            t.setLineSpacing(dp(1), 1f);
            col2.addView(t, Ui.lpm(-1, -2, 0, dp(2), 0, 0));
        }
        if (chips != null && !chips.isEmpty()) {
            Ui.Flow fl = new Ui.Flow(a);
            fl.gap = dp(5);
            for (String ch : chips) fl.addView(softPill(ch));
            col2.addView(fl, Ui.lpm(-1, -2, 0, dp(5), 0, 0));
        }
        row.addView(col2, Ui.weight(1));
        return row;
    }

    View divider() {
        View v = new View(a);
        v.setBackgroundColor(0x338A6A3A);
        return v;
    }

    View strategyCard(Smart.Result r, Engine e) {
        LinearLayout sp = Ui.col(a);
        sp.setBackground(Ui.parchment());
        sp.setPadding(dp(14), dp(12), dp(14), dp(14));
        // шапка: архетип
        LinearLayout hd = Ui.row(a);
        hd.addView(new Ui.Hex(a, theme, archGlyph(r.archetype)), Ui.lpm(dp(52), dp(52), 0, 0, dp(12), 0));
        LinearLayout ht = Ui.col(a);
        ht.addView(cap("СУТЬ АРМИИ"));
        ht.addView(Ui.text(a, r.archetype, 21, Theme.names, Ui.INK));
        String sub = Engine.fmt(e.rosterCost(Store.data.pointsId)) + " очков";
        if (p.enemyCat != null && Store.data.cats.get(p.enemyCat) != null) sub += " · против " + short_(Store.data.cats.get(p.enemyCat).name);
        ht.addView(Ui.text(a, sub, 13, Theme.bodyBold, Ui.INK_DIM));
        hd.addView(ht, Ui.weight(1));
        sp.addView(hd);
        // ядро
        if (!r.core.isEmpty()) {
            sp.addView(divider(), Ui.lpm(-1, dp(1), 0, dp(10), 0, dp(8)));
            sp.addView(cap("ЯДРО АРМИИ"));
            for (String[] cr : r.core) {
                LinearLayout row = Ui.row(a);
                Smart.Pick pk = null;
                for (Smart.Pick k : r.picks) if (k.name.equals(cr[0])) { pk = k; break; }
                Ui.Hex hx = new Ui.Hex(a, theme, pk == null ? "infantry" : Units.kind(e, e.entryOf(pk.sel), e.linkOf(pk.sel)));
                if (pk != null) hx.photo = a.unitArt(pk.sel.entryId, short_(Store.data.cats.get(r.roster.catalogueId).name));
                row.addView(hx, Ui.lpm(dp(36), dp(36), 0, 0, dp(10), 0));
                TextView nm = Ui.text(a, cr[0], 15, Theme.names, Ui.INK);
                row.addView(nm, Ui.weight(1));
                int ri = 0;
                try { ri = Integer.parseInt(cr[2]); } catch (Exception ignored) { }
                row.addView(pill(cr[1], ROLE_COL[Math.max(0, Math.min(5, ri))]));
                sp.addView(row, Ui.lpm(-1, -2, 0, dp(5), 0, 0));
            }
        }
        sp.addView(divider(), Ui.lpm(-1, dp(1), 0, dp(10), 0, dp(10)));
        // задача и слабое место
        if (r.task != null) {
            sp.addView(infoRow("cannon", r.taskIdx >= 0 ? ROLE_COL[r.taskIdx] : 0xFF8A3A1A, "ГЛАВНАЯ ЗАДАЧА", "Против этого врага важнее всего — " + r.task + ".", r.taskUnits));
        }
        if (r.weak != null) {
            LinearLayout w = infoRow("skull", 0xFF7A1A14, "СЛАБОЕ МЕСТО", r.weak.substring(0, 1).toUpperCase() + r.weak.substring(1) + ": не подставляйтесь там, где враг в этом силён, берегите отряды, которые это закрывают.", null);
            sp.addView(w, Ui.lpm(-1, -2, 0, dp(10), 0, 0));
        }
        if (r.holdNote != null) {
            sp.addView(infoRow("banner", 0xFF6A5420, "МАРКЕРЫ", r.holdNote, r.holders.size() > 5 ? r.holders.subList(0, 5) : r.holders), Ui.lpm(-1, -2, 0, dp(10), 0, 0));
        }
        if (r.transport != null) {
            sp.addView(infoRow("transport", 0xFF3A4A3A, "ТРАНСПОРТ", r.transport, null), Ui.lpm(-1, -2, 0, dp(10), 0, 0));
        }
        // план по ходам — шагами
        if (r.plan != null) {
            sp.addView(divider(), Ui.lpm(-1, dp(1), 0, dp(10), 0, dp(8)));
            sp.addView(cap("ПЛАН НА ИГРУ"));
            String[] steps = r.plan.split("(?<=\\.)\\s+(?=[А-ЯA-Z])");
            int n = 0;
            for (String st : steps) {
                st = st.trim();
                if (st.isEmpty()) continue;
                n++;
                String lab = n == 1 ? "Старт" : "Далее", txt = st;
                int colon = st.indexOf(':');
                if (colon > 0 && colon < 12 && st.startsWith("Ход")) { lab = st.substring(0, colon).replace("Ходы ", "").replace("Ход ", ""); txt = st.substring(colon + 1).trim(); }
                if (txt.isEmpty()) continue;
                LinearLayout row = Ui.row(a);
                row.setGravity(Gravity.TOP);
                TextView num = Ui.text(a, lab, 13, Theme.namesBlack, 0xFFFFF1D8);
                num.setGravity(Gravity.CENTER);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setColor(theme.accent);
                g.setCornerRadius(dp(14));
                num.setBackground(g);
                num.setPadding(dp(6), dp(2), dp(6), dp(3));
                num.setMinWidth(dp(54));
                row.addView(num, Ui.lpm(-2, -2, 0, dp(1), dp(10), 0));
                TextView t = Ui.text(a, txt.substring(0, 1).toUpperCase() + txt.substring(1), 14, Theme.body, Ui.INK);
                t.setLineSpacing(dp(1), 1f);
                row.addView(t, Ui.weight(1));
                sp.addView(row, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
            }
        }
        if (r.missionNote != null) {
            TextView mn = Ui.text(a, "🎯  " + r.missionNote, 13, Theme.bodyBold, Ui.INK);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(0x26B08A20);
            g.setStroke(dp(1), 0x88B08A20);
            g.setCornerRadius(dp(6));
            mn.setBackground(g);
            mn.setPadding(dp(10), dp(7), dp(10), dp(8));
            sp.addView(mn, Ui.lpm(-1, -2, 0, dp(10), 0, 0));
        }
        return sp;
    }

    View profileCard(Smart.Result r) {
        LinearLayout pc = Ui.col(a);
        pc.setBackground(Ui.parchment());
        pc.setPadding(dp(14), dp(12), dp(14), dp(12));
        pc.addView(cap("ПРОФИЛЬ АРМИИ"));
        TextView lg = Ui.text(a, "Полоса — сила армии. Метка справа — насколько это нужно против выбранного врага.", 12, Theme.body, Ui.INK_DIM);
        pc.addView(lg, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        double maxHave = 1;
        for (double v : r.have) maxHave = Math.max(maxHave, v);
        for (int i = 0; i < 6; i++) {
            LinearLayout br = Ui.row(a);
            br.addView(Ui.text(a, Smart.CAP_RU[i], 13, Theme.bodyBold, Ui.INK), Ui.lp(dp(118), -2));
            Ui.Gauge gg = new Ui.Gauge(a, theme);
            gg.set(r.have[i], maxHave);
            br.addView(gg, Ui.weight(1));
            String imp = r.need[i] >= 1.8 ? "ВАЖНО" : r.need[i] >= 1.2 ? "НУЖНО" : null;
            if (imp != null) br.addView(pill(imp, imp.equals("ВАЖНО") ? 0xFFA02020 : 0xFFA07818), Ui.lpm(dp(62), -2, dp(8), 0, 0, 0));
            else { View sp = new View(a); br.addView(sp, Ui.lpm(dp(62), dp(1), dp(8), 0, 0, 0)); }
            pc.addView(br, Ui.lpm(-1, dp(26), 0, dp(2), 0, dp(2)));
        }
        return pc;
    }

    boolean whyOpen;

    View whyCard(final Smart.Result r) {
        final LinearLayout why = Ui.col(a);
        why.setBackground(Ui.parchment());
        why.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout hd = Ui.row(a);
        hd.addView(cap("ПОЧЕМУ ТАК  ·  " + r.reasons.size()), Ui.weight(1));
        hd.addView(Ui.text(a, whyOpen ? "свернуть ▴" : "подробнее ▾", 13, Theme.bodyBold, Ui.INK_DIM));
        why.addView(hd, Ui.lpm(-1, dp(28), 0, 0, 0, 0));
        hd.setClickable(true);
        Ui.press(hd);
        hd.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { whyOpen = !whyOpen; Sfx.play("page", 0.5f, 1.2f); rebuild(); } });
        if (whyOpen) for (String s : r.reasons) {
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.TOP);
            row.addView(Ui.text(a, "⚜", 13, Theme.body, Ui.INK_RED), Ui.lpm(-2, -2, 0, dp(1), dp(8), 0));
            TextView t = Ui.text(a, s, 13, Theme.body, Ui.INK);
            t.setLineSpacing(dp(1), 1f);
            row.addView(t, Ui.weight(1));
            why.addView(row, Ui.lpm(-1, -2, 0, dp(4), 0, dp(2)));
        }
        return why;
    }

    // ================================================================ «зачем этот юнит»
    void explainSheet(Smart.Pick k) {
        String tag = k.status == Smart.OWNED ? "в коллекции" : k.status == Smart.PROXY ? "прокси из «" + k.proxyOf + "»" : "новый юнит";
        LinearLayout body = a.openSheet(k.name, Engine.fmt(k.pts) + " оч. · " + Units.slotRu(k.slot) + " · " + tag, theme);
        LinearLayout sp = Ui.col(a);
        sp.setBackground(Ui.parchment());
        sp.setPadding(dp(14), dp(12), dp(14), dp(14));
        int[] cols = {0xFF8A3A1A, 0xFF2A5A8A, 0xFF6A5420, 0xFF3A4A3A, 0xFF5A3A6A, 0xFF4A4A4A};
        int i = 0;
        for (String[] x : k.explain) {
            if (i > 0) sp.addView(divider(), Ui.lpm(-1, dp(1), 0, dp(9), 0, dp(9)));
            sp.addView(infoRow(x[0], cols[i % cols.length], x[1].toUpperCase(), x[2], null));
            i++;
        }
        if (k.explain.isEmpty()) sp.addView(Ui.text(a, "Обоснование для этого юнита не собрано.", 14, Theme.body, Ui.INK));
        // профиль вклада
        if (k.cap != null && result != null) {
            sp.addView(divider(), Ui.lpm(-1, dp(1), 0, dp(10), 0, dp(8)));
            sp.addView(cap("ВКЛАД В АРМИЮ"));
            double[] tot = new double[6];
            for (Smart.Pick q : result.picks) if (q.cap != null) for (int j = 0; j < 6; j++) tot[j] += q.cap.get(j);
            for (int j = 0; j < 6; j++) {
                LinearLayout br = Ui.row(a);
                br.addView(Ui.text(a, Smart.CAP_RU[j], 12, Theme.bodyBold, Ui.INK), Ui.lp(dp(112), -2));
                Ui.Gauge g = new Ui.Gauge(a, theme);
                g.set(k.cap.get(j), Math.max(1e-9, tot[j]));
                br.addView(g, Ui.weight(1));
                br.addView(Ui.text(a, Math.round(100 * k.cap.get(j) / Math.max(1e-9, tot[j])) + "%", 12, Theme.bodyBold, Ui.INK_DIM), Ui.lpm(dp(40), -2, dp(6), 0, 0, 0));
                sp.addView(br, Ui.lpm(-1, dp(22), 0, dp(2), 0, 0));
            }
        }
        body.addView(sp);
    }

    // ================================================================ сравнение вариантов
    void compareVariants() {
        if (busy) return;
        if (p.myCat == null) { a.toast("Выберите фракцию армии"); pickMine(); return; }
        busy = true;
        result = null;
        variants = null;
        savePrefs();
        Sfx.play("servo");
        Sfx.play("hum", 0.6f, 1f);
        rebuild();
        p.minis = Minis.forSmart(a);
        final Smart.Params base = copyParams(p);
        new Thread(new Runnable() {
            public void run() {
                final Smart.Result[] out = new Smart.Result[VARIANTS.length];
                for (int i = 0; i < VARIANTS.length; i++) {
                    Smart.Params q = copyParams(base);
                    if (!VARIANTS[i][0].isEmpty()) { q.style = new HashSet<String>(); q.style.add(VARIANTS[i][0]); }
                    q.seed = base.seed + i * 7919;
                    out[i] = new Smart(Store.data).build(all, q);
                }
                a.h.post(new Runnable() {
                    public void run() {
                        busy = false;
                        variants = out;
                        variantIdx = 0;
                        result = out[0];
                        Sfx.play("seal");
                        Ui.haptic(Ui.DOUBLE);
                        rebuild();
                    }
                });
            }
        }, "smart3").start();
    }

    View compareCard() {
        LinearLayout cc = Ui.col(a);
        cc.setBackground(Ui.parchment());
        cc.setPadding(dp(10), dp(10), dp(10), dp(12));
        cc.addView(cap("СРАВНЕНИЕ ВАРИАНТОВ · нажмите, чтобы открыть"));
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        double[] mx = new double[6];
        for (Smart.Result v : variants) if (v != null) for (int j = 0; j < 6; j++) mx[j] = Math.max(mx[j], v.have[j]);
        for (int i = 0; i < variants.length; i++) {
            final int vi = i;
            Smart.Result v = variants[i];
            LinearLayout col = Ui.col(a);
            boolean on = i == variantIdx;
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(on ? 0x22000000 : 0x0A000000);
            g.setStroke(dp(on ? 2 : 1), on ? theme.accent : 0x558A6A3A);
            g.setCornerRadius(dp(8));
            col.setBackground(g);
            col.setPadding(dp(8), dp(8), dp(8), dp(8));
            col.addView(Ui.text(a, VARIANTS[i][1], 14, Theme.names, Ui.INK));
            if (v == null || v.error != null || v.roster == null) {
                col.addView(Ui.text(a, v == null ? "—" : String.valueOf(v.error), 12, Theme.body, Ui.INK_RED));
            } else {
                Engine e = new Engine(Store.data, v.roster);
                col.addView(Ui.text(a, v.archetype == null ? "" : v.archetype, 12, Theme.bodyBold, Ui.INK_RED));
                col.addView(Ui.text(a, Engine.fmt(e.rosterCost(Store.data.pointsId)) + " оч.", 16, Theme.namesBlack, Ui.INK));
                int own = 0, prox = 0, nw = 0;
                for (Smart.Pick k : v.picks) if (k.status == Smart.OWNED) own++; else if (k.status == Smart.PROXY) prox++; else nw++;
                col.addView(Ui.text(a, "✓ " + own + "  ≈ " + prox + "  ✦ " + nw, 12, Theme.bodyBold, Ui.INK_DIM));
                for (int j = 0; j < 6; j++) {
                    LinearLayout br = Ui.row(a);
                    br.addView(Ui.text(a, SHORT_CAP[j], 10, Theme.bodyBold, Ui.INK), Ui.lp(dp(30), -2));
                    Ui.Gauge gg = new Ui.Gauge(a, theme);
                    gg.set(v.have[j], Math.max(1e-9, mx[j]));
                    br.addView(gg, Ui.weight(1));
                    col.addView(br, Ui.lpm(-1, dp(14), 0, dp(2), 0, 0));
                }
                if (v.errors > 0) col.addView(Ui.text(a, "ошибок: " + v.errors, 11, Theme.bodyBold, Ui.INK_RED));
            }
            col.setClickable(true);
            Ui.press(col);
            col.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) { if (variants[vi] == null) return; variantIdx = vi; result = variants[vi]; Sfx.play("page", 0.5f, 1.1f); rebuild(); }
            });
            LinearLayout.LayoutParams lp = Ui.lpm(0, -2, i == 0 ? 0 : dp(4), dp(6), 0, 0);
            lp.weight = 1;
            row.addView(col, lp);
        }
        cc.addView(row);
        TextView lg = Ui.text(a, "ПТ — против техники, Пх — против пехоты, Эл — против элиты, Жв — живучесть, Рк — рукопашная, Ск — скорость", 11, Theme.body, Ui.INK_DIM);
        cc.addView(lg, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        return cc;
    }

    static final String[] SHORT_CAP = {"ПТ", "Пх", "Эл", "Жв", "Рк", "Ск"};
}
