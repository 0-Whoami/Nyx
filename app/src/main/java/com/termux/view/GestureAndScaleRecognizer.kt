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
        mGestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                dx: Float,
                dy: Float
            ): Boolean {
                mListener.onScroll(e2, dy)
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                mListener.onFling(
                    e1,
                    e2,
                    velocityX,
                    velocityY
                )
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                mListener.onLongPress(e)
                this@GestureAndScaleRecognizer.isAfterLongPress = true
            }
        }, null, true)
        mGestureDetector.setOnDoubleTapListener(object : OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                mListener.onSingleTapUp(e)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                return false
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                return true
            }
        })
        mScaleDetector =
            ScaleGestureDetector(context!!, object : SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    mListener.onScale(detector.scaleFactor)
                    return true
                }
            })
        mScaleDetector.isQuickScaleEnabled = false
    }

    fun onTouchEvent(event: MotionEvent) {
        mGestureDetector.onTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> isAfterLongPress = false
            MotionEvent.ACTION_UP -> if (!isAfterLongPress) {
                // This behaviour is desired when in e.g. vim with mouse events, where we do not
                // want to move the cursor when lifting finger after a long press.
                mListener.onUp(event)
            }
        }
    }

    val isInProgress: Boolean
        get() = mScaleDetector.isInProgress

    interface Listener {
        fun onSingleTapUp(e: MotionEvent)

        fun onScroll(e2: MotionEvent, dy: Float)

        fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        )

        fun onScale(scale: Float)

        fun onUp(e: MotionEvent)

        fun onLongPress(e: MotionEvent)
    }
}
