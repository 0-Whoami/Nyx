package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.ViewGroup
import com.termux.utils.data.ConfigManager
import com.termux.utils.data.TerminalManager.console
import kotlin.math.roundToInt

class WindowManager : View(console.context) {
    var factor: Float = 1f
    private val rect = RectF()
    private val paint: Paint = Paint().apply {
        typeface = ConfigManager.typeface
        textSize = 35f
        textAlign = Paint.Align.CENTER
        color = primary
    }
    private val detector = ScaleGestureDetector(context, object : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            factor *= detector.scaleFactor
            changeSize()
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        detector.isQuickScaleEnabled = true
    }

    private val sizeRef = console.height

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(w * .25f, h - 85f, w * .75f, h - 15f)
    }

    private var dX = 0f
    private var dY = 0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        if (detector.isInProgress) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = console.x - event.rawX
                dY = console.y - event.rawY
                if (rect.contains(event.x, event.y)) {
                    (parent as ViewGroup).removeView(this@WindowManager)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                console.x = (event.rawX + dX)
                console.y = (event.rawY + dY)
            }
        }
        return true
    }

    fun changeSize() {
        val newHeight = (sizeRef * factor).roundToInt()
        val attr = console.layoutParams
        attr.height = newHeight
        attr.width = newHeight
        console.layoutParams = attr
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
        paint.color = primary
        canvas.drawRoundRect(rect, 35f, 35f, paint)
        paint.color = getContrastColor(primary)
        canvas.drawText("Apply", rect.centerX(), rect.centerY() + paint.descent(), paint)
    }
}
