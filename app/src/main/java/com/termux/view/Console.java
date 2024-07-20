package com.termux.view;

import static android.view.KeyEvent.META_ALT_LEFT_ON;
import static com.termux.data.ConfigManager.CONFIG_PATH;
import static com.termux.terminal.KeyHandler.KEYMOD_ALT;
import static com.termux.terminal.KeyHandler.KEYMOD_CTRL;
import static com.termux.terminal.KeyHandler.KEYMOD_NUM_LOCK;
import static com.termux.terminal.KeyHandler.KEYMOD_SHIFT;
import static com.termux.terminal.KeyHandler.getCode;
import static com.termux.terminal.SessionManager.addNewSession;
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
    private final GestureAndScaleRecognizer mGestureRecognizer;
    private final int cornerRadius;
    /**
     * The currently displayed terminal session, whose emulator is [.mEmulator].
     */
    public TerminalSession currentSession;
    public int RotaryMode = 0;
    /**
     * Our terminal emulator whose session is [.mTermSession].
     */
    public TerminalEmulator mEmulator;
    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0.
     */
    public int topRow = 0;
    private TextSelectionCursorController tsc;
    private Drawable blurDrawable;
    private TerminalRenderer mRenderer;
    /**
     * What was left in from scrolling movement.
     */
    private float mScrollRemainder = 0f;
    /**
     * If non-zero, this is the last unicode code point received if that was a combining character.
     */
    private int mCombiningAccent = 0;
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

    public Console(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);

        mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {
            boolean scrolledWithFinger = false;

            @Override
            public void onUp(MotionEvent e) {
                mScrollRemainder = 0.0f;
                if (mEmulator.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText && !scrolledWithFinger) { // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                    sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                    return;
                }
                scrolledWithFinger = false;
            }

            @Override
            public void onSingleTapUp() {
                if (isSelectingText) {
                    stopTextSelectionMode();
                } else {
                    requestFocus();
                    if (!mEmulator.isMouseTrackingActive()) {
                        showSoftKeyboard();
                    }
                }
            }

            @Override
            public void onScroll(MotionEvent e2, Float dy) {
                if (isSelectingText) stopTextSelectionMode();
                scrolledWithFinger = true;
                dy += mScrollRemainder;
                final var deltaRows = (int) (dy / mRenderer.fontLineSpacing);
                mScrollRemainder = dy - deltaRows * mRenderer.fontLineSpacing;
                doScroll(e2, deltaRows);
            }

            @Override
            public void onScale(Float scale) {
                if (isSelectingText) return;
                changeFontSize(scale);
            }

            @Override
            public void onLongPress(MotionEvent e) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                startTextSelectionMode(e);
            }
        });

        Properties properties = new Properties(CONFIG_PATH + "/config");
        if (new File(ConfigManager.EXTRA_BLUR_BACKGROUND).exists() && properties.getBoolean("blur", true)) {
            final var p = (View) getParent();
            blurDrawable = Drawable.createFromPath(ConfigManager.EXTRA_BLUR_BACKGROUND);
            blurDrawable.setBounds(0, 0, p.getWidth(), p.getHeight());
        }
        setBackground(new GradientDrawable() {{
            if (properties.getBoolean("border", true))
                setStroke(1, TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_FOREGROUND]);
        }});
        cornerRadius = properties.getInt("corner_radius", 5);
        font_size = properties.getInt("font_size", font_size);
        mRenderer = new TerminalRenderer(font_size);
    }

    TextSelectionCursorController ts() {
        if (tsc == null) tsc = new TextSelectionCursorController();
        return tsc;
    }

    void changeFontSize(float scale) {
        font_size = (1.0f < scale) ? min(font_size + 1, 30) : max(6, font_size - 1);
        setTextSize(font_size);
    }

    public void attachSession(int index) {
        topRow = 0;
        mCombiningAccent = 0;
        currentSession = sessions.get(index);
        mEmulator = currentSession.emulator;
        updateSize();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        return new BaseInputConnection(this, true) {
            @Override
            public boolean finishComposingText() {
                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < beforeLength; i++) sendKeyEvent(deleteKey);
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            void sendTextToTerminal(CharSequence text) {
                stopTextSelectionMode();
                final var textLengthInChars = text.length();
                for (int i = 0; i < textLengthInChars; i++) {
                    final var firstChar = text.charAt(i);
                    int codePoint = Character.isHighSurrogate(firstChar) ? ((++i < textLengthInChars) ? Character.toCodePoint(firstChar, text.charAt(i)) : TerminalEmulator.UNICODE_REPLACEMENT_CHAR) : firstChar;
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
                ts().decrementYTextSelectionCursors(rowShift);
            }
        }
        if (0 != topRow) topRow = 0; // Scroll down if not already there.
        mEmulator.clearScrollCounter();
        invalidate();
    }

    private void setTextSize(int textSize) {
        mRenderer = new TerminalRenderer(textSize);
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

    public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
        final int column = getCursorX(event.getX());
        int row = getCursorY(event.getY());
        if (!relativeToScroll) row -= topRow;

        return new int[]{column, row};
    }

    public int getCursorX(float x) {
        return (int) (x / mRenderer.fontWidth);
    }


    public int getCursorY(float y) {
        return (int) ((y - mRenderer.mFontLineSpacingAndAscent) / mRenderer.fontLineSpacing) + topRow;
    }

    public int getPointX(int cx) {
        return round(min(cx, mEmulator.mColumns) * mRenderer.fontWidth);
    }

    public int getPointY(int cy) {
        return (cy - topRow) * mRenderer.fontLineSpacing;
    }

    private void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
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

    private void doScroll(MotionEvent event, int rowsDown) {
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
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (MotionEvent.ACTION_SCROLL == event.getAction() && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            final var delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            switch (RotaryMode) {
                case 1 ->
                        handleKeyCode(0 < delta ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN);
                case 2 ->
                        handleKeyCode(0 < delta ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN);
                default -> doScroll(event, round(delta * 15));
            }
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isSelectingText) stopTextSelectionMode();
        final var metaState = event.getMetaState();
        final var controlDown = event.isCtrlPressed() || metaKeys[CTRL];
        final var leftAltDown = 0 != (metaState & KeyEvent.META_ALT_LEFT_ON) || metaKeys[ALT];
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


    private void inputCodePoint(int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {
        // Ensure cursor is shown when a Key is pressed down like long hold on (arrow) keys
        final var controlDown = controlDownFromEvent || metaKeys[CTRL];
        final var altDown = leftAltDownFromEvent || metaKeys[ALT];

        if (controlDown) {
            if (codePoint >= 'a' && codePoint <= 'z') {
                codePoint = codePoint - 'a' + 1;
            } else if (codePoint >= 'A' && codePoint <= 'Z') {
                codePoint = codePoint - 'A' + 1;
            } else if (codePoint == ' ' || codePoint == '2') {
                codePoint = 0;
            } else if (codePoint == '[' || codePoint == '3') {
                codePoint = 27; // ^[ (Esc)
            } else if (codePoint == '\\' || codePoint == '4') {
                codePoint = 28;
            } else if (codePoint == ']' || codePoint == '5') {
                codePoint = 29;
            } else if (codePoint == '^' || codePoint == '6') {
                codePoint = 30; // control-^
            } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31;
            } else if (codePoint == '8') {
                codePoint = 127; // DEL
            }
        }
        if (-1 < codePoint) // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            currentSession.writeCodePoint(altDown, codePoint);

    }

    private boolean handleKeyCode(int keyCode, int keyMod) { // Ensure cursor is shown when a Key is pressed down like long hold on (arrow) keys
        if (handleKeyCodeAction(keyCode, keyMod)) return true;
        final var code = getCode(keyCode, keyMod, mEmulator.isCursorKeysApplicationMode(), mEmulator.isKeypadApplicationMode());
        if (code == null) return false;
        currentSession.write(code);
        return true;
    }

    private boolean handleKeyCodeAction(int keyCode, int keyMod) {
        final var shiftDown = 0 != (keyMod & KEYMOD_SHIFT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP,
                 KeyEvent.KEYCODE_PAGE_DOWN -> {                // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    final var time = SystemClock.uptimeMillis();
                    final var motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0);
                    doScroll(motionEvent, KeyEvent.KEYCODE_PAGE_UP == keyCode ? -1 : 1);
                    motionEvent.recycle();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        ((GradientDrawable) getBackground()).setCornerRadius(h * cornerRadius / 100f);
        if (mEmulator == null) addNewSession(false);
        else updateSize();
    }

    private void updateSize() { // Set to 80 and 24 if you want to enable vttest.
        final var newColumns = (int) (getWidth() / mRenderer.fontWidth);
        final var newRows = getHeight() / mRenderer.fontLineSpacing;
        if (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows) {
            currentSession.updateSize(newColumns, newRows);
            topRow = 0;
            scrollTo(0, 0);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateBlurBackground(canvas);
        mRenderer.render(mEmulator, canvas, topRow);
        if (isSelectingText) ts().updateSelHandles();
    }

    private void startTextSelectionMode(MotionEvent event) {
        if (!requestFocus()) return;
        ts().showTextSelectionCursor(event);
        invalidate();
    }

    public void stopTextSelectionMode() {
        if (ts().hideTextSelectionCursor()) invalidate();
    }


    private int getEffectiveMetaState(KeyEvent event, boolean rightAltDownFromEvent, boolean shiftDown) {
        var bitsToClear = KeyEvent.META_CTRL_MASK;
        if (!rightAltDownFromEvent)
            bitsToClear |= (KeyEvent.META_ALT_ON | META_ALT_LEFT_ON); // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
        var effectiveMetaState = event.getMetaState() & ~bitsToClear;
        if (shiftDown) effectiveMetaState |= (KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
        if (metaKeys[FN]) effectiveMetaState |= KeyEvent.META_FUNCTION_ON;
        return effectiveMetaState;
    }

    public void onCopyTextToClipboard(String text) {
        final var clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        final var clip = ClipData.newPlainText("", text);
        clipboard.setPrimaryClip(clip);
    }

    public void onPasteTextFromClipboard() {
        final var text = ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0);
        if (text != null) mEmulator.paste(text.getText().toString());
    }

    void showSoftKeyboard() {
        getContext().getSystemService(InputMethodManager.class).showSoftInput(this, 0);
    }


    private void updateBlurBackground(Canvas c) {
        if (blurDrawable == null) return;
        c.save();
        c.translate(-getX(), -getY());
        blurDrawable.draw(c);
        c.restore();
    }
}
