package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.UiElements.paint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;

public final class WM extends View {
    private final RectF rect = new RectF();
    private final ScaleGestureDetector detector;
    private final int[] sizeRef;
    private float factor = 1;
    private float dX, dY;

    public WM(final Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(final ScaleGestureDetector detector) {
                factor *= detector.getScaleFactor();
                changeSize();
                return true;
            }
        });
        detector.setQuickScaleEnabled(true);
        sizeRef = new int[]{console.getWidth(), console.getHeight()};
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (0 != w || 0 != h) rect.set(w * 0.25f, h - 85.0f, w * 0.75f, h - 15.0f);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        getParent().requestDisallowInterceptTouchEvent(true);
        detector.onTouchEvent(event);
        if (detector.isInProgress()) return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                dX = console.getX() - event.getRawX();
                dY = console.getY() - event.getRawY();
            }
            case MotionEvent.ACTION_MOVE -> {
                console.setX(event.getRawX() + dX);
                console.setY(event.getRawY() + dY);
                console.invalidate();
            }
            case MotionEvent.ACTION_UP -> {
                if (rect.contains(event.getX(), event.getY())) {
                    ((ViewManager) getParent()).removeView(this);
                }
            }
        }
        return true;
    }

    private void changeSize() {
        final int newHeight = (int) (sizeRef[0] * factor);
        final int newWeight = (int) (sizeRef[1] * factor);
        console.setLayoutParams(new ViewGroup.LayoutParams(newWeight, newHeight));
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        if (MotionEvent.ACTION_SCROLL == event.getAction() && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            factor *= 0 < -event.getAxisValue(MotionEvent.AXIS_SCROLL) ? 0.95f : 1.05f;
            changeSize();
        }
        return true;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        paint.setColor(primary);
        canvas.drawRect(rect, paint);
        paint.setColor(getContrastColor(primary));
        canvas.drawText("Apply", rect.centerX(), rect.centerY() + paint.descent(), paint);
    }
}