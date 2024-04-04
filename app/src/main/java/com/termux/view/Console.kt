package com.termux.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Scroller
import com.termux.app.main
import com.termux.terminal.KeyHandler
import com.termux.terminal.KeyHandler.getCode
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.utils.data.ConfigManager
import com.termux.view.textselection.TextSelectionCursorController
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * View displaying and interacting with a [TerminalSession].
 */
class Console(context: Context?, attributes: AttributeSet?) : View(context, attributes) {

    val mActivity: main = context as main

    private val enable =
        File(ConfigManager.EXTRA_BLUR_BACKGROUND).exists() && ConfigManager.enableBlur
    private val location by lazy { IntArray(2) }
    private val blurBitmap by lazy {
        Drawable.createFromPath(ConfigManager.EXTRA_BLUR_BACKGROUND)
            ?.apply { setBounds(0, 0, 450, 450) }
    }
    private val rect by lazy { RectF() }
    private val paint by lazy {
        Paint().apply {
            color = TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_PRIMARY]
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
    }
    private val path by lazy {
        android.graphics.Path()
    }
    private var dx = 6f
    private var dy = 6f

    private val mDefaultSelectors = intArrayOf(-1, -1, -1, -1)
    private lateinit var mGestureRecognizer: GestureAndScaleRecognizer
    private lateinit var mScroller: Scroller
    private val isSelectingText: Boolean
        get() = mTextSelectionCursorController.isActive

    /**
     * The currently displayed terminal session, whose emulator is [.mEmulator].
     */
    lateinit var currentSession: TerminalSession

    /**
     * Our terminal emulator whose session is [.mTermSession].
     */
    lateinit var mEmulator: TerminalEmulator

    private var CURRENT_FONTSIZE: Int = 14

    var mRenderer: TerminalRenderer = TerminalRenderer(CURRENT_FONTSIZE)

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0.
     */
    var topRow: Int = 0

    /**
     * What was left in from scrolling movement.
     */
    private var mScrollRemainder = 0f

    /**
     * If non-zero, this is the last unicode code point received if that was a combining character.
     */
    private var mCombiningAccent = 0
    private var mTextSelectionCursorController: TextSelectionCursorController =
        TextSelectionCursorController(this)

    /**
     * Keep track of where mouse touch event started which we report as mouse scroll.
     */
    private var mMouseScrollStartX = -1
    private var mMouseScrollStartY = -1

    /**
     * Keep track of the time when a touch event leading to sending mouse scroll events started.
     */
    private var mMouseStartDownTime: Long = -1
    var CURRENT_NAVIGATION_MODE = 0
    var isReadShiftKey: Boolean = false
    var isControlKeydown: Boolean = false
    var isReadAltKey: Boolean = false
    var readFnKey: Boolean = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // NO_UCD (unused code)
        keepScreenOn = true
        mGestureRecognizer =
            GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
                var scrolledWithFinger: Boolean = false

                override fun onUp(e: MotionEvent) {
                    mScrollRemainder = 0.0f
                    if (mEmulator.isMouseTrackingActive && !e.isFromSource(
                            InputDevice.SOURCE_MOUSE
                        ) && !isSelectingText && !scrolledWithFinger
                    ) {
                        // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                        // for zooming.
                        sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true)
                        sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false)
                        return
                    }
                    scrolledWithFinger = false
                }

                override fun onSingleTapUp(e: MotionEvent) {
                    if (isSelectingText) {
                        stopTextSelectionMode()
                    }
                    requestFocus()
                    if (!mEmulator.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                        showSoftKeyboard(
                            this@Console
                        )
                    }
                }


                override fun onScroll(
                    e2: MotionEvent, dy: Float
                ) {
                    var distanceY = dy
                    if (mEmulator.isMouseTrackingActive && e2.isFromSource(InputDevice.SOURCE_MOUSE)) {
                        // If moving with mouse pointer while pressing button, report that instead of scroll.
                        // This means that we never report moving with button press-events for touch input,
                        // since we cannot just start sending these events without a starting press event,
                        // which we do not do for touch input, only mouse in onTouchEvent().
                        sendMouseEventCode(e2, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true)
                    } else {
                        scrolledWithFinger = true
                        distanceY += mScrollRemainder
                        val deltaRows = (distanceY / mRenderer.fontLineSpacing).toInt()
                        mScrollRemainder = distanceY - deltaRows * mRenderer.fontLineSpacing
                        doScroll(e2, deltaRows)
                    }
                }

                override fun onScale(scale: Float) {
                    if (isSelectingText) return
                    changeFontSize(scale)
                    return
                }

                override fun onFling(
                    e2: MotionEvent, velocityY: Float
                ) {
                    // Do not start scrolling until last fling has been taken care of:
                    if (!mScroller.isFinished) return
                    val mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive
                    val SCALE = 0.25f
                    if (mouseTrackingAtStartOfFling) {
                        mScroller.fling(
                            0,
                            0,
                            0,
                            -(velocityY * SCALE).toInt(),
                            0,
                            0,
                            -mEmulator.mRows / 2,
                            mEmulator.mRows / 2
                        )
                    } else {
                        mScroller.fling(
                            0,
                            topRow,
                            0,
                            -(velocityY * SCALE).toInt(),
                            0,
                            0,
                            -mEmulator.screen.activeTranscriptRows,
                            0
                        )
                    }

                    post(object : Runnable {
                        private var mLastY = 0

                        override fun run() {
                            if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive) {
                                mScroller.abortAnimation()
                                return
                            }
                            if (mScroller.isFinished) return
                            val more = mScroller.computeScrollOffset()
                            val newY = mScroller.currY
                            val diff =
                                if (mouseTrackingAtStartOfFling) (newY - mLastY) else (newY - topRow)
                            doScroll(e2, diff)
                            mLastY = newY
                            if (more) post(this)
                        }
                    })
                }

                override fun onLongPress(e: MotionEvent) {
                    if (mGestureRecognizer.isInProgress) return
                    if (!isSelectingText) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        startTextSelectionMode(e)
                    }
                }

                override fun onSwipe(ltr: Boolean) {
                    if (ltr) mActivity.navWindow.showSessionChooser() else mActivity.navWindow.showModeMenu()
                }
            })
        mScroller = Scroller(context)
    }

    fun changeFontSize(scale: Float) {
        CURRENT_FONTSIZE = if (1.0f < scale) min(CURRENT_FONTSIZE + 1, 256)
        else max(1, CURRENT_FONTSIZE - 1)
        setTextSize(CURRENT_FONTSIZE)
    }

    /**
     * Attach a [TerminalSession] to this view.
     *
     * @param session The [TerminalSession] this view will be displaying.
     */
    fun attachSession(session: TerminalSession) {
        topRow = 0
        mCombiningAccent = 0
        mEmulator = session.emulator
        currentSession = session
        updateSize()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        return object : BaseInputConnection(this, true) {
            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                sendTextToTerminal(editable)
                editable!!.clear()
                return true
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                val content = editable
                sendTextToTerminal(content)
                content!!.clear()
                return true
            }

            override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {
                // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.

                val deleteKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                for (i in 0 until leftLength) sendKeyEvent(deleteKey)
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            fun sendTextToTerminal(text: CharSequence?) {
                stopTextSelectionMode()
                val textLengthInChars = text!!.length
                var i = 0
                while (i < textLengthInChars) {
                    val firstChar = text[i]
                    var codePoint: Int
                    if (Character.isHighSurrogate(firstChar)) {
                        ++i
                        codePoint = if (i < textLengthInChars) {
                            Character.toCodePoint(firstChar, text[i])
                        } else {
                            // At end of string, with no low surrogate following the high:
                            TerminalEmulator.UNICODE_REPLACEMENT_CHAR
                        }
                    } else {
                        codePoint = firstChar.code
                    }
                    // Check onKeyDown() for details.
                    if (isReadShiftKey) codePoint = codePoint.toChar().uppercaseChar().code
                    var ctrlHeld = false
                    if (31 >= codePoint && 27 != codePoint) {
                        if ('\n'.code == codePoint) {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            codePoint = '\r'.code
                        }
                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true
                        when (codePoint) {
                            31 -> codePoint = '_'.code
                            30 -> codePoint = '^'.code
                            29 -> codePoint = ']'.code
                            28 -> codePoint = '\\'.code
                            else -> codePoint += 96
                        }
                    }
                    inputCodePoint(0, codePoint, ctrlHeld, false)
                    i++
                }
            }
        }
    }

    override fun computeVerticalScrollRange() = mEmulator.screen.activeRows

    override fun computeVerticalScrollExtent() = mEmulator.mRows

    override fun computeVerticalScrollOffset() =
        mEmulator.screen.activeRows + topRow - mEmulator.mRows

    fun onScreenUpdated() {
        val rowsInHistory = mEmulator.screen.activeTranscriptRows
        if (topRow < -rowsInHistory) topRow = -rowsInHistory
        if (isSelectingText) {
            // Do not scroll when selecting text.
            val rowShift = mEmulator.scrollCounter
            if (-topRow + rowShift > rowsInHistory) {
                // .. unless we're hitting the end of history transcript, in which
                // case we abort text selection and scroll to end.
                if (isSelectingText) stopTextSelectionMode()
            } else {
                //skipScrolling = true;
                topRow -= rowShift
                decrementYTextSelectionCursors(rowShift)
            }
        }
        if (0 != topRow) {
            // Scroll down if not already there.
            if (-3 > topRow) {
                // Awaken scroll bars only if scrolling a noticeable amount
                // - we do not want visible scroll bars during normal typing
                // of one row at a time.
                awakenScrollBars()
            }
            topRow = 0
        }
        mEmulator.clearScrollCounter()
        invalidate()
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param textSize the new font size, in density-independent pixels.
     */
    private fun setTextSize(textSize: Int) {
        mRenderer = TerminalRenderer(
            textSize
        )
        updateSize()
    }

    override fun onCheckIsTextEditor() = true

    override fun isOpaque() = true

    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event            The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     * position into account. E.g. if scrolled 3 lines up and the event
     * position is in the top left, column will be -3 if relativeToScroll is
     * true and 0 if relativeToScroll is false.
     * @return Array with the column and row.
     */
    fun getColumnAndRow(event: MotionEvent?, relativeToScroll: Boolean): IntArray {
        val column = (event!!.x / mRenderer.fontWidth).toInt()
        var row =
            ((event.y - mRenderer.mFontLineSpacingAndAscent) / mRenderer.fontLineSpacing).toInt()
        if (relativeToScroll) {
            row += topRow
        }
        return intArrayOf(column, row)
    }

    /**
     * Send a single mouse event code to the terminal.
     */
    private fun sendMouseEventCode(e: MotionEvent?, button: Int, pressed: Boolean) {
        val columnAndRow = getColumnAndRow(e, false)
        var x = columnAndRow[0] + 1
        var y = columnAndRow[1] + 1
        if (pressed && (TerminalEmulator.MOUSE_WHEELDOWN_BUTTON == button || TerminalEmulator.MOUSE_WHEELUP_BUTTON == button)) {
            if (mMouseStartDownTime == e!!.downTime) {
                x = mMouseScrollStartX
                y = mMouseScrollStartY
            } else {
                mMouseStartDownTime = e.downTime
                mMouseScrollStartX = x
                mMouseScrollStartY = y
            }
        }
        mEmulator.sendMouseEvent(button, x, y, pressed)
    }

    /**
     * Perform a scroll, either from dragging the console.
     */
    private fun doScroll(event: MotionEvent?, rowsDown: Int) {
        val up = 0 > rowsDown
        val amount = abs(rowsDown)
        for (i in 0 until amount) {
            if (mEmulator.isMouseTrackingActive) {
                sendMouseEventCode(
                    event,
                    if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON,
                    true
                )
            } else if (mEmulator.isAlternateBufferActive) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt console without mouse handling.
                handleKeyCode(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
            } else {
                topRow = min(
                    0, max(
                        -mEmulator.screen.activeTranscriptRows, (topRow + (if (up) -1 else 1))
                    )
                )
                invalidate()
            }
        }
    }

    /**
     * Overriding .
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val event1: Int
        if (MotionEvent.ACTION_SCROLL == event.action && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            //Todo
            when (CURRENT_NAVIGATION_MODE) {
                2 -> {
                    event1 = if (0 < delta) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
                    handleKeyCode(event1, KeyEvent.ACTION_DOWN)
                    return true
                }

                1 -> {
                    event1 =
                        if (0 < delta) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                    handleKeyCode(event1, KeyEvent.ACTION_DOWN)
                    return true
                }

                else -> {
                    doScroll(event, Math.round(delta * 15))
                    return true
                }
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mGestureRecognizer.onTouchEvent(event)
        return true
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            if (isSelectingText) {
                stopTextSelectionMode()
                return true
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isSelectingText) {
            stopTextSelectionMode()
        }
        if (KeyEvent.KEYCODE_ENTER == keyCode && !currentSession.isRunning) {
            mActivity.termuxTerminalSessionClientBase.removeFinishedSession(currentSession)
            invalidate()
            return true
        } else if (event.isSystem && (KeyEvent.KEYCODE_BACK != keyCode)) {
            return super.onKeyDown(keyCode, event)
        }
        val metaState = event.metaState
        val controlDown = event.isCtrlPressed || isControlKeydown
        val leftAltDown = 0 != (metaState and KeyEvent.META_ALT_LEFT_ON) || isReadAltKey
        val shiftDown = event.isShiftPressed || isReadShiftKey
        val rightAltDownFromEvent = 0 != (metaState and KeyEvent.META_ALT_RIGHT_ON)
        var keyMod = 0
        if (controlDown) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KeyHandler.KEYMOD_NUM_LOCK
        // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod)) {
            return true
        }
        // Clear Ctrl since we handle that ourselves:
        val effectiveMetaState = getEffectiveMetaState(event, rightAltDownFromEvent, shiftDown)
        var result = event.getUnicodeChar(effectiveMetaState)

        if (0 == result) {
            return false
        }
        val oldCombiningAccent = mCombiningAccent
        if (0 != (result and KeyCharacterMap.COMBINING_ACCENT)) {
            // If entered combining accent previously, write it out:
            if (0 != mCombiningAccent) inputCodePoint(
                event.deviceId, mCombiningAccent, controlDown, leftAltDown
            )
            mCombiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            if (0 != mCombiningAccent) {
                val combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result)
                if (0 < combinedChar) result = combinedChar
                mCombiningAccent = 0
            }
            inputCodePoint(event.deviceId, result, controlDown, leftAltDown)
        }
        if (mCombiningAccent != oldCombiningAccent) invalidate()
        return true
    }

    private fun inputCodePoint(
        eventSource: Int,
        codePoint: Int,
        controlDownFromEvent: Boolean,
        leftAltDownFromEvent: Boolean
    ) {
        var codePoint1 = codePoint
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        mEmulator.setCursorBlinkState(true)
        val controlDown = controlDownFromEvent || isControlKeydown
        val altDown = leftAltDownFromEvent || isReadAltKey
        if (controlDown) {
            if (106 == codePoint1 &&  /* Ctrl+j or \n */
                !currentSession.isRunning
            ) {
                mActivity.termuxTerminalSessionClientBase.removeFinishedSession(currentSession)
                return
            }
        }
        if (controlDown) {
            if ('a'.code <= codePoint1 && 'z'.code >= codePoint1) {
                codePoint1 = codePoint1 - 'a'.code + 1
            } else if ('A'.code <= codePoint1 && 'Z'.code >= codePoint1) {
                codePoint1 = codePoint1 - 'A'.code + 1
            } else if (' '.code == codePoint1 || '2'.code == codePoint1) {
                codePoint1 = 0
            } else if ('['.code == codePoint1 || '3'.code == codePoint1) {
                // ^[ (Esc)
                codePoint1 = 27
            } else if ('\\'.code == codePoint1 || '4'.code == codePoint1) {
                codePoint1 = 28
            } else if (']'.code == codePoint1 || '5'.code == codePoint1) {
                codePoint1 = 29
            } else if ('^'.code == codePoint1 || '6'.code == codePoint1) {
                // control-^
                codePoint1 = 30
            } else if ('_'.code == codePoint1 || '7'.code == codePoint1 || '/'.code == codePoint1) {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint1 = 31
            } else if ('8'.code == codePoint1) {
                // DEL
                codePoint1 = 127
            }
        }
        if (-1 < codePoint1) {
            // If not virtual or soft keyboard.
            if (0 < eventSource) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect - the
                // desire to input the original characters should be low.
                when (codePoint1) {
                    0x02DC ->                         // TILDE (~).
                        codePoint1 = 0x007E

                    0x02CB ->                         // GRAVE ACCENT (`).
                        codePoint1 = 0x0060

                    0x02C6 ->                         // CIRCUMFLEX ACCENT (^).
                        codePoint1 = 0x005E
                }
            }
            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            currentSession.writeCodePoint(altDown, codePoint1)
        }
    }

    /**
     * Input the specified keyCode if applicable and return if the input was consumed.
     */
    private fun handleKeyCode(keyCode: Int, keyMod: Int): Boolean {
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        mEmulator.setCursorBlinkState(true)
        if (handleKeyCodeAction(keyCode, keyMod)) return true
        val term = currentSession.emulator
        val code = getCode(
            keyCode, keyMod, term.isCursorKeysApplicationMode, term.isKeypadApplicationMode
        ) ?: return false
        currentSession.write(code)
        return true
    }

    private fun handleKeyCodeAction(keyCode: Int, keyMod: Int): Boolean {
        val shiftDown = 0 != (keyMod and KeyHandler.KEYMOD_SHIFT)
        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN ->                 // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    val time = SystemClock.uptimeMillis()
                    val motionEvent =
                        MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                    doScroll(motionEvent, if (KeyEvent.KEYCODE_PAGE_UP == keyCode) -1 else 1)
                    motionEvent.recycle()
                    return true
                }
        }
        return false
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event   A [KeyEvent] describing the event.
     * @return Whether the event was handled.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Do not return for KEYCODE_BACK and send it to the client since user may be trying
        // to exit the activity.

        if (event.isSystem) {
            // Let system key events through.
            return super.onKeyUp(keyCode, event)
        }
        return true
    }

    /**
     * This is called during layout when the size of this view has changed. If you were just added to the view
     * hierarchy, you're called with the old values of 0.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateSize()

    /**
     * Check if the terminal size in rows and columns should be updated.
     */
    private fun updateSize() {
        val viewWidth = width
        val viewHeight = height
        // Set to 80 and 24 if you want to enable vttest.
        dy = viewHeight * .018f
        dx = viewWidth * .018f
        if (ConfigManager.enableBorder || enable) rect.set(
            0f,
            0f,
            viewWidth.toFloat(),
            viewHeight.toFloat()
        )
        val newColumns = max(
            4, (viewWidth / mRenderer.fontWidth).toInt()
        )
        val newRows =
            4.coerceAtLeast((viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.fontLineSpacing)
        if (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows) {
            currentSession.updateSize(
                newColumns, newRows, mRenderer.fontWidth.toInt(), mRenderer.fontLineSpacing
            )
            // Update mTerminalCursorBlinkerRunnable inner class mEmulator on session change
            topRow = 0
            scrollTo(0, 0)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        updateBlurBackground(canvas)
        drawBorder(canvas)
        mTextSelectionCursorController.getSelectors(mDefaultSelectors)
        mRenderer.render(
            mEmulator,
            canvas,
            topRow,
            mDefaultSelectors[0],
            mDefaultSelectors[1],
            mDefaultSelectors[2],
            mDefaultSelectors[3]
        )
        // render the text selection handles
        renderTextSelection()
    }

    fun getCursorX(x: Float) = (x / mRenderer.fontWidth).toInt()

    fun getCursorY(y: Float) = (((y - 40) / mRenderer.fontLineSpacing) + topRow).toInt()

    fun getPointX(cx: Int): Int {
        var cx1 = cx
        if (cx1 > mEmulator.mColumns) {
            cx1 = mEmulator.mColumns
        }
        return Math.round(cx1 * mRenderer.fontWidth)
    }

    fun getPointY(cy: Int) = (cy - topRow) * mRenderer.fontLineSpacing


    private fun showTextSelectionCursors(event: MotionEvent?) {
        mTextSelectionCursorController.show(event!!)
    }

    private fun hideTextSelectionCursors() = mTextSelectionCursorController.hide()


    private fun renderTextSelection() = mTextSelectionCursorController.render()

    private fun startTextSelectionMode(event: MotionEvent?) {
        if (!requestFocus()) {
            return
        }
        showTextSelectionCursors(event)

        invalidate()
    }

    fun stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            invalidate()
        }
    }

    private fun decrementYTextSelectionCursors(decrement: Int) =
        mTextSelectionCursorController.decrementYTextSelectionCursors(decrement)


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnTouchModeChangeListener(mTextSelectionCursorController)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTextSelectionMode()
        viewTreeObserver.removeOnTouchModeChangeListener(mTextSelectionCursorController)
    }

    private fun getEffectiveMetaState(
        event: KeyEvent, rightAltDownFromEvent: Boolean, shiftDown: Boolean
    ): Int {
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAltDownFromEvent) {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear = bitsToClear or (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
        }
        var effectiveMetaState = event.metaState and bitsToClear.inv()
        if (shiftDown) effectiveMetaState =
            effectiveMetaState or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        if (readFnKey) effectiveMetaState = effectiveMetaState or KeyEvent.META_FUNCTION_ON
        return effectiveMetaState
    }

    fun showSoftKeyboard(view: View) {
        val inputMethodManager =
            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(view, 0)
    }

    private fun updateBlurBackground(c: Canvas) {
        if (!enable) return
        path.reset()
        path.addRoundRect(rect, 15f, 15f, android.graphics.Path.Direction.CW)
        c.clipPath(path)
        getLocationOnScreen(location)
        c.save()
        c.translate((-location[0]).toFloat(), (-location[1]).toFloat())
        blurBitmap?.draw(c)
        c.restore()
    }

    private fun drawBorder(c: Canvas) {
        if (ConfigManager.enableBorder)
            c.drawRoundRect(rect, 15f, 15f, paint)
    }
}
