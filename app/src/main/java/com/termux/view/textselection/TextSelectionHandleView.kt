package com.termux.view.textselection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewManager
import android.widget.PopupWindow
import com.termux.view.Console

class TextSelectionHandleView(
    private val console: Console, private val mCursorController: TextSelectionCursorController
) : View(
    console.context
) {
    private val paint = Paint().apply { color = Color.WHITE }
    private val mTempCoords = IntArray(2)
    private val mHandle = PopupWindow(this, 40, 40)
    private var mIsDragging = false
    private var mPointX = 0
    private var mPointY = 0
    private var mTouchToWindowOffsetX = 0f
    private var mTouchToWindowOffsetY = 0f
    private var mHotspotX = 10f
    private var mTouchOffsetY = -12f
    private var mLastParentX = 0
    private var mLastParentY = 0

    private fun show() {
        // We remove handle from its parent first otherwise the following exception may be thrown
        // java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        removeFromParent()
        // invalidate to make sure onDraw is called
        invalidate()
        val coords = mTempCoords
        console.getLocationInWindow(coords)
        coords[0] += mPointX
        coords[1] += mPointY
        mHandle.showAtLocation(console, 0, coords[0], coords[1])
    }

    fun hide() {
        mIsDragging = false
        mHandle.dismiss()
        // We remove handle from its parent, otherwise it may still be shown in some cases even after the dismiss call
        removeFromParent()
        invalidate()
    }

    private fun removeFromParent() {
        if (!isParentNull) {
            (this.parent as ViewManager).removeView(this)
        }
    }

    fun positionAtCursor(cx: Int, cy: Int) {
        val x = console.getPointX(cx)
        val y = console.getPointY(cy + 1)
        moveTo(x, y)
    }

    private fun moveTo(x: Int, y: Int) {
        mPointX = (x - mHotspotX).toInt()
        mPointY = y
        var coords: IntArray? = null
        if (isShowing) {
            coords = mTempCoords
            console.getLocationInWindow(coords)
            val x1 = coords[0] + mPointX
            val y1 = coords[1] + mPointY
            mHandle.update(x1, y1, width, height)
        } else {
            show()
        }
        if (mIsDragging) {
            if (null == coords) {
                coords = mTempCoords
                console.getLocationInWindow(coords)
            }
            if (coords[0] != mLastParentX || coords[1] != mLastParentY) {
                mTouchToWindowOffsetX += (coords[0] - mLastParentX).toFloat()
                mTouchToWindowOffsetY += (coords[1] - mLastParentY).toFloat()
                mLastParentX = coords[0]
                mLastParentY = coords[1]
            }
        }
    }

    public override fun onDraw(c: Canvas) {
        c.drawCircle(20f, 20f, 20f, paint)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rawX = event.rawX
                val rawY = event.rawY
                mTouchToWindowOffsetX = rawX - mPointX
                mTouchToWindowOffsetY = rawY - mPointY
                val coords = mTempCoords
                console.getLocationInWindow(coords)
                mLastParentX = coords[0]
                mLastParentY = coords[1]
                mIsDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawX = event.rawX
                val rawY = event.rawY
                val newPosX = rawX - mTouchToWindowOffsetX + mHotspotX
                val newPosY = rawY - mTouchToWindowOffsetY + mTouchOffsetY
                mCursorController.updatePosition(this, Math.round(newPosX), Math.round(newPosY))
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mIsDragging = false
        }
        return true
    }

    private val isShowing: Boolean
        get() = mHandle.isShowing

    private val isParentNull: Boolean
        get() = null == parent
}
