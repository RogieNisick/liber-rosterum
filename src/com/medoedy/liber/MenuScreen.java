package com.medoedy.liber;

import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medoedy.liber.engine.Data;

import static com.medoedy.liber.Ui.dp;

/** Главное меню: два режима — ростеры и битва — плюс данные и настройки. */
final class MenuScreen extends Screen {
    MenuScreen(MainActivity a) { super(a); }

    @Override
    void refresh() { rebuild(); }

    View build() {
        final FrameLayout f = new FrameLayout(a);
        LinearLayout c = a.column();
        c.setPadding(dp(16), dp(8), dp(16), dp(130));

        LinearLayout topRow = Ui.row(a);
        topRow.setGravity(Gravity.END);
        TextView gear = Ui.button(a, "⚙", Ui.steelButton(theme, dp(8)), Ui.GOLD, 20, new View.OnClickListener() {
            public void onClick(View v) { a.openSettings(theme, null); }
        });
        gear.setPadding(0, 0, 0, dp(3));
        TextView mode = Ui.button(a, Ui.dark ? "☾" : "☀", Ui.steelButton(theme, dp(8)), Ui.GOLD, 20, new View.OnClickListener() {
            public void onClick(View v) { a.toggleDark(); }
        });
        mode.setPadding(0, 0, 0, dp(3));
        LinearLayout modeBox = Ui.col(a);
        modeBox.setGravity(Gravity.CENTER_HORIZONTAL);
        modeBox.addView(mode, Ui.lp(dp(46), dp(46)));
        TextView ml = Ui.text(a, Ui.dark ? "Когитатор" : "Пергамент", 10, Theme.bodyBold, Ui.GOLD_DIM);
        modeBox.addView(ml);
        News.refresh(a, false, new Runnable() { public void run() { if (a.stack.peek() == MenuScreen.this && News.unseen(a)) rebuild(); } });
        topRow.addView(modeBox, Ui.lpm(-2, -2, 0, 0, dp(10), 0));
        topRow.addView(gear, Ui.lp(dp(46), dp(46)));
        // верхние кнопки убраны: тема — в настройках, настройки — долгое нажатие на сервочерепа
        c.addView(new View(a), Ui.lp(-1, dp(52)));

        LinearLayout hero = Ui.col(a);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        final Ui.Hex hx = new Ui.Hex(a, theme, "skull");
        hero.addView(hx, Ui.lp(dp(104), dp(104)));
        TextView t = Ui.text(a, "LIBER ROSTERUM", 30, Theme.namesBlack, Ui.BONE);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dp(10), 0, dp(2), 0xFF000000);
        hero.addView(t, Ui.lpm(-2, -2, 0, dp(8), 0, 0));
        TextView sub = Ui.title(a, "Ересь · третья редакция", 16, Ui.GOLD);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub);
        hero.addView(Ui.skullDivider(a, Ui.GOLD_DIM));
        c.addView(hero);
        hx.setRotation(-30);
        hx.setScaleX(0.6f);
        hx.setScaleY(0.6f);
        hx.animate().rotation(0).scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(new OvershootInterpolator(1.6f)).start();

        final Updater.Info upd = Updater.available;
        if (upd != null) {
            c.addView(tile("scroll", "Доступно обновление " + (upd.name == null ? "" : upd.name), "Нажмите, чтобы посмотреть, что нового, и установить", true, new View.OnClickListener() {
                public void onClick(View v) { Updater.show(a, upd, theme); }
            }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));
        } else if (!Updater.checked) {
            Updater.check(a, new Updater.Done() {
                public void got(Updater.Info i, String error) {
                    if (i != null && a.stack.peek() == MenuScreen.this) { Sfx.play("bell"); rebuild(); }
                }
            });
        }

        final Battle saved = Battle.load(a);
        if (saved != null && !saved.finished) {
            c.addView(tile("banner", "Продолжить битву", saved.mission.ru + " · ход " + saved.turn + " из " + saved.mission.turns
                    + " · " + saved.total(0) + " : " + saved.total(1), true, new View.OnClickListener() {
                public void onClick(View v) { a.show(new BattleScreen(a, saved)); }
            }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));
        }
        int n = a.store.rosters().size();
        c.addView(tile("banner", "Ростеры армий", n == 0 ? "Соберите первую армию" : n + " " + MainActivity.plural(n, "ростер", "ростера", "ростеров") + " в архиве", false, new View.OnClickListener() {
            public void onClick(View v) { a.show(new RosterListScreen(a)); }
        }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));
        c.addView(tile("mech", "Умный ростер", "Когитатор соберёт армию под противника из ваших моделей", false, new View.OnClickListener() {
            public void onClick(View v) { a.show(new SmartScreen(a)); }
        }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));
        int mu = Minis.all(a).size();
        c.addView(tile("infantry", "Мои миниатюры", mu == 0 ? "Каталог вашей реальной коллекции с фото" : mu + " " + MainActivity.plural(mu, "юнит", "юнита", "юнитов") + " в коллекции", false, new View.OnClickListener() {
            public void onClick(View v) { a.show(new MinisScreen(a)); }
        }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));
        c.addView(tile("dice", "Битва", "Счёт победных очков, миссии и ходы", false, new View.OnClickListener() {
            public void onClick(View v) { a.show(new BattleSetupScreen(a)); }
        }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));
        c.addView(tile("book", "Библиотека", "Книги правил: поиск по всем страницам, закладки", false, new View.OnClickListener() {
            public void onClick(View v) { a.show(new LibraryScreen(a)); }
        }), Ui.lpm(-1, -2, 0, dp(6), 0, dp(6)));

        f.addView(a.scroll(c), new FrameLayout.LayoutParams(-1, -1));
        a.h.postDelayed(new Runnable() { public void run() { if (a.stack.peek() == MenuScreen.this) Guide.welcome(a, false); } }, 1100);
        f.addView(new Ui.Candles(a, 5, 5), 0, new FrameLayout.LayoutParams(-1, dp(230), Gravity.BOTTOM)); // свечи — за кнопками
        // сервочереп-писарь парит у заголовка
        final Ui.ServoSkull sk = new Ui.ServoSkull(a);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(dp(84), dp(110), Gravity.TOP | Gravity.START);
        sl.topMargin = dp(40) + a.capPads()[0];
        sl.leftMargin = dp(6);
        // шестерня за сервочерепом — медленно вращается, подсказывает: долгое нажатие — настройки
        View cog = new View(a) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(android.graphics.Canvas cv) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
                android.graphics.Path g = new android.graphics.Path();
                int teeth = 12;
                for (int i = 0; i < teeth * 2; i++) {
                    double a0 = Math.PI * i / teeth, a1 = Math.PI * (i + 1) / teeth;
                    float rr = i % 2 == 0 ? r : r * 0.84f;
                    float x0 = cx + (float) Math.cos(a0 + 0.06) * rr, y0 = cy + (float) Math.sin(a0 + 0.06) * rr;
                    float x1 = cx + (float) Math.cos(a1 - 0.06) * rr, y1 = cy + (float) Math.sin(a1 - 0.06) * rr;
                    if (i == 0) g.moveTo(x0, y0); else g.lineTo(x0, y0);
                    g.lineTo(x1, y1);
                }
                g.close();
                g.addCircle(cx, cy, r * 0.42f, android.graphics.Path.Direction.CCW);
                g.setFillType(android.graphics.Path.FillType.EVEN_ODD);
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(0x2EC8A65A);
                cv.drawPath(g, p);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f));
                p.setColor(0x55C8A65A);
                cv.drawPath(g, p);
            }
        };
        int gs = dp(124);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(gs, gs, Gravity.TOP | Gravity.START);
        gl.topMargin = sl.topMargin + dp(110) / 2 - gs / 2;
        gl.leftMargin = sl.leftMargin + dp(84) / 2 - gs / 2;
        f.addView(cog, gl);
        android.animation.ObjectAnimator spin = android.animation.ObjectAnimator.ofFloat(cog, "rotation", 0f, 360f);
        spin.setDuration(45000);
        spin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        spin.setInterpolator(new android.view.animation.LinearInterpolator());
        spin.start();
        f.addView(sk, sl);
        sk.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { Ui.haptic(Ui.HEAVY); Sfx.play("servo", 0.8f, 0.8f); a.openSettings(theme, null); return true; }
        });
        sk.setAlpha(0f);
        sk.setTranslationX(-dp(60));
        sk.animate().alpha(1f).translationX(0).setStartDelay(500).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        // реплика сервочерепа: при каждом заходе в меню — новая
        final TextView say = Ui.text(a, Guide.nextPhrase(a), 14, Theme.bodyBold, 0xFF2B1E12);
        say.setBackground(new Ui.Bubble());
        say.setPadding(dp(22), dp(8), dp(12), dp(10));
        say.setMaxWidth(dp(190));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bl.topMargin = sl.topMargin + dp(6);
        bl.leftMargin = sl.leftMargin + dp(78);
        f.addView(say, bl);
        // вести Ереси — свиток справа, напротив сервочерепа
        FrameLayout nb = new FrameLayout(a);
        android.widget.ImageView ic = new android.widget.ImageView(a);
        try { ic.setImageBitmap(android.graphics.BitmapFactory.decodeStream(a.getAssets().open("news_scroll.png"))); } catch (Exception ignored) { }
        ic.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        // приглушённое старое золото — в тон интерфейсу
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setSaturation(0);
        cm.postConcat(new android.graphics.ColorMatrix(new float[]{0.74f, 0, 0, 0, 0, 0, 0.62f, 0, 0, 0, 0, 0, 0.38f, 0, 0, 0, 0, 0, 1, 0}));
        ic.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        ic.setAlpha(0.9f);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER);
        il.bottomMargin = dp(8);
        nb.addView(ic, il);
        TextView nl = Ui.title(a, "ВЕСТИ", 13, Ui.GOLD);
        nl.setLetterSpacing(0.18f);
        nl.setShadowLayer(dp(5), 0, dp(1), 0xFF000000);
        nb.addView(nl, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        if (News.unseen(a)) {
            View dot = new View(a);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(0xFFE03020);
            g.setStroke(dp(2), 0xFF1A140C);
            dot.setBackground(g);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END);
            dl.setMargins(0, dp(2), dp(4), 0);
            nb.addView(dot, dl);
        }
        nb.setClickable(true);
        Ui.press(nb);
        nb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("page"); a.show(new NewsScreen(a)); } });
        // зеркально сервочерепу: тот же размер и отступы
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(dp(84), dp(110), Gravity.TOP | Gravity.END);
        nlp.topMargin = sl.topMargin;
        nlp.rightMargin = sl.leftMargin;
        f.addView(nb, nlp);
        nb.setAlpha(0f);
        nb.animate().alpha(1f).setStartDelay(700).setDuration(600).start();
        say.setPivotX(0);
        say.setPivotY(dp(24));
        say.setScaleX(0f);
        say.setScaleY(0f);
        say.animate().scaleX(1f).scaleY(1f).setStartDelay(1100).setDuration(380).setInterpolator(new OvershootInterpolator(2.2f)).withStartAction(new Runnable() {
            public void run() { Sfx.play("servo", 0.6f, 1.25f); Voice.saySkull(say.getText().toString()); }
        }).start();
        say.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { say.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(160).start(); } });
        // ---- пасхалки
        skull = sk;
        Eggs.emblemGestures(a, hx, say);
        final int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        final boolean[] asleep = {hour >= 0 && hour < 5};
        if (asleep[0]) {
            say.setText("Zzz… 0101… zzz… 1101… хрр…");
            sk.animate().cancel();
            sk.setAlpha(0.85f);
            sk.setTranslationX(0);
            sk.setRotation(-14);
            final Runnable bob = new Runnable() {
                public void run() {
                    if (!asleep[0] || sk.getParent() == null) return;
                    sk.animate().translationY(dp(6)).setDuration(1600).withEndAction(new Runnable() {
                        public void run() { if (asleep[0]) sk.animate().translationY(0).setDuration(1600).start(); }
                    }).start();
                    a.h.postDelayed(this, 3300);
                }
            };
            a.h.postDelayed(bob, 600);
        }
        long last = a.prefs.getLong("last_open", 0), nowMs = System.currentTimeMillis();
        a.prefs.edit().putLong("last_open", nowMs).apply();
        final long days = last == 0 ? 0 : (nowMs - last) / 86400000L;
        if (days >= 7) {
            final View web = Eggs.cobwebs(a);
            f.addView(web, new FrameLayout.LayoutParams(-1, -1));
            say.setText(Guide.name(a) + "! Тебя не было " + days + " " + MainActivity.plural((int) days, "день", "дня", "дней") + ". Архив зарос паутиной… Сейчас приберусь.");
            Voice.saySkull("Тебя не было " + days + " " + MainActivity.plural((int) days, "день", "дня", "дней") + ". Архив зарос паутиной. Сейчас приберусь.");
            a.h.postDelayed(new Runnable() {
                public void run() {
                    Sfx.play("servo", 0.9f, 1.1f);
                    final float x0 = sk.getTranslationX();
                    int w = f.getWidth();
                    sk.animate().translationX(w * 0.7f).translationY(dp(260)).rotation(20).setDuration(900).withEndAction(new Runnable() {
                        public void run() {
                            sk.animate().translationX(x0).translationY(0).rotation(0).setDuration(900).start();
                        }
                    }).start();
                    web.animate().alpha(0f).setDuration(1700).withEndAction(new Runnable() {
                        public void run() { f.removeView(web); a.toast("Чисто. Можно снова собирать армии."); }
                    }).start();
                    Eggs.incense(a, sk, 0xFFCFC6B4, 26);
                }
            }, 2600);
        }
        sk.setOnClickListener(new View.OnClickListener() {
            final long[] taps = new long[10];
            int n;
            boolean away;
            public void onClick(View v) {
                if (away) return;
                if (asleep[0]) {
                    asleep[0] = false;
                    sk.animate().cancel();
                    sk.setAlpha(1f);
                    Sfx.play("error", 0.6f, 1.6f);
                    Ui.haptic(Ui.DOUBLE);
                    sk.animate().translationY(-dp(30)).rotation(0).setDuration(140).withEndAction(new Runnable() {
                        public void run() { sk.animate().translationY(0).setDuration(500).setInterpolator(new OvershootInterpolator(2f)).start(); }
                    }).start();
                    pop(say, "А? Что?! Я не спал! Я… охранял архив. Глубокая ночь, " + Guide.name(a) + ", а ты всё ростеры собираешь.");
                    Voice.saySkull("А? Что?! Я не спал! Я охранял архив. Глубокая ночь, а ты всё ростеры собираешь.");
                    return;
                }
                long now = android.os.SystemClock.uptimeMillis();
                taps[n % 10] = now;
                n++;
                if (n >= 10 && now - taps[n % 10] < 5000) {
                    n = 0;
                    away = true;
                    Sfx.play("error", 0.9f, 0.7f);
                    Ui.haptic(Ui.HEAVY);
                    Eggs.incense(a, sk, 0xFF555555, 22);
                    pop(say, "01001000 01010110 01000001 01010100 01001001 01010100! Я не игрушка, " + Guide.name(a) + ". Улетаю.");
                    Voice.saySkull("Я не игрушка. Улетаю.");
                    sk.animate().rotation(-25).setDuration(250).withEndAction(new Runnable() {
                        public void run() {
                            sk.animate().translationX(-dp(200)).translationY(-dp(40)).rotation(-60).setDuration(700).setStartDelay(900).withEndAction(new Runnable() {
                                public void run() {
                                    say.animate().alpha(0f).setDuration(300).start();
                                    a.h.postDelayed(new Runnable() {
                                        public void run() {
                                            sk.setRotation(0);
                                            sk.setTranslationY(0);
                                            sk.animate().translationX(0).setStartDelay(0).setDuration(1200).setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
                                                public void run() { away = false; pop(say, "Ладно, я вернулся. Но больше не тыкай."); Voice.saySkull("Ладно, я вернулся. Но больше не тыкай."); Sfx.play("servo"); }
                                            }).start();
                                        }
                                    }, 15000);
                                }
                            }).start();
                        }
                    }).start();
                    return;
                }
                String p = Guide.nextPhrase(a);
                pop(say, p);
                Voice.saySkull(p);
                Sfx.play("servo");
                Ui.haptic(Ui.TICK);
                v.animate().translationYBy(-dp(16)).rotationBy(12).setDuration(160).withEndAction(new Runnable() {
                    public void run() { sk.animate().translationY(0).rotation(0).setDuration(420).setInterpolator(new OvershootInterpolator(2f)).start(); }
                }).start();
            }
        });
        return f;
    }

    View skull;

    /** Экстерминатус по тряске телефона. */
    void shaken() { Eggs.exterminatus(a, skull); }

    static void pop(TextView say, String text) {
        say.setText(text);
        say.setAlpha(1f);
        say.setScaleX(0.4f);
        say.setScaleY(0.4f);
        say.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(300).setInterpolator(new OvershootInterpolator(2.2f)).start();
    }

    View tile(String glyph, String title, String sub, boolean heraldic, final View.OnClickListener l) {
        LinearLayout r = Ui.row(a);
        int trim = Ui.dark ? Ui.trimOf(theme) : Ui.GOLD;
        r.setBackground(heraldic ? Ui.states(new Ui.Steel(0xF2402418, 0xF2190C08, trim, 0xFFC04020, dp(12), true, false), new Ui.Steel(0xF2502C1E, 0xF2200F0A, trim, 0xFFC04020, dp(12), true, true))
                : Ui.dark ? Ui.states(new Ui.Steel(0xF41C1E1A, 0xF40A0B09, trim, 0, dp(12), true, false), new Ui.Steel(0xF4232A24, 0xF40D120E, trim, 0, dp(12), true, true))
                : Ui.states(new Ui.Steel(0xF226302B, 0xF2121815, trim, 0, dp(12), true, false), new Ui.Steel(0xF2343F39, 0xF21A211E, trim, 0, dp(12), true, true)));
        r.setPadding(dp(14), dp(14), dp(16), dp(14));
        r.addView(new Ui.Hex(a, theme, glyph), Ui.lpm(dp(60), dp(60), 0, 0, dp(14), 0));
        LinearLayout tt = Ui.col(a);
        TextView tv = Ui.title(a, title, 23, Ui.BONE);
        tv.setShadowLayer(dp(4), 0, dp(1), 0xFF000000);
        tt.addView(tv);
        tt.addView(Ui.text(a, sub, 14, Theme.body, Ui.GOLD));
        r.addView(tt, Ui.weight(1));
        r.addView(Ui.text(a, "›", 30, Theme.display, Ui.GOLD_DIM));
        r.setClickable(true);
        Ui.press(r);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Ui.haptic(Ui.CLICK); Sfx.play("clunk"); l.onClick(v); }
        });
        return r;
    }
}
