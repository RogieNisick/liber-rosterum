package com.medoedy.liber;

import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;
import com.medoedy.liber.engine.Engine;
import com.medoedy.liber.engine.Engine.ForceOpt;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster;
import com.medoedy.liber.engine.Roster.Force;

import java.util.List;

import static com.medoedy.liber.Ui.dp;

/** Новый ростер: название, лимит, фракция (с голосом глашатая). */
final class NewRosterScreen extends Screen {
    EditText name, limit;

    NewRosterScreen(MainActivity a) { super(a); }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.addView(a.header("Новый ростер", "Призвать армию", theme, true));
        c.addView(a.label("НАЗВАНИЕ", Ui.GOLD));
        name = a.input("", "Например: Крестовый поход на Улланор", false);
        c.addView(name, Ui.lp(-1, -2));
        c.addView(a.label("ЛИМИТ ОЧКОВ", Ui.GOLD));
        LinearLayout chips = Ui.row(a);
        limit = a.input("3000", "Очки", true);
        int[] presets = {1000, 2000, 3000, 4000};
        for (final int pv : presets) {
            TextView ch = Ui.button(a, String.valueOf(pv), Ui.steelButton(theme, dp(6)), Ui.BONE, 16, new View.OnClickListener() {
                public void onClick(View v) { limit.setText(String.valueOf(pv)); }
            });
            ch.setPadding(0, dp(4), 0, dp(6));
            LinearLayout.LayoutParams lp = Ui.lpm(0, dp(44), 0, 0, dp(6), 0);
            lp.weight = 1;
            chips.addView(ch, lp);
        }
        c.addView(chips, Ui.lpm(-1, -2, 0, 0, 0, dp(6)));
        c.addView(limit, Ui.lp(-1, -2));
        c.addView(a.ribbonTitle("ВЫБЕРИТЕ ЗНАМЯ", theme));
        boolean legH = false, othH = false;
        for (final Data.Cat cat : Store.data.playable()) {
            boolean legion = Data.sortKey(cat.name).startsWith("0");
            if (legion && !legH) { legH = true; c.addView(a.label("ЛЕГИОНЫ АСТАРТЕС", Ui.BONE_DIM)); }
            if (!legion && !othH) { othH = true; c.addView(a.label("ПРОЧИЕ СИЛЫ", Ui.BONE_DIM)); }
            final Theme t = Theme.forName(cat.name);
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.states(new Ui.Steel(0xF226302B, 0xF2121815, Theme.mix(t.trim, Ui.GOLD, 0.6f), t.glow, dp(8), false, false),
                    new Ui.Steel(Theme.mix(t.accent, 0xFF26302B, 0.5f), 0xF2121815, Theme.mix(t.trim, Ui.GOLD, 0.6f), t.glow, dp(8), false, true)));
            row.setPadding(dp(10), dp(8), dp(14), dp(8));
            final Ui.Hex hx = new Ui.Hex(a, t, t.numeral.isEmpty() ? Glyph.forUnitType(null, cat.name).equals("infantry") ? "banner" : Glyph.forUnitType(null, cat.name) : null);
            hx.label = t.numeral.isEmpty() ? null : t.numeral;
            row.addView(hx, Ui.lpm(dp(46), dp(46), 0, 0, dp(12), 0));
            String nm = cat.name;
            int dash = nm.indexOf(" - ");
            if (legion && dash > 0) nm = nm.substring(dash + 3);
            LinearLayout txt = Ui.col(a);
            txt.addView(Ui.text(a, nm, 18, Theme.names, Ui.BONE));
            TextView sw = Ui.text(a, "━━━━", 12, Theme.bodyBold, t.accentBright());
            txt.addView(sw);
            row.addView(txt, Ui.weight(1));
            row.setClickable(true);
            Ui.press(row);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Ui.haptic(Ui.DOUBLE);
                    Sfx.play("organ");
                    Voice.say(Voice.proclamation(cat.name));
                    if (Voice.on && !Voice.working()) a.toast(Voice.status);
                    theme = t;
                    a.setBgTheme(t);
                    create(cat);
                }
            });
            c.addView(row, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
        }
        f.addView(a.scroll(c));
        return f;
    }

    void create(Data.Cat chosen) {
        a.hideKeyboard();
        Roster r = new Roster();
        String nm = name.getText().toString().trim();
        r.name = nm.isEmpty() ? chosen.name.replaceAll("^[IVXL]+ - ", "") : nm;
        try { r.limit = Double.parseDouble(limit.getText().toString().trim()); } catch (Exception e) { r.limit = 3000; }
        r.catalogueId = chosen.id;
        Engine e = new Engine(Store.data, r);
        ForceOpt foc = null;
        List<ForceOpt> roots = e.rootForceOptions(chosen.id);
        for (ForceOpt o : roots) if (o.name().startsWith("Crusade")) { foc = o; break; }
        if (foc == null && !roots.isEmpty()) foc = roots.get(0);
        if (foc != null) {
            Force f = e.addForce(null, foc, chosen.id);
            for (Node n : e.rootOptions(f)) if (n.eff.name.equals("Allegiance")) { e.addUnit(f, n); break; }
            for (ForceOpt o : e.childForceOptions(f)) if (o.name().contains("Primary")) { e.addForce(f, o, null); break; }
        }
        a.store.save(r);
        a.replaceTop(new RosterScreen(a, r));
    }
}
