package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.termux.view.Console

class Extrakeys(private val console: Console) : View(console.context) {
    private var buttonRadius = 18f
    private var touchRegionLength = 40
    private var spacing = 30f
    private val paint = Paint().apply {
        color = Color.WHITE
        typeface = console.mRenderer.mTypeface
        textAlign = Paint.Align.CENTER
    }
    private val buttonStateRefs = arrayOf(
        console::isControlKeydown,
        console::isReadAltKey,
        console::isReadShiftKey
    )
    private var centerX = 0f
    private var key_enabled = false

    private val normalKey = arrayOf(KeyEvent.KEYCODE_DEL)
    private val numButtons = buttonStateRefs.size + normalKey.size
    private val label = arrayOf("C", "A", "S", "⌫")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        touchRegionLength = MeasureSpec.getSize(widthMeasureSpec) / numButtons
        spacing =
            (MeasureSpec.getSize(widthMeasureSpec) - (2 * buttonRadius * numButtons)) / (numButtons + 1)
        setMeasuredDimension(
            widthMeasureSpec,
            (buttonRadius * 2).toInt() + 5
        )
    }

    override fun onDraw(canvas: Canvas) {
        centerX = spacing + buttonRadius
        for (i in 0 until numButtons) {
            key_enabled = i in buttonStateRefs.indices && buttonStateRefs[i].get()
            paint.color =
                if (key_enabled) Color.BLUE else Color.WHITE
            canvas.drawCircle(
                centerX,
                buttonRadius + 5,
                buttonRadius,
                paint
            )
            paint.color = if (key_enabled) Color.WHITE else Color.BLACK
            val a = (label.getOrNull(i) ?: "")
            canvas.drawText(
                a,
                centerX,
                buttonRadius + 10,
                paint
            )
            paint.color = Color.WHITE
            centerX += spacing + 2 * buttonRadius
        }
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            for (i in 0 until numButtons) {
                val rect = RectF(
                    i * touchRegionLength.toFloat(), 0f,
                    (i + 1f) * touchRegionLength, height.toFloat()
                )
                if (rect.contains(event.x, event.y)) {
                    if (i < buttonStateRefs.size)
                        buttonStateRefs[i].set(!buttonStateRefs[i].get())
                    else
                        console.dispatchKeyEvent(
                            KeyEvent(
                                KeyEvent.ACTION_DOWN,
                                normalKey[i - buttonStateRefs.size]
                            )
                        )
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
