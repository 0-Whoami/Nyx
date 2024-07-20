package com.termux.utils;

import static com.termux.NyxActivity.console;
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

import com.termux.terminal.SessionManager;

public class SessionView extends View {
    private final GestureDetector gestureDetector;
    private float h2 = 0f, r = 0f;

    public SessionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                final float x = e.getX();
                final float y = e.getY();
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
                final float x = e.getX();
                final float y = e.getY();
                circles((i, cx) -> {
                    if (inCircle(cx, h2, r, x, y)) {
                        onLongClick(i);
                    }
                });
            }
        });
    }

    protected Object[] list() {
        return sessions.toArray();
    }

    protected String text(int i) {
        return String.valueOf(i + 1);
    }

    protected boolean enable(int i) {
        return console.currentSession == sessions.get(i);
    }

    protected void onClick(int i) {
        console.attachSession(i);
        console.invalidate();
    }

    protected void onLongClick(int i) {
        SessionManager.removeFinishedSession(sessions.get(i));
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int h = MeasureSpec.getSize(heightMeasureSpec) + 20;
        setMeasuredDimension(h * list().length, h);
        h2 = h / 2f;
        r = h2 - 10;
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
        for (int i = 0; i < list().length; i++) {
            action.a(i, (2 * i + 1f) * h2);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }

    interface b {
        void a(int i, float cx);
    }
}