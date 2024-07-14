package com.termux.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.termux.data.ConfigManager
import com.termux.data.ConfigManager.CONFIG_PATH
import com.termux.data.Properties
import com.termux.data.console
import com.termux.data.inCircle
import com.termux.terminal.SessionManager.sessions
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class Button(context: Context, attr: AttributeSet? = null) : View(context, attr) {
    private var check: Boolean = true

    fun setCheck(value: Boolean) {
        check = value
        invalidate()
    }

    private val text =
        attr?.getAttributeValue("http://schemas.android.com/apk/res/android", "text") ?: ""

    fun toogle() = setCheck(!check)
    override fun onDraw(canvas: Canvas) {
        drawRoundedBg(canvas, if (check) primary else secondary, 50)
        paint.color = getContrastColor(paint.color)
        canvas.drawText(text, width / 2f, height / 2f + paint.descent(), paint)
    }
}

class Layout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    init {
        background = GradientDrawable().apply { setColor(secondary) }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        (background as GradientDrawable).cornerRadius = h / 4f
    }
}

val paint by lazy {
    Paint().apply {
        typeface = ConfigManager.typeface
        textSize = 23f
        textAlign = Paint.Align.CENTER
        color = primary
    }
}

open class SessionViw(context: Context, attr: AttributeSet? = null) : View(context, attr) {
    open val list: List<Any> get() = sessions
    open fun text(i: Int): String = "${i + 1}"
    open fun enable(i: Int) = sessions[i] == console.currentSession
    open fun onClick(i: Int) = console.attachSession(i)
    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                circles { i, cx ->
                    if (inCircle(cx, h2, r, e.x, e.y)) {
                        onClick(i)
                        console.invalidate()
                        invalidate()
                    }
                }
                return true
            }
        })
    private var h2 = 0f
    private var r = 0f
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec) + 20
        setMeasuredDimension(h * list.size, h)
        h2 = h / 2f
        r = h2 - 10
    }

    override fun onDraw(canvas: Canvas) {
        circles { i, cx ->
            paint.color = if (enable(i)) primary else 0
            canvas.drawCircle(cx, h2, r, paint)
            paint.color = getContrastColor(paint.color)
            canvas.drawText(text(i), cx, h2 + paint.descent(), paint)
        }
    }

    private fun circles(action: (Int, Float) -> Unit) {
        for (i in list.indices) {
            action(i, (2 * i + 1f) * h2)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }
}

class Rotary(context: Context, attr: AttributeSet? = null) : SessionViw(context, attr) {
    override val list: List<Any> = listOf("⇅", "◀▶", "▲▼")
    override fun text(i: Int): String = list[i] as String
    override fun enable(i: Int): Boolean = i == console.RotaryMode
    override fun onClick(i: Int) {
        console.RotaryMode = i
    }

    override fun onDraw(canvas: Canvas) {
        drawRoundedBg(canvas, secondary, 50)
        super.onDraw(canvas)
    }
}

private fun drawRoundedBg(canvas: Canvas, color: Int, radius: Int) {
    paint.color = color
    val h = canvas.height.toFloat()
    val rx = h * radius / 100
    canvas.drawRoundRect(0f, 0f, canvas.width.toFloat(), h, rx, rx, paint)
}

private const val buttonRadius = 25f

class Extrakeys : View(console.context) {
    private var a = 0f
    private val offsetText = paint.descent()

    private val keys = arrayOf("⇪", "C", "A", "Fn").map { Key(it, 0) }.toMutableSet().apply {
        Properties("$CONFIG_PATH/keys").forEach { it, value ->
            add(Key(it, value.toInt()))
        }
    }

    init {
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val centerX = w / 2
        val numButtons = keys.size
        val angle = asin(buttonRadius * 2 / centerX) + 0.07f
        var centeringOffset = 3.14f / 2 + angle * (numButtons - 1) / 2
        a = (centerX - (buttonRadius + 5))
        for (i in keys) {
            i.x = centerX + a * cos(centeringOffset)
            i.y = centerX + a * sin(centeringOffset)
            centeringOffset -= angle
        }
    }


    override fun onDraw(canvas: Canvas) {
        for ((n, i) in keys.withIndex()) {
            paint.color = if (n < 4 && console.metaKeys[n]) primary else secondary
            canvas.drawCircle(
                i.x, i.y, buttonRadius, paint
            )
            paint.color = getContrastColor(paint.color)
            canvas.drawText(i.label, i.x, i.y + offsetText, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        if (event.action == MotionEvent.ACTION_DOWN && !inCircle(
                width / 2f, height / 2f, (a - buttonRadius), x, y
            )
        ) {
            for ((n, i) in keys.withIndex()) {
                if (inCircle(i.x, i.y, buttonRadius, x, y)) {
                    if (n < 4) console.metaKeys[n] = !console.metaKeys[n]
                    else console.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, i.code))
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    private class Key(val label: String, val code: Int, var x: Float = 0f, var y: Float = 0f)

}

class WindowManager : View(console.context) {
    var factor: Float = 1f
    private val rect = RectF()
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
        if (w != 0 || h != 0) rect.set(w * .25f, h - 85f, w * .75f, h - 15f)
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
        console.layoutParams = console.layoutParams.apply { height = newHeight;width = newHeight }

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
