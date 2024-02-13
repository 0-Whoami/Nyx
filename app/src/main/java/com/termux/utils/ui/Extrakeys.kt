package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.termux.view.Screen

class Extrakeys(private val screen: Screen) : View(screen.context) {
    private var buttonRadius = 18f
    private var touchRegionLength = 40
    private val paint = Paint().apply {
        color = Color.WHITE
        typeface = screen.mRenderer.mTypeface
    }
    private val buttonStateRefs = arrayOf(
        screen::isControlKeydown,
        screen::isReadAltKey,
        screen::isReadShiftKey
    )

    private val normalKey = arrayOf(KeyEvent.KEYCODE_DEL)
    private val numButtons = buttonStateRefs.size + normalKey.size
    private val label = arrayOf("C", "A", "S", "⌫")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        touchRegionLength = MeasureSpec.getSize(widthMeasureSpec) / numButtons
        setMeasuredDimension(
            widthMeasureSpec,
            (buttonRadius * 2).toInt() + 5
        )
    }

    override fun onDraw(canvas: Canvas) {
        for (i in 0 until numButtons) {
            val centerX =
                (i + .5f) * (touchRegionLength)
            val enabled = i in buttonStateRefs.indices && buttonStateRefs[i].get()
            paint.color =
                if (enabled) Color.RED else Color.WHITE
            canvas.drawCircle(
                centerX,
                buttonRadius,
                buttonRadius,
                paint
            )
            paint.style = Paint.Style.FILL
            paint.color = if (enabled) Color.WHITE else Color.BLACK
            val a = (label.getOrNull(i) ?: "")
            canvas.drawText(
                a,
                centerX - paint.measureText(a) / 2,
                buttonRadius + paint.textSize / 2,
                paint
            )
            paint.color = Color.WHITE
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
                        screen.dispatchKeyEvent(
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
