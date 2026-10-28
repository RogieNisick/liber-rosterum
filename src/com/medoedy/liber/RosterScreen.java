package com.medoedy.liber;

import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.CatSlot;
import com.medoedy.liber.engine.Engine.ForceOpt;
import com.medoedy.liber.engine.Engine.Issue;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

/** Ростер: сводка, детачменты с иконками и слотами, юниты. */
final class RosterScreen extends Screen {
    final Roster r;
    final Engine e;
    final Data d;
    List<Issue> issues = new ArrayList<Issue>();
    List<Problems.P> problems = new ArrayList<Problems.P>();
    final Map<Force, View> cards = new java.util.HashMap<Force, View>();
    android.widget.ScrollView sv;
    final List<View> shelves = new ArrayList<View>();
    Shrine.Keeper keeper;

    RosterScreen(MainActivity a, Roster r) {
        super(a);
        this.r = r;
        d = Store.data;
        e = new Engine(d, r);
        Data.Cat cat = d.cats.get(r.catalogueId);
        theme = Theme.forName(cat == null ? null : cat.name);
        try { if (e.reslot()) a.store.save(r); } catch (Throwable ignored) { }
    }

    @Override
    void refresh() { e.changed(); rebuild(); }

    void changed() { e.changed(); a.store.save(r); rebuild(); }

    View build() {
        Shrine.Frame frame = new Shrine.Frame(a);
        shelves.clear();
        if (keeper != null) a.h.removeCallbacks(keeper.idle);
        LinearLayout c = a.column();
        Data.Cat cat = d.cats.get(r.catalogueId);
        LinearLayout hd = a.header(r.name, cat == null ? "?" : cat.name, theme, true);
        LinearLayout hrow = (LinearLayout) hd.getChildAt(0);
        TextView menu = Ui.button(a, "⋮", Ui.steelButton(theme, dp(8)), Ui.GOLD, 22, new View.OnClickListener() {
            public void onClick(View v) { actions(); }
        });
        menu.setPadding(0, 0, 0, dp(2));
        hrow.addView(Ui.trashButton(a, new View.OnClickListener() {
            public void onClick(View v) {
                a.confirm("Удалить «" + r.name + "»?", "Это действие нельзя отменить.", "Удалить", theme, new Runnable() {
                    public void run() {
                        a.h.postDelayed(new Runnable() {
                            public void run() { Eggs.burn(a, view, new Runnable() { public void run() { a.store.delete(r); a.back(); } }); }
                        }, 250);
                    }
                });
            }
        }), Ui.lpm(dp(38), dp(38), dp(6), 0, dp(6), 0));
        hrow.addView(menu, Ui.lp(dp(46), dp(46)));
        c.addView(hd);

        double p = e.rosterCost(d.pointsId);
        issues = e.validate();
        int errN = 0;
        for (Issue i0 : issues) if (i0.error) errN++;
        Eggs.rosterCost(a, r.id, p, r.limit, errN);
        problems.clear();
        cards.clear();
        for (Issue i : issues) problems.add(Problems.explain(e, i, r.limit));
        int errs = 0;
        for (Issue i : issues) if (i.error) errs++;
        LinearLayout sum = Ui.row(a);
        sum.setBackground(Ui.steel(theme));
        sum.setPadding(dp(14), dp(12), dp(12), dp(12));
        LinearLayout left = Ui.col(a);
        LinearLayout pr = Ui.row(a);
        TextView big = Ui.text(a, Engine.fmt(p), 30, Theme.namesBlack, p > r.limit ? Ui.RED_BAD : Ui.BONE);
        big.setShadowLayer(dp(6), 0, dp(1), 0xFF000000);
        pr.addView(big);
        pr.addView(Ui.text(a, "  / " + Engine.fmt(r.limit) + " очков", 16, Theme.bodyBold, Ui.GOLD));
        left.addView(pr);
        Ui.Gauge g = new Ui.Gauge(a, theme);
        g.set(p, r.limit);
        left.addView(g, Ui.lpm(-1, dp(12), 0, dp(6), 0, dp(4)));
        left.addView(Ui.text(a, errs == 0 ? "Ростер верен — печать чиста" : errs + " " + MainActivity.plural(errs, "ошибка", "ошибки", "ошибок") + " — коснитесь печати", 13, Theme.body, errs == 0 ? Ui.GREEN_OK : Ui.RED_BAD));
        sum.setClickable(errs > 0);
        if (errs > 0) sum.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); showIssues(); } });
        sum.addView(left, Ui.weight(1));
        Ui.Seal seal = new Ui.Seal(a);
        seal.set(errs);
        seal.setClickable(true);
        seal.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("seal"); showIssues(); } });
        Ui.press(seal);
        sum.addView(seal, Ui.lpm(dp(60), dp(60), dp(10), 0, 0, 0));
        c.addView(sum, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        for (Problems.P pr0 : problems) if (pr0.force == null && pr0.unit == null) c.addView(banner(pr0), Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));

        // переключатель вида: список / схема как в книге
        final boolean chart = OrgChart.on(a);
        LinearLayout seg = Ui.row(a);
        String[] lbl = {"☰  Список", "⚜  Схема как в книге"};
        for (int i = 0; i < 2; i++) {
            final boolean want = i == 1;
            boolean onNow = want == chart;
            TextView t = Ui.text(a, lbl[i], 13, Theme.bodyBold, onNow ? Theme.onColor(theme.accent) : Ui.BONE_DIM);
            t.setGravity(android.view.Gravity.CENTER);
            t.setBackground(onNow ? Ui.heraldic(theme, dp(5)) : Ui.steelButton(theme, dp(5)));
            t.setPadding(0, dp(8), 0, dp(9));
            t.setClickable(true);
            Ui.press(t);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (want == OrgChart.on(a)) return; Ui.haptic(Ui.CLICK); Sfx.play("clunk"); OrgChart.set(a, want); rebuild(); }
            });
            LinearLayout.LayoutParams lp = Ui.lpm(0, -2, i == 0 ? 0 : dp(3), 0, i == 0 ? dp(3) : 0, 0);
            lp.weight = 1;
            seg.addView(t, lp);
        }
        c.addView(seg, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        // шпаргалка и улучшение — под рукой
        LinearLayout tools = Ui.row(a);
        TextView cheat = Ui.button(a, "📜  Шпаргалка", Ui.brass(dp(6)), 0xFF1A140C, 14, new View.OnClickListener() {
            public void onClick(View v) { a.show(new CheatScreen(a, RosterScreen.this)); }
        });
        LinearLayout.LayoutParams t1 = Ui.lpm(0, dp(42), 0, 0, dp(3), 0);
        t1.weight = 1;
        tools.addView(cheat, t1);
        TextView imp = Ui.button(a, "🧠  Улучшить", Ui.steelButton(theme, dp(6)), Ui.BONE, 14, new View.OnClickListener() {
            public void onClick(View v) { a.show(new ImproveScreen(a, RosterScreen.this)); }
        });
        LinearLayout.LayoutParams t2 = Ui.lpm(0, dp(42), dp(3), 0, 0, 0);
        t2.weight = 1;
        tools.addView(imp, t2);
        c.addView(tools, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));

        if (chart) {
            for (Problems.P pr0 : problems) if (pr0.force != null && pr0.unit == null) c.addView(banner(pr0), Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
            OrgChart.build(this, c);
        } else for (Force f : r.forces) addForce(c, f, 0);
        c.addView(Ui.button(a, "＋  Союзники и прочие силы", Ui.steelButton(theme, dp(8)), Ui.BONE, 16, new View.OnClickListener() {
            public void onClick(View v) { addRootForce(); }
        }), Ui.lpm(-1, dp(50), 0, dp(14), 0, 0));
        a.h.postDelayed(new Runnable() { public void run() { if (a.stack.peek() == RosterScreen.this) Guide.tip(a, "roster", "Глеб, это твой ростер. Нажми на юнит — откроется снаряжение; долгое нажатие — дублировать или удалить. Печать вверху показывает число ошибок: нажми её, и я объясню, что исправить. «＋ Детачмент» — добавить детачмент, в том числе союзников другой фракции."); } }, 700);
        sv = a.scroll(c);
        frame.addView(sv);
        keeper = new Shrine.Keeper(a, this, frame, shelves);
        return frame;
    }

    /** Красная плашка с проблемой (как в New Recruit — прямо у места ошибки). */
    View banner(final Problems.P p) {
        LinearLayout b = Ui.row(a);
        b.setBackground(Ui.states(new Ui.Steel(p.error ? 0xFF3A1414 : 0xFF3A3014, 0xFF1A0C0C, p.error ? 0xFF8A2A2A : 0xFF8A7A2A, 0, dp(6), false, false),
                new Ui.Steel(p.error ? 0xFF4A1A1A : 0xFF4A3A1A, 0xFF1A0C0C, p.error ? 0xFFB03A3A : 0xFFB09A3A, 0, dp(6), false, true)));
        b.setPadding(dp(10), dp(7), dp(10), dp(8));
        b.addView(Ui.text(a, p.error ? "✖" : "!", 16, Theme.bodyBold, p.error ? Ui.RED_BAD : Ui.GOLD), Ui.lpm(-2, -2, 0, 0, dp(8), 0));
        LinearLayout t = Ui.col(a);
        t.addView(Ui.text(a, p.title, 14, Theme.bodyBold, Ui.dark ? 0xFFFFD8CC : Ui.INK_RED));
        if (p.fix != null) {
            TextView fx = Ui.text(a, p.fix, 12, Theme.body, Ui.BONE_DIM);
            fx.setMaxLines(2);
            fx.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(fx);
        }
        b.addView(t, Ui.weight(1));
        b.addView(Ui.text(a, "›", 20, Theme.bodyBold, Ui.GOLD), Ui.lpm(-2, -2, dp(6), 0, 0, 0));
        b.setClickable(true);
        Ui.press(b);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); showProblem(p); } });
        return b;
    }

    /** Прокрутить к детачменту и мигнуть им. */
    void goTo(Force f) {
        final View card = cards.get(f);
        if (card == null || sv == null) return;
        int y = 0;
        View v = card;
        while (v != null && v != sv) { y += v.getTop(); v = (View) v.getParent(); }
        sv.smoothScrollTo(0, Math.max(0, y - dp(12)));
        card.animate().alpha(0.35f).setDuration(160).withEndAction(new Runnable() {
            public void run() { card.animate().alpha(1f).setDuration(260).withEndAction(new Runnable() {
                public void run() { card.animate().alpha(0.5f).setDuration(140).withEndAction(new Runnable() {
                    public void run() { card.animate().alpha(1f).setDuration(240).start(); } }).start(); } }).start(); }
        }).start();
        Ui.haptic(Ui.TICK);
    }

    void act(final Problems.P p, int act) {
        a.closeSheetNow();
        switch (act) {
            case Problems.GO_FORCE: if (p.force != null) goTo(p.force); break;
            case Problems.OPEN_UNIT: if (p.unit != null) a.show(new UnitScreen(a, this, Problems.unitRoot(p.unit))); break;
            case Problems.ADD_UNIT: if (p.force != null && !isContainer(p.force)) a.show(new PickerScreen(a, this, p.force)); else if (p.force != null) goTo(p.force); break;
            case Problems.ADD_DET: if (p.force != null) addChildForce(p.force); break;
            case Problems.EDIT_LIMIT: rename(); break;
            default: break;
        }
    }

    /** Подробная карточка проблемы: что не так, почему и что делать. */
    void showProblem(final Problems.P p) {
        LinearLayout body = a.openSheet(p.error ? "Ошибка ростера" : "Предупреждение", p.group, theme);
        body.addView(problemCard(p, false));
    }

    View problemCard(final Problems.P p, boolean compact) {
        LinearLayout sheet = Ui.col(a);
        sheet.setBackground(Ui.parchment());
        sheet.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout hr = Ui.row(a);
        hr.addView(Ui.text(a, p.error ? "✖ " : "! ", 18, Theme.bodyBold, p.error ? Ui.INK_RED : 0xFF8A6A10));
        hr.addView(Ui.text(a, p.title, 17, Theme.names, Ui.INK), Ui.weight(1));
        sheet.addView(hr);
        if (p.detail != null) {
            TextView dt = Ui.text(a, p.detail, 14, Theme.body, Ui.INK);
            dt.setPadding(0, dp(6), 0, 0);
            sheet.addView(dt);
        }
        if (p.fix != null) {
            TextView fh = Ui.text(a, "КАК ИСПРАВИТЬ", 11, Theme.bodyBold, Ui.INK_RED);
            fh.setLetterSpacing(0.1f);
            fh.setPadding(0, dp(10), 0, dp(2));
            sheet.addView(fh);
            sheet.addView(Ui.text(a, p.fix, 14, Theme.body, Ui.INK));
        }
        LinearLayout btns = Ui.row(a);
        if (p.button != null && p.act != Problems.NONE) {
            TextView b1 = Ui.button(a, p.button, Ui.brass(dp(6)), 0xFF1A140C, 15, new View.OnClickListener() { public void onClick(View v) { act(p, p.act); } });
            LinearLayout.LayoutParams lp = Ui.lpm(0, dp(42), 0, 0, dp(6), 0);
            lp.weight = 1;
            btns.addView(b1, lp);
        }
        if (p.button2 != null) {
            TextView b2 = Ui.button(a, p.button2, Ui.steelButton(theme, dp(6)), Ui.BONE, 15, new View.OnClickListener() { public void onClick(View v) { act(p, p.act2); } });
            LinearLayout.LayoutParams lp = Ui.lpm(0, dp(42), 0, 0, 0, 0);
            lp.weight = 1;
            btns.addView(b2, lp);
        }
        if (btns.getChildCount() > 0) sheet.addView(btns, Ui.lpm(-1, -2, 0, dp(12), 0, 0));
        return sheet;
    }

    boolean isContainer(Force f) {
        Map<String, Object> fe = d.forces.get(f.entryId);
        return !Data.list(fe, "forceEntries").isEmpty() || !Data.list(fe, "forceEntryLinks").isEmpty();
    }

    void addForce(LinearLayout c, final Force f, int depth) {
        final Engine.Eff fe = e.effForce(f);
        Data.Cat fc = d.cats.get(f.catalogueId);
        boolean container = isContainer(f);
        LinearLayout card = Ui.col(a);
        card.setBackground(container ? Ui.steelFlat(theme, dp(10)) : Ui.steel(theme));
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout head = Ui.row(a);
        List<CatSlot> slots = e.slots(f);
        Ui.Hex hx = new Ui.Hex(a, theme, container ? "banner" : Units.detachmentGlyph(fe.name, slots));
        head.addView(hx, Ui.lpm(dp(48), dp(48), 0, 0, dp(10), 0));
        LinearLayout ht = Ui.col(a);
        TextView fn = Ui.title(a, container ? (f.catalogueId != null && !f.catalogueId.equals(r.catalogueId) ? "Союзники · " + shortName(fc == null ? "" : fc.name) : "Армия") : fe.name, 19, Ui.BONE);
        fn.setShadowLayer(dp(4), 0, dp(1), 0xFF000000);
        ht.addView(fn);
        String sub = container ? fe.name : Units.detachmentType(fe.name);
        String ptsS = MainActivity.pts(e.forceCost(f, d.pointsId));
        sub = sub == null ? ptsS : sub + " · " + ptsS;
        if (f.parent != null && fc != null && !f.catalogueId.equals(f.parent.catalogueId)) sub += " · " + fc.name;
        ht.addView(Ui.text(a, sub, 13, Theme.body, Ui.GOLD));
        head.addView(ht, Ui.weight(1));
        if (f.parent != null || r.forces.size() > 1) {
            View del = Ui.trashButton(a, new View.OnClickListener() {
                public void onClick(View v) {
                    a.confirm("Распустить «" + fe.name + "»?", "Все юниты в нём будут удалены.", "Распустить", theme, new Runnable() {
                        public void run() { e.removeForce(f); changed(); }
                    });
                }
            });
            LinearLayout.LayoutParams dl = Ui.lp(dp(32), dp(32));
            dl.gravity = android.view.Gravity.TOP;
            head.addView(del, dl);
        }
        card.addView(head);
        cards.put(f, card);
        if (!container) shelves.add(card);
        for (Problems.P pr0 : problems) if (pr0.force == f && pr0.unit == null) card.addView(banner(pr0), Ui.lpm(-1, -2, 0, dp(8), 0, 0));

        if (!container) {
            LinearLayout chips = Ui.row(a);
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            for (CatSlot s : slots) {
                if (s.hidden || (s.max == 0 && s.count == 0) || (s.max < 0 && s.count == 0 && s.min == 0)) continue;
                boolean bad = (s.max >= 0 && s.count > s.max) || s.count < s.min;
                String lim = s.max < 0 ? "" : "/" + Engine.fmt(s.max);
                LinearLayout chip = Ui.row(a);
                chip.setBackground(new Ui.Steel(bad ? 0xFF3A1414 : 0xFF1C2420, 0xFF0E1311, bad ? 0xFF8A2A2A : Ui.GOLD_DIM, 0, dp(5), false, false));
                chip.setPadding(dp(6), dp(3), dp(10), dp(4));
                View gl = new GlyphView(a, Glyph.forCategory(s.name), bad ? Ui.RED_BAD : Ui.GOLD);
                chip.addView(gl, Ui.lpm(dp(20), dp(20), 0, 0, dp(5), 0));
                chip.addView(Ui.text(a, Units.slotRu(s.name) + "  " + Engine.fmt(s.count) + lim, 13, Theme.bodyBold, bad ? Ui.RED_BAD : (s.count > 0 ? Ui.BONE : Ui.BONE_DIM)));
                chips.addView(chip, Ui.lpm(-2, -2, 0, 0, dp(6), 0));
            }
            hs.addView(chips);
            card.addView(hs, Ui.lpm(-1, -2, 0, dp(10), 0, dp(4)));
        }

        Map<String, List<Sel>> bySlot = new LinkedHashMap<String, List<Sel>>();
        for (Sel s : f.sels) {
            String k = e.effSel(s).primary;
            if (k == null) k = "";
            if (!bySlot.containsKey(k)) bySlot.put(k, new ArrayList<Sel>());
            bySlot.get(k).add(s);
        }
        for (Map.Entry<String, List<Sel>> en : bySlot.entrySet()) {
            if (!container) {
                TextView sl = Ui.title(a, Units.slotRu(d.categoryName(en.getKey())), 14, theme.accentBright());
                sl.setPadding(dp(2), dp(8), 0, dp(2));
                card.addView(sl);
            }
            for (Sel s : en.getValue()) card.addView(unitRow(s), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }

        if (!container) {
            TextView addU = Ui.button(a, "＋  Юнит", Ui.heraldic(theme, dp(8)), Theme.onColor(theme.accent), 17, new View.OnClickListener() {
                public void onClick(View v) { a.show(new PickerScreen(a, RosterScreen.this, f)); }
            });
            card.addView(addU, Ui.lpm(-1, dp(48), 0, dp(8), 0, 0));
            shelves.add(addU);
        } else {
            for (final Node n : e.rootOptions(f)) {
                if (e.countDirect(f, n.opt) > 0) continue;
                TextView b = Ui.button(a, "＋  " + n.eff.name, Ui.steelButton(theme, dp(6)), Ui.BONE_DIM, 15, new View.OnClickListener() {
                    public void onClick(View v) {
                        Sel s = e.addUnit(f, n);
                        changed();
                        a.show(new UnitScreen(a, RosterScreen.this, s));
                    }
                });
                card.addView(b, Ui.lpm(-1, dp(44), 0, dp(4), 0, 0));
            }
        }
        c.addView(card, Ui.lpm(-1, -2, dp(depth * 6), dp(6), 0, dp(4)));
        for (Force ch : f.children) addForce(c, ch, depth + 1);
        if (container && !e.childForceOptions(f).isEmpty()) {
            c.addView(Ui.button(a, "＋  Детачмент", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
                public void onClick(View v) { addChildForce(f); }
            }), Ui.lpm(-1, dp(50), dp(6), dp(6), 0, dp(6)));
        }
    }

    View unitRow(final Sel s) {
        Engine.Eff ef = e.effSel(s);
        LinearLayout row = Ui.row(a);
        boolean bad = false;
        for (Issue i : issues) if (i.error && UnitScreen.belongs(i.where, s)) { bad = true; break; }
        row.setBackground(Ui.states(new Ui.Parchment(dp(6), false, bad ? 0xFFB02020 : 0, false), new Ui.Parchment(dp(6), false, bad ? 0xFFB02020 : 0, true)));
        row.setPadding(dp(8), dp(7), dp(12), dp(8));
        Ui.Hex hx = new Ui.Hex(a, theme, Units.kind(e, e.entryOf(s), e.linkOf(s)));
        hx.photo = a.unitArt(s.entryId, factionName(s.force));
        row.addView(hx, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
        LinearLayout mid = Ui.col(a);
        TextView n = Ui.text(a, ef.name, 16, Theme.names, Ui.INK);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(n);
        String summary = summary(s);
        if (!summary.isEmpty()) {
            TextView sm = Ui.text(a, summary, 13, Theme.body, Ui.INK_DIM);
            sm.setMaxLines(2);
            sm.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(sm);
        }
        row.addView(mid, Ui.weight(1));
        if (bad) row.addView(Ui.text(a, "⚠ ", 16, Theme.bodyBold, Ui.INK_RED));
        row.addView(Ui.text(a, Engine.fmt(e.points(s)), 19, Theme.namesBlack, Ui.INK_RED));
        row.addView(a.cameraButton(s.entryId, ef.name, factionName(s.force), theme, null), Ui.lpm(dp(36), dp(36), dp(8), 0, 0, 0));
        row.setClickable(true);
        Ui.press(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.haptic(Ui.CLICK); a.show(new UnitScreen(a, RosterScreen.this, s)); }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { Ui.haptic(Ui.HEAVY); unitMenu(s); return true; }
        });
        shelves.add(row);
        return row;
    }

    String factionName(Force f) {
        Data.Cat c = d.cats.get(f.catalogueId);
        return c == null ? "" : c.name;
    }

    String summary(Sel s) {
        int models = 0;
        List<String> gear = new ArrayList<String>();
        for (Sel c : s.children) {
            if ("model".equals(Data.str(e.entryOf(c), "type"))) models += c.number;
            else if (gear.size() < 4) gear.add((c.number > 1 ? c.number + "× " : "") + e.effSel(c).name);
        }
        StringBuilder b = new StringBuilder();
        if (models > 1) b.append(models).append(" ").append(MainActivity.plural(models, "модель", "модели", "моделей"));
        for (String g : gear) { if (b.length() > 0) b.append(" · "); b.append(g); }
        return b.toString();
    }

    void unitMenu(final Sel s) {
        LinearLayout body = a.openSheet(e.effSel(s).name, theme);
        a.sheetItem(body, "banner", "Дублировать", null, theme, false, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                Sel copy = UnitScreen.copySel(s, s.force, s.parent);
                if (s.parent == null) s.force.sels.add(s.force.sels.indexOf(s) + 1, copy); else s.parent.children.add(copy);
                Sfx.play("seal");
                changed();
            }
        });
        a.sheetItem(body, "trash", "Удалить", null, theme, true, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); Sfx.play("burn"); e.removeSel(s); changed(); Ui.haptic(Ui.HEAVY); }
        });
    }

    void addChildForce(final Force parent) { addDetachment(parent, parent.catalogueId); }

    static boolean isAllied(ForceOpt o) { return o.name().toLowerCase().startsWith("allied detachment and"); }

    /** Корневая сила (армия), к которой относятся детачменты фракции catId. */
    Force armyFor(Force parent, String catId) {
        if (parent != null && catId.equals(parent.catalogueId)) return parent;
        for (Force f : r.forces) if (catId.equals(f.catalogueId) && isContainer(f)) return f;
        return null;
    }

    ForceOpt alliedOpt(String catId) {
        for (ForceOpt o : e.rootForceOptions(catId)) if (isAllied(o)) return o;
        return null;
    }

    String shortName(String cat) { return cat == null ? "" : cat.replaceAll("^[IVXL]+ - ", ""); }

    /**
     * Добавление детачмента, как в New Recruit: сверху — фракция детачмента,
     * можно сменить на любую другую (союзник), ниже — доступные детачменты этой фракции.
     */
    void addDetachment(final Force parent, final String catId) {
        final Data.Cat cat = d.cats.get(catId);
        final Theme ct = Theme.forName(cat == null ? null : cat.name);
        final LinearLayout body = a.openSheet("Добавить детачмент", "Значок и слоты показывают, что он даёт", theme);

        // переключатель фракции
        LinearLayout fr = Ui.row(a);
        fr.setBackground(Ui.states(new Ui.Steel(0xF226302B, 0xF2121815, Theme.mix(ct.trim, Ui.GOLD, 0.6f), ct.glow, dp(8), false, false),
                new Ui.Steel(Theme.mix(ct.accent, 0xFF26302B, 0.5f), 0xF2121815, Theme.mix(ct.trim, Ui.GOLD, 0.6f), ct.glow, dp(8), false, true)));
        fr.setPadding(dp(10), dp(8), dp(14), dp(8));
        Ui.Hex hx = new Ui.Hex(a, ct, ct.numeral.isEmpty() ? "banner" : null);
        hx.label = ct.numeral.isEmpty() ? null : ct.numeral;
        fr.addView(hx, Ui.lpm(dp(46), dp(46), 0, 0, dp(12), 0));
        LinearLayout ft = Ui.col(a);
        ft.addView(Ui.text(a, "ФРАКЦИЯ ДЕТАЧМЕНТА", 11, Theme.bodyBold, Ui.GOLD));
        ft.addView(Ui.text(a, shortName(cat == null ? "?" : cat.name), 18, Theme.names, Ui.BONE));
        boolean home = armyFor(parent, catId) != null && catId.equals(r.catalogueId);
        ft.addView(Ui.text(a, catId.equals(r.catalogueId) ? "Основная армия" : "Союзник", 12, Theme.body, home ? Ui.BONE_DIM : Ui.GOLD));
        fr.addView(ft, Ui.weight(1));
        fr.addView(Ui.text(a, "Сменить ›", 15, Theme.bodyBold, Ui.GOLD));
        fr.setClickable(true);
        Ui.press(fr);
        fr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.haptic(Ui.CLICK);
                Sfx.play("page");
                a.closeSheetNow();
                pickFaction(parent, catId);
            }
        });
        body.addView(fr, Ui.lpm(-1, -2, 0, dp(2), 0, dp(8)));

        // список детачментов
        Force army = armyFor(parent, catId);
        ForceOpt allied = null;
        Force temp = null;
        if (army == null) {
            allied = alliedOpt(catId);
            if (allied != null) army = temp = e.addForce(null, allied, catId);
        }
        List<ForceOpt> opts = army == null ? new ArrayList<ForceOpt>() : e.childForceOptions(army);
        List<List<CatSlot>> slotLists = new ArrayList<List<CatSlot>>();
        for (ForceOpt o : opts) slotLists.add(e.slotsFor(o.entry, army));
        if (temp != null) e.removeForce(temp);

        if (opts.isEmpty()) {
            TextView t = Ui.text(a, army == null && allied == null
                    ? "Эта фракция не может выступать союзником в Крестовом походе."
                    : "Больше детачментов этой фракции добавить нельзя.", 15, Theme.body, Ui.BONE_DIM);
            t.setPadding(dp(6), dp(14), dp(6), dp(14));
            body.addView(t);
            return;
        }
        final Force armyF = army == temp ? null : army;
        final ForceOpt alliedF = allied;
        final List<ForceOpt> all = opts;
        for (int i = 0; i < opts.size(); i++) {
            final ForceOpt o = opts.get(i);
            List<CatSlot> slots = slotLists.get(i);
            String type = Units.detachmentType(o.name());
            String slotS = Units.slotSummary(slots);
            String sub = type == null ? slotS : (slotS == null ? type : type + "\n" + slotS);
            double cost = o.eff.cost(d.pointsId);
            String nm = o.name().replaceFirst("^(Auxiliary|Apex) - ", "");
            if (cost > 0) nm += "  (" + Engine.fmt(cost) + ")";
            a.sheetItem(body, Units.detachmentGlyph(o.name(), slots), nm, sub, ct, false, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheet();
                    Force target = armyF;
                    if (target == null) {
                        target = e.addForce(null, alliedF, catId);
                        Voice.say(Voice.proclamation(cat == null ? "" : cat.name));
                        if (!o.name().equals("Allied Detachment")) {
                            for (ForceOpt x : all) if (x.name().equals("Allied Detachment")) {
                                e.addForce(target, x, null);
                                a.toast("Добавлен и обязательный Allied Detachment");
                                break;
                            }
                        }
                    }
                    e.addForce(target, o, null);
                    Sfx.play("seal");
                    changed();
                }
            });
        }
    }

    /** Выбор фракции детачмента: своя армия сверху, ниже союзники. */
    void pickFaction(final Force parent, final String current) {
        LinearLayout body = a.openSheet("Фракция детачмента", "Другая фракция будет добавлена как союзник", theme);
        List<Data.Cat> cats = new ArrayList<Data.Cat>();
        Data.Cat main = d.cats.get(r.catalogueId);
        if (main != null) cats.add(main);
        for (Force f : r.forces) {
            Data.Cat c = d.cats.get(f.catalogueId);
            if (c != null && !cats.contains(c)) cats.add(c);
        }
        int already = cats.size();
        for (Data.Cat c : d.playable()) if (!cats.contains(c)) cats.add(c);
        for (int i = 0; i < cats.size(); i++) {
            final Data.Cat c = cats.get(i);
            if (i == 0) body.addView(a.label("ОСНОВНАЯ АРМИЯ", Ui.GOLD));
            if (i == 1 && already > 1) body.addView(a.label("УЖЕ В РОСТЕРЕ", Ui.GOLD));
            if (i == already) body.addView(a.label("СОЮЗНИКИ", Ui.GOLD));
            Theme t = Theme.forName(c.name);
            LinearLayout it = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, shortName(c.name), c.id.equals(current) ? "выбрана" : null, t, false, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheetNow();
                    addDetachment(parent, c.id);
                }
            });
            if (!t.numeral.isEmpty() && it.getChildCount() > 0) {
                Ui.Hex hx = new Ui.Hex(a, t, null);
                hx.label = t.numeral;
                it.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
            }
        }
    }

    void addRootForce() {
        LinearLayout body = a.openSheet("Прочие силы", "Союзники, Лорд войны и т. п.", theme);
        for (final ForceOpt o : e.rootForceOptions(r.catalogueId)) {
            if (o.name().startsWith("Crusade")) continue;
            List<CatSlot> slots = e.slotsFor(o.entry, r);
            boolean al = isAllied(o);
            a.sheetItem(body, al ? "banner" : Units.detachmentGlyph(o.name(), slots), al ? "Союзная армия" : o.name(), al ? "Выбрать фракцию союзника" : Units.detachmentType(o.name()), theme, false, new View.OnClickListener() {
                public void onClick(View v) {
                    a.closeSheetNow();
                    if (isAllied(o)) pickFaction(null, r.catalogueId);
                    else { e.addForce(null, o, r.catalogueId); Sfx.play("seal"); changed(); }
                }
            });
        }
    }

    void actions() {
        LinearLayout body = a.openSheet(r.name, theme);
        a.sheetItem(body, "banner", "Поделиться списком", "Текстом — в мессенджер или заметки", theme, false, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, r.name);
                i.putExtra(Intent.EXTRA_TEXT, exportText());
                a.startActivity(Intent.createChooser(i, "Поделиться ростером"));
            }
        });
        a.sheetItem(body, "scroll", "Экспорт в BattleScribe (.rosz)", "Откроется в BattleScribe и New Recruit", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); shareRos(true); }
        });
        a.sheetItem(body, "scroll", "Экспорт в .ros (XML)", "Несжатый файл BattleScribe", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); shareRos(false); }
        });
        a.sheetItem(body, "dice", "В битву с этим ростером", "Открыть счётчик победных очков", theme, false, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                BattleSetupScreen bs = new BattleSetupScreen(a);
                Data.Cat cat = d.cats.get(r.catalogueId);
                bs.preset(0, r.name, cat == null ? "" : cat.name);
                a.show(bs);
            }
        });
        a.sheetItem(body, "sword", "Переименовать / лимит", null, theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); a.h.postDelayed(new Runnable() { public void run() { rename(); } }, 200); }
        });
        a.sheetItem(body, "eye", "Настройки и язык правил", null, theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); a.h.postDelayed(new Runnable() { public void run() { a.openSettings(theme, null); } }, 200); }
        });
        a.sheetItem(body, "trash", "Удалить ростер", null, theme, true, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                a.confirm("Удалить «" + r.name + "»?", "Это действие нельзя отменить.", "Удалить", theme, new Runnable() {
                    public void run() { a.store.delete(r); a.back(); }
                });
            }
        });
    }

    void rename() {
        LinearLayout body = a.openSheet("Ростер", theme);
        final EditText n = a.input(r.name, "Название", false);
        body.addView(n, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));
        final EditText l = a.input(Engine.fmt(r.limit), "Лимит очков", true);
        body.addView(l, Ui.lpm(-1, -2, 0, dp(4), 0, dp(10)));
        body.addView(Ui.button(a, "Скрепить печатью", Ui.brass(dp(8)), 0xFF1A140C, 18, new View.OnClickListener() {
            public void onClick(View v) {
                String nm = n.getText().toString().trim();
                if (!nm.isEmpty()) r.name = nm;
                try { r.limit = Double.parseDouble(l.getText().toString().trim()); } catch (Exception ignored) { }
                a.closeSheet();
                Sfx.play("seal");
                changed();
            }
        }), Ui.lp(-1, dp(50)));
    }

    void showIssues() {
        int errs = 0, warns = 0;
        for (Problems.P p : problems) if (p.error) errs++; else warns++;
        String sub = problems.isEmpty() ? null : (errs > 0 ? errs + " " + MainActivity.plural(errs, "ошибка", "ошибки", "ошибок") : "")
                + (warns > 0 ? (errs > 0 ? " · " : "") + warns + " " + MainActivity.plural(warns, "предупреждение", "предупреждения", "предупреждений") : "");
        LinearLayout body = a.openSheet(problems.isEmpty() ? "Ростер верен" : "Проверка ростера", sub, theme);
        // сервочереп-писарь с отчётом
        LinearLayout scribe = Ui.row(a);
        scribe.addView(new Ui.ServoSkull(a), Ui.lp(dp(62), dp(80)));
        TextView sv = Ui.text(a, problems.isEmpty() ? "Сервочереп сверил ростер с данными: нарушений нет. Печать чиста."
                : "Сервочереп сверил ростер с данными и записал, что нужно исправить. Нажмите кнопку под ошибкой — он проведёт к месту.", 13, Theme.body, Ui.BONE_DIM);
        sv.setPadding(dp(8), 0, 0, 0);
        scribe.addView(sv, Ui.weight(1));
        body.addView(scribe, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        if (problems.isEmpty()) {
            body.addView(Ui.text(a, "Ограничения выбора, слоты детачментов и лимит очков в порядке.", 15, Theme.body, Ui.BONE_DIM));
            Sfx.play("choir", 0.6f, 1f);
            return;
        }
        // группируем по месту, как в New Recruit: сначала весь ростер, потом детачменты по порядку
        java.util.LinkedHashMap<String, List<Problems.P>> groups = new java.util.LinkedHashMap<String, List<Problems.P>>();
        for (int pass = 0; pass < 2; pass++)
            for (Problems.P p : problems) {
                if ((pass == 0) != p.error) continue;
                if (!groups.containsKey(p.group)) groups.put(p.group, new ArrayList<Problems.P>());
                groups.get(p.group).add(p);
            }
        for (java.util.Map.Entry<String, List<Problems.P>> en : groups.entrySet()) {
            body.addView(a.label(en.getKey().toUpperCase(), Ui.GOLD));
            for (Problems.P p : en.getValue()) body.addView(problemCard(p, true), Ui.lpm(-1, -2, 0, dp(2), 0, dp(8)));
        }
        TextView note = Ui.text(a, "Проверка повторяет логику данных BSData; редкие условные правила могут не учитываться.", 12, Theme.body, Ui.BONE_DIM);
        note.setPadding(dp(4), dp(6), dp(4), 0);
        body.addView(note);
    }

    void shareRos(boolean zip) {
        try {
            String base = r.name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
            if (base.isEmpty()) base = "roster";
            String name = base + (zip ? ".rosz" : ".ros");
            java.io.File f = new java.io.File(PhotoProvider.shareDir(a), name);
            byte[] data = zip ? com.medoedy.liber.engine.RosExport.rosz(d, r, base + ".ros") : com.medoedy.liber.engine.RosExport.ros(d, r).getBytes("UTF-8");
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(data);
            o.close();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(zip ? "application/zip" : "application/xml");
            i.putExtra(Intent.EXTRA_STREAM, PhotoProvider.shareUri(name));
            i.putExtra(Intent.EXTRA_SUBJECT, r.name);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Экспорт ростера"));
        } catch (Exception ex) { a.toast("Не удалось экспортировать: " + ex.getMessage()); }
    }

    String exportText() {
        StringBuilder b = new StringBuilder();
        Data.Cat cat = d.cats.get(r.catalogueId);
        b.append(r.name).append(" — ").append(cat == null ? "" : cat.name).append("\n");
        b.append(Engine.fmt(e.rosterCost(d.pointsId))).append(" / ").append(Engine.fmt(r.limit)).append(" очков\n");
        List<Force> all = new ArrayList<Force>();
        r.allForces(all);
        for (Force f : all) {
            b.append("\n== ").append(e.effForce(f).name).append(" [").append(Engine.fmt(e.forceCost(f, d.pointsId))).append("]");
            if (f.parent == null && f.catalogueId != null && !f.catalogueId.equals(r.catalogueId) && d.cats.get(f.catalogueId) != null) b.append(" {").append(d.cats.get(f.catalogueId).name).append("}");
            b.append(" ==\n");
            for (Sel s : f.sels) {
                b.append("• ").append(e.effSel(s).name);
                double p = e.points(s);
                if (p > 0) b.append(" [").append(Engine.fmt(p)).append("]");
                b.append("\n");
                exportChildren(b, s, "   ");
            }
        }
        int errs = 0;
        for (Issue i : e.validate()) if (i.error) errs++;
        b.append("\n").append(errs == 0 ? "✓ Ростер верен" : "⚠ Ошибок: " + errs).append("\nСобрано в Liber Rosterum · данные BSData r").append(d.revision);
        return b.toString();
    }

    void exportChildren(StringBuilder b, Sel s, String ind) {
        for (Sel c : s.children) {
            b.append(ind).append(c.number > 1 ? c.number + "× " : "").append(e.effSel(c).name);
            double p = e.points(c);
            if (p > 0) b.append(" [").append(Engine.fmt(p)).append("]");
            b.append("\n");
            exportChildren(b, c, ind + "   ");
        }
    }

    /** Маленький символ без рамки. */
    static final class GlyphView extends View {
        final String kind;
        final int color;
        GlyphView(android.content.Context c, String kind, int color) { super(c); this.kind = kind; this.color = color; }
        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            Glyph.draw(c, kind, getWidth() / 2f, getHeight() / 2f, s * 0.42f, color);
        }
    }
}
