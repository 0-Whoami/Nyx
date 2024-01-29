package com.termux.view

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Scroller
import com.termux.R
import com.termux.app.Navigation
import com.termux.app.TermuxActivity
import com.termux.shared.view.KeyboardUtils
import com.termux.terminal.KeyHandler
import com.termux.terminal.KeyHandler.getCode
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.textselection.TextSelectionCursorController
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * View displaying and interacting with a [TerminalSession].
 */
class TerminalView(context: Context?, attributes: AttributeSet?) : View(context, attributes) {
    val mActivity = context as TermuxActivity
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

    private var CURRENT_FONTSIZE: Int = 12

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
     * Define functions required for long hold toolbar.
     */
    private val mShowFloatingToolbar = Runnable {
        textSelectionActionMode.hide(0)
    }

    /**
     * Keep track of where mouse touch event started which we report as mouse scroll.
     */
    private var mMouseScrollStartX = -1
    private var mMouseScrollStartY = -1

    /**
     * Keep track of the time when a touch event leading to sending mouse scroll events started.
     */
    private var mMouseStartDownTime: Long = -1
    private var CURRENT_NAVIGATION_MODE = 0
    var isReadShiftKey: Boolean = false
    var isControlKeydown: Boolean = false
    var isReadAltKey: Boolean = false

    init {
        // NO_UCD (unused code)
        keepScreenOn = true
        mGestureRecognizer =
            GestureAndScaleRecognizer(context, object : GestureAndScaleRecognizer.Listener {
                var scrolledWithFinger: Boolean = false

                override fun onUp(e: MotionEvent?) {
                    mScrollRemainder = 0.0f
                    if (mEmulator.isMouseTrackingActive && !e!!.isFromSource(
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

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (isSelectingText) {
                        stopTextSelectionMode()
                        return true
                    }
                    requestFocus()
                    if (!mEmulator.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                        KeyboardUtils.showSoftKeyboard(
                            context, this@TerminalView
                        )
                    }
                    return true
                }


                override fun onScroll(
                    e2: MotionEvent,
                    dx: Float,
                    dy: Float
                ): Boolean {
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
                    return true
                }

                override fun onScale(focusX: Float, focusY: Float, scale: Float): Boolean {
                    if (isSelectingText) return true
                    changeFontSize(scale)
                    return true
                }

                override fun onFling(
                    e: MotionEvent,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    // Do not start scrolling until last fling has been taken care of:
                    if (!mScroller.isFinished) return true
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
                    if (100 < e2.x - abs(e.x.toDouble()) && 100 < abs(velocityX.toDouble()) && abs(
                            (e2.x - e.x).toDouble()
                        ) > abs((e2.y - e.y).toDouble())
                    ) mActivity.supportFragmentManager.beginTransaction()
                        .add(R.id.compose_fragment_container, Navigation::class.java, null, "nav")
                        .commit()

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
                    return true
                }

                override fun onDown(x: Float, y: Float): Boolean {
                    // Why is true not returned here?
                    // https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures
                    // Although setting this to true still does not solve the following errors when long pressing in terminal view text area
                    // ViewDragHelper: Ignoring pointerId=0 because ACTION_DOWN was not received for this pointer before ACTION_MOVE
                    // Commenting out the call to mGestureDetector.onTouchEvent(event) in GestureAndScaleRecognizer#onTouchEvent() removes
                    // the error logging, so issue is related to GestureDetector
                    return false
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    // Do not treat is as a single confirmed tap - it may be followed by zoom.
                    return false
                }

                override fun onLongPress(e: MotionEvent?) {
                    if (mGestureRecognizer.isInProgress) return
                    if (!isSelectingText) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        startTextSelectionMode(e)
                    }
                }
            })
        mScroller = Scroller(context)
    }

    fun changeFontSize(scale: Float) {
        CURRENT_FONTSIZE = if (1.0f < scale)
            min(CURRENT_FONTSIZE + 1, 256)
        else
            max(1, CURRENT_FONTSIZE - 1)
        setTextSize(CURRENT_FONTSIZE)
    }

    /**
     * Attach a [TerminalSession] to this view.
     *
     * @param session The [TerminalSession] this view will be displaying.
     */
    fun attachSession(session: TerminalSession): Boolean {
        topRow = 0
        currentSession = session
        mCombiningAccent = 0
        updateSize()
        return true
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
                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false)
                    i++
                }
            }
        }
    }

    override fun computeVerticalScrollRange(): Int {
        return mEmulator.screen.activeRows
    }

    override fun computeVerticalScrollExtent(): Int {
        return mEmulator.mRows
    }

    override fun computeVerticalScrollOffset(): Int {
        return mEmulator.screen.activeRows + topRow - mEmulator.mRows
    }

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
        if (0 != this.topRow) {
            // Scroll down if not already there.
            if (-3 > this.topRow) {
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

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    override fun isOpaque(): Boolean {
        return true
    }

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
     * Perform a scroll, either from dragging the screen or by scrolling a mouse wheel.
     */
    private fun doScroll(event: MotionEvent?, rowsDown: Int) {
        val up = 0 > rowsDown
        val amount = abs(rowsDown.toDouble()).toInt()
        for (i in 0 until amount) {
            if (mEmulator.isMouseTrackingActive) {
                sendMouseEventCode(
                    event,
                    if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON,
                    true
                )
            } else if (mEmulator.isAlternateBufferActive) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt screen without mouse handling.
                handleKeyCode(if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 0)
            } else {
                topRow = min(
                    0.0,
                    max(
                        -mEmulator.screen.activeTranscriptRows.toDouble(),
                        (topRow + (if (up) -1 else 1)).toDouble()
                    )
                ).toInt()
                if (!awakenScrollBars()) invalidate()
            }
        }
    }

    fun setRotaryNavigationMode(rotaryNavigationMode: Int) {
        CURRENT_NAVIGATION_MODE = rotaryNavigationMode
    }

    /**
     * Overriding .
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val event1: Int
        if (MotionEvent.ACTION_SCROLL == event.action &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)

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

    // View parent = getRootView();
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        if (isSelectingText) {
            updateFloatingToolbarVisibility(event)
            mGestureRecognizer.onTouchEvent(event)
            return true
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (MotionEvent.ACTION_DOWN == action) showContextMenu()
                return true
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                val clipboardManager =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                if (null != clipData) {
                    val clipItem = clipData.getItemAt(0)
                    if (null != clipItem) {
                        val text = clipItem.coerceToText(context)
                        if (!TextUtils.isEmpty(text)) mEmulator.paste(text.toString())
                    }
                }
            } else if (mEmulator.isMouseTrackingActive) {
                // BUTTON_PRIMARY.
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> sendMouseEventCode(
                        event,
                        TerminalEmulator.MOUSE_LEFT_BUTTON,
                        MotionEvent.ACTION_DOWN == event.action
                    )

                    MotionEvent.ACTION_MOVE -> sendMouseEventCode(
                        event,
                        TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED,
                        true
                    )
                }
            }
        }
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

    /**
     * Key presses in software keyboards will generally NOT trigger this listener, although some
     * may elect to do so in some situations. Do not rely on this to catch software key presses.
     * Gboard calls this when shouldEnforceCharBasedInput() is disabled (InputType.TYPE_NULL) instead
     * of calling commitText(), with deviceId=-1. However, Hacker's Keyboard, OpenBoard, LG Keyboard
     * call commitText().
     *
     *
     * This function may also be called directly without android calling it, like by
     * `TerminalExtraKeys` which generates a KeyEvent manually which uses [KeyCharacterMap.VIRTUAL_KEYBOARD]
     * as the device (deviceId=-1), as does Gboard. That would normally use mappings defined in
     * `/system/usr/keychars/Virtual.kcm`. You can run `dumpsys input` to find the `KeyCharacterMapFile`
     * used by virtual keyboard or hardware keyboard. Note that virtual keyboard device is not the
     * same as software keyboard, like Gboard, etc. Its a fake device used for generating events and
     * for testing.
     *
     *
     * We handle shift key in `commitText()` to convert codepoint to uppercase case there with a
     * call to [Character.toUpperCase], but here we instead rely on getUnicodeChar() for
     * conversion of keyCode, for both hardware keyboard shift key (via effectiveMetaState) and
     * `mClient.readShiftKey()`, based on value in kcm files.
     * This may result in different behaviour depending on keyboard and android kcm files set for the
     * InputDevice for the event passed to this function. This will likely be an issue for non-english
     * languages since `Virtual.kcm` in english only by default or at least in AOSP. For both hardware
     * shift key (via effectiveMetaState) and `mClient.readShiftKey()`, `getUnicodeChar()` is used
     * for shift specific behaviour which usually is to uppercase.
     *
     *
     * For fn key on hardware keyboard, android checks kcm files for hardware keyboards, which is
     * `Generic.kcm` by default, unless a vendor specific one is defined. The event passed will have
     * [KeyEvent.META_FUNCTION_ON] set. If the kcm file only defines a single character or unicode
     * code point `\\uxxxx`, then only one event is passed with that value. However, if kcm defines
     * a `fallback` key for fn or others, like `key DPAD_UP { ... fn: fallback PAGE_UP }`, then
     * android will first pass an event with original key `DPAD_UP` and [KeyEvent.META_FUNCTION_ON]
     * set. But this function will not consume it and android will pass another event with `PAGE_UP`
     * and [KeyEvent.META_FUNCTION_ON] not set, which will be consumed.
     *
     *
     * Now there are some other issues as well, firstly ctrl and alt flags are not passed to
     * `getUnicodeChar()`, so modified key values in kcm are not used. Secondly, if the kcm file
     * for other modifiers like shift or fn define a non-alphabet, like { fn: '\u0015' } to act as
     * DPAD_LEFT, the `getUnicodeChar()` will correctly return `21` as the code point but action will
     * not happen because the `handleKeyCode()` function that transforms DPAD_LEFT to `\033[D`
     * escape sequence for the terminal to perform the left action would not be called since its
     * called before `getUnicodeChar()` and terminal will instead get `21 0x15 Negative Acknowledgement`.
     * The solution to such issues is calling `getUnicodeChar()` before the call to `handleKeyCode()`
     * if user has defined a custom kcm file, like done in POC mentioned in #2237. Note that
     * Hacker's Keyboard calls `commitText()` so don't test fn/shift with it for this f[* https://github.com/termux/term](unction.
      )ux-app/p[* https://github.com/agnostic-apollo/termux-app/blob/terminal-code-point-custom-mapping/terminal-view/src/main/java/com/termux/view/T](ull/2237
      )erminalView.java
     *
     *
     * Key Character Map (kcm) and Key Layout (kl)[...]( files info:
      https://source.android.com/devices/input/key)-charact[* https://source.android.com/devices/in](er-map-files
      )put/key-[* https://source.android.com/devices/in](layout-files
      )put/keyboard-devices
     * AOSP kcm a[kl files:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks](nd)/base/da[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/packages/](ta/keyboards
      )InputDevices/res/raw
     *
     *
     * [...](     * KeyCodes:
      https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/an)droid/vi[* https://cs.android.com/android/platform/superproject/+/master:frameworks/native/in](ew/KeyEvent.java
      )clude/android/keycodes.h
     *
     *
     * [...](  * `dumpsys input`:
      https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinge)r/reader/EventHub.cpp;l=1917
     *
     *
     * [...](    * Loading of keymap:
      https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputfl)inger/re[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/nat](ader/EventHub.cpp;l=1644
      )ive/libs[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/n](/input/Keyboard.cpp;l=41
      )ative/libs/input/InputDevice.cpp
     * OVERLAY keymaps for hardware keyboards [be combined as well:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs](may)/input/K[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs](eyCharacterMap.cpp;l=165
      )/input/KeyCharacterMap.cpp;l=831
     * [
 *
 *
 * Parse kcm file:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/](*)libs/input/KeyCharacterMap.cpp;l[* Parse key value:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/](=727
      )libs/input/KeyCharacterMap.cpp;l=981
     *
     *
     * [...](   * `KeyEvent.getUnicodeChar()`
    https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/cor)e/java/a[* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java](ndroid/view/KeyEvent.java;l=2716
      )/android[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/jn](/view/KeyCharacterMap.java;l=368
      )i/androi[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/nat](d_view_KeyCharacterMap.cpp;l=117
      )ive/libs/input/KeyCharacterMap.cpp;l=231
     *
     *
     * Keyboard layouts advertised by applications, like for hardware keyboards via #ACTION_QUERY_KEYBOARD_LAYOUTS
     * Config is stored in `/[* http](data/system/input-manager-state.xml`
      )s://github.com/ris58h/custom-keybo[* Loading from apps:
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/](ard-layout
      )server/input/InputMa[* Set:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/a](nagerService.java;l=1221
      )ndroid/h[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/an](ardware/input/InputManager.java;l=89
      )droid/ha[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/apps/Settings/src/com/android/settings/inputme](rdware/input/InputManager.java;l=543
      )thod/Key[* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/](boardLayoutDialogFragment.java;l=167
      )server/i[* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/a](nput/InputManagerService.java;l=1385
      )ndroid/server/input/PersistentDataStore.jav[* Get overlay keyboard layout
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/](a
      )server/i[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/services/core/jni/com_androi](nput/InputManagerService.java;l=2158
      )d_server_input_InputManagerService.cpp;l=616
     */
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
            if (0 != this.mCombiningAccent) inputCodePoint(
                event.deviceId,
                mCombiningAccent,
                controlDown,
                leftAltDown
            )
            mCombiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            if (0 != this.mCombiningAccent) {
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
        var codePoint = codePoint
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        mEmulator.setCursorBlinkState(true)
        val controlDown = controlDownFromEvent || isControlKeydown
        val altDown = leftAltDownFromEvent || isReadAltKey
        if (controlDown) {
            if (106 == codePoint &&  /* Ctrl+j or \n */
                !currentSession.isRunning
            ) {
                mActivity.termuxTerminalSessionClientBase.removeFinishedSession(currentSession)
                return
            }
        }
        if (controlDown) {
            if ('a'.code <= codePoint && 'z'.code >= codePoint) {
                codePoint = codePoint - 'a'.code + 1
            } else if ('A'.code <= codePoint && 'Z'.code >= codePoint) {
                codePoint = codePoint - 'A'.code + 1
            } else if (' '.code == codePoint || '2'.code == codePoint) {
                codePoint = 0
            } else if ('['.code == codePoint || '3'.code == codePoint) {
                // ^[ (Esc)
                codePoint = 27
            } else if ('\\'.code == codePoint || '4'.code == codePoint) {
                codePoint = 28
            } else if (']'.code == codePoint || '5'.code == codePoint) {
                codePoint = 29
            } else if ('^'.code == codePoint || '6'.code == codePoint) {
                // control-^
                codePoint = 30
            } else if ('_'.code == codePoint || '7'.code == codePoint || '/'.code == codePoint) {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31
            } else if ('8'.code == codePoint) {
                // DEL
                codePoint = 127
            }
        }
        if (-1 < codePoint) {
            // If not virtual or soft keyboard.
            if (KEY_EVENT_SOURCE_SOFT_KEYBOARD < eventSource) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect - the
                // desire to input the original characters should be low.
                when (codePoint) {
                    0x02DC ->                         // TILDE (~).
                        codePoint = 0x007E

                    0x02CB ->                         // GRAVE ACCENT (`).
                        codePoint = 0x0060

                    0x02C6 ->                         // CIRCUMFLEX ACCENT (^).
                        codePoint = 0x005E
                }
            }
            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            currentSession.writeCodePoint(altDown, codePoint)
        }
    }

    /**
     * Input the specified keyCode if applicable and return if the input was consumed.
     */
    fun handleKeyCode(keyCode: Int, keyMod: Int): Boolean {
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        mEmulator.setCursorBlinkState(true)
        if (this.handleKeyCodeAction(keyCode, keyMod)) return true
        val term = currentSession.emulator
        val code = getCode(
            keyCode,
            keyMod,
            term.isCursorKeysApplicationMode,
            term.isKeypadApplicationMode
        )
            ?: return false
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
                    this.doScroll(motionEvent, if (KeyEvent.KEYCODE_PAGE_UP == keyCode) -1 else 1)
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
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        this.updateSize()
    }

    /**
     * Check if the terminal size in rows and columns should be updated.
     */
    private fun updateSize() {
        val viewWidth = this.width
        val viewHeight = this.height
        if (0 == viewWidth || 0 == viewHeight) return
        // Set to 80 and 24 if you want to enable vttest.
        val newColumns = max(
            4.0,
            (viewWidth / mRenderer.fontWidth).toInt().toDouble()
        ).toInt()
        val newRows =
            4.coerceAtLeast((viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.fontLineSpacing)
        if (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows) {
            currentSession.updateSize(
                newColumns, newRows,
                mRenderer.fontWidth.toInt(),
                mRenderer.fontLineSpacing
            )
            this.mEmulator = currentSession.emulator
            // Update mTerminalCursorBlinkerRunnable inner class mEmulator on session change
            this.topRow = 0
            this.scrollTo(0, 0)
            this.invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // render the terminal view and highlight any selected text
        val sel = this.mDefaultSelectors
        mTextSelectionCursorController.getSelectors(sel)
        mRenderer.render(mEmulator, canvas, this.topRow, sel[0], sel[1], sel[2], sel[3])
        // render the text selection handles
        this.renderTextSelection()
    }

    fun getCursorX(x: Float): Int {
        return (x / mRenderer.fontWidth).toInt()
    }

    fun getCursorY(y: Float): Int {
        return (((y - 40) / mRenderer.fontLineSpacing) + this.topRow).toInt()
    }

    fun getPointX(cx: Int): Int {
        var cx = cx
        if (cx > mEmulator.mColumns) {
            cx = mEmulator.mColumns
        }
        return Math.round(cx * mRenderer.fontWidth)
    }

    fun getPointY(cy: Int): Int {
        return (cy - this.topRow) * mRenderer.fontLineSpacing
    }


    private val textSelectionCursorController: TextSelectionCursorController
        /**
         * Define functions required for text selection and its handles.
         */
        get() {
            return this.mTextSelectionCursorController
        }

    private fun showTextSelectionCursors(event: MotionEvent?) {
        textSelectionCursorController.show(event!!)
    }

    private fun hideTextSelectionCursors(): Boolean {
        return textSelectionCursorController.hide()
    }

    private fun renderTextSelection() {
        mTextSelectionCursorController.render()
    }


    private val textSelectionActionMode: ActionMode
        /**
         * Unset the selected text stored before "MORE" button was pressed on the context menu.
         */
        get() = mTextSelectionCursorController.actionMode

    private fun startTextSelectionMode(event: MotionEvent?) {
        if (!this.requestFocus()) {
            return
        }
        this.showTextSelectionCursors(event)

        this.invalidate()
    }

    fun stopTextSelectionMode() {
        if (this.hideTextSelectionCursors()) {
            this.invalidate()
        }
    }

    private fun decrementYTextSelectionCursors(decrement: Int) {
        mTextSelectionCursorController.decrementYTextSelectionCursors(decrement)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        this.viewTreeObserver.addOnTouchModeChangeListener(this.mTextSelectionCursorController)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Might solve the following exception
        // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
        this.stopTextSelectionMode()
        this.viewTreeObserver.removeOnTouchModeChangeListener(this.mTextSelectionCursorController)
    }

    private fun showFloatingToolbar() {
        val delay = ViewConfiguration.getDoubleTapTimeout()
        this.postDelayed(this.mShowFloatingToolbar, delay.toLong())
    }

    private fun hideFloatingToolbar() {
        this.removeCallbacks(this.mShowFloatingToolbar)
        textSelectionActionMode.hide(-1)
    }

    fun updateFloatingToolbarVisibility(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> this.hideFloatingToolbar()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> this.showFloatingToolbar()
        }
    }

    companion object {
        /**
         * The [KeyEvent] is generated from a non-physical device, like if 0 value is returned by [KeyEvent.getDeviceId].
         */
        private const val KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0
        private const val readFnKey = false
        private fun getEffectiveMetaState(
            event: KeyEvent,
            rightAltDownFromEvent: Boolean,
            shiftDown: Boolean
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
    }
}
