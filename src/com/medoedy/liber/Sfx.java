package com.medoedy.liber;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Готические интерфейсные звуки, синтезированные при первом запуске (без чужих сэмплов). */
final class Sfx {
    static final int SR = 22050;
    private static final double TAU = Math.PI * 2;
    private static final Random R = new Random(31);
    static final String[] ALL = {"click", "clunk", "page", "bell", "gong", "seal", "organ", "choir", "burn", "error", "servo", "tick", "dice", "fanfare",
            "w_bolt", "w_las", "w_plasma", "w_melta", "w_flame", "w_chain", "w_power", "w_hammer", "w_auto", "w_missile", "w_volkite", "w_grenade", "w_blade",
            "gate_close", "gate_open", "hum", "warp_drone", "warp_whisper", "warp_glitch"};

    private static SoundPool pool;
    private static final Map<String, Integer> ids = new HashMap<String, Integer>();
    private static final Set<Integer> loaded = Collections.synchronizedSet(new HashSet<Integer>());
    static boolean on = true;

    static void init(final Context ctx) {
        if (pool != null) return;
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(aa).build();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool sp, int id, int status) { if (status == 0) loaded.add(id); }
        });
        final File dir = new File(ctx.getCacheDir(), "sfx_v2");
        new Thread(new Runnable() {
            public void run() {
                dir.mkdirs();
                for (String n : ALL) {
                    try {
                        File f = new File(dir, n + ".wav");
                        if (!f.exists() || f.length() < 100) {
                            File tmp = new File(dir, n + ".tmp");
                            writeWav(tmp, normalize(make(n), n.equals("click") || n.equals("tick") ? 0.6f : 0.85f));
                            tmp.renameTo(f);
                        }
                        int id = pool.load(f.getPath(), 1);
                        synchronized (ids) { ids.put(n, id); }
                    } catch (Exception ignored) { }
                }
            }
        }, "sfx").start();
    }

    static void play(String n) { play(n, 1f, 1f); }

    static void play(String n, float vol, float rate) {
        if (!on || pool == null) return;
        Integer id;
        synchronized (ids) { id = ids.get(n); }
        if (id != null && loaded.contains(id)) pool.play(id, vol, vol, 1, 0, rate);
    }

    private static int loopId;

    /** Зациклить звук (например, варп-гул). Повторный вызов не дублирует петлю. */
    static void loop(String n, float vol) {
        if (!on || pool == null) return;
        if (loopId != 0) return;
        Integer id;
        synchronized (ids) { id = ids.get(n); }
        if (id != null && loaded.contains(id)) loopId = pool.play(id, vol, vol, 1, -1, 1f);
    }

    static void loopStop() {
        if (pool != null && loopId != 0) { try { pool.stop(loopId); } catch (Throwable ignored) { } loopId = 0; }
    }

    /** Звук оружия по названию (или null, если это не оружие). */
    static String weaponSound(String name) {
        if (name == null) return null;
        String n = name.toLowerCase();
        if (n.contains("volkite")) return "w_volkite";
        if (n.contains("plasma")) return "w_plasma";
        if (n.contains("melta")) return "w_melta";
        if (n.contains("flame") || n.contains("inferno") || n.contains("burner") || n.contains("incinerat")) return "w_flame";
        if (n.contains("chain") || n.contains("eviscerator")) return "w_chain";
        if (n.contains("thunder hammer") || n.contains("hammer") || n.contains("maul") || n.contains("mace")) return "w_hammer";
        if (n.contains("power") || n.contains("lightning claw") || n.contains("claw") || n.contains("paragon blade") || n.contains("force ")) return "w_power";
        if (n.contains("missile") || n.contains("rocket") || n.contains("hunter-killer") || n.contains("launcher")) return "w_missile";
        if (n.contains("grenade") || n.contains("melta bomb") || n.contains("charge")) return "w_grenade";
        if (n.contains("las")) return "w_las";
        if (n.contains("bolt") || n.contains("combi") || n.contains("storm bolter")) return "w_bolt";
        if (n.contains("autocannon") || n.contains("cannon") || n.contains("stubber") || n.contains("autorifle") || n.contains("mortar") || n.contains("battle cannon") || n.contains("demolisher")) return "w_auto";
        if (n.contains("sword") || n.contains("blade") || n.contains("axe") || n.contains("spear") || n.contains("glaive") || n.contains("halberd") || n.contains("knife") || n.contains("gladius")) return "w_blade";
        return null;
    }

    static float weaponRate(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("heavy") || n.contains("lascannon") || n.contains("multi") || n.contains("cannon")) return 0.8f;
        if (n.contains("pistol")) return 1.18f;
        return 1f;
    }

    /** Сыграть звук оружия, если название похоже на оружие. true — сыграли. */
    static boolean weapon(String name) {
        String w = weaponSound(name);
        if (w == null) return false;
        play(w, 0.9f, weaponRate(name));
        return true;
    }

    // ================================================================ синтез
    private static float[] buf(double s) { return new float[(int) (s * SR)]; }
    private static double saw(double p) { p -= Math.floor(p); return 2 * p - 1; }
    private static double noise() { return R.nextDouble() * 2 - 1; }
    private static double c01(double x) { return x < 0 ? 0 : x > 1 ? 1 : x; }

    private static final class BP {
        final double b0, b2, a1, a2; double x1, x2, y1, y2;
        BP(double f, double q) { double w = TAU * f / SR, al = Math.sin(w) / (2 * q), a0 = 1 + al; b0 = al / a0; b2 = -al / a0; a1 = -2 * Math.cos(w) / a0; a2 = (1 - al) / a0; }
        double run(double x) { double y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2; x2 = x1; x1 = x; y2 = y1; y1 = y; return y; }
    }

    /** Простая «соборная» реверберация из нескольких затухающих повторов. */
    private static float[] hall(float[] b, double tail, double mix) {
        float[] out = new float[b.length + (int) (tail * SR)];
        System.arraycopy(b, 0, out, 0, b.length);
        double[] d = {0.031, 0.047, 0.071, 0.113, 0.157};
        for (double di : d) {
            int n = (int) (di * SR);
            double g = mix;
            for (int k = 1; k < 8; k++) {
                int off = n * k;
                g *= 0.62;
                for (int i = 0; i < b.length && i + off < out.length; i++) out[i + off] += (float) (b[i] * g);
            }
        }
        return out;
    }

    static float[] make(String n) {
        switch (n) {
            case "click": { // металлический щелчок с низким «тук»
                float[] b = buf(0.09);
                BP f = new BP(2600, 6);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double thud = Math.sin(TAU * 110 * t) * Math.exp(-t * 60);
                    double ring = f.run(noise()) * Math.exp(-t * 90) * 3;
                    b[i] = (float) (thud * 0.7 + ring);
                }
                return b;
            }
            case "tick": {
                float[] b = buf(0.05);
                for (int i = 0; i < b.length; i++) { double t = i / (double) SR; b[i] = (float) (Math.sin(TAU * 1800 * t) * Math.exp(-t * 140)); }
                return b;
            }
            case "clunk": { // тяжёлый засов
                float[] b = buf(0.4);
                BP f1 = new BP(620, 8), f2 = new BP(1430, 10);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double n0 = noise() * Math.exp(-t * 40);
                    double v = Math.sin(TAU * (70 + 40 * Math.exp(-t * 30)) * t) * Math.exp(-t * 12) + f1.run(n0) * 2.5 + f2.run(n0) * 1.5;
                    b[i] = (float) Math.tanh(v * 1.5);
                }
                return hall(b, 0.4, 0.25);
            }
            case "page": { // шорох пергамента
                float[] b = buf(0.45);
                BP f = new BP(3200, 0.8);
                double env = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double target = 0.3 + 0.7 * Math.abs(Math.sin(t * 38 + Math.sin(t * 13) * 2)) * Math.sin(Math.PI * t / 0.45);
                    env += (target - env) * 0.004;
                    double cr = R.nextDouble() < 0.004 ? noise() * 3 : 0;
                    b[i] = (float) ((f.run(noise()) * env + cr * 0.3) * Math.sin(Math.PI * t / 0.45));
                }
                return b;
            }
            case "bell": return bell(880, 1.4, 0.5);
            case "gong": return hall(bell(130, 3.0, 1.0), 1.2, 0.35);
            case "seal": { // удар печати: глухой «бум» + вязкий шлепок воска
                float[] b = buf(0.5);
                BP f = new BP(400, 1.5);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double boom = Math.sin(TAU * (55 + 60 * Math.exp(-t * 25)) * t) * Math.exp(-t * 9);
                    double squish = f.run(noise()) * Math.exp(-t * 18) * 1.5;
                    b[i] = (float) Math.tanh((boom + squish) * 1.8);
                }
                return hall(b, 0.5, 0.2);
            }
            case "organ": return hall(organ(new double[]{110, 164.8, 220, 261.6, 329.6}, 1.6), 1.4, 0.4);
            case "choir": return hall(choir(new double[]{220, 261.6, 329.6}, 2.0), 1.4, 0.45);
            case "fanfare": return hall(organ(new double[]{130.8, 196, 261.6, 329.6, 392}, 2.4), 1.6, 0.45);
            case "burn": { // сгорание / удаление
                float[] b = buf(0.6);
                double y = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double a = 0.02 + 0.3 * c01(t / 0.25) * Math.exp(-(t - 0.25) * 4 * (t > 0.25 ? 1 : 0));
                    y += a * (noise() - y);
                    double cr = R.nextDouble() < 0.01 ? noise() * 2 : 0;
                    b[i] = (float) ((y * 1.5 + cr * 0.4) * Math.sin(Math.PI * t / 0.6));
                }
                return b;
            }
            case "error": {
                float[] b = buf(0.5);
                double p1 = 0, p2 = 0, y = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    p1 += 73 / (double) SR; p2 += 77.5 / SR;
                    double s = saw(p1) + saw(p2);
                    y += 0.08 * (s - y);
                    b[i] = (float) (y * c01(t / 0.02) * c01((0.5 - t) / 0.15));
                }
                return b;
            }
            case "servo": { // сервочереп: щебет с модуляцией
                float[] b = buf(0.22);
                double ph = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double f = 900 + 1400 * (t / 0.22) + 200 * Math.sin(TAU * 40 * t);
                    ph += f / SR;
                    b[i] = (float) (Math.sin(TAU * ph) * Math.sin(Math.PI * t / 0.22) * 0.6 + (Math.sin(TAU * ph * 2.01) > 0.6 ? 0.15 : 0));
                }
                return b;
            }
            case "dice": { // стук костей
                float[] b = buf(0.5);
                BP f = new BP(2200, 4);
                double[] hits = {0, 0.08, 0.15, 0.21, 0.3, 0.36};
                for (double h : hits) {
                    int st = (int) (h * SR);
                    double amp = 1 - h * 1.5;
                    for (int i = st; i < b.length && i < st + SR / 20; i++) {
                        double t = (i - st) / (double) SR;
                        b[i] += (float) (f.run(noise()) * Math.exp(-t * 120) * amp * 3);
                    }
                }
                return b;
            }
            case "w_bolt": { // болтер: очередь масс-реактивных зарядов — выстрел + детонация
                float[] b = buf(0.75);
                double[] shots = {0, 0.12, 0.23};
                for (double sh : shots) {
                    BP f = new BP(900, 0.8), g = new BP(2600, 1.2);
                    int st = (int) (sh * SR);
                    double ph = 0;
                    for (int i = st; i < b.length && i < st + SR / 4; i++) {
                        double t = (i - st) / (double) SR;
                        ph += (75 * Math.exp(-t * 18) + 38) / SR;
                        double v = f.run(noise()) * Math.exp(-t * 55) * 2.2 + Math.sin(TAU * ph) * Math.exp(-t * 22) * 0.9;
                        double t2 = t - 0.035;
                        if (t2 > 0) v += g.run(noise()) * Math.exp(-t2 * 70) * 1.3;
                        b[i] += (float) Math.tanh(v * 1.6);
                    }
                }
                return hall(b, 0.4, 0.22);
            }
            case "w_las": { // лазер: «пиу» с жужжанием конденсатора
                float[] b = buf(0.5);
                double[] shots = {0, 0.2};
                for (double sh : shots) {
                    int st = (int) (sh * SR);
                    double ph = 0;
                    for (int i = st; i < b.length && i < st + (int) (0.2 * SR); i++) {
                        double t = (i - st) / (double) SR;
                        double f = 300 + 2600 * Math.exp(-t * 22);
                        ph += f / SR;
                        double v = Math.sin(TAU * ph) * 0.7 + Math.sin(TAU * ph * 1.5) * 0.25 + noise() * 0.08;
                        b[i] += (float) (v * Math.exp(-t * 14) * (t < 0.004 ? t / 0.004 : 1));
                    }
                }
                return hall(b, 0.3, 0.2);
            }
            case "w_plasma": { // плазма: нарастающий визг зарядки и удар
                float[] b = buf(1.1);
                double ph = 0;
                BP f = new BP(200, 0.7);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    if (t < 0.38) {
                        double fr = 250 + 1500 * (t / 0.38) * (t / 0.38);
                        ph += fr / SR;
                        b[i] += (float) (saw(ph) * 0.35 * (t / 0.38) + Math.sin(TAU * ph * 2) * 0.15 * (t / 0.38));
                    } else {
                        double u = t - 0.38;
                        b[i] += (float) Math.tanh((f.run(noise()) * 3 + Math.sin(TAU * (45 + 40 * Math.exp(-u * 8)) * u) * 1.2) * Math.exp(-u * 6) * 1.5);
                    }
                }
                return hall(b, 0.6, 0.28);
            }
            case "w_melta": { // мелта: рёв перегретого воздуха
                float[] b = buf(0.8);
                BP f = new BP(500, 0.9), g = new BP(3200, 2);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double env = Math.min(1, t / 0.05) * Math.exp(-Math.max(0, t - 0.35) * 7);
                    double trem = 0.7 + 0.3 * Math.sin(TAU * 28 * t);
                    b[i] = (float) Math.tanh((f.run(noise()) * 2.5 * trem + g.run(noise()) * 0.6) * env * 1.4);
                }
                return hall(b, 0.3, 0.15);
            }
            case "w_flame": { // огнемёт: «вшух» и треск
                float[] b = buf(0.95);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double fc = 350 + 900 * Math.sin(Math.PI * Math.min(1, t / 0.7));
                    BP f = new BP(fc, 0.6);
                    double env = Math.min(1, t / 0.08) * Math.exp(-Math.max(0, t - 0.5) * 6);
                    b[i] = (float) (noise() * 0.5 * env);
                }
                BP f = new BP(700, 0.7);
                for (int i = 0; i < b.length; i++) b[i] = (float) (f.run(b[i]) * 3);
                for (int k = 0; k < 40; k++) {
                    int st = (int) (R.nextDouble() * 0.8 * SR);
                    for (int i = st; i < b.length && i < st + 90; i++) b[i] += (float) (noise() * 0.6 * Math.exp(-(i - st) / 18.0));
                }
                return b;
            }
            case "w_chain": { // цепной меч: мотор раскручивается, зубья рвут
                float[] b = buf(1.0);
                double ph = 0, ph2 = 0;
                BP grit = new BP(1800, 1.5);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double rev = t < 0.18 ? 70 + 80 * (t / 0.18) : 150 + 6 * Math.sin(TAU * 7 * t);
                    ph += rev / SR;
                    ph2 += rev * 0.5 / SR;
                    double teeth = 0.6 + 0.4 * Math.abs(Math.sin(TAU * ph * 0.33));
                    double v = saw(ph) * 0.55 + saw(ph2) * 0.35 + grit.run(noise()) * 1.4 * teeth;
                    double env = Math.min(1, t / 0.03) * (t > 0.85 ? (1 - t) / 0.15 : 1);
                    b[i] = (float) Math.tanh(v * 2.2 * env) * 0.8f;
                }
                return b;
            }
            case "w_power": { // силовое поле: гудение и разряды
                float[] b = buf(0.8);
                double ph = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double f = 110 + 60 * Math.min(1, t / 0.25);
                    ph += f / SR;
                    double env = Math.min(1, t / 0.12) * (t > 0.6 ? (0.8 - t) / 0.2 : 1);
                    b[i] = (float) ((Math.tanh(Math.sin(TAU * ph) * 3) * 0.35 + Math.sin(TAU * ph * 4.02) * 0.08) * env);
                }
                for (int k = 0; k < 18; k++) {
                    int st = (int) (R.nextDouble() * 0.7 * SR);
                    BP f = new BP(3000 + R.nextDouble() * 3000, 3);
                    for (int i = st; i < b.length && i < st + 260; i++) b[i] += (float) (f.run(noise()) * 2.5 * Math.exp(-(i - st) / 60.0));
                }
                return hall(b, 0.25, 0.15);
            }
            case "w_hammer": { // громовой молот: разряд + тяжёлый удар
                float[] b = buf(1.0);
                for (int k = 0; k < 10; k++) {
                    int st = (int) (R.nextDouble() * 0.25 * SR);
                    BP f = new BP(2500 + R.nextDouble() * 3000, 3);
                    for (int i = st; i < b.length && i < st + 300; i++) b[i] += (float) (f.run(noise()) * 2 * Math.exp(-(i - st) / 70.0));
                }
                BP lo = new BP(120, 0.8);
                int st = (int) (0.3 * SR);
                for (int i = st; i < b.length; i++) {
                    double t = (i - st) / (double) SR;
                    b[i] += (float) Math.tanh((Math.sin(TAU * (40 + 60 * Math.exp(-t * 20)) * t) * 1.5 + lo.run(noise()) * 4) * Math.exp(-t * 7));
                }
                return hall(b, 0.6, 0.3);
            }
            case "w_auto": { // автопушка/тяжёлое орудие: тяжёлые раскатистые выстрелы
                float[] b = buf(1.0);
                double[] shots = {0, 0.28};
                for (double sh : shots) {
                    BP f = new BP(400, 0.7);
                    int st = (int) (sh * SR);
                    for (int i = st; i < b.length && i < st + SR / 2; i++) {
                        double t = (i - st) / (double) SR;
                        double v = f.run(noise()) * Math.exp(-t * 25) * 3 + Math.sin(TAU * (35 + 50 * Math.exp(-t * 15)) * t) * Math.exp(-t * 9) * 1.4;
                        b[i] += (float) Math.tanh(v * 1.4);
                    }
                }
                return hall(b, 0.6, 0.3);
            }
            case "w_missile": { // ракета: шипящий старт и далёкий разрыв
                float[] b = buf(1.3);
                for (int i = 0; i < (int) (0.6 * SR); i++) {
                    double t = i / (double) SR;
                    BP f = new BP(500 + 2500 * t, 1);
                    b[i] = (float) (noise() * Math.min(1, t / 0.03) * Math.exp(-t * 3) * 0.6);
                }
                BP hp = new BP(1500, 0.8);
                for (int i = 0; i < (int) (0.6 * SR); i++) b[i] = (float) (hp.run(b[i]) * 2.5);
                BP lo = new BP(90, 0.8);
                int st = (int) (0.7 * SR);
                for (int i = st; i < b.length; i++) {
                    double t = (i - st) / (double) SR;
                    b[i] += (float) Math.tanh(lo.run(noise()) * 6 * Math.exp(-t * 6));
                }
                return hall(b, 0.5, 0.25);
            }
            case "w_volkite": { // волкит: дефлаграционный трескучий луч
                float[] b = buf(0.6);
                double ph = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    ph += (600 + 300 * Math.sin(TAU * 35 * t)) / SR;
                    double env = Math.min(1, t / 0.02) * Math.exp(-t * 5);
                    b[i] = (float) ((saw(ph) * 0.3 + noise() * 0.25 * (Math.sin(TAU * 60 * t) > 0 ? 1 : 0.2)) * env);
                }
                return hall(b, 0.25, 0.2);
            }
            case "w_grenade": { // чеку долой — взрыв
                float[] b = buf(1.1);
                for (int i = 0; i < SR / 20; i++) { double t = i / (double) SR; b[i] = (float) (Math.sin(TAU * 3200 * t) * Math.exp(-t * 90) * 0.5); }
                BP lo = new BP(110, 0.7);
                int st = (int) (0.4 * SR);
                for (int i = st; i < b.length; i++) {
                    double t = (i - st) / (double) SR;
                    b[i] += (float) Math.tanh((lo.run(noise()) * 6 + noise() * 0.5 * Math.exp(-t * 30)) * Math.exp(-t * 6));
                }
                return hall(b, 0.5, 0.3);
            }
            case "w_blade": { // клинок: свист и звон стали
                float[] b = buf(0.6);
                for (int i = 0; i < (int) (0.25 * SR); i++) {
                    double t = i / (double) SR;
                    BP f = new BP(800 + 4000 * t, 2);
                    b[i] = (float) (noise() * Math.sin(Math.PI * t / 0.25) * 0.5);
                }
                BP f = new BP(2200, 1.5);
                for (int i = 0; i < (int) (0.25 * SR); i++) b[i] = (float) (f.run(b[i]) * 3);
                float[] ring = bell(1750, 0.4, 0.5);
                int st = (int) (0.2 * SR);
                for (int i = 0; i < ring.length && st + i < b.length; i++) b[st + i] += ring[i] * 0.6f;
                return b;
            }
            case "gate_close": { // гидравлика: шипение, храповик, удар створок
                float[] b = buf(0.6);
                BP hs = new BP(4000, 0.8);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    b[i] = (float) (hs.run(noise()) * 0.35 * Math.min(1, t / 0.05) * Math.exp(-t * 4));
                }
                for (int k = 0; k < 7; k++) {
                    int st = (int) ((0.03 + k * 0.035) * SR);
                    BP f = new BP(1600, 5);
                    for (int i = st; i < b.length && i < st + 300; i++) b[i] += (float) (f.run(noise()) * 1.5 * Math.exp(-(i - st) / 50.0));
                }
                BP lo = new BP(90, 0.9);
                int st = (int) (0.27 * SR);
                for (int i = st; i < b.length; i++) {
                    double t = (i - st) / (double) SR;
                    b[i] += (float) Math.tanh((lo.run(noise()) * 5 + Math.sin(TAU * 55 * t) * 0.8) * Math.exp(-t * 14));
                }
                return b;
            }
            case "gate_open": {
                float[] b = buf(0.55);
                BP hs = new BP(3000, 0.8);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    b[i] = (float) (hs.run(noise()) * 0.45 * Math.exp(-t * 6));
                }
                for (int k = 0; k < 6; k++) {
                    int st = (int) ((0.05 + k * 0.05) * SR);
                    BP f = new BP(1200, 5);
                    for (int i = st; i < b.length && i < st + 300; i++) b[i] += (float) (f.run(noise()) * 1.2 * Math.exp(-(i - st) / 50.0));
                }
                return b;
            }
            case "hum": { // гул когитатора
                float[] b = buf(0.6);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    b[i] = (float) ((Math.sin(TAU * 60 * t) * 0.5 + Math.sin(TAU * 120 * t) * 0.3 + Math.sin(TAU * 180 * t) * 0.1) * Math.sin(Math.PI * t / 0.6));
                }
                return b;
            }
            case "warp_drone": { // варп-гул: расстроенные низы, медленное дыхание, безличный хор
                float[] b = buf(7.0);
                double[] f = {55, 55.8, 82.4, 110.5, 164.9};
                double[] a = {0.5, 0.42, 0.3, 0.18, 0.08};
                double y = 0;
                BP air = new BP(900, 0.6);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR, v = 0;
                    for (int k = 0; k < f.length; k++)
                        v += a[k] * Math.sin(TAU * f[k] * t * (1 + 0.004 * Math.sin(TAU * (0.11 + k * 0.037) * t)));
                    y += 0.02 * (noise() * 0.5 - y);
                    double breath = 0.75 + 0.25 * Math.sin(TAU * 0.14 * t);
                    b[i] = (float) Math.tanh((v * breath + air.run(noise()) * 0.04) * 1.4) * 0.7f;
                }
                // стык петли: подмешаем начало в конец, чтобы зацикливалось без щелчка
                int fade = SR / 2;
                for (int i = 0; i < fade; i++) {
                    float k = i / (float) fade;
                    b[b.length - fade + i] = b[b.length - fade + i] * (1 - k * 0.5f) + b[i] * k * 0.5f;
                }
                return b;
            }
            case "warp_whisper": { // шёпот варпа: вдыхи-выдохи отфильтрованного шума, обратные атаки
                float[] b = buf(3.2);
                double y = 0;
                BP f = new BP(1500, 2.2), f2 = new BP(2600, 3.5);
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    y += 0.06 * (noise() - y);
                    double env = Math.max(0, Math.sin(TAU * 0.9 * t + Math.sin(TAU * 0.23 * t) * 2)) * (0.4 + 0.6 * R.nextDouble());
                    env *= c01(t / 0.5) * c01((3.2 - t) / 0.8);
                    double form = f.run(y) * 1.6 + f2.run(y) * 0.8;
                    b[i] = (float) (form * env * 1.8);
                }
                return b;
            }
            case "warp_glitch": { // глитч реальности: дёрганные импульсы и обрывы
                float[] b = buf(0.5);
                double ph = 0;
                for (int i = 0; i < b.length; i++) {
                    double t = i / (double) SR;
                    double gate = (Math.sin(TAU * 37 * t) > -0.2 ? 1 : 0.1) * (Math.sin(TAU * 13 * t + 1) > -0.6 ? 1 : 0.2);
                    ph += (180 + 700 * Math.abs(Math.sin(TAU * 3 * t))) / SR;
                    double v = saw(ph) * 0.35 * gate + noise() * 0.12 * gate;
                    v *= c01(t / 0.01) * c01((0.5 - t) / 0.12);
                    b[i] = (float) (Math.tanh(v * 2.4) * 0.8);
                }
                return b;
            }
        }
        return buf(0.05);
    }

    private static float[] bell(double f0, double dur, double vol) {
        double[] r = {1, 2.0, 2.76, 4.07, 5.4, 6.8};
        double[] a = {1, 0.6, 0.5, 0.25, 0.18, 0.08};
        double[] dc = {1.2, 1.8, 2.6, 3.5, 4.5, 6};
        float[] b = buf(dur);
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) SR, v = 0;
            for (int k = 0; k < r.length; k++) v += a[k] * Math.sin(TAU * f0 * r[k] * t) * Math.exp(-t * dc[k] / dur * 1.5);
            b[i] = (float) (v * vol * c01(t / 0.002));
        }
        return b;
    }

    private static float[] organ(double[] notes, double dur) {
        float[] b = buf(dur);
        double[] harm = {1, 2, 3, 4, 6, 8};
        double[] ha = {1, 0.7, 0.4, 0.35, 0.15, 0.1};
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) SR, v = 0;
            for (int n = 0; n < notes.length; n++)
                for (int h = 0; h < harm.length; h++) v += ha[h] * Math.sin(TAU * notes[n] * harm[h] * t * (1 + 0.0015 * Math.sin(TAU * 5 * t)));
            double env = c01(t / 0.12) * c01((dur - t) / 0.5);
            b[i] = (float) (v * env);
        }
        return b;
    }

    private static float[] choir(double[] notes, double dur) {
        float[] b = buf(dur);
        BP f1 = new BP(700, 5), f2 = new BP(1220, 7), f3 = new BP(2600, 9);
        double[] ph = new double[notes.length * 3];
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) SR, s = 0;
            for (int n = 0; n < notes.length; n++) for (int v = 0; v < 3; v++) {
                int k = n * 3 + v;
                ph[k] += notes[n] * (1 + (v - 1) * 0.006 + 0.004 * Math.sin(TAU * (4.5 + v) * t)) / SR;
                s += saw(ph[k]);
            }
            s += noise() * 0.3;
            double v = f1.run(s) * 1.2 + f2.run(s) * 0.9 + f3.run(s) * 0.4;
            b[i] = (float) (v * c01(t / 0.35) * c01((dur - t) / 0.6));
        }
        return b;
    }

    static float[] normalize(float[] b, float peak) {
        float m = 1e-6f;
        for (float v : b) m = Math.max(m, Math.abs(v));
        float k = peak / m;
        for (int i = 0; i < b.length; i++) b[i] *= k;
        int fade = Math.min(b.length, SR / 100);
        for (int i = 0; i < fade; i++) b[b.length - 1 - i] *= i / (float) fade;
        return b;
    }

    private static void writeWav(File f, float[] s) throws IOException {
        int n = s.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + n).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + n).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1).putInt(SR).putInt(SR * 2).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(n);
        for (float v : s) b.putShort((short) (Math.max(-1f, Math.min(1f, v)) * 32767));
        FileOutputStream out = new FileOutputStream(f);
        try { out.write(b.array()); } finally { out.close(); }
    }
}
