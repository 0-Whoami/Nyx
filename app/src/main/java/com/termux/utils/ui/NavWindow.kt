package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.termux.app.main
import kotlin.math.abs

class NavWindow(val mActivity: main) {
    var num = 0
    var isNav = false
    var isSession = false

    val windowView by lazy {
        object : View(mActivity) {
            var onNumChanged: ((Int) -> Unit)? = null
            private val minSwipe = 200f
            private var startX = 0f
            private val paint = Paint().apply {
                color = Color.WHITE
            }

            override fun isOpaque(): Boolean = true
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> startX = e.x
                    MotionEvent.ACTION_MOVE -> {
                        val delta = e.x - startX
                        if (abs(delta) > minSwipe) {
                            changeNum(delta > 0)
                            startX = e.x
                        }
                    }
                }
                return true
            }

            override fun onDraw(c: Canvas) {
                super.onDraw(c)
                c.drawCircle(width / 2f, height - 20f, 20f, paint)
                paint.color = Color.BLACK
                c.drawText("${value()}", width / 2f, height - 20f, paint)
                paint.color = Color.WHITE
            }

            override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                if (MotionEvent.ACTION_SCROLL == event.action &&
                    event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
                ) {
                    val delta = 0 > -event.getAxisValue(MotionEvent.AXIS_SCROLL)
                    changeNum(delta)
                }
                return true
            }

            fun changeNum(decrease: Boolean) {
                num += if (decrease) -1 else 1
                invalidate()
                onNumChanged?.invoke(value())
            }
        }
    }

    fun value() = abs(num)
    fun show() {
        mActivity.blur.addView(Extrakeys(mActivity.con))
//        windowView.focusable = View.FOCUSABLE
//        windowView.requestFocus()
    }

    private var dX = 0f
    private var dY = 0f
    fun moveOnTouchEvent(view: View, event: MotionEvent): Boolean {
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
        return true
    }
}

