package com.medoedy.liber;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Roster;

import java.util.List;
import java.util.Random;

import static com.medoedy.liber.Ui.dp;

/** Подготовка битвы: миссия, развёртывание, игроки, бросок за первый ход. */
final class BattleSetupScreen extends Screen {
    final List<Missions.Mission> missions = Missions.all();
    Missions.Mission chosen;
    int deployment = 0;
    final String[] names = {"Игрок 1", "Игрок 2"};
    final String[] factions = {"", ""};
    int first = -1;
    int attacker = 0;
    EditText[] nameIn = new EditText[2];
    final Random rnd = new Random();

    BattleSetupScreen(MainActivity a) {
        super(a);
        missions.add(Missions.custom());
        chosen = missions.get(0);
    }

    void preset(int p, String name, String faction) {
        names[p] = name;
        factions[p] = faction;
        theme = Theme.forName(faction);
    }

    void readNames() {
        for (int i = 0; i < 2; i++) if (nameIn[i] != null) {
            String s = nameIn[i].getText().toString().trim();
            if (!s.isEmpty()) names[i] = s;
        }
    }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Битва", "Подготовка к сражению", theme, true));

        // ---------- миссия
        c.addView(a.ribbonTitle("МИССИЯ", theme));
        String pack = null;
        for (final Missions.Mission m : missions) {
            if (!m.pack.equals(pack)) { pack = m.pack; c.addView(a.label(pack.toUpperCase(), Ui.GOLD)); }
            boolean on = m == chosen;
            LinearLayout card = Ui.row(a);
            card.setBackground(on ? Ui.states(new Ui.Steel(0xF2402418, 0xF2190C08, Ui.GOLD, 0xFFC04020, dp(10), true, false), new Ui.Steel(0xF2502C1E, 0xF2200F0A, Ui.GOLD, 0xFFC04020, dp(10), true, true))
                    : Ui.states(new Ui.Steel(0xF226302B, 0xF2121815, Ui.GOLD_DIM, 0, dp(10), false, false), new Ui.Steel(0xF2343F39, 0xF21A211E, Ui.GOLD_DIM, 0, dp(10), false, true)));
            card.setPadding(dp(12), dp(12), dp(14), dp(12));
            card.addView(new Ui.Hex(a, theme, m.custom ? "sword" : "banner"), Ui.lpm(dp(50), dp(50), 0, 0, dp(12), 0));
            LinearLayout txt = Ui.col(a);
            txt.addView(Ui.title(a, m.ru, 19, Ui.BONE));
            txt.addView(Ui.text(a, m.name, 13, Theme.names, Ui.GOLD));
            txt.addView(Ui.text(a, m.desc, 13, Theme.body, Ui.BONE_DIM));
            StringBuilder ob = new StringBuilder("Маркеры: ");
            for (int i = 0; i < m.objectives.size(); i++) { if (i > 0) ob.append(" · "); ob.append(m.objectives.get(i).vp); }
            ob.append(" ПО за ход");
            StringBuilder se = new StringBuilder("Вторичные: ");
            for (int i = 0; i < m.secondaries.size(); i++) { if (i > 0) se.append(", "); se.append(short2(m.secondaries.get(i).name)).append(" ").append(m.secondaries.get(i).vp); }
            String extra = (m.asym ? "\nАтакующий и Защитник" + (m.objAttacker ? " · маркеры только у Атакующего" : "") : "") + (m.advancing ? "\nМаркеры продвигаются и дорожают до 3" : "") + (m.events.isEmpty() ? "" : "\nОсобые очки: " + eventsShort(m));
            txt.addView(Ui.text(a, ob + "\n" + se + "\nХодов: " + m.turns + (m.extraTurn ? " (+1 на 4+)" : "") + extra, 12, Theme.bodyBold, on ? (Ui.dark ? 0xFFF0C080 : 0xFF8A4A10) : Ui.GOLD_DIM));
            if (on && m.note != null) {
                TextView nt = Ui.text(a, m.note, 12, Theme.body, Ui.BONE);
                nt.setPadding(0, dp(4), 0, 0);
                txt.addView(nt);
            }
            card.addView(txt, Ui.weight(1));
            if (m.book != null && m.page >= 0) {
                final Missions.Mission mm = m;
                card.addView(Ui.button(a, "📖", Ui.steelButton(theme, dp(6)), Ui.GOLD, 17, new View.OnClickListener() {
                    public void onClick(View v) { readNames(); LibraryScreen.openPage(a, mm.book, mm.page, mm.name); }
                }), Ui.lpm(dp(40), dp(40), dp(6), 0, 0, 0));
            }
            card.setClickable(true);
            Ui.press(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    readNames();
                    Ui.haptic(Ui.CLICK);
                    Sfx.play("seal", 0.7f, 1.1f);
                    chosen = m;
                    if (m.custom) editCustom(); else rebuild();
                }
            });
            c.addView(card, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        }

        // ---------- развёртывание
        c.addView(a.ribbonTitle("РАЗВЁРТЫВАНИЕ", theme));
        if (chosen.map != null) {
            if (chosen.mapId != null) c.addView(new MapView(a, chosen.mapId), Ui.lpm(-1, dp(185), 0, dp(4), 0, dp(2)));
            TextView mp = Ui.text(a, chosen.map, 13, Theme.body, Ui.BONE_DIM);
            mp.setPadding(dp(4), dp(2), 0, dp(4));
            c.addView(mp);
            if (chosen.book != null) c.addView(Ui.button(a, "📖  Карта миссии в книге", Ui.steelButton(theme, dp(8)), Ui.BONE, 15, new View.OnClickListener() {
                public void onClick(View v) { readNames(); LibraryScreen.openPage(a, chosen.book, chosen.page, chosen.name); }
            }), Ui.lpm(-1, dp(46), 0, dp(4), 0, 0));
        } else {
        LinearLayout deps = Ui.row(a);
        for (int i = 0; i < Missions.DEPLOYMENTS.length; i++) {
            final int idx = i;
            LinearLayout dc = Ui.col(a);
            dc.setGravity(Gravity.CENTER_HORIZONTAL);
            boolean on = i == deployment;
            dc.setBackground(on ? new Ui.Steel(0xF2402418, 0xF2190C08, Ui.GOLD, 0xFFC04020, dp(8), false, false) : Ui.steelFlat(theme, dp(8)));
            dc.setPadding(dp(6), dp(8), dp(6), dp(8));
            dc.addView(new MapView(a, i), Ui.lp(-1, dp(56)));
            TextView t = Ui.text(a, Missions.DEPLOYMENTS[i][1], 13, Theme.bodyBold, on ? Ui.BONE : Ui.BONE_DIM);
            t.setGravity(Gravity.CENTER);
            dc.addView(t);
            dc.setClickable(true);
            Ui.press(dc);
            dc.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { readNames(); Ui.haptic(Ui.CLICK); Sfx.play("click"); deployment = idx; rebuild(); }
            });
            LinearLayout.LayoutParams lp = Ui.lpm(0, -2, dp(2), 0, dp(2), 0);
            lp.weight = 1;
            deps.addView(dc, lp);
        }
        c.addView(deps);
        TextView depDesc = Ui.text(a, Missions.DEPLOYMENTS[deployment][2] + " (схема условная)", 13, Theme.body, Ui.BONE_DIM);
        depDesc.setPadding(dp(4), dp(6), 0, 0);
        c.addView(depDesc);
        c.addView(Ui.button(a, "🎲  Бросить кубик за развёртывание", Ui.steelButton(theme, dp(8)), Ui.BONE, 15, new View.OnClickListener() {
            public void onClick(View v) {
                readNames();
                Sfx.play("dice");
                int roll = rnd.nextInt(6) + 1;
                deployment = (roll - 1) / 2;
                a.toast("Выпало " + roll + " → " + Missions.DEPLOYMENTS[deployment][1]);
                rebuild();
            }
        }), Ui.lpm(-1, dp(46), 0, dp(6), 0, 0));
        }

        // ---------- игроки
        c.addView(a.ribbonTitle("ПОЛКОВОДЦЫ", theme));
        for (int p = 0; p < 2; p++) c.addView(playerCard(p), Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));

        // ---------- роли
        if (chosen.asym) {
            c.addView(a.ribbonTitle("АТАКУЮЩИЙ", theme));
            LinearLayout ar = Ui.row(a);
            for (int p = 0; p < 2; p++) {
                final int pp = p;
                TextView b = Ui.button(a, names[p], p == attacker ? Ui.brass(dp(8)) : Ui.steelButton(theme, dp(8)), p == attacker ? 0xFF1A140C : Ui.BONE, 16, new View.OnClickListener() {
                    public void onClick(View v) { readNames(); attacker = pp; rebuild(); }
                });
                LinearLayout.LayoutParams lp = Ui.lpm(0, dp(48), dp(2), 0, dp(2), 0);
                lp.weight = 1;
                ar.addView(b, lp);
            }
            c.addView(ar);
            TextView an = Ui.text(a, "Второй игрок — Защитник.", 13, Theme.body, Ui.BONE_DIM);
            an.setPadding(dp(4), dp(4), 0, 0);
            c.addView(an);
        }

        // ---------- первый ход
        c.addView(a.ribbonTitle("ПЕРВЫЙ ХОД", theme));
        LinearLayout fr = Ui.row(a);
        for (int p = 0; p < 2; p++) {
            final int pp = p;
            TextView b = Ui.button(a, names[p], p == first ? Ui.brass(dp(8)) : Ui.steelButton(theme, dp(8)), p == first ? 0xFF1A140C : Ui.BONE, 16, new View.OnClickListener() {
                public void onClick(View v) { readNames(); first = pp; rebuild(); }
            });
            LinearLayout.LayoutParams lp = Ui.lpm(0, dp(48), dp(2), 0, dp(2), 0);
            lp.weight = 1;
            fr.addView(b, lp);
        }
        c.addView(fr);
        c.addView(Ui.button(a, "🎲  Бросок за инициативу", Ui.steelButton(theme, dp(8)), Ui.BONE, 15, new View.OnClickListener() {
            public void onClick(View v) {
                readNames();
                Sfx.play("dice");
                int r0, r1;
                do { r0 = rnd.nextInt(6) + 1; r1 = rnd.nextInt(6) + 1; } while (r0 == r1);
                first = r0 > r1 ? 0 : 1;
                a.toast(names[0] + ": " + r0 + "   ·   " + names[1] + ": " + r1 + "\nПервым ходит " + names[first]);
                rebuild();
            }
        }), Ui.lpm(-1, dp(46), 0, dp(6), 0, 0));

        c.addView(Ui.button(a, "⚔  Начать битву", Ui.brass(dp(10)), 0xFF1A140C, 21, new View.OnClickListener() {
            public void onClick(View v) { start(); }
        }), Ui.lpm(-1, dp(58), 0, dp(18), 0, 0));
        f.addView(a.scroll(c));
        return f;
    }

    static String eventsShort(Missions.Mission m) {
        StringBuilder b = new StringBuilder();
        for (Missions.Event e : m.events) {
            String n = e.name.startsWith("Engine Kill") ? "Engine Kill" : e.name.contains("Emplacement") || e.name.contains("Bunker") || e.name.contains("Command Post") ? "стратегические цели" : e.name;
            if (b.indexOf(n) < 0) { if (b.length() > 0) b.append(", "); b.append(n); }
        }
        return b.toString();
    }

    static String short2(String n) {
        if (n.startsWith("Slay")) return "Генерал";
        if (n.startsWith("Giant")) return "Гигант";
        if (n.startsWith("Last")) return "Последний";
        if (n.startsWith("First")) return "Первый удар";
        return n;
    }

    View playerCard(final int p) {
        final Theme t = Theme.forName(factions[p]);
        LinearLayout card = Ui.col(a);
        card.setBackground(new Ui.Steel(0xF226302B, 0xF2121815, Theme.mix(t.trim, Ui.GOLD, 0.6f), t.glow, dp(10), true, false));
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout top = Ui.row(a);
        Ui.Hex hx = new Ui.Hex(a, t, t.numeral.isEmpty() ? "shield" : null);
        hx.label = t.numeral.isEmpty() ? null : t.numeral;
        top.addView(hx, Ui.lpm(dp(48), dp(48), 0, 0, dp(10), 0));
        nameIn[p] = a.input(names[p], "Имя игрока", false);
        top.addView(nameIn[p], Ui.weight(1));
        card.addView(top);
        card.addView(Ui.button(a, factions[p].isEmpty() ? "Выбрать фракцию / ростер" : factions[p], Ui.steelButton(t, dp(8)), Ui.BONE, 15, new View.OnClickListener() {
            public void onClick(View v) { readNames(); pickFaction(p); }
        }), Ui.lpm(-1, dp(46), 0, dp(8), 0, 0));
        return card;
    }

    void pickFaction(final int p) {
        LinearLayout body = a.openSheet("Знамя полководца", "Из ваших ростеров или фракция", theme);
        for (final Roster r : a.store.rosters()) {
            Data.Cat cat = Store.data == null ? null : Store.data.cats.get(r.catalogueId);
            final String cn = cat == null ? "" : cat.name;
            Theme t = Theme.forName(cn);
            a.sheetItem(body, "banner", r.name, cn + " · " + com.medoedy.liber.engine.Engine.fmt(r.limit) + " очков", t, false, new View.OnClickListener() {
                public void onClick(View v) { a.closeSheet(); names[p] = names[p].startsWith("Игрок") ? r.name : names[p]; factions[p] = cn; Voice.say(Voice.proclamation(cn)); rebuild(); }
            });
        }
        if (Store.data != null) for (final Data.Cat cat : Store.data.playable()) {
            Theme t = Theme.forName(cat.name);
            a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, cat.name, null, t, false, new View.OnClickListener() {
                public void onClick(View v) { a.closeSheet(); factions[p] = cat.name; Voice.say(Voice.proclamation(cat.name)); rebuild(); }
            });
        }
    }

    void editCustom() {
        final LinearLayout body = a.openSheet("Своя миссия", "Маркеры, ценность и вторичные цели", theme);
        fillCustom(body);
    }

    void fillCustom(final LinearLayout body) {
        body.removeAllViews();
        final Missions.Mission m = chosen;
        LinearLayout sheet = Ui.col(a);
        sheet.setBackground(Ui.parchment());
        sheet.setPadding(dp(12), dp(10), dp(12), dp(12));
        sheet.addView(counter("Ходов", m.turns, 1, 8, new UnitScreen.Step() { public void set(int v) { m.turns = v; fillCustom(body); } }));
        sheet.addView(counter("Маркеров", m.objectives.size(), 1, 8, new UnitScreen.Step() {
            public void set(int v) {
                while (m.objectives.size() < v) m.objectives.add(new Missions.Objective("Маркер " + (m.objectives.size() + 1), 2, false));
                while (m.objectives.size() > v) m.objectives.remove(m.objectives.size() - 1);
                fillCustom(body);
            }
        }));
        for (final Missions.Objective o : m.objectives) {
            sheet.addView(counter(o.name + " — ПО", o.vp, 0, 9, new UnitScreen.Step() { public void set(int v) { o.vp = v; fillCustom(body); } }));
        }
        for (final Missions.Secondary s : m.secondaries) {
            sheet.addView(counter(s.name, s.vp, 0, 9, new UnitScreen.Step() { public void set(int v) { s.vp = v; fillCustom(body); } }));
        }
        body.addView(sheet);
        body.addView(Ui.button(a, "Готово", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); rebuild(); }
        }), Ui.lpm(-1, dp(48), 0, dp(10), 0, 0));
    }

    View counter(String label, int value, final int lo, final int hi, final UnitScreen.Step st) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(Ui.text(a, label, 15, Theme.bodyBold, Ui.INK), Ui.weight(1));
        final int v = value;
        TextView minus = Ui.button(a, "−", Ui.steelButton(theme, dp(5)), Ui.GOLD, 20, new View.OnClickListener() { public void onClick(View x) { if (v > lo) st.set(v - 1); } });
        minus.setPadding(0, 0, 0, dp(3));
        row.addView(minus, Ui.lp(dp(40), dp(38)));
        TextView val = Ui.text(a, String.valueOf(value), 18, Theme.namesBlack, Ui.INK);
        val.setGravity(Gravity.CENTER);
        row.addView(val, Ui.lp(dp(36), -2));
        TextView plus = Ui.button(a, "+", Ui.steelButton(theme, dp(5)), Ui.GOLD, 20, new View.OnClickListener() { public void onClick(View x) { if (v < hi) st.set(v + 1); } });
        plus.setPadding(0, 0, 0, dp(3));
        row.addView(plus, Ui.lp(dp(40), dp(38)));
        return row;
    }

    void start() {
        readNames();
        Battle b = new Battle();
        b.mission = Battle.missionFrom(Battle.missionJson(chosen));
        b.deployment = chosen.map != null ? "карта миссии" : Missions.DEPLOYMENTS[deployment][1];
        b.attacker = attacker;
        b.names = names.clone();
        b.factions = factions.clone();
        b.first = first < 0 ? 0 : first;
        b.init();
        b.started = System.currentTimeMillis();
        b.turnStarted = b.started;
        b.save(a);
        Sfx.play("gong");
        Ui.haptic(Ui.HEAVY);
        Voice.say(Voice.line("Да начнётся битва.", "Let the battle begin."));
        a.replaceTop(new BattleScreen(a, b));
    }

    /** Схема развёртывания: три стандартные (kind 0-2) или карта особой миссии (mid). Стол 72″×48″. */
    static final class MapView extends View {
        final int kind;
        final String mid;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint t9 = new Paint(Paint.ANTI_ALIAS_FLAG);
        static final int ATK = 0xCCB03A2A, DEF = 0xCC2A5AB0, TXT = 0xFFE6D9B8;

        MapView(android.content.Context c, int kind) { super(c); this.kind = kind; this.mid = null; }
        MapView(android.content.Context c, String mid) { super(c); this.kind = -1; this.mid = mid; }

        float L, T, TW, TH;
        float X(float x) { return L + TW * x / 72f; }
        float Y(float y) { return T + TH * y / 48f; }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float tw = Math.min(w - dp(8), h * 1.5f - dp(4)), th = tw / 1.5f;
            L = (w - tw) / 2; T = (h - th) / 2; TW = tw; TH = th;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF3A3324);
            c.drawRect(L, T, L + TW, T + TH, p);
            if (mid != null) mission(c);
            else {
                if (kind == 0) { // диагональные четверти
                    zone(c, 0, 0, 30, 20, ATK);
                    zone(c, 42, 28, 72, 48, DEF);
                } else if (kind == 1) { // короткие края
                    zone(c, 0, 0, 14.5f, 48, ATK);
                    zone(c, 57.5f, 0, 72, 48, DEF);
                } else { // длинные края
                    zone(c, 0, 0, 72, 12, ATK);
                    zone(c, 0, 36, 72, 48, DEF);
                }
                p.setColor(TXT);
                c.drawCircle(L + TW / 2, T + TH / 2, dp(2.5f), p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setColor(Ui.GOLD_DIM);
            c.drawRect(new RectF(L, T, L + TW, T + TH), p);
            p.setStyle(Paint.Style.FILL);
        }

        void zone(Canvas c, float x0, float y0, float x1, float y1, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            c.drawRect(X(x0), Y(y0), X(x1), Y(y1), p);
        }

        void label(Canvas c, String s, float x, float y, int col, float sp) {
            t9.setColor(col);
            t9.setTextSize(dp(sp));
            t9.setTextAlign(Paint.Align.CENTER);
            t9.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(s, X(x), Y(y), t9);
        }

        void labelV(Canvas c, String s, float x, float y, int col, float sp) {
            t9.setColor(col);
            t9.setTextSize(dp(sp));
            t9.setTextAlign(Paint.Align.CENTER);
            t9.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.save();
            c.rotate(-90, X(x), Y(y));
            c.drawText(s, X(x), Y(y), t9);
            c.restore();
        }

        void marker(Canvas c, float x, float y, int v) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE6C25A);
            c.drawCircle(X(x), Y(y), dp(7), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(0xFF1A140C);
            c.drawCircle(X(x), Y(y), dp(7), p);
            p.setStyle(Paint.Style.FILL);
            label(c, String.valueOf(v), x, y + 1.9f, 0xFF1A140C, 9);
        }

        void grid(Canvas c) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(0.7f));
            p.setColor(0x55E6D9B8);
            for (int i = 1; i < 4; i++) {
                c.drawLine(X(i * 18), T, X(i * 18), T + TH, p);
                c.drawLine(L, Y(i * 12), L + TW, Y(i * 12), p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        void dimV(Canvas c, String s, float x, float y0, float y1) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(TXT);
            c.drawLine(X(x), Y(y0), X(x), Y(y1), p);
            c.drawLine(X(x) - dp(3), Y(y0), X(x) + dp(3), Y(y0), p);
            c.drawLine(X(x) - dp(3), Y(y1), X(x) + dp(3), Y(y1), p);
            p.setStyle(Paint.Style.FILL);
            t9.setColor(TXT);
            t9.setTextSize(dp(9));
            t9.setTextAlign(Paint.Align.LEFT);
            t9.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(s, X(x) + dp(4), (Y(y0) + Y(y1)) / 2 + dp(3), t9);
        }

        void dimH(Canvas c, String s, float y, float x0, float x1) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(TXT);
            c.drawLine(X(x0), Y(y), X(x1), Y(y), p);
            c.drawLine(X(x0), Y(y) - dp(3), X(x0), Y(y) + dp(3), p);
            c.drawLine(X(x1), Y(y) - dp(3), X(x1), Y(y) + dp(3), p);
            p.setStyle(Paint.Style.FILL);
            label(c, s, (x0 + x1) / 2, y - 1.2f, TXT, 9);
        }

        /** Маленький золотой треугольник-стрелка (направление продвижения маркеров). */
        void arrow(Canvas c, float x, float y, boolean up) {
            android.graphics.Path tri = new android.graphics.Path();
            float s = dp(4);
            if (up) { tri.moveTo(X(x), Y(y) - s); tri.lineTo(X(x) - s, Y(y) + s * 0.5f); tri.lineTo(X(x) + s, Y(y) + s * 0.5f); }
            else { tri.moveTo(X(x), Y(y) + s); tri.lineTo(X(x) - s, Y(y) - s * 0.5f); tri.lineTo(X(x) + s, Y(y) - s * 0.5f); }
            tri.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE6C25A);
            c.drawPath(tri, p);
        }

        void mission(Canvas c) {
            if (mid.equals("warofgods")) {
                zone(c, 0, 0, 72, 12, ATK);
                zone(c, 0, 36, 72, 48, DEF);
                label(c, "Сторона A — зона развёртывания 72″×12″", 36, 7, TXT, 9);
                label(c, "Сторона B — зона развёртывания 72″×12″", 36, 43, TXT, 9);
                dimV(c, "12″", 70, 0, 12);
                dimV(c, "12″", 70, 36, 48);
                marker(c, 18, 24, 2);
                marker(c, 36, 24, 2);
                marker(c, 54, 24, 2);
            } else if (mid.equals("giantkiller")) {
                zone(c, 0, 0, 24, 48, ATK);
                zone(c, 48, 0, 72, 48, DEF);
                zone(c, 60, 0, 72, 48, 0xCC16305E);
                label(c, "Атакующий", 12, 22, TXT, 10);
                label(c, "зона 24″×48″", 12, 27, TXT, 9);
                label(c, "Защитник", 54, 22, TXT, 10);
                label(c, "зона 24″×48″", 54, 27, TXT, 9);
                labelV(c, "Титаны — 12″", 66, 24, TXT, 9);
                dimH(c, "24″", 3, 0, 24);
                dimH(c, "24″", 3, 48, 72);
                dimH(c, "12″", 45, 60, 72);
                marker(c, 36, 24, 3);
                marker(c, 36, 10, 2);
                marker(c, 36, 38, 2);
            } else if (mid.equals("line")) {
                grid(c);
                zone(c, 0, 0, 72, 12, DEF);
                zone(c, 0, 36, 72, 48, ATK);
                label(c, "Край Защитника · зона 72″×12″", 36, 7, TXT, 9);
                label(c, "Край Атакующего · зона 72″×12″", 36, 43, TXT, 9);
                dimV(c, "12″", 70, 0, 12);
                dimV(c, "12″", 70, 36, 48);
                for (int i = 0; i < 4; i++) {
                    marker(c, 9 + i * 18, 30, 1);
                    arrow(c, 9 + i * 18, 25.5f, true);
                }
                label(c, "маркеры продвигаются к краю Защитника", 36, 17, 0xFFE6C25A, 8);
            } else if (mid.equals("breakthrough")) {
                grid(c);
                zone(c, 0, 0, 72, 12, DEF);
                zone(c, 0, 36, 72, 48, ATK);
                label(c, "Край Защитника · зона 72″×12″", 36, 7, TXT, 9);
                label(c, "Край Атакующего · зона 72″×12″", 36, 43, TXT, 9);
                dimV(c, "12″", 70, 0, 12);
                dimV(c, "12″", 70, 36, 48);
                marker(c, 9, 18, 1);
                marker(c, 63, 18, 1);
                marker(c, 27, 30, 1);
                marker(c, 45, 30, 1);
                // резервы Защитника с коротких краёв
                arrow(c, 4, 24, true);
                arrow(c, 68, 24, true);
                labelV(c, "резервы", 4, 30, 0xFFE6C25A, 8);
                labelV(c, "резервы", 68, 30, 0xFFE6C25A, 8);
            } else if (mid.equals("dawn")) {
                grid(c);
                zone(c, 0, 0, 36, 24, DEF);
                zone(c, 36, 24, 72, 48, ATK);
                label(c, "Защитник", 18, 10, TXT, 10);
                label(c, "зона 36″×24″", 18, 15, TXT, 9);
                label(c, "Атакующий", 54, 34, TXT, 10);
                label(c, "зона 36″×24″", 54, 39, TXT, 9);
                dimH(c, "36″", 2.5f, 0, 36);
                dimH(c, "36″", 45.5f, 36, 72);
                dimV(c, "24″", 2, 0, 24);
                dimV(c, "24″", 70, 24, 48);
                marker(c, 9, 6, 1);
                marker(c, 27, 18, 1);
                marker(c, 45, 30, 1);
                marker(c, 63, 42, 1);
            } else if (mid.equals("ignis")) {
                zone(c, 0, 0, 72, 12, DEF);
                zone(c, 0, 36, 72, 48, ATK);
                zone(c, 8, 36, 24, 48, 0x55E6C25A);
                zone(c, 48, 36, 64, 48, 0x55E6C25A);
                label(c, "Край Защитника · укрепления · зона 12″", 36, 7, TXT, 9);
                label(c, "Край Атакующего · зона 12″", 36, 41, TXT, 9);
                label(c, "Spearhead", 16, 46, 0xFF1A140C, 8);
                label(c, "Spearhead", 56, 46, 0xFF1A140C, 8);
                dimV(c, "12″", 70, 0, 12);
                dimV(c, "12″", 70, 36, 48);
                marker(c, 36, 24, 3);
                marker(c, 20, 15, 2);
                marker(c, 52, 15, 2);
            } else if (mid.equals("firstwave")) {
                zone(c, 0, 0, 72, 12, DEF);
                zone(c, 0, 36, 48, 48, ATK);
                zone(c, 48, 36, 72, 48, 0xCC7A2A20);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(TXT);
                c.drawLine(X(48), Y(36), X(48), Y(48), p);
                p.setStyle(Paint.Style.FILL);
                label(c, "Зона Защитника 72″×12″", 36, 7, TXT, 9);
                label(c, "Зона 1 — пехота", 24, 43, TXT, 9);
                label(c, "Зона 2 — техника", 60, 43, TXT, 9);
                dimV(c, "12″", 70, 0, 12);
                dimV(c, "12″", 2, 36, 48);
                marker(c, 18, 13, 2);
                marker(c, 36, 10, 2);
                marker(c, 54, 13, 2);
                marker(c, 36, 21, 2);
            }
        }
    }
}
