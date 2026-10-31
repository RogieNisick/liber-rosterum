# CODEMAP — Liber Rosterum 4.23 (versionCode 61)

Карта «где что лежит». Строки (`:N`) — якоря на момент версии 4.17, после правок слегка плывут: ищи через `grep -n "имя" -r src`. Крупные файлы целиком не читать.

Пакет `com.medoedy.liber` (`src/com/medoedy/liber/`), движок в `engine/`. Java 8, без Android SDK. Один `MainActivity`, экраны — наследники `Screen` (`Screen.java`: `build()`, `handleBack()`, `refresh()`).

## Сборка и релиз
- `build.sh` — aapt2 → javac (android.jar из `$T/plat/android-34`) → dx → подпись apksigner → `build/LiberRosterum.apk`.
- `release.sh` — обновляет `notes_history.json` из `release_notes.txt`, вызывает `build.sh`, пишет `build/version.json`. Версия берётся из `AndroidManifest.xml` (`versionCode`, `versionName`).
- На Windows (эта машина): сборка `bash tools/build-win.sh` (тулчейн в `tools/`, d8), релиз — `perl ../append_entry.pl` + `perl ../versionjson.pl` (скрипты в родителе репо). Подпись — `liber.keystore` в корне (pass `liberliber`, alias `liber`), SHA-256 `898cb04a…`; бэкап ключа — `D:\Liber Rosterum\liber.keystore.kimi-backup`. После сборки проверять: `jar tf build/LiberRosterum.apk | grep -c "\\\\"` = 0.
- Ветка `main` — исходники; ветка `updates` — только `LiberRosterum.apk`, `version.json`, `README.md`. **На `updates` коммитить строго `git add LiberRosterum.apk version.json` — НИКОГДА `git add -A`** (там нет `.gitignore`, подтянется `tools/`, `build/`, кейстор).
- `assets/`: `art/` (рамка `frame.png`, значки слотов `slot_*.png`, `b_*.png`, `f_*.png`, сервочереп, свечи), `fonts/`, `library/*.idx.gz` (поисковые индексы книг правил, 16 шт.), `phrases.txt`, `news_scroll.png`, `notes_history.json`.

## engine/ — правила и данные (без UI)
| Файл | Что внутри |
|---|---|
| `Data.java` (226) | Загрузка системы и каталогов BSData JSON (`loadSystem:77`, `loadCatalogue:90`), индексы по id (`index:117`, `finish:146`), `playable:182`, `categoryName:217`. Вложенный `Cat`. |
| `Engine.java` (1339) | Вычислитель в духе BattleScribe. Модификаторы и условия: `eff:71`, `applyMod:167`, `condsOk:235`, `cond:250`, `resolveScope:287`. Подсчёт: `countSels:426`, `selCost:459`, `rosterCost:472`, `points:485`. Опции и слоты: `options:489`, `rootOptions:549`, `slotsFor:584`, `slots:632`, `rootForceOptions:699`, `childForceOptions:717`. Изменение ростера: `addForce:752`, `addUnit:770`, `addSel:782`, `removeSel:796`, `setCount:1064`. Autofill: `autofill:802`, `fillNodes:811`, `defaultCandidates:857`. Лимиты: `minOf:979`, `maxOf:991`. Валидация: `validate:1088`, `validateForce:1099`, `checkLogistical:1199` (Logistical Benefits), `validateUnit:1227`, `checkConstraints:1256`. Профили и правила: `addProfiles:1308`, `addRules:1326`. Вложенные `Ctx`, `Eff`, `Node:496`, `CatSlot:577`, `ForceOpt:693`, `Issue:1072`. |
| `Opt.java` (27) | Вариант выбора: запись/группа, ссылка, путь групп. |
| `Roster.java` (123) | Модель ростера: `Force` (детачмент), `Sel` (выбор). |
| `Smart.java` (2609) | **Умный ростер.** См. раздел ниже. |
| `Foe.java` (134) | Профиль противника по данным (доли техники, элиты, лёгкой пехоты, рукопашников). |
| `Chars.java` (117) | Персонажи фракции из каталога (Warlord, High Command, Command). |
| `RosImport.java` (236) / `RosExport.java` (88) | Импорт и экспорт BattleScribe/New Recruit `.ros`/`.rosz`. |
| `TextImport.java` (173) | Импорт из текстового экспорта приложения («== детачмент ==», «• юнит»). |
| `Json.java` (173) | Свой JSON-парсер и писатель. |

### Smart.java — разделы по порядку
- Параметры и результат: `Params:27`, `Mini:51`, `Cap:67`, `Pick:75`, `Result:94`, `Owned:113` (что есть у игрока: `collect:158`, `usedOf:139`, `favouriteFaction:145`).
- Модель юнита: `models:248`, `modelProfile:255`, `gearNames:295`, `profiles:333`, `weapon:391`, `capOf:348`.
- Потребности и веса (6 осей): `needs:418`, `enemyNeeds:440`, `missionNeeds:494`, `styleBonus:589`, `holderBonus:508`, `hasRule:517`, `signature:608`.
- **Главный проход `build:637`**: союзники `allyPhase:828`, `fillAllyForce:962`, `templateAlly:978`; кандидаты `buildCands:1061`, `candidates:1450`; закреплённые `pinPhase:1092`; Prime-слоты `primePhase:1134`; Logistical Benefits `lbPhase:1166`, `tryLogistical:1187`, `pickBenefit:1257`, `collectBenefits:1283`; добор очков `fillUnits:1296`, `fillToLimit:1321`.
- Оценка и подбор: `probe:1536`, `similar:1554` (прокси на похожие миниатюры), `make:1592`, `value:1607`, `bestFor:1650`, `unitScore:1701`, `optimize:1712`, `place:1755`.
- Командование и вспомогательное: `addAuxiliary:1810`, `openAuxFor:1825`, `unlockCommand:1867`, `switchHighCommand:1889`, `bundles:1924`, `tryBundle:1942`, `fixOptions:2024`, `dropUselessLogistical:2062`, `trimOver:2100`.
- Результат для UI: `strategy:2133` (текстовая стратегия), `armyScore:2242`, `improve:2249` («Улучши мой ростер»), `sense:2397`, `explainAll:2435` («почему»), `charRules:2554`, `collectWeaponRules:2569`, `summarize:2596`.

## UI — экраны (наследники `Screen`)
| Файл | Назначение |
|---|---|
| `MainActivity.java` (1106) | Навигация (`show:103`, `back:140`), смена темы (`toggleDark:85`), старт и загрузка (`onCreate:236`, `startLoading:457`), импорт файлов (`importRoster:355`), общие виджеты (`header:490`, `openSheet:570`, `confirm:643`, `toast:654`), настройки (`fillSettings:703`), фото юнитов (`pickPhoto:845`). Вложенные `SettingsScreen:685`, `Toggle:786`, `LoadingScreen:972`, `DataScreen:1016`. |
| `MenuScreen.java` | Главное меню (ростеры / битва / данные / настройки). |
| `RosterListScreen.java` | Архив ростеров. |
| `NewRosterScreen.java` | Новый ростер: название, лимит, фракция. |
| `RosterScreen.java` (763) | Ростер: `build:57`, `unitRow:371`, `addDetachment:465`, `pickFaction:554`, `showIssues:662`, `shareRos:698`, `exportText:717`. |
| `OrgChart.java` | Вид «как в книге»: Force Organisation Chart, кружки слотов, Prime-компас. |
| `PickerScreen.java` | Выбор юнита для слота: поиск, силуэты, цена. |
| `UnitScreen.java` (997) | Карточка юнита: `build:89`, модели `renderModel:250`, снаряжение `renderGear:298`, группы опций `openGroup:466`, `fillLeaves:492`, `stepper:633`, карточки характеристик и оружия `statCard:746`, `weaponCard:842`, правила `showRule:937`. |
| `Problems.java` | Ошибки валидации → понятные объяснения и что нажать. |
| `SmartScreen.java` (875) | Экран Умного ростера: `build:54`, настройки `loadPrefs:263`, выбор `pickMine:303`, `pickEnemy:389`, `compute:410`, результат `showResult:438`, карточки `strategyCard:607`, `whyCard:725`, `compareVariants:783`. |
| `ImproveScreen.java` | «Улучши мой ростер»: точечные замены под противника. |
| `MinisScreen.java` / `Minis.java` | «Мои миниатюры»: коллекция игрока, каталог по фракциям. |
| `ChallengeScreen.java` (870) | Дуэли (Challenge): бойцы `stepFighters:212`, гамбиты `stepGambits:323`, Фокус `stepFocus:541`, удар `stepStrike:593`, Слава `stepGlory:642`. `Gambits.java` — данные гамбитов. |
| `BattleSetupScreen.java` / `BattleScreen.java` / `Battle.java` / `Missions.java` | Подготовка битвы, счёт победных очков, состояние битвы, миссии 3-й редакции. |
| `CheatScreen.java` | Шпаргалка: юниты, правила армии. |
| `LibraryScreen.java` / `Library.java` / `ReaderScreen.java` / `Bookmarks.java` | Библиотека книг: поиск по индексу, PDF-читалка, закладки. |
| `News.java` / `NewsScreen.java` | Новости Ереси: RSS/Atom, фильтр, перевод. |
| `ImageSearchScreen.java` / `AutoPhoto.java` / `PhotoProvider.java` | Фото юнитов: поиск картинок, автопоиск, запись снимка камерой. |

## Оформление, звук, прочее
- `Ui.java` — тема «Cogitator»: `setDark:52`, `steel/steelButton:290`, `parchment:355`, `button:512`, `row/col:629`, `drawSkull:661`, `parchmentPage:754`. Вложенные `Steel`, `Parchment`, `Metal`, `Ribbon`, `T`, `Glow`, `Backdrop`, `ServoSkull` (варп-скверна по `Ui.warp`). Флаг `Ui.warp` ставится из `Eggs.applyChaos`.
- `Cogitator.java` — рамка экрана, свечи, шлюз-переход. `Art.java` — картинки из `assets/art`. `Theme.java` — цвета фракций и шрифты. `Glyph.java` — нарисованные значки типов юнитов и миссий. `Units.java` — тип юнита для силуэта, русские названия слотов.
- `Sfx.java` — синтезированные звуки и хаптика. `Voice.java` — глашатай (TTS + вокс-эффект), `saySkull` — голос сервочерепа, `voxWarp` — варп-искажение (флаг `Voice.warp`, маркеры \u0001/\u0002 в pendingText). `Guide.java` — сервочереп-наставник, фразы из `assets/phrases.txt`, в варпе — `assets/phrases_warp.txt`. `Eggs.java` — пасхалки, варп-режим (`chaos` в prefs, `applyChaos`, долгое нажатие на эмблему или свайпы UUDD). `Shrine.java` — свечи и черепа на плашках.
- `Translator.java` / `TrText.java` — перевод правил на русский с кешем.
- `Store.java` — файлы данных BSData и сохранённые ростеры (`download:59`, `load:119`, `save:154`).
- `Updater.java` — обновления «по воздуху» (Яндекс Диск / GitHub): `check:69`, `githubRepo:148`, `download:229`, `install:263`.

## Куда смотреть по симптому
- Неверная цена, лимиты, видимость опций, слоты → `Engine` (`points`, `slots`, `checkConstraints`, `applyMod`).
- Не открывается слот союзников, Logistical Benefits → `Engine.checkLogistical`, `Smart.lbPhase/tryLogistical/allyPhase`.
- Не тот состав у Smart Roster → `Smart.build` и фаза из списка выше; прокси → `similar`; веса → `needs`, `unitScore`.
- Ошибка проверки на экране → `Problems.java`, `RosterScreen.showIssues`.
- Импорт/экспорт → `RosImport`, `RosExport`, `TextImport`.
- Внешний вид, тема → `Ui.java`, `Cogitator.java`, `Theme.java`.
- Обновления → `Updater.java`, `release.sh`, ветка `updates`.

## Дополнения 2026-10-30
- Озвучка сервочерепа: `Voice.saySkull` (TTS 1.5×, `voxSkull` — кольцевая модуляция без соборного эха); вызовы в `Guide.step`, шутки меню в `MenuScreen.build`, литания `Eggs.LITANY`, интро `ImproveScreen`.
- Smart: потолок 2 доп. детачментов союзника (`allyPhase`, `auxCount`); штраф HQ в `value()` теперь ловит слот «HQ» (был мёртвый `startsWith("Command")`); объяснения про командиров союзника в `res.reasons`.
- ImproveScreen: «Союзник врага» (`pickEnemyAlly`, pref `improve_enemy_ally`), выбор миссии (`improve_mission`), кнопка «Применить всё» (`applyAll`).
- Время боя: `Battle.turnStarted` (+json), живые часы `BattleScreen.fmtDur` (секундный тик, общее + на ход).
- Иконки фракций: `Theme.iconFor` (ключи da/ec/iw/…), `Glyph.drawFaction` (свои векторные знаки), `Ui.Hex` рисует иконку вместо «shield» и без symbol/label.
- Варп-тема: `Eggs.startVeil/stopVeil` (щупальца, глаза, глитчи, шёпоты), звуки `Sfx`: `warp_drone` (петля `Sfx.loop`), `warp_whisper`, `warp_glitch`.
- Сборка без SDK на этой машине: `tools/tools-build.sh` (build-tools r34 + android.jar + JDK17 в `tools/`; d8 вместо dx).
- Smart, багфикс 4.21: свободный режим (`Params.mode == 2`) — флаг `freeMode` в `build()` и `improve()`: коллекция даёт кандидатов, но не бонус (`value()`), лимит новых юнитов 30% снят; «ответный кулак» против стрелковых армий (`needs()`: Solar Auxilia w[4]=0.7, trait shoot +w[4], текст-объяснение) и множитель в `value()` (быстрые/элитные рукопашники ×1.3); фирменные юниты без урезания 0.9 при открытии aux-детачмента (bestAux k = signature ? 1.0 : 0.9).
