package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.terminal.SessionManager.removeFinishedSession;
import static com.termux.terminal.SessionManager.sessions;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.UiElements.inCircle;
import static com.termux.utils.UiElements.paint;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SessionView extends View {
    private final GestureDetector gestureDetector;
    protected int padding = 10;
    private float h2, r;

    public SessionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();
                circles((i, cx) -> {
                    if (inCircle(cx, h2, r, x, y)) {
                        onClick(i);
                        invalidate();
                    }
                });
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();
                circles((i, cx) -> {
                    if (inCircle(cx, h2, r, x, y)) {
                        onLongClick(i);
                    }
                });
            }
        });
    }

    int size() {
        return sessions.size();
    }

    String text(int i) {
        return String.valueOf(i + 1);
    }

    boolean enable(int i) {
        return console.currentSession == sessions.get(i);
    }

    void onClick(int i) {
        console.attachSession(i);
        console.invalidate();
    }

    void onLongClick(int i) {
        removeFinishedSession(sessions.get(i));
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = MeasureSpec.getSize(heightMeasureSpec) + 20;
        setMeasuredDimension(h * size(), h);
    }

    @Override
    protected final void onSizeChanged(int w, int h, int oldw, int oldh) {
        h2 = h / 2.0f;
        r = h2 - padding;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        circles((i, cx) -> {
            paint.setColor(enable(i) ? primary : 0);
            canvas.drawCircle(cx, h2, r, paint);
            paint.setColor(getContrastColor(paint.getColor()));
            canvas.drawText(text(i), cx, h2 + paint.descent(), paint);
        });
    }

    private void circles(b action) {
        var length = size();
        for (int i = 0; i < length; i++) {
            action.a(i, (2 * i + 1.0f) * h2);
        }
    }

    @Override
    public final boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @FunctionalInterface
    interface b {
        void a(int i, float cx);
    }
}