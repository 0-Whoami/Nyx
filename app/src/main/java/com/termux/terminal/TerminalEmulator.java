package com.termux.terminal;

import static com.termux.terminal.KeyHandler.getCodeFromTermcap;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BLINK;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BOLD;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_DIM;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
import static com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND;
import static com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR;
import static com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND;
import static com.termux.terminal.TextStyle.decodeEffect;
import static com.termux.terminal.TextStyle.encode;
import static com.termux.view.textselection.TextSelectionCursorController.selectors;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Renders text into a console. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 */
public final class TerminalEmulator {
    public static final int MOUSE_LEFT_BUTTON = 0;
    public static final int MOUSE_WHEELUP_BUTTON = 64;
    public static final int MOUSE_WHEELDOWN_BUTTON = 65;
    /**
     * Used for invalid data - [...](http://en.wikipedia.org/wiki/Replacement_character#Replacement_character)
     */
    public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;
    /* The supported terminal cursor styles. */
    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;
    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;
    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;
    /**
     * Mouse moving while having left mouse button pressed.
     */
    private static final int MOUSE_LEFT_BUTTON_MOVED = 32;
    /**
     * Escape processing: Not currently in an escape sequence.
     */
    private static final int ESC_NONE = 0;
    /**
     * Escape processing: Have seen an ESC character - proceed to [.doEsc]
     */
    private static final int ESC = 1;
    /**
     * Escape processing: Have seen ESC POUND
     */
    private static final int ESC_POUND = 2;
    /**
     * Escape processing: Have seen ESC and a character-set-select ( char
     */
    private static final int ESC_SELECT_LEFT_PAREN = 3;
    /**
     * Escape processing: Have seen ESC and a character-set-select ) char
     */
    private static final int ESC_SELECT_RIGHT_PAREN = 4;
    /**
     * Escape processing: "ESC [" or CSI (Control Sequence Introducer).
     */
    private static final int ESC_CSI = 6;
    /**
     * Escape processing: ESC [ ?
     */
    private static final int ESC_CSI_QUESTIONMARK = 7;
    /**
     * Escape processing: ESC [ $
     */
    private static final int ESC_CSI_DOLLAR = 8;
    /**
     * Escape processing: ESC ] (AKA OSC - Operating System Controls)
     */
    private static final int ESC_OSC = 10;
    /**
     * Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC
     */
    private static final int ESC_OSC_ESC = 11;
    /**
     * Escape processing: ESC [ >
     */
    private static final int ESC_CSI_BIGGERTHAN = 12;
    /**
     * Escape procession: "ESC P" or Device Control String (DCS)
     */
    private static final int ESC_P = 13;
    /**
     * Escape processing: CSI >
     */
    private static final int ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14;
    /**
     * Escape processing: CSI $ARGS ' '
     */
    private static final int ESC_CSI_ARGS_SPACE = 15;
    /**
     * Escape processing: CSI $ARGS '*'
     */
    private static final int ESC_CSI_ARGS_ASTERIX = 16;
    /**
     * Escape processing: CSI "
     */
    private static final int ESC_CSI_DOUBLE_QUOTE = 17;
    /**
     * Escape processing: CSI '
     */
    private static final int ESC_CSI_SINGLE_QUOTE = 18;
    /**
     * Escape processing: CSI !
     */
    private static final int ESC_CSI_EXCLAMATION = 19;
    /**
     * The number of parameter arguments. This name comes from the ANSI standard for terminal escape codes.
     */
    private static final int MAX_ESCAPE_PARAMETERS = 16;
    /**
     * Needs to be large enough to contain reasonable OSC 52 pastes.
     */
    private static final int MAX_OSC_STRING_LENGTH = 8192;
    /**
     * DECSET 1 - application cursor keys.
     */
    private static final int DECSET_BIT_APPLICATION_CURSOR_KEYS = 1;
    private static final int DECSET_BIT_REVERSE_VIDEO = 1 << 1;
    /**
     * [...](http://www.vt100.net/docs/vt510-rm/DECOM): "When DECOM is set, the home cursor position is at the upper-left
     * corner of the console, within the margins. The starting point for line numbers depends on the current top margin
     * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
     * upper-left corner of the console. The starting point for line numbers is independent of the margins. The cursor
     * can move outside of the margins."
     */
    private static final int DECSET_BIT_ORIGIN_MODE = 1 << 2;
    /**
     * [...](http://www.vt100.net/docs/vt510-rm/DECAWM): "If the DECAWM function is set, then graphic characters received when
     * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
     * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
     * characters received when the cursor is at the right border of the page replace characters already on the page."
     */
    private static final int DECSET_BIT_AUTOWRAP = 1 << 3;
    /**
     * DECSET 25 - if the cursor should be enabled, [.isCursorEnabled].
     */
    private static final int DECSET_BIT_CURSOR_ENABLED = 1 << 4;
    private static final int DECSET_BIT_APPLICATION_KEYPAD = 1 << 5;
    /**
     * DECSET 1000 - if to report mouse press&release events.
     */
    private static final int DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 << 6;
    /**
     * DECSET 1002 - like 1000, but report moving mouse while pressed.
     */
    private static final int DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 << 7;
    /**
     * DECSET 1004 - NOT implemented.
     */
    private static final int DECSET_BIT_SEND_FOCUS_EVENTS = 1 << 8;
    /**
     * DECSET 1006 - SGR-like mouse protocol (the modern sane choice).
     */
    private static final int DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 << 9;
    /**
     * DECSET 2004 - see [.paste]
     */
    private static final int DECSET_BIT_BRACKETED_PASTE_MODE = 1 << 10;
    /**
     * Toggled with DECLRMM - [...](http://www.vt100.net/docs/vt510-rm/DECLRMM)
     */
    private static final int DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 << 11;
    /**
     * Not really DECSET bit... - [...](http://www.vt100.net/docs/vt510-rm/DECSACE)
     */
    private static final int DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 << 12;
    private static final Pattern PATTERN = Pattern.compile("\r?\n");
    private static final Pattern REGEX = Pattern.compile("(\u001B|[\u0080-\u009F])");
    public final TerminalColors mColors = new TerminalColors();
    /**
     * The normal console buffer. Stores the characters that appear on the console of the emulated terminal.
     */
    private final TerminalBuffer mMainBuffer;
    /**
     * The alternate console buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate console buffer is active, you cannot scroll back to view saved lines).
     */
    private final TerminalBuffer mAltBuffer;
    /**
     * Holds the arguments of the current escape sequence.
     */
    private final int[] mArgs = new int[TerminalEmulator.MAX_ESCAPE_PARAMETERS];
    /**
     * Holds OSC and device control arguments, which can be strings.
     */
    private final StringBuilder mOSCOrDeviceControlArgs = new StringBuilder();
    private final SavedScreenState mSavedStateMain = new SavedScreenState();
    private final SavedScreenState mSavedStateAlt = new SavedScreenState();
    private final byte[] mUtf8InputBuffer = new byte[4];
    private final TerminalSession mSession;
    /**
     * The current console buffer, pointing at either [.mMainBuffer] or [.mAltBuffer].
     */
    public TerminalBuffer screen;
    /**
     * The terminal cursor styles.
     */
    public int cursorStyle;
    public int mColumns, mRows;
    /**
     * The cursor position. Between (0,0) and (mRows-1, mColumns-1).
     */
    public int mCursorRow;
    public int mCursorCol;
    /**
     * The number of scrolled lines since last calling [.clearScrollCounter]. Used for moving selection up along
     * with the scrolling text.
     */
    public int scrollCounter;
    /**
     * Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1.
     */
    private int mArgIndex;
    /**
     * True if the current escape sequence should continue, false if the current escape sequence should be terminated.
     * Used when parsing a single character.
     */
    private boolean mContinueSequence;
    /**
     * The current state of the escape sequence state machine. One of the ESC_* constants.
     */
    private int mEscapeState;
    /**
     * [...](http://www.vt100.net/docs/vt102-ug/table5-15.html)
     */
    private boolean mUseLineDrawingG0;
    private boolean mUseLineDrawingG1;
    private boolean mUseLineDrawingUsesG0 = true;
    private int mCurrentDecSetFlags;
    private int mSavedDecSetFlags;
    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private boolean mInsertMode;
    /**
     * An array of tab stops. mTabStop is true if there is a tab stop set for column i.
     */
    private boolean[] mTabStop;
    /**
     * Top margin of console for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
     * (Defines the first row after the scrolling region). Left/right margin in [0, mColumns].
     */
    private int mTopMargin;
    private int mBottomMargin;
    private int mLeftMargin;
    private int mRightMargin;
    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (mColumns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private boolean mAboutToAutoWrap;
    /**
     * Current foreground and background colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     * <p>
     * see TextStyle
     */
    private int mForeColor;
    private int mBackColor;
    /**
     * Current TextStyle effect.
     */
    private int mEffect;
    private byte mUtf8ToFollow;
    private byte mUtf8Index;
    private int mLastEmittedCodePoint = -1;

    public TerminalEmulator(TerminalSession session, int columns, int rows, int transcriptRows) {
        mSession = session;
        mColumns = columns;
        mRows = rows;
        mMainBuffer = new TerminalBuffer(columns, transcriptRows, rows);
        mAltBuffer = new TerminalBuffer(columns, rows, rows);
        mTabStop = new boolean[columns];
        screen = mMainBuffer;
        reset();
    }

    private static int mapDecSetBitToInternalBit(int decsetBit) {
        return switch (decsetBit) {
            case 1 -> TerminalEmulator.DECSET_BIT_APPLICATION_CURSOR_KEYS;
            case 5 -> TerminalEmulator.DECSET_BIT_REVERSE_VIDEO;
            case 6 -> TerminalEmulator.DECSET_BIT_ORIGIN_MODE;
            case 7 -> TerminalEmulator.DECSET_BIT_AUTOWRAP;
            case 25 -> TerminalEmulator.DECSET_BIT_CURSOR_ENABLED;
            case 66 -> TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD;
            case 69 -> TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE;
            case 1000 -> TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE;
            case 1002 -> TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT;
            case 1004 -> TerminalEmulator.DECSET_BIT_SEND_FOCUS_EVENTS;
            case 1006 -> TerminalEmulator.DECSET_BIT_MOUSE_PROTOCOL_SGR;
            case 2004 -> TerminalEmulator.DECSET_BIT_BRACKETED_PASTE_MODE;
            default -> -1;
        };
    }

    private boolean isDecsetInternalBitSet(int bit) {
        return 0 != (mCurrentDecSetFlags & bit);
    }

    private void setDecsetinternalBit(int internalBit, boolean set) {
        if (set) { // The mouse modes are mutually exclusive.
            if (TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE == internalBit)
                setDecsetinternalBit(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false);
            else if (TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT == internalBit)
                setDecsetinternalBit(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false);
        }
        mCurrentDecSetFlags = set ? mCurrentDecSetFlags | internalBit : mCurrentDecSetFlags & ~internalBit;
    }

    public boolean isAlternateBufferActive() {
        return screen == mAltBuffer;
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    public void sendMouseEvent(int mouseButton, int column, int row, boolean pressed) {
        column = (1 > column) ? 1 : min(column, mColumns);
        row = (1 > row) ? 1 : min(row, mRows);
        if (!(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED == mouseButton && !isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) && isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_PROTOCOL_SGR))
            mSession.write(String.format(Locale.US, "\u001b[<%d;%d;%d" + (pressed ? 'M' : 'm'), mouseButton, column, row));
        else { // 3 for release of all buttons.
            mouseButton = pressed ? mouseButton : 3;// Clip to console, and clip to the limits of 8-bit data.
            if (!(255 - 32 < column || 255 - 32 < row))
                mSession.write(new byte[]{'\u001b', '[', 'M', (byte) (32 + mouseButton), (byte) (32 + column), (byte) (32 + row)}, 6);
        }
    }

    public void resize(int columns, int rows) {
        if (mRows != rows) {
            mRows = rows;
            mTopMargin = 0;
            mBottomMargin = mRows;
        }
        if (mColumns != columns) {
            int oldColumns = mColumns;
            mColumns = columns;
            boolean[] oldTabStop = mTabStop;
            mTabStop = new boolean[mColumns];
            setDefaultTabStops();
            int toTransfer = min(oldColumns, columns);
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer);
            mLeftMargin = 0;
            mRightMargin = mColumns;
        }
        resizeScreen();
    }

    private void resizeScreen() {
        int[] cursor = {mCursorCol, mCursorRow};
        int newTotalRows = isAlternateBufferActive() ? mRows : mMainBuffer.mTotalRows;
        screen.resize(mColumns, mRows, newTotalRows, cursor, style(), isAlternateBufferActive());
        mCursorCol = cursor[0];
        mCursorRow = cursor[1];
    }

    private void setCursorRow(int row) {
        mCursorRow = row;
        mAboutToAutoWrap = false;
    }

    private void setCursorCol(int col) {
        mCursorCol = col;
        mAboutToAutoWrap = false;
    }

    public boolean isReverseVideo() {
        return isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_REVERSE_VIDEO);
    }

    public boolean shouldCursorBeVisible() {
        return isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_CURSOR_ENABLED);
    }

    public boolean isKeypadApplicationMode() {
        return isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD);
    }

    public boolean isCursorKeysApplicationMode() {
        return isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_CURSOR_KEYS);
    }

    /**
     * If mouse events are being sent as escape codes to the terminal.
     */
    public boolean isMouseTrackingActive() {
        return isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT);
    }

    private void setDefaultTabStops() {
        for (int i = 0; i < mColumns; i++)
            mTabStop[i] = 0 == (i & 7) && 0 != i;
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    void append(byte[] buffer, int length) {
        for (int i = 0; i < length; i++)
            processByte(buffer[i]);
    }

    /**
     * Called after getting data from session
     */
    private void processByte(byte byteToProcess) {
        if (0 < mUtf8ToFollow) {
            if (128 == (byteToProcess & 192)) { // 10xxxxxx, a continuation byte.
                mUtf8InputBuffer[mUtf8Index] = byteToProcess;
                mUtf8Index++;
                --mUtf8ToFollow;
                if (0 == mUtf8ToFollow) {
                    int firstByteMask = ((2 == mUtf8Index) ? 31 : ((3 == mUtf8Index) ? 15 : 7));
                    int codePoint = (mUtf8InputBuffer[0] & firstByteMask);
                    for (int i = 1; i < mUtf8Index; i++)
                        codePoint = (codePoint << 6) | (mUtf8InputBuffer[i] & 63);
                    if (((127 >= codePoint) && 1 < mUtf8Index) || (2047 > codePoint && 2 < mUtf8Index) || (65535 > codePoint && 3 < mUtf8Index))
                        codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;// Overlong encoding.
                    mUtf8Index = 0;
                    if (0x80 > codePoint || 0x9F < codePoint) {
                        switch (Character.getType(codePoint)) {
                            case Character.UNASSIGNED, Character.SURROGATE ->
                                    codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                        }
                        processCodePoint(codePoint);
                    }
                }
            } else { // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                mUtf8ToFollow = mUtf8Index = 0;
                emitCodePoint(TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                processByte(byteToProcess);
            }
        } else {
            if (0 == (byteToProcess & 128)) { // The leading bit is not set so it is a 7-bit ASCII character.
                processCodePoint(byteToProcess);
                return;
            } else if (192 == (byteToProcess & 224)) // 110xxxxx, a two-byte sequence.
                mUtf8ToFollow = 1;
            else if (224 == (byteToProcess & 240)) // 1110xxxx, a three-byte sequence.
                mUtf8ToFollow = 2;
            else if (240 == (byteToProcess & 248)) // 11110xxx, a four-byte sequence.
                mUtf8ToFollow = 3;
            else { // Not a valid UTF-8 sequence start, signal invalid data:
                processCodePoint(TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                return;
            }
            mUtf8InputBuffer[mUtf8Index] = byteToProcess;
            mUtf8Index++;
        }
    }

    private void processCodePoint(int b) {
        switch (b) {
            case 0 -> {
            }

            case 7 -> {
                if (TerminalEmulator.ESC_OSC == mEscapeState) doOsc(b);
            }

            case 8 -> {
                if (mLeftMargin == mCursorCol) { // Jump to previous line if it was auto-wrapped.
                    int previousRow = mCursorRow - 1;
                    if (0 <= previousRow && screen.getLineWrap(previousRow)) {
                        screen.clearLineWrap(previousRow);
                        setCursorRowCol(previousRow, mRightMargin - 1);
                    }
                } else setCursorCol(mCursorCol - 1);
            }

            case 9 -> mCursorCol = nextTabStop(1);

            case 10, 11, 12 -> doLinefeed();

            case 13 -> setCursorCol(mLeftMargin);

            case 14 -> mUseLineDrawingUsesG0 = false;
            case 15 -> mUseLineDrawingUsesG0 = true;
            case 24, 26 -> {
                if (TerminalEmulator.ESC_NONE != mEscapeState) { // FIXME: What is this??
                    mEscapeState = TerminalEmulator.ESC_NONE;
                    emitCodePoint(127);
                }
            }

            case 27 -> {
                if (TerminalEmulator.ESC_OSC == mEscapeState) doOsc(b);
                else startEscapeSequence();
            }


            default -> {
                mContinueSequence = false;
                switch (mEscapeState) {
                    case TerminalEmulator.ESC_NONE -> {
                        if (32 <= b) emitCodePoint(b);
                    }
                    case TerminalEmulator.ESC -> doEsc(b);
                    case TerminalEmulator.ESC_POUND -> doEscPound(b);
                    case TerminalEmulator.ESC_SELECT_LEFT_PAREN -> mUseLineDrawingG0 = ('0' == b);
                    case TerminalEmulator.ESC_SELECT_RIGHT_PAREN -> mUseLineDrawingG1 = ('0' == b);
                    case TerminalEmulator.ESC_CSI -> doCsi(b);
                    case TerminalEmulator.ESC_CSI_EXCLAMATION -> {
                        if ('p' == b)
                            reset();// Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                        else finishSequence();
                    }
                    case TerminalEmulator.ESC_CSI_QUESTIONMARK -> doCsiQuestionMark(b);
                    case TerminalEmulator.ESC_CSI_BIGGERTHAN -> doCsiBiggerThan(b);
                    case TerminalEmulator.ESC_CSI_DOLLAR -> {
                        boolean originMode = isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE);
                        int effectiveTopMargin = originMode ? mTopMargin : 0;
                        int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
                        int effectiveLeftMargin = originMode ? mLeftMargin : 0;
                        int effectiveRightMargin = originMode ? mRightMargin : mColumns;
                        switch (b) {
                            case 'v' -> {
                                int topSource = min(getArg(0, 1, true) - 1 + effectiveTopMargin, mRows);
                                int leftSource = min(getArg(1, 1, true) - 1 + effectiveLeftMargin, mColumns); // Inclusive, so do not subtract one:
                                int bottomSource = min(max(getArg(2, mRows, true) + effectiveTopMargin, topSource), mRows);
                                int rightSource = min(max(getArg(3, mColumns, true) + effectiveLeftMargin, leftSource), mColumns); // int sourcePage = getArg(4, 1, true);
                                int destionationTop = min(getArg(5, 1, true) - 1 + effectiveTopMargin, mRows);
                                int destinationLeft = min(getArg(6, 1, true) - 1 + effectiveLeftMargin, mColumns); // int destinationPage = getArg(7, 1, true);
                                int heightToCopy = min(mRows - destionationTop, bottomSource - topSource);
                                int widthToCopy = min(mColumns - destinationLeft, rightSource - leftSource);
                                screen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop);
                            }

                            case '{', 'x',
                                 'z' -> { // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                boolean erase = 'x' != b;
                                boolean selective = '{' == b; // Only DECSERA keeps visual attributes, DECERA does not:
                                boolean keepVisualAttributes = erase && selective;
                                int argIndex = 0;
                                int fillChar;
                                if (erase) {
                                    fillChar = ' ';
                                } else {
                                    fillChar = getArg(argIndex, -1, true);
                                    argIndex++;
                                }
                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if ((32 <= fillChar && 126 >= fillChar) || (160 <= fillChar && 255 >= fillChar)) { // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    int top = min(getArg(argIndex, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1);
                                    argIndex++;
                                    int left = min(getArg(argIndex, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1);
                                    argIndex++;
                                    int bottom = min(getArg(argIndex, mRows, true) + effectiveTopMargin, effectiveBottomMargin);
                                    argIndex++;
                                    int right = min(getArg(argIndex, mColumns, true) + effectiveLeftMargin, effectiveRightMargin);
                                    long style = style();
                                    for (int row = top - 1; row < bottom; row++)
                                        for (int col = left - 1; col < right; col++)
                                            if (!selective || 0 == (decodeEffect(screen.getStyleAt(row, col)) & CHARACTER_ATTRIBUTE_PROTECTED))
                                                screen.setChar(col, row, fillChar, keepVisualAttributes ? screen.getStyleAt(row, col) : style);
                                }
                            }
                            case 'r',
                                 't' -> { // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                boolean reverse = 't' == b;
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                int top = min(getArg0(1) - 1, effectiveBottomMargin) + effectiveTopMargin;
                                int left = min(getArg1(1) - 1, effectiveRightMargin) + effectiveLeftMargin;
                                int bottom = min(getArg(2, mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin;
                                int right = min(getArg(3, mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin;
                                if (4 <= mArgIndex) {
                                    if (mArgIndex >= mArgs.length)
                                        mArgIndex = mArgs.length - 1;
                                    for (int i = 4; i <= mArgIndex; i++) {
                                        boolean setOrClear = true;
                                        int bits = switch (getArg(i, 0, false)) {
                                            case 0 -> {
                                                if (!reverse) setOrClear = false;
                                                yield CHARACTER_ATTRIBUTE_BOLD | CHARACTER_ATTRIBUTE_UNDERLINE | CHARACTER_ATTRIBUTE_BLINK | CHARACTER_ATTRIBUTE_INVERSE;
                                            }

                                            case 1 -> CHARACTER_ATTRIBUTE_BOLD;
                                            case 4 -> CHARACTER_ATTRIBUTE_UNDERLINE;
                                            case 5 -> CHARACTER_ATTRIBUTE_BLINK;
                                            case 7 -> CHARACTER_ATTRIBUTE_INVERSE;
                                            case 22 -> {
                                                setOrClear = false;
                                                yield CHARACTER_ATTRIBUTE_BOLD;
                                            }

                                            case 24 -> {
                                                setOrClear = false;
                                                yield CHARACTER_ATTRIBUTE_UNDERLINE;
                                            }

                                            case 25 -> {
                                                setOrClear = false;
                                                yield CHARACTER_ATTRIBUTE_BLINK;
                                            }

                                            case 27 -> {
                                                setOrClear = false;
                                                yield CHARACTER_ATTRIBUTE_INVERSE;
                                            }
                                            default -> 0;
                                        };
                                        if (!reverse || setOrClear)
                                            screen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE), effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right);
                                    }
                                }
                            }
                            default -> finishSequence();
                        }
                    }
                    case TerminalEmulator.ESC_CSI_DOUBLE_QUOTE -> {
                        if ('q' == b) { // http://www.vt100.net/docs/vt510-rm/DECSCA
                            int arg = getArg0(0);
                            switch (arg) {
                                case 0, 2 -> // DECSED and DECSEL can erase characters.
                                        mEffect &= ~CHARACTER_ATTRIBUTE_PROTECTED;

                                case 1 -> // DECSED and DECSEL cannot erase characters.
                                        mEffect |= CHARACTER_ATTRIBUTE_PROTECTED;

                                default -> finishSequence();
                            }
                        } else finishSequence();
                    }

                    case TerminalEmulator.ESC_CSI_SINGLE_QUOTE -> {
                        switch (b) {
                            case '}' -> { // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                                int columnsAfterCursor = mRightMargin - mCursorCol;
                                int columnsToInsert = min(getArg0(1), columnsAfterCursor);
                                int columnsToMove = columnsAfterCursor - columnsToInsert;
                                screen.blockCopy(mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0);
                                blockClear(mCursorCol, 0, columnsToInsert, mRows);
                            }

                            case '~' -> { // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                                int columnsAfterCursor = mRightMargin - mCursorCol;
                                int columnsToDelete = min(getArg0(1), columnsAfterCursor);
                                int columnsToMove = columnsAfterCursor - columnsToDelete;
                                screen.blockCopy(mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0);
                            }
                            default -> finishSequence();
                        }
                    }

                    case 9 -> {
                    }

                    case TerminalEmulator.ESC_OSC -> doOsc(b);
                    case TerminalEmulator.ESC_OSC_ESC -> doOscEsc(b);
                    case TerminalEmulator.ESC_P -> doDeviceControl(b);
                    case TerminalEmulator.ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> {
                        if ('p' == b) { // Request DEC private mode (DECRQM).
                            int mode = getArg0(0);
                            int value = switch (mode) {
                                case 47, 1047, 1049 -> isAlternateBufferActive() ? 1 : 2;
                                default -> {
                                    int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(mode);
                                    yield (-1 == internalBit) ? 0 : (isDecsetInternalBitSet(internalBit) ? 1 : 2);
                                }
                            };
                            mSession.write(String.format(Locale.US, "\033[?%d;%d$y", mode, value));
                        } else finishSequence();
                    }

                    case TerminalEmulator.ESC_CSI_ARGS_SPACE -> {
                        int arg = getArg0(0);
                        switch (b) {
                            case 'q' -> {
                                switch (arg) {
                                    case 0, 1, 2 ->
                                            cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;

                                    case 3, 4 ->
                                            cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;

                                    case 5, 6 ->
                                            cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
                                }
                            }

                            case 't', 'u' -> {
                            }
                            default -> finishSequence();
                        }
                    }

                    case TerminalEmulator.ESC_CSI_ARGS_ASTERIX -> {
                        int attributeChangeExtent = getArg0(0);
                        if ('x' == b && (0 <= attributeChangeExtent && 2 >= attributeChangeExtent)) // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            setDecsetinternalBit(TerminalEmulator.DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, 2 == attributeChangeExtent);
                        else finishSequence();

                    }
                    default -> finishSequence();
                }
                if (!mContinueSequence) mEscapeState = TerminalEmulator.ESC_NONE;
            }
        }
    }

    /**
     * When in [.ESC_P] ("device control") sequence.
     */
    private void doDeviceControl(int b) {

        if ('\\' == b) // ESC \ terminates OSC
        // Sixel sequences may be very long. '$' and '!' are natural for breaking the sequence.
        {
            String dcs = mOSCOrDeviceControlArgs.toString(); // DCS $ q P t ST. Request Status String (DECRQSS)
            if (dcs.startsWith("$q")) {
                if ("$q\"p".equals(dcs)) // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                    mSession.write("\u001bP1$r64;1\"p\u001b\\");
                else finishSequence();
            } else if (dcs.startsWith("+q")) {
                for (String part : dcs.substring(2).split(";")) {
                    if (0 == part.length() % 2) {
                        StringBuilder transBuffer = new StringBuilder();
                        char c;
                        int length = part.length();
                        for (int i = 0; i < length; i += 2) {
                            try {
                                c = (char) Long.decode("0x" + part.charAt(i) + part.charAt(i + 1)).longValue();
                            } catch (Throwable t) {
                                continue;
                            }
                            transBuffer.append(c);
                        }
                        String trans = transBuffer.toString();
                        String responseValue = switch (trans) {
                            case "Co", "colors" -> "256";
                            case "TN", "name" -> "xterm";
                            default ->
                                    getCodeFromTermcap(trans, isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_CURSOR_KEYS), isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD));
                        };
                        if (null == responseValue)  // Respond with invalid request:
                            mSession.write("\033P0+r" + part + "\033\\");
                        else {
                            StringBuilder hexEncoded = new StringBuilder();
                            int length1 = responseValue.length();
                            for (int j = 0; j < length1; j++) {
                                hexEncoded.append(String.format(Locale.US, "%02X", (int) responseValue.charAt(j)));
                            }
                            mSession.write("\033P1+r" + part + "=" + hexEncoded + "\033\\");
                        }
                    }
                }
            }
            finishSequence();
        } else {
            if (TerminalEmulator.MAX_OSC_STRING_LENGTH < mOSCOrDeviceControlArgs.length()) { // Too long.
                mOSCOrDeviceControlArgs.setLength(0);
                finishSequence();
            } else {
                mOSCOrDeviceControlArgs.appendCodePoint(b);
                continueSequence(mEscapeState);
            }
        }
    }

    private int nextTabStop(int numTabs) {
        for (int i = mCursorCol + 1; i < mColumns; i++) {
            if (mTabStop[i]) {
                --numTabs;
                if (0 == numTabs) return min(i, mRightMargin);
            }
        }
        return mRightMargin - 1;
    }

    /**
     * Process byte while in the [.ESC_CSI_QUESTIONMARK] escape state.
     */
    private void doCsiQuestionMark(int b) {
        switch (b) {
            case 'J', 'K' -> {
                mAboutToAutoWrap = false;
                final char fillChar = ' ';
                var startCol = -1;
                var startRow = -1;
                var endCol = -1;
                var endRow = -1;
                boolean justRow = ('K' == b);
                switch (getArg0(0)) {
                    case 0 -> {
                        startCol = mCursorCol;
                        startRow = mCursorRow;
                        endCol = mColumns;
                        endRow = justRow ? (mCursorRow + 1) : mRows;
                    }

                    case 1 -> {
                        startCol = 0;
                        startRow = justRow ? mCursorRow : 0;
                        endCol = mCursorCol + 1;
                        endRow = mCursorRow + 1;
                    }

                    case 2 -> {
                        startCol = 0;
                        startRow = justRow ? mCursorRow : 0;
                        endCol = mColumns;
                        endRow = justRow ? (mCursorRow + 1) : mRows;
                    }

                    default -> finishSequence();
                }
                for (int row = startRow; row < endRow; row++) {
                    for (int col = startCol; col < endCol; col++) {
                        if (0 == (decodeEffect(screen.getStyleAt(row, col)) & CHARACTER_ATTRIBUTE_PROTECTED))
                            screen.setChar(col, row, fillChar, style());
                    }
                }
            }

            case 'h', 'l' -> {
                if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
                for (int i = 0; i <= mArgIndex; i++)
                    doDecSetOrReset('h' == b, mArgs[i]);
            }

            case 'n' -> {
                if (6 == getArg0(-1))  // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                    mSession.write(String.format(Locale.US, "\u001b[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1));
                else finishSequence();
            }


            case 'r', 's' -> {
                if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
                for (int i = 0; i <= mArgIndex; i++) {
                    int externalBit = mArgs[i];
                    int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(externalBit);
                    if (-1 != internalBit) {
                        if ('s' == b) mSavedDecSetFlags |= internalBit;
                        else
                            doDecSetOrReset(0 != (mSavedDecSetFlags & internalBit), externalBit);
                    }
                }
            }

            case '$' -> continueSequence(TerminalEmulator.ESC_CSI_QUESTIONMARK_ARG_DOLLAR);

            default -> parseArg(b);
        }
    }

    private void doDecSetOrReset(boolean setting, int externalBit) {
        int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(externalBit);
        if (-1 != internalBit) setDecsetinternalBit(internalBit, setting);

        switch (externalBit) {
            case 1, 4, 5, 7, 8, 9, 12, 25, 40, 45, 66, 1000, 1001, 1002, 1003, 1004, 1005, 1006,
                 1015, 1034, 2004 -> {
            }
            case 3 -> {
                mLeftMargin = mTopMargin = 0;
                mBottomMargin = mRows;
                mRightMargin = mColumns; // "DECCOLM resets vertical split console mode (DECLRMM) to unavailable":
                setDecsetinternalBit(TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false); // "Erases all data in page memory":
                blockClear(0, 0, mColumns, mRows);
                setCursorRowCol(0, 0);
            }

            case 6 -> {
                if (setting) setCursorPosition(0, 0);
            }

            case 69 -> {
                if (!setting) {
                    mLeftMargin = 0;
                    mRightMargin = mColumns;
                }
            }

            case 1048 -> {
                if (setting) saveCursor();
                else restoreCursor();
            }

            case 47, 1047,
                 1049 -> { // Set: Save cursor as in DECSC and use Alternate Console Buffer, clearing it first.
                // Reset: Use Normal Console Buffer and restore cursor as in DECRC.
                TerminalBuffer newScreen = setting ? mAltBuffer : mMainBuffer;
                if (newScreen != screen) {
                    boolean resized = !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows);
                    if (setting) saveCursor();
                    screen = newScreen;
                    if (!setting) {
                        int col = mSavedStateMain.mSavedCursorCol;
                        int row = mSavedStateMain.mSavedCursorRow;
                        restoreCursor();
                        if (resized) { // Restore cursor position _not_ clipped to current console (let resizeScreen() handle that):
                            mCursorCol = col;
                            mCursorRow = row;
                        }
                    } // Check if buffer size needs to be updated:
                    if (resized) resizeScreen(); // Clear new console if alt buffer:
                    if (newScreen == mAltBuffer)
                        newScreen.blockSet(0, 0, mColumns, mRows, ' ', style());
                }
            }

            default -> finishSequence();
        }
    }

    private void doCsiBiggerThan(int b) {

        switch (b) {
            case 'c' -> mSession.write("\u001b[>41;320;0c");
            case 'm' -> {
            }
            default -> parseArg(b);
        }
    }

    private void startEscapeSequence() {
        mEscapeState = TerminalEmulator.ESC;
        mArgIndex = 0;
        Arrays.fill(mArgs, -1);
    }

    private void doLinefeed() {
        var belowScrollingRegion = mCursorRow >= mBottomMargin;
        var newCursorRow = mCursorRow + 1;
        if (belowScrollingRegion) { // Move down (but not scroll) as long as we are above the last row.
            if (mCursorRow != mRows - 1) setCursorRow(newCursorRow);
        } else {
            if (newCursorRow == mBottomMargin) {
                scrollDownOneLine();
                newCursorRow = mBottomMargin - 1;
            }
            setCursorRow(newCursorRow);
        }
    }

    private void continueSequence(int state) {
        mEscapeState = state;
        mContinueSequence = true;
    }

    private void doEscPound(int b) {
        if ('8' == b) screen.blockSet(0, 0, mColumns, mRows, 'E', style());
        else finishSequence();
    }

    /**
     * Encountering a character in the [.ESC] state.
     */
    private void doEsc(int b) {
        switch (b) {
            case '#' -> continueSequence(TerminalEmulator.ESC_POUND);
            case '(' -> continueSequence(TerminalEmulator.ESC_SELECT_LEFT_PAREN);
            case ')' -> continueSequence(TerminalEmulator.ESC_SELECT_RIGHT_PAREN);
            case '6' -> {
                if (mCursorCol > mLeftMargin) mCursorCol--;
                else {
                    var rows = mBottomMargin - mTopMargin;
                    screen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin + 1, mTopMargin);
                    screen.blockSet(mLeftMargin, mTopMargin, 1, rows, ' ', encode(mForeColor, mBackColor, 0));
                }
            }

            case '7' -> saveCursor();
            case '8' -> restoreCursor();
            case '9' -> {
                if (mCursorCol < mRightMargin - 1) mCursorCol++;
                else {
                    var rows = mBottomMargin - mTopMargin;
                    screen.blockCopy(mLeftMargin + 1, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin, mTopMargin);
                    screen.blockSet(mRightMargin - 1, mTopMargin, 1, rows, ' ', encode(mForeColor, mBackColor, 0));
                }
            }

            case 'c' -> {
                reset();
                mMainBuffer.clearTranscript();
                blockClear(0, 0, mColumns, mRows);
                setCursorPosition(0, 0);
            }

            case 'D' -> doLinefeed();
            case 'E' -> {
                setCursorCol(isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE) ? mLeftMargin : 0);
                doLinefeed();
            }

            case 'F' -> setCursorRowCol(0, mBottomMargin - 1);
            case 'H' -> mTabStop[mCursorCol] = true;
            case 'M' -> {
                // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (mCursorRow <= mTopMargin) {
                    screen.blockCopy(0, mTopMargin, mColumns, mBottomMargin - (mTopMargin + 1), 0, mTopMargin + 1);
                    blockClear(0, mTopMargin, mColumns);
                } else mCursorRow--;
            }

            case 'N', '0' -> {
            }
            case 'P' -> {
                mOSCOrDeviceControlArgs.setLength(0);
                continueSequence(TerminalEmulator.ESC_P);
            }

            case '[' -> continueSequence(TerminalEmulator.ESC_CSI);

            case '=' -> setDecsetinternalBit(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD, true);
            case ']' -> {
                mOSCOrDeviceControlArgs.setLength(0);
                continueSequence(TerminalEmulator.ESC_OSC);
            }

            case '>' -> setDecsetinternalBit(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD, false);

            default -> finishSequence();
        }
    }

    /**
     * DECSC save cursor - [...](http://www.vt100.net/docs/vt510-rm/DECSC) . See [.restoreCursor].
     */
    private void saveCursor() {
        var state = isAlternateBufferActive() ? mSavedStateAlt : mSavedStateMain;
        state.mSavedCursorRow = mCursorRow;
        state.mSavedCursorCol = mCursorCol;
        state.mSavedEffect = mEffect;
        state.mSavedForeColor = mForeColor;
        state.mSavedBackColor = mBackColor;
        state.mSavedDecFlags = mCurrentDecSetFlags;
        state.mUseLineDrawingG0 = mUseLineDrawingG0;
        state.mUseLineDrawingG1 = mUseLineDrawingG1;
        state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0;
    }

    /**
     * DECRS restore cursor - [...](http://www.vt100.net/docs/vt510-rm/DECRC). See [.saveCursor].
     */
    private void restoreCursor() {
        var state = isAlternateBufferActive() ? mSavedStateAlt : mSavedStateMain;
        setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol);
        mEffect = state.mSavedEffect;
        mForeColor = state.mSavedForeColor;
        mBackColor = state.mSavedBackColor;
        final var mask = TerminalEmulator.DECSET_BIT_AUTOWRAP | TerminalEmulator.DECSET_BIT_ORIGIN_MODE;
        mCurrentDecSetFlags = (mCurrentDecSetFlags & ~mask) | (state.mSavedDecFlags & mask);
        mUseLineDrawingG0 = state.mUseLineDrawingG0;
        mUseLineDrawingG1 = state.mUseLineDrawingG1;
        mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0;
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". [.ESC_CSI].
     */
    private void doCsi(int b) {
        switch (b) {
            case '!' -> continueSequence(TerminalEmulator.ESC_CSI_EXCLAMATION);
            case '"' -> continueSequence(TerminalEmulator.ESC_CSI_DOUBLE_QUOTE);
            case '\'' -> continueSequence(TerminalEmulator.ESC_CSI_SINGLE_QUOTE);
            case '$' -> continueSequence(TerminalEmulator.ESC_CSI_DOLLAR);
            case '*' -> continueSequence(TerminalEmulator.ESC_CSI_ARGS_ASTERIX);
            case '@' -> { // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                mAboutToAutoWrap = false;
                var columnsAfterCursor = mColumns - mCursorCol;
                var spacesToInsert = min(getArg0(1), columnsAfterCursor);
                var charsToMove = columnsAfterCursor - spacesToInsert;
                screen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow);
                blockClear(mCursorCol, mCursorRow, spacesToInsert);
            }

            case 'A' -> setCursorRow(max(0, mCursorRow - getArg0(1)));

            case 'B' -> setCursorRow(min(mRows - 1, mCursorRow + getArg0(1)));

            case 'C', 'a' -> setCursorCol(min(mRightMargin - 1, mCursorCol + getArg0(1)));

            case 'D' -> setCursorCol(max(mLeftMargin, mCursorCol - getArg0(1)));

            case 'E' -> setCursorPosition(0, mCursorRow + getArg0(1));
            case 'F' -> setCursorPosition(0, mCursorRow - getArg0(1));
            case 'G' -> setCursorCol(min(max(1, getArg0(1)), mColumns) - 1);

            case 'H', 'f' -> setCursorPosition(getArg1(1) - 1, getArg0(1) - 1);
            case 'I' -> setCursorCol(nextTabStop(getArg0(1)));
            case 'J' -> {
                switch (getArg0(0)) {
                    case 0 -> {
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                        blockClear(0, mCursorRow + 1, mColumns, mRows - mCursorRow - 1);
                    }

                    case 1 -> {
                        blockClear(0, 0, mColumns, mCursorRow);
                        blockClear(0, mCursorRow, mCursorCol + 1);
                    }

                    case 2 -> blockClear(0, 0, mColumns, mRows);

                    case 3 -> mMainBuffer.clearTranscript();

                    default -> {
                        finishSequence();
                        return;
                    }
                }
                mAboutToAutoWrap = false;
            }

            case 'K' -> {
                switch (getArg0(0)) {
                    case 0 -> blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                    case 1 -> blockClear(0, mCursorRow, mCursorCol + 1);
                    case 2 -> blockClear(0, mCursorRow, mColumns);

                    default -> {
                        finishSequence();
                        return;
                    }
                }
                mAboutToAutoWrap = false;
            }

            case 'L' -> {
                var linesAfterCursor = mBottomMargin - mCursorRow;
                var linesToInsert = min(getArg0(1), linesAfterCursor);
                var linesToMove = linesAfterCursor - linesToInsert;
                screen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert);
                blockClear(0, mCursorRow, mColumns, linesToInsert);
            }

            case 'M' -> {
                mAboutToAutoWrap = false;
                var linesAfterCursor = mBottomMargin - mCursorRow;
                var linesToDelete = min(getArg0(1), linesAfterCursor);
                var linesToMove = linesAfterCursor - linesToDelete;
                screen.blockCopy(0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow);
                blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete);
            }

            case 'P' -> {
                mAboutToAutoWrap = false;
                var cellsAfterCursor = mColumns - mCursorCol;
                var cellsToDelete = min(getArg0(1), cellsAfterCursor);
                var cellsToMove = cellsAfterCursor - cellsToDelete;
                screen.blockCopy(mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow);
                blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete);
            }

            case 'S' -> {
                var linesToScroll = getArg0(1);
                for (int i = 0; i < linesToScroll; i++)
                    scrollDownOneLine();
            }

            case 'T' -> {
                if (0 == mArgIndex) {
                    var linesToScrollArg = getArg0(1);
                    var linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin;
                    var linesToScroll = min(linesBetweenTopAndBottomMargins, linesToScrollArg);
                    screen.blockCopy(0, mTopMargin, mColumns, linesBetweenTopAndBottomMargins - linesToScroll, 0, mTopMargin + linesToScroll);
                    blockClear(0, mTopMargin, mColumns, linesToScroll);
                } else finishSequence();
            }

            case 'X' -> {
                mAboutToAutoWrap = false;
                screen.blockSet(mCursorCol, mCursorRow, min(getArg0(1), mColumns - mCursorCol), 1, ' ', style());
            }

            case 'Z' -> {
                var numberOfTabs = getArg0(1);
                var newCol = mLeftMargin;
                for (int i = mCursorCol - 1; 0 <= i; i--) {
                    if (mTabStop[i]) {
                        --numberOfTabs;
                        if (0 == numberOfTabs) {
                            newCol = max(i, mLeftMargin);
                            break;
                        }
                    }
                }
                mCursorCol = newCol;
            }

            case '?' -> continueSequence(TerminalEmulator.ESC_CSI_QUESTIONMARK);
            case '>' -> continueSequence(TerminalEmulator.ESC_CSI_BIGGERTHAN);
            case '`' -> setCursorColRespectingOriginMode(getArg0(1) - 1);
            case 'b' -> {
                if (-1 == mLastEmittedCodePoint) break;
                var numRepeat = getArg0(1);
                for (int i = 0; i < numRepeat; i++) emitCodePoint(mLastEmittedCodePoint);
            }

            case 'c' -> {
                if (0 == getArg0(0)) mSession.write("\u001b[?64;1;2;6;9;15;18;21;22c");
            }

            case 'd' -> setCursorRow(min(max(1, getArg0(1)), mRows) - 1);

            case 'e' -> setCursorPosition(mCursorCol, mCursorRow + getArg0(1));
            case 'g' -> {
                switch (getArg0(0)) {
                    case 0 -> mTabStop[mCursorCol] = false;
                    case 3 -> {
                        for (int i = 0; i < mColumns; i++) mTabStop[i] = false;
                    }
                }
            }

            case 'h' -> doSetMode(true);
            case 'l' -> doSetMode(false);
            case 'm' -> selectGraphicRendition();
            case 'n' -> {
                switch (getArg0(0)) {
                    case 5 -> mSession.write(new byte[]{27, '[', '0', 'n'}, 4);
                    case 6 ->
                            mSession.write(String.format(Locale.US, "\u001b[%d;%dR", mCursorRow + 1, mCursorCol + 1));
                }
            }

            case 'r' -> {
                mTopMargin = max(0, min(getArg0(1) - 1, mRows - 2));
                mBottomMargin = max(mTopMargin + 2, min(getArg1(mRows), mRows));
                setCursorPosition(0, 0);
            }

            case 's' -> {
                if (isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) { // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                    mLeftMargin = min(getArg0(1) - 1, mColumns - 2);
                    mRightMargin = max(mLeftMargin + 1, min(getArg1(mColumns), mColumns)); // DECSLRM moves the cursor to column 1, line 1 of the page.
                    setCursorPosition(0, 0);
                } else saveCursor();
            }

            case 't' -> {
                switch (getArg0(0)) {
                    case 11 -> mSession.write("\u001b[1t");
                    case 13 -> mSession.write("\u001b[3;0;0t");
                    case 14 ->
                            mSession.write(String.format(Locale.US, "\u001b[4;%d;%dt", mRows * 12, mColumns * 12));

                    case 18 ->
                            mSession.write(String.format(Locale.US, "\u001b[8;%d;%dt", mRows, mColumns));
                    case 19 ->                         // We report the same size as the view, since it's the view really isn't resizable from the shell.
                            mSession.write(String.format(Locale.US, "\u001b[9;%d;%dt", mRows, mColumns));

                    case 20 -> mSession.write("\u001b]LIconLabel\u001b\\");
                    case 21 -> mSession.write("\u001b]l\u001b\\");
                }
            }

            case 'u' -> restoreCursor();
            case ' ' -> continueSequence(TerminalEmulator.ESC_CSI_ARGS_SPACE);
            default -> parseArg(b);
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see [...](http://en.wikipedia.org/wiki/ANSI_escape_code#graphics).
     */
    private void selectGraphicRendition() {
        if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
        for (int i = 0; i <= mArgIndex; i++) {
            var code = getArg(i, 0, false);
            if (0 > code) {
                if (0 < mArgIndex) {
                    i++;
                    continue;
                } else code = 0;
            }
            switch (code) {
                case 0 -> { // Reset attributes
                    mForeColor = COLOR_INDEX_FOREGROUND;
                    mBackColor = COLOR_INDEX_BACKGROUND;
                    mEffect = 0;
                }
                case 1 -> mEffect |= CHARACTER_ATTRIBUTE_BOLD;
                case 2 -> mEffect |= CHARACTER_ATTRIBUTE_DIM;
                case 3 -> mEffect |= CHARACTER_ATTRIBUTE_ITALIC;
                case 4 -> mEffect |= CHARACTER_ATTRIBUTE_UNDERLINE;
                case 5 -> mEffect |= CHARACTER_ATTRIBUTE_BLINK;
                case 7 -> mEffect |= CHARACTER_ATTRIBUTE_INVERSE;
                case 8 -> mEffect |= CHARACTER_ATTRIBUTE_INVISIBLE;
                case 9 -> mEffect |= CHARACTER_ATTRIBUTE_STRIKETHROUGH;
                case 22 -> mEffect &= ~(CHARACTER_ATTRIBUTE_BOLD | CHARACTER_ATTRIBUTE_DIM);
                case 23 -> mEffect &= ~CHARACTER_ATTRIBUTE_ITALIC;
                case 24 -> mEffect &= ~CHARACTER_ATTRIBUTE_UNDERLINE;
                case 25 -> mEffect &= ~CHARACTER_ATTRIBUTE_BLINK;
                case 27 -> mEffect &= ~CHARACTER_ATTRIBUTE_INVERSE;
                case 28 -> mEffect &= ~CHARACTER_ATTRIBUTE_INVISIBLE;
                case 29 -> mEffect &= ~CHARACTER_ATTRIBUTE_STRIKETHROUGH;
                case 39 -> mForeColor = COLOR_INDEX_FOREGROUND;
                default -> {
                    if (30 <= code && 37 >= code) mForeColor = code - 30;
                    else if (38 == code || 48 == code) {
                        if (i + 2 > mArgIndex) continue;
                        var firstArg = mArgs[i + 1];
                        switch (firstArg) {
                            case 2 -> {
                                if (i + 4 <= mArgIndex) {
                                    var red = getArg(i + 2, 0, false);
                                    var green = getArg(i + 3, 0, false);
                                    var blue = getArg(i + 4, 0, false);
                                    if (0 > red || 0 > green || 0 > blue || 255 < red || 255 < green || 255 < blue)
                                        finishSequence();
                                    else {
                                        int argbColor = 0xff000000 | (red << 16) | (green << 8) | blue;
                                        if (38 == code) mForeColor = argbColor;
                                        else mBackColor = argbColor;
                                    }
                                    i += 4;
                                }
                            }

                            case 5 -> {
                                var color = getArg(i + 2, 0, false);
                                i += 2;
                                if (0 <= color && TextStyle.NUM_INDEXED_COLORS > color) {
                                    if (38 == code) mForeColor = color;
                                    else mBackColor = color;
                                }
                            }
                            default -> finishSequence();
                        }
                    } else if (40 <= code && 47 >= code) { // Set background color.
                        mBackColor = code - 40;
                    } else if (49 == code) { // Set default background color.
                        mBackColor = COLOR_INDEX_BACKGROUND;
                    } else if (90 <= code && 97 >= code) { // Bright foreground colors (aixterm codes).
                        mForeColor = code - 90 + 8;
                    } else if (100 <= code && 107 >= code) { // Bright background color (aixterm codes).
                        mBackColor = code - 100 + 8;
                    }
                }
            }
        }
    }

    private void doOsc(int b) {
        switch (b) {
            case 7 -> doOscSetTextParameters("\u0007");
            case 27 -> continueSequence(TerminalEmulator.ESC_OSC_ESC);
            default -> collectOSCArgs(b);
        }
    }

    private void doOscEsc(int b) {
        if ('\\' == b) doOscSetTextParameters("\u001b\\");
        else { // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            collectOSCArgs(27);
            collectOSCArgs(b);
            continueSequence(TerminalEmulator.ESC_OSC);
        }
    }

    /**
     * An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.
     */
    private void doOscSetTextParameters(String bellOrStringTerminator) {
        var value = -1;
        var textParameter = ""; // Extract initial $value from initial "$value;..." string.
        final int length = mOSCOrDeviceControlArgs.length();
        for (int mOSCArgTokenizerIndex = 0; mOSCArgTokenizerIndex < length; mOSCArgTokenizerIndex++) {
            var b = mOSCOrDeviceControlArgs.charAt(mOSCArgTokenizerIndex);
            if (';' == b) {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1);
                break;
            } else if ('0' <= b && '9' >= b) value = ((0 > value) ? 0 : value * 10) + (b - '0');
            else {
                finishSequence();
                return;

            }
        }
        switch (value) {
            case 0, 1, 2, 119 -> {
            }

            case 4 -> {
                var colorIndex = -1;
                var parsingPairStart = -1;
                for (int i = 0; ; i++) {
                    var endOfInput = i == textParameter.length();
                    var b = endOfInput ? ';' : textParameter.charAt(i);
                    if (';' == b) {
                        if (0 > parsingPairStart) parsingPairStart = i + 1;
                        else {
                            if (0 > colorIndex || 255 < colorIndex) {
                                finishSequence();
                                return;
                            } else {
                                mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i));
                                colorIndex = -1;
                                parsingPairStart = -1;
                            }
                        }
                    } else if (0 > parsingPairStart && ('0' <= b && '9' >= b))
                        colorIndex = ((0 > colorIndex) ? 0 : colorIndex * 10) + (b - '0');
                    else {
                        finishSequence();
                        return;
                    }
                    if (endOfInput) break;
                }
            }

            case 10, 11, 12 -> {
                var specialIndex = COLOR_INDEX_FOREGROUND + (value - 10);
                var lastSemiIndex = 0;
                for (int charIndex = 0; ; charIndex++) {
                    var endOfInput = charIndex == textParameter.length();
                    if (endOfInput || ';' == textParameter.charAt(charIndex)) {
                        try {
                            var colorSpec = textParameter.substring(lastSemiIndex, charIndex);
                            if ("?".equals(colorSpec)) { // Report current color in the same format xterm and gnome-terminal does.
                                var rgb = mColors.mCurrentColors[specialIndex];
                                var r = (65535 * ((rgb & 0x00FF0000) >> 16)) / 255;
                                var g = (65535 * ((rgb & 0x0000FF00) >> 8)) / 255;
                                var b = (65535 * (rgb & 0x000000FF)) / 255;
                                mSession.write(String.format(Locale.US, "\u001B]" + value + ";rgb:%04x/%04x/%04x" + bellOrStringTerminator, r, g, b));
                            } else mColors.tryParseColor(specialIndex, colorSpec);

                            specialIndex++;
                            if (endOfInput || (COLOR_INDEX_CURSOR < specialIndex) || ++charIndex >= textParameter.length())
                                break;
                            lastSemiIndex = charIndex;
                        } catch (Throwable t) { // Ignore.
                        }
                    }
                }
            }

            case 52 -> {
                var startIndex = textParameter.indexOf(';') + 1;
                try {
                    var clipboardText = new String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8);
                    TerminalSession.onCopyTextToClipboard(clipboardText);
                } catch (Throwable ignored) {
                }
            }

            case 104 -> {
                if (textParameter.isEmpty()) mColors.reset();
                else {
                    var lastIndex = 0;
                    for (int charIndex = 0; ; charIndex++) {
                        var endOfInput = charIndex == textParameter.length();
                        if (endOfInput || ';' == textParameter.charAt(charIndex)) {
                            try {
                                var colorToReset = Integer.parseInt(textParameter.substring(lastIndex, charIndex));
                                mColors.reset(colorToReset);
                                if (endOfInput) break;
                                charIndex++;
                                lastIndex = charIndex;
                            } catch (Throwable t) { // Ignore.
                            }
                        }
                    }
                }
            }

            case 110, 111, 112 -> mColors.reset(COLOR_INDEX_FOREGROUND + (value - 110));
            default -> finishSequence();
        }
        finishSequence();
    }

    private void blockClear(int sx, int sy, int w) {
        blockClear(sx, sy, w, 1);
    }

    private void blockClear(int sx, int sy, int w, int h) {
        screen.blockSet(sx, sy, w, h, ' ', style());
    }

    private long style() {
        return encode(mForeColor, mBackColor, mEffect);
    }

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private void doSetMode(boolean newValue) {
        int modeBit = getArg0(0);
        switch (modeBit) {
            case 4 -> mInsertMode = newValue;
            case 34 -> {
            }
            default -> finishSequence();
        }
    }

    /**
     * NOTE: The parameters of this function respect the [.DECSET_BIT_ORIGIN_MODE]. Use
     * [.setCursorRowCol] for absolute pos.
     */
    private void setCursorPosition(int x, int y) {
        var originMode = isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE);
        var effectiveTopMargin = originMode ? mTopMargin : 0;
        var effectiveBottomMargin = originMode ? mBottomMargin : mRows;
        var effectiveLeftMargin = originMode ? mLeftMargin : 0;
        var effectiveRightMargin = originMode ? mRightMargin : mColumns;
        var newRow = max(effectiveTopMargin, min((effectiveTopMargin + y), (effectiveBottomMargin - 1)));
        var newCol = max(effectiveLeftMargin, min((effectiveLeftMargin + x), (effectiveRightMargin - 1)));
        setCursorRowCol(newRow, newCol);
    }

    private void scrollDownOneLine() {
        scrollCounter++;
        if (0 != mLeftMargin || mRightMargin != mColumns) { // Horizontal margin: Do not put anything into scroll history, just non-margin part of console up.
            screen.blockCopy(mLeftMargin, mTopMargin + 1, mRightMargin - mLeftMargin, mBottomMargin - mTopMargin - 1, mLeftMargin, mTopMargin); // .. and blank bottom row between margins:
            screen.blockSet(mLeftMargin, mBottomMargin - 1, mRightMargin - mLeftMargin, 1, ' ', mEffect);
        } else screen.scrollDownOneLine(mTopMargin, mBottomMargin, style());
    }

    /**
     * Process the next ASCII character of a parameter.
     * <p>
     * <p>
     * Parameter characters modify the action or interpretation of the sequence. You can use up to
     * 16 parameters per sequence. You must use the ; character to separate parameters.
     * All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     * <p>
     * [* https://vt100.net/docs/vt510-rm/chapter4.htm](
     * )l#S4.3.3
     */
    private void parseArg(int inputByte) {
        if ('0' <= inputByte && '9' >= inputByte) {
            if (mArgIndex < mArgs.length) {
                int oldValue = mArgs[mArgIndex];
                int thisDigit = inputByte - '0';
                int value = (0 <= oldValue) ? oldValue * 10 + thisDigit : thisDigit;
                if (9999 < value) value = 9999;
                mArgs[mArgIndex] = value;
            }
            continueSequence(mEscapeState);
        } else if (';' == inputByte) {
            if (mArgIndex < mArgs.length) mArgIndex++;
            continueSequence(mEscapeState);
        } else finishSequence();

    }

    private int getArg0(int defaultValue) {
        return getArg(0, defaultValue, true);
    }

    private int getArg1(int defaultValue) {
        return getArg(1, defaultValue, true);
    }

    private int getArg(int index, int defaultValue, boolean treatZeroAsDefault) {
        var result = mArgs[index];
        if (0 > result || (0 == result && treatZeroAsDefault)) result = defaultValue;
        return result;
    }

    private void collectOSCArgs(int b) {
        if (TerminalEmulator.MAX_OSC_STRING_LENGTH > mOSCOrDeviceControlArgs.length()) {
            mOSCOrDeviceControlArgs.appendCodePoint(b);
            continueSequence(mEscapeState);
        } else finishSequence();

    }

    private void finishSequence() {
        mEscapeState = TerminalEmulator.ESC_NONE;
    }

    /**
     * Send a Unicode code point to the console.
     *
     * @param codePoint The code point of the character to display
     */
    private void emitCodePoint(int codePoint) {
        mLastEmittedCodePoint = codePoint;
        if (mUseLineDrawingUsesG0 ? mUseLineDrawingG0 : mUseLineDrawingG1) { // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            codePoint = switch (codePoint) {
                case '_' ->                     // Blank.
                        ' ';

                case '`' ->                     // Diamond.
                        '◆';

                case '0' ->                     // Solid block;
                        '█';

                case 'a' ->                     // Checker board.
                        '▒';

                case 'b' ->                     // Horizontal tab.
                        '␉';

                case 'c' ->                     // Form feed.
                        '␌';

                case 'd' ->                     // Carriage return.
                        '\r';

                case 'e' ->                     // Linefeed.
                        '␊';

                case 'f' ->                     // Degree.
                        '°';

                case 'g' ->                     // Plus-minus.
                        '±';

                case 'h' ->                     // Newline.
                        '\n';

                case 'i' ->                     // Vertical tab.
                        '␋';

                case 'j' ->                     // Lower right corner.
                        '┘';

                case 'k' ->                     // Upper right corner.
                        '┐';

                case 'l' ->                     // Upper left corner.
                        '┌';

                case 'm' ->                     // Left left corner.
                        '└';

                case 'n' ->                     // Crossing lines.
                        '┼';

                case 'o' ->                     // Horizontal line - scan 1.
                        '⎺';

                case 'p' ->                     // Horizontal line - scan 3.
                        '⎻';

                case 'q' ->                     // Horizontal line - scan 5.
                        '─';

                case 'r' ->                     // Horizontal line - scan 7.
                        '⎼';

                case 's' ->                     // Horizontal line - scan 9.
                        '⎽';

                case 't' ->                     // T facing rightwards.
                        '├';

                case 'u' ->                     // T facing leftwards.
                        '┤';

                case 'v' ->                     // T facing upwards.
                        '┴';

                case 'w' ->                     // T facing downwards.
                        '┬';

                case 'x' ->                     // Vertical line.
                        '│';

                case 'y' ->                     // Less than or equal to.
                        '≤';

                case 'z' ->                     // Greater than or equal to.
                        '≥';

                case '{' ->                     // Pi.
                        'π';

                case '|' ->                     // Not equal to.
                        '≠';

                case '}' ->                     // UK pound.
                        '£';

                case '~' ->                     // Centered dot.
                        '·';

                default -> codePoint;
            };
        }
        var autoWrap = isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_AUTOWRAP);
        var displayWidth = WcWidth.width(codePoint);
        var cursorInLastColumn = mCursorCol == mRightMargin - 1;
        if (autoWrap) {
            if (cursorInLastColumn && ((mAboutToAutoWrap && 1 == displayWidth) || 2 == displayWidth)) {
                screen.setLineWrap(mCursorRow);
                mCursorCol = mLeftMargin;
                if (mCursorRow + 1 < mBottomMargin) mCursorRow++;
                else scrollDownOneLine();
            }
        } else if (cursorInLastColumn && 2 == displayWidth) // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return;

        if (mInsertMode && 0 < displayWidth) { // Move character to right one space.
            var destCol = mCursorCol + displayWidth;
            if (destCol < mRightMargin)
                screen.blockCopy(mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow);
        }
        var offsetDueToCombiningChar = (0 >= displayWidth && 0 < mCursorCol && !mAboutToAutoWrap) ? 1 : 0;
        var column = mCursorCol - offsetDueToCombiningChar; // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported // The offsetDueToCombiningChar would never be 1 if mCursorCol was 0 to get column/index=-1,
        // so was mCursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow, possibly causing others bugs too.
        if (0 > column) column = 0;
        screen.setChar(column, mCursorRow, codePoint, style());
        if (autoWrap && 0 < displayWidth)
            mAboutToAutoWrap = (mCursorCol == mRightMargin - displayWidth);
        mCursorCol = min((mCursorCol + displayWidth), (mRightMargin - 1));
    }

    /**
     * Set the cursor mode, but limit it to margins if [.DECSET_BIT_ORIGIN_MODE] is enabled.
     */
    private void setCursorColRespectingOriginMode(int col) {
        setCursorPosition(col, mCursorRow);
    }

    /**
     * TODO: Better name, distinguished from [.setCursorPosition] by not regarding origin mode.
     */
    private void setCursorRowCol(int row, int col) {
        mCursorRow = max(0, min(row, mRows - 1));
        mCursorCol = max(0, min(col, mColumns - 1));
        mAboutToAutoWrap = false;
    }

    public void clearScrollCounter() {
        scrollCounter = 0;
    }

    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    private void reset() {
        mArgIndex = 0;
        mContinueSequence = false;
        mEscapeState = TerminalEmulator.ESC_NONE;
        mInsertMode = false;
        mTopMargin = mLeftMargin = 0;
        mBottomMargin = mRows;
        mRightMargin = mColumns;
        mAboutToAutoWrap = false;
        mForeColor = mSavedStateMain.mSavedForeColor = mSavedStateAlt.mSavedForeColor = COLOR_INDEX_FOREGROUND;
        mBackColor = mSavedStateMain.mSavedBackColor = mSavedStateAlt.mSavedBackColor = COLOR_INDEX_BACKGROUND;
        setDefaultTabStops();

        mUseLineDrawingG0 = mUseLineDrawingG1 = false;
        mUseLineDrawingUsesG0 = true;

        mSavedStateMain.mSavedCursorRow = mSavedStateMain.mSavedCursorCol = mSavedStateMain.mSavedEffect = mSavedStateMain.mSavedDecFlags = 0;
        mSavedStateAlt.mSavedCursorRow = mSavedStateAlt.mSavedCursorCol = mSavedStateAlt.mSavedEffect = mSavedStateAlt.mSavedDecFlags = 0;
        mCurrentDecSetFlags = 0;
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        setDecsetinternalBit(TerminalEmulator.DECSET_BIT_AUTOWRAP, true);
        setDecsetinternalBit(TerminalEmulator.DECSET_BIT_CURSOR_ENABLED, true);
        mSavedDecSetFlags = mSavedStateMain.mSavedDecFlags = mSavedStateAlt.mSavedDecFlags = mCurrentDecSetFlags;

        // XXX: Should we set terminal driver back to IUTF8 with termios?
        mUtf8Index = mUtf8ToFollow = 0;

        mColors.reset();
    }

    public String getSelectedText() {
        return screen.getSelectedText(selectors[0], selectors[1], selectors[2], selectors[3]);
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    public void paste(String text) { // First: Always remove escape Key and C1 control characters [0x80,0x9F]:
        text = TerminalEmulator.REGEX.matcher(text).replaceAll(""); // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        text = TerminalEmulator.PATTERN.matcher(text).replaceAll("\r"); // Then: Implement bracketed paste mode if enabled:
        boolean bracketed = isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_BRACKETED_PASTE_MODE);
        if (bracketed) mSession.write("\u001b[200~");
        mSession.write(text);
        if (bracketed) mSession.write("\u001b[201~");
    }

    /**
     * [...](http://www.vt100.net/docs/vt510-rm/DECSC)
     */
    static class SavedScreenState {
        /**
         * Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences.
         */
        int mSavedCursorRow;
        int mSavedCursorCol;

        int mSavedEffect;
        int mSavedForeColor;
        int mSavedBackColor;

        int mSavedDecFlags;

        boolean mUseLineDrawingG0;
        boolean mUseLineDrawingG1;
        boolean mUseLineDrawingUsesG0 = true;
    }
}
