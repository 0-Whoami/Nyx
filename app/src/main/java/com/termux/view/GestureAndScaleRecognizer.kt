package com.termux.view

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener

/**
 * A combination of [GestureDetector] and [ScaleGestureDetector].
 */
internal class GestureAndScaleRecognizer(context : Context, private val mListener : Listener) {
    private val mGestureDetector : GestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
        override fun onScroll(e1 : MotionEvent?, e2 : MotionEvent, dx : Float, dy : Float) : Boolean {
            mListener.onScroll(e2, dy)
            return true
        }

        override fun onLongPress(e : MotionEvent) {
            if (mScaleDetector.isInProgress) return
            mListener.onLongPress(e)
            this@GestureAndScaleRecognizer.isAfterLongPress = true
        }

        override fun onSingleTapConfirmed(e : MotionEvent) : Boolean {
            mListener.onSingleTapUp()
            return true
        }
    })

    private val mScaleDetector : ScaleGestureDetector = ScaleGestureDetector(context, object : SimpleOnScaleGestureListener() {
        override fun onScale(detector : ScaleGestureDetector) : Boolean {
            mListener.onScale(detector.scaleFactor)
            return true
        }
    }).apply { isQuickScaleEnabled = true }

    private var isAfterLongPress = false


    fun onTouchEvent(event : MotionEvent) {
        mGestureDetector.onTouchEvent(event)
        mScaleDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> isAfterLongPress = false
            MotionEvent.ACTION_UP -> if (!isAfterLongPress) { // This behaviour is desired when in e.g. vim with mouse events, where we do not
                // want to move the cursor when lifting finger after a long press.
                mListener.onUp(event)
            }
        }
    }

    interface Listener {
        fun onSingleTapUp()

        fun onScroll(e2 : MotionEvent, dy : Float)

        fun onScale(scale : Float)

        fun onUp(e : MotionEvent)

        fun onLongPress(e : MotionEvent)
    }
}
