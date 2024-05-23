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
import com.termux.utils.TerminalManager.TerminalSessions
import com.termux.utils.TerminalManager.addNewSession
import com.termux.utils.TerminalManager.console
import com.termux.utils.TerminalManager.removeFinishedSession
import com.termux.utils.data.ConfigManager.CONFIG_PATH
import com.termux.utils.data.Properties
import com.termux.utils.data.RENDERING
import com.termux.view.GestureAndScaleRecognizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin


internal class WindowManager(val view: View) : View(view.context) {
    var factor = 1f
    private val rect = RectF()

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
        paint.color = colorPrimaryAccent
        canvas.drawRoundRect(rect, 35f, 35f, paint)
        paint.color = primaryTextColor
        canvas.drawText("Apply", rect.centerX(), rect.centerY() + paint.descent(), paint)

    }
}

class ButtonPref(
    val text: String, var cx: Float = 0f, var cy: Float = 0f, val action: () -> Unit
)

private val paint = Paint().apply {
    typeface = RENDERING.typeface
    textSize = 35f
    textAlign = Paint.Align.CENTER
    color = colorPrimaryAccent
}
private const val colorPrimaryAccent = 0xff729fcf.toInt()
private const val primaryTextColor = 0xffd3d7cf.toInt()
private const val surface = 0xff1a1a1a.toInt()
private const val secondaryText = 0xff888a85.toInt()
const val numOfButtonInline: Int = 3

class GesturedView(context: Context, attributeSet: AttributeSet?) : View(context, attributeSet) {
    private var extraKeysAdded: Boolean = false
    private val extrakeys by lazy { Extrakeys(context) }
    private var yOffset = 100f
    private var radius = 0f
    private lateinit var parentGroup: ViewGroup
    private var sessions: List<ButtonPref> = listOf()
    private var scrollLimit = 0f

    private val rotaryActions = listOf(ButtonPref("Scroll") { console.CURRENT_NAVIGATION_MODE = 0 },
        ButtonPref("◀▶") { console.CURRENT_NAVIGATION_MODE = 1 },
        ButtonPref("▲▼") { console.CURRENT_NAVIGATION_MODE = 2 })
    private val controls = listOf(ButtonPref("Keys") {
        if (extraKeysAdded) parentGroup.removeView(extrakeys) else parentGroup.addView(
            extrakeys
        )
        extraKeysAdded = !extraKeysAdded
    }, ButtonPref("◳") {
        createPopupWindow(WindowManager(console))
    }, ButtonPref(
        "✕"
    ) {
        for (it in TerminalSessions) {
            removeFinishedSession(it)
        }
    })

    private val detector =
        GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
            override fun onSingleTapUp(e: MotionEvent) {
                toogleVisibility()
                for (i in sessions) {
                    if (isPointInCircle(i.cx, i.cy + yOffset, radius, e.x, e.y)) i.action()
                }
                for (i in rotaryActions) {
                    if (isPointInCircle(i.cx, i.cy + yOffset, radius, e.x, e.y)) i.action()
                }
                for (i in controls) {
                    if (isPointInCircle(i.cx, i.cy + yOffset, radius, e.x, e.y)) i.action()
                }
            }

            override fun onScroll(e2: MotionEvent, dy: Float) {
                yOffset = max(-scrollLimit, yOffset - dy)
            }

            override fun onFling(e2: MotionEvent, velocityY: Float) {
            }


            override fun onScale(scale: Float) {
            }

            override fun onUp(e: MotionEvent) {
                if (yOffset >= height / 1.5f) {
                    toogleVisibility()
                    yOffset = scrollLimit
                } else updateOffset(0f)
            }

            override fun onLongPress(e: MotionEvent) {
                val buff = sessions.last()
                if (isPointInCircle(buff.cx, buff.cx, radius, e.x, e.y)) {
                    addNewSession(true)
                    toogleVisibility()
                }

            }

        })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun getSessions(): List<ButtonPref> {
        val pairs = mutableListOf<ButtonPref>()
        TerminalSessions.forEachIndexed { index, session ->
            pairs.add(ButtonPref("${index + 1}") { console.attachSession(session) })
        }
        pairs.add(ButtonPref("+") { addNewSession(false) })
        return pairs
    }

    fun toogleVisibility() {
        if (this.visibility == VISIBLE) {
            visibility = GONE
            console.requestFocus()
        } else {
            calculateButtons()
            visibility = VISIBLE
            requestFocus()
        }
    }

    private fun calculateButtons() {
        sessions = getSessions()
        val spacing = (width / numOfButtonInline)
        radius = spacing / 2f - 10
        val list = listOf(sessions, rotaryActions, controls)
        var y = spacing / 2f
        for (i in list.indices) {
            for (j in list[i].indices) {
                list[i][j].cx = (0.5f + j % numOfButtonInline) * spacing
                y += j / numOfButtonInline * spacing
                list[i][j].cy = y
            }
            y += spacing + 40
        }
        scrollLimit = y / 4
    }

    private fun createPopupWindow(view: View) {
        parentGroup.addView(view)
        view.requestFocus()
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        parentGroup = parent as ViewGroup
        calculateButtons()
    }

    private val textOffset = paint.descent()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xff0d0d0d.toInt())
        paint.color = secondaryText
        canvas.drawText("Sessions", width / 2f, yOffset - 10, paint)
        sessions.forEachIndexed { index, i ->
            paint.color =
                if (index == TerminalSessions.indexOf(console.currentSession)) colorPrimaryAccent else surface
            canvas.drawCircle(i.cx, i.cy + yOffset, radius, paint)
            paint.color = primaryTextColor
            canvas.drawText(i.text, i.cx, i.cy + yOffset + textOffset, paint)
        }
        paint.color = secondaryText
        canvas.drawText("Rotary", width / 2f, sessions.last().cy + yOffset + radius + 40, paint)
        rotaryActions.forEachIndexed { index, i ->
            paint.color =
                if (index == console.CURRENT_NAVIGATION_MODE) colorPrimaryAccent else surface
            canvas.drawCircle(i.cx, i.cy + yOffset, radius, paint)
            paint.color = primaryTextColor
            canvas.drawText(i.text, i.cx, i.cy + yOffset + textOffset, paint)
        }
        paint.color = secondaryText
        canvas.drawText(
            "Controls", width / 2f, rotaryActions.last().cy + yOffset + radius + 40, paint
        )
        for (i in controls) {
            paint.color = surface
            canvas.drawCircle(i.cx, i.cy + yOffset, radius, paint)
            paint.color = primaryTextColor
            canvas.drawText(i.text, i.cx, i.cy + yOffset + textOffset, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        invalidate()
        return true
    }

    private fun updateOffset(dy: Float) {
        yOffset = max(-scrollLimit, min(scrollLimit, yOffset - dy))
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            updateOffset(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * 200)
            invalidate()
        }
        return true
    }
}

class Key(val label: String, val code: Int)

private const val buttonRadius = 25f

internal class Extrakeys(context: Context) : View(context) {
    private var a = 0f

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

private fun isPointInCircle(
    centerX: Float, centerY: Float, radius: Float, pointX: Float, pointY: Float
): Boolean {
    return (pointX - centerX) * (pointX - centerX) + (pointY - centerY) * (pointY - centerY) <= radius * radius
}
