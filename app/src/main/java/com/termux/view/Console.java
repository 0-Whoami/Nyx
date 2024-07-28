package com.termux.view;

import static android.view.KeyEvent.META_ALT_LEFT_ON;
import static com.termux.data.ConfigManager.CONFIG_PATH;
import static com.termux.terminal.KeyHandler.KEYMOD_ALT;
import static com.termux.terminal.KeyHandler.KEYMOD_CTRL;
import static com.termux.terminal.KeyHandler.KEYMOD_NUM_LOCK;
import static com.termux.terminal.KeyHandler.KEYMOD_SHIFT;
import static com.termux.terminal.KeyHandler.getCode;
import static com.termux.terminal.SessionManager.sessions;
import static com.termux.view.textselection.TextSelectionCursorController.isSelectingText;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.termux.data.ConfigManager;
import com.termux.data.Properties;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.textselection.TextSelectionCursorController;

import java.io.File;

/**
 * View displaying and interacting with a [TerminalSession].
 */
public final class Console extends View {
    private static final int SHIFT = 0;
    private static final int CTRL = 1;
    private static final int ALT = 2;
    private static final int FN = 3;
    /**
     * Array representing the state of meta keys (Shift, Ctrl, Alt, Fn).
     * Each element corresponds to a specific meta key, with `true` indicating it's pressed and `false` indicating it's not.
     */
    public final boolean[] metaKeys = {false, false, false, false};
    private final GestureDetector mGestureDetector;
    /**
     * The currently displayed terminal session, whose emulator is [.mEmulator].
     */
    public TerminalSession currentSession;
    public int RotaryMode;
    /**
     * Our terminal emulator whose session is [.mTermSession].
     */
    public TerminalEmulator mEmulator;
    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0.
     */
    public int topRow;
    private TextSelectionCursorController tsc;
    private Drawable blurDrawable;
    /**
     * What was left in from scrolling movement.
     */
    private float mScrollRemainder;
    /**
     * If non-zero, this is the last unicode code point received if that was a combining character.
     */
    private int mCombiningAccent;
    /**
     * Keep track of where mouse touch event started which we report as mouse scroll.
     */
    private int mMouseScrollStartX = -1;
    private int mMouseScrollStartY = -1;
    /**
     * Keep track of the time when a touch event leading to sending mouse scroll events started.
     */
    private long mMouseStartDownTime = -1;
    private int font_size = 14;
    private boolean notScrolledWithFinger;
    private boolean isAfterLongPress;

    public Console(final Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e) {
                if (isSelectingText) {
                    stopTextSelectionMode();
                } else {
                    requestFocus();
                    if (!mEmulator.isMouseTrackingActive()) {
                        showSoftKeyboard();
                    }
                }
                return true;
            }

            @Override
            public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, float dy) {
                if (isSelectingText) stopTextSelectionMode();
                notScrolledWithFinger = false;
                dy += mScrollRemainder;
                final var deltaRows = (int) (dy / Renderer.fontLineSpacing);
                mScrollRemainder = dy - deltaRows * Renderer.fontLineSpacing;
                doScroll(e2, deltaRows);
                return true;
            }

            @Override
            public void onLongPress(final MotionEvent e) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (!requestFocus()) return;
                ts().showTextSelectionCursor(e);
                invalidate();
            }
        });

        final Properties properties = new Properties(CONFIG_PATH + "/config");
        if (new File(ConfigManager.EXTRA_BLUR_BACKGROUND).exists() && properties.getBoolean("blur", true)) {
            final var p = (View) getParent();
            blurDrawable = Drawable.createFromPath(ConfigManager.EXTRA_BLUR_BACKGROUND);
            blurDrawable.setBounds(0, 0, p.getWidth(), p.getHeight());
        }
        final int radius = properties.getInt("corner", 0);
        if (properties.getBoolean("border", false) || radius != 0) {
            final var bg = new GradientDrawable();
            bg.setStroke(1, TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_FOREGROUND]);
            bg.setCornerRadius(radius);
            setBackground(bg);
        }
        font_size = properties.getInt("font_size", font_size);
        Renderer.setTypeface();
        Renderer.setTextSize(font_size);
    }

    public static int getCursorX(final float x) {
        return (int) (x / Renderer.fontWidth);
    }

    private TextSelectionCursorController ts() {
        if (null == tsc) tsc = new TextSelectionCursorController();
        return tsc;
    }

    public void changeFontSize(final boolean increase) {
        font_size = increase ? min(font_size + 1, 30) : max(6, font_size - 1);
        setTextSize();
    }

    public void attachSession(final int index) {
        topRow = 0;
        mCombiningAccent = 0;
        currentSession = sessions.get(index);
        mEmulator = currentSession.emulator;
        updateSize();
    }

    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        return new BaseInputConnection(this, true) {
            @Override
            public boolean finishComposingText() {
                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean commitText(final CharSequence text, final int newCursorPosition) {
                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean deleteSurroundingText(final int beforeLength, final int afterLength) {
                final KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < beforeLength; i++) sendKeyEvent(deleteKey);
                return true;
            }

            void sendTextToTerminal(final CharSequence text) {
                stopTextSelectionMode();
                final var textLengthInChars = text.length();
                for (int i = 0; i < textLengthInChars; i++) {
                    final var firstChar = text.charAt(i);
                    int codePoint;
                    if (Character.isHighSurrogate(firstChar)) {
                        ++i;
                        codePoint = ((i < textLengthInChars) ? Character.toCodePoint(firstChar, text.charAt(i)) : TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                    } else {
                        codePoint = firstChar;
                    }
                    if (metaKeys[SHIFT]) codePoint = Character.toUpperCase(codePoint);
                    var ctrlHeld = false;
                    if (31 >= codePoint && 27 != codePoint) {
                        if ('\n' == codePoint) codePoint = '\r';
                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true;
                        codePoint = switch (codePoint) {
                            case 31 -> '_';
                            case 30 -> '^';
                            case 29 -> ']';
                            case 28 -> '\\';
                            default -> codePoint + 96;
                        };
                    }
                    inputCodePoint(codePoint, ctrlHeld, false);
                }
            }
        };
    }

    public void onScreenUpdated() {
        final var rowsInHistory = mEmulator.screen.activeTranscriptRows;
        if (topRow < -rowsInHistory) topRow = -rowsInHistory;
        if (isSelectingText) { // Do not scroll when selecting text.
            final var rowShift = mEmulator.scrollCounter;
            if (-topRow + rowShift > rowsInHistory) // .. unless we're hitting the end of history transcript, in which
                // case we abort text selection and scroll to end.
                stopTextSelectionMode();
            else { //skipScrolling = true;
                topRow -= rowShift;
                TextSelectionCursorController.decrementYTextSelectionCursors(rowShift);
            }
        }
        if (0 != topRow) topRow = 0; // Scroll down if not already there.
        mEmulator.clearScrollCounter();
        invalidate();
    }

    private void setTextSize() {
        Renderer.setTextSize(font_size);
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

    public int[] getColumnAndRow(final MotionEvent event, final boolean relativeToScroll) {
        final int column = getCursorX(event.getX());
        int row = getCursorY(event.getY());
        if (!relativeToScroll) row -= topRow;

        return new int[]{column, row};
    }

    public int getCursorY(final float y) {
        return (int) ((y - Renderer.mFontLineSpacingAndAscent) / Renderer.fontLineSpacing) + topRow;
    }

    public int getPointX(final int cx) {
        return round(min(cx, mEmulator.mColumns) * Renderer.fontWidth);
    }

    public int getPointY(final int cy) {
        return (cy - topRow) * Renderer.fontLineSpacing;
    }

    private void sendMouseEventCode(final MotionEvent e, final int button, final boolean pressed) {
        final var columnAndRow = getColumnAndRow(e, false);
        var x = columnAndRow[0] + 1;
        var y = columnAndRow[1] + 1;
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

    private void doScroll(final MotionEvent event, final int rowsDown) {
        final var up = 0 > rowsDown;
        final var amount = abs(rowsDown);
        for (int i = 0; i < amount; i++) {
            if (mEmulator.isMouseTrackingActive())
                sendMouseEventCode(event, up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
            else if (mEmulator.isAlternateBufferActive())
                handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
            else {
                topRow = min(0, max(-mEmulator.screen.activeTranscriptRows, topRow + (up ? -1 : 1)));
                invalidate();
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        if (MotionEvent.ACTION_SCROLL == event.getAction() && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            final var delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            switch (RotaryMode) {
                case 0 -> doScroll(event, round(delta * 15));
                case 1 ->
                        handleKeyCode(0 < delta ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN);
                case 2 ->
                        handleKeyCode(0 < delta ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN);
            }
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> isAfterLongPress = false;
            case MotionEvent.ACTION_UP -> {
                if (isAfterLongPress) return true;
                mScrollRemainder = 0.0f;
                if (mEmulator.isMouseTrackingActive() && !isSelectingText && notScrolledWithFinger) { // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                    return true;
                }
                notScrolledWithFinger = true;
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (isSelectingText) stopTextSelectionMode();
        final var metaState = event.getMetaState();
        final var controlDown = event.isCtrlPressed() || metaKeys[CTRL];
        final var leftAltDown = 0 != (metaState & META_ALT_LEFT_ON) || metaKeys[ALT];
        final var shiftDown = event.isShiftPressed() || metaKeys[SHIFT];
        final var rightAltDownFromEvent = 0 != (metaState & KeyEvent.META_ALT_RIGHT_ON);
        var keyMod = 0;
        if (controlDown) keyMod |= KEYMOD_CTRL;
        if (event.isAltPressed() || leftAltDown) keyMod |= KEYMOD_ALT;
        if (shiftDown) keyMod |= KEYMOD_SHIFT;
        if (event.isNumLockOn())
            keyMod |= KEYMOD_NUM_LOCK; // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) return true;

        // Clear Ctrl since we handle that ourselves:
        final int effectiveMetaState = getEffectiveMetaState(event, rightAltDownFromEvent, shiftDown);
        var result = event.getUnicodeChar(effectiveMetaState);

        if (0 == result) return false;

        final var oldCombiningAccent = mCombiningAccent;
        if (0 != (result & KeyCharacterMap.COMBINING_ACCENT)) { // If entered combining accent previously, write it out:
            if (0 != mCombiningAccent) inputCodePoint(mCombiningAccent, controlDown, leftAltDown);
            mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
        } else {
            if (0 != mCombiningAccent) {
                final var combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
                if (0 < combinedChar) result = combinedChar;
                mCombiningAccent = 0;
            }
            inputCodePoint(result, controlDown, leftAltDown);
        }
        if (mCombiningAccent != oldCombiningAccent) invalidate();
        return true;
    }


    private void inputCodePoint(int codePoint, final boolean controlDownFromEvent, final boolean leftAltDownFromEvent) {
        // Ensure cursor is shown when a Key is pressed down like long hold on (arrow) keys
        final var controlDown = controlDownFromEvent || metaKeys[CTRL];
        final var altDown = leftAltDownFromEvent || metaKeys[ALT];

        if (controlDown) {
            if ('a' <= codePoint && 'z' >= codePoint) {
                codePoint = codePoint - 'a' + 1;
            } else if ('A' <= codePoint && 'Z' >= codePoint) {
                codePoint = codePoint - 'A' + 1;
            } else if (' ' == codePoint || '2' == codePoint) {
                codePoint = 0;
            } else if ('[' == codePoint || '3' == codePoint) {
                codePoint = 27; // ^[ (Esc)
            } else if ('\\' == codePoint || '4' == codePoint) {
                codePoint = 28;
            } else if (']' == codePoint || '5' == codePoint) {
                codePoint = 29;
            } else if ('^' == codePoint || '6' == codePoint) {
                codePoint = 30; // control-^
            } else if ('_' == codePoint || '7' == codePoint || '/' == codePoint) {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31;
            } else if ('8' == codePoint) {
                codePoint = 127; // DEL
            }
        }
        if (-1 < codePoint) // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            currentSession.writeCodePoint(altDown, codePoint);

    }

    private boolean handleKeyCode(final int keyCode, final int keyMod) { // Ensure cursor is shown when a Key is pressed down like long hold on (arrow) keys
        if (handleKeyCodeAction(keyCode, keyMod)) return true;
        final var code = getCode(keyCode, keyMod, mEmulator.isCursorKeysApplicationMode(), mEmulator.isKeypadApplicationMode());
        if (null == code) return false;
        currentSession.write(code);
        return true;
    }

    private boolean handleKeyCodeAction(final int keyCode, final int keyMod) {
        final var shiftDown = 0 != (keyMod & KEYMOD_SHIFT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP,
                 KeyEvent.KEYCODE_PAGE_DOWN -> {                // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    final var time = SystemClock.uptimeMillis();
                    final var motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0.0f, 0.0f, 0);
                    doScroll(motionEvent, KeyEvent.KEYCODE_PAGE_UP == keyCode ? -1 : 1);
                    motionEvent.recycle();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (null != mEmulator) updateSize();
    }

    private void updateSize() { // Set to 80 and 24 if you want to enable vttest.
        final var newColumns = (int) (getWidth() / Renderer.fontWidth);
        final var newRows = getHeight() / Renderer.fontLineSpacing;
        if (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows) {
            currentSession.updateSize(newColumns, newRows);
            topRow = 0;
            scrollTo(0, 0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        updateBlurBackground(canvas);
        if (null == mEmulator) return;
        Renderer.render(mEmulator, canvas, topRow);
        if (isSelectingText) ts().updateSelHandles();
    }

    public void stopTextSelectionMode() {
        if (ts().hideTextSelectionCursor()) invalidate();
    }


    private int getEffectiveMetaState(final KeyEvent event, final boolean rightAltDownFromEvent, final boolean shiftDown) {
        var bitsToClear = KeyEvent.META_CTRL_MASK;
        if (!rightAltDownFromEvent)
            bitsToClear |= (KeyEvent.META_ALT_ON | META_ALT_LEFT_ON); // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
        var effectiveMetaState = event.getMetaState() & ~bitsToClear;
        if (shiftDown) effectiveMetaState |= (KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
        if (metaKeys[FN]) effectiveMetaState |= KeyEvent.META_FUNCTION_ON;
        return effectiveMetaState;
    }

    public void onCopyTextToClipboard(final CharSequence text) {
        final var clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        final var clip = ClipData.newPlainText("", text);
        clipboard.setPrimaryClip(clip);
    }

    public void onPasteTextFromClipboard() {
        final var text = ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0);
        if (null != text) mEmulator.paste(text.getText().toString());
    }

    private void showSoftKeyboard() {
        getContext().getSystemService(InputMethodManager.class).showSoftInput(this, 0);
    }


    private void updateBlurBackground(final Canvas c) {
        if (null == blurDrawable) return;
        c.save();
        c.translate(-getX(), -getY());
        blurDrawable.draw(c);
        c.restore();
    }
}
