package com.medoedy.liber;

import java.util.ArrayList;
import java.util.List;

/** Закладки и недавно открытые страницы книг (в настройках приложения). */
final class Bookmarks {
    private Bookmarks() { }

    static final class M { String book, title; int page; }

    static List<M> all(MainActivity a) { return parse(a.prefs.getString("book_marks", "")); }

    static List<M> recents(MainActivity a) { return parse(a.prefs.getString("book_recent", "")); }

    private static List<M> parse(String raw) {
        List<M> out = new ArrayList<M>();
        for (String ln : raw.split("\n")) {
            String[] p = ln.split("\t", -1);
            if (p.length < 2) continue;
            M m = new M();
            m.book = p[0];
            try { m.page = Integer.parseInt(p[1]); } catch (Exception e) { continue; }
            m.title = p.length > 2 ? p[2] : "";
            out.add(m);
        }
        return out;
    }

    private static String join(List<M> ms) {
        StringBuilder b = new StringBuilder();
        for (M m : ms) b.append(m.book).append('\t').append(m.page).append('\t').append(m.title == null ? "" : m.title.replace('\t', ' ').replace('\n', ' ')).append('\n');
        return b.toString();
    }

    static boolean has(MainActivity a, String book, int page) {
        for (M m : all(a)) if (m.book.equals(book) && m.page == page) return true;
        return false;
    }

    /** true — закладка поставлена, false — снята. */
    static boolean toggle(MainActivity a, String book, int page, String title) {
        List<M> ms = all(a);
        for (M m : ms) if (m.book.equals(book) && m.page == page) { ms.remove(m); a.prefs.edit().putString("book_marks", join(ms)).apply(); return false; }
        M m = new M();
        m.book = book; m.page = page; m.title = title;
        ms.add(0, m);
        a.prefs.edit().putString("book_marks", join(ms)).apply();
        return true;
    }

    /** Запомнить, где остановились в книге. */
    static void recent(MainActivity a, String book, int page) {
        List<M> ms = recents(a);
        for (int i = ms.size() - 1; i >= 0; i--) if (ms.get(i).book.equals(book)) ms.remove(i);
        M m = new M();
        m.book = book; m.page = page; m.title = "";
        ms.add(0, m);
        while (ms.size() > 6) ms.remove(ms.size() - 1);
        a.prefs.edit().putString("book_recent", join(ms)).apply();
    }

    static int lastPage(MainActivity a, String book) {
        for (M m : recents(a)) if (m.book.equals(book)) return m.page;
        return 0;
    }
}
