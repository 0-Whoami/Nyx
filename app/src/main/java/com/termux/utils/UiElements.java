package com.termux.utils;

import android.graphics.Paint;

public enum UiElements {
    ;
    public static final Paint paint = new Paint();

    static {
        paint.setTextSize(25);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public static boolean inCircle(final float centerX, final float centerY, final float radius, final float pointX, final float pointY) {
        final float dx = pointX - centerX;
        final float dy = pointY - centerY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

}