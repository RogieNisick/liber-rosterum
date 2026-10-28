package com.medoedy.liber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Chars;
import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.medoedy.liber.Ui.dp;

/**
 * Вызов (Challenge): выбор бойцов, тайный выбор гамбитов, бросок Фокуса, удары и Слава.
 * Порядок шагов — как в правилах 3-й редакции: Face-Off → Focus → Strike → Glory.
 */
final class ChallengeScreen extends Screen {
    final Battle b;
    final BattleScreen parent;
    final Random rnd = new Random();

    // состояние вызова
    final String[] fighter = {null, null};
    final String[] stats = {"", ""};
    final String[] entryId = {null, null};
    final int[] wounds = {3, 3}, woundsMax = {3, 3};
    final int[] wsv = {-1, -1}, iv = {-1, -1}, av = {-1, -1};
    final boolean[] command = {true, true};
    final Gambits.G[] gambit = {null, null};
    final int[] focus = {0, 0}, inflicted = {0, 0}, taunts = {0, 0};
    final int[] ci = {0, 0}, outside = {0, 0}, edge = {0, 0}, other = {0, 0};
    final int[] guardNext = {0, 0}, guardMiss = {0, 0}, flurry = {0, 0};
    final String[] focusText = {"", ""};
    String forbidden; // гамбит, запрещённый через Feint and Riposte
    boolean secret = true, revealed;
    /** Ручной ввод кубов фокуса: выпавшие значения (0 — не введено) и признак «фокус уже определён». */
    final int[] man1 = {0, 0}, man2 = {0, 0};
    final boolean[] focusSet = {false, false};
    int step = 0; // 0 бойцы, 1 гамбиты, 2 фокус, 3 удары, 4 слава
    int round = 1;
    int challenger = 0;
    int advantage = -1; // Challenge Advantage по итогам Фокуса текущего раунда
    int advNext = -1;   // кто выбирает первым в следующем Face-Off

    static final String[] STEPS = {"Бойцы", "Гамбиты", "Фокус", "Удары", "Слава"};

    ChallengeScreen(MainActivity a, BattleScreen parent, Battle b) {
        super(a);
        this.parent = parent;
        this.b = b;
        theme = parent.theme;
    }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("⚔ Вызов", "Ход " + b.turn + " · раунд " + round, theme, true));

        LinearLayout steps = Ui.row(a);
        for (int i = 0; i < STEPS.length; i++) {
            boolean on = i == step, done = i < step;
            TextView t = Ui.text(a, (i + 1) + " " + STEPS[i], 12, Theme.bodyBold, on ? Theme.onColor(theme.accent) : done ? Ui.GOLD : Ui.BONE_DIM);
            t.setGravity(Gravity.CENTER);
            t.setBackground(on ? Ui.heraldic(theme, dp(4)) : Ui.steelButton(theme, dp(4)));
            t.setPadding(0, dp(6), 0, dp(7));
            LinearLayout.LayoutParams lp = Ui.lpm(0, -2, dp(1), 0, dp(1), 0);
            lp.weight = 1;
            steps.addView(t, lp);
        }
        c.addView(steps, Ui.lpm(-1, -2, 0, dp(2), 0, dp(8)));

        LinearLayout duo = Ui.row(a);
        duo.setGravity(Gravity.TOP);
        for (int p = 0; p < 2; p++) {
            LinearLayout.LayoutParams lp = Ui.lpm(0, -2, p == 0 ? 0 : dp(4), 0, p == 0 ? dp(4) : 0, 0);
            lp.weight = 1;
            duo.addView(card(p), lp);
        }
        c.addView(duo);

        LinearLayout act = Ui.col(a);
        act.setPadding(0, dp(10), 0, 0);
        switch (step) {
            case 0: stepFighters(act); break;
            case 1: stepGambits(act); break;
            case 2: stepFocus(act); break;
            case 3: stepStrike(act); break;
            default: stepGlory(act); break;
        }
        c.addView(act);
        f.addView(a.scroll(c));
        return f;
    }

    String factionOf(int p) { return b.factions[p] == null ? "" : b.factions[p]; }
    boolean has(int p, String g) { return gambit[p] != null && g.equals(gambit[p].name); }
    int firstPicker() { return round == 1 || advNext < 0 ? challenger : advNext; }

    View card(final int p) {
        final Theme t = Theme.forName(factionOf(p));
        LinearLayout card = Ui.col(a);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.steel(t));
        card.setPadding(dp(10), dp(12), dp(10), dp(12));
        Ui.Hex hx = new Ui.Hex(a, t, "sword");
        if (entryId[p] != null) hx.photo = a.unitArt(entryId[p], factionOf(p));
        hx.label = hx.photo == null && !t.numeral.isEmpty() ? t.numeral : null;
        if (hx.label != null) hx.symbol = null;
        card.addView(hx, Ui.lp(dp(72), dp(72)));
        TextView pn = Ui.text(a, b.names[p] + (challenger == p ? " · вызвал" : ""), 12, Theme.bodyBold, Ui.GOLD);
        pn.setGravity(Gravity.CENTER);
        card.addView(pn, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        TextView fn = Ui.text(a, fighter[p] == null ? "Боец не выбран" : fighter[p], 16, Theme.names, Ui.BONE);
        fn.setGravity(Gravity.CENTER);
        card.addView(fn);
        if (!stats[p].isEmpty()) {
            TextView st = Ui.text(a, stats[p], 11, Theme.body, Ui.BONE_DIM);
            st.setGravity(Gravity.CENTER);
            card.addView(st);
        }
        LinearLayout wr = Ui.row(a);
        wr.setGravity(Gravity.CENTER);
        TextView minus = Ui.button(a, "−", Ui.steelButton(t, dp(5)), Ui.GOLD, 18, new View.OnClickListener() {
            public void onClick(View v) { if (wounds[p] > 0) { wounds[p]--; inflicted[1 - p]++; Sfx.play("w_blade", 0.7f, 1.1f); Ui.haptic(Ui.HEAVY); shake(); rebuild(); } }
        });
        minus.setPadding(0, 0, 0, dp(2));
        wr.addView(minus, Ui.lp(dp(36), dp(36)));
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < woundsMax[p]; i++) hearts.append(i < wounds[p] ? "♥" : "♡");
        TextView ht = Ui.text(a, hearts.toString(), woundsMax[p] > 6 ? 12 : 17, Theme.bodyBold, wounds[p] == 0 ? Ui.RED_BAD : 0xFFE05A4A);
        ht.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = Ui.lpm(0, -2, dp(4), 0, dp(4), 0);
        hl.weight = 1;
        wr.addView(ht, hl);
        TextView plus = Ui.button(a, "+", Ui.steelButton(t, dp(5)), Ui.GOLD, 18, new View.OnClickListener() {
            public void onClick(View v) { if (wounds[p] < woundsMax[p]) { wounds[p]++; if (inflicted[1 - p] > 0) inflicted[1 - p]--; Sfx.play("tick"); rebuild(); } }
        });
        plus.setPadding(0, 0, 0, dp(2));
        wr.addView(plus, Ui.lp(dp(36), dp(36)));
        card.addView(wr, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        String gs;
        if (gambit[p] == null) gs = step >= 1 ? "Гамбит не выбран" : "";
        else if (secret && !revealed) gs = "🔒 Гамбит выбран";
        else gs = "⚜ " + gambit[p].name;
        if (!gs.isEmpty()) {
            TextView g = Ui.text(a, gs, 13, Theme.bodyBold, gambit[p] == null ? Ui.BONE_DIM : Ui.GOLD);
            g.setGravity(Gravity.CENTER);
            g.setPadding(0, dp(6), 0, 0);
            card.addView(g);
        }
        if (step >= 2 && focus[p] > 0) {
            TextView fo = Ui.text(a, "Фокус: " + focus[p] + (advantage == p ? "  ★ Преимущество" : ""), 13, Theme.bodyBold, advantage == p ? Ui.GREEN_OK : Ui.BONE);
            fo.setGravity(Gravity.CENTER);
            card.addView(fo);
        }
        if (step >= 3 || inflicted[p] > 0) {
            TextView cr = Ui.text(a, "Нанёс ран: " + inflicted[p], 13, Theme.bodyBold, Ui.GOLD);
            cr.setGravity(Gravity.CENTER);
            card.addView(cr);
        }
        return card;
    }

    TextView note(LinearLayout act, String s) {
        TextView t = Ui.text(a, s, 13, Theme.body, Ui.BONE_DIM);
        t.setPadding(0, dp(2), 0, dp(4));
        act.addView(t);
        return t;
    }

    /** Строка «подпись  − N +». */
    void stepper(LinearLayout act, Theme t, String label, final int[] arr, final int p, boolean signed, final int min) {
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(Ui.text(a, label, 13, Theme.body, Ui.BONE), Ui.weight(1));
        r.addView(Ui.button(a, "−", Ui.steelButton(t, dp(5)), Ui.GOLD, 17, new View.OnClickListener() { public void onClick(View v) { if (arr[p] > min) arr[p]--; Sfx.play("tick"); rebuild(); } }), Ui.lp(dp(34), dp(34)));
        TextView mv = Ui.text(a, (signed && arr[p] > 0 ? "+" : "") + arr[p], 16, Theme.namesBlack, Ui.BONE);
        mv.setGravity(Gravity.CENTER);
        r.addView(mv, Ui.lp(dp(40), -2));
        r.addView(Ui.button(a, "+", Ui.steelButton(t, dp(5)), Ui.GOLD, 17, new View.OnClickListener() { public void onClick(View v) { arr[p]++; Sfx.play("tick"); rebuild(); } }), Ui.lp(dp(34), dp(34)));
        act.addView(r, Ui.lpm(-1, -2, 0, dp(2), 0, dp(2)));
    }

    LinearLayout panel(LinearLayout act, int p, String title) {
        Theme t = Theme.forName(factionOf(p));
        LinearLayout box = Ui.col(a);
        box.setBackground(Ui.steelFlat(t, dp(6)));
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.addView(Ui.text(a, title, 14, Theme.bodyBold, Ui.GOLD));
        act.addView(box, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        return box;
    }

    // ================================================================ шаг 1: бойцы
    void stepFighters(LinearLayout act) {
        act.addView(a.label("КТО БРОСИЛ ВЫЗОВ", Ui.GOLD));
        Ui.Flow ch = new Ui.Flow(a);
        ch.gap = dp(6);
        for (int p = 0; p < 2; p++) {
            final int pp = p;
            ch.addView(chipToggle(b.names[p], challenger == p, new Runnable() { public void run() { challenger = pp; rebuild(); } }));
        }
        act.addView(ch, Ui.lpm(-1, -2, 0, 0, 0, dp(4)));
        note(act, "Вызывающий выбирает гамбит первым в первом раунде.");
        for (int p = 0; p < 2; p++) {
            final int pp = p;
            act.addView(Ui.button(a, "Выбрать бойца: " + b.names[p], Ui.steelButton(Theme.forName(factionOf(p)), dp(6)), Ui.BONE, 15, new View.OnClickListener() {
                public void onClick(View v) { pickFighter(pp); }
            }), Ui.lpm(-1, dp(46), 0, dp(3), 0, dp(3)));
        }
        next(act, "Вызов принят → гамбиты", fighter[0] != null && fighter[1] != null, new Runnable() {
            public void run() { step = 1; Sfx.play("gong", 0.7f, 1f); Voice.say(Voice.line("Вызов принят.", "Challenge accepted.")); rebuild(); }
        });
    }

    void setFighter(int p, String name, String st, String id, int w, int ws, int in, int at, boolean cmd) {
        fighter[p] = name; stats[p] = st == null ? "" : st; entryId[p] = id;
        wounds[p] = woundsMax[p] = Math.max(1, w);
        wsv[p] = ws; iv[p] = in; av[p] = at; command[p] = cmd;
        ci[p] = Math.max(0, in);
        inflicted[0] = inflicted[1] = 0;
    }

    static final Map<String, Chars> CHARS = new java.util.HashMap<String, Chars>();

    /** Бойцы: из ваших ростеров этой фракции, все персонажи фракции из каталога, или своё имя. */
    void pickFighter(final int p) {
        final Theme th = Theme.forName(factionOf(p));
        LinearLayout body = a.openSheet("Боец: " + b.names[p], factionOf(p).isEmpty() ? null : factionOf(p), th);
        final EditText name = a.input("", "Своё имя бойца (например, Praetor)", false);
        body.addView(name, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        body.addView(Ui.button(a, "Взять это имя", Ui.brass(dp(6)), 0xFF1A140C, 15, new View.OnClickListener() {
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                setFighter(p, n, "", null, 3, -1, -1, -1, true);
                a.closeSheet(); rebuild();
            }
        }), Ui.lpm(-1, dp(44), 0, 0, 0, dp(8)));
        Data d = Store.data;
        if (d == null) return;
        // 1) из ростеров
        boolean head = false;
        for (Roster r : a.store.rosters()) {
            Data.Cat cat = d.cats.get(r.catalogueId);
            if (cat == null || (!factionOf(p).isEmpty() && !cat.name.equals(factionOf(p)))) continue;
            final Engine e = new Engine(d, r);
            List<Force> fs = new ArrayList<Force>();
            r.allForces(fs);
            for (Force f : fs) for (final Sel s : f.sels) {
                String slot = d.categoryName(e.effSel(s).primary);
                final String nm = e.effSel(s).name;
                if (!(slot.contains("Command") || slot.contains("Warlord") || nm.contains("Primarch"))) continue;
                final String[] prof = profile(e, s);
                if (prof[0].isEmpty()) continue;
                if (!head) { body.addView(a.label("ИЗ ВАШИХ РОСТЕРОВ", Ui.GOLD)); head = true; }
                a.sheetItem(body, "crown", nm, r.name + " · " + prof[0], th, false, new View.OnClickListener() {
                    public void onClick(View v) {
                        setFighter(p, nm, prof[0], s.entryId, Chars.num(prof[1]), Chars.num(prof[2]), Chars.num(prof[3]), Chars.num(prof[4]), prof[5].contains("Command") || prof[5].contains("Paragon"));
                        a.closeSheet(); Sfx.play("seal"); rebuild();
                    }
                });
            }
        }
        // 2) все персонажи фракции из каталога
        String catId = null;
        for (Data.Cat cat : d.cats.values()) if (cat.name.equals(factionOf(p))) { catId = cat.id; break; }
        if (catId == null) {
            body.addView(Ui.text(a, "Фракция игрока не задана в бою — список персонажей недоступен. Впишите имя бойца выше.", 13, Theme.body, Ui.BONE_DIM));
            return;
        }
        Chars cs = CHARS.get(catId);
        if (cs == null) { cs = new Chars(d, catId); CHARS.put(catId, cs); }
        String slot = null;
        for (final Chars.C c : cs.list) {
            if (!c.slot.equals(slot)) { slot = c.slot; body.addView(a.label(("ВСЕ ПЕРСОНАЖИ ФРАКЦИИ — " + slot).toUpperCase(), Ui.GOLD_DIM)); }
            a.sheetItem(body, c.slot.equals("Warlord") ? "crown" : "sword", c.name, c.stats + (c.type.isEmpty() ? "" : " · " + c.type), th, false, new View.OnClickListener() {
                public void onClick(View v) {
                    setFighter(p, c.name, c.stats, c.entryId, c.w, c.ws, c.i, c.a, c.command);
                    a.closeSheet(); Sfx.play("seal"); rebuild();
                }
            });
        }
        if (cs.list.isEmpty() && !head) body.addView(Ui.text(a, "В каталоге этой фракции нет персонажей — впишите имя бойца выше.", 13, Theme.body, Ui.BONE_DIM));
    }

    /** Характеристики модели из ростера: {строка, W, WS, I, A, Type}. */
    String[] profile(Engine e, Sel s) {
        List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>(e.profilesOf(e.entryOf(s), e.linkOf(s)));
        for (Sel ch : s.children) ps.addAll(e.profilesOf(e.entryOf(ch), e.linkOf(ch)));
        for (Map<String, Object> p : ps) {
            String ws = null, st = null, t = null, w = null, in = null, at = null, ty = "";
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String n = Data.str(ch, "name"), v = Data.str(ch, "$text");
                if (n == null || v == null) continue;
                if (n.equals("WS")) ws = v; else if (n.equals("S")) st = v; else if (n.equals("T")) t = v;
                else if (n.equals("W")) w = v; else if (n.equals("I")) in = v; else if (n.equals("A")) at = v;
                else if (n.equals("Type")) ty = v;
            }
            if (ws != null && w != null) return new String[]{"WS" + ws + " S" + st + " T" + t + " W" + w + " I" + in + " A" + at, w, ws, in, at, ty};
        }
        return new String[]{"", "3", "", "", "", ""};
    }

    // ================================================================ шаг 2: гамбиты (Face-Off)
    void stepGambits(LinearLayout act) {
        final int first = firstPicker(), second = 1 - first;
        String why = round == 1 ? "бросил вызов" : advNext >= 0 ? "имеет Challenge Advantage" : "бросил вызов";
        TextView hd = Ui.text(a, "Первым выбирает " + b.names[first] + " (" + why + "), затем " + b.names[second] + ".", 14, Theme.bodyBold, Ui.BONE);
        act.addView(hd);
        note(act, "Один и тот же гамбит не могут выбрать оба. Feint and Riposte доступен только тому, кто выбирает первым." + (forbidden != null ? "\n⛔ Запрещён через Feint and Riposte: " + forbidden : ""));
        Ui.Flow opts = new Ui.Flow(a);
        opts.gap = dp(6);
        opts.addView(chipToggle(secret ? "🔒 Тайный выбор (дом. правило)" : "Открытый выбор (по правилам)", secret, new Runnable() { public void run() { secret = !secret; rebuild(); } }));
        act.addView(opts, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        int[] order = {first, second};
        for (int k = 0; k < 2; k++) {
            final int pp = order[k];
            final boolean enabled = k == 0 || gambit[first] != null;
            String label = (k + 1) + ". " + (gambit[pp] == null ? "Выбрать гамбит: " + b.names[pp] : (secret && !revealed ? "Гамбит " + b.names[pp] + " выбран — сменить" : b.names[pp] + ": " + gambit[pp].name));
            TextView bt = Ui.button(a, label, Ui.steelButton(Theme.forName(factionOf(pp)), dp(6)), enabled ? Ui.BONE : Ui.BONE_DIM, 15, new View.OnClickListener() {
                public void onClick(View v) {
                    if (!enabled) { a.toast("Сначала выбирает " + b.names[first]); return; }
                    if (secret) a.toast("Передайте телефон: " + b.names[pp] + ". Соперник, не подглядывай!");
                    pickGambit(pp);
                }
            });
            act.addView(bt, Ui.lpm(-1, dp(46), 0, dp(3), 0, dp(3)));
        }
        next(act, "⚔  Сойтись!", gambit[0] != null && gambit[1] != null, new Runnable() {
            public void run() {
                if (gambit[first].name.equals(gambit[second].name) || gambit[second].name.equals(forbidden) || has(second, "Feint and Riposte")) {
                    a.toast("Гамбит " + b.names[second] + " недопустим (" + gambit[second].name + "): приоритет у " + b.names[first] + ". Выберите другой.");
                    gambit[second] = null;
                    revealed = true;
                    rebuild();
                    return;
                }
                revealed = true;
                for (int p = 0; p < 2; p++) if (has(p, "Taunt and Bait")) taunts[p]++;
                clash();
            }
        });
    }

    /** Почему игрок p не может взять гамбит g (или null). */
    String blocked(int p, Gambits.G g) {
        int first = firstPicker();
        if (p != first) {
            if (g.name.equals("Feint and Riposte")) return "только для выбирающего первым";
            if (gambit[first] != null && gambit[first].name.equals(g.name) && !(secret && !revealed)) return "уже выбран соперником";
            if (g.name.equals(forbidden)) return "запрещён через Feint and Riposte";
        }
        return null;
    }

    TextView chipToggle(String label, boolean on, final Runnable r) {
        TextView t = Ui.text(a, label, 13, Theme.bodyBold, on ? Theme.onColor(theme.accent) : Ui.BONE);
        t.setBackground(on ? Ui.heraldic(theme, dp(5)) : Ui.steelButton(theme, dp(5)));
        t.setPadding(dp(12), dp(7), dp(12), dp(8));
        t.setClickable(true);
        Ui.press(t);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("tick"); r.run(); } });
        return t;
    }

    void pickGambit(final int p) {
        final Theme t = Theme.forName(factionOf(p));
        LinearLayout body = a.openSheet("Гамбит: " + b.names[p], fighter[p], t);
        List<Gambits.G> fac = new ArrayList<Gambits.G>(), core = new ArrayList<Gambits.G>(), other = new ArrayList<Gambits.G>();
        for (Gambits.G g : Gambits.all()) {
            if (g.core) core.add(g);
            else if (Gambits.fits(g, factionOf(p))) fac.add(g);
            else other.add(g);
        }
        TextView rules = Ui.text(a, Gambits.RULES, 12, Theme.body, Ui.BONE_DIM);
        rules.setPadding(0, 0, 0, dp(6));
        body.addView(rules);
        if (!fac.isEmpty()) {
            body.addView(a.label("ОСОБЫЕ — " + factionOf(p).replaceAll("^[IVXL]+ - ", "").toUpperCase(), Ui.GOLD));
            for (Gambits.G g : fac) gambitItem(body, p, g, t);
        }
        body.addView(a.label("БАЗОВЫЕ", Ui.GOLD));
        for (Gambits.G g : core) gambitItem(body, p, g, t);
        List<Gambits.G> mine = Gambits.custom(a);
        if (!mine.isEmpty()) {
            body.addView(a.label("СВОИ", Ui.GOLD));
            for (Gambits.G g : mine) gambitItem(body, p, g, t);
        }
        a.sheetItem(body, "shield", "＋ Добавить свой гамбит", "Если нужного нет в списке", t, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); addCustom(p); }
        });
        body.addView(a.label("ДРУГИХ ФРАКЦИЙ И ПЕРСОНАЖЕЙ", Ui.GOLD_DIM));
        for (Gambits.G g : other) gambitItem(body, p, g, t);
    }

    void gambitItem(LinearLayout body, final int p, final Gambits.G g, final Theme t) {
        String sub = g.summary == null ? "" : g.summary;
        if (g.faction != null && !Gambits.fits(g, factionOf(p))) sub = g.faction.replaceAll("^[IVXL]+ - ", "") + " · " + sub;
        String bl = blocked(p, g);
        if (bl != null) sub = "⛔ " + bl + " · " + sub;
        a.sheetItem(body, g.core ? "sword" : g.custom ? "shield" : "crown", g.name, sub.length() > 160 ? sub.substring(0, 160) + "…" : sub, t, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); gambitDetail(p, g, t); }
        });
    }

    void gambitDetail(final int p, final Gambits.G g, final Theme t) {
        LinearLayout body = a.openSheet(g.name, g.faction == null ? (g.core ? "Базовый гамбит" : "Свой гамбит") : g.faction, t);
        LinearLayout sh = Ui.col(a);
        sh.setBackground(Ui.parchment());
        sh.setPadding(dp(14), dp(10), dp(14), dp(12));
        TextView sum = Ui.text(a, g.summary == null ? "" : g.summary, 15, Theme.bodyBold, Ui.INK);
        if (!g.core) tr(sum, g.summary);
        sh.addView(sum);
        if (g.description != null && !g.description.equals(g.summary)) {
            TextView d = Ui.text(a, g.description, 14, Theme.body, Ui.INK);
            d.setPadding(0, dp(8), 0, 0);
            if (!g.core) tr(d, g.description);
            sh.addView(d);
        }
        body.addView(sh);
        if (!g.custom) body.addView(LibraryScreen.bookButton(a, g.name, g.faction != null ? g.faction : factionOf(p), t, "📖  Открыть в книге"), Ui.lpm(-1, dp(44), 0, dp(8), 0, 0));
        final String bl = blocked(p, g);
        if (bl != null) {
            TextView w = Ui.text(a, "⛔ Сейчас нельзя: " + bl + ".", 14, Theme.bodyBold, Ui.RED_BAD);
            w.setPadding(0, dp(8), 0, 0);
            body.addView(w);
        }
        body.addView(Ui.button(a, bl == null ? "Выбрать гамбит" : "Назад к списку", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) {
                if (bl != null) { a.closeSheetNow(); pickGambit(p); return; }
                gambit[p] = g;
                int first = firstPicker();
                if (p == first) {
                    forbidden = null;
                    Gambits.G o = gambit[1 - p];
                    if (o != null && (o.name.equals(g.name) || o.name.equals("Feint and Riposte"))) gambit[1 - p] = null;
                }
                Sfx.play(secret ? "seal" : "w_blade");
                Ui.haptic(Ui.DOUBLE);
                if (p == first && g.name.equals("Feint and Riposte")) { a.closeSheetNow(); pickForbidden(p); return; }
                a.closeSheet();
                rebuild();
            }
        }), Ui.lpm(-1, dp(50), 0, dp(10), 0, 0));
    }

    /** Feint and Riposte: назвать гамбит, который сопернику брать нельзя. */
    void pickForbidden(final int p) {
        final int o = 1 - p;
        Theme t = Theme.forName(factionOf(p));
        LinearLayout body = a.openSheet("Feint and Riposte", "Какой гамбит запретить: " + b.names[o] + "?", t);
        List<Gambits.G> list = new ArrayList<Gambits.G>();
        for (Gambits.G g : Gambits.all()) if ((g.core || Gambits.fits(g, factionOf(o))) && !g.name.equals("Feint and Riposte")) list.add(g);
        for (final Gambits.G g : list) {
            a.sheetItem(body, g.core ? "sword" : "crown", g.name, g.summary, t, false, new View.OnClickListener() {
                public void onClick(View v) {
                    forbidden = g.name;
                    if (gambit[o] != null && gambit[o].name.equals(g.name)) gambit[o] = null;
                    a.closeSheet(); Sfx.play("seal"); rebuild();
                }
            });
        }
    }

    static void tr(final TextView tv, final String en) {
        TrText.bind(tv, en);
    }

    void addCustom(final int p) {
        LinearLayout body = a.openSheet("Свой гамбит", theme);
        final EditText n = a.input("", "Название", false);
        body.addView(n, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        final EditText tx = a.input("", "Что делает (кратко)", false);
        tx.setSingleLine(false);
        tx.setMinLines(2);
        body.addView(tx, Ui.lpm(-1, -2, 0, 0, 0, dp(8)));
        body.addView(Ui.button(a, "Сохранить и выбрать", Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
            public void onClick(View v) {
                String nm = n.getText().toString().trim();
                if (nm.isEmpty()) return;
                Gambits.addCustom(a, nm, tx.getText().toString().trim());
                Gambits.G g = new Gambits.G();
                g.name = nm;
                g.summary = tx.getText().toString().trim();
                g.description = g.summary;
                g.custom = true;
                gambit[p] = g;
                a.closeSheet();
                rebuild();
            }
        }), Ui.lp(-1, dp(48)));
    }

    // ================================================================ столкновение
    void clash() {
        Clash cl = new Clash(a, fighter, factionOf(0), factionOf(1), entryId, gambit);
        a.overlay.addView(cl, new FrameLayout.LayoutParams(-1, -1));
        cl.start(new Runnable() { public void run() { step = 2; rebuild(); } });
    }

    void shake() {
        final View v = view;
        if (v == null) return;
        v.animate().translationX(dp(6)).setDuration(40).withEndAction(new Runnable() {
            public void run() { v.animate().translationX(-dp(5)).setDuration(50).withEndAction(new Runnable() {
                public void run() { v.animate().translationX(0).setDuration(60).start(); } }).start(); }
        }).start();
    }

    // ================================================================ шаг 3: фокус
    /** 1 — обычный куб, 2 — два куба, берётся больший, -2 — два куба, берётся меньший. */
    int diceMode(int p) {
        if (has(p, "Seize the Initiative")) return 2;
        if (has(p, "Grandstand") || has(p, "Finishing Blow")) return -2;
        return 1;
    }

    int focusBonus(int p) {
        int lost = woundsMax[p] - wounds[p];
        return ci[p] + (has(p, "Grandstand") ? 0 : outside[p]) + edge[p] + other[p] - lost + guardNext[p];
    }

    void stepFocus(LinearLayout act) {
        note(act, "Фокус: D6 + Combat Initiative (I модели + IM оружия) + Outside Support + Duellist's Edge, −1 за каждую потерянную рану. Heavy −1, Light +1 — в «прочее». Больший результат получает Challenge Advantage: бьёт первым и получает +1 атаку. Ничья — переброс.");
        for (int p = 0; p < 2; p++) {
            Theme t = Theme.forName(factionOf(p));
            LinearLayout box = panel(act, p, b.names[p] + " · " + (fighter[p] == null ? "" : fighter[p]));
            stepper(box, t, "Combat Initiative (I" + (iv[p] >= 0 ? "=" + iv[p] : "") + " + IM)", ci, p, false, 0);
            stepper(box, t, has(p, "Grandstand") ? "Outside Support → в атаки (Grandstand)" : "Outside Support (+1 за 5 своих моделей в бою)", outside, p, true, 0);
            stepper(box, t, "Duellist's Edge", edge, p, true, 0);
            stepper(box, t, "Прочее (Heavy −1, Light +1…)", other, p, true, -9);
            StringBuilder auto = new StringBuilder();
            int lost = woundsMax[p] - wounds[p];
            if (lost > 0) auto.append("Потеряно ран: −").append(lost).append("\n");
            if (guardNext[p] > 0) auto.append("Guard Up (промахи соперника): +").append(guardNext[p]).append("\n");
            int dm = diceMode(p);
            auto.append(dm == 2 ? "Кубы: 2D6, берётся БОЛЬШИЙ (" + gambit[p].name + ")" : dm == -2 ? "Кубы: 2D6, берётся МЕНЬШИЙ (" + gambit[p].name + ")" : "Куб: D6");
            int bonus = focusBonus(p);
            auto.append("  ·  итого модификатор ").append(bonus >= 0 ? "+" : "").append(bonus);
            TextView at = Ui.text(a, auto.toString(), 12, Theme.bodyBold, Ui.BONE_DIM);
            at.setPadding(0, dp(4), 0, 0);
            box.addView(at);
            manualRows(box, p, dm, t);
            if (!focusText[p].isEmpty()) {
                TextView ft = Ui.text(a, focusText[p], 14, Theme.bodyBold, advantage == p ? Ui.GREEN_OK : Ui.BONE);
                ft.setPadding(0, dp(4), 0, 0);
                box.addView(ft);
            }
        }
        act.addView(Ui.button(a, "🎲  Бросить Фокус", Ui.brass(dp(8)), 0xFF1A140C, 18, new View.OnClickListener() {
            public void onClick(View v) {
                Sfx.play("dice");
                Ui.pulses(new int[]{0, 90, 180}, new int[]{Ui.TICK, Ui.TICK, Ui.HEAVY});
                // при ничьей перебрасываем обоих; иначе бросаем только тех, чей фокус ещё не определён (введён вручную)
                boolean reroll = focusSet[0] && focusSet[1] && focus[0] == focus[1];
                for (int p = 0; p < 2; p++) {
                    if (focusSet[p] && !reroll) continue;
                    int d1 = rnd.nextInt(6) + 1, d2 = rnd.nextInt(6) + 1, dm = diceMode(p);
                    int keep = dm == 2 ? Math.max(d1, d2) : dm == -2 ? Math.min(d1, d2) : d1;
                    int bonus = focusBonus(p);
                    focus[p] = keep + bonus;
                    focusText[p] = "🎲 " + (dm == 1 ? String.valueOf(d1) : d1 + " и " + d2 + " → " + keep) + " " + (bonus >= 0 ? "+ " : "− ") + Math.abs(bonus) + " = " + focus[p];
                    focusSet[p] = true;
                    man1[p] = man2[p] = 0;
                }
                settleFocus(true);
                rebuild();
            }
        }), Ui.lpm(-1, dp(52), 0, dp(10), 0, dp(4)));
        next(act, "К ударам →", advantage >= 0, new Runnable() {
            public void run() {
                guardNext[0] = guardNext[1] = 0;
                flurry[0] = flurry[1] = 0;
                step = 3; rebuild();
            }
        });
    }

    // ================================================================ шаг 4: удары
    /** Ряды кнопок 1–6 для ручного ввода выпавших кубов (без броска). */
    void manualRows(LinearLayout box, final int p, final int dm, Theme t) {
        TextView hd = Ui.text(a, "Или впишите выпавшие кубы:", 12, Theme.bodyBold, Ui.BONE_DIM);
        hd.setPadding(0, dp(6), 0, 0);
        box.addView(hd);
        int dice = dm == 1 ? 1 : 2;
        for (int d = 0; d < dice; d++) {
            final int die = d;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int cur = d == 0 ? man1[p] : man2[p];
            for (int v = 1; v <= 6; v++) {
                final int val = v;
                boolean sel = cur == v;
                View btn = Ui.button(a, (dice == 2 ? (d + 1) + ": " : "") + v, sel ? Ui.brass(dp(6)) : Ui.steelButton(t, dp(6)),
                        sel ? 0xFF1A140C : Ui.BONE, 15, new View.OnClickListener() {
                    public void onClick(View vv) { enterDie(p, die, val); }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                lp.setMargins(dp(2), dp(4), dp(2), 0);
                row.addView(btn, lp);
            }
            box.addView(row);
        }
    }

    /** Записать выпавший куб вручную; как только кубы введены — фокус определён. */
    void enterDie(int p, int die, int val) {
        if (die == 0) man1[p] = val; else man2[p] = val;
        int dm = diceMode(p);
        if (man1[p] == 0 || (dm != 1 && man2[p] == 0)) { rebuild(); return; }
        int keep = dm == 2 ? Math.max(man1[p], man2[p]) : dm == -2 ? Math.min(man1[p], man2[p]) : man1[p];
        int bonus = focusBonus(p);
        focus[p] = keep + bonus;
        focusText[p] = "✎ " + (dm == 1 ? String.valueOf(man1[p]) : man1[p] + " и " + man2[p] + " → " + keep) + " " + (bonus >= 0 ? "+ " : "− ") + Math.abs(bonus) + " = " + focus[p];
        focusSet[p] = true;
        settleFocus(false);
        rebuild();
    }

    /** Итог фокуса: если оба определены — выбрать Преимущество или перебросить при ничьей. */
    void settleFocus(boolean rolled) {
        if (!focusSet[0] || !focusSet[1]) { advantage = -1; return; }
        if (focus[0] == focus[1]) { advantage = -1; a.toast("Ничья: " + focus[0] + " — перебросьте или впишите заново"); }
        else { advantage = focus[0] > focus[1] ? 0 : 1; Sfx.play("gong", 0.6f, 1.2f); }
    }

    void stepStrike(LinearLayout act) {
        int f = advantage < 0 ? 0 : advantage;
        TextView hd = Ui.text(a, "Первым бьёт " + b.names[f] + " (Преимущество), затем " + b.names[1 - f] + ", если его боец ещё стоит.", 14, Theme.bodyBold, Ui.BONE);
        act.addView(hd);
        note(act, "Попадание — WS против WS соперника, ранение — S против T, затем спасброски и урон. Потерянные раны отмечайте «−» на карточке бойца.");
        for (int k = 0; k < 2; k++) {
            final int p = k == 0 ? f : 1 - f, o = 1 - p;
            Theme t = Theme.forName(factionOf(p));
            LinearLayout box = panel(act, p, (k + 1) + ". " + b.names[p] + " · " + (gambit[p] == null ? "" : gambit[p].name));
            StringBuilder s = new StringBuilder();
            int ws = wsv[p], at = av[p];
            String atStr;
            if (has(p, "Taunt and Bait") && wsv[o] >= 0 && av[o] >= 0) {
                boolean eq = wsv[p] == wsv[o] && av[p] == av[o];
                ws = wsv[o] - (eq ? 1 : 0);
                at = av[o] - (eq ? 1 : 0);
            }
            if (has(p, "Guard Up")) ws = ws < 0 ? -1 : ws + 1;
            if (has(p, "Guard Up") || has(p, "Withdraw")) atStr = "1 (" + gambit[p].name + ")";
            else {
                int bonus = (advantage == p ? 1 : 0) + (has(p, "Grandstand") ? outside[p] : 0) + (has(p, "Flurry of Blows") ? flurry[p] : 0);
                atStr = at >= 0 ? String.valueOf(at + bonus) : "A" + (bonus > 0 ? " + " + bonus : "");
                StringBuilder w = new StringBuilder();
                if (advantage == p) w.append("+1 Преимущество");
                if (has(p, "Grandstand") && outside[p] > 0) w.append(w.length() > 0 ? ", " : "").append("+").append(outside[p]).append(" Grandstand");
                if (has(p, "Flurry of Blows")) w.append(w.length() > 0 ? ", " : "").append(flurry[p] > 0 ? "+" + flurry[p] + " Flurry" : "+D3 Flurry — бросьте");
                if (w.length() > 0) atStr += " (" + w + ")";
            }
            s.append("Атаки: ").append(atStr);
            if (ws >= 0) s.append("\nWS: ").append(ws).append(has(p, "Guard Up") ? " (+1 Guard Up)" : has(p, "Taunt and Bait") ? " (Taunt and Bait)" : "").append(wsv[o] >= 0 ? " против WS " + (has(o, "Guard Up") ? wsv[o] + 1 : wsv[o]) : "");
            if (has(p, "Finishing Blow")) s.append("\nПопадания: +1 S и +1 Damage");
            if (has(p, "Flurry of Blows")) s.append("\nDamage всех попаданий = 1");
            if (has(p, "Test the Foe")) s.append("\nБез бонусов в этом раунде; если выживет — Преимущество в шаге Славы");
            if (has(p, "Withdraw")) s.append("\nВ Славе сможет завершить вызов без CRP");
            if (gambit[p] != null && !gambit[p].core) s.append("\nОсобый гамбит: ").append(gambit[p].summary == null ? "" : gambit[p].summary);
            TextView tv = Ui.text(a, s.toString(), 13, Theme.body, Ui.BONE);
            tv.setPadding(0, dp(4), 0, dp(2));
            box.addView(tv);
            if (has(p, "Flurry of Blows") && flurry[p] == 0) {
                box.addView(Ui.button(a, "🎲 Бросить D3 (Flurry of Blows)", Ui.steelButton(t, dp(6)), Ui.GOLD, 14, new View.OnClickListener() {
                    public void onClick(View v) { flurry[p] = rnd.nextInt(3) + 1; Sfx.play("dice"); rebuild(); }
                }), Ui.lpm(-1, dp(40), 0, dp(4), 0, 0));
            }
            if (has(p, "Guard Up")) stepper(box, t, "Промахи " + b.names[o] + " по этому бойцу", guardMiss, p, false, 0);
        }
        next(act, "К Славе →", true, new Runnable() { public void run() { step = 4; Sfx.play("choir", 0.5f, 1.1f); rebuild(); } });
    }

    // ================================================================ шаг 5: слава
    void stepGlory(LinearLayout act) {
        for (int p = 0; p < 2; p++) if (has(p, "Test the Foe") && wounds[p] > 0 && !(has(1 - p, "Test the Foe") && wounds[1 - p] > 0)) advantage = p;
        boolean dead0 = wounds[0] == 0, dead1 = wounds[1] == 0;
        int winner = -1, crp = 0;
        String why;
        if (dead0 && dead1) why = "Оба бойца пали — ничья, очков нет.";
        else if (dead0 || dead1) {
            int loser = dead0 ? 0 : 1;
            winner = 1 - loser;
            crp = woundsMax[loser] + (command[loser] ? 1 : 0) + taunts[winner];
            why = fighter[winner] + " сразил " + fighter[loser] + ": базовые Wounds (" + woundsMax[loser] + ")" + (command[loser] ? " +1 за Command/Paragon" : "") + (taunts[winner] > 0 ? " +" + taunts[winner] + " за Taunt and Bait" : "") + ".";
        } else if (inflicted[0] != inflicted[1]) {
            winner = inflicted[0] > inflicted[1] ? 0 : 1;
            crp = inflicted[winner] + taunts[winner];
            why = fighter[winner] + " нанёс больше ран: " + inflicted[winner] + " против " + inflicted[1 - winner] + (taunts[winner] > 0 ? ", +" + taunts[winner] + " за Taunt and Bait" : "") + ".";
        } else why = inflicted[0] == 0 ? "Никто не ранен — ничья." : "Раны поровну (" + inflicted[0] + ") — ничья.";

        TextView v = Ui.title(a, winner >= 0 ? b.names[winner] + " побеждает: " + crp + " CRP" : "Ничья", 20, Ui.GOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(4), 0, dp(4));
        act.addView(v);
        TextView w = Ui.text(a, why, 14, Theme.body, Ui.BONE);
        w.setGravity(Gravity.CENTER);
        act.addView(w);
        if (winner >= 0) note(act, "Очки Combat Resolution прибавьте к результату этой рукопашной (Resolution).");

        for (int p = 0; p < 2; p++) {
            if (!has(p, "Withdraw") || wounds[p] == 0 || winner == p) continue;
            final int pp = p;
            act.addView(Ui.button(a, "Withdraw: " + b.names[p] + " выходит из вызова без CRP", Ui.steelButton(Theme.forName(factionOf(p)), dp(8)), Ui.BONE, 15, new View.OnClickListener() {
                public void onClick(View v) { Sfx.play("clunk"); a.toast(fighter[pp] + " отступает. Вызов окончен, CRP никто не получает."); a.back(); }
            }), Ui.lpm(-1, dp(50), 0, dp(8), 0, 0));
        }
        if (winner < 0 && !(dead0 && dead1)) {
            int adv = advantage < 0 ? challenger : advantage;
            note(act, b.names[adv] + " (Преимущество) решает: новый раунд вызова или закончить вничью.");
            act.addView(Ui.button(a, "⚔  Следующий раунд (Face-Off)", Ui.steelButton(theme, dp(8)), Ui.BONE, 16, new View.OnClickListener() {
                public void onClick(View v) { nextRound(); }
            }), Ui.lpm(-1, dp(50), 0, dp(4), 0, dp(4)));
        }
        act.addView(Ui.button(a, "Завершить вызов", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) { Sfx.play(dead0 || dead1 ? "fanfare" : "seal"); a.back(); }
            final boolean dead0 = wounds[0] == 0, dead1 = wounds[1] == 0;
        }), Ui.lpm(-1, dp(50), 0, dp(6), 0, 0));
    }

    void nextRound() {
        advNext = advantage >= 0 ? advantage : advNext;
        round++;
        gambit[0] = gambit[1] = null;
        forbidden = null;
        revealed = false;
        focus[0] = focus[1] = 0;
        focusText[0] = focusText[1] = "";
        focusSet[0] = focusSet[1] = false;
        man1[0] = man1[1] = man2[0] = man2[1] = 0;
        for (int p = 0; p < 2; p++) { guardNext[p] = guardMiss[p]; guardMiss[p] = 0; }
        advantage = -1;
        step = 1;
        Sfx.play("clunk");
        rebuild();
    }

    void next(LinearLayout act, String label, boolean enabled, final Runnable r) {
        TextView n = Ui.button(a, label, enabled ? Ui.brass(dp(8)) : Ui.steelButton(theme, dp(8)), enabled ? 0xFF1A140C : Ui.BONE_DIM, 18, new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        n.setEnabled(enabled);
        act.addView(n, Ui.lpm(-1, dp(54), 0, dp(12), 0, 0));
    }

    // ================================================================ анимация столкновения
    /** Два бойца несутся навстречу, удар, вспышка, искры, ударная волна; затем раскрываются гамбиты. */
    static final class Clash extends View {
        final MainActivity a;
        final String[] names;
        final String f0, f1;
        final String[] ids;
        final Gambits.G[] gambits;
        final long t0 = SystemClock.uptimeMillis();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final float[] sx = new float[60], sy = new float[60], vx = new float[60], vy = new float[60];
        boolean hit;
        Runnable done;
        final Theme t0th, t1th;
        Bitmap ph0, ph1;

        Clash(MainActivity a, String[] names, String f0, String f1, String[] ids, Gambits.G[] gambits) {
            super(a);
            this.a = a; this.names = names; this.f0 = f0; this.f1 = f1; this.ids = ids; this.gambits = gambits;
            t0th = Theme.forName(f0);
            t1th = Theme.forName(f1);
            ph0 = ids[0] == null ? null : a.unitArt(ids[0], f0);
            ph1 = ids[1] == null ? null : a.unitArt(ids[1], f1);
            setClickable(true);
        }

        void start(Runnable done) {
            this.done = done;
            Sfx.play("gate_close", 0.6f, 1.3f);
            Ui.pulses(new int[]{0, 150, 300}, new int[]{Ui.TICK});
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float cy = h * 0.42f, r = Math.min(w, h) * 0.16f;
            // затемнение
            float dim = Math.min(1, t / 0.25f);
            c.drawColor(Color.argb((int) (210 * dim), 0, 0, 0));
            // разбег (0..0.55 с), удар в 0.55
            float run = Math.min(1, t / 0.55f);
            float ease = run * run * run;
            float hitT = 0.55f;
            float x0 = -r + (w / 2 - r * 0.9f + r) * ease, x1 = w + r - (w / 2 - r * 0.9f + r) * ease;
            if (t > hitT) {
                if (!hit) {
                    hit = true;
                    Sfx.play("w_power", 1f, 0.8f);
                    Sfx.play("w_blade", 1f, 0.9f);
                    Sfx.play("gong", 0.8f, 1.1f);
                    Ui.pulses(new int[]{0, 60, 140, 260}, new int[]{Ui.HEAVY, Ui.HEAVY, Ui.CLICK, Ui.TICK});
                    Random rr = new Random();
                    for (int i = 0; i < sx.length; i++) {
                        sx[i] = w / 2; sy[i] = cy;
                        double ang = rr.nextDouble() * Math.PI * 2;
                        float sp = dp(4 + rr.nextInt(14));
                        vx[i] = (float) Math.cos(ang) * sp; vy[i] = (float) Math.sin(ang) * sp - dp(2);
                    }
                }
                // отскок после удара
                float k = Math.min(1, (t - hitT) / 0.35f);
                float back = (float) Math.sin(k * Math.PI) * r * 0.5f;
                x0 = w / 2 - r * 0.9f - back;
                x1 = w / 2 + r * 0.9f + back;
            }
            float shake = t > hitT && t < hitT + 0.3f ? (float) Math.sin(t * 90) * dp(6) * (1 - (t - hitT) / 0.3f) : 0;
            c.save();
            c.translate(shake, 0);
            fighter(c, x0, cy, r, t0th, ph0, names[0], true);
            fighter(c, x1, cy, r, t1th, ph1, names[1], false);
            if (t > hitT) {
                float k = (t - hitT);
                // вспышка
                float fa = Math.max(0, 1 - k / 0.25f);
                p.setShader(new RadialGradient(w / 2, cy, r * 2.4f, new int[]{Color.argb((int) (255 * fa), 255, 250, 220), Color.argb((int) (160 * fa), 255, 170, 60), 0}, new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
                c.drawCircle(w / 2, cy, r * 2.4f, p);
                p.setShader(null);
                // ударная волна
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(4) * Math.max(0, 1 - k / 0.6f));
                p.setColor(Color.argb((int) (220 * Math.max(0, 1 - k / 0.6f)), 255, 220, 140));
                c.drawCircle(w / 2, cy, r * 0.3f + k * w * 0.9f, p);
                p.setStyle(Paint.Style.FILL);
                // искры
                for (int i = 0; i < sx.length; i++) {
                    sx[i] += vx[i]; sy[i] += vy[i]; vy[i] += dp(0.5f); vx[i] *= 0.97f;
                    float al = Math.max(0, 1 - k / 1.1f);
                    p.setColor(Color.argb((int) (255 * al), 255, 200 + (i * 7) % 55, 80));
                    c.drawCircle(sx[i], sy[i], dp(1.4f + (i % 3)), p);
                }
                // скрещённые клинки
                if (k > 0.15f) {
                    float ga = Math.min(1, (k - 0.15f) / 0.3f);
                    p.setColor(Color.argb((int) (255 * ga), 240, 230, 200));
                    p.setStrokeWidth(dp(5));
                    p.setStrokeCap(Paint.Cap.ROUND);
                    c.drawLine(w / 2 - r * 0.7f, cy - r * 0.9f, w / 2 + r * 0.7f, cy + r * 0.9f, p);
                    c.drawLine(w / 2 + r * 0.7f, cy - r * 0.9f, w / 2 - r * 0.7f, cy + r * 0.9f, p);
                }
                // раскрытие гамбитов
                if (k > 0.7f) {
                    float ga = Math.min(1, (k - 0.7f) / 0.35f);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTypeface(Theme.names);
                    p.setTextSize(dp(15));
                    p.setColor(Color.argb((int) (255 * ga), 230, 200, 120));
                    if (gambits[0] != null) c.drawText(gambits[0].name, w * 0.27f, cy + r * 2.1f + (1 - ga) * dp(20), p);
                    if (gambits[1] != null) c.drawText(gambits[1].name, w * 0.73f, cy + r * 2.1f + (1 - ga) * dp(20), p);
                    p.setTextSize(dp(30));
                    p.setTypeface(Theme.namesBlack);
                    p.setColor(Color.argb((int) (255 * ga), 240, 220, 170));
                    c.drawText("ВЫЗОВ!", w / 2, cy - r * 1.7f, p);
                }
            }
            c.restore();
            if (t < 2.4f) postInvalidateOnAnimation();
            else finish();
        }

        void fighter(Canvas c, float x, float y, float r, Theme th, Bitmap ph, String name, boolean left) {
            p.setShader(new RadialGradient(x, y, r * 1.6f, Theme.alpha(th.glow, 0.5f), 0, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 1.6f, p);
            p.setShader(null);
            android.graphics.Path hex = Ui.Hex.hexPath(x, y, r);
            p.setColor(Theme.mix(th.trim, Color.WHITE, 0.2f));
            c.drawPath(hex, p);
            android.graphics.Path in = Ui.Hex.hexPath(x, y, r * 0.86f);
            p.setColor(0xFF101410);
            c.drawPath(in, p);
            if (ph != null) {
                c.save();
                c.clipPath(in);
                float s = Math.max(r * 2 / ph.getWidth(), r * 2 / ph.getHeight());
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postScale(s, s);
                m.postTranslate(x - ph.getWidth() * s / 2, y - ph.getHeight() * s / 2);
                c.drawBitmap(ph, m, p);
                c.restore();
            } else {
                Glyph.draw(c, "sword", x, y, r * 0.55f, Theme.mix(th.accentBright(), Color.WHITE, 0.2f));
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Theme.names);
            p.setTextSize(dp(16));
            p.setColor(0xFFF0E2C0);
            c.drawText(name == null ? "" : name, x, y + r * 1.35f, p);
        }

        private boolean finished;

        void finish() {
            if (finished) return;
            finished = true;
            animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                public void run() { a.overlay.removeView(Clash.this); if (done != null) done.run(); }
            }).start();
        }
    }
}
