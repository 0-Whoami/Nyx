package com.termux.utils.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.termux.app.main
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TextStyle
import com.termux.utils.data.ConfigManager.CONFIG_PATH
import com.termux.utils.data.Properties
import com.termux.utils.data.RENDERING
import com.termux.view.Console
import com.termux.view.GestureAndScaleRecognizer
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class NavWindow(private val mActivity: main) {
    private var extraKeysAdded: Boolean = false
    val console: Console = mActivity.console

    private val extrakeys by lazy { Extrakeys(console) }

    private val navUi = GesturedView(mActivity)


    private val sessionPairs: List<buttonPref>
        get() {
            val pairs = mutableListOf<buttonPref>()
            mActivity.mNyxService.TerminalSessions.forEachIndexed { index, session ->
                pairs.add(buttonPref("${index + 1}") { console.attachSession(session) })
            }
            pairs.add(buttonPref("+") { mActivity.addNewSession(false) })
            pairs.add(buttonPref("+!") { mActivity.addNewSession(true) })
            return pairs
        }

    private val navigationPairs: List<buttonPref> by lazy {
        listOf(buttonPref("Scroll") { console.CURRENT_NAVIGATION_MODE = 0 },
            buttonPref("◀▶") { console.CURRENT_NAVIGATION_MODE = 1 },
            buttonPref("▲▼") { console.CURRENT_NAVIGATION_MODE = 2 },
            buttonPref("Keys") {
                if (extraKeysAdded) mActivity.frameLayout.removeView(extrakeys) else mActivity.frameLayout.addView(
                    extrakeys
                )
                extraKeysAdded = !extraKeysAdded
            },
            buttonPref("◳") {
                createPopupWindow(WindowManager(console))
            })

    }

    fun showModeMenu() {
        navUi.setData(navigationPairs)
        createPopupWindow(navUi)
    }

    fun showSessionChooser() {
        navUi.setData(sessionPairs)
        createPopupWindow(navUi)
    }


    private fun createPopupWindow(view: View) {
        mActivity.frameLayout.addView(view)
        view.requestFocus()
    }

}

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

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ) {
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

data class buttonPref(val text: String, val action: () -> Unit)

private const val primaryRadius = 70f

internal class GesturedView(context: Context) : View(context) {
    private var initialX = 0f
    private var halfHeight = 0f
    private var halfWidth = 0f
    private var angle = 0f
    private var a = 0f
    private var offset = 0f
    private var index: Int = 0
    private lateinit var pairs: List<buttonPref>
    private val pageNumber: Int
        get() = pairs.size
    private val paint = Paint().apply {
        typeface = RENDERING.typeface
        textSize = 35f
        textAlign = Paint.Align.CENTER
    }

    fun setData(data: List<buttonPref>) {
        pairs = data
        index = 0
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        halfHeight = h / 2f
        halfWidth = w / 2f
        angle = asin(20f / halfWidth)
        a = (halfWidth - 20)
        offset = (3.14f + angle * (pageNumber - 1)) / 2
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
        canvas.drawCircle(halfWidth, halfHeight, primaryRadius, paint)
        paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY]
        canvas.drawText(
            pairs[index].text, halfWidth, (halfHeight + paint.descent()), paint
        )
        paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
        for (i in pairs.indices) {
            paint.alpha = if (i == index) 255 else 100
            canvas.drawCircle(
                halfWidth + a * cos(offset - angle * i),
                halfWidth + a * sin(offset - angle * i),
                5f,
                paint
            )
        }
    }

    private fun swipeLeft() {
        if (index < pairs.size - 1) index++
    }

    private fun swipeRight() {
        if (index > 0) index--
    }

    private fun click(positionX: Float, positionY: Float) {
        (parent as ViewGroup).removeView(this)
        if (positionX in halfWidth - primaryRadius..halfWidth + primaryRadius && positionY in halfHeight - primaryRadius..halfHeight + primaryRadius) pairs[index].action()
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

class key(val label: String, val code: Int)

private const val buttonRadius = 25f

internal class Extrakeys(private val console: Console) : View(console.context) {
    private var a = 0f
    private val paint = Paint().apply {
        color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
        typeface = typeface
        textAlign = Paint.Align.CENTER
        textSize = 25f
    }
    private val buttonStateRefs = arrayOf(
        console::isControlKeydown, console::isReadAltKey, console::isReadShiftKey
    )
    private val offsetText = paint.descent()
    private val posMap = mutableMapOf<Float, Float>()

    private val normalKey = mutableListOf<key>()

    init {
        val properties = Properties("$CONFIG_PATH/keys")
        properties.forEach { it, value ->
            normalKey.add(key(it, value.toInt()))
        }
    }


    private val numButtons = buttonStateRefs.size + normalKey.size - 1
    private val label = arrayOf("C", "A", "S")

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
