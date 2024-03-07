package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.termux.app.main
import kotlin.math.abs
import kotlin.math.roundToInt

open class GesturedView(val context: main) : View(context) {
    private var initialX = 0f
    private var initialY = 0f
    private var halfHeight = 0f
    private var halfWidth = 0f
    private val primaryRadius = 70f

    open fun pairs(): List<Pair<String, () -> Unit>> = listOf<Pair<String, () -> Unit>>()
    var index: Int = 0
    open fun pageNumber(): Int = pairs().size
    private val paint = Paint().apply {
        color = Color.WHITE
        typeface = context.console.mRenderer.mTypeface
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        halfHeight = h / 2f
        halfWidth = w / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.WHITE
        canvas.drawCircle(halfWidth, halfHeight, primaryRadius, paint)
        paint.color = Color.BLACK
        canvas.drawText(
            pairs()[index].first,
            halfWidth,
            (halfHeight - ((paint.descent() + paint.ascent()) / 2)),
            paint
        )
        paint.color = Color.WHITE
        for (i in 0..<pageNumber()) {
            paint.alpha = if (i == index) 255 else 100
            canvas.drawCircle(
                halfWidth - 15 * pairs().size / 2 + 15 * i, height - 20f, 5f, paint
            )
        }
    }

    open fun swipeLeft() {
        if (index < pairs().size - 1) index++
    }

    open fun swipeRight() {
        if (index > 0) index--
    }

    open fun click(positionX: Float, positionY: Float) {
        if (positionX in halfWidth - primaryRadius..halfWidth + primaryRadius && positionY in halfHeight - primaryRadius..halfHeight + primaryRadius) pairs()[index].second()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            initialX = event.x
            initialY = event.y
        }
        if (event.action == MotionEvent.ACTION_UP) {
            val deltaX = event.x - initialX
            if (abs(deltaX) > 100) {
                if (deltaX > 0) swipeRight()
                else swipeLeft()
            } else {
                click(initialX, initialY)
            }
        }
        invalidate()
        context.console.invalidate()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            if (-event.getAxisValue(MotionEvent.AXIS_SCROLL) > 0) swipeRight()
            else swipeLeft()
            invalidate()
        }
        return true
    }
}

class NavWindow(val mActivity: main) {
    var extaKeysAdded: Boolean = false
    private lateinit var popupWindow: PopupWindow
    private val sessionList by lazy {
        object : GesturedView(mActivity) {
            override fun pairs() = listOf((currentSessionIndex() + 1).toString() to {}, "+" to {
                mActivity.mService.mTermuxTerminalSessionActivityClient.addNewSession(
                    false
                )
            })

            override fun swipeLeft() {
                val nextSessionIndex = currentSessionIndex() + 1
                if (nextSessionIndex < mActivity.mService.TerminalSessions.size) {
                    mActivity.mService.mTermuxTerminalSessionActivityClient.setCurrentSession(
                        mActivity.mService.TerminalSessions[nextSessionIndex]
                    )
                } else index = 1
            }

            override fun swipeRight() {
                if (index > 0) {
                    index = 0
                    return
                }
                val nextSessionIndex = currentSessionIndex() - 1
                if (nextSessionIndex >= 0) mActivity.mService.mTermuxTerminalSessionActivityClient.setCurrentSession(
                    mActivity.mService.TerminalSessions[nextSessionIndex]
                )
            }

            override fun click(positionX: Float, positionY: Float) {
                dismiss()
                super.click(positionX, positionY)
            }

            fun currentSessionIndex() =
                mActivity.mService.getIndexOfSession(mActivity.console.currentSession)

            override fun pageNumber() = pairs().size + mActivity.mService.TerminalSessions.size - 1
        }

    }
    private val windowMan by lazy {
        object : View(mActivity) {
            var factor = 1f

            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            val sizeRef = mActivity.blur.height
            val detector = ScaleGestureDetector(
                mActivity,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        factor *= detector.scaleFactor
                        changeSize()
                        return true
                    }
                })

            override fun onTouchEvent(event: MotionEvent): Boolean {
                moveOnTouchEvent(mActivity.blur, event)
                detector.onTouchEvent(event)
                mActivity.console.invalidate()
                return true
            }

            fun changeSize() {
                val newHeight = (sizeRef * factor).roundToInt()
                val attr = FrameLayout.LayoutParams(newHeight, newHeight)
                mActivity.blur.layoutParams = attr
            }

            override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    factor *= if (-event.getAxisValue(MotionEvent.AXIS_SCROLL) > 0) 0.95f
                    else 1.05f
                    changeSize()
                }
                return true
            }
        }
    }
    private val configurationMenu by lazy {
        object : GesturedView(mActivity) {
            val extrakeys by lazy { Extrakeys(mActivity.console) }
            override fun pairs() = listOf("⊻" to { mActivity.console.CURRENT_NAVIGATION_MODE = 0 },
                "◀▶" to { mActivity.console.CURRENT_NAVIGATION_MODE = 1 },
                "▲▼" to { mActivity.console.CURRENT_NAVIGATION_MODE = 2 },
                "Keys" to {
                    if (extaKeysAdded) mActivity.blur.removeView(extrakeys) else mActivity.blur.addView(
                        extrakeys
                    )
                    extaKeysAdded = !extaKeysAdded
                },
                "◳" to {
                    createPopupWindow(windowMan)
                    showPopup()
                })

            override fun click(positionX: Float, positionY: Float) {
                dismiss()
                super.click(positionX, positionY)
            }
        }
    }


    fun showModeMenu() {
        createPopupWindow(configurationMenu)
        showPopup()
    }

    fun dismiss() {
        popupWindow.dismiss()
        mActivity.console.requestFocus()
    }

    private fun showPopup() =
        popupWindow.showAtLocation(mActivity.console, Gravity.CENTER, 0, 0).also {
            popupWindow.contentView.requestFocus()
        }

    fun showSessionChooser() {
        createPopupWindow(sessionList)
        showPopup()
    }

    private fun createPopupWindow(view: View) {
        popupWindow = PopupWindow(
            view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true
        )
    }

    private var dX = 0f
    private var dY = 0f
    fun moveOnTouchEvent(view: View, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = view.x - event.rawX
                dY = view.y - event.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                view.x = (event.rawX + dX)
                view.y = (event.rawY + dY)
            }
        }
    }
}

