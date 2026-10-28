package com.medoedy.liber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.Random;

import static com.medoedy.liber.Ui.dp;

/** Оформление «когитатор»: рамка экрана, свечи и искры поверх, шлюз-переход механикум. */
final class Cogitator {
    private Cogitator() { }

    // геометрия рамки (в пикселях исходного frame.png)
    static final float SX0 = 40, SX1 = 712, SY0 = 8, IN_TOP = 172, MID0 = 180, MID1 = 1040, SY1 = 1180, IN_BOT = 1058, IN_L = 72, IN_R = 665;

    static float scale(int w) { return w / (SX1 - SX0); }

    /** Внутренняя область «экрана» когитатора — сюда помещается содержимое. */
    static Rect interior(int w, int h) {
        float s = scale(w);
        return new Rect((int) ((IN_L - SX0) * s), (int) ((IN_TOP - SY0) * s), (int) ((IN_R - SX0) * s), (int) (h - (SY1 - IN_BOT) * s));
    }

    static final int PHOS = 0xFF9BE58A;
    static final String[] BIN = {"0110", "1011", "0010", "1101", "1000", "0111", "1110", "0101", "1001", "0011", "\u2020101", "01\u2021"};

    // ================================================================ фон: рамка + экран
    static final class Display extends View {
        Theme theme;
        private Bitmap cached;
        private int cw, ch;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final long t0 = SystemClock.uptimeMillis();
        private final float[] mx = new float[26], my = new float[26], ms = new float[26];
        private final Random rnd = new Random(3);

        Display(Context c, Theme t) {
            super(c);
            theme = t;
            for (int i = 0; i < mx.length; i++) { mx[i] = rnd.nextFloat(); my[i] = rnd.nextFloat(); ms[i] = 0.3f + rnd.nextFloat(); }
        }

        void setTheme(Theme t) {
            if (t == null || (theme != null && theme.glow == t.glow && String.valueOf(theme.numeral).equals(String.valueOf(t.numeral)))) { theme = t == null ? theme : t; return; }
            theme = t;
            if (cached != null) { cached.recycle(); cached = null; }
            invalidate();
        }

        /** Собственный фон когитатора: тёмное фосфорное стекло, готические арки-развёртки, сетка, аквила. */
        private void build(int w, int h) {
            cached = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(cached);
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF0E1711, 0xFF0A110C, 0xFF060906}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            // подсветка цвета легиона сверху и тёплый отсвет свечей снизу
            p.setShader(new RadialGradient(w * 0.5f, -h * 0.05f, h * 0.65f, Theme.alpha(theme.glow, 0.18f), Theme.alpha(theme.glow, 0f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.5f, h * 1.05f, h * 0.5f, 0x30E08A30, 0x00E08A30, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            // тонкая координатная сетка экрана
            p.setColor(Theme.alpha(PHOS, 0.03f));
            float g = dp(28);
            for (float x = (w % g) / 2; x < w; x += g) c.drawRect(x, 0, x + dp(0.6f), h, p);
            for (float y = 0; y < h; y += g) c.drawRect(0, y, w, y + dp(0.6f), p);
            // готические арки линиями люминофора
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.6f));
            float aw = w / 3f;
            for (int i = 0; i < 3; i++) {
                float l = i * aw + dp(14), r = (i + 1) * aw - dp(14), top = h * 0.12f, mid = (l + r) / 2;
                Path a = new Path();
                a.moveTo(l, h * 0.78f);
                a.lineTo(l, top + aw * 0.5f);
                a.quadTo(l, top, mid, top - aw * 0.15f);
                a.quadTo(r, top, r, top + aw * 0.5f);
                a.lineTo(r, h * 0.78f);
                p.setColor(Theme.alpha(PHOS, 0.07f));
                c.drawPath(a, p);
                p.setColor(Theme.alpha(PHOS, 0.035f));
                c.drawLine(mid, top, mid, h * 0.78f, p);
                for (int k = 1; k < 5; k++) c.drawLine(l, top + aw * 0.5f + k * h * 0.11f, r, top + aw * 0.5f + k * h * 0.11f, p);
            }
            p.setStyle(Paint.Style.FILL);
            // водяной знак: номер легиона или аквила
            if (theme.numeral != null && !theme.numeral.isEmpty()) {
                p.setTypeface(Theme.namesBlack);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(Theme.alpha(PHOS, 0.05f));
                float size = Math.min(w * 1.1f / Math.max(1, theme.numeral.length() * 0.62f), h * 0.4f);
                p.setTextSize(size);
                c.drawText(theme.numeral, w * 0.5f, h * 0.58f + size * 0.35f, p);
            } else {
                Bitmap aq = Art.get("f_aquila");
                if (aq != null) {
                    p.setColorFilter(new LightingColorFilter(PHOS, 0));
                    p.setAlpha(16);
                    float sz = w * 0.9f;
                    c.drawBitmap(aq, null, new RectF(w / 2f - sz / 2, h * 0.55f - sz / 2, w / 2f + sz / 2, h * 0.55f + sz / 2), p);
                    p.setColorFilter(null);
                    p.setAlpha(255);
                }
            }
            // латунные рейки с заклёпками по верхнему и нижнему краю
            rail(c, 0, w, 0, dp(5));
            rail(c, 0, w, h - dp(5), dp(5));
            // строки развёртки
            p.setColor(0x18000000);
            float step = Math.max(2, dp(2.5f));
            for (float y = 0; y < h; y += step) c.drawRect(0, y, w, y + step * 0.45f, p);
            // виньетка стекла
            p.setShader(new RadialGradient(w / 2f, h * 0.45f, Math.max(w, h) * 0.75f, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        private void rail(Canvas c, float l, float r, float y, float hgt) {
            p.setShader(new LinearGradient(0, y, 0, y + hgt, new int[]{0xFF8A7448, 0xFF4A3C22, 0xFF1C160C}, null, Shader.TileMode.CLAMP));
            c.drawRect(l, y, r, y + hgt, p);
            p.setShader(null);
            for (float x = dp(14); x < r; x += dp(46)) {
                p.setShader(new RadialGradient(x, y + hgt / 2, hgt * 0.6f, 0xFFD8C8A0, 0xFF2A2214, Shader.TileMode.CLAMP));
                c.drawCircle(x, y + hgt / 2, hgt * 0.38f, p);
            }
            p.setShader(null);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            if (cached == null || cw != w || ch != h) { cw = w; ch = h; build(w, h); }
            c.drawBitmap(cached, 0, 0, null);
            float t = (SystemClock.uptimeMillis() - t0) / 1000f;
            Rect in = new Rect(0, 0, w, h);
            c.save();
            // мерцание люминофора
            float fl = (float) (0.5 + 0.5 * Math.sin(t * 1.7) * Math.sin(t * 0.37));
            p.setColor(Theme.alpha(PHOS, 0.015f + 0.02f * fl));
            c.drawRect(in, p);
            // бегущая полоса развёртки
            float by = in.top + ((t * 0.08f) % 1f) * in.height();
            p.setShader(new LinearGradient(0, by - dp(40), 0, by + dp(4), Theme.alpha(PHOS, 0f), Theme.alpha(PHOS, 0.05f), Shader.TileMode.CLAMP));
            c.drawRect(in.left, by - dp(40), in.right, by + dp(4), p);
            p.setShader(null);
            // пылинки в свете
            for (int i = 0; i < mx.length; i++) {
                float y = (my[i] - t * 0.012f * ms[i]) % 1f;
                if (y < 0) y += 1;
                float x = mx[i] + (float) Math.sin(t * 0.3 + i) * 0.01f;
                float a = (float) (0.25 + 0.25 * Math.sin(t * 1.3 + i * 2));
                p.setColor(Theme.alpha(0xFFE8D8A8, a * 0.5f));
                c.drawCircle(in.left + x * in.width(), in.top + y * in.height(), dp(0.6f + ms[i]), p);
            }
            // бинарная литания когитатора бежит по краям
            p.setTypeface(android.graphics.Typeface.MONOSPACE);
            p.setTextSize(dp(9));
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Theme.alpha(PHOS, 0.10f));
            float lh = dp(11);
            int rows = (int) (h / lh) + 2;
            float off = (t * dp(14)) % lh;
            for (int k = 0; k < rows; k++) {
                int seed = (int) (k + (int) (t * dp(14) / lh));
                c.drawText(BIN[Math.abs(seed) % BIN.length], dp(3), k * lh - off, p);
                c.drawText(BIN[Math.abs(seed * 7 + 3) % BIN.length], w - dp(3) - p.measureText(BIN[0]), k * lh - off, p);
            }
            c.restore();
            postInvalidateDelayed(50);
        }
    }

    // ================================================================ передний план: свечи и искры
    static final class Fx extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final long t0 = SystemClock.uptimeMillis();
        private final float[] ex = new float[16], ey = new float[16], ev = new float[16], el = new float[16];
        private final Random rnd = new Random(9);

        Fx(Context c) {
            super(c);
            setClickable(false);
            setFocusable(false);
            for (int i = 0; i < ex.length; i++) spawn(i, true);
        }

        private void spawn(int i, boolean anyAge) {
            ex[i] = rnd.nextBoolean() ? 0 : 1;
            ey[i] = 0;
            ev[i] = 0.5f + rnd.nextFloat();
            el[i] = anyAge ? rnd.nextFloat() : 0;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0) return;
            float s = scale(w), t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float candleH = dp(130);
            float baseY = h - dp(4);
            Ui.drawCandle(c, p, dp(24), baseY, candleH, t, 0);
            Ui.drawCandle(c, p, w - dp(24), baseY, candleH, t, 3.1f);
            postInvalidateDelayed(40);
        }
    }

    // ================================================================ шлюз-переход
    /** Две бронестворки с шестернёй Механикум: смыкаются, поворот, расходятся. */
    static final class Gate extends View {
        float closed;      // 0 — открыто, 1 — сомкнуто
        float spin;
        boolean running;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Paint grainP;

        Gate(Context c) {
            super(c);
            setVisibility(GONE);
            setClickable(true); // во время перехода касания не проходят
        }

        void run(final Runnable mid, boolean quick) {
            if (running) { mid.run(); return; }
            running = true;
            setVisibility(VISIBLE);
            Sfx.play("gate_close", 0.8f, quick ? 1.2f : 1f);
            Ui.pulses(new int[]{0, 70, 140}, new int[]{Ui.TICK});
            ValueAnimator close = ValueAnimator.ofFloat(0, 1);
            close.setDuration(quick ? 170 : 230);
            close.setInterpolator(new AccelerateInterpolator(1.6f));
            close.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { closed = (Float) a.getAnimatedValue(); spin = closed * 60; invalidate(); }
            });
            final boolean q = quick;
            close.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    Ui.haptic(Ui.HEAVY);
                    try { mid.run(); } catch (Throwable ignored) { }
                    ValueAnimator hold = ValueAnimator.ofFloat(60, 150);
                    hold.setDuration(q ? 80 : 140);
                    hold.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        public void onAnimationUpdate(ValueAnimator a) { spin = (Float) a.getAnimatedValue(); invalidate(); }
                    });
                    hold.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator an2) {
                            Sfx.play("gate_open", 0.7f, 1f);
                            Ui.haptic(Ui.TICK);
                            ValueAnimator open = ValueAnimator.ofFloat(1, 0);
                            open.setDuration(q ? 220 : 300);
                            open.setInterpolator(new DecelerateInterpolator(1.4f));
                            open.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                public void onAnimationUpdate(ValueAnimator a) { closed = (Float) a.getAnimatedValue(); spin = 150 + (1 - closed) * 60; invalidate(); }
                            });
                            open.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator an3) { running = false; setVisibility(GONE); }
                            });
                            open.start();
                        }
                    });
                    hold.start();
                }
            });
            close.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (closed <= 0.001f) return;
            if (grainP == null) {
                grainP = new Paint();
                grainP.setShader(new BitmapShader(Ui.grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            }
            float half = w / 2f * closed;
            // тень от створок
            p.setColor(Theme.alpha(0xFF000000, 0.5f * closed));
            c.drawRect(0, 0, w, h, p);
            door(c, 0, half, h, true);
            door(c, w - half, w, h, false);
            // шестерня на стыке
            Bitmap cog = Art.get("f_cog");
            float r = Math.min(w, h) * 0.2f;
            if (cog != null && closed > 0.55f) {
                float a = (closed - 0.55f) / 0.45f;
                c.save();
                c.rotate(spin, w / 2, h / 2);
                p.setShader(new RadialGradient(w / 2, h / 2, r * 1.2f, 0xFF2A2418, 0xFF0C0A06, Shader.TileMode.CLAMP));
                p.setAlpha((int) (255 * a));
                c.drawCircle(w / 2, h / 2, r * 0.98f, p);
                p.setShader(null);
                p.setColorFilter(new LightingColorFilter(0xFFD8B46A, 0));
                c.drawBitmap(cog, null, new RectF(w / 2 - r * 1.25f, h / 2 - r * 1.25f, w / 2 + r * 1.25f, h / 2 + r * 1.25f), p);
                p.setColorFilter(null);
                p.setAlpha(255);
                c.restore();
                // красный глаз в центре
                p.setShader(new RadialGradient(w / 2, h / 2, r * 0.25f, Theme.alpha(0xFFFF3A20, a), 0x00FF3A20, Shader.TileMode.CLAMP));
                c.drawCircle(w / 2, h / 2, r * 0.25f, p);
                p.setShader(null);
            }
        }

        private void door(Canvas c, float l, float r, float h, boolean left) {
            if (r - l < 1) return;
            float w = getWidth();
            // плита
            p.setShader(new LinearGradient(left ? r - w / 2 : l, 0, left ? r : l + w / 2, 0,
                    new int[]{0xFF1A1B17, 0xFF2E2E28, 0xFF15160F}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(l, 0, r, h, p);
            p.setShader(null);
            c.save();
            c.clipRect(l, 0, r, h);
            c.drawRect(l, 0, r, h, grainP);
            float edge = left ? r : l;
            float dir = left ? -1 : 1;
            // горизонтальные пояса с заклёпками
            for (int i = 1; i < 6; i++) {
                float y = h * i / 6f;
                p.setShader(new LinearGradient(0, y - dp(7), 0, y + dp(7), new int[]{0xFF4A4336, 0xFF2A251C, 0xFF0E0C09}, null, Shader.TileMode.CLAMP));
                c.drawRect(l, y - dp(7), r, y + dp(7), p);
                p.setShader(null);
                for (float x = edge + dir * dp(26); left ? x > l : x < r; x += dir * dp(34)) rivet(c, x, y);
            }
            // латунная окантовка кромки и зубья замка
            p.setShader(new LinearGradient(edge - dp(10), 0, edge + dp(10), 0, new int[]{0xFF5E4A22, 0xFFD8B46A, 0xFF5E4A22}, null, Shader.TileMode.CLAMP));
            c.drawRect(edge + dir * dp(10), 0, edge, h, p);
            p.setShader(null);
            p.setColor(0xFF0C0B08);
            Path teeth = new Path();
            float tw = dp(26);
            for (float y = 0; y < h; y += tw * 2) {
                teeth.moveTo(edge, y + (left ? 0 : tw));
                teeth.lineTo(edge - dir * dp(12), y + tw / 2 + (left ? 0 : tw));
                teeth.lineTo(edge, y + tw + (left ? 0 : tw));
                teeth.close();
            }
            c.drawPath(teeth, p);
            // предупреждающие огни
            float blink = (float) (0.5 + 0.5 * Math.sin(SystemClock.uptimeMillis() / 90.0));
            p.setShader(new RadialGradient(edge + dir * dp(24), h * 0.08f, dp(10), Theme.alpha(0xFFFF4020, 0.4f + 0.6f * blink), 0x00FF4020, Shader.TileMode.CLAMP));
            c.drawCircle(edge + dir * dp(24), h * 0.08f, dp(10), p);
            c.drawCircle(edge + dir * dp(24), h * 0.92f, dp(10), p);
            p.setShader(null);
            c.restore();
            // поршень от края экрана
            float py = h * 0.5f;
            float from = left ? 0 : w, to = edge + dir * dp(14);
            p.setShader(new LinearGradient(0, py - dp(6), 0, py + dp(6), new int[]{0xFF8A8A80, 0xFFE0E0D6, 0xFF55554E}, null, Shader.TileMode.CLAMP));
            c.drawRect(Math.min(from, to), py - dp(5), Math.max(from, to), py + dp(5), p);
            p.setShader(null);
        }

        private void rivet(Canvas c, float x, float y) {
            p.setShader(new RadialGradient(x - dp(1), y - dp(1), dp(4), 0xFFB8A880, 0xFF2A241A, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, dp(3.2f), p);
            p.setShader(null);
        }
    }
}
