package com.medoedy.liber;

import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Opt;
import com.medoedy.liber.engine.Roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.medoedy.liber.Ui.dp;

/** «Мои миниатюры»: каталог реальной коллекции, по фракциям; добавление юнитов из каталога BSData. */
final class MinisScreen extends Screen {
    MinisScreen(MainActivity a) { super(a); }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        List<Minis.Unit> all = Minis.all(a);
        int units = 0, models = 0;
        for (Minis.Unit u : all) { units += Math.max(1, u.units); models += Math.max(1, u.units) * Math.max(1, u.models); }
        c.addView(a.header("Мои миниатюры", all.isEmpty() ? "Ваша реальная коллекция" : units + " " + MainActivity.plural(units, "юнит", "юнита", "юнитов") + " · " + models + " " + MainActivity.plural(models, "модель", "модели", "моделей"), theme, true));
        if (all.isEmpty()) {
            LinearLayout info = Ui.col(a);
            info.setBackground(Ui.parchment());
            info.setPadding(dp(14), dp(12), dp(14), dp(12));
            info.addView(Ui.text(a, "Каталог вашей коллекции", 17, Theme.names, Ui.INK));
            TextView t = Ui.text(a, "Добавьте юниты, которые реально стоят у вас на полке: из каталога фракции или своим названием. Можно сфотографировать юнит целиком или каждую модель отдельно и подписать её. Умный ростер будет брать именно эти миниатюры и проксить только на свободные похожие модели.", 14, Theme.body, Ui.INK);
            t.setPadding(0, dp(6), 0, 0);
            info.addView(t);
            c.addView(info, Ui.lpm(-1, -2, 0, dp(4), 0, dp(8)));
        }
        c.addView(Ui.button(a, "＋  Добавить юнит", Ui.brass(dp(8)), 0xFF1A140C, 17, new View.OnClickListener() {
            public void onClick(View v) { pickFaction(); }
        }), Ui.lpm(-1, dp(52), 0, dp(4), 0, dp(6)));

        Map<String, List<Minis.Unit>> by = new LinkedHashMap<String, List<Minis.Unit>>();
        for (Minis.Unit u : all) {
            String k = u.catId == null ? "" : u.catId;
            if (!by.containsKey(k)) by.put(k, new ArrayList<Minis.Unit>());
            by.get(k).add(u);
        }
        for (Map.Entry<String, List<Minis.Unit>> en : by.entrySet()) {
            Data.Cat cat = Store.data == null ? null : Store.data.cats.get(en.getKey());
            int n = 0;
            for (Minis.Unit u : en.getValue()) n += Math.max(1, u.units);
            c.addView(a.label((cat == null ? "БЕЗ ФРАКЦИИ" : cat.name.toUpperCase()) + "  ·  " + n, Ui.GOLD));
            for (final Minis.Unit u : en.getValue()) c.addView(row(u, cat), Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
        f.addView(a.scroll(c));
        return f;
    }

    View row(final Minis.Unit u, Data.Cat cat) {
        Theme t = Theme.forName(cat == null ? null : cat.name);
        LinearLayout row = Ui.row(a);
        row.setBackground(Ui.states(new Ui.Parchment(dp(6), false, 0, false), new Ui.Parchment(dp(6), false, 0, true)));
        row.setPadding(dp(8), dp(7), dp(12), dp(8));
        Ui.Hex hx = new Ui.Hex(a, t, "infantry");
        hx.photo = a.photo(u.photoKey());
        if (hx.photo == null) for (Minis.Part p : u.parts) { Bitmap b = a.photo(u.partKey(p)); if (b != null) { hx.photo = b; break; } }
        row.addView(hx, Ui.lpm(dp(50), dp(50), 0, 0, dp(10), 0));
        LinearLayout mid = Ui.col(a);
        mid.addView(Ui.text(a, u.name, 16, Theme.names, Ui.INK));
        String sub = (u.units > 1 ? u.units + " юнита · " : "") + u.models + " " + MainActivity.plural(u.models, "модель", "модели", "моделей") + (u.parts.isEmpty() ? "" : " · фото моделей: " + u.parts.size()) + (u.entryId == null ? " · не из каталога" : "");
        mid.addView(Ui.text(a, sub, 13, Theme.body, Ui.INK_DIM));
        if (!u.notes.isEmpty()) {
            TextView nt = Ui.text(a, u.notes, 12, Theme.body, Ui.INK_DIM);
            nt.setMaxLines(1);
            nt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(nt);
        }
        row.addView(mid, Ui.weight(1));
        row.addView(Ui.text(a, "›", 22, Theme.bodyBold, Ui.INK_RED));
        row.setClickable(true);
        Ui.press(row);
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { a.show(new Edit(a, u)); } });
        return row;
    }

    // ================================================================ добавление
    void pickFaction() {
        LinearLayout body = a.openSheet("Фракция юнита", "Из какой армии эта миниатюра", theme);
        if (Store.data == null) return;
        Set<String> top = new HashSet<String>();
        for (Minis.Unit u : Minis.all(a)) if (u.catId != null) top.add(u.catId);
        for (Roster r : a.store.rosters()) if (r.catalogueId != null) top.add(r.catalogueId);
        List<Data.Cat> cats = new ArrayList<Data.Cat>(Store.data.playable());
        List<Data.Cat> first = new ArrayList<Data.Cat>(), rest = new ArrayList<Data.Cat>();
        for (Data.Cat c : cats) (top.contains(c.id) ? first : rest).add(c);
        if (!first.isEmpty()) body.addView(a.label("ВАШИ ФРАКЦИИ", Ui.GOLD));
        for (Data.Cat c : first) factionItem(body, c);
        body.addView(a.label("ВСЕ ФРАКЦИИ", Ui.GOLD_DIM));
        for (Data.Cat c : rest) factionItem(body, c);
    }

    void factionItem(LinearLayout body, final Data.Cat cat) {
        Theme t = Theme.forName(cat.name);
        LinearLayout it = a.sheetItem(body, t.numeral.isEmpty() ? "shield" : null, cat.name.replaceAll("^[IVXL]+ - ", ""), null, t, false, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); pickUnit(cat); }
        });
        if (!t.numeral.isEmpty()) {
            Ui.Hex hx = new Ui.Hex(a, t, null);
            hx.label = t.numeral;
            it.addView(hx, 0, Ui.lpm(dp(44), dp(44), 0, 0, dp(10), 0));
        }
    }

    static final class Entry { String id, name, slot; int models; }

    static final Map<String, List<Entry>> ENTRIES = new LinkedHashMap<String, List<Entry>>();

    /** Все юниты фракции из каталога (с профилями моделей). */
    static List<Entry> entries(String catId) {
        List<Entry> out = ENTRIES.get(catId);
        if (out != null) return out;
        out = new ArrayList<Entry>();
        Data d = Store.data;
        Data.Cat cat = d.cats.get(catId);
        Roster r = new Roster();
        r.catalogueId = catId;
        Engine e = new Engine(d, r);
        Set<String> seen = new HashSet<String>();
        for (Opt o : cat.roots) {
            String nm = Data.clean(Data.str(o.entry, "name"));
            if (nm == null || !seen.add(nm)) continue;
            if (nm.equals("Allegiance") || nm.startsWith("Rite of War")) continue;
            if (!"unit".equals(Data.str(o.entry, "type")) && !"model".equals(Data.str(o.entry, "type"))) continue;
            Entry en = new Entry();
            en.id = Data.str(o.entry, "id");
            en.name = nm;
            en.models = minModels(o.entry);
            out.add(en);
        }
        Collections.sort(out, new Comparator<Entry>() { public int compare(Entry x, Entry y) { return x.name.compareToIgnoreCase(y.name); } });
        ENTRIES.put(catId, out);
        return out;
    }

    /** Минимальный состав юнита по данным (сумма min у моделей), для подсказки. */
    static int minModels(Map<String, Object> entry) {
        if ("model".equals(Data.str(entry, "type"))) return 1;
        int n = 0;
        for (Map<String, Object> ch : Data.list(entry, "selectionEntries")) {
            if (!"model".equals(Data.str(ch, "type"))) continue;
            for (Map<String, Object> k : Data.list(ch, "constraints")) if ("min".equals(Data.str(k, "type"))) n += (int) Data.num(k, "value", 0);
        }
        return Math.max(1, n);
    }

    void pickUnit(final Data.Cat cat) {
        final Theme t = Theme.forName(cat.name);
        final LinearLayout body = a.openSheet("Какой юнит?", cat.name, t);
        final EditText q = a.input("", "Поиск или своё название", false);
        body.addView(q, Ui.lpm(-1, -2, 0, dp(2), 0, dp(6)));
        final LinearLayout list = Ui.col(a);
        body.addView(list);
        final List<Entry> es = entries(cat.id);
        final Runnable fill = new Runnable() {
            public void run() {
                list.removeAllViews();
                final String s = q.getText().toString().trim();
                if (!s.isEmpty()) a.sheetItem(list, "scroll", "＋ «" + s + "» — своё название", "Не из каталога: ростер не сможет его использовать", t, false, new View.OnClickListener() {
                    public void onClick(View v) { a.closeSheetNow(); created(Minis.add(a, cat.id, null, s, 1)); }
                });
                int shown = 0;
                String low = s.toLowerCase();
                for (final Entry en : es) {
                    if (!low.isEmpty() && !en.name.toLowerCase().contains(low)) continue;
                    if (++shown > 120) break;
                    a.sheetItem(list, null, en.name, en.models > 1 ? "от " + en.models + " моделей" : null, t, false, new View.OnClickListener() {
                        public void onClick(View v) { a.closeSheetNow(); created(Minis.add(a, cat.id, en.id, en.name, en.models)); }
                    });
                }
            }
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(Editable s) { fill.run(); }
        });
        fill.run();
    }

    void created(Minis.Unit u) {
        Sfx.play("seal");
        rebuild();
        a.show(new Edit(a, u));
    }

    // ================================================================ карточка юнита
    static final class Edit extends Screen {
        final Minis.Unit u;
        Edit(MainActivity a, Minis.Unit u) {
            super(a);
            this.u = u;
            Data.Cat c = Store.data == null ? null : Store.data.cats.get(u.catId);
            theme = Theme.forName(c == null ? null : c.name);
        }

        @Override
        boolean handleBack() { Minis.save(a); return false; }

        View build() {
            FrameLayout f = new FrameLayout(a);
            LinearLayout c = a.column();
            Data.Cat cat = Store.data == null ? null : Store.data.cats.get(u.catId);
            LinearLayout hd = a.header(u.name, cat == null ? null : cat.name, theme, true);
            ((LinearLayout) hd.getChildAt(0)).addView(Ui.trashButton(a, new View.OnClickListener() {
                public void onClick(View v) {
                    a.confirm("Удалить «" + u.name + "»?", "Фото тоже будут удалены.", "Удалить", theme, new Runnable() {
                        public void run() { Minis.remove(a, u); a.back(); }
                    });
                }
            }), Ui.lpm(dp(38), dp(38), dp(8), 0, 0, 0));
            c.addView(hd);

            // общее фото
            c.addView(photoBox(u.photoKey(), "📷  Фото всего юнита", dp(220)), Ui.lpm(-1, -2, 0, dp(4), 0, dp(8)));

            LinearLayout box = Ui.col(a);
            box.setBackground(Ui.parchment());
            box.setPadding(dp(12), dp(10), dp(12), dp(12));
            box.addView(Ui.text(a, "Название", 12, Theme.bodyBold, Ui.INK_DIM));
            final EditText nm = a.input(u.name, "Название", false);
            nm.addTextChangedListener(watch(new Runnable() { public void run() { u.name = nm.getText().toString().trim(); } }));
            box.addView(nm, Ui.lpm(-1, -2, 0, dp(2), 0, dp(8)));
            box.addView(counter("Сколько таких юнитов", u.units, 1, 20, new UnitScreen.Step() { public void set(int v) { u.units = v; Minis.save(a); rebuild(); } }));
            box.addView(counter("Моделей в юните", u.models, 1, 40, new UnitScreen.Step() { public void set(int v) { u.models = v; Minis.save(a); rebuild(); } }));
            box.addView(Ui.text(a, "Заметки: оружие на моделях, покраска, магниты…", 12, Theme.bodyBold, Ui.INK_DIM));
            final EditText notes = a.input(u.notes, "Например: 2 лазпушки на магнитах", false);
            notes.setSingleLine(false);
            notes.setMinLines(2);
            notes.addTextChangedListener(watch(new Runnable() { public void run() { u.notes = notes.getText().toString(); } }));
            box.addView(notes, Ui.lpm(-1, -2, 0, dp(2), 0, 0));
            if (u.entryId == null) {
                TextView w = Ui.text(a, "Этот юнит вписан своим названием и не связан с каталогом — умный ростер его не учитывает.", 12, Theme.body, Ui.INK_RED);
                w.setPadding(0, dp(8), 0, 0);
                box.addView(w);
            }
            c.addView(box);

            // модели по отдельности
            c.addView(a.label("МОДЕЛИ ПО ОТДЕЛЬНОСТИ (по желанию)", Ui.GOLD));
            for (int i = 0; i < u.parts.size(); i++) c.addView(partRow(i), Ui.lpm(-1, -2, 0, dp(4), 0, dp(4)));
            c.addView(Ui.button(a, "＋  Модель: фото и описание", Ui.steelButton(theme, dp(8)), Ui.BONE, 15, new View.OnClickListener() {
                public void onClick(View v) {
                    final Minis.Part p = new Minis.Part();
                    p.id = Long.toString(System.currentTimeMillis(), 36);
                    u.parts.add(p);
                    Minis.save(a);
                    a.pickPhoto(u.partKey(p), new Runnable() { public void run() { rebuild(); } });
                    rebuild();
                }
            }), Ui.lpm(-1, dp(48), 0, dp(4), 0, dp(4)));

            f.addView(a.scroll(c));
            return f;
        }

        TextWatcher watch(final Runnable r) {
            return new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
                public void onTextChanged(CharSequence s, int x, int y, int z) { }
                public void afterTextChanged(Editable s) { r.run(); Minis.save(a); }
            };
        }

        View photoBox(final String key, String empty, int h) {
            FrameLayout fr = new FrameLayout(a);
            fr.setBackground(Ui.steelFlat(theme, dp(8)));
            Bitmap b = a.photo(key);
            if (b != null) {
                ImageView iv = new ImageView(a);
                iv.setImageBitmap(b);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setPadding(dp(3), dp(3), dp(3), dp(3));
                fr.addView(iv, new FrameLayout.LayoutParams(-1, h));
                TextView ch = Ui.text(a, "📷", 16, Theme.bodyBold, Ui.BONE);
                ch.setBackground(Ui.steelButton(theme, dp(6)));
                ch.setPadding(dp(10), dp(4), dp(10), dp(6));
                fr.addView(ch, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM));
            } else {
                TextView t = Ui.text(a, empty, 15, Theme.bodyBold, Ui.GOLD);
                t.setGravity(Gravity.CENTER);
                fr.addView(t, new FrameLayout.LayoutParams(-1, h));
            }
            fr.setClickable(true);
            Ui.press(fr);
            fr.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { a.pickPhoto(key, new Runnable() { public void run() { rebuild(); } }); }
            });
            return fr;
        }

        View partRow(final int i) {
            final Minis.Part p = u.parts.get(i);
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.parchment());
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setGravity(Gravity.TOP);
            View ph = photoBox(u.partKey(p), "📷", dp(96));
            row.addView(ph, Ui.lpm(dp(96), dp(96), 0, 0, dp(10), 0));
            LinearLayout col = Ui.col(a);
            col.addView(Ui.text(a, "Модель " + (i + 1), 14, Theme.names, Ui.INK));
            final EditText d = a.input(p.desc, "Кто это и чем вооружён", false);
            d.setSingleLine(false);
            d.setMinLines(2);
            d.addTextChangedListener(watch(new Runnable() { public void run() { p.desc = d.getText().toString(); } }));
            col.addView(d, Ui.lpm(-1, -2, 0, dp(2), 0, 0));
            row.addView(col, Ui.weight(1));
            row.addView(Ui.trashButton(a, new View.OnClickListener() {
                public void onClick(View v) { u.parts.remove(p); a.removePhoto(u.partKey(p)); Minis.save(a); rebuild(); }
            }), Ui.lpm(dp(30), dp(30), dp(8), 0, 0, 0));
            return row;
        }

        View counter(String label, int value, final int lo, final int hi, final UnitScreen.Step st) {
            LinearLayout row = Ui.row(a);
            row.setPadding(0, dp(3), 0, dp(6));
            row.addView(Ui.text(a, label, 15, Theme.bodyBold, Ui.INK), Ui.weight(1));
            final int v = value;
            TextView minus = Ui.button(a, "−", Ui.steelButton(theme, dp(5)), Ui.GOLD, 20, new View.OnClickListener() { public void onClick(View x) { if (v > lo) st.set(v - 1); } });
            minus.setPadding(0, 0, 0, dp(3));
            row.addView(minus, Ui.lp(dp(40), dp(38)));
            TextView val = Ui.text(a, String.valueOf(value), 18, Theme.namesBlack, Ui.INK);
            val.setGravity(Gravity.CENTER);
            row.addView(val, Ui.lp(dp(40), -2));
            TextView plus = Ui.button(a, "+", Ui.steelButton(theme, dp(5)), Ui.GOLD, 20, new View.OnClickListener() { public void onClick(View x) { if (v < hi) st.set(v + 1); } });
            plus.setPadding(0, 0, 0, dp(3));
            row.addView(plus, Ui.lp(dp(40), dp(38)));
            return row;
        }
    }

    @Override
    void refresh() { rebuild(); }
}
