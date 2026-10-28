package com.medoedy.liber;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.CatSlot;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

/** Выбор юнита для детачмента: по слотам, с поиском, силуэтами и ценой. */
final class PickerScreen extends Screen {
    final RosterScreen rs;
    final Force f;
    LinearLayout list;
    List<Node> all;
    String filter = "";
    String onlySlot; // показать только этот слот (из схемы «как в книге»)

    PickerScreen(MainActivity a, RosterScreen rs, Force f) {
        super(a);
        this.rs = rs;
        this.f = f;
        theme = rs.theme;
    }

    View build() {
        FrameLayout fr = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Призвать юнит", rs.e.effForce(f).name, theme, true));
        EditText search = a.input("", "Поиск по названию…", false);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(Editable s) { filter = s.toString().trim().toLowerCase(); fill(); }
        });
        c.addView(search, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        list = Ui.col(a);
        c.addView(list);
        all = rs.e.rootOptions(f);
        loadCosts(a);
        fill();
        fr.addView(a.scroll(c));
        return fr;
    }

    static final java.util.Map<String, Double> COST = new java.util.concurrent.ConcurrentHashMap<String, Double>();
    private static boolean costLoaded;
    private static volatile boolean costDirty;
    private final java.util.concurrent.LinkedBlockingDeque<Object[]> jobs = new java.util.concurrent.LinkedBlockingDeque<Object[]>();
    private Thread worker;
    volatile boolean alive = true;

    static java.io.File costFile(MainActivity a) { return new java.io.File(a.getFilesDir(), "costs_" + (Store.data == null ? 0 : Store.data.revision) + ".json"); }

    /** Цены, посчитанные раньше, хранятся на диске — второй раз ничего не считается. */
    @SuppressWarnings("unchecked")
    static synchronized void loadCosts(MainActivity a) {
        if (costLoaded) return;
        costLoaded = true;
        try {
            java.io.File f = costFile(a);
            if (!f.exists()) return;
            Map<String, Object> m = (Map<String, Object>) com.medoedy.liber.engine.Json.parse(Store.read(f));
            for (Map.Entry<String, Object> en : m.entrySet()) if (en.getValue() instanceof Number) COST.put(en.getKey(), ((Number) en.getValue()).doubleValue());
        } catch (Throwable ignored) { }
    }

    static synchronized void saveCosts(MainActivity a) {
        if (!costDirty) return;
        costDirty = false;
        try {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Double> en : COST.entrySet()) m.put(en.getKey(), en.getValue());
            Store.write(costFile(a), com.medoedy.liber.engine.Json.write(m));
        } catch (Throwable ignored) { }
    }

    /** Цена юнита «как добавленного» считается в фоне на копии ростера — экран не тормозит, настоящий ростер не трогается. */
    void queue(String key, Node n, TextView tv) {
        jobs.add(new Object[]{key, n.opt.id() + "|" + n.place, tv, n});
        if (worker == null) startWorker();
    }

    private void startWorker() {
        final Roster clone;
        final int idx;
        try {
            clone = Roster.fromJson(rs.r.toJson());
            List<Force> mine = new ArrayList<Force>();
            rs.r.allForces(mine);
            idx = mine.indexOf(f);
        } catch (Throwable t) { return; }
        worker = new Thread(new Runnable() {
            public void run() {
                Engine e2 = null;
                Force f2 = null;
                Map<String, Node> nodes = new java.util.HashMap<String, Node>();
                try {
                    e2 = new Engine(rs.d, clone);
                    List<Force> fs = new ArrayList<Force>();
                    clone.allForces(fs);
                    f2 = idx >= 0 && idx < fs.size() ? fs.get(idx) : null;
                    if (f2 != null) for (Node n : e2.rootOptions(f2)) nodes.put(n.opt.id() + "|" + n.place, n);
                } catch (Throwable ignored) { }
                while (alive) {
                    Object[] j;
                    try { j = jobs.poll(1500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ie) { break; }
                    if (j == null) { saveCosts(a); continue; }
                    final String key = (String) j[0];
                    Double v = COST.get(key);
                    if (v == null) {
                        try {
                            Node n2 = nodes.get((String) j[1]);
                            if (n2 == null || e2 == null) throw new IllegalStateException();
                            Sel s2 = e2.addUnit(f2, n2);
                            v = e2.points(s2);
                            e2.removeSel(s2);
                        } catch (Throwable t) { v = ((Node) j[3]).eff.cost(rs.d.pointsId); }
                        COST.put(key, v);
                        costDirty = true;
                    }
                    final TextView tv = (TextView) j[2];
                    final double fv = v;
                    a.h.post(new Runnable() { public void run() { tv.setText(fv > 0 ? Engine.fmt(fv) : "0"); } });
                }
                saveCosts(a);
            }
        }, "picker-costs");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    boolean handleBack() { alive = false; return false; }

    void fill() {
        jobs.clear();
        list.removeAllViews();
        Map<String, List<Node>> by = new LinkedHashMap<String, List<Node>>();
        Map<String, CatSlot> slotMap = new LinkedHashMap<String, CatSlot>();
        for (CatSlot s : rs.e.slots(f)) { slotMap.put(s.categoryId, s); by.put(s.categoryId, new ArrayList<Node>()); }
        boolean slotHas = false;
        if (onlySlot != null) for (Node n : all) if (onlySlot.equals(n.place)) { slotHas = true; break; }
        for (Node n : all) {
            if (!filter.isEmpty() && !n.eff.name.toLowerCase().contains(filter)) continue;
            if (slotHas && !onlySlot.equals(n.place)) continue;
            List<Node> l = by.get(n.place);
            if (l != null) l.add(n);
        }
        for (Map.Entry<String, List<Node>> en : by.entrySet()) {
            if (en.getValue().isEmpty()) continue;
            CatSlot s = slotMap.get(en.getKey());
            String lim = s.max < 0 ? "" : "   " + Engine.fmt(s.count) + "/" + Engine.fmt(s.max);
            LinearLayout hdr = Ui.row(a);
            hdr.setPadding(dp(2), dp(12), 0, dp(4));
            hdr.addView(new RosterScreen.GlyphView(a, Glyph.forCategory(s.name), Ui.GOLD), Ui.lpm(dp(24), dp(24), 0, 0, dp(8), 0));
            TextView ht = Ui.title(a, Units.slotRu(s.name).toUpperCase() + lim, 15, Ui.GOLD);
            ht.setLetterSpacing(0.06f);
            hdr.addView(ht);
            list.addView(hdr);
            for (final Node n : en.getValue()) {
                LinearLayout row = Ui.row(a);
                row.setBackground(Ui.states(new Ui.Parchment(dp(6), false, 0, false), new Ui.Parchment(dp(6), false, 0, true)));
                row.setPadding(dp(8), dp(7), dp(14), dp(8));
                Ui.Hex hx = new Ui.Hex(a, theme, Units.kind(rs.e, n.opt.entry, n.opt.link));
                hx.photo = a.unitArt(n.opt.id(), rs.factionName(f));
                row.addView(hx, Ui.lpm(dp(42), dp(42), 0, 0, dp(10), 0));
                row.addView(Ui.text(a, n.eff.name, 16, Theme.names, Ui.INK), Ui.weight(1));
                // настоящая стоимость — с обязательными моделями и снаряжением по умолчанию
                final String key = f.catalogueId + "|" + f.entryId + "|" + n.opt.id() + "|" + n.place;
                Double known = COST.get(key);
                final TextView price = Ui.text(a, known != null ? Engine.fmt(known) : "…", 17, Theme.namesBlack, Ui.INK_RED);
                row.addView(price);
                if (known == null) queue(key, n, price);
                row.addView(a.cameraButton(n.opt.id(), n.eff.name, rs.factionName(f), theme, null), Ui.lpm(dp(36), dp(36), dp(8), 0, 0, 0));
                row.setClickable(true);
                Ui.press(row);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Ui.haptic(Ui.CLICK);
                        Sfx.play("click");
                        Sel s = rs.e.addUnit(f, n);
                        rs.e.changed();
                        a.show(new UnitScreen(a, rs, s, PickerScreen.this));
                    }
                });
                list.addView(row, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
            }
        }
        if (list.getChildCount() == 0) {
            TextView e = Ui.text(a, filter.isEmpty() ? "В этот детачмент сейчас нечего добавить." : "Ничего не найдено.", 15, Theme.body, Ui.BONE_DIM);
            e.setPadding(dp(6), dp(20), dp(6), dp(20));
            list.addView(e);
        }
    }
}
