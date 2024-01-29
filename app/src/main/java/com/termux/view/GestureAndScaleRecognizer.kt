package com.termux.view

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener

/**
 * A combination of [GestureDetector] and [ScaleGestureDetector].
 */
internal class GestureAndScaleRecognizer(context: Context?, private val mListener: Listener) {
    private val mGestureDetector: GestureDetector

    private val mScaleDetector: ScaleGestureDetector
    private var isAfterLongPress = false

    init {
        this.mGestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                dx: Float,
                dy: Float
            ): Boolean {
                return mListener.onScroll(e2, dx, dy)
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                return mListener.onFling(e1!!, e2, velocityX, velocityY)
            }

            override fun onDown(e: MotionEvent): Boolean {
                return mListener.onDown(e.x, e.y)
            }

            override fun onLongPress(e: MotionEvent) {
                mListener.onLongPress(e)
                this@GestureAndScaleRecognizer.isAfterLongPress = true
            }
        }, null, true)
        mGestureDetector.setOnDoubleTapListener(object : OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                return mListener.onSingleTapUp(e)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                return mListener.onDoubleTap(e)
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                return true
            }
        })
        this.mScaleDetector =
            ScaleGestureDetector(context!!, object : SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    return mListener.onScale(detector.focusX, detector.focusY, detector.scaleFactor)
                }
            })
        mScaleDetector.isQuickScaleEnabled = false
    }

    fun onTouchEvent(event: MotionEvent) {
        mGestureDetector.onTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> this.isAfterLongPress = false
            MotionEvent.ACTION_UP -> if (!this.isAfterLongPress) {
                // This behaviour is desired when in e.g. vim with mouse events, where we do not
                // want to move the cursor when lifting finger after a long press.
                mListener.onUp(event)
            }
        }
    }

    val isInProgress: Boolean
        get() = mScaleDetector.isInProgress

    interface Listener {
        fun onSingleTapUp(e: MotionEvent): Boolean

        fun onDoubleTap(e: MotionEvent): Boolean

        fun onScroll(e2: MotionEvent, dx: Float, dy: Float): Boolean

        fun onFling(e: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean

        fun onScale(focusX: Float, focusY: Float, scale: Float): Boolean

        fun onDown(x: Float, y: Float): Boolean

        fun onUp(e: MotionEvent?)

        fun onLongPress(e: MotionEvent?)
    }
}
