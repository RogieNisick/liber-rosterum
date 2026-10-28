package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Engine.Node;
import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Импорт ростеров BattleScribe / New Recruit (.ros — XML, .rosz — zip с .ros внутри). */
public final class RosImport {
    public static final class Result {
        public Roster roster;
        public final List<String> missing = new ArrayList<String>();
        public String error;
        public int units, forces;
    }

    private final Data d;
    private Engine e;
    private Result res;

    public RosImport(Data d) { this.d = d; }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    /** Достаёт XML: если это zip (.rosz) — первый .ros внутри. */
    static byte[] unwrap(byte[] b) throws IOException {
        if (b.length > 4 && b[0] == 'P' && b[1] == 'K') {
            ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b));
            ZipEntry ze;
            byte[] first = null;
            while ((ze = z.getNextEntry()) != null) {
                if (ze.isDirectory()) continue;
                byte[] body = readAll(z);
                if (ze.getName().toLowerCase().endsWith(".ros")) return body;
                if (first == null) first = body;
            }
            if (first != null) return first;
            throw new IOException("В архиве нет файла .ros");
        }
        return b;
    }

    public Result parse(byte[] raw) {
        res = new Result();
        try {
            byte[] xml = unwrap(raw);
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            DocumentBuilder db = f.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xml));
            Element root = doc.getDocumentElement();
            if (!"roster".equals(local(root))) { res.error = "Это не ростер BattleScribe."; return res; }
            Roster r = new Roster();
            r.name = attr(root, "name");
            if (r.name == null || r.name.isEmpty()) r.name = "Импорт BattleScribe";
            r.limit = 0;
            Element cl = child(root, "costLimits");
            if (cl != null) for (Element c : kids(cl, "costLimit")) {
                String tid = attr(c, "typeId"), nm = attr(c, "name");
                if ((tid != null && tid.equals(d.pointsId)) || (nm != null && nm.toLowerCase().startsWith("pts")) || (nm != null && nm.toLowerCase().startsWith("point"))) {
                    try { double v = Double.parseDouble(attr(c, "value")); if (v > 0) r.limit = v; } catch (Exception ignored) { }
                }
            }
            Element fs = child(root, "forces");
            List<Element> forceEls = fs == null ? new ArrayList<Element>() : kids(fs, "force");
            if (forceEls.isEmpty()) { res.error = "В ростере нет ни одной силы (force)."; return res; }
            for (Element fe : forceEls) {
                String cid = attr(fe, "catalogueId");
                if (cid != null && d.cats.containsKey(cid)) { r.catalogueId = cid; break; }
            }
            if (r.catalogueId == null) {
                String cn = attr(forceEls.get(0), "catalogueName");
                res.error = "Каталог «" + (cn == null ? "?" : cn) + "» не найден. Ростер должен быть собран для The Horus Heresy 3rd Edition (данные BSData).";
                return res;
            }
            e = new Engine(d, r);
            for (Element fe : forceEls) importForce(fe, null, r);
            if (r.limit <= 0) r.limit = Math.max(1000, Math.ceil(e.rosterCost(d.pointsId) / 500) * 500);
            res.roster = r;
        } catch (Exception ex) {
            res.error = "Не удалось прочитать файл: " + ex.getMessage();
        }
        return res;
    }

    private void importForce(Element fe, Force parent, Roster r) {
        String[] ids = split(attr(fe, "entryId"));
        String fid = ids[ids.length - 1];
        String name = attr(fe, "name");
        if (!d.forces.containsKey(fid)) { res.missing.add("Детачмент «" + name + "»"); return; }
        Force f = new Force();
        f.entryId = fid;
        if (ids.length > 1 && d.forceLinks.containsKey(ids[ids.length - 2])) f.linkId = ids[ids.length - 2];
        String cid = attr(fe, "catalogueId");
        f.catalogueId = cid != null && d.cats.containsKey(cid) ? cid : (parent != null ? parent.catalogueId : r.catalogueId);
        f.roster = r;
        f.parent = parent;
        if (parent == null) r.forces.add(f); else parent.children.add(f);
        res.forces++;
        e.changed();
        Element ss = child(fe, "selections");
        if (ss != null) for (Element se : kids(ss, "selection")) importRoot(se, f);
        Element cf = child(fe, "forces");
        if (cf != null) for (Element ch : kids(cf, "force")) importForce(ch, f, r);
    }

    private void importRoot(Element se, Force f) {
        String[] ids = split(attr(se, "entryId"));
        String target = ids[ids.length - 1];
        String prim = null;
        Element cats = child(se, "categories");
        if (cats != null) for (Element c : kids(cats, "category")) {
            if ("true".equals(attr(c, "primary"))) { String[] ci = split(attr(c, "entryId")); prim = ci[ci.length - 1]; }
        }
        Node best = null;
        int bestScore = -1;
        for (Node n : e.rootOptions(f)) {
            if (!target.equals(n.opt.id())) continue;
            int sc = 1;
            if (n.opt.linkId() != null && contains(ids, n.opt.linkId())) sc += 2;
            if (prim != null && prim.equals(n.place)) sc += 4;
            if (sc > bestScore) { bestScore = sc; best = n; }
        }
        Sel s = new Sel();
        if (best != null) {
            s.entryId = best.opt.id();
            s.linkId = best.opt.linkId();
            s.cat = best.place;
        } else {
            Data.Cat cat = d.cats.get(f.catalogueId);
            Opt found = null;
            if (cat != null) for (Opt o : cat.roots) if (target.equals(o.id()) && (found == null || (o.linkId() != null && contains(ids, o.linkId())))) found = o;
            if (found == null) { res.missing.add("«" + attr(se, "name") + "»"); return; }
            s.entryId = found.id();
            s.linkId = found.linkId();
            s.cat = prim;
        }
        s.number = number(se);
        s.force = f;
        f.sels.add(s);
        res.units++;
        e.changed();
        importChildren(se, s);
    }

    private void importChildren(Element se, Sel parent) {
        Element ss = child(se, "selections");
        if (ss == null) return;
        List<Node> flat = new ArrayList<Node>();
        flatten(e.options(e.entryOf(parent), e.linkOf(parent), parent), flat);
        for (Element ce : kids(ss, "selection")) {
            String[] ids = split(attr(ce, "entryId"));
            String target = ids[ids.length - 1];
            String[] gids = split(attr(ce, "entryGroupId"));
            String gid = gids.length == 0 || gids[0].isEmpty() ? null : gids[gids.length - 1];
            Node best = null;
            int bestScore = -1;
            for (Node n : flat) {
                if (n.isGroup() || !target.equals(n.opt.id())) continue;
                int sc = 1;
                if (n.opt.linkId() != null && contains(ids, n.opt.linkId())) sc += 2;
                if (gid != null && n.opt.groups != null && n.opt.groups.contains(gid)) sc += 4;
                if (sc > bestScore) { bestScore = sc; best = n; }
            }
            if (best == null) { res.missing.add("«" + attr(ce, "name") + "» в «" + e.effSel(rootOf(parent)).name + "»"); continue; }
            Sel s = new Sel();
            s.entryId = best.opt.id();
            s.linkId = best.opt.linkId();
            s.groups = new ArrayList<String>(best.opt.groups == null ? new ArrayList<String>() : best.opt.groups);
            s.number = number(ce);
            s.force = parent.force;
            s.parent = parent;
            parent.children.add(s);
            e.changed();
            importChildren(ce, s);
        }
    }

    private static Sel rootOf(Sel s) { while (s.parent != null) s = s.parent; return s; }

    private static void flatten(List<Node> in, List<Node> out) {
        for (Node n : in) {
            out.add(n);
            if (n.isGroup()) flatten(n.children, out);
        }
    }

    private static int number(Element el) {
        try { return Math.max(1, (int) Math.round(Double.parseDouble(attr(el, "number")))); } catch (Exception ex) { return 1; }
    }

    private static boolean contains(String[] a, String v) { for (String s : a) if (s.equals(v)) return true; return false; }

    private static String[] split(String s) { return s == null ? new String[]{""} : s.split("::"); }

    private static String local(Element el) {
        String n = el.getTagName();
        int i = n.indexOf(':');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    private static String attr(Element el, String n) { return el.hasAttribute(n) ? el.getAttribute(n) : null; }

    private static Element child(Element el, String name) {
        for (org.w3c.dom.Node c = el.getFirstChild(); c != null; c = c.getNextSibling())
            if (c instanceof Element && name.equals(local((Element) c))) return (Element) c;
        return null;
    }

    private static List<Element> kids(Element el, String name) {
        List<Element> out = new ArrayList<Element>();
        for (org.w3c.dom.Node c = el.getFirstChild(); c != null; c = c.getNextSibling())
            if (c instanceof Element && name.equals(local((Element) c))) out.add((Element) c);
        return out;
    }
}
