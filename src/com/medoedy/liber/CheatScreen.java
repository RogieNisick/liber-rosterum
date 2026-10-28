package com.medoedy.liber;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.medoedy.liber.Ui.dp;

/**
 * Шпаргалка на игру: всё, что легко забыть, на одном экране.
 * Юниты — характеристики, оружие, особые правила одним взглядом; правила армии по алфавиту;
 * реакции, снаряжение и психосилы; «★ Важное» — свои закладки. Поиск по всему.
 */
final class CheatScreen extends Screen {
    final RosterScreen rs;
    final Engine e;
    final Data d;
    int tab;
    String filter = "";
    final Set<String> open = new HashSet<String>();
    boolean expandAll;
    LinearLayout list;

    /** Запись шпаргалки: правило, реакция, снаряжение и т.п. */
    static final class Item {
        String key, title, kind, text, summary;
        final Set<String> users = new LinkedHashSet<String>();
    }

    /** Юнит: профиль, оружие, ссылки на правила. */
    static final class UnitInfo {
        Sel sel;
        String name, force, stats, prime;
        double pts;
        int models;
        final List<String[]> weapons = new ArrayList<String[]>(); // {имя, строка характеристик, спецправила}
        final Set<String> rules = new LinkedHashSet<String>();    // ключи Item
        final Set<String> extras = new LinkedHashSet<String>();   // ключи реакций/снаряжения
        final Set<String> weaponRules = new HashSet<String>();    // правила, которые относятся к оружию
    }

    final Map<String, Item> items = new LinkedHashMap<String, Item>();
    final List<UnitInfo> units = new ArrayList<UnitInfo>();
    final List<String> army = new ArrayList<String>(); // ключи правил армии (Rite of War и т.п.)
    Map<String, Map<String, Object>> ruleIndex;

    static final String[] TABS = {"Юниты", "Правила", "Реакции и снаряжение", "★ Важное"};

    CheatScreen(MainActivity a, RosterScreen rs) {
        super(a);
        this.rs = rs;
        this.e = rs.e;
        this.d = rs.d;
        theme = rs.theme;
        collect();
    }

    // ================================================================ сбор данных
    void collect() {
        ruleIndex = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> r : d.rules.values()) {
            String n = Data.clean(Data.str(r, "name"));
            if (n != null) ruleIndex.put(base(n), r);
        }
        List<Force> fs = new ArrayList<Force>();
        rs.r.allForces(fs);
        for (Force f : fs) {
            String fname = e.effForce(f).name;
            for (Sel s : f.sels) {
                String nm = e.effSel(s).name;
                if (!f.children.isEmpty() || nm.equals("Allegiance") || nm.startsWith("Rite of War")) {
                    // армейские выборы: их правила — в раздел «Армия»
                    Set<String> keys = new LinkedHashSet<String>();
                    walk(s, null, keys, null, "Армия");
                    for (String k : keys) if (!army.contains(k)) army.add(k);
                    continue;
                }
                UnitInfo u = new UnitInfo();
                u.sel = s;
                u.name = nm;
                u.force = fname;
                u.pts = e.points(s);
                for (Sel ch : s.children) if ("model".equals(Data.str(e.entryOf(ch), "type"))) u.models += ch.number;
                walk(s, u, u.rules, u.extras, nm);
                units.add(u);
            }
        }
    }

    static String base(String n) { return n.toLowerCase().replaceAll("\\s*\\(.*", "").trim(); }

    void walk(Sel s, UnitInfo u, Set<String> ruleKeys, Set<String> extraKeys, String user) {
        String sn = e.effSel(s).name;
        if (u != null && sn.equals("Prime Unit")) for (Sel b : s.children) u.prime = e.effSel(b).name;
        for (Map<String, Object> r : e.rulesOf(e.entryOf(s), e.linkOf(s))) {
            String n = Data.clean(Data.str(r, "name"));
            if (n == null || n.isEmpty()) continue;
            String k = "r|" + base(n);
            Item it = items.get(k);
            if (it == null) { it = new Item(); it.key = k; it.title = n; it.kind = "Правило"; it.text = Data.str(r, "description"); items.put(k, it); }
            it.users.add(user);
            ruleKeys.add(k);
        }
        for (Map<String, Object> p : e.profilesOf(e.entryOf(s), e.linkOf(s))) {
            String type = Data.clean(Data.str(p, "typeName"));
            String pn = Data.clean(Data.str(p, "name"));
            if (type == null || pn == null) continue;
            Map<String, String> ch = new LinkedHashMap<String, String>();
            for (Map<String, Object> c : Data.list(p, "characteristics")) {
                String k = Data.str(c, "name"), v = Data.str(c, "$text");
                if (k != null && v != null) ch.put(k.replace(":", "").trim(), v.trim());
            }
            String tl = type.toLowerCase();
            if (tl.contains("weapon")) {
                if (u == null) continue;
                boolean dup = false;
                for (String[] w : u.weapons) if (w[0].equals(pn)) dup = true;
                if (dup) continue;
                StringBuilder line = new StringBuilder();
                String[] keys = tl.contains("melee") ? new String[]{"IM", "AM", "SM", "AP", "D"} : new String[]{"R", "FP", "RS", "AP", "D"};
                for (String k : keys) if (ch.containsKey(k)) { if (line.length() > 0) line.append(" · "); line.append(k).append(' ').append(ch.get(k)); }
                String sr = ch.containsKey("Special Rules") ? ch.get("Special Rules") : "";
                u.weapons.add(new String[]{pn, line.toString(), sr});
                // спецправила оружия — тоже в шпаргалку
                for (String part : sr.split(",")) {
                    String rn = part.trim();
                    if (rn.isEmpty() || rn.equals("-")) continue;
                    Map<String, Object> rr = ruleIndex.get(base(rn));
                    String k = "r|" + base(rn);
                    Item it = items.get(k);
                    if (it == null) { it = new Item(); it.key = k; it.title = rn.replaceAll("\\s*\\(.*", ""); it.kind = "Правило оружия"; it.text = rr == null ? null : Data.str(rr, "description"); items.put(k, it); }
                    it.users.add(user);
                    ruleKeys.add(k);
                    u.weaponRules.add(k);
                }
            } else if (tl.equals("profile") || tl.contains("vehicle") || tl.contains("knight")) {
                if (u != null && u.stats == null) u.stats = statLine(ch);
            } else if (tl.contains("reaction") || tl.contains("wargear") || tl.contains("psychic") || tl.contains("gambit") || tl.contains("rite") || tl.contains("asset")) {
                String k = "p|" + pn.toLowerCase().trim();
                Item it = items.get(k);
                if (it == null) {
                    it = new Item();
                    it.key = k;
                    it.title = pn.trim();
                    it.kind = UnitScreen.typeRu(type);
                    StringBuilder t = new StringBuilder();
                    for (Map.Entry<String, String> en : ch.entrySet()) {
                        if (en.getValue().isEmpty()) continue;
                        if (en.getKey().equals("Summary")) { it.summary = en.getValue(); continue; }
                        if (t.length() > 0) t.append("\n\n");
                        t.append(en.getKey().toUpperCase()).append(": ").append(en.getValue());
                    }
                    it.text = t.toString();
                    items.put(k, it);
                }
                it.users.add(user);
                if (extraKeys != null) extraKeys.add(k); else ruleKeys.add(k);
            }
        }
        for (Sel ch : s.children) walk(ch, u, ruleKeys, extraKeys, user);
    }

    static String statLine(Map<String, String> ch) {
        String[] ks = ch.containsKey("Front Armour") ? new String[]{"M", "BS", "Front Armour", "Side Armour", "Rear Armour", "HP"}
                : new String[]{"M", "WS", "BS", "S", "T", "W", "I", "A", "LD", "SAV", "INV"};
        StringBuilder b = new StringBuilder();
        for (String k : ks) {
            String v = ch.get(k);
            if (v == null || v.isEmpty() || v.equals("-")) continue;
            String lab = k.equals("Front Armour") ? "Фронт" : k.equals("Side Armour") ? "Борт" : k.equals("Rear Armour") ? "Корма" : k.equals("SAV") ? "Sv" : k.equals("INV") ? "Inv" : k.equals("LD") ? "Ld" : k;
            if (b.length() > 0) b.append("  ");
            b.append(lab).append(' ').append(v);
        }
        return b.toString();
    }

    // ================================================================ избранное
    Set<String> favs() {
        return new HashSet<String>(a.prefs.getStringSet("cheat_fav", new HashSet<String>()));
    }

    void toggleFav(String key) {
        Set<String> f = favs();
        if (!f.remove(key)) f.add(key);
        a.prefs.edit().putStringSet("cheat_fav", f).apply();
    }

    // ================================================================ экран
    @Override
    View build() {
        FrameLayout fr = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Шпаргалка", rs.r.name, theme, true));
        EditText search = a.input(filter, "Поиск: правило, юнит, оружие…", false);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(Editable s) { filter = s.toString().trim().toLowerCase(); fill(); }
        });
        c.addView(search, Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
        Ui.Flow tabs = new Ui.Flow(a);
        tabs.gap = dp(6);
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            boolean on = tab == i;
            TextView tv = Ui.text(a, TABS[i], 14, Theme.bodyBold, on ? Theme.onColor(theme.accent) : Ui.BONE);
            tv.setBackground(on ? Ui.heraldic(theme, dp(5)) : Ui.steelButton(theme, dp(5)));
            tv.setPadding(dp(12), dp(8), dp(12), dp(9));
            tv.setClickable(true);
            Ui.press(tv);
            tv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("page", 0.5f, 1.1f); tab = t; rebuild(); } });
            tabs.addView(tv);
        }
        c.addView(tabs, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        list = Ui.col(a);
        c.addView(list);
        fill();
        fr.addView(a.scroll(c));
        return fr;
    }

    boolean match(String... parts) {
        if (filter.isEmpty()) return true;
        for (String p : parts) if (p != null && p.toLowerCase().contains(filter)) return true;
        return false;
    }

    void fill() {
        if (list == null) return;
        list.removeAllViews();
        if (tab == 0) fillUnits();
        else if (tab == 1) fillItems(false, false);
        else if (tab == 2) fillItems(true, false);
        else fillItems(false, true);
        if (list.getChildCount() == 0) {
            TextView t = Ui.text(a, tab == 3 ? "Здесь будут ваши закладки. Нажмите ☆ у любого правила, реакции или снаряжения, которые вы постоянно забываете." : "Ничего не найдено.", 14, Theme.body, Ui.BONE);
            t.setPadding(dp(6), dp(16), dp(6), dp(16));
            list.addView(t);
        }
    }

    // ---------------------------------------------------------------- юниты
    void fillUnits() {
        if (filter.isEmpty()) {
            TextView ex = Ui.text(a, expandAll ? "Свернуть все ▴" : "Развернуть все ▾", 13, Theme.bodyBold, Ui.GOLD);
            ex.setGravity(Gravity.END);
            ex.setPadding(0, 0, dp(4), dp(4));
            ex.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { expandAll = !expandAll; open.clear(); fill(); } });
            list.addView(ex, Ui.lpm(-1, -2, 0, 0, 0, 0));
        }
        if (!army.isEmpty() && match(joinTitles(army))) {
            LinearLayout ac = Ui.col(a);
            ac.setBackground(Ui.parchment());
            ac.setPadding(dp(12), dp(10), dp(12), dp(10));
            ac.addView(cap("ПРАВИЛА АРМИИ"));
            Ui.Flow fl = new Ui.Flow(a);
            fl.gap = dp(5);
            for (String k : army) { Item it = items.get(k); if (it != null) fl.addView(ruleChip(it)); }
            ac.addView(fl, Ui.lpm(-1, -2, 0, dp(4), 0, 0));
            list.addView(ac, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
        String lastForce = null;
        for (final UnitInfo u : units) {
            StringBuilder hay = new StringBuilder(u.name);
            for (String[] w : u.weapons) hay.append(' ').append(w[0]).append(' ').append(w[2]);
            for (String k : u.rules) { Item it = items.get(k); if (it != null) hay.append(' ').append(it.title); }
            for (String k : u.extras) { Item it = items.get(k); if (it != null) hay.append(' ').append(it.title); }
            if (!match(hay.toString())) continue;
            if (!u.force.equals(lastForce) && filter.isEmpty()) {
                lastForce = u.force;
                list.addView(a.label(u.force.toUpperCase(), Ui.GOLD));
            }
            list.addView(unitCard(u), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
    }

    String joinTitles(List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (String k : keys) { Item it = items.get(k); if (it != null) b.append(it.title).append(' '); }
        return b.toString();
    }

    View unitCard(final UnitInfo u) {
        final String key = "u|" + System.identityHashCode(u.sel);
        boolean opened = expandAll || open.contains(key) || !filter.isEmpty();
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.parchment());
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout hd = Ui.row(a);
        Ui.Hex hx = new Ui.Hex(a, theme, Units.kind(e, e.entryOf(u.sel), e.linkOf(u.sel)));
        hx.photo = a.unitArt(u.sel.entryId, rs.factionName(u.sel.force));
        hd.addView(hx, Ui.lpm(dp(40), dp(40), 0, 0, dp(10), 0));
        LinearLayout ht = Ui.col(a);
        ht.addView(Ui.text(a, u.name, 16, Theme.names, Ui.INK));
        String sub = (u.models > 0 ? u.models + " " + MainActivity.plural(u.models, "модель", "модели", "моделей") + " · " : "") + Engine.fmt(u.pts) + " оч.";
        ht.addView(Ui.text(a, sub, 12, Theme.bodyBold, Ui.INK_DIM));
        hd.addView(ht, Ui.weight(1));
        hd.addView(Ui.text(a, opened ? "▴" : "▾", 18, Theme.bodyBold, Ui.INK_DIM));
        card.addView(hd);
        hd.setClickable(true);
        hd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (!open.remove(key)) open.add(key); Sfx.play("page", 0.4f, 1.2f); fill(); }
        });
        if (u.prime != null) {
            TextView pr = Ui.text(a, "★ Прайм: " + u.prime, 13, Theme.bodyBold, 0xFF8A5A10);
            pr.setPadding(0, dp(4), 0, 0);
            pr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Item it = findByTitle(u.prime); if (it != null) showItem(it); } });
            card.addView(pr);
        }
        if (u.stats != null) {
            TextView st = Ui.text(a, u.stats, 13, Theme.bodyBold, Ui.INK);
            st.setPadding(dp(8), dp(5), dp(8), dp(6));
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(0x1A000000);
            g.setCornerRadius(dp(6));
            st.setBackground(g);
            card.addView(st, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        }
        // особые правила — всегда видны: именно их и забывают
        if (!u.rules.isEmpty()) {
            Ui.Flow fl = new Ui.Flow(a);
            fl.gap = dp(5);
            for (String k : u.rules) {
                Item it = items.get(k);
                if (it == null || u.weaponRules.contains(k)) continue;
                fl.addView(ruleChip(it));
            }
            if (fl.getChildCount() > 0) card.addView(fl, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        }
        if (opened) {
            for (String[] w : u.weapons) {
                LinearLayout wr = Ui.col(a);
                wr.setPadding(0, dp(6), 0, 0);
                LinearLayout top = Ui.row(a);
                top.addView(new RosterScreen.GlyphView(a, w[1].startsWith("IM") ? "sword" : "cannon", 0xFF8A3A1A), Ui.lpm(dp(18), dp(18), 0, 0, dp(6), 0));
                top.addView(Ui.text(a, w[0], 14, Theme.bodyBold, Ui.INK), Ui.weight(1));
                wr.addView(top);
                if (!w[1].isEmpty()) wr.addView(Ui.text(a, w[1], 12, Theme.body, Ui.INK_DIM), Ui.lpm(-1, -2, dp(24), 0, 0, 0));
                if (!w[2].isEmpty() && !w[2].equals("-")) {
                    Ui.Flow fl = new Ui.Flow(a);
                    fl.gap = dp(4);
                    for (String part : w[2].split(",")) {
                        final String rn = part.trim();
                        if (rn.isEmpty()) continue;
                        final Item it = items.get("r|" + base(rn));
                        TextView ch = smallChip(rn);
                        ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (it != null) showItem(it); } });
                        fl.addView(ch);
                    }
                    wr.addView(fl, Ui.lpm(-1, -2, dp(24), dp(3), 0, 0));
                }
                card.addView(wr);
            }
            if (!u.extras.isEmpty()) {
                TextView xh = cap("РЕАКЦИИ И СНАРЯЖЕНИЕ");
                xh.setPadding(0, dp(8), 0, dp(2));
                card.addView(xh);
                for (String k : u.extras) {
                    final Item it = items.get(k);
                    if (it == null) continue;
                    TextView t = Ui.text(a, "› " + it.title + (it.summary != null ? " — " + it.summary : ""), 13, Theme.body, Ui.INK);
                    t.setPadding(0, dp(3), 0, dp(3));
                    t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showItem(it); } });
                    card.addView(t);
                }
            }
        }
        return card;
    }

    Item findByTitle(String title) {
        for (Item it : items.values()) if (it.title.equalsIgnoreCase(title)) return it;
        return null;
    }

    TextView ruleChip(final Item it) {
        boolean fav = favs().contains(it.key);
        TextView t = Ui.text(a, (fav ? "★ " : "") + it.title, 13, Theme.bodyBold, Ui.INK);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fav ? 0x33B08A20 : 0x14000000);
        g.setStroke(dp(1), fav ? 0xFFB08A20 : 0x668A6A3A);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        t.setPadding(dp(10), dp(4), dp(10), dp(5));
        t.setClickable(true);
        Ui.press(t);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); showItem(it); } });
        return t;
    }

    TextView smallChip(String s) {
        TextView t = Ui.text(a, s, 12, Theme.bodyBold, 0xFF5A2A10);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x10000000);
        g.setStroke(dp(1), 0x448A3A1A);
        g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setPadding(dp(8), dp(2), dp(8), dp(3));
        t.setClickable(true);
        return t;
    }

    TextView cap(String s) {
        TextView t = Ui.text(a, s, 11, Theme.bodyBold, Ui.INK_RED);
        t.setLetterSpacing(0.12f);
        return t;
    }

    // ---------------------------------------------------------------- правила / реакции / важное
    void fillItems(boolean extras, boolean favOnly) {
        Set<String> f = favs();
        List<Item> its = new ArrayList<Item>();
        for (Item it : items.values()) {
            boolean isProfile = it.key.startsWith("p|");
            if (favOnly ? !f.contains(it.key) : (extras != isProfile)) continue;
            if (!match(it.title, it.text, it.summary, android.text.TextUtils.join(" ", it.users))) continue;
            its.add(it);
        }
        Collections.sort(its, new Comparator<Item>() { public int compare(Item x, Item y) { return x.title.compareToIgnoreCase(y.title); } });
        // закладки — наверху
        final Set<String> ff = f;
        Collections.sort(its, new Comparator<Item>() { public int compare(Item x, Item y) { return Boolean.compare(ff.contains(y.key), ff.contains(x.key)); } });
        for (Item it : its) list.addView(itemCard(it, f.contains(it.key)), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
    }

    View itemCard(final Item it, boolean fav) {
        final String key = "i|" + it.key;
        boolean opened = open.contains(key) || (!filter.isEmpty() && it.title.toLowerCase().contains(filter));
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.parchment());
        card.setPadding(dp(12), dp(9), dp(8), dp(10));
        LinearLayout hd = Ui.row(a);
        LinearLayout tt = Ui.col(a);
        tt.addView(Ui.text(a, it.title, 15, Theme.names, Ui.INK));
        tt.addView(Ui.text(a, it.kind + " · " + usersLine(it), 12, Theme.body, Ui.INK_DIM));
        hd.addView(tt, Ui.weight(1));
        TextView star = Ui.text(a, fav ? "★" : "☆", 22, Theme.bodyBold, fav ? 0xFFB08A20 : Ui.INK_DIM);
        star.setPadding(dp(10), 0, dp(6), 0);
        star.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("seal", 0.5f, 1.3f); toggleFav(it.key); fill(); } });
        hd.addView(star);
        card.addView(hd);
        hd.setClickable(true);
        hd.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!open.remove(key)) open.add(key); fill(); } });
        if (it.summary != null && !opened) {
            TextView s = Ui.text(a, it.summary, 13, Theme.body, Ui.INK);
            s.setPadding(0, dp(4), dp(6), 0);
            card.addView(s);
        }
        if (opened) {
            if (it.summary != null) {
                TextView s = Ui.text(a, "", 14, Theme.bodyBold, Ui.INK);
                s.setPadding(0, dp(6), dp(6), 0);
                card.addView(s);
                TrText.bind(s, it.summary);
            }
            TextView t = Ui.text(a, "", 14, Theme.body, Ui.INK);
            t.setLineSpacing(dp(2), 1f);
            t.setPadding(0, dp(6), dp(6), 0);
            card.addView(t);
            if (it.text == null || it.text.isEmpty()) t.setText("Текста нет в данных — откройте в книге.");
            else TrText.bind(t, it.text);
            card.addView(LibraryScreen.bookButton(a, it.title, (rs.r.forces.isEmpty() ? "" : rs.factionName(rs.r.forces.get(0))), theme, "📖  В книге"), Ui.lpm(-2, dp(36), 0, dp(8), 0, 0));
        }
        return card;
    }

    String usersLine(Item it) {
        List<String> u = new ArrayList<String>(it.users);
        if (u.size() <= 3) return android.text.TextUtils.join(", ", u);
        return android.text.TextUtils.join(", ", u.subList(0, 3)) + " и ещё " + (u.size() - 3);
    }

    void showItem(final Item it) {
        LinearLayout body = a.openSheet(it.title, it.kind + " · " + usersLine(it), theme);
        LinearLayout sh = Ui.col(a);
        sh.setBackground(Ui.parchment());
        sh.setPadding(dp(14), dp(12), dp(14), dp(14));
        if (it.summary != null) {
            TextView s = Ui.text(a, "", 15, Theme.bodyBold, Ui.INK);
            sh.addView(s);
            TrText.bind(s, it.summary);
        }
        TextView t = Ui.text(a, "", 15, Theme.body, Ui.INK);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, dp(6), 0, 0);
        sh.addView(t);
        if (it.text == null || it.text.isEmpty()) t.setText("Текста нет в данных — откройте в книге.");
        else TrText.bind(t, it.text);
        body.addView(sh);
        final boolean fav = favs().contains(it.key);
        body.addView(Ui.button(a, fav ? "★  Убрать из важного" : "☆  В «Важное»", Ui.steelButton(theme, dp(6)), Ui.GOLD, 15, new View.OnClickListener() {
            public void onClick(View v) { toggleFav(it.key); a.closeSheet(); Sfx.play("seal", 0.5f, 1.3f); fill(); }
        }), Ui.lpm(-1, dp(46), 0, dp(10), 0, 0));
        body.addView(LibraryScreen.bookButton(a, it.title, (rs.r.forces.isEmpty() ? "" : rs.factionName(rs.r.forces.get(0))), theme, "📖  Открыть в книге"), Ui.lpm(-1, dp(46), 0, dp(6), 0, 0));
    }
}
