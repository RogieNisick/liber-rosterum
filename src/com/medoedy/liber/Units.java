package com.medoedy.liber;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.CatSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Вспомогательное: тип юнита для силуэта, русские названия слотов и детачментов. */
final class Units {
    private static final Map<String, String> kindCache = new HashMap<String, String>();

    /** Силуэт по профилю модели («Type»/«Unit Type») и названию. */
    static String kind(Engine e, Map<String, Object> entry, Map<String, Object> link) {
        String id = Data.str(entry, "id");
        String k = kindCache.get(id);
        if (k != null) return k;
        String type = findType(e, entry, link, 0);
        k = Glyph.forUnitType(type, Data.str(entry, "name"));
        kindCache.put(id, k);
        return k;
    }

    private static String findType(Engine e, Map<String, Object> entry, Map<String, Object> link, int depth) {
        if (entry == null || depth > 2) return null;
        for (Map<String, Object> p : e.profilesOf(entry, link)) {
            for (Map<String, Object> ch : Data.list(p, "characteristics")) {
                String n = Data.str(ch, "name");
                if (n != null && (n.equalsIgnoreCase("Type") || n.equalsIgnoreCase("Unit Type"))) return Data.str(ch, "$text");
            }
        }
        for (Map<String, Object> c : Data.list(entry, "selectionEntries")) {
            if (!"model".equals(Data.str(c, "type"))) continue;
            String t = findType(e, c, null, depth + 1);
            if (t != null) return t;
        }
        return null;
    }

    static String slotRu(String name) {
        if (name == null) return "";
        String n = name;
        String[][] map = {
                {"Prime High Command", "Прайм: Верх. командование"}, {"High Command", "Верховное командование"},
                {"Prime Command", "Прайм: Командование"}, {"Command", "Командование"},
                {"Prime Troops", "Прайм: Войска"}, {"Troops", "Войска"},
                {"Prime Elites", "Прайм: Элита"}, {"Elites", "Элита"},
                {"Prime Retinue", "Прайм: Свита"}, {"Retinue", "Свита"},
                {"Heavy Transport", "Тяжёлый транспорт"}, {"Transport", "Транспорт"},
                {"Heavy Assault", "Тяжёлый штурм"}, {"Support", "Поддержка"},
                {"Armour", "Бронетехника"}, {"War-engine", "Боевые машины"},
                {"Fast Attack", "Быстрая атака"}, {"Recon", "Разведка"},
                {"Lord of War", "Лорд войны"}, {"Warlord", "Военачальник"},
        };
        for (String[] m : map) if (n.startsWith(m[0])) return m[1] + n.substring(m[0].length());
        return n;
    }

    /** Тип детачмента по-русски. */
    static String detachmentType(String name) {
        String n = name.toLowerCase();
        if (n.contains("primary")) return "Основной детачмент — ядро армии";
        if (n.startsWith("auxiliary")) return "Вспомогательный — расширяет армию";
        if (n.startsWith("apex")) return "Апекс — элитное усиление";
        if (n.contains("allied")) return "Союзники другой фракции";
        if (n.contains("lord of war")) return "Лорды войны и гиганты";
        if (n.contains("warlord")) return "Детачмент военачальника";
        if (n.contains("cohorts vagus")) return "Странствующие когорты";
        if (n.contains("zone mortalis")) return "Бой в тесных коридорах";
        return null;
    }

    /** Главный значок детачмента: самый ёмкий слот. */
    static String detachmentGlyph(String name, List<CatSlot> slots) {
        String n = name.toLowerCase();
        if (n.contains("primary")) return "banner";
        if (n.contains("allied")) return "shield";
        if (n.contains("lord of war") || n.contains("warlord")) return "crown";
        if (n.contains("armour") || n.contains("armored") || n.contains("armoured") || n.contains("tank") || n.contains("leviathan")) return "tank";
        if (n.contains("heavy support") || n.contains("artillery") || n.contains("bastion")) return "cannon";
        if (n.contains("shock") || n.contains("assault") || n.contains("linebreaker") || n.contains("breach")) return "fist";
        if (n.contains("first strike") || n.contains("vanguard") || n.contains("scout") || n.contains("recon")) return "eye";
        if (n.contains("officer") || n.contains("command") || n.contains("cadre") || n.contains("retinue")) return "crown";
        if (n.contains("tactical") || n.contains("company") || n.contains("infantry")) return "infantry";
        if (n.contains("pioneer") || n.contains("techmarine") || n.contains("forge")) return "fort";
        if (n.contains("wing") || n.contains("air") || n.contains("strike")) return "wing";
        if (n.contains("psy") || n.contains("librar") || n.contains("revelation")) return "psyker";
        CatSlot best = null;
        for (CatSlot s : slots) {
            if (s.hidden || s.max == 0) continue;
            if (best == null || s.max > best.max) best = s;
        }
        return best == null ? "diamond" : Glyph.forCategory(best.name);
    }

    /** «Даёт: Бронетехника ×2, Транспорт ×1». */
    static String slotSummary(List<CatSlot> slots) {
        StringBuilder b = new StringBuilder();
        int shown = 0;
        for (CatSlot s : slots) {
            if (s.hidden || s.max <= 0) continue;
            if (shown++ >= 4) { b.append(" …"); break; }
            if (b.length() > 0) b.append(", ");
            b.append(slotRu(s.name)).append(" ×").append(Engine.fmt(s.max));
        }
        return b.length() == 0 ? null : "Слоты: " + b;
    }

    /** Короткое имя группы опций: «This Model may have its bolt pistol exchanged for:» → «Замена: bolt pistol». */
    static String groupName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll(":+$", "").trim();
        String l = s.toLowerCase();
        String prefix = "";
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("^(\\d+\\s*(?:-|in)\\s*\\d+)").matcher(l);
        if (pm.find()) prefix = " (" + pm.group(1).replace("in", "из").replaceAll("\\s+", "") + ")";
        // «may have its X exchanged for»
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(?:its|their)\\s+(.+?)\\s+exchanged").matcher(s);
        if (m1.find()) return "Замена: " + m1.group(1).trim() + prefix;
        // «exchange (its|their) X for»
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?i)exchange\\s+(?:its\\s+|their\\s+)?(.+?)\\s+for").matcher(s);
        if (m2.find()) return "Замена: " + m2.group(1).trim() + prefix;
        if (l.contains("exchange")) return "Замена снаряжения" + prefix;
        if (l.contains("selected for it") || l.startsWith("may take") || l.contains("may take") || l.contains("one of the following")) return "Дополнительно" + prefix;
        if (l.startsWith("this model may") || l.startsWith("may ")) return "Опции" + prefix;
        if (s.length() > 40) s = s.substring(0, 38) + "…";
        return s;
    }

}
