package com.termux.utils

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.termux.utils.data.ConfigManager
import com.termux.utils.data.TerminalManager.addNewSession
import com.termux.utils.data.TerminalManager.console
import com.termux.utils.data.TerminalManager.removeFinishedSession
import com.termux.utils.data.TerminalManager.sessions
import com.termux.utils.data.isPointInCircle
import com.termux.utils.ui.Extrakeys
import com.termux.utils.ui.WindowManager
import com.termux.utils.ui.getContrastColor
import com.termux.utils.ui.primary
import com.termux.utils.ui.secondary
import com.termux.utils.ui.secondaryTextColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ControlsUI : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ControlView(this).apply { requestFocus() })
    }

    private inner class ControlView(context: Context) : View(context) {
        private val paint = Paint().apply {
            typeface = ConfigManager.typeface
            textSize = 35f
            textAlign = Paint.Align.CENTER
            color = primary
        }
        private var scroll = 100f
        private var radius = 0f
        private var maxScroll = 0f
        private lateinit var parentGroup: ViewGroup

        private val sessions_buttons = sessions.mapIndexed { index, session ->
            ButtonPref("${index + 1}", longAction = { removeFinishedSession(session) }) {
                console.attachSession(session)
            }
        } + ButtonPref("+", longAction = { addNewSession(true) }) {
            addNewSession(false)
        }

        private val rotaryActions = listOf(
            "Scroll", "◀ ▶", "▲▼"
        ).mapIndexed { index, s -> ButtonPref(s) { console.CURRENT_ROTARY_MODE = index } }

        private val controls = listOf(ButtonPref("Keys") { toggleExtraKeys() },
            ButtonPref("◳") { showWindowManager() },
            ButtonPref("✕") { sessions.forEach { removeFinishedSession(it) } })

        private val detector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    (sessions_buttons + rotaryActions + controls).forEach {
                        if (isPointInCircle(
                                it.cx, it.cy + scroll, radius, e.x, e.y
                            )
                        ) {
                            it.action()
                            finish()
                            return@forEach
                        }
                    }

                    return super.onSingleTapConfirmed(e)
                }

                override fun onLongPress(e: MotionEvent) {
                    sessions_buttons.forEach {
                        if (isPointInCircle(
                                it.cx, it.cy + scroll, radius, e.x, e.y
                            )
                        ) {
                            it.longAction()
                            return
                        }
                    }
                    finish()
                }

                override fun onScroll(
                    e1: MotionEvent?, e2: MotionEvent, distanceX: Float, dy: Float
                ): Boolean {
                    updateOffset(dy)
                    return super.onScroll(e1, e2, distanceX, dy)
                }

                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
                ): Boolean {
                    updateOffset(-velocityY)
                    return super.onFling(e1, e2, velocityX, velocityY)
                }
            })

        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onDraw(canvas: Canvas) {
            drawSection(
                canvas, "Sessions", sessions_buttons, sessions.indexOf(console.currentSession), 0f
            )
            drawSection(
                canvas,
                "Rotary",
                rotaryActions,
                console.CURRENT_ROTARY_MODE,
                sessions_buttons.last().cy + radius + 40
            )
            drawSection(canvas, "Controls", controls, -1, rotaryActions.last().cy + radius + 40)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            parentGroup = console.parent as ViewGroup
            calculateButtonPositions()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            detector.onTouchEvent(event)
            return true
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                updateOffset(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * 200)
                invalidate()
            }
            return true
        }

        private fun drawSection(
            canvas: Canvas,
            title: String,
            buttons: List<ButtonPref>,
            highlightIndex: Int,
            startY: Float
        ) {
            paint.color = secondaryTextColor
            canvas.drawText(title, width / 2f, startY + scroll - 5, paint)
            buttons.forEachIndexed { index, button ->
                paint.color = if (index == highlightIndex) primary else secondary
                canvas.drawCircle(button.cx, button.cy + scroll, radius, paint)
                paint.color = getContrastColor(paint.color)
                canvas.drawText(
                    button.text, button.cx, button.cy + scroll + paint.descent(), paint
                )
            }
        }

        private fun updateOffset(dy: Float) {
            val newScroll = max(-maxScroll, min(150f, scroll - dy))
            if (abs(newScroll - scroll) > 1) {
                scroll = newScroll
                invalidate()
            }
        }

        private fun toggleExtraKeys() {
            for (i in 0 until parentGroup.childCount) {
                val child = parentGroup.getChildAt(i)
                if (child is Extrakeys) {
                    parentGroup.removeView(child)
                    return
                }
            }
            parentGroup.addView(Extrakeys())
        }

        private fun showWindowManager() {
            for (i in 0 until parentGroup.childCount) {
                val child = parentGroup.getChildAt(i)
                if (child is WindowManager) {
                    parentGroup.removeView(child)
                    return
                }
            }
            parentGroup.addView(WindowManager().apply { requestFocus() })
        }

        private fun calculateButtonPositions() {
            val spacing = width / BUTTONS_PER_LINE
            radius = spacing / 2f - 10
            val sections = listOf(sessions_buttons, rotaryActions, controls)
            var y = spacing / 2f
            sections.forEach { section ->
                section.forEachIndexed { index, button ->
                    button.cx = (0.5f + index % BUTTONS_PER_LINE) * spacing
                    button.cy = y + (index / BUTTONS_PER_LINE * spacing)
                }
                y += spacing * ceil(section.size / BUTTONS_PER_LINE.toFloat()) + 40
            }
            maxScroll = y - height
        }

    }

    private class ButtonPref(
        val text: String,
        var cx: Float = 0f,
        var cy: Float = 0f,
        val longAction: (() -> Unit) = {},
        val action: () -> Unit
    )

    private companion object {
        const val BUTTONS_PER_LINE = 3
    }
}
