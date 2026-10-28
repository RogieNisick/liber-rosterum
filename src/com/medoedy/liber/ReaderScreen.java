package com.medoedy.liber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.medoedy.liber.Ui.dp;

/** Читалка PDF: страница с масштабом двумя пальцами, листание свайпом, подсветка найденного, переходы по совпадениям. */
final class ReaderScreen extends Screen {
    final Library.Book book;
    int page;
    final String query;
    final List<Library.Hit> hits;
    int hitIdx;

    ParcelFileDescriptor pfd;
    PdfRenderer renderer;
    int count = 1;
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    PageView pv;
    TextView pageLabel, hitLabel, star;
    SeekBar bar;

    ReaderScreen(MainActivity a, Library.Book book, int page, String query, List<Library.Hit> hits, int hitIdx) {
        super(a);
        this.book = book;
        this.page = page;
        this.query = query;
        this.hits = hits == null ? new ArrayList<Library.Hit>() : hits;
        this.hitIdx = hitIdx;
    }

    boolean open() {
        if (renderer != null) return true;
        try {
            File f = Library.fileOf(a, book);
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            count = renderer.getPageCount();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void close() {
        exec.execute(new Runnable() {
            public void run() {
                try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
                try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
                renderer = null;
            }
        });
        exec.shutdown();
    }

    @Override
    boolean handleBack() { close(); return false; }

    View build() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = Ui.col(a);
        c.setPadding(dp(10), dp(6), dp(10), dp(8));
        LinearLayout hd = a.header(book.shortName, null, theme, true);
        LinearLayout hrow = (LinearLayout) hd.getChildAt(0);
        star = Ui.button(a, Bookmarks.has(a, book.id, page) ? "★" : "☆", Ui.steelButton(theme, dp(8)), Ui.GOLD, 20, new View.OnClickListener() {
            public void onClick(View v) { toggleMark(); }
        });
        hrow.addView(star, Ui.lpm(dp(44), dp(44), dp(6), 0, 0, 0));
        if (!book.custom && !book.toc.isEmpty()) {
            hrow.addView(Ui.button(a, "≡", Ui.steelButton(theme, dp(8)), Ui.GOLD, 22, new View.OnClickListener() {
                public void onClick(View v) { showToc(); }
            }), Ui.lpm(dp(44), dp(44), dp(6), 0, 0, 0));
        }
        if (!book.custom) {
            hrow.addView(Ui.button(a, "⌕", Ui.steelButton(theme, dp(8)), Ui.GOLD, 22, new View.OnClickListener() {
                public void onClick(View v) { LibraryScreen ls = new LibraryScreen(a); ls.only = book; a.show(ls); }
            }), Ui.lpm(dp(44), dp(44), dp(6), 0, 0, 0));
        }
        c.addView(hd);

        if (!open()) {
            TextView t = Ui.text(a, "Файл книги не открылся. Загрузите PDF заново в «Библиотеке».", 15, Theme.body, Ui.RED_BAD);
            t.setPadding(0, dp(20), 0, 0);
            c.addView(t);
            f.addView(c);
            return f;
        }

        // совпадения
        if (!hits.isEmpty()) {
            LinearLayout hr = Ui.row(a);
            hr.addView(Ui.button(a, "‹", Ui.steelButton(theme, dp(6)), Ui.GOLD, 20, new View.OnClickListener() {
                public void onClick(View v) { jumpHit(-1); }
            }), Ui.lp(dp(44), dp(38)));
            hitLabel = Ui.text(a, "", 13, Theme.bodyBold, Ui.BONE);
            hitLabel.setGravity(Gravity.CENTER);
            hr.addView(hitLabel, Ui.weight(1));
            hr.addView(Ui.button(a, "›", Ui.steelButton(theme, dp(6)), Ui.GOLD, 20, new View.OnClickListener() {
                public void onClick(View v) { jumpHit(1); }
            }), Ui.lp(dp(44), dp(38)));
            c.addView(hr, Ui.lpm(-1, -2, 0, dp(2), 0, dp(4)));
        }

        pv = new PageView(a, this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, 0);
        lp.weight = 1;
        c.addView(pv, lp);

        LinearLayout nav = Ui.row(a);
        nav.addView(Ui.button(a, "‹", Ui.steelButton(theme, dp(6)), Ui.GOLD, 22, new View.OnClickListener() {
            public void onClick(View v) { go(page - 1); }
        }), Ui.lp(dp(46), dp(42)));
        bar = new SeekBar(a);
        bar.setMax(Math.max(0, count - 1));
        bar.setProgress(page);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) pageLabel.setText((v + 1) + " / " + count); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { go(s.getProgress()); }
        });
        nav.addView(bar, Ui.weight(1));
        pageLabel = Ui.text(a, "", 13, Theme.bodyBold, Ui.GOLD);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askPage(); } });
        nav.addView(pageLabel, Ui.lp(dp(76), -2));
        nav.addView(Ui.button(a, "›", Ui.steelButton(theme, dp(6)), Ui.GOLD, 22, new View.OnClickListener() {
            public void onClick(View v) { go(page + 1); }
        }), Ui.lp(dp(46), dp(42)));
        c.addView(nav, Ui.lpm(-1, -2, 0, dp(6), 0, 0));
        f.addView(c);
        updateLabels();
        pv.post(new Runnable() { public void run() { pv.showPage(page); } });
        Bookmarks.recent(a, book.id, page);
        return f;
    }

    void updateLabels() {
        if (pageLabel != null && renderer != null) pageLabel.setText((page + 1) + " / " + count);
        if (bar != null) bar.setProgress(page);
        if (star != null) star.setText(Bookmarks.has(a, book.id, page) ? "★" : "☆");
        if (hitLabel != null) {
            int at = -1;
            for (int i = 0; i < hits.size(); i++) if (hits.get(i).book == book && hits.get(i).page == page) { at = i; break; }
            if (at >= 0) hitIdx = at;
            hitLabel.setText("«" + query + "» · " + (at >= 0 ? (at + 1) + " из " + hits.size() : "совпадений: " + hits.size()));
        }
    }

    void go(int p) {
        if (renderer == null) return;
        p = Math.max(0, Math.min(count - 1, p));
        if (p == page) { updateLabels(); return; }
        page = p;
        Sfx.play("page", 0.4f, 1.1f);
        pv.showPage(page);
        updateLabels();
        Bookmarks.recent(a, book.id, page);
        Eggs.pageRead(a);
    }

    void jumpHit(int d) {
        if (hits.isEmpty()) return;
        hitIdx = (hitIdx + d + hits.size()) % hits.size();
        Library.Hit h = hits.get(hitIdx);
        if (h.book != book) {
            close();
            a.replaceTop(new ReaderScreen(a, h.book, h.page, query, hits, hitIdx));
            return;
        }
        go(h.page);
    }

    void toggleMark() {
        boolean on = Bookmarks.toggle(a, book.id, page, titleFor(page));
        Ui.haptic(Ui.CLICK);
        Sfx.play(on ? "seal" : "tick");
        a.toast(on ? "Закладка: стр. " + (page + 1) : "Закладка снята");
        updateLabels();
    }

    String titleFor(int p) {
        String t = null;
        for (Library.Toc x : book.toc) if (x.page <= p) t = x.title; else break;
        return t == null ? book.shortName : t;
    }

    void showToc() {
        LinearLayout body = a.openSheet("Оглавление", book.title, theme);
        for (final Library.Toc t : book.toc) {
            boolean big = t.size >= 2.2f;
            a.sheetItem(body, big ? "banner" : null, t.title, "стр. " + (t.page + 1), theme, false, new View.OnClickListener() {
                public void onClick(View v) { a.closeSheet(); go(t.page); }
            });
        }
    }

    void askPage() {
        LinearLayout body = a.openSheet("Перейти на страницу", theme);
        final android.widget.EditText e = a.input("", "1 – " + count, true);
        body.addView(e, Ui.lpm(-1, -2, 0, dp(4), 0, dp(8)));
        body.addView(Ui.button(a, "Перейти", Ui.brass(dp(8)), 0xFF1A140C, 16, new View.OnClickListener() {
            public void onClick(View v) {
                try { go(Integer.parseInt(e.getText().toString().trim()) - 1); } catch (Exception ignored) { }
                a.closeSheet();
            }
        }), Ui.lp(-1, dp(48)));
    }

    // ================================================================ страница
    static final class PageView extends View {
        final ReaderScreen rs;
        Bitmap base, detail;
        final Matrix detailM = new Matrix();
        float pw = 1, ph = 1;          // размер страницы в пунктах
        float scale = 1, tx, ty;       // масштаб относительно «по ширине» и сдвиг
        float fit = 1;                 // пикселей на пункт при масштабе 1
        int gen;
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ScaleGestureDetector sg;
        final GestureDetector gd;
        List<Integer> marks = new ArrayList<Integer>();
        Runnable pendingDetail;

        PageView(Context c, ReaderScreen rs) {
            super(c);
            this.rs = rs;
            hl.setColor(0x55FFC83C);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    float ns = Math.max(1f, Math.min(6f, scale * d.getScaleFactor()));
                    float k = ns / scale;
                    tx = d.getFocusX() - (d.getFocusX() - tx) * k;
                    ty = d.getFocusY() - (d.getFocusY() - ty) * k;
                    scale = ns;
                    clamp();
                    detail = null;
                    invalidate();
                    return true;
                }
                public void onScaleEnd(ScaleGestureDetector d) { scheduleDetail(); }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                public boolean onDown(MotionEvent e) { return true; }
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    tx -= dx; ty -= dy; clamp(); detail = null; invalidate(); scheduleDetail(); return true;
                }
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (scale <= 1.05f && Math.abs(vx) > Math.abs(vy) * 1.3f && Math.abs(vx) > dp(600)) {
                        rs.go(rs.page + (vx < 0 ? 1 : -1));
                        return true;
                    }
                    return false;
                }
                public boolean onDoubleTap(MotionEvent e) {
                    float ns = scale > 1.5f ? 1f : 2.5f;
                    float k = ns / scale;
                    tx = e.getX() - (e.getX() - tx) * k;
                    ty = e.getY() - (e.getY() - ty) * k;
                    scale = ns;
                    clamp();
                    detail = null;
                    invalidate();
                    scheduleDetail();
                    return true;
                }
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (scale > 1.05f) return false;
                    float w = getWidth();
                    if (e.getX() < w * 0.18f) { rs.go(rs.page - 1); return true; }
                    if (e.getX() > w * 0.82f) { rs.go(rs.page + 1); return true; }
                    return false;
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            sg.onTouchEvent(e);
            gd.onTouchEvent(e);
            if (scale > 1.02f) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        void clamp() {
            float w = getWidth(), h = getHeight();
            float cw = pw * fit * scale, ch = ph * fit * scale;
            if (cw <= w) tx = (w - cw) / 2; else tx = Math.max(w - cw, Math.min(0, tx));
            if (ch <= h) ty = (h - ch) / 2; else ty = Math.max(h - ch, Math.min(0, ty));
        }

        void showPage(final int idx) {
            final int g = ++gen;
            final int w = getWidth(), h = getHeight();
            if (w == 0) { post(new Runnable() { public void run() { showPage(idx); } }); return; }
            marks = rs.book.custom || idx >= rs.book.pages ? new ArrayList<Integer>() : Library.matchLines(rs.book, idx, rs.query);
            rs.exec.execute(new Runnable() {
                public void run() {
                    if (rs.renderer == null || g != gen) return;
                    try {
                        PdfRenderer.Page pg = rs.renderer.openPage(idx);
                        float ppw = pg.getWidth(), pph = pg.getHeight();
                        float f = w / ppw; // по ширине
                        int bw = Math.min(2000, Math.round(ppw * f * 1.4f)), bh = Math.round(bw * pph / ppw);
                        final Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                        bm.eraseColor(Color.WHITE);
                        pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        pg.close();
                        final float ff = f;
                        post(new Runnable() {
                            public void run() {
                                if (g != gen) return;
                                pw = ppw; ph = pph; fit = ff;
                                base = bm; detail = null;
                                scale = 1; tx = 0; ty = 0;
                                clamp();
                                // показать первое совпадение
                                if (!marks.isEmpty() && !rs.book.custom) {
                                    short[] bx = rs.book.boxes[idx];
                                    int li = marks.get(0);
                                    float y = bx[li * 4 + 1] / 1000f * ph * fit;
                                    if (ph * fit > getHeight()) { ty = -(y - getHeight() * 0.3f); clamp(); }
                                }
                                invalidate();
                            }
                        });
                    } catch (Exception e) { /* страница не отрисовалась */ }
                }
            });
        }

        void scheduleDetail() {
            if (pendingDetail != null) removeCallbacks(pendingDetail);
            pendingDetail = new Runnable() { public void run() { renderDetail(); } };
            postDelayed(pendingDetail, 220);
        }

        /** Чёткая отрисовка видимой области при увеличении. */
        void renderDetail() {
            if (scale < 1.2f || base == null) return;
            final int g = gen, w = getWidth(), h = getHeight(), idx = rs.page;
            final float s = fit * scale, ox = tx, oy = ty;
            rs.exec.execute(new Runnable() {
                public void run() {
                    if (rs.renderer == null || g != gen) return;
                    try {
                        PdfRenderer.Page pg = rs.renderer.openPage(idx);
                        final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bm.eraseColor(Color.TRANSPARENT);
                        Matrix m = new Matrix();
                        m.setScale(s, s);
                        m.postTranslate(ox, oy);
                        pg.render(bm, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        pg.close();
                        post(new Runnable() {
                            public void run() {
                                if (g != gen || tx != ox || ty != oy || fit * scale != s) return;
                                detail = bm;
                                invalidate();
                            }
                        });
                    } catch (Exception ignored) { }
                }
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            if (base == null) {
                p.setColor(Ui.GOLD_DIM);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(dp(15));
                c.drawText("Листаю страницы…", getWidth() / 2f, getHeight() / 2f, p);
                return;
            }
            float cw = pw * fit * scale, ch = ph * fit * scale;
            RectF dst = new RectF(tx, ty, tx + cw, ty + ch);
            p.setColor(0x66000000);
            c.drawRect(dst.left + dp(2), dst.top + dp(3), dst.right + dp(2), dst.bottom + dp(3), p);
            p.setColor(Color.WHITE);
            c.drawRect(dst, p);
            c.drawBitmap(base, null, dst, p);
            if (detail != null) c.drawBitmap(detail, 0, 0, p);
            if (!marks.isEmpty() && !rs.book.custom && rs.page < rs.book.pages) {
                short[] bx = rs.book.boxes[rs.page];
                for (int li : marks) {
                    if (li * 4 + 3 >= bx.length) continue;
                    float x0 = tx + bx[li * 4] / 1000f * cw, y0 = ty + bx[li * 4 + 1] / 1000f * ch;
                    float x1 = tx + bx[li * 4 + 2] / 1000f * cw, y1 = ty + bx[li * 4 + 3] / 1000f * ch;
                    c.drawRoundRect(new RectF(x0 - dp(2), y0 - dp(1), x1 + dp(2), y1 + dp(1)), dp(3), dp(3), hl);
                }
            }
        }
    }
}
