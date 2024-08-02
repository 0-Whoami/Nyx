package com.termux.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.termux.NyxActivity;

public final class Rotary extends View {
    private static final String[] list = {"⇅", "◀▶", "▲▼"};
    private final GestureDetector gestureDetector;

    public Rotary(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        this.gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(final MotionEvent e) {
                final float x = e.getX();
                final int height = Rotary.this.getHeight(), size = Rotary.list.length;
                for (int i = 0; i < size; i++) {
                    final int dx = i * height;
                    if (x > dx && x < dx + height) {
                        NyxActivity.console.RotaryMode = i;
                        Rotary.this.invalidate();
                    }
                }
                return true;
            }
        });
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int w = MeasureSpec.getSize(widthMeasureSpec);
        this.setMeasuredDimension(w, w / Rotary.list.length);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        canvas.drawColor(Theme.secondary);
        final int height = this.getHeight(), size = Rotary.list.length;
        for (int i = 0; i < size; i++) {
            final float x = i * height;
            UiElements.paint.setColor(i == NyxActivity.console.RotaryMode ? Theme.primary : 0);
            canvas.drawRect(x, 0, x + height, height, UiElements.paint);
            UiElements.paint.setColor(Theme.getContrastColor(UiElements.paint.getColor()));
            canvas.drawText(Rotary.list[i], x + height / 2, height / 2 + UiElements.paint.descent(), UiElements.paint);
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        return true;
    }
}