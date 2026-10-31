package com.medoedy.liber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Миссии 3-й редакции: основной набор из книги правил, Engine Kill (Liber Questoris),
 * Onslaught и легендарная миссия из Journal Tactica: The Forges of Saturn, легендарная миссия
 * The First Wave (Isstvan V, часть 1). Только игровая механика, описания — своими словами.
 */
final class Missions {
    static final class Objective {
        String name;
        int vp;
        boolean decays; // «окно возможностей»: ценность падает на 1 после каждого начисления
        Objective(String name, int vp, boolean decays) { this.name = name; this.vp = vp; this.decays = decays; }
    }

    static final class Secondary {
        String name, desc;
        int vp;
        Secondary(String name, int vp, String desc) { this.name = name; this.vp = vp; this.desc = desc; }
    }

    /** Особое начисление очков (кнопка «+N»): Engine Kill, Slaughter the Foe, Vanguard Защитника… */
    static final class Event {
        String name, desc;
        int vp;
        int side; // 0 — любой игрок, 1 — только Атакующий, 2 — только Защитник
        Event(String name, int vp, int side, String desc) { this.name = name; this.vp = vp; this.side = side; this.desc = desc; }
    }

    static final class Mission {
        String name, ru, desc;
        String pack = "Основные миссии";
        String book, map, note; // книга для 📖, своя карта развёртывания, особенности подготовки
        String mapId;           // идентификатор схемы развёртывания для MapView (warofgods, giantkiller, line, breakthrough, dawn, ignis, firstwave)
        int page = -1;          // страница в PDF (с 0)
        int turns = 4;
        boolean asym;           // есть Атакующий и Защитник
        boolean objAttacker;    // маркеры набирает только Атакующий
        boolean advancing;      // Advancing Objectives (Onslaught)
        boolean extraTurn;      // после 4-го хода бросок 4+ → 5-й ход
        final List<Objective> objectives = new ArrayList<Objective>();
        final List<Secondary> secondaries = new ArrayList<Secondary>();
        final List<String> rules = new ArrayList<String>();
        final List<Event> events = new ArrayList<Event>();
        boolean custom;
    }

    static final String[][] DEPLOYMENTS = {
            {"Search and Destroy", "Поиск и уничтожение", "Зоны в противоположных четвертях стола, по диагонали."},
            {"Hammer and Anvil", "Молот и наковальня", "Зоны вдоль коротких краёв стола, большая дистанция."},
            {"Dawn of War", "Рассвет войны", "Зоны вдоль длинных краёв стола, классическая линия фронта."},
    };

    static Secondary slay(int vp) { return new Secondary("Slay the Warlord", vp, "Снят как потеря вражеский юнит High Command (один раз за битву)."); }
    static Secondary giant(int vp) { return new Secondary("Giant Killer", vp, "Снят как потеря вражеский Lord of War или Warlord (один раз за битву)."); }
    static Secondary last(int vp) { return new Secondary("Last Man Standing", vp, "В конце битвы у вас больше юнитов без статуса Routed, чем у противника. Не засчитывается при внезапной смерти."); }
    static Secondary first(int vp) { return new Secondary("First Strike", vp, "В свой первый ход вы полностью уничтожили хотя бы один вражеский юнит (ушедшие за край стола тоже считаются)."); }

    static List<Mission> all() {
        List<Mission> l = core();
        l.addAll(engineKill());
        l.addAll(onslaught());
        l.addAll(legendary());
        return l;
    }

    static List<Mission> core() {
        List<Mission> l = new ArrayList<Mission>();
        Mission a = new Mission();
        a.name = "The Heart of Battle";
        a.ru = "Сердце битвы";
        a.desc = "Три маркера: ценный в центре и два второстепенных — не ближе 12\" к центру и 6\" к краям. Борьба за середину стола решает исход.";
        a.book = "rulebook"; a.page = 312;
        a.objectives.add(new Objective("Центр", 3, false));
        a.objectives.add(new Objective("Фланг А", 1, false));
        a.objectives.add(new Objective("Фланг Б", 1, false));
        a.secondaries.add(slay(3)); a.secondaries.add(giant(3)); a.secondaries.add(last(3)); a.secondaries.add(first(3));
        a.rules.add("Reserves"); a.rules.add("Counter Offensive"); a.rules.add("Seize the Initiative");
        l.add(a);

        Mission b = new Mission();
        b.name = "The Crucible of War";
        b.ru = "Горнило войны";
        b.desc = "Четыре равноценных маркера в середине поля: не ближе 12\" к краям и друг к другу. Ранний удар здесь особенно ценен.";
        b.book = "rulebook"; b.page = 313;
        for (int i = 1; i <= 4; i++) b.objectives.add(new Objective("Маркер " + i, 2, false));
        b.secondaries.add(slay(2)); b.secondaries.add(giant(2)); b.secondaries.add(last(2)); b.secondaries.add(first(4));
        b.rules.add("Reserves"); b.rules.add("Counter Offensive"); b.rules.add("Seize the Initiative");
        l.add(b);

        Mission c = new Mission();
        c.name = "Take and Hold";
        c.ru = "Захватить и удержать";
        c.desc = "Два ценных маркера в центре, не ближе 18\" друг к другу. Каждый раз, когда маркер приносит очки, его ценность падает на 1 (окно возможностей).";
        c.book = "rulebook"; c.page = 314;
        c.objectives.add(new Objective("Маркер 1", 3, true));
        c.objectives.add(new Objective("Маркер 2", 3, true));
        c.secondaries.add(slay(2)); c.secondaries.add(giant(4)); c.secondaries.add(last(4)); c.secondaries.add(first(2));
        c.rules.add("Reserves"); c.rules.add("Counter Offensive"); c.rules.add("Seize the Initiative"); c.rules.add("Window of Opportunity");
        l.add(c);
        return l;
    }

    static void engineEvents(Mission m, int strategicSide, int killSide) {
        m.events.add(new Event("Engine Kill: Scout", 8, killSide, "Снят как потеря вражеский Титан класса Scout."));
        m.events.add(new Event("Engine Kill: Light Battle", 10, killSide, "Снят вражеский Титан класса Light Battle."));
        m.events.add(new Event("Engine Kill: Battle", 12, killSide, "Снят вражеский Титан класса Battle."));
        m.events.add(new Event("Engine Kill: Heavy Battle", 15, killSide, "Снят вражеский Титан класса Heavy Battle."));
        m.events.add(new Event("Macro Emplacement: −5 HP", 5, strategicSide, "Стратегическая цель (AV 15, HP ∞): +5 ПО за каждые 5 Hull Points, снятые за ход, но не больше 15 ПО за ход."));
        m.events.add(new Event("Communications Bunker разрушен", 10, strategicSide, "AV 14, 20 HP. При разрушении — 10 ПО, а противник до конца битвы проходит Reserves только на 6+."));
        m.events.add(new Event("Command Post разрушен", 10, strategicSide, "AV 14, 20 HP. При разрушении — 10 ПО, и каждый не связанный боем юнит противника проходит Cool Check или получает Suppressed."));
    }

    static List<Mission> engineKill() {
        List<Mission> l = new ArrayList<Mission>();
        Mission a = new Mission();
        a.pack = "Engine Kill · Liber Questoris";
        a.name = "War of Gods";
        a.ru = "Война богов";
        a.desc = "Два Легио Титаникус сходятся в бою: каждый выставляет Titan Maniple Detachment вместо Primary, остальные очки — на союзников. Три маркера по 2 ПО, очки за сбитых Титанов и стратегические цели.";
        a.book = "questoris"; a.page = 91;
        a.map = "Стол 72″×48″. Зоны сторон A и B — треугольники в противоположных углах: диагональ от точки в 36″ дальнего края до ближнего угла. Три маркера по 2 ПО — колонна по центру с шагом 12″.";
        a.mapId = "warofgods";
        a.note = "Бросок кубика за миссию: 1–3 War of Gods, 4–6 Giant Killer. 3000 очков. Титаны не идут в резерв и выставляются до остальной армии. Если Титаны есть у обоих — первым ходит тот, кто первым развернулся.";
        for (int i = 1; i <= 3; i++) a.objectives.add(new Objective("Маркер " + i, 2, false));
        a.secondaries.add(last(3)); a.secondaries.add(first(1));
        a.rules.add("Reserves"); a.rules.add("Seize the Initiative"); a.rules.add("Dynamic Uplink"); a.rules.add("Sudden Death");
        engineEvents(a, 0, 0);
        l.add(a);

        Mission b = new Mission();
        b.pack = a.pack;
        b.name = "Giant Killer";
        b.ru = "Убийца гигантов";
        b.desc = "Одинокий боевой Титан Защитника рвётся вперёд, Атакующий должен его свалить. Маркеры (3, 2, 2) и Engine Kill набирает только Атакующий, стратегические цели — только Защитник.";
        b.book = "questoris"; b.page = 92;
        b.asym = true; b.objAttacker = true;
        b.map = "Стол 72″×48″. Зона Защитника — полоса 24″ у его короткого края, Атакующий занимает остальное. Маркеры 2·3·2 — колонна в 18″ от зоны Защитника, шаг 12″.";
        b.mapId = "giantkiller";
        b.note = "Защитник берёт Titan Maniple Detachment вместо Primary, Атакующий — обычную армию. Атакующий всегда ходит первым.";
        b.objectives.add(new Objective("Маркер «3»", 3, false));
        b.objectives.add(new Objective("Маркер «2» А", 2, false));
        b.objectives.add(new Objective("Маркер «2» Б", 2, false));
        b.secondaries.add(last(2)); b.secondaries.add(first(1));
        b.rules.add("Reserves"); b.rules.add("Dynamic Uplink"); b.rules.add("Sudden Death");
        engineEvents(b, 2, 1);
        l.add(b);
        return l;
    }

    static void onslaughtCommon(Mission m) {
        m.pack = "Onslaught · The Forges of Saturn";
        m.book = "saturn";
        m.asym = true; m.objAttacker = true; m.advancing = true;
        for (int i = 1; i <= 4; i++) m.objectives.add(new Objective("Маркер " + i, 1, false));
        m.secondaries.add(slay(2)); m.secondaries.add(giant(1)); m.secondaries.add(last(2)); m.secondaries.add(first(1));
        m.note = "Террейн как в основных миссиях, но первым ставит Защитник, а в конце он может убрать один элемент целиком в половине Атакующего. Маркеры набирает только Атакующий.";
        m.events.add(new Event("Маркер не у Атакующего", 1, 2, "В фазе победы Защитника: +1 ПО за каждый маркер, который Атакующий не контролирует."));
        m.events.add(new Event("Vanguard (2) Защитника", 2, 2, "Юнит Защитника уничтожил последний вражеский юнит у маркера или заставил отступить из боя юнит, бывший в 3\" от маркера."));
    }

    static List<Mission> onslaught() {
        List<Mission> l = new ArrayList<Mission>();
        Mission a = new Mission();
        onslaughtCommon(a);
        a.name = "Line Advance";
        a.ru = "Наступление по линии";
        a.desc = "Атакующий продавливает вражескую линию: четыре маркера по 1 ПО продвигаются к краю Защитника и дорожают до 3.";
        a.page = 42;
        a.map = "Стол 72″×48″, сетка секторов 4×4 (18″×12″). Зона Атакующего — нижний ряд секторов (12″ у его края). Четыре маркера по 1 ПО — в соседнем ряду, продвигаются к краю Защитника и дорожают до 3.";
        a.mapId = "line";
        a.rules.add("Advancing Objectives"); a.rules.add("Battlefield Assets"); a.rules.add("Reserves"); a.rules.add("Seize the Initiative"); a.rules.add("Sudden Death");
        l.add(a);

        Mission b = new Mission();
        onslaughtCommon(b);
        b.name = "Breakthrough";
        b.ru = "Прорыв";
        b.desc = "Атакующий продвигает центральные маркеры, остерегаясь окружения: резервы Защитника выходят и с коротких краёв.";
        b.page = 43;
        b.map = "Стол 72″×48″, сетка 4×4, зона Атакующего — 12″ у его края. Начальные маркеры: фланговые — в ряду у зоны, центральные — на ряд выше; продвигаются к краю Защитника. Резервы Защитника входят и с коротких краёв.";
        b.mapId = "breakthrough";
        b.rules.add("Advancing Objectives"); b.rules.add("Battlefield Assets"); b.rules.add("Reserves"); b.rules.add("Seize the Initiative"); b.rules.add("Encircling Reinforcements"); b.rules.add("Sudden Death");
        l.add(b);

        Mission c = new Mission();
        onslaughtCommon(c);
        c.name = "Dawn Raid";
        c.ru = "Рассветный налёт";
        c.desc = "Удар на рассвете: Защитник развёртывается первым без броска, Атакующий может перехватить первый ход на 4+, а весь первый ход стрельба только Snap Shots.";
        c.page = 44;
        c.map = "Стол 72″×48″, сетка 4×4. Зона Атакующего — треугольник у его края: диагональ от его дальнего угла до середины бокового края (24″). Четыре маркера по 1 ПО — в двух рядах у края Защитника, продвигаются к нему.";
        c.mapId = "dawn";
        c.rules.add("Advancing Objectives"); c.rules.add("Battlefield Assets"); c.rules.add("Reserves"); c.rules.add("Surprise Attack"); c.rules.add("Sudden Death");
        l.add(c);
        return l;
    }

    static List<Mission> legendary() {
        List<Mission> l = new ArrayList<Mission>();
        Mission a = new Mission();
        a.pack = "Легендарные миссии";
        a.name = "Ignis Sector Assault";
        a.ru = "Штурм сектора Игнис";
        a.desc = "Isstvan V: Саламандры во главе с сатурнинскими терминаторами штурмуют позиции Гвардии Смерти. Центральный маркер 3 ПО и два по 2 ПО.";
        a.book = "saturn"; a.page = 33;
        a.asym = true; a.extraTurn = true;
        a.map = "Стол 72″×48″. Зоны по 12″ у длинных краёв. Два сектора Spearhead — треугольники с основаниями у краёв стола (от 12″ до 36″) и вершинами в центре: туда в первый ход выходит сатурнинская волна. Маркеры: центральный 3 ПО в центре стола, по 2 ПО — в 18″ от боковых краёв на высоте 18″.";
        a.mapId = "ignis";
        a.note = "Атакующий — Loyalist, Защитник — Traitor. Защитник развёртывается первым, Атакующий ходит первым. В половине Защитника — укрепления и окопы, у Атакующего — открытое поле с обломками. После 4-го хода Защитник бросает D6: на 4+ играется 5-й ход.";
        a.objectives.add(new Objective("Центр", 3, false));
        a.objectives.add(new Objective("Маркер «2» А", 2, false));
        a.objectives.add(new Objective("Маркер «2» Б", 2, false));
        a.secondaries.add(slay(3)); a.secondaries.add(giant(2)); a.secondaries.add(last(3)); a.secondaries.add(first(2));
        a.rules.add("Spearhead Sector"); a.rules.add("Preliminary Bombardment"); a.rules.add("Second Wave"); a.rules.add("Battlefield Assets"); a.rules.add("Sudden Death");
        l.add(a);

        Mission b = new Mission();
        b.pack = a.pack;
        b.name = "The First Wave";
        b.ru = "Первая волна";
        b.desc = "Isstvan V: авангард лоялистов высаживается на плацдарм. Атакующий захватывает четыре маркера по 2 ПО, Защитник получает 2 ПО за каждый уничтоженный юнит.";
        b.book = "isstvan"; b.page = 33;
        b.asym = true; b.objAttacker = true;
        b.map = "Стол 72″×48″. Зона Защитника — 10″ у его края. У Атакующего две зоны по 10″ у его края: 1 — пехота, 2 — техника (Waves of Attackers). Четыре маркера по 2 ПО — плацдармы в 6–10″ от зоны Защитника (крайние в 12″ от боковых краёв).";
        b.mapId = "firstwave";
        b.note = "Атакующий — Loyalist, Защитник — Traitor. На шаге развёртывания Защитник автоматически выигрывает бросок. Используются Battlefield Assets.";
        for (int i = 1; i <= 4; i++) b.objectives.add(new Objective("Плацдарм " + i, 2, false));
        b.secondaries.add(slay(4)); b.secondaries.add(giant(2)); b.secondaries.add(last(4)); b.secondaries.add(first(3));
        b.rules.add("Battlefield Assets"); b.rules.add("Reserves"); b.rules.add("Seize the Initiative"); b.rules.add("Waves of Attackers"); b.rules.add("Sudden Death");
        b.events.add(new Event("Slaughter the Foe", 2, 2, "Основная цель Защитника: 2 ПО каждый раз, когда последняя модель вражеского юнита снята как потеря."));
        l.add(b);
        return l;
    }

    static Mission custom() {
        Mission m = new Mission();
        m.custom = true;
        m.pack = "Своя";
        m.name = "Custom";
        m.ru = "Своя миссия";
        m.desc = "Задайте маркеры, их ценность и вторичные цели под свою книгу миссий.";
        m.objectives.add(new Objective("Маркер 1", 2, false));
        m.objectives.add(new Objective("Маркер 2", 2, false));
        m.secondaries.add(slay(2)); m.secondaries.add(giant(2)); m.secondaries.add(last(2)); m.secondaries.add(first(2));
        m.rules.add("Sudden Death");
        return m;
    }

    // ================================================================ правила миссий своими словами
    static final Map<String, String[]> RULES = new LinkedHashMap<String, String[]>();
    static {
        rule("Reserves", "rulebook", 311, "Резервы: юниты вне стола. В подфазе резервов каждый такой юнит проходит Reserves Test и при успехе выходит на поле по правилам входа из резерва. Уйти со стола в резерв можно только по особому правилу (например, Flyer).");
        rule("Seize the Initiative", "rulebook", 311, "Перехват инициативы: перед первым ходом игрок, который должен ходить вторым, может бросить D6. На 6 первый ход достаётся ему.");
        rule("Counter Offensive", "rulebook", 311, "Контрнаступление: если в начале последнего хода у игрока вдвое меньше ПО, чем у соперника (или ещё меньше), он может объявить контрнаступление. Тогда его ПО за маркеры и Vanguard (X) в последнем ходу удваиваются (после всех модификаторов).");
        rule("Window of Opportunity", "rulebook", 314, "Окно возможностей: каждый раз, когда маркер приносит ПО в фазе победы, его ценность падает на 1. Маркер с ценностью 0 сразу убирается со стола.");
        rule("Sudden Death", "rulebook", 308, "Внезапная смерть: если в конце любого хода битвы у игрока нет моделей на поле (резервы не считаются; Routed, сидящие в транспорте и зданиях — считаются), битва сразу кончается. Победитель решается по ПО, а игрок, у которого остались модели, получает ещё 3 ПО. Last Man Standing при этом не засчитывается.");
        rule("Dynamic Uplink", "questoris", 90, "Динамическая связь: каждый игрок с Титаном в начальной фазе своего первого хода бросает D6 — 1–2 Macro Emplacement, 3–4 Communications Bunker, 5–6 Command Post. Это его активная стратегическая цель. В следующих своих ходах можно перебросить, но новый результат обязателен. Стратегические цели атакуют только Титаны (Strategic Attack).");
        rule("Advancing Objectives", "saturn", 42, "Продвигающиеся маркеры: стол делится на сетку 4×4, маркеры стоят в центрах секторов. В своей фазе победы Атакующий получает ПО, равные ценности одного контролируемого маркера, а все остальные контролируемые им маркеры сдвигаются на сектор к краю Защитника (не по диагонали), и их ценность растёт на 1, но не выше 3. Маркер у края Защитника только дорожает.");
        rule("Battlefield Assets", "isstvan", 41, "Полевые активы: игроки покупают маркеры-активы за Asset Points — до 1500 очков: 2, до 3500: 5, до 5000: 8, больше: 10. Минные поля, глушилки, склады боеприпасов, когитаторы и т.д.; у каждого свои правила установки и действия (Isstvan V, ч. 1, стр. 42–45).");
        rule("Encircling Reinforcements", "saturn", 43, "Окружение: юниты Защитника, выходящие из резерва, могут появляться с любой точки его края или любого из коротких краёв стола.");
        rule("Surprise Attack", "saturn", 44, "Внезапная атака: броска на развёртывание нет — Защитник ставит всю армию первым. Перед первым ходом Атакующий может бросить D6: на 4+ он ходит первым. Весь первый ход битвы стрельба ведётся только Snap Shots.");
        rule("Spearhead Sector", "saturn", 34, "Сектор прорыва (Атакующий): Praetor и Centurion in Saturnine Terminator Armour, Saturnine Terminator Command Squad и Saturnine Terminator Squad обязаны стоять в резерве и в подфазе резервов первого хода выходят в секторе Spearhead. Остальные юниты Атакующего ставятся обычно и в резерв добровольно не идут.");
        rule("Second Wave", "saturn", 34, "Вторая волна (Защитник): все союзные детачменты Защитника стоят в резерве и без проверок выходят в подфазе резервов его 3-го и 4-го ходов — с края стола в зоне Атакующего. По юнитам второй волны нельзя объявлять Intercept.");
        rule("Preliminary Bombardment", "saturn", 34, "Предварительный обстрел (Защитник): перед первым ходом Защитник может выстрелить каждым выставленным Tarantula Battery и Araknae Quad Accelerator Platform — только Snap Shots.");
        rule("Waves of Attackers", "isstvan", 34, "Волны атакующих: все юниты Атакующего с моделями типа Vehicle развёртываются в его зоне развёртывания 2.");
    }

    private static void rule(String name, String book, int page, String text) { RULES.put(name, new String[]{text, book, String.valueOf(page)}); }

    static String ruleText(String name) {
        String[] r = RULES.get(name);
        return r == null ? name : r[0];
    }
}
