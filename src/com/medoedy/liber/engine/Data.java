package com.medoedy.liber.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Игровая система + каталоги в формате BSData JSON, с индексами по id. */
@SuppressWarnings("unchecked")
public final class Data {

    public static final class Cat {
        public String id, name, file;
        public boolean library;
        public Map<String, Object> node;
        public List<Opt> roots;          // доступные корневые записи (с импортом)
        public List<Map<String, Object>> forceEntries = new ArrayList<Map<String, Object>>();
    }

    public Map<String, Object> gs;
    public final Map<String, Cat> cats = new LinkedHashMap<String, Cat>();
    public final Map<String, Map<String, Object>> entries = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> groups = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> links = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> categories = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> forces = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> forceLinks = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> profiles = new HashMap<String, Map<String, Object>>();
    public final Map<String, Map<String, Object>> rules = new HashMap<String, Map<String, Object>>();
    public final Set<String> costTypeIds = new HashSet<String>();
    public final List<Map<String, Object>> costTypes = new ArrayList<Map<String, Object>>();
    public String pointsId;
    public int revision;

    // ------------------------------------------------------------ helpers
    public static String str(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object o = m.get(k);
        return o == null ? null : (o instanceof Double ? fmtNum((Double) o) : o.toString());
    }

    static String fmtNum(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    public static boolean bool(Map<String, Object> m, String k) {
        if (m == null) return false;
        Object o = m.get(k);
        return o instanceof Boolean ? (Boolean) o : "true".equals(o);
    }

    public static double num(Map<String, Object> m, String k, double def) {
        if (m == null) return def;
        Object o = m.get(k);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) try { return Double.parseDouble((String) o); } catch (Exception e) { return def; }
        if (o instanceof Boolean) return ((Boolean) o) ? 1 : 0;
        return def;
    }

    public static List<Map<String, Object>> list(Map<String, Object> m, String k) {
        if (m == null) return Collections.emptyList();
        Object o = m.get(k);
        if (o instanceof List) return (List<Map<String, Object>>) o;
        return Collections.emptyList();
    }

    public static String clean(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------ loading
    public void loadSystem(String json) {
        Map<String, Object> root = (Map<String, Object>) Json.parse(json);
        gs = (Map<String, Object>) root.get("gameSystem");
        revision = (int) num(gs, "revision", 0);
        for (Map<String, Object> c : list(gs, "costTypes")) {
            costTypes.add(c);
            costTypeIds.add(str(c, "id"));
            if (pointsId == null && str(c, "name").toLowerCase().startsWith("point")) pointsId = str(c, "id");
        }
        index(gs, null);
        for (Map<String, Object> f : list(gs, "forceEntries")) indexForce(f);
    }

    public Cat loadCatalogue(String json, String file) {
        Map<String, Object> root = (Map<String, Object>) Json.parse(json);
        Map<String, Object> c = (Map<String, Object>) root.get("catalogue");
        if (c == null) return null;
        Cat cat = new Cat();
        cat.id = str(c, "id");
        cat.name = clean(str(c, "name"));
        cat.file = file;
        cat.library = bool(c, "library");
        cat.node = c;
        cats.put(cat.id, cat);
        index(c, cat);
        for (Map<String, Object> f : list(c, "forceEntries")) { indexForce(f); cat.forceEntries.add(f); }
        return cat;
    }

    private void indexForce(Map<String, Object> f) {
        put(forces, f);
        for (Map<String, Object> l : list(f, "forceEntryLinks")) put(forceLinks, l);
        for (Map<String, Object> c : list(f, "forceEntries")) indexForce(c);
    }

    private static void put(Map<String, Map<String, Object>> map, Map<String, Object> n) {
        String id = str(n, "id");
        if (id != null && !map.containsKey(id)) map.put(id, n);
    }

    private void index(Object o, Cat cat) {
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (!(v instanceof List)) continue;
                List<Object> l = (List<Object>) v;
                if (k.equals("selectionEntries") || k.equals("sharedSelectionEntries")) {
                    for (Object x : l) put(entries, (Map<String, Object>) x);
                } else if (k.equals("selectionEntryGroups") || k.equals("sharedSelectionEntryGroups")) {
                    for (Object x : l) put(groups, (Map<String, Object>) x);
                } else if (k.equals("entryLinks")) {
                    for (Object x : l) put(links, (Map<String, Object>) x);
                } else if (k.equals("categoryEntries")) {
                    for (Object x : l) put(categories, (Map<String, Object>) x);
                } else if (k.equals("profiles") || k.equals("sharedProfiles")) {
                    for (Object x : l) put(profiles, (Map<String, Object>) x);
                } else if (k.equals("rules") || k.equals("sharedRules")) {
                    for (Object x : l) put(rules, (Map<String, Object>) x);
                }
                for (Object x : l) index(x, cat);
            }
        } else if (o instanceof List) {
            for (Object x : (List<Object>) o) index(x, cat);
        }
    }

    /** После загрузки всех каталогов: вычислить корневые записи с импортом. */
    public void finish() {
        for (Cat c : cats.values()) {
            List<Opt> roots = new ArrayList<Opt>();
            Set<String> seen = new HashSet<String>();
            addRoots(c, roots, seen, new HashSet<String>());
            for (Map<String, Object> e : list(gs, "selectionEntries")) addOpt(roots, seen, e, null);
            for (Map<String, Object> l : list(gs, "entryLinks")) addOpt(roots, seen, resolveEntry(l), l);
            c.roots = roots;
        }
    }

    private void addRoots(Cat c, List<Opt> out, Set<String> seen, Set<String> visited) {
        if (!visited.add(c.id)) return;
        for (Map<String, Object> e : list(c.node, "selectionEntries")) addOpt(out, seen, e, null);
        for (Map<String, Object> l : list(c.node, "entryLinks")) addOpt(out, seen, resolveEntry(l), l);
        for (Map<String, Object> cl : list(c.node, "catalogueLinks")) {
            if (!bool(cl, "importRootEntries")) continue;
            Cat t = cats.get(str(cl, "targetId"));
            if (t != null) addRoots(t, out, seen, visited);
        }
    }

    private void addOpt(List<Opt> out, Set<String> seen, Map<String, Object> e, Map<String, Object> link) {
        if (e == null) return;
        String key = (link != null ? str(link, "id") : "") + ":" + str(e, "id");
        if (!seen.add(key)) return;
        out.add(new Opt(e, link, new ArrayList<String>(), link != null && "selectionEntryGroup".equals(str(link, "type"))));
    }

    public Map<String, Object> resolveEntry(Map<String, Object> link) {
        String t = str(link, "targetId");
        String type = str(link, "type");
        if ("selectionEntryGroup".equals(type)) return groups.get(t);
        return entries.get(t);
    }

    public List<Cat> playable() {
        List<Cat> out = new ArrayList<Cat>();
        for (Cat c : cats.values()) if (!c.library) out.add(c);
        Collections.sort(out, new java.util.Comparator<Cat>() {
            public int compare(Cat a, Cat b) { return sortKey(a.name).compareTo(sortKey(b.name)); }
        });
        return out;
    }

    /** Легионы сортируются по номеру (I, II, …, XX), остальные — по алфавиту после них. */
    public static String sortKey(String name) {
        int dash = name.indexOf(" - ");
        if (dash > 0) {
            int n = roman(name.substring(0, dash).trim());
            if (n > 0) return String.format("0%02d", n);
        }
        return "1" + name;
    }

    public static int roman(String s) {
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v;
            switch (s.charAt(i)) {
                case 'I': v = 1; break;
                case 'V': v = 5; break;
                case 'X': v = 10; break;
                case 'L': v = 50; break;
                default: return 0;
            }
            if (v < prev) total -= v; else { total += v; prev = v; }
        }
        return total;
    }

    public String categoryName(String id) {
        Map<String, Object> c = categories.get(id);
        return c == null ? "?" : clean(str(c, "name"));
    }

    public String costName(String id) {
        for (Map<String, Object> c : costTypes) if (id.equals(str(c, "id"))) return clean(str(c, "name"));
        return id;
    }
}
