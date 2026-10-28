package com.medoedy.liber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.CatSlot;
import com.medoedy.liber.engine.Engine.Issue;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

/**
 * Альтернативный вид ростера — «как в книге правил»: Force Organisation Chart,
 * детачменты-рамки с кружками слотов (иконки родов войск), Prime-слоты с «компасом».
 */
final class OrgChart {
    private OrgChart() { }

    static boolean on(MainActivity a) { return a.prefs.getBoolean("roster_chart", false); }
    static void set(MainActivity a, boolean v) { a.prefs.edit().putBoolean("roster_chart", v).apply(); }

    // палитра «зелёного когитатора»
    static int line() { return Ui.dark ? 0xFF6FAE5C : 0xFF3C6A2A; }
    static int iconOn() { return Ui.dark ? 0xFFA8EC8A : 0xFF2C5A1A; }
    static int iconOff() { return Ui.dark ? 0x5A7FC06A : 0x553C6A2A; }
    static int fillOn() { return Ui.dark ? 0x3A5AA048 : 0x264E8A3A; }
    static int boxFill() { return Ui.dark ? 0xD80F1512 : 0xF2F3EAD6; }
    static int boxLine() { return Ui.dark ? 0xFF4E7A44 : 0xFF3A2A1A; }

    static String iconKey(String slot) {
        if (slot == null) return null;
        String b = base(slot);
        if (b.equals("high command")) return "high_command";
        if (b.equals("command")) return "command";
        if (b.equals("troops")) return "troops";
        if (b.equals("transport")) return "transport";
        if (b.equals("heavy transport")) return "heavy_transport";
        if (b.equals("warlord")) return "warlord";
        if (b.equals("retinue")) return "retinue";
        if (b.equals("lord of war")) return "lord_of_war";
        if (b.equals("support")) return "support";
        if (b.equals("armour")) return "armour";
        if (b.equals("war-engine") || b.equals("war engine")) return "war_engine";
        if (b.equals("recon")) return "recon";
        if (b.equals("heavy assault")) return "heavy_assault";
        if (b.equals("fast attack")) return "fast_attack";
        if (b.equals("elites")) return "elites";
        return null;
    }

    static String base(String slot) {
        return slot.replaceFirst("^Prime ", "").replaceAll(" - .*$", "").toLowerCase().trim();
    }

    static boolean prime(String slot) { return slot != null && slot.startsWith("Prime "); }

    // ================================================================ построение
    static void build(final RosterScreen rs, LinearLayout c) {
        MainActivity a = rs.a;
        List<Force> loose = new ArrayList<Force>();
        for (Force f : rs.r.forces) {
            if (rs.isContainer(f)) container(rs, c, f);
            else loose.add(f);
        }
        if (!loose.isEmpty()) {
            section(rs, c, "Прочие силы");
            Ui.Flow fl = flow(a);
            for (Force f : loose) fl.addView(box(rs, f, false));
            c.addView(fl, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        }
    }

    static Ui.Flow flow(MainActivity a) {
        Ui.Flow fl = new Ui.Flow(a);
        fl.gap = dp(6);
        return fl;
    }

    /** Армия / союзники: заголовок схемы, армейские опции, детачменты по разделам книги. */
    static void container(final RosterScreen rs, LinearLayout c, final Force f) {
        final MainActivity a = rs.a;
        Engine.Eff fe = rs.e.effForce(f);
        Data.Cat fc = rs.d.cats.get(f.catalogueId);
        boolean allied = f.catalogueId != null && !f.catalogueId.equals(rs.r.catalogueId);

        TextView t = Ui.text(a, allied ? "Союзники · " + rs.shortName(fc == null ? "" : fc.name) : fe.name, 18, Theme.namesBlack, Ui.BONE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(12), 0, 0);
        c.addView(t);
        TextView st = Ui.text(a, (allied ? fe.name + " · " : "") + MainActivity.pts(rs.e.forceCost(f, rs.d.pointsId)), 12, Theme.bodyBold, Ui.GOLD);
        st.setGravity(Gravity.CENTER);
        c.addView(st, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        rs.cards.put(f, t);

        // армейские опции (Allegiance и т.п.)
        for (Sel s : f.sels) c.addView(rs.unitRow(s), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        for (final Node n : rs.e.rootOptions(f)) {
            if (rs.e.countDirect(f, n.opt) > 0) continue;
            c.addView(Ui.button(a, "＋  " + n.eff.name, Ui.steelButton(rs.theme, dp(6)), Ui.BONE_DIM, 15, new View.OnClickListener() {
                public void onClick(View v) {
                    Sel s = rs.e.addUnit(f, n);
                    rs.changed();
                    a.show(new UnitScreen(a, rs, s));
                }
            }), Ui.lpm(-1, dp(44), 0, dp(4), 0, 0));
        }

        List<Force> primary = new ArrayList<Force>(), extra = new ArrayList<Force>(), aux = new ArrayList<Force>(), apex = new ArrayList<Force>();
        for (Force ch : f.children) {
            String nm = rs.e.effForce(ch).name;
            if (nm.contains("Primary")) primary.add(ch);
            else if (nm.startsWith("Auxiliary") || nm.startsWith("Mech -")) aux.add(ch);
            else if (nm.startsWith("Apex")) apex.add(ch);
            else extra.add(ch);
        }
        for (Force p : primary) c.addView(box(rs, p, true), Ui.lpm(-1, -2, 0, dp(6), 0, dp(4)));
        group(rs, c, "Дополнительные детачменты", extra);
        group(rs, c, "Вспомогательные детачменты", aux);
        group(rs, c, "Апекс-детачменты", apex);
        if (!rs.e.childForceOptions(f).isEmpty()) {
            TextView add = Ui.button(a, "＋  Детачмент", Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
                public void onClick(View v) { rs.addChildForce(f); }
            });
            c.addView(add, Ui.lpm(-1, dp(48), 0, dp(10), 0, dp(8)));
        }
    }

    static void group(RosterScreen rs, LinearLayout c, String title, List<Force> fs) {
        if (fs.isEmpty()) return;
        section(rs, c, title);
        Ui.Flow fl = flow(rs.a);
        for (Force f : fs) fl.addView(box(rs, f, false));
        c.addView(fl, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        for (Force f : fs) for (Force ch : f.children) {
            Ui.Flow sub = flow(rs.a);
            sub.addView(box(rs, ch, false));
            c.addView(sub, Ui.lpm(-1, -2, dp(14), dp(4), 0, dp(4)));
        }
    }

    /** Заголовок раздела как в книге: линия ———— и подпись капителью справа. */
    static void section(RosterScreen rs, LinearLayout c, String title) {
        LinearLayout row = Ui.row(rs.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View ln = new View(rs.a);
        ln.setBackgroundColor(boxLine());
        LinearLayout.LayoutParams lp = Ui.lpm(0, Math.max(1, dp(1.2f)), 0, 0, dp(8), 0);
        lp.weight = 1;
        row.addView(ln, lp);
        TextView t = Ui.text(rs.a, title.toUpperCase(), 14, Theme.names, Ui.BONE);
        t.setLetterSpacing(0.04f);
        row.addView(t);
        c.addView(row, Ui.lpm(-1, -2, 0, dp(16), 0, dp(6)));
    }

    // ================================================================ рамка детачмента
    static final class Group {
        CatSlot reg, prime;
        final List<Sel> units = new ArrayList<Sel>();
    }

    static View box(final RosterScreen rs, final Force f, boolean wide) {
        final MainActivity a = rs.a;
        final Engine e = rs.e;
        final Engine.Eff fe = e.effForce(f);
        LinearLayout b = Ui.col(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(boxFill());
        bg.setStroke(Math.max(1, dp(1.3f)), boxLine());
        bg.setCornerRadius(dp(2));
        b.setBackground(bg);
        b.setPadding(dp(10), dp(8), dp(10), dp(10));
        rs.cards.put(f, b);
        rs.shelves.add(b);

        // заголовок: ✕ слева, название справа (как в книге)
        LinearLayout hr = Ui.row(a);
        hr.setGravity(Gravity.TOP);
        if (f.parent != null || rs.r.forces.size() > 1) {
            View del = Ui.trashButton(a, new View.OnClickListener() {
                public void onClick(View v) {
                    a.confirm("Распустить «" + fe.name + "»?", "Все юниты в нём будут удалены.", "Распустить", rs.theme, new Runnable() {
                        public void run() { e.removeForce(f); rs.changed(); }
                    });
                }
            });
            hr.addView(del, Ui.lpm(dp(28), dp(28), 0, 0, dp(8), 0));
        }
        int probs = 0;
        Problems.P first = null;
        for (Problems.P p : rs.problems) if (p.force == f && p.unit == null) { probs++; if (first == null) first = p; }
        if (probs > 0) {
            final Problems.P fp = first;
            TextView w = Ui.text(a, "⚠ " + probs, 13, Theme.bodyBold, Ui.RED_BAD);
            w.setPadding(0, 0, dp(8), 0);
            w.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); rs.showProblem(fp); } });
            hr.addView(w);
        }
        LinearLayout tc = Ui.col(a);
        String nm = fe.name.replaceFirst("^(Auxiliary|Apex) - ", "");
        TextView t = Ui.text(a, nm, 14, Theme.names, Ui.BONE);
        t.setGravity(Gravity.RIGHT);
        t.setMaxWidth(dp(wide ? 320 : 240));
        tc.addView(t, Ui.lp(-1, -2));
        String sub = MainActivity.pts(e.forceCost(f, rs.d.pointsId));
        Data.Cat fc = rs.d.cats.get(f.catalogueId);
        if (f.parent != null && fc != null && !f.catalogueId.equals(f.parent.catalogueId)) sub = rs.shortName(fc.name) + " · " + sub;
        TextView pt = Ui.text(a, sub, 11, Theme.bodyBold, Ui.GOLD);
        pt.setGravity(Gravity.RIGHT);
        tc.addView(pt, Ui.lp(-1, -2));
        hr.addView(tc, Ui.weight(1));
        b.addView(hr, Ui.lp(-1, -2));

        // группы слотов
        List<CatSlot> slots = e.slots(f);
        List<Group> groups = new ArrayList<Group>();
        for (CatSlot s : slots) {
            if (prime(s.name)) continue;
            boolean show = !s.hidden && s.max != 0 || s.count > 0;
            if (!show) continue;
            Group g = new Group();
            g.reg = s;
            groups.add(g);
        }
        for (CatSlot s : slots) {
            if (!prime(s.name) || s.hidden || s.max == 0) continue;
            Group to = null;
            for (Group g : groups) if (g.reg != null && g.prime == null && base(g.reg.name).equals(base(s.name))) { to = g; break; }
            if (to == null) { to = new Group(); groups.add(to); }
            to.prime = s;
        }
        for (Sel s : f.sels) {
            String p = e.effSel(s).primary;
            Group to = null;
            for (Group g : groups) if ((g.reg != null && g.reg.categoryId.equals(p)) || (g.prime != null && g.prime.categoryId.equals(p))) { to = g; break; }
            if (to == null) {
                to = new Group();
                CatSlot cs = new CatSlot();
                cs.categoryId = p; cs.name = rs.d.categoryName(p); cs.max = -1;
                to.reg = cs;
                groups.add(to);
            }
            to.units.add(s);
        }

        java.util.Set<String> lbSlots = new java.util.HashSet<String>();
        for (Sel s : f.sels) {
            Sel lb = e.logisticalOf(s);
            String x = lb == null ? null : e.logisticalSlot(lb);
            if (x != null) lbSlots.add(x.toLowerCase());
        }
        int maxCols = 4;
        for (final Group g : groups) {
            final CatSlot main = g.reg != null ? g.reg : g.prime;
            // prime-юнит — первым
            List<Sel> order = new ArrayList<Sel>(g.units);
            boolean lbHere = lbSlots.contains(base(main.name)) && !main.name.contains(" - ");
            int cap = main.max < 0 ? order.size() + 1 : (int) main.max;
            if (g.reg == null && g.prime != null) cap = (int) Math.max(1, g.prime.max);
            int n = Math.max(cap, order.size());
            if (n == 0) continue;
            int needMin = (int) Math.max(0, main.min - order.size());
            LinearLayout row = null;
            for (int i = 0; i < n; i++) {
                if (i % maxCols == 0) {
                    row = Ui.row(a);
                    b.addView(row, Ui.lpm(-2, -2, 0, dp(6), 0, 0));
                }
                Sel s = i < order.size() ? order.get(i) : null;
                // шипы — юнит с Logistical Benefit; двойное кольцо — другая Prime-выгода; подпись LB — добавленный слот
                int mark = s == null ? (lbHere && i == n - 1 ? 3 : 0) : e.logisticalOf(s) != null ? 1 : hasPrime(e, s) ? 2 : 0;
                boolean over = main.max >= 0 && i >= main.max;
                boolean missing = s == null && i - order.size() < needMin;
                row.addView(cell(rs, f, main, g, s, mark, over || missing), Ui.lp(dp(68), -2));
            }
        }
        if (groups.isEmpty()) {
            TextView none = Ui.text(a, "Нет слотов", 12, Theme.body, Ui.BONE_DIM);
            b.addView(none);
        }
        b.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { return false; }
        });
        return b;
    }

    static boolean hasPrime(Engine e, Sel s) {
        for (Sel c : s.children) { String n = e.effSel(c).name; if (n != null && n.startsWith("Prime Unit")) return true; }
        return false;
    }

    static View cell(final RosterScreen rs, final Force f, final CatSlot slot, final Group g, final Sel s, int mark, boolean bad) {
        final MainActivity a = rs.a;
        LinearLayout cell = Ui.col(a);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean unitBad = false;
        if (s != null) for (Issue i : rs.issues) if (i.error && UnitScreen.belongs(i.where, s)) { unitBad = true; break; }
        SlotCircle sc = new SlotCircle(a, iconKey(slot.name), slot.name, s != null, mark == 1, bad || unitBad);
        sc.ring2 = mark == 2;
        cell.addView(sc, Ui.lp(dp(62), dp(62)));
        if (s != null) {
            Engine.Eff ef = rs.e.effSel(s);
            TextView nm = Ui.text(a, ef.name, 10, Theme.bodyBold, Ui.BONE);
            nm.setGravity(Gravity.CENTER);
            nm.setMaxLines(2);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(nm, Ui.lp(-1, -2));
            TextView pt = Ui.text(a, Engine.fmt(rs.e.points(s)), 10, Theme.namesBlack, Ui.dark ? 0xFFE07A62 : Ui.INK_RED);
            pt.setGravity(Gravity.CENTER);
            cell.addView(pt, Ui.lp(-1, -2));
        } else {
            String r = slot.name.contains(" - ") ? slot.name.replaceAll("^.* - ", "") : "";
            if (mark == 3) r = "Logistical Benefit";
            if (!r.isEmpty()) {
                TextView rt = Ui.text(a, r, 9, Theme.body, Ui.BONE_DIM);
                rt.setGravity(Gravity.CENTER);
                rt.setMaxLines(2);
                rt.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(rt, Ui.lp(-1, -2));
            }
        }
        cell.setPadding(0, 0, 0, dp(2));
        cell.setClickable(true);
        Ui.press(cell);
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.haptic(Ui.CLICK);
                if (s != null) { a.show(new UnitScreen(a, rs, s)); return; }
                PickerScreen ps = new PickerScreen(a, rs, f);
                ps.onlySlot = slot.categoryId;
                a.show(ps);
            }
        });
        if (s != null) cell.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { Ui.haptic(Ui.HEAVY); rs.unitMenu(s); return true; }
        });
        return cell;
    }

    // ================================================================ кружок слота
    static final class SlotCircle extends View {
        final Bitmap icon;
        final String glyph;
        final boolean filled, prime, bad;
        boolean ring2;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        SlotCircle(Context c, String key, String slotName, boolean filled, boolean prime, boolean bad) {
            super(c);
            icon = key == null ? null : Art.get("slot_" + key);
            glyph = Glyph.forCategory(slotName);
            this.filled = filled; this.prime = prime; this.bad = bad;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            float r = Math.min(w, h) / 2 * 0.74f;
            int ln = bad ? Ui.RED_BAD : line();
            if (prime) {
                // «компас» Prime-слота: 4 длинных и 4 коротких луча
                p.setStyle(Paint.Style.FILL);
                p.setColor(ln);
                Path path = new Path();
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI / 4 * i - Math.PI / 2;
                    float len = i % 2 == 0 ? r * 1.33f : r * 1.16f;
                    float half = (float) (Math.PI / (i % 2 == 0 ? 14 : 18));
                    path.moveTo(cx + (float) Math.cos(ang) * len, cy + (float) Math.sin(ang) * len);
                    path.lineTo(cx + (float) Math.cos(ang - half) * r * 0.98f, cy + (float) Math.sin(ang - half) * r * 0.98f);
                    path.lineTo(cx + (float) Math.cos(ang + half) * r * 0.98f, cy + (float) Math.sin(ang + half) * r * 0.98f);
                    path.close();
                }
                c.drawPath(path, p);
            }
            if (filled) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(fillOn());
                c.drawCircle(cx, cy, r, p);
            } else if (!Ui.dark) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x33FFFFFF);
                c.drawCircle(cx, cy, r, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(prime ? 2.6f : 2.2f));
            p.setColor(ln);
            c.drawCircle(cx, cy, r, p);
            if (ring2) {
                p.setStrokeWidth(dp(1.2f));
                c.drawCircle(cx, cy, r * 1.17f, p);
            }
            p.setStyle(Paint.Style.FILL);
            int ic = filled ? iconOn() : iconOff();
            if (bad && !filled) ic = (Ui.RED_BAD & 0x00FFFFFF) | 0x88000000;
            if (icon != null) {
                p.setColorFilter(new PorterDuffColorFilter(ic, PorterDuff.Mode.SRC_IN));
                if (filled && Ui.dark) p.setShadowLayer(dp(5), 0, 0, 0xAA6FE050);
                float side = r * 2.5f;
                c.drawBitmap(icon, null, new RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2), p);
                p.setColorFilter(null);
                p.clearShadowLayer();
            } else {
                Glyph.draw(c, glyph, cx, cy, r * 0.5f, ic);
            }
        }
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> unused;
}
