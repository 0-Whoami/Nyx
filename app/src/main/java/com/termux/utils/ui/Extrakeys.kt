package com.termux.utils.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.termux.utils.data.ConfigManager
import com.termux.utils.data.ConfigManager.CONFIG_PATH
import com.termux.utils.data.Properties
import com.termux.utils.data.TerminalManager.console
import com.termux.utils.data.isPointInCircle
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

private class Key(val label: String, val code: Int)

private const val buttonRadius = 25f

class Extrakeys(context: Context) : View(context) {
    private var a = 0f
    val paint: Paint = Paint().apply {
        typeface = ConfigManager.typeface
        textSize = 35f
        textAlign = Paint.Align.CENTER
        color = colorPrimaryAccent
    }
    private val buttonStateRefs = arrayOf(
        console::isControlKeydown,
        console::isReadAltKey,
        console::isReadShiftKey,
        console::readFnKey
    )
    private val offsetText = paint.descent()
    private val posMap = mutableMapOf<Float, Float>()

    private val normalKey = mutableListOf<Key>()

    init {
        val properties = Properties("$CONFIG_PATH/keys")
        properties.forEach { it, value ->
            normalKey.add(Key(it, value.toInt()))
        }

        isFocusable = false
    }


    private val numButtons = buttonStateRefs.size + normalKey.size - 1
    private val label = arrayOf("C", "A", "S", "Fn")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        posMap.clear()
        val centerX = w / 2
        val angle = asin(buttonRadius * 2 / centerX) + (3.14f / 45)
        var centeringOffset = 3.14f / 2 + angle * numButtons / 2
        a = (centerX - (buttonRadius + 5))
        for (i in 0..numButtons) {
            posMap[(centerX + a * cos(centeringOffset))] = centerX + a * sin(centeringOffset)
            centeringOffset -= angle
        }
    }


    override fun onDraw(canvas: Canvas) {
        var n = 0
        for ((key, value) in posMap) {
            paint.color =
                if (n < buttonStateRefs.size && buttonStateRefs[n].get()) colorPrimaryAccent else surface
            canvas.drawCircle(
                key, value, buttonRadius, paint
            )
            paint.color = primaryTextColor
            val text =
                if (n < buttonStateRefs.size) label[n] else normalKey[n - buttonStateRefs.size].label
            canvas.drawText(text, key, value + offsetText, paint)
            n++
        }
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        var n = 0
        if (event.action == MotionEvent.ACTION_DOWN && !isPointInCircle(
                width / 2f, height / 2f, (a - buttonRadius), x, y
            )
        ) {
            for ((key, value) in posMap) {
                if (isPointInCircle(
                        key, value, buttonRadius, x, y
                    )
                ) {
                    if (n < buttonStateRefs.size) buttonStateRefs[n].set(!buttonStateRefs[n].get())
                    else console.dispatchKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN, normalKey[n - buttonStateRefs.size].code
                        )
                    )
                    invalidate()
                    return true
                }
                n++
            }
        }
        return false
    }
}
