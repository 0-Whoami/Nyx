package com.termux.view;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * A combination of {@link GestureDetector} and {@link ScaleGestureDetector}.
 */
final class GestureAndScaleRecognizer {

    private final Listener mListener;
    private final GestureDetector mGestureDetector;

    private final ScaleGestureDetector mScaleDetector;
    private boolean isAfterLongPress;

    GestureAndScaleRecognizer(final Context context, final Listener listener) {
        super();
        this.mListener = listener;
        this.mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float dx, final float dy) {
                return GestureAndScaleRecognizer.this.mListener.onScroll(e2, dx, dy);
            }

            @Override
            public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
                return GestureAndScaleRecognizer.this.mListener.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onDown(final MotionEvent e) {
                return GestureAndScaleRecognizer.this.mListener.onDown(e.getX(), e.getY());
            }

            @Override
            public void onLongPress(final MotionEvent e) {
                GestureAndScaleRecognizer.this.mListener.onLongPress(e);
                GestureAndScaleRecognizer.this.isAfterLongPress = true;
            }
        }, null, true);
        this.mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {

            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e) {
                return GestureAndScaleRecognizer.this.mListener.onSingleTapUp(e);
            }

            @Override
            public boolean onDoubleTap(final MotionEvent e) {
                return GestureAndScaleRecognizer.this.mListener.onDoubleTap(e);
            }

            @Override
            public boolean onDoubleTapEvent(final MotionEvent e) {
                return true;
            }
        });
        this.mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {

            @Override
            public boolean onScaleBegin(final ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(final ScaleGestureDetector detector) {
                return GestureAndScaleRecognizer.this.mListener.onScale(detector.getFocusX(), detector.getFocusY(), detector.getScaleFactor());
            }
        });
        this.mScaleDetector.setQuickScaleEnabled(false);
    }

    public void onTouchEvent(final MotionEvent event) {
        this.mGestureDetector.onTouchEvent(event);
        this.mScaleDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                this.isAfterLongPress = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!this.isAfterLongPress) {
                    // This behaviour is desired when in e.g. vim with mouse events, where we do not
                    // want to move the cursor when lifting finger after a long press.
                    this.mListener.onUp(event);
                }
                break;
        }
    }

    public boolean isInProgress() {
        return this.mScaleDetector.isInProgress();
    }

    public interface Listener {

        boolean onSingleTapUp(MotionEvent e);

        boolean onDoubleTap(MotionEvent e);

        boolean onScroll(MotionEvent e2, float dx, float dy);

        boolean onFling(MotionEvent e, MotionEvent e2, float velocityX, float velocityY);

        boolean onScale(float focusX, float focusY, float scale);

        boolean onDown(float x, float y);

        void onUp(MotionEvent e);

        void onLongPress(MotionEvent e);
    }
}
