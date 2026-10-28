package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Профиль противника по данным: какая доля армии — техника, тяжёлая техника, элита (Sv 2+ / T5+),
 * лёгкая пехота (Sv 4+ и хуже), рукопашники. Если у игрока есть ростеры этой фракции (например,
 * импортированные ростеры соперника) — считаем по ним, взвешивая очками; иначе — по каталогу.
 */
public final class Foe {
    public double vehicle, heavy, elite, horde, melee, fast;
    public int units;
    public boolean fromRosters;

    private Foe() { }

    public static Foe of(Data d, String catId, List<Roster> rosters) {
        Foe f = new Foe();
        if (catId == null || d.cats.get(catId) == null) return f;
        double total = 0;
        for (Roster r : rosters) {
            if (!catId.equals(r.catalogueId)) continue;
            Engine e = new Engine(d, r);
            List<Force> fs = new ArrayList<Force>();
            r.allForces(fs);
            for (Force fo : fs) for (Sel s : fo.sels) {
                double pts = Math.max(20, e.points(s));
                String[] pr = profileSel(e, s);
                if (pr == null) continue;
                f.add(pr, pts);
                total += pts;
                f.units++;
            }
        }
        if (total > 0) { f.fromRosters = true; f.norm(total); return f; }
        Data.Cat cat = d.cats.get(catId);
        Roster r = new Roster();
        r.catalogueId = catId;
        Engine e = new Engine(d, r);
        Set<String> seen = new HashSet<String>();
        for (Opt o : cat.roots) {
            String nm = Data.str(o.entry, "name");
            if (nm == null || !seen.add(nm)) continue;
            String[] pr = profileEntry(d, o.entry, 0, new HashSet<String>());
            if (pr == null && o.link != null) pr = profileEntry(d, o.link, 0, new HashSet<String>());
            if (pr == null) continue;
            f.add(pr, 1);
            total += 1;
            f.units++;
        }
        if (total > 0) f.norm(total);
        return f;
    }

    private void add(String[] pr, double w) {
        // pr: {kind, type, save, T, A, front}
        String type = pr[1] == null ? "" : pr[1].toLowerCase();
        boolean veh = "V".equals(pr[0]) || type.contains("vehicle") || type.contains("knight") || type.contains("titan");
        if (veh) {
            vehicle += w;
            int av = Chars.num(pr[5]);
            if (av >= 13 || type.contains("knight") || type.contains("titan") || type.contains("super-heavy")) heavy += w;
            if (type.contains("flyer") || type.contains("skimmer") || type.contains("fast")) fast += w;
            return;
        }
        int sv = Chars.num(pr[2]), t = Chars.num(pr[3]), a = Chars.num(pr[4]);
        if (type.contains("walker") || type.contains("automata") || type.contains("dreadnought")) { heavy += w * 0.5; vehicle += w * 0.5; }
        if ((sv > 0 && sv <= 2) || t >= 5) elite += w;
        else if (sv >= 4 || sv == 0) horde += w;
        if (a >= 3) melee += w * (a >= 4 ? 1 : 0.6);
        if (type.contains("cavalry") || type.contains("jump") || type.contains("antigrav") || type.contains("bike")) fast += w;
    }

    private void norm(double total) {
        vehicle /= total; heavy /= total; elite /= total; horde /= total; melee /= total; fast /= total;
    }

    static String[] fromProfile(Map<String, Object> p) {
        String type = null, sv = null, t = null, a = null, front = null;
        boolean model = false, veh = false;
        for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String k = Data.str(ch, "name"), v = Data.str(ch, "$text");
            if (k == null || v == null) continue;
            if (k.equals("Type")) type = v;
            else if (k.equals("SAV")) { sv = v; model = true; }
            else if (k.equals("T")) { t = v; model = true; }
            else if (k.equals("A")) a = v;
            else if (k.equals("Front Armour")) { front = v; veh = true; }
        }
        if (veh) return new String[]{"V", type, null, null, null, front};
        if (model) return new String[]{"M", type, sv, t, a, null};
        return null;
    }

    static String[] profileSel(Engine e, Sel s) {
        for (Map<String, Object> p : e.profilesOf(e.entryOf(s), e.linkOf(s))) { String[] r = fromProfile(p); if (r != null) return r; }
        for (Sel ch : s.children) for (Map<String, Object> p : e.profilesOf(e.entryOf(ch), e.linkOf(ch))) { String[] r = fromProfile(p); if (r != null) return r; }
        return null;
    }

    @SuppressWarnings("unchecked")
    static String[] profileEntry(Data d, Map<String, Object> n, int depth, Set<String> seen) {
        if (n == null || depth > 5) return null;
        String id = Data.str(n, "id");
        if (id != null && !seen.add(id)) return null;
        for (Map<String, Object> p : Data.list(n, "profiles")) { String[] r = fromProfile(p); if (r != null) return r; }
        for (Map<String, Object> il : Data.list(n, "infoLinks")) {
            if (!"profile".equals(Data.str(il, "type"))) continue;
            String[] r = fromProfile(d.profiles.get(Data.str(il, "targetId")));
            if (r != null) return r;
        }
        for (Map<String, Object> ch : Data.list(n, "selectionEntries")) { String[] r = profileEntry(d, ch, depth + 1, seen); if (r != null) return r; }
        for (Map<String, Object> g : Data.list(n, "selectionEntryGroups")) { String[] r = profileEntry(d, g, depth + 1, seen); if (r != null) return r; }
        for (Map<String, Object> l : Data.list(n, "entryLinks")) {
            String tid = Data.str(l, "targetId");
            Map<String, Object> t = d.entries.get(tid);
            if (t == null) t = d.groups.get(tid);
            String[] r = profileEntry(d, t, depth + 1, seen);
            if (r != null) return r;
        }
        return null;
    }

    public String describe() {
        return Math.round(vehicle * 100) + "% техники (тяжёлой " + Math.round(heavy * 100) + "%), " + Math.round(elite * 100) + "% элиты, " + Math.round(horde * 100) + "% лёгкой пехоты";
    }
}
