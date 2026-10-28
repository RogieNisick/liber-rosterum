package com.medoedy.liber.engine;

import com.medoedy.liber.engine.Roster.Force;
import com.medoedy.liber.engine.Roster.Sel;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Экспорт в формат BattleScribe (.ros — XML, .rosz — он же в zip): открывается в BattleScribe и New Recruit. */
public final class RosExport {
    private RosExport() { }

    public static String ros(Data d, Roster r) {
        Engine e = new Engine(d, r);
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        String gsId = Data.str(d.gs, "id"), gsName = Data.str(d.gs, "name");
        b.append("<roster id=\"").append(id()).append("\" name=\"").append(x(r.name)).append("\" battleScribeVersion=\"2.03\" gameSystemId=\"").append(x(gsId))
                .append("\" gameSystemName=\"").append(x(gsName)).append("\" gameSystemRevision=\"").append(d.revision).append("\" xmlns=\"http://www.battlescribe.net/schema/rosterSchema\">\n");
        b.append("  <costs>\n    <cost name=\"pts\" typeId=\"").append(x(d.pointsId)).append("\" value=\"").append(Engine.fmt(e.rosterCost(d.pointsId))).append("\"/>\n  </costs>\n");
        b.append("  <costLimits>\n    <costLimit name=\"pts\" typeId=\"").append(x(d.pointsId)).append("\" value=\"").append(Engine.fmt(r.limit)).append("\"/>\n  </costLimits>\n");
        b.append("  <forces>\n");
        for (Force f : r.forces) force(d, e, f, b, "    ");
        b.append("  </forces>\n</roster>\n");
        return b.toString();
    }

    public static byte[] rosz(Data d, Roster r, String fileName) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        z.putNextEntry(new ZipEntry(fileName));
        z.write(ros(d, r).getBytes("UTF-8"));
        z.closeEntry();
        z.close();
        return bo.toByteArray();
    }

    private static void force(Data d, Engine e, Force f, StringBuilder b, String ind) {
        Data.Cat cat = d.cats.get(f.catalogueId);
        b.append(ind).append("<force id=\"").append(id()).append("\" name=\"").append(x(e.effForce(f).name)).append("\" entryId=\"")
                .append(x((f.linkId != null ? f.linkId + "::" : "") + f.entryId)).append("\" catalogueId=\"").append(x(f.catalogueId))
                .append("\" catalogueRevision=\"1\" catalogueName=\"").append(x(cat == null ? "" : cat.name)).append("\">\n");
        if (!f.sels.isEmpty()) {
            b.append(ind).append("  <selections>\n");
            for (Sel s : f.sels) sel(d, e, s, b, ind + "    ", true);
            b.append(ind).append("  </selections>\n");
        }
        if (!f.children.isEmpty()) {
            b.append(ind).append("  <forces>\n");
            for (Force c : f.children) force(d, e, c, b, ind + "    ");
            b.append(ind).append("  </forces>\n");
        }
        b.append(ind).append("</force>\n");
    }

    private static void sel(Data d, Engine e, Sel s, StringBuilder b, String ind, boolean root) {
        Engine.Eff ef = e.effSel(s);
        String type = Data.str(e.entryOf(s), "type");
        if (type == null) type = "upgrade";
        b.append(ind).append("<selection id=\"").append(id()).append("\" name=\"").append(x(ef.name)).append("\" entryId=\"")
                .append(x((s.linkId != null ? s.linkId + "::" : "") + s.entryId)).append("\"");
        List<String> g = s.groups;
        if (g != null && !g.isEmpty()) b.append(" entryGroupId=\"").append(x(g.get(g.size() - 1))).append("\"");
        b.append(" number=\"").append(s.number).append("\" type=\"").append(x(type)).append("\">\n");
        double own = ef.cost(d.pointsId) * s.number;
        b.append(ind).append("  <costs>\n").append(ind).append("    <cost name=\"pts\" typeId=\"").append(x(d.pointsId)).append("\" value=\"").append(Engine.fmt(own)).append("\"/>\n").append(ind).append("  </costs>\n");
        if (root && s.cat != null) {
            b.append(ind).append("  <categories>\n").append(ind).append("    <category id=\"").append(id()).append("\" name=\"").append(x(d.categoryName(s.cat)))
                    .append("\" entryId=\"").append(x(s.cat)).append("\" primary=\"true\"/>\n").append(ind).append("  </categories>\n");
        }
        if (!s.children.isEmpty()) {
            b.append(ind).append("  <selections>\n");
            for (Sel c : s.children) sel(d, e, c, b, ind + "    ", false);
            b.append(ind).append("  </selections>\n");
        }
        b.append(ind).append("</selection>\n");
    }

    private static String id() { String u = UUID.randomUUID().toString(); return u.substring(0, 4) + "-" + u.substring(4, 8) + "-" + u.substring(9, 13) + "-" + u.substring(14, 18); }

    private static String x(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
