package com.medoedy.liber;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Smart;

import java.util.List;

import static com.medoedy.liber.Ui.dp;

/** «Улучши мой ростер»: точечные замены под выбранного противника, каждая применяется одним нажатием. */
final class ImproveScreen extends Screen {
    final RosterScreen rs;
    final Roster r;
    final Smart.Params p = new Smart.Params();
    Smart.Improve result;
    boolean busy;

    ImproveScreen(MainActivity a, RosterScreen rs) {
        super(a);
        this.rs = rs;
        this.r = rs.r;
        theme = rs.theme;
        p.myCat = r.catalogueId;
        p.limit = r.limit;
        p.mode = a.prefs.getInt("improve_mode", 1);
        String en = a.prefs.getString("improve_enemy", null);
        if (en != null && Store.data.cats.containsKey(en)) p.enemyCat = en;
    }

    @Override
    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Улучшить ростер", r.name, theme, true));
        LinearLayout intro = Ui.row(a);
        intro.addView(new Ui.ServoSkull(a), Ui.lp(dp(64), dp(84)));
        TextView it = Ui.text(a, "Ищу точечные замены: какой юнит поменять на какой, чтобы армия стала сильнее против выбранного врага — за те же очки. Каждую замену можно применить одним нажатием.", 14, Theme.body, Ui.INK);
        it.setBackground(new Ui.Bubble());
        it.setPadding(dp(22), dp(9), dp(12), dp(11));
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.addView(it, Ui.weight(1));
        c.addView(intro, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));

        c.addView(a.label("ПРОТИВНИК", Ui.GOLD));
        Data.Cat ec = p.enemyCat == null ? null : Store.data.cats.get(p.enemyCat);
        TextView en = Ui.button(a, (ec == null ? "Не выбран" : SmartScreen.short_(ec.name)) + "  ›", Ui.steelButton(theme, dp(8)), Ui.BONE, 17, new View.OnClickListener() {
            public void onClick(View v) { pickEnemy(); }
        });
        c.addView(en, Ui.lpm(-1, dp(50), 0, dp(2), 0, dp(6)));
        c.addView(a.label("ИЗ ЧЕГО МЕНЯТЬ", Ui.GOLD));
        Ui.Flow modes = new Ui.Flow(a);
        modes.gap = dp(6);
        String[] ml = {"Только моё", "Моё + прокси", "Можно новое"};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            boolean on = p.mode == i;
            TextView t = Ui.text(a, ml[i], 14, Theme.bodyBold, on ? Theme.onColor(theme.accent) : Ui.BONE);
            t.setBackground(on ? Ui.heraldic(theme, dp(5)) : Ui.steelButton(theme, dp(5)));
            t.setPadding(dp(12), dp(8), dp(12), dp(9));
            t.setClickable(true);
            Ui.press(t);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { p.mode = m; a.prefs.edit().putInt("improve_mode", m).apply(); result = null; rebuild(); } });
            modes.addView(t);
        }
        c.addView(modes, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        c.addView(Ui.button(a, busy ? "Когитатор считает…" : "🧠  Найти улучшения", Ui.brass(dp(8)), 0xFF1A140C, 18, new View.OnClickListener() {
            public void onClick(View v) { compute(); }
        }), Ui.lpm(-1, dp(54), 0, dp(10), 0, dp(8)));

        if (result != null) showResult(c);
        f.addView(a.scroll(c));
        return f;
    }

    void pickEnemy() {
        LinearLayout body = a.openSheet("Противник", "Под кого улучшаем", theme);
        for (final Data.Cat cat : Store.data.playable()) {
            Theme t = Theme.forName(cat.name);
            LinearLayout itv = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, SmartScreen.short_(cat.name), null, t, false, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheet();
                    p.enemyCat = cat.id;
                    a.prefs.edit().putString("improve_enemy", cat.id).apply();
                    result = null;
                    rebuild();
                }
            });
            if (!t.numeral.isEmpty()) {
                Ui.Hex hx = new Ui.Hex(a, t, null);
                hx.label = t.numeral;
                itv.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
            }
        }
    }

    void compute() {
        if (busy) return;
        if (p.enemyCat == null) { a.toast("Выберите противника"); pickEnemy(); return; }
        busy = true;
        Sfx.play("servo");
        Sfx.play("hum", 0.6f, 1f);
        rebuild();
        p.minis = Minis.forSmart(a);
        final List<Roster> all = a.store.rosters();
        new Thread(new Runnable() {
            public void run() {
                final Smart.Improve im = new Smart(Store.data).improve(all, p, r);
                a.h.post(new Runnable() {
                    public void run() {
                        busy = false;
                        result = im;
                        if (im.error != null) { a.toast(im.error); Sfx.play("error"); } else Sfx.play("seal");
                        rebuild();
                    }
                });
            }
        }, "improve").start();
    }

    void showResult(LinearLayout c) {
        if (result.error != null) {
            c.addView(Ui.text(a, result.error, 15, Theme.body, Ui.RED_BAD));
            return;
        }
        c.addView(a.ribbonTitle(result.swaps.isEmpty() ? "ВСЁ И ТАК ХОРОШО" : "ЗАМЕНЫ: " + result.swaps.size(), theme));
        for (final Smart.Swap sw : result.swaps) {
            LinearLayout card = Ui.col(a);
            card.setBackground(Ui.parchment());
            card.setPadding(dp(14), dp(10), dp(14), dp(12));
            LinearLayout fromTo = Ui.row(a);
            LinearLayout l = Ui.col(a);
            l.addView(Ui.text(a, "УБРАТЬ", 10, Theme.bodyBold, Ui.INK_DIM));
            l.addView(Ui.text(a, sw.from, 15, Theme.names, Ui.INK));
            l.addView(Ui.text(a, Engine.fmt(sw.fromPts) + " оч.", 12, Theme.bodyBold, Ui.INK_DIM));
            fromTo.addView(l, Ui.weight(1));
            fromTo.addView(Ui.text(a, "→", 26, Theme.namesBlack, theme.accent), Ui.lpm(-2, -2, dp(6), 0, dp(6), 0));
            LinearLayout rr = Ui.col(a);
            rr.addView(Ui.text(a, "ПОСТАВИТЬ", 10, Theme.bodyBold, Ui.INK_DIM));
            rr.addView(Ui.text(a, sw.to, 15, Theme.names, Ui.INK));
            String st = sw.status == Smart.OWNED ? "✓ есть у вас" : sw.status == Smart.PROXY ? "≈ прокси из «" + sw.proxyOf + "»" : "✦ нужно собрать";
            rr.addView(Ui.text(a, Engine.fmt(sw.toPts) + " оч. · " + st, 12, Theme.bodyBold, sw.status == Smart.OWNED ? 0xFF3E6A22 : sw.status == Smart.PROXY ? 0xFF8A5A10 : Ui.INK_RED));
            fromTo.addView(rr, Ui.weight(1));
            card.addView(fromTo);
            if (sw.text != null && !sw.text.isEmpty()) {
                TextView g = Ui.text(a, sw.text, 13, Theme.bodyBold, 0xFF2A5A2A);
                g.setPadding(0, dp(8), 0, 0);
                card.addView(g);
            }
            TextView sc = Ui.text(a, "Армия сильнее против этого врага примерно на " + Math.max(1, Math.round(sw.gain * 100)) + "%", 12, Theme.body, Ui.INK_DIM);
            sc.setPadding(0, dp(2), 0, 0);
            card.addView(sc);
            card.addView(Ui.button(a, "Применить", Ui.brass(dp(6)), 0xFF1A140C, 15, new View.OnClickListener() {
                public void onClick(View v) { apply(sw); }
            }), Ui.lpm(-1, dp(42), 0, dp(8), 0, 0));
            c.addView(card, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
        if (!result.reasons.isEmpty()) {
            LinearLayout why = Ui.col(a);
            why.setBackground(Ui.parchment());
            why.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView h = Ui.text(a, "ЧТО УЧТЕНО", 11, Theme.bodyBold, Ui.INK_RED);
            h.setLetterSpacing(0.12f);
            why.addView(h);
            for (String s : result.reasons) why.addView(Ui.text(a, "⚜  " + s, 13, Theme.body, Ui.INK), Ui.lpm(-1, -2, 0, dp(4), 0, 0));
            c.addView(why, Ui.lpm(-1, -2, 0, dp(6), 0, dp(10)));
        }
    }

    void apply(Smart.Swap sw) {
        Roster nr = Roster.fromJson(sw.after);
        nr.id = r.id;
        nr.name = r.name;
        nr.updated = System.currentTimeMillis();
        a.store.save(nr);
        Sfx.play("seal");
        Ui.haptic(Ui.DOUBLE);
        a.toast("Заменено: " + sw.from + " → " + sw.to);
        a.back();
        a.replaceTop(new RosterScreen(a, nr));
    }
}
