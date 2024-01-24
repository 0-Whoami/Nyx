package com.termux.terminal;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 * <p>
 * References:
 * <ul>
 * <li><a href="http://invisible-island.net/xterm/ctlseqs/ctlseqs.html">...</a></li>
 * <li><a href="http://en.wikipedia.org/wiki/ANSI_escape_code">...</a></li>
 * <li><a href="http://man.he.net/man4/console_codes">...</a></li>
 * <li><a href="http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c">...</a></li>
 * <li><a href="http://www.columbia.edu/~kermit/k95manual/iso2022.html">...</a></li>
 * <li><a href="http://www.vt100.net/docs/vt510-rm/chapter4">...</a></li>
 * <li><a href="http://en.wikipedia.org/wiki/ISO/IEC_2022">...</a> - for 7-bit and 8-bit GL GR explanation</li>
 * <li><a href="http://bjh21.me.uk/all-escapes/all-escapes.txt">...</a> - extensive!</li>
 * <li><a href="http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref">...</a>.
 * html - document for konsole - accessible!</li>
 * </ul>
 */
public final class TerminalEmulator {

    public static final int MOUSE_LEFT_BUTTON = 0;

    /**
     * Mouse moving while having left mouse button pressed.
     */
    public static final int MOUSE_LEFT_BUTTON_MOVED = 32;

    public static final int MOUSE_WHEELUP_BUTTON = 64;

    public static final int MOUSE_WHEELDOWN_BUTTON = 65;

    /**
     * Used for invalid data - <a href="http://en.wikipedia.org/wiki/Replacement_character#Replacement_character">...</a>
     */
    public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;
    /* The supported terminal cursor styles. */
    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;
    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;
    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;
    /**
     * Escape processing: Not currently in an escape sequence.
     */
    private static final int ESC_NONE = 0;
    /**
     * Escape processing: Have seen an ESC character - proceed to {@link #doEsc(int)}
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
     * Escape processing: ESC %
     */
    private static final int ESC_PERCENT = 9;
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
     * Escape processing: APC
     */
    private static final int ESC_APC = 20;
    private static final int ESC_APC_ESC = 21;
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
     * <a href="http://www.vt100.net/docs/vt510-rm/DECOM">...</a>: "When DECOM is set, the home cursor position is at the upper-left
     * corner of the screen, within the margins. The starting point for line numbers depends on the current top margin
     * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
     * upper-left corner of the screen. The starting point for line numbers is independent of the margins. The cursor
     * can move outside of the margins."
     */
    private static final int DECSET_BIT_ORIGIN_MODE = 1 << 2;
    /**
     * <a href="http://www.vt100.net/docs/vt510-rm/DECAWM">...</a>: "If the DECAWM function is set, then graphic characters received when
     * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
     * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
     * characters received when the cursor is at the right border of the page replace characters already on the page."
     */
    private static final int DECSET_BIT_AUTOWRAP = 1 << 3;
    /**
     * DECSET 25 - if the cursor should be enabled, {@link #isCursorEnabled()}.
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
     * DECSET 2004 - see {@link #paste(String)}
     */
    private static final int DECSET_BIT_BRACKETED_PASTE_MODE = 1 << 10;
    /**
     * Toggled with DECLRMM - <a href="http://www.vt100.net/docs/vt510-rm/DECLRMM">...</a>
     */
    private static final int DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 << 11;
    /**
     * Not really DECSET bit... - <a href="http://www.vt100.net/docs/vt510-rm/DECSACE">...</a>
     */
    private static final int DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 << 12;
    private static final int DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000;
    private static final int DEFAULT_TERMINAL_CURSOR_STYLE = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
    private static final Pattern PATTERN = Pattern.compile("\r?\n");
    private static final Pattern REGEX = Pattern.compile("(\u001B|[\u0080-\u009F])");
    private static final Pattern REGEXP = Pattern.compile("[0-9;]*q.*");
    public final TerminalColors mColors = new TerminalColors();
    private final Stack<String> mTitleStack = new Stack<>();
    /**
     * The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal.
     */
    private final TerminalBuffer mMainBuffer;
    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate screen buffer is active, you cannot scroll back to view saved lines).
     * <p>
     * See <a href="http://www.xfree86.org/current/ctlseqs.html#The%20Alternate%20Screen%20Buffer">...</a>
     */
    private final TerminalBuffer mAltBuffer;
    /**
     * The terminal session this emulator is bound to.
     */
    private final TerminalSession mSession;
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
    private final boolean mBoldWithBright;
    private final byte[] mUtf8InputBuffer = new byte[4];
    /**
     * The number of character rows and columns in the terminal screen.
     */
    public int mRows, mColumns;
    private String mTitle;
    /**
     * If processing first character of first parameter of {@link #ESC_CSI}.
     */
    private boolean mIsCSIStart;
    /**
     * The last character processed of a parameter of {@link #ESC_CSI}.
     */
    private Integer mLastCSIArg;
    /**
     * The cursor position. Between (0,0) and (mRows-1, mColumns-1).
     */
    private int mCursorRow, mCursorCol;
    /**
     * The terminal cursor styles.
     */
    private int mCursorStyle = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;
    /**
     * The current screen buffer, pointing at either {@link #mMainBuffer} or {@link #mAltBuffer}.
     */
    private TerminalBuffer mScreen;
    private TerminalSessionClient mClient;
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
    private boolean ESC_P_escape;
    private boolean ESC_P_sixel;
    private List<Byte> ESC_OSC_data;
    private int ESC_OSC_colon;
    /**
     * <a href="http://www.vt100.net/docs/vt102-ug/table5-15.html">...</a>
     */
    private boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;
    /**
     * @see TerminalEmulator#mapDecSetBitToInternalBit(int)
     */
    private int mCurrentDecSetFlags, mSavedDecSetFlags;
    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private boolean mInsertMode;
    /**
     * An array of tab stops. mTabStop[i] is true if there is a tab stop set for column i.
     */
    private boolean[] mTabStop;
    /**
     * Top margin of screen for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
     * (Defines the first row after the scrolling region). Left/right margin in [0, mColumns].
     */
    private int mTopMargin, mBottomMargin, mLeftMargin, mRightMargin;
    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (mColumns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private boolean mAboutToAutoWrap;
    /**
     * If the cursor blinking is enabled. It requires cursor itself to be enabled, which is controlled
     * byt whether {@link #DECSET_BIT_CURSOR_ENABLED} bit is set or not.
     */
    private boolean mCursorBlinkingEnabled;
    /**
     * If currently cursor should be in a visible state or not if {@link #mCursorBlinkingEnabled}
     * is {@code true}.
     */
    private boolean mCursorBlinkState;
    /**
     * Current foreground and background colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     *
     * @see TextStyle
     */
    private int mForeColor;
    private int mBackColor;
    /**
     * Current {@link TextStyle} effect.
     */
    private int mEffect;
    /**
     * The number of scrolled lines since last calling {@link #clearScrollCounter()}. Used for moving selection up along
     * with the scrolling text.
     */
    private int mScrollCounter;
    private byte mUtf8ToFollow, mUtf8Index;
    private int mLastEmittedCodePoint = -1;
    private int cellW = 12, cellH = 12;

    public TerminalEmulator(final TerminalSession session, final boolean boldWithBright, final int columns, final int rows, final Integer transcriptRows, final TerminalSessionClient client) {
        super();
        this.mSession = session;
        this.mScreen = this.mMainBuffer = new TerminalBuffer(columns, TerminalEmulator.getTerminalTranscriptRows(transcriptRows), rows);
        this.mAltBuffer = new TerminalBuffer(columns, rows, rows);
        this.mClient = client;
        this.mBoldWithBright = boldWithBright;
        this.mRows = rows;
        this.mColumns = columns;
        this.mTabStop = new boolean[this.mColumns];
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

    private static int getTerminalTranscriptRows(final Integer transcriptRows) {
        return Objects.<Integer>requireNonNullElse(transcriptRows, TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS);
    }

    public void setCellSize(final int w, final int h) {
        this.cellW = w;
        this.cellH = h;
    }

    private boolean isDecsetInternalBitSet(final int bit) {
        return (this.mCurrentDecSetFlags & bit) != 0;
    }

    private void setDecsetinternalBit(final int internalBit, final boolean set) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false);
            } else if (TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT == internalBit) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false);
            }
        }
        if (set) {
            mCurrentDecSetFlags |= internalBit;
        } else {
            mCurrentDecSetFlags &= ~internalBit;
        }
    }

    public void updateTermuxTerminalSessionClientBase(TerminalSessionClient client) {
        mClient = client;
        setCursorStyle();
        mCursorBlinkState = true;
    }

    public TerminalBuffer getScreen() {
        return mScreen;
    }

    public boolean isAlternateBufferActive() {
        return mScreen == mAltBuffer;
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    public void sendMouseEvent(int mouseButton, int column, int row, boolean pressed) {
        if (1 > column)
            column = 1;
        if (column > mColumns)
            column = mColumns;
        if (1 > row)
            row = 1;
        if (row > mRows)
            row = mRows;
        if (!(MOUSE_LEFT_BUTTON_MOVED == mouseButton && !this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) && this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
            this.mSession.write(String.format("\033[<%d;%d;%d" + (pressed ? 'M' : 'm'), mouseButton, column, row));
        } else {
            // 3 for release of all buttons.
            mouseButton = pressed ? mouseButton : 3;
            // Clip to screen, and clip to the limits of 8-bit data.
            final boolean out_of_bounds = 255 - 32 < column || 255 - 32 < row;
            if (!out_of_bounds) {
                final byte[] data = {'\033', '[', 'M', (byte) (32 + mouseButton), (byte) (32 + column), (byte) (32 + row)};
                this.mSession.write(data, 0, data.length);
            }
        }
    }

    public void resize(final int columns, final int rows) {
        if (this.mRows == rows && this.mColumns == columns) {
            return;
        } else if (2 > columns || 2 > rows) {
            throw new IllegalArgumentException("rows=" + rows + ", columns=" + columns);
        }
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
        int[] cursor = {this.mCursorCol, this.mCursorRow};
        final int newTotalRows = (this.mScreen == this.mAltBuffer) ? this.mRows : this.mMainBuffer.mTotalRows;
        this.mScreen.resize(this.mColumns, this.mRows, newTotalRows, cursor, this.getStyle(), this.isAlternateBufferActive());
        this.mCursorCol = cursor[0];
        this.mCursorRow = cursor[1];
    }

    public int getCursorRow() {
        return this.mCursorRow;
    }

    private void setCursorRow(final int row) {
        this.mCursorRow = row;
        this.mAboutToAutoWrap = false;
    }

    public int getCursorCol() {
        return this.mCursorCol;
    }

    private void setCursorCol(final int col) {
        this.mCursorCol = col;
        this.mAboutToAutoWrap = false;
    }

    public int getCursorStyle() {
        return this.mCursorStyle;
    }

    /**
     * Set the terminal cursor style.
     */
    private void setCursorStyle() {
        this.mCursorStyle = this.mClient.getTerminalCursorStyle();
    }

    public boolean isReverseVideo() {
        return this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_REVERSE_VIDEO);
    }

    private boolean isCursorEnabled() {
        return !this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_CURSOR_ENABLED);
    }

    public boolean shouldCursorBeVisible() {
        if (this.isCursorEnabled())
            return false;
        else
            return !this.mCursorBlinkingEnabled || this.mCursorBlinkState;
    }

    public void setCursorBlinkState(final boolean cursorBlinkState) {
        mCursorBlinkState = cursorBlinkState;
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

    /**
     * Indicates if bold should be shown with bright colors.
     */
    public boolean isBoldWithBright() {
        return this.mBoldWithBright;
    }

    private void setDefaultTabStops() {
        for (int i = 0; i < this.mColumns; i++) this.mTabStop[i] = 0 == (i & 7) && 0 != i;
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    public void append(final byte[] buffer, final int length) {
        for (int i = 0; i < length; i++) this.processByte(buffer[i]);
    }

    private void processByte(final byte byteToProcess) {
        if (0 < mUtf8ToFollow) {
            if (0b10000000 == (byteToProcess & 0b11000000)) {
                // 10xxxxxx, a continuation byte.
                this.mUtf8InputBuffer[this.mUtf8Index] = byteToProcess;
                this.mUtf8Index++;
                --mUtf8ToFollow;
                if (0 == mUtf8ToFollow) {
                    final byte firstByteMask = (byte) (2 == mUtf8Index ? 0b00011111 : (3 == mUtf8Index ? 0b00001111 : 0b00000111));
                    int codePoint = (this.mUtf8InputBuffer[0] & firstByteMask);
                    for (int i = 1; i < this.mUtf8Index; i++)
                        codePoint = ((codePoint << 6) | (this.mUtf8InputBuffer[i] & 0b00111111));
                    if (((0b1111111 >= codePoint) && 1 < mUtf8Index) || (0b11111111111 > codePoint && 2 < mUtf8Index) || (0b1111111111111111 > codePoint && 3 < mUtf8Index)) {
                        // Overlong encoding.
                        codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                    }
                    this.mUtf8Index = 0;
                    if (0x80 > codePoint || 0x9F < codePoint) {
                        codePoint = switch (Character.getType(codePoint)) {
                            case Character.UNASSIGNED, Character.SURROGATE ->
                                TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                            default -> codePoint;
                        };
                        this.processCodePoint(codePoint);
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                this.mUtf8Index = this.mUtf8ToFollow = 0;
                this.emitCodePoint(TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                // The Unicode Standard Version 6.2 – Core Specification
                // (http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf):
                // "If the converter encounters an ill-formed UTF-8 code unit sequence which starts with a valid first
                // byte, but which does not continue with valid successor bytes (see Table 3-7), it must not consume the
                // successor bytes as part of the ill-formed subsequence
                // whenever those successor bytes themselves constitute part of a well-formed UTF-8 code unit
                // subsequence."
                this.processByte(byteToProcess);
            }
        } else {
            if (0 == (byteToProcess & 0b10000000)) {
                // The leading bit is not set so it is a 7-bit ASCII character.
                this.processCodePoint(byteToProcess);
                return;
            } else if (0b11000000 == (byteToProcess & 0b11100000)) {
                // 110xxxxx, a two-byte sequence.
                this.mUtf8ToFollow = 1;
            } else if (0b11100000 == (byteToProcess & 0b11110000)) {
                // 1110xxxx, a three-byte sequence.
                this.mUtf8ToFollow = 2;
            } else if (0b11110000 == (byteToProcess & 0b11111000)) {
                // 11110xxx, a four-byte sequence.
                this.mUtf8ToFollow = 3;
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                this.processCodePoint(TerminalEmulator.UNICODE_REPLACEMENT_CHAR);
                return;
            }
            this.mUtf8InputBuffer[this.mUtf8Index] = byteToProcess;
            this.mUtf8Index++;
        }
    }

    private void processCodePoint(int b) {
        mScreen.bitmapGC(300000);
        switch (b) {
            case // Null character (NUL, ^@). Do nothing.
                0:
                break;
            case // Bell (BEL, ^G, \a). If in an OSC sequence, BEL may terminate a string; otherwise signal bell.
                7:
                if (TerminalEmulator.ESC_OSC == mEscapeState)
                    doOsc(b);
                else {
                    if (TerminalEmulator.ESC_APC == mEscapeState) {
                        doApc(b);
                    }
                }
                break;
            case // Backspace (BS, ^H).
                8:
                if (mLeftMargin == mCursorCol) {
                    // Jump to previous line if it was auto-wrapped.
                    int previousRow = mCursorRow - 1;
                    if (0 <= previousRow && mScreen.getLineWrap(previousRow)) {
                        mScreen.clearLineWrap(previousRow);
                        setCursorRowCol(previousRow, mRightMargin - 1);
                    }
                } else {
                    setCursorCol(mCursorCol - 1);
                }
                break;
            case // Horizontal tab (HT, \t) - move to next tab stop, but not past edge of screen
                9:
                // XXX: Should perhaps use color if writing to new cells. Try with
                //       printf "\033[41m\tXX\033[0m\n"
                // The OSX Terminal.app colors the spaces from the tab red, but xterm does not.
                // Note that Terminal.app only colors on new cells, in e.g.
                //       printf "\033[41m\t\r\033[42m\tXX\033[0m\n"
                // the first cells are created with a red background, but when tabbing over
                // them again with a green background they are not overwritten.
                mCursorCol = nextTabStop(1);
                break;
            // Line feed (LF, \n).
            case 10:
                // Vertical tab (VT, \v).
            case 11:
            case // Form feed (FF, \f).
                12:
                if ((TerminalEmulator.ESC_P != mEscapeState || !ESC_P_sixel) && 0 >= this.ESC_OSC_colon) {
                    // Ignore CR/LF inside sixels or iterm2 data
                    doLinefeed();
                }
                break;
            case // Carriage return (CR, \r).
                13:
                if ((TerminalEmulator.ESC_P != mEscapeState || !ESC_P_sixel) && 0 >= this.ESC_OSC_colon) {
                    // Ignore CR/LF inside sixels or iterm2 data
                    setCursorCol(mLeftMargin);
                }
                break;
            case // Shift Out (Ctrl-N, SO) → Switch to Alternate Character Set. This invokes the G1 character set.
                14:
                mUseLineDrawingUsesG0 = false;
                break;
            case // Shift In (Ctrl-O, SI) → Switch to Standard Character Set. This invokes the G0 character set.
                15:
                mUseLineDrawingUsesG0 = true;
                break;
            // CAN.
            case 24:
            case // SUB.
                26:
                if (TerminalEmulator.ESC_NONE != mEscapeState) {
                    // FIXME: What is this??
                    mEscapeState = ESC_NONE;
                    emitCodePoint(127);
                }
                break;
            case // ESC
                27:
                // Starts an escape sequence unless we're parsing a string
                if (TerminalEmulator.ESC_P == mEscapeState) {
                    // XXX: Ignore escape when reading device control sequence, since it may be part of string terminator.
                    ESC_P_escape = true;
                    return;
                } else if (TerminalEmulator.ESC_OSC != mEscapeState) {
                    if (TerminalEmulator.ESC_APC != mEscapeState) {
                        startEscapeSequence();
                    } else {
                        doApc(b);
                    }
                } else {
                    doOsc(b);
                }
                break;
            default:
                mContinueSequence = false;
                switch (mEscapeState) {
                    case ESC_NONE:
                        if (32 <= b)
                            emitCodePoint(b);
                        break;
                    case ESC:
                        doEsc(b);
                        break;
                    case ESC_POUND:
                        doEscPound(b);
                        break;
                    case // Designate G0 Character Set (ISO 2022, VT100).
                        ESC_SELECT_LEFT_PAREN:
                        mUseLineDrawingG0 = ('0' == b);
                        break;
                    case // Designate G1 Character Set (ISO 2022, VT100).
                        ESC_SELECT_RIGHT_PAREN:
                        mUseLineDrawingG1 = ('0' == b);
                        break;
                    case ESC_CSI:
                        doCsi(b);
                        break;
                    case ESC_CSI_EXCLAMATION:
                        if ('p' == b) {
                            // Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                            reset();
                        } else {
                            finishSequence();
                        }
                        break;
                    case ESC_CSI_QUESTIONMARK:
                        doCsiQuestionMark(b);
                        break;
                    case ESC_CSI_BIGGERTHAN:
                        doCsiBiggerThan(b);
                        break;
                    case ESC_CSI_DOLLAR:
                        boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
                        int effectiveTopMargin = originMode ? mTopMargin : 0;
                        int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
                        int effectiveLeftMargin = originMode ? mLeftMargin : 0;
                        int effectiveRightMargin = originMode ? mRightMargin : mColumns;
                        switch (b) {
                            case // ${CSI}${SRC_TOP}${SRC_LEFT}${SRC_BOTTOM}${SRC_RIGHT}${SRC_PAGE}${DST_TOP}${DST_LEFT}${DST_PAGE}$v"
                                'v':
                                // Copy rectangular area (DECCRA - http://vt100.net/docs/vt510-rm/DECCRA):
                                // "If Pbs is greater than Pts, or Pls is greater than Prs, the terminal ignores DECCRA.
                                // The coordinates of the rectangular area are affected by the setting of origin mode (DECOM).
                                // DECCRA is not affected by the page margins.
                                // The copied text takes on the line attributes of the destination area.
                                // If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, then the value
                                // is treated as the width or height of that page.
                                // If the destination area is partially off the page, then DECCRA clips the off-page data.
                                // DECCRA does not change the active cursor position."
                                final int topSource = Math.min(this.getArg(0, 1, true) - 1 + effectiveTopMargin, this.mRows);
                                final int leftSource = Math.min(this.getArg(1, 1, true) - 1 + effectiveLeftMargin, this.mColumns);
                                // Inclusive, so do not subtract one:
                                final int bottomSource = Math.min(Math.max(this.getArg(2, this.mRows, true) + effectiveTopMargin, topSource), this.mRows);
                                final int rightSource = Math.min(Math.max(this.getArg(3, this.mColumns, true) + effectiveLeftMargin, leftSource), this.mColumns);
                                // int sourcePage = getArg(4, 1, true);
                                final int destionationTop = Math.min(this.getArg(5, 1, true) - 1 + effectiveTopMargin, this.mRows);
                                final int destinationLeft = Math.min(this.getArg(6, 1, true) - 1 + effectiveLeftMargin, this.mColumns);
                                // int destinationPage = getArg(7, 1, true);
                                final int heightToCopy = Math.min(this.mRows - destionationTop, bottomSource - topSource);
                                final int widthToCopy = Math.min(this.mColumns - destinationLeft, rightSource - leftSource);
                                mScreen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop);
                                break;
                            // ${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${"
                            case '{':
                                // Selective erase rectangular area (DECSERA - http://www.vt100.net/docs/vt510-rm/DECSERA).
                                // ${CSI}${CHAR};${TOP}${LEFT}${BOTTOM}${RIGHT}$x"
                            case 'x':
                                // Fill rectangular area (DECFRA - http://www.vt100.net/docs/vt510-rm/DECFRA).
                            case // ${CSI}$${TOP}${LEFT}${BOTTOM}${RIGHT}$z"
                                'z':
                                // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                final boolean erase = 'x' != b;
                                final boolean selective = '{' == b;
                                // Only DECSERA keeps visual attributes, DECERA does not:
                                final boolean keepVisualAttributes = erase && selective;
                                int argIndex = 0;
                                final int fillChar;
                                if (erase) {
                                    fillChar = ' ';
                                } else {
                                    fillChar = getArg(argIndex, -1, true);
                                    argIndex++;
                                }
                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if ((32 <= fillChar && 126 >= fillChar) || (160 <= fillChar && 255 >= fillChar)) {
                                    // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    final int top = Math.min(this.getArg(argIndex, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1);
                                    argIndex++;
                                    final int left = Math.min(this.getArg(argIndex, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1);
                                    argIndex++;
                                    final int bottom = Math.min(this.getArg(argIndex, this.mRows, true) + effectiveTopMargin, effectiveBottomMargin);
                                    argIndex++;
                                    final int right = Math.min(this.getArg(argIndex, this.mColumns, true) + effectiveLeftMargin, effectiveRightMargin);
                                    final long style = this.getStyle();
                                    for (int row = top - 1; row < bottom; row++)
                                        for (int col = left - 1; col < right; col++)
                                            if (!selective || 0 == (TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED))
                                                this.mScreen.setChar(col, row, fillChar, keepVisualAttributes ? this.mScreen.getStyleAt(row, col) : style);
                                }
                                break;
                            // "${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${ATTRIBUTES}$r"
                            case 'r':
                                // Change attributes in rectangular area (DECCARA - http://vt100.net/docs/vt510-rm/DECCARA).
                            case // "${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${ATTRIBUTES}$t"
                                't':
                                // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                final boolean reverse = 't' == b;
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                final int top = Math.min(this.getArg(0, 1, true) - 1, effectiveBottomMargin) + effectiveTopMargin;
                                final int left = Math.min(this.getArg(1, 1, true) - 1, effectiveRightMargin) + effectiveLeftMargin;
                                final int bottom = Math.min(this.getArg(2, this.mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin;
                                final int right = Math.min(this.getArg(3, this.mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin;
                                if (4 <= mArgIndex) {
                                    if (this.mArgIndex >= this.mArgs.length)
                                        this.mArgIndex = this.mArgs.length - 1;
                                    for (int i = 4; i <= this.mArgIndex; i++) {
                                        int bits = 0;
                                        // True if setting, false if clearing.
                                        boolean setOrClear = true;
                                        switch (this.getArg(i, 0, false)) {
                                            case // Attributes off (no bold, no underline, no blink, positive image).
                                                0:
                                                bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BLINK | TextStyle.CHARACTER_ATTRIBUTE_INVERSE);
                                                if (!reverse)
                                                    setOrClear = false;
                                                break;
                                            case // Bold.
                                                1:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                                break;
                                            case // Underline.
                                                4:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                                break;
                                            case // Blink.
                                                5:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                                break;
                                            case // Negative image.
                                                7:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                                break;
                                            case // No bold.
                                                22:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                                setOrClear = false;
                                                break;
                                            case // No underline.
                                                24:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                                setOrClear = false;
                                                break;
                                            case // No blink.
                                                25:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                                setOrClear = false;
                                                break;
                                            case // Positive image.
                                                27:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                                setOrClear = false;
                                                break;
                                        }
                                        if (!reverse || setOrClear) {
                                            mScreen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE), effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right);
                                        }
                                    }
                                }  // Do nothing.

                                break;
                            default:
                                finishSequence();
                        }
                        break;
                    case ESC_CSI_DOUBLE_QUOTE:
                        if ('q' == b) {
                            // http://www.vt100.net/docs/vt510-rm/DECSCA
                            int arg = getArg0(0);
                            if (0 == arg || 2 == arg) {
                                // DECSED and DECSEL can erase characters.
                                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
                            } else if (1 == arg) {
                                // DECSED and DECSEL cannot erase characters.
                                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
                            } else {
                                finishSequence();
                            }
                        } else {
                            this.finishSequence();
                        }
                        break;
                    case ESC_CSI_SINGLE_QUOTE:
                        if ('}' == b) {
                            // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                            final int columnsAfterCursor = this.mRightMargin - this.mCursorCol;
                            final int columnsToInsert = Math.min(this.getArg0(1), columnsAfterCursor);
                            final int columnsToMove = columnsAfterCursor - columnsToInsert;
                            this.mScreen.blockCopy(this.mCursorCol, 0, columnsToMove, this.mRows, this.mCursorCol + columnsToInsert, 0);
                            this.blockClear(this.mCursorCol, 0, columnsToInsert, this.mRows);
                        } else if ('~' == b) {
                            // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                            final int columnsAfterCursor = this.mRightMargin - this.mCursorCol;
                            final int columnsToDelete = Math.min(this.getArg0(1), columnsAfterCursor);
                            final int columnsToMove = columnsAfterCursor - columnsToDelete;
                            this.mScreen.blockCopy(this.mCursorCol + columnsToDelete, 0, columnsToMove, this.mRows, this.mCursorCol, 0);
                        } else {
                            this.finishSequence();
                        }
                        break;
                    case TerminalEmulator.ESC_PERCENT:
                        break;
                    case TerminalEmulator.ESC_APC:
                        this.doApc(b);
                        break;
                    case TerminalEmulator.ESC_APC_ESC:
                        this.doApcEsc(b);
                        break;
                    case TerminalEmulator.ESC_OSC:
                        this.doOsc(b);
                        break;
                    case TerminalEmulator.ESC_OSC_ESC:
                        this.doOscEsc(b);
                        break;
                    case TerminalEmulator.ESC_P:
                        this.doDeviceControl(b);
                        break;
                    case TerminalEmulator.ESC_CSI_QUESTIONMARK_ARG_DOLLAR:
                        if ('p' == b) {
                            // Request DEC private mode (DECRQM).
                            final int mode = this.getArg0(0);
                            final int value = this.getValues(mode);
                            this.mSession.write(String.format(Locale.US, "\033[?%d;%d$y", mode, value));
                        } else {
                            this.finishSequence();
                        }
                        break;
                    case TerminalEmulator.ESC_CSI_ARGS_SPACE:
                        final int arg = this.getArg0(0);
                        switch (b) {
                            case // "${CSI}${STYLE} q" - set cursor style (http://www.vt100.net/docs/vt510-rm/DECSCUSR).
                                'q':
                                switch (arg) {
                                    // Blinking block.
                                    case 0:
                                        // Blinking block.
                                    case 1:
                                    case // Steady block.
                                        2:
                                        this.mCursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                                        this.mCursorBlinkingEnabled = 2 != arg;
                                        break;
                                    // Blinking underline.
                                    case 3:
                                    case // Steady underline.
                                        4:
                                        this.mCursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
                                        this.mCursorBlinkingEnabled = 4 != arg;
                                        break;
                                    // Blinking bar (xterm addition).
                                    case 5:
                                    case // Steady bar (xterm addition).
                                        6:
                                        this.mCursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
                                        this.mCursorBlinkingEnabled = 6 != arg;
                                        break;
                                }
                                break;
                            case 't':
                            case 'u':
                                // Set margin-bell volume - ignore.
                                break;
                            default:
                                this.finishSequence();
                        }
                        break;
                    case TerminalEmulator.ESC_CSI_ARGS_ASTERIX:
                        final int attributeChangeExtent = this.getArg0(0);
                        if ('x' == b && (0 <= attributeChangeExtent && 2 >= attributeChangeExtent)) {
                            // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, 2 == attributeChangeExtent);
                        } else {
                            this.finishSequence();
                        }
                        break;
                    default:
                        this.finishSequence();
                        break;
                }
                if (!this.mContinueSequence)
                    this.mEscapeState = TerminalEmulator.ESC_NONE;
                break;
        }
    }

    private int getValues(final int mode) {
        final int value;
        if (47 == mode || 1047 == mode || 1049 == mode) {
            // This state is carried by mScreen pointer.
            value = (this.mScreen == this.mAltBuffer) ? 1 : 2;
        } else {
            final int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(mode);
            if (-1 != internalBit) {
                // 1=set, 2=reset.
                value = this.isDecsetInternalBitSet(internalBit) ? 1 : 2;
            } else {
                // 0=not recognized, 3=permanently set, 4=permanently reset
                value = 0;
            }
        }
        return value;
    }

    /**
     * When in {@link #ESC_P} ("device control") sequence.
     */
    private void doDeviceControl(final int b) {
        boolean firstSixel = false;
        if (!this.ESC_P_sixel && ('$' == b || '-' == b || '#' == b)) {
            //Check if sixel sequence that needs breaking
            final String dcs = this.mOSCOrDeviceControlArgs.toString();
            if (TerminalEmulator.REGEXP.matcher(dcs).matches()) {
                firstSixel = true;
            }
        }
        if (firstSixel || (this.ESC_P_escape && '\\' == b) || (this.ESC_P_sixel && ('$' == b || '-' == b || '#' == b))) // ESC \ terminates OSC
        // Sixel sequences may be very long. '$' and '!' are natural for breaking the sequence.
        {
            String dcs = this.mOSCOrDeviceControlArgs.toString();
            // DCS $ q P t ST. Request Status String (DECRQSS)
            if (dcs.startsWith("$q")) {
                if ("$q\"p".equals(dcs)) {
                    // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                    final String csiString = "64;1\"p";
                    this.mSession.write("\033P1$r" + csiString + "\033\\");
                } else {
                    this.finishSequence();
                }
            } else if (dcs.startsWith("+q")) {
                // Request Termcap/Terminfo String. The string following the "q" is a list of names encoded in
                // hexadecimal (2 digits per character) separated by ; which correspond to termcap or terminfo key
                // names.
                // Two special features are also recognized, which are not key names: Co for termcap colors (or colors
                // for terminfo colors), and TN for termcap name (or name for terminfo name).
                // xterm responds with DCS 1 + r P t ST for valid requests, adding to P t an = , and the value of the
                // corresponding string that xterm would send, or DCS 0 + r P t ST for invalid requests. The strings are
                // encoded in hexadecimal (2 digits per character).
                // Example:
                // :kr=\EOC: ks=\E[?1h\E=: ku=\EOA: le=^H:mb=\E[5m:md=\E[1m:\
                // where
                // kd=down-arrow key
                // kl=left-arrow key
                // kr=right-arrow key
                // ku=up-arrow key
                // #2=key_shome, "shifted home"
                // #4=key_sleft, "shift arrow left"
                // %i=key_sright, "shift arrow right"
                // *7=key_send, "shifted end"
                // k1=F1 function key
                // Example: Request for ku is "ESC P + q 6 b 7 5 ESC \", where 6b7d=ku in hexadecimal.
                // Xterm response in normal cursor mode:
                // "<27> P 1 + r 6 b 7 5 = 1 B 5 B 4 1" where 0x1B 0x5B 0x41 = 27 91 65 = ESC [ A
                // Xterm response in application cursor mode:
                // "<27> P 1 + r 6 b 7 5 = 1 B 5 B 4 1" where 0x1B 0x4F 0x41 = 27 91 65 = ESC 0 A
                // #4 is "shift arrow left":
                // *** Device Control (DCS) for '#4'- 'ESC P + q 23 34 ESC \'
                // Response: <27> P 1 + r 2 3 3 4 = 1 B 5 B 3 1 3 B 3 2 4 4 <27> \
                // where 0x1B 0x5B 0x31 0x3B 0x32 0x44 = ESC [ 1 ; 2 D
                // which we find in: TermKeyListener.java: KEY_MAP.put(KEYMOD_SHIFT | KEYCODE_DPAD_LEFT, "\033[1;2D");
                // See http://h30097.www3.hp.com/docs/base_doc/DOCUMENTATION/V40G_HTML/MAN/MAN4/0178____.HTM for what to
                // respond, as well as http://www.freebsd.org/cgi/man.cgi?query=termcap&sektion=5#CAPABILITIES for
                // the meaning of e.g. "ku", "kd", "kr", "kl"
                for (final String part : dcs.substring(2).split(";")) {
                    if (0 == part.length() % 2) {
                        final StringBuilder transBuffer = new StringBuilder();
                        char c;
                        for (int i = 0; i < part.length(); i += 2) {
                            try {
                                c = (char) Long.decode("0x" + part.charAt(i) + part.charAt(i + 1)).longValue();
                            } catch (final NumberFormatException e) {
                                continue;
                            }
                            transBuffer.append(c);
                        }
                        final String trans = transBuffer.toString();
                        final String responseValue = switch (trans) {
                            case "Co", "colors" ->
                                // Number of colors.
                                "256";
                            case "TN", "name" -> "xterm";
                            default ->
                                KeyHandler.getCodeFromTermcap(trans, this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_CURSOR_KEYS), this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD));
                        };
                        if (null == responseValue) {
                            switch (trans) {
                                // Help key - ignore
                                case "%1":
                                case // Undo key - ignore.
                                    "&8":
                                    break;
                            }
                            // Respond with invalid request:
                            this.mSession.write("\033P0+r" + part + "\033\\");
                        } else {
                            final StringBuilder hexEncoded = new StringBuilder();
                            for (int j = 0; j < responseValue.length(); j++) {
                                hexEncoded.append(String.format("%02X", (int) responseValue.charAt(j)));
                            }
                            this.mSession.write("\033P1+r" + part + "=" + hexEncoded + "\033\\");
                        }
                    }
                }
            } else if (this.ESC_P_sixel || TerminalEmulator.REGEXP.matcher(dcs).matches()) {
                int pos = 0;
                if (!this.ESC_P_sixel) {
                    this.ESC_P_sixel = true;
                    this.mScreen.sixelStart(100, 100);
                    while ('q' != dcs.codePointAt(pos)) {
                        pos++;
                    }
                    pos++;
                }
                if ('$' == b || '-' == b) {
                    // Add to string
                    dcs = dcs + (char) b;
                }
                int rep = 1;
                while (pos < dcs.length()) {
                    if ('"' == dcs.codePointAt(pos)) {
                        pos++;
                        //int[] args = { 0, 0, 0, 0 };
                        int arg = 0;
                        while (pos < dcs.length() && (('0' <= dcs.codePointAt(pos) && '9' >= dcs.codePointAt(pos)) || ';' == dcs.codePointAt(pos))) {
                            if ('0' > dcs.codePointAt(pos) || '9' < dcs.codePointAt(pos)) {
                                arg++;
                                if (3 < arg) {
                                    break;
                                }
                            }
                            pos++;
                        }
                        if (pos == dcs.length()) {
                            break;
                        }
                    } else if ('#' == dcs.codePointAt(pos)) {
                        int col = 0;
                        pos++;
                        while (pos < dcs.length() && '0' <= dcs.codePointAt(pos) && '9' >= dcs.codePointAt(pos)) {
                            col = col * 10 + dcs.codePointAt(pos) - '0';
                            pos++;
                        }
                        if (pos == dcs.length() || ';' != dcs.codePointAt(pos)) {
                            this.mScreen.sixelSetColor(col);
                        } else {
                            pos++;
                            final int[] args = {0, 0, 0, 0};
                            int arg = 0;
                            while (pos < dcs.length() && (('0' <= dcs.codePointAt(pos) && '9' >= dcs.codePointAt(pos)) || ';' == dcs.codePointAt(pos))) {
                                if ('0' <= dcs.codePointAt(pos) && '9' >= dcs.codePointAt(pos)) {
                                    args[arg] = args[arg] * 10 + dcs.codePointAt(pos) - '0';
                                } else {
                                    arg++;
                                    if (3 < arg) {
                                        break;
                                    }
                                }
                                pos++;
                            }
                            if (2 == args[0]) {
                                this.mScreen.sixelSetColor(col, args[1], args[2], args[3]);
                            }
                        }
                    } else if ('!' == dcs.codePointAt(pos)) {
                        rep = 0;
                        pos++;
                        while (pos < dcs.length() && '0' <= dcs.codePointAt(pos) && '9' >= dcs.codePointAt(pos)) {
                            rep = rep * 10 + dcs.codePointAt(pos) - '0';
                            pos++;
                        }
                    } else if ('$' == dcs.codePointAt(pos) || '-' == dcs.codePointAt(pos) || ('?' <= dcs.codePointAt(pos) && '~' >= dcs.codePointAt(pos))) {
                        this.mScreen.sixelChar(dcs.codePointAt(pos), rep);
                        pos++;
                        rep = 1;
                    } else {
                        pos++;
                    }
                }
                if ('\\' == b) {
                    this.ESC_P_sixel = false;
                    int n = this.mScreen.sixelEnd(this.mCursorRow, this.mCursorCol, this.cellW, this.cellH);
                    for (; 0 < n; n--) {
                        this.doLinefeed();
                    }
                } else {
                    this.mOSCOrDeviceControlArgs.setLength(0);
                    if ('#' == b) {
                        this.mOSCOrDeviceControlArgs.appendCodePoint('#');
                    }
                    // Do not finish sequence
                    this.continueSequence(this.mEscapeState);
                    return;
                }
            }
            this.finishSequence();
        } else {
            this.ESC_P_escape = false;
            if (MAX_OSC_STRING_LENGTH < mOSCOrDeviceControlArgs.length()) {
                // Too long.
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
            if (mTabStop[i]) {
                --numTabs;
                if (0 == numTabs) return Math.min(i, this.mRightMargin);
            }
        }
        return this.mRightMargin - 1;
    }

    /**
     * Process byte while in the {@link #ESC_CSI_QUESTIONMARK} escape state.
     */
    private void doCsiQuestionMark(final int b) {
        switch (b) {
            // Selective erase in display (DECSED) - http://www.vt100.net/docs/vt510-rm/DECSED.
            case 'J':
            case // Selective erase in line (DECSEL) - http://vt100.net/docs/vt510-rm/DECSEL.
                'K':
                this.mAboutToAutoWrap = false;
                final int fillChar = ' ';
                int startCol = -1;
                int startRow = -1;
                int endCol = -1;
                int endRow = -1;
                final boolean justRow = ('K' == b);
                switch (this.getArg0(0)) {
                    case // Erase from the active position to the end, inclusive (default).
                        0:
                        startCol = this.mCursorCol;
                        startRow = this.mCursorRow;
                        endCol = this.mColumns;
                        endRow = justRow ? (this.mCursorRow + 1) : this.mRows;
                        break;
                    case // Erase from start to the active position, inclusive.
                        1:
                        startCol = 0;
                        startRow = justRow ? this.mCursorRow : 0;
                        endCol = this.mCursorCol + 1;
                        endRow = this.mCursorRow + 1;
                        break;
                    case // Erase all of the display/line.
                        2:
                        startCol = 0;
                        startRow = justRow ? this.mCursorRow : 0;
                        endCol = this.mColumns;
                        endRow = justRow ? (this.mCursorRow + 1) : this.mRows;
                        break;
                    default:
                        this.finishSequence();
                        break;
                }
                final long style = this.getStyle();
                for (int row = startRow; row < endRow; row++) {
                    for (int col = startCol; col < endCol; col++) {
                        if (0 == (TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED))
                            this.mScreen.setChar(col, row, fillChar, style);
                    }
                }
                break;
            case 'h':
            case 'l':
                if (this.mArgIndex >= this.mArgs.length)
                    this.mArgIndex = this.mArgs.length - 1;
                for (int i = 0; i <= this.mArgIndex; i++)
                    this.doDecSetOrReset('h' == b, this.mArgs[i]);
                break;
            case // Device Status Report (DSR, DEC-specific).
                'n':
                if (6 == getArg0(-1)) {// Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                    this.mSession.write(String.format(Locale.US, "\033[?%d;%d;1R", this.mCursorRow + 1, this.mCursorCol + 1));
                } else {
                    this.finishSequence();
                    return;
                }
                break;
            case 'r':
            case 's':
                if (this.mArgIndex >= this.mArgs.length)
                    this.mArgIndex = this.mArgs.length - 1;
                for (int i = 0; i <= this.mArgIndex; i++) {
                    final int externalBit = this.mArgs[i];
                    final int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(externalBit);
                    if (-1 != internalBit) {
                        if ('s' == b) {
                            this.mSavedDecSetFlags |= internalBit;
                        } else {
                            this.doDecSetOrReset(0 != (mSavedDecSetFlags & internalBit), externalBit);
                        }
                    }
                }
                break;
            case '$':
                this.continueSequence(TerminalEmulator.ESC_CSI_QUESTIONMARK_ARG_DOLLAR);
                return;
            default:
                this.parseArg(b);
        }
    }

    private void doDecSetOrReset(final boolean setting, final int externalBit) {
        final int internalBit = TerminalEmulator.mapDecSetBitToInternalBit(externalBit);
        if (-1 != internalBit) {
            this.setDecsetinternalBit(internalBit, setting);
        }
        switch (externalBit) {
            case // Application Cursor Keys (DECCKM).
                1:
                break;
            case // Set: 132 column mode (. Reset: 80 column mode. ANSI name: DECCOLM.
                3:
                // We don't actually set/reset 132 cols, but we do want the side effects
                // (FIXME: Should only do this if the 95 DECSET bit (DECNCSM) is set, and if changing value?):
                // Sets the left, right, top and bottom scrolling margins to their default positions, which is important for
                // the "reset" utility to really reset the terminal:
                this.mLeftMargin = this.mTopMargin = 0;
                this.mBottomMargin = this.mRows;
                this.mRightMargin = this.mColumns;
                // "DECCOLM resets vertical split screen mode (DECLRMM) to unavailable":
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false);
                // "Erases all data in page memory":
                this.blockClear(0, 0, this.mColumns, this.mRows);
                this.setCursorRowCol(0, 0);
                break;
            case // DECSCLM-Scrolling Mode. Ignore.
                4:
                break;
            case // Reverse video. No action.
                5:
                break;
            case // Set: Origin Mode. Reset: Normal Cursor Mode. Ansi name: DECOM.
                6:
                if (setting)
                    this.setCursorPosition(0, 0);
                break;
            // Wrap-around bit, not specific action.
            case 7:
                // Auto-repeat Keys (DECARM). Do not implement.
            case 8:
                // X10 mouse reporting - outdated. Do not implement.
            case 9:
                // Control cursor blinking - ignore.
            case 12:
            case // Hide/show cursor - no action needed, renderer will check with shouldCursorBeVisible().
                25:
                break;
            // Allow 80 => 132 Mode, ignore.
            case 40:
                // TODO: Reverse wrap-around. Implement???
            case 45:
            case // Application keypad (DECNKM).
                66:
                break;
            case // Left and right margin mode (DECLRMM).
                69:
                if (!setting) {
                    this.mLeftMargin = 0;
                    this.mRightMargin = this.mColumns;
                }
                break;
            case 1000:
            case 1001:
            case 1002:
            case 1003:
            case 1004:
                // UTF-8 mouse mode, ignore.
            case 1005:
                // SGR Mouse Mode
            case 1006:
            case 1015:
            case // Interpret "meta" key, sets eighth bit.
                1034:
                break;
            case // Set: Save cursor as in DECSC. Reset: Restore cursor as in DECRC.
                1048:
                if (setting)
                    this.saveCursor();
                else
                    this.restoreCursor();
                break;
            case 47:
            case 1047:
            case 1049: {
                // Set: Save cursor as in DECSC and use Alternate Screen Buffer, clearing it first.
                // Reset: Use Normal Screen Buffer and restore cursor as in DECRC.
                final TerminalBuffer newScreen = setting ? this.mAltBuffer : this.mMainBuffer;
                if (newScreen != this.mScreen) {
                    final boolean resized = !(newScreen.mColumns == this.mColumns && newScreen.mScreenRows == this.mRows);
                    if (setting)
                        this.saveCursor();
                    this.mScreen = newScreen;
                    if (!setting) {
                        final int col = this.mSavedStateMain.mSavedCursorCol;
                        final int row = this.mSavedStateMain.mSavedCursorRow;
                        this.restoreCursor();
                        if (resized) {
                            // Restore cursor position _not_ clipped to current screen (let resizeScreen() handle that):
                            this.mCursorCol = col;
                            this.mCursorRow = row;
                        }
                    }
                    // Check if buffer size needs to be updated:
                    if (resized)
                        this.resizeScreen();
                    // Clear new screen if alt buffer:
                    if (newScreen == this.mAltBuffer)
                        newScreen.blockSet(0, 0, this.mColumns, this.mRows, ' ', this.getStyle());
                }
                break;
            }
            case 2004:
                // Bracketed paste mode - setting bit is enough.
                break;
            default:
                this.finishSequence();
                break;
        }
    }

    private void doCsiBiggerThan(final int b) {
        switch (b) {
            case // "${CSI}>c" or "${CSI}>c". Secondary Device Attributes (DA2).
                'c':
                // Originally this was used for the terminal to respond with "identification code, firmware version level,
                // and hardware options" (http://vt100.net/docs/vt510-rm/DA2), with the first "41" meaning the VT420
                // terminal type. This is not used anymore, but the second version level field has been changed by xterm
                // to mean it's release number ("patch numbers" listed at http://invisible-island.net/xterm/xterm.log.html),
                // and some applications use it as a feature check:
                // * tmux used to have a "xterm won't reach version 500 for a while so set that as the upper limit" check,
                // and then check "xterm_version > 270" if rectangular area operations such as DECCRA could be used.
                // * vim checks xterm version number >140 for "Request termcap/terminfo string" functionality >276 for SGR
                // mouse report.
                // The third number is a keyboard identifier not used nowadays.
                this.mSession.write("\033[>41;320;0c");
                break;
            case 'm':
                // https://bugs.launchpad.net/gnome-terminal/+bug/96676/comments/25
                // Depending on the first number parameter, this can set one of the xterm resources
                // modifyKeyboard, modifyCursorKeys, modifyFunctionKeys and modifyOtherKeys.
                // http://invisible-island.net/xterm/manpage/xterm.html#RESOURCES
                // * modifyKeyboard (parameter=1):
                // Normally xterm makes a special case regarding modifiers (shift, control, etc.) to handle special keyboard
                // layouts (legacy and vt220). This is done to provide compatible keyboards for DEC VT220 and related
                // terminals that implement user-defined keys (UDK).
                // The bits of the resource value selectively enable modification of the given category when these keyboards
                // are selected. The default is "0":
                // (0) The legacy/vt220 keyboards interpret only the Control-modifier when constructing numbered
                // function-keys. Other special keys are not modified.
                // (1) allows modification of the numeric keypad
                // (2) allows modification of the editing keypad
                // (4) allows modification of function-keys, overrides use of Shift-modifier for UDK.
                // (8) allows modification of other special keys
                // * modifyCursorKeys (parameter=2):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a cursor-key. The default is "2".
                // - Set it to -1 to disable it.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.
                // * modifyFunctionKeys (parameter=3):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a (numbered) function-
                // key. The default is "2". The resource values are similar to modifyCursorKeys:
                // Set it to -1 to permit the user to use shift- and control-modifiers to construct function-key strings
                // using the normal encoding scheme.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.
                // If modifyFunctionKeys is zero, xterm uses Control- and Shift-modifiers to allow the user to construct
                // numbered function-keys beyond the set provided by the keyboard:
                // (Control) adds the value given by the ctrlFKeys resource.
                // (Shift) adds twice the value given by the ctrlFKeys resource.
                // (Control/Shift) adds three times the value given by the ctrlFKeys resource.
                //
                // As a special case, legacy (when oldFunctionKeys is true) or vt220 (when sunKeyboard is true)
                // keyboards interpret only the Control-modifier when constructing numbered function-keys.
                // This is done to provide compatible keyboards for DEC VT220 and related terminals that
                // implement user-defined keys (UDK).
                // * modifyOtherKeys (parameter=4):
                // Like modifyCursorKeys, tells xterm to construct an escape sequence for other keys (such as "2") when
                // modified by Control-, Alt- or Meta-modifiers. This feature does not apply to function keys and
                // well-defined keys such as ESC or the control keys. The default is "0".
                // (0) disables this feature.
                // (1) enables this feature for keys except for those with well-known behavior, e.g., Tab, Backarrow and
                // some special control character cases, e.g., Control-Space to make a NUL.
                // (2) enables this feature for keys including the exceptions listed.
                break;
            default:
                this.parseArg(b);
                break;
        }
    }

    private void startEscapeSequence() {
        this.mEscapeState = TerminalEmulator.ESC;
        this.mArgIndex = 0;
        Arrays.fill(this.mArgs, -1);
    }

    private void doLinefeed() {
        final boolean belowScrollingRegion = this.mCursorRow >= this.mBottomMargin;
        int newCursorRow = this.mCursorRow + 1;
        if (belowScrollingRegion) {
            // Move down (but not scroll) as long as we are above the last row.
            if (this.mCursorRow != this.mRows - 1) {
                this.setCursorRow(newCursorRow);
            }
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
        this.mContinueSequence = true;
    }

    private void doEscPound(final int b) {
        // Esc # 8 - DEC screen alignment test - fill screen with E's.
        if ('8' == b) {
            this.mScreen.blockSet(0, 0, this.mColumns, this.mRows, 'E', this.getStyle());
        } else {
            this.finishSequence();
        }
    }

    /**
     * Encountering a character in the {@link #ESC} state.
     */
    private void doEsc(final int b) {
        switch (b) {
            case '#':
                this.continueSequence(TerminalEmulator.ESC_POUND);
                break;
            case '(':
                this.continueSequence(TerminalEmulator.ESC_SELECT_LEFT_PAREN);
                break;
            case ')':
                this.continueSequence(TerminalEmulator.ESC_SELECT_RIGHT_PAREN);
                break;
            case // Back index (http://www.vt100.net/docs/vt510-rm/DECBI). Move left, insert blank column if start.
                '6':
                if (this.mCursorCol > this.mLeftMargin) {
                    this.mCursorCol--;
                } else {
                    final int rows = this.mBottomMargin - this.mTopMargin;
                    this.mScreen.blockCopy(this.mLeftMargin, this.mTopMargin, this.mRightMargin - this.mLeftMargin - 1, rows, this.mLeftMargin + 1, this.mTopMargin);
                    this.mScreen.blockSet(this.mLeftMargin, this.mTopMargin, 1, rows, ' ', TextStyle.encode(this.mForeColor, this.mBackColor, 0));
                }
                break;
            case // DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC
                '7':
                this.saveCursor();
                break;
            case // DECRC restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC
                '8':
                this.restoreCursor();
                break;
            case // Forward Index (http://www.vt100.net/docs/vt510-rm/DECFI). Move right, insert blank column if end.
                '9':
                if (this.mCursorCol < this.mRightMargin - 1) {
                    this.mCursorCol++;
                } else {
                    final int rows = this.mBottomMargin - this.mTopMargin;
                    this.mScreen.blockCopy(this.mLeftMargin + 1, this.mTopMargin, this.mRightMargin - this.mLeftMargin - 1, rows, this.mLeftMargin, this.mTopMargin);
                    this.mScreen.blockSet(this.mRightMargin - 1, this.mTopMargin, 1, rows, ' ', TextStyle.encode(this.mForeColor, this.mBackColor, 0));
                }
                break;
            case // RIS - Reset to Initial State (http://vt100.net/docs/vt510-rm/RIS).
                'c':
                this.reset();
                this.mMainBuffer.clearTranscript();
                this.blockClear(0, 0, this.mColumns, this.mRows);
                this.setCursorPosition(0, 0);
                break;
            case // INDEX
                'D':
                this.doLinefeed();
                break;
            case // Next line (http://www.vt100.net/docs/vt510-rm/NEL).
                'E':
                this.setCursorCol(this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE) ? this.mLeftMargin : 0);
                this.doLinefeed();
                break;
            case // Cursor to lower-left corner of screen
                'F':
                this.setCursorRowCol(0, this.mBottomMargin - 1);
                break;
            case // Tab set
                'H':
                this.mTabStop[this.mCursorCol] = true;
                break;
            case // "${ESC}M" - reverse index (RI).
                'M':
                // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (this.mCursorRow <= this.mTopMargin) {
                    this.mScreen.blockCopy(this.mLeftMargin, this.mTopMargin, this.mRightMargin - this.mLeftMargin, this.mBottomMargin - (this.mTopMargin + 1), this.mLeftMargin, this.mTopMargin + 1);
                    this.blockClear(this.mLeftMargin, this.mTopMargin, this.mRightMargin - this.mLeftMargin);
                } else {
                    this.mCursorRow--;
                }
                break;
            // SS2, ignore.
            case 'N':
            case // SS3, ignore.
                '0':
                break;
            case // Device control string
                'P':
                this.mOSCOrDeviceControlArgs.setLength(0);
                this.ESC_P_escape = false;
                this.continueSequence(TerminalEmulator.ESC_P);
                break;
            case '[':
                this.continueSequence(TerminalEmulator.ESC_CSI);
                this.mIsCSIStart = true;
                this.mLastCSIArg = null;
                break;
            case // DECKPAM
                '=':
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD, true);
                break;
            case // OSC
                ']':
                this.mOSCOrDeviceControlArgs.setLength(0);
                this.continueSequence(TerminalEmulator.ESC_OSC);
                this.ESC_OSC_colon = -1;
                break;
            case // DECKPNM
                '>':
                this.setDecsetinternalBit(TerminalEmulator.DECSET_BIT_APPLICATION_KEYPAD, false);
                break;
            case // APC
                '_':
                this.mOSCOrDeviceControlArgs.setLength(0);
                this.continueSequence(TerminalEmulator.ESC_APC);
                break;
            default:
                this.finishSequence();
                break;
        }
    }

    /**
     * DECSC save cursor - <a href="http://www.vt100.net/docs/vt510-rm/DECSC">...</a> . See {@link #restoreCursor()}.
     */
    private void saveCursor() {
        final SavedScreenState state = (this.mScreen == this.mMainBuffer) ? this.mSavedStateMain : this.mSavedStateAlt;
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
     * DECRS restore cursor - <a href="http://www.vt100.net/docs/vt510-rm/DECRC">...</a>. See {@link #saveCursor()}.
     */
    private void restoreCursor() {
        final SavedScreenState state = (this.mScreen == this.mMainBuffer) ? this.mSavedStateMain : this.mSavedStateAlt;
        this.setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol);
        this.mEffect = state.mSavedEffect;
        this.mForeColor = state.mSavedForeColor;
        this.mBackColor = state.mSavedBackColor;
        final int mask = (TerminalEmulator.DECSET_BIT_AUTOWRAP | TerminalEmulator.DECSET_BIT_ORIGIN_MODE);
        this.mCurrentDecSetFlags = (this.mCurrentDecSetFlags & ~mask) | (state.mSavedDecFlags & mask);
        this.mUseLineDrawingG0 = state.mUseLineDrawingG0;
        this.mUseLineDrawingG1 = state.mUseLineDrawingG1;
        this.mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0;
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". {@link #ESC_CSI}.
     */
    private void doCsi(final int b) {
        switch (b) {
            case '!':
                this.continueSequence(TerminalEmulator.ESC_CSI_EXCLAMATION);
                break;
            case '"':
                this.continueSequence(TerminalEmulator.ESC_CSI_DOUBLE_QUOTE);
                break;
            case '\'':
                this.continueSequence(TerminalEmulator.ESC_CSI_SINGLE_QUOTE);
                break;
            case '$':
                this.continueSequence(TerminalEmulator.ESC_CSI_DOLLAR);
                break;
            case '*':
                this.continueSequence(TerminalEmulator.ESC_CSI_ARGS_ASTERIX);
                break;
            case '@': {
                // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                this.mAboutToAutoWrap = false;
                final int columnsAfterCursor = this.mColumns - this.mCursorCol;
                final int spacesToInsert = Math.min(this.getArg0(1), columnsAfterCursor);
                final int charsToMove = columnsAfterCursor - spacesToInsert;
                this.mScreen.blockCopy(this.mCursorCol, this.mCursorRow, charsToMove, 1, this.mCursorCol + spacesToInsert, this.mCursorRow);
                this.blockClear(this.mCursorCol, this.mCursorRow, spacesToInsert);
            }
            break;
            case // "CSI${n}A" - Cursor up (CUU) ${n} rows.
                'A':
                this.setCursorRow(Math.max(0, this.mCursorRow - this.getArg0(1)));
                break;
            case // "CSI${n}B" - Cursor down (CUD) ${n} rows.
                'B':
                this.setCursorRow(Math.min(this.mRows - 1, this.mCursorRow + this.getArg0(1)));
                break;
            // "CSI${n}C" - Cursor forward (CUF).
            case 'C':
            case // "CSI${n}a" - Horizontal position relative (HPR). From ISO-6428/ECMA-48.
                'a':
                this.setCursorCol(Math.min(this.mRightMargin - 1, this.mCursorCol + this.getArg0(1)));
                break;
            case // "CSI${n}D" - Cursor backward (CUB) ${n} columns.
                'D':
                this.setCursorCol(Math.max(this.mLeftMargin, this.mCursorCol - this.getArg0(1)));
                break;
            case // "CSI{n}E - Cursor Next Line (CNL). From ISO-6428/ECMA-48.
                'E':
                this.setCursorPosition(0, this.mCursorRow + this.getArg0(1));
                break;
            case // "CSI{n}F - Cursor Previous Line (CPL). From ISO-6428/ECMA-48.
                'F':
                this.setCursorPosition(0, this.mCursorRow - this.getArg0(1));
                break;
            case // "CSI${n}G" - Cursor horizontal absolute (CHA) to column ${n}.
                'G':
                this.setCursorCol(Math.min(Math.max(1, this.getArg0(1)), this.mColumns) - 1);
                break;
            // "${CSI}${ROW};${COLUMN}H" - Cursor position (CUP).
            case 'H':
            case // "${CSI}${ROW};${COLUMN}f" - Horizontal and Vertical Position (HVP).
                'f':
                this.setCursorPosition(this.getArg1(1) - 1, this.getArg0(1) - 1);
                break;
            case // Cursor Horizontal Forward Tabulation (CHT). Move the active position n tabs forward.
                'I':
                this.setCursorCol(this.nextTabStop(this.getArg0(1)));
                break;
            case // "${CSI}${0,1,2,3}J" - Erase in Display (ED)
                'J':
                // ED ignores the scrolling margins.
                switch (this.getArg0(0)) {
                    case // Erase from the active position to the end of the screen, inclusive (default).
                        0:
                        this.blockClear(this.mCursorCol, this.mCursorRow, this.mColumns - this.mCursorCol);
                        this.blockClear(0, this.mCursorRow + 1, this.mColumns, this.mRows - (this.mCursorRow + 1));
                        break;
                    case // Erase from start of the screen to the active position, inclusive.
                        1:
                        this.blockClear(0, 0, this.mColumns, this.mCursorRow);
                        this.blockClear(0, this.mCursorRow, this.mCursorCol + 1);
                        break;
                    case // Erase all of the display - all lines are erased, changed to single-width, and the cursor does not
                        2:
                        // move..
                        this.blockClear(0, 0, this.mColumns, this.mRows);
                        break;
                    case // Delete all lines saved in the scrollback buffer (xterm etc)
                        3:
                        this.mMainBuffer.clearTranscript();
                        break;
                    default:
                        this.finishSequence();
                        return;
                }
                this.mAboutToAutoWrap = false;
                break;
            case // "CSI{n}K" - Erase in line (EL).
                'K':
                switch (this.getArg0(0)) {
                    case // Erase from the cursor to the end of the line, inclusive (default)
                        0:
                        this.blockClear(this.mCursorCol, this.mCursorRow, this.mColumns - this.mCursorCol);
                        break;
                    case // Erase from the start of the screen to the cursor, inclusive.
                        1:
                        this.blockClear(0, this.mCursorRow, this.mCursorCol + 1);
                        break;
                    case // Erase all of the line.
                        2:
                        this.blockClear(0, this.mCursorRow, this.mColumns);
                        break;
                    default:
                        this.finishSequence();
                        return;
                }
                this.mAboutToAutoWrap = false;
                break;
            case // "${CSI}{N}L" - insert ${N} lines (IL).
                'L': {
                final int linesAfterCursor = this.mBottomMargin - this.mCursorRow;
                final int linesToInsert = Math.min(this.getArg0(1), linesAfterCursor);
                final int linesToMove = linesAfterCursor - linesToInsert;
                this.mScreen.blockCopy(0, this.mCursorRow, this.mColumns, linesToMove, 0, this.mCursorRow + linesToInsert);
                this.blockClear(0, this.mCursorRow, this.mColumns, linesToInsert);
            }
            break;
            case // "${CSI}${N}M" - delete N lines (DL).
                'M': {
                this.mAboutToAutoWrap = false;
                final int linesAfterCursor = this.mBottomMargin - this.mCursorRow;
                final int linesToDelete = Math.min(this.getArg0(1), linesAfterCursor);
                final int linesToMove = linesAfterCursor - linesToDelete;
                this.mScreen.blockCopy(0, this.mCursorRow + linesToDelete, this.mColumns, linesToMove, 0, this.mCursorRow);
                this.blockClear(0, this.mCursorRow + linesToMove, this.mColumns, linesToDelete);
            }
            break;
            case // "${CSI}{N}P" - delete ${N} characters (DCH).
                'P': {
                // http://www.vt100.net/docs/vt510-rm/DCH: "If ${N} is greater than the number of characters between the
                // cursor and the right margin, then DCH only deletes the remaining characters.
                // As characters are deleted, the remaining characters between the cursor and right margin move to the left.
                // Character attributes move with the characters. The terminal adds blank spaces with no visual character
                // attributes at the right margin. DCH has no effect outside the scrolling margins."
                this.mAboutToAutoWrap = false;
                final int cellsAfterCursor = this.mColumns - this.mCursorCol;
                final int cellsToDelete = Math.min(this.getArg0(1), cellsAfterCursor);
                final int cellsToMove = cellsAfterCursor - cellsToDelete;
                this.mScreen.blockCopy(this.mCursorCol + cellsToDelete, this.mCursorRow, cellsToMove, 1, this.mCursorCol, this.mCursorRow);
                this.blockClear(this.mCursorCol + cellsToMove, this.mCursorRow, cellsToDelete);
            }
            break;
            case 'S': {
                // "${CSI}${N}S" - scroll up ${N} lines (default = 1) (SU).
                int linesToScroll = this.getArg0(1);
                for (int i = 0; i < linesToScroll; i++) this.scrollDownOneLine();
                break;
            }
            case 'T':
                if (0 == mArgIndex) {
                    // "${CSI}${N}T" - Scroll down N lines (default = 1) (SD).
                    // http://vt100.net/docs/vt510-rm/SD: "N is the number of lines to move the user window up in page
                    // memory. N new lines appear at the top of the display. N old lines disappear at the bottom of the
                    // display. You cannot pan past the top margin of the current page".
                    int linesToScrollArg = this.getArg0(1);
                    int linesBetweenTopAndBottomMargins = this.mBottomMargin - this.mTopMargin;
                    int linesToScroll = Math.min(linesBetweenTopAndBottomMargins, linesToScrollArg);
                    this.mScreen.blockCopy(this.mLeftMargin, this.mTopMargin, this.mRightMargin - this.mLeftMargin, linesBetweenTopAndBottomMargins - linesToScroll, this.mLeftMargin, this.mTopMargin + linesToScroll);
                    this.blockClear(this.mLeftMargin, this.mTopMargin, this.mRightMargin - this.mLeftMargin, linesToScroll);
                } else {
                    // "${CSI}${func};${startx};${starty};${firstrow};${lastrow}T" - initiate highlight mouse tracking.
                    this.finishSequence();
                }
                break;
            case // "${CSI}${N}X" - Erase ${N:=1} character(s) (ECH). FIXME: Clears character attributes?
                'X':
                this.mAboutToAutoWrap = false;
                this.mScreen.blockSet(this.mCursorCol, this.mCursorRow, Math.min(this.getArg0(1), this.mColumns - this.mCursorCol), 1, ' ', this.getStyle());
                break;
            case // Cursor Backward Tabulation (CBT). Move the active position n tabs backward.
                'Z':
                int numberOfTabs = this.getArg0(1);
                int newCol = this.mLeftMargin;
                for (int i = this.mCursorCol - 1; 0 <= i; i--)
                    if (this.mTabStop[i]) {
                        --numberOfTabs;
                        if (0 == numberOfTabs) {
                            newCol = Math.max(i, this.mLeftMargin);
                            break;
                        }
                    }
                this.mCursorCol = newCol;
                break;
            case // Esc [ ? -- start of a private mode set
                '?':
                this.continueSequence(TerminalEmulator.ESC_CSI_QUESTIONMARK);
                break;
            case // "Esc [ >" --
                '>':
                this.continueSequence(TerminalEmulator.ESC_CSI_BIGGERTHAN);
                break;
            case // Horizontal position absolute (HPA - http://www.vt100.net/docs/vt510-rm/HPA).
                '`':
                this.setCursorColRespectingOriginMode(this.getArg0(1) - 1);
                break;
            case // Repeat the preceding graphic character Ps times (REP).
                'b':
                if (-1 == mLastEmittedCodePoint)
                    break;
                int numRepeat = this.getArg0(1);
                for (int i = 0; i < numRepeat; i++) this.emitCodePoint(this.mLastEmittedCodePoint);
                break;
            case // Primary Device Attributes (http://www.vt100.net/docs/vt510-rm/DA1) if argument is missing or zero.
                'c':
                // The important part that may still be used by some (tmux stores this value but does not currently use it)
                // is the first response parameter identifying the terminal service class, where we send 64 for "vt420".
                // This is followed by a list of attributes which is probably unused by applications. Send like xterm.
                if (0 == getArg0(0))
                    this.mSession.write("\033[?64;1;2;4;6;9;15;18;21;22c");
                break;
            case // ESC [ Pn d - Vert Position Absolute
                'd':
                this.setCursorRow(Math.min(Math.max(1, this.getArg0(1)), this.mRows) - 1);
                break;
            case // Vertical Position Relative (VPR). From ISO-6429 (ECMA-48).
                'e':
                this.setCursorPosition(this.mCursorCol, this.mCursorRow + this.getArg0(1));
                break;
            // case 'f': "${CSI}${ROW};${COLUMN}f" - Horizontal and Vertical Position (HVP). Grouped with case 'H'.
            case // Clear tab stop
                'g':
                switch (this.getArg0(0)) {
                    case 0:
                        this.mTabStop[this.mCursorCol] = false;
                        break;
                    case 3:
                        for (int i = 0; i < this.mColumns; i++) {
                            this.mTabStop[i] = false;
                        }
                        break;
                    default:
                        // Specified to have no effect.
                        break;
                }
                break;
            case // Set Mode
                'h':
                this.doSetMode(true);
                break;
            case // Reset Mode
                'l':
                this.doSetMode(false);
                break;
            case // Esc [ Pn m - character attributes. (can have up to 16 numerical arguments)
                'm':
                this.selectGraphicRendition();
                break;
            case // Esc [ Pn n - ECMA-48 Status Report Commands
                'n':
                // sendDeviceAttributes()
                switch (this.getArg0(0)) {
                    case // Device status report (DSR):
                        5:
                        // Answer is ESC [ 0 n (Terminal OK).
                        final byte[] dsr = {(byte) 27, (byte) '[', (byte) '0', (byte) 'n'};
                        this.mSession.write(dsr, 0, dsr.length);
                        break;
                    case // Cursor position report (CPR):
                        6:
                        // Answer is ESC [ y ; x R, where x,y is
                        // the cursor location.
                        this.mSession.write(String.format(Locale.US, "\033[%d;%dR", this.mCursorRow + 1, this.mCursorCol + 1));
                        break;
                    default:
                        break;
                }
                break;
            case // "CSI${top};${bottom}r" - set top and bottom Margins (DECSTBM).
                'r': {
                // https://vt100.net/docs/vt510-rm/DECSTBM.html
                // The top margin defaults to 1, the bottom margin defaults to mRows.
                // The escape sequence numbers top 1..23, but we number top 0..22.
                // The escape sequence numbers bottom 2..24, and so do we (because we use a zero based numbering
                // scheme, but we store the first line below the bottom-most scrolling line.
                // As a result, we adjust the top line by -1, but we leave the bottom line alone.
                // Also require that top + 2 <= bottom.
                this.mTopMargin = Math.max(0, Math.min(this.getArg0(1) - 1, this.mRows - 2));
                this.mBottomMargin = Math.max(this.mTopMargin + 2, Math.min(this.getArg1(this.mRows), this.mRows));
                // DECSTBM moves the cursor to column 1, line 1 of the page respecting origin mode.
                this.setCursorPosition(0, 0);
            }
            break;
            case 's':
                if (this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                    // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                    this.mLeftMargin = Math.min(this.getArg0(1) - 1, this.mColumns - 2);
                    this.mRightMargin = Math.max(this.mLeftMargin + 1, Math.min(this.getArg1(this.mColumns), this.mColumns));
                    // DECSLRM moves the cursor to column 1, line 1 of the page.
                    this.setCursorPosition(0, 0);
                } else {
                    // Save cursor (ANSI.SYS), available only when DECLRMM is disabled.
                    this.saveCursor();
                }
                break;
            case // Window manipulation (from dtterm, as well as extensions)
                't':
                switch (this.getArg0(0)) {
                    case // Report xterm window state. If the xterm window is open (non-iconified), it returns CSI 1 t .
                        11:
                        this.mSession.write("\033[1t");
                        break;
                    case // Report xterm window position. Result is CSI 3 ; x ; y t
                        13:
                        this.mSession.write("\033[3;0;0t");
                        break;
                    case // Report xterm window in pixels. Result is CSI 4 ; height ; width t
                        14:
                        this.mSession.write(String.format(Locale.US, "\033[4;%d;%dt", this.mRows * this.cellH, this.mColumns * this.cellW));
                        break;
                    case // Report xterm window in pixels. Result is CSI 4 ; height ; width t
                        16:
                        this.mSession.write(String.format(Locale.US, "\033[6;%d;%dt", this.cellH, this.cellW));
                        break;
                    case // Report the size of the text area in characters. Result is CSI 8 ; height ; width t
                        18:
                        this.mSession.write(String.format(Locale.US, "\033[8;%d;%dt", this.mRows, this.mColumns));
                        break;
                    case // Report the size of the screen in characters. Result is CSI 9 ; height ; width t
                        19:
                        // We report the same size as the view, since it's the view really isn't resizable from the shell.
                        this.mSession.write(String.format(Locale.US, "\033[9;%d;%dt", this.mRows, this.mColumns));
                        break;
                    case // Report xterm windows icon label. Result is OSC L label ST. Disabled due to security concerns:
                        20:
                        this.mSession.write("\033]LIconLabel\033\\");
                        break;
                    case // Report xterm windows title. Result is OSC l label ST. Disabled due to security concerns:
                        21:
                        this.mSession.write("\033]l\033\\");
                        break;
                    case 22:
                        // 22;0 -> Save xterm icon and window title on stack.
                        // 22;1 -> Save xterm icon title on stack.
                        // 22;2 -> Save xterm window title on stack.
                        this.mTitleStack.push(this.mTitle);
                        if (20 < mTitleStack.size()) {
                            // Limit size
                            this.mTitleStack.remove(0);
                        }
                        break;
                    case // Like 22 above but restore from stack.
                        23:
                        if (!this.mTitleStack.isEmpty())
                            this.mTitle = this.mTitleStack.pop();
                        break;
                    default:
                        // Ignore window manipulation.
                        break;
                }
                break;
            case // Restore cursor (ANSI.SYS).
                'u':
                this.restoreCursor();
                break;
            case ' ':
                this.continueSequence(TerminalEmulator.ESC_CSI_ARGS_SPACE);
                break;
            default:
                this.parseArg(b);
                break;
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see <a href="http://en.wikipedia.org/wiki/ANSI_escape_code#graphics">...</a>.
     */
    private void selectGraphicRendition() {
        if (this.mArgIndex >= this.mArgs.length)
            this.mArgIndex = this.mArgs.length - 1;
        for (int i = 0; i <= this.mArgIndex; i++) {
            int code = this.mArgs[i];
            if (0 > code) {
                if (0 < mArgIndex) {
                    continue;
                } else {
                    code = 0;
                }
            }
            if (0 == code) {
                // reset
                this.mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
                this.mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
                this.mEffect = 0;
            } else if (1 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BOLD;
            } else if (2 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_DIM;
            } else if (3 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
            } else if (4 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
            } else if (5 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BLINK;
            } else if (7 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
            } else if (8 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
            } else if (9 == code) {
                this.mEffect |= TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
            } else if (22 == code) {
                // Normal color or intensity, neither bright, bold nor faint.
                this.mEffect &= ~(TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_DIM);
            } else if (23 == code) {
                // not italic, but rarely used as such; clears standout with TERM=screen
                this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
            } else if (24 == code) {
                // underline: none
                this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
            } else if (25 == code) {
                // blink: none
                this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_BLINK;
            } else if (27 == code) {
                // image: positive
                this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
            } else if (28 == code) {
                this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
            } else if (29 == code) {
                this.mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
            } else if (30 <= code && 37 >= code) {
                this.mForeColor = code - 30;
            } else if (38 == code || 48 == code) {
                // Extended set foreground(38)/background (48) color.
                // This is followed by either "2;$R;$G;$B" to set a 24-bit color or
                // "5;$INDEX" to set an indexed color.
                if (i + 2 > this.mArgIndex)
                    continue;
                final int firstArg = this.mArgs[i + 1];
                if (2 == firstArg) {
                    if (i + 4 <= this.mArgIndex) {
                        final int red = this.mArgs[i + 2];
                        int green = mArgs[i + 3];
                        final int blue = this.mArgs[i + 4];
                        if (0 > red || 0 > green || 0 > blue || 255 < red || 255 < green || 255 < blue) {
                            this.finishSequence();
                        } else {
                            final int argbColor = 0xff000000 | (red << 16) | (green << 8) | blue;
                            if (38 == code) {
                                this.mForeColor = argbColor;
                            } else {
                                this.mBackColor = argbColor;
                            }
                        }
                        // "2;P_r;P_g;P_r"
                        i += 4;
                    }
                } else if (5 == firstArg) {
                    final int color = this.mArgs[i + 2];
                    // "5;P_s"
                    i += 2;
                    if (0 <= color && TextStyle.NUM_INDEXED_COLORS > color) {
                        if (38 == code) {
                            this.mForeColor = color;
                        } else {
                            this.mBackColor = color;
                        }
                    }
                } else {
                    this.finishSequence();
                }
            } else if (39 == code) {
                // Set default foreground color.
                this.mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
            } else if (40 <= code && 47 >= code) {
                // Set background color.
                this.mBackColor = code - 40;
            } else if (49 == code) {
                // Set default background color.
                this.mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
            } else if (90 <= code && 97 >= code) {
                // Bright foreground colors (aixterm codes).
                this.mForeColor = code - 90 + 8;
            } else if (100 <= code && 107 >= code) {
                // Bright background color (aixterm codes).
                this.mBackColor = code - 100 + 8;
            }
        }
    }

    private void doApc(final int b) {
        switch (b) {
            case // Bell.
                7:
                break;
            case // Escape.
                27:
                this.continueSequence(TerminalEmulator.ESC_APC_ESC);
                break;
            default:
                this.collectOSCArgs(b);
                this.continueSequence(TerminalEmulator.ESC_OSC);
        }
    }

    private void doApcEsc(final int b) {
        if ('\\' != b) {// The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            this.collectOSCArgs(27);
            this.collectOSCArgs(b);
            this.continueSequence(TerminalEmulator.ESC_APC);
        }
    }

    private void doOsc(final int b) {
        switch (b) {
            case // Bell.
                7:
                this.doOscSetTextParameters("\007");
                break;
            case // Escape.
                27:
                this.continueSequence(TerminalEmulator.ESC_OSC_ESC);
                break;
            default:
                this.collectOSCArgs(b);
                if (-1 == ESC_OSC_colon && ':' == b) {
                    // Collect base64 data for OSC 1337
                    this.ESC_OSC_colon = this.mOSCOrDeviceControlArgs.length();
                    this.ESC_OSC_data = new ArrayList<>(65536);
                } else if (0 <= ESC_OSC_colon && 4 == mOSCOrDeviceControlArgs.length() - ESC_OSC_colon) {
                    try {
                        final byte[] decoded = Base64.decode(this.mOSCOrDeviceControlArgs.substring(this.ESC_OSC_colon), 0);
                        for (final byte value : decoded) {
                            this.ESC_OSC_data.add(value);
                        }
                    } catch (final Exception e) {
                        // Ignore non-Base64 data.
                    }
                    this.mOSCOrDeviceControlArgs.setLength(this.ESC_OSC_colon);
                }
                break;
        }
    }

    private void doOscEsc(final int b) {
        if ('\\' == b) {
            this.doOscSetTextParameters("\033\\");
        } else {// The ESC character was not followed by a \, so insert the ESC and
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
        int value = -1;
        final int osc_colon = this.ESC_OSC_colon;
        this.ESC_OSC_colon = -1;
        String textParameter = "";
        // Extract initial $value from initial "$value;..." string.
        for (int mOSCArgTokenizerIndex = 0; mOSCArgTokenizerIndex < this.mOSCOrDeviceControlArgs.length(); mOSCArgTokenizerIndex++) {
            final char b = this.mOSCOrDeviceControlArgs.charAt(mOSCArgTokenizerIndex);
            if (';' == b) {
                textParameter = this.mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1);
                break;
            } else if ('0' <= b && '9' >= b) {
                value = ((0 > value) ? 0 : value * 10) + (b - '0');
            } else {
                this.finishSequence();
                return;
            }
        }
        switch (value) {
            // Change icon name and window title to T.
            case 0:
                // Change icon name to T.
            case 1:
            case // Change window title to T.
                2:
                this.mTitle = textParameter;
                break;
            case 4:
                // P s = 4 ; c ; spec → Change Color Number c to the color specified by spec. This can be a name or RGB
                // specification as per XParseColor. Any number of c name pairs may be given. The color numbers correspond
                // to the ANSI colors 0-7, their bright versions 8-15, and if supported, the remainder of the 88-color or
                // 256-color table.
                // If a "?" is given rather than a name or RGB specification, xterm replies with a control sequence of the
                // same form which can be used to set the corresponding color. Because more than one pair of color number
                // and specification can be given in one control sequence, xterm can make more than one reply.
                int colorIndex = -1;
                int parsingPairStart = -1;
                for (int i = 0; ; i++) {
                    final boolean endOfInput = i == textParameter.length();
                    final char b = endOfInput ? ';' : textParameter.charAt(i);
                    if (';' == b) {
                        if (0 > parsingPairStart) {
                            parsingPairStart = i + 1;
                        } else {
                            if (0 > colorIndex || 255 < colorIndex) {
                                this.finishSequence();
                                return;
                            } else {
                                this.mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i));
                                colorIndex = -1;
                                parsingPairStart = -1;
                            }
                        }
                    } else if (0 > parsingPairStart && ('0' <= b && '9' >= b)) {
                        colorIndex = ((0 > colorIndex) ? 0 : colorIndex * 10) + (b - '0');
                    } else {
                        this.finishSequence();
                        return;
                    }
                    if (endOfInput)
                        break;
                }
                break;
            // Set foreground color.
            case 10:
                // Set background color.
            case 11:
            case // Set cursor color.
                12:
                int specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10);
                int lastSemiIndex = 0;
                for (int charIndex = 0; ; charIndex++) {
                    final boolean endOfInput = charIndex == textParameter.length();
                    if (endOfInput || ';' == textParameter.charAt(charIndex)) {
                        try {
                            final String colorSpec = textParameter.substring(lastSemiIndex, charIndex);
                            if ("?".equals(colorSpec)) {
                                // Report current color in the same format xterm and gnome-terminal does.
                                final int rgb = this.mColors.mCurrentColors[specialIndex];
                                final int r = (65535 * ((rgb & 0x00FF0000) >> 16)) / 255;
                                final int g = (65535 * ((rgb & 0x0000FF00) >> 8)) / 255;
                                final int b = (65535 * ((rgb & 0x000000FF))) / 255;
                                this.mSession.write("\033]" + value + ";rgb:" + String.format(Locale.US, "%04x", r) + "/" + String.format(Locale.US, "%04x", g) + "/" + String.format(Locale.US, "%04x", b) + bellOrStringTerminator);
                            } else {
                                this.mColors.tryParseColor(specialIndex, colorSpec);
                            }
                            specialIndex++;
                            if (endOfInput || (TextStyle.COLOR_INDEX_CURSOR < specialIndex) || ++charIndex >= textParameter.length())
                                break;
                            lastSemiIndex = charIndex;
                        } catch (final NumberFormatException e) {
                            // Ignore.
                        }
                    }
                }
                break;
            case // Manipulate Selection Data. Skip the optional first selection parameter(s).
                52:
                final int startIndex = textParameter.indexOf(';') + 1;
                try {
                    final String clipboardText = new String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8);
                    this.mSession.onCopyTextToClipboard(clipboardText);
                } catch (final Exception ignored) {
                }
                break;
            case 104:
                // "104;$c" → Reset Color Number $c. It is reset to the color specified by the corresponding X
                // resource. Any number of c parameters may be given. These parameters correspond to the ANSI colors 0-7,
                // their bright versions 8-15, and if supported, the remainder of the 88-color or 256-color table. If no
                // parameters are given, the entire table will be reset.
                if (textParameter.isEmpty()) {
                    this.mColors.reset();
                } else {
                    int lastIndex = 0;
                    for (int charIndex = 0; ; charIndex++) {
                        final boolean endOfInput = charIndex == textParameter.length();
                        if (endOfInput || ';' == textParameter.charAt(charIndex)) {
                            try {
                                final int colorToReset = Integer.parseInt(textParameter.substring(lastIndex, charIndex));
                                this.mColors.reset(colorToReset);
                                if (endOfInput)
                                    break;
                                charIndex++;
                                lastIndex = charIndex;
                            } catch (final NumberFormatException e) {
                                // Ignore.
                            }
                        }
                    }
                }
                break;
            // Reset foreground color.
            case 110:
                // Reset background color.
            case 111:
            case // Reset cursor color.
                112:
                this.mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110));
                break;
            case // Reset highlight color.
                119:
                break;
            case // iTerm extemsions
                1337:
                if (textParameter.startsWith("File=")) {
                    int pos = 5;
                    boolean inline = false;
                    boolean aspect = true;
                    int width = -1;
                    int height = -1;
                    while (pos < textParameter.length()) {
                        final int eqpos = textParameter.indexOf('=', pos);
                        if (-1 == eqpos) {
                            break;
                        }
                        int semicolonpos = textParameter.indexOf(';', eqpos);
                        if (-1 == semicolonpos) {
                            semicolonpos = textParameter.length() - 1;
                        }
                        final String k = textParameter.substring(pos, eqpos);
                        final String v = textParameter.substring(eqpos + 1, semicolonpos);
                        pos = semicolonpos + 1;
                        if ("inline".equalsIgnoreCase(k)) {
                            inline = "1".equals(v);
                        }
                        if ("preserveAspectRatio".equalsIgnoreCase(k)) {
                            aspect = !"0".equals(v);
                        }
                        final boolean percent = !v.isEmpty() && '%' == v.charAt(v.length() - 1);
                        if ("width".equalsIgnoreCase(k)) {
                            double factor = this.cellW;
                            // int div = 1;
                            int e = v.length();
                            if (v.endsWith("px")) {
                                factor = 1;
                                e -= 2;
                            } else if (percent) {
                                factor = 0.01 * this.cellW * this.mColumns;
                                e -= 1;
                            }
                            try {
                                width = (int) (factor * Integer.parseInt(v.substring(0, e)));
                            } catch (final Exception ignored) {
                            }
                        }
                        if ("height".equalsIgnoreCase(k)) {
                            double factor = this.cellH;
                            //int div = 1;
                            int e = v.length();
                            if (v.endsWith("px")) {
                                factor = 1;
                                e -= 2;
                            } else if (percent) {
                                factor = 0.01 * this.cellH * this.mRows;
                                e -= 1;
                            }
                            try {
                                height = (int) (factor * Integer.parseInt(v.substring(0, e)));
                            } catch (final Exception ignored) {
                            }
                        }
                    }
                    if (!inline) {
                        this.finishSequence();
                        return;
                    }
                    if (0 <= osc_colon && this.mOSCOrDeviceControlArgs.length() > osc_colon) {
                        while (4 > mOSCOrDeviceControlArgs.length() - osc_colon) {
                            this.mOSCOrDeviceControlArgs.append('=');
                        }
                        try {
                            final byte[] decoded = Base64.decode(this.mOSCOrDeviceControlArgs.substring(osc_colon), 0);
                            for (final byte b : decoded) {
                                this.ESC_OSC_data.add(b);
                            }
                        } catch (final Exception e) {
                            // Ignore non-Base64 data.
                        }
                        this.mOSCOrDeviceControlArgs.setLength(osc_colon);
                    }
                    if (0 <= osc_colon) {
                        final byte[] result = new byte[this.ESC_OSC_data.size()];
                        for (int i = 0; i < this.ESC_OSC_data.size(); i++) {
                            result[i] = this.ESC_OSC_data.get(i);
                        }
                        final int[] res = this.mScreen.addImage(result, this.mCursorRow, this.mCursorCol, this.cellW, this.cellH, width, height, aspect);
                        int col = res[1] + this.mCursorCol;
                        if (col < this.mColumns - 1) {
                            res[0] -= 1;
                        } else {
                            col = 0;
                        }
                        for (; 0 < res[0]; res[0]--) {
                            this.doLinefeed();
                        }
                        this.mCursorCol = col;
                        this.ESC_OSC_data.clear();
                    }
                } else if (textParameter.startsWith("ReportCellSize")) {
                    this.mSession.write(String.format(Locale.US, "\0331337;ReportCellSize=%d;%d\007", this.cellH, this.cellW));
                }
                break;
            default:
                this.finishSequence();
                break;
        }
        this.finishSequence();
    }

    private void blockClear(final int sx, final int sy, final int w) {
        this.blockClear(sx, sy, w, 1);
    }

    private void blockClear(final int sx, final int sy, final int w, final int h) {
        this.mScreen.blockSet(sx, sy, w, h, ' ', this.getStyle());
    }

    private long getStyle() {
        return TextStyle.encode(this.mForeColor, this.mBackColor, this.mEffect);
    }

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private void doSetMode(final boolean newValue) {
        final int modeBit = this.getArg0(0);
        switch (modeBit) {
            case // Set="Insert Mode". Reset="Replace Mode". (IRM).
                4:
                this.mInsertMode = newValue;
                break;
            // http://www.vt100.net/docs/vt510-rm/LNM
            case 34:
                // Normal cursor visibility - when using TERM=screen, see
                // http://www.gnu.org/software/screen/manual/html_node/Control-Sequences.html
                break;
            default:
                this.finishSequence();
                break;
        }
    }

    /**
     * NOTE: The parameters of this function respect the {@link #DECSET_BIT_ORIGIN_MODE}. Use
     * {@link #setCursorRowCol(int, int)} for absolute pos.
     */
    private void setCursorPosition(final int x, final int y) {
        final boolean originMode = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_ORIGIN_MODE);
        final int effectiveTopMargin = originMode ? this.mTopMargin : 0;
        final int effectiveBottomMargin = originMode ? this.mBottomMargin : this.mRows;
        final int effectiveLeftMargin = originMode ? this.mLeftMargin : 0;
        final int effectiveRightMargin = originMode ? this.mRightMargin : this.mColumns;
        final int newRow = Math.max(effectiveTopMargin, Math.min(effectiveTopMargin + y, effectiveBottomMargin - 1));
        final int newCol = Math.max(effectiveLeftMargin, Math.min(effectiveLeftMargin + x, effectiveRightMargin - 1));
        this.setCursorRowCol(newRow, newCol);
    }

    private void scrollDownOneLine() {
        this.mScrollCounter++;
        if (0 != mLeftMargin || this.mRightMargin != this.mColumns) {
            // Horizontal margin: Do not put anything into scroll history, just non-margin part of screen up.
            this.mScreen.blockCopy(this.mLeftMargin, this.mTopMargin + 1, this.mRightMargin - this.mLeftMargin, this.mBottomMargin - this.mTopMargin - 1, this.mLeftMargin, this.mTopMargin);
            // .. and blank bottom row between margins:
            this.mScreen.blockSet(this.mLeftMargin, this.mBottomMargin - 1, this.mRightMargin - this.mLeftMargin, 1, ' ', this.mEffect);
        } else {
            this.mScreen.scrollDownOneLine(this.mTopMargin, this.mBottomMargin, this.getStyle());
        }
    }

    /**
     * Process the next ASCII character of a parameter.
     * <p>
     * Parameter characters modify the action or interpretation of the sequence. You can use up to
     * 16 parameters per sequence. You must use the ; character to separate parameters.
     * All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     * <p><a href="
     * ">* https://vt100.net/docs/vt510-rm/chapter4.htm</a>l#S4.3.3
     */
    private void parseArg(final int inputByte) {
        final int[] bytes = this.getInts(inputByte);
        this.mIsCSIStart = false;
        for (final int b : bytes) {
            if ('0' <= b && '9' >= b) {
                if (this.mArgIndex < this.mArgs.length) {
                    final int oldValue = this.mArgs[this.mArgIndex];
                    final int thisDigit = b - '0';
                    int value;
                    if (0 <= oldValue) {
                        value = oldValue * 10 + thisDigit;
                    } else {
                        value = thisDigit;
                    }
                    if (9999 < value)
                        value = 9999;
                    this.mArgs[this.mArgIndex] = value;
                }
                this.continueSequence(this.mEscapeState);
            } else if (';' == b) {
                if (this.mArgIndex < this.mArgs.length) {
                    this.mArgIndex++;
                }
                this.continueSequence(this.mEscapeState);
            } else {
                this.finishSequence();
            }
            this.mLastCSIArg = b;
        }
    }

    private int[] getInts(final int inputByte) {
        int[] bytes = {inputByte};
        // Only doing this for ESC_CSI and not for other ESC_CSI_* since they seem to be using their
        // own defaults with getArg*() calls, but there may be missed cases
        if (TerminalEmulator.ESC_CSI == mEscapeState) {
            if (// If sequence starts with a ; character, like \033[;m
                (this.mIsCSIStart && ';' == inputByte) || (!this.mIsCSIStart && null != mLastCSIArg && ';' == mLastCSIArg && ';' == inputByte)) {
                // If sequence contains sequential ; characters, like \033[;;m
                // Assume 0 was passed
                bytes = new int[]{'0', ';'};
            }
        }
        return bytes;
    }

    private int getArg0(final int defaultValue) {
        return this.getArg(0, defaultValue, true);
    }

    private int getArg1(final int defaultValue) {
        return this.getArg(1, defaultValue, true);
    }

    private int getArg(final int index, final int defaultValue, final boolean treatZeroAsDefault) {
        int result = this.mArgs[index];
        if (0 > result || (0 == result && treatZeroAsDefault)) {
            result = defaultValue;
        }
        return result;
    }

    private void collectOSCArgs(final int b) {
        if (MAX_OSC_STRING_LENGTH > mOSCOrDeviceControlArgs.length()) {
            this.mOSCOrDeviceControlArgs.appendCodePoint(b);
            this.continueSequence(this.mEscapeState);
        } else {
            this.finishSequence();
        }
    }

    private void finishSequence() {
        this.mEscapeState = TerminalEmulator.ESC_NONE;
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private void emitCodePoint(int codePoint) {
        this.mLastEmittedCodePoint = codePoint;
        if (this.mUseLineDrawingUsesG0 ? this.mUseLineDrawingG0 : this.mUseLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            switch (codePoint) {
                case '_':
                    // Blank.
                    codePoint = ' ';
                    break;
                case '`':
                    // Diamond.
                    codePoint = '◆';
                    break;
                case '0':
                    // Solid block;
                    codePoint = '█';
                    break;
                case 'a':
                    // Checker board.
                    codePoint = '▒';
                    break;
                case 'b':
                    // Horizontal tab.
                    codePoint = '␉';
                    break;
                case 'c':
                    // Form feed.
                    codePoint = '␌';
                    break;
                case 'd':
                    // Carriage return.
                    codePoint = '\r';
                    break;
                case 'e':
                    // Linefeed.
                    codePoint = '␊';
                    break;
                case 'f':
                    // Degree.
                    codePoint = '°';
                    break;
                case 'g':
                    // Plus-minus.
                    codePoint = '±';
                    break;
                case 'h':
                    // Newline.
                    codePoint = '\n';
                    break;
                case 'i':
                    // Vertical tab.
                    codePoint = '␋';
                    break;
                case 'j':
                    // Lower right corner.
                    codePoint = '┘';
                    break;
                case 'k':
                    // Upper right corner.
                    codePoint = '┐';
                    break;
                case 'l':
                    // Upper left corner.
                    codePoint = '┌';
                    break;
                case 'm':
                    // Left left corner.
                    codePoint = '└';
                    break;
                case 'n':
                    // Crossing lines.
                    codePoint = '┼';
                    break;
                case 'o':
                    // Horizontal line - scan 1.
                    codePoint = '⎺';
                    break;
                case 'p':
                    // Horizontal line - scan 3.
                    codePoint = '⎻';
                    break;
                case 'q':
                    // Horizontal line - scan 5.
                    codePoint = '─';
                    break;
                case 'r':
                    // Horizontal line - scan 7.
                    codePoint = '⎼';
                    break;
                case 's':
                    // Horizontal line - scan 9.
                    codePoint = '⎽';
                    break;
                case 't':
                    // T facing rightwards.
                    codePoint = '├';
                    break;
                case 'u':
                    // T facing leftwards.
                    codePoint = '┤';
                    break;
                case 'v':
                    // T facing upwards.
                    codePoint = '┴';
                    break;
                case 'w':
                    // T facing downwards.
                    codePoint = '┬';
                    break;
                case 'x':
                    // Vertical line.
                    codePoint = '│';
                    break;
                case 'y':
                    // Less than or equal to.
                    codePoint = '≤';
                    break;
                case 'z':
                    // Greater than or equal to.
                    codePoint = '≥';
                    break;
                case '{':
                    // Pi.
                    codePoint = 'π';
                    break;
                case '|':
                    // Not equal to.
                    codePoint = '≠';
                    break;
                case '}':
                    // UK pound.
                    codePoint = '£';
                    break;
                case '~':
                    // Centered dot.
                    codePoint = '·';
                    break;
            }
        }
        boolean autoWrap = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_AUTOWRAP);
        int displayWidth = WcWidth.width(codePoint);
        boolean cursorInLastColumn = this.mCursorCol == this.mRightMargin - 1;
        if (autoWrap) {
            if (cursorInLastColumn && ((this.mAboutToAutoWrap && 1 == displayWidth) || 2 == displayWidth)) {
                this.mScreen.setLineWrap(this.mCursorRow);
                this.mCursorCol = this.mLeftMargin;
                if (this.mCursorRow + 1 < this.mBottomMargin) {
                    this.mCursorRow++;
                } else {
                    this.scrollDownOneLine();
                }
            }
        } else if (cursorInLastColumn && 2 == displayWidth) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return;
        }
        if (this.mInsertMode && 0 < displayWidth) {
            // Move character to right one space.
            final int destCol = this.mCursorCol + displayWidth;
            if (destCol < this.mRightMargin)
                this.mScreen.blockCopy(this.mCursorCol, this.mCursorRow, this.mRightMargin - destCol, 1, destCol, this.mCursorRow);
        }
        final int column = this.getColumn(displayWidth);
        this.mScreen.setChar(column, this.mCursorRow, codePoint, this.getStyle());
        if (autoWrap && 0 < displayWidth)
            this.mAboutToAutoWrap = (this.mCursorCol == this.mRightMargin - displayWidth);
        this.mCursorCol = Math.min(this.mCursorCol + displayWidth, this.mRightMargin - 1);
    }

    private int getColumn(final int displayWidth) {
        final int offsetDueToCombiningChar = ((0 >= displayWidth && 0 < mCursorCol && !this.mAboutToAutoWrap) ? 1 : 0);
        int column = this.mCursorCol - offsetDueToCombiningChar;
        // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported
        // The offsetDueToCombiningChar would never be 1 if mCursorCol was 0 to get column/index=-1,
        // so was mCursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow, possibly causing others bugs too.
        if (0 > column)
            column = 0;
        return column;
    }

    /**
     * Set the cursor mode, but limit it to margins if {@link #DECSET_BIT_ORIGIN_MODE} is enabled.
     */
    private void setCursorColRespectingOriginMode(final int col) {
        this.setCursorPosition(col, this.mCursorRow);
    }

    /**
     * TODO: Better name, distinguished from {@link #setCursorPosition(int, int)} by not regarding origin mode.
     */
    private void setCursorRowCol(final int row, final int col) {
        this.mCursorRow = Math.max(0, Math.min(row, this.mRows - 1));
        this.mCursorCol = Math.max(0, Math.min(col, this.mColumns - 1));
        this.mAboutToAutoWrap = false;
    }

    public int getScrollCounter() {
        return this.mScrollCounter;
    }

    public void clearScrollCounter() {
        this.mScrollCounter = 0;
    }


    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    public void reset() {
        this.setCursorStyle();
        this.mArgIndex = 0;
        this.mContinueSequence = false;
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
        this.ESC_P_escape = false;
        this.ESC_P_sixel = false;
        this.ESC_OSC_colon = -1;
    }

    public String getSelectedText(final int x1, final int y1, final int x2, final int y2) {
        return this.mScreen.getSelectedText(x1, y1, x2, y2);
    }

    /**
     * Get the terminal session's title (null if not set).
     */
    public String getTitle() {
        return this.mTitle;
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    public void paste(String text) {
        // First: Always remove escape key and C1 control characters [0x80,0x9F]:
        text = TerminalEmulator.REGEX.matcher(text).replaceAll("");
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        text = TerminalEmulator.PATTERN.matcher(text).replaceAll("\r");
        // Then: Implement bracketed paste mode if enabled:
        final boolean bracketed = this.isDecsetInternalBitSet(TerminalEmulator.DECSET_BIT_BRACKETED_PASTE_MODE);
        if (bracketed)
            this.mSession.write("\033[200~");
        this.mSession.write(text);
        if (bracketed)
            this.mSession.write("\033[201~");
    }

    /**
     * <a href="http://www.vt100.net/docs/vt510-rm/DECSC">...</a>
     */
    static final class SavedScreenState {

        /**
         * Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences.
         */
        int mSavedCursorRow, mSavedCursorCol;

        int mSavedEffect, mSavedForeColor, mSavedBackColor;

        int mSavedDecFlags;

        boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;
    }
}
