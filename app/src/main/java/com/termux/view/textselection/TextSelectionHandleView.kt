package com.termux.view.textselection

import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.view.WindowManager
import android.widget.PopupWindow
import com.termux.R
import com.termux.view.TerminalView

class TextSelectionHandleView(
    private val terminalView: TerminalView,
    private val mCursorController: TextSelectionCursorController
) : View(
    terminalView.context
) {
    private val mTempCoords = IntArray(2)
    private val mHandleDrawable = context.getDrawable(R.drawable.text_select_handle_material)
    private var mTempRect: Rect = Rect()
    private var mHandle: PopupWindow? = null
    private var mIsDragging = false
    private var mPointX = 0
    private var mPointY = 0
    private var mTouchToWindowOffsetX = 0f
    private var mTouchToWindowOffsetY = 0f
    private var mHotspotX = 0f
    private var mHotspotY = 0f
    private var mTouchOffsetY = 0f
    private var mLastParentX = 0
    private var mLastParentY = 0
    var handleHeight: Int = 0
        private set
    private var mLastTime: Long = 0

    init {
        setOrientation()
    }

    private fun initHandle() {
        mHandle =
            PopupWindow(terminalView.context, null, android.R.attr.textSelectHandleWindowStyle)
        mHandle!!.isSplitTouchEnabled = true
        mHandle!!.isClippingEnabled = false
        mHandle!!.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mHandle!!.height = ViewGroup.LayoutParams.WRAP_CONTENT
        mHandle!!.setBackgroundDrawable(null)
        mHandle!!.animationStyle = 0
        mHandle!!.windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
        mHandle!!.enterTransition = null
        mHandle!!.exitTransition = null
        mHandle!!.contentView = this
    }

    private fun setOrientation() {
        val handleWidth = mHandleDrawable!!.intrinsicWidth
        mHotspotX = handleWidth / 4.0f
        handleHeight = mHandleDrawable.intrinsicHeight
        mTouchOffsetY = -handleHeight * 0.3f
        mHotspotY = 0f
        invalidate()
    }

    private fun show() {
        if (!isPositionVisible) {
            hide()
            return
        }
        // We remove handle from its parent first otherwise the following exception may be thrown
        // java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        removeFromParent()
        // init the handle
        initHandle()
        // invalidate to make sure onDraw is called
        invalidate()
        val coords = mTempCoords
        terminalView.getLocationInWindow(coords)
        coords[0] += mPointX
        coords[1] += mPointY
        if (null != this.mHandle) mHandle!!.showAtLocation(terminalView, 0, coords[0], coords[1])
    }

    fun hide() {
        mIsDragging = false
        if (null != this.mHandle) {
            mHandle!!.dismiss()
            // We remove handle from its parent, otherwise it may still be shown in some cases even after the dismiss call
            removeFromParent()
            // garbage collect the handle
            mHandle = null
        }
        invalidate()
    }

    private fun removeFromParent() {
        if (!isParentNull) {
            (this.parent as ViewManager).removeView(this)
        }
    }

    fun positionAtCursor(cx: Int, cy: Int, forceOrientationCheck: Boolean) {
        val x = terminalView.getPointX(cx)
        val y = terminalView.getPointY(cy + 1)
        moveTo(x, y, forceOrientationCheck)
    }

    private fun moveTo(x: Int, y: Int, forceOrientationCheck: Boolean) {
        mPointX = (x - mHotspotX).toInt()
        mPointY = y
        checkChangedOrientation(forceOrientationCheck)
        if (isPositionVisible) {
            var coords: IntArray? = null
            if (isShowing) {
                coords = mTempCoords
                terminalView.getLocationInWindow(coords)
                val x1 = coords[0] + mPointX
                val y1 = coords[1] + mPointY
                if (null != this.mHandle) mHandle!!.update(x1, y1, width, height)
            } else {
                show()
            }
            if (mIsDragging) {
                if (null == coords) {
                    coords = mTempCoords
                    terminalView.getLocationInWindow(coords)
                }
                if (coords[0] != mLastParentX || coords[1] != mLastParentY) {
                    mTouchToWindowOffsetX += (coords[0] - mLastParentX).toFloat()
                    mTouchToWindowOffsetY += (coords[1] - mLastParentY).toFloat()
                    mLastParentX = coords[0]
                    mLastParentY = coords[1]
                }
            }
        } else {
            hide()
        }
    }

    private fun checkChangedOrientation(force: Boolean) {
        if (!mIsDragging && !force) {
            return
        }
        val millis = SystemClock.currentThreadTimeMillis()
        if (50 > millis - this.mLastTime && !force) {
            return
        }
        mLastTime = millis
        val hostView = terminalView
        val left = hostView.left
        val right = hostView.width
        val top = hostView.top
        val bottom = hostView.height
        val clip = mTempRect
        clip.left = left + terminalView.paddingLeft
        clip.top = top + terminalView.paddingTop
        clip.right = right - terminalView.paddingRight
        clip.bottom = bottom - terminalView.paddingBottom
        val parent = hostView.parent
        parent?.getChildVisibleRect(hostView, clip, null)
    }

    private val isPositionVisible: Boolean
        get() {
            // Always show a dragging handle.
            if (mIsDragging) {
                return true
            }
            val hostView = terminalView
            val left = 0
            val right = hostView.width
            val top = 0
            val bottom = hostView.height
            val clip: Rect = mTempRect
            clip.left = left + terminalView.paddingLeft
            clip.top = top + terminalView.paddingTop
            clip.right = right - terminalView.paddingRight
            clip.bottom = bottom - terminalView.paddingBottom
            val parent = hostView.parent
            if (null == parent || !parent.getChildVisibleRect(hostView, clip, null)) {
                return false
            }
            val coords = mTempCoords
            hostView.getLocationInWindow(coords)
            val posX = coords[0] + mPointX + mHotspotX.toInt()
            val posY = coords[1] + mPointY + mHotspotY.toInt()
            return posX >= clip.left && posX <= clip.right && posY >= clip.top && posY <= clip.bottom
        }

    public override fun onDraw(c: Canvas) {
        val width = mHandleDrawable!!.intrinsicWidth
        val height = mHandleDrawable.intrinsicHeight
        mHandleDrawable.setBounds(0, 0, width, height)
        mHandleDrawable.draw(c)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        terminalView.updateFloatingToolbarVisibility(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rawX = event.rawX
                val rawY = event.rawY
                mTouchToWindowOffsetX = rawX - mPointX
                mTouchToWindowOffsetY = rawY - mPointY
                val coords = mTempCoords
                terminalView.getLocationInWindow(coords)
                mLastParentX = coords[0]
                mLastParentY = coords[1]
                mIsDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawX = event.rawX
                val rawY = event.rawY
                val newPosX = rawX - mTouchToWindowOffsetX + mHotspotX
                val newPosY = rawY - mTouchToWindowOffsetY + mHotspotY + mTouchOffsetY
                mCursorController.updatePosition(this, Math.round(newPosX), Math.round(newPosY))
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mIsDragging = false
        }
        return true
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(mHandleDrawable!!.intrinsicWidth, mHandleDrawable.intrinsicHeight)
    }

    private val isShowing: Boolean
        get() = if (null != this.mHandle) mHandle!!.isShowing
        else false

    private val isParentNull: Boolean
        get() = null == parent
}
