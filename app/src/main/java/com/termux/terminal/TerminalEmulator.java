package com.termux.terminal;

import android.util.Base64;

import com.termux.view.textselection.TextSelectionCursorController;

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
     * Used for invalid data - [...](<a href="http://en.wikipedia.org/wiki/Replacement_character#Replacement_character">...</a>)
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
     * [...](<a href="http://www.vt100.net/docs/vt510-rm/DECOM">...</a>): "When DECOM is set, the home cursor position is at the upper-left
     * corner of the console, within the margins. The starting point for line numbers depends on the current top margin
     * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
     * upper-left corner of the console. The starting point for line numbers is independent of the margins. The cursor
     * can move outside of the margins."
     */
    private static final int DECSET_BIT_ORIGIN_MODE = 1 << 2;
    /**
     * [...](<a href="http://www.vt100.net/docs/vt510-rm/DECAWM">...</a>): "If the DECAWM function is set, then graphic characters received when
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
     * Toggled with DECLRMM - [...](<a href="http://www.vt100.net/docs/vt510-rm/DECLRMM">...</a>)
     */
    private static final int DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 << 11;
    /**
     * Not really DECSET bit... - [...](<a href="http://www.vt100.net/docs/vt510-rm/DECSACE">...</a>)
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
    private boolean dontContinueSequence;
    /**
     * The current state of the escape sequence state machine. One of the ESC_* constants.
     */
    private int mEscapeState;
    /**
     * [...](<a href="http://www.vt100.net/docs/vt102-ug/table5-15.html">...</a>)
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

    public TerminalEmulator(final TerminalSession session, final int columns, final int rows, final int transcriptRows) {
        this.mSession = session;
        this.mColumns = columns;
        this.mRows = rows;
        this.mMainBuffer = new TerminalBuffer(columns, transcriptRows, rows);
        this.mAltBuffer = new TerminalBuffer(columns, rows, rows);
        this.mTabStop = new boolean[columns];
        this.screen = this.mMainBuffer;
        this.reset();
    }

    private static int mapDecSetBitToInternalBit(final int decsetBit) {
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

    private boolean isDecsetInternalBitSet(final int bit) {
        return 0 != (this.mCurrentDecSetFlags & bit);
    }

    private void setDecsetinternalBit(final int internalBit, final boolean set) {
        if (set) { // The mouse modes are mutually exclusive.
            if (TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE == internalBit)
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false);
            else if (TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT == internalBit)
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false);
        }
        this.mCurrentDecSetFlags = set ? this.mCurrentDecSetFlags | internalBit : this.mCurrentDecSetFlags & ~internalBit;
    }

    public boolean isAlternateBufferActive() {
        return this.screen == this.mAltBuffer;
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    public void sendMouseEvent(int mouseButton, int column, int row, final boolean pressed) {
        column = (1 > column) ? 1 : Math.min(column, this.mColumns);
        row = (1 > row) ? 1 : Math.min(row, this.mRows);
        if (!(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED == mouseButton && !this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) && this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_PROTOCOL_SGR))
            this.mSession.write("\033[<" + mouseButton + ";" + column + ";" + row + (pressed ? 'M' : 'm'));
        else { // 3 for release of all buttons.
            mouseButton = pressed ? mouseButton : 3;// Clip to console, and clip to the limits of 8-bit data.
            if (!(255 - 32 < column || 255 - 32 < row))
                this.mSession.write(new byte[]{'\033', '[', 'M', (byte) (32 + mouseButton), (byte) (32 + column), (byte) (32 + row)}, 6);
        }
    }

    public void resize(final int columns, final int rows) {
        if (this.mRows != rows) {
            this.mRows = rows;
            this.mTopMargin = 0;
            this.mBottomMargin = this.mRows;
        }
        if (this.mColumns != columns) {
            final int oldColumns = this.mColumns;
            this.mColumns = columns;
            final boolean[] oldTabStop = this.mTabStop;
            this.mTabStop = new boolean[this.mColumns];
            this.setDefaultTabStops();
            final int toTransfer = Math.min(oldColumns, columns);
            System.arraycopy(oldTabStop, 0, this.mTabStop, 0, toTransfer);
            this.mLeftMargin = 0;
            this.mRightMargin = this.mColumns;
        }
        this.resizeScreen();
    }

    private void resizeScreen() {
        final int[] cursor = {this.mCursorCol, this.mCursorRow};
        final int newTotalRows = this.isAlternateBufferActive() ? this.mRows : this.mMainBuffer.mTotalRows;
        this.screen.resize(this.mColumns, this.mRows, newTotalRows, cursor, this.style(), this.isAlternateBufferActive());
        this.mCursorCol = cursor[0];
        this.mCursorRow = cursor[1];
    }

    private void setCursorRow(final int row) {
        this.mCursorRow = row;
        this.mAboutToAutoWrap = false;
    }

    private void setCursorCol(final int col) {
        this.mCursorCol = col;
        this.mAboutToAutoWrap = false;
    }

    public boolean isReverseVideo() {
        return this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_REVERSE_VIDEO);
    }

    public boolean shouldCursorBeVisible() {
        return this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_CURSOR_ENABLED);
    }

    public boolean isKeypadApplicationMode() {
        return this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD);
    }

    public boolean isCursorKeysApplicationMode() {
        return this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_CURSOR_KEYS);
    }

    /**
     * If mouse events are being sent as escape codes to the terminal.
     */
    public boolean isMouseTrackingActive() {
        return this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT);
    }

    private void setDefaultTabStops() {
        for (int i = 0; i < this.mColumns; i++)
            this.mTabStop[i] = 0 == (i & 7) && 0 != i;
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    void append(final byte[] buffer, final int length) {
        for (int i = 0; i < length; i++)
            this.processByte(buffer[i]);
    }

    /**
     * Called after getting data from session
     */
    private void processByte(final byte byteToProcess) {
        if (0 < this.mUtf8ToFollow) {
            if (128 == (byteToProcess & 192)) { // 10xxxxxx, a continuation byte.
                this.mUtf8InputBuffer[this.mUtf8Index] = byteToProcess;
                this.mUtf8Index++;
                --this.mUtf8ToFollow;
                if (0 == this.mUtf8ToFollow) {
                    final int firstByteMask = ((2 == this.mUtf8Index) ? 31 : ((3 == this.mUtf8Index) ? 15 : 7));
                    int codePoint = (this.mUtf8InputBuffer[0] & firstByteMask);
                    for (int i = 1; i < this.mUtf8Index; i++)
                        codePoint = (codePoint << 6) | (this.mUtf8InputBuffer[i] & 63);
                    if (((127 >= codePoint) && 1 < this.mUtf8Index) || (2047 > codePoint && 2 < this.mUtf8Index) || (65535 > codePoint && 3 < this.mUtf8Index))
                        codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;// Overlong encoding.
                    this.mUtf8Index = 0;
                    if (0x80 > codePoint || 0x9F < codePoint) {
                        switch (Character.getType(codePoint)) {
                            case Character.UNASSIGNED, Character.SURROGATE ->
                                    codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                        }
                        this.processCodePoint(codePoint);
                    }
                }
            } else { // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                this.mUtf8ToFollow = this.mUtf8Index = 0;
                this.emitCodePoint(TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                this.processByte(byteToProcess);
            }
        } else {
            if (0 == (byteToProcess & 128)) { // The leading bit is not set so it is a 7-bit ASCII character.
                this.processCodePoint(byteToProcess);
                return;
            } else if (192 == (byteToProcess & 224)) // 110xxxxx, a two-byte sequence.
                this.mUtf8ToFollow = 1;
            else if (224 == (byteToProcess & 240)) // 1110xxxx, a three-byte sequence.
                this.mUtf8ToFollow = 2;
            else if (240 == (byteToProcess & 248)) // 11110xxx, a four-byte sequence.
                this.mUtf8ToFollow = 3;
            else { // Not a valid UTF-8 sequence start, signal invalid data:
                this.processCodePoint(TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                return;
            }
            this.mUtf8InputBuffer[this.mUtf8Index] = byteToProcess;
            this.mUtf8Index++;
        }
    }

    private void processCodePoint(final int b) {
        switch (b) {
            case 0 -> {
            }

            case 7 -> {
                if (TerminalEmulator.ESC_OSC == this.mEscapeState) this.doOsc(b);
            }

            case 8 -> {
                if (this.mLeftMargin == this.mCursorCol) { // Jump to previous line if it was auto-wrapped.
                    final int previousRow = this.mCursorRow - 1;
                    if (0 <= previousRow && this.screen.getLineWrap(previousRow)) {
                        this.screen.clearLineWrap(previousRow);
                        this.setCursorRowCol(previousRow, this.mRightMargin - 1);
                    }
                } else this.setCursorCol(this.mCursorCol - 1);
            }

            case 9 -> this.mCursorCol = this.nextTabStop(1);

            case 10, 11, 12 -> this.doLinefeed();

            case 13 -> this.setCursorCol(this.mLeftMargin);

            case 14 -> this.mUseLineDrawingUsesG0 = false;
            case 15 -> this.mUseLineDrawingUsesG0 = true;
            case 24, 26 -> {
                if (TerminalEmulator.ESC_NONE != this.mEscapeState) { // FIXME: What is this??
                    this.mEscapeState = TerminalEmulator.ESC_NONE;
                    this.emitCodePoint(127);
                }
            }

            case 27 -> {
                if (TerminalEmulator.ESC_OSC == this.mEscapeState) this.doOsc(b);
                else this.startEscapeSequence();
            }


            default -> {
                this.dontContinueSequence = true;
                switch (this.mEscapeState) {
                    case TerminalEmulator.ESC_NONE -> {
                        if (32 <= b) this.emitCodePoint(b);
                    }
                    case TerminalEmulator.ESC -> this.doEsc(b);
                    case TerminalEmulator.ESC_POUND -> this.doEscPound(b);
                    case TerminalEmulator.ESC_SELECT_LEFT_PAREN ->
                            this.mUseLineDrawingG0 = ('0' == b);
                    case TerminalEmulator.ESC_SELECT_RIGHT_PAREN ->
                            this.mUseLineDrawingG1 = ('0' == b);
                    case TerminalEmulator.ESC_CSI -> this.doCsi(b);
                    case TerminalEmulator.ESC_CSI_EXCLAMATION -> {
                        if ('p' == b)
                            this.reset();// Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                        else this.finishSequence();
                    }
                    case TerminalEmulator.ESC_CSI_QUESTIONMARK -> this.doCsiQuestionMark(b);
                    case TerminalEmulator.ESC_CSI_BIGGERTHAN -> this.doCsiBiggerThan(b);
                    case TerminalEmulator.ESC_CSI_DOLLAR -> {
                        final boolean originMode = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE);
                        final int effectiveTopMargin = originMode ? this.mTopMargin : 0;
                        final int effectiveBottomMargin = originMode ? this.mBottomMargin : this.mRows;
                        final int effectiveLeftMargin = originMode ? this.mLeftMargin : 0;
                        final int effectiveRightMargin = originMode ? this.mRightMargin : this.mColumns;
                        switch (b) {
                            case 'v' -> {
                                final int topSource = Math.min(this.getArg(0, 1, true) - 1 + effectiveTopMargin, this.mRows);
                                final int leftSource = Math.min(this.getArg(1, 1, true) - 1 + effectiveLeftMargin, this.mColumns); // Inclusive, so do not subtract one:
                                final int bottomSource = Math.min(Math.max(this.getArg(2, this.mRows, true) + effectiveTopMargin, topSource), this.mRows);
                                final int rightSource = Math.min(Math.max(this.getArg(3, this.mColumns, true) + effectiveLeftMargin, leftSource), this.mColumns); // int sourcePage = getArg(4, 1, true);
                                final int destionationTop = Math.min(this.getArg(5, 1, true) - 1 + effectiveTopMargin, this.mRows);
                                final int destinationLeft = Math.min(this.getArg(6, 1, true) - 1 + effectiveLeftMargin, this.mColumns); // int destinationPage = getArg(7, 1, true);
                                final int heightToCopy = Math.min(this.mRows - destionationTop, bottomSource - topSource);
                                final int widthToCopy = Math.min(this.mColumns - destinationLeft, rightSource - leftSource);
                                this.screen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop);
                            }

                            case '{', 'x',
                                 'z' -> { // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                final boolean erase = 'x' != b;
                                final boolean selective = '{' == b; // Only DECSERA keeps visual attributes, DECERA does not:
                                final boolean keepVisualAttributes = erase && selective;
                                int argIndex = 0;
                                final int fillChar;
                                if (erase) {
                                    fillChar = ' ';
                                } else {
                                    fillChar = this.getArg(argIndex, -1, true);
                                    argIndex++;
                                }
                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if ((32 <= fillChar && 126 >= fillChar) || (160 <= fillChar && 255 >= fillChar)) { // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    final int top = Math.min(this.getArg(argIndex, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1);
                                    argIndex++;
                                    final int left = Math.min(this.getArg(argIndex, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1);
                                    argIndex++;
                                    final int bottom = Math.min(this.getArg(argIndex, this.mRows, true) + effectiveTopMargin, effectiveBottomMargin);
                                    argIndex++;
                                    final int right = Math.min(this.getArg(argIndex, this.mColumns, true) + effectiveLeftMargin, effectiveRightMargin);
                                    final long style = this.style();
                                    for (int row = top - 1; row < bottom; row++)
                                        for (int col = left - 1; col < right; col++)
                                            if (!selective || 0 == (TextStyle.decodeEffect(this.screen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED))
                                                this.screen.setChar(col, row, fillChar, keepVisualAttributes ? this.screen.getStyleAt(row, col) : style);
                                }
                            }
                            case 'r',
                                 't' -> { // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                final boolean reverse = 't' == b;
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                final int top = Math.min(this.getArg0(1) - 1, effectiveBottomMargin) + effectiveTopMargin;
                                final int left = Math.min(this.getArg1(1) - 1, effectiveRightMargin) + effectiveLeftMargin;
                                final int bottom = Math.min(this.getArg(2, this.mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin;
                                final int right = Math.min(this.getArg(3, this.mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin;
                                if (4 <= this.mArgIndex) {
                                    if (this.mArgIndex >= this.mArgs.length)
                                        this.mArgIndex = this.mArgs.length - 1;
                                    for (int i = 4; i <= this.mArgIndex; i++) {
                                        boolean setOrClear = true;
                                        final int bits = switch (this.getArg(i, 0, false)) {
                                            case 0 -> {
                                                if (!reverse) setOrClear = false;
                                                yield TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BLINK | TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                            }

                                            case 1 -> TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                            case 4 -> TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                            case 5 -> TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                            case 7 -> TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                            case 22 -> {
                                                setOrClear = false;
                                                yield TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                            }

                                            case 24 -> {
                                                setOrClear = false;
                                                yield TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                            }

                                            case 25 -> {
                                                setOrClear = false;
                                                yield TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                            }

                                            case 27 -> {
                                                setOrClear = false;
                                                yield TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                            }
                                            default -> 0;
                                        };
                                        if (!reverse || setOrClear)
                                            this.screen.setOrClearEffect(bits, setOrClear, reverse, this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE), effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right);
                                    }
                                }
                            }
                            default -> this.finishSequence();
                        }
                    }
                    case TerminalEmulator.ESC_CSI_DOUBLE_QUOTE -> {
                        if ('q' == b) { // http://www.vt100.net/docs/vt510-rm/DECSCA
                            final int arg = this.getArg0(0);
                            switch (arg) {
                                case 0, 2 -> // DECSED and DECSEL can erase characters.
                                        this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;

                                case 1 -> // DECSED and DECSEL cannot erase characters.
                                        this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;

                                default -> this.finishSequence();
                            }
                        } else this.finishSequence();
                    }

                    case TerminalEmulator.ESC_CSI_SINGLE_QUOTE -> {
                        switch (b) {
                            case '}' -> { // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                                final int columnsAfterCursor = this.mRightMargin - this.mCursorCol;
                                final int columnsToInsert = Math.min(this.getArg0(1), columnsAfterCursor);
                                final int columnsToMove = columnsAfterCursor - columnsToInsert;
                                this.screen.blockCopy(this.mCursorCol, 0, columnsToMove, this.mRows, this.mCursorCol + columnsToInsert, 0);
                                this.blockClear(this.mCursorCol, 0, columnsToInsert, this.mRows);
                            }

                            case '~' -> { // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                                final int columnsAfterCursor = this.mRightMargin - this.mCursorCol;
                                final int columnsToDelete = Math.min(this.getArg0(1), columnsAfterCursor);
                                final int columnsToMove = columnsAfterCursor - columnsToDelete;
                                this.screen.blockCopy(this.mCursorCol + columnsToDelete, 0, columnsToMove, this.mRows, this.mCursorCol, 0);
                            }
                            default -> this.finishSequence();
                        }
                    }

                    case 9 -> {
                    }

                    case TerminalEmulator.ESC_OSC -> this.doOsc(b);
                    case TerminalEmulator.ESC_OSC_ESC -> this.doOscEsc(b);
                    case TerminalEmulator.ESC_P -> this.doDeviceControl(b);
                    case TerminalEmulator.ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> {
                        if ('p' == b) { // Request DEC private mode (DECRQM).
                            final int mode = this.getArg0(0);
                            final int value = switch (mode) {
                                case 47, 1047, 1049 -> this.isAlternateBufferActive() ? 1 : 2;
                                default -> {
                                    final int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(mode);
                                    yield (-1 == internalBit) ? 0 : (this.isDecsetInternalBitSet(internalBit) ? 1 : 2);
                                }
                            };
                            this.mSession.write("\033[?" + mode + ";" + value + "$y");
                        } else this.finishSequence();
                    }

                    case TerminalEmulator.ESC_CSI_ARGS_SPACE -> {
                        final int arg = this.getArg0(0);
                        switch (b) {
                            case 'q' -> {
                                switch (arg) {
                                    case 0, 1, 2 ->
                                            this.cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;

                                    case 3, 4 ->
                                            this.cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;

                                    case 5, 6 ->
                                            this.cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
                                }
                            }

                            case 't', 'u' -> {
                            }
                            default -> this.finishSequence();
                        }
                    }

                    case TerminalEmulator.ESC_CSI_ARGS_ASTERIX -> {
                        final int attributeChangeExtent = this.getArg0(0);
                        if ('x' == b && (0 <= attributeChangeExtent && 2 >= attributeChangeExtent)) // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, 2 == attributeChangeExtent);
                        else this.finishSequence();

                    }
                    default -> this.finishSequence();
                }
                if (this.dontContinueSequence) this.mEscapeState = TerminalEmulator.ESC_NONE;
            }
        }
    }

    /**
     * When in [.ESC_P] ("device control") sequence.
     */
    private void doDeviceControl(final int b) {

        if ('\\' == b) // ESC \ terminates OSC
        // Sixel sequences may be very long. '$' and '!' are natural for breaking the sequence.
        {
            final String dcs = this.mOSCOrDeviceControlArgs.toString(); // DCS $ q P t ST. Request Status String (DECRQSS)
            if (dcs.startsWith("$q")) {
                if ("$q\"p".equals(dcs)) // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                    this.mSession.write("\033P1$r64;1\"p\033\\");
                else this.finishSequence();
            } else if (dcs.startsWith("+q")) {
                for (final String part : dcs.substring(2).split(";")) {
                    if (0 == part.length() % 2) {
                        final StringBuilder transBuffer = new StringBuilder();
                        char c;
                        final int length = part.length();
                        for (int i = 0; i < length; i += 2) {
                            try {
                                c = (char) Long.decode("0x" + part.charAt(i) + part.charAt(i + 1)).longValue();
                            } catch (final Throwable t) {
                                continue;
                            }
                            transBuffer.append(c);
                        }
                        final String trans = transBuffer.toString();
                        final String responseValue = switch (trans) {
                            case "Co", "colors" -> "256";
                            case "TN", "name" -> "xterm";
                            default ->
                                    KeyHandler.getCodeFromTermcap(trans, this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_CURSOR_KEYS), this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD));
                        };
                        if (null == responseValue)  // Respond with invalid request:
                            this.mSession.write("\033P0+r" + part + "\033\\");
                        else {
                            final StringBuilder hexEncoded = new StringBuilder();
                            final int length1 = responseValue.length();
                            for (int j = 0; j < length1; j++) {
                                hexEncoded.append(String.format(Locale.US, "%02X", (int) responseValue.charAt(j)));
                            }
                            this.mSession.write("\033P1+r" + part + "=" + hexEncoded + "\033\\");
                        }
                    }
                }
            }
            this.finishSequence();
        } else {
            if (TerminalEmulator.MAX_OSC_STRING_LENGTH < this.mOSCOrDeviceControlArgs.length()) { // Too long.
                this.mOSCOrDeviceControlArgs.setLength(0);
                this.finishSequence();
            } else {
                this.mOSCOrDeviceControlArgs.appendCodePoint(b);
                this.continueSequence(this.mEscapeState);
            }
        }
    }

    private int nextTabStop(int numTabs) {
        for (int i = this.mCursorCol + 1; i < this.mColumns; i++) {
            if (this.mTabStop[i]) {
                --numTabs;
                if (0 == numTabs) return Math.min(i, this.mRightMargin);
            }
        }
        return this.mRightMargin - 1;
    }

    /**
     * Process byte while in the [.ESC_CSI_QUESTIONMARK] escape state.
     */
    private void doCsiQuestionMark(final int b) {
        switch (b) {
            case 'J', 'K' -> {
                this.mAboutToAutoWrap = false;
                final char fillChar = ' ';
                var startCol = -1;
                var startRow = -1;
                var endCol = -1;
                var endRow = -1;
                final boolean justRow = ('K' == b);
                switch (this.getArg0(0)) {
                    case 0 -> {
                        startCol = this.mCursorCol;
                        startRow = this.mCursorRow;
                        endCol = this.mColumns;
                        endRow = justRow ? (this.mCursorRow + 1) : this.mRows;
                    }

                    case 1 -> {
                        startCol = 0;
                        startRow = justRow ? this.mCursorRow : 0;
                        endCol = this.mCursorCol + 1;
                        endRow = this.mCursorRow + 1;
                    }

                    case 2 -> {
                        startCol = 0;
                        startRow = justRow ? this.mCursorRow : 0;
                        endCol = this.mColumns;
                        endRow = justRow ? (this.mCursorRow + 1) : this.mRows;
                    }

                    default -> this.finishSequence();
                }
                for (int row = startRow; row < endRow; row++) {
                    for (int col = startCol; col < endCol; col++) {
                        if (0 == (TextStyle.decodeEffect(this.screen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED))
                            this.screen.setChar(col, row, fillChar, this.style());
                    }
                }
            }

            case 'h', 'l' -> {
                if (this.mArgIndex >= this.mArgs.length) this.mArgIndex = this.mArgs.length - 1;
                for (int i = 0; i <= this.mArgIndex; i++)
                    this.doDecSetOrReset('h' == b, this.mArgs[i]);
            }

            case 'n' -> {
                if (6 == this.getArg0(-1))  // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                    this.mSession.write("\033[?" + this.mCursorRow + 1 + ";" + this.mCursorCol + 1 + ";1R");
                else this.finishSequence();
            }


            case 'r', 's' -> {
                if (this.mArgIndex >= this.mArgs.length) this.mArgIndex = this.mArgs.length - 1;
                for (int i = 0; i <= this.mArgIndex; i++) {
                    final int externalBit = this.mArgs[i];
                    final int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(externalBit);
                    if (-1 != internalBit) {
                        if ('s' == b) this.mSavedDecSetFlags |= internalBit;
                        else
                            this.doDecSetOrReset(0 != (this.mSavedDecSetFlags & internalBit), externalBit);
                    }
                }
            }

            case '$' -> this.continueSequence(TerminalEmulator.ESC_CSI_QUESTIONMARK_ARG_DOLLAR);

            default -> this.parseArg(b);
        }
    }

    private void doDecSetOrReset(final boolean setting, final int externalBit) {
        final int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(externalBit);
        if (-1 != internalBit) this.setDecsetinternalBit(internalBit, setting);

        switch (externalBit) {
            case 1, 4, 5, 7, 8, 9, 12, 25, 40, 45, 66, 1000, 1001, 1002, 1003, 1004, 1005, 1006,
                 1015, 1034, 2004 -> {
            }
            case 3 -> {
                this.mLeftMargin = this.mTopMargin = 0;
                this.mBottomMargin = this.mRows;
                this.mRightMargin = this.mColumns; // "DECCOLM resets vertical split console mode (DECLRMM) to unavailable":
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false); // "Erases all data in page memory":
                this.blockClear(0, 0, this.mColumns, this.mRows);
                this.setCursorRowCol(0, 0);
            }

            case 6 -> {
                if (setting) this.setCursorPosition(0, 0);
            }

            case 69 -> {
                if (!setting) {
                    this.mLeftMargin = 0;
                    this.mRightMargin = this.mColumns;
                }
            }

            case 1048 -> {
                if (setting) this.saveCursor();
                else this.restoreCursor();
            }

            case 47, 1047,
                 1049 -> { // Set: Save cursor as in DECSC and use Alternate Console Buffer, clearing it first.
                // Reset: Use Normal Console Buffer and restore cursor as in DECRC.
                final TerminalBuffer newScreen = setting ? this.mAltBuffer : this.mMainBuffer;
                if (newScreen != this.screen) {
                    final boolean resized = !(newScreen.mColumns == this.mColumns && newScreen.mScreenRows == this.mRows);
                    if (setting) this.saveCursor();
                    this.screen = newScreen;
                    if (!setting) {
                        final int col = this.mSavedStateMain.mSavedCursorCol;
                        final int row = this.mSavedStateMain.mSavedCursorRow;
                        this.restoreCursor();
                        if (resized) { // Restore cursor position _not_ clipped to current console (let resizeScreen() handle that):
                            this.mCursorCol = col;
                            this.mCursorRow = row;
                        }
                    } // Check if buffer size needs to be updated:
                    if (resized) this.resizeScreen(); // Clear new console if alt buffer:
                    if (newScreen == this.mAltBuffer)
                        newScreen.blockSet(0, 0, this.mColumns, this.mRows, ' ', this.style());
                }
            }

            default -> this.finishSequence();
        }
    }

    private void doCsiBiggerThan(final int b) {

        switch (b) {
            case 'c' -> this.mSession.write("\033[>41;320;0c");
            case 'm' -> {
            }
            default -> this.parseArg(b);
        }
    }

    private void startEscapeSequence() {
        this.mEscapeState = TerminalEmulator.ESC;
        this.mArgIndex = 0;
        Arrays.fill(this.mArgs, -1);
    }

    private void doLinefeed() {
        final var belowScrollingRegion = this.mCursorRow >= this.mBottomMargin;
        var newCursorRow = this.mCursorRow + 1;
        if (belowScrollingRegion) { // Move down (but not scroll) as long as we are above the last row.
            if (this.mCursorRow != this.mRows - 1) this.setCursorRow(newCursorRow);
        } else {
            if (newCursorRow == this.mBottomMargin) {
                this.scrollDownOneLine();
                newCursorRow = this.mBottomMargin - 1;
            }
            this.setCursorRow(newCursorRow);
        }
    }

    private void continueSequence(final int state) {
        this.mEscapeState = state;
        this.dontContinueSequence = false;
    }

    private void doEscPound(final int b) {
        if ('8' == b) this.screen.blockSet(0, 0, this.mColumns, this.mRows, 'E', this.style());
        else this.finishSequence();
    }

    /**
     * Encountering a character in the [.ESC] state.
     */
    private void doEsc(final int b) {
        switch (b) {
            case '#' -> this.continueSequence(TerminalEmulator.ESC_POUND);
            case '(' -> this.continueSequence(TerminalEmulator.ESC_SELECT_LEFT_PAREN);
            case ')' -> this.continueSequence(TerminalEmulator.ESC_SELECT_RIGHT_PAREN);
            case '6' -> {
                if (this.mCursorCol > this.mLeftMargin) this.mCursorCol--;
                else {
                    final int rows = this.mBottomMargin - this.mTopMargin;
                    this.screen.blockCopy(this.mLeftMargin, this.mTopMargin, this.mRightMargin - this.mLeftMargin - 1, rows, this.mLeftMargin + 1, this.mTopMargin);
                    this.screen.blockSet(this.mLeftMargin, this.mTopMargin, 1, rows, ' ', TextStyle.encode(this.mForeColor, this.mBackColor, 0));
                }
            }

            case '7' -> this.saveCursor();
            case '8' -> this.restoreCursor();
            case '9' -> {
                if (this.mCursorCol < this.mRightMargin - 1) this.mCursorCol++;
                else {
                    final int rows = this.mBottomMargin - this.mTopMargin;
                    this.screen.blockCopy(this.mLeftMargin + 1, this.mTopMargin, this.mRightMargin - this.mLeftMargin - 1, rows, this.mLeftMargin, this.mTopMargin);
                    this.screen.blockSet(this.mRightMargin - 1, this.mTopMargin, 1, rows, ' ', TextStyle.encode(this.mForeColor, this.mBackColor, 0));
                }
            }

            case 'c' -> {
                this.reset();
                this.mMainBuffer.clearTranscript();
                this.blockClear(0, 0, this.mColumns, this.mRows);
                this.setCursorPosition(0, 0);
            }

            case 'D' -> this.doLinefeed();
            case 'E' -> {
                this.setCursorCol(this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE) ? this.mLeftMargin : 0);
                this.doLinefeed();
            }

            case 'F' -> this.setCursorRowCol(0, this.mBottomMargin - 1);
            case 'H' -> this.mTabStop[this.mCursorCol] = true;
            case 'M' -> {
                // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (this.mCursorRow <= this.mTopMargin) {
                    this.screen.blockCopy(0, this.mTopMargin, this.mColumns, this.mBottomMargin - (this.mTopMargin + 1), 0, this.mTopMargin + 1);
                    this.blockClear(0, this.mTopMargin, this.mColumns);
                } else this.mCursorRow--;
            }

            case 'N', '0' -> {
            }
            case 'P' -> {
                this.mOSCOrDeviceControlArgs.setLength(0);
                this.continueSequence(TerminalEmulator.ESC_P);
            }

            case '[' -> this.continueSequence(TerminalEmulator.ESC_CSI);

            case '=' ->
                    this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD, true);
            case ']' -> {
                this.mOSCOrDeviceControlArgs.setLength(0);
                this.continueSequence(TerminalEmulator.ESC_OSC);
            }

            case '>' ->
                    this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD, false);

            default -> this.finishSequence();
        }
    }

    /**
     * DECSC save cursor - [...](<a href="http://www.vt100.net/docs/vt510-rm/DECSC">...</a>) . See [.restoreCursor].
     */
    private void saveCursor() {
        final var state = this.isAlternateBufferActive() ? this.mSavedStateAlt : this.mSavedStateMain;
        state.mSavedCursorRow = this.mCursorRow;
        state.mSavedCursorCol = this.mCursorCol;
        state.mSavedEffect = this.mEffect;
        state.mSavedForeColor = this.mForeColor;
        state.mSavedBackColor = this.mBackColor;
        state.mSavedDecFlags = this.mCurrentDecSetFlags;
        state.mUseLineDrawingG0 = this.mUseLineDrawingG0;
        state.mUseLineDrawingG1 = this.mUseLineDrawingG1;
        state.mUseLineDrawingUsesG0 = this.mUseLineDrawingUsesG0;
    }

    /**
     * DECRS restore cursor - [...](<a href="http://www.vt100.net/docs/vt510-rm/DECRC">...</a>). See [.saveCursor].
     */
    private void restoreCursor() {
        final var state = this.isAlternateBufferActive() ? this.mSavedStateAlt : this.mSavedStateMain;
        this.setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol);
        this.mEffect = state.mSavedEffect;
        this.mForeColor = state.mSavedForeColor;
        this.mBackColor = state.mSavedBackColor;
        final var mask = TerminalEmulator.DECSET_BIT_AUTOWRAP | TerminalEmulator.DECSET_BIT_ORIGIN_MODE;
        this.mCurrentDecSetFlags = (this.mCurrentDecSetFlags & ~mask) | (state.mSavedDecFlags & mask);
        this.mUseLineDrawingG0 = state.mUseLineDrawingG0;
        this.mUseLineDrawingG1 = state.mUseLineDrawingG1;
        this.mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0;
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". [.ESC_CSI].
     */
    private void doCsi(final int b) {
        switch (b) {
            case '!' -> this.continueSequence(TerminalEmulator.ESC_CSI_EXCLAMATION);
            case '"' -> this.continueSequence(TerminalEmulator.ESC_CSI_DOUBLE_QUOTE);
            case '\'' -> this.continueSequence(TerminalEmulator.ESC_CSI_SINGLE_QUOTE);
            case '$' -> this.continueSequence(TerminalEmulator.ESC_CSI_DOLLAR);
            case '*' -> this.continueSequence(TerminalEmulator.ESC_CSI_ARGS_ASTERIX);
            case '@' -> { // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                this.mAboutToAutoWrap = false;
                final var columnsAfterCursor = this.mColumns - this.mCursorCol;
                final var spacesToInsert = Math.min(this.getArg0(1), columnsAfterCursor);
                final var charsToMove = columnsAfterCursor - spacesToInsert;
                this.screen.blockCopy(this.mCursorCol, this.mCursorRow, charsToMove, 1, this.mCursorCol + spacesToInsert, this.mCursorRow);
                this.blockClear(this.mCursorCol, this.mCursorRow, spacesToInsert);
            }

            case 'A' -> this.setCursorRow(Math.max(0, this.mCursorRow - this.getArg0(1)));

            case 'B' ->
                    this.setCursorRow(Math.min(this.mRows - 1, this.mCursorRow + this.getArg0(1)));

            case 'C', 'a' ->
                    this.setCursorCol(Math.min(this.mRightMargin - 1, this.mCursorCol + this.getArg0(1)));

            case 'D' ->
                    this.setCursorCol(Math.max(this.mLeftMargin, this.mCursorCol - this.getArg0(1)));

            case 'E' -> this.setCursorPosition(0, this.mCursorRow + this.getArg0(1));
            case 'F' -> this.setCursorPosition(0, this.mCursorRow - this.getArg0(1));
            case 'G' ->
                    this.setCursorCol(Math.min(Math.max(1, this.getArg0(1)), this.mColumns) - 1);

            case 'H', 'f' -> this.setCursorPosition(this.getArg1(1) - 1, this.getArg0(1) - 1);
            case 'I' -> this.setCursorCol(this.nextTabStop(this.getArg0(1)));
            case 'J' -> {
                switch (this.getArg0(0)) {
                    case 0 -> {
                        this.blockClear(this.mCursorCol, this.mCursorRow, this.mColumns - this.mCursorCol);
                        this.blockClear(0, this.mCursorRow + 1, this.mColumns, this.mRows - this.mCursorRow - 1);
                    }

                    case 1 -> {
                        this.blockClear(0, 0, this.mColumns, this.mCursorRow);
                        this.blockClear(0, this.mCursorRow, this.mCursorCol + 1);
                    }

                    case 2 -> this.blockClear(0, 0, this.mColumns, this.mRows);

                    case 3 -> this.mMainBuffer.clearTranscript();

                    default -> {
                        this.finishSequence();
                        return;
                    }
                }
                this.mAboutToAutoWrap = false;
            }

            case 'K' -> {
                switch (this.getArg0(0)) {
                    case 0 ->
                            this.blockClear(this.mCursorCol, this.mCursorRow, this.mColumns - this.mCursorCol);
                    case 1 -> this.blockClear(0, this.mCursorRow, this.mCursorCol + 1);
                    case 2 -> this.blockClear(0, this.mCursorRow, this.mColumns);

                    default -> {
                        this.finishSequence();
                        return;
                    }
                }
                this.mAboutToAutoWrap = false;
            }

            case 'L' -> {
                final var linesAfterCursor = this.mBottomMargin - this.mCursorRow;
                final var linesToInsert = Math.min(this.getArg0(1), linesAfterCursor);
                final var linesToMove = linesAfterCursor - linesToInsert;
                this.screen.blockCopy(0, this.mCursorRow, this.mColumns, linesToMove, 0, this.mCursorRow + linesToInsert);
                this.blockClear(0, this.mCursorRow, this.mColumns, linesToInsert);
            }

            case 'M' -> {
                this.mAboutToAutoWrap = false;
                final var linesAfterCursor = this.mBottomMargin - this.mCursorRow;
                final var linesToDelete = Math.min(this.getArg0(1), linesAfterCursor);
                final var linesToMove = linesAfterCursor - linesToDelete;
                this.screen.blockCopy(0, this.mCursorRow + linesToDelete, this.mColumns, linesToMove, 0, this.mCursorRow);
                this.blockClear(0, this.mCursorRow + linesToMove, this.mColumns, linesToDelete);
            }

            case 'P' -> {
                this.mAboutToAutoWrap = false;
                final var cellsAfterCursor = this.mColumns - this.mCursorCol;
                final var cellsToDelete = Math.min(this.getArg0(1), cellsAfterCursor);
                final var cellsToMove = cellsAfterCursor - cellsToDelete;
                this.screen.blockCopy(this.mCursorCol + cellsToDelete, this.mCursorRow, cellsToMove, 1, this.mCursorCol, this.mCursorRow);
                this.blockClear(this.mCursorCol + cellsToMove, this.mCursorRow, cellsToDelete);
            }

            case 'S' -> {
                final var linesToScroll = this.getArg0(1);
                for (int i = 0; i < linesToScroll; i++)
                    this.scrollDownOneLine();
            }

            case 'T' -> {
                if (0 == this.mArgIndex) {
                    final var linesToScrollArg = this.getArg0(1);
                    final var linesBetweenTopAndBottomMargins = this.mBottomMargin - this.mTopMargin;
                    final var linesToScroll = Math.min(linesBetweenTopAndBottomMargins, linesToScrollArg);
                    this.screen.blockCopy(0, this.mTopMargin, this.mColumns, linesBetweenTopAndBottomMargins - linesToScroll, 0, this.mTopMargin + linesToScroll);
                    this.blockClear(0, this.mTopMargin, this.mColumns, linesToScroll);
                } else this.finishSequence();
            }

            case 'X' -> {
                this.mAboutToAutoWrap = false;
                this.screen.blockSet(this.mCursorCol, this.mCursorRow, Math.min(this.getArg0(1), this.mColumns - this.mCursorCol), 1, ' ', this.style());
            }

            case 'Z' -> {
                int numberOfTabs = this.getArg0(1);
                int newCol = this.mLeftMargin;
                for (int i = this.mCursorCol - 1; 0 <= i; i--) {
                    if (this.mTabStop[i]) {
                        --numberOfTabs;
                        if (0 == numberOfTabs) {
                            newCol = Math.max(i, this.mLeftMargin);
                            break;
                        }
                    }
                }
                this.mCursorCol = newCol;
            }

            case '?' -> this.continueSequence(TerminalEmulator.ESC_CSI_QUESTIONMARK);
            case '>' -> this.continueSequence(TerminalEmulator.ESC_CSI_BIGGERTHAN);
            case '`' -> this.setCursorColRespectingOriginMode(this.getArg0(1) - 1);
            case 'b' -> {
                if (-1 == this.mLastEmittedCodePoint) break;
                final var numRepeat = this.getArg0(1);
                for (int i = 0; i < numRepeat; i++) this.emitCodePoint(this.mLastEmittedCodePoint);
            }

            case 'c' -> {
                if (0 == this.getArg0(0)) this.mSession.write("\033[?64;1;2;6;9;15;18;21;22c");
            }

            case 'd' -> this.setCursorRow(Math.min(Math.max(1, this.getArg0(1)), this.mRows) - 1);

            case 'e' -> this.setCursorPosition(this.mCursorCol, this.mCursorRow + this.getArg0(1));
            case 'g' -> {
                switch (this.getArg0(0)) {
                    case 0 -> this.mTabStop[this.mCursorCol] = false;
                    case 3 -> {
                        for (int i = 0; i < this.mColumns; i++) this.mTabStop[i] = false;
                    }
                }
            }

            case 'h' -> this.doSetMode(true);
            case 'l' -> this.doSetMode(false);
            case 'm' -> this.selectGraphicRendition();
            case 'n' -> {
                switch (this.getArg0(0)) {
                    case 5 -> this.mSession.write(new byte[]{27, '[', '0', 'n'}, 4);
                    case 6 ->
                            this.mSession.write("\033[" + this.mCursorRow + 1 + ";" + this.mCursorCol + 1 + "R");
                }
            }

            case 'r' -> {
                this.mTopMargin = Math.max(0, Math.min(this.getArg0(1) - 1, this.mRows - 2));
                this.mBottomMargin = Math.max(this.mTopMargin + 2, Math.min(this.getArg1(this.mRows), this.mRows));
                this.setCursorPosition(0, 0);
            }

            case 's' -> {
                if (this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) { // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                    this.mLeftMargin = Math.min(this.getArg0(1) - 1, this.mColumns - 2);
                    this.mRightMargin = Math.max(this.mLeftMargin + 1, Math.min(this.getArg1(this.mColumns), this.mColumns)); // DECSLRM moves the cursor to column 1, line 1 of the page.
                    this.setCursorPosition(0, 0);
                } else this.saveCursor();
            }

            case 't' -> {
                switch (this.getArg0(0)) {
                    case 11 -> this.mSession.write("\033[1t");
                    case 13 -> this.mSession.write("\033[3;0;0t");
                    case 14 ->
                            this.mSession.write("\033[4;" + this.mRows * 12 + ";" + this.mColumns * 12 + "t");

                    case 18 ->
                            this.mSession.write("\033[8;" + this.mRows + ";" + this.mColumns + "t");
                    case 19 ->                         // We report the same size as the view, since it's the view really isn't resizable from the shell.
                            this.mSession.write("\033[9;" + this.mRows + ";" + this.mColumns + "t");

                    case 20 -> this.mSession.write("\033]LIconLabel\033\\");
                    case 21 -> this.mSession.write("\033]l\033\\");
                }
            }

            case 'u' -> this.restoreCursor();
            case ' ' -> this.continueSequence(TerminalEmulator.ESC_CSI_ARGS_SPACE);
            default -> this.parseArg(b);
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see [...](<a href="http://en.wikipedia.org/wiki/ANSI_escape_code#graphics">...</a>).
     */
    private void selectGraphicRendition() {
        if (this.mArgIndex >= this.mArgs.length) this.mArgIndex = this.mArgs.length - 1;
        for (int i = 0; i <= this.mArgIndex; i++) {
            var code = this.getArg(i, 0, false);
            if (0 > code) {
                if (0 < this.mArgIndex) {
                    i++;
                    continue;
                } else code = 0;
            }
            switch (code) {
                case 0 -> { // Reset attributes
                    this.mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
                    this.mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
                    this.mEffect = 0;
                }
                case 1 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                case 2 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_DIM;
                case 3 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
                case 4 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                case 5 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                case 7 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                case 8 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
                case 9 -> this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
                case 22 ->
                        this.mEffect &= ~(TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_DIM);
                case 23 -> this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
                case 24 -> this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                case 25 -> this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                case 27 -> this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                case 28 -> this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
                case 29 -> this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
                case 39 -> this.mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
                default -> {
                    if (30 <= code && 37 >= code) this.mForeColor = code - 30;
                    else if (38 == code || 48 == code) {
                        if (i + 2 > this.mArgIndex) continue;
                        final var firstArg = this.mArgs[i + 1];
                        switch (firstArg) {
                            case 2 -> {
                                if (i + 4 <= this.mArgIndex) {
                                    final var red = this.getArg(i + 2, 0, false);
                                    final var green = this.getArg(i + 3, 0, false);
                                    final var blue = this.getArg(i + 4, 0, false);
                                    if (0 > red || 0 > green || 0 > blue || 255 < red || 255 < green || 255 < blue)
                                        this.finishSequence();
                                    else {
                                        final int argbColor = 0xff000000 | (red << 16) | (green << 8) | blue;
                                        if (38 == code) this.mForeColor = argbColor;
                                        else this.mBackColor = argbColor;
                                    }
                                    i += 4;
                                }
                            }

                            case 5 -> {
                                final var color = this.getArg(i + 2, 0, false);
                                i += 2;
                                if (0 <= color && TextStyle.NUM_INDEXED_COLORS > color) {
                                    if (38 == code) this.mForeColor = color;
                                    else this.mBackColor = color;
                                }
                            }
                            default -> this.finishSequence();
                        }
                    } else if (40 <= code && 47 >= code) { // Set background color.
                        this.mBackColor = code - 40;
                    } else if (49 == code) { // Set default background color.
                        this.mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
                    } else if (90 <= code && 97 >= code) { // Bright foreground colors (aixterm codes).
                        this.mForeColor = code - 90 + 8;
                    } else if (100 <= code && 107 >= code) { // Bright background color (aixterm codes).
                        this.mBackColor = code - 100 + 8;
                    }
                }
            }
        }
    }

    private void doOsc(final int b) {
        switch (b) {
            case 7 -> this.doOscSetTextParameters("\u0007");
            case 27 -> this.continueSequence(TerminalEmulator.ESC_OSC_ESC);
            default -> this.collectOSCArgs(b);
        }
    }

    private void doOscEsc(final int b) {
        if ('\\' == b) this.doOscSetTextParameters("\033\\");
        else { // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            this.collectOSCArgs(27);
            this.collectOSCArgs(b);
            this.continueSequence(TerminalEmulator.ESC_OSC);
        }
    }

    /**
     * An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.
     */
    private void doOscSetTextParameters(final String bellOrStringTerminator) {
        var value = -1;
        var textParameter = ""; // Extract initial $value from initial "$value;..." string.
        final int length = this.mOSCOrDeviceControlArgs.length();
        for (int mOSCArgTokenizerIndex = 0; mOSCArgTokenizerIndex < length; mOSCArgTokenizerIndex++) {
            final var b = this.mOSCOrDeviceControlArgs.charAt(mOSCArgTokenizerIndex);
            if (';' == b) {
                textParameter = this.mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1);
                break;
            } else if ('0' <= b && '9' >= b) value = ((0 > value) ? 0 : value * 10) + (b - '0');
            else {
                this.finishSequence();
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
                    final var endOfInput = i == textParameter.length();
                    final var b = endOfInput ? ';' : textParameter.charAt(i);
                    if (';' == b) {
                        if (0 > parsingPairStart) parsingPairStart = i + 1;
                        else {
                            if (0 > colorIndex || 255 < colorIndex) {
                                this.finishSequence();
                                return;
                            } else {
                                this.mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i));
                                colorIndex = -1;
                                parsingPairStart = -1;
                            }
                        }
                    } else if (0 > parsingPairStart && ('0' <= b && '9' >= b))
                        colorIndex = ((0 > colorIndex) ? 0 : colorIndex * 10) + (b - '0');
                    else {
                        this.finishSequence();
                        return;
                    }
                    if (endOfInput) break;
                }
            }

            case 10, 11, 12 -> {
                var specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10);
                var lastSemiIndex = 0;
                for (int charIndex = 0; ; charIndex++) {
                    final var endOfInput = charIndex == textParameter.length();
                    if (endOfInput || ';' == textParameter.charAt(charIndex)) {
                        try {
                            final var colorSpec = textParameter.substring(lastSemiIndex, charIndex);
                            if ("?".equals(colorSpec)) { // Report current color in the same format xterm and gnome-terminal does.
                                final var rgb = this.mColors.mCurrentColors[specialIndex];
                                final var r = (65535 * ((rgb & 0x00FF0000) >> 16)) / 255;
                                final var g = (65535 * ((rgb & 0x0000FF00) >> 8)) / 255;
                                final var b = (65535 * (rgb & 0x000000FF)) / 255;
                                this.mSession.write(String.format(Locale.US, "\u001B]" + value + ";rgb:%04x/%04x/%04x" + bellOrStringTerminator, r, g, b));
                            } else this.mColors.tryParseColor(specialIndex, colorSpec);

                            specialIndex++;
                            if (endOfInput || (TextStyle.COLOR_INDEX_CURSOR < specialIndex) || ++charIndex >= textParameter.length())
                                break;
                            lastSemiIndex = charIndex;
                        } catch (final Throwable t) { // Ignore.
                        }
                    }
                }
            }

            case 52 -> {
                final var startIndex = textParameter.indexOf(';') + 1;
                try {
                    final CharSequence clipboardText = new String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8);
                    TerminalSession.onCopyTextToClipboard(clipboardText);
                } catch (final Throwable ignored) {
                }
            }

            case 104 -> {
                if (textParameter.isEmpty()) this.mColors.reset();
                else {
                    var lastIndex = 0;
                    for (int charIndex = 0; ; charIndex++) {
                        final var endOfInput = charIndex == textParameter.length();
                        if (endOfInput || ';' == textParameter.charAt(charIndex)) {
                            try {
                                final var colorToReset = Integer.parseInt(textParameter.substring(lastIndex, charIndex));
                                this.mColors.reset(colorToReset);
                                if (endOfInput) break;
                                charIndex++;
                                lastIndex = charIndex;
                            } catch (final Throwable t) { // Ignore.
                            }
                        }
                    }
                }
            }

            case 110, 111, 112 ->
                    this.mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110));
            default -> this.finishSequence();
        }
        this.finishSequence();
    }

    private void blockClear(final int sx, final int sy, final int w) {
        this.blockClear(sx, sy, w, 1);
    }

    private void blockClear(final int sx, final int sy, final int w, final int h) {
        this.screen.blockSet(sx, sy, w, h, ' ', this.style());
    }

    private long style() {
        return TextStyle.encode(this.mForeColor, this.mBackColor, this.mEffect);
    }

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private void doSetMode(final boolean newValue) {
        final int modeBit = this.getArg0(0);
        switch (modeBit) {
            case 4 -> this.mInsertMode = newValue;
            case 34 -> {
            }
            default -> this.finishSequence();
        }
    }

    /**
     * NOTE: The parameters of this function respect the [.DECSET_BIT_ORIGIN_MODE]. Use
     * [.setCursorRowCol] for absolute pos.
     */
    private void setCursorPosition(final int x, final int y) {
        final var originMode = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE);
        final var effectiveTopMargin = originMode ? this.mTopMargin : 0;
        final var effectiveBottomMargin = originMode ? this.mBottomMargin : this.mRows;
        final var effectiveLeftMargin = originMode ? this.mLeftMargin : 0;
        final var effectiveRightMargin = originMode ? this.mRightMargin : this.mColumns;
        final var newRow = Math.max(effectiveTopMargin, Math.min((effectiveTopMargin + y), (effectiveBottomMargin - 1)));
        final var newCol = Math.max(effectiveLeftMargin, Math.min((effectiveLeftMargin + x), (effectiveRightMargin - 1)));
        this.setCursorRowCol(newRow, newCol);
    }

    private void scrollDownOneLine() {
        this.scrollCounter++;
        if (0 != this.mLeftMargin || this.mRightMargin != this.mColumns) { // Horizontal margin: Do not put anything into scroll history, just non-margin part of console up.
            this.screen.blockCopy(this.mLeftMargin, this.mTopMargin + 1, this.mRightMargin - this.mLeftMargin, this.mBottomMargin - this.mTopMargin - 1, this.mLeftMargin, this.mTopMargin); // .. and blank bottom row between margins:
            this.screen.blockSet(this.mLeftMargin, this.mBottomMargin - 1, this.mRightMargin - this.mLeftMargin, 1, ' ', this.mEffect);
        } else this.screen.scrollDownOneLine(this.mTopMargin, this.mBottomMargin, this.style());
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
     * [* <a href="https://vt100.net/docs/vt510-rm/chapter4.htm">...</a>](
     * )l#S4.3.3
     */
    private void parseArg(final int inputByte) {
        if ('0' <= inputByte && '9' >= inputByte) {
            if (this.mArgIndex < this.mArgs.length) {
                final int oldValue = this.mArgs[this.mArgIndex];
                final int thisDigit = inputByte - '0';
                int value = (0 <= oldValue) ? oldValue * 10 + thisDigit : thisDigit;
                if (9999 < value) value = 9999;
                this.mArgs[this.mArgIndex] = value;
            }
            this.continueSequence(this.mEscapeState);
        } else if (';' == inputByte) {
            if (this.mArgIndex < this.mArgs.length) this.mArgIndex++;
            this.continueSequence(this.mEscapeState);
        } else this.finishSequence();

    }

    private int getArg0(final int defaultValue) {
        return this.getArg(0, defaultValue, true);
    }

    private int getArg1(final int defaultValue) {
        return this.getArg(1, defaultValue, true);
    }

    private int getArg(final int index, final int defaultValue, final boolean treatZeroAsDefault) {
        var result = this.mArgs[index];
        if (0 > result || (0 == result && treatZeroAsDefault)) result = defaultValue;
        return result;
    }

    private void collectOSCArgs(final int b) {
        if (TerminalEmulator.MAX_OSC_STRING_LENGTH > this.mOSCOrDeviceControlArgs.length()) {
            this.mOSCOrDeviceControlArgs.appendCodePoint(b);
            this.continueSequence(this.mEscapeState);
        } else this.finishSequence();

    }

    private void finishSequence() {
        this.mEscapeState = TerminalEmulator.ESC_NONE;
    }

    /**
     * Send a Unicode code point to the console.
     *
     * @param codePoint The code point of the character to display
     */
    private void emitCodePoint(int codePoint) {
        this.mLastEmittedCodePoint = codePoint;
        if (this.mUseLineDrawingUsesG0 ? this.mUseLineDrawingG0 : this.mUseLineDrawingG1) { // http://www.vt100.net/docs/vt102-ug/table5-15.html.
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
        final var autoWrap = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_AUTOWRAP);
        final var displayWidth = WcWidth.width(codePoint);
        final var cursorInLastColumn = this.mCursorCol == this.mRightMargin - 1;
        if (autoWrap) {
            if (cursorInLastColumn && ((this.mAboutToAutoWrap && 1 == displayWidth) || 2 == displayWidth)) {
                this.screen.setLineWrap(this.mCursorRow);
                this.mCursorCol = this.mLeftMargin;
                if (this.mCursorRow + 1 < this.mBottomMargin) this.mCursorRow++;
                else this.scrollDownOneLine();
            }
        } else if (cursorInLastColumn && 2 == displayWidth) // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return;

        if (this.mInsertMode && 0 < displayWidth) { // Move character to right one space.
            final var destCol = this.mCursorCol + displayWidth;
            if (destCol < this.mRightMargin)
                this.screen.blockCopy(this.mCursorCol, this.mCursorRow, this.mRightMargin - destCol, 1, destCol, this.mCursorRow);
        }
        final var offsetDueToCombiningChar = (0 >= displayWidth && 0 < this.mCursorCol && !this.mAboutToAutoWrap) ? 1 : 0;
        var column = this.mCursorCol - offsetDueToCombiningChar; // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported // The offsetDueToCombiningChar would never be 1 if mCursorCol was 0 to get column/index=-1,
        // so was mCursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow, possibly causing others bugs too.
        if (0 > column) column = 0;
        this.screen.setChar(column, this.mCursorRow, codePoint, this.style());
        if (autoWrap && 0 < displayWidth)
            this.mAboutToAutoWrap = (this.mCursorCol == this.mRightMargin - displayWidth);
        this.mCursorCol = Math.min((this.mCursorCol + displayWidth), (this.mRightMargin - 1));
    }

    /**
     * Set the cursor mode, but limit it to margins if [.DECSET_BIT_ORIGIN_MODE] is enabled.
     */
    private void setCursorColRespectingOriginMode(final int col) {
        this.setCursorPosition(col, this.mCursorRow);
    }

    /**
     * TODO: Better name, distinguished from [.setCursorPosition] by not regarding origin mode.
     */
    private void setCursorRowCol(final int row, final int col) {
        this.mCursorRow = Math.max(0, Math.min(row, this.mRows - 1));
        this.mCursorCol = Math.max(0, Math.min(col, this.mColumns - 1));
        this.mAboutToAutoWrap = false;
    }

    public void clearScrollCounter() {
        this.scrollCounter = 0;
    }

    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    private void reset() {
        this.mArgIndex = 0;
        this.dontContinueSequence = true;
        this.mEscapeState = TerminalEmulator.ESC_NONE;
        this.mInsertMode = false;
        this.mTopMargin = this.mLeftMargin = 0;
        this.mBottomMargin = this.mRows;
        this.mRightMargin = this.mColumns;
        this.mAboutToAutoWrap = false;
        this.mForeColor = this.mSavedStateMain.mSavedForeColor = this.mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
        this.mBackColor = this.mSavedStateMain.mSavedBackColor = this.mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
        this.setDefaultTabStops();

        this.mUseLineDrawingG0 = this.mUseLineDrawingG1 = false;
        this.mUseLineDrawingUsesG0 = true;

        this.mSavedStateMain.mSavedCursorRow = this.mSavedStateMain.mSavedCursorCol = this.mSavedStateMain.mSavedEffect = this.mSavedStateMain.mSavedDecFlags = 0;
        this.mSavedStateAlt.mSavedCursorRow = this.mSavedStateAlt.mSavedCursorCol = this.mSavedStateAlt.mSavedEffect = this.mSavedStateAlt.mSavedDecFlags = 0;
        this.mCurrentDecSetFlags = 0;
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_AUTOWRAP, true);
        this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_CURSOR_ENABLED, true);
        this.mSavedDecSetFlags = this.mSavedStateMain.mSavedDecFlags = this.mSavedStateAlt.mSavedDecFlags = this.mCurrentDecSetFlags;

        // XXX: Should we set terminal driver back to IUTF8 with termios?
        this.mUtf8Index = this.mUtf8ToFollow = 0;

        this.mColors.reset();
    }

    public String getSelectedText() {
        return this.screen.getSelectedText(TextSelectionCursorController.selectors[0], TextSelectionCursorController.selectors[1], TextSelectionCursorController.selectors[2], TextSelectionCursorController.selectors[3]);
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    public void paste(String text) { // First: Always remove escape Key and C1 control characters [0x80,0x9F]:
        text = TerminalEmulator.REGEX.matcher(text).replaceAll(""); // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        text = TerminalEmulator.PATTERN.matcher(text).replaceAll("\r"); // Then: Implement bracketed paste mode if enabled:
        final boolean bracketed = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_BRACKETED_PASTE_MODE);
        if (bracketed) this.mSession.write("\033[200~");
        this.mSession.write(text);
        if (bracketed) this.mSession.write("\033[201~");
    }

    /**
     * [...](<a href="http://www.vt100.net/docs/vt510-rm/DECSC">...</a>)
     */
    private static class SavedScreenState {
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
