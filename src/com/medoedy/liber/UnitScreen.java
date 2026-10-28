package com.medoedy.liber;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.Issue;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.medoedy.liber.Ui.dp;

/**
 * Карточка юнита: портрет (фото/силуэт), состав по группам моделей, компактное снаряжение
 * (каждая группа опций — одна строка, выбор в листе снизу), профили и правила.
 */
final class UnitScreen extends Screen {
    final RosterScreen rs;
    final Sel s;
    final Engine e;
    final Data d;
    ScrollView sv;
    Map<String, Map<String, Object>> rules = new LinkedHashMap<String, Map<String, Object>>();

    /** Черновик: юнит выбран в списке, но ещё не добавлен — уйдёт, если не нажать «Добавить в ростер». */
    boolean draft;
    /** Экран выбора, с которого пришли: после добавления «назад» ведёт сразу в ростер. */
    Screen from;

    UnitScreen(MainActivity a, RosterScreen rs, Sel s, Screen from) {
        this(a, rs, s);
        this.draft = true;
        this.from = from;
    }

    void save() { if (!draft) a.store.save(rs.r); }

    @Override
    boolean handleBack() {
        if (draft) { e.removeSel(s); e.changed(); draft = false; from = null; return false; }
        if (from != null && a.stack.contains(from)) {
            a.stack.remove(from);
            if (from instanceof PickerScreen) ((PickerScreen) from).alive = false;
            if (from.view != null) a.stage.removeView(from.view);
            from = null;
        }
        return false;
    }

    UnitScreen(MainActivity a, RosterScreen rs, Sel s) {
        super(a);
        this.rs = rs;
        this.s = s;
        this.e = rs.e;
        this.d = rs.d;
        theme = rs.theme;
    }

    void changed() {
        e.changed();
        try {
            List<String> gone = e.resolveForbidden(s);
            if (!gone.isEmpty()) a.toast("Снято несовместимое: " + android.text.TextUtils.join(", ", gone));
        } catch (Throwable ignored) { }
        save();
        final int y = sv == null ? 0 : sv.getScrollY();
        rebuild();
        if (sv != null) sv.post(new Runnable() { public void run() { sv.scrollTo(0, y); } });
    }

    @Override
    void refresh() { changed(); }

    View build() {
        a.h.postDelayed(new Runnable() { public void run() { if (a.stack.peek() == UnitScreen.this) Guide.tip(a, "unit", "Глеб, здесь снаряжение юнита. Чипы включают опции, строки со стрелкой открывают группы выбора. Выбираешь оружие — услышишь его выстрел. Ниже — профили и особые правила: нажми на правило, чтобы прочесть его. Портрет я подберу сам, а фотоаппарат позволит выбрать другой."); } }, 800);
        FrameLayout fr = new FrameLayout(a);
        LinearLayout c = a.column();
        Engine.Eff ef = e.effSel(s);
        c.addView(a.header(ef.name, s.cat != null ? Units.slotRu(d.categoryName(s.cat)) : null, theme, true));
        rules = new LinkedHashMap<String, Map<String, Object>>();
        collectRules(s);

        // ---------- портрет и сводка
        LinearLayout hero = Ui.row(a);
        hero.setBackground(Ui.steel(theme));
        hero.setPadding(dp(14), dp(14), dp(14), dp(14));
        final Ui.Hex hx = new Ui.Hex(a, theme, Units.kind(e, e.entryOf(s), e.linkOf(s)));
        final String fac = rs.factionName(s.force);
        hx.photo = a.unitArt(s.entryId, fac);
        hx.setClickable(true);
        Ui.press(hx);
        hx.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { photoMenu(); } });
        boolean searching = false;
        if (hx.photo == null) {
            searching = AutoPhoto.request(a, s.entryId, ef.name, fac, new AutoPhoto.Done() {
                public void got(boolean ok) {
                    if (ok) Sfx.play("seal", 0.5f, 1.2f);
                    if (a.stack.peek() == UnitScreen.this) changed();
                }
            });
        }
        hero.addView(hx, Ui.lpm(dp(96), dp(96), 0, 0, dp(14), 0));
        LinearLayout ht = Ui.col(a);
        TextView nm = Ui.text(a, ef.name, 20, Theme.names, Ui.BONE);
        nm.setShadowLayer(dp(4), 0, dp(1), 0xFF000000);
        ht.addView(nm);
        int models = 0;
        for (Sel ch : s.children) if ("model".equals(Data.str(e.entryOf(ch), "type"))) models += ch.number;
        if (models > 0) ht.addView(Ui.text(a, models + " " + MainActivity.plural(models, "модель", "модели", "моделей"), 14, Theme.body, Ui.GOLD));
        TextView ptsV = Ui.text(a, Engine.fmt(e.points(s)) + " оч.", 26, Theme.namesBlack, theme.accentBright());
        ptsV.setShadowLayer(dp(6), 0, dp(1), 0xFF000000);
        ht.addView(ptsV);
        if (searching) ht.addView(Ui.text(a, "Ищем фото миниатюры…", 12, Theme.bodyBold, Ui.GOLD));
        else if (hx.photo == null) ht.addView(Ui.text(a, "Фотоаппарат справа — найти фото в Google", 12, Theme.body, Ui.BONE_DIM));
        hero.addView(ht, Ui.weight(1));
        hero.addView(LibraryScreen.bookButton(a, e.effSel(s).name, rs.factionName(s.force), theme, "📖"), Ui.lpm(dp(40), dp(40), dp(8), 0, 0, 0));
        hero.addView(a.cameraButton(s.entryId, e.effSel(s).name, rs.factionName(s.force), theme, null), Ui.lpm(dp(40), dp(40), dp(8), 0, 0, 0));
        c.addView(hero, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));

        // ---------- действия: добавить/удалить, дублировать
        LinearLayout acts = Ui.row(a);
        TextView main;
        if (draft) {
            main = Ui.button(a, "＋  Добавить в ростер", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
                public void onClick(View v) {
                    draft = false;
                    e.changed();
                    a.store.save(rs.r);
                    Sfx.play("seal");
                    Ui.haptic(Ui.DOUBLE);
                    a.toast("«" + e.effSel(s).name + "» в ростере");
                    changed();
                }
            });
        } else {
            main = Ui.button(a, "Удалить из ростера", Ui.danger(dp(8)), 0xFFFFC0B0, 16, new View.OnClickListener() {
                public void onClick(View v) { Sfx.play("burn"); e.removeSel(s); a.store.save(rs.r); Ui.haptic(Ui.HEAVY); a.back(); }
            });
        }
        LinearLayout.LayoutParams la = Ui.lpm(0, dp(50), 0, 0, dp(5), 0);
        la.weight = 1.4f;
        acts.addView(main, la);
        TextView dup = Ui.button(a, "Дублировать", Ui.steelButton(theme, dp(8)), draft ? Ui.BONE_DIM : Ui.BONE, 16, new View.OnClickListener() {
            public void onClick(View v) {
                if (draft) { a.toast("Сначала добавь юнит в ростер"); return; }
                Sel copy = copySel(s, s.force, s.parent);
                if (s.parent == null) s.force.sels.add(s.force.sels.indexOf(s) + 1, copy); else s.parent.children.add(copy);
                e.changed();
                save();
                Sfx.play("seal");
                a.toast("Юнит продублирован");
            }
        });
        if (draft) dup.setAlpha(0.55f);
        LinearLayout.LayoutParams lb = Ui.lpm(0, dp(50), dp(5), 0, 0, 0);
        lb.weight = 1;
        acts.addView(dup, lb);
        c.addView(acts, Ui.lpm(-1, -2, 0, 0, 0, dp(8)));

        // ---------- ошибки
        List<Issue> mine = new ArrayList<Issue>();
        for (Issue i : e.validateUnit(s)) if (i.error) mine.add(i);
        if (!mine.isEmpty()) {
            LinearLayout warn = Ui.col(a);
            warn.setBackground(new Ui.Parchment(dp(8), true, 0xFFC02020, false));
            warn.setPadding(dp(14), dp(10), dp(14), dp(12));
            for (Issue i : mine) {
                Problems.P p = Problems.explain(e, i, rs.r.limit);
                warn.addView(Ui.text(a, "✖ " + p.title, 15, Theme.bodyBold, Ui.INK_RED));
                if (p.detail != null) warn.addView(Ui.text(a, p.detail, 13, Theme.body, Ui.INK));
                if (p.fix != null) warn.addView(Ui.text(a, "→ " + p.fix, 13, Theme.body, Ui.INK_DIM));
            }
            c.addView(warn, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        }

        // ---------- состав и снаряжение
        List<Node> opts = e.options(e.entryOf(s), e.linkOf(s), s);
        List<Node> modelNodes = new ArrayList<Node>(), gear = new ArrayList<Node>();
        for (Node n : opts) {
            if (!n.isGroup() && "model".equals(n.opt.type())) modelNodes.add(n); else gear.add(n);
        }
        if (!modelNodes.isEmpty()) {
            c.addView(a.ribbonTitle("СОСТАВ", theme));
            for (Node n : modelNodes) renderModel(c, s, n);
        }
        if (anyVisible(gear, s)) {
            c.addView(a.ribbonTitle(modelNodes.isEmpty() ? "СНАРЯЖЕНИЕ" : "СНАРЯЖЕНИЕ ОТРЯДА", theme));
            LinearLayout box = Ui.col(a);
            box.setBackground(Ui.parchment());
            box.setPadding(dp(12), dp(10), dp(12), dp(12));
            renderGear(box, s, gear);
            c.addView(box);
        }

        // ---------- профили и правила
        addProfiles(c);
        addRules(c);

        sv = a.scroll(c);
        fr.addView(sv);
        return fr;
    }

    void photoMenu() {
        final String fac = rs.factionName(s.force);
        final android.graphics.Bitmap cur = a.unitArt(s.entryId, fac);
        LinearLayout body = a.openSheet("Портрет юнита", "Хранится только на телефоне", theme);
        if (cur != null) a.sheetItem(body, "eye", "Посмотреть крупно", null, theme, false, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                final android.graphics.Bitmap full = a.photo(s.entryId);
                a.h.postDelayed(new Runnable() { public void run() { if (full != null) a.viewImage(full, e.effSel(s).name, theme); } }, 200);
            }
        });
        a.sheetItem(body, "camera", "Найти в Google", "Удерживайте картинку — она станет портретом", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); a.searchImages(s.entryId, e.effSel(s).name, fac, theme, null); }
        });
        a.sheetItem(body, "eye", "Выбрать фото", "Из галереи или камеры", theme, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); a.pickPhoto(s.entryId, new Runnable() { public void run() { changed(); } }); }
        });
        a.sheetItem(body, "dice", "Подобрать автоматически", "Взять первую картинку из Google заново", theme, false, new View.OnClickListener() {
            public void onClick(View v) {
                a.closeSheet();
                a.removePhoto(s.entryId);
                a.prefs.edit().remove("auto_" + s.entryId).apply();
                changed();
            }
        });
        if (a.photo(s.entryId) != null) a.sheetItem(body, "skull", "Убрать фото", null, theme, true, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); a.removePhoto(s.entryId); Sfx.play("burn"); changed(); }
        });
    }

    // ================================================================ модели
    void renderModel(LinearLayout into, final Sel parent, final Node n) {
        List<Sel> clumps = new ArrayList<Sel>();
        for (Sel ch : parent.children) if (Engine.sameOpt(ch, n.opt)) clumps.add(ch);
        final double max = e.maxOf(n, parent), min = e.minOf(n, parent);
        int total = 0;
        for (Sel ch : clumps) total += ch.number;
        if (n.eff.hidden && total == 0) return;
        double cost = n.eff.cost(d.pointsId);
        LinearLayout head = Ui.row(a);
        head.setPadding(dp(2), dp(6), dp(2), dp(4));
        head.addView(new RosterScreen.GlyphView(a, Units.kind(e, n.opt.entry, n.opt.link), Ui.GOLD), Ui.lpm(dp(26), dp(26), 0, 0, dp(8), 0));
        head.addView(Ui.text(a, n.eff.name, 18, Theme.names, Ui.BONE), Ui.weight(1));
        boolean bad = total < min || (max >= 0 && total > max);
        String range = (min > 0 || max >= 0) ? Engine.fmt(min) + "–" + (max < 0 ? "∞" : Engine.fmt(max)) : "";
        head.addView(Ui.text(a, total + (range.isEmpty() ? "" : " из " + range) + (cost > 0 ? "  · " + Engine.fmt(cost) + "/шт." : ""), 13, Theme.bodyBold, bad ? Ui.RED_BAD : Ui.GOLD));
        into.addView(head);
        for (final Sel cl : clumps) {
            LinearLayout card = Ui.col(a);
            card.setBackground(Ui.parchment());
            card.setPadding(dp(12), dp(8), dp(12), dp(10));
            LinearLayout r1 = Ui.row(a);
            String title = clumps.size() > 1 ? "Группа моделей" : "Количество";
            r1.addView(Ui.text(a, title, 14, Theme.bodyBold, Ui.INK_DIM), Ui.weight(1));
            final int others = total - cl.number;
            int hi = max < 0 ? 999 : (int) (max - others);
            r1.addView(stepper(cl.number, clumps.size() > 1 ? 0 : 1, Math.max(cl.number, hi), new Step() {
                public void set(int v) { if (v <= 0) e.removeSel(cl); else e.setNumber(cl, v); Sfx.play("tick"); changed(); }
            }));
            card.addView(r1);
            List<Node> sub = e.options(e.entryOf(cl), e.linkOf(cl), cl);
            if (anyVisible(sub, cl)) {
                View div = new View(a);
                div.setBackgroundColor(0x406A4A22);
                card.addView(div, Ui.lpm(-1, dp(1), 0, dp(6), 0, dp(6)));
                renderGear(card, cl, sub);
            }
            into.addView(card, Ui.lpm(-1, -2, 0, dp(3), 0, dp(4)));
        }
        if (max < 0 || total < max) {
            TextView add = Ui.button(a, clumps.isEmpty() ? "＋ Добавить" : "＋ Модели с другим снаряжением", Ui.steelButton(theme, dp(6)), Ui.BONE_DIM, 14, new View.OnClickListener() {
                public void onClick(View v) { e.addSel(parent.force, parent, n.opt, 1); Sfx.play("seal"); changed(); }
            });
            add.setMinHeight(dp(38));
            into.addView(add, Ui.lpm(-1, dp(40), 0, dp(2), 0, dp(6)));
        }
    }

    // ================================================================ снаряжение (компактно)
    void renderGear(LinearLayout into, final Sel parent, List<Node> nodes) {
        final int m = Engine.mult(parent);
        Ui.Flow chips = new Ui.Flow(a);
        List<View> rows = new ArrayList<View>();
        List<Sel> nested = new ArrayList<Sel>();
        for (final Node n : nodes) {
            if (n.isGroup()) {
                double inGroup = e.countInGroup(parent, n.opt.id());
                if (n.eff.hidden && inGroup == 0) continue;
                if (!hasVisible(n, parent)) continue;
                if (isContainer(n, parent)) {
                    // группа-контейнер: подгруппы отдельными строками, листья — чипами
                    LinearLayout inner = Ui.col(a);
                    renderGear(inner, parent, n.children);
                    rows.add(inner);
                    continue;
                }
                rows.add(groupRow(parent, n));
                // вложенные опции выбранных вариантов группы
                for (Sel ch : parent.children) if (ch.groups.contains(n.opt.id()) && hasOwnOptions(ch)) nested.add(ch);
                continue;
            }
            if ("model".equals(n.opt.type())) continue;
            final double count = e.countDirect(parent, n.opt);
            if (n.eff.hidden && count == 0) continue;
            final double max = e.maxOf(n, parent), min = e.minOf(n, parent);
            if (max == 0 && count == 0) continue;
            double cost = n.eff.cost(d.pointsId);
            if (min > 0 && max >= 0 && min >= max) {
                chips.addView(chip(n.eff.name + (count > 1 ? " ×" + Engine.fmt(count) : ""), 0, true, true, null));
            } else if (max >= 0 && max <= m) {
                final boolean on = count > 0;
                chips.addView(chip(n.eff.name, cost, on, false, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (on && min >= 1) { a.toast("Обязательный выбор"); Ui.haptic(Ui.HEAVY); return; }
                        e.setCount(parent, n.opt, on ? 0 : (int) Math.max(1, Math.min(max, m)));
                        if (on) Sfx.play("tick"); else armed(n, "servo");
                        changed();
                    }
                }));
            } else {
                rows.add(stepRow(parent, n, (int) count, max, cost));
            }
            if (count > 0) {
                Sel ch = e.findChild(parent, n.opt);
                if (ch != null && hasOwnOptions(ch)) nested.add(ch);
            }
        }
        if (chips.getChildCount() > 0) into.addView(chips, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        for (View r : rows) into.addView(r, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        for (Sel ch : nested) {
            LinearLayout sub = Ui.col(a);
            sub.setPadding(dp(12), dp(4), 0, dp(2));
            sub.addView(Ui.text(a, "↳ " + e.effSel(ch).name, 14, Theme.bodyBold, Ui.INK_RED));
            renderGear(sub, ch, e.options(e.entryOf(ch), e.linkOf(ch), ch));
            into.addView(sub);
        }
    }

    /** Группа, внутри которой есть подгруппы, и её лимит не меньше их числа — разворачиваем. */
    boolean isContainer(Node g, Sel parent) {
        int sub = 0;
        for (Node c : g.children) if (c.isGroup() && hasVisible(c, parent)) sub++;
        if (sub == 0) return false;
        double gmax = e.maxOf(g, parent);
        return gmax < 0 || gmax > Engine.mult(parent);
    }

    boolean hasOwnOptions(Sel ch) {
        return anyVisible(e.options(e.entryOf(ch), e.linkOf(ch), ch), ch);
    }

    /** Чип: обязательный (без действия), включённый или выключенный. */
    View chip(String name, double cost, boolean on, boolean fixed, View.OnClickListener l) {
        String label = (fixed ? "● " : on ? "✓ " : "+ ") + name + (cost > 0 && !fixed ? "  " + Engine.fmt(cost) : "");
        TextView t = Ui.text(a, label, 14, on || fixed ? Theme.bodyBold : Theme.body, fixed ? Ui.INK_DIM : on ? Ui.BONE : Ui.INK);
        if (fixed) t.setBackground(new Ui.Steel(0x33FFFFFF, 0x22FFFFFF, 0x806A4A22, 0, dp(4), false, false));
        else if (on) t.setBackground(Ui.states(new Ui.Metal(0xFF8A2A1E, 0xFF3E0E0A, 0xFF1A0503, dp(4)), new Ui.Metal(0xFFA03A2A, 0xFF4E1610, 0xFF1A0503, dp(4))));
        else t.setBackground(Ui.states(new Ui.Steel(0x26FFFFFF, 0x14000000, 0xFF8A6A3A, 0, dp(4), false, false), new Ui.Steel(0x40FFFFFF, 0x22000000, 0xFF8A6A3A, 0, dp(4), false, true)));
        t.setPadding(dp(10), dp(6), dp(10), dp(7));
        if (l != null) {
            t.setClickable(true);
            Ui.press(t);
            final View.OnClickListener ll = l;
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); ll.onClick(v); } });
        }
        return t;
    }

    View stepRow(final Sel parent, final Node n, int count, double max, double cost) {
        LinearLayout row = Ui.row(a);
        LinearLayout txt = Ui.col(a);
        txt.addView(Ui.text(a, n.eff.name, 15, Theme.bodyBold, count > 0 ? Ui.INK : Ui.INK_DIM));
        if (cost != 0) txt.addView(Ui.text(a, Engine.fmt(cost) + " оч./шт.", 12, Theme.body, Ui.INK_RED));
        row.addView(txt, Ui.weight(1));
        row.addView(stepper(count, 0, max < 0 ? 999 : (int) max, new Step() {
            public void set(int v) { boolean up = v > e.countDirect(parent, n.opt); e.setCount(parent, n.opt, v); if (up) armed(n, "tick"); else Sfx.play("tick"); changed(); }
        }));
        return row;
    }

    /** Строка группы: короткое имя, текущий выбор, требование, «›». */
    View groupRow(final Sel parent, final Node g) {
        LinearLayout row = Ui.row(a);
        row.setBackground(Ui.states(new Ui.Steel(0x1AFFFFFF, 0x10000000, 0xFF8A6A3A, 0, dp(6), false, false), new Ui.Steel(0x33FFFFFF, 0x1A000000, 0xFF8A6A3A, 0, dp(6), false, true)));
        row.setPadding(dp(12), dp(8), dp(10), dp(9));
        LinearLayout txt = Ui.col(a);
        TextView gt = Ui.text(a, Units.groupName(g.eff.name).toUpperCase(), 12, Theme.bodyBold, Ui.INK_RED);
        gt.setLetterSpacing(0.05f);
        txt.addView(gt);
        String cur = currentInGroup(parent, g.opt.id());
        TextView cv = Ui.text(a, cur.isEmpty() ? "— не выбрано" : cur, 16, Theme.names, cur.isEmpty() ? Ui.INK_DIM : Ui.INK);
        cv.setMaxLines(2);
        cv.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(cv);
        row.addView(txt, Ui.weight(1));
        double have = e.countInGroup(parent, g.opt.id()), gmin = e.minOf(g, parent), gmax = e.maxOf(g, parent);
        boolean bad = have < gmin || (gmax >= 0 && have > gmax);
        if (gmin > 0 || bad) {
            TextView req = Ui.text(a, Engine.fmt(have) + "/" + (gmax < 0 ? Engine.fmt(gmin) + "+" : Engine.fmt(gmax)), 13, Theme.bodyBold, bad ? Ui.INK_RED : Ui.INK_DIM);
            req.setPadding(dp(6), 0, dp(6), 0);
            row.addView(req);
        }
        row.addView(Ui.text(a, "›", 26, Theme.display, Ui.INK_RED));
        row.setClickable(true);
        Ui.press(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.haptic(Ui.CLICK); openGroup(parent, g.opt.id(), g.eff.name); }
        });
        return row;
    }

    String currentInGroup(Sel parent, String gid) {
        StringBuilder b = new StringBuilder();
        for (Sel ch : parent.children) {
            if (!ch.groups.contains(gid)) continue;
            if (b.length() > 0) b.append(", ");
            int per = Engine.mult(parent);
            if (ch.number > 1 && ch.number != per) b.append(ch.number).append("× ");
            b.append(e.effSel(ch).name);
        }
        return b.toString();
    }

    // ---------------------------------------------------------- лист выбора в группе
    /** Что даёт вариант: его правила и текстовые профили (прайм-выгоды, снаряжение, реакции). */
    String optionText(Node n) {
        StringBuilder b = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (Map<String, Object> r : e.rulesOf(n.opt.entry, n.opt.link)) {
            String nm = Data.clean(Data.str(r, "name")), ds = Data.str(r, "description");
            if (ds == null || ds.isEmpty() || !seen.add(nm)) continue;
            if (b.length() > 0) b.append("\n\n");
            b.append(nm).append(": ").append(ds);
        }
        for (Map<String, Object> p : e.profilesOf(n.opt.entry, n.opt.link)) {
            String t = Data.str(p, "typeName");
            if (t != null && (t.toLowerCase().contains("weapon") || t.equalsIgnoreCase("Profile"))) continue;
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String v = Data.str(ch, "$text");
                if (v == null || v.length() < 20) continue;
                if (b.length() > 0) b.append("\n\n");
                b.append(v);
            }
        }
        return b.length() == 0 ? null : b.toString();
    }

    void openGroup(final Sel parent, final String gid, String name) {
        Node g = findGroup(e.options(e.entryOf(parent), e.linkOf(parent), parent), gid);
        if (g == null) return;
        double gmin = e.minOf(g, parent), gmax = e.maxOf(g, parent);
        String req = gmin > 0 && gmin == gmax ? "Выберите " + Engine.fmt(gmin) : gmax >= 0 ? "Не больше " + Engine.fmt(gmax) + (gmin > 0 ? ", минимум " + Engine.fmt(gmin) : "") : "Без ограничения";
        LinearLayout body = a.openSheet(Units.groupName(name), req + (name.length() > 30 ? "\n" + name : ""), theme);
        fillGroup(body, parent, gid);
    }

    void fillGroup(final LinearLayout body, final Sel parent, final String gid) {
        body.removeAllViews();
        final Node g = findGroup(e.options(e.entryOf(parent), e.linkOf(parent), parent), gid);
        if (g == null) return;
        final int m = Engine.mult(parent);
        double gmax = e.maxOf(g, parent);
        final boolean radio = gmax > 0 && gmax <= m && allLeaves(g);
        LinearLayout sheet = Ui.col(a);
        sheet.setBackground(Ui.parchment());
        sheet.setPadding(dp(10), dp(8), dp(10), dp(10));
        fillLeaves(sheet, body, parent, g, gid, radio, 0);
        body.addView(sheet);
        body.addView(Ui.button(a, "Готово", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheet(); }
        }), Ui.lpm(-1, dp(48), 0, dp(10), 0, 0));
    }

    void fillLeaves(LinearLayout into, final LinearLayout body, final Sel parent, Node g, final String gid, final boolean radio, int depth) {
        final int m = Engine.mult(parent);
        for (final Node n : g.children) {
            if (n.isGroup()) {
                if (!hasVisible(n, parent)) continue;
                TextView sh = Ui.text(a, Units.groupName(n.eff.name), 13, Theme.bodyBold, Ui.INK_RED);
                sh.setPadding(dp(4 + depth * 8), dp(8), 0, dp(2));
                into.addView(sh);
                fillLeaves(into, body, parent, n, gid, radio, depth + 1);
                continue;
            }
            final double count = e.countDirect(parent, n.opt);
            if (n.eff.hidden && count == 0) continue;
            final double max = e.maxOf(n, parent), min = e.minOf(n, parent);
            if (max == 0 && count == 0) continue;
            double cost = n.eff.cost(d.pointsId);
            LinearLayout row = Ui.row(a);
            row.setPadding(dp(4 + depth * 8), dp(4), 0, dp(4));
            LinearLayout txt = Ui.col(a);
            txt.addView(Ui.text(a, n.eff.name, 16, count > 0 ? Theme.bodyBold : Theme.body, Ui.INK));
            if (cost != 0) txt.addView(Ui.text(a, (cost > 0 ? "+" : "") + Engine.fmt(cost) + " оч." + (m > 1 ? " за модель" : ""), 12, Theme.bodyBold, Ui.INK_RED));
            row.addView(txt, Ui.weight(1));
            final String info = optionText(n);
            if (info != null) {
                final TextView desc = Ui.text(a, "", 13, Theme.body, Ui.INK_DIM);
                desc.setVisibility(View.GONE);
                desc.setLineSpacing(dp(1), 1f);
                txt.addView(desc, Ui.lpm(-1, -2, 0, dp(4), 0, dp(2)));
                TextView q = Ui.button(a, "?", Ui.steelButton(theme, dp(5)), Ui.GOLD, 15, new View.OnClickListener() {
                    public void onClick(View v) {
                        Ui.haptic(Ui.CLICK);
                        if (desc.getVisibility() == View.VISIBLE) { desc.setVisibility(View.GONE); return; }
                        desc.setVisibility(View.VISIBLE);
                        TrText.bind(desc, info);
                    }
                });
                q.setPadding(0, 0, 0, dp(2));
                row.addView(q, Ui.lpm(dp(34), dp(34), 0, 0, dp(8), 0));
            }
            if (radio) {
                final boolean on = count > 0;
                MainActivity.Toggle tg = new MainActivity.Toggle(a, on, true, theme);
                tg.onParchment = true;
                row.addView(tg, Ui.lp(dp(42), dp(42)));
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (on) {
                            // повторное нажатие снимает выбор, если группа не обязательна
                            Node top = findGroup(e.options(e.entryOf(parent), e.linkOf(parent), parent), gid);
                            double gmin = top == null ? 0 : e.minOf(top, parent);
                            if (gmin > 0 || min > 0) { Ui.haptic(Ui.HEAVY); a.toast("Обязательный выбор — можно только заменить"); return; }
                            e.setCount(parent, n.opt, 0);
                            Ui.haptic(Ui.CLICK);
                            Sfx.play("tick");
                            changed();
                            fillGroup(body, parent, gid);
                            return;
                        }
                        List<Sel> rm = new ArrayList<Sel>();
                        for (Sel ch : parent.children) if (ch.groups.contains(gid)) rm.add(ch);
                        for (Sel ch : rm) parent.children.remove(ch);
                        e.changed();
                        int want = (int) Math.max(1, max < 0 ? m : Math.min(max, m));
                        e.setCount(parent, n.opt, want);
                        Ui.haptic(Ui.CLICK);
                        armed(n, "seal");
                        changed();
                        a.closeSheet();
                    }
                });
            } else if (max >= 0 && max <= m) {
                final boolean on = count > 0;
                MainActivity.Toggle tg = new MainActivity.Toggle(a, on, false, theme);
                tg.onParchment = true;
                row.addView(tg, Ui.lp(dp(42), dp(42)));
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (on && min >= 1) { a.toast("Обязательный выбор"); return; }
                        e.setCount(parent, n.opt, on ? 0 : (int) Math.max(1, Math.min(max, m)));
                        Ui.haptic(Ui.CLICK);
                        if (on) Sfx.play("tick"); else armed(n, "servo");
                        changed();
                        fillGroup(body, parent, gid);
                    }
                });
            } else {
                row.addView(stepper((int) count, 0, max < 0 ? 999 : (int) max, new Step() {
                    public void set(int v) { boolean up = v > e.countDirect(parent, n.opt); e.setCount(parent, n.opt, v); if (up) armed(n, "tick"); else Sfx.play("tick"); changed(); fillGroup(body, parent, gid); }
                }));
            }
            Ui.press(row);
            into.addView(row);
        }
    }

    Node findGroup(List<Node> nodes, String gid) {
        for (Node n : nodes) {
            if (!n.isGroup()) continue;
            if (gid.equals(n.opt.id())) return n;
            Node x = findGroup(n.children, gid);
            if (x != null) return x;
        }
        return null;
    }

    boolean allLeaves(Node g) {
        for (Node c : g.children) {
            if (c.isGroup()) { if (!allLeaves(c)) return false; }
            else if ("model".equals(c.opt.type())) return false;
        }
        return true;
    }

    /** Группа видна, только если она не скрыта данными и в ней можно что-то выбрать (или уже выбрано). */
    boolean hasVisible(Node g, Sel parent) {
        boolean chosen = e.countInGroup(parent, g.opt.id()) > 0;
        if (!chosen && (g.eff.hidden || e.maxOf(g, parent) == 0)) return false;
        for (Node c : g.children) {
            if (c.isGroup()) { if (hasVisible(c, parent)) return true; }
            else if (leafVisible(c, parent)) return true;
        }
        return false;
    }

    boolean leafVisible(Node c, Sel parent) {
        if (e.countDirect(parent, c.opt) > 0) return true;
        return !c.eff.hidden && e.maxOf(c, parent) != 0;
    }

    boolean anyVisible(List<Node> nodes, Sel parent) {
        for (Node n : nodes) {
            if (n.isGroup()) { if (hasVisible(n, parent)) return true; }
            else if (leafVisible(n, parent)) return true;
        }
        return false;
    }

    interface Step { void set(int v); }

    View stepper(final int value, final int lo, final int hi, final Step st) {
        LinearLayout r = Ui.row(a);
        TextView minus = Ui.button(a, "−", Ui.steelButton(theme, dp(5)), value > lo ? Ui.GOLD : 0x44D2AE62, 20, new View.OnClickListener() {
            public void onClick(View v) { if (value > lo) st.set(value - 1); else Ui.haptic(Ui.HEAVY); }
        });
        minus.setPadding(0, 0, 0, dp(3));
        minus.setMinHeight(dp(38));
        r.addView(minus, Ui.lp(dp(40), dp(38)));
        TextView val = Ui.text(a, String.valueOf(value), 18, Theme.namesBlack, Ui.INK);
        val.setGravity(Gravity.CENTER);
        r.addView(val, Ui.lp(dp(38), -2));
        TextView plus = Ui.button(a, "+", Ui.steelButton(theme, dp(5)), value < hi ? Ui.GOLD : 0x44D2AE62, 20, new View.OnClickListener() {
            public void onClick(View v) { if (value < hi) st.set(value + 1); else { Ui.haptic(Ui.HEAVY); Sfx.play("error", 0.5f, 1f); a.toast("Достигнут максимум"); } }
        });
        plus.setPadding(0, 0, 0, dp(3));
        plus.setMinHeight(dp(38));
        r.addView(plus, Ui.lp(dp(40), dp(38)));
        return r;
    }

    /** Звук и отдача выбранного оружия (болтер — очередь, пиломеч — мотор…). */
    void armed(Node n, String fallback) {
        String nm = n.eff.name;
        String w = Sfx.weaponSound(nm);
        if (w == null) for (Map<String, Object> p : e.profilesOf(n.opt.entry, n.opt.link)) {
            String pn = Data.str(p, "name");
            if (Sfx.weaponSound(pn) != null) { nm = pn; w = Sfx.weaponSound(pn); break; }
        }
        if (w == null) { Sfx.play(fallback); return; }
        Sfx.weapon(nm);
        Ui.recoil(w);
    }

    // ================================================================ профили
    void addProfiles(LinearLayout c) {
        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<String, List<Map<String, Object>>>();
        collectProfiles(s, byType, new HashSet<String>());
        if (byType.isEmpty()) return;
        // сначала модели, потом оружие, потом текстовые
        List<String> order = new ArrayList<String>();
        for (String t : byType.keySet()) if (!isText(byType.get(t)) && !t.toLowerCase().contains("weapon")) order.add(t);
        for (String t : byType.keySet()) if (!isText(byType.get(t)) && t.toLowerCase().contains("weapon") && !order.contains(t)) order.add(t);
        for (String t : byType.keySet()) if (!order.contains(t)) order.add(t);
        c.addView(a.ribbonTitle("ХАРАКТЕРИСТИКИ", theme));
        for (String type : order) {
            List<Map<String, Object>> ps = byType.get(type);
            LinearLayout th = Ui.row(a);
            th.setPadding(dp(2), dp(10), 0, dp(4));
            String tl = type.toLowerCase();
            String glyph = tl.contains("trait") ? "banner" : tl.contains("vehicle") ? "tank" : tl.contains("melee") ? "sword" : tl.contains("ranged") || tl.contains("weapon") ? "cannon" : tl.contains("reaction") ? "fist" : tl.contains("psych") ? "psyker" : tl.contains("wargear") ? "shield" : "skull";
            th.addView(new RosterScreen.GlyphView(a, glyph, Ui.GOLD), Ui.lpm(dp(22), dp(22), 0, 0, dp(8), 0));
            TextView tt = Ui.title(a, typeRu(type), 16, Ui.GOLD);
            tt.setLetterSpacing(0.06f);
            th.addView(tt);
            c.addView(th);
            if (tl.contains("trait")) c.addView(traitsBox(ps), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
            else if (tl.contains("weapon")) for (Map<String, Object> p : ps) c.addView(statLike(p) ? weaponCard(p) : textCard(p), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
            else for (Map<String, Object> p : ps) c.addView(statLike(p) ? statCard(p) : textCard(p), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
    }

    static String typeRu(String t) {
        String l = t.toLowerCase();
        if (l.contains("ranged")) return "Стрелковое оружие";
        if (l.contains("melee")) return "Оружие ближнего боя";
        if (l.contains("weapon")) return "Оружие";
        if (l.contains("reaction")) return "Реакции";
        if (l.contains("wargear")) return "Снаряжение";
        if (l.contains("psych")) return "Психосилы";
        if (l.contains("trait")) return "Трейты";
        if (l.contains("vehicle")) return "Профиль техники";
        if (l.equals("profile") || l.contains("model") || l.contains("unit")) return "Профиль";
        return t;
    }

    boolean isText(List<Map<String, Object>> ps) {
        for (Map<String, Object> p : ps) for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String v = Data.str(ch, "$text");
            if (v != null && v.length() > 40) return true;
        }
        return false;
    }

    /** Похож ли профиль на таблицу характеристик (а не на текст правила). */
    static boolean statLike(Map<String, Object> p) {
        int shortVals = 0;
        for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String v = Data.str(ch, "$text");
            if (v != null && v.trim().length() > 0 && v.trim().length() <= 6) shortVals++;
        }
        return shortVals >= 3;
    }

    static String isTypeField(String n) {
        return n == null ? null : (n.equalsIgnoreCase("Type") || n.equalsIgnoreCase("Unit Type") ? n : null);
    }

    /** Короткая подпись для плитки: Front Armour → Front, Transport Capacity → Cap. */
    static String tileLabel(String n) {
        if (n == null) return "";
        String l = n.toLowerCase();
        if (l.startsWith("front armour") || l.startsWith("front armor")) return "Front";
        if (l.startsWith("side armour") || l.startsWith("side armor")) return "Side";
        if (l.startsWith("rear armour") || l.startsWith("rear armor")) return "Rear";
        if (l.startsWith("transport capacity")) return "Cap";
        if (l.startsWith("hull points")) return "HP";
        if (n.length() <= 6) return n;
        StringBuilder b = new StringBuilder();
        for (String w : n.split("[\\s-]+")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0)));
        return b.length() >= 2 ? b.toString() : n.substring(0, 5);
    }

    /** Профиль модели или техники: имя, тип, плитки характеристик, ниже — длинные поля. */
    View statCard(Map<String, Object> p) {
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.parchment());
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        card.addView(Ui.text(a, Data.clean(Data.str(p, "name")), 17, Theme.names, Ui.INK));
        Ui.Flow flow = new Ui.Flow(a);
        flow.gap = dp(5);
        List<String[]> extras = new ArrayList<String[]>();
        for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String n = Data.str(ch, "name"), v = Data.str(ch, "$text");
            if (v == null || v.trim().isEmpty()) v = "–";
            v = v.trim();
            if (isTypeField(n) != null) {
                card.addView(Ui.text(a, v, 13, Theme.bodyBold, Ui.INK_DIM));
                continue;
            }
            if (v.length() > 6) { extras.add(new String[]{n, v}); continue; }
            flow.addView(statCell(tileLabel(n), v));
        }
        card.addView(flow, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        for (String[] ex : extras) {
            TextView lbl = Ui.text(a, ex[0] == null ? "" : ex[0].toUpperCase(), 11, Theme.bodyBold, Ui.INK_RED);
            lbl.setLetterSpacing(0.08f);
            lbl.setPadding(0, dp(8), 0, dp(1));
            card.addView(lbl);
            TextView tv = Ui.text(a, ex[1], 14, Theme.body, Ui.INK);
            translateInto(tv, ex[1]);
            card.addView(tv);
        }
        return card;
    }

    /** Трейты: компактные чипы в одной рамке; если у трейта есть описание — тап его покажет. */
    View traitsBox(List<Map<String, Object>> ps) {
        LinearLayout box = Ui.col(a);
        box.setBackground(Ui.parchment());
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        Ui.Flow flow = new Ui.Flow(a);
        flow.gap = dp(6);
        for (Map<String, Object> p : ps) {
            final String name = Data.clean(Data.str(p, "name")).replace("[", "").replace("]", "").trim();
            final StringBuilder desc = new StringBuilder();
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String v = Data.str(ch, "$text");
                if (v == null) continue;
                v = v.trim();
                if (v.isEmpty() || v.equals("-") || v.equals("–")) continue;
                if (desc.length() > 0) desc.append("\n\n");
                desc.append(v);
            }
            boolean has = desc.length() > 0;
            TextView chip = Ui.text(a, has ? name + "  ⓘ" : name, 14, Theme.bodyBold, has ? Ui.INK : Ui.INK_DIM);
            chip.setBackground(Ui.states(new Ui.Steel(0x1AFFFFFF, 0x10000000, 0xFF8A6A3A, 0, dp(4), false, false), new Ui.Steel(0x33FFFFFF, 0x1A000000, 0xFF8A6A3A, 0, dp(4), false, true)));
            chip.setPadding(dp(10), dp(5), dp(10), dp(7));
            if (has) {
                chip.setClickable(true);
                Ui.press(chip);
                chip.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Ui.haptic(Ui.CLICK);
                        LinearLayout body = a.openSheet(name, "Трейт", theme);
                        LinearLayout sh = Ui.col(a);
                        sh.setBackground(Ui.parchment());
                        sh.setPadding(dp(14), dp(10), dp(14), dp(12));
                        TextView tv = Ui.text(a, desc.toString(), 15, Theme.body, Ui.INK);
                        translateInto(tv, desc.toString());
                        sh.addView(tv);
                        body.addView(sh);
                    }
                });
            }
            flow.addView(chip);
        }
        box.addView(flow);
        TextView note = Ui.text(a, "Трейты определяют, на что влияют правила (Allegiance, Legiones Astartes и т. п.).", 12, Theme.body, Ui.INK_DIM);
        note.setPadding(dp(2), dp(8), 0, 0);
        box.addView(note);
        return box;
    }

    View statCell(String label, String value) {
        LinearLayout cell = Ui.col(a);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setBackground(new Ui.Steel(0xFF2A3430, 0xFF141A17, Ui.GOLD_DIM, 0, dp(4), false, false));
        cell.setPadding(dp(4), dp(3), dp(4), dp(5));
        cell.setMinimumWidth(dp(42));
        TextView l = Ui.text(a, label == null ? "" : label, 10, Theme.bodyBold, Ui.GOLD);
        l.setGravity(Gravity.CENTER);
        cell.addView(l, Ui.lp(-1, -2));
        TextView v = Ui.text(a, value, 17, Theme.namesBlack, Ui.BONE);
        v.setGravity(Gravity.CENTER);
        cell.addView(v, Ui.lp(-1, -2));
        return cell;
    }

    /** Оружие: имя, полоса характеристик, особые правила чипами (тап — описание). */
    View weaponCard(Map<String, Object> p) {
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.parchment());
        card.setPadding(dp(12), dp(8), dp(12), dp(10));
        card.addView(Ui.text(a, Data.clean(Data.str(p, "name")), 16, Theme.names, Ui.INK));
        Ui.Flow stats = new Ui.Flow(a);
        stats.gap = dp(4);
        List<String> traits = new ArrayList<String>();
        for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String n = Data.str(ch, "name"), v = Data.str(ch, "$text");
            if (v == null || v.isEmpty()) v = "–";
            String ln = n == null ? "" : n.toLowerCase();
            if (ln.contains("trait") || ln.contains("rule") || ln.contains("special") || v.length() > 7) {
                if (!v.equals("-") && !v.equals("–")) for (String part : v.split(",")) if (!part.trim().isEmpty()) traits.add(part.trim());
                continue;
            }
            stats.addView(statCell(n, v));
        }
        card.addView(stats, Ui.lpm(-1, -2, 0, dp(5), 0, 0));
        if (!traits.isEmpty()) {
            Ui.Flow tf = new Ui.Flow(a);
            tf.gap = dp(5);
            for (final String tr : traits) {
                TextView t = Ui.text(a, tr, 13, Theme.bodyBold, Ui.INK_RED);
                t.setBackground(Ui.states(new Ui.Steel(0x1AFFFFFF, 0x10000000, 0xFF8A6A3A, 0, dp(4), false, false), new Ui.Steel(0x33FFFFFF, 0x1A000000, 0xFF8A6A3A, 0, dp(4), false, true)));
                t.setPadding(dp(8), dp(3), dp(8), dp(4));
                t.setClickable(true);
                Ui.press(t);
                t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); showRule(tr); } });
                tf.addView(t);
            }
            card.addView(tf, Ui.lpm(-1, -2, 0, dp(7), 0, 0));
        }
        return card;
    }

    /** Текстовый профиль (реакции, снаряжение): заголовок и абзацы с подписями. */
    View textCard(Map<String, Object> p) {
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.parchment());
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        card.addView(Ui.text(a, Data.clean(Data.str(p, "name")), 17, Theme.names, Ui.INK));
        for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String n = Data.str(ch, "name"), v = Data.str(ch, "$text");
            if (v == null || v.trim().isEmpty() || v.equals("-")) continue;
            TextView lbl = Ui.text(a, n == null ? "" : n.toUpperCase(), 11, Theme.bodyBold, Ui.INK_RED);
            lbl.setLetterSpacing(0.08f);
            lbl.setPadding(0, dp(6), 0, dp(1));
            card.addView(lbl);
            TextView tv = Ui.text(a, v, 15, Theme.body, Ui.INK);
            tv.setLineSpacing(dp(2), 1f);
            translateInto(tv, v);
            card.addView(tv);
        }
        return card;
    }

    void translateInto(final TextView tv, final String en) { TrText.bind(tv, en); }

    void collectProfiles(Sel x, Map<String, List<Map<String, Object>>> byType, Set<String> seen) {
        for (Map<String, Object> p : e.profilesOf(e.entryOf(x), e.linkOf(x))) {
            String key = Data.str(p, "name") + "|" + Data.str(p, "typeId");
            if (!seen.add(key)) continue;
            String type = Data.clean(Data.str(p, "typeName"));
            if (type.isEmpty()) type = "Profile";
            if (!byType.containsKey(type)) byType.put(type, new ArrayList<Map<String, Object>>());
            byType.get(type).add(p);
        }
        for (Sel ch : x.children) collectProfiles(ch, byType, seen);
    }

    // ================================================================ правила
    void addRules(LinearLayout c) {
        if (rules.isEmpty()) return;
        c.addView(a.ribbonTitle("ОСОБЫЕ ПРАВИЛА", theme));
        LinearLayout box = Ui.col(a);
        box.setBackground(Ui.parchment());
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        Ui.Flow flow = new Ui.Flow(a);
        for (final String name : rules.keySet()) {
            TextView chip = Ui.text(a, name, 14, Theme.bodyBold, Ui.INK);
            chip.setBackground(Ui.states(new Ui.Steel(0x1AFFFFFF, 0x10000000, 0xFF8A6A3A, 0, dp(4), false, false), new Ui.Steel(0x33FFFFFF, 0x1A000000, 0xFF8A6A3A, 0, dp(4), false, true)));
            chip.setPadding(dp(10), dp(5), dp(10), dp(7));
            chip.setClickable(true);
            Ui.press(chip);
            chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); showRule(name); } });
            flow.addView(chip);
        }
        box.addView(flow);
        TextView hint = Ui.text(a, "Коснитесь правила, чтобы прочитать. Под текстом — кнопка перевода на русский.", 12, Theme.body, Ui.INK_DIM);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        c.addView(box);
    }

    void showRule(String name) {
        Map<String, Object> r = rules.get(name);
        if (r == null) {
            String key = name.toLowerCase().replaceAll("\\s*\\(.*", "").trim();
            for (Map.Entry<String, Map<String, Object>> en : rules.entrySet()) {
                String k = en.getKey().toLowerCase().replaceAll("\\s*\\(.*", "").trim();
                if (k.equals(key) || k.startsWith(key) || key.startsWith(k)) { r = en.getValue(); break; }
            }
        }
        if (r == null) {
            for (Map<String, Object> rr : d.rules.values()) {
                String k = Data.clean(Data.str(rr, "name")).toLowerCase();
                String key = name.toLowerCase().replaceAll("\\s*\\(.*", "").trim();
                if (k.equals(key) || k.startsWith(key + " (")) { r = rr; break; }
            }
        }
        LinearLayout body = a.openSheet(name, theme);
        LinearLayout sheet = Ui.col(a);
        sheet.setBackground(Ui.parchment());
        sheet.setPadding(dp(14), dp(12), dp(14), dp(14));
        String desc = r == null ? null : Data.str(r, "description");
        TextView tv = Ui.text(a, "", 15, Theme.body, Ui.INK);
        tv.setLineSpacing(dp(2), 1f);
        if (desc == null || desc.isEmpty()) tv.setText("Описание этого правила отсутствует в данных.");
        else translateInto(tv, desc);
        sheet.addView(tv);
        body.addView(sheet);
        body.addView(LibraryScreen.bookButton(a, name.replaceAll("\\s*\\(.*", "").trim(), rs.factionName(s.force), theme, "📖  Открыть в книге"), Ui.lpm(-1, dp(46), 0, dp(10), 0, 0));
    }

    void collectRules(Sel x) {
        for (Map<String, Object> r : e.rulesOf(e.entryOf(x), e.linkOf(x))) {
            String n = Data.clean(Data.str(r, "name"));
            if (!rules.containsKey(n)) rules.put(n, r);
        }
        for (Sel ch : x.children) collectRules(ch);
    }

    // ================================================================ утилиты
    static boolean belongs(Object where, Sel s) {
        Object o = where;
        while (o instanceof Sel) {
            if (o == s) return true;
            o = ((Sel) o).parent;
        }
        return false;
    }

    static Sel copySel(Sel s, Force f, Sel parent) {
        Sel c = new Sel();
        c.entryId = s.entryId;
        c.linkId = s.linkId;
        c.cat = s.cat;
        c.groups = new ArrayList<String>(s.groups);
        c.number = s.number;
        c.force = f;
        c.parent = parent;
        for (Sel ch : s.children) c.children.add(copySel(ch, f, c));
        return c;
    }
}
