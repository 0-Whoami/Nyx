package com.termux.terminal

import android.util.Base64
import com.termux.terminal.KeyHandler.getCodeFromTermcap
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 *
 *
 * References:
 *
 *  * [...](http://invisible-island.net/xterm/ctlseqs/ctlseqs.html)
 *  * [...](http://en.wikipedia.org/wiki/ANSI_escape_code)
 *  * [...](http://man.he.net/man4/console_codes)
 *  * [...](http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c)
 *  * [...](http://www.columbia.edu/~kermit/k95manual/iso2022.html)
 *  * [...](http://www.vt100.net/docs/vt510-rm/chapter4)
 *  * [...](http://en.wikipedia.org/wiki/ISO/IEC_2022) - for 7-bit and 8-bit GL GR explanation
 *  * [...](http://bjh21.me.uk/all-escapes/all-escapes.txt) - extensive!
 *  * [...](http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref).
 * html - document for konsole - accessible!
 *
 */
class TerminalEmulator(
    /**
     * The terminal session this emulator is bound to.
     */
    private val mSession: TerminalSession,
    boldWithBright: Boolean,
    columns: Int,
    rows: Int,
    transcriptRows: Int
) {

    val mColors: TerminalColors = TerminalColors()

    /**
     * The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal.
     */
    private val mMainBuffer: TerminalBuffer =
        TerminalBuffer(columns, transcriptRows, rows)

    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate screen buffer is active, you cannot scroll back to view saved lines).
     *
     *
     * See [...](http://www.xfree86.org/current/ctlseqs.html#The%20Alternate%20Screen%20Buffer)
     */
    private val mAltBuffer: TerminalBuffer

    /**
     * Holds the arguments of the current escape sequence.
     */
    private val mArgs = IntArray(MAX_ESCAPE_PARAMETERS)

    /**
     * Holds OSC and device control arguments, which can be strings.
     */
    private val mOSCOrDeviceControlArgs = StringBuilder()
    private val mSavedStateMain = SavedScreenState()
    private val mSavedStateAlt = SavedScreenState()

    /**
     * Indicates if bold should be shown with bright colors.
     */
    val isBoldWithBright: Boolean
    private val mUtf8InputBuffer = ByteArray(4)

    /**
     * The number of character rows and columns in the terminal screen.
     */

    var mRows: Int


    var mColumns: Int

    /**
     * Get the terminal session's title (null if not set).
     */
    /**
     * If processing first character of first parameter of [.ESC_CSI].
     */
    private var mIsCSIStart = false

    /**
     * The last character processed of a parameter of [.ESC_CSI].
     */
    private var mLastCSIArg: Int? = null

    /**
     * The cursor position. Between (0,0) and (mRows-1, mColumns-1).
     */
    private var mCursorRow = 0
    private var mCursorCol = 0

    /**
     * The terminal cursor styles.
     */
    var cursorStyle: Int = 0
        private set

    /**
     * The current screen buffer, pointing at either [.mMainBuffer] or [.mAltBuffer].
     */
    var screen: TerminalBuffer
        private set

    /**
     * Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1.
     */
    private var mArgIndex = 0

    /**
     * True if the current escape sequence should continue, false if the current escape sequence should be terminated.
     * Used when parsing a single character.
     */
    private var mContinueSequence = false

    /**
     * The current state of the escape sequence state machine. One of the ESC_* constants.
     */
    private var mEscapeState = 0
    private var ESC_P_escape = false
    private var ESC_P_sixel = false
    private var ESC_OSC_data: MutableList<Byte>? = null
    private var ESC_OSC_colon = 0

    /**
     * [...](http://www.vt100.net/docs/vt102-ug/table5-15.html)
     */
    private var mUseLineDrawingG0 = false
    private var mUseLineDrawingG1 = false
    private var mUseLineDrawingUsesG0 = true

    /**
     * @see TerminalEmulator.mapDecSetBitToInternalBit
     */
    private var mCurrentDecSetFlags = 0
    private var mSavedDecSetFlags = 0

    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private var mInsertMode = false

    /**
     * An array of tab stops. mTabStop[i] is true if there is a tab stop set for column i.
     */
    private var mTabStop: BooleanArray

    /**
     * Top margin of screen for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
     * (Defines the first row after the scrolling region). Left/right margin in [0, mColumns].
     */
    private var mTopMargin = 0
    private var mBottomMargin = 0
    private var mLeftMargin = 0
    private var mRightMargin = 0

    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (mColumns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private var mAboutToAutoWrap = false

    /**
     * If the cursor blinking is enabled. It requires cursor itself to be enabled, which is controlled
     * byt whether [.DECSET_BIT_CURSOR_ENABLED] bit is set or not.
     */
    private var mCursorBlinkingEnabled = false

    /**
     * If currently cursor should be in a visible state or not if [.mCursorBlinkingEnabled]
     * is `true`.
     */
    private var mCursorBlinkState = false

    /**
     * Current foreground and background colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     *
     * @see TextStyle
     */
    private var mForeColor = 0
    private var mBackColor = 0

    /**
     * Current [TextStyle] effect.
     */
    private var mEffect = 0

    /**
     * The number of scrolled lines since last calling [.clearScrollCounter]. Used for moving selection up along
     * with the scrolling text.
     */
    var scrollCounter: Int = 0
        private set
    private var mUtf8ToFollow: Byte = 0
    private var mUtf8Index: Byte = 0
    private var mLastEmittedCodePoint = -1
    private var cellW = 12
    private var cellH = 12

    init {
        this.screen = this.mMainBuffer
        this.mAltBuffer = TerminalBuffer(columns, rows, rows)
        this.isBoldWithBright = boldWithBright
        this.mRows = rows
        this.mColumns = columns
        this.mTabStop = BooleanArray(this.mColumns)
        this.reset()
    }

    fun setCellSize(w: Int, h: Int) {
        this.cellW = w
        this.cellH = h
    }

    private fun isDecsetInternalBitSet(bit: Int): Boolean {
        return (this.mCurrentDecSetFlags and bit) != 0
    }

    private fun setDecsetinternalBit(internalBit: Int, set: Boolean) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                this.setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false)
            } else if (DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT == internalBit) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false)
            }
        }
        mCurrentDecSetFlags = if (set) {
            mCurrentDecSetFlags or internalBit
        } else {
            mCurrentDecSetFlags and internalBit.inv()
        }
    }

    fun updateTermuxTerminalSessionClientBase() {
        mCursorBlinkState = true
    }

    val isAlternateBufferActive: Boolean
        get() = screen == mAltBuffer

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    fun sendMouseEvent(mouseButton: Int, column: Int, row: Int, pressed: Boolean) {
        var mouseButton = mouseButton
        var column = column
        var row = row
        if (1 > column) column = 1
        if (column > mColumns) column = mColumns
        if (1 > row) row = 1
        if (row > mRows) row = mRows
        if (!(MOUSE_LEFT_BUTTON_MOVED == mouseButton && !this.isDecsetInternalBitSet(
                DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
            )) && this.isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)
        ) {
            mSession.write(
                String.format(
                    "\u001b[<%d;%d;%d" + (if (pressed) 'M' else 'm'),
                    mouseButton,
                    column,
                    row
                )
            )
        } else {
            // 3 for release of all buttons.
            mouseButton = if (pressed) mouseButton else 3
            // Clip to screen, and clip to the limits of 8-bit data.
            val out_of_bounds = 255 - 32 < column || 255 - 32 < row
            if (!out_of_bounds) {
                val data = byteArrayOf(
                    '\u001b'.code.toByte(),
                    '['.code.toByte(),
                    'M'.code.toByte(),
                    (32 + mouseButton).toByte(),
                    (32 + column).toByte(),
                    (32 + row).toByte()
                )
                mSession.write(data, 0, data.size)
            }
        }
    }

    fun resize(columns: Int, rows: Int) {
        if (this.mRows == rows && this.mColumns == columns) {
            return
        } /*else require(!(2 > columns || 2 > rows)) { "rows=$rows, columns=$columns" }*/
        if (this.mRows != rows) {
            this.mRows = rows
            this.mTopMargin = 0
            this.mBottomMargin = this.mRows
        }
        if (this.mColumns != columns) {
            val oldColumns = this.mColumns
            this.mColumns = columns
            val oldTabStop = this.mTabStop
            this.mTabStop = BooleanArray(this.mColumns)
            this.setDefaultTabStops()
            val toTransfer = min(oldColumns.toDouble(), columns.toDouble()).toInt()
            System.arraycopy(oldTabStop, 0, this.mTabStop, 0, toTransfer)
            this.mLeftMargin = 0
            this.mRightMargin = this.mColumns
        }
        this.resizeScreen()
    }

    private fun resizeScreen() {
        val cursor = intArrayOf(this.mCursorCol, this.mCursorRow)
        val newTotalRows =
            if ((this.screen == this.mAltBuffer)) this.mRows else mMainBuffer.mTotalRows
        screen.resize(
            this.mColumns, this.mRows, newTotalRows, cursor,
            style,
            isAlternateBufferActive
        )
        this.mCursorCol = cursor[0]
        this.mCursorRow = cursor[1]
    }

    var cursorRow: Int
        get() = this.mCursorRow
        private set(row) {
            this.mCursorRow = row
            this.mAboutToAutoWrap = false
        }

    var cursorCol: Int
        get() = this.mCursorCol
        private set(col) {
            this.mCursorCol = col
            this.mAboutToAutoWrap = false
        }


    val isReverseVideo: Boolean
        get() = this.isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO)

    private val isCursorEnabled: Boolean
        get() = !this.isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED)

    fun shouldCursorBeVisible(): Boolean {
        return if (this.isCursorEnabled) false
        else !this.mCursorBlinkingEnabled || this.mCursorBlinkState
    }

    fun setCursorBlinkState(cursorBlinkState: Boolean) {
        mCursorBlinkState = cursorBlinkState
    }

    val isKeypadApplicationMode: Boolean
        get() = this.isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)

    val isCursorKeysApplicationMode: Boolean
        get() = this.isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS)

    val isMouseTrackingActive: Boolean
        /**
         * If mouse events are being sent as escape codes to the terminal.
         */
        get() = this.isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || this.isDecsetInternalBitSet(
            DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
        )

    private fun setDefaultTabStops() {
        for (i in 0 until this.mColumns) mTabStop[i] = 0 == (i and 7) && 0 != i
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    fun append(buffer: ByteArray, length: Int) {
        for (i in 0 until length) this.processByte(buffer[i])
    }

    private fun processByte(byteToProcess: Byte) {
        if (0 < mUtf8ToFollow) {
            if (128 == (byteToProcess.toInt() and 192)) {
                // 10xxxxxx, a continuation byte.
                mUtf8InputBuffer[mUtf8Index.toInt()] = byteToProcess
                mUtf8Index++
                --mUtf8ToFollow
                if (0 == mUtf8ToFollow.toInt()) {
                    val firstByteMask =
                        (if (2 == mUtf8Index.toInt()) 31 else (if (3 == mUtf8Index.toInt()) 15 else 7)).toByte()
                    var codePoint = (mUtf8InputBuffer[0].toInt() and firstByteMask.toInt())
                    for (i in 1 until this.mUtf8Index) codePoint =
                        ((codePoint shl 6) or (mUtf8InputBuffer[i].toInt() and 63))
                    if (((127 >= codePoint) && 1 < mUtf8Index) || (2047 > codePoint && 2 < mUtf8Index) || (65535 > codePoint && 3 < mUtf8Index)) {
                        // Overlong encoding.
                        codePoint = UNICODE_REPLACEMENT_CHAR
                    }
                    this.mUtf8Index = 0
                    if (0x80 > codePoint || 0x9F < codePoint) {
                        codePoint = when (Character.getType(codePoint).toByte()) {
                            Character.UNASSIGNED, Character.SURROGATE -> UNICODE_REPLACEMENT_CHAR
                            else -> codePoint
                        }
                        this.processCodePoint(codePoint)
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                this.mUtf8ToFollow = 0
                this.mUtf8Index = this.mUtf8ToFollow
                this.emitCodePoint(UNICODE_REPLACEMENT_CHAR)
                // The Unicode Standard Version 6.2 – Core Specification
                // (http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf):
                // "If the converter encounters an ill-formed UTF-8 code unit sequence which starts with a valid first
                // byte, but which does not continue with valid successor bytes (see Table 3-7), it must not consume the
                // successor bytes as part of the ill-formed subsequence
                // whenever those successor bytes themselves constitute part of a well-formed UTF-8 code unit
                // subsequence."
                this.processByte(byteToProcess)
            }
        } else {
            if (0 == (byteToProcess.toInt() and 128)) {
                // The leading bit is not set so it is a 7-bit ASCII character.
                this.processCodePoint(byteToProcess.toInt())
                return
            } else if (192 == (byteToProcess.toInt() and 224)) {
                // 110xxxxx, a two-byte sequence.
                this.mUtf8ToFollow = 1
            } else if (224 == (byteToProcess.toInt() and 240)) {
                // 1110xxxx, a three-byte sequence.
                this.mUtf8ToFollow = 2
            } else if (240 == (byteToProcess.toInt() and 248)) {
                // 11110xxx, a four-byte sequence.
                this.mUtf8ToFollow = 3
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                this.processCodePoint(UNICODE_REPLACEMENT_CHAR)
                return
            }
            mUtf8InputBuffer[mUtf8Index.toInt()] = byteToProcess
            mUtf8Index++
        }
    }

    private fun processCodePoint(b: Int) {
        screen.bitmapGC(300000)
        when (b) {
            0 -> {}
            7 -> if (ESC_OSC == mEscapeState) doOsc(b)
            else {
                if (ESC_APC == mEscapeState) {
                    doApc(b)
                }
            }

            8 -> if (mLeftMargin == mCursorCol) {
                // Jump to previous line if it was auto-wrapped.
                val previousRow = mCursorRow - 1
                if (0 <= previousRow && screen.getLineWrap(previousRow)) {
                    screen.clearLineWrap(previousRow)
                    setCursorRowCol(previousRow, mRightMargin - 1)
                }
            } else {
                cursorCol = mCursorCol - 1
            }

            9 ->                 // XXX: Should perhaps use color if writing to new cells. Try with
                //       printf "\033[41m\tXX\033[0m\n"
                // The OSX Terminal.app colors the spaces from the tab red, but xterm does not.
                // Note that Terminal.app only colors on new cells, in e.g.
                //       printf "\033[41m\t\r\033[42m\tXX\033[0m\n"
                // the first cells are created with a red background, but when tabbing over
                // them again with a green background they are not overwritten.
                mCursorCol = nextTabStop(1)

            10, 11, 12 -> if ((ESC_P != mEscapeState || !ESC_P_sixel) && 0 >= this.ESC_OSC_colon) {
                // Ignore CR/LF inside sixels or iterm2 data
                doLinefeed()
            }

            13 -> if ((ESC_P != mEscapeState || !ESC_P_sixel) && 0 >= this.ESC_OSC_colon) {
                // Ignore CR/LF inside sixels or iterm2 data
                cursorCol = mLeftMargin
            }

            14 -> mUseLineDrawingUsesG0 = false
            15 -> mUseLineDrawingUsesG0 = true
            24, 26 -> if (ESC_NONE != mEscapeState) {
                // FIXME: What is this??
                mEscapeState = ESC_NONE
                emitCodePoint(127)
            }

            27 ->                 // Starts an escape sequence unless we're parsing a string
                if (ESC_P == mEscapeState) {
                    // XXX: Ignore escape when reading device control sequence, since it may be part of string terminator.
                    ESC_P_escape = true
                    return
                } else if (ESC_OSC != mEscapeState) {
                    if (ESC_APC != mEscapeState) {
                        startEscapeSequence()
                    } else {
                        doApc(b)
                    }
                } else {
                    doOsc(b)
                }

            else -> {
                mContinueSequence = false
                when (mEscapeState) {
                    ESC_NONE -> if (32 <= b) emitCodePoint(b)
                    ESC -> doEsc(b)
                    ESC_POUND -> doEscPound(b)
                    ESC_SELECT_LEFT_PAREN -> mUseLineDrawingG0 = ('0'.code == b)
                    ESC_SELECT_RIGHT_PAREN -> mUseLineDrawingG1 = ('0'.code == b)
                    ESC_CSI -> doCsi(b)
                    ESC_CSI_EXCLAMATION -> if ('p'.code == b) {
                        // Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                        reset()
                    } else {
                        finishSequence()
                    }

                    ESC_CSI_QUESTIONMARK -> doCsiQuestionMark(b)
                    ESC_CSI_BIGGERTHAN -> doCsiBiggerThan(b)
                    ESC_CSI_DOLLAR -> {
                        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
                        val effectiveTopMargin = if (originMode) mTopMargin else 0
                        val effectiveBottomMargin = if (originMode) mBottomMargin else mRows
                        val effectiveLeftMargin = if (originMode) mLeftMargin else 0
                        val effectiveRightMargin = if (originMode) mRightMargin else mColumns
                        when (b.toChar()) {
                            'v' -> {
                                // Copy rectangular area (DECCRA - http://vt100.net/docs/vt510-rm/DECCRA):
                                // "If Pbs is greater than Pts, or Pls is greater than Prs, the terminal ignores DECCRA.
                                // The coordinates of the rectangular area are affected by the setting of origin mode (DECOM).
                                // DECCRA is not affected by the page margins.
                                // The copied text takes on the line attributes of the destination area.
                                // If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, then the value
                                // is treated as the width or height of that page.
                                // If the destination area is partially off the page, then DECCRA clips the off-page data.
                                // DECCRA does not change the active cursor position."
                                val topSource = min(
                                    (this.getArg(0, 1, true) - 1 + effectiveTopMargin).toDouble(),
                                    mRows.toDouble()
                                )
                                    .toInt()
                                val leftSource = min(
                                    (this.getArg(1, 1, true) - 1 + effectiveLeftMargin).toDouble(),
                                    mColumns.toDouble()
                                )
                                    .toInt()
                                // Inclusive, so do not subtract one:
                                val bottomSource = min(
                                    max(
                                        (this.getArg(
                                            2,
                                            this.mRows,
                                            true
                                        ) + effectiveTopMargin).toDouble(), topSource.toDouble()
                                    ),
                                    mRows.toDouble()
                                )
                                    .toInt()
                                val rightSource = min(
                                    max(
                                        (this.getArg(
                                            3,
                                            this.mColumns,
                                            true
                                        ) + effectiveLeftMargin).toDouble(), leftSource.toDouble()
                                    ),
                                    mColumns.toDouble()
                                )
                                    .toInt()
                                // int sourcePage = getArg(4, 1, true);
                                val destionationTop = min(
                                    (this.getArg(5, 1, true) - 1 + effectiveTopMargin).toDouble(),
                                    mRows.toDouble()
                                )
                                    .toInt()
                                val destinationLeft = min(
                                    (this.getArg(6, 1, true) - 1 + effectiveLeftMargin).toDouble(),
                                    mColumns.toDouble()
                                )
                                    .toInt()
                                // int destinationPage = getArg(7, 1, true);
                                val heightToCopy = min(
                                    (this.mRows - destionationTop).toDouble(),
                                    (bottomSource - topSource).toDouble()
                                )
                                    .toInt()
                                val widthToCopy = min(
                                    (this.mColumns - destinationLeft).toDouble(),
                                    (rightSource - leftSource).toDouble()
                                )
                                    .toInt()
                                screen.blockCopy(
                                    leftSource,
                                    topSource,
                                    widthToCopy,
                                    heightToCopy,
                                    destinationLeft,
                                    destionationTop
                                )
                            }

                            '{', 'x', 'z' -> {
                                // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                val erase = 'x'.code != b
                                val selective = '{'.code == b
                                // Only DECSERA keeps visual attributes, DECERA does not:
                                val keepVisualAttributes = erase && selective
                                var argIndex = 0
                                val fillChar: Int
                                if (erase) {
                                    fillChar = ' '.code
                                } else {
                                    fillChar = getArg(argIndex, -1, true)
                                    argIndex++
                                }
                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if ((fillChar in 32..126) || (fillChar in 160..255)) {
                                    // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    val top = min(
                                        (this.getArg(
                                            argIndex,
                                            1,
                                            true
                                        ) + effectiveTopMargin).toDouble(),
                                        (effectiveBottomMargin + 1).toDouble()
                                    )
                                        .toInt()
                                    argIndex++
                                    val left = min(
                                        (this.getArg(
                                            argIndex,
                                            1,
                                            true
                                        ) + effectiveLeftMargin).toDouble(),
                                        (effectiveRightMargin + 1).toDouble()
                                    )
                                        .toInt()
                                    argIndex++
                                    val bottom = min(
                                        (this.getArg(
                                            argIndex,
                                            this.mRows,
                                            true
                                        ) + effectiveTopMargin).toDouble(),
                                        effectiveBottomMargin.toDouble()
                                    )
                                        .toInt()
                                    argIndex++
                                    val right = min(
                                        (this.getArg(
                                            argIndex,
                                            this.mColumns,
                                            true
                                        ) + effectiveLeftMargin).toDouble(),
                                        effectiveRightMargin.toDouble()
                                    )
                                        .toInt()
                                    val style = this.style
                                    var row = top - 1
                                    while (row < bottom) {
                                        var col = left - 1
                                        while (col < right) {
                                            if (!selective || 0 == (TextStyle.decodeEffect(
                                                    screen.getStyleAt(row, col)
                                                ) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED)
                                            ) screen.setChar(
                                                col,
                                                row,
                                                fillChar,
                                                if (keepVisualAttributes) screen.getStyleAt(
                                                    row,
                                                    col
                                                ) else style
                                            )
                                            col++
                                        }
                                        row++
                                    }
                                }
                            }

                            'r', 't' -> {
                                // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                val reverse = 't'.code == b
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                val top = (min(
                                    (this.getArg(0, 1, true) - 1).toDouble(),
                                    effectiveBottomMargin.toDouble()
                                ) + effectiveTopMargin).toInt()
                                val left = (min(
                                    (this.getArg(1, 1, true) - 1).toDouble(),
                                    effectiveRightMargin.toDouble()
                                ) + effectiveLeftMargin).toInt()
                                val bottom = (min(
                                    (this.getArg(2, this.mRows, true) + 1).toDouble(),
                                    (effectiveBottomMargin - 1).toDouble()
                                ) + effectiveTopMargin).toInt()
                                val right = (min(
                                    (this.getArg(3, this.mColumns, true) + 1).toDouble(),
                                    (effectiveRightMargin - 1).toDouble()
                                ) + effectiveLeftMargin).toInt()
                                if (4 <= mArgIndex) {
                                    if (this.mArgIndex >= mArgs.size) this.mArgIndex =
                                        mArgs.size - 1
                                    var i = 4
                                    while (i <= this.mArgIndex) {
                                        var bits = 0
                                        // True if setting, false if clearing.
                                        var setOrClear = true
                                        when (this.getArg(i, 0, false)) {
                                            0 -> {
                                                bits =
                                                    (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE or TextStyle.CHARACTER_ATTRIBUTE_BLINK or TextStyle.CHARACTER_ATTRIBUTE_INVERSE)
                                                if (!reverse) setOrClear = false
                                            }

                                            1 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD
                                            4 -> bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                                            5 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK
                                            7 -> bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                                            22 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD
                                                setOrClear = false
                                            }

                                            24 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                                                setOrClear = false
                                            }

                                            25 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK
                                                setOrClear = false
                                            }

                                            27 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                                                setOrClear = false
                                            }
                                        }
                                        if (!reverse || setOrClear) {
                                            screen.setOrClearEffect(
                                                bits,
                                                setOrClear,
                                                reverse,
                                                isDecsetInternalBitSet(
                                                    DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE
                                                ),
                                                effectiveLeftMargin,
                                                effectiveRightMargin,
                                                top,
                                                left,
                                                bottom,
                                                right
                                            )
                                        }
                                        i++
                                    }
                                } // Do nothing.
                            }

                            else -> finishSequence()
                        }
                    }

                    ESC_CSI_DOUBLE_QUOTE -> if ('q'.code == b) {
                        // http://www.vt100.net/docs/vt510-rm/DECSCA
                        val arg = getArg0(0)
                        when (arg) {
                            0, 2 -> {
                                // DECSED and DECSEL can erase characters.
                                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED.inv()
                            }

                            1 -> {
                                // DECSED and DECSEL cannot erase characters.
                                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_PROTECTED
                            }

                            else -> {
                                finishSequence()
                            }
                        }
                    } else {
                        this.finishSequence()
                    }

                    ESC_CSI_SINGLE_QUOTE -> if ('}'.code == b) {
                        // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                        val columnsAfterCursor = this.mRightMargin - this.mCursorCol
                        val columnsToInsert =
                            min(getArg0(1).toDouble(), columnsAfterCursor.toDouble())
                                .toInt()
                        val columnsToMove = columnsAfterCursor - columnsToInsert
                        screen.blockCopy(
                            this.mCursorCol,
                            0,
                            columnsToMove,
                            this.mRows,
                            this.mCursorCol + columnsToInsert,
                            0
                        )
                        this.blockClear(this.mCursorCol, 0, columnsToInsert, this.mRows)
                    } else if ('~'.code == b) {
                        // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                        val columnsAfterCursor = this.mRightMargin - this.mCursorCol
                        val columnsToDelete =
                            min(getArg0(1).toDouble(), columnsAfterCursor.toDouble())
                                .toInt()
                        val columnsToMove = columnsAfterCursor - columnsToDelete
                        screen.blockCopy(
                            this.mCursorCol + columnsToDelete,
                            0,
                            columnsToMove,
                            this.mRows,
                            this.mCursorCol,
                            0
                        )
                    } else {
                        this.finishSequence()
                    }

                    ESC_PERCENT -> {}
                    ESC_APC -> this.doApc(b)
                    ESC_APC_ESC -> this.doApcEsc(b)
                    ESC_OSC -> this.doOsc(b)
                    ESC_OSC_ESC -> this.doOscEsc(b)
                    ESC_P -> this.doDeviceControl(b)
                    ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> if ('p'.code == b) {
                        // Request DEC private mode (DECRQM).
                        val mode = this.getArg0(0)
                        val value = this.getValues(mode)
                        mSession.write(String.format(Locale.US, "\u001b[?%d;%d\$y", mode, value))
                    } else {
                        this.finishSequence()
                    }

                    ESC_CSI_ARGS_SPACE -> {
                        val arg = this.getArg0(0)
                        when (b.toChar()) {
                            'q' -> when (arg) {
                                0, 1, 2 -> {
                                    this.cursorStyle = TERMINAL_CURSOR_STYLE_BLOCK
                                    this.mCursorBlinkingEnabled = 2 != arg
                                }

                                3, 4 -> {
                                    this.cursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE
                                    this.mCursorBlinkingEnabled = 4 != arg
                                }

                                5, 6 -> {
                                    this.cursorStyle = TERMINAL_CURSOR_STYLE_BAR
                                    this.mCursorBlinkingEnabled = 6 != arg
                                }
                            }

                            't', 'u' -> {}
                            else -> this.finishSequence()
                        }
                    }

                    ESC_CSI_ARGS_ASTERIX -> {
                        val attributeChangeExtent = this.getArg0(0)
                        if ('x'.code == b && (attributeChangeExtent in 0..2)) {
                            // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            this.setDecsetinternalBit(
                                DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE,
                                2 == attributeChangeExtent
                            )
                        } else {
                            this.finishSequence()
                        }
                    }

                    else -> this.finishSequence()
                }
                if (!this.mContinueSequence) this.mEscapeState = ESC_NONE
            }
        }
    }

    private fun getValues(mode: Int): Int {
        val value: Int
        if (47 == mode || 1047 == mode || 1049 == mode) {
            // This state is carried by mScreen pointer.
            value = if ((this.screen == this.mAltBuffer)) 1 else 2
        } else {
            val internalBit = mapDecSetBitToInternalBit(mode)
            value = if (-1 != internalBit) {
                // 1=set, 2=reset.
                if (this.isDecsetInternalBitSet(internalBit)) 1 else 2
            } else {
                // 0=not recognized, 3=permanently set, 4=permanently reset
                0
            }
        }
        return value
    }

    /**
     * When in [.ESC_P] ("device control") sequence.
     */
    private fun doDeviceControl(b: Int) {
        var firstSixel = false
        if (!this.ESC_P_sixel && ('$'.code == b || '-'.code == b || '#'.code == b)) {
            //Check if sixel sequence that needs breaking
            val dcs = mOSCOrDeviceControlArgs.toString()
            if (REGEXP.matcher(dcs).matches()) {
                firstSixel = true
            }
        }
        if (firstSixel || (this.ESC_P_escape && '\\'.code == b) || (this.ESC_P_sixel && ('$'.code == b || '-'.code == b || '#'.code == b))) // ESC \ terminates OSC
        // Sixel sequences may be very long. '$' and '!' are natural for breaking the sequence.
        {
            var dcs = mOSCOrDeviceControlArgs.toString()
            // DCS $ q P t ST. Request Status String (DECRQSS)
            if (dcs.startsWith("\$q")) {
                if ("\$q\"p" == dcs) {
                    // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                    val csiString = "64;1\"p"
                    mSession.write("\u001bP1\$r$csiString\u001b\\")
                } else {
                    this.finishSequence()
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
                for (part in dcs.substring(2).split(";".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    if (0 == part.length % 2) {
                        val transBuffer = StringBuilder()
                        var c: Char
                        var i = 0
                        while (i < part.length) {
                            try {
                                c = Char(
                                    java.lang.Long.decode("0x" + part[i] + part[i + 1]).toUShort()
                                )
                            } catch (e: NumberFormatException) {
                                i += 2
                                continue
                            }
                            transBuffer.append(c)
                            i += 2
                        }
                        val trans = transBuffer.toString()
                        val responseValue = when (trans) {
                            "Co", "colors" ->  // Number of colors.
                                "256"

                            "TN", "name" -> "xterm"
                            else -> getCodeFromTermcap(
                                trans, this.isDecsetInternalBitSet(
                                    DECSET_BIT_APPLICATION_CURSOR_KEYS
                                ), this.isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)
                            )
                        }
                        if (null == responseValue) {
                            when (trans) {
                                "%1", "&8" -> {}
                            }
                            // Respond with invalid request:
                            mSession.write("\u001bP0+r$part\u001b\\")
                        } else {
                            val hexEncoded = StringBuilder()
                            for (element in responseValue) {
                                hexEncoded.append(String.format("%02X", element.code))
                            }
                            mSession.write("\u001bP1+r$part=$hexEncoded\u001b\\")
                        }
                    }
                }
            } else if (this.ESC_P_sixel || REGEXP.matcher(dcs).matches()) {
                var pos = 0
                if (!this.ESC_P_sixel) {
                    this.ESC_P_sixel = true
                    screen.sixelStart(100, 100)
                    while ('q'.code != dcs.codePointAt(pos)) {
                        pos++
                    }
                    pos++
                }
                if ('$'.code == b || '-'.code == b) {
                    // Add to string
                    dcs += b.toChar()
                }
                var rep = 1
                while (pos < dcs.length) {
                    if ('"'.code == dcs.codePointAt(pos)) {
                        pos++
                        //int[] args = { 0, 0, 0, 0 };
                        var arg = 0
                        while (pos < dcs.length && (('0'.code <= dcs.codePointAt(pos) && '9'.code >= dcs.codePointAt(
                                pos
                            )) || ';'.code == dcs.codePointAt(pos))
                        ) {
                            if ('0'.code > dcs.codePointAt(pos) || '9'.code < dcs.codePointAt(pos)) {
                                arg++
                                if (3 < arg) {
                                    break
                                }
                            }
                            pos++
                        }
                        if (pos == dcs.length) {
                            break
                        }
                    } else if ('#'.code == dcs.codePointAt(pos)) {
                        var col = 0
                        pos++
                        while (pos < dcs.length && '0'.code <= dcs.codePointAt(pos) && '9'.code >= dcs.codePointAt(
                                pos
                            )
                        ) {
                            col = col * 10 + dcs.codePointAt(pos) - '0'.code
                            pos++
                        }
                        if (pos == dcs.length || ';'.code != dcs.codePointAt(pos)) {
                            screen.sixelSetColor(col)
                        } else {
                            pos++
                            val args = intArrayOf(0, 0, 0, 0)
                            var arg = 0
                            while (pos < dcs.length && (('0'.code <= dcs.codePointAt(pos) && '9'.code >= dcs.codePointAt(
                                    pos
                                )) || ';'.code == dcs.codePointAt(pos))
                            ) {
                                if ('0'.code <= dcs.codePointAt(pos) && '9'.code >= dcs.codePointAt(
                                        pos
                                    )
                                ) {
                                    args[arg] = args[arg] * 10 + dcs.codePointAt(pos) - '0'.code
                                } else {
                                    arg++
                                    if (3 < arg) {
                                        break
                                    }
                                }
                                pos++
                            }
                            if (2 == args[0]) {
                                screen.sixelSetColor(col, args[1], args[2], args[3])
                            }
                        }
                    } else if ('!'.code == dcs.codePointAt(pos)) {
                        rep = 0
                        pos++
                        while (pos < dcs.length && '0'.code <= dcs.codePointAt(pos) && '9'.code >= dcs.codePointAt(
                                pos
                            )
                        ) {
                            rep = rep * 10 + dcs.codePointAt(pos) - '0'.code
                            pos++
                        }
                    } else if ('$'.code == dcs.codePointAt(pos) || '-'.code == dcs.codePointAt(pos) || ('?'.code <= dcs.codePointAt(
                            pos
                        ) && '~'.code >= dcs.codePointAt(pos))
                    ) {
                        screen.sixelChar(dcs.codePointAt(pos), rep)
                        pos++
                        rep = 1
                    } else {
                        pos++
                    }
                }
                if ('\\'.code == b) {
                    this.ESC_P_sixel = false
                    var n =
                        screen.sixelEnd(this.mCursorRow, this.mCursorCol, this.cellW, this.cellH)
                    while (0 < n) {
                        this.doLinefeed()
                        n--
                    }
                } else {
                    mOSCOrDeviceControlArgs.setLength(0)
                    if ('#'.code == b) {
                        mOSCOrDeviceControlArgs.appendCodePoint('#'.code)
                    }
                    // Do not finish sequence
                    this.continueSequence(this.mEscapeState)
                    return
                }
            }
            this.finishSequence()
        } else {
            this.ESC_P_escape = false
            if (MAX_OSC_STRING_LENGTH < mOSCOrDeviceControlArgs.length) {
                // Too long.
                mOSCOrDeviceControlArgs.setLength(0)
                this.finishSequence()
            } else {
                mOSCOrDeviceControlArgs.appendCodePoint(b)
                this.continueSequence(this.mEscapeState)
            }
        }
    }

    private fun nextTabStop(numTabs: Int): Int {
        var numTabs = numTabs
        for (i in this.mCursorCol + 1 until this.mColumns) {
            if (mTabStop[i]) {
                --numTabs
                if (0 == numTabs) return min(i.toDouble(), mRightMargin.toDouble())
                    .toInt()
            }
        }
        return this.mRightMargin - 1
    }

    /**
     * Process byte while in the [.ESC_CSI_QUESTIONMARK] escape state.
     */
    private fun doCsiQuestionMark(b: Int) {
        when (b.toChar()) {
            'J', 'K' -> {
                this.mAboutToAutoWrap = false
                val fillChar = ' '.code
                var startCol = -1
                var startRow = -1
                var endCol = -1
                var endRow = -1
                val justRow = ('K'.code == b)
                when (this.getArg0(0)) {
                    0 -> {
                        startCol = this.mCursorCol
                        startRow = this.mCursorRow
                        endCol = this.mColumns
                        endRow = if (justRow) (this.mCursorRow + 1) else this.mRows
                    }

                    1 -> {
                        startCol = 0
                        startRow = if (justRow) this.mCursorRow else 0
                        endCol = this.mCursorCol + 1
                        endRow = this.mCursorRow + 1
                    }

                    2 -> {
                        startCol = 0
                        startRow = if (justRow) this.mCursorRow else 0
                        endCol = this.mColumns
                        endRow = if (justRow) (this.mCursorRow + 1) else this.mRows
                    }

                    else -> this.finishSequence()
                }
                val style = this.style
                var row = startRow
                while (row < endRow) {
                    var col = startCol
                    while (col < endCol) {
                        if (0 == (TextStyle.decodeEffect(
                                screen.getStyleAt(
                                    row,
                                    col
                                )
                            ) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED)
                        ) screen.setChar(col, row, fillChar, style)
                        col++
                    }
                    row++
                }
            }

            'h', 'l' -> {
                if (this.mArgIndex >= mArgs.size) this.mArgIndex =
                    mArgs.size - 1
                var i = 0
                while (i <= this.mArgIndex) {
                    this.doDecSetOrReset('h'.code == b, mArgs[i])
                    i++
                }
            }

            'n' -> if (6 == getArg0(-1)) { // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                mSession.write(
                    String.format(
                        Locale.US,
                        "\u001b[?%d;%d;1R",
                        this.mCursorRow + 1,
                        this.mCursorCol + 1
                    )
                )
            } else {
                this.finishSequence()
                return
            }

            'r', 's' -> {
                if (this.mArgIndex >= mArgs.size) this.mArgIndex =
                    mArgs.size - 1
                var i = 0
                while (i <= this.mArgIndex) {
                    val externalBit = mArgs[i]
                    val internalBit = mapDecSetBitToInternalBit(externalBit)
                    if (-1 != internalBit) {
                        if ('s'.code == b) {
                            this.mSavedDecSetFlags = this.mSavedDecSetFlags or internalBit
                        } else {
                            this.doDecSetOrReset(
                                0 != (mSavedDecSetFlags and internalBit),
                                externalBit
                            )
                        }
                    }
                    i++
                }
            }

            '$' -> {
                this.continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR)
                return
            }

            else -> this.parseArg(b)
        }
    }

    private fun doDecSetOrReset(setting: Boolean, externalBit: Int) {
        val internalBit = mapDecSetBitToInternalBit(externalBit)
        if (-1 != internalBit) {
            this.setDecsetinternalBit(internalBit, setting)
        }
        when (externalBit) {
            1 -> {}
            3 -> {
                run {
                    this.mTopMargin = 0
                    this.mLeftMargin = this.mTopMargin
                }
                this.mBottomMargin = this.mRows
                this.mRightMargin = this.mColumns
                // "DECCOLM resets vertical split screen mode (DECLRMM) to unavailable":
                this.setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false)
                // "Erases all data in page memory":
                this.blockClear(0, 0, this.mColumns, this.mRows)
                this.setCursorRowCol(0, 0)
            }

            4 -> {}
            5 -> {}
            6 -> if (setting) this.setCursorPosition(0, 0)
            7, 8, 9, 12, 25 -> {}
            40, 45, 66 -> {}
            69 -> if (!setting) {
                this.mLeftMargin = 0
                this.mRightMargin = this.mColumns
            }

            1000, 1001, 1002, 1003, 1004, 1005, 1006, 1015, 1034 -> {}
            1048 -> if (setting) this.saveCursor()
            else this.restoreCursor()

            47, 1047, 1049 -> {
                // Set: Save cursor as in DECSC and use Alternate Screen Buffer, clearing it first.
                // Reset: Use Normal Screen Buffer and restore cursor as in DECRC.
                val newScreen = if (setting) this.mAltBuffer else this.mMainBuffer
                if (newScreen != this.screen) {
                    val resized =
                        !(newScreen.mColumns == this.mColumns && newScreen.mScreenRows == this.mRows)
                    if (setting) this.saveCursor()
                    this.screen = newScreen
                    if (!setting) {
                        val col = mSavedStateMain.mSavedCursorCol
                        val row = mSavedStateMain.mSavedCursorRow
                        this.restoreCursor()
                        if (resized) {
                            // Restore cursor position _not_ clipped to current screen (let resizeScreen() handle that):
                            this.mCursorCol = col
                            this.mCursorRow = row
                        }
                    }
                    // Check if buffer size needs to be updated:
                    if (resized) this.resizeScreen()
                    // Clear new screen if alt buffer:
                    if (newScreen == this.mAltBuffer) newScreen.blockSet(
                        0, 0, this.mColumns, this.mRows, ' '.code,
                        style
                    )
                }
            }

            2004 -> {}
            else -> this.finishSequence()
        }
    }

    private fun doCsiBiggerThan(b: Int) {
        when (b.toChar()) {
            'c' ->                 // Originally this was used for the terminal to respond with "identification code, firmware version level,
                // and hardware options" (http://vt100.net/docs/vt510-rm/DA2), with the first "41" meaning the VT420
                // terminal type. This is not used anymore, but the second version level field has been changed by xterm
                // to mean it's release number ("patch numbers" listed at http://invisible-island.net/xterm/xterm.log.html),
                // and some applications use it as a feature check:
                // * tmux used to have a "xterm won't reach version 500 for a while so set that as the upper limit" check,
                // and then check "xterm_version > 270" if rectangular area operations such as DECCRA could be used.
                // * vim checks xterm version number >140 for "Request termcap/terminfo string" functionality >276 for SGR
                // mouse report.
                // The third number is a keyboard identifier not used nowadays.
                mSession.write("\u001b[>41;320;0c")

            'm' -> {}
            else -> this.parseArg(b)
        }
    }

    private fun startEscapeSequence() {
        this.mEscapeState = ESC
        this.mArgIndex = 0
        Arrays.fill(this.mArgs, -1)
    }

    private fun doLinefeed() {
        val belowScrollingRegion = this.mCursorRow >= this.mBottomMargin
        var newCursorRow = this.mCursorRow + 1
        if (belowScrollingRegion) {
            // Move down (but not scroll) as long as we are above the last row.
            if (this.mCursorRow != this.mRows - 1) {
                this.cursorRow = newCursorRow
            }
        } else {
            if (newCursorRow == this.mBottomMargin) {
                this.scrollDownOneLine()
                newCursorRow = this.mBottomMargin - 1
            }
            this.cursorRow = newCursorRow
        }
    }

    private fun continueSequence(state: Int) {
        this.mEscapeState = state
        this.mContinueSequence = true
    }

    private fun doEscPound(b: Int) {
        // Esc # 8 - DEC screen alignment test - fill screen with E's.
        if ('8'.code == b) {
            screen.blockSet(
                0, 0, this.mColumns, this.mRows, 'E'.code,
                style
            )
        } else {
            this.finishSequence()
        }
    }

    /**
     * Encountering a character in the [.ESC] state.
     */
    private fun doEsc(b: Int) {
        when (b.toChar()) {
            '#' -> this.continueSequence(ESC_POUND)
            '(' -> this.continueSequence(ESC_SELECT_LEFT_PAREN)
            ')' -> this.continueSequence(ESC_SELECT_RIGHT_PAREN)
            '6' -> if (this.mCursorCol > this.mLeftMargin) {
                mCursorCol--
            } else {
                val rows = this.mBottomMargin - this.mTopMargin
                screen.blockCopy(
                    this.mLeftMargin,
                    this.mTopMargin,
                    this.mRightMargin - this.mLeftMargin - 1,
                    rows,
                    this.mLeftMargin + 1,
                    this.mTopMargin
                )
                screen.blockSet(
                    this.mLeftMargin, this.mTopMargin, 1, rows, ' '.code, TextStyle.encode(
                        this.mForeColor, this.mBackColor, 0
                    )
                )
            }

            '7' -> this.saveCursor()
            '8' -> this.restoreCursor()
            '9' -> if (this.mCursorCol < this.mRightMargin - 1) {
                mCursorCol++
            } else {
                val rows = this.mBottomMargin - this.mTopMargin
                screen.blockCopy(
                    this.mLeftMargin + 1,
                    this.mTopMargin,
                    this.mRightMargin - this.mLeftMargin - 1,
                    rows,
                    this.mLeftMargin,
                    this.mTopMargin
                )
                screen.blockSet(
                    this.mRightMargin - 1, this.mTopMargin, 1, rows, ' '.code, TextStyle.encode(
                        this.mForeColor, this.mBackColor, 0
                    )
                )
            }

            'c' -> {
                this.reset()
                mMainBuffer.clearTranscript()
                this.blockClear(0, 0, this.mColumns, this.mRows)
                this.setCursorPosition(0, 0)
            }

            'D' -> this.doLinefeed()
            'E' -> {
                this.cursorCol =
                    if (this.isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)) this.mLeftMargin else 0
                this.doLinefeed()
            }

            'F' -> this.setCursorRowCol(0, this.mBottomMargin - 1)
            'H' -> mTabStop[mCursorCol] = true
            'M' ->                 // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (this.mCursorRow <= this.mTopMargin) {
                    screen.blockCopy(
                        this.mLeftMargin,
                        this.mTopMargin,
                        this.mRightMargin - this.mLeftMargin,
                        this.mBottomMargin - (this.mTopMargin + 1),
                        this.mLeftMargin,
                        this.mTopMargin + 1
                    )
                    this.blockClear(
                        this.mLeftMargin,
                        this.mTopMargin,
                        this.mRightMargin - this.mLeftMargin
                    )
                } else {
                    mCursorRow--
                }

            'N', '0' -> {}
            'P' -> {
                mOSCOrDeviceControlArgs.setLength(0)
                this.ESC_P_escape = false
                this.continueSequence(ESC_P)
            }

            '[' -> {
                this.continueSequence(ESC_CSI)
                this.mIsCSIStart = true
                this.mLastCSIArg = null
            }

            '=' -> this.setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true)
            ']' -> {
                mOSCOrDeviceControlArgs.setLength(0)
                this.continueSequence(ESC_OSC)
                this.ESC_OSC_colon = -1
            }

            '>' -> this.setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false)
            '_' -> {
                mOSCOrDeviceControlArgs.setLength(0)
                this.continueSequence(ESC_APC)
            }

            else -> this.finishSequence()
        }
    }

    /**
     * DECSC save cursor - [...](http://www.vt100.net/docs/vt510-rm/DECSC) . See [.restoreCursor].
     */
    private fun saveCursor() {
        val state =
            if ((this.screen == this.mMainBuffer)) this.mSavedStateMain else this.mSavedStateAlt
        state.mSavedCursorRow = this.mCursorRow
        state.mSavedCursorCol = this.mCursorCol
        state.mSavedEffect = this.mEffect
        state.mSavedForeColor = this.mForeColor
        state.mSavedBackColor = this.mBackColor
        state.mSavedDecFlags = this.mCurrentDecSetFlags
        state.mUseLineDrawingG0 = this.mUseLineDrawingG0
        state.mUseLineDrawingG1 = this.mUseLineDrawingG1
        state.mUseLineDrawingUsesG0 = this.mUseLineDrawingUsesG0
    }

    /**
     * DECRS restore cursor - [...](http://www.vt100.net/docs/vt510-rm/DECRC). See [.saveCursor].
     */
    private fun restoreCursor() {
        val state =
            if ((this.screen == this.mMainBuffer)) this.mSavedStateMain else this.mSavedStateAlt
        this.setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol)
        this.mEffect = state.mSavedEffect
        this.mForeColor = state.mSavedForeColor
        this.mBackColor = state.mSavedBackColor
        val mask = (DECSET_BIT_AUTOWRAP or DECSET_BIT_ORIGIN_MODE)
        this.mCurrentDecSetFlags =
            (this.mCurrentDecSetFlags and mask.inv()) or (state.mSavedDecFlags and mask)
        this.mUseLineDrawingG0 = state.mUseLineDrawingG0
        this.mUseLineDrawingG1 = state.mUseLineDrawingG1
        this.mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". [.ESC_CSI].
     */
    private fun doCsi(b: Int) {
        when (b.toChar()) {
            '!' -> this.continueSequence(ESC_CSI_EXCLAMATION)
            '"' -> this.continueSequence(ESC_CSI_DOUBLE_QUOTE)
            '\'' -> this.continueSequence(ESC_CSI_SINGLE_QUOTE)
            '$' -> this.continueSequence(ESC_CSI_DOLLAR)
            '*' -> this.continueSequence(ESC_CSI_ARGS_ASTERIX)
            '@' -> {
                // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                this.mAboutToAutoWrap = false
                val columnsAfterCursor = this.mColumns - this.mCursorCol
                val spacesToInsert = min(getArg0(1).toDouble(), columnsAfterCursor.toDouble())
                    .toInt()
                val charsToMove = columnsAfterCursor - spacesToInsert
                screen.blockCopy(
                    this.mCursorCol,
                    this.mCursorRow,
                    charsToMove,
                    1,
                    this.mCursorCol + spacesToInsert,
                    this.mCursorRow
                )
                this.blockClear(this.mCursorCol, this.mCursorRow, spacesToInsert)
            }

            'A' -> this.cursorRow = max(0.0, (this.mCursorRow - this.getArg0(1)).toDouble())
                .toInt()

            'B' -> this.cursorRow =
                min((this.mRows - 1).toDouble(), (this.mCursorRow + this.getArg0(1)).toDouble())
                    .toInt()

            'C', 'a' -> this.cursorCol = min(
                (this.mRightMargin - 1).toDouble(),
                (this.mCursorCol + this.getArg0(1)).toDouble()
            )
                .toInt()

            'D' -> this.cursorCol =
                max(mLeftMargin.toDouble(), (this.mCursorCol - this.getArg0(1)).toDouble())
                    .toInt()

            'E' -> this.setCursorPosition(0, this.mCursorRow + this.getArg0(1))
            'F' -> this.setCursorPosition(0, this.mCursorRow - this.getArg0(1))
            'G' -> this.cursorCol = (min(
                max(
                    1.0,
                    getArg0(1).toDouble()
                ), mColumns.toDouble()
            ) - 1).toInt()

            'H', 'f' -> this.setCursorPosition(this.getArg1(1) - 1, this.getArg0(1) - 1)
            'I' -> this.cursorCol = this.nextTabStop(this.getArg0(1))
            'J' -> {
                when (this.getArg0(0)) {
                    0 -> {
                        this.blockClear(
                            this.mCursorCol,
                            this.mCursorRow,
                            this.mColumns - this.mCursorCol
                        )
                        this.blockClear(
                            0,
                            this.mCursorRow + 1,
                            this.mColumns,
                            this.mRows - (this.mCursorRow + 1)
                        )
                    }

                    1 -> {
                        this.blockClear(0, 0, this.mColumns, this.mCursorRow)
                        this.blockClear(0, this.mCursorRow, this.mCursorCol + 1)
                    }

                    2 ->                         // move..
                        this.blockClear(0, 0, this.mColumns, this.mRows)

                    3 -> mMainBuffer.clearTranscript()
                    else -> {
                        this.finishSequence()
                        return
                    }
                }
                this.mAboutToAutoWrap = false
            }

            'K' -> {
                when (this.getArg0(0)) {
                    0 -> this.blockClear(
                        this.mCursorCol,
                        this.mCursorRow,
                        this.mColumns - this.mCursorCol
                    )

                    1 -> this.blockClear(0, this.mCursorRow, this.mCursorCol + 1)
                    2 -> this.blockClear(0, this.mCursorRow, this.mColumns)
                    else -> {
                        this.finishSequence()
                        return
                    }
                }
                this.mAboutToAutoWrap = false
            }

            'L' -> {
                val linesAfterCursor = this.mBottomMargin - this.mCursorRow
                val linesToInsert = min(getArg0(1).toDouble(), linesAfterCursor.toDouble())
                    .toInt()
                val linesToMove = linesAfterCursor - linesToInsert
                screen.blockCopy(
                    0,
                    this.mCursorRow,
                    this.mColumns,
                    linesToMove,
                    0,
                    this.mCursorRow + linesToInsert
                )
                this.blockClear(0, this.mCursorRow, this.mColumns, linesToInsert)
            }

            'M' -> {
                this.mAboutToAutoWrap = false
                val linesAfterCursor = this.mBottomMargin - this.mCursorRow
                val linesToDelete = min(getArg0(1).toDouble(), linesAfterCursor.toDouble())
                    .toInt()
                val linesToMove = linesAfterCursor - linesToDelete
                screen.blockCopy(
                    0,
                    this.mCursorRow + linesToDelete,
                    this.mColumns,
                    linesToMove,
                    0,
                    this.mCursorRow
                )
                this.blockClear(0, this.mCursorRow + linesToMove, this.mColumns, linesToDelete)
            }

            'P' -> {
                // http://www.vt100.net/docs/vt510-rm/DCH: "If ${N} is greater than the number of characters between the
                // cursor and the right margin, then DCH only deletes the remaining characters.
                // As characters are deleted, the remaining characters between the cursor and right margin move to the left.
                // Character attributes move with the characters. The terminal adds blank spaces with no visual character
                // attributes at the right margin. DCH has no effect outside the scrolling margins."
                this.mAboutToAutoWrap = false
                val cellsAfterCursor = this.mColumns - this.mCursorCol
                val cellsToDelete = min(getArg0(1).toDouble(), cellsAfterCursor.toDouble())
                    .toInt()
                val cellsToMove = cellsAfterCursor - cellsToDelete
                screen.blockCopy(
                    this.mCursorCol + cellsToDelete,
                    this.mCursorRow,
                    cellsToMove,
                    1,
                    this.mCursorCol,
                    this.mCursorRow
                )
                this.blockClear(this.mCursorCol + cellsToMove, this.mCursorRow, cellsToDelete)
            }

            'S' -> {
                // "${CSI}${N}S" - scroll up ${N} lines (default = 1) (SU).
                val linesToScroll = this.getArg0(1)
                var i = 0
                while (i < linesToScroll) {
                    this.scrollDownOneLine()
                    i++
                }
            }

            'T' -> if (0 == mArgIndex) {
                // "${CSI}${N}T" - Scroll down N lines (default = 1) (SD).
                // http://vt100.net/docs/vt510-rm/SD: "N is the number of lines to move the user window up in page
                // memory. N new lines appear at the top of the display. N old lines disappear at the bottom of the
                // display. You cannot pan past the top margin of the current page".
                val linesToScrollArg = this.getArg0(1)
                val linesBetweenTopAndBottomMargins = this.mBottomMargin - this.mTopMargin
                val linesToScroll =
                    min(linesBetweenTopAndBottomMargins.toDouble(), linesToScrollArg.toDouble())
                        .toInt()
                screen.blockCopy(
                    this.mLeftMargin,
                    this.mTopMargin,
                    this.mRightMargin - this.mLeftMargin,
                    linesBetweenTopAndBottomMargins - linesToScroll,
                    this.mLeftMargin,
                    this.mTopMargin + linesToScroll
                )
                this.blockClear(
                    this.mLeftMargin,
                    this.mTopMargin,
                    this.mRightMargin - this.mLeftMargin,
                    linesToScroll
                )
            } else {
                // "${CSI}${func};${startx};${starty};${firstrow};${lastrow}T" - initiate highlight mouse tracking.
                this.finishSequence()
            }

            'X' -> {
                this.mAboutToAutoWrap = false
                screen.blockSet(
                    this.mCursorCol, this.mCursorRow, min(
                        getArg0(1).toDouble(), (this.mColumns - this.mCursorCol).toDouble()
                    )
                        .toInt(), 1, ' '.code,
                    style
                )
            }

            'Z' -> {
                var numberOfTabs = this.getArg0(1)
                var newCol = this.mLeftMargin
                var i = this.mCursorCol - 1
                while (0 <= i) {
                    if (mTabStop[i]) {
                        --numberOfTabs
                        if (0 == numberOfTabs) {
                            newCol = max(i.toDouble(), mLeftMargin.toDouble())
                                .toInt()
                            break
                        }
                    }
                    i--
                }
                this.mCursorCol = newCol
            }

            '?' -> this.continueSequence(ESC_CSI_QUESTIONMARK)
            '>' -> this.continueSequence(ESC_CSI_BIGGERTHAN)
            '`' -> this.setCursorColRespectingOriginMode(this.getArg0(1) - 1)
            'b' -> {
                if (-1 == mLastEmittedCodePoint) return
                val numRepeat = this.getArg0(1)
                var i = 0
                while (i < numRepeat) {
                    this.emitCodePoint(this.mLastEmittedCodePoint)
                    i++
                }
            }

            'c' ->                 // The important part that may still be used by some (tmux stores this value but does not currently use it)
                // is the first response parameter identifying the terminal service class, where we send 64 for "vt420".
                // This is followed by a list of attributes which is probably unused by applications. Send like xterm.
                if (0 == getArg0(0)) mSession.write("\u001b[?64;1;2;4;6;9;15;18;21;22c")

            'd' -> this.cursorRow = (min(
                max(
                    1.0,
                    getArg0(1).toDouble()
                ), mRows.toDouble()
            ) - 1).toInt()

            'e' -> this.setCursorPosition(this.mCursorCol, this.mCursorRow + this.getArg0(1))
            'g' -> when (this.getArg0(0)) {
                0 -> mTabStop[mCursorCol] = false
                3 -> {
                    var i = 0
                    while (i < this.mColumns) {
                        mTabStop[i] = false
                        i++
                    }
                }

                else -> {}
            }

            'h' -> this.doSetMode(true)
            'l' -> this.doSetMode(false)
            'm' -> this.selectGraphicRendition()
            'n' -> when (this.getArg0(0)) {
                5 -> {
                    // Answer is ESC [ 0 n (Terminal OK).
                    val dsr = byteArrayOf(
                        27.toByte(),
                        '['.code.toByte(),
                        '0'.code.toByte(),
                        'n'.code.toByte()
                    )
                    mSession.write(dsr, 0, dsr.size)
                }

                6 ->                         // Answer is ESC [ y ; x R, where x,y is
                    // the cursor location.
                    mSession.write(
                        String.format(
                            Locale.US,
                            "\u001b[%d;%dR",
                            this.mCursorRow + 1,
                            this.mCursorCol + 1
                        )
                    )

                else -> {}
            }

            'r' -> {
                // https://vt100.net/docs/vt510-rm/DECSTBM.html
                // The top margin defaults to 1, the bottom margin defaults to mRows.
                // The escape sequence numbers top 1..23, but we number top 0..22.
                // The escape sequence numbers bottom 2..24, and so do we (because we use a zero based numbering
                // scheme, but we store the first line below the bottom-most scrolling line.
                // As a result, we adjust the top line by -1, but we leave the bottom line alone.
                // Also require that top + 2 <= bottom.
                this.mTopMargin =
                    max(0.0, min((this.getArg0(1) - 1).toDouble(), (this.mRows - 2).toDouble()))
                        .toInt()
                this.mBottomMargin = max(
                    (this.mTopMargin + 2).toDouble(), min(
                        getArg1(this.mRows).toDouble(),
                        mRows.toDouble()
                    )
                )
                    .toInt()
                // DECSTBM moves the cursor to column 1, line 1 of the page respecting origin mode.
                this.setCursorPosition(0, 0)
            }

            's' -> if (this.isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                this.mLeftMargin =
                    min((this.getArg0(1) - 1).toDouble(), (this.mColumns - 2).toDouble())
                        .toInt()
                this.mRightMargin = max(
                    (this.mLeftMargin + 1).toDouble(), min(
                        getArg1(this.mColumns).toDouble(),
                        mColumns.toDouble()
                    )
                )
                    .toInt()
                // DECSLRM moves the cursor to column 1, line 1 of the page.
                this.setCursorPosition(0, 0)
            } else {
                // Save cursor (ANSI.SYS), available only when DECLRMM is disabled.
                this.saveCursor()
            }

            't' -> when (this.getArg0(0)) {
                11 -> mSession.write("\u001b[1t")
                13 -> mSession.write("\u001b[3;0;0t")
                14 -> mSession.write(
                    String.format(
                        Locale.US,
                        "\u001b[4;%d;%dt",
                        this.mRows * this.cellH,
                        this.mColumns * this.cellW
                    )
                )

                16 -> mSession.write(
                    String.format(
                        Locale.US,
                        "\u001b[6;%d;%dt",
                        this.cellH,
                        this.cellW
                    )
                )

                18 -> mSession.write(
                    String.format(
                        Locale.US,
                        "\u001b[8;%d;%dt",
                        this.mRows,
                        this.mColumns
                    )
                )

                19 ->                         // We report the same size as the view, since it's the view really isn't resizable from the shell.
                    mSession.write(
                        String.format(
                            Locale.US,
                            "\u001b[9;%d;%dt",
                            this.mRows,
                            this.mColumns
                        )
                    )

                20 -> mSession.write("\u001b]LIconLabel\u001b\\")
                21 -> mSession.write("\u001b]l\u001b\\")
//                22 -> {
//                    // 22;0 -> Save xterm icon and window title on stack.
//                    // 22;1 -> Save xterm icon title on stack.
//                    // 22;2 -> Save xterm window title on stack.
//                    mTitleStack.push(this.title)
//                    if (20 < mTitleStack.size) {
//                        // Limit size
//                        mTitleStack.removeAt(0)
//                    }
//                }
//
//                23 -> if (!mTitleStack.isEmpty()) this.title =
//                    mTitleStack.pop()

                else -> {}
            }

            'u' -> this.restoreCursor()
            ' ' -> this.continueSequence(ESC_CSI_ARGS_SPACE)
            else -> this.parseArg(b)
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see [...](http://en.wikipedia.org/wiki/ANSI_escape_code#graphics).
     */
    private fun selectGraphicRendition() {
        if (this.mArgIndex >= mArgs.size) this.mArgIndex =
            mArgs.size - 1
        var i = 0
        while (i <= this.mArgIndex) {
            var code = mArgs[i]
            if (0 > code) {
                if (0 < mArgIndex) {
                    i++
                    continue
                } else {
                    code = 0
                }
            }
            if (0 == code) {
                // reset
                this.mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
                this.mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
                this.mEffect = 0
            } else if (1 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_BOLD
            } else if (2 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_DIM
            } else if (3 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_ITALIC
            } else if (4 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
            } else if (5 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_BLINK
            } else if (7 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_INVERSE
            } else if (8 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
            } else if (9 == code) {
                this.mEffect = this.mEffect or TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH
            } else if (22 == code) {
                // Normal color or intensity, neither bright, bold nor faint.
                this.mEffect =
                    this.mEffect and (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_DIM).inv()
            } else if (23 == code) {
                // not italic, but rarely used as such; clears standout with TERM=screen
                this.mEffect = this.mEffect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC.inv()
            } else if (24 == code) {
                // underline: none
                this.mEffect = this.mEffect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE.inv()
            } else if (25 == code) {
                // blink: none
                this.mEffect = this.mEffect and TextStyle.CHARACTER_ATTRIBUTE_BLINK.inv()
            } else if (27 == code) {
                // image: positive
                this.mEffect = this.mEffect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE.inv()
            } else if (28 == code) {
                this.mEffect = this.mEffect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE.inv()
            } else if (29 == code) {
                this.mEffect = this.mEffect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH.inv()
            } else if (code in 30..37) {
                this.mForeColor = code - 30
            } else if (38 == code || 48 == code) {
                // Extended set foreground(38)/background (48) color.
                // This is followed by either "2;$R;$G;$B" to set a 24-bit color or
                // "5;$INDEX" to set an indexed color.
                if (i + 2 > this.mArgIndex) {
                    i++
                    continue
                }
                val firstArg = mArgs[i + 1]
                if (2 == firstArg) {
                    if (i + 4 <= this.mArgIndex) {
                        val red = mArgs[i + 2]
                        val green = mArgs[i + 3]
                        val blue = mArgs[i + 4]
                        if (0 > red || 0 > green || 0 > blue || 255 < red || 255 < green || 255 < blue) {
                            this.finishSequence()
                        } else {
                            val argbColor = -0x1000000 or (red shl 16) or (green shl 8) or blue
                            if (38 == code) {
                                this.mForeColor = argbColor
                            } else {
                                this.mBackColor = argbColor
                            }
                        }
                        // "2;P_r;P_g;P_r"
                        i += 4
                    }
                } else if (5 == firstArg) {
                    val color = mArgs[i + 2]
                    // "5;P_s"
                    i += 2
                    if (0 <= color && TextStyle.NUM_INDEXED_COLORS > color) {
                        if (38 == code) {
                            this.mForeColor = color
                        } else {
                            this.mBackColor = color
                        }
                    }
                } else {
                    this.finishSequence()
                }
            } else if (39 == code) {
                // Set default foreground color.
                this.mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
            } else if (code in 40..47) {
                // Set background color.
                this.mBackColor = code - 40
            } else if (49 == code) {
                // Set default background color.
                this.mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
            } else if (code in 90..97) {
                // Bright foreground colors (aixterm codes).
                this.mForeColor = code - 90 + 8
            } else if (code in 100..107) {
                // Bright background color (aixterm codes).
                this.mBackColor = code - 100 + 8
            }
            i++
        }
    }

    private fun doApc(b: Int) {
        when (b) {
            7 -> {}
            27 -> this.continueSequence(ESC_APC_ESC)
            else -> {
                this.collectOSCArgs(b)
                this.continueSequence(ESC_OSC)
            }
        }
    }

    private fun doApcEsc(b: Int) {
        if ('\\'.code != b) { // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            this.collectOSCArgs(27)
            this.collectOSCArgs(b)
            this.continueSequence(ESC_APC)
        }
    }

    private fun doOsc(b: Int) {
        when (b) {
            7 -> this.doOscSetTextParameters("\u0007")
            27 -> this.continueSequence(ESC_OSC_ESC)
            else -> {
                this.collectOSCArgs(b)
                if (-1 == ESC_OSC_colon && ':'.code == b) {
                    // Collect base64 data for OSC 1337
                    this.ESC_OSC_colon = mOSCOrDeviceControlArgs.length
                    this.ESC_OSC_data = ArrayList(65536)
                } else if (0 <= ESC_OSC_colon && 4 == mOSCOrDeviceControlArgs.length - ESC_OSC_colon) {
                    try {
                        val decoded = Base64.decode(
                            mOSCOrDeviceControlArgs.substring(this.ESC_OSC_colon), 0
                        )
                        for (value in decoded) {
                            ESC_OSC_data!!.add(value)
                        }
                    } catch (e: Exception) {
                        // Ignore non-Base64 data.
                    }
                    mOSCOrDeviceControlArgs.setLength(this.ESC_OSC_colon)
                }
            }
        }
    }

    private fun doOscEsc(b: Int) {
        if ('\\'.code == b) {
            this.doOscSetTextParameters("\u001b\\")
        } else { // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            this.collectOSCArgs(27)
            this.collectOSCArgs(b)
            this.continueSequence(ESC_OSC)
        }
    }

    /**
     * An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.
     */
    private fun doOscSetTextParameters(bellOrStringTerminator: String) {
        var value = -1
        val osc_colon = this.ESC_OSC_colon
        this.ESC_OSC_colon = -1
        var textParameter = ""
        // Extract initial $value from initial "$value;..." string.
        for (mOSCArgTokenizerIndex in mOSCOrDeviceControlArgs.indices) {
            val b = mOSCOrDeviceControlArgs[mOSCArgTokenizerIndex]
            if (';' == b) {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1)
                break
            } else if (b in '0'..'9') {
                value = (if ((0 > value)) 0 else value * 10) + (b.code - '0'.code)
            } else {
                this.finishSequence()
                return
            }
        }
        when (value) {
//            0, 1, 2 -> this.title = textParameter
            4 -> {
                // P s = 4 ; c ; spec → Change Color Number c to the color specified by spec. This can be a name or RGB
                // specification as per XParseColor. Any number of c name pairs may be given. The color numbers correspond
                // to the ANSI colors 0-7, their bright versions 8-15, and if supported, the remainder of the 88-color or
                // 256-color table.
                // If a "?" is given rather than a name or RGB specification, xterm replies with a control sequence of the
                // same form which can be used to set the corresponding color. Because more than one pair of color number
                // and specification can be given in one control sequence, xterm can make more than one reply.
                var colorIndex = -1
                var parsingPairStart = -1
                var i = 0
                while (true) {
                    val endOfInput = i == textParameter.length
                    val b = if (endOfInput) ';' else textParameter[i]
                    if (';' == b) {
                        if (0 > parsingPairStart) {
                            parsingPairStart = i + 1
                        } else {
                            if (0 > colorIndex || 255 < colorIndex) {
                                this.finishSequence()
                                return
                            } else {
                                mColors.tryParseColor(
                                    colorIndex,
                                    textParameter.substring(parsingPairStart, i)
                                )
                                colorIndex = -1
                                parsingPairStart = -1
                            }
                        }
                    } else if (0 > parsingPairStart && (b in '0'..'9')) {
                        colorIndex =
                            (if ((0 > colorIndex)) 0 else colorIndex * 10) + (b.code - '0'.code)
                    } else {
                        this.finishSequence()
                        return
                    }
                    if (endOfInput) break
                    i++
                }
            }

            10, 11, 12 -> {
                var specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10)
                var lastSemiIndex = 0
                var charIndex = 0
                while (true) {
                    val endOfInput = charIndex == textParameter.length
                    if (endOfInput || ';' == textParameter[charIndex]) {
                        try {
                            val colorSpec = textParameter.substring(lastSemiIndex, charIndex)
                            if ("?" == colorSpec) {
                                // Report current color in the same format xterm and gnome-terminal does.
                                val rgb = mColors.mCurrentColors[specialIndex]
                                val r = (65535 * ((rgb and 0x00FF0000) shr 16)) / 255
                                val g = (65535 * ((rgb and 0x0000FF00) shr 8)) / 255
                                val b = (65535 * ((rgb and 0x000000FF))) / 255
                                mSession.write(
                                    "\u001b]$value;rgb:" + String.format(
                                        Locale.US,
                                        "%04x",
                                        r
                                    ) + "/" + String.format(
                                        Locale.US, "%04x", g
                                    ) + "/" + String.format(
                                        Locale.US,
                                        "%04x",
                                        b
                                    ) + bellOrStringTerminator
                                )
                            } else {
                                mColors.tryParseColor(specialIndex, colorSpec)
                            }
                            specialIndex++
                            if (endOfInput || (TextStyle.COLOR_INDEX_CURSOR < specialIndex) || ++charIndex >= textParameter.length) break
                            lastSemiIndex = charIndex
                        } catch (e: NumberFormatException) {
                            // Ignore.
                        }
                    }
                    charIndex++
                }
            }

            52 -> {
                val startIndex = textParameter.indexOf(';') + 1
                try {
                    val clipboardText = String(
                        Base64.decode(textParameter.substring(startIndex), 0),
                        StandardCharsets.UTF_8
                    )
                    mSession.onCopyTextToClipboard(clipboardText)
                } catch (ignored: Exception) {
                }
            }

            104 ->                 // "104;$c" → Reset Color Number $c. It is reset to the color specified by the corresponding X
                // resource. Any number of c parameters may be given. These parameters correspond to the ANSI colors 0-7,
                // their bright versions 8-15, and if supported, the remainder of the 88-color or 256-color table. If no
                // parameters are given, the entire table will be reset.
                if (textParameter.isEmpty()) {
                    mColors.reset()
                } else {
                    var lastIndex = 0
                    var charIndex = 0
                    while (true) {
                        val endOfInput = charIndex == textParameter.length
                        if (endOfInput || ';' == textParameter[charIndex]) {
                            try {
                                val colorToReset =
                                    textParameter.substring(lastIndex, charIndex).toInt()
                                mColors.reset(colorToReset)
                                if (endOfInput) break
                                charIndex++
                                lastIndex = charIndex
                            } catch (e: NumberFormatException) {
                                // Ignore.
                            }
                        }
                        charIndex++
                    }
                }

            110, 111, 112 -> mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110))
            119 -> {}
            1337 -> if (textParameter.startsWith("File=")) {
                var pos = 5
                var inline = false
                var aspect = true
                var width = -1
                var height = -1
                while (pos < textParameter.length) {
                    val eqpos = textParameter.indexOf('=', pos)
                    if (-1 == eqpos) {
                        break
                    }
                    var semicolonpos = textParameter.indexOf(';', eqpos)
                    if (-1 == semicolonpos) {
                        semicolonpos = textParameter.length - 1
                    }
                    val k = textParameter.substring(pos, eqpos)
                    val v = textParameter.substring(eqpos + 1, semicolonpos)
                    pos = semicolonpos + 1
                    if ("inline".equals(k, ignoreCase = true)) {
                        inline = "1" == v
                    }
                    if ("preserveAspectRatio".equals(k, ignoreCase = true)) {
                        aspect = "0" != v
                    }
                    val percent = v.isNotEmpty() && '%' == v[v.length - 1]
                    if ("width".equals(k, ignoreCase = true)) {
                        var factor = cellW.toDouble()
                        // int div = 1;
                        var e = v.length
                        if (v.endsWith("px")) {
                            factor = 1.0
                            e -= 2
                        } else if (percent) {
                            factor = 0.01 * this.cellW * this.mColumns
                            e -= 1
                        }
                        try {
                            width = (factor * v.substring(0, e).toInt()).toInt()
                        } catch (ignored: Exception) {
                        }
                    }
                    if ("height".equals(k, ignoreCase = true)) {
                        var factor = cellH.toDouble()
                        //int div = 1;
                        var e = v.length
                        if (v.endsWith("px")) {
                            factor = 1.0
                            e -= 2
                        } else if (percent) {
                            factor = 0.01 * this.cellH * this.mRows
                            e -= 1
                        }
                        try {
                            height = (factor * v.substring(0, e).toInt()).toInt()
                        } catch (ignored: Exception) {
                        }
                    }
                }
                if (!inline) {
                    this.finishSequence()
                    return
                }
                if (0 <= osc_colon && mOSCOrDeviceControlArgs.length > osc_colon) {
                    while (4 > mOSCOrDeviceControlArgs.length - osc_colon) {
                        mOSCOrDeviceControlArgs.append('=')
                    }
                    try {
                        val decoded = Base64.decode(
                            mOSCOrDeviceControlArgs.substring(osc_colon), 0
                        )
                        for (b in decoded) {
                            ESC_OSC_data!!.add(b)
                        }
                    } catch (e: Exception) {
                        // Ignore non-Base64 data.
                    }
                    mOSCOrDeviceControlArgs.setLength(osc_colon)
                }
                if (0 <= osc_colon) {
                    val result = ByteArray(ESC_OSC_data!!.size)
                    var i = 0
                    while (i < ESC_OSC_data!!.size) {
                        result[i] = ESC_OSC_data!![i]
                        i++
                    }
                    val res = screen.addImage(
                        result,
                        this.mCursorRow,
                        this.mCursorCol,
                        this.cellW,
                        this.cellH,
                        width,
                        height,
                        aspect
                    )
                    var col = res[1] + this.mCursorCol
                    if (col < this.mColumns - 1) {
                        res[0] -= 1
                    } else {
                        col = 0
                    }
                    while (0 < res[0]) {
                        this.doLinefeed()
                        res[0]--
                    }
                    this.mCursorCol = col
                    ESC_OSC_data!!.clear()
                }
            } else if (textParameter.startsWith("ReportCellSize")) {
                mSession.write(
                    String.format(
                        Locale.US,
                        "\u001b1337;ReportCellSize=%d;%d\u0007",
                        this.cellH,
                        this.cellW
                    )
                )
            }

            else -> this.finishSequence()
        }
        this.finishSequence()
    }

    private fun blockClear(sx: Int, sy: Int, w: Int, h: Int = 1) {
        screen.blockSet(sx, sy, w, h, ' '.code, this.style)
    }

    private val style: Long
        get() = TextStyle.encode(this.mForeColor, this.mBackColor, this.mEffect)

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private fun doSetMode(newValue: Boolean) {
        val modeBit = this.getArg0(0)
        when (modeBit) {
            4 -> this.mInsertMode = newValue
            34 -> {}
            else -> this.finishSequence()
        }
    }

    /**
     * NOTE: The parameters of this function respect the [.DECSET_BIT_ORIGIN_MODE]. Use
     * [.setCursorRowCol] for absolute pos.
     */
    private fun setCursorPosition(x: Int, y: Int) {
        val originMode = this.isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
        val effectiveTopMargin = if (originMode) this.mTopMargin else 0
        val effectiveBottomMargin = if (originMode) this.mBottomMargin else this.mRows
        val effectiveLeftMargin = if (originMode) this.mLeftMargin else 0
        val effectiveRightMargin = if (originMode) this.mRightMargin else this.mColumns
        val newRow = max(
            effectiveTopMargin.toDouble(),
            min((effectiveTopMargin + y).toDouble(), (effectiveBottomMargin - 1).toDouble())
        ).toInt()
        val newCol = max(
            effectiveLeftMargin.toDouble(),
            min((effectiveLeftMargin + x).toDouble(), (effectiveRightMargin - 1).toDouble())
        ).toInt()
        this.setCursorRowCol(newRow, newCol)
    }

    private fun scrollDownOneLine() {
        scrollCounter++
        if (0 != mLeftMargin || this.mRightMargin != this.mColumns) {
            // Horizontal margin: Do not put anything into scroll history, just non-margin part of screen up.
            screen.blockCopy(
                this.mLeftMargin,
                this.mTopMargin + 1,
                this.mRightMargin - this.mLeftMargin,
                this.mBottomMargin - this.mTopMargin - 1,
                this.mLeftMargin,
                this.mTopMargin
            )
            // .. and blank bottom row between margins:
            screen.blockSet(
                this.mLeftMargin,
                this.mBottomMargin - 1,
                this.mRightMargin - this.mLeftMargin,
                1,
                ' '.code,
                mEffect.toLong()
            )
        } else {
            screen.scrollDownOneLine(
                this.mTopMargin, this.mBottomMargin,
                style
            )
        }
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     *
     * Parameter characters modify the action or interpretation of the sequence. You can use up to
     * 16 parameters per sequence. You must use the ; character to separate parameters.
     * All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     *
     * [* https://vt100.net/docs/vt510-rm/chapter4.htm](
      )l#S4.3.3
     */
    private fun parseArg(inputByte: Int) {
        val bytes = this.getInts(inputByte)
        this.mIsCSIStart = false
        for (b in bytes) {
            if ('0'.code <= b && '9'.code >= b) {
                if (this.mArgIndex < mArgs.size) {
                    val oldValue = mArgs[mArgIndex]
                    val thisDigit = b - '0'.code
                    var value: Int
                    value = if (0 <= oldValue) {
                        oldValue * 10 + thisDigit
                    } else {
                        thisDigit
                    }
                    if (9999 < value) value = 9999
                    mArgs[mArgIndex] = value
                }
                this.continueSequence(this.mEscapeState)
            } else if (';'.code == b) {
                if (this.mArgIndex < mArgs.size) {
                    mArgIndex++
                }
                this.continueSequence(this.mEscapeState)
            } else {
                this.finishSequence()
            }
            this.mLastCSIArg = b
        }
    }

    private fun getInts(inputByte: Int): IntArray {
        var bytes = intArrayOf(inputByte)
        // Only doing this for ESC_CSI and not for other ESC_CSI_* since they seem to be using their
        // own defaults with getArg*() calls, but there may be missed cases
        if (ESC_CSI == mEscapeState) {
            if ( // If sequence starts with a ; character, like \033[;m
                (this.mIsCSIStart && ';'.code == inputByte) || (!this.mIsCSIStart && null != mLastCSIArg && ';'.code == mLastCSIArg && ';'.code == inputByte)) {
                // If sequence contains sequential ; characters, like \033[;;m
                // Assume 0 was passed
                bytes = intArrayOf('0'.code, ';'.code)
            }
        }
        return bytes
    }

    private fun getArg0(defaultValue: Int): Int {
        return this.getArg(0, defaultValue, true)
    }

    private fun getArg1(defaultValue: Int): Int {
        return this.getArg(1, defaultValue, true)
    }

    private fun getArg(index: Int, defaultValue: Int, treatZeroAsDefault: Boolean): Int {
        var result = mArgs[index]
        if (0 > result || (0 == result && treatZeroAsDefault)) {
            result = defaultValue
        }
        return result
    }

    private fun collectOSCArgs(b: Int) {
        if (MAX_OSC_STRING_LENGTH > mOSCOrDeviceControlArgs.length) {
            mOSCOrDeviceControlArgs.appendCodePoint(b)
            this.continueSequence(this.mEscapeState)
        } else {
            this.finishSequence()
        }
    }

    private fun finishSequence() {
        this.mEscapeState = ESC_NONE
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private fun emitCodePoint(codePoint: Int) {
        var codePoint = codePoint
        this.mLastEmittedCodePoint = codePoint
        if (if (this.mUseLineDrawingUsesG0) this.mUseLineDrawingG0 else this.mUseLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            when (codePoint.toChar()) {
                '_' ->                     // Blank.
                    codePoint = ' '.code

                '`' ->                     // Diamond.
                    codePoint = '◆'.code

                '0' ->                     // Solid block;
                    codePoint = '█'.code

                'a' ->                     // Checker board.
                    codePoint = '▒'.code

                'b' ->                     // Horizontal tab.
                    codePoint = '␉'.code

                'c' ->                     // Form feed.
                    codePoint = '␌'.code

                'd' ->                     // Carriage return.
                    codePoint = '\r'.code

                'e' ->                     // Linefeed.
                    codePoint = '␊'.code

                'f' ->                     // Degree.
                    codePoint = '°'.code

                'g' ->                     // Plus-minus.
                    codePoint = '±'.code

                'h' ->                     // Newline.
                    codePoint = '\n'.code

                'i' ->                     // Vertical tab.
                    codePoint = '␋'.code

                'j' ->                     // Lower right corner.
                    codePoint = '┘'.code

                'k' ->                     // Upper right corner.
                    codePoint = '┐'.code

                'l' ->                     // Upper left corner.
                    codePoint = '┌'.code

                'm' ->                     // Left left corner.
                    codePoint = '└'.code

                'n' ->                     // Crossing lines.
                    codePoint = '┼'.code

                'o' ->                     // Horizontal line - scan 1.
                    codePoint = '⎺'.code

                'p' ->                     // Horizontal line - scan 3.
                    codePoint = '⎻'.code

                'q' ->                     // Horizontal line - scan 5.
                    codePoint = '─'.code

                'r' ->                     // Horizontal line - scan 7.
                    codePoint = '⎼'.code

                's' ->                     // Horizontal line - scan 9.
                    codePoint = '⎽'.code

                't' ->                     // T facing rightwards.
                    codePoint = '├'.code

                'u' ->                     // T facing leftwards.
                    codePoint = '┤'.code

                'v' ->                     // T facing upwards.
                    codePoint = '┴'.code

                'w' ->                     // T facing downwards.
                    codePoint = '┬'.code

                'x' ->                     // Vertical line.
                    codePoint = '│'.code

                'y' ->                     // Less than or equal to.
                    codePoint = '≤'.code

                'z' ->                     // Greater than or equal to.
                    codePoint = '≥'.code

                '{' ->                     // Pi.
                    codePoint = 'π'.code

                '|' ->                     // Not equal to.
                    codePoint = '≠'.code

                '}' ->                     // UK pound.
                    codePoint = '£'.code

                '~' ->                     // Centered dot.
                    codePoint = '·'.code
            }
        }
        val autoWrap = this.isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP)
        val displayWidth = WcWidth.width(codePoint)
        val cursorInLastColumn = this.mCursorCol == this.mRightMargin - 1
        if (autoWrap) {
            if (cursorInLastColumn && ((this.mAboutToAutoWrap && 1 == displayWidth) || 2 == displayWidth)) {
                screen.setLineWrap(this.mCursorRow)
                this.mCursorCol = this.mLeftMargin
                if (this.mCursorRow + 1 < this.mBottomMargin) {
                    mCursorRow++
                } else {
                    this.scrollDownOneLine()
                }
            }
        } else if (cursorInLastColumn && 2 == displayWidth) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return
        }
        if (this.mInsertMode && 0 < displayWidth) {
            // Move character to right one space.
            val destCol = this.mCursorCol + displayWidth
            if (destCol < this.mRightMargin) screen.blockCopy(
                this.mCursorCol,
                this.mCursorRow,
                this.mRightMargin - destCol,
                1,
                destCol,
                this.mCursorRow
            )
        }
        val column = this.getColumn(displayWidth)
        screen.setChar(
            column, this.mCursorRow, codePoint,
            style
        )
        if (autoWrap && 0 < displayWidth) this.mAboutToAutoWrap =
            (this.mCursorCol == this.mRightMargin - displayWidth)
        this.mCursorCol =
            min((this.mCursorCol + displayWidth).toDouble(), (this.mRightMargin - 1).toDouble())
                .toInt()
    }

    private fun getColumn(displayWidth: Int): Int {
        val offsetDueToCombiningChar =
            (if ((0 >= displayWidth && 0 < mCursorCol && !this.mAboutToAutoWrap)) 1 else 0)
        var column = this.mCursorCol - offsetDueToCombiningChar
        // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported
        // The offsetDueToCombiningChar would never be 1 if mCursorCol was 0 to get column/index=-1,
        // so was mCursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow, possibly causing others bugs too.
        if (0 > column) column = 0
        return column
    }

    /**
     * Set the cursor mode, but limit it to margins if [.DECSET_BIT_ORIGIN_MODE] is enabled.
     */
    private fun setCursorColRespectingOriginMode(col: Int) {
        this.setCursorPosition(col, this.mCursorRow)
    }

    /**
     * TODO: Better name, distinguished from [.setCursorPosition] by not regarding origin mode.
     */
    private fun setCursorRowCol(row: Int, col: Int) {
        this.mCursorRow = max(0.0, min(row.toDouble(), (this.mRows - 1).toDouble()))
            .toInt()
        this.mCursorCol = max(
            0.0,
            min(col.toDouble(), (this.mColumns - 1).toDouble())
        ).toInt()
        this.mAboutToAutoWrap = false
    }

    fun clearScrollCounter() {
        this.scrollCounter = 0
    }


    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    private fun reset() {
        this.mArgIndex = 0
        this.mContinueSequence = false
        this.mEscapeState = ESC_NONE
        this.mInsertMode = false
        this.mLeftMargin = 0
        this.mTopMargin = this.mLeftMargin
        this.mBottomMargin = this.mRows
        this.mRightMargin = this.mColumns
        this.mAboutToAutoWrap = false
        mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND
        mSavedStateMain.mSavedForeColor = mSavedStateAlt.mSavedForeColor
        this.mForeColor = mSavedStateMain.mSavedForeColor
        mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND
        mSavedStateMain.mSavedBackColor = mSavedStateAlt.mSavedBackColor
        this.mBackColor = mSavedStateMain.mSavedBackColor
        this.setDefaultTabStops()
        this.mUseLineDrawingG1 = false
        this.mUseLineDrawingG0 = this.mUseLineDrawingG1
        this.mUseLineDrawingUsesG0 = true
        mSavedStateMain.mSavedDecFlags = 0
        mSavedStateMain.mSavedEffect = 0
        mSavedStateMain.mSavedCursorCol = 0
        mSavedStateMain.mSavedCursorRow = 0
        mSavedStateAlt.mSavedDecFlags = 0
        mSavedStateAlt.mSavedEffect = 0
        mSavedStateAlt.mSavedCursorCol = 0
        mSavedStateAlt.mSavedCursorRow = 0
        this.mCurrentDecSetFlags = 0
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        this.setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true)
        this.setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true)
        mSavedStateAlt.mSavedDecFlags = this.mCurrentDecSetFlags
        mSavedStateMain.mSavedDecFlags = mSavedStateAlt.mSavedDecFlags
        this.mSavedDecSetFlags = mSavedStateMain.mSavedDecFlags
        // XXX: Should we set terminal driver back to IUTF8 with termios?
        this.mUtf8ToFollow = 0
        this.mUtf8Index = this.mUtf8ToFollow
        mColors.reset()
        this.ESC_P_escape = false
        this.ESC_P_sixel = false
        this.ESC_OSC_colon = -1
    }

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return screen.getSelectedText(x1, y1, x2, y2)
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    fun paste(text: String?) {
        // First: Always remove escape key and C1 control characters [0x80,0x9F]:
        var text = text
        text = REGEX.matcher(text).replaceAll("")
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        text = PATTERN.matcher(text).replaceAll("\r")
        // Then: Implement bracketed paste mode if enabled:
        val bracketed = this.isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE)
        if (bracketed) mSession.write("\u001b[200~")
        mSession.write(text)
        if (bracketed) mSession.write("\u001b[201~")
    }

    /**
     * [...](http://www.vt100.net/docs/vt510-rm/DECSC)
     */
    internal class SavedScreenState {
        /**
         * Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences.
         */
        var mSavedCursorRow: Int = 0
        var mSavedCursorCol: Int = 0

        var mSavedEffect: Int = 0
        var mSavedForeColor: Int = 0
        var mSavedBackColor: Int = 0

        var mSavedDecFlags: Int = 0

        var mUseLineDrawingG0: Boolean = false
        var mUseLineDrawingG1: Boolean = false
        var mUseLineDrawingUsesG0: Boolean = true
    }

    companion object {
        const val MOUSE_LEFT_BUTTON: Int = 0

        /**
         * Mouse moving while having left mouse button pressed.
         */
        const val MOUSE_LEFT_BUTTON_MOVED: Int = 32

        const val MOUSE_WHEELUP_BUTTON: Int = 64

        const val MOUSE_WHEELDOWN_BUTTON: Int = 65

        /**
         * Used for invalid data - [...](http://en.wikipedia.org/wiki/Replacement_character#Replacement_character)
         */
        const val UNICODE_REPLACEMENT_CHAR: Int = 0xFFFD

        /* The supported terminal cursor styles. */
        const val TERMINAL_CURSOR_STYLE_BLOCK: Int = 0
        const val TERMINAL_CURSOR_STYLE_UNDERLINE: Int = 1
        const val TERMINAL_CURSOR_STYLE_BAR: Int = 2

        /**
         * Escape processing: Not currently in an escape sequence.
         */
        private const val ESC_NONE = 0

        /**
         * Escape processing: Have seen an ESC character - proceed to [.doEsc]
         */
        private const val ESC = 1

        /**
         * Escape processing: Have seen ESC POUND
         */
        private const val ESC_POUND = 2

        /**
         * Escape processing: Have seen ESC and a character-set-select ( char
         */
        private const val ESC_SELECT_LEFT_PAREN = 3

        /**
         * Escape processing: Have seen ESC and a character-set-select ) char
         */
        private const val ESC_SELECT_RIGHT_PAREN = 4

        /**
         * Escape processing: "ESC [" or CSI (Control Sequence Introducer).
         */
        private const val ESC_CSI = 6

        /**
         * Escape processing: ESC [ ?
         */
        private const val ESC_CSI_QUESTIONMARK = 7

        /**
         * Escape processing: ESC [ $
         */
        private const val ESC_CSI_DOLLAR = 8

        /**
         * Escape processing: ESC %
         */
        private const val ESC_PERCENT = 9

        /**
         * Escape processing: ESC ] (AKA OSC - Operating System Controls)
         */
        private const val ESC_OSC = 10

        /**
         * Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC
         */
        private const val ESC_OSC_ESC = 11

        /**
         * Escape processing: ESC [ >
         */
        private const val ESC_CSI_BIGGERTHAN = 12

        /**
         * Escape procession: "ESC P" or Device Control String (DCS)
         */
        private const val ESC_P = 13

        /**
         * Escape processing: CSI >
         */
        private const val ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14

        /**
         * Escape processing: CSI $ARGS ' '
         */
        private const val ESC_CSI_ARGS_SPACE = 15

        /**
         * Escape processing: CSI $ARGS '*'
         */
        private const val ESC_CSI_ARGS_ASTERIX = 16

        /**
         * Escape processing: CSI "
         */
        private const val ESC_CSI_DOUBLE_QUOTE = 17

        /**
         * Escape processing: CSI '
         */
        private const val ESC_CSI_SINGLE_QUOTE = 18

        /**
         * Escape processing: CSI !
         */
        private const val ESC_CSI_EXCLAMATION = 19

        /**
         * Escape processing: APC
         */
        private const val ESC_APC = 20
        private const val ESC_APC_ESC = 21

        /**
         * The number of parameter arguments. This name comes from the ANSI standard for terminal escape codes.
         */
        private const val MAX_ESCAPE_PARAMETERS = 16

        /**
         * Needs to be large enough to contain reasonable OSC 52 pastes.
         */
        private const val MAX_OSC_STRING_LENGTH = 8192

        /**
         * DECSET 1 - application cursor keys.
         */
        private const val DECSET_BIT_APPLICATION_CURSOR_KEYS = 1
        private const val DECSET_BIT_REVERSE_VIDEO = 1 shl 1

        /**
         * [...](http://www.vt100.net/docs/vt510-rm/DECOM): "When DECOM is set, the home cursor position is at the upper-left
         * corner of the screen, within the margins. The starting point for line numbers depends on the current top margin
         * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
         * upper-left corner of the screen. The starting point for line numbers is independent of the margins. The cursor
         * can move outside of the margins."
         */
        private const val DECSET_BIT_ORIGIN_MODE = 1 shl 2

        /**
         * [...](http://www.vt100.net/docs/vt510-rm/DECAWM): "If the DECAWM function is set, then graphic characters received when
         * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
         * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
         * characters received when the cursor is at the right border of the page replace characters already on the page."
         */
        private const val DECSET_BIT_AUTOWRAP = 1 shl 3

        /**
         * DECSET 25 - if the cursor should be enabled, [.isCursorEnabled].
         */
        private const val DECSET_BIT_CURSOR_ENABLED = 1 shl 4
        private const val DECSET_BIT_APPLICATION_KEYPAD = 1 shl 5

        /**
         * DECSET 1000 - if to report mouse press&release events.
         */
        private const val DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 shl 6

        /**
         * DECSET 1002 - like 1000, but report moving mouse while pressed.
         */
        private const val DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 shl 7

        /**
         * DECSET 1004 - NOT implemented.
         */
        private const val DECSET_BIT_SEND_FOCUS_EVENTS = 1 shl 8

        /**
         * DECSET 1006 - SGR-like mouse protocol (the modern sane choice).
         */
        private const val DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 shl 9

        /**
         * DECSET 2004 - see [.paste]
         */
        private const val DECSET_BIT_BRACKETED_PASTE_MODE = 1 shl 10

        /**
         * Toggled with DECLRMM - [...](http://www.vt100.net/docs/vt510-rm/DECLRMM)
         */
        private const val DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 shl 11

        /**
         * Not really DECSET bit... - [...](http://www.vt100.net/docs/vt510-rm/DECSACE)
         */
        private const val DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 shl 12
        private val PATTERN: Pattern = Pattern.compile("\r?\n")
        private val REGEX: Pattern = Pattern.compile("(\u001B|[\u0080-\u009F])")
        private val REGEXP: Pattern = Pattern.compile("[0-9;]*q.*")
        private fun mapDecSetBitToInternalBit(decsetBit: Int): Int {
            return when (decsetBit) {
                1 -> DECSET_BIT_APPLICATION_CURSOR_KEYS
                5 -> DECSET_BIT_REVERSE_VIDEO
                6 -> DECSET_BIT_ORIGIN_MODE
                7 -> DECSET_BIT_AUTOWRAP
                25 -> DECSET_BIT_CURSOR_ENABLED
                66 -> DECSET_BIT_APPLICATION_KEYPAD
                69 -> DECSET_BIT_LEFTRIGHT_MARGIN_MODE
                1000 -> DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE
                1002 -> DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
                1004 -> DECSET_BIT_SEND_FOCUS_EVENTS
                1006 -> DECSET_BIT_MOUSE_PROTOCOL_SGR
                2004 -> DECSET_BIT_BRACKETED_PASTE_MODE
                else -> -1
            }
        }
    }
}
