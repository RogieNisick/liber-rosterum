package com.medoedy.liber;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

import static com.medoedy.liber.Ui.dp;

/** Счётчик победных очков: табло, трекер ходов, маркеры, вторичные цели, кубики. */
final class BattleScreen extends Screen {
    final Battle b;
    final Theme[] th = new Theme[2];
    final Random rnd = new Random();

    BattleScreen(MainActivity a, Battle b) {
        super(a);
        this.b = b;
        th[0] = Theme.forName(b.factions[0]);
        th[1] = Theme.forName(b.factions[1]);
        theme = th[b.first];
    }

    void changed() { b.save(a); rebuild(); }

    @Override
    void refresh() { rebuild(); }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        LinearLayout hd = a.header(b.mission.ru, b.mission.name + " · " + b.deployment, theme, true);
        LinearLayout hrow = (LinearLayout) hd.getChildAt(0);
        TextView menu = Ui.button(a, "⋮", Ui.steelButton(theme, dp(8)), Ui.GOLD, 22, new View.OnClickListener() {
            public void onClick(View v) { menu(); }
        });
        menu.setPadding(0, 0, 0, dp(2));
        hrow.addView(menu, Ui.lp(dp(46), dp(46)));
        c.addView(hd);

        // ---------- табло
        LinearLayout board = Ui.row(a);
        board.addView(scoreCard(0), weighted());
        LinearLayout mid = Ui.col(a);
        mid.setGravity(Gravity.CENTER);
        mid.addView(Ui.text(a, "VS", 16, Theme.namesBlack, Ui.GOLD_DIM));
        board.addView(mid, Ui.lpm(dp(34), -2, 0, 0, 0, 0));
        board.addView(scoreCard(1), weighted());
        c.addView(board, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));

        // ---------- ходы
        LinearLayout turns = Ui.row(a);
        turns.setBackground(Ui.steel(theme));
        turns.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView prev = Ui.button(a, "‹", Ui.steelButton(theme, dp(6)), b.turn > 1 ? Ui.GOLD : 0x44D2AE62, 24, new View.OnClickListener() {
            public void onClick(View v) { if (b.turn > 1) { b.turn--; Sfx.play("clunk"); changed(); } }
        });
        prev.setPadding(0, 0, 0, dp(3));
        turns.addView(prev, Ui.lp(dp(44), dp(44)));
        LinearLayout tc = Ui.col(a);
        tc.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView tt = Ui.title(a, b.finished ? "Битва окончена" : "Ход " + b.turn + " из " + b.mission.turns, 20, Ui.BONE);
        tt.setGravity(Gravity.CENTER);
        tc.addView(tt);
        tc.addView(new TurnPips(a, b.turn, b.mission.turns, b.finished), Ui.lp(-1, dp(22)));
        String who = b.names[(b.first + 0) % 2] + " ходит первым";
        TextView sub = Ui.text(a, who, 12, Theme.body, Ui.GOLD);
        sub.setGravity(Gravity.CENTER);
        tc.addView(sub);
        turns.addView(tc, Ui.weight(1));
        boolean last = b.turn >= b.mission.turns;
        TextView next = Ui.button(a, last ? "⚑" : "›", last ? Ui.brass(dp(6)) : Ui.steelButton(theme, dp(6)), last ? 0xFF1A140C : Ui.GOLD, 22, new View.OnClickListener() {
            public void onClick(View v) { nextTurn(); }
        });
        next.setPadding(0, 0, 0, dp(3));
        turns.addView(next, Ui.lp(dp(44), dp(44)));
        c.addView(turns, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));

        // ---------- маркеры текущего хода
        c.addView(a.ribbonTitle("МАРКЕРЫ · ХОД " + b.turn, theme));
        LinearLayout obj = Ui.col(a);
        obj.setBackground(Ui.parchment());
        obj.setPadding(dp(12), dp(10), dp(12), dp(12));
        for (int o = 0; o < b.mission.objectives.size(); o++) obj.addView(objectiveRow(o));
        String hs = b.mission.advancing ? "Отметьте маркеры, которые Атакующий контролирует в своей фазе победы, и ★ — какой из них засчитать. Остальные контролируемые маркеры продвигаются и дорожают на 1 (до 3)."
                : b.mission.objAttacker ? "Маркеры набирает только Атакующий: отметьте, какие он контролирует в своей фазе победы."
                : "Отметьте, кто контролирует каждый маркер в конце хода (фаза победы).";
        TextView hint = Ui.text(a, hs, 12, Theme.body, Ui.INK_DIM);
        hint.setPadding(0, dp(6), 0, 0);
        obj.addView(hint);
        c.addView(obj);

        // ---------- доп. очки за ход
        c.addView(a.ribbonTitle("ДОП. ОЧКИ ЗА ХОД", theme));
        LinearLayout bon = Ui.col(a);
        bon.setBackground(Ui.parchment());
        bon.setPadding(dp(12), dp(10), dp(12), dp(10));
        for (int p = 0; p < 2; p++) {
            final int pp = p;
            LinearLayout row = Ui.row(a);
            row.addView(Ui.text(a, b.names[p], 16, Theme.names, Ui.INK), Ui.weight(1));
            row.addView(stepper(b.bonus[p][b.turn], -9, 20, new UnitScreen.Step() {
                public void set(int v) { b.bonus[pp][b.turn] = v; Sfx.play("tick"); changed(); }
            }));
            bon.addView(row);
        }
        TextView bh = Ui.text(a, "Line, Vanguard, Expendable и прочие корректировки очков за этот ход.", 12, Theme.body, Ui.INK_DIM);
        bh.setPadding(0, dp(4), 0, 0);
        bon.addView(bh);
        c.addView(bon);

        // ---------- особые очки миссии
        if (!b.mission.events.isEmpty()) {
            c.addView(a.ribbonTitle("ОСОБЫЕ ЦЕЛИ МИССИИ", theme));
            LinearLayout evb = Ui.col(a);
            evb.setBackground(Ui.parchment());
            evb.setPadding(dp(12), dp(8), dp(12), dp(10));
            for (final Missions.Event ev : b.mission.events) {
                LinearLayout row = Ui.row(a);
                row.setPadding(0, dp(3), 0, dp(3));
                LinearLayout txt = Ui.col(a);
                txt.addView(Ui.text(a, ev.name + "  · " + ev.vp + " ПО", 15, Theme.bodyBold, Ui.INK));
                txt.addView(Ui.text(a, ev.desc, 12, Theme.body, Ui.INK_DIM));
                row.addView(txt, Ui.weight(1));
                for (int p = 0; p < 2; p++) {
                    final int pp = p;
                    boolean allowed = ev.side == 0 || !b.mission.asym || (ev.side == 1) == (p == b.attacker);
                    if (!allowed) { row.addView(new View(a), Ui.lp(dp(52), dp(40))); continue; }
                    TextView btn = Ui.button(a, "+" + ev.vp + "\n" + initials(b.names[p]), Ui.heraldic(th[p], dp(5)), Theme.onColor(th[p].accent), 11, new View.OnClickListener() {
                        public void onClick(View v) {
                            b.bonus[pp][b.turn] += ev.vp;
                            Sfx.play("bell");
                            Ui.haptic(Ui.DOUBLE);
                            a.toast(b.names[pp] + ": +" + ev.vp + " ПО — " + ev.name + " (ход " + b.turn + "). Отменить — в «Доп. очки за ход».");
                            changed();
                        }
                    });
                    row.addView(btn, Ui.lpm(dp(52), dp(44), dp(4), 0, 0, 0));
                }
                evb.addView(row);
            }
            c.addView(evb);
        }

        // ---------- контрнаступление (последний ход)
        if (b.mission.rules.contains("Counter Offensive") && b.turn == b.mission.turns && !b.finished) {
            LinearLayout co = Ui.col(a);
            co.setBackground(Ui.steel(theme));
            co.setPadding(dp(12), dp(10), dp(12), dp(10));
            co.addView(Ui.text(a, "Контрнаступление", 16, Theme.names, Ui.BONE));
            boolean any = false;
            for (int p = 0; p < 2; p++) {
                final int pp = p;
                int mine = b.totalBefore(p, b.turn), theirs = b.totalBefore(1 - p, b.turn);
                boolean ok = theirs > 0 && mine * 2 <= theirs;
                if (!ok && !b.counter[p]) continue;
                any = true;
                co.addView(Ui.button(a, (b.counter[p] ? "✓ " : "") + b.names[p] + ": " + (b.counter[p] ? "объявлено — ПО за маркеры ×2" : "объявить (" + mine + " против " + theirs + ")"), b.counter[p] ? Ui.brass(dp(6)) : Ui.steelButton(th[p], dp(6)), b.counter[p] ? 0xFF1A140C : Ui.BONE, 14, new View.OnClickListener() {
                    public void onClick(View v) { b.counter[pp] = !b.counter[pp]; Sfx.play(b.counter[pp] ? "gong" : "tick"); changed(); }
                }), Ui.lpm(-1, dp(44), 0, dp(6), 0, 0));
            }
            if (!any) co.addView(Ui.text(a, "Никто не отстаёт вдвое — контрнаступление недоступно.", 13, Theme.body, Ui.BONE_DIM));
            c.addView(co, Ui.lpm(-1, -2, 0, dp(8), 0, 0));
        }

        // ---------- вторичные цели
        c.addView(a.ribbonTitle("ВТОРИЧНЫЕ ЦЕЛИ", theme));
        LinearLayout sec = Ui.col(a);
        sec.setBackground(Ui.parchment());
        sec.setPadding(dp(12), dp(8), dp(12), dp(10));
        LinearLayout hdr = Ui.row(a);
        hdr.addView(new View(a), Ui.weight(1));
        for (int p = 0; p < 2; p++) {
            TextView n = Ui.text(a, initials(b.names[p]), 12, Theme.bodyBold, Ui.INK_RED);
            n.setGravity(Gravity.CENTER);
            hdr.addView(n, Ui.lp(dp(48), -2));
        }
        sec.addView(hdr);
        for (int i = 0; i < b.mission.secondaries.size(); i++) {
            final int ii = i;
            Missions.Secondary s = b.mission.secondaries.get(i);
            LinearLayout row = Ui.row(a);
            row.setPadding(0, dp(3), 0, dp(3));
            LinearLayout txt = Ui.col(a);
            boolean off = b.anySudden() && s.name.startsWith("Last Man");
            txt.addView(Ui.text(a, s.name + "  · " + s.vp + " ПО" + (off ? "  (не в счёт: внезапная смерть)" : ""), 15, Theme.bodyBold, off ? Ui.INK_DIM : Ui.INK));
            txt.addView(Ui.text(a, s.desc, 12, Theme.body, Ui.INK_DIM));
            row.addView(txt, Ui.weight(1));
            for (int p = 0; p < 2; p++) {
                final int pp = p;
                MainActivity.Toggle tg = new MainActivity.Toggle(a, b.secondary[p][i], false, th[p]);
                tg.onParchment = true;
                tg.setClickable(true);
                Ui.press(tg);
                tg.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        b.secondary[pp][ii] = !b.secondary[pp][ii];
                        Ui.haptic(Ui.CLICK);
                        Sfx.play(b.secondary[pp][ii] ? "bell" : "tick");
                        changed();
                    }
                });
                row.addView(tg, Ui.lp(dp(48), dp(46)));
            }
            sec.addView(row);
        }
        c.addView(sec);

        // ---------- правила миссии
        if (!b.mission.rules.isEmpty()) {
            c.addView(a.label("ПРАВИЛА МИССИИ", Ui.GOLD));
            Ui.Flow rf = new Ui.Flow(a);
            for (final String r : b.mission.rules) {
                TextView chip = Ui.text(a, r, 13, Theme.bodyBold, Ui.BONE);
                chip.setBackground(Ui.steelButton(theme, dp(4)));
                chip.setPadding(dp(10), dp(5), dp(10), dp(6));
                chip.setClickable(true);
                Ui.press(chip);
                chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); ruleSheet(r); } });
                rf.addView(chip);
            }
            c.addView(rf);
        }

        // ---------- журнал
        c.addView(a.ribbonTitle("ЖУРНАЛ БИТВЫ", theme));
        LinearLayout log = Ui.col(a);
        log.setBackground(Ui.parchment());
        log.setPadding(dp(12), dp(10), dp(12), dp(10));
        for (int t = 1; t <= b.mission.turns; t++) {
            int p0 = b.primaryTurn(0, t), p1 = b.primaryTurn(1, t);
            boolean played = t <= b.turn;
            TextView row = Ui.text(a, "Ход " + t + ":   " + b.names[0] + " +" + p0 + "   ·   " + b.names[1] + " +" + p1, 14, played ? Theme.bodyBold : Theme.body, played ? Ui.INK : Ui.INK_DIM);
            row.setPadding(0, dp(2), 0, dp(2));
            log.addView(row);
        }
        TextView totals = Ui.text(a, "Вторичные: " + b.secondaryVp(0) + " / " + b.secondaryVp(1), 13, Theme.body, Ui.INK_DIM);
        totals.setPadding(0, dp(4), 0, 0);
        log.addView(totals);
        c.addView(log);

        c.setPadding(c.getPaddingLeft(), c.getPaddingTop(), c.getPaddingRight(), dp(90));
        f.addView(a.scroll(c));

        // ---------- кубики
        TextView dice = Ui.button(a, "🎲", Ui.brass(dp(10)), 0xFF1A140C, 24, new View.OnClickListener() {
            public void onClick(View v) { diceSheet(); }
        });
        dice.setPadding(0, 0, 0, dp(2));
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        dl.setMargins(0, 0, dp(18), dp(18));
        f.addView(dice, dl);
        // ---------- вызов
        TextView duel = Ui.button(a, "⚔ Вызов", Ui.heraldic(theme, dp(10)), Theme.onColor(theme.accent), 17, new View.OnClickListener() {
            public void onClick(View v) { Sfx.play("w_blade"); a.show(new ChallengeScreen(a, BattleScreen.this, b)); }
        });
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, dp(60), Gravity.BOTTOM | Gravity.START);
        cl.setMargins(dp(18), 0, 0, dp(18));
        duel.setPadding(dp(18), 0, dp(18), dp(2));
        f.addView(duel, cl);
        return f;
    }

    LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); return lp; }

    static String initials(String n) {
        n = n.trim();
        return n.length() <= 6 ? n : n.substring(0, 5) + ".";
    }

    View scoreCard(int p) {
        Theme t = th[p];
        LinearLayout card = Ui.col(a);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(new Ui.Steel(0xF226302B, 0xF2121815, Theme.mix(t.trim, Ui.GOLD, 0.6f), t.glow, dp(10), true, false));
        card.setPadding(dp(8), dp(12), dp(8), dp(12));
        Ui.Hex hx = new Ui.Hex(a, t, t.numeral.isEmpty() ? "shield" : null);
        hx.label = t.numeral.isEmpty() ? null : t.numeral;
        card.addView(hx, Ui.lp(dp(46), dp(46)));
        TextView n = Ui.text(a, b.names[p], 15, Theme.names, Ui.BONE);
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(true);
        card.addView(n, Ui.lp(-1, -2));
        TextView sc = Ui.text(a, String.valueOf(b.total(p)), 44, Theme.namesBlack, t.accentBright());
        sc.setGravity(Gravity.CENTER);
        sc.setShadowLayer(dp(10), 0, dp(2), 0xFF000000);
        card.addView(sc);
        TextView sub = Ui.text(a, "победных очков", 11, Theme.body, Ui.GOLD);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);
        String tag = (b.mission.asym ? (p == b.attacker ? "⚔ Атакующий" : "⛨ Защитник") : "") + (b.sudden[p] ? (b.mission.asym ? " · " : "") + "+3 внезапная смерть" : "") + (b.counter[p] ? " · контрнаступление" : "");
        if (!tag.isEmpty()) {
            TextView tg = Ui.text(a, tag, 11, Theme.bodyBold, Ui.BONE);
            tg.setGravity(Gravity.CENTER);
            card.addView(tg);
        }
        return card;
    }

    View objectiveRow(final int o) {
        Missions.Objective ob = b.mission.objectives.get(o);
        LinearLayout row = Ui.row(a);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout txt = Ui.col(a);
        txt.addView(Ui.text(a, ob.name, 16, Theme.names, Ui.INK));
        int v = b.value(o, b.turn);
        String vs = b.mission.advancing ? "ценность " + v + (v != ob.vp ? " (продвинут)" : "") : v + " ПО" + (ob.decays && v != ob.vp ? "  (было " + ob.vp + ")" : "");
        if (b.counter[0] || b.counter[1]) if (b.turn == b.mission.turns) vs += " · ×2 контрнаступление";
        txt.addView(Ui.text(a, vs, 12, Theme.bodyBold, Ui.INK_RED));
        row.addView(txt, Ui.weight(1));
        final int cur = b.control[o][b.turn];
        if (b.mission.advancing) {
            final int at = b.attacker;
            boolean on = cur == at;
            row.addView(Ui.button(a, on ? "✓ " + initials(b.names[at]) : "—", on ? Ui.heraldic(th[at], dp(5)) : Ui.steelButton(theme, dp(5)), on ? Theme.onColor(th[at].accent) : Ui.BONE_DIM, 13, new View.OnClickListener() {
                public void onClick(View v) {
                    boolean nowOn = b.control[o][b.turn] != at;
                    b.control[o][b.turn] = nowOn ? at : -1;
                    if (!nowOn && b.pick[b.turn] == o) b.pick[b.turn] = -1;
                    if (nowOn && b.pick[b.turn] < 0) b.pick[b.turn] = o;
                    Sfx.play(nowOn ? "bell" : "tick", 0.7f, 1f + 0.1f * o);
                    changed();
                }
            }), Ui.lpm(dp(72), dp(40), dp(3), 0, 0, 0));
            boolean star = b.pick[b.turn] == o;
            row.addView(Ui.button(a, star ? "★" : "☆", star ? Ui.brass(dp(5)) : Ui.steelButton(theme, dp(5)), star ? 0xFF1A140C : Ui.BONE_DIM, 17, new View.OnClickListener() {
                public void onClick(View v) {
                    if (b.control[o][b.turn] != at) { a.toast("Засчитать можно только маркер под контролем Атакующего"); return; }
                    b.pick[b.turn] = o;
                    Sfx.play("seal");
                    changed();
                }
            }), Ui.lpm(dp(44), dp(40), dp(3), 0, 0, 0));
            return row;
        }
        for (int s = 0; s < 3; s++) {
            final int who = s == 0 ? 0 : s == 1 ? -1 : 1;
            if (b.mission.objAttacker && who >= 0 && who != b.attacker) continue;
            boolean on = cur == who;
            String label = who < 0 ? "—" : initials(b.names[who]);
            Theme t = who < 0 ? theme : th[who];
            TextView btn = Ui.button(a, label, on ? (who < 0 ? Ui.brass(dp(5)) : Ui.heraldic(t, dp(5))) : Ui.steelButton(t, dp(5)), on ? (who < 0 ? 0xFF1A140C : Theme.onColor(t.accent)) : Ui.BONE_DIM, 13, new View.OnClickListener() {
                public void onClick(View v) {
                    b.control[o][b.turn] = who;
                    Sfx.play(who < 0 ? "tick" : "bell", 0.7f, 1f + 0.1f * o);
                    changed();
                }
            });
            btn.setPadding(dp(4), dp(2), dp(4), dp(4));
            btn.setMinHeight(dp(40));
            row.addView(btn, Ui.lpm(who < 0 ? dp(40) : dp(64), dp(40), dp(3), 0, 0, 0));
        }
        return row;
    }

    View stepper(final int value, final int lo, final int hi, final UnitScreen.Step st) {
        LinearLayout r = Ui.row(a);
        TextView minus = Ui.button(a, "−", Ui.steelButton(theme, dp(5)), Ui.GOLD, 20, new View.OnClickListener() { public void onClick(View v) { if (value > lo) st.set(value - 1); } });
        minus.setPadding(0, 0, 0, dp(3));
        r.addView(minus, Ui.lp(dp(40), dp(38)));
        TextView val = Ui.text(a, (value > 0 ? "+" : "") + value, 18, Theme.namesBlack, Ui.INK);
        val.setGravity(Gravity.CENTER);
        r.addView(val, Ui.lp(dp(40), -2));
        TextView plus = Ui.button(a, "+", Ui.steelButton(theme, dp(5)), Ui.GOLD, 20, new View.OnClickListener() { public void onClick(View v) { if (value < hi) st.set(value + 1); } });
        plus.setPadding(0, 0, 0, dp(3));
        r.addView(plus, Ui.lp(dp(40), dp(38)));
        return r;
    }

    void ruleSheet(final String r) {
        LinearLayout body = a.openSheet(r, b.mission.ru, theme);
        LinearLayout sh = Ui.col(a);
        sh.setBackground(Ui.parchment());
        sh.setPadding(dp(14), dp(10), dp(14), dp(12));
        sh.addView(Ui.text(a, Missions.ruleText(r), 15, Theme.body, Ui.INK));
        body.addView(sh);
        final String[] info = Missions.RULES.get(r);
        if (info != null) body.addView(Ui.button(a, "📖  Открыть в книге", Ui.steelButton(theme, dp(6)), Ui.GOLD, 15, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); LibraryScreen.openPage(a, info[1], Integer.parseInt(info[2]), r); }
        }), Ui.lpm(-1, dp(46), 0, dp(10), 0, 0));
        if (r.equals("Sudden Death")) body.addView(Ui.button(a, "☠  Наступила внезапная смерть", Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); suddenDeath(); }
        }), Ui.lpm(-1, dp(48), 0, dp(8), 0, 0));
    }

    void suddenDeath() {
        LinearLayout body = a.openSheet("Внезапная смерть", "У кого ОСТАЛИСЬ модели на поле?", theme);
        for (int p = 0; p < 2; p++) {
            final int pp = p;
            a.sheetItem(body, "skull", b.names[p], "Получает +3 ПО, битва заканчивается", th[p], false, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheet();
                    b.sudden[pp] = true; b.sudden[1 - pp] = false;
                    b.finished = true;
                    Sfx.play("gong");
                    b.save(a);
                    rebuild();
                    result();
                }
            });
        }
        if (b.anySudden()) a.sheetItem(body, null, "Отменить внезапную смерть", null, theme, true, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); b.sudden[0] = b.sudden[1] = false; changed(); }
        });
    }

    void nextTurn() {
        if (b.finished) { result(); return; }
        if (b.turn >= b.mission.turns && b.mission.extraTurn) {
            LinearLayout body = a.openSheet("Ещё один ход?", "После 4-го хода Защитник бросает D6: на 4+ играется 5-й ход", theme);
            final int def = 1 - b.attacker;
            body.addView(Ui.button(a, "🎲  Бросить за " + b.names[def], Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheetNow();
                    int r = rnd.nextInt(6) + 1;
                    Sfx.play("dice");
                    extraTurn(r >= 4, "Выпало " + r + ". ");
                }
            }), Ui.lpm(-1, dp(48), 0, 0, 0, dp(6)));
            a.sheetItem(body, null, "Выпало 4+ на своём кубике — играем 5-й ход", null, theme, false, new View.OnClickListener() { public void onClick(View v) { a.closeSheetNow(); extraTurn(true, ""); } });
            a.sheetItem(body, null, "Меньше 4 — битва окончена", null, theme, false, new View.OnClickListener() { public void onClick(View v) { a.closeSheetNow(); extraTurn(false, ""); } });
            return;
        }
        if (b.turn >= b.mission.turns) {
            a.confirm("Завершить битву?", "Итоговый счёт будет закреплён печатью.", "Завершить", theme, new Runnable() {
                public void run() { b.finished = true; b.save(a); rebuild(); result(); }
            });
            return;
        }
        b.turn++;
        Sfx.play("bell");
        Ui.haptic(Ui.DOUBLE);
        a.toast("Ход " + b.turn + ". " + b.names[0] + " " + b.total(0) + " : " + b.total(1) + " " + b.names[1]);
        changed();
    }

    void extraTurn(boolean yes, String pre) {
        b.mission.extraTurn = false;
        if (yes) {
            b.mission.turns++;
            b.turn++;
            a.toast(pre + "Битва продолжается: ход " + b.turn + ".");
            Sfx.play("bell");
            changed();
        } else {
            b.finished = true;
            b.save(a);
            a.toast(pre + "Битва окончена.");
            rebuild();
            result();
        }
    }

    boolean saluted;

    void result() {
        int s0 = b.total(0), s1 = b.total(1);
        final int w = s0 == s1 ? -1 : s0 > s1 ? 0 : 1;
        if (!saluted && w >= 0 && Math.max(s0, s1) >= 4 && Math.max(s0, s1) >= 2 * Math.min(s0, s1)) {
            saluted = true;
            a.h.postDelayed(new Runnable() { public void run() { Eggs.skullFireworks(a); } }, 500);
        }
        Theme t = w < 0 ? theme : th[w];
        LinearLayout body = a.openSheet(w < 0 ? "Ничья" : "Победа: " + b.names[w], b.mission.ru, t);
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.CENTER);
        for (int p = 0; p < 2; p++) {
            LinearLayout col = Ui.col(a);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(Ui.text(a, b.names[p], 15, Theme.names, Ui.BONE));
            TextView sc = Ui.text(a, String.valueOf(b.total(p)), 48, Theme.namesBlack, p == w ? th[p].accentBright() : Ui.BONE_DIM);
            col.addView(sc);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            row.addView(col, lp);
        }
        body.addView(row);
        body.addView(new Ui.Candles(a, 5, 2), Ui.lp(-1, dp(160)));
        Sfx.play("fanfare");
        a.h.postDelayed(new Runnable() { public void run() { Sfx.play("choir", 0.7f, 1f); } }, 600);
        Ui.haptic(Ui.HEAVY);
        Voice.say(w < 0 ? "The battle ends in a stalemate." : "Victory belongs to " + b.names[w] + ".");
    }

    void diceSheet() {
        final LinearLayout body = a.openSheet("Кубики", theme);
        final TextView res = Ui.text(a, "—", 40, Theme.namesBlack, Ui.BONE);
        res.setGravity(Gravity.CENTER);
        int[][] sets = {{1, 6}, {2, 6}, {3, 6}, {1, 3}};
        String[] names = {"1D6", "2D6", "3D6", "1D3"};
        LinearLayout row = Ui.row(a);
        for (int i = 0; i < sets.length; i++) {
            final int n = sets[i][0], s = sets[i][1];
            TextView btn = Ui.button(a, names[i], Ui.steelButton(theme, dp(6)), Ui.BONE, 16, new View.OnClickListener() {
                public void onClick(View v) {
                    Sfx.play("dice");
                    Ui.haptic(Ui.DOUBLE);
                    StringBuilder sb = new StringBuilder();
                    int sum = 0;
                    for (int k = 0; k < n; k++) { int r = rnd.nextInt(s) + 1; sum += r; if (k > 0) sb.append(" + "); sb.append(r); }
                    res.setText(n > 1 ? sb + " = " + sum : String.valueOf(sum));
                    res.setScaleX(0.4f);
                    res.setScaleY(0.4f);
                    res.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                }
            });
            LinearLayout.LayoutParams lp = Ui.lpm(0, dp(48), dp(2), 0, dp(2), 0);
            lp.weight = 1;
            row.addView(btn, lp);
        }
        body.addView(row);
        body.addView(res, Ui.lpm(-1, -2, 0, dp(14), 0, dp(10)));
    }

    void menu() {
        LinearLayout body = a.openSheet("Битва", theme);
        a.sheetItem(body, "banner", "Итоги", "Показать текущий счёт", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); a.h.postDelayed(new Runnable() { public void run() { result(); } }, 200); }
        });
        a.sheetItem(body, "skull", "Внезапная смерть", "У одного из игроков не осталось моделей на поле", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); a.h.postDelayed(new Runnable() { public void run() { suddenDeath(); } }, 200); }
        });
        if (b.mission.book != null && b.mission.page >= 0) a.sheetItem(body, "book", "Миссия в книге", b.mission.name, theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); LibraryScreen.openPage(a, b.mission.book, b.mission.page, b.mission.name); }
        });
        if (b.finished) a.sheetItem(body, "sword", "Продолжить подсчёт", "Снять отметку о завершении", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); b.finished = false; changed(); }
        });
        a.sheetItem(body, "skull", "Закрыть битву", "Удалить сохранённый подсчёт", theme, true, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                a.confirm("Закрыть битву?", "Счёт будет удалён.", "Закрыть", theme, new Runnable() {
                    public void run() { Battle.delete(a); a.back(); }
                });
            }
        });
    }

    /** Черепа-отметки ходов. */
    static final class TurnPips extends View {
        final int turn, total;
        final boolean finished;
        TurnPips(android.content.Context c, int turn, int total, boolean finished) { super(c); this.turn = turn; this.total = total; this.finished = finished; }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), step = dp(26), start = w / 2 - step * (total - 1) / 2;
            for (int i = 1; i <= total; i++) {
                float x = start + (i - 1) * step;
                int col = i < turn || finished ? Ui.GOLD : i == turn ? 0xFFFFE6A0 : 0x55D2AE62;
                Ui.drawSkull(c, x, h / 2, h * 0.38f, col);
            }
        }
    }
}
