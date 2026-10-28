package com.medoedy.liber;

import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.Issue;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Превращает сухие ошибки проверки в понятные объяснения:
 * что не так, почему, и что нажать, чтобы исправить. Термины — в оригинале.
 */
final class Problems {
    private Problems() { }

    static final int NONE = 0, GO_FORCE = 1, OPEN_UNIT = 2, ADD_UNIT = 3, ADD_DET = 4, EDIT_LIMIT = 5;

    static final class P {
        Issue src;
        boolean error;
        String title, detail, fix;
        String button, button2;
        int act, act2;
        Force force;   // куда вести
        Sel unit;      // какой юнит открыть
        String group;  // заголовок группы: где находится проблема
    }

    static String f(double v) { return Engine.fmt(v); }

    static String slot(String en) {
        String ru = Units.slotRu(en);
        return ru.equals(en) ? "«" + en + "»" : "«" + en + "» (" + ru + ")";
    }

    static String pts(double v) { return f(v) + " " + MainActivity.plural((int) Math.round(v), "очко", "очка", "очков"); }

    static P explain(Engine e, Issue i, double limit) {
        P p = new P();
        p.src = i;
        p.error = i.error;
        if (i.where instanceof Force) p.force = (Force) i.where;
        if (i.where instanceof Sel) { p.unit = (Sel) i.where; p.force = p.unit.force; }
        switch (i.kind) {
            case Issue.LIMIT: {
                double over = i.n - i.v;
                p.title = "Превышен лимит очков";
                p.detail = "Армия стоит " + pts(i.n) + " при лимите " + f(i.v) + " — лишних " + pts(over) + ".";
                p.fix = "Уберите юниты или снаряжение на " + pts(over) + " — или поднимите лимит ростера, если играете на большую игру.";
                p.button = "Изменить лимит";
                p.act = EDIT_LIMIT;
                p.force = null;
                break;
            }
            case Issue.FORCE_COST: {
                String cost = i.extra == null ? "" : i.extra;
                boolean points = cost.toLowerCase().startsWith("point");
                boolean allied = i.subject != null && i.subject.toLowerCase().contains("allied");
                if (points && allied) {
                    double over = i.n - i.v;
                    p.title = "Союзники дороже половины армии";
                    p.detail = "Allied Detachment и его Auxiliary стоят " + pts(i.n) + ", а союзникам можно не больше "
                            + pts(i.v) + (limit > 0 ? " — это половина лимита ростера (" + f(limit) + ")." : ".");
                    p.fix = "Уберите из союзников юниты на " + pts(over) + " или поднимите лимит ростера.";
                    p.button = "Показать союзников";
                    p.act = GO_FORCE;
                    p.button2 = "Изменить лимит";
                    p.act2 = EDIT_LIMIT;
                } else if (cost.startsWith("Auxiliary Detachment")) {
                    p.title = "Слишком много Auxiliary Detachment";
                    p.detail = "В армии " + f(i.n) + " Auxiliary Detachment, а открыто только " + f(i.v) + ".";
                    p.fix = "Каждый Auxiliary Detachment «открывает» командир в Primary Detachment: каждый юнит слота Command даёт +1. "
                            + "У юнита High Command можно выбрать опцию «+1 Auxiliary from High Command». "
                            + "Добавьте командира или распустите лишний Auxiliary (✕ на его карточке).";
                    p.button = "К Primary Detachment";
                    p.act = GO_FORCE;
                    p.force = primary(e, p.force);
                } else if (cost.startsWith("Apex Detachment")) {
                    p.title = "Слишком много Apex Detachment";
                    p.detail = "В армии " + f(i.n) + " Apex Detachment, а открыто только " + f(i.v) + ".";
                    p.fix = "Apex Detachment открывает юнит High Command в Primary Detachment с опцией «+1 Apex from High Command». "
                            + "Выберите её у Praetor/Primarch или распустите лишний Apex.";
                    p.button = "К Primary Detachment";
                    p.act = GO_FORCE;
                    p.force = primary(e, p.force);
                } else if (cost.startsWith("Combined Warlord")) {
                    p.title = "Слишком много очков в Warlord и Lord of War";
                    p.detail = "Warlord и Lord of War вместе стоят " + pts(i.n) + ", а можно " + pts(i.v) + ".";
                    p.fix = "Уберите часть юнитов из Warlord / Lord of War Detachment или поднимите лимит ростера.";
                    p.button = "Перейти";
                    p.act = GO_FORCE;
                } else {
                    p.title = "«" + i.subject + "»: превышено " + cost;
                    p.detail = "Сейчас " + f(i.n) + ", максимум " + f(i.v) + ".";
                    p.fix = "Уберите часть юнитов из этой силы.";
                    p.button = "Перейти";
                    p.act = GO_FORCE;
                }
                break;
            }
            case Issue.FORCE_MAX:
                p.title = "Слишком много «" + i.subject + "»";
                p.detail = "В ростере " + f(i.n) + ", а такой детачмент можно взять не больше " + f(i.v) + ".";
                p.fix = "Распустите лишний — кнопка ✕ на его карточке.";
                p.button = "Перейти";
                p.act = GO_FORCE;
                break;
            case Issue.FORCE_MIN:
                p.title = "Нужно больше «" + i.subject + "»";
                p.detail = "Требуется минимум " + f(i.v) + ", сейчас " + f(i.n) + ".";
                p.fix = "Добавьте этот детачмент кнопкой «＋ Детачмент».";
                p.button = "Добавить детачмент";
                p.act = ADD_DET;
                break;
            case Issue.NEED_DET:
                p.title = "Не хватает обязательного «" + i.subject + "»";
                p.detail = "В «" + i.extra + "» обязательно должен быть " + i.subject + ".";
                p.fix = "Нажмите «＋ Детачмент» под этой армией и выберите «" + i.subject + "».";
                p.button = "Добавить детачмент";
                p.act = ADD_DET;
                break;
            case Issue.SLOT_OVER:
                p.title = "Переполнен слот " + slot(i.subject);
                p.detail = "В «" + i.extra + "» занято " + f(i.n) + " из " + f(i.v) + ".";
                p.fix = "Удалите лишний юнит из этого слота (долгое нажатие на юнит → Удалить) или возьмите детачмент, где есть свободный слот " + slot(i.subject) + ".";
                p.button = "Перейти к детачменту";
                p.act = GO_FORCE;
                break;
            case Issue.SLOT_MIN:
                p.title = "Не заполнен обязательный слот " + slot(i.subject);
                p.detail = "В «" + i.extra + "» нужно минимум " + f(i.v) + ", сейчас " + f(i.n) + ".";
                p.fix = "Добавьте юнит в слот " + slot(i.subject) + ".";
                p.button = "＋ Юнит";
                p.act = ADD_UNIT;
                break;
            case Issue.FORCE_RULE:
            case Issue.UNIT_RULE: {
                String ru = rule(i.extra);
                p.title = ru != null ? ru : i.extra;
                p.detail = (ru != null ? "Правило данных: «" + i.extra + "»" : "Сообщение из данных BSData") + " — для «" + i.subject + "».";
                p.fix = ruleFix(i.extra);
                if (p.unit != null) { p.button = "Открыть юнит"; p.act = OPEN_UNIT; }
                else { p.button = "Перейти"; p.act = GO_FORCE; }
                break;
            }
            case Issue.UNIT_COST:
                p.title = "«" + i.subject + "»: превышено " + i.extra;
                p.detail = "Сейчас " + f(i.n) + ", максимум " + f(i.v) + ".";
                p.fix = "Откройте юнит и уберите часть снаряжения.";
                p.button = "Открыть юнит";
                p.act = OPEN_UNIT;
                break;
            case Issue.OPT_MAX:
            case Issue.LB_EMPTY: {
                p.title = "«" + i.subject + "»: Logistical Benefit без слота";
                p.detail = "Logistical Benefit добавляет детачменту один дополнительный слот (любой, кроме High Command, Command, Warlord и Lord of War) — но тип слота не выбран, так что выгода ничего не даёт. Её можно взять только один раз на детачмент.";
                p.fix = "Откройте юнит и в Prime Unit → Logistical Benefit выберите слот (LB - Armour, LB - War-engine…) — или возьмите другую Prime-выгоду.";
                p.button = "Открыть юнит";
                p.act = OPEN_UNIT;
                p.unit = unitRoot(p.unit);
                break;
            }
            case Issue.LB_UNUSED: {
                p.title = "Пустой слот " + i.extra + " от Logistical Benefit";
                p.detail = "«" + i.subject + "» взял Logistical Benefit на слот " + i.extra + ", но в детачменте занято " + f(i.n) + " из " + f(i.v) + " таких слотов — дополнительный слот пропадает.";
                p.fix = "Добавьте юнит " + i.extra + " в этот детачмент или смените Prime-выгоду на другую.";
                p.button = "Добавить юнит";
                p.act = ADD_UNIT;
                p.button2 = "Открыть юнит";
                p.act2 = OPEN_UNIT;
                p.unit = unitRoot(p.unit);
                break;
            }
            case Issue.OPT_MIN: {
                boolean max = i.kind == Issue.OPT_MAX;
                String sc = i.scope;
                if (i.extra != null) {
                    // опция внутри юнита
                    p.title = max ? "«" + i.subject + "»: слишком много в «" + i.extra + "»"
                            : "В «" + i.extra + "» не выбрано обязательное «" + i.subject + "»";
                    p.detail = (max ? "Можно не больше " : "Нужно минимум ") + f(i.v) + ", выбрано " + f(i.n) + ".";
                    p.fix = max ? "Откройте юнит и уберите лишнее «" + i.subject + "»." : "Откройте юнит и выберите «" + i.subject + "».";
                    p.button = "Открыть юнит";
                    p.act = OPEN_UNIT;
                    p.unit = unitRoot(p.unit);
                } else if ("roster".equals(sc)) {
                    p.title = max ? "«" + i.subject + "» — не больше " + f(i.v) + " на ростер" : "В ростере обязателен «" + i.subject + "»";
                    p.detail = max ? "Сейчас в ростере " + f(i.n) + "." : "Нужно минимум " + f(i.v) + ", сейчас " + f(i.n) + ".";
                    p.fix = max ? "Удалите лишний экземпляр (долгое нажатие на юнит → Удалить)." : "Добавьте «" + i.subject + "» в ростер.";
                    Sel s = findByName(e, i.subject);
                    if (s != null && max) { p.unit = s; p.button = "Показать"; p.act = GO_FORCE; p.force = s.force; }
                    else if (!max) { p.button = "＋ Юнит"; p.act = ADD_UNIT; }
                } else {
                    p.title = max ? "«" + i.subject + "» — не больше " + f(i.v) + " в детачменте" : "В детачменте обязателен «" + i.subject + "»";
                    p.detail = max ? "Сейчас " + f(i.n) + "." : "Нужно минимум " + f(i.v) + ", сейчас " + f(i.n) + ".";
                    p.fix = max ? "Удалите лишний или перенесите его в другой детачмент." : "Добавьте «" + i.subject + "» в этот детачмент.";
                    p.button = max ? "Перейти" : "＋ Юнит";
                    p.act = max ? GO_FORCE : ADD_UNIT;
                }
                break;
            }
            default:
                p.title = i.text;
                p.detail = null;
                p.fix = null;
        }
        p.group = groupOf(e, p);
        return p;
    }

    static String groupOf(Engine e, P p) {
        if (p.src.kind == Issue.LIMIT) return "Весь ростер";
        Force f = p.force;
        if (f == null) return "Весь ростер";
        return e.effForce(f).name;
    }

    static Sel unitRoot(Sel s) {
        while (s != null && s.parent != null) s = s.parent;
        return s;
    }

    static Force primary(Engine e, Force around) {
        Force root = around;
        while (root != null && root.parent != null) root = root.parent;
        if (root == null) return around;
        for (Force ch : root.children) if (e.effForce(ch).name.toLowerCase().contains("primary")) return ch;
        return around;
    }

    static Sel findByName(Engine e, String name) {
        List<Force> all = new ArrayList<Force>();
        e.r.allForces(all);
        for (Force f : all) for (Sel s : f.sels) if (e.effSel(s).name.equals(name)) return s;
        return null;
    }

    // ================================================================ перевод правил данных
    static final Map<String, String[]> RULES = new HashMap<String, String[]>();

    static void put(String en, String ru, String fix) { RULES.put(en, new String[]{ru, fix}); }

    static {
        put("All Models in a Detachment with the Broken Helix Oath of Moment must have the same Special Rule selected for them",
                "Broken Helix: у всех моделей детачмента должно быть одно и то же особое правило", "Откройте юниты детачмента и выберите всем одинаковое правило.");
        put("Berserker Cadre only allowed with Legiones Astartes Rite of War", "Berserker Cadre — только с Rite of War «Legiones Astartes»", "Смените Rite of War в «Армии» или распустите детачмент.");
        put("Brotherhood of the Phoenix is only valid for Emperor's Children", "Brotherhood of the Phoenix — только для Emperor's Children", "Распустите этот детачмент.");
        put("Brotherhood of the Phoenix only allowed with Legiones Hereticus Rite of War", "Brotherhood of the Phoenix — только с Rite of War «Legiones Hereticus»", "Смените Rite of War в «Армии».");
        put("Can only be taken if Rogal Dorn is included in the Army", "Можно взять, только если в армии есть Rogal Dorn", "Добавьте Rogal Dorn или уберите этот выбор.");
        put("Demonic Manifestation Detachment requires an Esoterist", "Daemonic Manifestation требует Esoterist", "Добавьте Esoterist в армию.");
        put("Demonic Manifestation Detachment requires an Rogue Psyker", "Daemonic Manifestation требует Rogue Psyker", "Добавьте Rogue Psyker в армию.");
        put("Demonic Rapturous Detachment requires an Esoterist", "Demonic Rapturous требует Esoterist", "Добавьте Esoterist в армию.");
        put("Dreadnought Talon requires a Mortificator", "Dreadnought Talon требует Mortificator", "Добавьте Mortificator (Centurion с этой специализацией).");
        put("Each Antemasque Detachment requires an Infantry Tercio detachment", "Каждый Antemasque требует Infantry Tercio", "Добавьте Infantry Tercio.");
        put("Each Helot Tercio requires a Veletaris Tercio detachment", "Каждый Helot Tercio требует Veletaris Tercio", "Добавьте Veletaris Tercio.");
        put("Each Ætheric Incursion Detachment requires an Infantry Tercio detachment", "Каждый Ætheric Incursion требует Infantry Tercio", "Добавьте Infantry Tercio.");
        put("Existing Warlord Detachment must be removed", "Сначала уберите уже взятый Warlord Detachment", "Распустите существующий Warlord Detachment.");
        put("Flyers and Vehicles with 3 hull points or more are not allowed in games of Zone Mortalis", "В Zone Mortalis нельзя Flyer и технику с 3+ Hull Points", "Удалите такие юниты.");
        put("Legiones Hereticus can only be selected for an Army with the Traitor Allegiance", "Legiones Hereticus — только для армии с Allegiance «Traitor»", "Смените Allegiance на Traitor или выберите другой Rite of War.");
        put("Maelstrom Sentry Battery requires a Master of Signals", "Maelstrom Sentry Battery требует Master of Signals", "Добавьте Master of Signals.");
        put("Maximum Points limit for Cohorts Vagus Primary is 1500", "Cohorts Vagus Primary — не больше 1500 очков", "Уберите юниты или уменьшите лимит.");
        put("Multiple Mechanicum Traits in Detachment", "В детачменте больше одного Mechanicum Trait", "Оставьте только один Trait (Archmagos/Magos → Trait).");
        put("Multiple Skitarii Traits in Detachment", "В детачменте больше одного Skitarii Trait", "Оставьте только один Trait.");
        put("Multiple Ætheric Dominions in Army", "В армии больше одного Ætheric Dominion", "Оставьте один Ætheric Dominion.");
        put("Multiple Ætheric Dominions in Detachment", "В детачменте больше одного Ætheric Dominion", "Оставьте один Ætheric Dominion.");
        put("Only one Delegatus allowed in Army", "В армии может быть только один Delegatus", "Уберите опцию Delegatus у всех командиров, кроме одного.");
        put("Please use the \"Knight Lord of War\" Detachment instead", "Используйте детачмент «Knight Lord of War»", "Распустите этот детачмент и возьмите Knight Lord of War.");
        put("Primacy Wing only allowed with Legiones Astartes Rite of War", "Primacy Wing — только с Rite of War «Legiones Astartes»", "Смените Rite of War в «Армии».");
        put("Primary force must be Mechanicum", "Основная армия должна быть Mechanicum", "Этот выбор доступен только армии Mechanicum.");
        put("Questoris Household Armies may not contain Allied Detachments", "Армия Questoris Household не может брать Allied Detachment", "Распустите союзников.");
        put("Recon Demi-Company requires a Vigilator", "Recon Demi-Company требует Vigilator", "Добавьте Vigilator (Centurion с этой специализацией).");
        put("Roster must be at least 3000 points for Brotherhood of the Phoenix", "Brotherhood of the Phoenix — только при лимите от 3000 очков", "Поднимите лимит ростера.");
        put("Sons of Bodt only allowed with Legiones Hereticus Rite of War", "Sons of Bodt — только с Rite of War «Legiones Hereticus»", "Смените Rite of War в «Армии».");
        put("Storm Battery Detachment requires a Siege Breaker", "Storm Battery требует Siege Breaker", "Добавьте Siege Breaker (Centurion с этой специализацией).");
        put("The Prime Advantage selected must be the same for all Units that are selected as part of a Cohorts Vagus detachment",
                "Prime Advantage должен быть одинаковым у всех юнитов Cohorts Vagus", "Выберите всем юнитам один Prime Advantage.");
        put("Veteran Cadre requires Legion Champion", "Veteran Cadre требует Legion Champion", "Добавьте Legion Champion.");
        put("You have exceeded your Asset Point limit", "Превышен лимит Asset Points", "Уберите часть Battlefield Assets.");
        put("Ætheric Incursion requires an Esoterist or a Prosperine Sorcerer", "Ætheric Incursion требует Esoterist или Prosperine Sorcerer", "Добавьте одного из них.");
        put("Armoured Command Squadron Detachment is only legal in Armoured Spearhead Missions", "Armoured Command Squadron — только в миссиях Armoured Spearhead", "Это предупреждение: в обычной игре детачмент не разрешён.");
        put("Daemonic alignment mismatch", "Не совпадает демоническое выравнивание (alignment)", "Выберите всем демоническим юнитам одно выравнивание.");
        put("Leviathan Armoured Fist Detachment is only legal in Leviathan Missions", "Leviathan Armoured Fist — только в миссиях Leviathan", "Это предупреждение для обычной игры.");
        put("Leviathan Bastion of Fire Detachment is only legal in Leviathan Missions", "Leviathan Bastion of Fire — только в миссиях Leviathan", "Это предупреждение для обычной игры.");
        put("Warlord requires an Army of at least 3000 points", "Warlord — только в армии от 3000 очков", "Поднимите лимит ростера до 3000 или уберите Warlord.");
    }

    static String rule(String en) {
        if (en == null) return null;
        String[] r = RULES.get(en.trim());
        return r == null ? null : r[0];
    }

    static String ruleFix(String en) {
        String[] r = en == null ? null : RULES.get(en.trim());
        return r == null ? "Проверьте выбор, к которому относится это правило." : r[1];
    }
}
