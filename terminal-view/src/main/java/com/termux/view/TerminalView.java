package com.termux.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Scroller;

import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.textselection.TextSelectionCursorController;

/**
 * View displaying and interacting with a {@link TerminalSession}.
 */
public final class TerminalView extends View {

    /**
     * The {@link KeyEvent} is generated from a non-physical device, like if 0 value is returned by {@link KeyEvent#getDeviceId()}.
     */
    private static final int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;
    private static final boolean readFnKey = false;
    private final int[] mDefaultSelectors = {-1, -1, -1, -1};
    private final GestureAndScaleRecognizer mGestureRecognizer;
    private final Scroller mScroller;
    /**
     * The currently displayed terminal session, whose emulator is {@link #mEmulator}.
     */
    public TerminalSession mTermSession;
    /**
     * Our terminal emulator whose session is {@link #mTermSession}.
     */
    public TerminalEmulator mEmulator;
    public TerminalRenderer mRenderer;
    private TerminalViewClient mClient;
    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0.
     */
    private int mTopRow;
    private float mScaleFactor = 1.0f;
    /**
     * What was left in from scrolling movement.
     */
    private float mScrollRemainder;
    /**
     * If non-zero, this is the last unicode code point received if that was a combining character.
     */
    private int mCombiningAccent;
    private TextSelectionCursorController mTextSelectionCursorController;

    // private final boolean mAccessibilityEnabled;
    /**
     * Define functions required for long hold toolbar.
     */
    private final Runnable mShowFloatingToolbar = () -> {
        if (null != this.getTextSelectionActionMode()) {
            // hide off.
            getTextSelectionActionMode().hide(0);
        }
    };
    /**
     * Keep track of where mouse touch event started which we report as mouse scroll.
     */
    private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;
    /**
     * Keep track of the time when a touch event leading to sending mouse scroll events started.
     */
    private long mMouseStartDownTime = -1;
    private int CURRENT_NAVIGATION_MODE;
    private boolean readShiftKey;
    private boolean ControlKeydown;
    private boolean readAltKey;

    public TerminalView(Context context, AttributeSet attributes) {
        // NO_UCD (unused code)
        super(context, attributes);
        setKeepScreenOn(true);
        mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {
            boolean scrolledWithFinger;

            @Override
            public void onUp(MotionEvent event) {
                mScrollRemainder = 0.0f;
                if (null != TerminalView.this.mEmulator && mEmulator.isMouseTrackingActive() && !event.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText() && !scrolledWithFinger) {
                    // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                    return;
                }
                scrolledWithFinger = false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (null == TerminalView.this.mEmulator)
                    return true;
                if (isSelectingText()) {
                    stopTextSelectionMode();
                    return true;
                }
                requestFocus();
                mClient.onSingleTapUp(event);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e, float distanceX, float distanceY) {
                if (null == TerminalView.this.mEmulator)
                    return true;
                if (mEmulator.isMouseTrackingActive() && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    // If moving with mouse pointer while pressing button, report that instead of scroll.
                    // This means that we never report moving with button press-events for touch input,
                    // since we cannot just start sending these events without a starting press event,
                    // which we do not do for touch input, only mouse in onTouchEvent().
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                } else {
                    scrolledWithFinger = true;
                    distanceY += mScrollRemainder;
                    int deltaRows = (int) (distanceY / mRenderer.mFontLineSpacing);
                    mScrollRemainder = distanceY - deltaRows * mRenderer.mFontLineSpacing;
                    doScroll(e, deltaRows);
                }
                return true;
            }

            @Override
            public boolean onScale(float focusX, float focusY, float scale) {
                if (null == TerminalView.this.mEmulator || isSelectingText())
                    return true;
                mScaleFactor *= scale;
                mScaleFactor = mClient.onScale(mScaleFactor);
                return true;
            }

            @Override
            public boolean onFling(final MotionEvent e1, final MotionEvent e2, float velocityX, float velocityY) {
                if (null == TerminalView.this.mEmulator)
                    return true;
                // Do not start scrolling until last fling has been taken care of:
                if (!mScroller.isFinished())
                    return true;
                final boolean mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive();
                final float SCALE = 0.25f;
                if (mouseTrackingAtStartOfFling) {
                    mScroller.fling(0, 0, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.mRows / 2, mEmulator.mRows / 2);
                } else {
                    mScroller.fling(0, mTopRow, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.getScreen().getActiveTranscriptRows(), 0);
                }
                if (100 < e2.getX() - Math.abs(e1.getX()) && 100 < Math.abs(velocityX) && Math.abs(e2.getX() - e1.getX()) > Math.abs(e2.getY() - e1.getY()))
                    mClient.onSwipe();
                post(new Runnable() {

                    private int mLastY;

                    @Override
                    public void run() {
                        if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive()) {
                            mScroller.abortAnimation();
                            return;
                        }
                        if (mScroller.isFinished())
                            return;
                        boolean more = mScroller.computeScrollOffset();
                        int newY = mScroller.getCurrY();
                        int diff = mouseTrackingAtStartOfFling ? (newY - mLastY) : (newY - mTopRow);
                        doScroll(e2, diff);
                        mLastY = newY;
                        if (more)
                            post(this);
                    }
                });
                return true;
            }

            @Override
            public boolean onDown(float x, float y) {
                // Why is true not returned here?
                // https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures
                // Although setting this to true still does not solve the following errors when long pressing in terminal view text area
                // ViewDragHelper: Ignoring pointerId=0 because ACTION_DOWN was not received for this pointer before ACTION_MOVE
                // Commenting out the call to mGestureDetector.onTouchEvent(event) in GestureAndScaleRecognizer#onTouchEvent() removes
                // the error logging, so issue is related to GestureDetector
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                // Do not treat is as a single confirmed tap - it may be followed by zoom.
                return false;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                if (mGestureRecognizer.isInProgress())
                    return;
                if (!isSelectingText()) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    startTextSelectionMode(event);
                }
            }
        });
        mScroller = new Scroller(context);
    }

    private static int getEffectiveMetaState(KeyEvent event, boolean rightAltDownFromEvent, boolean shiftDown) {
        int bitsToClear = KeyEvent.META_CTRL_MASK;
        if (!rightAltDownFromEvent) {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }
        int effectiveMetaState = event.getMetaState() & ~bitsToClear;
        if (shiftDown)
            effectiveMetaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (TerminalView.readFnKey)
            effectiveMetaState |= KeyEvent.META_FUNCTION_ON;
        return effectiveMetaState;
    }

    public boolean isReadShiftKey() {
        return readShiftKey;
    }

    public void setReadShiftKey(boolean readShiftKey) {
        this.readShiftKey = readShiftKey;
    }

    public boolean isControlKeydown() {
        return ControlKeydown;
    }

    public void setControlKeydown(boolean controlKeydown) {
        this.ControlKeydown = controlKeydown;
    }

    public boolean isReadAltKey() {
        return readAltKey;
    }

    public void setReadAltKey(boolean readAltKey) {
        this.readAltKey = readAltKey;
    }

    public void setTerminalViewClient(TerminalViewClient client) {
        this.mClient = client;
    }

    /**
     * Attach a {@link TerminalSession} to this view.
     *
     * @param session The {@link TerminalSession} this view will be displaying.
     */
    public boolean attachSession(TerminalSession session) {
        if (session == mTermSession)
            return false;
        mTopRow = 0;
        mTermSession = session;
        mEmulator = null;
        mCombiningAccent = 0;
        updateSize();
        // Wait with enabling the scrollbar until we have a terminal to get scroll position from.
        setVerticalScrollBarEnabled(true);
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // Ensure that inputType is only set if TerminalView is selected view with the keyboard and
        // an alternate view is not selected, like an EditText. This is necessary if an activity is
        // initially started with the alternate view or if activity is returned to from another app
        // and the alternate view was the one selected the last time.
//        if (mClient.isTerminalViewSelected()) {
//            // Using InputType.NULL is the most correct input type and avoids issues with other hacks.
//            //
//            // Previous keyboard issues:
//            // https://github.com/termux/termux-packages/issues/25
//            // https://github.com/termux/termux-app/issues/87.
//            // https://github.com/termux/termux-app/issues/126.
//            // https://github.com/termux/termux-app/issues/137 (japanese chars and TYPE_NULL).
//            outAttrs.inputType = InputType.TYPE_NULL;
//        } else {
//            // Corresponds to android:inputType="text"
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
//        }
        // Note that IME_ACTION_NONE cannot be used as that makes it impossible to input newlines using the on-screen
        // keyboard on Android TV (see https://github.com/termux/termux-app/issues/221).
        return new BaseInputConnection(this, true) {

            @Override
            public boolean finishComposingText() {
                super.finishComposingText();
                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {

                super.commitText(text, newCursorPosition);
                if (null == TerminalView.this.mEmulator)
                    return true;
                Editable content = getEditable();
                sendTextToTerminal(content);
                content.clear();
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {

                // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < leftLength; i++) sendKeyEvent(deleteKey);
                return super.deleteSurroundingText(leftLength, rightLength);
            }

            void sendTextToTerminal(CharSequence text) {
                stopTextSelectionMode();
                final int textLengthInChars = text.length();
                for (int i = 0; i < textLengthInChars; i++) {
                    char firstChar = text.charAt(i);
                    int codePoint;
                    if (Character.isHighSurrogate(firstChar)) {
                        ++i;
                        if (i < textLengthInChars) {
                            codePoint = Character.toCodePoint(firstChar, text.charAt(i));
                        } else {
                            // At end of string, with no low surrogate following the high:
                            codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                        }
                    } else {
                        codePoint = firstChar;
                    }
                    // Check onKeyDown() for details.
                    if (readShiftKey)
                        codePoint = Character.toUpperCase(codePoint);
                    boolean ctrlHeld = false;
                    if (31 >= codePoint && 27 != codePoint) {
                        if ('\n' == codePoint) {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            codePoint = '\r';
                        }
                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true;
                        switch (codePoint) {
                            case 31:
                                codePoint = '_';
                                break;
                            case 30:
                                codePoint = '^';
                                break;
                            case 29:
                                codePoint = ']';
                                break;
                            case 28:
                                codePoint = '\\';
                                break;
                            default:
                                codePoint += 96;
                                break;
                        }
                    }
                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
                }
            }
        };
    }

    @Override
    protected int computeVerticalScrollRange() {
        return null == this.mEmulator ? 1 : mEmulator.getScreen().getActiveRows();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return null == this.mEmulator ? 1 : mEmulator.mRows;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return null == this.mEmulator ? 1 : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
    }

    public void onScreenUpdated() {
        if (null == this.mEmulator)
            return;
        int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
        if (mTopRow < -rowsInHistory)
            mTopRow = -rowsInHistory;
        if (isSelectingText()) {
            // Do not scroll when selecting text.
            int rowShift = mEmulator.getScrollCounter();
            if (-mTopRow + rowShift > rowsInHistory) {
                // .. unless we're hitting the end of history transcript, in which
                // case we abort text selection and scroll to end.
                if (isSelectingText())
                    stopTextSelectionMode();
            } else {
                //skipScrolling = true;
                mTopRow -= rowShift;
                decrementYTextSelectionCursors(rowShift);
            }
        }
        if (0 != this.mTopRow) {
            // Scroll down if not already there.
            if (-3 > this.mTopRow) {
                // Awaken scroll bars only if scrolling a noticeable amount
                // - we do not want visible scroll bars during normal typing
                // of one row at a time.
                awakenScrollBars();
            }
            mTopRow = 0;
        }
        mEmulator.clearScrollCounter();
        invalidate();
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param textSize the new font size, in density-independent pixels.
     */
    public void setTextSize(int textSize) {
        mRenderer = new TerminalRenderer(textSize, null == this.mRenderer ? Typeface.MONOSPACE : mRenderer.mTypeface, null == this.mRenderer ? Typeface.MONOSPACE : mRenderer.mItalicTypeface);
        updateSize();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event            The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     *                         position into account. E.g. if scrolled 3 lines up and the event
     *                         position is in the top left, column will be -3 if relativeToScroll is
     *                         true and 0 if relativeToScroll is false.
     * @return Array with the column and row.
     */
    public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
        int column = (int) (event.getX() / mRenderer.mFontWidth);
        int row = (int) ((event.getY() - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);
        if (relativeToScroll) {
            row += mTopRow;
        }
        return new int[]{column, row};
    }

    /**
     * Send a single mouse event code to the terminal.
     */
    private void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
        int[] columnAndRow = getColumnAndRow(e, false);
        int x = columnAndRow[0] + 1;
        int y = columnAndRow[1] + 1;
        if (pressed && (TerminalEmulator.MOUSE_WHEELDOWN_BUTTON == button || TerminalEmulator.MOUSE_WHEELUP_BUTTON == button)) {
            if (mMouseStartDownTime == e.getDownTime()) {
                x = mMouseScrollStartX;
                y = mMouseScrollStartY;
            } else {
                mMouseStartDownTime = e.getDownTime();
                mMouseScrollStartX = x;
                mMouseScrollStartY = y;
            }
        }
        mEmulator.sendMouseEvent(button, x, y, pressed);
    }

    /**
     * Perform a scroll, either from dragging the screen or by scrolling a mouse wheel.
     */
    private void doScroll(MotionEvent event, int rowsDown) {
        boolean up = 0 > rowsDown;
        int amount = Math.abs(rowsDown);
        for (int i = 0; i < amount; i++) {
            if (mEmulator.isMouseTrackingActive()) {
                sendMouseEventCode(event, up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
            } else if (mEmulator.isAlternateBufferActive()) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt screen without mouse handling.
                handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
            } else {
                mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
                if (!awakenScrollBars())
                    invalidate();
            }
        }
    }

    public void setRotaryNavigationMode(int rotaryNavigationMode) {
        CURRENT_NAVIGATION_MODE = rotaryNavigationMode;
    }

    /**
     * Overriding .
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int event1;
        if (MotionEvent.ACTION_SCROLL == event.getAction() &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);

            switch (CURRENT_NAVIGATION_MODE) {
                default:
                    doScroll(event, Math.round(delta * 15));
                    return true;
                case 2:
                    event1 = 0 < delta ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
                    handleKeyCode(event1, KeyEvent.ACTION_DOWN);
                    return true;
                case 1:
                    event1 = 0 < delta ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                    handleKeyCode(event1, KeyEvent.ACTION_DOWN);
                    return true;
            }
        }
        return true;
    }

    // View parent = getRootView();
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (null == this.mEmulator)
            return false;
        final int action = event.getAction();
        if (isSelectingText()) {
            updateFloatingToolbarVisibility(event);
            mGestureRecognizer.onTouchEvent(event);
            return true;
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (MotionEvent.ACTION_DOWN == action)
                    showContextMenu();
                return true;
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (null != clipData) {
                    ClipData.Item clipItem = clipData.getItemAt(0);
                    if (null != clipItem) {
                        CharSequence text = clipItem.coerceToText(getContext());
                        if (!TextUtils.isEmpty(text))
                            mEmulator.paste(text.toString());
                    }
                }
            } else if (mEmulator.isMouseTrackingActive()) {
                // BUTTON_PRIMARY.
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, MotionEvent.ACTION_DOWN == event.getAction());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                        break;
                }
            }
        }
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode) {
            if (isSelectingText()) {
                stopTextSelectionMode();
                return true;
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Key presses in software keyboards will generally NOT trigger this listener, although some
     * may elect to do so in some situations. Do not rely on this to catch software key presses.
     * Gboard calls this when shouldEnforceCharBasedInput() is disabled (InputType.TYPE_NULL) instead
     * of calling commitText(), with deviceId=-1. However, Hacker's Keyboard, OpenBoard, LG Keyboard
     * call commitText().
     * <p>
     * This function may also be called directly without android calling it, like by
     * `TerminalExtraKeys` which generates a KeyEvent manually which uses {@link KeyCharacterMap#VIRTUAL_KEYBOARD}
     * as the device (deviceId=-1), as does Gboard. That would normally use mappings defined in
     * `/system/usr/keychars/Virtual.kcm`. You can run `dumpsys input` to find the `KeyCharacterMapFile`
     * used by virtual keyboard or hardware keyboard. Note that virtual keyboard device is not the
     * same as software keyboard, like Gboard, etc. Its a fake device used for generating events and
     * for testing.
     * <p>
     * We handle shift key in `commitText()` to convert codepoint to uppercase case there with a
     * call to {@link Character#toUpperCase(int)}, but here we instead rely on getUnicodeChar() for
     * conversion of keyCode, for both hardware keyboard shift key (via effectiveMetaState) and
     * `mClient.readShiftKey()`, based on value in kcm files.
     * This may result in different behaviour depending on keyboard and android kcm files set for the
     * InputDevice for the event passed to this function. This will likely be an issue for non-english
     * languages since `Virtual.kcm` in english only by default or at least in AOSP. For both hardware
     * shift key (via effectiveMetaState) and `mClient.readShiftKey()`, `getUnicodeChar()` is used
     * for shift specific behaviour which usually is to uppercase.
     * <p>
     * For fn key on hardware keyboard, android checks kcm files for hardware keyboards, which is
     * `Generic.kcm` by default, unless a vendor specific one is defined. The event passed will have
     * {@link KeyEvent#META_FUNCTION_ON} set. If the kcm file only defines a single character or unicode
     * code point `\\uxxxx`, then only one event is passed with that value. However, if kcm defines
     * a `fallback` key for fn or others, like `key DPAD_UP { ... fn: fallback PAGE_UP }`, then
     * android will first pass an event with original key `DPAD_UP` and {@link KeyEvent#META_FUNCTION_ON}
     * set. But this function will not consume it and android will pass another event with `PAGE_UP`
     * and {@link KeyEvent#META_FUNCTION_ON} not set, which will be consumed.
     * <p>
     * Now there are some other issues as well, firstly ctrl and alt flags are not passed to
     * `getUnicodeChar()`, so modified key values in kcm are not used. Secondly, if the kcm file
     * for other modifiers like shift or fn define a non-alphabet, like { fn: '\u0015' } to act as
     * DPAD_LEFT, the `getUnicodeChar()` will correctly return `21` as the code point but action will
     * not happen because the `handleKeyCode()` function that transforms DPAD_LEFT to `\033[D`
     * escape sequence for the terminal to perform the left action would not be called since its
     * called before `getUnicodeChar()` and terminal will instead get `21 0x15 Negative Acknowledgement`.
     * The solution to such issues is calling `getUnicodeChar()` before the call to `handleKeyCode()`
     * if user has defined a custom kcm file, like done in POC mentioned in #2237. Note that
     * Hacker's Keyboard calls `commitText()` so don't test fn/shift with it for this f<a href="unction.
     * ">* https://github.com/termux/term</a>ux-app/p<a href="ull/2237
     * ">* https://github.com/agnostic-apollo/termux-app/blob/terminal-code-point-custom-mapping/terminal-view/src/main/java/com/termux/view/T</a>erminalView.java
     * <p>
     * Key Character Map (kcm) and Key Layout (kl)<a href=" files info:
     * https://source.android.com/devices/input/key">...</a>-charact<a href="er-map-files
     * ">* https://source.android.com/devices/in</a>put/key-<a href="layout-files
     * ">* https://source.android.com/devices/in</a>put/keyboard-devices
     * AOSP kcm a<a href="nd">kl files:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks</a>/base/da<a href="ta/keyboards
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/packages/</a>InputDevices/res/raw
     * <p>
     * <a href="     * KeyCodes:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/an">...</a>droid/vi<a href="ew/KeyEvent.java
     * ">* https://cs.android.com/android/platform/superproject/+/master:frameworks/native/in</a>clude/android/keycodes.h
     * <p>
     * <a href="  * `dumpsys input`:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinge">...</a>r/reader/EventHub.cpp;l=1917
     * <p>
     * <a href="    * Loading of keymap:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputfl">...</a>inger/re<a href="ader/EventHub.cpp;l=1644
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/nat</a>ive/libs<a href="/input/Keyboard.cpp;l=41
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/n</a>ative/libs/input/InputDevice.cpp
     * OVERLAY keymaps for hardware keyboards <a href="may">be combined as well:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs</a>/input/K<a href="eyCharacterMap.cpp;l=165
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs</a>/input/KeyCharacterMap.cpp;l=831
     * <a href="*"><p>
     * Parse kcm file:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/</a>libs/input/KeyCharacterMap.cpp;l<a href="=727
     * ">* Parse key value:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/</a>libs/input/KeyCharacterMap.cpp;l=981
     * <p>
     * <a href="   * `KeyEvent.getUnicodeChar()`
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/cor">...</a>e/java/a<a href="ndroid/view/KeyEvent.java;l=2716
     * ">* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java</a>/android<a href="/view/KeyCharacterMap.java;l=368
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/jn</a>i/androi<a href="d_view_KeyCharacterMap.cpp;l=117
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/nat</a>ive/libs/input/KeyCharacterMap.cpp;l=231
     * <p>
     * Keyboard layouts advertised by applications, like for hardware keyboards via #ACTION_QUERY_KEYBOARD_LAYOUTS
     * Config is stored in `/<a href="data/system/input-manager-state.xml`
     * ">* http</a>s://github.com/ris58h/custom-keybo<a href="ard-layout
     * ">* Loading from apps:
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/</a>server/input/InputMa<a href="nagerService.java;l=1221
     * ">* Set:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/a</a>ndroid/h<a href="ardware/input/InputManager.java;l=89
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/an</a>droid/ha<a href="rdware/input/InputManager.java;l=543
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/apps/Settings/src/com/android/settings/inputme</a>thod/Key<a href="boardLayoutDialogFragment.java;l=167
     * ">* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/</a>server/i<a href="nput/InputManagerService.java;l=1385
     * ">* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/a</a>ndroid/server/input/PersistentDataStore.jav<a href="a
     * ">* Get overlay keyboard layout
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/</a>server/i<a href="nput/InputManagerService.java;l=2158
     * ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/services/core/jni/com_androi</a>d_server_input_InputManagerService.cpp;l=616
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (null == this.mEmulator)
            return true;
        if (isSelectingText()) {
            stopTextSelectionMode();
        }
        if (mClient.onKeyDown(keyCode, event, mTermSession)) {
            invalidate();
            return true;
        } else if (event.isSystem() && (KeyEvent.KEYCODE_BACK != keyCode)) {
            return super.onKeyDown(keyCode, event);
        }/* else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            mTermSession.write(event.getCharacters());
            return true;
        }*/
        final int metaState = event.getMetaState();
        final boolean controlDown = event.isCtrlPressed() || ControlKeydown;
        final boolean leftAltDown = 0 != (metaState & KeyEvent.META_ALT_LEFT_ON) || readAltKey;
        final boolean shiftDown = event.isShiftPressed() || readShiftKey;
        final boolean rightAltDownFromEvent = 0 != (metaState & KeyEvent.META_ALT_RIGHT_ON);
        int keyMod = 0;
        if (controlDown)
            keyMod |= KeyHandler.KEYMOD_CTRL;
        if (event.isAltPressed() || leftAltDown)
            keyMod |= KeyHandler.KEYMOD_ALT;
        if (shiftDown)
            keyMod |= KeyHandler.KEYMOD_SHIFT;
        if (event.isNumLockOn())
            keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
        // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {

            return true;
        }
        // Clear Ctrl since we handle that ourselves:
        int effectiveMetaState = getEffectiveMetaState(event, rightAltDownFromEvent, shiftDown);
        int result = event.getUnicodeChar(effectiveMetaState);

        if (0 == result) {
            return false;
        }
        int oldCombiningAccent = mCombiningAccent;
        if (0 != (result & KeyCharacterMap.COMBINING_ACCENT)) {
            // If entered combining accent previously, write it out:
            if (0 != this.mCombiningAccent)
                inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
            mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
        } else {
            if (0 != this.mCombiningAccent) {
                int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
                if (0 < combinedChar)
                    result = combinedChar;
                mCombiningAccent = 0;
            }
            inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
        }
        if (mCombiningAccent != oldCombiningAccent)
            invalidate();
        return true;
    }

    private void inputCodePoint(int eventSource, int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {

        if (null == this.mTermSession)
            return;
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        if (null != this.mEmulator)
            mEmulator.setCursorBlinkState(true);
        final boolean controlDown = controlDownFromEvent || ControlKeydown;
        final boolean altDown = leftAltDownFromEvent || readAltKey;
        if (mClient.onCodePoint(codePoint, controlDown, mTermSession))
            return;
        if (controlDown) {
            if ('a' <= codePoint && 'z' >= codePoint) {
                codePoint = codePoint - 'a' + 1;
            } else if ('A' <= codePoint && 'Z' >= codePoint) {
                codePoint = codePoint - 'A' + 1;
            } else if (' ' == codePoint || '2' == codePoint) {
                codePoint = 0;
            } else if ('[' == codePoint || '3' == codePoint) {
                // ^[ (Esc)
                codePoint = 27;
            } else if ('\\' == codePoint || '4' == codePoint) {
                codePoint = 28;
            } else if (']' == codePoint || '5' == codePoint) {
                codePoint = 29;
            } else if ('^' == codePoint || '6' == codePoint) {
                // control-^
                codePoint = 30;
            } else if ('_' == codePoint || '7' == codePoint || '/' == codePoint) {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31;
            } else if ('8' == codePoint) {
                // DEL
                codePoint = 127;
            }
        }
        if (-1 < codePoint) {
            // If not virtual or soft keyboard.
            if (KEY_EVENT_SOURCE_SOFT_KEYBOARD < eventSource) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect - the
                // desire to input the original characters should be low.
                switch (codePoint) {
                    case // SMALL TILDE.
                        0x02DC:
                        // TILDE (~).
                        codePoint = 0x007E;
                        break;
                    case // MODIFIER LETTER GRAVE ACCENT.
                        0x02CB:
                        // GRAVE ACCENT (`).
                        codePoint = 0x0060;
                        break;
                    case // MODIFIER LETTER CIRCUMFLEX ACCENT.
                        0x02C6:
                        // CIRCUMFLEX ACCENT (^).
                        codePoint = 0x005E;
                        break;
                }
            }
            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            this.mTermSession.writeCodePoint(altDown, codePoint);
        }
    }

    /**
     * Input the specified keyCode if applicable and return if the input was consumed.
     */
    public boolean handleKeyCode(final int keyCode, final int keyMod) {
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        if (null != mEmulator)
            this.mEmulator.setCursorBlinkState(true);
        if (this.handleKeyCodeAction(keyCode, keyMod))
            return true;
        final TerminalEmulator term = this.mTermSession.getEmulator();
        final String code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode());
        if (null == code)
            return false;
        this.mTermSession.write(code);
        return true;
    }

    private boolean handleKeyCodeAction(final int keyCode, final int keyMod) {
        final boolean shiftDown = 0 != (keyMod & KeyHandler.KEYMOD_SHIFT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    final long time = SystemClock.uptimeMillis();
                    final MotionEvent motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
                    this.doScroll(motionEvent, KeyEvent.KEYCODE_PAGE_UP == keyCode ? -1 : 1);
                    motionEvent.recycle();
                    return true;
                }
        }
        return false;
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event   A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {

        // Do not return for KEYCODE_BACK and send it to the client since user may be trying
        // to exit the activity.
        if (null == mEmulator && KeyEvent.KEYCODE_BACK != keyCode)
            return true;
        if (this.mClient.onKeyUp(keyCode, event)) {
            this.invalidate();
            return true;
        } else if (event.isSystem()) {
            // Let system key events through.
            return super.onKeyUp(keyCode, event);
        }
        return true;
    }

    /**
     * This is called during layout when the size of this view has changed. If you were just added to the view
     * hierarchy, you're called with the old values of 0.
     */
    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        this.updateSize();
    }

    /**
     * Check if the terminal size in rows and columns should be updated.
     */
    private void updateSize() {
        final int viewWidth = this.getWidth();
        final int viewHeight = this.getHeight();
        if (0 == viewWidth || 0 == viewHeight || null == mTermSession)
            return;
        // Set to 80 and 24 if you want to enable vttest.
        final int newColumns = Math.max(4, (int) (viewWidth / this.mRenderer.mFontWidth));
        final int newRows = Math.max(4, (viewHeight - this.mRenderer.mFontLineSpacingAndAscent) / this.mRenderer.mFontLineSpacing);
        if (null == mEmulator || (newColumns != this.mEmulator.mColumns || newRows != this.mEmulator.mRows)) {
            this.mTermSession.updateSize(newColumns, newRows, (int) this.mRenderer.getFontWidth(), this.mRenderer.getFontLineSpacing());
            this.mEmulator = this.mTermSession.getEmulator();
            // Update mTerminalCursorBlinkerRunnable inner class mEmulator on session change
            this.mTopRow = 0;
            this.scrollTo(0, 0);
            this.invalidate();
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        if (null == mEmulator) {
            canvas.drawColor(0XFF000000);
        } else {
            // render the terminal view and highlight any selected text
            final int[] sel = this.mDefaultSelectors;
            if (null != mTextSelectionCursorController) {
                this.mTextSelectionCursorController.getSelectors(sel);
            }
            this.mRenderer.render(this.mEmulator, canvas, this.mTopRow, sel[0], sel[1], sel[2], sel[3]);
            // render the text selection handles
            this.renderTextSelection();
        }
    }

    public TerminalSession getCurrentSession() {
        return this.mTermSession;
    }

    public int getCursorX(final float x) {
        return (int) (x / this.mRenderer.mFontWidth);
    }

    public int getCursorY(final float y) {
        return (int) (((y - 40) / this.mRenderer.mFontLineSpacing) + this.mTopRow);
    }

    public int getPointX(int cx) {
        if (cx > this.mEmulator.mColumns) {
            cx = this.mEmulator.mColumns;
        }
        return Math.round(cx * this.mRenderer.mFontWidth);
    }

    public int getPointY(final int cy) {
        return (cy - this.mTopRow) * this.mRenderer.mFontLineSpacing;
    }

    public int getTopRow() {
        return this.mTopRow;
    }

    public void setTopRow(final int mTopRow) {
        this.mTopRow = mTopRow;
    }


    /**
     * Define functions required for text selection and its handles.
     */
    private TextSelectionCursorController getTextSelectionCursorController() {
        if (null == mTextSelectionCursorController) {
            this.mTextSelectionCursorController = new TextSelectionCursorController(this);
            ViewTreeObserver observer = this.getViewTreeObserver();
            if (null != observer) {
                observer.addOnTouchModeChangeListener(this.mTextSelectionCursorController);
            }
        }
        return this.mTextSelectionCursorController;
    }

    private void showTextSelectionCursors(final MotionEvent event) {
        this.getTextSelectionCursorController().show(event);
    }

    private boolean hideTextSelectionCursors() {
        return this.getTextSelectionCursorController().hide();
    }

    private void renderTextSelection() {
        if (null != mTextSelectionCursorController)
            this.mTextSelectionCursorController.render();
    }

    private boolean isSelectingText() {
        if (null != mTextSelectionCursorController) {
            return this.mTextSelectionCursorController.isActive();
        } else {
            return false;
        }
    }

    /**
     * Unset the selected text stored before "MORE" button was pressed on the context menu.
     */
    private ActionMode getTextSelectionActionMode() {
        if (null != mTextSelectionCursorController) {
            return this.mTextSelectionCursorController.getActionMode();
        } else {
            return null;
        }
    }

    private void startTextSelectionMode(final MotionEvent event) {
        if (!this.requestFocus()) {
            return;
        }
        this.showTextSelectionCursors(event);

        this.invalidate();
    }

    public void stopTextSelectionMode() {
        if (this.hideTextSelectionCursors()) {

            this.invalidate();
        }
    }

    private void decrementYTextSelectionCursors(final int decrement) {
        if (null != mTextSelectionCursorController) {
            this.mTextSelectionCursorController.decrementYTextSelectionCursors(decrement);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (null != mTextSelectionCursorController) {
            this.getViewTreeObserver().addOnTouchModeChangeListener(this.mTextSelectionCursorController);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (null != mTextSelectionCursorController) {
            // Might solve the following exception
            // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
            this.stopTextSelectionMode();
            this.getViewTreeObserver().removeOnTouchModeChangeListener(this.mTextSelectionCursorController);
        }
    }

    private void showFloatingToolbar() {
        if (null != getTextSelectionActionMode()) {
            final int delay = ViewConfiguration.getDoubleTapTimeout();
            this.postDelayed(this.mShowFloatingToolbar, delay);
        }
    }

    private void hideFloatingToolbar() {
        if (null != getTextSelectionActionMode()) {
            this.removeCallbacks(this.mShowFloatingToolbar);
            this.getTextSelectionActionMode().hide(-1);
        }
    }

    public void updateFloatingToolbarVisibility(final MotionEvent event) {
        if (null != getTextSelectionActionMode()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    this.hideFloatingToolbar();
                    break;
                // fall through
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    this.showFloatingToolbar();
            }
        }
    }
}
