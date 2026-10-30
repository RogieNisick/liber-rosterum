package com.medoedy.liber;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.medoedy.liber.Ui.dp;

/** Пасхалки: печать Омниссии, сожжение, салют, Экстерминатус, ладан, паутина, тема Хаоса, архивариус. */
final class Eggs {
    private Eggs() { }

    static final Random rnd = new Random();

    /** Слой поверх всего приложения. */
    static FrameLayout layer(MainActivity a) { return a.root; }

    /** Базовый вид-эффект: сам перерисовывается и сам удаляется по окончании. */
    abstract static class Fx extends View {
        final long start = android.os.SystemClock.uptimeMillis();
        final long dur;
        Fx(MainActivity a, long dur) { super(a); this.dur = dur; setClickable(false); }
        float t() { return (android.os.SystemClock.uptimeMillis() - start) / 1000f; }
        abstract void paint(Canvas c, float t);
        @Override
        protected void onDraw(Canvas c) {
            float t = t();
            paint(c, t);
            if (t * 1000 < dur) postInvalidateOnAnimation();
            else post(new Runnable() { public void run() { finish(); } });
        }
        void finish() { if (getParent() != null) ((ViewGroup) getParent()).removeView(this); }
    }

    static void add(MainActivity a, View v) { layer(a).addView(v, new FrameLayout.LayoutParams(-1, -1)); }

    // ================================================================ салют из черепов
    static void skullFireworks(final MainActivity a) {
        final int[] cols = {0xFFE8C66A, 0xFFF2E6C8, 0xFF7CFF9A, 0xFFFF8A5A, 0xFFB9A2FF};
        final List<float[]> parts = new ArrayList<float[]>(); // x,y,vx,vy,born,col,size,kind(0 ракета,1 череп)
        add(a, new Fx(a, 5200) {
            long last = 0;
            int launched = 0;
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            void paint(Canvas c, float t) {
                int w = getWidth(), h = getHeight();
                if (w == 0) return;
                // запуск ракет
                while (launched < 6 && t > launched * 0.55f) {
                    float x = w * (0.15f + rnd.nextFloat() * 0.7f);
                    parts.add(new float[]{x, h, (rnd.nextFloat() - 0.5f) * dp(60), -h * (0.95f + rnd.nextFloat() * 0.35f), t, cols[rnd.nextInt(cols.length)], dp(3), 0, h * (0.18f + rnd.nextFloat() * 0.3f)});
                    launched++;
                    Sfx.play("tick", 0.5f, 0.6f);
                }
                float dt = last == 0 ? 0.016f : Math.min(0.05f, (android.os.SystemClock.uptimeMillis() - last) / 1000f);
                last = android.os.SystemClock.uptimeMillis();
                List<float[]> add = new ArrayList<float[]>();
                for (int i = parts.size() - 1; i >= 0; i--) {
                    float[] q = parts.get(i);
                    q[0] += q[2] * dt;
                    q[1] += q[3] * dt;
                    if (q[7] == 0) {
                        q[3] += h * 0.55f * dt;
                        p.setColor((int) q[5]);
                        p.setAlpha(255);
                        c.drawCircle(q[0], q[1], q[6], p);
                        p.setAlpha(90);
                        c.drawCircle(q[0], q[1] + dp(8), q[6] * 0.8f, p);
                        if (q[1] <= q[8] || q[3] >= 0) {
                            parts.remove(i);
                            int n = 16 + rnd.nextInt(6);
                            for (int k = 0; k < n; k++) {
                                double ang = Math.PI * 2 * k / n + rnd.nextFloat() * 0.2;
                                float sp = dp(90) + rnd.nextFloat() * dp(80);
                                add.add(new float[]{q[0], q[1], (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp, t, q[5], dp(5) + rnd.nextFloat() * dp(4), 1, 0});
                            }
                            Sfx.play("w_grenade", 0.35f, 1.3f + rnd.nextFloat() * 0.4f);
                            Ui.haptic(Ui.TICK);
                        }
                    } else {
                        float age = t - q[4];
                        if (age > 1.8f) { parts.remove(i); continue; }
                        q[3] += dp(140) * dt;
                        q[2] *= 0.985f;
                        int al = (int) (255 * Math.max(0, 1 - age / 1.8f));
                        Ui.drawSkull(c, q[0], q[1], q[6], Color.argb(al, Color.red((int) q[5]), Color.green((int) q[5]), Color.blue((int) q[5])));
                    }
                }
                parts.addAll(add);
            }
        });
        Sfx.play("fanfare", 0.8f, 1f);
        a.h.postDelayed(new Runnable() { public void run() { Sfx.play("bell"); } }, 3600);
    }

    // ================================================================ печать «Совершенство Омниссии»
    static void perfectSeal(final MainActivity a) {
        final FrameLayout box = new FrameLayout(a);
        View seal = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas c) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) * 0.92f;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new android.graphics.RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, 0xFFF6DE8A, 0xFF8A5A16, android.graphics.Shader.TileMode.CLAMP));
                // зубчатый край сургуча
                Path wax = new Path();
                for (int i = 0; i <= 48; i++) {
                    double ang = Math.PI * 2 * i / 48;
                    float rr = r * (i % 2 == 0 ? 1f : 0.93f);
                    float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
                    if (i == 0) wax.moveTo(x, y); else wax.lineTo(x, y);
                }
                c.drawPath(wax, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.03f);
                p.setColor(0xFF5A3A0C);
                c.drawCircle(cx, cy, r * 0.8f, p);
                c.drawCircle(cx, cy, r * 0.56f, p);
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(r * 0.15f);
                p.setTypeface(Theme.namesBlack != null ? Theme.namesBlack : Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                Path ring = new Path();
                ring.addCircle(cx, cy, r * 0.63f, Path.Direction.CW);
                p.setLetterSpacing(0.12f);
                c.drawTextOnPath("СОВЕРШЕНСТВО ✦ ОМНИССИИ ✦", ring, 0, 0, p);
                Ui.drawSkull(c, cx, cy, r * 0.36f, 0xFF4A2E08);
            }
        };
        int s = dp(220);
        box.addView(seal, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        add(a, box);
        seal.setScaleX(3f);
        seal.setScaleY(3f);
        seal.setAlpha(0f);
        seal.setRotation(-25);
        seal.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(-8).setDuration(320).setInterpolator(new OvershootInterpolator(1.4f)).withEndAction(new Runnable() {
            public void run() {
                Sfx.play("seal", 1f, 0.8f);
                Ui.haptic(Ui.HEAVY);
                a.h.postDelayed(new Runnable() { public void run() { Sfx.play("bell"); } }, 180);
                a.toast("Ровно в лимит. Омниссия доволен.");
            }
        }).start();
        final Runnable gone = new Runnable() {
            public void run() {
                if (box.getParent() == null) return;
                box.animate().alpha(0f).setDuration(450).withEndAction(new Runnable() { public void run() { if (box.getParent() != null) ((ViewGroup) box.getParent()).removeView(box); } }).start();
            }
        };
        box.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { gone.run(); } });
        a.h.postDelayed(gone, 2600);
    }

    /** Ростер: «ровно в лимит» и числа-приметы. Срабатывает только при изменении очков. */
    private static final Map<String, Double> lastCost = new HashMap<String, Double>();

    static void rosterCost(MainActivity a, String rosterId, double pts, double limit, int errors) {
        if (rosterId == null) return;
        Double prev = lastCost.put(rosterId, pts);
        if (prev == null || prev == pts) return;
        long p = Math.round(pts);
        if (limit > 0 && Math.abs(pts - limit) < 0.01 && errors == 0) { perfectSeal(a); return; }
        String say = null;
        if (p == 666) say = "Шестьсот шестьдесят шесть очков. Сервочереп нервно крестится аквилой. Это просто число… правда же?";
        else if (p == 1337) say = "1337 очков. Когитатор одобряет: 01101100 01100101 01100101 01110100.";
        else if (p == 1234) say = "1-2-3-4 очка. Сервочереп любит порядок.";
        else if (p == 999) say = "999 очков. Один пункт до тысячи — Омниссия любит, когда доделывают.";
        if (say != null) { Sfx.play("servo", 0.7f, 1.2f); a.toast(say); }
    }

    // ================================================================ сожжение
    /** Сжечь вид: пергамент горит снизу вверх с тлеющей кромкой и искрами, затем done. */
    static void burn(final MainActivity a, final View target, final Runnable done) {
        if (target == null || target.getWidth() == 0 || target.getParent() == null) { done.run(); return; }
        final int w = target.getWidth(), h = target.getHeight();
        final Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            target.draw(new Canvas(bmp));
        } catch (Throwable t) { done.run(); return; }
        int[] lt = new int[2], lr = new int[2];
        target.getLocationInWindow(lt);
        layer(a).getLocationInWindow(lr);
        final int cell = Math.max(dp(5), (int) Math.sqrt((double) w * h / 2600.0));
        final int cw = w / cell + 1, ch = h / cell + 1;
        final float[] n = new float[cw * ch];
        // гладкий шум + огонь идёт снизу
        float[] coarse = new float[8 * 8];
        for (int i = 0; i < coarse.length; i++) coarse[i] = rnd.nextFloat();
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            float fx = (float) x / cw * 7, fy = (float) y / ch * 7;
            int ix = (int) fx, iy = (int) fy;
            float ux = fx - ix, uy = fy - iy;
            float v = coarse[iy * 8 + ix] * (1 - ux) * (1 - uy) + coarse[iy * 8 + ix + 1] * ux * (1 - uy) + coarse[(iy + 1) * 8 + ix] * (1 - ux) * uy + coarse[(iy + 1) * 8 + ix + 1] * ux * uy;
            n[y * cw + x] = 0.45f * v + 0.45f * (1f - (float) y / ch) + 0.1f * rnd.nextFloat();
        }
        target.setVisibility(View.INVISIBLE);
        final List<float[]> embers = new ArrayList<float[]>();
        Fx fx = new Fx(a, 1700) {
            final Paint clear = new Paint();
            final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint char_ = new Paint();
            { clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); char_.setColor(0xFF1A0E06); }
            void paint(Canvas c, float t) {
                float th = t / 1.45f * 1.12f;
                int sc = c.saveLayer(0, 0, getWidth(), getHeight(), null);
                c.drawBitmap(bmp, 0, 0, null);
                for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
                    float v = n[y * cw + x];
                    float l = x * cell, tp = y * cell;
                    if (v < th - 0.1f) c.drawRect(l, tp, l + cell, tp + cell, clear);
                    else if (v < th - 0.05f) c.drawRect(l, tp, l + cell, tp + cell, char_);
                    else if (v < th) {
                        float k = (th - v) / 0.05f;
                        glow.setColor(Color.argb(230, 255, (int) (200 - 120 * k), (int) (60 - 50 * k)));
                        c.drawRect(l, tp, l + cell, tp + cell, glow);
                        if (rnd.nextFloat() < 0.02f && embers.size() < 140) embers.add(new float[]{l + cell / 2f, tp, (rnd.nextFloat() - 0.5f) * dp(30), -dp(40) - rnd.nextFloat() * dp(60), t});
                    } else if (v < th + 0.06f) {
                        glow.setColor(Color.argb((int) (110 * (1 - (v - th) / 0.06f)), 60, 30, 0));
                        c.drawRect(l, tp, l + cell, tp + cell, glow);
                    }
                }
                c.restoreToCount(sc);
                for (int i = embers.size() - 1; i >= 0; i--) {
                    float[] e = embers.get(i);
                    float age = t - e[4];
                    if (age > 0.9f) { embers.remove(i); continue; }
                    glow.setColor(Color.argb((int) (255 * (1 - age / 0.9f)), 255, 170, 60));
                    c.drawCircle(e[0] + e[2] * age, e[1] + e[3] * age, dp(1.6f), glow);
                }
            }
            @Override
            void finish() {
                super.finish();
                bmp.recycle();
                done.run();
            }
        };
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h + dp(80));
        lp.leftMargin = lt[0] - lr[0];
        lp.topMargin = lt[1] - lr[1];
        layer(a).addView(fx, lp);
        Sfx.play("w_flame", 0.8f, 0.8f);
        a.h.postDelayed(new Runnable() { public void run() { Sfx.play("burn"); } }, 500);
        Ui.haptic(Ui.HEAVY);
    }

    // ================================================================ Экстерминатус
    static long lastExterminatus;

    static void exterminatus(final MainActivity a, final View skull) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastExterminatus < 7000) return;
        lastExterminatus = now;
        final List<float[]> cracks = new ArrayList<float[]>();
        Fx fx = new Fx(a, 3600) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean made;
            void paint(Canvas c, float t) {
                int w = getWidth(), h = getHeight();
                if (!made && w > 0) { made = true; crack(cracks, w * (0.3f + rnd.nextFloat() * 0.4f), h * (0.35f + rnd.nextFloat() * 0.3f), Math.max(w, h)); }
                float repair = t < 2.4f ? 0 : Math.min(1, (t - 2.4f) / 0.9f);
                float flash = (float) (0.5 + 0.5 * Math.sin(t * 18));
                int red = (int) ((t < 0.25f ? t / 0.25f : 1) * (1 - repair) * (60 + 70 * flash));
                c.drawColor(Color.argb(red, 200, 0, 0));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                float grow = Math.min(1, t / 0.35f);
                for (float[] s : cracks) {
                    if (s[4] > grow) continue;
                    p.setStrokeWidth(s[5]);
                    p.setColor(Color.argb((int) (230 * (1 - repair)), 255, 230, 220));
                    c.drawLine(s[0], s[1], s[2], s[3], p);
                    p.setColor(Color.argb((int) (160 * (1 - repair)), 0, 0, 0));
                    p.setStrokeWidth(s[5] * 0.4f);
                    c.drawLine(s[0] + 1, s[1] + 1, s[2] + 1, s[3] + 1, p);
                }
                if (repair < 1) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTypeface(Theme.namesBlack != null ? Theme.namesBlack : Typeface.DEFAULT_BOLD);
                    p.setTextSize(Math.min(w / 9f, dp(46)));
                    p.setColor(Color.argb((int) (255 * (1 - repair) * (0.6f + 0.4f * flash)), 255, 40, 30));
                    p.setShadowLayer(dp(8), 0, 0, 0xFF000000);
                    c.drawText("EXTERMINATUS", w / 2f, h * 0.22f, p);
                    p.setShadowLayer(0, 0, 0, 0);
                }
            }
        };
        add(a, fx);
        Sfx.play("gong", 1f, 0.7f);
        Ui.haptic(Ui.HEAVY);
        a.h.postDelayed(new Runnable() { public void run() { Sfx.play("w_grenade", 1f, 0.6f); Ui.haptic(Ui.HEAVY); } }, 250);
        // экран трясёт
        final View st = a.stage;
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setDuration(1400);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator v) {
                float k = 1 - v.getAnimatedFraction();
                st.setTranslationX((rnd.nextFloat() - 0.5f) * dp(22) * k);
                st.setTranslationY((rnd.nextFloat() - 0.5f) * dp(16) * k);
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            public void onAnimationEnd(android.animation.Animator x) { st.setTranslationX(0); st.setTranslationY(0); }
        });
        va.start();
        // череп в панике
        if (skull != null) {
            final float x0 = skull.getTranslationX(), y0 = skull.getTranslationY();
            ValueAnimator pa = ValueAnimator.ofFloat(0, 1);
            pa.setDuration(2300);
            pa.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator v) {
                    float f = v.getAnimatedFraction();
                    skull.setTranslationX(x0 + (float) Math.sin(f * 40) * dp(30) + (rnd.nextFloat() - 0.5f) * dp(8));
                    skull.setTranslationY(y0 + (float) Math.cos(f * 33) * dp(14));
                    skull.setRotation((float) Math.sin(f * 50) * 25);
                }
            });
            pa.addListener(new android.animation.AnimatorListenerAdapter() {
                public void onAnimationEnd(android.animation.Animator x) {
                    Sfx.play("servo", 0.8f, 1.4f);
                    skull.animate().translationX(x0).translationY(y0).rotation(0).setDuration(500).setInterpolator(new OvershootInterpolator(2f)).start();
                    a.toast("Сервочереп заделал трещины в реальности. Больше так не делай, " + Guide.name(a) + ".");
                }
            });
            pa.start();
            Sfx.play("error", 0.7f, 1.5f);
        }
    }

    private static void crack(List<float[]> out, float x, float y, float len) {
        int arms = 6 + rnd.nextInt(4);
        for (int i = 0; i < arms; i++) {
            double ang = Math.PI * 2 * i / arms + rnd.nextFloat() * 0.5;
            branch(out, x, y, ang, len * (0.25f + rnd.nextFloat() * 0.35f), dp(3), 0, 0);
        }
    }

    private static void branch(List<float[]> out, float x, float y, double ang, float len, float wdt, float when, int depth) {
        float seg = dp(18);
        int steps = (int) (len / seg);
        for (int i = 0; i < steps; i++) {
            ang += (rnd.nextFloat() - 0.5f) * 0.7f;
            float nx = x + (float) Math.cos(ang) * seg, ny = y + (float) Math.sin(ang) * seg;
            float tw = when + (float) i / Math.max(1, steps) * (1 - when);
            out.add(new float[]{x, y, nx, ny, tw, Math.max(1, wdt * (1 - (float) i / steps * 0.6f))});
            if (depth < 2 && rnd.nextFloat() < 0.15f) branch(out, nx, ny, ang + (rnd.nextBoolean() ? 0.8 : -0.8), len * 0.4f, wdt * 0.6f, tw, depth + 1);
            x = nx;
            y = ny;
        }
    }

    // ================================================================ ладан
    static void incense(MainActivity a, View anchor, final int color, final int count) {
        int[] lt = new int[2], lr = new int[2];
        anchor.getLocationInWindow(lt);
        layer(a).getLocationInWindow(lr);
        final float ox = lt[0] - lr[0] + anchor.getWidth() / 2f, oy = lt[1] - lr[1] + anchor.getHeight() * 0.75f;
        final float[][] ps = new float[count][];
        for (int i = 0; i < count; i++) ps[i] = new float[]{(rnd.nextFloat() - 0.5f) * dp(40), rnd.nextFloat() * 1.2f, (rnd.nextFloat() - 0.5f) * dp(50), dp(8) + rnd.nextFloat() * dp(10)};
        add(a, new Fx(a, 3600) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            void paint(Canvas c, float t) {
                for (float[] q : ps) {
                    float age = t - q[1];
                    if (age < 0 || age > 2.4f) continue;
                    float k = age / 2.4f;
                    float x = ox + q[0] + q[2] * k + (float) Math.sin(age * 3 + q[0]) * dp(10);
                    float y = oy - age * dp(70);
                    p.setColor(Color.argb((int) (120 * (1 - k) * Math.min(1, age * 4)), Color.red(color), Color.green(color), Color.blue(color)));
                    c.drawCircle(x, y, q[3] * (1 + k * 2.5f), p);
                }
            }
        });
    }

    static final String[] LITANY = {
            "О дух машины, прими масло и ладан. Пусть шестерни твои вращаются без скрежета, а очки ростера сходятся.",
            "Плоть слаба, когитатор вечен. Благословен код, что считает очки без ошибки.",
            "Да не сгорит конденсатор, да не потеряется ростер. Омниссия хранит архив.",
            "Литания запуска прочитана. Дух машины доволен и шлёт тебе благословение, " };

    // ================================================================ пыль и паутина
    static View cobwebs(MainActivity a) {
        View v = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final float[] dust = new float[160 * 3];
            { for (int i = 0; i < dust.length; i += 3) { dust[i] = rnd.nextFloat(); dust[i + 1] = rnd.nextFloat(); dust[i + 2] = 0.5f + rnd.nextFloat() * 1.8f; } }
            @Override
            protected void onDraw(Canvas c) {
                int w = getWidth(), h = getHeight();
                c.drawColor(0x33B8B0A0);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x66D8D0C0);
                for (int i = 0; i < dust.length; i += 3) c.drawCircle(dust[i] * w, dust[i + 1] * h, dp(dust[i + 2]), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(0x99E8E4DA);
                web(c, 0, 0, 1, 1, Math.min(w, h) * 0.42f);
                web(c, w, 0, -1, 1, Math.min(w, h) * 0.34f);
                web(c, 0, h, 1, -1, Math.min(w, h) * 0.3f);
                web(c, w, h, -1, -1, Math.min(w, h) * 0.45f);
            }
            void web(Canvas c, float x, float y, int sx, int sy, float r) {
                int spokes = 6;
                for (int i = 0; i <= spokes; i++) {
                    double ang = Math.PI / 2 * i / spokes;
                    c.drawLine(x, y, x + sx * (float) Math.cos(ang) * r, y + sy * (float) Math.sin(ang) * r, p);
                }
                for (int ring = 1; ring <= 6; ring++) {
                    float rr = r * ring / 6.5f;
                    Path q = new Path();
                    for (int i = 0; i <= spokes; i++) {
                        double ang = Math.PI / 2 * i / spokes;
                        float px = x + sx * (float) Math.cos(ang) * rr, py = y + sy * (float) Math.sin(ang) * rr;
                        if (i == 0) q.moveTo(px, py);
                        else {
                            double mid = Math.PI / 2 * (i - 0.5) / spokes;
                            float mr = rr * 0.86f;
                            q.quadTo(x + sx * (float) Math.cos(mid) * mr, y + sy * (float) Math.sin(mid) * mr, px, py);
                        }
                    }
                    c.drawPath(q, p);
                }
            }
        };
        v.setClickable(false);
        return v;
    }

    // ================================================================ тема Хаоса
    private static View veil;

    static void applyChaos(MainActivity a) {
        boolean on = a.prefs.getBoolean("chaos", false);
        Ui.warp = on;
        Voice.warp = on;
        if (!on) {
            a.root.setLayerType(View.LAYER_TYPE_NONE, null);
            stopVeil(a);
            return;
        }
        ColorMatrix m = hue(-125);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.25f);
        m.postConcat(sat);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(m));
        a.root.setLayerType(View.LAYER_TYPE_HARDWARE, p);
        startVeil(a);
    }

    /** Живая варп-вуаль: щупальца по краям, редкие глаза, шёпоты и глитчи реальности. */
    private static void startVeil(final MainActivity a) {
        if (veil != null && veil.getParent() != null) return;
        final float density = a.getResources().getDisplayMetrics().density;
        final int[] wh = {0, 0};
        View v = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            final long t0 = android.os.SystemClock.uptimeMillis();
            final int N = 7; // щупалец: нижние + боковые
            final float[][] ten = new float[N][6]; // phase, speed, amp, width, edge(0 низ,1 лево,2 право), curl
            { for (int i = 0; i < N; i++) { ten[i][0] = rnd.nextFloat() * 6.28f; ten[i][1] = 0.35f + rnd.nextFloat() * 0.5f; ten[i][2] = 0.7f + rnd.nextFloat() * 0.6f; ten[i][3] = 10 + rnd.nextFloat() * 14; ten[i][4] = i < 4 ? 0 : (i == 4 ? 1 : 2); ten[i][5] = rnd.nextFloat() * 6.28f; } }

            @Override
            protected void onDraw(Canvas c) {
                int w = getWidth(), h = getHeight();
                if (w == 0) return;
                wh[0] = w; wh[1] = h;
                float t = (android.os.SystemClock.uptimeMillis() - t0) / 1000f;
                // дымка: пульсирующая фиолетовая тень по краям
                float pulse = 0.05f + 0.025f * (float) Math.sin(t * 0.7);
                p.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f, 0x00000000, Color.argb((int) (255 * pulse * 3.2f), 40, 4, 58), android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
                // щупальца
                for (int i = 0; i < N; i++) {
                    float[] q = ten[i];
                    float bx, by, dirx, diry;
                    if (q[4] == 0) { bx = w * (0.12f + 0.76f * i / 3f); by = h + 8 * density; dirx = 0; diry = -1; }
                    else if (q[4] == 1) { bx = -8 * density; by = h * (0.2f + 0.35f * (i - 4)); dirx = 1; diry = 0; }
                    else { bx = w + 8 * density; by = h * (0.25f + 0.4f * (i - 5)); dirx = -1; diry = 0; }
                    float len = Math.max(w, h) * (0.34f + 0.1f * (float) Math.sin(t * 0.23f + q[5]));
                    int seg = 14;
                    Path path = new Path();
                    path.moveTo(bx, by);
                    float px = bx, py = by;
                    for (int k = 1; k <= seg; k++) {
                        float u = k / (float) seg;
                        float wave = (float) Math.sin(t * q[1] * 2.2f + q[0] + u * 4.2f) * q[2] * 30 * density * u
                                   + (float) Math.sin(t * 0.6f + q[5] + u * 9f) * 8 * density * u;
                        float nx = bx + dirx * len * u + (dirx == 0 ? wave : -diry * 0);
                        float ny = by + diry * len * u + (diry == 0 ? wave : 0);
                        if (dirx != 0) ny = by + diry * len * u + wave * 0.3f;
                        else nx = bx + wave;
                        path.quadTo(px + (nx - px) * 0.4f, py + (ny - py) * 0.4f, nx, ny);
                        px = nx; py = ny;
                    }
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(q[3] * density);
                    p.setColor(0x592A0A3C);
                    c.drawPath(path, p);
                    p.setStrokeWidth(q[3] * density * 0.45f);
                    p.setColor(0x40B03AA0);
                    c.drawPath(path, p);
                    // присоски вдоль кончика
                    p.setStyle(Paint.Style.FILL);
                    for (int k = seg - 3; k <= seg; k++) {
                        float u = k / (float) seg;
                        float wave = (float) Math.sin(t * q[1] * 2.2f + q[0] + u * 4.2f) * q[2] * 30 * density * u;
                        float nx = bx + dirx * len * u + (dirx == 0 ? wave : 0);
                        float ny = by + diry * len * u + (diry == 0 ? wave * 0.3f : 0);
                        p.setColor(0x4DA020B0);
                        c.drawCircle(nx, ny, q[3] * density * 0.22f, p);
                    }
                }
                // редкие глаза варпа: появляются, всматриваются, гаснут
                for (int i = 0; i < 3; i++) {
                    float cyc = (t * 0.11f + i * 0.37f) % 1f;
                    if (cyc > 0.22f) continue;
                    float k = cyc / 0.22f;
                    float al = (float) Math.sin(k * Math.PI);
                    float ex = w * (0.25f + 0.5f * ((i * 0.618f + 0.13f) % 1f));
                    float ey = h * (0.2f + 0.5f * ((i * 0.414f + 0.31f) % 1f));
                    float er = (10 + i * 4) * density;
                    glow.setColor(Color.argb((int) (70 * al), 190, 40, 220));
                    c.drawCircle(ex, ey, er * 2.4f, glow);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb((int) (200 * al), 235, 220, 245));
                    c.drawOval(new RectF(ex - er, ey - er * 0.55f, ex + er, ey + er * 0.55f), p);
                    p.setColor(Color.argb((int) (255 * al), 30, 0, 40));
                    c.drawCircle(ex, ey, er * 0.34f, p);
                }
                postInvalidateOnAnimation();
            }
        };
        v.setClickable(false);
        veil = v;
        a.root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        // гул стартует, как только сэмпл догрузится (звуки синтезируются в фоне при запуске)
        final Runnable[] retry = new Runnable[1];
        retry[0] = new Runnable() {
            public void run() {
                if (veil == null || veil.getParent() == null) return;
                Sfx.loop("warp_drone", 0.35f);
                a.h.postDelayed(retry[0], 3000);
            }
        };
        a.h.postDelayed(retry[0], 1200);
        // шёпоты — редкие, из разных сторон
        final Runnable whisper = new Runnable() {
            public void run() {
                if (veil == null || veil.getParent() == null) return;
                if (a.prefs.getBoolean("chaos", false)) {
                    Sfx.play("warp_whisper", 0.45f, 0.75f + rnd.nextFloat() * 0.5f);
                    a.h.postDelayed(this, 9000 + rnd.nextInt(15000));
                }
            }
        };
        a.h.postDelayed(whisper, 7000);
        // глитч реальности: рывок экрана и обрыв звука
        final Runnable glitch = new Runnable() {
            public void run() {
                if (veil == null || veil.getParent() == null) return;
                if (a.prefs.getBoolean("chaos", false)) {
                    Sfx.play("warp_glitch", 0.55f, 0.9f + rnd.nextFloat() * 0.3f);
                    final View st = a.stage;
                    if (st != null) {
                        st.animate().translationX((rnd.nextFloat() - 0.5f) * dp(12)).translationY((rnd.nextFloat() - 0.5f) * dp(8)).setDuration(60).withEndAction(new Runnable() {
                            public void run() { st.animate().translationX((rnd.nextFloat() - 0.5f) * dp(8)).translationY(0).setDuration(50).withEndAction(new Runnable() {
                                public void run() { st.setTranslationX(0); st.setTranslationY(0); }
                            }).start(); }
                        }).start();
                    }
                    a.h.postDelayed(this, 14000 + rnd.nextInt(18000));
                }
            }
        };
        a.h.postDelayed(glitch, 16000);
    }

    private static void stopVeil(MainActivity a) {
        if (veil != null && veil.getParent() != null) ((ViewGroup) veil.getParent()).removeView(veil);
        veil = null;
        Sfx.loopStop();
    }

    static ColorMatrix hue(float deg) {
        double r = Math.toRadians(deg);
        float cos = (float) Math.cos(r), sin = (float) Math.sin(r);
        float lr = 0.213f, lg = 0.715f, lb = 0.072f;
        return new ColorMatrix(new float[]{
                lr + cos * (1 - lr) + sin * (-lr), lg + cos * (-lg) + sin * (-lg), lb + cos * (-lb) + sin * (1 - lb), 0, 0,
                lr + cos * (-lr) + sin * 0.143f, lg + cos * (1 - lg) + sin * 0.14f, lb + cos * (-lb) + sin * (-0.283f), 0, 0,
                lr + cos * (-lr) + sin * (-(1 - lr)), lg + cos * (-lg) + sin * lg, lb + cos * (1 - lb) + sin * lb, 0, 0,
                0, 0, 0, 1, 0});
    }

    static void toggleChaos(final MainActivity a) {
        final boolean on = !a.prefs.getBoolean("chaos", false);
        a.prefs.edit().putBoolean("chaos", on).apply();
        // вспышка-глитч перед сменой
        add(a, new Fx(a, 700) {
            final Paint p = new Paint();
            void paint(Canvas c, float t) {
                int w = getWidth(), h = getHeight();
                for (int i = 0; i < 14; i++) {
                    p.setColor(Color.argb(120 + rnd.nextInt(120), on ? 220 : 40, on ? 20 : 220, on ? 20 : 80));
                    float y = rnd.nextFloat() * h;
                    c.drawRect(rnd.nextFloat() * w * 0.3f, y, w - rnd.nextFloat() * w * 0.3f, y + dp(2) + rnd.nextFloat() * dp(14), p);
                }
            }
        });
        Sfx.play(on ? "organ" : "choir", 1f, on ? 0.6f : 1f);
        if (on) Sfx.play("warp_glitch", 0.7f, 0.8f);
        Ui.haptic(Ui.HEAVY);
        a.h.postDelayed(new Runnable() {
            public void run() {
                applyChaos(a);
                a.toast(on ? "Варп шепчет… Тема Хаоса пробуждена. Долгое нажатие на эмблему вернёт свет Императора." : "Скверна изгнана. Свет Императора вернулся.");
            }
        }, 350);
    }

    /** Жесты по эмблеме меню: 7 тапов — литания; долгое нажатие (или свайпы UUDD) — тема Хаоса. */
    static void emblemGestures(final MainActivity a, final View hx, final TextView say) {
        hx.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { toggleChaos(a); return true; }
        });
        final float slop = dp(36);
        hx.setOnTouchListener(new View.OnTouchListener() {
            float y0;
            final StringBuilder seq = new StringBuilder();
            final long[] taps = new long[7];
            int ti;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    y0 = e.getRawY();
                    // пока палец на эмблеме, меню не прокручивается — иначе свайп забирает скролл
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                float dy = e.getRawY() - y0;
                char ch = dy < -slop ? 'U' : dy > slop ? 'D' : 'T';
                seq.append(ch);
                if (seq.length() > 4) seq.delete(0, seq.length() - 4);
                if (seq.toString().equals("UUDD")) { seq.setLength(0); toggleChaos(a); return true; }
                if (ch == 'T') {
                    long now = android.os.SystemClock.uptimeMillis();
                    taps[ti % 7] = now;
                    ti++;
                    hx.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction(new Runnable() { public void run() { hx.animate().scaleX(1f).scaleY(1f).setDuration(160).start(); } }).start();
                    Sfx.play("tick", 0.5f, 0.8f + (ti % 7) * 0.08f);
                    if (ti >= 7 && now - taps[ti % 7] < 3500) {
                        ti = 0;
                        incense(a, hx, 0xFFE8E2D0, 40);
                        Sfx.play("choir");
                        hx.animate().scaleX(1.18f).scaleY(1.18f).setDuration(500).withEndAction(new Runnable() { public void run() { hx.animate().scaleX(1f).scaleY(1f).setDuration(900).start(); } }).start();
                        String l = LITANY[rnd.nextInt(LITANY.length)];
                        if (l.endsWith(", ")) l += Guide.name(a) + ".";
                        Voice.saySkull(l);
                        if (say != null) {
                            say.setText(l);
                            say.setAlpha(1f);
                            say.setScaleX(0.4f);
                            say.setScaleY(0.4f);
                            say.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(300).setInterpolator(new OvershootInterpolator(2.2f)).start();
                        }
                    }
                } else Sfx.play("servo", 0.3f, ch == 'U' ? 1.5f : 0.8f);
                return true;
            }
        });
    }

    // ================================================================ архивариус
    static void pageRead(final MainActivity a) {
        int n = a.prefs.getInt("pages_read", 0) + 1;
        a.prefs.edit().putInt("pages_read", n).apply();
        if (n == 100) a.h.postDelayed(new Runnable() { public void run() { certificate(a); } }, 600);
    }

    static void certificate(MainActivity a) {
        LinearLayout body = a.openSheet("Сервочереп принёс грамоту", "За сто прочитанных страниц", Theme.DEFAULT);
        LinearLayout sh = Ui.col(a);
        sh.setBackground(Ui.parchment());
        sh.setPadding(dp(18), dp(16), dp(18), dp(18));
        sh.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = Ui.text(a, "ЧИТАТЕЛЬ АРХИВА", 24, Theme.namesBlack, Ui.INK_RED);
        t.setGravity(Gravity.CENTER);
        sh.addView(t);
        sh.addView(Ui.skullDivider(a, 0xFF8A6A3A));
        TextView m = Ui.text(a, "Сим удостоверяется, что " + Guide.name(a) + " прочёл сто страниц священных книг правил и не уснул. "
                + "Отныне ни одно спорное правило не скроется от его взора, а оппоненты будут трепетать при словах «а вот на странице…».", 15, Theme.body, Ui.INK);
        m.setGravity(Gravity.CENTER);
        m.setLineSpacing(dp(2), 1f);
        sh.addView(m, Ui.lpm(-1, -2, 0, dp(8), 0, dp(8)));
        TextView dt = Ui.text(a, "Скреплено печатью Архива · " + new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date()), 12, Theme.bodyBold, Ui.INK_DIM);
        dt.setGravity(Gravity.CENTER);
        sh.addView(dt);
        View seal = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas c) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
                p.setColor(0xFFA0201A);
                c.drawCircle(cx, cy, r, p);
                Ui.drawSkull(c, cx, cy, r * 0.55f, 0xFF5A0A08);
            }
        };
        sh.addView(seal, Ui.lpm(dp(64), dp(64), 0, dp(10), 0, 0));
        body.addView(sh);
        Sfx.play("choir");
        Ui.haptic(Ui.DOUBLE);
    }

    static RectF tmp = new RectF();
}
