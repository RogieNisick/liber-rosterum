package com.medoedy.liber;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Библиотека книг правил: поисковый индекс (распознанный текст страниц, оглавление) лежит в assets/library,
 * сами PDF игрок один раз загружает в приложение (они хранятся во внутренней памяти).
 */
final class Library {
    private Library() { }

    static final class Toc {
        int page; float size; String title;
    }

    static final class Book {
        String id, title, shortName, match;
        int pages;
        long origSize;
        boolean custom;     // своя книга без индекса
        File file;          // для своих книг
        final List<Toc> toc = new ArrayList<Toc>();
        String[] norm;      // нормализованный текст страницы (строки через \n)
        int[][] lineStart;  // смещения начала строк в norm
        short[][] boxes;    // по 4 числа на строку, 0..1000
        String[][] lines;   // исходные строки
    }

    static final class Hit {
        Book book;
        int page;
        int score;
        int line = -1;
        String snippet;
        boolean heading;
    }

    static final List<Book> books = new ArrayList<Book>();
    static volatile boolean loaded;
    private static boolean loading;
    private static final List<Runnable> waiters = new ArrayList<Runnable>();

    /** Загрузить индекс (в фоне); done — в главном потоке. */
    static void load(final MainActivity a, final Runnable done) {
        synchronized (Library.class) {
            if (loaded) { if (done != null) a.h.post(done); return; }
            if (done != null) waiters.add(done);
            if (loading) return;
            loading = true;
        }
        new Thread(new Runnable() {
            public void run() {
                List<Book> out = new ArrayList<Book>();
                try {
                    String[] files = a.getAssets().list("library");
                    java.util.Arrays.sort(files);
                    for (String f : files) {
                        if (!f.endsWith(".idx.gz")) continue;
                        try { out.add(read(a.getAssets().open("library/" + f))); } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
                Collections.sort(out, new Comparator<Book>() {
                    public int compare(Book x, Book y) { return order(x.id) - order(y.id); }
                });
                // свои книги (без индекса)
                File dir = dir(a);
                File[] fs = dir.listFiles();
                if (fs != null) for (File f : fs) {
                    if (!f.getName().startsWith("x_") || !f.getName().endsWith(".pdf")) continue;
                    Book b = new Book();
                    b.custom = true;
                    b.file = f;
                    b.id = f.getName().substring(0, f.getName().length() - 4);
                    b.title = f.getName().substring(2, f.getName().length() - 4).replace('_', ' ');
                    b.shortName = b.title;
                    b.pages = pageCount(f);
                    out.add(b);
                }
                synchronized (Library.class) {
                    books.clear();
                    books.addAll(out);
                    loaded = true;
                    loading = false;
                    for (Runnable r : waiters) a.h.post(r);
                    waiters.clear();
                }
            }
        }).start();
    }

    static final String[] ORDER = {"rulebook", "faq", "astartes", "hereticus", "auxilia", "mechanicum", "questoris", "legacies", "talons", "militia", "daemons", "blackshields", "inductii", "saturn", "isstvan", "firstwave"};

    static int order(String id) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(id)) return i;
        return 99;
    }

    private static Book read(InputStream raw) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), "UTF-8"), 1 << 16);
        Book b = new Book();
        String ln;
        int cur = -1;
        List<String> pl = new ArrayList<String>();
        List<short[]> pb = new ArrayList<short[]>();
        while ((ln = r.readLine()) != null) {
            if (ln.isEmpty()) continue;
            char k = ln.charAt(0);
            String[] p = ln.split("\t", -1);
            if (k == 'B') {
                b.id = p[1]; b.title = p[2]; b.pages = Integer.parseInt(p[3]); b.shortName = p[4]; b.match = p[5];
                b.origSize = Long.parseLong(p[6]);
                b.norm = new String[b.pages];
                b.lineStart = new int[b.pages][];
                b.boxes = new short[b.pages][];
                b.lines = new String[b.pages][];
            } else if (k == 'T') {
                Toc t = new Toc();
                t.page = Integer.parseInt(p[1]); t.size = Float.parseFloat(p[2]); t.title = p[3];
                b.toc.add(t);
            } else if (k == 'P') {
                if (cur >= 0) finishPage(b, cur, pl, pb);
                cur = Integer.parseInt(p[1]);
                pl.clear(); pb.clear();
            } else if (k == 'L') {
                pb.add(new short[]{Short.parseShort(p[1]), Short.parseShort(p[2]), Short.parseShort(p[3]), Short.parseShort(p[4])});
                pl.add(p[5]);
            }
        }
        if (cur >= 0) finishPage(b, cur, pl, pb);
        for (int i = 0; i < b.pages; i++) if (b.norm[i] == null) { b.norm[i] = ""; b.lineStart[i] = new int[0]; b.boxes[i] = new short[0]; b.lines[i] = new String[0]; }
        r.close();
        return b;
    }

    private static void finishPage(Book b, int page, List<String> pl, List<short[]> pb) {
        if (page < 0 || page >= b.pages) return;
        StringBuilder sb = new StringBuilder();
        int[] st = new int[pl.size()];
        short[] bx = new short[pl.size() * 4];
        for (int i = 0; i < pl.size(); i++) {
            st[i] = sb.length();
            sb.append(norm(pl.get(i))).append('\n');
            System.arraycopy(pb.get(i), 0, bx, i * 4, 4);
        }
        b.norm[page] = sb.toString();
        b.lineStart[page] = st;
        b.boxes[page] = bx;
        b.lines[page] = pl.toArray(new String[0]);
    }

    /** Нормализация для поиска: нижний регистр, только буквы/цифры, одиночные пробелы. */
    static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean sp = true;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c == '’' || c == '\'') continue;
            if (c == '&') { if (!sp) b.append(' '); b.append("and "); sp = true; continue; }
            if (Character.isLetterOrDigit(c)) { b.append(c); sp = false; }
            else if (!sp) { b.append(' '); sp = true; }
        }
        int n = b.length();
        if (n > 0 && b.charAt(n - 1) == ' ') b.setLength(n - 1);
        return b.toString();
    }

    // ================================================================ файлы
    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "books");
        d.mkdirs();
        return d;
    }

    static File fileOf(Context c, Book b) { return b.custom ? b.file : new File(dir(c), b.id + ".pdf"); }

    static boolean has(Context c, Book b) { File f = fileOf(c, b); return f.exists() && f.length() > 1000; }

    static int loadedCount(Context c) {
        int n = 0;
        for (Book b : books) if (has(c, b)) n++;
        return n;
    }

    static Book byId(String id) {
        for (Book b : books) if (b.id.equals(id)) return b;
        return null;
    }

    static int pageCount(File f) {
        ParcelFileDescriptor pfd = null;
        android.graphics.pdf.PdfRenderer r = null;
        try {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            r = new android.graphics.pdf.PdfRenderer(pfd);
            return r.getPageCount();
        } catch (Exception e) {
            return -1;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        }
    }

    /** Определить книгу по числу страниц и словам в имени файла. */
    static Book identify(String name, int pages) {
        String n = norm(name == null ? "" : name);
        Book best = null;
        int bestScore = 0;
        for (Book b : books) {
            if (b.custom) continue;
            int s = 0;
            if (b.pages == pages) s += 10;
            for (String w : b.match.split(" ")) if (!w.isEmpty() && n.contains(w)) s += 4;
            if (s > bestScore && (b.pages == pages || s >= 8)) { best = b; bestScore = s; }
        }
        return bestScore >= 10 ? best : null;
    }

    interface Progress { void step(String msg); void done(String result); }

    /** Импорт выбранных файлов: PDF или ZIP с PDF внутри. */
    static void importUris(final MainActivity a, final List<Uri> uris, final Progress pr) {
        new Thread(new Runnable() {
            public void run() {
                int ok = 0, unknown = 0, bad = 0;
                StringBuilder names = new StringBuilder();
                for (Uri u : uris) {
                    String name = displayName(a, u);
                    try {
                        InputStream in = a.getContentResolver().openInputStream(u);
                        if (name.toLowerCase().endsWith(".zip")) {
                            ZipInputStream z = new ZipInputStream(in);
                            ZipEntry en;
                            while ((en = z.getNextEntry()) != null) {
                                if (en.isDirectory() || !en.getName().toLowerCase().endsWith(".pdf")) continue;
                                String nm = new File(en.getName()).getName();
                                post(a, pr, "Распаковка: " + nm);
                                int r = store(a, z, nm, names);
                                if (r == 1) ok++; else if (r == 2) unknown++; else bad++;
                            }
                            z.close();
                        } else {
                            post(a, pr, "Копирую: " + name);
                            int r = store(a, in, name, names);
                            if (r == 1) ok++; else if (r == 2) unknown++; else bad++;
                            in.close();
                        }
                    } catch (Exception e) { bad++; }
                }
                synchronized (Library.class) { loaded = false; }
                final String res = "Книг с поиском: " + ok + (unknown > 0 ? ", своих без индекса: " + unknown : "") + (bad > 0 ? ", не открылось: " + bad : "") + (names.length() > 0 ? "\n" + names : "");
                load(a, new Runnable() { public void run() { pr.done(res); } });
            }
        }).start();
    }

    private static void post(MainActivity a, final Progress pr, final String m) {
        a.h.post(new Runnable() { public void run() { pr.step(m); } });
    }

    /** 1 — книга из индекса, 2 — своя книга, 0 — ошибка. */
    private static int store(MainActivity a, InputStream in, String name, StringBuilder names) throws Exception {
        File tmp = new File(dir(a), "incoming.tmp");
        OutputStream out = new FileOutputStream(tmp);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        int pages = pageCount(tmp);
        if (pages <= 0) { tmp.delete(); return 0; }
        Book b = identify(name, pages);
        File dst;
        int res;
        if (b != null) { dst = new File(dir(a), b.id + ".pdf"); res = 1; names.append("✓ ").append(b.title).append('\n'); }
        else {
            String base = name.replaceAll("(?i)\\.pdf$", "").replaceAll("[^\\p{L}\\p{N}]+", "_");
            dst = new File(dir(a), "x_" + base + ".pdf");
            res = 2;
            names.append("• ").append(name).append(" — без индекса\n");
        }
        dst.delete();
        if (!tmp.renameTo(dst)) { tmp.delete(); return 0; }
        return res;
    }

    static String displayName(Context c, Uri u) {
        String n = null;
        try {
            android.database.Cursor cur = c.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null) { if (cur.moveToFirst()) n = cur.getString(0); cur.close(); }
        } catch (Exception ignored) { }
        if (n == null) n = u.getLastPathSegment();
        return n == null ? "book.pdf" : n;
    }

    // ================================================================ поиск
    /** Поиск по всем книгам: заголовки выше, затем точная фраза, затем все слова на странице. */
    static List<Hit> search(String query, Book only, int limit) {
        List<Hit> out = new ArrayList<Hit>();
        String q = norm(query);
        if (q.length() < 2) return out;
        String[] words = q.split(" ");
        for (Book b : books) {
            if (b.custom || b.norm == null) continue;
            if (only != null && only != b) continue;
            boolean[] headPage = new boolean[b.pages];
            int[] headScore = new int[b.pages];
            for (Toc t : b.toc) {
                String nt = norm(t.title);
                if (t.page < 0 || t.page >= b.pages) continue;
                int s = 0;
                if (nt.equals(q)) s = 1000;
                else if (nt.startsWith(q + " ") || nt.endsWith(" " + q)) s = 500;
                else if (nt.contains(q)) s = 300;
                if (s > headScore[t.page]) { headScore[t.page] = s; headPage[t.page] = true; }
            }
            for (int p = 0; p < b.pages; p++) {
                String t = b.norm[p];
                if (t == null || t.isEmpty()) continue;
                int idx = t.indexOf(q);
                int score = headScore[p];
                int line = -1;
                if (idx >= 0) {
                    int cnt = 0, from = idx;
                    while (from >= 0 && cnt < 20) { cnt++; from = t.indexOf(q, from + q.length()); }
                    score += 50 + cnt * 6;
                    line = lineAt(b.lineStart[p], idx);
                    // фраза — целым словом
                    boolean wordStart = idx == 0 || !Character.isLetterOrDigit(t.charAt(idx - 1));
                    int end = idx + q.length();
                    boolean wordEnd = end >= t.length() || !Character.isLetterOrDigit(t.charAt(end));
                    if (wordStart && wordEnd) score += 25;
                } else if (words.length > 1) {
                    boolean all = true;
                    for (String w : words) if (w.length() > 1 && t.indexOf(w) < 0) { all = false; break; }
                    if (!all) { if (score == 0) continue; }
                    else {
                        score += 10;
                        line = lineAt(b.lineStart[p], t.indexOf(words[0]));
                    }
                } else if (score == 0) continue;
                if (score <= 0) continue;
                if (b.id.equals("faq")) score += 5;
                Hit h = new Hit();
                h.book = b; h.page = p; h.score = score; h.line = line; h.heading = headPage[p] && headScore[p] >= 300;
                h.snippet = snippet(b, p, line);
                out.add(h);
            }
        }
        Collections.sort(out, new Comparator<Hit>() {
            public int compare(Hit x, Hit y) {
                if (x.score != y.score) return y.score - x.score;
                int o = order(x.book.id) - order(y.book.id);
                return o != 0 ? o : x.page - y.page;
            }
        });
        if (out.size() > limit) out = new ArrayList<Hit>(out.subList(0, limit));
        return out;
    }

    static int lineAt(int[] starts, int idx) {
        if (starts == null || starts.length == 0 || idx < 0) return -1;
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (starts[mid] <= idx) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    static String snippet(Book b, int p, int line) {
        String[] ls = b.lines[p];
        if (ls == null || ls.length == 0) return "";
        if (line < 0) line = 0;
        StringBuilder s = new StringBuilder();
        for (int i = Math.max(0, line - 1); i <= Math.min(ls.length - 1, line + 1); i++) {
            if (s.length() > 0) s.append(' ');
            s.append(ls[i]);
        }
        String r = s.toString();
        return r.length() > 260 ? r.substring(0, 260) + "…" : r;
    }

    /** Строки страницы, где есть слова запроса (для подсветки). */
    static List<Integer> matchLines(Book b, int p, String query) {
        List<Integer> out = new ArrayList<Integer>();
        if (b.custom || b.norm == null || query == null) return out;
        String q = norm(query);
        if (q.length() < 2) return out;
        String t = b.norm[p];
        int[] st = b.lineStart[p];
        int from = t.indexOf(q);
        if (from >= 0) {
            while (from >= 0) {
                int l = lineAt(st, from);
                int l2 = lineAt(st, from + q.length() - 1);
                for (int i = l; i <= l2; i++) if (!out.contains(i)) out.add(i);
                from = t.indexOf(q, from + q.length());
            }
            return out;
        }
        String[] ws = q.split(" ");
        for (int i = 0; i < st.length; i++) {
            int end = i + 1 < st.length ? st[i + 1] : t.length();
            String ln = t.substring(st[i], end);
            for (String w : ws) if (w.length() > 2 && ln.contains(w)) { out.add(i); break; }
        }
        return out;
    }

    /** Лучшая страница для названия (юнита, правила, гамбита). */
    static List<Hit> lookup(String name) { return lookup(name, null); }

    /** То же, но книга фракции (Hereticus для предателей, Astartes для лоялистов и т.д.) — выше. */
    static List<Hit> lookup(String name, String faction) {
        String clean = name == null ? "" : name.replaceAll("\\(.*?\\)", "").trim();
        List<Hit> hs = search(clean, null, 60);
        final String pref = bookFor(faction);
        if (pref != null) {
            for (Hit h : hs) if (h.book.id.equals(pref)) h.score += 40;
            Collections.sort(hs, new Comparator<Hit>() {
                public int compare(Hit x, Hit y) { return x.score != y.score ? y.score - x.score : order(x.book.id) - order(y.book.id); }
            });
        }
        return hs;
    }

    static final String[] TRAITORS = {"Emperor's Children", "Iron Warriors", "Night Lords", "World Eaters", "Death Guard", "Thousand Sons", "Sons of Horus", "Word Bearers", "Alpha Legion"};

    static String bookFor(String faction) {
        if (faction == null || faction.isEmpty()) return null;
        String f = faction.toLowerCase();
        for (String t : TRAITORS) if (f.contains(t.toLowerCase())) return "hereticus";
        if (f.matches("^[ivxl]+ - .*") || f.contains("shattered")) return "astartes";
        if (f.contains("mechanicum") || f.contains("skitarii")) return "mechanicum";
        if (f.contains("titanicus") || f.contains("questoris") || f.contains("knight")) return "questoris";
        if (f.contains("solar")) return "auxilia";
        if (f.contains("militia")) return "militia";
        if (f.contains("custodes") || f.contains("psykana") || f.contains("assassin")) return "talons";
        if (f.contains("daemon")) return "daemons";
        if (f.contains("blackshield")) return "blackshields";
        return null;
    }
}
