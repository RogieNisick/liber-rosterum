package com.medoedy.liber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * Визуальный язык в духе Rogue Trader: зелёно-стальные рамы с латунными уголками,
 * состаренный пергамент, ленты, гексагональные значки, свечи, черепа, готические арки.
 */
final class Ui {
    static float density = 3;

    // палитра
    static final int INK = 0xFF2B1E12, INK_DIM = 0xB32B1E12, INK_RED = 0xFF8A1C14;
    static int BONE = 0xFFE4D8BC, BONE_DIM = 0xB3E4D8BC, GOLD = 0xFFC4A462, GOLD_DIM = 0xFF6E5A32;
    static final int PHOS = 0xFF9BE58A, PHOS_DIM = 0x999BE58A;

    /** Тёмная тема — «когитатор»; светлая — прежняя, пергамент и бирюзовая сталь. */
    static boolean dark = true;

    static void setDark(boolean d) {
        dark = d;
        if (d) { BONE = 0xFFE4D8BC; BONE_DIM = 0xB3E4D8BC; GOLD = 0xFFC4A462; GOLD_DIM = 0xFF6E5A32; GREEN_OK = 0xFF8FBF6A; RED_BAD = 0xFFE07A62; }
        else { BONE = 0xFF2E2014; BONE_DIM = 0xB02E2014; GOLD = 0xFF7A4A1C; GOLD_DIM = 0xFF9A7A4A; GREEN_OK = 0xFF4E7A2A; RED_BAD = 0xFFA8321E; }
    }

    static float lum(int c) { return (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f) / 255f; }

    /** Цвет «свечения» интерактивных элементов. */
    static int glowColor() { return dark ? PHOS : 0xFFFFD27A; }
    static final int STEEL_TOP = 0xFF26302B, STEEL_BOT = 0xFF141A17, TEAL = 0xFF6FB3A0;
    static int GREEN_OK = 0xFF8FBF6A, RED_BAD = 0xFFE07A62;

    static int dp(float v) { return (int) (v * density + 0.5f); }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        Theme.init(c.getAssets());
    }

    // ================================================================ вибрация
    private static Vibrator vib;
    static boolean hapticsOn = true;

    static void initHaptics(Context c) {
        try { vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE); } catch (Exception ignored) { }
    }

    static final int TICK = 0, CLICK = 1, HEAVY = 2, DOUBLE = 3;

    @SuppressWarnings("deprecation")
    static void haptic(int kind) {
        if (vib == null || !hapticsOn) return;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                int e = kind == TICK ? VibrationEffect.EFFECT_TICK : kind == CLICK ? VibrationEffect.EFFECT_CLICK
                        : kind == HEAVY ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_DOUBLE_CLICK;
                vib.vibrate(VibrationEffect.createPredefined(e));
            } else if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(kind == TICK ? 8 : kind == CLICK ? 15 : 30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else vib.vibrate(kind == TICK ? 8 : kind == CLICK ? 15 : 30);
        } catch (Throwable ignored) { }
    }

    private static final android.os.Handler H = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Серия вибраций: моменты (мс) и сила каждого импульса. */
    static void pulses(int[] at, final int[] kinds) {
        for (int i = 0; i < at.length; i++) {
            final int k = kinds[Math.min(i, kinds.length - 1)];
            H.postDelayed(new Runnable() { public void run() { haptic(k); } }, at[i]);
        }
    }

    /** Отдача оружия в вибрации — в такт синтезированному звуку. */
    static void recoil(String w) {
        if ("w_bolt".equals(w)) pulses(new int[]{0, 120, 230}, new int[]{HEAVY});
        else if ("w_auto".equals(w)) pulses(new int[]{0, 280}, new int[]{HEAVY});
        else if ("w_las".equals(w)) pulses(new int[]{0, 200}, new int[]{CLICK});
        else if ("w_plasma".equals(w)) pulses(new int[]{0, 120, 240, 380}, new int[]{TICK, TICK, TICK, HEAVY});
        else if ("w_chain".equals(w)) pulses(new int[]{0, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600}, new int[]{TICK});
        else if ("w_power".equals(w)) pulses(new int[]{0, 90, 230, 300, 450}, new int[]{TICK, CLICK, TICK, CLICK, TICK});
        else if ("w_hammer".equals(w)) pulses(new int[]{0, 100, 300}, new int[]{TICK, TICK, HEAVY});
        else if ("w_missile".equals(w)) pulses(new int[]{0, 700}, new int[]{CLICK, HEAVY});
        else if ("w_grenade".equals(w)) pulses(new int[]{0, 400}, new int[]{TICK, HEAVY});
        else if ("w_flame".equals(w) || "w_melta".equals(w)) pulses(new int[]{0, 100, 200, 300, 400}, new int[]{CLICK, TICK});
        else pulses(new int[]{0}, new int[]{CLICK});
    }

    // ================================================================ текстуры
    private static Bitmap grain, paper;

    static Bitmap grain() {
        if (grain == null) {
            int n = 160;
            grain = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Random r = new Random(7);
            int[] px = new int[n * n];
            for (int i = 0; i < px.length; i++) { int g = r.nextInt(255); px[i] = Color.argb(r.nextInt(20), g, g, g); }
            for (int k = 0; k < 50; k++) {
                int x = r.nextInt(n), y = r.nextInt(n), len = 6 + r.nextInt(30);
                for (int j = 0; j < len; j++) px[((y + j / 4) % n) * n + (x + j) % n] = Color.argb(16, 220, 230, 210);
            }
            grain.setPixels(px, 0, n, 0, 0, n, n);
        }
        return grain;
    }

    /** Волокна и пятна старой бумаги. */
    static Bitmap paper() {
        if (paper == null) {
            int n = 200;
            paper = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Random r = new Random(11);
            int[] px = new int[n * n];
            for (int i = 0; i < px.length; i++) {
                int v = r.nextInt(100);
                px[i] = v < 6 ? Color.argb(28, 90, 60, 30) : v < 10 ? Color.argb(18, 255, 250, 230) : Color.argb(r.nextInt(10), 120, 90, 50);
            }
            for (int k = 0; k < 14; k++) { // пятна
                int cx = r.nextInt(n), cy = r.nextInt(n), rad = 6 + r.nextInt(22);
                for (int y = -rad; y <= rad; y++) for (int x = -rad; x <= rad; x++) {
                    if (x * x + y * y > rad * rad) continue;
                    int idx = ((cy + y + n) % n) * n + (cx + x + n) % n;
                    px[idx] = Color.argb(10 + r.nextInt(8), 110, 75, 35);
                }
            }
            for (int k = 0; k < 120; k++) { // волокна
                int x = r.nextInt(n), y = r.nextInt(n), len = 5 + r.nextInt(14);
                for (int j = 0; j < len; j++) px[((y + (j % 3 == 0 ? 1 : 0)) % n) * n + (x + j) % n] = Color.argb(22, 100, 70, 35);
            }
            paper.setPixels(px, 0, n, 0, 0, n, n);
        }
        return paper;
    }

    static void chamfer(Path p, float l, float t, float r, float b, float c) {
        c = Math.min(c, Math.min((r - l) / 2, (b - t) / 2));
        p.reset();
        p.moveTo(l + c, t); p.lineTo(r - c, t); p.lineTo(r, t + c); p.lineTo(r, b - c);
        p.lineTo(r - c, b); p.lineTo(l + c, b); p.lineTo(l, b - c); p.lineTo(l, t + c); p.close();
    }

    // ================================================================ стальная рама
    /** Зелёно-стальная панель: двойная латунная окантовка, орнаментальные уголки, заклёпки. */
    static final class Steel extends Drawable {
        final int top, bot, trim;
        final float cut;
        final boolean ornate, pressed;
        final int glow;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), inner = new Paint(Paint.ANTI_ALIAS_FLAG),
                orn = new Paint(Paint.ANTI_ALIAS_FLAG), grainP = new Paint(), glowP = new Paint(Paint.ANTI_ALIAS_FLAG), bevel = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path(), innerPath = new Path();

        Steel(int top, int bot, int trim, int glow, float cut, boolean ornate, boolean pressed) {
            if (!dark && lum(top) < 0.4f) {
                // светлая тема: тёмная сталь превращается в состаренный пергамент с латунной окантовкой
                top = pressed ? 0xFFE6D6B0 : 0xFFF2E6C8;
                bot = pressed ? 0xFFCDB688 : 0xFFDCC79C;
                trim = Theme.mix(trim, 0xFF6A4A22, 0.5f);
            }
            this.top = top; this.bot = bot; this.trim = trim; this.glow = glow; this.cut = cut; this.ornate = ornate; this.pressed = pressed;
            edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(dp(1.6f));
            inner.setStyle(Paint.Style.STROKE); inner.setStrokeWidth(dp(0.8f));
            orn.setStyle(Paint.Style.FILL);
            grainP.setShader(new BitmapShader(lum(this.top) > 0.6f ? paper() : grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float s = edge.getStrokeWidth() / 2;
            chamfer(path, b.left + s, b.top + s, b.right - s, b.bottom - s, cut);
            float in = dp(4);
            chamfer(innerPath, b.left + in, b.top + in, b.right - in, b.bottom - in, Math.max(0, cut - in * 0.5f));
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, pressed ? Theme.mix(top, Color.WHITE, 0.08f) : top, bot, Shader.TileMode.CLAMP));
            edge.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{Theme.mix(trim, Color.WHITE, 0.4f), trim, Theme.mix(trim, Color.BLACK, 0.55f), trim, Theme.mix(trim, Color.WHITE, 0.2f)},
                    new float[]{0, 0.25f, 0.55f, 0.8f, 1}, Shader.TileMode.CLAMP));
            inner.setColor(Theme.alpha(trim, 0.35f));
            if (glow != 0) glowP.setShader(new RadialGradient(b.centerX(), b.top, Math.max(b.width(), b.height()) * 0.8f, Theme.alpha(glow, 0.22f), Theme.alpha(glow, 0f), Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, fill);
            c.save(); c.clipPath(path); c.drawPaint(grainP); if (glow != 0) c.drawPaint(glowP);
            Rect bb = getBounds();
            if (dark) {
            // фаска: светлая кромка сверху, тень снизу
            bevel.setShader(new LinearGradient(0, bb.top, 0, bb.top + dp(10), 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(bb.left, bb.top, bb.right, bb.top + dp(10), bevel);
            bevel.setShader(new LinearGradient(0, bb.bottom - dp(14), 0, bb.bottom, 0x00000000, 0x80000000, Shader.TileMode.CLAMP));
            c.drawRect(bb.left, bb.bottom - dp(14), bb.right, bb.bottom, bevel);
            if (pressed) {
                bevel.setShader(new RadialGradient(bb.centerX(), bb.centerY(), Math.max(bb.width(), bb.height()) * 0.7f, 0x409BE58A, 0x009BE58A, Shader.TileMode.CLAMP));
                c.drawRect(bb, bevel);
            }
            bevel.setShader(null);
            }
            c.restore();
            c.drawPath(innerPath, inner);
            c.drawPath(path, edge);
            if (ornate) {
                Rect b = getBounds();
                corner(c, b.left, b.top, 1, 1);
                corner(c, b.right, b.top, -1, 1);
                corner(c, b.left, b.bottom, 1, -1);
                corner(c, b.right, b.bottom, -1, -1);
            }
        }

        private void corner(Canvas c, float x, float y, int sx, int sy) {
            if (dark) cornerPlate(c, x, y, sx, sy); else cornerCurl(c, x, y, sx, sy);
        }

        /** Латунный уголок: ромб + завиток + заклёпка (светлая тема). */
        private void cornerCurl(Canvas c, float x, float y, int sx, int sy) {
            float s = dp(9);
            orn.setShader(new LinearGradient(x, y, x + sx * s * 2, y + sy * s * 2, Theme.mix(trim, Color.WHITE, 0.45f), Theme.mix(trim, Color.BLACK, 0.4f), Shader.TileMode.CLAMP));
            Path p = new Path();
            float ox = x + sx * dp(3), oy = y + sy * dp(3);
            p.moveTo(ox, oy);
            p.lineTo(ox + sx * s * 1.6f, oy);
            p.lineTo(ox + sx * s * 0.9f, oy + sy * dp(2.5f));
            p.lineTo(ox + sx * dp(2.5f), oy + sy * dp(2.5f));
            p.lineTo(ox + sx * dp(2.5f), oy + sy * s * 0.9f);
            p.lineTo(ox, oy + sy * s * 1.6f);
            p.close();
            c.drawPath(p, orn);
            c.drawCircle(ox + sx * dp(5.5f), oy + sy * dp(5.5f), dp(2.1f), orn);
        }

        /** Угловая накладка: скошенная латунная пластина и заклёпка. */
        private void cornerPlate(Canvas c, float x, float y, int sx, int sy) {
            float s = dp(11);
            orn.setShader(new LinearGradient(x, y, x + sx * s, y + sy * s, Theme.mix(trim, Color.WHITE, 0.25f), Theme.mix(trim, Color.BLACK, 0.55f), Shader.TileMode.CLAMP));
            Path p = new Path();
            float ox = x + sx * dp(2), oy = y + sy * dp(2);
            p.moveTo(ox + sx * cut * 0.6f, oy);
            p.lineTo(ox + sx * s * 1.5f, oy);
            p.lineTo(ox + sx * s * 1.2f, oy + sy * dp(3));
            p.lineTo(ox + sx * dp(3), oy + sy * s * 1.2f);
            p.lineTo(ox, oy + sy * s * 1.5f);
            p.lineTo(ox, oy + sy * cut * 0.6f);
            p.close();
            c.drawPath(p, orn);
            float rx = ox + sx * dp(6.5f), ry = oy + sy * dp(6.5f);
            orn.setShader(new RadialGradient(rx - dp(0.8f), ry - dp(0.8f), dp(3.2f), 0xFFD8C8A0, 0xFF2A2214, Shader.TileMode.CLAMP));
            c.drawCircle(rx, ry, dp(2.4f), orn);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static int trimOf(Theme t) { return dark ? Theme.mix(Theme.mix(t.trim, GOLD, 0.7f), 0xFF5A5040, 0.35f) : Theme.mix(t.trim, GOLD, 0.6f); }

    static Drawable steel(Theme t) {
        return dark ? new Steel(0xF41A1C18, 0xF40A0B09, trimOf(t), t.glow, dp(10), true, false)
                : new Steel(0xF226302B, 0xF2121815, trimOf(t), t.glow, dp(10), true, false);
    }

    static Drawable steelFlat(Theme t, float cut) {
        return dark ? new Steel(0xF0181A16, 0xF00B0C0A, trimOf(t), 0, cut, false, false)
                : new Steel(0xEE222A26, 0xEE131815, trimOf(t), 0, cut, false, false);
    }

    static Drawable steelButton(Theme t, float cut) {
        int trim = trimOf(t);
        if (!dark) return states(new Steel(0xFF2C3833, 0xFF161D1A, trim, 0, cut, false, false), new Steel(0xFF3B4A43, 0xFF1E2622, trim, 0, cut, false, true));
        return states(new Steel(0xFF20231E, 0xFF0D0F0C, trim, 0, cut, false, false), new Steel(0xFF27302A, 0xFF101611, Theme.mix(trim, PHOS, 0.4f), 0, cut, false, true));
    }

    // ================================================================ пергамент
    /** Состаренный пергамент с подпалёнными краями. */
    static final class Parchment extends Drawable {
        final float cut;
        final boolean pressed, edge;
        final int tint;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), tex = new Paint(), burn = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), tintP = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Parchment(float cut, boolean edge, int tint, boolean pressed) {
            this.cut = cut; this.edge = edge; this.tint = tint; this.pressed = pressed;
            tex.setShader(new BitmapShader(paper(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(1.2f));
        }

        @Override
        protected void onBoundsChange(Rect b) {
            chamfer(path, b.left, b.top, b.right, b.bottom, cut);
            fill.setShader(new LinearGradient(b.left, b.top, b.right * 0.7f, b.bottom,
                    dark ? (pressed ? 0xFFE2D0A4 : 0xFFD6C397) : (pressed ? 0xFFF0E2BE : 0xFFE6D5AC), dark ? (pressed ? 0xFFC4AC78 : 0xFFB59D6C) : (pressed ? 0xFFD8C291 : 0xFFCDB683), Shader.TileMode.CLAMP));
            burn.setShader(new RadialGradient(b.centerX(), b.centerY(), Math.max(b.width(), b.height()) * 0.72f,
                    dark ? new int[]{0x00000000, 0x10301A08, 0x70502E10, 0xCC2A1606} : new int[]{0x00000000, 0x00000000, 0x55603A14, 0xAA3A200A}, dark ? new float[]{0, 0.5f, 0.84f, 1} : new float[]{0, 0.55f, 0.85f, 1}, Shader.TileMode.CLAMP));
            line.setColor(0x806A4A22);
            if (tint != 0) tintP.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom, Theme.alpha(tint, 0.14f), Theme.alpha(tint, 0f), Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, fill);
            c.save(); c.clipPath(path);
            c.drawPaint(tex);
            if (tint != 0) c.drawPaint(tintP);
            c.drawPaint(burn);
            c.restore();
            if (edge) {
                Rect b = getBounds();
                float in = dp(5);
                Path ip = new Path();
                chamfer(ip, b.left + in, b.top + in, b.right - in, b.bottom - in, Math.max(0, cut - in * 0.5f));
                c.drawPath(ip, line);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static Drawable parchment() { return new Parchment(dp(6), true, 0, false); }

    static Drawable parchmentButton(float cut) { return states(new Parchment(cut, false, 0, false), new Parchment(cut, false, 0, true)); }

    // ================================================================ латунь и сургуч
    static final class Metal extends Drawable {
        final int top, bot, edgeC;
        final float cut;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Metal(int top, int bot, int edgeC, float cut) {
            this.top = top; this.bot = bot; this.edgeC = edgeC; this.cut = cut;
            edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(dp(1.4f)); edge.setColor(edgeC);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            chamfer(path, b.left + dp(0.7f), b.top + dp(0.7f), b.right - dp(0.7f), b.bottom - dp(0.7f), cut);
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{Theme.mix(top, Color.WHITE, 0.2f), top, bot, Theme.mix(bot, Color.BLACK, 0.2f)}, new float[]{0, 0.35f, 0.75f, 1}, Shader.TileMode.CLAMP));
            shine.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.45f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, fill);
            c.save(); c.clipPath(path); c.drawRect(getBounds(), shine); c.restore();
            c.drawPath(path, edge);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static Drawable brass(float cut) {
        return states(new Metal(0xFFD9B66A, 0xFF7E5E26, 0xFF3E2A0E, cut), new Metal(0xFFEBCB85, 0xFF8E6E36, 0xFF3E2A0E, cut));
    }

    static Drawable heraldic(Theme t, float cut) {
        int a = t.accent;
        return states(new Metal(Theme.mix(a, Color.WHITE, 0.08f), Theme.mix(a, Color.BLACK, 0.5f), Theme.mix(t.trim, Color.BLACK, 0.2f), cut),
                new Metal(Theme.mix(a, Color.WHITE, 0.22f), Theme.mix(a, Color.BLACK, 0.35f), Theme.mix(t.trim, Color.BLACK, 0.2f), cut));
    }

    static Drawable danger(float cut) {
        return states(new Metal(0xFF6A1A14, 0xFF2E0A08, 0xFF1A0503, cut), new Metal(0xFF8A2A20, 0xFF3E120E, 0xFF1A0503, cut));
    }

    static Drawable states(Drawable normal, Drawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    // ================================================================ лента
    /** Тканевая лента-заголовок с раздвоенными концами. */
    static final class Ribbon extends Drawable {
        final int color;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), fold = new Paint(Paint.ANTI_ALIAS_FLAG), stitch = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Ribbon(int color) {
            this.color = color;
            stitch.setStyle(Paint.Style.STROKE);
            stitch.setStrokeWidth(dp(0.8f));
            stitch.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3), dp(2)}, 0));
        }

        @Override
        protected void onBoundsChange(Rect b) {
            if (!dark) { boundsLight(b); return; }
            path.reset();
            chamfer(path, b.left + dp(1), b.top + dp(1), b.right - dp(1), b.bottom - dp(1), dp(7));
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{0xFF34342C, 0xFF1C1C17, 0xFF0B0B09}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            fold.setStyle(Paint.Style.STROKE);
            fold.setStrokeWidth(dp(1.4f));
            fold.setShader(new LinearGradient(b.left, 0, b.right, 0, new int[]{0xFF4E4230, 0xFFC4A462, 0xFF4E4230}, null, Shader.TileMode.CLAMP));
            stitch.setColor(Theme.alpha(Theme.mix(color, PHOS, 0.5f), 0.55f));
            stitch.setPathEffect(null);
            stitch.setStrokeWidth(dp(1.2f));
        }

        private void boundsLight(Rect b) {
            float n = b.height() * 0.4f;
            path.reset();
            path.moveTo(b.left, b.top);
            path.lineTo(b.right, b.top);
            path.lineTo(b.right - n, b.centerY());
            path.lineTo(b.right, b.bottom);
            path.lineTo(b.left, b.bottom);
            path.lineTo(b.left + n, b.centerY());
            path.close();
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, new int[]{Theme.mix(color, Color.WHITE, 0.15f), color, Theme.mix(color, Color.BLACK, 0.45f)}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            stitch.setStrokeWidth(dp(0.8f));
            stitch.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3), dp(2)}, 0));
            stitch.setColor(Theme.alpha(Theme.mix(color, Color.WHITE, 0.6f), 0.5f));
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (!dark) {
                c.drawPath(path, p);
                float n = b.height() * 0.4f;
                c.drawLine(b.left + n, b.top + dp(3), b.right - n, b.top + dp(3), stitch);
                c.drawLine(b.left + n, b.bottom - dp(3), b.right - n, b.bottom - dp(3), stitch);
                return;
            }
            c.drawPath(path, p);
            c.drawPath(path, fold);
            c.drawLine(b.left + dp(18), b.bottom - dp(4), b.right - dp(18), b.bottom - dp(4), stitch);
            drawSkull(c, b.left + dp(11), b.centerY(), b.height() * 0.24f, 0xFFB09A6A);
            drawSkull(c, b.right - dp(11), b.centerY(), b.height() * 0.24f, 0xFFB09A6A);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================ текст и кнопки
    /** Текст: в светлой теме чёрные тени заменяются мягким светлым ореолом. */
    static final class T extends TextView {
        T(Context c) { super(c); }
        @Override
        public void setShadowLayer(float r, float dx, float dy, int color) {
            if (!dark && lum(color) < 0.3f && Color.alpha(color) > 120) super.setShadowLayer(r * 0.6f, 0, dy * 0.5f, 0x66FFF6DC);
            else super.setShadowLayer(r, dx, dy, color);
        }
    }

    static TextView text(Context c, String s, float sp, Typeface tf, int color) {
        TextView t = new T(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    static TextView title(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, Theme.display, color);
        t.setLetterSpacing(0.04f);
        return t;
    }

    static TextView ribbon(Context c, String s, int color) {
        TextView t = text(c, s, 15, Theme.display, dark ? 0xFFD6EBC8 : 0xFFF6EBD0);
        t.setLetterSpacing(dark ? 0.14f : 0.1f);
        t.setBackground(new Ribbon(color));
        if (dark) { t.setPadding(dp(30), dp(5), dp(30), dp(8)); t.setShadowLayer(dp(6), 0, 0, 0xAA6FD45A); }
        else { t.setPadding(dp(22), dp(4), dp(22), dp(6)); t.setShadowLayer(dp(2), 0, dp(1), 0xCC000000); }
        return t;
    }

    static TextView button(Context c, String label, Drawable bg, int textColor, float sp, final View.OnClickListener l) {
        final TextView b = text(c, label, sp, Theme.display, textColor);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setBackground(bg);
        b.setPadding(dp(14), dp(6), dp(14), dp(8));
        b.setMinHeight(dp(46));
        b.setClickable(true);
        press(b);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { haptic(CLICK); Sfx.play("click"); l.onClick(v); }
        });
        return b;
    }

    /** Нажатие: пружинит, щёлкает и вспыхивает светом из-под пальца. */
    /** Маленькая иконка-корзина для удаления (ставится в угол). */
    static View trashButton(android.content.Context ctx, final View.OnClickListener l) {
        android.widget.FrameLayout b = new android.widget.FrameLayout(ctx);
        b.setBackground(states(new Steel(0xFF3A2220, 0xFF1A0E0C, 0xFF8A4A3A, 0, dp(6), false, false), new Steel(0xFF5A3028, 0xFF2A1410, 0xFFE07050, 0, dp(6), false, true)));
        b.addView(new RosterScreen.GlyphView(ctx, "trash", 0xFFE8907A), new android.widget.FrameLayout.LayoutParams(-1, -1));
        b.setClickable(true);
        b.setContentDescription("Удалить");
        press(b);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { haptic(CLICK); l.onClick(v); }
        });
        return b;
    }

    static void press(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            Glow g;
            public boolean onTouch(final View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.965f).scaleY(0.965f).setDuration(80).setInterpolator(new DecelerateInterpolator()).start();
                    if (g != null) v.getOverlay().remove(g);
                    g = new Glow(glowColor(), e.getX(), e.getY());
                    g.setBounds(0, 0, v.getWidth(), v.getHeight());
                    v.getOverlay().add(g);
                    g.grow(v, 0.55f, 160);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
                    final Glow fg = g;
                    g = null;
                    if (fg != null) fg.fade(v, a == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });
    }

    /** Световое пятно нажатия: растёт из точки касания и гаснет. */
    static final class Glow extends Drawable {
        final int color;
        final float x, y;
        float radius, alpha = 1f;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glow(int color, float x, float y) { this.color = color; this.x = x; this.y = y; }

        void grow(final View v, final float to, int ms) {
            final float max = Math.max(getBounds().width(), getBounds().height());
            android.animation.ValueAnimator an = android.animation.ValueAnimator.ofFloat(0.05f, to);
            an.setDuration(ms);
            an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator va) { radius = max * (Float) va.getAnimatedValue(); invalidateSelf(); v.invalidate(); }
            });
            an.start();
        }

        void fade(final View v, boolean flash) {
            final float max = Math.max(getBounds().width(), getBounds().height());
            final float r0 = radius;
            android.animation.ValueAnimator an = android.animation.ValueAnimator.ofFloat(0, 1);
            an.setDuration(flash ? 420 : 200);
            an.setInterpolator(new DecelerateInterpolator());
            an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator va) {
                    float f = (Float) va.getAnimatedValue();
                    radius = r0 + (max * 1.1f - r0) * f;
                    alpha = 1 - f;
                    invalidateSelf();
                    v.invalidate();
                }
            });
            an.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { v.getOverlay().remove(Glow.this); }
            });
            an.start();
        }

        @Override
        public void draw(Canvas c) {
            if (radius < 1 || alpha <= 0) return;
            p.setShader(new RadialGradient(x, y, radius, new int[]{Theme.alpha(color, 0.32f * alpha), Theme.alpha(color, 0.12f * alpha), Theme.alpha(color, 0)}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(getBounds(), p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Появление экрана: панели «выезжают» снизу по очереди, как механизм. */
    static void reveal(ViewGroup col) {
        int n = Math.min(col.getChildCount(), 14);
        for (int i = 0; i < n; i++) {
            View ch = col.getChildAt(i);
            ch.setAlpha(0f);
            ch.setTranslationY(dp(22));
            ch.animate().alpha(1f).translationY(0).setStartDelay(40 + i * 35L).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(l, t, r, b); return p; }

    static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }

    /** Разделитель с черепом посередине. */
    static View skullDivider(Context c, final int color) {
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas cv) {
                float w = getWidth(), h = getHeight(), cy = h / 2, cx = w / 2;
                p.setShader(new LinearGradient(0, 0, w, 0, new int[]{Theme.alpha(color, 0), color, Theme.alpha(color, 0)}, null, Shader.TileMode.CLAMP));
                p.setStrokeWidth(dp(1));
                cv.drawLine(0, cy - dp(2.5f), cx - dp(16), cy - dp(2.5f), p);
                cv.drawLine(cx + dp(16), cy - dp(2.5f), w, cy - dp(2.5f), p);
                cv.drawLine(dp(24), cy + dp(2.5f), cx - dp(16), cy + dp(2.5f), p);
                cv.drawLine(cx + dp(16), cy + dp(2.5f), w - dp(24), cy + dp(2.5f), p);
                p.setShader(null);
                drawSkull(cv, cx, cy, h * 0.42f, color);
            }
        };
        v.setLayoutParams(lpm(-1, dp(22), 0, dp(6), 0, dp(6)));
        return v;
    }

    /** Стилизованный череп. */
    static void drawSkull(Canvas c, float cx, float cy, float r, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.5f, r * 1.8f, Theme.mix(color, Color.WHITE, 0.35f), Theme.mix(color, Color.BLACK, 0.45f), Shader.TileMode.CLAMP));
        Path s = new Path();
        s.addCircle(cx, cy - r * 0.15f, r * 0.82f, Path.Direction.CW);
        s.addRoundRect(new RectF(cx - r * 0.5f, cy + r * 0.25f, cx + r * 0.5f, cy + r * 0.95f), r * 0.18f, r * 0.18f, Path.Direction.CW);
        c.drawPath(s, p);
        p.setShader(null);
        p.setColor(0xE6120C08);
        c.drawOval(new RectF(cx - r * 0.55f, cy - r * 0.25f, cx - r * 0.1f, cy + r * 0.2f), p);
        c.drawOval(new RectF(cx + r * 0.1f, cy - r * 0.25f, cx + r * 0.55f, cy + r * 0.2f), p);
        Path nose = new Path();
        nose.moveTo(cx, cy + r * 0.22f); nose.lineTo(cx - r * 0.1f, cy + r * 0.42f); nose.lineTo(cx + r * 0.1f, cy + r * 0.42f); nose.close();
        c.drawPath(nose, p);
        p.setStrokeWidth(Math.max(1, r * 0.07f));
        for (int i = -2; i <= 2; i++) c.drawLine(cx + i * r * 0.17f, cy + r * 0.6f, cx + i * r * 0.17f, cy + r * 0.92f, p);
    }

    // ================================================================ фон: собор
    /** Тёмный собор: арки, свечное свечение снизу, номер легиона и виньетка. */
    static final class Backdrop extends View {
        private Theme theme;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), grainP = new Paint();

        Backdrop(Context c, Theme t) {
            super(c);
            theme = t;
            grainP.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        void setTheme(Theme t) {
            boolean same = theme != null && t != null && theme.glow == t.glow && String.valueOf(theme.numeral).equals(String.valueOf(t.numeral));
            theme = t;
            if (!same) cache = null;
            invalidate();
        }

        private Bitmap cache;
        private int cw, ch;

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (!dark) {
                if (cache == null || cw != w || ch != h) { cw = w; ch = h; cache = parchmentPage(w, h, theme); }
                c.drawBitmap(cache, 0, 0, null);
                return;
            }
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF141B18, 0xFF0C100E, 0xFF0A0B09}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            // арки
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(0x14C8D8C0);
            float aw = w / 3f;
            for (int i = 0; i < 3; i++) {
                float l = i * aw + dp(14), r = (i + 1) * aw - dp(14), top = h * 0.12f, mid = (l + r) / 2;
                Path a = new Path();
                a.moveTo(l, h * 0.75f);
                a.lineTo(l, top + aw * 0.5f);
                a.quadTo(l, top, mid, top - aw * 0.15f);
                a.quadTo(r, top, r, top + aw * 0.5f);
                a.lineTo(r, h * 0.75f);
                c.drawPath(a, p);
                // переплёт витража
                p.setColor(0x0CC8D8C0);
                c.drawLine(mid, top, mid, h * 0.75f, p);
                for (int k = 1; k < 5; k++) c.drawLine(l, top + aw * 0.5f + k * h * 0.11f, r, top + aw * 0.5f + k * h * 0.11f, p);
                p.setColor(0x14C8D8C0);
            }
            p.setStyle(Paint.Style.FILL);
            // свечение легиона сверху и тёплое снизу (свечи)
            p.setShader(new RadialGradient(w * 0.5f, -h * 0.05f, h * 0.65f, Theme.alpha(theme.glow, 0.28f), Theme.alpha(theme.glow, 0f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.5f, h * 1.05f, h * 0.55f, 0x40E08A30, 0x00E08A30, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            c.drawRect(0, 0, w, h, grainP);
            if (theme.numeral != null && !theme.numeral.isEmpty()) {
                p.setShader(null);
                p.setTypeface(Theme.namesBlack);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(Theme.alpha(theme.accentBright(), 0.06f));
                float size = Math.min(w * 1.1f / Math.max(1, theme.numeral.length() * 0.62f), h * 0.42f);
                p.setTextSize(size);
                c.drawText(theme.numeral, w * 0.5f, h * 0.6f + size * 0.35f, p);
            }
            p.setShader(new RadialGradient(w * 0.5f, h * 0.45f, Math.max(w, h) * 0.78f, 0x00000000, 0xC0000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
    }

    /** Большой лист старого пергамента: волокна, пятна, обожжённые края, водяной знак. */
    static Bitmap parchmentPage(int w, int h, Theme theme) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.4f, h, new int[]{0xFFF1E4C2, 0xFFE6D3A6, 0xFFDCC48F}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new BitmapShader(paper(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Random r = new Random(17);
        for (int i = 0; i < 18; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = dp(30 + r.nextInt(120));
            p.setShader(new RadialGradient(x, y, rad, 0x18704010, 0x00704010, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        p.setShader(null);
        if (theme != null && theme.numeral != null && !theme.numeral.isEmpty()) {
            p.setTypeface(Theme.namesBlack);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0x146A3A14);
            float size = Math.min(w * 1.1f / Math.max(1, theme.numeral.length() * 0.62f), h * 0.42f);
            p.setTextSize(size);
            c.drawText(theme.numeral, w * 0.5f, h * 0.6f + size * 0.35f, p);
        } else {
            Bitmap aq = Art.get("f_aquila");
            if (aq != null) {
                Paint ap = new Paint(Paint.FILTER_BITMAP_FLAG);
                ap.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFF6A3A14, android.graphics.PorterDuff.Mode.SRC_IN));
                ap.setAlpha(22);
                float sz = w * 0.85f;
                c.drawBitmap(aq, null, new RectF(w / 2f - sz / 2, h * 0.55f - sz / 2, w / 2f + sz / 2, h * 0.55f + sz / 2), ap);
            }
        }
        p.setColor(0x30A02018);
        c.drawRect(dp(8), 0, dp(9), h, p);
        c.drawRect(w - dp(9), 0, w - dp(8), h, p);
        p.setShader(new RadialGradient(w / 2f, h * 0.48f, Math.max(w, h) * 0.72f, new int[]{0x00000000, 0x00000000, 0x553A1E08, 0xCC24120A}, new float[]{0, 0.6f, 0.86f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        return b;
    }

    // ================================================================ свечи
    /** Ряд оплывших свечей (картинка) с мягким живым свечением пламени. */
    static final class Candles extends View {
        private final float[] heights, phase;
        private final long t0 = SystemClock.uptimeMillis();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final int count;
        boolean skulls = true;

        Candles(Context c, int count, long seed) {
            super(c);
            this.count = count;
            Random r = new Random(seed);
            heights = new float[count];
            phase = new float[count];
            for (int i = 0; i < count; i++) { heights[i] = 0.62f + r.nextFloat() * 0.38f; phase[i] = r.nextFloat() * 10; }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float slot = w / count;
            // свечение должно целиком помещаться в вид — иначе видны края прямоугольника
            for (int i = 0; i < count; i++) {
                float cx = slot * (i + 0.5f) + (float) Math.sin(i * 7.3) * slot * 0.1f;
                drawCandle(c, p, cx, h - dp(2), h * 0.55f * heights[i], t, phase[i]);
            }
            if (skulls) for (int i = 0; i < count - 1; i++) drawSkull(c, slot * (i + 1), h - dp(9), dp(7), dark ? 0xFF8A7A5A : 0xFFB8A882);
            postInvalidateDelayed(50);
        }
    }

    /** Одна свеча-картинка: основание в baseY, высота ch; пламя мягко пульсирует светом. */
    static void drawCandle(Canvas c, Paint p, float cx, float baseY, float ch, float t, float phase) {
        Bitmap cd = Art.get("candle");
        float cw = cd == null ? ch * 0.3f : ch * cd.getWidth() / cd.getHeight();
        float top = baseY - ch;
        float fy = top + ch * 0.05f;
        float fl = (float) (0.9 + 0.06 * Math.sin(t * 7 + phase) + 0.04 * Math.sin(t * 17 + phase * 2));
        p.setShader(new RadialGradient(cx, fy, ch * 0.75f, new int[]{0x50FFB050, 0x18FF9030, 0x00FF9030}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        p.setAlpha((int) (255 * fl));
        c.drawCircle(cx, fy, ch * 0.75f, p);
        p.setShader(null);
        p.setAlpha(255);
        if (cd != null) c.drawBitmap(cd, null, new RectF(cx - cw / 2, top, cx + cw / 2, baseY), p);
        p.setShader(new RadialGradient(cx, fy, ch * 0.09f, Theme.alpha(0xFFFFE2A0, 0.55f * fl), 0x00FFE2A0, Shader.TileMode.CLAMP));
        c.drawCircle(cx, fy, ch * 0.09f, p);
        p.setShader(null);
    }

    // ================================================================ реплика как в комиксе
    /** Облачко реплики с хвостиком к говорящему (слева). */
    static final class Bubble extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Bubble() {
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(2));
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(0xFF1A120A);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float tail = dp(12), r = dp(14);
            RectF body = new RectF(b.left + tail + dp(1), b.top + dp(1), b.right - dp(1), b.bottom - dp(1));
            path.reset();
            path.addRoundRect(body, r, r, Path.Direction.CW);
            Path t = new Path();
            float ty = body.top + Math.min(dp(26), body.height() * 0.4f);
            t.moveTo(body.left + dp(2), ty - dp(7));
            t.lineTo(b.left + dp(1), ty + dp(4));
            t.lineTo(body.left + dp(2), ty + dp(7));
            t.close();
            path.op(t, Path.Op.UNION);
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFFFFF8E6, 0xFFEADCB8, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, fill);
            c.drawPath(path, line);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================ сервочереп
    /** Парящий сервочереп: покачивается, линзa-глаз пульсирует красным. */
    static final class ServoSkull extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final long t0 = SystemClock.uptimeMillis();
        boolean mirror;

        ServoSkull(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            Bitmap b = Art.get("servoskull");
            if (b == null) return;
            float w = getWidth(), h = getHeight(), t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float s = Math.min(w / b.getWidth(), h * 0.88f / b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            float bob = (float) Math.sin(t * 1.6) * h * 0.04f;
            float tilt = (float) Math.sin(t * 0.9) * 3f;
            float cx = w / 2, cy = h / 2 + bob;
            // тень снизу
            p.setShader(new RadialGradient(cx, h - dp(4), bw * 0.4f, 0x50000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawOval(new RectF(cx - bw * 0.4f, h - dp(10), cx + bw * 0.4f, h), p);
            p.setShader(null);
            c.save();
            c.rotate(tilt, cx, cy);
            if (mirror) c.scale(-1, 1, cx, cy);
            c.drawBitmap(b, null, new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), p);
            float ex = cx - bw / 2 + bw * 0.588f, ey = cy - bh / 2 + bh * 0.446f;
            float pulse = (float) (0.55 + 0.45 * Math.sin(t * 3.1));
            p.setShader(new RadialGradient(ex, ey, bw * 0.16f, Theme.alpha(0xFFFF3020, 0.55f * pulse), 0x00FF3020, Shader.TileMode.CLAMP));
            c.drawCircle(ex, ey, bw * 0.16f, p);
            p.setShader(null);
            c.restore();
            postInvalidateDelayed(40);
        }
    }

    // ================================================================ гекс
    /** Гексагональная рамка (как значки репутации) с символом внутри. */
    static final class Hex extends View {
        final Theme theme;
        String symbol;   // тип символа для Glyph.draw
        Bitmap photo;
        String label;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Hex(Context c, Theme t, String symbol) { super(c); theme = t; this.symbol = symbol; }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - dp(1.5f);
            Path hex = hexPath(cx, cy, r);
            Path innerHex = hexPath(cx, cy, r * 0.84f);
            p.setStyle(Paint.Style.FILL);
            int tr = trimOf(theme);
            if (dark) p.setShader(new LinearGradient(0, cy - r, 0, cy + r, new int[]{Theme.mix(tr, Color.WHITE, 0.3f), Theme.mix(tr, Color.BLACK, 0.35f), Theme.mix(tr, Color.BLACK, 0.7f)}, null, Shader.TileMode.CLAMP));
            else p.setShader(new LinearGradient(0, cy - r, 0, cy + r, Theme.mix(theme.trim, Color.WHITE, 0.4f), Theme.mix(theme.trim, Color.BLACK, 0.5f), Shader.TileMode.CLAMP));
            c.drawPath(hex, p);
            p.setShader(dark ? new RadialGradient(cx, cy - r * 0.2f, r * 1.1f, 0xFF16241A, 0xFF050806, Shader.TileMode.CLAMP)
                    : new RadialGradient(cx, cy - r * 0.3f, r * 1.2f, 0xFF2E3A34, 0xFF0E1311, Shader.TileMode.CLAMP));
            c.drawPath(innerHex, p);
            p.setShader(null);
            if (photo == null && dark) {
                c.save();
                c.clipPath(innerHex);
                p.setColor(0x22000000);
                for (float y = cy - r; y < cy + r; y += dp(2)) c.drawRect(cx - r, y, cx + r, y + dp(0.9f), p);
                c.restore();
            }
            if (photo != null) {
                c.save();
                c.clipPath(innerHex);
                float s = Math.max(r * 2 / photo.getWidth(), r * 2 / photo.getHeight());
                Matrix m = new Matrix();
                m.postScale(s, s);
                m.postTranslate(cx - photo.getWidth() * s / 2, cy - photo.getHeight() * s / 2);
                c.drawBitmap(photo, m, p);
                c.restore();
            } else if (symbol != null) {
                if ("shield".equals(symbol) && theme.icon != null) Glyph.drawFaction(c, theme.icon, cx, cy, r * 0.62f, dark ? Theme.mix(PHOS, theme.accentBright(), 0.3f) : Theme.mix(theme.trim, Color.WHITE, 0.25f));
                else Glyph.draw(c, symbol, cx, cy, r * 0.62f, dark ? Theme.mix(PHOS, theme.accentBright(), 0.3f) : Theme.mix(theme.trim, Color.WHITE, 0.25f));
            } else if (theme.icon != null) {
                Glyph.drawFaction(c, theme.icon, cx, cy, r * 0.62f, dark ? Theme.mix(PHOS, theme.accentBright(), 0.3f) : Theme.mix(theme.trim, Color.WHITE, 0.25f));
            }
            if (label != null) {
                p.setTypeface(Theme.namesBlack);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(r * (label.length() > 3 ? 0.42f : 0.6f));
                p.setColor(BONE);
                p.setShadowLayer(dp(2), 0, dp(1), 0xFF000000);
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2, p);
                p.clearShadowLayer();
            }
        }

        static Path hexPath(float cx, float cy, float r) {
            Path p = new Path();
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 6 + i * Math.PI / 3;
                float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
            return p;
        }
    }

    // ================================================================ печать
    static final class Seal extends View {
        int errors;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Seal(Context c) { super(c); }

        void set(int e) { errors = e; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - dp(1);
            int base = errors > 0 ? 0xFFA51F1F : 0xFF5E7A3A;
            path.reset();
            int bumps = 14;
            for (int i = 0; i <= bumps * 4; i++) {
                double a = Math.PI * 2 * i / (bumps * 4);
                float rr = (float) (r * (0.9 + 0.08 * Math.sin(a * bumps) + 0.02 * Math.sin(a * 5)));
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.4f, Theme.mix(base, Color.WHITE, 0.25f), Theme.mix(base, Color.BLACK, 0.55f), Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setColor(0x59000000);
            c.drawCircle(cx, cy, r * 0.66f, p);
            p.setStyle(Paint.Style.FILL);
            if (errors > 0) {
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Theme.namesBlack);
                p.setColor(0xFFF3E6CC);
                String s = errors > 99 ? "99+" : String.valueOf(errors);
                p.setTextSize(r * (s.length() > 2 ? 0.55f : 0.72f));
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, p);
            } else {
                drawSkull(c, cx, cy, r * 0.48f, 0xFFE8DCB8);
            }
        }
    }

    // ================================================================ шкала
    static final class Gauge extends View {
        double value, limit;
        Theme theme;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Gauge(Context c, Theme t) { super(c); theme = t; }

        void set(double v, double lim) { value = v; limit = lim; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            chamfer(path, 0, 0, w, h, h * 0.5f);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF080A09);
            c.drawPath(path, p);
            double frac = limit > 0 ? Math.min(1, value / limit) : 0;
            boolean over = limit > 0 && value > limit;
            int col = over ? 0xFFB02A20 : theme.accentBright();
            c.save();
            c.clipPath(path);
            p.setShader(new LinearGradient(0, 0, 0, h, Theme.mix(col, Color.WHITE, 0.35f), Theme.mix(col, Color.BLACK, 0.35f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, (float) (w * frac), h, p);
            p.setShader(null);
            p.setColor(0x66000000);
            p.setStrokeWidth(dp(1));
            if (limit > 0) for (double m = 500; m < limit; m += 500) { float x = (float) (w * m / limit); c.drawLine(x, h * 0.2f, x, h * 0.8f, p); }
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setShader(new LinearGradient(0, 0, w, 0, Theme.mix(GOLD, Color.WHITE, 0.3f), GOLD_DIM, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
    }

    /** Простой перенос по строкам (для чипов и характеристик). */
    static final class Flow extends android.view.ViewGroup {
        int gap = dp(6);

        Flow(Context c) { super(c); }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                ch.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                if (x > 0 && x + ch.getMeasuredWidth() > w) { x = 0; y += rowH + gap; rowH = 0; }
                x += ch.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, ch.getMeasuredHeight());
            }
            setMeasuredDimension(w, y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > w) { x = 0; y += rowH + gap; rowH = 0; }
                ch.layout(x, y, x + cw, y + chh);
                x += cw + gap;
                rowH = Math.max(rowH, chh);
            }
        }
    }
}
