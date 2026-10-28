package com.medoedy.liber;

import com.medoedy.liber.engine.Json;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/** Новости Ереси: RSS/Atom официального сайта и новостных ресурсов, раз в сутки, с фильтром по теме. */
final class News {
    private News() { }

    /** {название, адрес ленты, нужен ли фильтр по ключевым словам}. */
    static final String[][] SOURCES = {
            {"Google News", "https://news.google.com/rss/search?q=%22Horus+Heresy%22+OR+%22Warhammer+30k%22&hl=en-US&gl=US&ceid=US:en", "1"},
            {"Goonhammer · Horus Heresy", "https://www.goonhammer.com/category/horus-heresy/feed/", "0"},
            {"Bell of Lost Souls", "https://www.belloflostsouls.net/tag/horus-heresy/feed", "1"},
            {"Spikey Bits", "https://spikeybits.com/category/horus-heresy/feed/", "1"},
            {"Natfka (Faeit 212)", "https://natfka.blogspot.com/feeds/posts/default", "1"},
            {"Reddit r/Horusheresy", "https://www.reddit.com/r/Horusheresy/top/.rss?t=week", "0"},
            {"Reddit r/Warhammer30k", "https://www.reddit.com/r/Warhammer30k/top/.rss?t=week", "0"},
    };

    static final String[] KEYS = {"horus heresy", "heresy", "30k", "legiones astartes", "age of darkness"};
    static final String[] NOT = {"sigmar", "aos ", "age of sigmar", "kill team", "necromunda", "blood bowl", "old world", "40k", "40,000", "space marine 2"};

    static final class Item { String source, title, link, text, img; long date; }

    static volatile boolean busy;

    static File file(MainActivity a) { return new File(a.getFilesDir(), "news.json"); }

    @SuppressWarnings("unchecked")
    static List<Item> load(MainActivity a) {
        List<Item> out = new ArrayList<Item>();
        try {
            File f = file(a);
            if (!f.exists()) return out;
            for (Object o : (List<Object>) Json.parse(Store.read(f))) {
                Map<String, Object> m = (Map<String, Object>) o;
                Item it = new Item();
                it.source = (String) m.get("s"); it.title = (String) m.get("t"); it.link = (String) m.get("l"); it.text = (String) m.get("x");
                it.date = m.get("d") instanceof Number ? ((Number) m.get("d")).longValue() : 0;
                it.img = (String) m.get("i");
                boolean filtered = !(it.source.startsWith("Goonhammer") || it.source.startsWith("Reddit"));
                if (filtered && !onTopic(it.title + " " + (it.text == null ? "" : it.text))) continue; // старые записи — по новому фильтру
                out.add(it);
            }
        } catch (Exception ignored) { }
        return dedupe(out);
    }

    /** Одна и та же новость из разных источников: одинаковая ссылка, картинка или почти тот же заголовок. */
    static List<Item> dedupe(List<Item> in) {
        List<Item> out = new ArrayList<Item>();
        List<Set<String>> words = new ArrayList<Set<String>>();
        Set<String> links = new HashSet<String>(), imgs = new HashSet<String>();
        for (Item it : in) {
            Set<String> w = words(it.title);
            boolean dup = (it.link != null && links.contains(it.link)) || (it.img != null && imgs.contains(it.img));
            for (int i = 0; !dup && i < words.size(); i++) {
                Set<String> o = words.get(i);
                int common = 0;
                for (String x : w) if (o.contains(x)) common++;
                int min = Math.min(w.size(), o.size());
                if (min > 0 && common >= Math.max(2, Math.ceil(min * 0.6))) dup = true;
            }
            if (dup) continue;
            out.add(it);
            words.add(w);
            if (it.link != null) links.add(it.link);
            if (it.img != null) imgs.add(it.img);
        }
        return out;
    }

    static final Set<String> STOP = new HashSet<String>(java.util.Arrays.asList("the", "a", "an", "of", "for", "and", "to", "in", "on", "with", "from", "is", "new", "warhammer", "community", "horus", "heresy", "bell", "lost", "souls", "ixbt", "games", "goonhammer", "spikey", "bits", "reddit", "ign", "revealed", "s"));

    static Set<String> words(String t) {
        Set<String> w = new HashSet<String>();
        if (t == null) return w;
        String x = t.toLowerCase().replaceAll("\\s[-–|]\\s[^-–|]+$", ""); // « - Источник» в конце заголовка Google News
        for (String p : x.split("[^\\p{L}\\p{N}]+")) if (p.length() > 1 && !STOP.contains(p)) w.add(p);
        return w;
    }

    static void save(MainActivity a, List<Item> items) {
        List<Object> out = new ArrayList<Object>();
        for (Item it : items) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("s", it.source); m.put("t", it.title); m.put("l", it.link); m.put("x", it.text); m.put("d", (double) it.date); m.put("i", it.img);
            out.add(m);
        }
        try { Store.write(file(a), Json.write(out)); } catch (Exception ignored) { }
    }

    /** Запомнить настоящую ссылку и картинку (после разбора Google News / og:image). */
    static synchronized void remember(MainActivity a, String oldLink, String link, String img) {
        List<Item> all = load(a);
        for (Item it : all) if (it.link != null && it.link.equals(oldLink)) { it.link = link; if (img != null) it.img = img; }
        save(a, all);
    }

    /** Есть ли непрочитанные (новее последнего открытия экрана новостей). */
    static boolean unseen(MainActivity a) {
        long seen = a.prefs.getLong("news_seen", 0);
        for (Item it : load(a)) if (it.date > seen) return true;
        return false;
    }

    /** Раз в сутки (или принудительно) — обновить ленты в фоне. */
    static void refresh(final MainActivity a, boolean force, final Runnable done) {
        long last = a.prefs.getLong("news_fetched", 0);
        if (busy || (!force && System.currentTimeMillis() - last < 20L * 3600 * 1000)) return; // без колбэка — иначе экран перестраивался бы по кругу
        busy = true;
        new Thread(new Runnable() {
            public void run() {
                List<Item> all = new ArrayList<Item>(load(a));
                Set<String> have = new HashSet<String>();
                for (Item it : all) have.add(it.link);
                int ok = 0;
                for (String[] src : SOURCES) {
                    try {
                        for (Item it : fetch(src[0], src[1], "1".equals(src[2]))) if (it.link != null && have.add(it.link)) all.add(it);
                        ok++;
                    } catch (Throwable ignored) { }
                }
                Collections.sort(all, new Comparator<Item>() { public int compare(Item x, Item y) { return Long.compare(y.date, x.date); } });
                while (all.size() > 150) all.remove(all.size() - 1);
                if (ok > 0) {
                    save(a, all);
                    a.prefs.edit().putLong("news_fetched", System.currentTimeMillis()).apply();
                }
                busy = false;
                if (done != null) a.h.post(done);
            }
        }, "news").start();
    }

    static List<Item> fetch(String source, String url, boolean filter) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", url.contains("reddit.com") ? "android:com.medoedy.liber:v4.16 (by /u/liber_rosterum)" : "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        byte[] body = Store.readAll(c.getInputStream());
        c.disconnect();
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        List<Item> out = new ArrayList<Item>();
        NodeList items = doc.getElementsByTagName("item");
        boolean atom = items.getLength() == 0;
        if (atom) items = doc.getElementsByTagName("entry");
        for (int i = 0; i < items.getLength() && i < 40; i++) {
            Element el = (Element) items.item(i);
            Item it = new Item();
            it.source = source;
            it.title = clean(text(el, "title"));
            if (atom) {
                NodeList ls = el.getElementsByTagName("link");
                for (int k = 0; k < ls.getLength(); k++) {
                    Element l = (Element) ls.item(k);
                    String rel = l.getAttribute("rel");
                    if (rel.isEmpty() || rel.equals("alternate")) { it.link = l.getAttribute("href"); break; }
                }
                it.text = clean(first(text(el, "summary"), text(el, "content")));
                it.date = date(first(text(el, "published"), text(el, "updated")));
            } else {
                it.link = text(el, "link");
                it.text = clean(text(el, "description"));
                it.date = date(first(text(el, "pubDate"), text(el, "dc:date")));
            }
            it.img = image(el);
            if (it.title == null || it.link == null) continue;
            if (it.text != null && it.text.length() > 280) it.text = it.text.substring(0, 280).trim() + "…";
            if (filter && !onTopic(it.title + " " + (it.text == null ? "" : it.text))) continue;
            out.add(it);
        }
        return out;
    }

    /** Картинка из ленты: enclosure / media:content / media:thumbnail / первая <img> в тексте. */
    static String image(Element el) {
        String[] tags = {"media:content", "media:thumbnail", "enclosure"};
        for (String t : tags) {
            NodeList n = el.getElementsByTagName(t);
            for (int i = 0; i < n.getLength(); i++) {
                Element x = (Element) n.item(i);
                String u = x.getAttribute("url");
                String ty = x.getAttribute("type");
                if (!u.isEmpty() && (ty.isEmpty() || ty.startsWith("image"))) return u;
            }
        }
        String[] bodies = {"content:encoded", "description", "content", "summary"};
        for (String b : bodies) {
            String h = text(el, b);
            if (h == null) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<img[^>]+src=[\"']([^\"']+)").matcher(h);
            if (m.find()) return m.group(1).replace("&amp;", "&");
        }
        return null;
    }

    /** Ссылка Google News ведёт на промежуточную страницу — достаём адрес настоящей статьи. */
    static String resolveGoogle(String link) {
        if (link == null || !link.contains("news.google.com")) return link;
        try {
            String id = link.replaceAll("^.*/articles/", "").replaceAll("[?#].*$", "");
            // старый формат: адрес зашит в base64
            try {
                byte[] raw = android.util.Base64.decode(id, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
                String sraw = new String(raw, "ISO-8859-1");
                int h = sraw.indexOf("http");
                if (h >= 0 && !sraw.startsWith("\u0008\u0013\"AU_yqL")) {
                    String u = sraw.substring(h).replaceAll("[\\x00-\\x20\\x7f-\\xff].*$", "");
                    if (u.startsWith("http") && !u.contains("AU_yqL")) return u;
                }
            } catch (Exception ignored) { }
            // новый формат: подпись и время со страницы, затем запрос batchexecute
            HttpURLConnection c = (HttpURLConnection) new URL("https://news.google.com/articles/" + id).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            String page = new String(Store.readAll(c.getInputStream()), "UTF-8");
            c.disconnect();
            java.util.regex.Matcher ms = java.util.regex.Pattern.compile("data-n-a-sg=\"([^\"]+)\"").matcher(page);
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("data-n-a-ts=\"([^\"]+)\"").matcher(page);
            if (!ms.find() || !mt.find()) return link;
            String sg = ms.group(1), ts = mt.group(1);
            String inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"" + id + "\"," + ts + ",\"" + sg + "\"]";
            String freq = "[[[\"Fbv4je\"," + jsonStr(inner) + ",null,\"generic\"]]]";
            HttpURLConnection p = (HttpURLConnection) new URL("https://news.google.com/_/DotsSplashUi/data/batchexecute").openConnection();
            p.setRequestMethod("POST");
            p.setDoOutput(true);
            p.setConnectTimeout(8000);
            p.setReadTimeout(10000);
            p.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            p.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/124.0 Mobile");
            byte[] body = ("f.req=" + java.net.URLEncoder.encode(freq, "UTF-8")).getBytes("UTF-8");
            p.getOutputStream().write(body);
            String resp = new String(Store.readAll(p.getInputStream()), "UTF-8");
            p.disconnect();
            java.util.regex.Matcher mu = java.util.regex.Pattern.compile("garturlres\\\\\",\\\\\"(https?://[^\\\\\"]+)").matcher(resp);
            if (mu.find()) return mu.group(1);
        } catch (Exception ignored) { }
        return link;
    }

    static String jsonStr(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    /** Нет картинки в ленте — берём og:image со страницы статьи. */
    static String ogImage(String link) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            if (c.getResponseCode() != 200) return null;
            java.io.InputStream in = c.getInputStream();
            byte[] buf = new byte[65536];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            c.disconnect();
            String h = new String(buf, 0, n, "UTF-8");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]*content=[\"']([^\"']+)").matcher(h);
            if (m.find()) return m.group(1).replace("&amp;", "&");
            m = java.util.regex.Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']og:image").matcher(h);
            if (m.find()) return m.group(1).replace("&amp;", "&");
        } catch (Exception ignored) { }
        return null;
    }

    static boolean onTopic(String s) {
        String t = s.toLowerCase();
        for (String k : NOT) if (t.contains(k) && !t.contains("horus heresy")) return false;
        for (String k : KEYS) if (t.contains(k)) return true;
        return false;
    }

    static String first(String a, String b) { return a != null && !a.trim().isEmpty() ? a : b; }

    static String text(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        if (n.getLength() == 0) return null;
        Node x = n.item(0);
        return x.getTextContent();
    }

    static String clean(String s) {
        if (s == null) return null;
        String t = s.replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&#8217;", "’").replace("&#8211;", "–").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("submitted by .*$", "").trim();
        return t.isEmpty() ? null : t;
    }

    static long date(String s) {
        if (s == null) return 0;
        s = s.trim();
        String[] fmts = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String f : fmts) {
            try { return new SimpleDateFormat(f, Locale.ENGLISH).parse(s).getTime(); } catch (Exception ignored) { }
        }
        return 0;
    }
}
