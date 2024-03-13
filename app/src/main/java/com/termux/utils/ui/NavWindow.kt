package com.termux.utils.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.termux.app.main
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TextStyle
import com.termux.utils.data.ConfigManager
import com.termux.view.Console
import kotlin.math.abs
import kotlin.math.roundToInt

class NavWindow(val mActivity: main) {
    private var extraKeysAdded: Boolean = false
    private val popupWindow: PopupWindow by lazy {
        PopupWindow(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { isFocusable = true }
    }
    private val extrakeys by lazy { Extrakeys(mActivity.console) }
    private val sessionPairs: List<Pair<String, () -> Unit>>
        get() {
            val pairs = mutableListOf<Pair<String, () -> Unit>>()

            pairs.addAll(mActivity.mNyxService.TerminalSessions.mapIndexed { index, session ->
                index.toString() to {
                    mActivity.mNyxService.mTermuxTerminalSessionActivityClient.setCurrentSession(
                        session
                    )
                }
            })
            pairs.add("+" to {
                mActivity.mNyxService.mTermuxTerminalSessionActivityClient.addNewSession(
                    false
                )
            })
            pairs.add("+!" to {
                mActivity.mNyxService.mTermuxTerminalSessionActivityClient.addNewSession(
                    true
                )
            })
            return pairs
        }

    private val navigationPairs: List<Pair<String, () -> Unit>> by lazy {
        listOf("⊻" to { mActivity.console.CURRENT_NAVIGATION_MODE = 0 },
            "◀▶" to { mActivity.console.CURRENT_NAVIGATION_MODE = 1 },
            "▲▼" to { mActivity.console.CURRENT_NAVIGATION_MODE = 2 },
            "Keys" to {
                if (extraKeysAdded) mActivity.linearLayout.removeView(extrakeys) else mActivity.linearLayout.addView(
                    extrakeys
                )
                extraKeysAdded = !extraKeysAdded
            },
            "◳" to {
                createPopupWindow(windowMan)
                showPopup()
            })
    }
    private val windowMan by lazy {
        object : View(mActivity) {
            var factor = 1f

            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            val sizeRef = mActivity.linearLayout.height
            val detector = ScaleGestureDetector(mActivity,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        factor *= detector.scaleFactor
                        changeSize()
                        return true
                    }
                })
            private var dX = 0f
            private var dY = 0f
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = mActivity.linearLayout.x - event.rawX
                        dY = mActivity.linearLayout.y - event.rawY
                    }

                    MotionEvent.ACTION_MOVE -> {
                        mActivity.linearLayout.x = (event.rawX + dX)
                        mActivity.linearLayout.y = (event.rawY + dY)
                    }
                }
                detector.onTouchEvent(event)
                mActivity.console.invalidate()
                return true
            }

            fun changeSize() {
                val newHeight = (sizeRef * factor).roundToInt()
                val attr = FrameLayout.LayoutParams(newHeight, newHeight)
                mActivity.linearLayout.layoutParams = attr
            }

            override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    factor *= if (-event.getAxisValue(MotionEvent.AXIS_SCROLL) > 0) 0.95f
                    else 1.05f
                    changeSize()
                }
                return true
            }
        }
    }
    private val navUi by lazy {
        GesturedView(mActivity) { dismiss() }
    }

    fun showModeMenu() {
        navUi.setData(navigationPairs)
        createPopupWindow(navUi)
        showPopup()
    }

    fun showSessionChooser() {
        navUi.setData(sessionPairs)
        createPopupWindow(navUi)
        showPopup()
    }

    private fun dismiss() {
        popupWindow.dismiss()
        mActivity.console.requestFocus()
    }

    private fun showPopup() =
        popupWindow.showAtLocation(mActivity.console, Gravity.CENTER, 0, 0).also {
            popupWindow.contentView.requestFocus()
        }


    private fun createPopupWindow(view: View) {
        popupWindow.contentView = view
    }


    internal class GesturedView(private val context: main, private val dismissal: () -> Unit) :
        View(context) {
        private var initialX = 0f
        private var initialY = 0f
        private var halfHeight = 0f
        private var halfWidth = 0f
        private val primaryRadius = 70f

        private var index: Int = 0
        private val pageNumber: Int
            get() = pairs.size
        private val paint = Paint().apply {
            typeface = context.console.mRenderer.mTypeface
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        private lateinit var pairs: List<Pair<String, () -> Unit>>
        fun setData(data: List<Pair<String, () -> Unit>>) {
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
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
            canvas.drawCircle(halfWidth, halfHeight, primaryRadius, paint)
            paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY]
            canvas.drawText(
                pairs[index].first,
                halfWidth,
                (halfHeight - ((paint.descent() + paint.ascent()) / 2)),
                paint
            )
            paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
            for (i in 0..<pageNumber) {
                paint.alpha = if (i == index) 255 else 100
                canvas.drawCircle(
                    halfWidth - 15 * pairs.size / 2 + 15 * i, height - 20f, 5f, paint
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
            if (positionX in halfWidth - primaryRadius..halfWidth + primaryRadius && positionY in halfHeight - primaryRadius..halfHeight + primaryRadius) pairs[index].second()
            dismissal()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                initialX = event.x
                initialY = event.y
            }
            if (event.action == MotionEvent.ACTION_UP) {
                val deltaX = event.x - initialX
                if (abs(deltaX) > 100) {
                    if (deltaX > 0) swipeRight()
                    else swipeLeft()
                } else {
                    click(initialX, initialY)
                }
            }
            invalidate()
            context.console.invalidate()
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

    internal class Extrakeys(private val console: Console) : View(console.context) {
        private var buttonRadius = 18f
        private var touchRegionLength = 40
        private var spacing = 30f
        private val paint = Paint().apply {
            color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
            typeface = console.mRenderer.mTypeface
            textAlign = Paint.Align.CENTER
        }
        private val buttonStateRefs = arrayOf(
            console::isControlKeydown, console::isReadAltKey, console::isReadShiftKey
        )
        private var centerX = 0f
        private var key_enabled = false

        private val normalKey = ConfigManager.keys
        private val numButtons = buttonStateRefs.size + normalKey.size
        private val touchRanges = Array(numButtons) { IntRange(0, 0) }
        private val label = ConfigManager.keyLabel
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            touchRegionLength = MeasureSpec.getSize(widthMeasureSpec) / numButtons
            spacing =
                (MeasureSpec.getSize(widthMeasureSpec) - (2 * buttonRadius * numButtons)) / (numButtons + 1)
            setMeasuredDimension(
                widthMeasureSpec, (buttonRadius * 2).toInt() + 5
            )
            for (i in 0 until numButtons) touchRanges[i] =
                i * touchRegionLength..(i + 1) * touchRegionLength

        }

        override fun onDraw(canvas: Canvas) {
            centerX = spacing + buttonRadius
            for (i in 0 until numButtons) {
                key_enabled = i in buttonStateRefs.indices && buttonStateRefs[i].get()
                paint.color =
                    if (key_enabled) TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY] else TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
                canvas.drawCircle(
                    centerX, buttonRadius + 5, buttonRadius, paint
                )
                paint.color =
                    if (key_enabled) TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY] else TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_SECONDARY]
                val a = (label.getOrNull(i) ?: "")
                canvas.drawText(
                    a, centerX, buttonRadius + 10, paint
                )
                paint.color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
                centerX += spacing + 2 * buttonRadius
            }
            super.onDraw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                for (i in 0 until numButtons) {
                    if (event.x.toInt() in touchRanges[i]) {
                        if (i < buttonStateRefs.size) buttonStateRefs[i].set(!buttonStateRefs[i].get())
                        else console.dispatchKeyEvent(
                            KeyEvent(
                                KeyEvent.ACTION_DOWN, normalKey[i - buttonStateRefs.size]
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

}

