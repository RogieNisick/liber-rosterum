package com.medoedy.liber.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ростер: дерево сил (детачментов) и выборов. */
public final class Roster {
    public String id, name, catalogueId;
    public double limit = 3000;
    public long updated;
    public final List<Force> forces = new ArrayList<Force>();

    public static final class Force {
        public String entryId, linkId, catalogueId;
        public Force parent;
        public Roster roster;
        public final List<Force> children = new ArrayList<Force>();
        public final List<Sel> sels = new ArrayList<Sel>();
    }

    public static final class Sel {
        public String entryId, linkId;
        public String cat; // слот (категория), в который поставлен корневой юнит
        public List<String> groups = new ArrayList<String>();
        public int number = 1;
        public Sel parent;
        public Force force;
        public final List<Sel> children = new ArrayList<Sel>();
    }

    public void allForces(List<Force> out) {
        for (Force f : forces) collect(f, out);
    }

    private static void collect(Force f, List<Force> out) {
        out.add(f);
        for (Force c : f.children) collect(c, out);
    }

    // ------------------------------------------------------------ сохранение
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("name", name);
        m.put("catalogueId", catalogueId);
        m.put("limit", limit);
        m.put("updated", (double) updated);
        List<Object> fs = new ArrayList<Object>();
        for (Force f : forces) fs.add(forceJson(f));
        m.put("forces", fs);
        return m;
    }

    private static Map<String, Object> forceJson(Force f) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("e", f.entryId);
        if (f.linkId != null) m.put("l", f.linkId);
        m.put("c", f.catalogueId);
        List<Object> ch = new ArrayList<Object>();
        for (Force c : f.children) ch.add(forceJson(c));
        m.put("f", ch);
        List<Object> ss = new ArrayList<Object>();
        for (Sel s : f.sels) ss.add(selJson(s));
        m.put("s", ss);
        return m;
    }

    private static Map<String, Object> selJson(Sel s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("e", s.entryId);
        if (s.linkId != null) m.put("l", s.linkId);
        if (s.cat != null) m.put("k", s.cat);
        if (!s.groups.isEmpty()) m.put("g", new ArrayList<Object>(s.groups));
        if (s.number != 1) m.put("n", (double) s.number);
        if (!s.children.isEmpty()) {
            List<Object> ch = new ArrayList<Object>();
            for (Sel c : s.children) ch.add(selJson(c));
            m.put("s", ch);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Roster fromJson(Map<String, Object> m) {
        Roster r = new Roster();
        r.id = Data.str(m, "id");
        r.name = Data.str(m, "name");
        r.catalogueId = Data.str(m, "catalogueId");
        r.limit = Data.num(m, "limit", 3000);
        r.updated = (long) Data.num(m, "updated", 0);
        for (Map<String, Object> f : Data.list(m, "forces")) r.forces.add(forceFrom(f, r, null));
        return r;
    }

    private static Force forceFrom(Map<String, Object> m, Roster r, Force parent) {
        Force f = new Force();
        f.entryId = Data.str(m, "e");
        f.linkId = Data.str(m, "l");
        f.catalogueId = Data.str(m, "c");
        f.roster = r;
        f.parent = parent;
        for (Map<String, Object> c : Data.list(m, "f")) f.children.add(forceFrom(c, r, f));
        for (Map<String, Object> s : Data.list(m, "s")) f.sels.add(selFrom(s, f, null));
        return f;
    }

    @SuppressWarnings("unchecked")
    private static Sel selFrom(Map<String, Object> m, Force f, Sel parent) {
        Sel s = new Sel();
        s.entryId = Data.str(m, "e");
        s.linkId = Data.str(m, "l");
        s.cat = Data.str(m, "k");
        Object g = m.get("g");
        if (g instanceof List) for (Object x : (List<Object>) g) s.groups.add(x.toString());
        s.number = (int) Data.num(m, "n", 1);
        s.force = f;
        s.parent = parent;
        for (Map<String, Object> c : Data.list(m, "s")) s.children.add(selFrom(c, f, s));
        return s;
    }
}
