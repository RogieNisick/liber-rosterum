package com.medoedy.liber;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Json;
import com.medoedy.liber.engine.Smart;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** «Мои миниатюры»: реальная коллекция игрока — юниты, их число, фото юнита и отдельных моделей. */
final class Minis {
    private Minis() { }

    static final class Part {
        String id, desc = "";
    }

    static final class Unit {
        String id, catId, entryId, name, notes = "";
        int units = 1, models = 1;
        final List<Part> parts = new ArrayList<Part>();
        long added = System.currentTimeMillis();

        String photoKey() { return "mini_" + id; }
        String partKey(Part p) { return "mini_" + id + "_" + p.id; }
    }

    private static List<Unit> cache;

    static File file(MainActivity a) { return new File(a.getFilesDir(), "minis.json"); }

    static synchronized List<Unit> all(MainActivity a) {
        if (cache != null) return cache;
        cache = new ArrayList<Unit>();
        try {
            File f = file(a);
            if (f.exists()) {
                @SuppressWarnings("unchecked") List<Object> l = (List<Object>) Json.parse(Store.read(f));
                for (Object o : l) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                    Unit u = new Unit();
                    u.id = Data.str(m, "id");
                    u.catId = Data.str(m, "cat");
                    u.entryId = Data.str(m, "entry");
                    u.name = Data.str(m, "name");
                    if (Data.str(m, "notes") != null) u.notes = Data.str(m, "notes");
                    u.units = (int) Data.num(m, "units", 1);
                    u.models = (int) Data.num(m, "models", 1);
                    u.added = (long) Data.num(m, "added", 0);
                    for (Map<String, Object> pm : Data.list(m, "parts")) {
                        Part p = new Part();
                        p.id = Data.str(pm, "id");
                        if (Data.str(pm, "desc") != null) p.desc = Data.str(pm, "desc");
                        u.parts.add(p);
                    }
                    cache.add(u);
                }
            }
        } catch (Exception ignored) { }
        return cache;
    }

    static synchronized void save(MainActivity a) {
        List<Object> out = new ArrayList<Object>();
        for (Unit u : all(a)) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", u.id);
            m.put("cat", u.catId);
            m.put("entry", u.entryId);
            m.put("name", u.name);
            m.put("notes", u.notes);
            m.put("units", (double) u.units);
            m.put("models", (double) u.models);
            m.put("added", (double) u.added);
            List<Object> ps = new ArrayList<Object>();
            for (Part p : u.parts) { Map<String, Object> pm = new LinkedHashMap<String, Object>(); pm.put("id", p.id); pm.put("desc", p.desc); ps.add(pm); }
            m.put("parts", ps);
            out.add(m);
        }
        try { Store.write(file(a), Json.write(out)); } catch (Exception ignored) { }
    }

    static Unit add(MainActivity a, String catId, String entryId, String name, int models) {
        Unit u = new Unit();
        u.id = Long.toString(System.currentTimeMillis(), 36);
        u.catId = catId;
        u.entryId = entryId;
        u.name = name;
        u.models = Math.max(1, models);
        all(a).add(0, u);
        save(a);
        return u;
    }

    static void remove(MainActivity a, Unit u) {
        all(a).remove(u);
        a.removePhoto(u.photoKey());
        for (Part p : u.parts) a.removePhoto(u.partKey(p));
        save(a);
    }

    /** Для умного ростера: что физически есть. */
    static List<Smart.Mini> forSmart(MainActivity a) {
        List<Smart.Mini> out = new ArrayList<Smart.Mini>();
        for (Unit u : all(a)) {
            if (u.entryId == null) continue;
            Smart.Mini m = new Smart.Mini();
            m.catId = u.catId;
            m.entryId = u.entryId;
            m.name = u.name;
            m.units = Math.max(1, u.units);
            m.models = u.models;
            out.add(m);
        }
        return out;
    }

    static int unitsOf(MainActivity a, String catId) {
        int n = 0;
        for (Unit u : all(a)) if (catId != null && catId.equals(u.catId)) n += Math.max(1, u.units);
        return n;
    }
}
