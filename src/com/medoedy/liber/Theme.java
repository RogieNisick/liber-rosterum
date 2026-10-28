package com.medoedy.liber;

import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;

/** Геральдика: цвета под легион/фракцию и шрифты. */
final class Theme {
    final int accent;     // основной цвет брони
    final int trim;       // отделка (металл)
    final int glow;       // цвет подсветки фона
    final String numeral; // номер легиона для водяного знака

    static Typeface display, names, namesBlack, body, bodyBold;

    Theme(int accent, int trim, int glow, String numeral) {
        this.accent = accent;
        this.trim = trim;
        this.glow = glow;
        this.numeral = numeral;
    }

    static final int IRON = 0xFF15130F, IRON_DARK = 0xFF0A0908, PARCH = 0xFFE8DCC0, PARCH_DIM = 0xB3E8DCC0,
            BRASS = 0xFFC9A55A, BRASS_DARK = 0xFF7A5C24, BLOOD = 0xFF9E1B1B, OK = 0xFF7FA15A;

    static final Theme DEFAULT = new Theme(0xFF8E7A55, BRASS, 0xFF8E6A2A, "");

    static void init(AssetManager am) {
        if (display != null) return;
        display = load(am, "fonts/CormorantSC-Bold.ttf", Typeface.SERIF);
        names = load(am, "fonts/Cinzel-700.ttf", Typeface.SERIF);
        namesBlack = load(am, "fonts/Cinzel-900.ttf", Typeface.SERIF);
        body = load(am, "fonts/PT_Sans-Narrow-Web-Regular.ttf", Typeface.SANS_SERIF);
        bodyBold = load(am, "fonts/PT_Sans-Narrow-Web-Bold.ttf", Typeface.DEFAULT_BOLD);
    }

    private static Typeface load(AssetManager am, String path, Typeface def) {
        try { return Typeface.createFromAsset(am, path); } catch (Exception e) { return def; }
    }

    /** Подбор геральдики по имени каталога. */
    static Theme forName(String name) {
        if (name == null) return DEFAULT;
        String n = name.toLowerCase();
        String num = "";
        int dash = name.indexOf(" - ");
        if (dash > 0 && com.medoedy.liber.engine.Data.roman(name.substring(0, dash).trim()) > 0) num = name.substring(0, dash).trim();
        if (n.contains("dark angels")) return new Theme(0xFF1F4A30, 0xFFC9B98F, 0xFF1F6A3A, num);
        if (n.contains("emperor's children") || n.contains("emperor’s children")) return new Theme(0xFF6A2C82, 0xFFD4A73A, 0xFF8A3AA8, num);
        if (n.contains("iron warriors")) return new Theme(0xFF6E7378, 0xFFD1A62A, 0xFF8A8F94, num);
        if (n.contains("white scars")) return new Theme(0xFFB0272A, 0xFFE8E1D3, 0xFFE8E1D3, num);
        if (n.contains("space wolves")) return new Theme(0xFF5E7A8C, 0xFFD8C9A0, 0xFF7FA0B5, num);
        if (n.contains("imperial fists")) return new Theme(0xFFD9A520, 0xFF2A2522, 0xFFE0B030, num);
        if (n.contains("night lords")) return new Theme(0xFF2A3E6E, 0xFFB8C8E8, 0xFF3A5AA8, num);
        if (n.contains("blood angels")) return new Theme(0xFFA51F1F, 0xFFD4AA3A, 0xFFC02828, num);
        if (n.contains("iron hands")) return new Theme(0xFF3A3D40, 0xFFB8C0C6, 0xFF8A9298, num);
        if (n.contains("world eaters")) return new Theme(0xFF2E5A9C, 0xFFE6E3DC, 0xFF3A70C0, num);
        if (n.contains("ultramarines")) return new Theme(0xFF1F4E9C, 0xFFD4AA3A, 0xFF2A62C0, num);
        if (n.contains("death guard")) return new Theme(0xFF8A8462, 0xFF6E7A3A, 0xFF9AA060, num);
        if (n.contains("thousand sons")) return new Theme(0xFF9A1F2C, 0xFFD4AA3A, 0xFFB8283A, num);
        if (n.contains("sons of horus")) return new Theme(0xFF2E6B63, 0xFFC9A55A, 0xFF3A8A80, num);
        if (n.contains("word bearers")) return new Theme(0xFF7E1A22, 0xFFC9A55A, 0xFF9A2A30, num);
        if (n.contains("salamanders")) return new Theme(0xFF1F6B3A, 0xFFE0682D, 0xFF2A8A4A, num);
        if (n.contains("raven guard")) return new Theme(0xFF2A2A2E, 0xFFD8D8D8, 0xFF6A6A78, num);
        if (n.contains("alpha legion")) return new Theme(0xFF2D6E7E, 0xFF9FB7BD, 0xFF3A8A9E, num);
        if (n.contains("blackshields")) return new Theme(0xFF26262A, 0xFFA8ADB2, 0xFF55555E, "");
        if (n.contains("shattered")) return new Theme(0xFF4A4A50, 0xFFB0272A, 0xFF6A6A74, "");
        if (n.contains("solar auxilia")) return new Theme(0xFF4E5A63, 0xFFC9A55A, 0xFF6A7A88, "");
        if (n.contains("custodes")) return new Theme(0xFFC9A227, 0xFF9E1B1B, 0xFFD8B030, "");
        if (n.contains("mechanicum") || n.contains("skitarii")) return new Theme(0xFF8E1B1B, 0xFFB07A3A, 0xFFA82A2A, "");
        if (n.contains("titanicus")) return new Theme(0xFF7A1A1A, 0xFFD4AA3A, 0xFF9A2A2A, "");
        if (n.contains("questoris")) return new Theme(0xFF24467A, 0xFFD9A520, 0xFF3060A0, "");
        if (n.contains("militia")) return new Theme(0xFF5F6B3A, 0xFFB8A878, 0xFF7A8A4A, "");
        if (n.contains("daemon")) return new Theme(0xFF6E1A5A, 0xFF7AB83A, 0xFF8A2A78, "");
        if (n.contains("assassin")) return new Theme(0xFF222225, 0xFF9E1B1B, 0xFF4A4A55, "");
        if (n.contains("psykana")) return new Theme(0xFF8E9298, 0xFF9E1B1B, 0xFFA0A4AA, "");
        if (n.contains("knights-errant")) return new Theme(0xFF55585E, 0xFFC9A55A, 0xFF70747A, "");
        return DEFAULT;
    }

    static int mix(int a, int b, float t) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int alpha(int c, float a) {
        return Color.argb((int) (255 * a), Color.red(c), Color.green(c), Color.blue(c));
    }

    /** Цвет текста, читаемый на фоне c. */
    static int onColor(int c) {
        double l = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255;
        return l > 0.6 ? 0xFF16120C : PARCH;
    }

    /** Подсветка для тёмных цветов брони (чтобы акцент читался на железе). */
    int accentBright() {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[2] = Math.max(hsv[2], 0.62f);
        return Color.HSVToColor(hsv);
    }
}
