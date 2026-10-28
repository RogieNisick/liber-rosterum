package com.medoedy.liber;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/** Экран приложения: строит своё представление и умеет перестраиваться. */
abstract class Screen {
    final MainActivity a;
    Theme theme = Theme.DEFAULT;
    View view;

    Screen(MainActivity a) { this.a = a; }

    abstract View build();

    void refresh() { }

    /** true — экран сам обработал «назад». */
    boolean handleBack() { return false; }

    /** Перестроить экран, не сбрасывая прокрутку (иначе страница прыгает наверх). */
    void rebuild() {
        if (view == null) return;
        ViewGroup p = (ViewGroup) view.getParent();
        int idx = p == null ? -1 : p.indexOfChild(view);
        java.util.List<ScrollView> olds = new java.util.ArrayList<ScrollView>();
        scrolls(view, olds);
        final int[] ys = new int[olds.size()];
        for (int i = 0; i < ys.length; i++) ys[i] = olds.get(i).getScrollY();
        View nv = build();
        if (p != null) { p.removeView(view); p.addView(nv, idx); }
        view = nv;
        final java.util.List<ScrollView> news = new java.util.ArrayList<ScrollView>();
        scrolls(nv, news);
        for (int i = 0; i < Math.min(ys.length, news.size()); i++) {
            final ScrollView sv = news.get(i);
            final int y = ys[i];
            if (y == 0) continue;
            sv.scrollTo(0, y);
            sv.post(new Runnable() { public void run() { sv.scrollTo(0, y); } });
            sv.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                int n = 0;
                public void onGlobalLayout() {
                    if (sv.getScrollY() != y) sv.scrollTo(0, y);
                    if (++n >= 3) sv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    static void scrolls(View v, java.util.List<ScrollView> out) {
        if (v instanceof ScrollView) out.add((ScrollView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) scrolls(g.getChildAt(i), out);
        }
    }
}
