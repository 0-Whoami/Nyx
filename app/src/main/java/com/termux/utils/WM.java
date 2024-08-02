package com.termux.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;

import com.termux.NyxActivity;

public final class WM extends View {
    private final RectF rect = new RectF();
    private final ScaleGestureDetector detector;
    private final int[] sizeRef;
    private float factor = 1;
    private float dX, dY;

    public WM(final Context context) {
        super(context);
        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
        this.detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(final ScaleGestureDetector detector) {
                WM.this.factor *= detector.getScaleFactor();
                WM.this.changeSize();
                return true;
            }
        });
        this.detector.setQuickScaleEnabled(true);
        this.sizeRef = new int[]{NyxActivity.console.getWidth(), NyxActivity.console.getHeight()};
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (0 != w || 0 != h) this.rect.set(w * 0.25f, h - 85.0f, w * 0.75f, h - 15.0f);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        this.getParent().requestDisallowInterceptTouchEvent(true);
        this.detector.onTouchEvent(event);
        if (this.detector.isInProgress()) return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                this.dX = NyxActivity.console.getX() - event.getRawX();
                this.dY = NyxActivity.console.getY() - event.getRawY();
            }
            case MotionEvent.ACTION_MOVE -> {
                NyxActivity.console.setX(event.getRawX() + this.dX);
                NyxActivity.console.setY(event.getRawY() + this.dY);
                NyxActivity.console.invalidate();
            }
            case MotionEvent.ACTION_UP -> {
                if (this.rect.contains(event.getX(), event.getY())) {
                    ((ViewManager) this.getParent()).removeView(this);
                }
            }
        }
        return true;
    }

    private void changeSize() {
        final int newHeight = (int) (this.sizeRef[0] * this.factor);
        final int newWeight = (int) (this.sizeRef[1] * this.factor);
        NyxActivity.console.setLayoutParams(new ViewGroup.LayoutParams(newWeight, newHeight));
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        if (MotionEvent.ACTION_SCROLL == event.getAction() && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            this.factor *= 0 < -event.getAxisValue(MotionEvent.AXIS_SCROLL) ? 0.95f : 1.05f;
            this.changeSize();
        }
        return true;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        UiElements.paint.setColor(Theme.primary);
        canvas.drawRect(this.rect, UiElements.paint);
        UiElements.paint.setColor(Theme.getContrastColor(Theme.primary));
        canvas.drawText("Apply", this.rect.centerX(), this.rect.centerY() + UiElements.paint.descent(), UiElements.paint);
    }
}