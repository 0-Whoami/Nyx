package com.termux.utils;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.termux.data.ConfigManager;

final public class UiElements {
    public static final Paint paint = new Paint() {{
        setTypeface(ConfigManager.typeface);
        setTextSize(23);
        setTextAlign(Align.CENTER);
    }};

    public static boolean inCircle(float centerX, float centerY, float radius, float pointX, float pointY) {
        return (pointX - centerX) * (pointX - centerX) + (pointY - centerY) * (pointY - centerY) <= radius * radius;
    }

    public static void drawRoundedBg(Canvas canvas, int color, int radius) {
        paint.setColor(color);
        final int h = canvas.getHeight();
        final int rx = h * radius / 100;
        canvas.drawRoundRect(0f, 0f, canvas.getWidth(), h, rx, rx, paint);
    }

}