package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.medoedy.liber.engine.Data.bool;
import static com.medoedy.liber.engine.Data.list;
import static com.medoedy.liber.engine.Data.num;
import static com.medoedy.liber.engine.Data.str;

/**
 * Вычислитель в духе BattleScribe: модификаторы с условиями и повторами,
 * подсчёт выборов в областях (self/parent/unit/model/force/roster/…),
 * ограничения min/max, стоимость и проверка ростера.
 */
@SuppressWarnings("unchecked")
public final class Engine {
    public final Data d;
    public final Roster r;

    public Engine(Data d, Roster r) {
        this.d = d;
        this.r = r;
    }

    // ================================================================ кеш
    private final IdentityHashMap<Object, Eff> effCache = new IdentityHashMap<Object, Eff>();
    private final IdentityHashMap<Object, Boolean> inProgress = new IdentityHashMap<Object, Boolean>();

    /** Вызывать после любого изменения ростера. */
    public void changed() {
        effCache.clear();
    }

    // ================================================================ контекст
    /** self — существующий экземпляр (Sel/Force) или null для кандидата; parent — Sel/Force/Roster. */
    public static final class Ctx {
        public final Object self, parent;
        public final String place; // слот корневого юнита
        public Ctx(Object self, Object parent) { this(self, parent, null); }
        public Ctx(Object self, Object parent, String place) { this.self = self; this.parent = parent; this.place = place; }
    }

    public Ctx ctxOf(Sel s) { return new Ctx(s, s.parent != null ? s.parent : s.force, s.parent == null ? s.cat : null); }
    public Ctx ctxOf(Force f) { return new Ctx(f, f.parent != null ? f.parent : r); }
    public Ctx candidate(Object parent) { return new Ctx(null, parent); }

    // ================================================================ эффективные свойства
    public static final class Eff {
        public boolean hidden;
        public String name;
        public final Map<String, Double> costs = new LinkedHashMap<String, Double>();
        public final Map<String, Double> cons = new HashMap<String, Double>();
        public final LinkedHashSet<String> cats = new LinkedHashSet<String>();
        public String primary;
        public final List<String> errors = new ArrayList<String>();
        public final List<String> warnings = new ArrayList<String>();
        public double cost(String id) { Double v = costs.get(id); return v == null ? 0 : v; }
    }

    public Eff eff(Map<String, Object> entry, Map<String, Object> link, Ctx c) {
        Eff e = new Eff();
        e.hidden = bool(entry, "hidden") || (link != null && bool(link, "hidden"));
        e.name = Data.clean(str(entry, "name"));
        if ((e.name == null || e.name.isEmpty()) && link != null) e.name = Data.clean(str(link, "name"));
        for (Map<String, Object> co : list(entry, "costs")) e.costs.put(str(co, "typeId"), num(co, "value", 0));
        if (link != null) for (Map<String, Object> co : list(link, "costs")) e.costs.put(str(co, "typeId"), num(co, "value", 0));
        for (Map<String, Object> k : list(entry, "constraints")) e.cons.put(str(k, "id"), num(k, "value", 0));
        if (link != null) for (Map<String, Object> k : list(link, "constraints")) e.cons.put(str(k, "id"), num(k, "value", 0));
        addCats(e, entry);
        if (link != null) addCats(e, link);
        apply(entry, e, c);
        if (link != null) apply(link, e, c);
        if (e.primary == null && !e.cats.isEmpty()) e.primary = e.cats.iterator().next();
        return e;
    }

    private static void addCats(Eff e, Map<String, Object> n) {
        for (Map<String, Object> cl : list(n, "categoryLinks")) {
            String t = str(cl, "targetId");
            e.cats.add(t);
            if (bool(cl, "primary")) e.primary = t;
        }
    }

    public Eff effSel(Sel s) {
        Eff e = effCache.get(s);
        if (e != null) return e;
        if (inProgress.containsKey(s)) return staticEff(s);
        inProgress.put(s, Boolean.TRUE);
        try {
            e = eff(entryOf(s), linkOf(s), ctxOf(s));
        } finally {
            inProgress.remove(s);
        }
        effCache.put(s, e);
        return e;
    }

    public Eff effForce(Force f) {
        Eff e = effCache.get(f);
        if (e != null) return e;
        if (inProgress.containsKey(f)) { Eff st = new Eff(); st.name = forceName(f); return st; }
        inProgress.put(f, Boolean.TRUE);
        try {
            Map<String, Object> fe = d.forces.get(f.entryId);
            e = eff(fe, f.linkId != null ? d.forceLinks.get(f.linkId) : null, ctxOf(f));
        } finally {
            inProgress.remove(f);
        }
        effCache.put(f, e);
        return e;
    }

    private Eff staticEff(Sel s) {
        Eff e = new Eff();
        Map<String, Object> en = entryOf(s), ln = linkOf(s);
        e.name = Data.clean(str(en, "name"));
        addCats(e, en);
        if (ln != null) addCats(e, ln);
        for (Map<String, Object> co : list(en, "costs")) e.costs.put(str(co, "typeId"), num(co, "value", 0));
        return e;
    }

    public Map<String, Object> entryOf(Sel s) {
        Map<String, Object> e = d.entries.get(s.entryId);
        if (e == null) e = d.groups.get(s.entryId);
        return e;
    }

    public Map<String, Object> linkOf(Sel s) { return s.linkId == null ? null : d.links.get(s.linkId); }

    public String forceName(Force f) {
        Map<String, Object> fe = d.forces.get(f.entryId);
        return Data.clean(str(fe, "name"));
    }

    // ================================================================ модификаторы
    private void apply(Map<String, Object> node, Eff e, Ctx c) {
        for (Map<String, Object> m : list(node, "modifiers")) applyMod(m, e, c);
        applyGroups(list(node, "modifierGroups"), e, c);
    }

    private void applyGroups(List<Map<String, Object>> groups, Eff e, Ctx c) {
        for (Map<String, Object> g : groups) {
            if (!condsOk(g, c)) continue;
            int times = repeatTimes(g, c);
            for (int t = 0; t < Math.max(1, Math.min(times, 50)); t++) {
                if (times == 0) break;
                for (Map<String, Object> m : list(g, "modifiers")) applyMod(m, e, c);
                applyGroups(list(g, "modifierGroups"), e, c);
                if (!g.containsKey("repeats")) break;
            }
        }
    }

    private void applyMod(Map<String, Object> m, Eff e, Ctx c) {
        if (!condsOk(m, c)) return;
        int times = repeatTimes(m, c);
        if (times <= 0) return;
        String f = str(m, "field"), t = str(m, "type");
        Object v = m.get("value");
        if (f == null || t == null) return;
        if (f.equals("hidden")) {
            if (t.equals("set")) e.hidden = truthy(v);
        } else if (f.equals("name")) {
            String sv = v == null ? "" : (v instanceof Double ? Data.fmtNum((Double) v) : v.toString());
            String join = str(m, "join");
            if (join == null) join = " ";
            if (t.equals("set")) e.name = sv;
            else if (t.equals("append")) e.name = e.name + join + sv;
            else if (t.equals("prepend")) e.name = sv + join + e.name;
            else if (t.equals("replace")) { String arg = str(m, "arg"); if (arg != null) e.name = e.name.replace(arg, sv); }
        } else if (f.equals("category")) {
            String cid = v == null ? null : v.toString();
            if (cid == null) return;
            if (t.equals("add")) e.cats.add(cid);
            else if (t.equals("remove")) { e.cats.remove(cid); if (cid.equals(e.primary)) e.primary = null; }
            else if (t.equals("set-primary")) { e.cats.add(cid); e.primary = cid; }
            else if (t.equals("unset-primary")) { if (cid.equals(e.primary)) e.primary = null; }
        } else if (f.equals("error")) {
            if (t.equals("add") || t.equals("set")) e.errors.add(String.valueOf(v));
        } else if (f.equals("warning")) {
            if (t.equals("add") || t.equals("set")) e.warnings.add(String.valueOf(v));
        } else if (f.equals("description") || f.equals("page") || f.equals("annotation") || f.equals("tracked") || f.equals("publication")) {
            // не влияет на подсчёт
        } else {
            boolean isCost = d.costTypeIds.contains(f);
            Map<String, Double> map = isCost ? e.costs : e.cons;
            if (!isCost && !map.containsKey(f)) map.put(f, 0d);
            double cur = map.containsKey(f) ? map.get(f) : 0;
            double val = num(m, "value", 0);
            if (t.equals("set")) cur = val;
            else if (t.equals("increment")) cur += val * times;
            else if (t.equals("decrement")) cur -= val * times;
            else if (t.equals("ceil")) cur = Math.ceil(cur);
            else if (t.equals("floor")) cur = Math.floor(cur);
            else return;
            map.put(f, cur);
        }
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        return "true".equals(v);
    }

    private int repeatTimes(Map<String, Object> m, Ctx c) {
        List<Map<String, Object>> reps = list(m, "repeats");
        if (reps.isEmpty()) return 1;
        int total = 0;
        for (Map<String, Object> rp : reps) {
            double n = query(str(rp, "scope"), str(rp, "childId"), str(rp, "field"),
                    bool(rp, "includeChildSelections"), bool(rp, "includeChildForces"), c);
            double per = num(rp, "value", 1);
            if (per <= 0) per = 1;
            double k = bool(rp, "roundUp") ? Math.ceil(n / per) : Math.floor(n / per);
            total += (int) (k * num(rp, "repeats", 1));
        }
        return total;
    }

    // ================================================================ условия
    private boolean condsOk(Map<String, Object> node, Ctx c) {
        for (Map<String, Object> cd : list(node, "conditions")) if (!cond(cd, c)) return false;
        for (Map<String, Object> g : list(node, "conditionGroups")) if (!groupOk(g, c)) return false;
        return true;
    }

    private boolean groupOk(Map<String, Object> g, Ctx c) {
        boolean or = "or".equals(str(g, "type"));
        List<Map<String, Object>> cs = list(g, "conditions"), gs = list(g, "conditionGroups");
        if (cs.isEmpty() && gs.isEmpty()) return true;
        for (Map<String, Object> cd : cs) { boolean ok = cond(cd, c); if (or && ok) return true; if (!or && !ok) return false; }
        for (Map<String, Object> sg : gs) { boolean ok = groupOk(sg, c); if (or && ok) return true; if (!or && !ok) return false; }
        return !or;
    }

    private boolean cond(Map<String, Object> cd, Ctx c) {
        String t = str(cd, "type");
        String scope = str(cd, "scope"), child = str(cd, "childId"), field = str(cd, "field");
        if ("instanceOf".equals(t)) return instanceOf(scope, child, c);
        if ("notInstanceOf".equals(t)) return !instanceOf(scope, child, c);
        double n = query(scope, child, field, bool(cd, "includeChildSelections"), bool(cd, "includeChildForces"), c);
        if (bool(cd, "percentValue")) {
            double total = query(scope, "any", field, bool(cd, "includeChildSelections"), bool(cd, "includeChildForces"), c);
            n = total == 0 ? 0 : n * 100 / total;
        }
        double v = num(cd, "value", 0);
        if ("atLeast".equals(t)) return n >= v;
        if ("atMost".equals(t)) return n <= v;
        if ("greaterThan".equals(t)) return n > v;
        if ("lessThan".equals(t)) return n < v;
        if ("equalTo".equals(t)) return n == v;
        if ("notEqualTo".equals(t)) return n != v;
        return false;
    }

    // ================================================================ области
    private Object start(Ctx c) { return c.self != null ? c.self : c.parent; }

    private Object up(Object o) {
        if (o instanceof Sel) { Sel s = (Sel) o; return s.parent != null ? s.parent : s.force; }
        if (o instanceof Force) { Force f = (Force) o; return f.parent != null ? f.parent : r; }
        return null;
    }

    public Force forceOf(Object o) {
        if (o instanceof Sel) return ((Sel) o).force;
        if (o instanceof Force) return (Force) o;
        return null;
    }

    private String typeOf(Sel s) { return str(entryOf(s), "type"); }

    Object resolveScope(String scope, Ctx c) {
        if (scope == null) return c.parent;
        if (scope.equals("self")) return c.self;
        if (scope.equals("parent")) return c.parent;
        if (scope.equals("roster") || scope.equals("primary-catalogue")) return r;
        if (scope.equals("force")) return forceOf(start(c));
        if (scope.equals("ancestor")) return c.parent;
        if (scope.equals("unit") || scope.equals("unit-self") || scope.equals("model") || scope.equals("model-self")) {
            String want = scope.startsWith("unit") ? "unit" : "model";
            Object o = start(c);
            Sel top = null;
            while (o instanceof Sel) {
                Sel s = (Sel) o;
                if (want.equals(typeOf(s))) return s;
                top = s;
                o = up(o);
            }
            return want.equals("unit") ? top : null;
        }
        if (scope.equals("root-entry")) {
            Object o = start(c);
            Sel top = null;
            while (o instanceof Sel) { top = (Sel) o; o = up(o); }
            return top;
        }
        // id предка
        Object o = start(c);
        while (o != null && o != r) {
            if (matchesId(o, scope)) return o;
            o = up(o);
        }
        return null;
    }

    private boolean matchesId(Object o, String id) {
        if (o instanceof Sel) { Sel s = (Sel) o; return id.equals(s.entryId) || id.equals(s.linkId) || s.groups.contains(id); }
        if (o instanceof Force) { Force f = (Force) o; return id.equals(f.entryId) || id.equals(f.linkId) || id.equals(f.catalogueId); }
        return false;
    }

    private boolean instanceOf(String scope, String id, Ctx c) {
        if (id == null) return false;
        if ("primary-catalogue".equals(scope)) {
            Force f = forceIn(c);
            if (f != null && isInstance(f, id)) return true;
            return id.equals(forceCat(c));
        }
        if ("roster".equals(scope)) {
            if (id.equals(r.catalogueId)) return true;
            List<Force> all = new ArrayList<Force>();
            r.allForces(all);
            for (Force f : all) if (id.equals(f.catalogueId)) return true;
            return false;
        }
        if ("ancestor".equals(scope)) {
            Object o = c.parent;
            while (o != null && o != r) { if (isInstance(o, id)) return true; o = up(o); }
            return false;
        }
        if (("parent".equals(scope) || scope == null) && c.place != null && c.place.equals(id) && c.parent instanceof Force) return true;
        Object o = resolveScope(scope, c);
        return o != null && isInstance(o, id);
    }

    /** Каталог силы, в которой идёт вычисление (для союзников — каталог союзника). */
    String catHint;
    Force forceIn(Ctx c) {
        Object o = c.self != null ? c.self : c.parent;
        while (o != null && o != r && !(o instanceof Force)) o = up(o);
        return o instanceof Force ? (Force) o : null;
    }

    String forceCat(Ctx c) {
        Object o = c.self != null ? c.self : c.parent;
        while (o != null && o != r && !(o instanceof Force)) o = up(o);
        if (o instanceof Force && ((Force) o).catalogueId != null) return ((Force) o).catalogueId;
        return catHint != null ? catHint : r.catalogueId;
    }

    private boolean isInstance(Object o, String id) {
        if (o instanceof Sel) {
            Sel s = (Sel) o;
            if (id.equals(s.entryId) || id.equals(s.linkId) || s.groups.contains(id)) return true;
            return effSel(s).cats.contains(id);
        }
        if (o instanceof Force) {
            Force f = (Force) o;
            return id.equals(f.entryId) || id.equals(f.linkId) || id.equals(f.catalogueId);
        }
        if (o instanceof Roster) return id.equals(r.catalogueId);
        return false;
    }

    // ================================================================ подсчёт
    double query(String scope, String child, String field, boolean ics, boolean icf, Ctx c) {
        Object so = resolveScope(scope, c);
        if (so == null) return 0;
        if ("forces".equals(field)) return countForces(so, child, icf);
        // выбор с number > 1 — это N одинаковых копий: условия считаются «на одну копию»
        if (so instanceof Sel && ((Sel) so).number > 1 && (field == null || !field.startsWith("limit::"))) {
            Sel ss = (Sel) so;
            double raw = field != null && d.costTypeIds.contains(field) ? sumCost(so, child, field, ics, icf) : countSels(so, child, ics, icf);
            return raw / ss.number;
        }
        if (field != null && field.startsWith("limit::")) {
            String cid = field.substring(7);
            return cid.equals(d.pointsId) && r.limit > 0 ? r.limit : -1;
        }
        if (field != null && d.costTypeIds.contains(field)) return sumCost(so, child, field, ics, icf);
        return countSels(so, child, ics, icf);
    }

    private void selsIn(Object so, boolean ics, boolean icf, List<Sel> out) {
        if (so instanceof Sel) {
            for (Sel ch : ((Sel) so).children) addSel(ch, ics, out);
        } else if (so instanceof Force) {
            addForceSels((Force) so, ics, icf, out);
        } else if (so instanceof Roster) {
            for (Force f : r.forces) addForceSels(f, ics, true, out);
        }
    }

    private void addForceSels(Force f, boolean ics, boolean icf, List<Sel> out) {
        for (Sel s : f.sels) addSel(s, ics, out);
        if (icf) for (Force ch : f.children) addForceSels(ch, ics, true, out);
    }

    private void addSel(Sel s, boolean deep, List<Sel> out) {
        out.add(s);
        if (deep) for (Sel ch : s.children) addSel(ch, true, out);
    }

    boolean matches(Sel s, String child) {
        if (child == null || child.equals("any")) return true;
        if (child.equals("model") || child.equals("unit") || child.equals("upgrade")) return child.equals(typeOf(s));
        if (child.equals(s.entryId) || child.equals(s.linkId) || s.groups.contains(child)) return true;
        return effSel(s).cats.contains(child);
    }

    private double countSels(Object so, String child, boolean ics, boolean icf) {
        List<Sel> all = new ArrayList<Sel>();
        selsIn(so, ics, icf, all);
        double n = 0;
        for (Sel s : all) if (matches(s, child)) n += s.number;
        return n;
    }

    private double sumCost(Object so, String child, String costId, boolean ics, boolean icf) {
        if ("self".equals(child) || child == null) return totalCost(so, costId);
        List<Sel> all = new ArrayList<Sel>();
        selsIn(so, ics, icf, all);
        double n = 0;
        for (Sel s : all) if (matches(s, child)) n += ics ? effSel(s).cost(costId) * s.number : selCost(s, costId);
        return n;
    }

    private double countForces(Object so, String child, boolean icf) {
        List<Force> fs = new ArrayList<Force>();
        if (so instanceof Roster) r.allForces(fs);
        else if (so instanceof Force) { Force f = (Force) so; for (Force ch : f.children) collectForces(ch, icf, fs); }
        else return 0;
        double n = 0;
        for (Force f : fs) if (child == null || child.equals("any") || matchesId(f, child)) n++;
        return n;
    }

    private void collectForces(Force f, boolean deep, List<Force> out) {
        out.add(f);
        if (deep) for (Force c : f.children) collectForces(c, true, out);
    }

    // ================================================================ стоимость
    public double selCost(Sel s, String costId) {
        double v = effSel(s).cost(costId) * s.number;
        for (Sel ch : s.children) v += selCost(ch, costId);
        return v;
    }

    public double forceCost(Force f, String costId) {
        double v = effForce(f).cost(costId);
        for (Sel s : f.sels) v += selCost(s, costId);
        for (Force ch : f.children) v += forceCost(ch, costId);
        return v;
    }

    public double rosterCost(String costId) {
        double v = 0;
        for (Force f : r.forces) v += forceCost(f, costId);
        return v;
    }

    public double totalCost(Object o, String costId) {
        if (o instanceof Sel) return selCost((Sel) o, costId);
        if (o instanceof Force) return forceCost((Force) o, costId);
        if (o instanceof Roster) return rosterCost(costId);
        return 0;
    }

    public double points(Object o) { return totalCost(o, d.pointsId); }

    // ================================================================ варианты выбора
    /** Дочерние варианты для выбора/записи: записи, ссылки и группы (группы раскрываются в дерево). */
    public List<Node> options(Map<String, Object> owner, Map<String, Object> ownerLink, Object parent) {
        List<Node> out = new ArrayList<Node>();
        collectOptions(owner, new ArrayList<String>(), parent, out);
        if (ownerLink != null) collectOptions(ownerLink, new ArrayList<String>(), parent, out);
        return out;
    }

    public static final class Node {
        public Opt opt;
        public Eff eff;
        public List<Node> children = new ArrayList<Node>(); // для групп
        public String place; // слот для корневых юнитов
        public boolean isGroup() { return opt.group; }
    }

    private void collectOptions(Map<String, Object> owner, List<String> groupPath, Object parent, List<Node> out) {
        for (Map<String, Object> e : list(owner, "selectionEntries")) out.add(node(new Opt(e, null, groupPath, false), parent));
        for (Map<String, Object> l : list(owner, "entryLinks")) {
            Map<String, Object> t = d.resolveEntry(l);
            if (t == null) continue;
            boolean g = "selectionEntryGroup".equals(str(l, "type"));
            Node n = node(new Opt(t, l, groupPath, g), parent);
            if (g) expandGroup(n, groupPath, parent);
            out.add(n);
        }
        for (Map<String, Object> g : list(owner, "selectionEntryGroups")) {
            Node n = node(new Opt(g, null, groupPath, true), parent);
            expandGroup(n, groupPath, parent);
            out.add(n);
        }
        sortNodes(out);
    }

    private void expandGroup(Node n, List<String> groupPath, Object parent) {
        List<String> gp = new ArrayList<String>(groupPath);
        gp.add(n.opt.id());
        if (n.opt.linkId() != null) gp.add(n.opt.linkId());
        collectOptions(n.opt.entry, gp, parent, n.children);
        if (n.opt.link != null) collectOptions(n.opt.link, gp, parent, n.children);
    }

    private static void sortNodes(List<Node> l) {
        java.util.Collections.sort(l, new java.util.Comparator<Node>() {
            public int compare(Node a, Node b) {
                double sa = num(a.opt.link != null ? a.opt.link : a.opt.entry, "sortIndex", num(a.opt.entry, "sortIndex", 999));
                double sb = num(b.opt.link != null ? b.opt.link : b.opt.entry, "sortIndex", num(b.opt.entry, "sortIndex", 999));
                if (sa != sb) return Double.compare(sa, sb);
                return 0;
            }
        });
    }

    private Node node(Opt o, Object parent) {
        Node n = new Node();
        n.opt = o;
        n.eff = eff(o.entry, o.link, candidate(parent));
        return n;
    }

    /** Корневые юниты, доступные в силе (детачменте): по одному узлу на каждый подходящий слот. */
    public List<Node> rootOptions(Force f) {
        Data.Cat cat = d.cats.get(f.catalogueId);
        List<Node> out = new ArrayList<Node>();
        if (cat == null) return out;
        Set<String> slots = new HashSet<String>();
        for (CatSlot s : slots(f)) if (!s.hidden && s.max != 0) slots.add(s.categoryId);
        for (Opt o : cat.roots) {
            Eff base = eff(o.entry, o.link, candidate(f));
            if (base.hidden) continue;
            Set<String> tried = new HashSet<String>();
            List<String> cands = new ArrayList<String>();
            if (base.primary != null) cands.add(base.primary);
            cands.addAll(base.cats);
            for (String slot : cands) {
                if (!slots.contains(slot) || !tried.add(slot)) continue;
                Eff e = slot.equals(base.primary) ? base : eff(o.entry, o.link, new Ctx(null, f, slot));
                if (e.hidden || !slot.equals(e.primary)) continue;
                Node n = new Node();
                n.opt = o;
                n.eff = e;
                n.place = slot;
                out.add(n);
            }
        }
        return out;
    }

    // ================================================================ слоты детачмента
    public static final class CatSlot {
        public String categoryId, name;
        public double min = 0, max = -1, count;
        public boolean hidden;
    }

    /** Слоты, которые даст детачмент, если его добавить под parent (для подсказок). */
    public List<CatSlot> slotsFor(Map<String, Object> fe, Object parent) {
        List<CatSlot> out = new ArrayList<CatSlot>();
        Ctx c = new Ctx(null, parent);
        for (Map<String, Object> cl : list(fe, "categoryLinks")) {
            CatSlot s = new CatSlot();
            s.categoryId = str(cl, "targetId");
            s.name = Data.clean(str(cl, "name"));
            Eff e = eff(cl, null, c);
            s.hidden = e.hidden;
            for (Map<String, Object> k : list(cl, "constraints")) {
                double v = e.cons.containsKey(str(k, "id")) ? e.cons.get(str(k, "id")) : num(k, "value", 0);
                if ("max".equals(str(k, "type"))) s.max = s.max < 0 ? v : Math.min(s.max, v);
                else if ("min".equals(str(k, "type"))) s.min = Math.max(s.min, v);
            }
            categoryOwn(s, c);
            out.add(s);
        }
        return out;
    }

    /**
     * Ограничения и видимость, заданные в самой категории (categoryEntry), а не в ссылке детачмента.
     * Так устроены «открываемые» слоты: War-engine — Iron-clad, Cult Operative, Legiones Thallaxes —
     * по умолчанию скрыты с максимумом 0 и открываются Prime-выгодами.
     */
    private void categoryOwn(CatSlot s, Ctx c) {
        Map<String, Object> ce = d.categories.get(s.categoryId);
        if (ce == null) return;
        if (list(ce, "constraints").isEmpty() && list(ce, "modifiers").isEmpty() && !bool(ce, "hidden")) return;
        Eff e = eff(ce, null, c);
        if (e.hidden) s.hidden = true;
        for (Map<String, Object> k : list(ce, "constraints")) {
            if (!"selections".equals(str(k, "field"))) continue;
            double v = e.cons.containsKey(str(k, "id")) ? e.cons.get(str(k, "id")) : num(k, "value", 0);
            if ("max".equals(str(k, "type"))) s.max = s.max < 0 ? v : Math.min(s.max, v);
            else if ("min".equals(str(k, "type"))) s.min = Math.max(s.min, v);
        }
    }

    private int deepCat(Sel s, String cat) {
        int n = 0;
        for (Sel ch : s.children) {
            if (effSel(ch).cats.contains(cat)) n += Math.max(1, ch.number);
            else n += deepCat(ch, cat);
        }
        return n;
    }

    public List<CatSlot> slots(Force f) {
        List<CatSlot> out = new ArrayList<CatSlot>();
        Map<String, Object> fe = d.forces.get(f.entryId);
        if (fe == null) return out;
        Ctx c = new Ctx(null, f);
        for (Map<String, Object> cl : list(fe, "categoryLinks")) {
            CatSlot s = new CatSlot();
            s.categoryId = str(cl, "targetId");
            s.name = Data.clean(str(cl, "name"));
            Eff e = eff(cl, null, c);
            s.hidden = e.hidden;
            for (Map<String, Object> k : list(cl, "constraints")) {
                double v = e.cons.containsKey(str(k, "id")) ? e.cons.get(str(k, "id")) : num(k, "value", 0);
                if ("max".equals(str(k, "type"))) s.max = s.max < 0 ? v : Math.min(s.max, v);
                else if ("min".equals(str(k, "type"))) s.min = Math.max(s.min, v);
            }
            categoryOwn(s, c);
            boolean deep = false;
            for (Map<String, Object> k : list(cl, "constraints")) if (bool(k, "includeChildSelections")) deep = true;
            for (Sel sel : f.sels) {
                if (s.categoryId.equals(effSel(sel).primary)) s.count += 1;
                // Prime-слоты: категорию «Prime …» даёт вложенный выбор «Prime Unit», считаем и его
                else if (deep) s.count += deepCat(sel, s.categoryId);
            }
            out.add(s);
        }
        return out;
    }

    /**
     * Починка слотов: если юнит стоит в скрытом или переполненном слоте, а в детачменте есть
     * подходящий открытый слот — переставить его туда (нужно для ростеров, сохранённых старыми версиями).
     */
    public boolean reslot() {
        boolean any = false;
        List<Force> all = new ArrayList<Force>();
        r.allForces(all);
        for (Force f : all) {
            for (Sel s : new ArrayList<Sel>(f.sels)) {
                String prim = effSel(s).primary;
                if (prim == null) continue;
                CatSlot cur = null;
                for (CatSlot x : slots(f)) if (x.categoryId.equals(prim)) cur = x;
                if (cur != null && !cur.hidden && (cur.max < 0 || cur.count <= cur.max)) continue;
                for (Node n : rootOptions(f)) {
                    if (!n.opt.id().equals(s.entryId) || n.place == null || n.place.equals(prim)) continue;
                    CatSlot to = null;
                    for (CatSlot x : slots(f)) if (x.categoryId.equals(n.place)) to = x;
                    if (to == null || to.hidden || (to.max >= 0 && to.count + 1 > to.max)) continue;
                    s.cat = n.place;
                    s.linkId = n.opt.linkId();
                    changed();
                    any = true;
                    break;
                }
            }
        }
        return any;
    }

    // ================================================================ силы
    public static final class ForceOpt {
        public Map<String, Object> entry, link;
        public Eff eff;
        public String name() { return eff.name; }
    }

    public List<ForceOpt> rootForceOptions(String catalogueId) {
        List<ForceOpt> out = new ArrayList<ForceOpt>();
        List<Map<String, Object>> fes = new ArrayList<Map<String, Object>>(list(d.gs, "forceEntries"));
        Data.Cat cat = d.cats.get(catalogueId);
        if (cat != null) fes.addAll(cat.forceEntries);
        String old = catHint;
        catHint = catalogueId;
        try {
            for (Map<String, Object> fe : fes) {
                ForceOpt o = new ForceOpt();
                o.entry = fe;
                o.eff = eff(fe, null, candidate(r));
                if (!o.eff.hidden) out.add(o);
            }
        } finally { catHint = old; }
        return out;
    }

    public List<ForceOpt> childForceOptions(Force parent) {
        List<ForceOpt> out = new ArrayList<ForceOpt>();
        Map<String, Object> pe = d.forces.get(parent.entryId);
        for (Map<String, Object> fe : list(pe, "forceEntries")) {
            ForceOpt o = new ForceOpt();
            o.entry = fe;
            o.eff = eff(fe, null, candidate(parent));
            if (!o.eff.hidden && maxForcesOk(o, parent)) out.add(o);
        }
        for (Map<String, Object> l : list(pe, "forceEntryLinks")) {
            Map<String, Object> fe = d.forces.get(str(l, "targetId"));
            if (fe == null) continue;
            ForceOpt o = new ForceOpt();
            o.entry = fe;
            o.link = l;
            o.eff = eff(fe, l, candidate(parent));
            if (!o.eff.hidden && maxForcesOk(o, parent)) out.add(o);
        }
        return out;
    }

    private boolean maxForcesOk(ForceOpt o, Force parent) {
        Ctx c = candidate(parent);
        List<Map<String, Object>> cons = new ArrayList<Map<String, Object>>(list(o.entry, "constraints"));
        if (o.link != null) cons.addAll(list(o.link, "constraints"));
        for (Map<String, Object> k : cons) {
            if (!"forces".equals(str(k, "field")) || !"max".equals(str(k, "type"))) continue;
            double v = o.eff.cons.containsKey(str(k, "id")) ? o.eff.cons.get(str(k, "id")) : num(k, "value", 0);
            Object so = resolveScope(str(k, "scope"), c);
            double n = so == null ? 0 : countForces(so, str(o.entry, "id"), true);
            if (n + 1 > v) return false;
        }
        return true;
    }

    public Force addForce(Force parent, ForceOpt o, String catalogueId) {
        Force f = new Force();
        f.entryId = str(o.entry, "id");
        f.linkId = o.link == null ? null : str(o.link, "id");
        f.catalogueId = catalogueId != null ? catalogueId : (parent != null ? parent.catalogueId : r.catalogueId);
        f.roster = r;
        f.parent = parent;
        if (parent == null) r.forces.add(f); else parent.children.add(f);
        changed();
        return f;
    }

    public void removeForce(Force f) {
        if (f.parent == null) r.forces.remove(f); else f.parent.children.remove(f);
        changed();
    }

    // ================================================================ добавление выборов
    public Sel addUnit(Force f, Node n) {
        Sel s = new Sel();
        s.entryId = n.opt.id();
        s.linkId = n.opt.linkId();
        s.cat = n.place;
        s.force = f;
        f.sels.add(s);
        changed();
        autofill(s, 0);
        return s;
    }

    public Sel addSel(Force f, Sel parent, Opt o, int number) {
        Sel s = new Sel();
        s.entryId = o.id();
        s.linkId = o.linkId();
        s.groups = new ArrayList<String>(o.groups == null ? new ArrayList<String>() : o.groups);
        s.number = Math.max(1, number);
        s.force = f;
        s.parent = parent;
        if (parent == null) f.sels.add(s); else parent.children.add(s);
        changed();
        autofill(s, 0);
        return s;
    }

    public void removeSel(Sel s) {
        if (s.parent == null) s.force.sels.remove(s); else s.parent.children.remove(s);
        changed();
    }

    /** Добавить обязательные дочерние выборы (минимумы и группы с выбором по умолчанию). */
    public void autofill(Sel s, int depth) {
        if (depth > 6) return;
        // по одному добавлению за проход: условия пересчитываются после каждого изменения
        for (int pass = 0; pass < 40; pass++) {
            List<Node> opts = options(entryOf(s), linkOf(s), s);
            if (!fillNodes(s, opts, depth)) break;
        }
    }

    private boolean fillNodes(Sel s, List<Node> nodes, int depth) {
        for (Node n : nodes) {
            if (n.eff.hidden) continue;
            if (n.isGroup()) {
                // сначала вложенные требования, потом сама группа
                if (fillNodes(s, n.children, depth)) return true;
                double min = minOf(n, s);
                double max = maxOf(n, s);
                double have = countInGroup(s, n.opt.id());
                if (min > have && (max < 0 || have < max)) {
                    int need = (int) (min - have);
                    for (Node def : defaultCandidates(n)) {
                        double cap = maxOf(def, s);
                        double cur = countDirect(s, def.opt);
                        double room = cap < 0 ? need : cap - cur;
                        // лимиты вложенных групп на пути к варианту
                        List<Node> path = new ArrayList<Node>();
                        if (pathTo(n, def, path)) {
                            for (Node g : path) {
                                double gm = maxOf(g, s);
                                if (gm >= 0) room = Math.min(room, gm - countInGroup(s, g.opt.id()));
                            }
                        }
                        int add = (int) Math.min(need, room);
                        if (add <= 0) continue;
                        Sel ex = findChild(s, def.opt);
                        if (ex != null) { ex.number += add; changed(); }
                        else addSel(s.force, s, def.opt, add);
                        return true;
                    }
                }
            } else {
                double min = minOf(n, s);
                if (min <= 0) continue;
                Sel ex = findChild(s, n.opt);
                int have = ex == null ? 0 : ex.number;
                if (have >= min) continue;
                if (ex != null) { ex.number = (int) min; changed(); }
                else addSel(s.force, s, n.opt, (int) min);
                return true;
            }
        }
        return false;
    }

    /** Кандидаты для автозаполнения группы: сначала явный вариант по умолчанию, потом бесплатные, потом остальные. */
    private List<Node> defaultCandidates(Node g) {
        List<Node> out = new ArrayList<Node>();
        Node ex = defaultExplicit(g);
        if (ex != null) out.add(ex);
        List<Node> leaves = new ArrayList<Node>();
        collectLeaves(g, leaves);
        for (Node l : leaves) if (!out.contains(l) && l.eff.cost(d.pointsId) == 0) out.add(l);
        for (Node l : leaves) if (!out.contains(l)) out.add(l);
        return out;
    }

    /** Путь из групп (без корня) от группы g до листа target. */
    private boolean pathTo(Node g, Node target, List<Node> path) {
        for (Node c : g.children) {
            if (c == target) return true;
            if (c.isGroup()) {
                path.add(c);
                if (pathTo(c, target, path)) return true;
                path.remove(path.size() - 1);
            }
        }
        return false;
    }

    private void collectLeaves(Node g, List<Node> out) {
        for (Node c : g.children) {
            if (c.eff.hidden) continue;
            if (c.isGroup()) collectLeaves(c, out); else out.add(c);
        }
    }

    private Node defaultIn(Node g) {
        Node d1 = defaultExplicit(g);
        if (d1 != null) return d1;
        return firstDeep(g);
    }

    /** Явно указанный вариант по умолчанию в группе или во вложенных группах. */
    private Node defaultExplicit(Node g) {
        String def = str(g.opt.entry, "defaultSelectionEntryId");
        if (def == null && g.opt.link != null) def = str(g.opt.link, "defaultSelectionEntryId");
        if (def != null && !def.equals("none")) {
            for (Node c : g.children) {
                if (c.eff.hidden || c.isGroup()) continue;
                if (def.equals(c.opt.id()) || def.equals(c.opt.linkId())) return c;
            }
        }
        for (Node c : g.children) {
            if (c.eff.hidden || !c.isGroup()) continue;
            Node x = defaultExplicit(c);
            if (x != null) return x;
        }
        return null;
    }

    private Node firstDeep(Node g) {
        for (Node c : g.children) if (!c.eff.hidden && !c.isGroup() && c.eff.cost(d.pointsId) == 0) return c;
        for (Node c : g.children) if (!c.eff.hidden && !c.isGroup()) return c;
        for (Node c : g.children) {
            if (c.eff.hidden || !c.isGroup()) continue;
            Node x = firstDeep(c);
            if (x != null) return x;
        }
        return null;
    }

    /**
     * Убрать выбор, который стал запрещён другим выбором (например, «Pair of lightning claws»
     * заменяет и пистолет, и меч — их группы закрываются). Возвращает названия снятого.
     */
    public List<String> resolveForbidden(Sel root) {
        List<String> removed = new ArrayList<String>();
        for (int k = 0; k < 10; k++) {
            boolean did = false;
            for (Issue i : validateUnit(root)) {
                if (!i.error || i.kind != Issue.OPT_MAX || i.v != 0 || !(i.where instanceof Sel)) continue;
                Sel w = (Sel) i.where;
                Node n = findByName(options(entryOf(w), linkOf(w), w), i.subject);
                if (n == null) continue;
                List<Sel> rm = new ArrayList<Sel>();
                if (n.isGroup()) { for (Sel c : w.children) if (c.groups.contains(n.opt.id())) rm.add(c); }
                else for (Sel c : w.children) if (sameOpt(c, n.opt)) rm.add(c);
                if (rm.isEmpty()) continue;
                for (Sel c : rm) { w.children.remove(c); removed.add(effSel(c).name); }
                changed();
                did = true;
                break;
            }
            if (!did) break;
        }
        return removed;
    }

    private static Node findByName(List<Node> nodes, String name) {
        for (Node n : nodes) {
            if (name.equals(n.eff.name)) return n;
            if (n.isGroup()) { Node x = findByName(n.children, name); if (x != null) return x; }
        }
        return null;
    }

    public Sel findChild(Sel s, Opt o) {
        for (Sel c : s.children) if (sameOpt(c, o)) return c;
        return null;
    }

    /** Выбор сделан через этот же вариант (та же запись, ссылка и путь групп). */
    public static boolean sameOpt(Sel c, Opt o) {
        if (!c.entryId.equals(o.id()) || !eq(c.linkId, o.linkId())) return false;
        List<String> g = o.groups == null ? new ArrayList<String>() : o.groups;
        return c.groups.equals(g);
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    public double countInGroup(Sel s, String groupId) {
        double n = 0;
        for (Sel c : s.children) if (c.groups.contains(groupId)) n += c.number;
        return n;
    }

    /** Минимум (область parent) для варианта под выбором s. */
    public double minOf(Node n, Object parent) {
        double min = 0;
        for (Map<String, Object> k : constraintsOf(n.opt)) {
            if (!"min".equals(str(k, "type")) || !"selections".equals(str(k, "field"))) continue;
            String sc = str(k, "scope");
            if (!"parent".equals(sc)) continue;
            min = Math.max(min, conVal(n.eff, k) * mult(parent));
        }
        return min;
    }

    /** Максимум добавляемых экземпляров варианта под родителем (учитывая все области). -1 — без ограничения. */
    public double maxOf(Node n, Object parent) {
        double best = -1;
        Ctx c = candidate(parent);
        double inParent = n.isGroup() ? (parent instanceof Sel ? countInGroup((Sel) parent, n.opt.id()) : 0) : countDirect(parent, n.opt);
        for (Map<String, Object> k : constraintsOf(n.opt)) {
            if (!"max".equals(str(k, "type")) || !"selections".equals(str(k, "field"))) continue;
            double v = conVal(n.eff, k);
            if (v < 0) continue;
            String sc = str(k, "scope");
            double allowed;
            if ("parent".equals(sc)) allowed = v * mult(parent);
            else {
                Object so = resolveScope(sc, c);
                if (so == null) continue;
                double inScope = countMatching(so, n.opt, bool(k, "includeChildSelections"), bool(k, "includeChildForces"));
                allowed = v - (inScope - inParent);
            }
            best = best < 0 ? allowed : Math.min(best, allowed);
        }
        return best;
    }

    /** Выбор с number=N — это N одинаковых экземпляров: ограничения "на родителя" умножаются. */
    public static int mult(Object parent) {
        return parent instanceof Sel ? Math.max(1, ((Sel) parent).number) : 1;
    }

    /** Изменить численность выбора (например, моделей) с пересчётом дочернего снаряжения. */
    public void setNumber(Sel s, int n) {
        int old = s.number;
        if (n < 1) n = 1;
        if (n == old) return;
        s.number = n;
        for (Sel c : s.children) {
            if (c.number == old) c.number = n;
            else if (c.number > n) c.number = n;
        }
        changed();
        autofill(s, 0);
    }

    double conVal(Eff e, Map<String, Object> k) {
        Double v = e.cons.get(str(k, "id"));
        return v != null ? v : num(k, "value", 0);
    }

    public List<Map<String, Object>> constraintsOf(Opt o) {
        List<Map<String, Object>> l = new ArrayList<Map<String, Object>>(list(o.entry, "constraints"));
        if (o.link != null) l.addAll(list(o.link, "constraints"));
        return l;
    }

    public double countDirect(Object parent, Opt o) {
        List<Sel> kids = parent instanceof Sel ? ((Sel) parent).children : parent instanceof Force ? ((Force) parent).sels : new ArrayList<Sel>();
        double n = 0;
        for (Sel s : kids) if (sameOpt(s, o)) n += s.number;
        return n;
    }

    private double countMatching(Object so, Opt o, boolean ics, boolean icf) {
        List<Sel> all = new ArrayList<Sel>();
        selsIn(so, ics || !(so instanceof Sel), icf || so instanceof Roster, all);
        double n = 0;
        String id = o.id();
        for (Sel s : all) {
            if (o.group ? s.groups.contains(id) : id.equals(s.entryId)) n += s.number;
        }
        return n;
    }

    public Sel findChildPublic(Sel parent, Opt o) { return findChild(parent, o); }

    /** Установить количество варианта под родителем (Sel), добавляя/удаляя выборы. */
    public void setCount(Sel parent, Opt o, int n) {
        Sel ex = findChild(parent, o);
        if (n <= 0) { if (ex != null) removeSel(ex); return; }
        if (ex == null) addSel(parent.force, parent, o, n);
        else { ex.number = n; changed(); }
    }

    // ================================================================ проверка
    public static final class Issue {
        public final boolean error;
        public final String text;
        public final Object where;
        /** Тип проблемы — для понятного объяснения в интерфейсе. */
        public int kind;
        public String subject, extra, scope, slotId, costId;
        public double n, v;
        Issue(boolean error, String text, Object where) { this.error = error; this.text = text; this.where = where; }
        Issue k(int kind, String subject, String extra, double n, double v) {
            this.kind = kind; this.subject = subject; this.extra = extra; this.n = n; this.v = v; return this;
        }
        public static final int LIMIT = 1, FORCE_RULE = 2, FORCE_COST = 3, FORCE_MAX = 4, FORCE_MIN = 5, NEED_DET = 6,
                SLOT_OVER = 7, SLOT_MIN = 8, UNIT_RULE = 9, UNIT_COST = 10, OPT_MAX = 11, OPT_MIN = 12, LB_EMPTY = 13, LB_UNUSED = 14;
    }

    public List<Issue> validate() {
        List<Issue> out = new ArrayList<Issue>();
        double pts = rosterCost(d.pointsId);
        if (r.limit > 0 && pts > r.limit)
            out.add(new Issue(true, "Превышен лимит очков: " + fmt(pts) + " из " + fmt(r.limit), r).k(Issue.LIMIT, null, null, pts, r.limit));
        List<Force> all = new ArrayList<Force>();
        r.allForces(all);
        for (Force f : all) validateForce(f, out);
        return out;
    }

    private void validateForce(Force f, List<Issue> out) {
        Eff fe = effForce(f);
        String fname = fe.name;
        for (String e : fe.errors) out.add(new Issue(true, fname + ": " + e, f).k(Issue.FORCE_RULE, fname, e, 0, 0));
        for (String w : fe.warnings) out.add(new Issue(false, fname + ": " + w, f).k(Issue.FORCE_RULE, fname, w, 0, 0));
        // ограничения на число детачментов и бюджеты (стоимость)
        Ctx c = ctxOf(f);
        List<Map<String, Object>> cons = new ArrayList<Map<String, Object>>(list(d.forces.get(f.entryId), "constraints"));
        if (f.linkId != null) cons.addAll(list(d.forceLinks.get(f.linkId), "constraints"));
        for (Map<String, Object> k : cons) {
            String field = str(k, "field");
            double v = conVal(fe, k);
            double n;
            if ("forces".equals(field)) {
                Object so = resolveScope(str(k, "scope"), new Ctx(null, f.parent != null ? f.parent : r));
                if (so == null) continue;
                n = countForces(so, f.entryId, true);
            } else if (field != null && d.costTypeIds.contains(field)) {
                // ограничение считает саму эту силу (все её экземпляры) в пределах scope
                Object so = "self".equals(str(k, "scope")) ? f : resolveScope(str(k, "scope"), c);
                if (so == null) continue;
                if (so == f) n = totalCost(f, field);
                else {
                    List<Force> fs = new ArrayList<Force>();
                    if (so instanceof Roster) r.allForces(fs);
                    else if (so instanceof Force) collectForces((Force) so, true, fs);
                    n = 0;
                    for (Force x : fs) if (f.entryId.equals(x.entryId)) n += forceCost(x, field);
                }
                if ("max".equals(str(k, "type")) && n > v) {
                    Issue is = new Issue(true, fname + ": " + d.costName(field) + " — " + fmt(n) + " из " + fmt(v), f).k(Issue.FORCE_COST, fname, d.costName(field), n, v);
                    is.costId = field;
                    out.add(is);
                }
                continue;
            } else continue;
            if ("max".equals(str(k, "type")) && n > v) out.add(new Issue(true, fname + ": не больше " + fmt(v) + " в ростере", f).k(Issue.FORCE_MAX, fname, null, n, v));
            if ("min".equals(str(k, "type")) && n < v && f.parent == null) out.add(new Issue(true, fname + ": нужно минимум " + fmt(v), f).k(Issue.FORCE_MIN, fname, null, n, v));
        }
        // обязательные детачменты внутри
        Map<String, Object> pe = d.forces.get(f.entryId);
        for (Map<String, Object> ch : list(pe, "forceEntries")) {
            Eff ce = eff(ch, null, candidate(f));
            if (ce.hidden) continue;
            for (Map<String, Object> k : list(ch, "constraints")) {
                if (!"min".equals(str(k, "type")) || !"forces".equals(str(k, "field"))) continue;
                Object so = resolveScope(str(k, "scope"), candidate(f));
                if (so == null) continue;
                double n = countForces(so, str(ch, "id"), true);
                double v = conVal(ce, k);
                if (n < v) out.add(new Issue(true, "Нужен детачмент: " + ce.name, f).k(Issue.NEED_DET, ce.name, fname, n, v));
            }
        }
        // слоты
        for (CatSlot s : slots(f)) {
            if (s.max >= 0 && s.count > s.max) { Issue is = new Issue(true, fname + ": слот «" + s.name + "» — " + fmt(s.count) + " из " + fmt(s.max), f).k(Issue.SLOT_OVER, s.name, fname, s.count, s.max); is.slotId = s.categoryId; out.add(is); }
            if (s.count < s.min) { Issue is = new Issue(true, fname + ": нужно «" + s.name + "» × " + fmt(s.min), f).k(Issue.SLOT_MIN, s.name, fname, s.count, s.min); is.slotId = s.categoryId; out.add(is); }
        }
        // юниты
        for (Sel s : f.sels) validateSel(s, out);
        checkLogistical(f, out);
        // корневые ограничения (на ростер/силу) для того, что уже выбрано
        Set<String> checked = new HashSet<String>();
        Data.Cat cat = d.cats.get(f.catalogueId);
        if (cat != null) {
            for (Opt o : cat.roots) {
                Node n = node(o, f);
                if (n.eff.hidden && countDirect(f, o) == 0) continue;
                if (!slotAccepts(f, n)) continue;
                String key = o.id();
                if (!checked.add(key)) continue;
                checkConstraints(n, f, out, true);
            }
        }
    }

    /** Найти внутри юнита выбор «Logistical Benefit» (сам Prime-выгода). */
    public Sel logisticalOf(Sel s) {
        for (Sel c : s.children) {
            String n = effSel(c).name;
            if (n != null && n.contains("Mech")) continue; // у Механикума своя выгода (детачмент), без «LB -»
            if (n != null && n.startsWith("Logistical Benefit")) return c;
            Sel x = logisticalOf(c);
            if (x != null) return x;
        }
        return null;
    }

    /** Выбранный слот «LB - X» → «X» (или null). */
    public String logisticalSlot(Sel lb) {
        for (Sel c : lb.children) {
            String n = effSel(c).name;
            if (n != null && n.startsWith("LB - ")) return n.substring(5).trim();
            String x = logisticalSlot(c);
            if (x != null) return x;
        }
        return null;
    }

    /** Logistical Benefit без выбранного слота или с пустым дополнительным слотом — предупреждение. */
    private void checkLogistical(Force f, List<Issue> out) {
        List<CatSlot> slots = null;
        for (Sel s : f.sels) {
            Sel lb = logisticalOf(s);
            if (lb == null) continue;
            String uname = effSel(s).name;
            String slot = logisticalSlot(lb);
            if (slot == null) {
                out.add(new Issue(false, uname + ": Logistical Benefit без выбранного слота", s).k(Issue.LB_EMPTY, uname, null, 0, 0));
                continue;
            }
            if (slots == null) slots = slots(f);
            for (CatSlot cs : slots) {
                if (cs.hidden || !slot.equalsIgnoreCase(cs.name)) continue;
                if (cs.max >= 0 && cs.count < cs.max) out.add(new Issue(false, uname + ": слот " + slot + " от Logistical Benefit пуст", s).k(Issue.LB_UNUSED, uname, slot, cs.count, cs.max));
                break;
            }
        }
    }

    private boolean slotAccepts(Force f, Node n) {
        if (n.eff.primary == null) return false;
        Map<String, Object> fe = d.forces.get(f.entryId);
        for (Map<String, Object> cl : list(fe, "categoryLinks")) if (n.eff.primary.equals(str(cl, "targetId"))) return true;
        return false;
    }

    /** Проверка одного юнита (быстрее полной проверки ростера). */
    public List<Issue> validateUnit(Sel s) {
        List<Issue> out = new ArrayList<Issue>();
        validateSel(s, out);
        return out;
    }

    private void validateSel(Sel s, List<Issue> out) {
        Eff e = effSel(s);
        for (String er : e.errors) out.add(new Issue(true, e.name + ": " + er, s).k(Issue.UNIT_RULE, e.name, er, 0, 0));
        for (String w : e.warnings) out.add(new Issue(false, e.name + ": " + w, s).k(Issue.UNIT_RULE, e.name, w, 0, 0));
        for (Map<String, Object> k : list(entryOf(s), "constraints")) {
            String field = str(k, "field");
            if (field != null && d.costTypeIds.contains(field) && "self".equals(str(k, "scope"))) {
                double v = conVal(e, k), n = selCost(s, field);
                if ("max".equals(str(k, "type")) && n > v) out.add(new Issue(true, e.name + ": " + d.costName(field) + " " + fmt(n) + " > " + fmt(v), s).k(Issue.UNIT_COST, e.name, d.costName(field), n, v));
            }
        }
        List<Node> opts = options(entryOf(s), linkOf(s), s);
        checkNodes(opts, s, out);
        for (Sel c : s.children) validateSel(c, out);
    }

    private void checkNodes(List<Node> nodes, Sel s, List<Issue> out) {
        for (Node n : nodes) {
            checkConstraints(n, s, out, false);
            if (n.isGroup()) checkNodes(n.children, s, out);
        }
    }

    private void checkConstraints(Node n, Object parent, List<Issue> out, boolean rootOnlyIfPresent) {
        Ctx c = candidate(parent);
        boolean hidden = n.eff.hidden;
        for (Map<String, Object> k : constraintsOf(n.opt)) {
            if (!"selections".equals(str(k, "field"))) continue;
            String type = str(k, "type");
            double v = conVal(n.eff, k);
            if (v < 0) continue;
            String sc = str(k, "scope");
            if ("parent".equals(sc)) v *= mult(parent);
            Object so = resolveScope(sc, c);
            if (so == null) continue;
            if (rootOnlyIfPresent && "parent".equals(sc)) continue;
            double count = countMatching(so, n.opt, bool(k, "includeChildSelections"), bool(k, "includeChildForces"));
            if ("parent".equals(sc) && parent instanceof Sel) {
                count = n.isGroup() ? countInGroup((Sel) parent, n.opt.id()) : countDirect(parent, n.opt);
            }
            String where = parent instanceof Sel ? effSel((Sel) parent).name + " → " : "";
            String what = n.eff.name;
            if ("max".equals(type) && count > v) {
                Issue is = new Issue(true, where + what + ": максимум " + fmt(v) + scopeWord(sc) + ", выбрано " + fmt(count), parent).k(Issue.OPT_MAX, what, parent instanceof Sel ? effSel((Sel) parent).name : null, count, v);
                is.scope = sc;
                out.add(is);
            } else if ("min".equals(type) && count < v && !hidden) {
                if (rootOnlyIfPresent && "roster".equals(sc) == false && "force".equals(sc) == false) continue;
                Issue is = new Issue(true, where + what + ": минимум " + fmt(v) + scopeWord(sc) + ", выбрано " + fmt(count), parent).k(Issue.OPT_MIN, what, parent instanceof Sel ? effSel((Sel) parent).name : null, count, v);
                is.scope = sc;
                out.add(is);
            }
        }
    }

    private static String scopeWord(String sc) {
        if ("roster".equals(sc)) return " на ростер";
        if ("force".equals(sc)) return " на детачмент";
        if ("unit".equals(sc)) return " на юнит";
        return "";
    }

    public static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }

    // ================================================================ профили и правила
    public List<Map<String, Object>> profilesOf(Map<String, Object> entry, Map<String, Object> link) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Set<String> seen = new HashSet<String>();
        addProfiles(entry, out, seen);
        if (link != null) addProfiles(link, out, seen);
        return out;
    }

    private void addProfiles(Map<String, Object> n, List<Map<String, Object>> out, Set<String> seen) {
        for (Map<String, Object> p : list(n, "profiles")) if (seen.add(str(p, "id")) && !bool(p, "hidden")) out.add(p);
        for (Map<String, Object> il : list(n, "infoLinks")) {
            if (!"profile".equals(str(il, "type"))) continue;
            Map<String, Object> p = d.profiles.get(str(il, "targetId"));
            if (p != null && seen.add(str(p, "id")) && !bool(il, "hidden")) out.add(p);
        }
        for (Map<String, Object> ig : list(n, "infoGroups")) addProfiles(ig, out, seen);
    }

    public List<Map<String, Object>> rulesOf(Map<String, Object> entry, Map<String, Object> link) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Set<String> seen = new HashSet<String>();
        addRules(entry, out, seen);
        if (link != null) addRules(link, out, seen);
        return out;
    }

    private void addRules(Map<String, Object> n, List<Map<String, Object>> out, Set<String> seen) {
        for (Map<String, Object> p : list(n, "rules")) if (seen.add(str(p, "id")) && !bool(p, "hidden")) out.add(p);
        for (Map<String, Object> il : list(n, "infoLinks")) {
            if (!"rule".equals(str(il, "type"))) continue;
            Map<String, Object> p = d.rules.get(str(il, "targetId"));
            if (p != null && seen.add(str(p, "id")) && !bool(il, "hidden")) {
                Map<String, Object> copy = new LinkedHashMap<String, Object>(p);
                if (il.get("name") != null) copy.put("name", il.get("name"));
                out.add(copy);
            }
        }
        for (Map<String, Object> ig : list(n, "infoGroups")) addRules(ig, out, seen);
    }
}
