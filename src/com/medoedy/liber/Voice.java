package com.medoedy.liber;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Глашатай: мужской голос синтеза речи, пропущенный через «вокс-кастер» —
 * понижение тона, лёгкий перегруз, соборное эхо и низкий гул органа.
 */
final class Voice {
    private static TextToSpeech tts;
    private static boolean ready, failed;
    private static String pending;
    static boolean on = true;
    /** true — глашатай говорит по-русски (фразы переведены), false — по-английски. */
    static boolean ru = true;
    static String status = "Запуск синтеза речи…";
    static String voiceName = "";
    private static File cacheDir;
    private static final Map<String, short[]> cooked = new HashMap<String, short[]>();
    private static final Map<String, Integer> rates = new HashMap<String, Integer>();
    private static AudioTrack track;
    private static int seq;

    // мужские голоса Google TTS (по опыту), в порядке предпочтения
    private static final String[] MALE = {"en-gb-x-gbd", "en-gb-x-rjs", "en-gb-x-gbb", "en-us-x-iom", "en-us-x-tpd", "en-us-x-iol", "en-au-x-aub", "en-au-x-aud", "en-in-x-ene", "en-gb-x-gbd-local"};

    static void init(Context c) {
        if (tts != null) return;
        cacheDir = new File(c.getCacheDir(), "voice_v2");
        cacheDir.mkdirs();
        tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
            public void onInit(int st) {
                if (st != TextToSpeech.SUCCESS) {
                    failed = true;
                    status = "На телефоне не найден синтез речи. Установите «Google Синтез речи» в Play Маркете.";
                    return;
                }
                setLang();
                try {
                    tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                } catch (Throwable ignored) { }
                tts.setPitch(0.72f);
                tts.setSpeechRate(0.82f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) { }
                    public void onError(String id) { }
                    public void onDone(String id) { rendered(id); }
                });
                ready = true;
                if (pending != null) { String p = pending; pending = null; say(p); }
            }
        });
    }

    /** Выставить язык синтеза по настройке «ru» и подобрать мужской голос. */
    static void setLang() {
        if (tts == null) return;
        int r;
        if (ru) {
            r = tts.setLanguage(new Locale("ru", "RU"));
            status = r < 0 ? "Русский голос не установлен — добавьте русский синтез речи в «Google Синтез речи»." : "Глашатай готов";
        } else {
            r = tts.setLanguage(Locale.UK);
            if (r < 0) r = tts.setLanguage(Locale.US);
            if (r < 0) r = tts.setLanguage(Locale.ENGLISH);
            status = r < 0 ? "Английский голос не установлен — говорит голос по умолчанию." : "Глашатай готов";
        }
        pickMale();
    }

    /** Текст для глашатая: русский или английский в зависимости от настройки. */
    static String line(String russian, String english) { return ru ? russian : english; }

    /** Выбрать мужской голос нужного языка. */
    private static void pickMale() {
        try {
            Set<android.speech.tts.Voice> vs = tts.getVoices();
            if (vs == null) return;
            String lang = ru ? "ru" : "en";
            android.speech.tts.Voice best = null;
            int bestScore = -1;
            for (android.speech.tts.Voice v : vs) {
                String n = v.getName().toLowerCase();
                if (v.getLocale() == null || !lang.equals(v.getLocale().getLanguage())) continue;
                int sc = 0;
                if (ru) sc = 40;
                else for (int i = 0; i < MALE.length; i++) if (n.startsWith(MALE[i])) { sc = 100 - i * 3; break; }
                if (n.contains("male") && !n.contains("female")) sc = Math.max(sc, 90);
                if (n.contains("female")) sc = -1;
                if (sc <= 0) continue;
                if (!v.isNetworkConnectionRequired()) sc += 5;
                if (v.getFeatures() != null && v.getFeatures().contains("notInstalled")) sc -= 50;
                if (sc > bestScore) { bestScore = sc; best = v; }
            }
            if (best != null) { tts.setVoice(best); voiceName = best.getName(); status = "Глашатай готов: " + voiceName; }
            else status = ru ? "Мужской русский голос не найден — в «Google Синтез речи» скачайте русский голос."
                             : "Мужской голос не найден — в «Google Синтез речи» скачайте английский голос (Великобритания).";
        } catch (Throwable ignored) { }
    }

    static void say(String text) {
        if (!on || text == null) return;
        if (!ready) { pending = text; return; }
        stop();
        short[] pcm = cooked.get(text);
        if (pcm != null) { play(pcm, rates.get(text)); return; }
        final String id = "v" + (++seq) + "_" + Integer.toHexString(text.hashCode());
        File f = new File(cacheDir, id + ".wav");
        try {
            Bundle b = new Bundle();
            pendingText.put(id, text);
            int r = tts.synthesizeToFile(text, b, f, id);
            if (r != TextToSpeech.SUCCESS) speakRaw(text);
        } catch (Throwable t) { speakRaw(text); }
    }

    private static final Map<String, String> pendingText = new HashMap<String, String>();

    private static void speakRaw(String text) {
        try {
            Bundle b = new Bundle();
            b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, b, "liber");
        } catch (Exception ignored) { }
    }

    /** Синтез в файл закончен — обработать и проиграть (в фоне). */
    private static void rendered(final String id) {
        final String text;
        synchronized (pendingText) { text = pendingText.remove(id); }
        if (text == null) return;
        final File f = new File(cacheDir, id + ".wav");
        new Thread(new Runnable() {
            public void run() {
                try {
                    int[] srOut = new int[1];
                    short[] raw = readWav(f, srOut);
                    f.delete();
                    if (raw == null || raw.length == 0) { speakRaw(text); return; }
                    short[] pcm = voxCaster(raw, srOut[0]);
                    cooked.put(text, pcm);
                    rates.put(text, srOut[0]);
                    if (id.startsWith("v" + seq + "_")) play(pcm, srOut[0]);
                } catch (Throwable t) { speakRaw(text); }
            }
        }, "vox").start();
    }

    private static synchronized void play(short[] pcm, int sr) {
        try {
            if (track != null) { try { track.stop(); } catch (Throwable ignored) { } track.release(); track = null; }
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(pcm, 0, pcm.length);
            t.play();
            track = t;
        } catch (Throwable ignored) { }
    }

    static void stop() {
        try { if (tts != null) tts.stop(); } catch (Throwable ignored) { }
        synchronized (Voice.class) {
            if (track != null) { try { track.stop(); } catch (Throwable ignored) { } track.release(); track = null; }
        }
    }

    // ================================================================ вокс-кастер
    private static short[] readWav(File f, int[] srOut) throws Exception {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        int off = 0, n;
        while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        in.close();
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (b.length < 44 || b[0] != 'R') return null;
        int pos = 12, sr = 22050, ch = 1, bits = 16;
        int dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, "US-ASCII");
            int len = bb.getInt(pos + 4);
            if (id.equals("fmt ")) { ch = bb.getShort(pos + 10); sr = bb.getInt(pos + 12); bits = bb.getShort(pos + 22); }
            if (id.equals("data")) { dataOff = pos + 8; dataLen = Math.min(len < 0 ? b.length : len, b.length - dataOff); break; }
            pos += 8 + len;
        }
        if (dataOff < 0 || bits != 16) return null;
        int frames = dataLen / 2 / ch;
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) out[i] = bb.getShort(dataOff + i * 2 * ch);
        srOut[0] = sr;
        return out;
    }

    /** Ниже тон, плотнее голос, эхо собора и гул под ним. */
    static short[] voxCaster(short[] in, int sr) {
        // 1) понижение тона ресемплингом (голос глубже и медленнее)
        double k = 0.86;
        int n = (int) (in.length / k);
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            double p = i * k;
            int a = (int) p;
            double fr = p - a;
            float s0 = a < in.length ? in[a] : 0, s1 = a + 1 < in.length ? in[a + 1] : 0;
            x[i] = (float) ((s0 + (s1 - s0) * fr) / 32768.0);
        }
        // 2) тёплый перегруз + подъём низов
        float lp = 0;
        for (int i = 0; i < n; i++) {
            lp += (x[i] - lp) * 0.08f;
            float v = x[i] + lp * 0.9f;
            x[i] = (float) Math.tanh(v * 1.9) * 0.75f;
        }
        // 3) собор: 4 гребенчатых + 2 всепропускающих фильтра (Шрёдер)
        int tail = (int) (sr * 1.6);
        float[] y = new float[n + tail];
        int[] cd = {(int) (sr * 0.0437), (int) (sr * 0.0411), (int) (sr * 0.0371), (int) (sr * 0.0297)};
        float[] cg = {0.80f, 0.79f, 0.78f, 0.77f};
        float[] wet = new float[y.length];
        for (int c = 0; c < 4; c++) {
            float[] buf = new float[cd[c]];
            int bi = 0;
            float damp = 0;
            for (int i = 0; i < y.length; i++) {
                float inS = i < n ? x[i] : 0;
                float out = buf[bi];
                damp = out * 0.7f + damp * 0.3f;
                buf[bi] = inS + damp * cg[c];
                bi = (bi + 1) % buf.length;
                wet[i] += out * 0.25f;
            }
        }
        int[] ad = {(int) (sr * 0.005), (int) (sr * 0.0017)};
        for (int d : ad) {
            float[] buf = new float[Math.max(1, d)];
            int bi = 0;
            for (int i = 0; i < wet.length; i++) {
                float bo = buf[bi];
                float v = -wet[i] * 0.6f + bo;
                buf[bi] = wet[i] + bo * 0.6f;
                bi = (bi + 1) % buf.length;
                wet[i] = v;
            }
        }
        // 4) низкий органный гул под словами
        double tau = Math.PI * 2;
        float peak = 0.001f;
        for (int i = 0; i < y.length; i++) {
            double t = i / (double) sr;
            double env = Math.min(1, t / 0.4) * Math.max(0, Math.min(1, (y.length - i) / (sr * 0.8)));
            double drone = (Math.sin(tau * 55 * t) * 0.5 + Math.sin(tau * 82.4 * t) * 0.3 + Math.sin(tau * 110 * t) * 0.2) * 0.07 * env;
            float dry = i < n ? x[i] : 0;
            y[i] = dry * 0.85f + wet[i] * 0.42f + (float) drone;
            peak = Math.max(peak, Math.abs(y[i]));
        }
        short[] out = new short[y.length];
        float g = 0.92f / peak;
        for (int i = 0; i < y.length; i++) out[i] = (short) (Math.max(-1f, Math.min(1f, y[i] * g)) * 32767);
        return out;
    }

    static boolean working() { return ready && !failed; }

    static void shutdown() {
        stop();
        if (tts != null) { tts.shutdown(); tts = null; ready = false; }
    }

    /** Название и собственный (не цитата из книг) девиз фракции. */
    static String proclamation(String catName) {
        if (catName == null) return null;
        if (!ru) return proclamationEn(catName);
        String n = catName.toLowerCase();
        if (n.contains("dark angels")) return "Первый легион. Ангелы Тьмы. Наши секреты — наша броня.";
        if (n.contains("emperor's children") || n.contains("emperor’s children")) return "Третий легион. Дети Императора. Ничто, кроме безупречности.";
        if (n.contains("iron warriors")) return "Четвёртый легион. Железные Воины. Каждая стена падёт.";
        if (n.contains("white scars")) return "Пятый легион. Белые Шрамы. Быстры, как буря.";
        if (n.contains("space wolves")) return "Шестой легион. Космические Волки. Охота начинается.";
        if (n.contains("imperial fists")) return "Седьмой легион. Имперские Кулаки. Этот рубеж устоит.";
        if (n.contains("night lords")) return "Восьмой легион. Ночные Властелины. Пусть боятся тьмы.";
        if (n.contains("blood angels")) return "Девятый легион. Кровавые Ангелы. Крылья из огня, сердца из ярости.";
        if (n.contains("iron hands")) return "Десятый легион. Железные Руки. Сталь не дрогнет.";
        if (n.contains("world eaters")) return "Двенадцатый легион. Пожиратели Миров. Без оков и без пощады.";
        if (n.contains("ultramarines")) return "Тринадцатый легион. Ультрамарины. Дисциплина — это победа.";
        if (n.contains("death guard")) return "Четырнадцатый легион. Гвардия Смерти. Мы выдержим всё.";
        if (n.contains("thousand sons")) return "Пятнадцатый легион. Тысяча Сынов. Звёзды говорили.";
        if (n.contains("sons of horus")) return "Шестнадцатый легион. Сыновья Хоруса. Где ведёт Вармастер, там мы идём.";
        if (n.contains("word bearers")) return "Семнадцатый легион. Носители Слова. Наша вера — пылающий клинок.";
        if (n.contains("salamanders")) return "Восемнадцатый легион. Саламандры. Выкованы в пламени, закалены долгом.";
        if (n.contains("raven guard")) return "Девятнадцатый легион. Гвардия Ворона. Бей из тени.";
        if (n.contains("alpha legion")) return "Двадцатый легион. Альфа Легион. Мы повсюду.";
        if (n.contains("blackshields")) return "Чёрные Щиты. Ни знамени, ни господина, ни милости.";
        if (n.contains("shattered")) return "Расколотые Легионы. Сломлены, но не побеждены.";
        if (n.contains("solar auxilia")) return "Солнечные Вспомогательные. Стойте твёрдо и стреляйте как один.";
        if (n.contains("custodes")) return "Легион Кустодес. Никто не пройдёт.";
        if (n.contains("mechanicum")) return "Механикум. Машины войны пробуждаются.";
        if (n.contains("skitarii")) return "Скитарии. Плоть подчиняется кузне.";
        if (n.contains("titanicus")) return "Легион Титаникус. Боги-машины шагают.";
        if (n.contains("questoris")) return "Семья Квестрис. За дом и за честь.";
        if (n.contains("militia")) return "Имперская милиция. Десять тысяч винтовок, одна воля.";
        if (n.contains("daemon")) return "Варп голоден.";
        if (n.contains("assassin")) return "Дивизия Ассасинорум. Тишина. Затем смерть.";
        if (n.contains("psykana")) return "Анафема Психана. Тишина — наше оружие.";
        if (n.contains("knights-errant")) return "Странствующие Рыцари. Тени на службе Императора.";
        return catName + ".";
    }

    static String proclamationEn(String catName) {
        if (catName == null) return null;
        String n = catName.toLowerCase();
        if (n.contains("dark angels")) return "The First Legion. Dark Angels. Our secrets are our armour.";
        if (n.contains("emperor's children") || n.contains("emperor’s children")) return "The Third Legion. Emperor's Children. Nothing less than flawless.";
        if (n.contains("iron warriors")) return "The Fourth Legion. Iron Warriors. Every wall will fall.";
        if (n.contains("white scars")) return "The Fifth Legion. White Scars. Swift as the storm.";
        if (n.contains("space wolves")) return "The Sixth Legion. Space Wolves. The hunt begins.";
        if (n.contains("imperial fists")) return "The Seventh Legion. Imperial Fists. This line will hold.";
        if (n.contains("night lords")) return "The Eighth Legion. Night Lords. Let them fear the dark.";
        if (n.contains("blood angels")) return "The Ninth Legion. Blood Angels. Wings of fire, hearts of fury.";
        if (n.contains("iron hands")) return "The Tenth Legion. Iron Hands. Steel does not falter.";
        if (n.contains("world eaters")) return "The Twelfth Legion. World Eaters. Unchained and unrelenting.";
        if (n.contains("ultramarines")) return "The Thirteenth Legion. Ultramarines. Discipline is victory.";
        if (n.contains("death guard")) return "The Fourteenth Legion. Death Guard. We endure all things.";
        if (n.contains("thousand sons")) return "The Fifteenth Legion. Thousand Sons. The stars have spoken.";
        if (n.contains("sons of horus")) return "The Sixteenth Legion. Sons of Horus. Where the Warmaster leads, we follow.";
        if (n.contains("word bearers")) return "The Seventeenth Legion. Word Bearers. Our faith is a burning blade.";
        if (n.contains("salamanders")) return "The Eighteenth Legion. Salamanders. Forged in flame, tempered by duty.";
        if (n.contains("raven guard")) return "The Nineteenth Legion. Raven Guard. Strike from the shadows.";
        if (n.contains("alpha legion")) return "The Twentieth Legion. Alpha Legion. We are everywhere.";
        if (n.contains("blackshields")) return "Blackshields. No banner, no master, no mercy.";
        if (n.contains("shattered")) return "Shattered Legions. Broken, but never beaten.";
        if (n.contains("solar auxilia")) return "Solar Auxilia. Stand fast, and fire as one.";
        if (n.contains("custodes")) return "Legio Custodes. None shall pass.";
        if (n.contains("mechanicum")) return "Mechanicum. The engines of war awaken.";
        if (n.contains("skitarii")) return "Skitarii. Flesh obeys the forge.";
        if (n.contains("titanicus")) return "Legio Titanicus. The god-engines walk.";
        if (n.contains("questoris")) return "Questoris Familia. For house and for honour.";
        if (n.contains("militia")) return "Imperialis Militia. Ten thousand rifles, one will.";
        if (n.contains("daemon")) return "The warp hungers.";
        if (n.contains("assassin")) return "Divisio Assassinorum. Silence. Then death.";
        if (n.contains("psykana")) return "Anathema Psykana. Silence is our weapon.";
        if (n.contains("knights-errant")) return "Knights Errant. Shadows in the Emperor's service.";
        return catName + ".";
    }
}
