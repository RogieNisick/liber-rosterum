package com.medoedy.liber;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.Issue;
import com.medoedy.liber.engine.Roster;

import java.util.List;

import static com.medoedy.liber.Ui.dp;

/** Архив ростеров. */
final class RosterListScreen extends Screen {
    RosterListScreen(MainActivity a) { super(a); }

    @Override
    void refresh() { rebuild(); }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Ростеры армий", "Архив ваших войск", theme, true));
        LinearLayout tiles = Ui.row(a);
        LinearLayout.LayoutParams t1 = Ui.lpm(0, dp(118), 0, 0, dp(5), 0);
        t1.weight = 1;
        tiles.addView(tile("plus", "Новый ростер", "Собрать вручную", Ui.brass(dp(10)), 0xFF1A140C, 0xFF3A2A10, new View.OnClickListener() {
            public void onClick(View v) { a.show(new NewRosterScreen(a)); }
        }), t1);
        LinearLayout.LayoutParams t2 = Ui.lpm(0, dp(118), dp(5), 0, 0, 0);
        t2.weight = 1;
        int on = Theme.onColor(theme.accent);
        tiles.addView(tile("brain", "Умный ростер", "Под противника и миссию", Ui.heraldic(theme, dp(10)), on, on, new View.OnClickListener() {
            public void onClick(View v) { a.show(new SmartScreen(a)); }
        }), t2);
        c.addView(tiles, Ui.lpm(-1, -2, 0, dp(4), 0, dp(8)));
        c.addView(Ui.button(a, "⇩  Импорт: файл BattleScribe или текст", Ui.steelButton(theme, dp(8)), Ui.BONE, 16, new View.OnClickListener() {
            public void onClick(View v) {
                Ui.haptic(Ui.CLICK);
                LinearLayout body = a.openSheet("Импорт ростера", theme);
                a.sheetItem(body, "scroll", "Файл BattleScribe / New Recruit", ".ros или .rosz", theme, false, new View.OnClickListener() {
                    public void onClick(View v) { a.closeSheetNow(); a.pickRosterFile(); }
                });
                a.sheetItem(body, "book", "Текст из буфера обмена", "Текстовый список, которым поделились из Liber Rosterum", theme, false, new View.OnClickListener() {
                    public void onClick(View v) { a.closeSheetNow(); importClipboard(); }
                });
            }
        }), Ui.lpm(-1, dp(50), 0, 0, 0, dp(8)));
        List<Roster> rs = a.store.rosters();
        if (rs.isEmpty()) {
            TextView e = Ui.text(a, "Архив пуст. Создайте первый ростер — выберите легион или фракцию и лимит очков — или импортируйте свой из BattleScribe / New Recruit.", 15, Theme.body, Ui.BONE_DIM);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(20), dp(6), dp(20), dp(30));
            LinearLayout sk = Ui.row(a);
            sk.setGravity(Gravity.CENTER);
            sk.addView(new Ui.ServoSkull(a), Ui.lp(dp(110), dp(140)));
            c.addView(sk, Ui.lpm(-1, -2, 0, dp(20), 0, 0));
            c.addView(e);
        } else {
            for (Roster r : rs) c.addView(card(r), Ui.lpm(-1, -2, 0, dp(5), 0, dp(5)));
        }
        f.addView(a.scroll(c));
        return f;
    }

    /** Плитка-кнопка: крупный символ, название, подпись. */
    View tile(String glyph, String title, String sub, android.graphics.drawable.Drawable bg, int fg, int glyphColor, final View.OnClickListener l) {
        LinearLayout b = Ui.col(a);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.addView(new RosterScreen.GlyphView(a, glyph, glyphColor), Ui.lp(dp(46), dp(46)));
        TextView tt = Ui.text(a, title, 17, Theme.namesBlack, fg);
        tt.setGravity(Gravity.CENTER);
        b.addView(tt, Ui.lpm(-2, -2, 0, dp(6), 0, 0));
        TextView st = Ui.text(a, sub, 12, Theme.body, fg);
        st.setGravity(Gravity.CENTER);
        st.setAlpha(0.8f);
        b.addView(st);
        b.setClickable(true);
        Ui.press(b);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("click"); l.onClick(v); }
        });
        return b;
    }

    View card(final Roster r) {
        Data d = Store.data;
        Data.Cat cat = d.cats.get(r.catalogueId);
        String cname = cat == null ? "Неизвестная фракция" : cat.name;
        final Theme t = Theme.forName(cname);
        Engine e = new Engine(d, r);
        double p = e.rosterCost(d.pointsId);
        int errs = 0;
        for (Issue i : e.validate()) if (i.error) errs++;
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.states(new Ui.Steel(0xF226302B, 0xF2121815, Theme.mix(t.trim, Ui.GOLD, 0.6f), t.glow, dp(10), true, false),
                new Ui.Steel(0xF2343F39, 0xF21A211E, Theme.mix(t.trim, Ui.GOLD, 0.6f), t.glow, dp(10), true, true)));
        card.setPadding(dp(12), dp(12), dp(14), dp(12));
        Ui.Hex hx = new Ui.Hex(a, t, t.numeral.isEmpty() ? "banner" : null);
        hx.label = t.numeral.isEmpty() ? null : t.numeral;
        card.addView(hx, Ui.lpm(dp(56), dp(56), 0, 0, dp(12), 0));
        LinearLayout mid = Ui.col(a);
        TextView n = Ui.title(a, r.name, 20, Ui.BONE);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(n);
        TextView s = Ui.text(a, cname, 14, Theme.body, t.accentBright());
        s.setSingleLine(true);
        mid.addView(s);
        mid.addView(Ui.text(a, Engine.fmt(p) + " / " + Engine.fmt(r.limit) + " очков", 14, Theme.bodyBold, Ui.GOLD));
        card.addView(mid, Ui.weight(1));
        Ui.Seal seal = new Ui.Seal(a);
        seal.set(errs);
        card.addView(seal, Ui.lp(dp(46), dp(46)));
        card.setClickable(true);
        Ui.press(card);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("clunk"); Data.Cat cc = Store.data.cats.get(r.catalogueId); if (cc != null) Voice.say(Voice.proclamation(cc.name)); a.show(new RosterScreen(a, r)); }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { Ui.haptic(Ui.HEAVY); menu(r, t, v); return true; }
        });
        return card;
    }

    void importClipboard() {
        String t = null;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) t = String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(a));
        } catch (Exception ignored) { }
        if (t == null || !com.medoedy.liber.engine.TextImport.looksLike(t)) { a.toast("В буфере нет текстового ростера. Скопируйте текст, которым поделились из Liber Rosterum."); return; }
        com.medoedy.liber.engine.TextImport ti = new com.medoedy.liber.engine.TextImport(Store.data).parse(t);
        if (ti.roster == null) { a.toast(ti.warnings.isEmpty() ? "Не удалось прочитать ростер." : ti.warnings.get(0)); return; }
        ti.roster.updated = System.currentTimeMillis();
        a.store.save(ti.roster);
        Sfx.play("seal");
        a.toast(ti.warnings.isEmpty() ? "Ростер «" + ti.roster.name + "» импортирован" : "Импортирован, но: " + ti.warnings.get(0) + (ti.warnings.size() > 1 ? " (и ещё " + (ti.warnings.size() - 1) + ")" : ""));
        a.show(new RosterScreen(a, ti.roster));
    }

    void menu(final Roster r, final Theme t, final View card) {
        LinearLayout body = a.openSheet(r.name, t);
        a.sheetItem(body, "banner", "Дублировать", "Копия ростера", t, false, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                Roster copy = Roster.fromJson(r.toJson());
                copy.id = null;
                copy.name = r.name + " (копия)";
                a.store.save(copy);
                Sfx.play("seal");
                rebuild();
            }
        });
        a.sheetItem(body, "trash", "Удалить ростер", null, t, true, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                a.confirm("Удалить «" + r.name + "»?", "Это действие нельзя отменить.", "Удалить", t, new Runnable() {
                    public void run() {
                        a.h.postDelayed(new Runnable() {
                            public void run() { Eggs.burn(a, card, new Runnable() { public void run() { a.store.delete(r); rebuild(); } }); }
                        }, 250);
                    }
                });
            }
        });
    }
}
