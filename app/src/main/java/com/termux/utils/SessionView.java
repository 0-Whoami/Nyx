package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.terminal.SessionManager.sessions;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.paint;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SessionView extends View {
    private final GestureDetector gestureDetector;
    private float scroll;

    public SessionView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(final MotionEvent e) {
                final float x = e.getX();
                final int height = getHeight(), size = size();
                for (int i = 0; i < size; i++) {
                    final int dx = i * height;
                    if (x > dx && x < dx + height) {
                        onClick(i);
                        invalidate();
                    }
                }
                return true;
            }

            @Override
            public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
                final int width = getWidth(), actualWidth = getHeight() * size();
                if (actualWidth <= width) return true;
                getParent().requestDisallowInterceptTouchEvent(true);
                scroll = max(width - actualWidth, min(scroll - distanceX, 0));
                invalidate();
                return true;
            }
        });
    }

    int size() {
        return sessions.size();
    }

    String text(final int i) {
        return Integer.toString(i + 1);
    }

    boolean enable(final int i) {
        return console.currentSession == sessions.get(i);
    }

    void onClick(final int i) {
        console.attachSession(i);
        console.invalidate();
    }

    @Override
    protected final void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {

        if (MeasureSpec.EXACTLY != MeasureSpec.getMode(heightMeasureSpec)) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, w / size());
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    }

    @Override
    protected final void onDraw(final Canvas canvas) {
        canvas.drawColor(secondary);
        final int height = getHeight(), size = size();
        for (int i = 0; i < size; i++) {
            final float x = i * height + scroll;
            paint.setColor(enable(i) ? primary : 0);
            canvas.drawRect(x, 0, x + height, height, paint);
            paint.setColor(getContrastColor(paint.getColor()));
            canvas.drawText(text(i), x + height / 2, height / 2 + paint.descent(), paint);
        }
    }

    @Override
    public final boolean onTouchEvent(final MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }
}