package com.termux.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.termux.NyxActivity;
import com.termux.terminal.SessionManager;

public final class SessionView extends View {
    private final GestureDetector gestureDetector;
    private final int padding;
    private final float height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, this.getResources().getDisplayMetrics());
    private final int margin;

    public SessionView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        this.gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e) {
                final int size = SessionManager.sessions.size();
                final float y = e.getY();
                for (int i = 0; i < size; i++) {
                    final float h = i * (SessionView.this.height + SessionView.this.margin);
                    if (y >= h && y <= h + SessionView.this.height) {
                        if (NyxActivity.console.currentSession == SessionManager.sessions.get(i)) {
                            SessionManager.removeFinishedSession(SessionManager.sessions.get(i));
                            SessionView.this.requestLayout();
                        } else {
                            NyxActivity.console.attachSession(i);
                            SessionView.this.invalidate();
                        }
                        return true;
                    }
                }
                return true;
            }
        });
        this.padding = this.getPaddingStart();
        this.margin = this.getPaddingTop();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        this.setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) ((this.height + this.margin) * SessionManager.sessions.size()));
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        final int size = SessionManager.sessions.size(), width = this.getWidth();
        UiElements.paint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < size; i++) {
            final boolean e = NyxActivity.console.currentSession == SessionManager.sessions.get(i);
            final float y = i * (this.height + this.margin);
            UiElements.paint.setColor(e ? Theme.primary : Theme.secondary);
            canvas.drawRect(0, y, width, y + this.height, UiElements.paint);
            UiElements.paint.setColor(Theme.getContrastColor(UiElements.paint.getColor()));
            final String pid = String.valueOf(SessionManager.sessions.get(i).mShellPid);
            canvas.drawText("[" + (i + 1) + "] " + (e ? "KILL(" + pid + ")" : "pid:" + pid), this.padding, y + this.height / 2 + UiElements.paint.descent(), UiElements.paint);
        }
        UiElements.paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        return true;
    }
}
