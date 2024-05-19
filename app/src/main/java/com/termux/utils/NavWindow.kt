package com.termux.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TextStyle
import com.termux.utils.TerminalManager.TerminalSessions
import com.termux.utils.TerminalManager.addNewSession
import com.termux.utils.TerminalManager.console
import com.termux.utils.data.ConfigManager.CONFIG_PATH
import com.termux.utils.data.Properties
import com.termux.utils.data.RENDERING
import com.termux.view.GestureAndScaleRecognizer
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


internal class WindowManager(val view: View) : View(view.context) {
    var factor = 1f
    private val rect = RectF()
    private val mPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f
        typeface = RENDERING.typeface
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        alpha = 0.8f
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
        mPaint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
        canvas.drawRoundRect(rect, 35f, 35f, mPaint)
        mPaint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY]
        canvas.drawText("Apply", rect.centerX(), rect.centerY() + mPaint.descent(), mPaint)

    }
}

class ButtonPref(val text: String, val description: Short, val action: () -> Unit)

private const val radius = 85f

class GesturedView(context: Context, attributeSet: AttributeSet?) : View(context, attributeSet) {
    private var extraKeysAdded: Boolean = false
    private val extrakeys by lazy { Extrakeys(context) }
    private var initialX = 0f
    private var halfHeight = 0f
    private var halfWidth = 0f

    private var index: Int = 2
    private lateinit var parentGroup: ViewGroup
    private val pairs: List<ButtonPref>
        get() {
            val pairs = mutableListOf<ButtonPref>()
            pairs.add(ButtonPref("Failsafe", 1) { addNewSession(true) })
            pairs.add(ButtonPref("+Add", 1) { addNewSession(false) })
            TerminalSessions.forEachIndexed { index, session ->
                pairs.add(ButtonPref("${index + 1}", 0) { console.attachSession(session) })
            }
            pairs.add(ButtonPref("Scroll", 2) { console.CURRENT_NAVIGATION_MODE = 0 })
            pairs.add(ButtonPref("◀▶", 2) { console.CURRENT_NAVIGATION_MODE = 1 })
            pairs.add(ButtonPref("▲▼", 2) { console.CURRENT_NAVIGATION_MODE = 2 })
            pairs.add(ButtonPref("Keys", 3) {
                if (extraKeysAdded) parentGroup.removeView(extrakeys) else parentGroup.addView(
                    extrakeys
                )
                extraKeysAdded = !extraKeysAdded
            })
            pairs.add(ButtonPref("◳", 3) {
                createPopupWindow(WindowManager(console))
            })
            return pairs
        }

    private val paint = Paint().apply {
        typeface = RENDERING.typeface
        textSize = 30f
        textAlign = Paint.Align.CENTER
        color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
        strokeWidth = 2f
    }

    init {
        alpha = 0.8f
    }

    fun toogleVisibility() {
        if (this.visibility == VISIBLE) {
            visibility = GONE
            console.requestFocus()
        } else {
            visibility = VISIBLE
            requestFocus()
        }
    }

    private fun createPopupWindow(view: View) {
        parentGroup.addView(view)
        view.requestFocus()
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        halfHeight = h / 2f
        halfWidth = w / 2f
        parentGroup = parent as ViewGroup
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.STROKE
        canvas.drawColor(TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY])
        canvas.drawCircle(halfWidth, halfHeight, radius, paint)
        val c = (halfWidth - 4.5f * pairs.size)
        for (i in pairs.indices) {
            canvas.drawCircle(
                c + 9 * i, halfHeight + radius + 70, if (i == index) 4f else 2f, paint
            )
        }
        paint.style = Paint.Style.FILL
        canvas.drawText(
            when (pairs[index].description) {
                0.toShort() -> "Sessions";1.toShort() -> "New Session";2.toShort() -> "Rotary";else -> "Controls"
            }, halfWidth, halfHeight + radius + 40, paint
        )
        canvas.drawText(
            pairs[index].text, halfWidth, (halfHeight + paint.descent()), paint
        )
    }

    private fun swipeLeft() {
        if (index < pairs.size - 1) index++
    }

    private fun swipeRight() {
        if (index > 0) index--
    }

    private fun click(positionX: Float, positionY: Float) {
        toogleVisibility()
        if (positionX in halfWidth - radius..halfWidth + radius && positionY in halfHeight - radius..halfHeight + radius) pairs[index].action()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            initialX = event.x
        }
        if (event.action == MotionEvent.ACTION_UP) {
            val deltaX = event.x - initialX
            if (abs(deltaX) > 100) {
                if (deltaX > 0) swipeRight()
                else swipeLeft()
            } else {
                click(initialX, event.y)
            }
        }
        invalidate()
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

class Key(val label: String, val code: Int)

private const val buttonRadius = 25f

internal class Extrakeys(context: Context) : View(context) {
    private var a = 0f
    private val paint = Paint().apply {
        color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
        typeface = typeface
        textAlign = Paint.Align.CENTER
        textSize = 25f
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
                if (n < buttonStateRefs.size && buttonStateRefs[n].get()) TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY] else TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
            canvas.drawCircle(
                key, value, buttonRadius, paint
            )
            paint.color =
                if (n < buttonStateRefs.size && buttonStateRefs[n].get()) TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY] else TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY]
            val text =
                if (n < buttonStateRefs.size) label[n] else normalKey[n - buttonStateRefs.size].label
            canvas.drawText(text, key, value + offsetText, paint)
            n++
        }
        super.onDraw(canvas)
    }

    private fun isPointInCircle(
        centerX: Float, centerY: Float, radius: Float, pointX: Float, pointY: Float
    ): Boolean {
        return (pointX - centerX) * (pointX - centerX) + (pointY - centerY) * (pointY - centerY) <= radius * radius
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
