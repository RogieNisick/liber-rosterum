package com.medoedy.liber;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import static com.medoedy.liber.Ui.dp;

/** Библиотека: поиск по всем книгам правил, полка, закладки, загрузка PDF. */
final class LibraryScreen extends Screen {
    Library.Book only;
    String query = "";
    LinearLayout results;
    EditText search;
    Runnable pending;
    int gen;

    LibraryScreen(MainActivity a) { super(a); }

    LibraryScreen(MainActivity a, String q) { super(a); query = q == null ? "" : q; }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        int pages = 0;
        for (Library.Book b : Library.books) if (!b.custom) pages += b.pages;
        String sub = only != null ? "Поиск в книге: " + only.shortName
                : Library.loaded ? Library.books.size() + " " + MainActivity.plural(Library.books.size(), "книга", "книги", "книг") + " · " + pages + " стр. в поиске" : "Открываю архив…";
        c.addView(a.header(only != null ? "Поиск" : "Библиотека", sub, theme, true));
        search = a.input(query, only != null ? "Найти в «" + only.shortName + "»…" : "Правило, юнит, оружие, гамбит…", false);
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(Editable s) {
                query = s.toString();
                if (pending != null) a.h.removeCallbacks(pending);
                pending = new Runnable() { public void run() { fill(); } };
                a.h.postDelayed(pending, 280);
            }
        });
        c.addView(search, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));
        results = Ui.col(a);
        c.addView(results);
        f.addView(a.scroll(c));
        if (!Library.loaded) Library.load(a, new Runnable() { public void run() { rebuild(); } });
        else fill();
        if (only == null) a.h.postDelayed(new Runnable() { public void run() { if (a.stack.peek() == LibraryScreen.this) Guide.tip(a, "library", "Глеб, это библиотека: все книги правил с поиском по каждой странице. Пиши название правила, юнита или оружия — я покажу книгу и страницу, а найденные строки подсвечу. В карточке юнита и в правилах есть кнопка 📖 — она сразу открывает нужную страницу. Сначала загрузи PDF кнопкой «Загрузить книги»."); } }, 700);
        return f;
    }

    @Override
    void refresh() { if (results != null) fill(); }

    void fill() {
        if (results == null || !Library.loaded) return;
        results.removeAllViews();
        String q = query.trim();
        if (q.length() >= 2) { searchNow(q); return; }
        if (only != null) { tocOf(only); return; }
        shelf();
    }

    // ================================================================ поиск
    void searchNow(final String q) {
        final int g = ++gen;
        TextView wait = Ui.text(a, "Ищу «" + q + "»…", 14, Theme.body, Ui.BONE_DIM);
        results.addView(wait);
        new Thread(new Runnable() {
            public void run() {
                final List<Library.Hit> hits = Library.search(q, only, 120);
                a.h.post(new Runnable() {
                    public void run() {
                        if (g != gen) return;
                        results.removeAllViews();
                        if (hits.isEmpty()) {
                            results.addView(Ui.text(a, "Ничего не нашлось. Попробуйте короче или другое написание: текст сканов распознан автоматически и может содержать опечатки.", 14, Theme.body, Ui.BONE_DIM));
                            return;
                        }
                        results.addView(a.label("НАЙДЕНО: " + hits.size() + (hits.size() >= 120 ? "+" : ""), Ui.GOLD));
                        for (int i = 0; i < hits.size(); i++) hitRow(hits, i, q);
                    }
                });
            }
        }).start();
    }

    void hitRow(final List<Library.Hit> hits, final int i, final String q) {
        final Library.Hit h = hits.get(i);
        boolean have = Library.has(a, h.book);
        LinearLayout row = Ui.col(a);
        row.setBackground(Ui.states(new Ui.Parchment(dp(6), false, 0, false), new Ui.Parchment(dp(6), false, 0, true)));
        row.setPadding(dp(12), dp(8), dp(12), dp(9));
        LinearLayout top = Ui.row(a);
        TextView bt = Ui.text(a, (h.heading ? "⚜ " : "") + h.book.shortName, 13, Theme.bodyBold, Ui.INK_RED);
        bt.setSingleLine(true);
        top.addView(bt, Ui.weight(1));
        top.addView(Ui.text(a, (have ? "" : "нет файла · ") + "стр. " + (h.page + 1), 13, Theme.bodyBold, have ? Ui.INK : Ui.INK_DIM));
        row.addView(top);
        String sec = section(h.book, h.page);
        if (sec != null) {
            TextView st = Ui.text(a, sec, 14, Theme.names, Ui.INK);
            st.setSingleLine(true);
            row.addView(st);
        }
        TextView sn = Ui.text(a, "", 13, Theme.body, Ui.INK_DIM);
        sn.setText(highlight(h.snippet, q));
        sn.setMaxLines(3);
        row.addView(sn);
        row.setClickable(true);
        Ui.press(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.haptic(Ui.CLICK);
                if (!Library.has(a, h.book)) { missing(h.book); return; }
                a.show(new ReaderScreen(a, h.book, h.page, q, hits, i));
            }
        });
        results.addView(row, Ui.lpm(-1, -2, 0, dp(3), 0, dp(3)));
    }

    static String section(Library.Book b, int p) {
        String t = null;
        for (Library.Toc x : b.toc) { if (x.page <= p) t = x.title; else break; }
        return t;
    }

    static CharSequence highlight(String text, String q) {
        SpannableString s = new SpannableString(text == null ? "" : text);
        String low = s.toString().toLowerCase();
        for (String w : Library.norm(q).split(" ")) {
            if (w.length() < 2) continue;
            int from = low.indexOf(w);
            while (from >= 0) {
                s.setSpan(new StyleSpan(Typeface.BOLD), from, from + w.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new BackgroundColorSpan(0x66FFC83C), from, from + w.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new ForegroundColorSpan(Ui.INK), from, from + w.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from = low.indexOf(w, from + w.length());
            }
        }
        return s;
    }

    // ================================================================ полка
    void shelf() {
        int have = Library.loadedCount(a);
        if (have == 0) {
            LinearLayout info = Ui.col(a);
            info.setBackground(Ui.parchment());
            info.setPadding(dp(14), dp(12), dp(14), dp(12));
            info.addView(Ui.text(a, "Книги ещё не загружены", 17, Theme.names, Ui.INK));
            TextView t = Ui.text(a, "Поиск уже работает по всем книгам — он покажет книгу и страницу. Чтобы открывать сами страницы, один раз загрузите PDF: архив «Liber Library» из чата или ваши исходные файлы (можно все сразу). Книги узнаются автоматически.", 14, Theme.body, Ui.INK);
            t.setPadding(0, dp(6), 0, 0);
            info.addView(t);
            results.addView(info, Ui.lpm(-1, -2, 0, dp(4), 0, dp(6)));
        }
        results.addView(Ui.button(a, "＋  Загрузить книги (PDF или ZIP)", have == 0 ? Ui.brass(dp(8)) : Ui.steelButton(theme, dp(8)), have == 0 ? 0xFF1A140C : Ui.BONE, 16, new View.OnClickListener() {
            public void onClick(View v) { importBooks(); }
        }), Ui.lpm(-1, dp(50), 0, dp(4), 0, dp(6)));

        List<Bookmarks.M> rec = Bookmarks.recents(a);
        boolean any = false;
        for (final Bookmarks.M m : rec) {
            final Library.Book b = Library.byId(m.book);
            if (b == null || !Library.has(a, b)) continue;
            if (!any) { results.addView(a.label("ПРОДОЛЖИТЬ ЧТЕНИЕ", Ui.GOLD)); any = true; }
            String sec = section(b, m.page);
            a.sheetItem(results, "book", b.shortName, "стр. " + (m.page + 1) + (sec != null ? " · " + sec : ""), theme, false, new View.OnClickListener() {
                public void onClick(View v) { a.show(new ReaderScreen(a, b, m.page, null, null, 0)); }
            });
        }
        List<Bookmarks.M> marks = Bookmarks.all(a);
        if (!marks.isEmpty()) {
            results.addView(a.label("ЗАКЛАДКИ", Ui.GOLD));
            for (final Bookmarks.M m : marks) {
                final Library.Book b = Library.byId(m.book);
                if (b == null) continue;
                LinearLayout it = a.sheetItem(results, "star", m.title == null || m.title.isEmpty() ? b.shortName : m.title, b.shortName + " · стр. " + (m.page + 1), theme, false, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (!Library.has(a, b)) { missing(b); return; }
                        a.show(new ReaderScreen(a, b, m.page, null, null, 0));
                    }
                });
                it.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { Bookmarks.toggle(a, m.book, m.page, m.title); Ui.haptic(Ui.HEAVY); fill(); return true; }
                });
            }
        }

        results.addView(a.label("ПОЛКА", Ui.GOLD));
        for (final Library.Book b : Library.books) {
            boolean ok = Library.has(a, b);
            String s = (ok ? "✓ " : "⤓ нет файла · ") + b.pages + " стр." + (b.custom ? " · без поиска" : b.toc.isEmpty() ? "" : " · разделов: " + b.toc.size());
            a.sheetItem(results, b.custom ? "scroll" : "book", b.title, s, theme, false, new View.OnClickListener() {
                public void onClick(View v) { openBook(b); }
            });
        }
    }

    void openBook(final Library.Book b) {
        if (!Library.has(a, b)) { missing(b); return; }
        if (b.custom || b.toc.isEmpty()) { a.show(new ReaderScreen(a, b, Bookmarks.lastPage(a, b.id), null, null, 0)); return; }
        LibraryScreen ls = new LibraryScreen(a);
        ls.only = b;
        a.show(ls);
    }

    /** Оглавление книги (когда поиск в одной книге пуст). */
    void tocOf(final Library.Book b) {
        final int last = Bookmarks.lastPage(a, b.id);
        results.addView(Ui.button(a, last > 0 ? "▶  Продолжить: стр. " + (last + 1) : "▶  Читать с начала", Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
            public void onClick(View v) {
                if (!Library.has(a, b)) { missing(b); return; }
                a.show(new ReaderScreen(a, b, last, null, null, 0));
            }
        }), Ui.lpm(-1, dp(50), 0, dp(2), 0, dp(6)));
        results.addView(a.label("ОГЛАВЛЕНИЕ", Ui.GOLD));
        for (final Library.Toc t : b.toc) {
            boolean big = t.size >= 2.2f;
            TextView tv = Ui.text(a, t.title, big ? 16 : 14, big ? Theme.names : Theme.body, big ? Ui.GOLD : Ui.BONE);
            LinearLayout row = Ui.row(a);
            row.setPadding(dp(big ? 4 : 16), dp(big ? 9 : 6), dp(6), dp(big ? 7 : 6));
            row.addView(tv, Ui.weight(1));
            row.addView(Ui.text(a, String.valueOf(t.page + 1), 13, Theme.bodyBold, Ui.GOLD_DIM));
            row.setClickable(true);
            Ui.press(row);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!Library.has(a, b)) { missing(b); return; }
                    a.show(new ReaderScreen(a, b, t.page, null, null, 0));
                }
            });
            results.addView(row);
        }
    }

    void missing(Library.Book b) {
        a.confirmInfo("«" + b.title + "» не загружена", "Поиск знает, на какой странице это написано, но самого PDF в приложении нет. Загрузите книгу — достаточно один раз.", "Загрузить", theme, new Runnable() {
            public void run() { importBooks(); }
        });
    }

    void importBooks() {
        a.pickBooks(new Library.Progress() {
            TextView st;
            public void step(String msg) {
                if (st == null) {
                    LinearLayout body = a.openSheet("Загрузка книг", "Копирую в память приложения…", theme);
                    st = Ui.text(a, msg, 14, Theme.body, Ui.BONE);
                    body.addView(st);
                } else st.setText(msg);
            }
            public void done(String result) {
                a.closeSheetNow();
                LinearLayout body = a.openSheet("Книги загружены", theme);
                body.addView(Ui.text(a, result, 14, Theme.body, Ui.BONE));
                Sfx.play("seal");
                rebuild();
            }
        });
    }

    // ================================================================ «открыть в книге»
    /** Найти название в книгах и открыть лучшую страницу (с переходами по остальным совпадениям). */
    static void openFor(final MainActivity a, final String name) { openFor(a, name, null); }

    static void openFor(final MainActivity a, final String name, final String faction) {
        if (name == null || name.trim().isEmpty()) return;
        Ui.haptic(Ui.CLICK);
        Library.load(a, new Runnable() {
            public void run() {
                new Thread(new Runnable() {
                    public void run() {
                        final List<Library.Hit> hits = Library.lookup(name, faction);
                        a.h.post(new Runnable() {
                            public void run() {
                                if (hits.isEmpty()) { a.toast("В книгах не нашлось «" + name + "»"); return; }
                                Library.Hit h = hits.get(0);
                                if (!Library.has(a, h.book)) { a.show(new LibraryScreen(a, name)); return; }
                                Sfx.play("page", 0.7f, 1f);
                                a.show(new ReaderScreen(a, h.book, h.page, name.replaceAll("\\(.*?\\)", "").trim(), hits, 0));
                            }
                        });
                    }
                }).start();
            }
        });
    }

    static TextView bookButton(final MainActivity a, final String name, Theme t, String label) { return bookButton(a, name, null, t, label); }

    static TextView bookButton(final MainActivity a, final String name, final String faction, Theme t, String label) {
        return Ui.button(a, label, Ui.steelButton(t, dp(6)), Ui.GOLD, label.length() > 3 ? 15 : 18, new View.OnClickListener() {
            public void onClick(View v) { a.closeSheetNow(); openFor(a, name, faction); }
        });
    }

    /** Открыть конкретную страницу книги (если книга загружена), иначе — поиск по названию. */
    static void openPage(final MainActivity a, final String bookId, final int page, final String query) {
        Library.load(a, new Runnable() {
            public void run() {
                Library.Book b = Library.byId(bookId);
                if (b != null && Library.has(a, b)) { Sfx.play("page", 0.7f, 1f); a.show(new ReaderScreen(a, b, page, query, null, 0)); }
                else if (b != null) a.show(new LibraryScreen(a, query));
                else a.toast("Книга не найдена");
            }
        });
    }
}
