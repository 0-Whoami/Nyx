package com.termux.utils;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.termux.data.ConfigManager;

enum UiElements {
    ;
    public static final Paint paint = new Paint() {{
        setTypeface(ConfigManager.typeface);
        setTextSize(23);
        setTextAlign(Align.CENTER);
    }};

    public static boolean inCircle(float centerX, float centerY, float radius, float pointX, float pointY) {
        float dx = pointX - centerX;
        float dy = pointY - centerY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    public static void drawRoundedBg(Canvas canvas, int color, int radius) {
        UiElements.paint.setColor(color);
        int h = canvas.getHeight();
        int rx = h * radius / 100;
        canvas.drawRoundRect(0.0f, 0.0f, canvas.getWidth(), h, rx, rx, UiElements.paint);
    }

}