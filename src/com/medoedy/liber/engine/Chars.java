package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Engine.Eff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Все персонажи фракции прямо из каталога (ростеры не нужны): Warlord, High Command, Command.
 * Характеристики ищутся в профилях записи и её вложенных моделей; техника (без WS) отбрасывается.
 */
public final class Chars {
    public static final class C {
        public String name, slot, entryId, stats, type;
        public int ws, s, t, w, i, a;
        public boolean command;
    }

    public final List<C> list = new ArrayList<C>();
    final Data d;

    public Chars(Data d, String catId) {
        this.d = d;
        Data.Cat cat = d.cats.get(catId);
        if (cat == null) return;
        Roster r = new Roster();
        r.catalogueId = catId;
        r.limit = 99999;
        Engine e = new Engine(d, r);
        Object ctx = null;
        try {
            Engine.ForceOpt foc = null;
            for (Engine.ForceOpt o : e.rootForceOptions(catId)) if (o.name().startsWith("Crusade")) { foc = o; break; }
            if (foc == null) for (Engine.ForceOpt o : e.rootForceOptions(catId)) { foc = o; break; }
            if (foc != null) {
                Roster.Force army = e.addForce(null, foc, catId);
                ctx = army;
                for (Engine.ForceOpt o : e.childForceOptions(army)) if (o.name().contains("Primary")) { ctx = e.addForce(army, o, null); break; }
            }
        } catch (Exception ignored) { }
        Set<String> seen = new HashSet<String>();
        for (Opt o : cat.roots) {
            Eff ef;
            try { ef = e.eff(o.entry, o.link, e.candidate(ctx)); } catch (Exception ex) { continue; }
            String slot = ef.primary == null ? "" : d.categoryName(ef.primary);
            String nm = ef.name == null ? Data.str(o.entry, "name") : ef.name;
            if (nm == null) continue;
            boolean cmd = slot.equals("Warlord") || slot.equals("High Command") || slot.equals("Command") || slot.startsWith("Command -") || slot.equals("HQ");
            if (!cmd && !nm.contains("Primarch")) continue;
            if (!seen.add(nm)) continue;
            C c = new C();
            c.name = nm;
            c.slot = slot;
            c.entryId = Data.str(o.entry, "id");
            if (!stats(o.entry, c, 0, new HashSet<String>()) && (o.link == null || !stats(o.link, c, 0, new HashSet<String>()))) continue;
            c.command = c.type.contains("Command") || c.type.contains("Paragon");
            list.add(c);
        }
        java.util.Collections.sort(list, new java.util.Comparator<C>() {
            public int compare(C a, C b) { int x = rank(a.slot) - rank(b.slot); return x != 0 ? x : a.name.compareTo(b.name); }
        });
    }

    static int rank(String s) { return s.contains(" - ") ? 3 : s.equals("Warlord") ? 0 : s.contains("High Command") ? 1 : 2; }

    @SuppressWarnings("unchecked")
    private boolean stats(Map<String, Object> n, C c, int depth, Set<String> seen) {
        if (n == null || depth > 6) return false;
        String id = Data.str(n, "id");
        if (id != null && !seen.add(id)) return false;
        if (prof(n, c)) return true;
        for (Map<String, Object> ch : Data.list(n, "selectionEntries")) if (stats(ch, c, depth + 1, seen)) return true;
        for (Map<String, Object> g : Data.list(n, "selectionEntryGroups")) if (stats(g, c, depth + 1, seen)) return true;
        for (Map<String, Object> l : Data.list(n, "entryLinks")) {
            String tid = Data.str(l, "targetId");
            Map<String, Object> t = d.entries.get(tid);
            if (t == null) t = d.groups.get(tid);
            if (stats(t, c, depth + 1, seen)) return true;
        }
        return false;
    }

    private boolean prof(Map<String, Object> n, C c) {
        List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>(Data.list(n, "profiles"));
        for (Map<String, Object> il : Data.list(n, "infoLinks")) {
            if (!"profile".equals(Data.str(il, "type"))) continue;
            Map<String, Object> p = d.profiles.get(Data.str(il, "targetId"));
            if (p != null) ps.add(p);
        }
        for (Map<String, Object> p : ps) {
            String ws = null, st = null, t = null, w = null, in = null, at = null, ty = null;
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String k = Data.str(ch, "name"), v = Data.str(ch, "$text");
                if (k == null || v == null) continue;
                v = v.trim();
                if (k.equals("WS")) ws = v; else if (k.equals("S")) st = v; else if (k.equals("T")) t = v;
                else if (k.equals("W")) w = v; else if (k.equals("I")) in = v; else if (k.equals("A")) at = v;
                else if (k.equals("Type")) ty = v;
            }
            if (ws == null || w == null) continue;
            c.stats = "WS" + ws + " S" + st + " T" + t + " W" + w + " I" + in + " A" + at;
            c.type = ty == null ? "" : ty;
            c.ws = num(ws); c.s = num(st); c.t = num(t); c.w = Math.max(1, num(w)); c.i = num(in); c.a = num(at);
            return true;
        }
        return false;
    }

    public static int num(String v) {
        if (v == null) return 0;
        String x = v.replaceAll("[^0-9].*$", "").replaceAll("[^0-9]", "");
        try { return x.isEmpty() ? 0 : Integer.parseInt(x); } catch (Exception e) { return 0; }
    }
}
