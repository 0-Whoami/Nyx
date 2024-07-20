package com.termux.view;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;

/**
 * A combination of [GestureDetector] and [ScaleGestureDetector].
 */
public final class GestureAndScaleRecognizer {
    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleDetector;
    private final Listener mListener;
    private boolean isAfterLongPress = false;

    public GestureAndScaleRecognizer(Context context, Listener listener) {
        mGestureDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                listener.onScroll(e2, distanceY);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (mScaleDetector.isInProgress()) return;
                listener.onLongPress(e);
                isAfterLongPress = true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                listener.onSingleTapUp();
                return true;
            }
        });
        mScaleDetector = new ScaleGestureDetector(context, new SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                listener.onScale(detector.getScaleFactor());
                return true;
            }
        });
        mScaleDetector.setQuickScaleEnabled(true);
        mListener = listener;
    }

    public void onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mScaleDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> isAfterLongPress = false;
            case MotionEvent.ACTION_UP -> {
                if (!isAfterLongPress) mListener.onUp(event);

            }
        }
    }

    interface Listener {
        void onSingleTapUp();

        void onScroll(MotionEvent e2, Float dy);

        void onScale(Float scale);

        void onUp(MotionEvent e);

        void onLongPress(MotionEvent e);
    }
}
