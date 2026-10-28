package com.medoedy.liber;

import com.medoedy.liber.engine.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Гамбиты для вызовов: особые — из данных BSData (по фракциям), базовые — краткая памятка, свои — от игрока. */
final class Gambits {
    private Gambits() { }

    static final String GAMBIT_TYPE = "9b5f-da4e-5739-cbcf";

    static final class G {
        String name, summary, description, faction;
        boolean core, custom;
    }

    private static List<G> all;

    /** Базовые гамбиты 3-й редакции: название, кратко, подробно (по своду правил, своими словами). */
    static final String[][] CORE = {
            {"Seize the Initiative", "Шаг Фокуса: бросьте 2 куба, оставьте больший.",
                    "В шаге Фокуса бросьте дополнительный куб и отбросьте наименьший результат (то есть 2D6, берётся больший). Все остальные модификаторы Фокуса — Combat Initiative, раны, Outside Support, Duellist's Edge — применяются как обычно. Других эффектов нет: хорош, когда важно ударить первым."},
            {"Flurry of Blows", "+D3 атаки, но урон каждого попадания — 1.",
                    "В шаге Ударов Attacks модели получают +D3. Но Damage всех попаданий этой модели в этом раунде становится 1, и это значение не может изменить никакое особое правило. Хорош против моделей с 1–2 ранами и плохой выбор против многораневых персонажей."},
            {"Test the Foe", "Без бонусов в Фокусе и Ударах; если выжил — Преимущество в шаге Славы.",
                    "Модель не получает никаких бонусов в шагах Фокуса и Ударов (дерётся как обычно, без эффекта гамбита). Но если она не снята как потеря, то в шаге Славы её игрок автоматически получает Challenge Advantage: при ничьей именно он решает, продолжать ли вызов, и в следующем Face-Off выбирает гамбит первым."},
            {"Guard Up", "+1 WS, но только 1 атака; за каждый промах врага +1 к следующему Фокусу.",
                    "В шаге Ударов модель получает +1 к Weapon Skill, но делает только одну атаку. За каждую атаку противника, которая промахнулась по этой модели в этом шаге Ударов, вы получаете +1 к следующему броску Фокуса, если вызов продолжится. Оборонительный выбор, чтобы пережить раунд и перехватить инициативу."},
            {"Taunt and Bait", "WS и A становятся как у врага; +1 CRP при победе за каждое использование.",
                    "Weapon Skill и Attacks модели становятся равны значениям противника; если они уже равны — каждое уменьшается ещё на 1. Зато за каждый раз, когда этот гамбит был выбран в этом вызове, вы получаете +1 очко Combat Resolution, если выиграете вызов. Выбор для набора очков в долгом поединке."},
            {"Grandstand", "Фокус: 2 куба, берётся меньший, без Outside Support; Outside Support идёт в атаки.",
                    "В шаге Фокуса бросьте дополнительный куб и отбросьте наибольший результат, и модель не получает бонус Outside Support к Фокусу. В шаге Ударов модель получает к Attacks бонус, равный тому Outside Support, который она получила бы к Фокусу. Хорош, когда рядом много своих моделей."},
            {"Feint and Riposte", "Только тому, кто выбирает первым: запретите противнику один гамбит.",
                    "Можно выбрать, только если вы выбираете гамбит первым в этом шаге Face-Off. Назовите любой другой гамбит — противник не может выбрать его в этом Face-Off. Других эффектов нет. Полезен, чтобы отнять у противника Seize the Initiative или Withdraw."},
            {"Withdraw", "Только 1 атака; в шаге Славы можно завершить вызов без очков для всех.",
                    "В шаге Ударов модель делает только одну атаку. В шаге Славы, если модель не была снята как потеря в шаге Ударов, её игрок может завершить вызов: ни одна сторона не получает очков Combat Resolution. Способ спасти ценного командира из проигрышной дуэли."},
            {"Finishing Blow", "Фокус: 2 куба, берётся меньший; попадания +1 S и +1 Damage.",
                    "В шаге Фокуса бросьте дополнительный куб и отбросьте наибольший результат. В шаге Ударов попадания модели получают +1 к Strength и +1 к Damage. Рискованный выбор, чтобы добить раненого противника."},
    };

    /** Общие правила выбора гамбитов — показываются в списке. */
    static final String RULES = "Первым гамбит выбирает вызывающий (в 1-м раунде) или игрок с Challenge Advantage (в следующих). Оба игрока не могут выбрать один и тот же гамбит в одном Face-Off. Эффекты действуют до конца раунда, если не сказано иное; при противоречии приоритет у игрока с Преимуществом.";

    static List<G> all() {
        if (all != null) return all;
        all = new ArrayList<G>();
        for (String[] c : CORE) {
            G g = new G();
            g.name = c[0];
            g.summary = c[1];
            g.description = c[2];
            g.core = true;
            all.add(g);
        }
        Data d = Store.data;
        if (d != null) {
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (Data.Cat cat : d.cats.values()) collect(cat.node, cat.name, all, seen);
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object o, String cat, List<G> out, java.util.Set<String> seen) {
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (GAMBIT_TYPE.equals(Data.str(m, "typeId")) && m.containsKey("characteristics")) {
                String name = Data.clean(Data.str(m, "name"));
                if (name != null && seen.add(name)) {
                    G g = new G();
                    g.name = name;
                    g.faction = cat;
                    for (Map<String, Object> ch : Data.list(m, "characteristics")) {
                        String n = Data.str(ch, "name"), v = Data.str(ch, "$text");
                        if (v == null) continue;
                        v = v.replace("\"", "").trim();
                        if ("Summary".equals(n)) g.summary = v; else if ("Description".equals(n)) g.description = v;
                    }
                    if (g.summary == null || g.summary.isEmpty()) g.summary = g.description == null ? "" : g.description;
                    out.add(g);
                }
            }
            for (Object v : m.values()) collect(v, cat, out, seen);
        } else if (o instanceof List) {
            for (Object v : (List<Object>) o) collect(v, cat, out, seen);
        }
    }

    /** Гамбиты фракции игрока (по ключевым словам названия каталога). */
    static boolean fits(G g, String faction) {
        if (g.faction == null || faction == null || faction.isEmpty()) return false;
        String f = faction.toLowerCase().replaceAll("^[ivxl]+ - ", "");
        String gf = g.faction.toLowerCase().replaceAll("^[ivxl]+ - ", "");
        if (gf.contains("legiones astartes") && faction.matches("^[IVXL]+ - .*")) return true;
        return gf.equals(f) || gf.contains(f) || f.contains(gf);
    }

    static List<G> custom(MainActivity a) {
        List<G> out = new ArrayList<G>();
        String raw = a.prefs.getString("gambits_custom", "");
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split("\t", 2);
            G g = new G();
            g.name = p[0];
            g.summary = p.length > 1 ? p[1] : "";
            g.description = g.summary;
            g.custom = true;
            out.add(g);
        }
        return out;
    }

    static void addCustom(MainActivity a, String name, String text) {
        String raw = a.prefs.getString("gambits_custom", "");
        raw += name.replace("\t", " ").replace("\n", " ") + "\t" + text.replace("\t", " ").replace("\n", " ") + "\n";
        a.prefs.edit().putString("gambits_custom", raw).apply();
    }
}
