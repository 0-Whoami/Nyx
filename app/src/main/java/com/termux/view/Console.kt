package com.termux.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.data.ConfigManager
import com.termux.data.ConfigManager.enableBorder
import com.termux.data.ConfigManager.font_size
import com.termux.terminal.KeyHandler.KEYMOD_ALT
import com.termux.terminal.KeyHandler.KEYMOD_CTRL
import com.termux.terminal.KeyHandler.KEYMOD_NUM_LOCK
import com.termux.terminal.KeyHandler.KEYMOD_SHIFT
import com.termux.terminal.KeyHandler.getCode
import com.termux.terminal.SessionManager.addNewSession
import com.termux.terminal.SessionManager.sessions
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.textselection.TextSelectionCursorController.decrementYTextSelectionCursors
import com.termux.view.textselection.TextSelectionCursorController.hideTextSelectionCursor
import com.termux.view.textselection.TextSelectionCursorController.isSelectingText
import com.termux.view.textselection.TextSelectionCursorController.showTextSelectionCursor
import com.termux.view.textselection.TextSelectionCursorController.updateSelHandles
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * View displaying and interacting with a [TerminalSession].
 */
class Console(context : Context) : View(context) {

    private val enableBackground = File(ConfigManager.EXTRA_BLUR_BACKGROUND).exists() && ConfigManager.enableBlur
    private val blurDrawable by lazy {
        Drawable.createFromPath(ConfigManager.EXTRA_BLUR_BACKGROUND)
    }

    private val mGestureRecognizer : GestureAndScaleRecognizer = GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
        var scrolledWithFinger : Boolean = false

        override fun onUp(e : MotionEvent) {
            mScrollRemainder = 0.0f
            if (mEmulator.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText && !scrolledWithFinger) { // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                // for zooming.
                sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true)
                sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false)
                return
            }
            scrolledWithFinger = false
        }

        override fun onSingleTapUp() {
            if (isSelectingText) {
                stopTextSelectionMode()
            } else {
                requestFocus()
                if (!mEmulator.isMouseTrackingActive) {
                    showSoftKeyboard()
                }
            }
        }


        override fun onScroll(e2 : MotionEvent, dy : Float) {
            if (isSelectingText) stopTextSelectionMode()
            var distanceY = dy
            scrolledWithFinger = true
            distanceY += mScrollRemainder
            val deltaRows = (distanceY / mRenderer.fontLineSpacing).toInt()
            mScrollRemainder = distanceY - deltaRows * mRenderer.fontLineSpacing
            doScroll(e2, deltaRows)
        }

        override fun onScale(scale : Float) {
            if (isSelectingText) return
            changeFontSize(scale)
        }

        override fun onLongPress(e : MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startTextSelectionMode(e)
        }
    })

    /**
     * The currently displayed terminal session, whose emulator is [.mEmulator].
     */
    lateinit var currentSession : TerminalSession

    /**
     * Our terminal emulator whose session is [.mTermSession].
     */
    lateinit var mEmulator : TerminalEmulator

    var mRenderer : TerminalRenderer = TerminalRenderer(font_size)

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0.
     */
    var topRow : Int = 0

    /**
     * What was left in from scrolling movement.
     */
    private var mScrollRemainder = 0f

    /**
     * If non-zero, this is the last unicode code point received if that was a combining character.
     */
    private var mCombiningAccent = 0

    /**
     * Keep track of where mouse touch event started which we report as mouse scroll.
     */
    private var mMouseScrollStartX = -1
    private var mMouseScrollStartY = -1

    /**
     * Keep track of the time when a touch event leading to sending mouse scroll events started.
     */
    private var mMouseStartDownTime : Long = -1
    var RotaryMode = 0

    /**
     * Array representing the state of meta keys (Shift, Ctrl, Alt, Fn).
     * Each element corresponds to a specific meta key, with `true` indicating it's pressed and `false` indicating it's not.
     */
    val metaKeys = arrayOf(false, false, false, false)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        if (enableBackground) {
            val p = parent as View
            blurDrawable?.setBounds(0, 0, p.width, p.height)
        }
        background = GradientDrawable().apply {
            if (enableBorder) setStroke(1, TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_FOREGROUND])
        }
    }

    fun changeFontSize(scale : Float) {
        font_size = if (1.0f < scale) min(font_size + 1, 30)
        else max(6, font_size - 1)
        setTextSize(font_size)
    }

    fun attachSession(index : Int) {
        topRow = 0
        mCombiningAccent = 0
        with(sessions[index]) {
            mEmulator = emulator
            currentSession = this
        }
        updateSize()
    }

    override fun onCreateInputConnection(outAttrs : EditorInfo) : InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        return object : BaseInputConnection(this, true) {
            override fun finishComposingText() : Boolean {
                super.finishComposingText()
                sendTextToTerminal(editable!!)
                editable!!.clear()
                return true
            }

            override fun commitText(text : CharSequence, newCursorPosition : Int) : Boolean {
                super.commitText(text, newCursorPosition)
                sendTextToTerminal(editable!!)
                editable!!.clear()
                return true
            }

            override fun deleteSurroundingText(leftLength : Int,
                                               rightLength : Int) : Boolean { // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                val deleteKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                for (i in 0 until leftLength) sendKeyEvent(deleteKey)
                return super.deleteSurroundingText(leftLength, rightLength)
            }

            fun sendTextToTerminal(text : CharSequence) {
                stopTextSelectionMode()
                val textLengthInChars = text.length
                var i = 0
                while (i < textLengthInChars) {
                    val firstChar = text[i]
                    var codePoint : Int
                    if (Character.isHighSurrogate(firstChar)) {
                        ++i
                        codePoint = if (i < textLengthInChars) Character.toCodePoint(firstChar, text[i])
                        else TerminalEmulator.UNICODE_REPLACEMENT_CHAR
                    } else codePoint = firstChar.code

                    if (metaKeys[SHIFT]) codePoint = Character.toUpperCase(codePoint)
                    var ctrlHeld = false
                    if (31 >= codePoint && 27 != codePoint) {
                        if ('\n'.code == codePoint) { // The AOSP keyboard and descendants seems to send \n as text when the enter Key is pressed,
                            // instead of a Key event like most other keyboard apps. A terminal expects \r for the enter
                            // Key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            codePoint = '\r'.code
                        } // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true
                        when (codePoint) {
                            31 -> codePoint = '_'.code
                            30 -> codePoint = '^'.code
                            29 -> codePoint = ']'.code
                            28 -> codePoint = '\\'.code
                            else -> codePoint += 96
                        }
                    }
                    inputCodePoint(codePoint, ctrlHeld, false)
                    i++
                }
            }
        }
    }

    fun onScreenUpdated() {
        val rowsInHistory = mEmulator.screen.activeTranscriptRows
        if (topRow < -rowsInHistory) topRow = -rowsInHistory
        if (isSelectingText) { // Do not scroll when selecting text.
            val rowShift = mEmulator.scrollCounter
            if (-topRow + rowShift > rowsInHistory) // .. unless we're hitting the end of history transcript, in which
            // case we abort text selection and scroll to end.
                stopTextSelectionMode()
            else { //skipScrolling = true;
                topRow -= rowShift
                decrementYTextSelectionCursors(rowShift)
            }
        }
        if (0 != topRow) topRow = 0 // Scroll down if not already there.

        mEmulator.clearScrollCounter()
        invalidate()
    }

    private fun setTextSize(textSize : Int) {
        mRenderer = TerminalRenderer(textSize)
        updateSize()
    }

    override fun onCheckIsTextEditor() = true

    override fun isOpaque() = true

    fun getColumnAndRow(event : MotionEvent, relativeToScroll : Boolean) : IntArray {
        val column = getCursorX(event.x)
        var row = getCursorY(event.y)
        if (!relativeToScroll) row -= topRow

        return intArrayOf(column, row)
    }

    fun getCursorX(x : Float) = (x / mRenderer.fontWidth).toInt()

    fun getCursorY(y : Float) = ((y - mRenderer.mFontLineSpacingAndAscent) / mRenderer.fontLineSpacing).toInt() + topRow

    fun getPointX(cx : Int) : Int = Math.round((if (cx > mEmulator.mColumns) mEmulator.mColumns else cx) * mRenderer.fontWidth)

    fun getPointY(cy : Int) = (cy - topRow) * mRenderer.fontLineSpacing

    private fun sendMouseEventCode(e : MotionEvent, button : Int, pressed : Boolean) {
        val columnAndRow = getColumnAndRow(e, false)
        var x = columnAndRow[0] + 1
        var y = columnAndRow[1] + 1
        if (pressed && (TerminalEmulator.MOUSE_WHEELDOWN_BUTTON == button || TerminalEmulator.MOUSE_WHEELUP_BUTTON == button)) {
            if (mMouseStartDownTime == e.downTime) {
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

    private fun doScroll(event : MotionEvent, rowsDown : Int) {
        val up = 0 > rowsDown
        val amount = abs(rowsDown)
        for (i in 0 until amount) {
            if (mEmulator.isMouseTrackingActive) sendMouseEventCode(event,
                                                                    if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON,
                                                                    true)
            else if (mEmulator.isAlternateBufferActive) // Send up and down Key events for scrolling, which is what some terminals do to make scroll work in
            // e.g. less, which shifts to the alt console without mouse handling.
                handleKeyCode(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
            else {
                topRow = min(0, max(-mEmulator.screen.activeTranscriptRows, (topRow + (if (up) -1 else 1))))
                invalidate()
            }
        }
    }

    override fun onGenericMotionEvent(event : MotionEvent) : Boolean {
        if (MotionEvent.ACTION_SCROLL == event.action && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            when (RotaryMode) {
                2 -> handleKeyCode(if (0 < delta) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN)


                1 -> handleKeyCode(if (0 < delta) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN)

                else -> doScroll(event, Math.round(delta * 15))
            }
        }
        return true
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        mGestureRecognizer.onTouchEvent(event)
        return true
    }

    override fun onKeyPreIme(keyCode : Int, event : KeyEvent) : Boolean {
        if (KeyEvent.KEYCODE_BACK == keyCode && isSelectingText) {
            stopTextSelectionMode()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode : Int, event : KeyEvent) : Boolean {
        if (isSelectingText) stopTextSelectionMode()
        val metaState = event.metaState
        val controlDown = event.isCtrlPressed || metaKeys[CTRL]
        val leftAltDown = 0 != (metaState and KeyEvent.META_ALT_LEFT_ON) || metaKeys[ALT]
        val shiftDown = event.isShiftPressed || metaKeys[SHIFT]
        val rightAltDownFromEvent = 0 != (metaState and KeyEvent.META_ALT_RIGHT_ON)
        var keyMod = 0
        if (controlDown) keyMod = keyMod or KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KEYMOD_NUM_LOCK // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod)) return true

        // Clear Ctrl since we handle that ourselves:
        val effectiveMetaState = getEffectiveMetaState(event, rightAltDownFromEvent, shiftDown)
        var result = event.getUnicodeChar(effectiveMetaState)

        if (0 == result) return false

        val oldCombiningAccent = mCombiningAccent
        if (0 != (result and KeyCharacterMap.COMBINING_ACCENT)) { // If entered combining accent previously, write it out:
            if (0 != mCombiningAccent) inputCodePoint(mCombiningAccent, controlDown, leftAltDown)
            mCombiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            if (0 != mCombiningAccent) {
                val combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result)
                if (0 < combinedChar) result = combinedChar
                mCombiningAccent = 0
            }
            inputCodePoint(result, controlDown, leftAltDown)
        }
        if (mCombiningAccent != oldCombiningAccent) invalidate()
        return true
    }

    private fun inputCodePoint(codePoint : Int, controlDownFromEvent : Boolean, leftAltDownFromEvent : Boolean) {
        var codePoint1 = codePoint // Ensure cursor is shown when a Key is pressed down like long hold on (arrow) keys
        val controlDown = controlDownFromEvent || metaKeys[CTRL]
        val altDown = leftAltDownFromEvent || metaKeys[ALT]

        if (controlDown) {
            codePoint1 = when (codePoint1) {
                in 'a'.code..'z'.code -> codePoint1 - 'a'.code + 1
                in 'A'.code..'Z'.code -> codePoint1 - 'A'.code + 1
                ' '.code, '2'.code -> 0
                '['.code, '3'.code -> 27
                '\\'.code, '4'.code -> 28
                ']'.code, '5'.code -> 29
                '^'.code, '6'.code -> 30
                '_'.code, '7'.code, '/'.code -> 31
                '8'.code -> 127 //DEL
                else -> codePoint1
            }
        }
        if (-1 < codePoint1) // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            currentSession.writeCodePoint(altDown, codePoint1)

    }

    private fun handleKeyCode(keyCode : Int,
                              keyMod : Int) : Boolean { // Ensure cursor is shown when a Key is pressed down like long hold on (arrow) keys
        if (handleKeyCodeAction(keyCode, keyMod)) return true
        val code = getCode(keyCode, keyMod, mEmulator.isCursorKeysApplicationMode, mEmulator.isKeypadApplicationMode) ?: return false
        currentSession.write(code)
        return true
    }

    private fun handleKeyCodeAction(keyCode : Int, keyMod : Int) : Boolean {
        val shiftDown = 0 != (keyMod and KEYMOD_SHIFT)
        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN ->                 // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    val time = SystemClock.uptimeMillis()
                    val motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                    doScroll(motionEvent, if (KeyEvent.KEYCODE_PAGE_UP == keyCode) -1 else 1)
                    motionEvent.recycle()
                    return true
                }
        }
        return false
    }

    private var firstrun = true

    override fun onSizeChanged(w : Int, h : Int, oldw : Int, oldh : Int) {
        if (w == 0 || h == 0) return
        if (firstrun) {
            addNewSession(false)
            firstrun = false
        } else updateSize()
        (background as GradientDrawable).cornerRadius = h * ConfigManager.cornerRadius / 100f
    }

    private fun updateSize() { // Set to 80 and 24 if you want to enable vttest.
        val newColumns = (width / mRenderer.fontWidth).toInt()
        val newRows = height / mRenderer.fontLineSpacing
        if (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows) {
            currentSession.updateSize(newColumns, newRows)
            topRow = 0
            scrollTo(0, 0)
            invalidate()
        }
    }

    override fun onDraw(canvas : Canvas) {
        if (firstrun) return
        updateBlurBackground(canvas)
        mRenderer.render(mEmulator, canvas, topRow)
        if (isSelectingText) updateSelHandles()
    }

    private fun startTextSelectionMode(event : MotionEvent) {
        if (!requestFocus()) return
        showTextSelectionCursor(event)
        invalidate()
    }

    fun stopTextSelectionMode() {
        if (hideTextSelectionCursor()) invalidate()
    }

    private fun getEffectiveMetaState(event : KeyEvent, rightAltDownFromEvent : Boolean, shiftDown : Boolean) : Int {
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAltDownFromEvent) bitsToClear =
            bitsToClear or (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON) // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:

        var effectiveMetaState = event.metaState and bitsToClear.inv()
        if (shiftDown) effectiveMetaState = effectiveMetaState or (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        if (metaKeys[FN]) effectiveMetaState = effectiveMetaState or KeyEvent.META_FUNCTION_ON
        return effectiveMetaState
    }

    fun onCopyTextToClipboard(text : String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text)
        clipboard.setPrimaryClip(clip)
    }

    fun onPasteTextFromClipboard() {
        val text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text ?: return
        mEmulator.paste(text)
    }

    fun showSoftKeyboard() = context.getSystemService(InputMethodManager::class.java).showSoftInput(this, 0)


    private fun updateBlurBackground(c : Canvas) {
        if (!enableBackground) return
        c.save()
        c.translate(-x, -y)
        blurDrawable?.draw(c)
        c.restore()
    }

    companion object {
        const val SHIFT = 0
        const val CTRL = 1
        const val ALT = 2
        const val FN = 3
    }
}
