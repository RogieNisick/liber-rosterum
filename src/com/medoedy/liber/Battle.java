package com.medoedy.liber;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Идущая битва: миссия, игроки, контроль маркеров по ходам, вторичные цели, бонусы. */
final class Battle {
    Missions.Mission mission;
    String deployment = Missions.DEPLOYMENTS[0][1];
    int turn = 1;
    boolean finished;
    String[] names = {"Игрок 1", "Игрок 2"};
    String[] factions = {"", ""};
    int first = 0;
    int[][] control;      // [маркер][ход] → -1 никто, 0, 1
    int[][] bonus;        // [игрок][ход]
    boolean[][] secondary; // [игрок][цель]
    boolean[] sudden = new boolean[2];   // внезапная смерть: у этого игрока остались модели (+3 ПО)
    boolean[] counter = new boolean[2];  // объявлено контрнаступление
    int attacker = 0;                    // для асимметричных миссий
    int[] pick;                          // Advancing Objectives: какой маркер засчитан в ходу (-1 — никакой)
    long started = System.currentTimeMillis();
    long turnStarted = System.currentTimeMillis();

    int maxTurns() { return mission.turns + (mission.extraTurn ? 1 : 0); }

    void init() {
        int t = Math.max(mission.turns, maxTurns()) + 1;
        pick = new int[t];
        java.util.Arrays.fill(pick, -1);
        control = new int[mission.objectives.size()][t];
        for (int[] row : control) java.util.Arrays.fill(row, -1);
        bonus = new int[2][t];
        secondary = new boolean[2][mission.secondaries.size()];
    }

    /** Ценность маркера в ходу t с учётом «окна возможностей». */
    int value(int o, int t) {
        Missions.Objective ob = mission.objectives.get(o);
        if (mission.advancing) {
            int v = ob.vp;
            for (int k = 1; k < t && k < pick.length; k++) if (control[o][k] == attacker && pick[k] != o) v++;
            return Math.min(3, v);
        }
        if (!ob.decays) return ob.vp;
        int scored = 0;
        for (int k = 1; k < t; k++) if (control[o][k] >= 0) scored++;
        return Math.max(0, ob.vp - scored);
    }

    /** ПО за маркеры в ходу t (без доп. очков). */
    int objTurn(int p, int t) {
        int s = 0;
        if (mission.advancing) {
            if (p == attacker && t < pick.length && pick[t] >= 0 && control[pick[t]][t] == p) s = value(pick[t], t);
        } else {
            for (int o = 0; o < control.length; o++) if (control[o][t] == p) s += value(o, t);
        }
        if (counter[p] && t == mission.turns) s *= 2;
        return s;
    }

    int primary(int p, int upto) {
        int s = 0;
        for (int t = 1; t <= upto; t++) s += objTurn(p, t);
        return s;
    }

    int primaryTurn(int p, int t) { return objTurn(p, t) + bonus[p][t]; }

    /** Счёт до начала хода t (для контрнаступления). */
    int totalBefore(int p, int t) {
        int s = 0;
        for (int k = 1; k < t; k++) s += primaryTurn(p, k);
        return s + secondaryVp(p);
    }

    boolean anySudden() { return sudden[0] || sudden[1]; }

    int secondaryVp(int p) {
        int s = 0;
        for (int i = 0; i < mission.secondaries.size(); i++) {
            if (!secondary[p][i]) continue;
            if (anySudden() && mission.secondaries.get(i).name.startsWith("Last Man")) continue;
            s += mission.secondaries.get(i).vp;
        }
        return s;
    }

    int bonusSum(int p) {
        int s = 0;
        for (int t = 1; t <= mission.turns; t++) s += bonus[p][t];
        return s;
    }

    int total(int p) {
        return primary(p, mission.turns) + bonusSum(p) + secondaryVp(p) + (sudden[p] ? 3 : 0);
    }

    // ================================================================ хранение
    static File file(MainActivity a) { return new File(a.getFilesDir(), "battle.json"); }

    void save(MainActivity a) {
        try { Store.write(file(a), Json.write(toJson())); } catch (Exception ignored) { }
    }

    static void delete(MainActivity a) { file(a).delete(); }

    @SuppressWarnings("unchecked")
    static Battle load(MainActivity a) {
        try {
            File f = file(a);
            if (!f.exists()) return null;
            return fromJson((Map<String, Object>) Json.parse(Store.read(f)));
        } catch (Exception e) { return null; }
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("mission", missionJson(mission));
        m.put("deployment", deployment);
        m.put("turn", (double) turn);
        m.put("finished", finished);
        m.put("names", list(names));
        m.put("factions", list(factions));
        m.put("first", (double) first);
        m.put("started", (double) started);
        m.put("turnStarted", (double) turnStarted);
        List<Object> c = new ArrayList<Object>();
        for (int[] row : control) c.add(ints(row));
        m.put("control", c);
        List<Object> b = new ArrayList<Object>();
        for (int[] row : bonus) b.add(ints(row));
        m.put("bonus", b);
        List<Object> s = new ArrayList<Object>();
        for (boolean[] row : secondary) { List<Object> r = new ArrayList<Object>(); for (boolean v : row) r.add(v); s.add(r); }
        m.put("secondary", s);
        List<Object> sd = new ArrayList<Object>();
        sd.add(sudden[0]); sd.add(sudden[1]);
        m.put("sudden", sd);
        List<Object> co = new ArrayList<Object>();
        co.add(counter[0]); co.add(counter[1]);
        m.put("counter", co);
        m.put("attacker", (double) attacker);
        m.put("pick", ints(pick));
        return m;
    }

    @SuppressWarnings("unchecked")
    static Battle fromJson(Map<String, Object> m) {
        Battle b = new Battle();
        b.mission = missionFrom((Map<String, Object>) m.get("mission"));
        b.deployment = Data.str(m, "deployment");
        b.turn = (int) Data.num(m, "turn", 1);
        b.finished = Data.bool(m, "finished");
        b.first = (int) Data.num(m, "first", 0);
        b.started = (long) Data.num(m, "started", 0);
        b.turnStarted = (long) Data.num(m, "turnStarted", b.started);
        List<Object> n = (List<Object>) m.get("names"), f = (List<Object>) m.get("factions");
        for (int i = 0; i < 2; i++) { b.names[i] = String.valueOf(n.get(i)); b.factions[i] = String.valueOf(f.get(i)); }
        b.init();
        List<Object> c = (List<Object>) m.get("control");
        for (int o = 0; o < c.size() && o < b.control.length; o++) fill(b.control[o], (List<Object>) c.get(o));
        List<Object> bo = (List<Object>) m.get("bonus");
        for (int p = 0; p < 2; p++) fill(b.bonus[p], (List<Object>) bo.get(p));
        List<Object> s = (List<Object>) m.get("secondary");
        for (int p = 0; p < 2; p++) {
            List<Object> r = (List<Object>) s.get(p);
            for (int i = 0; i < r.size() && i < b.secondary[p].length; i++) b.secondary[p][i] = Boolean.TRUE.equals(r.get(i));
        }
        List<Object> sd = (List<Object>) m.get("sudden");
        b.sudden[0] = Boolean.TRUE.equals(sd.get(0));
        b.sudden[1] = Boolean.TRUE.equals(sd.get(1));
        Object co = m.get("counter");
        if (co instanceof List) { List<Object> cl = (List<Object>) co; b.counter[0] = Boolean.TRUE.equals(cl.get(0)); b.counter[1] = Boolean.TRUE.equals(cl.get(1)); }
        b.attacker = (int) Data.num(m, "attacker", 0);
        Object pk = m.get("pick");
        if (pk instanceof List) fill(b.pick, (List<Object>) pk);
        return b;
    }

    private static void fill(int[] dst, List<Object> src) {
        for (int i = 0; i < src.size() && i < dst.length; i++) dst[i] = (int) ((Number) src.get(i)).doubleValue();
    }

    private static List<Object> list(String[] a) { List<Object> l = new ArrayList<Object>(); for (String s : a) l.add(s); return l; }

    private static List<Object> ints(int[] a) { List<Object> l = new ArrayList<Object>(); for (int v : a) l.add((double) v); return l; }

    static Map<String, Object> missionJson(Missions.Mission ms) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", ms.name);
        m.put("ru", ms.ru);
        m.put("desc", ms.desc);
        m.put("turns", (double) ms.turns);
        m.put("custom", ms.custom);
        m.put("pack", ms.pack);
        m.put("book", ms.book);
        m.put("page", (double) ms.page);
        m.put("map", ms.map);
        m.put("note", ms.note);
        m.put("asym", ms.asym);
        m.put("objAttacker", ms.objAttacker);
        m.put("advancing", ms.advancing);
        m.put("extraTurn", ms.extraTurn);
        List<Object> ev = new ArrayList<Object>();
        for (Missions.Event e : ms.events) { Map<String, Object> x = new LinkedHashMap<String, Object>(); x.put("n", e.name); x.put("v", (double) e.vp); x.put("s", (double) e.side); x.put("d", e.desc); ev.add(x); }
        m.put("events", ev);
        List<Object> ob = new ArrayList<Object>();
        for (Missions.Objective o : ms.objectives) { Map<String, Object> x = new LinkedHashMap<String, Object>(); x.put("n", o.name); x.put("v", (double) o.vp); x.put("d", o.decays); ob.add(x); }
        m.put("objectives", ob);
        List<Object> se = new ArrayList<Object>();
        for (Missions.Secondary s : ms.secondaries) { Map<String, Object> x = new LinkedHashMap<String, Object>(); x.put("n", s.name); x.put("v", (double) s.vp); x.put("d", s.desc); se.add(x); }
        m.put("secondaries", se);
        List<Object> ru = new ArrayList<Object>(ms.rules);
        m.put("rules", ru);
        return m;
    }

    @SuppressWarnings("unchecked")
    static Missions.Mission missionFrom(Map<String, Object> m) {
        Missions.Mission ms = new Missions.Mission();
        ms.name = Data.str(m, "name");
        ms.ru = Data.str(m, "ru");
        ms.desc = Data.str(m, "desc");
        ms.turns = (int) Data.num(m, "turns", 4);
        ms.custom = Data.bool(m, "custom");
        if (Data.str(m, "pack") != null) ms.pack = Data.str(m, "pack");
        ms.book = Data.str(m, "book");
        ms.page = (int) Data.num(m, "page", -1);
        ms.map = Data.str(m, "map");
        ms.note = Data.str(m, "note");
        ms.asym = Data.bool(m, "asym");
        ms.objAttacker = Data.bool(m, "objAttacker");
        ms.advancing = Data.bool(m, "advancing");
        ms.extraTurn = Data.bool(m, "extraTurn");
        for (Map<String, Object> x : Data.list(m, "events")) ms.events.add(new Missions.Event(Data.str(x, "n"), (int) Data.num(x, "v", 1), (int) Data.num(x, "s", 0), Data.str(x, "d")));
        for (Map<String, Object> x : Data.list(m, "objectives")) ms.objectives.add(new Missions.Objective(Data.str(x, "n"), (int) Data.num(x, "v", 1), Data.bool(x, "d")));
        for (Map<String, Object> x : Data.list(m, "secondaries")) ms.secondaries.add(new Missions.Secondary(Data.str(x, "n"), (int) Data.num(x, "v", 1), Data.str(x, "d")));
        Object r = m.get("rules");
        if (r instanceof List) for (Object o : (List<Object>) r) ms.rules.add(String.valueOf(o));
        return ms;
    }
}
