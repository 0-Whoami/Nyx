package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.terminal.SessionManager.removeFinishedSession;
import static com.termux.terminal.SessionManager.sessions;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.paint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

final public class SessionView extends View {
    final GestureDetector gestureDetector;
    final int padding;
    final float height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
    final int margin;

    public SessionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                final int size = sessions.size();
                final float y = e.getY();
                for (int i = 0; i < size; i++) {
                    final float h = i * (height + margin);
                    if (y >= h && y <= h + height) {
                        if (console.currentSession == sessions.get(i)) {
                            removeFinishedSession(sessions.get(i));
                            requestLayout();
                        } else {
                            console.attachSession(i);
                            invalidate();
                        }
                        return true;
                    }
                }
                return true;
            }
        });
        padding = getPaddingStart();
        margin = getPaddingTop();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) ((height + margin) * sessions.size()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int size = sessions.size(), width = getWidth();
        paint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < size; i++) {
            final boolean e = console.currentSession == sessions.get(i);
            final float y = i * (height + margin);
            paint.setColor(e ? primary : secondary);
            canvas.drawRect(0, y, width, y + height, paint);
            paint.setColor(getContrastColor(paint.getColor()));
            final String pid = String.valueOf(sessions.get(i).mShellPid);
            canvas.drawText("[" + (i + 1) + "] " + (e ? "KILL(" + pid + ")" : "pid:" + pid), padding, y + height / 2 + paint.descent(), paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }
}
