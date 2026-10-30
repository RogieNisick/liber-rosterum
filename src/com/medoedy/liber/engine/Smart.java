package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Engine.CatSlot;
import com.medoedy.liber.engine.Engine.ForceOpt;
import com.medoedy.liber.engine.Engine.Issue;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * «Умный ростер»: изучает ваши ростеры (что у вас, скорее всего, есть из миниатюр),
 * оценивает юниты по профилям оружия и собирает армию против выбранного противника.
 */
public final class Smart {
    public static boolean DEBUG = false, DEBUG_CAP = false, NOFILL = false;
    public static final int OWNED = 0, PROXY = 1, NEW = 2;

    public static final class Params {
        public String myCat, enemyCat;
        public Set<String> enemyTraits = new HashSet<String>();
        public double limit = 3000;
        public int mode = 1; // 0 — только мои, 1 — мои + прокси, 2 — свободно
        public long seed = 1;
        public Set<String> exclude = new HashSet<String>();
        /** Характер армии: infantry, armour, assault, elite, fast, dread, balanced. */
        public Set<String> style = new HashSet<String>();
        /** Союзники противника (Allied Detachment), например Solar Auxilia при Imperial Fists. */
        public List<String> enemyAllies = new ArrayList<String>();
        /** Миссия: heart, crucible, hold, ons_att, ons_def, engine — или null. */
        public String mission;
        /** Мой союзник (Allied Detachment) — фракция или null. */
        public String myAlly;
        /** «Мои миниатюры»: что физически есть у игрока. */
        public List<Mini> minis = new ArrayList<Mini>();
        /** «Обязательно взять»: entryId юнитов, которые игрок хочет видеть в ростере. */
        public Set<String> include = new HashSet<String>();
        /** Разнообразие: не больше стольких одинаковых юнитов (0 — без ограничения). */
        public int maxSame;
    }

    /** Реальная миниатюра (юнит) из раздела «Мои миниатюры». */
    public static final class Mini {
        public String catId, entryId, linkId, name;
        public int units = 1, models;
    }

    public static final String[][] MISSIONS = {
            {"heart", "Сердце битвы"}, {"crucible", "Горнило войны"}, {"hold", "Захватить и удержать"},
            {"ons_att", "Onslaught: я атакую"}, {"ons_def", "Onslaught: я защищаю"}, {"engine", "Engine Kill: против Титанов"},
    };

    public static final String[][] STYLES = {
            {"infantry", "Пехотная линия"}, {"armour", "Бронекулак"}, {"assault", "Штурм и рукопашная"},
            {"elite", "Элита и терминаторы"}, {"fast", "Быстрая армия"}, {"dread", "Дредноуты"}, {"balanced", "Сбалансированно"},
    };

    /** Возможности юнита: против техники, пехоты, элиты; живучесть, ближний бой, скорость. */
    public static final class Cap {
        public double at, ai, ae, dur, melee, speed;
        void add(Cap o, double k) { at += o.at * k; ai += o.ai * k; ae += o.ae * k; dur += o.dur * k; melee += o.melee * k; speed += o.speed * k; }
        public double get(int i) { return i == 0 ? at : i == 1 ? ai : i == 2 ? ae : i == 3 ? dur : i == 4 ? melee : speed; }
    }

    public static final String[] CAP_RU = {"против техники", "против пехоты", "против элиты", "живучесть", "ближний бой", "скорость"};

    public static final class Pick {
        public String name, why, proxyOf, slot, entryId;
        public int status;
        public double pts;
        public int rosters;
        public boolean infantry, transport;
        public String type;
        public Sel sel;
        public Cap cap;
        Owned like;
        public boolean physical;
        public boolean pinned;
        /** Снаряжение, подобранное под противника (отличия от варианта по умолчанию). */
        public final List<String> gear = new ArrayList<String>();
        /** Обоснование: {значок, заголовок, текст}. */
        public final List<String[]> explain = new ArrayList<String[]>();
        Cand cand;
    }

    public static final class Result {
        public Roster roster;
        public final List<Pick> picks = new ArrayList<Pick>();
        public final List<String> reasons = new ArrayList<String>();
        public double[] need = new double[6], have = new double[6];
        /** Краткая суть армии и план на игру. */
        public final List<String> strategy = new ArrayList<String>();
        public String archetype;
        /** Структура плана для наглядного показа. */
        public final List<String[]> core = new ArrayList<String[]>(); // {имя, роль, индекс роли}
        public final List<String> holders = new ArrayList<String>();
        public String holdNote, transport, task, weak, plan, missionNote;
        public final List<String> taskUnits = new ArrayList<String>();
        public int taskIdx = -1, weakIdx = -1;
        public int errors;
        public String error;
    }

    /** Что есть у игрока: юнит, сколько раз встречался, наибольшее число копий и шаблон снаряжения. */
    static final class Owned {
        String id, name;
        int rosters, copies, models;
        Sel template;
        long updated;
        String type, save, toughness;
        boolean physical;
        String entryId;
        int proxyUse;
    }

    private final Data d;
    private Engine e;
    private Roster r;
    private Result res;
    private Random rnd;
    private final Map<String, Owned> owned = new LinkedHashMap<String, Owned>();
    private final Set<String> usedDets = new HashSet<String>();
    private Set<String> excl = new HashSet<String>();
    private double proxyPts, newPts, limit;
    private boolean scratch; // коллекции нет — собираем с нуля
    private Set<String> style = new HashSet<String>();
    private final Map<String, Integer> usedById = new HashMap<String, Integer>();
    private final double[] norm = new double[6];
    private final Map<String, Integer> usedBySlot = new HashMap<String, Integer>();

    private int usedOf(Cand c) { Integer v = usedById.get(c.node.opt.id()); return v == null ? 0 : v; }

    public Smart(Data d) { this.d = d; }

    // ================================================================ коллекция
    /** Самая частая фракция в ростерах игрока. */
    public static String favouriteFaction(List<Roster> all) {
        Map<String, Integer> n = new HashMap<String, Integer>();
        String best = null;
        int bn = 0;
        for (Roster x : all) {
            if (x.catalogueId == null) continue;
            int v = (n.containsKey(x.catalogueId) ? n.get(x.catalogueId) : 0) + 1;
            n.put(x.catalogueId, v);
            if (v > bn) { bn = v; best = x.catalogueId; }
        }
        return best;
    }

    private void collect(List<Roster> all, String cat) {
        owned.clear();
        usedDets.clear();
        for (Roster x : all) {
            Engine ex = new Engine(d, x);
            List<Force> fs = new ArrayList<Force>();
            x.allForces(fs);
            Map<String, Integer> inThis = new HashMap<String, Integer>();
            for (Force f : fs) {
                if (!cat.equals(f.catalogueId)) continue;
                if (isContainer(f)) continue;
                usedDets.add(f.entryId);
                for (Sel s : f.sels) {
                    String id = s.entryId + "|" + s.linkId; // одна запись может быть под разными ссылками (Centurion / с прыжковым ранцем…)
                    Owned o = owned.get(id);
                    if (o == null) {
                        o = new Owned();
                        o.id = id;
                        o.name = ex.effSel(s).name;
                        owned.put(id, o);
                        modelProfile(ex, s, o);
                    }
                    int k = (inThis.containsKey(id) ? inThis.get(id) : 0) + 1;
                    inThis.put(id, k);
                    o.copies = Math.max(o.copies, k);
                    o.models = Math.max(o.models, models(ex, s));
                    if (o.template == null || x.updated >= o.updated) { o.template = s; o.updated = x.updated; }
                }
            }
            for (String id : inThis.keySet()) owned.get(id).rosters++;
        }
        physical = false;
        if (minis == null) return;
        List<Mini> mine = new ArrayList<Mini>();
        for (Mini m : minis) if (cat.equals(m.catId) && m.entryId != null) mine.add(m);
        if (mine.isEmpty()) return;
        // коллекция главнее ростеров: «моими» считаются только реально имеющиеся миниатюры
        physical = true;
        Map<String, Owned> fromRosters = new LinkedHashMap<String, Owned>(owned);
        owned.clear();
        for (Mini m : mine) {
            Owned o = null;
            for (Owned x : fromRosters.values()) if (x.id.startsWith(m.entryId + "|") && (m.linkId == null || x.id.equals(m.entryId + "|" + m.linkId))) { o = x; break; }
            String key = m.entryId + "|" + m.linkId;
            if (o == null) {
                o = owned.get(key);
                if (o == null) { o = new Owned(); o.id = key; o.name = m.name; }
                else { o.copies += Math.max(1, m.units); o.models += m.models; continue; }
                o.copies = 0;
            } else o.copies = 0;
            o.physical = true;
            o.entryId = m.entryId;
            o.copies += Math.max(1, m.units);
            o.models = Math.max(1, m.models); // в коллекции — реальное число моделей в юните
            o.rosters = Math.max(1, o.rosters);
            owned.put(o.id, o);
        }
    }

    boolean physical;
    boolean ownedPhase;
    boolean relaxNew; // при реальной коллекции сначала ставим сами миниатюры, прокси — только из оставшихся
    boolean freeMode; // «Свободно» (mode 2): коллекция не даёт преимущества — выбор чисто по силе против врага
    List<Mini> minis;
    String curCat;

    /** Сколько ещё физических юнитов этой миниатюры свободно (для прокси). */
    int spare(Owned o) {
        if (o == null) return 0;
        String eid = o.entryId != null ? o.entryId : o.id.split("\\|")[0];
        Integer used = usedById.get(eid);
        return o.copies - (used == null ? 0 : used) - o.proxyUse;
    }

    /** Кандидата ещё можно брать: не исчерпан и (для прокси при реальной коллекции) есть свободная модель. */
    int maxSame;

    boolean ok(Cand c) {
        if (c.dead || usedOf(c) >= c.maxCopies) return false;
        // разнообразие: не больше N одинаковых (транспорт не считаем — он идёт за пехотой)
        if (maxSame > 0 && !c.transport && usedOf(c) >= maxSame) return false;
        if (physical && c.status == PROXY && (ownedPhase || spare(c.like) <= 0)) return false;
        if (physical && c.status == OWNED && c.own != null && spare(c.own) <= 0) return false; // модели заняты под прокси
        return true;
    }

    private boolean isContainer(Force f) {
        Map<String, Object> fe = d.forces.get(f.entryId);
        return !Data.list(fe, "forceEntries").isEmpty() || !Data.list(fe, "forceEntryLinks").isEmpty();
    }

    private static int models(Engine ex, Sel s) {
        int n = 0;
        for (Sel c : s.children) if ("model".equals(Data.str(ex.entryOf(c), "type"))) n += c.number;
        return Math.max(1, n);
    }

    /** Тип модели, спасбросок и стойкость — чтобы искать похожие миниатюры для прокси. */
    private void modelProfile(Engine ex, Sel s, Owned o) {
        List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>();
        profiles(ex, s, ps, 0);
        for (Map<String, Object> p : ps) {
            String type = null, sv = null, t = null;
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String n = Data.str(ch, "name"), v = Data.str(ch, "$text");
                if (n == null || v == null) continue;
                if (n.equalsIgnoreCase("Type")) type = sizeKey(v, Data.str(p, "name"));
                if (n.equalsIgnoreCase("SAV") || n.equalsIgnoreCase("Front Armour")) sv = v.trim();
                if (n.equalsIgnoreCase("T") || n.equalsIgnoreCase("HP")) t = v.trim();
            }
            if (type != null) { o.type = type; o.save = sv; o.toughness = t; return; }
        }
    }

    /**
     * «Размерный класс» модели для прокси: базовый тип + подтипы, от которых зависят размер и подставка
     * (Heavy, Light, Antigrav, Skirmish…). Командир/сержант/уникальный размер не меняют.
     * Так байк не изображает лендспидер (Antigrav), Контемптор — Дередео (Walker vs Walker (Heavy)),
     * а Сатурнины (огромная броня) — только Сатурнинов.
     */
    static String sizeKey(String type, String model) {
        if (type == null) return null;
        String t = type.trim();
        String base = t.split("[ (]")[0];
        java.util.TreeSet<String> sub = new java.util.TreeSet<String>();
        int i = t.indexOf('('), j = t.lastIndexOf(')');
        if (i >= 0 && j > i) for (String x : t.substring(i + 1, j).split(",")) {
            String y = x.trim();
            if (y.equals("Heavy") || y.equals("Light") || y.equals("Antigrav") || y.equals("Skirmish") || y.equals("Malefic") || y.equals("Flyer")
                    || y.equals("Super-heavy") || y.equals("Knight") || y.equals("Titan") || y.equals("Transport") || y.equals("Paragon")) sub.add(y);
        }
        String m = model == null ? "" : model.toLowerCase();
        // в данных у некоторых терминаторов нет «Heavy» — по названию они всё равно тяжёлые
        if (base.equals("Infantry") && (m.contains("terminator") || m.contains("cataphractii") || m.contains("tartaros") || m.contains("gorgon"))) sub.add("Heavy");
        if (m.contains("saturnine")) sub.add("Saturnine");
        return base + (sub.isEmpty() ? "" : "(" + android_join(sub) + ")");
    }

    private void gearNames(Sel s, Set<String> out) {
        for (Sel ch : s.children) {
            String t = Data.str(e.entryOf(ch), "type");
            if (!"model".equals(t)) {
                String nm = e.effSel(ch).name;
                if (!nm.startsWith("Prime") && !nm.equals("Logistical Benefit")) out.add(nm);
            }
            gearNames(ch, out);
        }
    }

    /** Шасси техники — чтобы прокси были физически похожи. */
    static String chassis(String name) {
        if (name == null) return null;
        String n = name.toLowerCase();
        if (n.contains("land raider") || n.contains("proteus")) return "landraider";
        if (n.contains("spartan") || n.contains("typhon") || n.contains("cerberus") || n.contains("fellblade") || n.contains("falchion") || n.contains("glaive") || n.contains("mastodon")) return "superheavy";
        if (n.contains("sicaran")) return "sicaran";
        if (n.contains("kratos")) return "kratos";
        if (n.contains("rhino") || n.contains("predator") || n.contains("razorback") || n.contains("vindicator") || n.contains("whirlwind") || n.contains("scorpius") || n.contains("damocles")) return "rhino";
        if (n.contains("land speeder") || n.contains("javelin")) return "speeder";
        if (n.contains("storm eagle") || n.contains("fire raptor") || n.contains("thunderhawk") || n.contains("xiphon") || n.contains("storm bird") || n.contains("stormbird")) return n.contains("xiphon") ? "fighter" : "gunship";
        if (n.contains("drop pod")) return "droppod";
        if (n.contains("arvus") || n.contains("lighter")) return "arvus";
        if (n.contains("triaros")) return "triaros";
        if (n.contains("karacnos") || n.contains("krios")) return "mech-tank";
        if (n.contains("leman russ") || n.contains("malcador") || n.contains("valdor")) return n.contains("malcador") || n.contains("valdor") ? "malcador" : "russ";
        if (n.contains("dracosan")) return "dracosan";
        if (n.contains("aurox") || n.contains("termite")) return n.contains("termite") ? "termite" : "aurox";
        return null;
    }

    private static String android_join(java.util.Set<String> s) {
        StringBuilder b = new StringBuilder();
        for (String x : s) { if (b.length() > 0) b.append(','); b.append(x); }
        return b.toString();
    }

    private void profiles(Engine ex, Sel s, List<Map<String, Object>> out, int depth) {
        if (depth > 6) return;
        out.addAll(ex.profilesOf(ex.entryOf(s), ex.linkOf(s)));
        for (Sel c : s.children) profiles(ex, c, out, depth + 1);
    }

    // ================================================================ оценка юнита
    static double num(String v, double def) {
        if (v == null) return def;
        String t = v.replaceAll("[^0-9.+-]", "");
        if (t.isEmpty() || t.equals("-") || t.equals("+")) return def;
        try { return Double.parseDouble(t.replace("+", "")); } catch (Exception e) { return def; }
    }

    /** Возможности выбора (юнита со всем снаряжением) по профилям из данных. */
    Cap capOf(Sel s) {
        Cap c = new Cap();
        capRec(s, 1, c, 0, new double[]{4, 1});
        return c;
    }

    private void capRec(Sel s, int mult, Cap c, int depth, double[] modelSA) {
        if (depth > 7) return;
        int m = mult * Math.max(1, s.number);
        for (Map<String, Object> p : e.profilesOf(e.entryOf(s), e.linkOf(s))) {
            String type = Data.str(p, "typeName");
            type = type == null ? "" : type.toLowerCase();
            Map<String, String> ch = new HashMap<String, String>();
            for (Map<String, Object> x : Data.list(p, "characteristics")) {
                String n = Data.str(x, "name"), v = Data.str(x, "$text");
                if (n != null && v != null) ch.put(n.toUpperCase(), v);
            }
            if (DEBUG_CAP) System.err.println("    prof " + Data.str(p, "name") + " [" + type + "] m=" + m + " depth=" + depth + " sel=" + e.effSel(s).name);
            String pn = String.valueOf(Data.str(p, "name")).toLowerCase();
            double wm = pn.contains("grenade") || pn.contains("bomb") || pn.contains("charge") ? m * 0.25 : m; // одноразовое
            if (type.contains("weapon")) weapon(ch, wm, c, type.contains("melee"), modelSA);
            else if (ch.containsKey("W") && ch.containsKey("T")) {
                double w = num(ch.get("W"), 1), t = num(ch.get("T"), 4), sv = num(ch.get("SAV"), 4), inv = num(ch.get("INV"), 7);
                double save = Math.min(sv, inv);
                double sf = save <= 2 ? 1.6 : save <= 3 ? 1.25 : save <= 4 ? 1.0 : 0.8;
                c.dur += w * (t / 4.0) * sf * m;
                double mv = num(ch.get("M"), 7);
                String tp = String.valueOf(ch.get("TYPE")).toLowerCase();
                if (mv >= 10 || tp.contains("cavalry") || tp.contains("jump") || tp.contains("antigrav") || tp.contains("bike")) c.speed += m;
                modelSA[0] = num(ch.get("S"), 4);
                modelSA[1] = num(ch.get("A"), 1);
            } else if (ch.containsKey("HP") || ch.containsKey("FRONT ARMOUR")) {
                double hp = num(ch.get("HP"), 3), fa = num(ch.get("FRONT ARMOUR"), 12);
                c.dur += hp * (fa / 12.0) * 2.2 * m;
                double mv = num(ch.get("M"), 10);
                String tp = String.valueOf(ch.get("TYPE")).toLowerCase();
                if (mv >= 12 || tp.contains("flyer") || tp.contains("skimmer") || tp.contains("fast")) c.speed += m * 2;
            }
        }
        // в BattleScribe число дочернего выбора — уже общее на всю «пачку» моделей, поэтому множитель родителя не переносим
        for (Sel ch : s.children) capRec(ch, mult, c, depth + 1, modelSA);
    }

    private static void weapon(Map<String, String> ch, double m, Cap c, boolean melee, double[] modelSA) {
        String traits = "";
        for (Map.Entry<String, String> en : ch.entrySet()) if (en.getValue().length() > 5) traits += " " + en.getValue().toLowerCase();
        double ap = num(ch.get("AP"), 6), dmg = num(ch.get("D"), 1);
        double str, shots;
        if (melee) {
            String sm = ch.get("SM");
            str = sm == null ? modelSA[0] : sm.trim().startsWith("+") ? modelSA[0] + num(sm, 0) : sm.trim().toLowerCase().startsWith("x") ? modelSA[0] * 2 : num(sm, modelSA[0]);
            shots = Math.max(1, modelSA[1] + num(ch.get("AM"), 0));
            c.melee += shots * (ap <= 3 ? 1.5 : 1) * Math.min(3, dmg) * m * 0.5;
        } else {
            str = num(ch.get("RS"), 4);
            shots = Math.max(1, num(ch.get("FP"), 1));
        }
        boolean av = traits.contains("armourbane") || traits.contains("lance") || traits.contains("melta") || traits.contains("sunder") || traits.contains("las");
        double at = (str >= 9 ? 1.4 : str >= 8 ? 1.0 : str >= 6 ? 0.35 : 0) * Math.min(4, dmg) * shots + (av ? 1 : 0);
        boolean blast = traits.contains("blast") || traits.contains("template") || traits.contains("barrage") || traits.contains("shred");
        double ai = shots * (ap >= 5 ? 1 : 0.7) * (melee ? 0.5 : 1) + (blast ? 2.5 : 0);
        double ae = (ap <= 2 ? 1.6 : ap <= 3 ? 0.8 : 0) * shots * Math.min(3, dmg) + (traits.contains("breaching") || traits.contains("rending") ? 0.6 : 0);
        double k = m * (melee ? 0.6 : 1);
        c.at += at * k;
        c.ai += ai * k;
        c.ae += ae * k;
    }

    // ================================================================ противник
    /** Что нужно против этой армии: веса возможностей. */
    public static double[] needs(String enemyName, Set<String> traits, List<String> why) {
        double[] w = {1, 1, 1, 0.8, 0.5, 0.4};
        String n = enemyName == null ? "" : enemyName.toLowerCase();
        if (n.contains("titanicus") || n.contains("questoris") || n.contains("knight")) { w[0] = 3; w[1] = 0.3; w[2] = 0.4; why.add("Противник — богомашины и рыцари: главное — тяжёлое противотанковое оружие (мелта, лазпушки, D3+)."); }
        else if (n.contains("mechanicum")) { w[0] = 2; w[2] = 1.3; w[1] = 0.7; why.add("Механикум выставляет автоматоны и технику: нужны пробивающее и противотанковое оружие."); }
        else if (n.contains("solar auxilia")) { w[0] = 1.8; w[1] = 1.4; w[4] = 0.7; why.add("Солар Ауксилия — много бронетехники, плотная пехота и линии огня: держим баланс противотанка, огня по пехоте и своего ответного удара."); }
        else if (n.contains("militia")) { w[1] = 2.2; w[0] = 0.8; w[2] = 0.4; why.add("Ополчение берёт числом: нужны шаблоны, взрывы и много выстрелов."); }
        else if (n.contains("custodes")) { w[2] = 2.4; w[1] = 0.4; w[3] = 1.2; why.add("Кустодии — малочисленная элита в лучших доспехах: нужны AP2 и урон D2+."); }
        else if (n.contains("daemon")) { w[2] = 1.6; w[4] = 0.9; why.add("Демоны сильны в рукопашной и стойки к обычному оружию: нужны AP и свой ближний бой."); }
        else if (n.contains("world eaters") || n.contains("space wolves") || n.contains("blood angels") || n.contains("white scars")) { w[3] = 1.3; w[1] = 1.3; w[4] = 1.0; why.add("Легион силён в натиске и ближнем бою: нужны живучесть, плотный огонь и контрудар."); }
        else if (n.contains("iron warriors") || n.contains("imperial fists") || n.contains("iron hands") || n.contains("death guard")) { w[0] = 1.6; w[2] = 1.4; w[4] += 0.3; why.add("Легион любит технику, прочную пехоту и стрельбу: нужно больше противотанка, AP2-3 и своего рукопашного ответа, чтобы сблизиться."); }
        else if (n.contains("legion") || n.contains(" - ") || n.contains("blackshields") || n.contains("shattered")) { w[2] = 1.4; w[0] = 1.2; why.add("Легионы Астартес — пехота в силовой броне и техника: в цене AP3 и универсальное оружие."); }
        if (traits.contains("armour")) { w[0] += 1.2; why.add("Вы отметили: много техники — добавлен противотанк."); }
        if (traits.contains("horde")) { w[1] += 1.2; why.add("Вы отметили: орда — добавлен огонь по пехоте."); }
        if (traits.contains("elite")) { w[2] += 1.2; why.add("Вы отметили: элита и терминаторы — больше AP2."); }
        if (traits.contains("melee")) { w[3] += 0.6; w[1] += 0.4; w[4] += 0.4; why.add("Вы отметили: рукопашники — больше живучести и огня на подходе."); }
        if (traits.contains("shoot")) { w[3] += 0.6; w[4] += 0.9; w[5] += 0.8; why.add("Вы отметили: стрелковая армия — кроме живучести нужен ответный удар: быстрые и элитные рукопашники (джамперы, Риверы, терминаторы), которые доберутся до её линий."); }
        if (traits.contains("fast")) { w[1] += 0.4; w[5] += 0.4; why.add("Вы отметили: быстрая армия — больше выстрелов и своя мобильность."); }
        return w;
    }

    /** Потребности против основной фракции и её союзников, с поправкой на профиль армии по данным. */
    double[] enemyNeeds(List<Roster> all, Params p, List<String> why) {
        Data.Cat ec = p.enemyCat == null ? null : d.cats.get(p.enemyCat);
        double[] w = needs(ec == null ? "" : ec.name, p.enemyTraits, why);
        if (ec != null) { foeMain = Foe.of(d, ec.id, all); enemyName = short_(ec.name); adjust(w, foeMain, short_(ec.name), why, 1); }
        List<String> allies = p.enemyAllies == null ? new ArrayList<String>() : p.enemyAllies;
        if (ec != null && !allies.isEmpty()) {
            double[] sum = new double[6];
            int n = 0;
            StringBuilder names = new StringBuilder();
            for (String id : allies) {
                Data.Cat ac = d.cats.get(id);
                if (ac == null || ac.id.equals(ec.id)) continue;
                List<String> tmp = new ArrayList<String>();
                double[] aw = needs(ac.name, new HashSet<String>(), tmp);
                adjust(aw, Foe.of(d, ac.id, all), short_(ac.name), tmp, 0);
                for (int i = 0; i < 6; i++) sum[i] += aw[i];
                n++;
                if (names.length() > 0) names.append(", ");
                names.append(short_(ac.name));
                if (!tmp.isEmpty()) why.add("Союзник " + short_(ac.name) + ": " + lower1(tmp.get(0)));
            }
            if (n > 0) {
                // союзники — до половины армии, обычно около трети
                for (int i = 0; i < 6; i++) w[i] = w[i] * 0.65 + sum[i] / n * 0.35;
                why.add("Состав учитывает союзников соперника (" + names + ") — примерно треть его армии.");
            }
        }
        return w;
    }

    String allyTail(Params p) {
        StringBuilder b = new StringBuilder();
        if (p.enemyAllies != null) for (String id : p.enemyAllies) { Data.Cat c = d.cats.get(id); if (c != null) b.append(" + ").append(short_(c.name)); }
        return b.toString();
    }

    static String lower1(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }

    static String short_(String n) { return n == null ? "" : n.replaceAll("^[IVXL]+ - ", ""); }

    /** Поправка по реальному составу армии: доля техники, элиты, лёгкой пехоты, рукопашников. */
    static void adjust(double[] w, Foe f, String name, List<String> why, int say) {
        if (f.units == 0) return;
        w[0] += 2.2 * (f.vehicle - 0.3) + 1.5 * (f.heavy - 0.12);
        w[2] += 2.0 * (f.elite - 0.25);
        w[1] += 1.8 * (f.horde - 0.3);
        w[3] += 0.8 * (f.melee - 0.3);
        w[4] += 0.5 * (f.melee - 0.3);
        w[5] += 0.6 * (f.fast - 0.2);
        for (int i = 0; i < 6; i++) w[i] = Math.max(0.2, w[i]);
        if (say > 0 || f.fromRosters) why.add((f.fromRosters ? "По вашим ростерам " + name + " (" + f.units + " юн.): " : "По каталогу " + name + ": ") + f.describe() + ".");
    }

    /** Поправки под миссию: кто держит маркеры, что ценнее в этой постановке. */
    static void missionNeeds(double[] w, String m, List<String> why) {
        if (m == null) return;
        if (m.equals("heart")) why.add("Миссия «Сердце битвы»: центральный маркер на 3 ПО — нужны пешие отряды, способные его держать (техника и кавалерия маркеры не держат).");
        else if (m.equals("crucible")) { w[5] += 0.5; w[1] += 0.2; why.add("Миссия «Горнило войны»: First Strike стоит 4 ПО и четыре маркера — в цене скорость, ранний урон и много держателей точек."); }
        else if (m.equals("hold")) { w[3] += 0.5; why.add("Миссия «Захватить и удержать»: два дорогих маркера, Last Man Standing 4 — важна живучесть держателей."); }
        else if (m.equals("ons_att")) { w[5] += 0.5; w[3] += 0.2; why.add("Onslaught, вы атакуете: очки дают только удержанные вами маркеры (не техникой) — нужны пешие держатели и скорость."); }
        else if (m.equals("ons_def")) { w[3] += 0.4; w[1] += 0.3; w[0] += 0.2; why.add("Onslaught, вы защищаете: очки за маркеры, которые враг не держит, и за выбивание его держателей — огонь по пехоте и живучесть."); }
        else if (m.equals("engine")) { w[0] += 1.6; w[2] -= 0.2; why.add("Engine Kill: Титан на столе — максимум тяжёлого противотанка (D3+, Armourbane)."); }
    }

    String mission;
    static final Map<String, Boolean> LINE = new HashMap<String, Boolean>();

    /** Миссия на маркеры: насколько ценен пеший держатель точек. */
    double holderBonus(Cand c) {
        if (mission == null || mission.equals("engine") || mission.equals("ons_def")) return 1;
        boolean holder = c.type != null && !c.type.toLowerCase().matches(".*(vehicle|cavalry|automata|knight|titan).*");
        if (!holder) return mission.startsWith("ons") ? 0.9 : 1;
        double k = mission.equals("crucible") ? 1.3 : mission.equals("ons_att") ? 1.35 : 1.2;
        if (c.line) k *= 1.2;
        return k;
    }

    static boolean hasRule(Data d, Map<String, Object> n, String rule, int depth, Set<String> seen) {
        if (n == null || depth > 4) return false;
        String id = Data.str(n, "id");
        if (id != null && !seen.add(id)) return false;
        for (Map<String, Object> il : Data.list(n, "infoLinks")) { String nm = Data.str(il, "name"); if (nm != null && (nm.equals(rule) || nm.startsWith(rule + " ("))) return true; }
        for (Map<String, Object> r : Data.list(n, "rules")) { String nm = Data.str(r, "name"); if (nm != null && (nm.equals(rule) || nm.startsWith(rule + " ("))) return true; }
        for (Map<String, Object> ch : Data.list(n, "selectionEntries")) if (hasRule(d, ch, rule, depth + 1, seen)) return true;
        for (Map<String, Object> l : Data.list(n, "entryLinks")) if (hasRule(d, d.entries.get(Data.str(l, "targetId")), rule, depth + 1, seen)) return true;
        return false;
    }

    // ================================================================ сборка
    static final class Cand {
        Sense sense;
        Node node;
        Owned own;
        int status;
        String proxyOf;
        double pts;
        Cap cap;
        int used;
        int maxCopies;
        boolean dead;
        ForceOpt det; // null — Primary
        String name, slotName, type;
        boolean transport, infantry, troops, signature, line;
        String enables; // имя детачмента, который открывает этот консул
        Owned like;     // на какую миниатюру проксируем
    }

    // ================================================================ знания о сборке
    /** Консулы, без которых нет смысла (и нельзя) брать особые детачменты. Из правил данных: «X requires a Y». */
    static final String[][] ENABLERS = {
            {"Mortificator", "Dreadnought Talon"}, {"Vigilator", "Recon Demi-Company"}, {"Siege Breaker", "Storm Battery"},
            {"Legion Champion", "Veteran Cadre"}, {"Master of Signals", "Maelstrom Sentry Battery"}, {"Esoterist", "Daemonic Manifestation"},
    };

    /** Фирменные отряды легионов — то, ради чего обычно играют этой армией. */
    static final String[][] SIGNATURE = {
            {"dark angels", "Deathwing", "Dreadwing", "Ravenwing", "Inner Circle", "Firewing", "Interemptor"},
            {"emperor's children", "Phoenix", "Palatine", "Kakophoni", "Sun Killer", "Kharybdis"},
            {"iron warriors", "Tyrant", "Iron Havoc", "Dominator", "Siege", "Ironfire"},
            {"white scars", "Keshig", "Jetbike", "Scimitar", "Sky-hunter", "Outrider"},
            {"space wolves", "Varagyr", "Grey Slayer", "Deathsworn", "Rout", "Hunter"},
            {"imperial fists", "Phalanx Warder", "Templar", "Huscarl", "Castellan"},
            {"night lords", "Terror", "Night Raptor", "Contekar", "Raptor"},
            {"blood angels", "Crimson Paladin", "Dawnbreaker", "Angel's Tears", "Assault"},
            {"iron hands", "Gorgon", "Medusan", "Iron-father", "Castellax"},
            {"world eaters", "Rampager", "Red Butcher", "Assault", "Devourer"},
            {"ultramarines", "Invictarus", "Fulmentarus", "Locutarus", "Praetorian"},
            {"death guard", "Grave Warden", "Deathshroud", "Destroyer", "Cataphractii"},
            {"thousand sons", "Ammitara", "Khenetai", "Sekhmet", "Castellax"},
            {"sons of horus", "Justaerin", "Reaver", "Sicaran", "Spartan", "Contemptor"},
            {"word bearers", "Gal Vorbak", "Ashen Circle", "Mhara Gal", "Dark Brethren"},
            {"salamanders", "Pyroclast", "Firedrake", "Inferno", "Flamer"},
            {"raven guard", "Dark Fury", "Mor Deythan", "Darkwing", "Raptor"},
            {"alpha legion", "Headhunter", "Lernaean", "Shadow", "Recon"},
            {"mechanicum", "Thanatar", "Castellax", "Myrmidon", "Vorax", "Domitar"},
            {"solar auxilia", "Leman Russ", "Malcador", "Veletaris", "Tercio", "Rapier"},
            {"custodes", "Sentinel", "Aquilon", "Pallas", "Contemptor"},
    };

    /** Подходит ли детачмент-связка под заданный характер армии. */
    private boolean bundleFitsStyle(String det) {
        if (style.isEmpty() || style.contains("balanced")) return true;
        if (det.contains("Dreadnought")) return style.contains("dread") || style.contains("armour");
        if (det.contains("Storm Battery") || det.contains("Maelstrom")) return style.contains("armour") || style.contains("infantry");
        if (det.contains("Recon")) return style.contains("fast") || style.contains("infantry");
        if (det.contains("Veteran")) return style.contains("elite") || style.contains("assault");
        return false;
    }

    private double styleBonus(Cand c) {
        if (style.isEmpty()) return 1;
        String n = c.name.toLowerCase(), t = c.type == null ? "" : c.type.toLowerCase();
        boolean vehicle = t.contains("vehicle") || t.contains("walker") || n.contains("tank") || n.contains("rhino") || n.contains("predator") || n.contains("sicaran") || n.contains("land raider") || n.contains("spartan");
        boolean dread = n.contains("dreadnought") || n.contains("contemptor") || n.contains("leviathan") || n.contains("deredeo");
        boolean elite = n.contains("terminator") || n.contains("cataphractii") || n.contains("tartaros") || n.contains("saturnine") || n.contains("veteran") || n.contains("justaerin") || n.contains("companion") || (c.cap != null && c.cap.ae > 0 && c.cap.dur > 10);
        boolean fast = (c.cap != null && c.cap.speed > 0) || n.contains("jump") || n.contains("bike") || n.contains("jetbike") || n.contains("speeder") || n.contains("javelin");
        boolean assault = n.contains("assault") || n.contains("breacher") || n.contains("reaver") || n.contains("destroyer") || (c.cap != null && c.cap.melee > c.cap.ai * 0.6);
        double k = 1;
        if (style.contains("infantry") && (c.infantry || c.troops)) k *= 1.5;
        if (style.contains("armour") && vehicle && !dread) k *= 1.7;
        if (style.contains("dread") && dread) k *= 2.0;
        if (style.contains("elite") && elite) k *= 1.7;
        if (style.contains("fast") && fast) k *= 1.7;
        if (style.contains("assault") && assault) k *= 1.6;
        if (style.contains("infantry") && vehicle && !c.transport) k *= 0.8;
        return k;
    }

    private boolean signature(String unit) {
        Data.Cat mc = d.cats.get(r.catalogueId);
        String fac = mc == null ? "" : mc.name.toLowerCase();
        for (String[] row : SIGNATURE) {
            if (!fac.contains(row[0])) continue;
            for (int i = 1; i < row.length; i++) if (unit.toLowerCase().contains(row[i].toLowerCase())) return true;
        }
        return false;
    }

    private static String enables(String unit) {
        for (String[] en : ENABLERS) if (unit.startsWith(en[0])) return en[1];
        return null;
    }

    private boolean hasDetachment(String prefix) {
        List<Force> fs = new ArrayList<Force>();
        r.allForces(fs);
        for (Force f : fs) if (e.effForce(f).name.contains(prefix)) return true;
        return false;
    }

    /** Сколько пеших отрядов ещё без транспорта. */
    private int needRides() {
        int inf = 0, tr = 0;
        for (Pick p : res.picks) { if (p.infantry) inf++; if (p.transport) tr++; }
        return inf - tr;
    }

    public Result build(List<Roster> all, Params p) {
        res = new Result();
        rnd = new Random(p.seed);
        excl = p.exclude == null ? new HashSet<String>() : p.exclude;
        maxSame = p.maxSame;
        freeMode = p.mode == 2; // до того, как scratch-ветка перезапишет p.mode
        limit = p.limit;
        proxyPts = 0;
        newPts = 0;
        usedById.clear();
        usedBySlot.clear();
        try {
            String cat = p.myCat != null ? p.myCat : favouriteFaction(all);
            if (cat == null || !d.cats.containsKey(cat)) { res.error = "Выберите фракцию армии."; return res; }
            minis = p.minis;
            curCat = cat;
            ownedPhase = true;
            relaxNew = false;
            collect(all, cat);
            if (physical) {
                int u = 0;
                for (Owned o : owned.values()) u += o.copies;
                res.reasons.add("Учтены «Мои миниатюры»: " + u + " " + (u == 1 ? "юнит" : "юнитов") + " этой фракции. Прокси — только на свободные похожие модели из коллекции.");
            }
            scratch = owned.isEmpty();
            style = p.style == null ? new HashSet<String>() : p.style;
            if (scratch) {
                p.mode = 2;
                StringBuilder sb = new StringBuilder();
                for (String[] st : STYLES) if (style.contains(st[0])) { if (sb.length() > 0) sb.append(", "); sb.append(st[1].toLowerCase()); }
                res.reasons.add("Ваших ростеров этой фракции нет — собираю армию с нуля" + (sb.length() > 0 ? " в характере: " + sb + "." : ", сбалансированно."));
            }
            if (freeMode && !scratch) res.reasons.add("Режим «Свободно»: миниатюры и ростеры дают список кандидатов, но преимущества не дают — выбор строго по силе против врага.");
            Data.Cat ec = p.enemyCat == null ? null : d.cats.get(p.enemyCat);
            res.need = enemyNeeds(all, p, res.reasons);
            mission = p.mission;
            missionNeeds(res.need, mission, res.reasons);

            r = new Roster();
            Data.Cat mc = d.cats.get(cat);
            r.name = "Умный: " + mc.name.replaceAll("^[IVXL]+ - ", "") + (ec != null ? " против " + ec.name.replaceAll("^[IVXL]+ - ", "") + allyTail(p) : "");
            r.catalogueId = cat;
            r.limit = p.limit;
            e = new Engine(d, r);
            ForceOpt foc = null;
            for (ForceOpt o : e.rootForceOptions(cat)) if (o.name().startsWith("Crusade")) { foc = o; break; }
            if (foc == null) { res.error = "Для этой фракции нет Crusade Force Organization Chart."; return res; }
            Force army = e.addForce(null, foc, cat);
            copyArmyChoices(all, cat, army);
            Force prim = null;
            for (ForceOpt o : e.childForceOptions(army)) if (o.name().contains("Primary")) { prim = e.addForce(army, o, null); break; }
            if (prim == null) { res.error = "Не удалось добавить Primary Detachment."; return res; }

            List<Cand> cands = buildCands(army, prim, p.mode, null);
            if (cands.isEmpty()) { res.error = "Нет подходящих юнитов для этой фракции."; return res; }
            computeNorm(cands);
            // 0) «обязательно взять» — выбор игрока, ставим первыми
            pinPhase(army, prim, cands, p, null);
            // 1) обязательные слоты
            for (CatSlot s : e.slots(prim)) {
                if (s.hidden || s.min <= 0) continue;
                for (int k = (int) s.count; k < s.min; k++) {
                    Cand best = bestFor(cands, prim, s.categoryId, p.limit - e.rosterCost(d.pointsId), true);
                    if (best == null) break;
                    place(best, prim, "обязательный слот " + s.name);
                }
            }
            // 2) связки «консул + его детачмент»: Мортификатор → Dreadnought Talon с дредноутами и т.п.
            bundles(army, prim, cands, p);
            if (DEBUG) { System.err.println("after bundles pts=" + e.rosterCost(d.pointsId) + " picks=" + res.picks.size() + " forces=" + listForces().size()); for (Force ff : listForces()) System.err.println("   F " + e.effForce(ff).name + " sels=" + ff.sels.size()); }
            // 3) жадно по ценности на очко с убывающей отдачей
            boolean withAlly = p.myAlly != null && d.cats.containsKey(p.myAlly) && !p.myAlly.equals(cat);
            double mainLimit = withAlly ? p.limit * 0.7 : p.limit;
            for (int iter = 0; iter < 120; iter++) {
                double left = mainLimit - e.rosterCost(d.pointsId);
                if (left < 15) break;
                Cand best = null, bestAux = null;
                double bs = 0, bsAux = 0;
                Force where = null;
                List<Force> forces = new ArrayList<Force>();
                r.allForces(forces);
                for (Cand c : cands) {
                    if (!ok(c) || c.pts > left || c.pts <= 0) continue;
                    // новые и прокси — аккуратно: не больше трети армии
                    if (c.status == PROXY && proxyPts + c.pts > limit * 0.35) continue;
                    if (c.status == NEW && !scratch && !freeMode && !relaxNew && newPts + c.pts > limit * 0.3) continue;
                    Force f = forceWithRoom(forces, c);
                    double sc = value(c) * (0.9 + rnd.nextDouble() * 0.2);
                    if (f == null) {
                        // свой юнит, которому нужен ещё не открытый вспомогательный детачмент (Юстаэрины, Риверы…) —
                        // соревнуется на равных, а не ждёт, пока кончатся очки
                        double k = c.signature ? 1.0 : 0.9;
                        if (c.status == OWNED && c.det != null && sc * k > bsAux) { bsAux = sc * k; bestAux = c; }
                        continue;
                    }
                    if (sc > bs) { bs = sc; best = c; where = f; }
                }
                if (bestAux != null && bsAux > bs && lbPhase(bestAux)) continue; // слот через Logistical Benefit дешевле нового детачмента
                if (bestAux != null && bsAux > bs) {
                    if (DEBUG) System.err.println("  open aux for " + bestAux.name + " score=" + bsAux + " vs " + (best == null ? "-" : best.name) + " " + bs);
                    openAuxFor(army, cands, left, bestAux);
                    continue;
                }
                if (best == null && lbPhase()) continue;
                if (best == null && physical && ownedPhase) {
                    if (addAuxiliary(army, cands, left)) continue;
                    ownedPhase = false;
                    continue;
                }
                if (best == null && p.mode >= 2 && !relaxNew && !scratch) {
                    // «Свободно»: своё кончилось — добираем новыми юнитами до лимита
                    relaxNew = true;
                    res.reasons.add("Своих юнитов не хватило на весь лимит — добрал новыми до " + Engine.fmt(mainLimit) + " очков.");
                    continue;
                }
                if (best == null) {
                    if (DEBUG) { System.err.println("  greedy: no best, left=" + left); int dd=0,uu=0,pp=0,ff=0; for (Cand c : cands) { if (c.dead) dd++; else if (usedOf(c) >= c.maxCopies) uu++; else if (c.pts > left || c.pts <= 0) pp++; else if (forceWithRoom(forces, c) == null) ff++; } System.err.println("   dead="+dd+" used="+uu+" pts="+pp+" noroom="+ff+" total="+cands.size()); }
                    if (!addAuxiliary(army, cands, left)) break;
                    continue;
                }
                place(best, where, null);
            }
            if (withAlly) {
                ownedPhase = false;
                int errBefore = 0;
                for (Issue i : e.validate()) if (i.error && i.kind != Issue.LIMIT) errBefore++;
                List<Cand> mainCands = cands;
                allyPhase(all, p, army, cat);
                int errAfter = 0;
                for (Issue i : e.validate()) if (i.error && i.kind != Issue.LIMIT) errAfter++;
                if (DEBUG) for (Issue i : e.validate()) if (i.error) System.err.println("ALLY ISSUE " + i.text);
                if (errAfter > errBefore) dropAlly(p);
                // остаток очков — снова основной армии
                collect(all, cat);
                for (int iter = 0; iter < 20; iter++) {
                    double left = p.limit - e.rosterCost(d.pointsId);
                    if (left < 15) break;
                    Cand best = null;
                    double bs = 0;
                    Force where = null;
                    List<Force> forces = new ArrayList<Force>();
                    r.allForces(forces);
                    for (Cand c : mainCands) {
                        if (!ok(c) || c.pts > left || c.pts <= 0) continue;
                        if (c.status == PROXY && proxyPts + c.pts > limit * 0.35) continue;
                        if (c.status == NEW && !scratch && !freeMode && !relaxNew && newPts + c.pts > limit * 0.3) continue;
                        Force f = forceWithRoom(forces, c);
                        if (f == null) continue;
                        double sc = value(c) * (0.9 + rnd.nextDouble() * 0.2);
                        if (sc > bs) { bs = sc; best = c; where = f; }
                    }
                    if (best == null) break;
                    place(best, where, null);
                }
            }
            trimOver(p.limit);
            fixOptions();
            try { primePhase(); } catch (Throwable ignored) { }
            int errBeforeFill = 0;
            for (Issue i : e.validate()) if (i.error) errBeforeFill++;
            double beforeFill = e.rosterCost(d.pointsId);
            long tf0 = System.currentTimeMillis();
            try { if (!NOFILL) { fillUnits(cands, p); fillToLimit(p.limit, errBeforeFill); fillUnits(cands, p); fillToLimit(p.limit, errBeforeFill); } } catch (Throwable ignored) { }
            if (DEBUG) System.err.println("fill ms=" + (System.currentTimeMillis() - tf0));
            double filled = e.rosterCost(d.pointsId) - beforeFill;
            if (filled > 0) res.reasons.add("Добил до лимита улучшениями на " + Engine.fmt(filled) + " очков (модели в отряды, снаряжение) — эффективная часть армии не тронута.");
            for (Issue i : e.validate()) if (i.error) res.errors++;
            res.roster = r;
            summarize();
            double tot = e.rosterCost(d.pointsId);
            if (physical && p.mode < 2 && tot < p.limit * 0.85)
                res.reasons.add("Из ваших миниатюр набралось " + Engine.fmt(tot) + " из " + Engine.fmt(p.limit) + " очков — больше в коллекции нет подходящего. Добавьте миниатюры в «Мои миниатюры» или выберите режим «Свободно».");
        } catch (Throwable t) {
            res.error = "Ошибка сборки: " + t;
        }
        return res;
    }

    /** Союзник ломает ростер (особые требования его детачмента) — убираем целиком. */
    private void dropAlly(Params p) {
        Force cont = null;
        for (Force f : r.forces) if (p.myAlly.equals(f.catalogueId)) cont = f;
        if (cont == null) return;
        List<Force> fs = new ArrayList<Force>();
        fs.add(cont);
        for (int i = 0; i < fs.size(); i++) fs.addAll(fs.get(i).children);
        for (int i = res.picks.size() - 1; i >= 0; i--) if (fs.contains(res.picks.get(i).sel.force)) res.picks.remove(i);
        e.removeForce(cont);
        Data.Cat c = d.cats.get(p.myAlly);
        for (int i = res.reasons.size() - 1; i >= 0; i--) if (res.reasons.get(i).startsWith("Союзник ")) res.reasons.remove(i);
        res.reasons.add("Союзник " + short_(c.name) + " не встал без ошибок (у его детачмента особые требования) — соберите союзника вручную в ростере.");
    }

    /** Мой союзник: Allied Detachment другой фракции на оставшиеся очки (не больше половины армии по правилам). */
    private void allyPhase(List<Roster> all, Params p, Force army, String mainCat) {
        String ac = p.myAlly;
        Data.Cat cat = d.cats.get(ac);
        ForceOpt ao = null;
        for (ForceOpt o : e.rootForceOptions(ac)) if (o.name().startsWith("Allied")) { ao = o; break; }
        if (ao == null) { res.reasons.add("Союзник " + short_(cat.name) + ": для этой фракции нет Allied Detachment."); return; }
        // образец — союзник этой фракции из ростеров игрока (там уже верная связка детачментов и Logistical Benefits)
        Force cont = templateAlly(all, ac, p);
        boolean fromTpl = cont != null;
        if (!fromTpl) {
            cont = e.addForce(null, ao, ac);
            for (Sel s : army.sels) if ("Allegiance".equals(e.effSel(s).name)) { cont.sels.add(copy(s, cont, null)); e.changed(); }
            defaultArmyChoices(cont);
        }
        Force det = null;
        if (fromTpl) for (Force f : cont.children) if (e.effForce(f).name.contains("Allied") || e.effForce(f).name.contains("Primary")) { det = f; break; }
        if (det == null)
        for (ForceOpt o : e.childForceOptions(cont)) if (o.name().contains("Allied") || o.name().contains("Primary")) { det = e.addForce(cont, o, null); break; }
        if (det == null) for (ForceOpt o : e.childForceOptions(cont)) { if (o.name().startsWith("Mech -")) continue; det = e.addForce(cont, o, null); break; }
        if (det == null) { e.removeForce(cont); res.reasons.add("Союзник " + short_(cat.name) + ": не удалось добавить детачмент."); return; }
        int picksBefore = fromTpl ? res.picks.size() - tplPicks : res.picks.size();
        collect(all, ac);
        boolean allyScratch = owned.isEmpty();
        boolean keepScratch = scratch;
        scratch = allyScratch;
        curCat = ac;
        List<Cand> cands = candidates(det, allyScratch ? 2 : p.mode, null);
        // юниты, которым нужен вспомогательный детачмент союзника (Thallax, Castellax и т.п.)
        for (ForceOpt o : e.childForceOptions(cont)) {
            String nm = o.name();
            if (Data.str(o.entry, "id").equals(det.entryId) || nm.startsWith("Mech -")) continue;
            if (!nm.startsWith("Auxiliary") && !nm.startsWith("Apex")) continue;
            boolean used = usedDets.contains(Data.str(o.entry, "id"));
            boolean general = allyScratch || p.mode >= 2 || physical;
            if (!used && !general) continue;
            Force tmp = e.addForce(cont, o, null);
            Set<String> have = new HashSet<String>();
            for (Cand c : cands) have.add(c.node.opt.id());
            for (Cand c : candidates(tmp, allyScratch ? 2 : p.mode, o)) if (!have.contains(c.node.opt.id())) cands.add(c);
            e.removeForce(tmp);
        }
        pinPhase(cont, det, cands, p, ac);
        for (CatSlot s : e.slots(det)) {
            if (s.hidden || s.min <= 0) continue;
            for (int k = (int) s.count; k < s.min; k++) {
                Cand best = bestFor(cands, det, s.categoryId, p.limit - e.rosterCost(d.pointsId), true);
                if (best == null) break;
                place(best, det, hqSlot(s) ? "командир союзника — обязателен для детачмента (HQ)" : "обязательный слот союзника");
            }
        }
        // доп. детачменты союзника: как в ростере игрока, и те, что требуют правила (у Mechanicum — Logistical Benefits)
        List<Cand> extraCands = new ArrayList<Cand>();
        Roster tpl = null;
        Force tplCont = null;
        for (Roster x : all) for (Force f : x.forces) {
            if (!ac.equals(f.catalogueId) || f.children.isEmpty()) continue;
            if (tpl == null || x.updated > tpl.updated) { tpl = x; tplCont = f; }
        }
        if (DEBUG) { System.err.println("ALLY tpl=" + (tplCont != null) + " det=" + e.effForce(det).name); for (ForceOpt o : e.childForceOptions(cont)) System.err.println("ALLY opt " + o.name()); for (Issue i : e.validate()) if (i.kind == Issue.NEED_DET) System.err.println("ALLY need " + i.subject + " whereCont=" + (i.where == cont) + " where=" + i.where); }
        if (tplCont != null && !fromTpl) {
            boolean skippedDet = false;
            for (Force tf : tplCont.children) {
                if (!skippedDet && tf.entryId.equals(det.entryId)) { skippedDet = true; continue; }
                ForceOpt fo = null;
                for (ForceOpt o : e.childForceOptions(cont)) if (tf.entryId.equals(Data.str(o.entry, "id"))) { fo = o; break; }
                if (fo == null) continue;
                Force nf = e.addForce(cont, fo, null);
                if (nf != null) fillAllyForce(nf, p, extraCands);
            }
        }
        List<Cand> allCands = new ArrayList<Cand>(cands);
        // союзнику хватает 1–2 доп. детачментов: каждый тянет за собой обязательного командира (HQ) —
        // без потолка союзник разрастается в стаю техно-жрецов ради слотов
        final int[] auxCount = {0};
        if (DEBUG) for (Cand c : cands) System.err.println("ALLYC " + c.name + " st=" + c.status + " ok=" + ok(c) + " pts=" + c.pts + " det=" + (c.det == null ? "-" : c.det.name()) + " own=" + (c.own != null) + " left=" + (p.limit - e.rosterCost(d.pointsId)));
        for (int pass = 0; pass < 3; pass++) {
        allCands.addAll(extraCands);
        if (DEBUG) { for (Cand c : extraCands) System.err.println("ALLYX " + c.name + " ok=" + ok(c) + " place=" + d.categoryName(c.node.place)); for (Force f : cont.children) System.err.println("ALLYF " + e.effForce(f).name + " sels=" + f.sels.size()); }
        extraCands.clear();
        for (int iter = 0; iter < 45; iter++) {
            double left = p.limit - e.rosterCost(d.pointsId);
            if (left < 15) break;
            Cand best = null;
            double bs = 0;
            Force where = null;
            List<Force> forces = new ArrayList<Force>();
            r.allForces(forces);
            for (Cand c : allCands) {
                if (!ok(c) || c.pts > left || c.pts <= 0) continue;
                if (c.status == PROXY && proxyPts + c.pts > limit * 0.35) continue;
                if (c.status == NEW && !scratch && !relaxNew && newPts + c.pts > limit * 0.3) continue;
                Force f = forceWithRoom(forces, c);
                if (f == null) continue;
                double sc = value(c) * (0.9 + rnd.nextDouble() * 0.2);
                if (sc > bs) { bs = sc; best = c; where = f; }
            }
            if (best == null) {
                // не влезает в имеющиеся детачменты — откроем союзнику вспомогательный (не больше двух)
                if (auxCount[0] < 2 && addAuxiliary(cont, allCands, left)) { auxCount[0]++; continue; }
                break;
            }
            place(best, where, "союзный детачмент");
        }
        // правила требуют ещё детачмент (появляется после того, как в основном детачменте союзника есть юниты)
        int extraBefore = extraCands.size();
        {
            boolean added = false;
            for (Issue i : e.validate()) {
                if (!i.error || i.kind != Issue.NEED_DET || i.where != cont) continue;
                for (ForceOpt o : e.childForceOptions(cont)) {
                    if (!o.name().equals(i.subject)) continue;
                    Force nf = e.addForce(cont, o, null);
                    if (nf != null) { fillAllyForce(nf, p, extraCands); added = true; }
                    break;
                }
                if (added) break;
            }
        }
        // пустые доп. детачменты убираем (они только мешают)
        for (Force f : new ArrayList<Force>(cont.children)) if (f != det && f.sels.isEmpty()) {
            e.removeForce(f);
            boolean worse = false;
            for (Issue i : e.validate()) if (i.error && i.kind == Issue.NEED_DET && i.where == cont) worse = true;
            if (worse) { // детачмент обязателен — вернём, пусть будет предупреждение
                for (ForceOpt o : e.childForceOptions(cont)) if (f.entryId.equals(Data.str(o.entry, "id"))) { e.addForce(cont, o, null); break; }
            }
        }
        if (extraCands.size() == extraBefore) break;
        }
        scratch = keepScratch;
        curCat = mainCat;
        int got = res.picks.size() - picksBefore;
        if (got == 0 || det.sels.isEmpty()) { e.removeForce(cont); res.reasons.add("Союзник " + short_(cat.name) + ": не нашлось подходящих юнитов в пределах очков."); return; }
        res.reasons.add("Союзник " + short_(cat.name) + ": " + got + " " + (got == 1 ? "юнит" : "юнитов") + " на " + Engine.fmt(e.forceCost(cont, d.pointsId)) + " очков" + (allyScratch ? " (ваших ростеров этой фракции нет — подобрано с нуля)" : "") + ".");
        // если командиров много — объясняем почему, чтобы «стаи техно-жрецов» не выглядели багом
        int hq = 0, dets = 0;
        for (Force f : cont.children) if (!f.sels.isEmpty()) dets++;
        for (int pi = res.picks.size() - got; pi < res.picks.size(); pi++) {
            String sl = res.picks.get(pi).slot;
            if (sl != null && (sl.toLowerCase().contains("hq") || sl.toLowerCase().contains("command"))) hq++;
        }
        if (hq > 2) res.reasons.add("Командиров у союзника " + hq + ": по правилам каждый детачмент союзника обязан иметь своего HQ (" + dets + " " + (dets == 1 ? "детачмент" : "детачмента") + "), это не моя жадность. Поэтому я и ограничил союзника " + Math.min(2, auxCount[0]) + " доп. детачментами: иначе очки утекали бы в штабистов, а не в боевые юниты.");
        else if (auxCount[0] > 0) res.reasons.add("Союзнику открыто доп. детачментов: " + auxCount[0] + " (потолок — 2). Каждый новый детачмент требует своего командира и съедает очки основной армии.");
    }

    /** Заполнить обязательные слоты дополнительного детачмента союзника. */
    private void fillAllyForce(Force nf, Params p, List<Cand> extra) {
        List<Cand> cs = candidates(nf, scratch ? 2 : p.mode, null);
        extra.addAll(cs);
        for (CatSlot sl : e.slots(nf)) {
            if (sl.hidden || sl.min <= 0) continue;
            for (int k = (int) sl.count; k < sl.min; k++) {
                Cand best = bestFor(cs, nf, sl.categoryId, p.limit - e.rosterCost(d.pointsId), true);
                if (best == null) break;
                place(best, nf, hqSlot(sl) ? "командир вспомогательного детачмента союзника — без него детачмент нельзя взять" : "обязательный слот союзника");
            }
        }
    }

    private static boolean hqSlot(CatSlot s) {
        String n = s.name == null ? "" : s.name.toLowerCase();
        return n.contains("hq") || n.contains("command");
    }

    private int tplPicks;

    /** Скопировать союзника из ростера игрока: детачменты и юниты, которые у игрока есть (с учётом «Моих миниатюр»). */
    private Force templateAlly(List<Roster> all, String ac, Params p) {
        tplPicks = 0;
        Roster tpl = null;
        Force src = null;
        for (Roster x : all) for (Force f : x.forces) {
            if (!ac.equals(f.catalogueId) || f.children.isEmpty()) continue;
            boolean any = false;
            for (Force ch : f.children) if (!ch.sels.isEmpty()) any = true;
            if (!any) continue;
            if (tpl == null || x.updated > tpl.updated) { tpl = x; src = f; }
        }
        if (src == null) return null;
        int errBefore = 0;
        for (Issue i : e.validate()) if (i.error) errBefore++;
        String keepCat = curCat;
        collect(all, ac);
        curCat = ac;
        Force cont = copyForce(src, null);
        e.changed();
        // убрать то, чего у игрока нет (или уже занято), и не вылезти за лимит очков
        List<Sel> units = new ArrayList<Sel>();
        for (Force f : cont.children) units.addAll(f.sels);
        Map<String, Integer> took = new HashMap<String, Integer>();
        for (Sel u : units) {
            Owned o = null;
            for (Owned x : owned.values()) if (x.id.startsWith(u.entryId + "|")) { o = x; break; }
            int t = took.containsKey(u.entryId) ? took.get(u.entryId) : 0;
            boolean have = o != null && (!physical || t < o.copies) && (p.mode > 0 || o.copies > t);
            if (!have) { e.removeSel(u); continue; }
            took.put(u.entryId, t + 1);
        }
        e.changed();
        while (e.rosterCost(d.pointsId) > p.limit) {
            Sel worst = null;
            for (Force f : cont.children) for (Sel u : f.sels) if (worst == null || e.points(u) > e.points(worst)) worst = u;
            if (worst == null) break;
            took.put(worst.entryId, took.get(worst.entryId) - 1);
            e.removeSel(worst);
        }
        for (Force f : new ArrayList<Force>(cont.children)) if (f.sels.isEmpty() && !e.effForce(f).name.contains("Allied")) e.removeForce(f);
        int errAfter = 0;
        for (Issue i : e.validate()) if (i.error) errAfter++;
        curCat = keepCat;
        boolean any = false;
        for (Force f : cont.children) if (!f.sels.isEmpty()) any = true;
        if (errAfter > errBefore || !any) { e.removeForce(cont); return null; }
        for (Force f : cont.children) for (Sel u : f.sels) {
            Owned o = null;
            for (Owned x : owned.values()) if (x.id.startsWith(u.entryId + "|")) { o = x; break; }
            usedById.put(u.entryId, (usedById.containsKey(u.entryId) ? usedById.get(u.entryId) : 0) + 1);
            Cap cap = capOf(u);
            for (int i = 0; i < 6; i++) res.have[i] += cap.get(i);
            Pick pk = new Pick();
            pk.sel = u;
            pk.name = e.effSel(u).name;
            pk.entryId = u.entryId;
            pk.status = OWNED;
            pk.pts = e.points(u);
            pk.cap = cap;
            pk.physical = o != null && o.physical;
            pk.rosters = o == null ? 1 : o.rosters;
            pk.slot = d.categoryName(u.cat);
            pk.why = "союзник как в вашем ростере";
            res.picks.add(pk);
            tplPicks++;
        }
        return cont;
    }

    private Force copyForce(Force src, Force parent) {
        Force f = new Force();
        f.entryId = src.entryId;
        f.linkId = src.linkId;
        f.catalogueId = src.catalogueId;
        f.roster = r;
        f.parent = parent;
        if (parent == null) r.forces.add(f); else parent.children.add(f);
        for (Sel s : src.sels) f.sels.add(copy(s, f, null));
        for (Force ch : src.children) copyForce(ch, f);
        return f;
    }

    /** Кандидаты основной армии: Primary + вспомогательные детачменты (из ростеров игрока и общие). */
    private List<Cand> buildCands(Force army, Force prim, int mode, Set<String> only) {
        List<Cand> cands = candidates(prim, mode, null);
        for (ForceOpt o : e.childForceOptions(army)) {
            String nm = o.name();
            if (nm.contains("Primary") || nm.startsWith("Mech -") || nm.startsWith("Allied")) continue;
            boolean used = usedDets.contains(Data.str(o.entry, "id"));
            boolean general = (mode >= 2 || physical) && (nm.endsWith("Tactical Support") || nm.endsWith("Armoured Fist") || nm.endsWith("Heavy Support") || nm.endsWith("Armoured Support") || nm.endsWith("Shock Assault") || nm.endsWith("First Strike"));
            if (!nm.startsWith("Auxiliary") && !nm.startsWith("Apex") && !nm.startsWith("Warlord") && !used) continue;
            Force tmp = null;
            if (!used && !general && only == null) {
                // реальная коллекция: детачмент нужен, если в нём есть место хоть для одной своей миниатюры (Юстаэрины → Supremacy Cadre)
                if (!physical) continue;
                tmp = e.addForce(army, o, null);
                boolean mineHere = false;
                for (Node n : e.rootOptions(tmp)) {
                    for (Owned ow : owned.values()) if (ow.physical && ow.id.startsWith(n.opt.id() + "|")) { mineHere = true; break; }
                    if (mineHere) break;
                }
                if (!mineHere) { e.removeForce(tmp); continue; }
            }
            if (tmp == null) tmp = e.addForce(army, o, null);
            Set<String> have = new HashSet<String>();
            for (Cand c : cands) have.add(c.node.opt.id());
            for (Cand c : candidates(tmp, mode, o)) if (!have.contains(c.node.opt.id())) cands.add(c);
            e.removeForce(tmp);
        }
        if (only != null) for (int i = cands.size() - 1; i >= 0; i--) if (!only.contains(cands.get(i).node.opt.id())) cands.remove(i);
        return cands;
    }

    /** «Обязательно взять»: юниты, отмеченные игроком, ставятся первыми — под них открываются нужные детачменты. */
    private void pinPhase(Force army, Force prim, List<Cand> cands, Params p, String allyCat) {
        if (p.include == null || p.include.isEmpty()) return;
        String cat = allyCat != null ? allyCat : r.catalogueId;
        Set<String> mine = new HashSet<String>();
        Data.Cat dc = d.cats.get(cat);
        if (dc != null) for (Opt o : dc.roots) if (p.include.contains(Data.str(o.entry, "id"))) mine.add(Data.str(o.entry, "id"));
        if (mine.isEmpty()) return;
        for (String id : mine) {
            Cand c = null;
            for (Cand x : cands) if (x.node.opt.id().equals(id) && (c == null || (x.status == OWNED && c.status != OWNED))) c = x;
            if (c == null) {
                // в этом режиме юнит не кандидат (например, его нет в коллекции) — игрок попросил явно, берём как новый
                Set<String> one = new HashSet<String>();
                one.add(id);
                List<Cand> extra = allyCat != null ? candidates(prim, 2, null) : buildCands(army, prim, 2, one);
                for (Cand x : extra) if (x.node.opt.id().equals(id)) { c = x; break; }
                if (c != null) cands.add(c);
            }
            String nm = c != null ? c.name : String.valueOf(Data.str(d.entries.get(id), "name"));
            if (c == null) { res.reasons.add("Обязательно взять «" + nm + "» не получилось: этот юнит недоступен в выбранной армии."); continue; }
            double left = p.limit - e.rosterCost(d.pointsId);
            if (c.pts > left) { res.reasons.add("Обязательно взять «" + nm + "» не получилось: не хватает очков."); continue; }
            int before = c.used;
            List<Force> forces = new ArrayList<Force>();
            r.allForces(forces);
            Force f = forceWithRoom(forces, c);
            if (f != null) place(c, f, "вы отметили «обязательно взять»");
            else if (c.det != null) openAuxFor(army, cands, left, c);
            if (c.used > before) {
                Pick last = res.picks.get(res.picks.size() - 1);
                last.pinned = true;
                last.why = "вы отметили «обязательно взять»";
                c.dead = false;
            } else res.reasons.add("Обязательно взять «" + nm + "» не получилось: нет свободного слота или детачмента под него.");
        }
    }

    /**
     * Прайм-слоты: каждому юниту, кому правила позволяют (есть свободный Prime), даём «Prime Unit» с выгодой.
     * Logistical Benefit ставится отдельно (когда нужен лишний слот); иначе — ветераны, мастер-сержант,
     * «Paragon of Battle» для героев или легионная выгода. Прайм бесплатен — пустовать ему нельзя.
     */
    private void primePhase() {
        int base = errorCount();
        List<Pick> order = new ArrayList<Pick>(res.picks);
        // сначала самые ценные отряды: прайм-выгода на дорогом отряде работает сильнее
        java.util.Collections.sort(order, new java.util.Comparator<Pick>() { public int compare(Pick x, Pick y) { return Double.compare(y.pts, x.pts); } });
        List<String> names = new ArrayList<String>();
        for (Pick pk : order) {
            Sel s = pk.sel;
            if (s == null || !containsSel(s)) continue;
            List<Node> os = e.options(e.entryOf(s), e.linkOf(s), s);
            Node pn = findOpt(os, "Prime Unit");
            if (pn == null || pn.eff.hidden || e.maxOf(pn, s) == 0) continue;
            Sel have = e.findChildPublic(s, pn.opt);
            if (have != null && !have.children.isEmpty()) continue; // уже прайм с выгодой
            if (have == null) { e.setCount(s, pn.opt, 1); e.changed(); }
            Sel pu = e.findChildPublic(s, pn.opt);
            if (pu == null) continue;
            if (errorCount() > base) { e.setCount(s, pn.opt, 0); e.changed(); continue; } // прайм-слотов больше нет
            String got = tryLogistical(s, pu, pk, base);
            if (got == null) got = pickBenefit(s, pu, pk);
            if (got == null || errorCount() > base) { e.setCount(s, pn.opt, 0); e.changed(); continue; }
            names.add(pk.name + " — " + got);
            pk.why = pk.why == null ? "прайм: " + got : pk.why + "; прайм: " + got;
        }
        if (!names.isEmpty()) res.reasons.add("Прайм-слоты заняты: " + list3(names) + ". Прайм бесплатен — пустым его не оставляю.");
    }

    /**
     * Logistical Benefit: прайм даёт лишний слот выбранного типа. Берём его, если в этот слот есть что поставить
     * (на оставшиеся очки), — это дешевле, чем брать ещё командира ради нового детачмента.
     */
    /** Слоты кончились: попробовать открыть лишний слот через Logistical Benefit свободного прайма. */
    private boolean lbPhase() { return lbPhase(null); }

    private boolean lbPhase(Cand only) {
        int base = errorCount();
        for (Pick pk : new ArrayList<Pick>(res.picks)) {
            Sel s = pk.sel;
            if (s == null || !containsSel(s)) continue;
            Node pn = findOpt(e.options(e.entryOf(s), e.linkOf(s), s), "Prime Unit");
            if (pn == null || pn.eff.hidden || e.maxOf(pn, s) == 0 || e.findChildPublic(s, pn.opt) != null) continue;
            e.setCount(s, pn.opt, 1);
            e.changed();
            Sel pu = e.findChildPublic(s, pn.opt);
            if (pu == null || errorCount() > base) { e.setCount(s, pn.opt, 0); e.changed(); continue; }
            String got = tryLogistical(s, pu, pk, base, only);
            if (got != null) { pk.why = pk.why == null ? "прайм: " + got : pk.why + "; прайм: " + got; return true; }
            e.setCount(s, pn.opt, 0);
            e.changed();
        }
        return false;
    }

    private String tryLogistical(Sel unit, Sel pu, Pick pk, int base) { return tryLogistical(unit, pu, pk, base, null); }

    private String tryLogistical(Sel unit, Sel pu, Pick pk, int base, Cand only) {
        List<Node> leaves = new ArrayList<Node>();
        flatten(e.options(e.entryOf(pu), e.linkOf(pu), pu), leaves);
        Node lbn = null;
        for (Node n : leaves) if (!n.eff.hidden && e.maxOf(n, pu) != 0 && n.eff.name.equals("Logistical Benefit")) lbn = n;
        if (lbn == null) return null;
        double left = limit - e.rosterCost(d.pointsId);
        if (left < 30) return null;
        e.setCount(pu, lbn.opt, 1);
        e.changed();
        Sel lb = e.findChildPublic(pu, lbn.opt);
        if (lb == null) { e.setCount(pu, lbn.opt, 0); e.changed(); return null; }
        List<Node> slots = new ArrayList<Node>();
        flatten(e.options(e.entryOf(lb), e.linkOf(lb), lb), slots);
        Force f = unit.force;
        String curC = curCat;
        curCat = f.catalogueId;
        Cand best = null;
        Node bestSlot = null, bestNode = null;
        double bv = 0;
        for (Node sn : slots) {
            if (sn.eff.hidden || !sn.eff.name.startsWith("LB - ")) continue;
            String slot = sn.eff.name.substring(5).trim();
            e.setCount(lb, sn.opt, 1);
            e.changed();
            for (Node rn : e.rootOptions(f)) {
                if (rn.place == null || !slot.equals(d.categoryName(rn.place))) continue;
                for (Cand c : pool) {
                    if (only != null && c != only) continue;
                    if (!c.node.opt.id().equals(rn.opt.id()) || !ok(c) || c.pts <= 0 || c.pts > left) continue;
                    double v = value(c);
                    if (best == null || v > bv) { best = c; bv = v; bestSlot = sn; bestNode = rn; }
                }
            }
            e.setCount(lb, sn.opt, 0);
            e.changed();
        }
        if (DEBUG) { StringBuilder sb0 = new StringBuilder(); for (Node sn : slots) sb0.append(sn.eff.name).append(sn.eff.hidden ? "(h)" : "").append(", "); System.err.println("  LB try on " + pk.name + " only=" + (only == null ? "-" : only.name + "/" + only.slotName) + " slots=" + sb0 + " best=" + (best == null ? null : best.name)); }
        String got = null;
        if (best != null) {
            e.setCount(lb, bestSlot.opt, 1);
            e.changed();
            Cand cp = copyCand(best, bestNode);
            int u = cp.used;
            place(cp, f, "встал в лишний слот от Logistical Benefit");
            if (cp.used > u) best = cp;
            if (cp.used > u && errorCount() <= base) {
                got = "Logistical Benefit (" + bestSlot.eff.name.substring(5).trim() + " → " + best.name + ")";
                res.reasons.add("Logistical Benefit на «" + pk.name + "»: лишний слот «" + bestSlot.eff.name.substring(5).trim() + "» под " + best.name + " — дешевле, чем брать ещё командира ради нового детачмента.");
            } else {
                if (best.used > u) { Pick last = res.picks.get(res.picks.size() - 1); e.removeSel(last.sel); res.picks.remove(last); best.used--; usedById.put(best.node.opt.id(), usedOf(best) - 1); }
                e.setCount(lb, bestSlot.opt, 0);
                e.changed();
            }
        }
        curCat = curC;
        if (got == null) { e.setCount(pu, lbn.opt, 0); e.changed(); }
        return got;
    }

    private static Cand copyCand(Cand c, Node node) {
        Cand x = new Cand();
        x.sense = c.sense; x.node = node; x.own = c.own; x.status = c.status; x.proxyOf = c.proxyOf; x.pts = c.pts; x.cap = c.cap;
        x.used = 0; x.maxCopies = c.maxCopies; x.det = null; x.name = c.name; x.slotName = c.slotName; x.type = c.type;
        x.transport = c.transport; x.infantry = c.infantry; x.troops = c.troops; x.signature = c.signature; x.line = c.line; x.enables = c.enables; x.like = c.like;
        return x;
    }

    private String pickBenefit(Sel unit, Sel pu, Pick pk) {
        List<Node> top = e.options(e.entryOf(pu), e.linkOf(pu), pu);
        List<Node> leaves = new ArrayList<Node>();
        List<Node> legion = new ArrayList<Node>();
        collectBenefits(top, leaves, legion, false);
        boolean character = (pk.slot != null && (pk.slot.contains("Command") || pk.slot.contains("Warlord"))) || models(e, unit) <= 1;
        String[] pref = character
                ? new String[]{"*legion", "Paragon of Battle", "Cult Operative", "Combat Veterans"}
                : new String[]{"*legion", "Combat Veterans", "Master Sergeant", "Cult Operative"};
        for (String want : pref) {
            List<Node> pool = want.equals("*legion") ? legion : leaves;
            for (Node n : pool) {
                if (n.eff.hidden || e.maxOf(n, pu) == 0) continue;
                String nm = n.eff.name;
                if (nm.startsWith("Logistical Benefit") || nm.equals("Petty Warlord")) continue;
                if (!want.equals("*legion") && !nm.equals(want)) continue;
                int before = errorCount();
                e.setCount(pu, n.opt, 1);
                e.changed();
                if (errorCount() > before) { e.setCount(pu, n.opt, 0); e.changed(); continue; }
                return nm;
            }
        }
        return null;
    }

    private static void collectBenefits(List<Node> ns, List<Node> all, List<Node> legion, boolean inLegion) {
        for (Node n : ns) {
            if (n.isGroup()) {
                if (n.eff.hidden) continue;
                collectBenefits(n.children, all, legion, inLegion || n.eff.name.startsWith("Legion"));
            } else {
                all.add(n);
                if (inLegion) legion.add(n);
            }
        }
    }

    /** Добить очки до лимита юнитами: самый дорогой, что ещё влезает (лимиты прокси/новых здесь не важны — главное заполнить очки). */
    private void fillUnits(List<Cand> cands, Params p) {
        for (int iter = 0; iter < 10; iter++) {
            double left = p.limit - e.rosterCost(d.pointsId);
            if (left < 15) return;
            List<Force> forces = new ArrayList<Force>();
            r.allForces(forces);
            Cand best = null;
            Force where = null;
            for (Cand c : cands) {
                if (!ok(c) || c.pts <= 0 || c.pts > left) continue;
                if (c.status == NEW && p.mode < 2 && !scratch) continue;
                if (c.status == PROXY && p.mode < 1) continue;
                Force f = forceWithRoom(forces, c);
                if (f == null) continue;
                if (best == null || c.pts > best.pts || (c.pts == best.pts && value(c) > value(best))) { best = c; where = f; }
            }
            if (best == null) { if (lbPhase()) continue; return; }
            int u = best.used;
            place(best, where, "добивает очки до лимита");
            if (best.used == u) best.dead = true;
            if (e.rosterCost(d.pointsId) > p.limit) { Pick last = res.picks.get(res.picks.size() - 1); e.removeSel(last.sel); res.picks.remove(last); return; }
        }
    }

    /** Добить очки до лимита: улучшения (модели в отряды, снаряжение, вокс и т.п.) поверх готового ростера. */
    private void fillToLimit(double limit, int errBase) {
        for (int round = 0; round < 4; round++) {
            double left = limit - e.rosterCost(d.pointsId);
            if (left < 1) return;
            List<Object[]> ups = new ArrayList<Object[]>(); // {Sel root, Sel parent, Opt, Integer cur, Double delta}
            for (Pick pk : res.picks) {
                if (pk.sel == null || !containsSel(pk.sel)) continue;
                collectUps(pk.sel, pk.sel, ups, left, 0);
            }
            if (ups.isEmpty()) return;
            java.util.Collections.sort(ups, new java.util.Comparator<Object[]>() {
                public int compare(Object[] x, Object[] y) { return Double.compare((Double) y[4], (Double) x[4]); }
            });
            boolean any = false;
            List<Object[]> applied = new ArrayList<Object[]>();
            Set<String> usedKey = new HashSet<String>();
            int tries = 0;
            for (Object[] u : ups) {
                if (++tries > 120) break;
                left = limit - e.rosterCost(d.pointsId);
                double dl = (Double) u[4];
                if (dl > left + 0.01) continue;
                Sel root = (Sel) u[0], par = (Sel) u[1];
                Opt o = (Opt) u[2];
                String key = System.identityHashCode(par) + "|" + o.id();
                if (!usedKey.add(key)) continue;
                Sel ex = e.findChildPublic(par, o);
                int cur = ex == null ? 0 : ex.number;
                int uErr = unitErr(root);
                e.setCount(par, o, cur + 1);
                e.changed();
                boolean bad = unitErr(root) > uErr || e.rosterCost(d.pointsId) > limit + 0.01;
                if (bad) { e.setCount(par, o, cur); e.changed(); continue; }
                applied.add(new Object[]{par, o, cur});
                any = true;
            }
            // общая проверка ростера — один раз за круг; если что-то сломалось, откатываем с конца
            for (int k = applied.size() - 1; k >= 0; k--) {
                int errs = 0;
                for (Issue i : e.validate()) if (i.error) errs++;
                if (errs <= errBase) break;
                Object[] u = applied.remove(k);
                e.setCount((Sel) u[0], (Opt) u[1], (Integer) u[2]);
                e.changed();
            }
            for (Pick pk : res.picks) if (pk.sel != null) pk.pts = e.points(pk.sel);
            if (!any || applied.isEmpty()) return;
        }
    }

    private boolean containsSel(Sel s) {
        List<Force> fs = new ArrayList<Force>();
        r.allForces(fs);
        for (Force f : fs) if (f.sels.contains(s)) return true;
        return false;
    }

    private int unitErr(Sel root) {
        int n = 0;
        for (Issue i : e.validateUnit(root)) if (i.error) n++;
        return n;
    }

    private void collectUps(Sel root, Sel x, List<Object[]> out, double left, int depth) {
        if (depth > 3) return;
        List<Node> nodes = new ArrayList<Node>();
        flatten(e.options(e.entryOf(x), e.linkOf(x), x), nodes);
        for (Node n : nodes) {
            if (n.eff.hidden) continue;
            double dl = n.eff.cost(d.pointsId); // цена одного варианта — без пробной постановки, быстро
            if (dl <= 0.01 || dl > left + 0.01) continue;
            Sel ex = e.findChildPublic(x, n.opt);
            int cur = ex == null ? 0 : ex.number;
            double max = e.maxOf(n, x);
            if (max >= 0 && cur >= max) continue;
            out.add(new Object[]{root, x, n.opt, cur, dl});
        }
        for (Sel ch : new ArrayList<Sel>(x.children)) collectUps(root, ch, out, left, depth + 1);
    }

    private static void flatten(List<Node> in, List<Node> out) {
        for (Node n : in) {
            if (n.isGroup()) { if (!n.eff.hidden && !n.eff.name.contains("Arcana")) flatten(n.children, out); }
            else out.add(n);
        }
    }

    /** Allegiance, Rite of War и прочее из самого свежего ростера этой фракции. */
    private void copyArmyChoices(List<Roster> all, String cat, Force army) {
        Roster src = null;
        for (Roster x : all) if (cat.equals(x.catalogueId) && (src == null || x.updated > src.updated)) src = x;
        if (src != null) for (Force f : src.forces) {
            if (!cat.equals(f.catalogueId) || !f.entryId.equals(army.entryId)) continue;
            for (Sel s : f.sels) army.sels.add(copy(s, army, null));
            e.changed();
            defaultArmyChoices(army); // в старом ростере может не быть Rite of War — без него видны чужие опции (Pyrae)
            return;
        }
        defaultArmyChoices(army);
    }

    /** Без ростера-образца: Allegiance, Rite of War (легион) и прочие армейские выборы по умолчанию.
     *  Без Rite of War данные считают легион неизвестным и показывают чужие опции (Prosperine Arcana и т.п.). */
    private void defaultArmyChoices(Force army) {
        Set<String> have = new HashSet<String>();
        for (Sel s : army.sels) have.add(e.effSel(s).name);
        for (Node n : e.rootOptions(army)) {
            if (have.contains(n.eff.name) || n.eff.hidden) continue;
            Sel s = e.addUnit(army, n);
            if (s == null) continue;
            if (s.children.isEmpty() && !n.eff.name.equals("Allegiance")) { e.removeSel(s); continue; }
            have.add(n.eff.name);
        }
        e.changed();
    }

    static Sel copy(Sel s, Force f, Sel parent) {
        Sel c = new Sel();
        c.entryId = s.entryId;
        c.linkId = s.linkId;
        c.cat = s.cat;
        c.groups = new ArrayList<String>(s.groups);
        c.number = s.number;
        c.force = f;
        c.parent = parent;
        for (Sel ch : s.children) c.children.add(copy(ch, f, c));
        return c;
    }

    private List<Cand> candidates(Force prim, int mode, ForceOpt det) {
        List<Cand> out = new ArrayList<Cand>();
        Set<String> seen = new HashSet<String>();
        Map<String, Owned> byType = new HashMap<String, Owned>();
        if (physical) for (Node n : e.rootOptions(prim)) for (Owned o : owned.values()) {
            if (o.type != null || !o.id.startsWith(n.opt.id() + "|")) continue;
            Owned pr = probe(n);
            if (pr != null) { o.type = pr.type; o.save = pr.save; o.toughness = pr.toughness; }
        }
        for (Owned o : owned.values()) if (o.type != null) byType.put(o.type + "|" + o.save + "|" + o.toughness, o);
        for (Node n : e.rootOptions(prim)) {
            String id = n.opt.id();
            if (excl.contains(id)) continue;
            if (!seen.add(id + "|" + n.place)) continue;
            Cand c = new Cand();
            c.node = n;
            c.det = det;
            c.name = n.eff.name;
            c.slotName = d.categoryName(n.place);
            c.transport = c.slotName.contains("Transport");
            c.troops = c.slotName.contains("Troops");
            c.type = modelType(n);
            Boolean ln = LINE.get(n.opt.id());
            if (ln == null) { ln = hasRule(d, n.opt.entry, "Line", 0, new HashSet<String>()) || (n.opt.link != null && hasRule(d, n.opt.link, "Line", 0, new HashSet<String>())); LINE.put(n.opt.id(), ln); }
            c.line = ln;
            String tl = c.type == null ? "" : c.type.toLowerCase();
            c.infantry = tl.startsWith("infantry") && !tl.contains("jump") && !tl.contains("antigrav") && !tl.contains("heavy") && !c.name.contains("Terminator") && !c.name.contains("Jump");
            c.signature = signature(c.name);
            c.enables = enables(c.name);
            c.own = owned.get(id + "|" + n.opt.linkId());
            if (c.own == null && physical) {
                // в коллекции юнит мог быть записан без конкретной ссылки (linkId)
                for (Owned o : owned.values()) if (o.physical && o.id.equals(id + "|null")) { c.own = o; break; }
            }
            if (c.own != null && c.own.type == null) { Owned pr = probe(n); if (pr != null) { c.own.type = pr.type; c.own.save = pr.save; c.own.toughness = pr.toughness; } }
            if (c.own != null) { c.status = OWNED; c.maxCopies = Math.max(1, c.own.copies); }
            else {
                Owned like = similar(n, byType);
                if (like != null && mode >= 1) { c.status = PROXY; c.proxyOf = like.name; c.like = like; c.maxCopies = physical ? 1 : 2; }
                else if (mode >= 2) { c.status = NEW; c.maxCopies = scratch ? 3 : 2; }
                else continue;
            }
            // пробная постановка: очки и возможности
            Sel s = make(c, prim);
            if (s == null) continue;
            if (c.status == PROXY && c.like != null) {
                // прокси возможен, только если моделей у «похожей» миниатюры хватает: одним Абаддоном не изобразить 5 терминаторов
                int need = models(e, s);
                if (c.like.models < need) {
                    Owned alt = null;
                    for (Owned o : owned.values()) {
                        if (o.type == null || !o.type.equals(c.like.type) || o.save == null || !o.save.equals(c.like.save)) continue;
                        if (o.models >= need && (alt == null || o.models < alt.models)) alt = o;
                    }
                    if (alt == null) { e.removeSel(s); continue; }
                    c.like = alt;
                    c.proxyOf = alt.name;
                }
            }
            c.pts = e.points(s);
            c.cap = capOf(s);
            if (c.type != null && c.type.contains("Unique")) c.maxCopies = 1; // именной персонаж — один на ростер
            if ((c.slotName != null && (c.slotName.contains("Command") || c.slotName.contains("Warlord") || c.slotName.contains("Lord"))) || (c.type != null && c.type.contains("Unique")))
                try { c.sense = sense(s); } catch (Throwable ignored) { }
            e.removeSel(s);
            out.add(c);
        }
        pool.addAll(out);
        return out;
    }

    /** Все рассмотренные кандидаты — для объяснений «вместо чего». */
    final List<Cand> pool = new ArrayList<Cand>();
    Foe foeMain;
    String enemyName;

    private String modelType(Node n) {
        List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>(e.profilesOf(n.opt.entry, n.opt.link));
        for (Node ch : e.options(n.opt.entry, n.opt.link, null)) if (!ch.isGroup() && "model".equals(ch.opt.type())) ps.addAll(e.profilesOf(ch.opt.entry, ch.opt.link));
        for (Map<String, Object> p : ps) for (Map<String, Object> ch : Data.list(p, "characteristics")) {
            String nm = Data.str(ch, "name"), v = Data.str(ch, "$text");
            if (nm != null && v != null && nm.equalsIgnoreCase("Type")) return v.trim();
        }
        return null;
    }

    private Owned probe(Node n) {
        List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>(e.profilesOf(n.opt.entry, n.opt.link));
        for (Node ch : e.options(n.opt.entry, n.opt.link, null)) if (!ch.isGroup() && "model".equals(ch.opt.type())) ps.addAll(e.profilesOf(ch.opt.entry, ch.opt.link));
        for (Map<String, Object> p : ps) {
            String type = null, sv = null, t = null;
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String nm = Data.str(ch, "name"), v = Data.str(ch, "$text");
                if (nm == null || v == null) continue;
                if (nm.equalsIgnoreCase("Type")) type = sizeKey(v, Data.str(p, "name"));
                if (nm.equalsIgnoreCase("SAV") || nm.equalsIgnoreCase("Front Armour")) sv = v.trim();
                if (nm.equalsIgnoreCase("T") || nm.equalsIgnoreCase("HP")) t = v.trim();
            }
            if (type != null) { Owned o = new Owned(); o.type = type; o.save = sv; o.toughness = t; return o; }
        }
        return null;
    }

    /** Похожая миниатюра из коллекции: тот же тип модели, спасбросок и стойкость. */
    private Owned similar(Node n, Map<String, Owned> byType) {
        Owned probe = new Owned();
        Sel s = new Sel();
        s.entryId = n.opt.id();
        s.linkId = n.opt.linkId();
        // профиль смотрим по данным без постановки в ростер
        List<Map<String, Object>> ps = new ArrayList<Map<String, Object>>(e.profilesOf(n.opt.entry, n.opt.link));
        for (Node ch : e.options(n.opt.entry, n.opt.link, null)) if (!ch.isGroup() && "model".equals(ch.opt.type())) ps.addAll(e.profilesOf(ch.opt.entry, ch.opt.link));
        for (Map<String, Object> p : ps) {
            String type = null, sv = null, t = null;
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String nm = Data.str(ch, "name"), v = Data.str(ch, "$text");
                if (nm == null || v == null) continue;
                if (nm.equalsIgnoreCase("Type")) type = sizeKey(v, Data.str(p, "name"));
                if (nm.equalsIgnoreCase("SAV") || nm.equalsIgnoreCase("Front Armour")) sv = v.trim();
                if (nm.equalsIgnoreCase("T") || nm.equalsIgnoreCase("HP")) t = v.trim();
            }
            if (type != null) { probe.type = type; probe.save = sv; probe.toughness = t; break; }
        }
        if (probe.type == null) return null;
        Owned exact = byType.get(probe.type + "|" + probe.save + "|" + probe.toughness);
        boolean isVeh = probe.type.toLowerCase().matches(".*(vehicle|knight|titan|flyer).*");
        if (isVeh) {
            // технику проксим только на том же шасси: Rhino ↔ Predator можно, Rhino ↔ Termite — нет
            String fam = chassis(n.eff.name);
            if (fam == null) return null;
            if (exact != null && fam.equals(chassis(exact.name))) return exact;
            for (Owned o : owned.values()) if (probe.type.equals(o.type) && fam.equals(chassis(o.name))) return o;
            return null;
        }
        if (exact != null) return exact;
        // мягче: тот же тип модели и спасбросок
        // техника — только точное совпадение (броня и корпус), иначе Predator «станет» Thunderhawk
        boolean veh = probe.type.toLowerCase().matches(".*(vehicle|knight|titan|flyer).*");
        if (!veh) for (Owned o : owned.values()) if (probe.type.equals(o.type) && probe.save != null && probe.save.equals(o.save)) return o;
        return null;
    }

    private Sel make(Cand c, Force f) {
        if (c.own != null && c.own.template != null) {
            Sel s = copy(c.own.template, f, null);
            s.cat = c.node.place;
            f.sels.add(s);
            e.changed();
            return s;
        }
        Node n = null;
        for (Node x : e.rootOptions(f)) if (x.opt.id().equals(c.node.opt.id()) && (c.node.place == null || c.node.place.equals(x.place))) { n = x; break; }
        if (n == null) for (Node x : e.rootOptions(f)) if (x.opt.id().equals(c.node.opt.id())) { n = x; break; }
        if (n == null) return null;
        return e.addUnit(f, n);
    }

    private double value(Cand c) {
        // возможности нормируются «на очко» по всем кандидатам: дредноут с лазпушками
        // оценивается в противотанке так же честно, как десять болтеров — в огне по пехоте
        double v = 0;
        for (int i = 0; i < 6; i++) {
            double n = norm[i] <= 0 ? 1 : norm[i];
            double gain = c.cap.get(i) / n;           // «очков-эквивалента» в этом направлении
            double haveEq = res.have[i] / n;
            double sat = 1 + haveEq / Math.max(150, limit / 5);
            v += res.need[i] * gain / sat;
        }
        double own = scratch || freeMode ? 1 : c.status == OWNED ? 2.2 + 0.2 * Math.min(3, c.own.rosters) : c.status == PROXY ? 1.0 : 0.6;
        Integer inSlot = usedBySlot.get(c.node.place == null ? "" : c.node.place);
        double variety = Math.pow(0.72, inSlot == null ? 0 : inSlot); // разнообразие: штраф за однотипные выборы
        double logic = 1;
        // транспорт ценен, только если есть кого везти
        if (c.transport) logic *= needRides() > 0 ? 1.6 : 0.12;
        // пеших отрядов стоит брать, когда для них есть или будет транспорт / они держат точки
        if (c.troops) logic *= 1.15;
        // консул-ключ без своего детачмента почти бесполезен
        if (c.enables != null && !hasDetachment(c.enables)) logic *= 0.3;
        // фирменные отряды легиона
        // особые правила персонажа: чем лучше они закрывают то, что нужно против врага, тем он ценнее
        if (c.sense != null) {
            double sb = 0;
            for (int i = 0; i < 6; i++) sb += res.need[i] * c.sense.cap[i];
            logic *= 1 + Math.min(0.7, sb * 0.25);
        }
        // фирменные отряды легиона: свои — заметно ценнее (особые правила легиона + докупать ничего не надо),
        // чем чаще игрок ими играет, тем выше; но бонус умножает реальную пользу, а не заменяет её
        if (c.signature) {
            if (c.status == OWNED && c.own != null) logic *= Math.min(1.7, 1.35 + 0.1 * Math.min(2, c.own.rosters - 1) + (c.own.physical ? 0.15 : 0));
            else logic *= 1.12;
        }
        // характер армии, заданный игроком
        logic *= styleBonus(c);
        // миссия на маркеры: пешие держатели точек (особенно с Line) ценнее
        logic *= holderBonus(c);
        // командиров сверх необходимого — осторожно (слот называется «HQ» или «Command»)
        if (c.slotName != null && (c.slotName.startsWith("Command") || c.slotName.equalsIgnoreCase("HQ")) && inSlot != null && inSlot >= 1) logic *= 0.5;
        // одинаковые юниты подряд — всё меньше отдачи
        if (usedOf(c) >= 2) logic *= 0.6;
        // «ответный кулак» против стрелковой армии: ценим рукопашников, которые реально доберутся
        // (скорость или элитная броня), и принижаем медленную «мясную» рукопашь — она не дойдёт
        if (res.need[4] >= 1.0 && c.cap != null && c.cap.melee > 0) {
            if (c.cap.speed > 0 || c.cap.ae > 0) logic *= 1.3;
            else logic *= 0.9;
        }
        return v * own * variety * logic / Math.max(40, c.pts) * (usedOf(c) > 0 ? 0.7 : 1);
    }

    private Cand bestFor(List<Cand> cands, Force f, String slot, double left, boolean cheap) {
        Cand best = null;
        double bs = -1;
        for (Cand c : cands) {
            if (!slot.equals(c.node.place) || !ok(c) || c.pts > left) continue;
            double sc = value(c) * (c.status == OWNED ? 2 : 1);
            if (sc > bs) { bs = sc; best = c; }
        }
        return best;
    }

    private Force forceWithRoom(List<Force> forces, Cand c) {
        for (Force f : forces) {
            if (isContainer(f) || !(curCat != null ? curCat : r.catalogueId).equals(f.catalogueId)) continue;
            if (c.det != null && !f.entryId.equals(Data.str(c.det.entry, "id"))) continue;
            if (c.det == null && !e.effForce(f).name.contains("Primary")) {
                boolean ok = false;
                for (Node x : e.rootOptions(f)) if (x.opt.id().equals(c.node.opt.id())) { ok = true; break; }
                if (!ok) continue;
            }
            for (CatSlot s : e.slots(f)) {
                if (s.hidden || !s.categoryId.equals(c.node.place)) continue;
                if (s.max < 0 || s.count < s.max) return f;
            }
        }
        return null;
    }

    private int costErrors() {
        int n = 0;
        for (Issue i : e.validate()) if (i.error && (i.kind == Issue.FORCE_COST || i.kind == Issue.SLOT_OVER || i.kind == Issue.FORCE_MAX || i.kind == Issue.FORCE_RULE)) n++;
        return n;
    }

    private void computeNorm(List<Cand> cands) {
        for (int i = 0; i < 6; i++) {
            double sum = 0;
            int n = 0;
            for (Cand c : cands) if (c.pts > 0 && c.cap.get(i) > 0) { sum += c.cap.get(i) / c.pts; n++; }
            norm[i] = n == 0 ? 1 : sum / n;
        }
    }

    /** Ценность кандидата без поправок на коллекцию и разнообразие. */
    private double raw(Cand c) {
        double v = 0;
        for (int i = 0; i < 6; i++) v += res.need[i] * c.cap.get(i) / (norm[i] <= 0 ? 1 : norm[i]);
        return v / Math.max(40, c.pts);
    }

    /** Ценность юнита «как есть» на очко — для подбора снаряжения. */
    private double unitScore(Sel s) {
        Cap c = capOf(s);
        double v = 0;
        for (int i = 0; i < 6; i++) v += res.need[i] * c.get(i) / (norm[i] <= 0 ? 1 : norm[i]);
        return v / Math.max(40, e.points(s));
    }

    /**
     * Подбор снаряжения: в группах замены («Exchange … for», оружейные опции) пробуем каждый вариант
     * и оставляем тот, что лучше всего работает против выбранного противника за свои очки.
     */
    private void optimize(Sel root) {
        List<Sel> targets = new ArrayList<Sel>();
        targets.add(root);
        for (Sel ch : root.children) if ("model".equals(Data.str(e.entryOf(ch), "type"))) targets.add(ch);
        for (Sel t : targets) {
            List<Node> groups = new ArrayList<Node>();
            for (Node n : e.options(e.entryOf(t), e.linkOf(t), t)) if (n.isGroup() && !n.eff.hidden) groups.add(n); // скрытое (чужой культ, Cult Arcana) не трогаем
            for (Node g : groups) {
                double gmax = e.maxOf(g, t);
                int m = Engine.mult(t);
                if (gmax <= 0 || gmax > m) continue; // только «одна вещь на модель»
                List<Node> leaves = new ArrayList<Node>();
                for (Node l : g.children) if (!l.isGroup() && !l.eff.hidden && e.maxOf(l, t) != 0) leaves.add(l);
                if (leaves.size() < 2 || leaves.size() > 12) continue;
                String gid = g.opt.id();
                double best = unitScore(root);
                Node bestLeaf = null;
                List<Sel> orig = new ArrayList<Sel>();
                for (Sel ch : t.children) if (ch.groups.contains(gid)) orig.add(ch);
                double ptsLeft = limit - e.rosterCost(d.pointsId);
                for (Node l : leaves) {
                    for (Sel ch : new ArrayList<Sel>(t.children)) if (ch.groups.contains(gid)) t.children.remove(ch);
                    e.changed();
                    e.setCount(t, l.opt, (int) Math.max(1, Math.min(gmax, m)));
                    e.resolveForbidden(root);
                    double sc = unitScore(root);
                    boolean ok = unitOk(root) && limit - e.rosterCost(d.pointsId) >= Math.min(0, ptsLeft);
                    if (ok && sc > best * 1.03) { best = sc; bestLeaf = l; }
                }
                // ставим лучший или возвращаем исходное
                for (Sel ch : new ArrayList<Sel>(t.children)) if (ch.groups.contains(gid)) t.children.remove(ch);
                if (bestLeaf != null) e.setCount(t, bestLeaf.opt, (int) Math.max(1, Math.min(gmax, m)));
                else for (Sel o : orig) t.children.add(o);
                e.changed();
            }
        }
    }

    private boolean unitOk(Sel s) {
        for (Issue i : e.validateUnit(s)) if (i.error) return false;
        return true;
    }

    private void place(Cand c, Force f, String note) {
        if (physical && c.status == PROXY && (ownedPhase || spare(c.like) <= 0)) return; // свободной похожей модели больше нет
        int before = costErrors();
        Sel s = make(c, f);
        if (s == null) { c.dead = true; return; }
        if (!unitOk(s)) {
            // шаблон из старого ростера не подошёл — берём снаряжение по умолчанию
            e.removeSel(s);
            Owned keep = c.own;
            Sel tpl = keep == null ? null : keep.template;
            if (keep != null) keep.template = null;
            s = make(c, f);
            if (keep != null) keep.template = tpl;
            if (s == null || !unitOk(s)) { if (s != null) e.removeSel(s); c.dead = true; return; }
        }
        // своё снаряжение игрока не трогаем; новым и прокси подбираем оружие под противника
        Set<String> gear0 = new HashSet<String>();
        gearNames(s, gear0);
        if (c.status != OWNED || c.own == null || c.own.template == null) {
            try { optimize(s); } catch (Throwable ignored) { }
            if (!unitOk(s)) { e.removeSel(s); c.dead = true; return; }
        }
        if (costErrors() > before) { e.removeSel(s); c.dead = true; return; } // нарушил бы лимиты детачментов — не берём
        c.used++;
        usedById.put(c.node.opt.id(), usedOf(c) + 1);
        String sk = c.node.place == null ? "" : c.node.place;
        usedBySlot.put(sk, (usedBySlot.containsKey(sk) ? usedBySlot.get(sk) : 0) + 1);
        if (c.status == PROXY) { proxyPts += e.points(s); if (c.like != null) c.like.proxyUse++; }
        if (c.status == NEW) newPts += e.points(s);
        Cap cap = capOf(s);
        for (int i = 0; i < 6; i++) res.have[i] += cap.get(i);
        Pick pk = new Pick();
        pk.sel = s;
        pk.name = e.effSel(s).name;
        pk.entryId = s.entryId;
        pk.status = c.status;
        pk.pts = e.points(s);
        pk.cap = cap;
        pk.proxyOf = c.proxyOf;
        pk.like = c.like;
        pk.physical = c.own != null && c.own.physical;
        pk.rosters = c.own == null ? 0 : c.own.rosters;
        pk.slot = d.categoryName(s.cat);
        pk.why = note != null ? note : c.signature ? "фирменный отряд легиона" : c.transport ? "везёт пехоту" : null;
        pk.infantry = c.infantry;
        pk.type = c.type;
        pk.transport = c.transport;
        pk.cand = c;
        Set<String> gear1 = new HashSet<String>();
        gearNames(s, gear1);
        for (String g : gear1) if (!gear0.contains(g)) pk.gear.add(g);
        res.picks.add(pk);
    }

    /** Если слоты кончились — открыть Auxiliary Detachment под лучший оставшийся юнит. */
    private boolean addAuxiliary(Force army, List<Cand> cands, double left) {
        Cand want = null;
        double bs = 0;
        for (Cand c : cands) {
            if (!ok(c) || c.pts > left || c.pts <= 0) continue;
            double v = value(c) * (c.status == OWNED ? 1.5 : 1);
            if (want == null || v > bs) { bs = v; want = c; }
        }
        if (DEBUG && want == null) for (Cand c : cands) if (c.own != null && c.own.physical && usedOf(c) < c.own.copies) System.err.println("    unplaced " + c.name + " ok=" + ok(c) + " dead=" + c.dead + " pts=" + c.pts + " left=" + left + " det=" + (c.det == null ? "-" : c.det.name()));
        if (DEBUG) System.err.println("  addAux want=" + (want == null ? null : want.name + " det=" + (want.det == null ? "prim" : want.det.name()) + " st=" + want.status));
        if (want == null) return false;
        return openAuxFor(army, cands, left, want);
    }

    /** Открыть вспомогательный детачмент ради конкретного юнита и сразу поставить его туда. */
    private boolean openAuxFor(Force army, List<Cand> cands, double left, Cand want) {
        List<ForceOpt> all = e.childForceOptions(army);
        List<ForceOpt> opts = new ArrayList<ForceOpt>();
        // сначала «свой» детачмент юнита, затем любые другие, где ему есть слот (Юстаэрины: Supremacy Cadre или Apex)
        if (want.det != null) for (ForceOpt o : all) if (Data.str(o.entry, "id").equals(Data.str(want.det.entry, "id"))) opts.add(o);
        for (ForceOpt o : all) if (!opts.contains(o)) opts.add(o);
        for (ForceOpt o : opts) {
            if (!o.name().startsWith("Auxiliary") && !o.name().startsWith("Apex") && !o.name().startsWith("Warlord")) continue;
            // Warlord Detachment — только ради самого Warlord / Lord of War, иначе он остаётся без обязательного лидера
            if (o.name().startsWith("Warlord")) { String pl = want.slotName == null ? "" : want.slotName; if (!pl.contains("Warlord") && !pl.contains("Lord")) continue; }
            boolean ok = false;
            for (CatSlot s : e.slotsFor(o.entry, army)) if (!s.hidden && s.categoryId.equals(want.node.place) && s.max != 0) ok = true;
            if (!ok) continue;
            int before = costErrors();
            Force aux = e.addForce(army, o, null);
            boolean bad = costErrors() > before;
            if (bad && o.name().startsWith("Auxiliary") && switchHighCommand(army)) bad = costErrors() > before;
            if (bad && o.name().startsWith("Auxiliary")) {
                // каждый Command в Primary открывает ещё один Auxiliary — возьмём командира
                e.removeForce(aux);
                if (unlockCommand(army, cands, left - want.pts)) {
                    before = costErrors();
                    aux = e.addForce(army, o, null);
                    bad = costErrors() > before;
                } else continue;
            }
            if (DEBUG) System.err.println("   aux " + o.name() + " bad=" + bad);
            if (bad) { e.removeForce(aux); continue; }
            boolean hosts = false;
            for (Node n : e.rootOptions(aux)) if (n.opt.id().equals(want.node.opt.id())) { hosts = true; break; }
            if (!hosts) { e.removeForce(aux); continue; }
            // сразу ставим юнит, ради которого открыли детачмент; не встал — пустой детачмент не оставляем
            int usedBefore = want.used;
            place(want, aux, null);
            if (want.used == usedBefore) { e.removeForce(aux); want.dead = false; continue; }
            return true;
        }
        want.dead = true; // некуда поставить — больше не пытаемся
        return true;
    }

    /** Взять командира в слот Command основного детачмента, чтобы открыть Auxiliary. */
    private boolean unlockCommand(Force army, List<Cand> cands, double left) {
        Force prim = null;
        for (Force f : army.children) { String fn = e.effForce(f).name; if (fn.contains("Primary") || fn.equals("Allied Detachment")) prim = f; }
        if (prim == null) return false;
        Cand best = null;
        double bs = 0;
        for (Cand c : cands) {
            if (!ok(c) || c.pts <= 0 || c.pts > left || c.det != null) continue;
            if (c.slotName == null || !c.slotName.equals("Command")) continue;
            double v = value(c) / Math.max(1, c.pts / 80.0);
            if (v > bs) { bs = v; best = c; }
        }
        if (best == null) return false;
        List<Force> one = new ArrayList<Force>();
        one.add(prim);
        if (forceWithRoom(one, best) == null) return false;
        int u = best.used;
        place(best, prim, "открывает ещё один вспомогательный детачмент");
        return best.used > u;
    }

    /** High Command без Apex: переключить его на «+1 Auxiliary from High Command». */
    private boolean switchHighCommand(Force army) {
        for (Force f : army.children) {
            if (!e.effForce(f).name.contains("Primary")) continue;
            for (Sel s : f.sels) {
                List<Node> os = e.options(e.entryOf(s), e.linkOf(s), s);
                Node aux = findOpt(os, "One Auxiliary Detachment");
                if (aux == null) aux = findOpt(os, "+1 Auxiliary from High Command");
                if (aux == null || e.findChildPublic(s, aux.opt) != null) continue;
                Node apex = findOpt(os, "One Apex Detachment");
                if (apex == null) apex = findOpt(os, "+1 Apex from High Command");
                if (apex != null && e.findChildPublic(s, apex.opt) != null) {
                    // Apex уже используется? тогда не трогаем
                    boolean apexUsed = false;
                    for (Force x : army.children) if (e.effForce(x).name.startsWith("Apex")) apexUsed = true;
                    if (apexUsed) continue;
                    e.setCount(s, apex.opt, 0);
                }
                e.setCount(s, aux.opt, 1);
                e.changed();
                return true;
            }
        }
        return false;
    }

    private static Node findOpt(List<Node> ns, String name) {
        for (Node n : ns) {
            if (name.equals(n.eff.name)) return n;
            Node x = findOpt(n.children, name);
            if (x != null) return x;
        }
        return null;
    }

    /** Пробуем связки: консул-ключ + детачмент + лучшие юниты в нём. Берём, если выгоднее обычных выборов. */
    private void bundles(Force army, Force prim, List<Cand> cands, Params p) {
        // сначала оцениваем все связки «на пробу», потом строим лучшую (или две — на большой лимит)
        Map<String, Double> score = new HashMap<String, Double>();
        for (String[] en : ENABLERS) {
            if (!bundleFitsStyle(en[1])) continue;
            double sc = tryBundle(army, cands, p, en, false);
            if (sc > 0) score.put(en[0], sc);
        }
        int maxBundles = p.limit >= 3000 && !scratch ? 2 : 1;
        for (int k = 0; k < maxBundles && !score.isEmpty(); k++) {
            String best = null;
            for (String key : score.keySet()) if (best == null || score.get(key) > score.get(best)) best = key;
            score.remove(best);
            for (String[] en : ENABLERS) if (en[0].equals(best)) tryBundle(army, cands, p, en, true);
        }
    }

    /** Собрать связку; keep=false — только оценить и откатить. Возвращает оценку (0 — не годится). */
    private double tryBundle(Force army, List<Cand> cands, Params p, String[] en, boolean keep) {
        // ориентир — средняя ценность пяти лучших обычных выборов
        List<Double> vs = new ArrayList<Double>();
        for (Cand c : cands) if (c.pts >= 60 && !c.transport && !c.dead) vs.add(raw(c));
        java.util.Collections.sort(vs, java.util.Collections.<Double>reverseOrder());
        double baseline = 0;
        int nb = Math.min(5, vs.size());
        for (int k = 0; k < nb; k++) baseline += vs.get(k) / nb;
        Cand key = null;
        for (Cand c : cands) if (c.name.startsWith(en[0]) && ok(c) && (key == null || value(c) > value(key))) key = c;
        if (key == null) return 0;
        double left = p.limit - e.rosterCost(d.pointsId);
        if (key.pts > left * 0.5) return 0;
        // сначала ставим консула — только он открывает детачмент
        int picksBefore = res.picks.size();
        double[] haveBefore = res.have.clone();
        double proxyBefore = proxyPts, newBefore = newPts;
        Force where = forceWithRoom(listForces(), key);
        if (where == null) return 0;
        place(key, where, "ключ к детачменту " + en[1]);
        if (res.picks.size() == picksBefore) return 0;
        Pick keyPick = res.picks.get(res.picks.size() - 1);
        ForceOpt det = null;
        for (ForceOpt o : e.childForceOptions(army)) if (o.name().contains(en[1])) { det = o; break; }
        if (det == null) { rollback(picksBefore, haveBefore, null); proxyPts = proxyBefore; newPts = newBefore; return 0; }
        int before = costErrors();
        Force df = e.addForce(army, det, null);
        if (costErrors() > before) { rollback(picksBefore, haveBefore, df); proxyPts = proxyBefore; newPts = newBefore; return 0; }
        List<Cand> payload = candidates(df, p.mode, det);
        double val = 0, pts = keyPick.pts;
        int added = 0;
        for (int k = 0; k < 3; k++) {
            Cand best = null;
            double bs = 0;
            double l2 = p.limit - e.rosterCost(d.pointsId);
            for (Cand c : payload) {
                if (!ok(c) || c.pts > l2 || c.pts <= 0) continue;
                if (forceWithRoom(single(df), c) == null) continue;
                double v = value(c);
                if (v > bs) { bs = v; best = c; }
            }
            if (best == null) break;
            int n0 = res.picks.size();
            place(best, df, "в " + en[1] + " (открыт консулом " + en[0] + ")");
            if (res.picks.size() > n0) { Pick pp = res.picks.get(res.picks.size() - 1); val += unitScore(pp.sel); pts += pp.pts; added++; }
            else best.dead = true;
        }
        double avg = val / Math.max(1, added);
        boolean ok = added > 0 && pts <= p.limit * 0.45 && avg >= baseline * 0.7;
        if (DEBUG) System.err.println("bundle " + en[0] + " keep=" + keep + " added=" + added + " pts=" + pts + " avg=" + avg + " base=" + baseline + " ok=" + ok);
        if (!ok || !keep) {
            rollback(picksBefore, haveBefore, df);
            proxyPts = proxyBefore;
            newPts = newBefore;
            return ok ? avg * (1 + added * 0.1) : 0;
        }
        for (Cand c : payload) cands.add(c);
        res.reasons.add("Связка: " + en[0] + " открывает " + en[1] + " — взяты " + added + " " + (added == 1 ? "юнит" : "юнита") + " в этот детачмент.");
        return avg;
    }

    private List<Force> listForces() { List<Force> fs = new ArrayList<Force>(); r.allForces(fs); return fs; }

    private static List<Force> single(Force f) { List<Force> l = new ArrayList<Force>(); l.add(f); return l; }

    private void rollback(int picksBefore, double[] haveBefore, Force df) {
        while (res.picks.size() > picksBefore) {
            Pick pk = res.picks.remove(res.picks.size() - 1);
            if (pk.status == PROXY) { proxyPts -= pk.pts; if (pk.like != null && pk.like.proxyUse > 0) pk.like.proxyUse--; }
            if (pk.status == NEW) newPts -= pk.pts;
            String sk = pk.sel.cat == null ? "" : pk.sel.cat;
            if (usedBySlot.containsKey(sk)) usedBySlot.put(sk, Math.max(0, usedBySlot.get(sk) - 1));
            e.removeSel(pk.sel);
            Integer u = usedById.get(pk.sel.entryId);
            for (Map.Entry<String, Integer> en : new ArrayList<Map.Entry<String, Integer>>(usedById.entrySet()))
                if (en.getKey().equals(pk.sel.entryId) && en.getValue() > 0) usedById.put(en.getKey(), en.getValue() - 1);
        }
        res.have = haveBefore;
        if (df != null && (df.parent == null ? r.forces.contains(df) : df.parent.children.contains(df))) e.removeForce(df);
    }

    /** Убрать лишние улучшения, которые дублируются при копировании шаблонов (например, Prime-выгоды). */
    private void fixOptions() {
        for (int k = 0; k < 12; k++) {
            boolean changed = false;
            for (Issue i : e.validate()) {
                if (!i.error || i.kind != Issue.OPT_MAX || !(i.where instanceof Sel)) continue;
                Sel w = (Sel) i.where;
                Sel victim = findByName(w, i.subject);
                if (victim == null) continue;
                if (victim.number > 1 && victim.number > i.v) victim.number = (int) Math.max(1, i.v);
                else removeDeep(victim);
                e.changed();
                changed = true;
                break;
            }
            if (!changed) break;
        }
        // прайм-выгоды вне прайм-слотов
        for (int k = 0; k < 12; k++) {
            boolean changed = false;
            for (Issue i : e.validate()) {
                if (!i.error || !(i.where instanceof Sel)) continue;
                Sel w = (Sel) i.where;
                if (!e.effSel(w).name.toLowerCase().startsWith("prime")) continue;
                removeDeep(w);
                e.changed();
                changed = true;
                break;
            }
            if (!changed) break;
        }
        dropUselessLogistical();
        for (Pick pk : res.picks) pk.pts = e.points(pk.sel);
    }

    /**
     * Logistical Benefit, скопированный из шаблона, имеет смысл только если дополнительный слот занят.
     * Убираем Prime-выгоду, если без неё ростер не получает новых ошибок (слот не нужен) или слот не выбран.
     */
    private void dropUselessLogistical() {
        for (Force f : listForces()) {
            for (Sel s : new ArrayList<Sel>(f.sels)) {
                Sel lb = e.logisticalOf(s);
                if (lb == null) continue;
                // верхний «Prime Unit», внутри которого выгода
                Sel top = lb;
                while (top.parent != null && top.parent != s) top = top.parent;
                int before = errorCount();
                Sel holder = top.parent == null ? s : top.parent;
                int idx = holder.children.indexOf(top);
                if (idx < 0) continue;
                holder.children.remove(idx);
                e.changed();
                if (errorCount() > before) { holder.children.add(idx, top); e.changed(); }
            }
        }
    }

    private int errorCount() {
        int n = 0;
        for (Issue i : e.validate()) if (i.error) n++;
        return n;
    }

    private Sel findByName(Sel root, String name) {
        for (Sel c : root.children) {
            if (e.effSel(c).name.equals(name)) return c;
            Sel x = findByName(c, name);
            if (x != null) return x;
        }
        return null;
    }

    private void removeDeep(Sel s) {
        if (s.parent != null) s.parent.children.remove(s); else s.force.sels.remove(s);
    }

    private void trimOver(double limit) {
        while (e.rosterCost(d.pointsId) > limit && !res.picks.isEmpty()) {
            Pick worst = null;
            for (int i = res.picks.size() - 1; i >= 0 && worst == null; i--) if (!res.picks.get(i).pinned) worst = res.picks.get(i);
            if (worst == null) break;
            e.removeSel(worst.sel);
            res.picks.remove(worst);
        }
    }

    static final String[] ROLE = {"охотник на технику", "косит пехоту", "ломает элиту", "держит удар", "рукопашный кулак", "быстрый удар"};

    static String kindOf(Pick p) {
        String t = p.type == null ? "" : p.type.toLowerCase();
        String n = p.name == null ? "" : p.name.toLowerCase();
        if (t.contains("walker") || t.contains("dreadnought") || n.contains("dreadnought")) return "dread";
        if (t.contains("knight") || t.contains("titan")) return "engine";
        if (t.contains("vehicle") || t.contains("flyer")) return p.transport ? "transport" : "vehicle";
        if (t.contains("automata")) return "dread";
        if (t.contains("cavalry") || t.contains("jump") || t.contains("antigrav") || n.contains("bike") || n.contains("speeder")) return "fast";
        if (t.contains("heavy") || n.contains("terminator") || n.contains("cataphractii") || n.contains("tartaros") || n.contains("saturnine")) return "elite";
        return "infantry";
    }

    static String list3(List<String> xs) {
        StringBuilder b = new StringBuilder();
        boolean more = xs.size() > 3;
        for (int i = 0; i < xs.size() && i < 3; i++) { if (i > 0) b.append(!more && i == xs.size() - 1 ? " и " : ", "); b.append(xs.get(i)); }
        if (more) b.append(" и др");
        return b.toString();
    }

    /** Суть армии и план на игру — по составу, противнику и миссии. */
    private void strategy() {
        List<String> st = res.strategy;
        st.clear();
        res.core.clear();
        res.holders.clear();
        res.taskUnits.clear();
        if (res.picks.isEmpty()) return;
        Map<String, Double> by = new LinkedHashMap<String, Double>();
        double total = 0;
        for (Pick p : res.picks) { String k = kindOf(p); by.put(k, (by.containsKey(k) ? by.get(k) : 0) + p.pts); total += p.pts; }
        double inf = val(by, "infantry"), veh = val(by, "vehicle") + val(by, "engine"), dr = val(by, "dread"), fast = val(by, "fast"), el = val(by, "elite"), tr = val(by, "transport");
        String arch;
        if (veh / total > 0.4) arch = "Бронекулак";
        else if (dr / total > 0.3) arch = "Стена дредноутов";
        else if (el / total > 0.35) arch = "Элитное ядро";
        else if ((fast + tr) / total > 0.35) arch = "Мобильный удар";
        else if (inf / total > 0.45) arch = "Пехотная линия";
        else arch = "Сбалансированная армия";
        res.archetype = arch;
        // ядро — самые дорогие выборы с их ролью
        List<Pick> sorted = new ArrayList<Pick>(res.picks);
        java.util.Collections.sort(sorted, new java.util.Comparator<Pick>() { public int compare(Pick x, Pick y) { return Double.compare(y.pts, x.pts); } });
        List<String> core = new ArrayList<String>();
        for (Pick p : sorted) {
            if (core.size() >= 3) break;
            int bi = 0;
            for (int i = 0; i < 6; i++) if (p.cap != null && p.cap.get(i) / Math.max(1e-9, norm[i]) > p.cap.get(bi) / Math.max(1e-9, norm[bi])) bi = i;
            core.add(p.name + " (" + ROLE[bi] + ")");
            res.core.add(new String[]{p.name, ROLE[bi], String.valueOf(bi)});
        }
        res.core.clear();
        for (Pick p : sorted) {
            if (res.core.size() >= 3) break;
            int bi = 0;
            for (int i = 0; i < 6; i++) if (p.cap != null && p.cap.get(i) / Math.max(1e-9, norm[i]) > p.cap.get(bi) / Math.max(1e-9, norm[bi])) bi = i;
            res.core.add(new String[]{p.name, ROLE[bi], String.valueOf(bi)});
        }
        st.add(arch + " на " + Engine.fmt(total) + " очков. Ядро: " + list3(core) + ".");
        // держатели маркеров
        List<String> hold = new ArrayList<String>();
        for (Pick p : res.picks) { String k = kindOf(p); if ((k.equals("infantry") || k.equals("elite")) && !hold.contains(p.name)) hold.add(p.name); }
        boolean objMission = mission == null || !mission.equals("engine");
        if (objMission) {
            if (hold.isEmpty()) { st.add("Пеших отрядов почти нет — маркеры держать некому (техника и кавалерия их не контролируют): играйте на уничтожение и вторичные цели."); res.holdNote = "Пеших отрядов почти нет — играйте на уничтожение и вторичные цели."; }
            else { st.add("Маркеры держат пешие отряды: " + list3(hold) + ". Техника и кавалерия маркеры не контролируют — они прикрывают и выбивают держателей противника."); res.holders.addAll(hold); res.holdNote = "Техника и кавалерия маркеры не держат — они прикрывают и выбивают держателей врага."; }
        }
        // транспорт
        if (tr > 0) { st.add("Пехота едет в транспорте: первым ходом подвезите отряды к маркерам под прикрытием брони и высаживайтесь, когда точку можно удержать."); res.transport = "Первым ходом подвезите пехоту к маркерам под прикрытием брони, высаживайтесь, когда точку можно удержать."; }
        // против кого
        int top = 0;
        for (int i = 0; i < 6; i++) if (res.need[i] > res.need[top]) top = i;
        List<String> answer = new ArrayList<String>();
        for (Pick p : sorted) if (p.cap != null && p.cap.get(top) / Math.max(1e-9, norm[top]) > 40 && answer.size() < 3) answer.add(p.name);
        st.add("Главная задача против этого противника — " + CAP_RU[top] + (answer.isEmpty() ? "." : ": это работа для " + list3(answer) + "."));
        res.task = CAP_RU[top];
        res.taskIdx = top;
        res.taskUnits.addAll(answer);
        // слабое место
        double maxHave = 1e-9;
        for (int i = 0; i < 6; i++) maxHave = Math.max(maxHave, res.have[i] / Math.max(1e-9, norm[i]));
        int weak = -1;
        double worst = 0;
        for (int i = 0; i < 6; i++) {
            double gap = res.need[i] - (res.have[i] / Math.max(1e-9, norm[i])) / maxHave * 2;
            if (res.need[i] >= 0.8 && gap > worst) { worst = gap; weak = i; }
        }
        if (weak >= 0 && weak != top) { st.add("Слабое место — " + CAP_RU[weak] + ": не подставляйтесь там, где враг силён в этом, и берегите отряды, которые это закрывают."); res.weak = CAP_RU[weak]; res.weakIdx = weak; }
        // план по ходам
        String plan;
        if (arch.equals("Бронекулак") || arch.equals("Стена дредноутов")) plan = "Ходы 1–2: выбивайте вражескую технику и тяжёлые отряды с дистанции, пехоту держите за бронёй. Ходы 3–4: продвигайтесь к маркерам и добивайте ослабленных.";
        else if (arch.equals("Мобильный удар")) plan = "Ход 1: быстрые отряды и транспорт — вперёд, ради First Strike и ранних маркеров. Дальше бейте во фланги и перебрасывайтесь туда, где маркер плохо прикрыт.";
        else if (arch.equals("Элитное ядро")) plan = "Элита — мало моделей, каждая на счету: идите единым кулаком к центру, занимайте ключевой маркер и продавливайте рукопашную; на второстепенные маркеры — самые дешёвые отряды.";
        else if (arch.equals("Пехотная линия")) plan = "Растяните пехоту по маркерам с первого хода, держите плотный огонь и не лезьте в рукопашную с элитой — пусть враг идёт к вам.";
        else plan = "Ходы 1–2: займите ближние маркеры и снимите самые опасные угрозы стрельбой. Ходы 3–4: удерживайте точки, контратакуйте подошедших и добирайте вторичные цели.";
        res.plan = plan;
        String mn = null;
        if (mission != null && mission.equals("crucible")) mn = "В «Горниле войны» First Strike стоит 4 ПО — уничтожьте хоть что-то в первый ход.";
        if (mission != null && mission.equals("hold")) mn = "В «Захватить и удержать» маркеры дешевеют после каждого начисления — не держите одну точку весь бой.";
        if (mission != null && mission.equals("heart")) mn = "В «Сердце битвы» центральный маркер стоит 3 ПО — туда самые живучие держатели.";
        if (mission != null && mission.equals("ons_att")) mn = "В Onslaught очки идут только с удержанных маркеров — не тормозите наступление.";
        if (mission != null && mission.equals("ons_def")) mn = "В Onslaught защитнику выгодно выбивать держателей у маркеров (Vanguard 2) и не отдавать точки.";
        if (mission != null && mission.equals("engine")) mn = "Сначала стратегические цели и Титан: Engine Kill стоит 8–15 ПО.";
        res.missionNote = mn;
        if (mission != null && mission.equals("crucible")) plan += " В «Горниле войны» First Strike стоит 4 ПО — уничтожьте хоть что-то в первый ход.";
        if (mission != null && mission.equals("hold")) plan += " В «Захватить и удержать» маркеры дешевеют после каждого начисления — не держите одну точку весь бой.";
        if (mission != null && mission.equals("heart")) plan += " В «Сердце битвы» центральный маркер стоит 3 ПО — туда самые живучие держатели.";
        if (mission != null && mission.equals("ons_att")) plan += " В Onslaught очки идут только с удержанных маркеров — не тормозите наступление.";
        if (mission != null && mission.equals("ons_def")) plan += " В Onslaught защитнику выгодно выбивать держателей у маркеров (Vanguard 2) и не отдавать точки.";
        if (mission != null && mission.equals("engine")) plan += " Сначала стратегические цели и Титан: Engine Kill стоит 8–15 ПО.";
        st.add(plan);
    }

    private static double val(Map<String, Double> m, String k) { return m.containsKey(k) ? m.get(k) : 0; }

    // ================================================================ «улучши мой ростер»
    public static final class Swap {
        public String from, to, text, proxyOf;
        public double fromPts, toPts, gain;
        public int status;
        public Map<String, Object> after;
    }

    public static final class Improve {
        public final List<Swap> swaps = new ArrayList<Swap>();
        public final List<String> reasons = new ArrayList<String>();
        public String error;
        public double[] need;
    }

    private double armyScore(double[] have) {
        double s = 0;
        for (int i = 0; i < 6; i++) s += res.need[i] * Math.sqrt(Math.max(0, have[i]) / Math.max(1e-9, norm[i]));
        return s;
    }

    /** Точечные замены в готовом ростере: «замени A на B — станет сильнее против этого врага за те же очки». */
    public Improve improve(List<Roster> all, Params p, Roster target) {
        Improve out = new Improve();
        res = new Result();
        rnd = new Random(p.seed);
        excl = p.exclude == null ? new HashSet<String>() : p.exclude;
        freeMode = p.mode == 2; // «Улучши ростер» тоже работает по свободным правилам при выборе замен
        limit = target.limit > 0 ? target.limit : p.limit;
        usedById.clear();
        usedBySlot.clear();
        try {
            String cat = target.catalogueId;
            minis = p.minis;
            curCat = cat;
            collect(all, cat);
            scratch = owned.isEmpty();
            style = new HashSet<String>();
            res.need = enemyNeeds(all, p, out.reasons);
            mission = p.mission;
            missionNeeds(res.need, mission, out.reasons);
            out.need = res.need;
            r = Roster.fromJson(target.toJson());
            e = new Engine(d, r);
            List<Force> forces = new ArrayList<Force>();
            r.allForces(forces);
            // кандидаты по каждому детачменту основной фракции
            Map<Force, List<Cand>> byForce = new java.util.LinkedHashMap<Force, List<Cand>>();
            List<Cand> every = new ArrayList<Cand>();
            for (Force f : forces) {
                if (isContainer(f) || !cat.equals(f.catalogueId) || f.sels.isEmpty()) continue;
                List<Cand> cs = candidates(f, p.mode, null);
                byForce.put(f, cs);
                every.addAll(cs);
            }
            if (every.isEmpty()) { out.error = "Не нашёл, чем заменять: в ростере нет юнитов основной фракции."; return out; }
            computeNorm(every);
            // уже стоящие юниты занимают коллекцию
            for (Force f : forces) for (Sel s : f.sels) usedById.put(s.entryId, (usedById.containsKey(s.entryId) ? usedById.get(s.entryId) : 0) + 1);
            double[] have = new double[6];
            Map<Sel, Cap> caps = new java.util.HashMap<Sel, Cap>();
            for (Force f : byForce.keySet()) for (Sel s : f.sels) {
                Cap c = capOf(s);
                caps.put(s, c);
                for (int i = 0; i < 6; i++) have[i] += c.get(i);
            }
            double base = armyScore(have);
            int errBase = errorCount();
            double total = e.rosterCost(d.pointsId);
            List<Object[]> found = new ArrayList<Object[]>(); // {Sel, Cand, gain}
            for (Force f : byForce.keySet()) {
                for (Sel u : new ArrayList<Sel>(f.sels)) {
                    Cap cu = caps.get(u);
                    if (cu == null) continue;
                    double up = e.points(u);
                    if (up <= 0) continue;
                    String un = e.effSel(u).name;
                    if (un.equals("Allegiance") || un.startsWith("Rite of War")) continue;
                    Cand best = null;
                    double bg = 0;
                    for (Cand c : byForce.get(f)) {
                        if (c.node.opt.id().equals(u.entryId) || c.node.place == null || !c.node.place.equals(u.cat)) continue;
                        if (c.pts <= 0 || c.pts > up + Math.max(0, limit - total) || c.pts < up * 0.6) continue;
                        if (excl.contains(c.node.opt.id()) || !ok(c)) continue;
                        double[] h2 = have.clone();
                        for (int i = 0; i < 6; i++) h2[i] += c.cap.get(i) - cu.get(i);
                        double g = armyScore(h2) - base;
                        if (c.status == NEW) g *= 0.8; // нового придётся докупать
                        if (c.status == PROXY) g *= 0.9;
                        if (g > bg) { bg = g; best = c; }
                    }
                    if (best != null && bg > base * 0.006) found.add(new Object[]{u, best, bg});
                }
            }
            java.util.Collections.sort(found, new java.util.Comparator<Object[]>() { public int compare(Object[] x, Object[] y) { return Double.compare((Double) y[2], (Double) x[2]); } });
            for (Object[] fx : found) {
                if (out.swaps.size() >= 6) break;
                Sel u = (Sel) fx[0];
                Cand c = (Cand) fx[1];
                Force f = u.force;
                int idx = f.sels.indexOf(u);
                double up = e.points(u);
                Cap cu = caps.get(u);
                String un = e.effSel(u).name;
                f.sels.remove(idx);
                e.changed();
                int before = res.picks.size();
                place(c, f, null);
                boolean ok = res.picks.size() > before;
                Sel ns = ok ? res.picks.get(res.picks.size() - 1).sel : null;
                if (ok) {
                    // новый юнит — на место старого, чтобы порядок в ростере не прыгал
                    f.sels.remove(ns);
                    f.sels.add(idx, ns);
                    e.changed();
                    ok = errorCount() <= errBase && e.rosterCost(d.pointsId) <= limit + 0.01;
                }
                if (ok) {
                    Swap sw = new Swap();
                    sw.from = un;
                    sw.to = e.effSel(ns).name;
                    sw.fromPts = up;
                    sw.toPts = e.points(ns);
                    sw.status = c.status;
                    sw.proxyOf = c.proxyOf;
                    sw.gain = (Double) fx[2] / Math.max(1e-9, base);
                    Cap cn = capOf(ns);
                    StringBuilder t = new StringBuilder();
                    for (int i = 0; i < 6; i++) {
                        if (res.need[i] < 1.0 && Math.abs(cn.get(i) - cu.get(i)) < 1e-9) continue;
                        double was = have[i], now = have[i] - cu.get(i) + cn.get(i);
                        if (was <= 0 && now <= 0) continue;
                        long ch = Math.round(100 * (now - was) / Math.max(1e-9, was));
                        if (Math.abs(ch) < 5) continue;
                        if (t.length() > 0) t.append(" · ");
                        t.append(ch > 0 ? "+" : "").append(ch).append("% ").append(CAP_SHORT[i]);
                    }
                    sw.text = t.toString();
                    sw.after = r.toJson();
                    out.swaps.add(sw);
                }
                // откат
                if (ns != null) { e.removeSel(ns); res.picks.remove(res.picks.size() - 1); }
                f.sels.add(idx, u);
                e.changed();
            }
            if (out.swaps.isEmpty()) out.reasons.add(0, "Явных улучшений не нашлось: ростер уже хорошо закрывает то, что нужно против этого противника.");
        } catch (Throwable t) {
            out.error = "Ошибка анализа: " + t;
        }
        return out;
    }

    // ================================================================ смысл особых правил персонажей
    static final String[][] SENSE = {
            // ключевые слова (regex, по тексту правила) → направление, эффект
            {"re-?roll[^.]{0,60}(hit|wound)|\\+1 to (hit|wound)", "-1", "перебросы/бонусы к попаданию и ранению — отряд с ним бьёт заметно сильнее"},
            {"invulnerable|feel no pain|shrouded|damage mitigation", "3", "защита (инвул/FNP) — держит удар дольше"},
            {"reaction", "3", "дополнительные реакции — больше ответных действий в ход врага"},
            {"deep strike|infiltrat|scout|outflank|flank|move[^.]{0,30}additional|\\+\\d\"? to (its )?movement", "5", "мобильность (глубокий удар/инфильтрация/скорость) — ранние маркеры и фланги"},
            {"objective|victory point|line \\(", "6", "работа с маркерами и победными очками"},
            {"charge|combat|melee|attacks characteristic|fight", "4", "усиливает рукопашную"},
            {"armourbane|vehicle|hull point|armour penetration", "0", "лучше против техники"},
            {"precision|challenge|gambit|character", "2", "сильнее в вызовах и против командиров/элиты"},
            {"leadership|morale|fear|pinning|panic", "7", "давит Лидерство и мораль врага"},
            {"warlord|master of the legion|rite of war|detachment|command", "8", "командная роль: открывает детачменты/Rite of War, ведёт армию"},
    };

    static final class Sense { final double[] cap = new double[6]; final List<String> tags = new ArrayList<String>(); }

    /** Разбор правил персонажа: что они дают армии (по тексту правил из данных). */
    Sense sense(Sel s) {
        Sense out = new Sense();
        Set<String> weapon = new HashSet<String>();
        collectWeaponRules(s, weapon);
        List<Sel> ss = new ArrayList<Sel>();
        ss.add(s);
        for (Sel ch : s.children) if ("model".equals(Data.str(e.entryOf(ch), "type"))) ss.add(ch);
        Set<String> done = new HashSet<String>();
        for (Sel x : ss) for (Map<String, Object> r : e.rulesOf(e.entryOf(x), e.linkOf(x))) {
            String n = Data.clean(Data.str(r, "name"));
            String ds = Data.str(r, "description");
            if (n == null || ds == null || !done.add(n)) continue;
            if (weapon.contains(n.toLowerCase().replaceAll("\\s*\\(.*", "").trim())) continue;
            String t = ds.toLowerCase();
            for (String[] row : SENSE) {
                if (!java.util.regex.Pattern.compile(row[0]).matcher(t).find()) continue;
                int k = Integer.parseInt(row[1]);
                if (k == -1) { out.cap[0] += 0.15; out.cap[1] += 0.2; out.cap[2] += 0.2; out.cap[4] += 0.15; }
                else if (k < 6) out.cap[k] += 0.3;
                else if (k == 6) out.cap[5] += 0.1;
                if (out.tags.size() < 4) out.tags.add(n + " — " + row[2]);
                break;
            }
        }
        return out;
    }

    static final String[] CAP_SHORT = {"противотанка", "огня по пехоте", "огня по элите", "живучести", "рукопашной", "скорости"};

    /** «Сырая» польза на очко против этого врага, без поправки на уже набранное. */
    private double raw(Cap cap, double pts) {
        if (cap == null) return 0;
        double v = 0;
        for (int i = 0; i < 6; i++) v += res.need[i] * cap.get(i) / Math.max(1e-9, norm[i]);
        return v / Math.max(40, pts);
    }

    /** Обоснование каждого юнита: роль и доля в армии, против чего у врага, почему именно он, снаряжение, альтернатива. */
    private void explainAll() {
        double[] tot = new double[6];
        for (Pick p : res.picks) if (p.cap != null) for (int i = 0; i < 6; i++) tot[i] += p.cap.get(i);
        for (Pick p : res.picks) {
            p.explain.clear();
            if (p.cap == null) continue;
            // роль и доля
            int a = 0;
            for (int i = 0; i < 6; i++) if (p.cap.get(i) / Math.max(1e-9, norm[i]) > p.cap.get(a) / Math.max(1e-9, norm[a])) a = i;
            int b = -1;
            for (int i = 0; i < 6; i++) if (i != a && (b < 0 || p.cap.get(i) / Math.max(1e-9, norm[i]) > p.cap.get(b) / Math.max(1e-9, norm[b]))) b = i;
            long sa = Math.round(100 * p.cap.get(a) / Math.max(1e-9, tot[a]));
            boolean support = sa < 8;
            String role = support ? "Своей огневой мощи немного (" + sa + "% " + CAP_SHORT[a] + " армии) — его ценность в другом, см. ниже"
                    : "Главная роль — " + ROLE[a] + ": даёт " + sa + "% всего " + CAP_SHORT[a] + " армии";
            if (b >= 0 && !support) { long sb = Math.round(100 * p.cap.get(b) / Math.max(1e-9, tot[b])); if (sb >= 8) role += ", плюс " + sb + "% " + CAP_SHORT[b]; }
            role += ".";
            if (!support && res.need[a] >= 1.8) role += " Против этого врага это самое важное направление.";
            else if (!support && res.need[a] >= 1.2) role += " Против этого врага это нужно.";
            p.explain.add(new String[]{"cannon", "Роль в армии", role});
            // против чего у врага
            if (foeMain != null && enemyName != null && !support) {
                String t = null;
                if (a == 0) t = foeMain.vehicle >= 0.15 ? "У " + enemyName + " около " + pct(foeMain.vehicle) + "% армии — техника" + (foeMain.heavy > 0.05 ? " (тяжёлая — " + pct(foeMain.heavy) + "%)" : "") + ": этот юнит её пробивает."
                        : "Техники у " + enemyName + " немного (" + pct(foeMain.vehicle) + "%), но то, что есть — дредноуты, танки, транспорт — без противотанка не снять.";
                else if (a == 1) t = foeMain.horde >= 0.15 ? "У " + enemyName + " около " + pct(foeMain.horde) + "% лёгкой пехоты — этот юнит выкашивает такие отряды."
                        : "Лёгкой пехоты у " + enemyName + " немного (" + pct(foeMain.horde) + "%), но маркеры держит именно пехота — её всё равно нужно снимать.";
                else if (a == 2) t = foeMain.elite >= 0.15 ? "У " + enemyName + " около " + pct(foeMain.elite) + "% элиты (Sv 2+, T5+) — здесь нужно AP2 и сильные удары."
                        : "Элиты у " + enemyName + " немного (" + pct(foeMain.elite) + "%), но терминаторы и командиры с 2+ — главные угрозы, против них нужен AP2.";
                else if (a == 3) t = "Держит удар: стоит на маркере и переживает огонь " + enemyName + ", пока остальные работают.";
                else if (a == 4) t = "Рукопашный кулак: врубается в " + (foeMain.melee > 0.3 ? "рукопашников " : "стрелков ") + enemyName + " и связывает их боем.";
                else t = "Быстрый: ранние маркеры, First Strike и перехват держателей " + enemyName + ".";
                p.explain.add(new String[]{"eye", "Против " + enemyName, t});
            }
            // именной персонаж: главное — его особые правила
            boolean unique = p.cand != null && p.cand.type != null && p.cand.type.contains("Unique");
            boolean hero = p.slot != null && (p.slot.contains("Command") || p.slot.contains("Warlord") || p.slot.contains("Lord"));
            if (unique || (support && hero)) {
                List<String> rl = charRules(p.sel);
                Sense sn = p.cand != null ? p.cand.sense : null;
                if (!rl.isEmpty()) {
                    StringBuilder t = new StringBuilder();
                    if (sn != null && !sn.tags.isEmpty()) {
                        t.append(unique ? "Его взяли ради правил:\n" : "Что дают его правила:\n");
                        for (String tg : sn.tags) t.append("• ").append(tg).append("\n");
                        int top = 0;
                        for (int i = 0; i < 6; i++) if (res.need[i] > res.need[top]) top = i;
                        if (sn.cap[top] > 0) t.append("Против этого врага это в точку: нужнее всего ").append(CAP_RU[top]).append(". ");
                        List<String> rest = new ArrayList<String>();
                        for (String r0 : rl) { boolean used = false; for (String tg : sn.tags) if (tg.startsWith(r0 + " ")) used = true; if (!used) rest.add(r0); }
                        if (!rest.isEmpty()) t.append("Ещё: ").append(android_join2(rest, 3)).append(".");
                    } else {
                        t.append(unique ? "Его сила — особые правила: " : "Особые правила: ");
                        t.append(android_join2(rl, 4)).append(".");
                    }
                    Cand gen = null;
                    for (Cand c : pool) if (c.name.equals("Centurion") && c.node.place != null && p.cand != null && c.node.place.equals(p.cand.node.place)) { gen = c; break; }
                    if (unique) {
                        t.append(" Берите его, если будете играть через эти правила");
                        if (gen != null && gen.pts < p.pts) t.append(" — иначе выгоднее Centurion (" + Engine.fmt(gen.pts) + " оч.) с прокачкой, экономия " + Engine.fmt(p.pts - gen.pts) + " оч");
                        t.append(".");
                    }
                    p.explain.add(0, new String[]{"book", unique ? "Ради чего он в армии" : "Особые правила", t.toString()});
                }
            }
            // почему именно он
            StringBuilder w = new StringBuilder();
            if (p.pinned) w.append("Вы отметили «обязательно взять». ");
            if (p.status == OWNED) w.append(p.physical ? "Есть в «Моих миниатюрах»" : "Есть у вас").append(p.rosters > 1 ? ", вы играли им в " + p.rosters + " ростерах" : "").append(" — докупать не нужно. ");
            else if (p.status == PROXY) w.append("Прокси из «").append(p.proxyOf).append("»: тот же размер и подставка, моделей хватает. ");
            else w.append("Нового юнита нет в коллекции — его пришлось бы собрать. ");
            if (p.why != null && !p.pinned) w.append(Character.toUpperCase(p.why.charAt(0))).append(p.why.substring(1)).append(". ");
            if (p.cand != null && p.cand.signature && (p.why == null || !p.why.contains("фирменн"))) w.append("Фирменный отряд легиона — у него особые правила легиона. ");
            if (p.cand != null && p.cand.line && (mission == null || !mission.equals("engine"))) w.append("Есть Line — надёжно держит маркеры. ");
            p.explain.add(new String[]{"star", "Почему именно он", w.toString().trim()});
            // прайм
            if (p.why != null && p.why.contains("прайм: ")) {
                String pb = p.why.substring(p.why.indexOf("прайм: ") + 7);
                int sc = pb.indexOf(';');
                if (sc > 0) pb = pb.substring(0, sc);
                String txt;
                if (pb.startsWith("Logistical Benefit")) txt = pb + ": прайм-слот открыл ещё одно место в детачменте — это дешевле, чем брать ещё командира ради нового вспомогательного детачмента.";
                else {
                    String rule = null;
                    for (Map<String, Object> rr : d.rules.values()) if (pb.equalsIgnoreCase(Data.clean(Data.str(rr, "name")))) { rule = Data.str(rr, "description"); break; }
                    if (rule != null) { int dot = rule.indexOf(". "); if (dot > 20 && dot < 220) rule = rule.substring(0, dot + 1); else if (rule.length() > 220) rule = rule.substring(0, 220) + "…"; }
                    txt = "Прайм-выгода «" + pb + "»" + (rule != null ? ": " + rule : ".") + " Logistical Benefit здесь не дал бы полезного слота (ставить в него нечего), поэтому выбрана эта выгода — прайм бесплатен.";
                }
                p.explain.add(new String[]{"crown", "Прайм-слот", txt});
            }
            // снаряжение
            if (!p.gear.isEmpty()) p.explain.add(new String[]{"sword", "Снаряжение", "Подобрано под противника: " + list3(p.gear) + "."});
            else if (p.cand != null && p.cand.own != null && p.cand.own.template != null) p.explain.add(new String[]{"sword", "Снаряжение", "Как в вашем ростере — свою сборку не трогаю."});
            // альтернатива
            if (p.cand != null) {
                double mine = raw(p.cap, p.pts);
                Cand alt = null;
                double altV = 0;
                for (Cand c : pool) {
                    if (c.node.opt.id().equals(p.cand.node.opt.id()) || c.cap == null || c.node.place == null || !c.node.place.equals(p.cand.node.place)) continue;
                    if (c.pts <= 0 || c.pts > p.pts * 1.5 || c.pts < p.pts * 0.5) continue;
                    boolean taken = false;
                    for (Pick q : res.picks) if (q.entryId.equals(c.node.opt.id())) { taken = true; break; }
                    if (taken) continue;
                    double v = raw(c.cap, c.pts);
                    if (alt == null || v > altV) { alt = c; altV = v; }
                }
                if (alt != null) {
                    String st = alt.status == OWNED ? "есть у вас" : alt.status == PROXY ? "можно проксить" : "нужно собрать";
                    String cmp;
                    if (altV <= 0 || mine >= altV) cmp = "на " + Math.max(1, Math.round(100 * (1 - altV / Math.max(1e-9, mine)))) + "% слабее против этого врага на очко";
                    else cmp = "чуть сильнее на очко (+" + Math.round(100 * (altV / Math.max(1e-9, mine) - 1)) + "%), но " + (p.status == OWNED && alt.status != OWNED ? "его нет в коллекции" : "армии уже хватает " + CAP_SHORT[bestIdx(alt.cap)]);
                    p.explain.add(new String[]{"scroll", "Вместо чего", "Альтернатива в этом слоте — " + alt.name + " (" + Engine.fmt(alt.pts) + " оч., " + st + "): " + cmp + "."});
                }
            }
        }
    }

    /** Собственные правила персонажа (без правил оружия). */
    private List<String> charRules(Sel s) {
        Set<String> weapon = new HashSet<String>();
        collectWeaponRules(s, weapon);
        List<String> out = new ArrayList<String>();
        List<Sel> ss = new ArrayList<Sel>();
        ss.add(s);
        for (Sel ch : s.children) if ("model".equals(Data.str(e.entryOf(ch), "type"))) ss.add(ch);
        for (Sel x : ss) for (Map<String, Object> r : e.rulesOf(e.entryOf(x), e.linkOf(x))) {
            String n = Data.clean(Data.str(r, "name"));
            if (n == null || n.isEmpty() || out.contains(n) || weapon.contains(n.toLowerCase().replaceAll("\\s*\\(.*", "").trim())) continue;
            out.add(n);
        }
        return out;
    }

    private void collectWeaponRules(Sel s, Set<String> out) {
        for (Map<String, Object> p : e.profilesOf(e.entryOf(s), e.linkOf(s))) {
            String t = Data.str(p, "typeName");
            if (t == null || !t.toLowerCase().contains("weapon")) continue;
            for (Map<String, Object> c : Data.list(p, "characteristics")) if ("Special Rules".equals(Data.str(c, "name"))) {
                String v = Data.str(c, "$text");
                if (v != null) for (String part : v.split(",")) out.add(part.trim().toLowerCase().replaceAll("\\s*\\(.*", "").trim());
            }
        }
        for (Sel ch : s.children) collectWeaponRules(ch, out);
    }

    private static String android_join2(List<String> xs, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.size() && i < max; i++) { if (i > 0) b.append(", "); b.append(xs.get(i)); }
        if (xs.size() > max) b.append(" и др.");
        return b.toString();
    }

    private int bestIdx(Cap c) {
        int a = 0;
        for (int i = 0; i < 6; i++) if (c.get(i) / Math.max(1e-9, norm[i]) > c.get(a) / Math.max(1e-9, norm[a])) a = i;
        return a;
    }

    private static long pct(double v) { return Math.round(v * 100); }

    private void summarize() {
        try { strategy(); } catch (Throwable ignored) { }
        try { explainAll(); } catch (Throwable ignored) { }
        int own = 0, prox = 0, nw = 0;
        for (Pick p : res.picks) if (p.status == OWNED) own++; else if (p.status == PROXY) prox++; else nw++;
        res.reasons.add("Из вашей коллекции: " + own + ", прокси на похожие миниатюры: " + prox + (nw > 0 ? ", новых: " + nw : "") + ".");
        // главные сильные стороны
        int a = 0;
        for (int i = 0; i < 6; i++) if (res.have[i] * res.need[i] > res.have[a] * res.need[a]) a = i;
        int b = a == 0 ? 1 : 0;
        for (int i = 0; i < 6; i++) if (i != a && res.have[i] * res.need[i] > res.have[b] * res.need[b]) b = i;
        res.reasons.add("Упор состава: " + CAP_RU[a] + " и " + CAP_RU[b] + ".");
    }
}
