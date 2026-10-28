package com.medoedy.liber;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Картинки оформления из assets/art: рамка когитатора, значки, свеча, сервочереп. */
final class Art {
    private Art() { }

    private static Context ctx;
    private static final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private static final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    static void init(Context c) { ctx = c.getApplicationContext(); }

    static Bitmap get(String name) {
        if (ctx == null || name == null) return null;
        Bitmap b = cache.get(name);
        if (b != null || cache.containsKey(name)) return b;
        try {
            String file = name.contains(".") ? name : name + ".png";
            InputStream in = ctx.getAssets().open("art/" + file);
            b = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Exception e) { b = null; }
        cache.put(name, b);
        return b;
    }

    /** Значок из листа Rogue Trader для символа интерфейса (или null — тогда рисуем сами). */
    static String badgeFor(String kind) {
        if (kind == null) return null;
        switch (kind) {
            case "crown": return "b_crown";
            case "command": return "b_officer";
            case "fist": return "b_fist";
            case "infantry": return "b_bolter";
            case "shield": return "b_shield";
            case "eye": return "b_recon";
            case "skull": return "b_assassin";
            case "sword": return "b_swords";
            case "cannon": return "b_gunsword";
            case "heavy": return "b_axe";
            case "banner": return "f_aquila";
            case "mech": return "b_mech";
            case "cog": return "f_cog";
            case "psyker": return "f_k";
            case "heart": return "b_heart";
            default: return null;
        }
    }

    /** Нарисовать значок-маску с подсветкой цветом (тени сохраняются). true — нарисовали. */
    static boolean drawBadge(Canvas c, String kind, float cx, float cy, float r, int color) {
        Bitmap b = get(badgeFor(kind));
        if (b == null) return false;
        float s = r * 1.55f;
        RectF dst = new RectF(cx - s, cy - s, cx + s, cy + s);
        tint.setColorFilter(new LightingColorFilter(color, 0));
        tint.setAlpha(70);
        RectF glow = new RectF(dst);
        glow.inset(-r * 0.12f, -r * 0.12f);
        c.drawBitmap(b, null, glow, tint);
        tint.setAlpha(255);
        c.drawBitmap(b, null, dst, tint);
        return true;
    }
}
