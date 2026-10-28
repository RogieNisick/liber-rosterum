package com.medoedy.liber.engine;

import java.util.List;
import java.util.Map;

/** Вариант выбора: запись или группа, ссылка (entryLink), путь групп от родителя. */
public final class Opt {
    public final Map<String, Object> entry;
    public final Map<String, Object> link;
    public final List<String> groups;
    public final boolean group;

    public Opt(Map<String, Object> entry, Map<String, Object> link, List<String> groups) {
        this(entry, link, groups, false);
    }

    public Opt(Map<String, Object> entry, Map<String, Object> link, List<String> groups, boolean group) {
        this.entry = entry;
        this.link = link;
        this.groups = groups;
        this.group = group;
    }

    public String id() { return Data.str(entry, "id"); }
    public String linkId() { return link == null ? null : Data.str(link, "id"); }
    public String type() { return Data.str(entry, "type"); }
}
