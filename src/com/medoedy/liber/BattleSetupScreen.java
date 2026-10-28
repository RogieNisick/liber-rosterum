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
            TextView mp = Ui.text(a, chosen.map, 14, Theme.body, Ui.BONE);
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
        b.save(a);
        Sfx.play("gong");
        Ui.haptic(Ui.HEAVY);
        Voice.say("Let the battle begin.");
        a.replaceTop(new BattleScreen(a, b));
    }

    /** Условная схема зон развёртывания на столе 6×4. */
    static final class MapView extends View {
        final int kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        MapView(android.content.Context c, int kind) { super(c); this.kind = kind; }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float tw = Math.min(w - dp(8), h * 1.5f - dp(4)), th = tw / 1.5f;
            float l = (w - tw) / 2, t = (h - th) / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF3A3324);
            c.drawRect(l, t, l + tw, t + th, p);
            int c1 = 0xCCB03A2A, c2 = 0xCC2A5AB0;
            if (kind == 0) { // диагональные четверти
                p.setColor(c1); c.drawRect(l, t, l + tw * 0.42f, t + th * 0.42f, p);
                p.setColor(c2); c.drawRect(l + tw * 0.58f, t + th * 0.58f, l + tw, t + th, p);
            } else if (kind == 1) { // короткие края
                p.setColor(c1); c.drawRect(l, t, l + tw * 0.2f, t + th, p);
                p.setColor(c2); c.drawRect(l + tw * 0.8f, t, l + tw, t + th, p);
            } else { // длинные края
                p.setColor(c1); c.drawRect(l, t, l + tw, t + th * 0.25f, p);
                p.setColor(c2); c.drawRect(l, t + th * 0.75f, l + tw, t + th, p);
            }
            p.setColor(0xFFE6D9B8);
            c.drawCircle(l + tw / 2, t + th / 2, dp(2.5f), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setColor(Ui.GOLD_DIM);
            c.drawRect(new RectF(l, t, l + tw, t + th), p);
        }
    }
}
