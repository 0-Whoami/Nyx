package com.termux.utils

import android.app.Activity
import android.graphics.Canvas
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.termux.utils.TerminalManager.TerminalSessions
import com.termux.utils.TerminalManager.addNewSession
import com.termux.utils.TerminalManager.console
import com.termux.utils.TerminalManager.removeFinishedSession
import com.termux.utils.ui.Extrakeys
import com.termux.utils.ui.WindowManager
import com.termux.utils.ui.colorPrimaryAccent
import com.termux.utils.ui.paint
import com.termux.utils.ui.primaryTextColor
import com.termux.utils.ui.secondaryText
import com.termux.utils.ui.surface
import com.termux.view.GestureAndScaleRecognizer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


class NavWindow : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(object : View(this) {
            private fun extraKeysToogle() {
                for (i in 0..<parentGroup.childCount) with(parentGroup.getChildAt(i)) {
                    if (this is Extrakeys) {
                        parentGroup.removeView(this)
                        return
                    }
                }
                parentGroup.addView(Extrakeys(context))
            }

            private var yOffset = 100f
            private var radius = 0f
            private lateinit var parentGroup: ViewGroup
            private var sessions: List<ButtonPref> = listOf()
            private var scrollLimit = 0f

            private val rotaryActions =
                listOf(ButtonPref("Scroll") { console.CURRENT_NAVIGATION_MODE = 0 },
                    ButtonPref("◀▶") { console.CURRENT_NAVIGATION_MODE = 1 },
                    ButtonPref("▲▼") { console.CURRENT_NAVIGATION_MODE = 2 })
            private val controls = listOf(ButtonPref("Keys") {
                extraKeysToogle()
            }, ButtonPref("◳") {
                showWindowManager()
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
                        for (i in sessions) {
                            if (isPointInCircle(i.cx, i.cy + yOffset, radius, e.x, e.y)) i.action()
                        }
                        for (i in rotaryActions) {
                            if (isPointInCircle(i.cx, i.cy + yOffset, radius, e.x, e.y)) i.action()
                        }
                        for (i in controls) {
                            if (isPointInCircle(i.cx, i.cy + yOffset, radius, e.x, e.y)) i.action()
                        }
                        finish()
                    }

                    override fun onScroll(e2: MotionEvent, dy: Float) {
                        updateOffset(dy)
                        invalidate()
                    }

                    override fun onFling(e2: MotionEvent, velocityY: Float) {
                    }


                    override fun onScale(scale: Float) {
                    }

                    override fun onUp(e: MotionEvent) {
                    }

                    override fun onLongPress(e: MotionEvent) {
                        for (i in sessions) if (isPointInCircle(
                                i.cx, i.cy + yOffset, radius, e.x, e.y
                            )
                        ) i.longAction()
                        finish()
                    }

                })

            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun isOpaque() = true
            private fun getSessions(): List<ButtonPref> {
                val pairs = mutableListOf<ButtonPref>()
                TerminalSessions.forEachIndexed { index, session ->
                    pairs.add(ButtonPref(
                        "${index + 1}",
                        longAction = { removeFinishedSession(session) }) {
                        console.attachSession(
                            session
                        )
                    })
                }
                pairs.add(ButtonPref("+", longAction = { addNewSession(true) }) {
                    addNewSession(
                        false
                    )
                })
                return pairs
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
                        list[i][j].cy = y + (j / numOfButtonInline * spacing)
                    }
                    y += spacing * ceil(list[i].size / numOfButtonInline.toFloat()) + 40
                }
                scrollLimit = y - height
            }

            private fun showWindowManager() {
                for (i in 0..<parentGroup.childCount) with(parentGroup.getChildAt(i)) {
                    if (this is WindowManager) {
                        parentGroup.removeView(this)
                        return
                    }
                }
                val view = WindowManager(console)
                parentGroup.addView(view)
                view.requestFocus()
            }


            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                parentGroup = console.parent as ViewGroup
                calculateButtons()
            }

            private val textOffset = paint.descent()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawColor(0xff0a0a0a.toInt())
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
                canvas.drawText(
                    "Rotary", width / 2f, sessions.last().cy + yOffset + radius + 40, paint
                )
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
                return true
            }

            private fun updateOffset(dy: Float) {
                yOffset = max(-scrollLimit, min(150f, yOffset - dy))
            }

            override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    updateOffset(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * 200)
                    invalidate()
                }
                return true
            }
        }.apply {
            requestFocus()
        })
        super.onCreate(savedInstanceState)
    }
}

private class ButtonPref(
    val text: String,
    var cx: Float = 0f,
    var cy: Float = 0f,
    val longAction: (() -> Unit) = { },
    val action: () -> Unit
)

private const val numOfButtonInline: Int = 3
