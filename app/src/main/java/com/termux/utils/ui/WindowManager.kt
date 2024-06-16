package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.termux.utils.data.ConfigManager
import com.termux.view.GestureAndScaleRecognizer
import kotlin.math.roundToInt

class WindowManager(val view: View) : View(view.context) {
    var factor: Float = 1f
    private val rect = RectF()
    val paint: Paint = Paint().apply {
        typeface = ConfigManager.typeface
        textSize = 35f
        textAlign = Paint.Align.CENTER
        color = colorPrimaryAccent
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private val sizeRef = view.height
    private val detector =
        GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
            override fun onSingleTapUp(e: MotionEvent) {
                if (rect.contains(e.x, e.y)) {
                    (parent as ViewGroup).removeView(this@WindowManager)
                }
            }

            override fun onScroll(e2: MotionEvent, dy: Float) {
            }

            override fun onFling(e2: MotionEvent, velocityY: Float) {
            }


            override fun onScale(scale: Float) {
                factor *= scale
                changeSize()
            }

            override fun onUp(e: MotionEvent) {
            }

            override fun onLongPress(e: MotionEvent) {
            }

        })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(w / 2 - 70f, h - 85f, w / 2 + 70f, h - 15f)
    }

    private var dX = 0f
    private var dY = 0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
        detector.onTouchEvent(event)
        view.invalidate()
        return true
    }

    fun changeSize() {
        val newHeight = (sizeRef * factor).roundToInt()
        val attr = view.layoutParams
        attr.height = newHeight
        attr.width = newHeight
        view.layoutParams = attr
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            factor *= if (-event.getAxisValue(MotionEvent.AXIS_SCROLL) > 0) 0.95f
            else 1.05f
            changeSize()
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        paint.color = colorPrimaryAccent
        canvas.drawRoundRect(rect, 35f, 35f, paint)
        paint.color = primaryTextColor
        canvas.drawText("Apply", rect.centerX(), rect.centerY() + paint.descent(), paint)

    }
}
