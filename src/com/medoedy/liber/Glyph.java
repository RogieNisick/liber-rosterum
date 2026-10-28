package com.medoedy.liber;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Нарисованные с нуля символы: типы юнитов и детачментов, миссии. */
final class Glyph {
    private Glyph() { }

    static void draw(Canvas c, String kind, float cx, float cy, float r, int color) {
        if (Ui.dark && Art.drawBadge(c, kind, cx, cy, r, color)) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, cy - r, 0, cy + r, Theme.mix(color, Color.WHITE, 0.3f), Theme.mix(color, Color.BLACK, 0.35f), Shader.TileMode.CLAMP));
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(Math.max(1.5f, r * 0.11f));
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStrokeJoin(Paint.Join.ROUND);
        s.setColor(color);
        Path q = new Path();
        switch (kind) {
            case "tank": {
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.05f, cx + r, cy + r * 0.55f), r * 0.25f, r * 0.25f, p);
                q.moveTo(cx - r * 0.75f, cy - r * 0.05f); q.lineTo(cx - r * 0.55f, cy - r * 0.45f); q.lineTo(cx + r * 0.45f, cy - r * 0.45f); q.lineTo(cx + r * 0.7f, cy - r * 0.05f); q.close();
                c.drawPath(q, p);
                c.drawRect(cx + r * 0.1f, cy - r * 0.38f, cx + r * 1.05f, cy - r * 0.26f, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xCC0E1311);
                for (int i = -2; i <= 2; i++) c.drawCircle(cx + i * r * 0.38f, cy + r * 0.28f, r * 0.13f, d);
                break;
            }
            case "walker": {
                c.drawRoundRect(new RectF(cx - r * 0.55f, cy - r * 0.75f, cx + r * 0.55f, cy + r * 0.05f), r * 0.2f, r * 0.2f, p);
                c.drawRect(cx - r * 0.95f, cy - r * 0.6f, cx - r * 0.6f, cy + r * 0.15f, p);
                c.drawRect(cx + r * 0.6f, cy - r * 0.6f, cx + r * 0.95f, cy - r * 0.25f, p);
                c.drawRect(cx + r * 0.95f, cy - r * 0.52f, cx + r * 1.15f, cy - r * 0.34f, p);
                s.setStrokeWidth(r * 0.22f);
                c.drawLine(cx - r * 0.3f, cy + r * 0.05f, cx - r * 0.45f, cy + r * 0.9f, s);
                c.drawLine(cx + r * 0.3f, cy + r * 0.05f, cx + r * 0.45f, cy + r * 0.9f, s);
                break;
            }
            case "knight": {
                c.drawCircle(cx, cy - r * 0.55f, r * 0.28f, p);
                q.moveTo(cx - r * 0.75f, cy - r * 0.3f); q.lineTo(cx + r * 0.75f, cy - r * 0.3f); q.lineTo(cx + r * 0.5f, cy + r * 0.3f); q.lineTo(cx - r * 0.5f, cy + r * 0.3f); q.close();
                c.drawPath(q, p);
                c.drawRect(cx - r * 1.05f, cy - r * 0.35f, cx - r * 0.75f, cy + r * 0.35f, p);
                c.drawRect(cx + r * 0.75f, cy - r * 0.45f, cx + r * 1.05f, cy + r * 0.1f, p);
                s.setStrokeWidth(r * 0.24f);
                c.drawLine(cx - r * 0.3f, cy + r * 0.3f, cx - r * 0.5f, cy + r * 0.95f, s);
                c.drawLine(cx + r * 0.3f, cy + r * 0.3f, cx + r * 0.5f, cy + r * 0.95f, s);
                break;
            }
            case "titan": {
                c.drawRect(cx - r * 0.35f, cy - r, cx + r * 0.35f, cy - r * 0.55f, p);
                c.drawRect(cx - r * 0.7f, cy - r * 0.55f, cx + r * 0.7f, cy + r * 0.15f, p);
                c.drawRect(cx - r * 1.1f, cy - r * 0.5f, cx - r * 0.75f, cy + r * 0.3f, p);
                c.drawRect(cx + r * 0.75f, cy - r * 0.5f, cx + r * 1.1f, cy + r * 0.3f, p);
                s.setStrokeWidth(r * 0.26f);
                c.drawLine(cx - r * 0.35f, cy + r * 0.15f, cx - r * 0.4f, cy + r, s);
                c.drawLine(cx + r * 0.35f, cy + r * 0.15f, cx + r * 0.4f, cy + r, s);
                break;
            }
            case "flyer": {
                q.moveTo(cx, cy - r); q.lineTo(cx + r * 0.18f, cy - r * 0.2f); q.lineTo(cx + r, cy + r * 0.25f); q.lineTo(cx + r, cy + r * 0.45f);
                q.lineTo(cx + r * 0.18f, cy + r * 0.25f); q.lineTo(cx + r * 0.15f, cy + r * 0.7f); q.lineTo(cx + r * 0.45f, cy + r * 0.95f);
                q.lineTo(cx - r * 0.45f, cy + r * 0.95f); q.lineTo(cx - r * 0.15f, cy + r * 0.7f); q.lineTo(cx - r * 0.18f, cy + r * 0.25f);
                q.lineTo(cx - r, cy + r * 0.45f); q.lineTo(cx - r, cy + r * 0.25f); q.lineTo(cx - r * 0.18f, cy - r * 0.2f); q.close();
                c.drawPath(q, p);
                break;
            }
            case "bike": {
                s.setStrokeWidth(r * 0.16f);
                c.drawCircle(cx - r * 0.6f, cy + r * 0.45f, r * 0.35f, s);
                c.drawCircle(cx + r * 0.6f, cy + r * 0.45f, r * 0.35f, s);
                q.moveTo(cx - r * 0.75f, cy + r * 0.1f); q.lineTo(cx + r * 0.85f, cy + r * 0.1f); q.lineTo(cx + r * 0.5f, cy - r * 0.25f); q.lineTo(cx - r * 0.35f, cy - r * 0.25f); q.close();
                c.drawPath(q, p);
                c.drawCircle(cx - r * 0.05f, cy - r * 0.6f, r * 0.22f, p);
                break;
            }
            case "heavy": case "infantry": case "command": {
                boolean heavy = kind.equals("heavy");
                float bw = heavy ? 0.62f : 0.48f;
                c.drawCircle(cx, cy - r * 0.62f, r * (heavy ? 0.24f : 0.26f), p);
                q.moveTo(cx - r * bw, cy - r * 0.35f); q.lineTo(cx + r * bw, cy - r * 0.35f); q.lineTo(cx + r * bw * 0.7f, cy + r * 0.3f); q.lineTo(cx - r * bw * 0.7f, cy + r * 0.3f); q.close();
                c.drawPath(q, p);
                // наплечники
                c.drawOval(new RectF(cx - r * (bw + 0.28f), cy - r * 0.5f, cx - r * (bw - 0.18f), cy - r * 0.08f), p);
                c.drawOval(new RectF(cx + r * (bw - 0.18f), cy - r * 0.5f, cx + r * (bw + 0.28f), cy - r * 0.08f), p);
                s.setStrokeWidth(r * (heavy ? 0.26f : 0.2f));
                c.drawLine(cx - r * 0.2f, cy + r * 0.3f, cx - r * 0.3f, cy + r * 0.95f, s);
                c.drawLine(cx + r * 0.2f, cy + r * 0.3f, cx + r * 0.3f, cy + r * 0.95f, s);
                if (kind.equals("command")) {
                    s.setStrokeWidth(r * 0.1f);
                    c.drawLine(cx + r * 0.75f, cy - r * 0.95f, cx + r * 0.75f, cy + r * 0.6f, s);
                    q.reset(); q.moveTo(cx + r * 0.75f, cy - r * 0.95f); q.lineTo(cx + r * 1.15f, cy - r * 0.8f); q.lineTo(cx + r * 0.75f, cy - r * 0.62f); q.close();
                    c.drawPath(q, p);
                } else {
                    s.setStrokeWidth(r * 0.14f);
                    c.drawLine(cx + r * 0.2f, cy - r * 0.05f, cx + r * 0.95f, cy - r * 0.3f, s);
                }
                break;
            }
            case "crown": {
                q.moveTo(cx - r * 0.9f, cy + r * 0.5f); q.lineTo(cx - r * 0.9f, cy - r * 0.35f); q.lineTo(cx - r * 0.45f, cy + r * 0.05f);
                q.lineTo(cx, cy - r * 0.7f); q.lineTo(cx + r * 0.45f, cy + r * 0.05f); q.lineTo(cx + r * 0.9f, cy - r * 0.35f); q.lineTo(cx + r * 0.9f, cy + r * 0.5f); q.close();
                c.drawPath(q, p);
                c.drawRect(cx - r * 0.9f, cy + r * 0.58f, cx + r * 0.9f, cy + r * 0.78f, p);
                break;
            }
            case "shield": {
                q.moveTo(cx - r * 0.8f, cy - r * 0.85f); q.lineTo(cx + r * 0.8f, cy - r * 0.85f); q.lineTo(cx + r * 0.8f, cy);
                q.quadTo(cx + r * 0.75f, cy + r * 0.7f, cx, cy + r); q.quadTo(cx - r * 0.75f, cy + r * 0.7f, cx - r * 0.8f, cy); q.close();
                c.drawPath(q, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xCC0E1311); d.setStrokeWidth(r * 0.12f);
                c.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, d);
                c.drawLine(cx - r * 0.45f, cy - r * 0.15f, cx + r * 0.45f, cy - r * 0.15f, d);
                break;
            }
            case "cannon": {
                c.drawRoundRect(new RectF(cx - r * 0.8f, cy + r * 0.15f, cx + r * 0.3f, cy + r * 0.65f), r * 0.15f, r * 0.15f, p);
                c.save(); c.rotate(-28, cx - r * 0.1f, cy + r * 0.2f);
                c.drawRect(cx - r * 0.2f, cy - r * 0.05f, cx + r * 1.05f, cy + r * 0.25f, p);
                c.restore();
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(color);
                c.drawCircle(cx - r * 0.55f, cy + r * 0.75f, r * 0.22f, d);
                c.drawCircle(cx + r * 0.05f, cy + r * 0.75f, r * 0.22f, d);
                break;
            }
            case "fist": {
                c.drawRoundRect(new RectF(cx - r * 0.7f, cy - r * 0.55f, cx + r * 0.7f, cy + r * 0.35f), r * 0.25f, r * 0.25f, p);
                c.drawRect(cx - r * 0.45f, cy + r * 0.35f, cx + r * 0.45f, cy + r * 0.95f, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xCC0E1311); d.setStrokeWidth(r * 0.08f);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * r * 0.35f, cy - r * 0.55f, cx + i * r * 0.35f, cy - r * 0.05f, d);
                // молния
                q.moveTo(cx + r * 0.25f, cy - r); q.lineTo(cx - r * 0.1f, cy - r * 0.62f); q.lineTo(cx + r * 0.1f, cy - r * 0.62f); q.lineTo(cx - r * 0.2f, cy - r * 0.3f);
                s.setStrokeWidth(r * 0.09f); c.drawPath(q, s);
                break;
            }
            case "eye": {
                q.moveTo(cx - r, cy); q.quadTo(cx, cy - r * 0.85f, cx + r, cy); q.quadTo(cx, cy + r * 0.85f, cx - r, cy); q.close();
                c.drawPath(q, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xEE0E1311);
                c.drawCircle(cx, cy, r * 0.35f, d);
                d.setColor(color);
                c.drawCircle(cx, cy, r * 0.13f, d);
                break;
            }
            case "transport": {
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.45f, cx + r * 0.8f, cy + r * 0.45f), r * 0.2f, r * 0.2f, p);
                q.moveTo(cx + r * 0.8f, cy - r * 0.45f); q.lineTo(cx + r * 1.05f, cy - r * 0.1f); q.lineTo(cx + r * 1.05f, cy + r * 0.45f); q.lineTo(cx + r * 0.8f, cy + r * 0.45f); q.close();
                c.drawPath(q, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xCC0E1311);
                c.drawRect(cx - r * 0.65f, cy - r * 0.25f, cx - r * 0.1f, cy + r * 0.05f, d);
                c.drawRect(cx + r * 0.05f, cy - r * 0.25f, cx + r * 0.6f, cy + r * 0.05f, d);
                s.setStrokeWidth(r * 0.12f);
                c.drawLine(cx - r * 0.5f, cy + r * 0.75f, cx + r * 0.5f, cy + r * 0.75f, s);
                break;
            }
            case "wing": {
                for (int side = -1; side <= 1; side += 2) {
                    q.reset();
                    q.moveTo(cx, cy + r * 0.2f);
                    q.quadTo(cx + side * r * 0.6f, cy - r * 0.9f, cx + side * r * 1.05f, cy - r * 0.7f);
                    q.lineTo(cx + side * r * 0.85f, cy - r * 0.35f);
                    q.lineTo(cx + side * r * 0.95f, cy - r * 0.2f);
                    q.lineTo(cx + side * r * 0.7f, cy + r * 0.05f);
                    q.lineTo(cx + side * r * 0.75f, cy + r * 0.25f);
                    q.close();
                    c.drawPath(q, p);
                }
                c.drawCircle(cx, cy + r * 0.35f, r * 0.2f, p);
                break;
            }
            case "psyker": {
                c.drawCircle(cx, cy, r * 0.32f, p);
                s.setStrokeWidth(r * 0.09f);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(cx + (float) Math.cos(a) * r * 0.5f, cy + (float) Math.sin(a) * r * 0.5f, cx + (float) Math.cos(a) * r * 0.95f, cy + (float) Math.sin(a) * r * 0.95f, s);
                }
                break;
            }
            case "banner": {
                s.setStrokeWidth(r * 0.12f);
                c.drawLine(cx - r * 0.55f, cy - r, cx - r * 0.55f, cy + r, s);
                q.moveTo(cx - r * 0.55f, cy - r * 0.85f); q.lineTo(cx + r * 0.85f, cy - r * 0.85f); q.lineTo(cx + r * 0.55f, cy - r * 0.35f);
                q.lineTo(cx + r * 0.85f, cy + r * 0.15f); q.lineTo(cx - r * 0.55f, cy + r * 0.15f); q.close();
                c.drawPath(q, p);
                break;
            }
            case "skull":
                Ui.drawSkull(c, cx, cy, r * 0.95f, color);
                break;
            case "book": {
                // раскрытая книга
                q.moveTo(cx, cy - r * 0.55f); q.quadTo(cx - r * 0.5f, cy - r * 0.85f, cx - r * 1.0f, cy - r * 0.6f);
                q.lineTo(cx - r * 1.0f, cy + r * 0.6f); q.quadTo(cx - r * 0.5f, cy + r * 0.35f, cx, cy + r * 0.7f); q.close();
                c.drawPath(q, p);
                Path q2 = new Path();
                q2.moveTo(cx, cy - r * 0.55f); q2.quadTo(cx + r * 0.5f, cy - r * 0.85f, cx + r * 1.0f, cy - r * 0.6f);
                q2.lineTo(cx + r * 1.0f, cy + r * 0.6f); q2.quadTo(cx + r * 0.5f, cy + r * 0.35f, cx, cy + r * 0.7f); q2.close();
                c.drawPath(q2, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0x990E1311); d.setStrokeWidth(Math.max(1f, r * 0.06f));
                for (int i = 0; i < 3; i++) {
                    float y = cy - r * 0.25f + i * r * 0.25f;
                    c.drawLine(cx - r * 0.8f, y, cx - r * 0.2f, y + r * 0.05f, d);
                    c.drawLine(cx + r * 0.2f, y + r * 0.05f, cx + r * 0.8f, y, d);
                }
                break;
            }
            case "star": {
                for (int i = 0; i < 10; i++) {
                    double ang = Math.PI / 5 * i - Math.PI / 2;
                    float rr = i % 2 == 0 ? r : r * 0.42f;
                    float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
                    if (i == 0) q.moveTo(x, y); else q.lineTo(x, y);
                }
                q.close();
                c.drawPath(q, p);
                break;
            }
            case "scroll": {
                c.drawRoundRect(new RectF(cx - r * 0.7f, cy - r * 0.8f, cx + r * 0.7f, cy + r * 0.8f), r * 0.15f, r * 0.15f, p);
                c.drawCircle(cx - r * 0.7f, cy - r * 0.8f, r * 0.18f, p);
                c.drawCircle(cx + r * 0.7f, cy + r * 0.8f, r * 0.18f, p);
                break;
            }
            case "sword": {
                q.moveTo(cx, cy - r); q.lineTo(cx + r * 0.12f, cy - r * 0.8f); q.lineTo(cx + r * 0.12f, cy + r * 0.35f); q.lineTo(cx - r * 0.12f, cy + r * 0.35f); q.lineTo(cx - r * 0.12f, cy - r * 0.8f); q.close();
                c.drawPath(q, p);
                c.drawRect(cx - r * 0.5f, cy + r * 0.35f, cx + r * 0.5f, cy + r * 0.48f, p);
                c.drawRect(cx - r * 0.08f, cy + r * 0.48f, cx + r * 0.08f, cy + r * 0.85f, p);
                c.drawCircle(cx, cy + r * 0.92f, r * 0.12f, p);
                break;
            }
            case "fort": {
                c.drawRect(cx - r * 0.85f, cy - r * 0.2f, cx + r * 0.85f, cy + r * 0.8f, p);
                for (int i = 0; i < 4; i++) c.drawRect(cx - r * 0.85f + i * r * 0.5f, cy - r * 0.5f, cx - r * 0.6f + i * r * 0.5f, cy - r * 0.2f, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xCC0E1311);
                c.drawRect(cx - r * 0.2f, cy + r * 0.3f, cx + r * 0.2f, cy + r * 0.8f, d);
                break;
            }
            case "dice": {
                c.drawRoundRect(new RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f), r * 0.2f, r * 0.2f, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xEE0E1311);
                float o = r * 0.42f;
                c.drawCircle(cx - o, cy - o, r * 0.13f, d); c.drawCircle(cx + o, cy - o, r * 0.13f, d);
                c.drawCircle(cx, cy, r * 0.13f, d);
                c.drawCircle(cx - o, cy + o, r * 0.13f, d); c.drawCircle(cx + o, cy + o, r * 0.13f, d);
                break;
            }
            case "camera": {
                c.drawRoundRect(new RectF(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.75f), r * 0.2f, r * 0.2f, p);
                q.moveTo(cx - r * 0.42f, cy - r * 0.5f); q.lineTo(cx - r * 0.28f, cy - r * 0.8f); q.lineTo(cx + r * 0.28f, cy - r * 0.8f); q.lineTo(cx + r * 0.42f, cy - r * 0.5f); q.close();
                c.drawPath(q, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xEE0E1311);
                c.drawCircle(cx, cy + r * 0.12f, r * 0.42f, d);
                d.setColor(color);
                c.drawCircle(cx, cy + r * 0.12f, r * 0.24f, d);
                break;
            }
            case "plus": {
                float w = r * 0.26f;
                c.drawRoundRect(new RectF(cx - w, cy - r * 0.9f, cx + w, cy + r * 0.9f), w * 0.5f, w * 0.5f, p);
                c.drawRoundRect(new RectF(cx - r * 0.9f, cy - w, cx + r * 0.9f, cy + w), w * 0.5f, w * 0.5f, p);
                break;
            }
            case "trash": {
                // крышка с ручкой
                c.drawRoundRect(new RectF(cx - r * 0.85f, cy - r * 0.72f, cx + r * 0.85f, cy - r * 0.52f), r * 0.08f, r * 0.08f, p);
                c.drawRoundRect(new RectF(cx - r * 0.28f, cy - r * 0.92f, cx + r * 0.28f, cy - r * 0.68f), r * 0.08f, r * 0.08f, s);
                // корпус-трапеция
                q.moveTo(cx - r * 0.68f, cy - r * 0.42f); q.lineTo(cx + r * 0.68f, cy - r * 0.42f);
                q.lineTo(cx + r * 0.52f, cy + r * 0.92f); q.lineTo(cx - r * 0.52f, cy + r * 0.92f); q.close();
                c.drawPath(q, p);
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xCC0E1311); d.setStrokeWidth(Math.max(1.2f, r * 0.11f)); d.setStrokeCap(Paint.Cap.ROUND);
                for (int i = -1; i <= 1; i++) c.drawLine(cx + i * r * 0.27f, cy - r * 0.2f, cx + i * r * 0.23f, cy + r * 0.7f, d);
                break;
            }
            case "brain": {
                // левая половина — живой мозг (доли и извилины)
                c.save();
                c.clipRect(cx - r * 1.1f, cy - r * 1.1f, cx - r * 0.04f, cy + r * 1.1f);
                c.drawCircle(cx - r * 0.42f, cy - r * 0.42f, r * 0.5f, p);
                c.drawCircle(cx - r * 0.62f, cy + r * 0.12f, r * 0.42f, p);
                c.drawCircle(cx - r * 0.3f, cy + r * 0.45f, r * 0.42f, p);
                c.drawCircle(cx - r * 0.1f, cy - r * 0.1f, r * 0.55f, p);
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG); g.setStyle(Paint.Style.STROKE); g.setStrokeCap(Paint.Cap.ROUND);
                g.setColor(0xAA0E1311); g.setStrokeWidth(Math.max(1.2f, r * 0.08f));
                Path w = new Path();
                w.moveTo(cx - r * 0.75f, cy - r * 0.2f); w.quadTo(cx - r * 0.5f, cy - r * 0.05f, cx - r * 0.3f, cy - r * 0.35f);
                w.moveTo(cx - r * 0.55f, cy - r * 0.62f); w.quadTo(cx - r * 0.35f, cy - r * 0.45f, cx - r * 0.12f, cy - r * 0.62f);
                w.moveTo(cx - r * 0.7f, cy + r * 0.3f); w.quadTo(cx - r * 0.45f, cy + r * 0.15f, cx - r * 0.2f, cy + r * 0.32f);
                w.moveTo(cx - r * 0.45f, cy + r * 0.66f); w.quadTo(cx - r * 0.3f, cy + r * 0.5f, cx - r * 0.12f, cy + r * 0.62f);
                c.drawPath(w, g);
                c.restore();
                // правая половина — микросхема: контур мозга из дорожек и контактов
                Paint t = new Paint(s); t.setStrokeWidth(Math.max(1.4f, r * 0.1f));
                Path k = new Path();
                k.moveTo(cx + r * 0.04f, cy - r * 0.9f); k.lineTo(cx + r * 0.45f, cy - r * 0.9f); k.lineTo(cx + r * 0.85f, cy - r * 0.5f);
                k.lineTo(cx + r * 0.85f, cy + r * 0.45f); k.lineTo(cx + r * 0.45f, cy + r * 0.88f); k.lineTo(cx + r * 0.04f, cy + r * 0.88f);
                c.drawPath(k, t);
                Path tr = new Path();
                tr.moveTo(cx + r * 0.04f, cy - r * 0.5f); tr.lineTo(cx + r * 0.35f, cy - r * 0.5f); tr.lineTo(cx + r * 0.5f, cy - r * 0.35f);
                tr.moveTo(cx + r * 0.04f, cy); tr.lineTo(cx + r * 0.6f, cy);
                tr.moveTo(cx + r * 0.04f, cy + r * 0.48f); tr.lineTo(cx + r * 0.3f, cy + r * 0.48f); tr.lineTo(cx + r * 0.45f, cy + r * 0.3f);
                c.drawPath(tr, t);
                Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG); dot.setColor(color);
                float nr = r * 0.13f;
                c.drawCircle(cx + r * 0.5f, cy - r * 0.35f, nr, dot);
                c.drawCircle(cx + r * 0.6f, cy, nr, dot);
                c.drawCircle(cx + r * 0.45f, cy + r * 0.3f, nr, dot);
                c.drawCircle(cx + r * 0.85f, cy - r * 0.5f, nr * 0.8f, dot);
                c.drawCircle(cx + r * 0.85f, cy + r * 0.45f, nr * 0.8f, dot);
                // шов посередине
                c.drawLine(cx, cy - r * 0.95f, cx, cy + r * 0.95f, t);
                break;
            }
            default: // ромб
                q.moveTo(cx, cy - r); q.lineTo(cx + r * 0.7f, cy); q.lineTo(cx, cy + r); q.lineTo(cx - r * 0.7f, cy); q.close();
                c.drawPath(q, p);
        }
    }

    /** Символ по имени категории/слота. */
    static String forCategory(String name) {
        if (name == null) return "diamond";
        String n = name.toLowerCase();
        if (n.contains("high command") || n.contains("warlord") || n.contains("lord of war")) return "crown";
        if (n.contains("command") || n.contains("officer")) return "command";
        if (n.contains("retinue") || n.contains("elite")) return "shield";
        if (n.contains("troops") || n.contains("line")) return "infantry";
        if (n.contains("heavy transport") || n.contains("transport")) return "transport";
        if (n.contains("heavy assault")) return "heavy";
        if (n.contains("armour") || n.contains("armor") || n.contains("tank")) return "tank";
        if (n.contains("war-engine") || n.contains("dreadnought")) return "walker";
        if (n.contains("support") || n.contains("artillery")) return "cannon";
        if (n.contains("fast attack") || n.contains("flyer") || n.contains("air")) return "wing";
        if (n.contains("recon") || n.contains("scout")) return "eye";
        if (n.contains("knight")) return "knight";
        if (n.contains("titan")) return "titan";
        if (n.contains("fort")) return "fort";
        if (n.contains("psyk") || n.contains("psych")) return "psyker";
        if (n.contains("assassin") || n.contains("operative")) return "skull";
        return "diamond";
    }

    /** Силуэт по строке типа модели (из профиля). */
    static String forUnitType(String type, String name) {
        String t = ((type == null ? "" : type) + " " + (name == null ? "" : name)).toLowerCase();
        if (t.contains("titan") || t.contains("warlord") || t.contains("reaver") || t.contains("warhound")) return "titan";
        if (t.contains("knight")) return "knight";
        if (t.contains("dreadnought") || t.contains("walker")) return "walker";
        if (t.contains("flyer") || t.contains("aircraft") || t.contains("thunderhawk") || t.contains("storm eagle") || t.contains("xiphon") || t.contains("fire raptor") || t.contains("lighter")) return "flyer";
        if (t.contains("cavalry") || t.contains("bike") || t.contains("jetbike") || t.contains("outrider") || t.contains("speeder")) return "bike";
        if (t.contains("vehicle") || t.contains("tank") || t.contains("rhino") || t.contains("predator") || t.contains("land raider")) return "tank";
        if (t.contains("terminator") || t.contains("automata") || t.contains("paragon") || t.contains("castellax") || t.contains("custodian")) return "heavy";
        if (t.contains("command") || t.contains("unique") || t.contains("praetor") || t.contains("centurion") || t.contains("primarch")) return "command";
        if (t.contains("psyker") || t.contains("librarian")) return "psyker";
        if (t.contains("fortification")) return "fort";
        return "infantry";
    }
}
