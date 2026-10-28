package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Engine.ForceOpt;
import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.util.ArrayList;
import java.util.List;

/** Импорт ростера из текстового экспорта Liber Rosterum («== детачмент ==», «• юнит», отступы — снаряжение). */
public final class TextImport {
    public Roster roster;
    public final List<String> warnings = new ArrayList<String>();
    private final Data d;

    public TextImport(Data d) { this.d = d; }

    static final class Line { int depth, n = 1; String name; }

    public static boolean looksLike(String t) { return t != null && t.contains("\n== ") && t.contains("• "); }

    public TextImport parse(String text) {
        String[] ls = text.replace("\r", "").split("\n");
        if (ls.length < 3) { warnings.add("Текст не похож на экспорт Liber Rosterum."); return this; }
        Roster r = new Roster();
        String head = ls[0];
        int dash = head.lastIndexOf(" — ");
        r.name = dash > 0 ? head.substring(0, dash).trim() : head.trim();
        String catName = dash > 0 ? head.substring(dash + 3).trim() : "";
        Data.Cat cat = null;
        for (Data.Cat c : d.cats.values()) if (c.name.equalsIgnoreCase(catName)) cat = c;
        if (cat == null) { warnings.add("Не нашёл фракцию «" + catName + "»."); return this; }
        r.catalogueId = cat.id;
        try { r.limit = Double.parseDouble(ls[1].replaceAll(".*/\\s*", "").replaceAll("[^0-9.]", "")); } catch (Exception ignored) { r.limit = 3000; }
        Engine e = new Engine(d, r);
        Force root = null, cur = null;
        // юниты собираем блоками и ставим в несколько проходов: слот может открыться позже (Logistical Benefit и т.п.)
        List<Object[]> blocks = new ArrayList<Object[]>(); // {Force, Line unit, List<Line> children}
        List<Line> kids = null;
        for (int i = 2; i < ls.length; i++) {
            String raw = ls[i];
            if (raw.trim().isEmpty() || raw.startsWith("✓") || raw.startsWith("⚠") || raw.startsWith("Собрано в")) continue;
            if (raw.startsWith("== ")) {
                kids = null;
                String body = raw.substring(3).replaceAll("\\s*==\\s*$", "");
                String fcat = null;
                int b1 = body.lastIndexOf('{');
                if (b1 > 0 && body.endsWith("}")) { fcat = body.substring(b1 + 1, body.length() - 1).trim(); body = body.substring(0, b1).trim(); }
                String fname = body.replaceAll("\\s*\\[[^\\]]*\\]\\s*$", "").trim();
                String fcid = cat.id;
                if (fcat != null) for (Data.Cat c : d.cats.values()) if (c.name.equalsIgnoreCase(fcat)) fcid = c.id;
                ForceOpt ro = null;
                String roCat = null;
                for (ForceOpt o : e.rootForceOptions(fcid)) if (o.name().equals(fname)) { ro = o; roCat = fcid; }
                if (ro == null && fcat == null && fname.startsWith("Allied")) {
                    roCat = guessAllyCat(ls, i, cat.id);
                    if (roCat != null) for (ForceOpt o : e.rootForceOptions(roCat)) if (o.name().equals(fname)) ro = o;
                }
                if (ro != null) { root = e.addForce(null, ro, roCat); cur = root; }
                else {
                    ForceOpt co = null;
                    if (root != null) for (ForceOpt o : e.childForceOptions(root)) if (o.name().equals(fname)) co = o;
                    if (co == null) { warnings.add("Детачмент «" + fname + "» не найден — пропущен."); cur = null; continue; }
                    cur = e.addForce(root, co, null);
                }
                continue;
            }
            if (cur == null) continue;
            if (raw.startsWith("• ")) {
                kids = new ArrayList<Line>();
                blocks.add(new Object[]{cur, line(raw.substring(2), 0), kids});
                continue;
            }
            if (kids == null) continue;
            int sp = 0;
            while (sp < raw.length() && raw.charAt(sp) == ' ') sp++;
            kids.add(line(raw.trim(), sp / 3));
        }
        boolean[] done = new boolean[blocks.size()];
        for (int pass = 0; pass < 4; pass++) {
            boolean any = false;
            for (int k = 0; k < blocks.size(); k++) {
                if (done[k]) continue;
                Force f = (Force) blocks.get(k)[0];
                Line ul = (Line) blocks.get(k)[1];
                Sel unit = null;
                for (Node n : e.rootOptions(f)) if (n.eff.name.equals(ul.name) && !n.eff.hidden) { unit = e.addUnit(f, n); break; }
                if (unit == null) for (Node n : e.rootOptions(f)) if (n.eff.name.equalsIgnoreCase(ul.name)) { unit = e.addUnit(f, n); break; }
                if (unit == null) continue;
                done[k] = true;
                any = true;
                unit.children.clear();
                e.changed();
                @SuppressWarnings("unchecked") List<Line> ch = (List<Line>) blocks.get(k)[2];
                applyKids(e, unit, ch);
            }
            if (!any) break;
        }
        for (int k = 0; k < blocks.size(); k++) if (!done[k]) warnings.add("Юнит «" + ((Line) blocks.get(k)[1]).name + "» не найден в «" + e.effForce((Force) blocks.get(k)[0]).name + "».");
        e.changed();
        roster = r;
        return this;
    }

    private void applyKids(Engine e, Sel unit, List<Line> kids) {
        List<Sel> stack = new ArrayList<Sel>();
        stack.add(unit);
        for (Line ln : kids) {
            while (stack.size() > ln.depth) stack.remove(stack.size() - 1);
            if (stack.isEmpty()) stack.add(unit);
            Sel parent = stack.get(stack.size() - 1);
            List<Node> all = new ArrayList<Node>();
            findAll(e.options(e.entryOf(parent), e.linkOf(parent), parent), ln.name, all);
            Node found = null;
            // одно имя может быть в нескольких группах (Pintle #1 / #2) — берём ещё не заполненную
            for (Node n : all) {
                Sel ex0 = e.findChildPublic(parent, n.opt);
                double mx = e.maxOf(n, parent);
                if (ex0 == null || mx < 0 || ex0.number + ln.n <= mx) { found = n; break; }
            }
            if (found == null && !all.isEmpty()) found = all.get(0);
            if (found == null) { warnings.add("«" + ln.name + "» не найдено у «" + e.effSel(parent).name + "»."); stack.add(parent); continue; }
            Sel ex = e.findChildPublic(parent, found.opt);
            int have = ex == null ? 0 : ex.number;
            e.setCount(parent, found.opt, have + ln.n);
            Sel ch = e.findChildPublic(parent, found.opt);
            if (ch != null && have == 0) { ch.children.clear(); e.changed(); }
            stack.add(ch == null ? parent : ch);
        }
    }

    private String guessAllyCat(String[] ls, int from, String mainCat) {
        for (int j = from + 1; j < ls.length && j < from + 40; j++) {
            if (!ls[j].startsWith("• ")) continue;
            String nm = line(ls[j].substring(2), 0).name;
            for (Data.Cat c : d.cats.values()) {
                if (c.id.equals(mainCat)) continue;
                for (Opt o : c.roots) if (nm.equals(Data.str(o.entry, "name"))) return c.id;
            }
        }
        return null;
    }

    static Line line(String s, int depth) {
        Line l = new Line();
        l.depth = depth;
        s = s.replaceAll("\\s*\\[[^\\]]*\\]\\s*$", "").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)×\\s+(.*)$").matcher(s);
        if (m.find()) { l.n = Integer.parseInt(m.group(1)); s = m.group(2); }
        l.name = s;
        return l;
    }

    static void findAll(List<Node> ns, String name, List<Node> out) {
        for (Node n : ns) {
            if (n.isGroup()) findAll(n.children, name, out);
            else if (n.eff.name.equalsIgnoreCase(name)) out.add(n);
        }
    }

    static Node find(List<Node> ns, String name) {
        for (Node n : ns) {
            if (!n.isGroup() && n.eff.name.equals(name)) return n;
            if (n.isGroup()) { Node x = find(n.children, name); if (x != null) return x; }
        }
        for (Node n : ns) {
            if (!n.isGroup() && n.eff.name.equalsIgnoreCase(name)) return n;
            if (n.isGroup()) { Node x = find(n.children, name); if (x != null) return x; }
        }
        return null;
    }
}
