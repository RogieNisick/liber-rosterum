package com.medoedy.liber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.medoedy.liber.Ui.dp;

/**
 * Бессмысленное, но благочестивое: если ростер долго не трогают, прилетает сервочереп и расставляет
 * свечи и черепа на верхних кромках плашек. Касание — череп улетает, всё рушится вниз.
 */
final class Shrine {
    private Shrine() { }

    static final long IDLE_MS = 30000;

    /** Корневой контейнер экрана, который замечает любое касание. */
    static final class Frame extends FrameLayout {
        Runnable onTouch;
        Frame(Context c) { super(c); }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (onTouch != null && (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_MOVE)) onTouch.run();
            return super.dispatchTouchEvent(ev);
        }
    }

    /** Управляет таймером бездействия и слоем со сервочерепом. */
    static final class Keeper {
        final MainActivity a;
        final Screen owner;
        final Frame frame;
        final List<View> shelves;
        Layer layer;
        final Runnable idle = new Runnable() { public void run() { wake(); } };

        Keeper(MainActivity a, Screen owner, Frame frame, List<View> shelves) {
            this.a = a; this.owner = owner; this.frame = frame; this.shelves = shelves;
            frame.onTouch = new Runnable() { public void run() { touched(); } };
            schedule();
        }

        void schedule() {
            a.h.removeCallbacks(idle);
            a.h.postDelayed(idle, IDLE_MS);
        }

        void touched() {
            if (layer != null && !layer.leaving) layer.scatter();
            schedule();
        }

        void wake() {
            if (!a.stack.contains(owner)) return; // экран закрыт — больше не ждём
            if (a.stack.peek() != owner || a.overlay.getChildCount() > 0 || !frame.isAttachedToWindow() || !frame.isShown()) { schedule(); return; }
            if (layer != null) return;
            layer = new Layer(a, this);
            frame.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        }

        void gone() {
            if (layer != null) frame.removeView(layer);
            layer = null;
        }
    }

    // ================================================================ предметы
    static final class Item {
        boolean candle;
        float x, y;          // основание (центр по x, низ по y)
        float size;          // высота свечи или радиус черепа
        float phase, tilt;
        // падение
        boolean falling;
        long fallAt;
        float vx, vy, rot, vr;
        float alpha = 1;
        // мягкая посадка
        float drop;          // остаток высоты над полкой
        float dv;
    }

    static final class Shelf { float x0, x1, y; }

    static final class Layer extends View {
        final MainActivity a;
        final Keeper k;
        final Random rnd = new Random();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint wire = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<Item> items = new ArrayList<Item>();
        final long t0 = SystemClock.uptimeMillis();
        long last = SystemClock.uptimeMillis();

        // сервочереп
        float sx, sy, fromX, fromY, toX, toY;
        boolean facingLeft;
        int phase;              // 0 летит к цели, 1 опускается, 2 пауза
        long phaseAt, phaseLen;
        Item carry;
        Shelf targetShelf;
        boolean leaving;
        float lvx;              // скорость при отлёте
        int enterSide;

        Layer(MainActivity a, Keeper k) {
            super(a);
            this.a = a;
            this.k = k;
            setClickable(false);
            setFocusable(false);
            post(new Runnable() { public void run() { enter(); } });
        }

        float now() { return (SystemClock.uptimeMillis() - t0) / 1000f; }

        // ---------------------------------------------------------- полки
        List<Shelf> shelves() {
            List<Shelf> out = new ArrayList<Shelf>();
            int[] me = new int[2];
            getLocationInWindow(me);
            int[] loc = new int[2];
            float h = getHeight(), w = getWidth();
            for (View v : k.shelves) {
                if (v == null || !v.isShown() || v.getWidth() < dp(60)) continue;
                v.getLocationInWindow(loc);
                Shelf s = new Shelf();
                s.y = loc[1] - me[1] + dp(2.5f);
                s.x0 = Math.max(dp(10), loc[0] - me[0] + dp(20));
                s.x1 = Math.min(w - dp(10), loc[0] - me[0] + v.getWidth() - dp(20));
                if (s.y < dp(70) || s.y > h - dp(16) || s.x1 - s.x0 < dp(24)) continue;
                out.add(s);
            }
            return out;
        }

        Item newItem() {
            Item it = new Item();
            it.candle = rnd.nextFloat() < 0.6f;
            it.size = it.candle ? dp(26 + rnd.nextInt(16)) : dp(7 + rnd.nextInt(4));
            it.phase = rnd.nextFloat() * 6.28f;
            it.tilt = it.candle ? 0 : (rnd.nextFloat() - 0.5f) * 24f;
            return it;
        }

        float itemHeight(Item it) { return it.candle ? it.size : it.size * 1.9f; }

        // ---------------------------------------------------------- сценарий
        void enter() {
            enterSide = rnd.nextBoolean() ? 0 : 1;
            sx = enterSide == 0 ? -dp(90) : getWidth() + dp(90);
            sy = getHeight() * (0.2f + rnd.nextFloat() * 0.3f);
            Sfx.play("hum", 0.25f, 1.3f);
            nextTarget();
        }

        void nextTarget() {
            List<Shelf> sh = shelves();
            carry = newItem();
            if (sh.isEmpty()) { targetShelf = null; flyTo(getWidth() * (0.2f + rnd.nextFloat() * 0.6f), getHeight() * (0.2f + rnd.nextFloat() * 0.5f)); return; }
            Shelf best = null;
            float bx = 0;
            for (int tries = 0; tries < 16 && best == null; tries++) {
                Shelf s = sh.get(rnd.nextInt(sh.size()));
                float x = s.x0 + rnd.nextFloat() * (s.x1 - s.x0);
                boolean free = true;
                for (Item it : items) {
                    if (it.falling || Math.abs(it.y - s.y) > dp(3)) continue;
                    float gap = (it.candle ? it.size * 0.36f : it.size) + (carry.candle ? carry.size * 0.36f : carry.size) + dp(5);
                    if (Math.abs(it.x - x) < gap) { free = false; break; }
                }
                if (free) { best = s; bx = x; }
            }
            if (best == null) { Shelf s = sh.get(rnd.nextInt(sh.size())); best = s; bx = s.x0 + rnd.nextFloat() * (s.x1 - s.x0); }
            targetShelf = best;
            carry.x = bx;
            carry.y = best.y;
            flyTo(bx, best.y - itemHeight(carry) - hang() - dp(20));
        }

        float skullH() { Bitmap b = Art.get("servoskull"); return b == null ? dp(70) : dp(58) * b.getHeight() / b.getWidth(); }

        /** От центра черепа до низа висящего предмета (без высоты предмета). */
        float hang() { return skullH() * 0.32f + dp(10); }

        void flyTo(float x, float y) {
            fromX = sx; fromY = sy; toX = x; toY = y;
            facingLeft = toX < fromX;
            float d = (float) Math.hypot(toX - fromX, toY - fromY);
            phase = 0;
            phaseAt = SystemClock.uptimeMillis();
            phaseLen = (long) Math.max(500, Math.min(1800, d / dp(1) * 3.2f));
            invalidate();
        }

        void scatter() {
            leaving = true;
            long nowMs = SystemClock.uptimeMillis();
            boolean right = enterSide == 0; // улетает в противоположную сторону
            facingLeft = !right;
            lvx = right ? dp(120) : -dp(120);
            Sfx.play("hum", 0.2f, 1.8f);
            if (carry != null && phase < 2) {
                // уронить то, что нёс
                carry.x = sx; carry.y = sy + dp(26) + itemHeight(carry);
                items.add(carry);
            }
            carry = null;
            for (Item it : items) {
                it.falling = true;
                it.fallAt = nowMs + rnd.nextInt(380);
                it.vx = (rnd.nextFloat() - 0.5f) * dp(140);
                it.vy = -rnd.nextFloat() * dp(160);
                it.vr = (rnd.nextFloat() - 0.5f) * 720f;
            }
            invalidate();
        }

        // ---------------------------------------------------------- кадр
        @Override
        protected void onDraw(Canvas c) {
            long ms = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (ms - last) / 1000f);
            last = ms;
            float t = now();
            float w = getWidth(), h = getHeight();

            // предметы
            boolean anyLeft = false;
            for (int i = items.size() - 1; i >= 0; i--) {
                Item it = items.get(i);
                if (it.falling) {
                    if (ms >= it.fallAt) {
                        it.vy += dp(2400) * dt;
                        it.x += it.vx * dt;
                        it.y += it.vy * dt;
                        it.rot += it.vr * dt;
                        it.vx *= 0.995f;
                    }
                    if (it.y - itemHeight(it) * 1.5f > h + dp(20)) { items.remove(i); continue; }
                } else if (it.drop > 0) {
                    it.dv += dp(2000) * dt;
                    it.drop -= it.dv * dt;
                    if (it.drop <= 0) {
                        if (it.dv > dp(160)) { it.dv = -it.dv * 0.25f; it.drop = 0.01f; } else { it.drop = 0; it.dv = 0; }
                    }
                }
                if (it.alpha < 1 && !it.falling) { it.alpha -= dt * 1.5f; if (it.alpha <= 0) { items.remove(i); continue; } }
                anyLeft = true;
                drawItem(c, it, t);
            }

            // сервочереп
            boolean skullVisible = true;
            if (leaving) {
                lvx *= 1f + dt * 4.5f;
                sx += lvx * dt;
                sy -= dp(40) * dt;
                if (sx < -dp(140) || sx > w + dp(140)) skullVisible = false;
            } else {
                step(ms);
            }
            if (skullVisible) drawSkull(c, t);

            if (leaving && !skullVisible && !anyLeft) { k.gone(); return; }
            postInvalidateOnAnimation();
        }

        void step(long ms) {
            float k2 = Math.min(1f, (ms - phaseAt) / (float) phaseLen);
            if (phase == 0) {
                float e = k2 < 0.5f ? 2 * k2 * k2 : 1 - (float) Math.pow(-2 * k2 + 2, 2) / 2;
                float arc = (float) Math.sin(k2 * Math.PI) * -dp(26);
                sx = fromX + (toX - fromX) * e;
                sy = fromY + (toY - fromY) * e + arc;
                if (k2 >= 1) {
                    if (targetShelf == null) { pause(); return; }
                    phase = 1; phaseAt = ms; phaseLen = 360;
                }
            } else if (phase == 1) {
                float dip = (float) Math.sin(k2 * Math.PI) * dp(12);
                sx = toX; sy = toY + dip;
                if (k2 >= 0.5f && carry != null) {
                    Item it = carry;
                    it.drop = Math.max(0, it.y - (sy + hang() + itemHeight(it)));
                    it.dv = 0;
                    items.add(it);
                    carry = null;
                    Sfx.play("tick", 0.22f, it.candle ? 1.4f : 0.9f);
                    if (items.size() > 140) for (Item o : items) if (!o.falling && o.alpha >= 1) { o.alpha = 0.99f; break; }
                }
                if (k2 >= 1) pause();
            } else {
                sx = toX + (float) Math.sin(now() * 2.2f) * dp(3);
                sy = toY + (float) Math.sin(now() * 1.7f) * dp(4);
                if (k2 >= 1) nextTarget();
            }
        }

        void pause() {
            phase = 2;
            phaseAt = SystemClock.uptimeMillis();
            phaseLen = 250 + rnd.nextInt(650);
        }

        void drawItem(Canvas c, Item it, float t) {
            float lift = it.falling ? 0 : it.drop;
            float baseY = it.y - lift;
            int save = c.save();
            float hgt = itemHeight(it);
            if (it.falling) c.rotate(it.rot, it.x, baseY - hgt / 2);
            else if (!it.candle) c.rotate(it.tilt * 0.3f, it.x, baseY);
            if (it.alpha < 1) c.saveLayerAlpha(null, (int) (255 * Math.max(0, it.alpha)));
            if (it.candle) {
                standCandle(c, it, baseY, t);
            } else {
                Ui.drawSkull(c, it.x, baseY - it.size * 0.95f, it.size, 0xFFD9CCAE);
            }
            c.restoreToCount(save);
        }

        /** Стоящая свеча: оплывший огарок из спрайта, лужица воска у основания, мерцающий огонёк. */
        void standCandle(Canvas c, Item it, float baseY, float t) {
            Bitmap cd = Art.get("candle_stand");
            float ch = it.size, cw = cd == null ? ch * 0.65f : ch * cd.getWidth() / cd.getHeight();
            float fx = it.x + cw * 0.05f, fy = baseY - ch * 0.87f;
            float fl = (float) (0.88 + 0.07 * Math.sin(t * 7 + it.phase) + 0.05 * Math.sin(t * 17 + it.phase * 2));
            p.setShader(new RadialGradient(fx, fy, ch * 0.8f, new int[]{Theme.alpha(0x60FFB050, fl), 0x1CFF9030, 0x00FF9030}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, ch * 0.8f, p);
            p.setShader(null);
            if (!it.falling) {
                p.setColor(0x40000000);
                c.drawOval(new RectF(it.x - cw * 0.55f, baseY - dp(1.5f), it.x + cw * 0.55f, baseY + dp(2.5f)), p);
                p.setColor(0xF0E9DFC6);
                c.drawOval(new RectF(it.x - cw * 0.46f, baseY - dp(2.5f), it.x + cw * 0.46f, baseY + dp(1.2f)), p);
                p.setColor(0xFFFFFFFF);
            }
            if (cd != null) c.drawBitmap(cd, null, new RectF(it.x - cw / 2, baseY - ch, it.x + cw / 2, baseY + dp(0.5f)), p);
            p.setShader(new RadialGradient(fx, fy, ch * 0.12f, Theme.alpha(0xFFFFE6A8, 0.6f * fl), 0x00FFE2A0, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, ch * 0.12f, p);
            p.setShader(null);
        }

        void drawSkull(Canvas c, float t) {
            Bitmap b = Art.get("servoskull");
            float bw = dp(58), bh = b == null ? dp(70) : bw * b.getHeight() / b.getWidth();
            float bob = leaving ? 0 : (float) Math.sin(t * 2.4f) * dp(2);
            float cx = sx, cy = sy + bob;
            // что несёт — висит под черепом
            if (carry != null && !leaving) {
                Item it = carry;
                float hgt = itemHeight(it);
                wire.setColor(0xCC8A826E);
                wire.setStrokeWidth(dp(1.4f));
                c.drawLine(cx - dp(5), cy + bh * 0.32f, cx - dp(3), cy + bh * 0.32f + dp(10), wire);
                c.drawLine(cx + dp(5), cy + bh * 0.32f, cx + dp(3), cy + bh * 0.32f + dp(10), wire);
                float base = cy + bh * 0.32f + dp(10) + hgt;
                float x0 = it.x; it.x = cx;
                float y0 = it.y; it.y = base;
                float d0 = it.drop; it.drop = 0;
                drawItem(c, it, t);
                it.x = x0; it.y = y0; it.drop = d0;
            }
            if (b == null) { Ui.drawSkull(c, cx, cy, dp(20), 0xFFD9CCAE); return; }
            float tilt = leaving ? (facingLeft ? -12 : 12) : (phase == 0 ? (toX - fromX > 0 ? 6 : -6) : (float) Math.sin(t * 1.3f) * 3);
            c.save();
            c.rotate(tilt, cx, cy);
            if (facingLeft) c.scale(-1, 1, cx, cy);
            c.drawBitmap(b, null, new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), p);
            float ex = cx - bw / 2 + bw * 0.588f, ey = cy - bh / 2 + bh * 0.446f;
            float pulse = (float) (0.55 + 0.45 * Math.sin(t * 3.1));
            p.setShader(new RadialGradient(ex, ey, bw * 0.16f, Theme.alpha(0xFFFF3020, 0.6f * pulse), 0x00FF3020, Shader.TileMode.CLAMP));
            c.drawCircle(ex, ey, bw * 0.16f, p);
            p.setShader(null);
            c.restore();
        }
    }
}
