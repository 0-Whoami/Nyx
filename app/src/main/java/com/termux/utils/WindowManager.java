package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.UiElements.paint;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;

public class WindowManager extends View {
    private final RectF rect = new RectF();
    private final ScaleGestureDetector detector;
    private final int[] sizeRef;
    float factor = 1;
    private float dX = 0, dY = 0;

    public WindowManager() {
        super(console.getContext());
        setFocusable(true);
        setFocusableInTouchMode(true);
        detector = new ScaleGestureDetector(console.getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                factor *= detector.getScaleFactor();
                changeSize();
                return true;
            }
        });
        detector.setQuickScaleEnabled(true);
        sizeRef = new int[]{console.getWidth(), console.getHeight()};
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w != 0 || h != 0) rect.set(w * .25f, h - 85f, w * .75f, h - 15f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        if (detector.isInProgress()) return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (rect.contains(event.getX(), event.getY())) {
                    ((ViewGroup) getParent()).removeView(this);
                    return true;
                }
                dX = console.getX() - event.getRawX();
                dY = console.getY() - event.getRawY();
            }
            case MotionEvent.ACTION_MOVE -> {
                console.setX(event.getRawX() + dX);
                console.setY(event.getRawY() + dY);
            }
        }
        return true;
    }

    void changeSize() {
        final int newHeight = (int) (sizeRef[0] * factor);
        final int newWeight = (int) (sizeRef[1] * factor);
        console.setLayoutParams(new ViewGroup.LayoutParams(newWeight, newHeight));
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            factor *= -event.getAxisValue(MotionEvent.AXIS_SCROLL) > 0 ? 0.95f : 1.05f;
            changeSize();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(primary);
        canvas.drawRoundRect(rect, 35f, 35f, paint);
        paint.setColor(getContrastColor(primary));
        canvas.drawText("Apply", rect.centerX(), rect.centerY() + paint.descent(), paint);
    }
}