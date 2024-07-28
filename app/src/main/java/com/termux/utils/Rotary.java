package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.paint;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public final class Rotary extends View {
    private static final String[] list = {"⇅", "◀▶", "▲▼"};
    private final GestureDetector gestureDetector;

    public Rotary(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(final MotionEvent e) {
                final float x = e.getX();
                final int height = getHeight(), size = list.length;
                for (int i = 0; i < size; i++) {
                    final int dx = i * height;
                    if (x > dx && x < dx + height) {
                        console.RotaryMode = i;
                        invalidate();
                    }
                }
                return true;
            }
        });
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, w / list.length);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        canvas.drawColor(secondary);
        final int height = getHeight(), size = list.length;
        for (int i = 0; i < size; i++) {
            final float x = i * height;
            paint.setColor(i == console.RotaryMode ? primary : 0);
            canvas.drawRect(x, 0, x + height, height, paint);
            paint.setColor(getContrastColor(paint.getColor()));
            canvas.drawText(list[i], x + height / 2, height / 2 + paint.descent(), paint);
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }
}