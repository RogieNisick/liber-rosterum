package com.medoedy.liber;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import static com.medoedy.liber.Ui.dp;

/** Текст правила на английском с кнопкой «перевести» прямо под ним — без похода в настройки. */
final class TrText {
    private TrText() { }

    /** Выбор игрока для конкретного текста (если не трогал — как в настройках). */
    private static final Map<String, Boolean> choice = new HashMap<String, Boolean>();

    static boolean ru(String en) {
        Boolean b = choice.get(en);
        return b != null && b;
    }

    static void bind(final TextView tv, final String en) {
        if (en == null) return;
        show(tv, en);
        Translator.prefetch(en);
        if (tv.getTag(R_TAG) != null) return;
        tv.setTag(R_TAG, Boolean.TRUE);
        tv.post(new Runnable() { public void run() { wrap(tv, en); } });
    }

    private static final int R_TAG = 0x7f0e1001;

    private static void show(final TextView tv, final String en) {
        if (!ru(en)) { tv.setText(en); return; }
        String c = Translator.cached(en);
        if (c != null) { tv.setText(c); return; }
        tv.setText(en + "\n(переводим…)");
        Translator.translate(en, new Translator.Done() {
            public void got(String r) { if (ru(en)) tv.setText(r); }
        });
    }

    private static void wrap(final TextView tv, final String en) {
        if (!(tv.getParent() instanceof ViewGroup)) return;
        ViewGroup p = (ViewGroup) tv.getParent();
        int idx = p.indexOfChild(tv);
        ViewGroup.LayoutParams lp = tv.getLayoutParams();
        p.removeView(tv);
        LinearLayout box = new LinearLayout(tv.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        final TextView b = new TextView(tv.getContext());
        b.setTypeface(Theme.bodyBold);
        b.setTextSize(12);
        b.setTextColor(Ui.INK_DIM);
        b.setBackground(Ui.states(new Ui.Steel(0x14FFFFFF, 0x10000000, 0xFF8A6A3A, 0, dp(4), false, false), new Ui.Steel(0x33FFFFFF, 0x1A000000, 0xFF8A6A3A, 0, dp(4), false, true)));
        b.setPadding(dp(10), dp(4), dp(10), dp(6));
        label(b, en);
        b.setClickable(true);
        Ui.press(b);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.haptic(Ui.CLICK);
                choice.put(en, !ru(en));
                show(tv, en);
                label(b, en);
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.gravity = Gravity.END;
        bl.topMargin = dp(4);
        box.addView(b, bl);
        p.addView(box, idx, lp);
    }

    private static void label(TextView b, String en) {
        b.setText(ru(en) ? "EN  оригинал" : "RU  перевести");
    }
}
