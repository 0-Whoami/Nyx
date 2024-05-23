package com.termux.terminal

import android.util.Base64
import com.termux.terminal.KeyHandler.getCodeFromTermcap
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BLINK
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BOLD
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_DIM
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVERSE
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_ITALIC
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_PROTECTED
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
import com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND
import com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR
import com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND
import com.termux.terminal.TextStyle.NUM_INDEXED_COLORS
import com.termux.terminal.TextStyle.decodeEffect
import com.termux.terminal.TextStyle.encode
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

/**
 * Renders text into a console. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 */
class TerminalEmulator(
    private val mSession: TerminalSession, var mColumns: Int, var mRows: Int, transcriptRows: Int
) {
    val mColors: TerminalColors = TerminalColors()

    /**
     * The normal console buffer. Stores the characters that appear on the console of the emulated terminal.
     */
    private val mMainBuffer: TerminalBuffer = TerminalBuffer(mColumns, transcriptRows, mRows)

    /**
     * The alternate console buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate console buffer is active, you cannot scroll back to view saved lines).
     */
    private val mAltBuffer: TerminalBuffer = TerminalBuffer(mColumns, mRows, mRows)

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

    private val mUtf8InputBuffer = ByteArray(4)

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
     * The current console buffer, pointing at either [.mMainBuffer] or [.mAltBuffer].
     */
    var screen: TerminalBuffer = mMainBuffer
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
     * An array of tab stops. mTabStop is true if there is a tab stop set for column i.
     */
    private var mTabStop: BooleanArray = BooleanArray(mColumns)

    /**
     * Top margin of console for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
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
     * see TextStyle
     */
    private var mForeColor = 0
    private var mBackColor = 0

    /**
     * Current TextStyle effect.
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

    init {
        reset()
    }

    private fun isDecsetInternalBitSet(bit: Int) = (mCurrentDecSetFlags and bit) != 0


    private fun setDecsetinternalBit(internalBit: Int, set: Boolean) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false)
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


    val isAlternateBufferActive: Boolean
        get() = screen == mAltBuffer

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    fun sendMouseEvent(mouseButton: Int, column: Int, row: Int, pressed: Boolean) {
        val column1 = if (1 > column) 1 else if (column > mColumns) mColumns else column
        val row1 = if (1 > row) 1 else if (row > mRows) mRows else row

        if (!(MOUSE_LEFT_BUTTON_MOVED == mouseButton && !isDecsetInternalBitSet(
                DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
            )) && isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)
        ) {
            mSession.write(
                String.format(
                    Locale.US,
                    "\u001b[<%d;%d;%d" + (if (pressed) 'M' else 'm'),
                    mouseButton,
                    column1,
                    row1
                )
            )
        } else {
            // 3 for release of all buttons.
            val mouseButton1 = if (pressed) mouseButton else 3
            // Clip to console, and clip to the limits of 8-bit data.
            val outOfBounds = 255 - 32 < column1 || 255 - 32 < row1
            if (!outOfBounds) {
                val data = byteArrayOf(
                    '\u001b'.code.toByte(),
                    '['.code.toByte(),
                    'M'.code.toByte(),
                    (32 + mouseButton1).toByte(),
                    (32 + column1).toByte(),
                    (32 + row1).toByte()
                )
                mSession.write(data, 0, data.size)
            }
        }
    }

    fun resize(columns: Int, rows: Int) {
        if (mRows != rows) {
            mRows = rows
            mTopMargin = 0
            mBottomMargin = mRows
        }
        if (mColumns != columns) {
            val oldColumns = mColumns
            mColumns = columns
            val oldTabStop = mTabStop
            mTabStop = BooleanArray(mColumns)
            setDefaultTabStops()
            val toTransfer = min(oldColumns, columns)
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer)
            mLeftMargin = 0
            mRightMargin = mColumns
        }
        resizeScreen()
    }

    private fun resizeScreen() {
        val cursor = intArrayOf(mCursorCol, mCursorRow)
        val newTotalRows = if (isAlternateBufferActive) mRows else mMainBuffer.mTotalRows
        screen.resize(
            mColumns, mRows, newTotalRows, cursor, style, isAlternateBufferActive
        )
        mCursorCol = cursor[0]
        mCursorRow = cursor[1]
    }

    var cursorRow: Int
        get() = mCursorRow
        private set(row) {
            mCursorRow = row
            mAboutToAutoWrap = false
        }

    var cursorCol: Int
        get() = mCursorCol
        private set(col) {
            mCursorCol = col
            mAboutToAutoWrap = false
        }


    val isReverseVideo: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO)

    private val isCursorDisabled: Boolean
        get() = !isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED)

    fun shouldCursorBeVisible(): Boolean {
        return if (isCursorDisabled) false
        else !mCursorBlinkingEnabled || mCursorBlinkState
    }

    fun setCursorBlinkState(cursorBlinkState: Boolean) {
        mCursorBlinkState = cursorBlinkState
    }

    val isKeypadApplicationMode: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)

    val isCursorKeysApplicationMode: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS)

    val isMouseTrackingActive: Boolean
        /**
         * If mouse events are being sent as escape codes to the terminal.
         */
        get() = isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(
            DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
        )

    private fun setDefaultTabStops() {
        for (i in 0 until mColumns) mTabStop[i] = 0 == (i and 7) && 0 != i
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    fun append(buffer: ByteArray, length: Int) {
        for (i in 0 until length) processByte(buffer[i])
    }

    /** Called after getting data from session*/
    private fun processByte(byteToProcess: Byte) {
        if (0 < mUtf8ToFollow) {
            if (128.toByte() == (byteToProcess and 192.toByte())) {
                // 10xxxxxx, a continuation byte.
                mUtf8InputBuffer[mUtf8Index++.toInt()] = byteToProcess
                if (0.toByte() == --mUtf8ToFollow) {
                    val firstByteMask =
                        (if (2.toByte() == mUtf8Index) 31 else (if (3.toByte() == mUtf8Index) 15 else 7)).toByte()
                    var codePoint = (mUtf8InputBuffer[0] and firstByteMask).toInt()
                    for (i in 1.toByte() until mUtf8Index) codePoint =
                        ((codePoint shl 6) or (mUtf8InputBuffer[i] and 63.toByte()).toInt())
                    if (((127 >= codePoint) && 1 < mUtf8Index) || (2047 > codePoint && 2 < mUtf8Index) || (65535 > codePoint && 3 < mUtf8Index)) {
                        // Overlong encoding.
                        codePoint = UNICODE_REPLACEMENT_CHAR
                    }
                    mUtf8Index = 0
                    mUtf8ToFollow = 0
                    if (0x80 > codePoint || 0x9F < codePoint) {
                        codePoint = when (Character.getType(codePoint).toByte()) {
                            Character.UNASSIGNED, Character.SURROGATE -> UNICODE_REPLACEMENT_CHAR
                            else -> codePoint
                        }
                        processCodePoint(codePoint)
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                mUtf8ToFollow = 0
                mUtf8Index = 0
                emitCodePoint(UNICODE_REPLACEMENT_CHAR)

                processByte(byteToProcess)
            }
        } else {
            val byteToProcessInt = byteToProcess.toInt()
            if (0 == (byteToProcessInt and 128)) {
                // The leading bit is not set so it is a 7-bit ASCII character.
                processCodePoint(byteToProcessInt)
                return
            } else if (192 == (byteToProcessInt and 224)) {
                // 110xxxxx, a two-byte sequence.
                mUtf8ToFollow = 1
            } else if (224 == (byteToProcessInt and 240)) {
                // 1110xxxx, a three-byte sequence.
                mUtf8ToFollow = 2
            } else if (240 == (byteToProcessInt and 248)) {
                // 11110xxx, a four-byte sequence.
                mUtf8ToFollow = 3
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                processCodePoint(UNICODE_REPLACEMENT_CHAR)
                return
            }
            mUtf8InputBuffer[mUtf8Index++.toInt()] = byteToProcess
        }
    }

    private fun processCodePoint(b: Int) {
        when (b) {
            0 -> {}
            7 -> if (ESC_OSC == mEscapeState) doOsc(b)

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

            9 -> mCursorCol = nextTabStop(1)

            10, 11, 12 -> doLinefeed()

            13 -> cursorCol = mLeftMargin

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
                    return
                } else if (ESC_OSC != mEscapeState) {
                    startEscapeSequence()
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
                                val topSource =
                                    min(getArg(0, 1, true) - 1 + effectiveTopMargin, mRows)
                                val leftSource =
                                    min(getArg(1, 1, true) - 1 + effectiveLeftMargin, mColumns)
                                // Inclusive, so do not subtract one:
                                val bottomSource = min(
                                    max(getArg(2, mRows, true) + effectiveTopMargin, topSource),
                                    mRows
                                )
                                val rightSource = min(
                                    max(
                                        getArg(3, mColumns, true) + effectiveLeftMargin, leftSource
                                    ), mColumns
                                )
                                // int sourcePage = getArg(4, 1, true);
                                val destionationTop =
                                    min(getArg(5, 1, true) - 1 + effectiveTopMargin, mRows)
                                val destinationLeft =
                                    min(getArg(6, 1, true) - 1 + effectiveLeftMargin, mColumns)
                                // int destinationPage = getArg(7, 1, true);
                                val heightToCopy =
                                    min(mRows - destionationTop, bottomSource - topSource)
                                val widthToCopy =
                                    min(mColumns - destinationLeft, rightSource - leftSource)
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
                                val fillChar = if (erase) ' '.code else getArg(argIndex++, -1, true)

                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if ((fillChar in 32..126) || (fillChar in 160..255)) {
                                    // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    val top = min(
                                        getArg(argIndex++, 1, true) + effectiveTopMargin,
                                        effectiveBottomMargin + 1
                                    )
                                    val left = min(
                                        getArg(argIndex++, 1, true) + effectiveLeftMargin,
                                        effectiveRightMargin + 1
                                    )
                                    val bottom = min(
                                        getArg(argIndex++, mRows, true) + effectiveTopMargin,
                                        effectiveBottomMargin
                                    )
                                    val right = min(
                                        getArg(argIndex, mColumns, true) + effectiveLeftMargin,
                                        effectiveRightMargin
                                    )
                                    for (row in top - 1 until bottom) {
                                        for (col in left - 1 until right) {
                                            if (!selective || 0 == (decodeEffect(
                                                    screen.getStyleAt(row, col)
                                                ) and CHARACTER_ATTRIBUTE_PROTECTED)
                                            ) screen.setChar(
                                                col,
                                                row,
                                                fillChar,
                                                if (keepVisualAttributes) screen.getStyleAt(
                                                    row, col
                                                ) else style
                                            )
                                        }
                                    }
                                }
                            }

                            'r', 't' -> {
                                // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                val reverse = 't'.code == b
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                val top =
                                    min(getArg0(1) - 1, effectiveBottomMargin) + effectiveTopMargin
                                val left =
                                    min(getArg1(1) - 1, effectiveRightMargin) + effectiveLeftMargin
                                val bottom = min(
                                    getArg(2, mRows, true) + 1, effectiveBottomMargin - 1
                                ) + effectiveTopMargin
                                val right = min(
                                    getArg(3, mColumns, true) + 1, effectiveRightMargin - 1
                                ) + effectiveLeftMargin
                                if (4 <= mArgIndex) {
                                    if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                                    for (i in 4..mArgIndex) {
                                        var bits = 0
                                        // True if setting, false if clearing.
                                        var setOrClear = true
                                        when (getArg(i, 0, false)) {
                                            0 -> {
                                                bits =
                                                    CHARACTER_ATTRIBUTE_BOLD or CHARACTER_ATTRIBUTE_UNDERLINE or CHARACTER_ATTRIBUTE_BLINK or CHARACTER_ATTRIBUTE_INVERSE
                                                if (!reverse) setOrClear = false
                                            }

                                            1 -> bits = CHARACTER_ATTRIBUTE_BOLD
                                            4 -> bits = CHARACTER_ATTRIBUTE_UNDERLINE
                                            5 -> bits = CHARACTER_ATTRIBUTE_BLINK
                                            7 -> bits = CHARACTER_ATTRIBUTE_INVERSE
                                            22 -> {
                                                bits = CHARACTER_ATTRIBUTE_BOLD
                                                setOrClear = false
                                            }

                                            24 -> {
                                                bits = CHARACTER_ATTRIBUTE_UNDERLINE
                                                setOrClear = false
                                            }

                                            25 -> {
                                                bits = CHARACTER_ATTRIBUTE_BLINK
                                                setOrClear = false
                                            }

                                            27 -> {
                                                bits = CHARACTER_ATTRIBUTE_INVERSE
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
                                    }
                                }
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
                                mEffect = mEffect and CHARACTER_ATTRIBUTE_PROTECTED.inv()
                            }

                            1 -> {
                                // DECSED and DECSEL cannot erase characters.
                                mEffect = mEffect or CHARACTER_ATTRIBUTE_PROTECTED
                            }

                            else -> {
                                finishSequence()
                            }
                        }
                    } else {
                        finishSequence()
                    }

                    ESC_CSI_SINGLE_QUOTE -> when (b) {
                        '}'.code -> {
                            // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                            val columnsAfterCursor = mRightMargin - mCursorCol
                            val columnsToInsert = min(getArg0(1), columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToInsert
                            screen.blockCopy(
                                mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0
                            )
                            blockClear(mCursorCol, 0, columnsToInsert, mRows)
                        }

                        '~'.code -> {
                            // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                            val columnsAfterCursor = mRightMargin - mCursorCol
                            val columnsToDelete = min(getArg0(1), columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToDelete
                            screen.blockCopy(
                                mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0
                            )
                        }

                        else -> finishSequence()
                    }

                    9 -> {}
                    ESC_OSC -> doOsc(b)
                    ESC_OSC_ESC -> doOscEsc(b)
                    ESC_P -> doDeviceControl(b)
                    ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> if ('p'.code == b) {
                        // Request DEC private mode (DECRQM).
                        val mode = getArg0(0)
                        val value = getValues(mode)
                        mSession.write(
                            String.format(
                                Locale.US, "\u001b[?%d;%d\$y", mode, value
                            )
                        )
                    } else {
                        finishSequence()
                    }

                    ESC_CSI_ARGS_SPACE -> {
                        val arg = getArg0(0)
                        when (b.toChar()) {
                            'q' -> when (arg) {
                                0, 1, 2 -> {
                                    cursorStyle = TERMINAL_CURSOR_STYLE_BLOCK
                                    mCursorBlinkingEnabled = 2 != arg
                                }

                                3, 4 -> {
                                    cursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE
                                    mCursorBlinkingEnabled = 4 != arg
                                }

                                5, 6 -> {
                                    cursorStyle = TERMINAL_CURSOR_STYLE_BAR
                                    mCursorBlinkingEnabled = 6 != arg
                                }
                            }

                            't', 'u' -> {}
                            else -> finishSequence()
                        }
                    }

                    ESC_CSI_ARGS_ASTERIX -> {
                        val attributeChangeExtent = getArg0(0)
                        if ('x'.code == b && (attributeChangeExtent in 0..2)) {
                            // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            setDecsetinternalBit(
                                DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, 2 == attributeChangeExtent
                            )
                        } else {
                            finishSequence()
                        }
                    }

                    else -> finishSequence()
                }
                if (!mContinueSequence) mEscapeState = ESC_NONE
            }
        }
    }

    private fun getValues(mode: Int): Int {
        val value: Int
        if (47 == mode || 1047 == mode || 1049 == mode) {
            // This state is carried by mScreen pointer.
            value = if (isAlternateBufferActive) 1 else 2
        } else {
            val internalBit = mapDecSetBitToInternalBit(mode)
            value = if (-1 != internalBit) {
                // 1=set, 2=reset.
                if (isDecsetInternalBitSet(internalBit)) 1 else 2
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

        if ('\\'.code == b) // ESC \ terminates OSC
        // Sixel sequences may be very long. '$' and '!' are natural for breaking the sequence.
        {
            val dcs = mOSCOrDeviceControlArgs.toString()
            // DCS $ q P t ST. Request Status String (DECRQSS)
            if (dcs.startsWith("\$q")) {
                if ("\$q\"p" == dcs) {
                    // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                    val csiString = "64;1\"p"
                    mSession.write("\u001bP1\$r$csiString\u001b\\")
                } else {
                    finishSequence()
                }
            } else if (dcs.startsWith("+q")) {
                for (part in dcs.substring(2).split(";")) {
                    if (0 == part.length % 2) {
                        val transBuffer = StringBuilder()
                        var c: Char
                        for (i in part.indices step 2) {
                            try {
                                c = Char(
                                    java.lang.Long.decode("0x" + part[i] + part[i + 1]).toUShort()
                                )
                            } catch (e: NumberFormatException) {
                                continue
                            }
                            transBuffer.append(c)
                        }
                        val responseValue = when (val trans = transBuffer.toString()) {
                            "Co", "colors" ->  // Number of colors.
                                "256"

                            "TN", "name" -> "xterm"
                            else -> getCodeFromTermcap(
                                trans, isDecsetInternalBitSet(
                                    DECSET_BIT_APPLICATION_CURSOR_KEYS
                                ), isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)
                            )
                        }
                        if (null == responseValue) {
                            // Respond with invalid request:
                            mSession.write("\u001bP0+r$part\u001b\\")
                        } else {
                            val hexEncoded = StringBuilder()
                            for (element in responseValue) {
                                hexEncoded.append(
                                    String.format(
                                        Locale.US, "%02X", element.code
                                    )
                                )
                            }
                            mSession.write("\u001bP1+r$part=$hexEncoded\u001b\\")
                        }
                    }
                }
            }
            finishSequence()
        } else {
            if (MAX_OSC_STRING_LENGTH < mOSCOrDeviceControlArgs.length) {
                // Too long.
                mOSCOrDeviceControlArgs.setLength(0)
                finishSequence()
            } else {
                mOSCOrDeviceControlArgs.appendCodePoint(b)
                continueSequence(mEscapeState)
            }
        }
    }

    private fun nextTabStop(numTabs: Int): Int {
        var numTabs1 = numTabs
        for (i in mCursorCol + 1 until mColumns) if (mTabStop[i] && 0 == --numTabs1) return min(
            i, mRightMargin
        )

        return mRightMargin - 1
    }

    /**
     * Process byte while in the [.ESC_CSI_QUESTIONMARK] escape state.
     */
    private fun doCsiQuestionMark(b: Int) {
        when (b.toChar()) {
            'J', 'K' -> {
                mAboutToAutoWrap = false
                val fillChar = ' '.code
                var startCol = -1
                var startRow = -1
                var endCol = -1
                var endRow = -1
                val justRow = ('K'.code == b)
                when (getArg0(0)) {
                    0 -> {
                        startCol = mCursorCol
                        startRow = mCursorRow
                        endCol = mColumns
                        endRow = if (justRow) (mCursorRow + 1) else mRows
                    }

                    1 -> {
                        startCol = 0
                        startRow = if (justRow) mCursorRow else 0
                        endCol = mCursorCol + 1
                        endRow = mCursorRow + 1
                    }

                    2 -> {
                        startCol = 0
                        startRow = if (justRow) mCursorRow else 0
                        endCol = mColumns
                        endRow = if (justRow) (mCursorRow + 1) else mRows
                    }

                    else -> finishSequence()
                }
                for (row in startRow until endRow) {
                    for (col in startCol until endCol) {
                        if (0 == (decodeEffect(
                                screen.getStyleAt(
                                    row, col
                                )
                            ) and CHARACTER_ATTRIBUTE_PROTECTED)
                        ) screen.setChar(col, row, fillChar, style)
                    }
                }
            }

            'h', 'l' -> {
                if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                for (i in 0..mArgIndex) doDecSetOrReset('h'.code == b, mArgs[i])
            }

            'n' -> if (6 == getArg0(-1)) { // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                mSession.write(
                    String.format(
                        Locale.US, "\u001b[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1
                    )
                )
            } else {
                finishSequence()
            }

            'r', 's' -> {
                if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                for (i in 0..mArgIndex) {
                    val externalBit = mArgs[i]
                    val internalBit = mapDecSetBitToInternalBit(externalBit)
                    if (-1 != internalBit) {
                        if ('s'.code == b) {
                            mSavedDecSetFlags = mSavedDecSetFlags or internalBit
                        } else {
                            doDecSetOrReset(
                                0 != (mSavedDecSetFlags and internalBit), externalBit
                            )
                        }
                    }
                }
            }

            '$' -> continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR)

            else -> parseArg(b)
        }
    }

    private fun doDecSetOrReset(setting: Boolean, externalBit: Int) {
        val internalBit = mapDecSetBitToInternalBit(externalBit)
        if (-1 != internalBit) setDecsetinternalBit(internalBit, setting)

        when (externalBit) {
            1, 4, 5, 7, 8, 9, 12, 25, 40, 45, 66, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1015, 1034, 2004 -> {}
            3 -> {
                mLeftMargin = 0
                mTopMargin = 0
                mBottomMargin = mRows
                mRightMargin = mColumns
                // "DECCOLM resets vertical split console mode (DECLRMM) to unavailable":
                setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false)
                // "Erases all data in page memory":
                blockClear(0, 0, mColumns, mRows)
                setCursorRowCol(0, 0)
            }

            6 -> if (setting) setCursorPosition(0, 0)

            69 -> if (!setting) {
                mLeftMargin = 0
                mRightMargin = mColumns
            }

            1048 -> if (setting) saveCursor() else restoreCursor()

            47, 1047, 1049 -> {
                // Set: Save cursor as in DECSC and use Alternate Console Buffer, clearing it first.
                // Reset: Use Normal Console Buffer and restore cursor as in DECRC.
                val newScreen = if (setting) mAltBuffer else mMainBuffer
                if (newScreen != screen) {
                    val resized =
                        !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows)
                    if (setting) saveCursor()
                    screen = newScreen
                    if (!setting) {
                        val col = mSavedStateMain.mSavedCursorCol
                        val row = mSavedStateMain.mSavedCursorRow
                        restoreCursor()
                        if (resized) {
                            // Restore cursor position _not_ clipped to current console (let resizeScreen() handle that):
                            mCursorCol = col
                            mCursorRow = row
                        }
                    }
                    // Check if buffer size needs to be updated:
                    if (resized) resizeScreen()
                    // Clear new console if alt buffer:
                    if (newScreen == mAltBuffer) newScreen.blockSet(
                        0, 0, mColumns, mRows, ' '.code, style
                    )
                }
            }

            else -> finishSequence()
        }
    }

    private fun doCsiBiggerThan(b: Int) {
        when (b.toChar()) {
            'c' -> mSession.write("\u001b[>41;320;0c")
            'm' -> {}
            else -> parseArg(b)
        }
    }

    private fun startEscapeSequence() {
        mEscapeState = ESC
        mArgIndex = 0
        Arrays.fill(mArgs, -1)
    }

    private fun doLinefeed() {
        val belowScrollingRegion = mCursorRow >= mBottomMargin
        var newCursorRow = mCursorRow + 1
        if (belowScrollingRegion) {
            // Move down (but not scroll) as long as we are above the last row.
            if (mCursorRow != mRows - 1) {
                cursorRow = newCursorRow
            }
        } else {
            if (newCursorRow == mBottomMargin) {
                scrollDownOneLine()
                newCursorRow = mBottomMargin - 1
            }
            cursorRow = newCursorRow
        }
    }

    private fun continueSequence(state: Int) {
        mEscapeState = state
        mContinueSequence = true
    }

    private fun doEscPound(b: Int) {
        // Esc # 8 - DEC console alignment test - fill console with E's.
        if ('8'.code == b) {
            screen.blockSet(
                0, 0, mColumns, mRows, 'E'.code, style
            )
        } else {
            finishSequence()
        }
    }

    /**
     * Encountering a character in the [.ESC] state.
     */
    private fun doEsc(b: Int) {
        when (b.toChar()) {
            '#' -> continueSequence(ESC_POUND)
            '(' -> continueSequence(ESC_SELECT_LEFT_PAREN)
            ')' -> continueSequence(ESC_SELECT_RIGHT_PAREN)
            '6' -> if (mCursorCol > mLeftMargin) {
                mCursorCol--
            } else {
                val rows = mBottomMargin - mTopMargin
                screen.blockCopy(
                    mLeftMargin,
                    mTopMargin,
                    mRightMargin - mLeftMargin - 1,
                    rows,
                    mLeftMargin + 1,
                    mTopMargin
                )
                screen.blockSet(
                    mLeftMargin, mTopMargin, 1, rows, ' '.code, encode(
                        mForeColor, mBackColor, 0
                    )
                )
            }

            '7' -> saveCursor()
            '8' -> restoreCursor()
            '9' -> if (mCursorCol < mRightMargin - 1) {
                mCursorCol++
            } else {
                val rows = mBottomMargin - mTopMargin
                screen.blockCopy(
                    mLeftMargin + 1,
                    mTopMargin,
                    mRightMargin - mLeftMargin - 1,
                    rows,
                    mLeftMargin,
                    mTopMargin
                )
                screen.blockSet(
                    mRightMargin - 1, mTopMargin, 1, rows, ' '.code, encode(
                        mForeColor, mBackColor, 0
                    )
                )
            }

            'c' -> {
                reset()
                mMainBuffer.clearTranscript()
                blockClear(0, 0, mColumns, mRows)
                setCursorPosition(0, 0)
            }

            'D' -> doLinefeed()
            'E' -> {
                cursorCol = if (isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)) mLeftMargin else 0
                doLinefeed()
            }

            'F' -> setCursorRowCol(0, mBottomMargin - 1)
            'H' -> mTabStop[mCursorCol] = true
            'M' ->                 // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (mCursorRow <= mTopMargin) {
                    screen.blockCopy(
                        0,
                        mTopMargin,
                        mColumns,
                        mBottomMargin - (mTopMargin + 1),
                        0,
                        mTopMargin + 1
                    )
                    blockClear(
                        0, mTopMargin, mColumns
                    )
                } else {
                    mCursorRow--
                }

            'N', '0' -> {}
            'P' -> {
                mOSCOrDeviceControlArgs.setLength(0)
                continueSequence(ESC_P)
            }

            '[' -> {
                continueSequence(ESC_CSI)
                mIsCSIStart = true
                mLastCSIArg = null
            }

            '=' -> setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true)
            ']' -> {
                mOSCOrDeviceControlArgs.setLength(0)
                continueSequence(ESC_OSC)
            }

            '>' -> setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false)

            else -> finishSequence()
        }
    }

    /**
     * DECSC save cursor - [...](http://www.vt100.net/docs/vt510-rm/DECSC) . See [.restoreCursor].
     */
    private fun saveCursor() {
        val state = if (isAlternateBufferActive) mSavedStateAlt else mSavedStateMain
        state.mSavedCursorRow = mCursorRow
        state.mSavedCursorCol = mCursorCol
        state.mSavedEffect = mEffect
        state.mSavedForeColor = mForeColor
        state.mSavedBackColor = mBackColor
        state.mSavedDecFlags = mCurrentDecSetFlags
        state.mUseLineDrawingG0 = mUseLineDrawingG0
        state.mUseLineDrawingG1 = mUseLineDrawingG1
        state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0
    }

    /**
     * DECRS restore cursor - [...](http://www.vt100.net/docs/vt510-rm/DECRC). See [.saveCursor].
     */
    private fun restoreCursor() {
        val state = if (isAlternateBufferActive) mSavedStateAlt else mSavedStateMain
        setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol)
        mEffect = state.mSavedEffect
        mForeColor = state.mSavedForeColor
        mBackColor = state.mSavedBackColor
        val mask = (DECSET_BIT_AUTOWRAP or DECSET_BIT_ORIGIN_MODE)
        mCurrentDecSetFlags =
            (mCurrentDecSetFlags and mask.inv()) or (state.mSavedDecFlags and mask)
        mUseLineDrawingG0 = state.mUseLineDrawingG0
        mUseLineDrawingG1 = state.mUseLineDrawingG1
        mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". [.ESC_CSI].
     */
    private fun doCsi(b: Int) {
        when (b.toChar()) {
            '!' -> continueSequence(ESC_CSI_EXCLAMATION)
            '"' -> continueSequence(ESC_CSI_DOUBLE_QUOTE)
            '\'' -> continueSequence(ESC_CSI_SINGLE_QUOTE)
            '$' -> continueSequence(ESC_CSI_DOLLAR)
            '*' -> continueSequence(ESC_CSI_ARGS_ASTERIX)
            '@' -> {
                // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                mAboutToAutoWrap = false
                val columnsAfterCursor = mColumns - mCursorCol
                val spacesToInsert = min(getArg0(1), columnsAfterCursor)
                val charsToMove = columnsAfterCursor - spacesToInsert
                screen.blockCopy(
                    mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow
                )
                blockClear(mCursorCol, mCursorRow, spacesToInsert)
            }

            'A' -> cursorRow = max(0, mCursorRow - getArg0(1))

            'B' -> cursorRow = min(mRows - 1, mCursorRow + getArg0(1))

            'C', 'a' -> cursorCol = min(mRightMargin - 1, mCursorCol + getArg0(1))

            'D' -> cursorCol = max(mLeftMargin, mCursorCol - getArg0(1))

            'E' -> setCursorPosition(0, mCursorRow + getArg0(1))
            'F' -> setCursorPosition(0, mCursorRow - getArg0(1))
            'G' -> cursorCol = min(max(1, getArg0(1)), mColumns) - 1

            'H', 'f' -> setCursorPosition(getArg1(1) - 1, getArg0(1) - 1)
            'I' -> cursorCol = nextTabStop(getArg0(1))
            'J' -> {
                when (getArg0(0)) {
                    0 -> {
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                        blockClear(0, mCursorRow + 1, mColumns, mRows - mCursorRow - 1)
                    }

                    1 -> {
                        blockClear(0, 0, mColumns, mCursorRow)
                        blockClear(0, mCursorRow, mCursorCol + 1)
                    }

                    2 -> blockClear(0, 0, mColumns, mRows)

                    3 -> mMainBuffer.clearTranscript()

                    else -> {
                        finishSequence()
                        return
                    }
                }
                mAboutToAutoWrap = false
            }

            'K' -> {
                when (getArg0(0)) {
                    0 -> blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                    1 -> blockClear(0, mCursorRow, mCursorCol + 1)
                    2 -> blockClear(0, mCursorRow, mColumns)

                    else -> {
                        finishSequence()
                        return
                    }
                }
                mAboutToAutoWrap = false
            }

            'L' -> {
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToInsert = min(getArg0(1), linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToInsert
                screen.blockCopy(
                    0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert
                )
                blockClear(0, mCursorRow, mColumns, linesToInsert)
            }

            'M' -> {
                mAboutToAutoWrap = false
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToDelete = min(getArg0(1), linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToDelete
                screen.blockCopy(
                    0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow
                )
                blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete)
            }

            'P' -> {
                mAboutToAutoWrap = false
                val cellsAfterCursor = mColumns - mCursorCol
                val cellsToDelete = min(getArg0(1), cellsAfterCursor)
                val cellsToMove = cellsAfterCursor - cellsToDelete
                screen.blockCopy(
                    mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow
                )
                blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete)
            }

            'S' -> {
                val linesToScroll = getArg0(1)
                for (i in 0 until linesToScroll) scrollDownOneLine()
            }

            'T' -> if (0 == mArgIndex) {
                val linesToScrollArg = getArg0(1)
                val linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin
                val linesToScroll = min(linesBetweenTopAndBottomMargins, linesToScrollArg)
                screen.blockCopy(
                    0,
                    mTopMargin,
                    mColumns,
                    linesBetweenTopAndBottomMargins - linesToScroll,
                    0,
                    mTopMargin + linesToScroll
                )
                blockClear(0, mTopMargin, mColumns, linesToScroll)
            } else finishSequence()


            'X' -> {
                mAboutToAutoWrap = false
                screen.blockSet(
                    mCursorCol,
                    mCursorRow,
                    min(getArg0(1), mColumns - mCursorCol),
                    1,
                    ' '.code,
                    style
                )
            }

            'Z' -> {
                var numberOfTabs = getArg0(1)
                var newCol = mLeftMargin
                for (i in (mCursorCol - 1)..0) {
                    if (mTabStop[i]) {
                        if (0 == --numberOfTabs) {
                            newCol = max(i, mLeftMargin)
                            break
                        }
                    }
                }
                mCursorCol = newCol
            }

            '?' -> continueSequence(ESC_CSI_QUESTIONMARK)
            '>' -> continueSequence(ESC_CSI_BIGGERTHAN)
            '`' -> setCursorColRespectingOriginMode(getArg0(1) - 1)
            'b' -> {
                if (-1 == mLastEmittedCodePoint) return
                val numRepeat = getArg0(1)
                for (i in 0 until numRepeat) emitCodePoint(mLastEmittedCodePoint)
            }

            'c' -> if (0 == getArg0(0)) mSession.write("\u001b[?64;1;2;6;9;15;18;21;22c")

            'd' -> cursorRow = min(max(1, getArg0(1)), mRows) - 1

            'e' -> setCursorPosition(mCursorCol, mCursorRow + getArg0(1))
            'g' -> when (getArg0(0)) {
                0 -> mTabStop[mCursorCol] = false
                3 -> for (i in 0 until mColumns) mTabStop[i] = false
            }

            'h' -> doSetMode(true)
            'l' -> doSetMode(false)
            'm' -> selectGraphicRendition()
            'n' -> when (getArg0(0)) {
                5 -> {
                    // Answer is ESC [ 0 n (Terminal OK).
                    val dsr = byteArrayOf(
                        27.toByte(), '['.code.toByte(), '0'.code.toByte(), 'n'.code.toByte()
                    )
                    mSession.write(dsr, 0, dsr.size)
                }

                6 ->                         // Answer is ESC [ y ; x R, where x,y is
                    // the cursor location.
                    mSession.write(
                        String.format(
                            Locale.US, "\u001b[%d;%dR", mCursorRow + 1, mCursorCol + 1
                        )
                    )
            }

            'r' -> {
                mTopMargin = max(0, min(getArg0(1) - 1, mRows - 2))
                mBottomMargin = max(mTopMargin + 2, min(getArg1(mRows), mRows))
                setCursorPosition(0, 0)
            }

            's' -> if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                mLeftMargin = min(getArg0(1) - 1, mColumns - 2)
                mRightMargin = max(mLeftMargin + 1, min(getArg1(mColumns), mColumns))
                // DECSLRM moves the cursor to column 1, line 1 of the page.
                setCursorPosition(0, 0)
            } else saveCursor()


            't' -> when (getArg0(0)) {
                11 -> mSession.write("\u001b[1t")
                13 -> mSession.write("\u001b[3;0;0t")
                14 -> mSession.write(
                    String.format(
                        Locale.US, "\u001b[4;%d;%dt", mRows * 12, mColumns * 12
                    )
                )

                18 -> mSession.write(String.format(Locale.US, "\u001b[8;%d;%dt", mRows, mColumns))
                19 ->                         // We report the same size as the view, since it's the view really isn't resizable from the shell.
                    mSession.write(String.format(Locale.US, "\u001b[9;%d;%dt", mRows, mColumns))

                20 -> mSession.write("\u001b]LIconLabel\u001b\\")
                21 -> mSession.write("\u001b]l\u001b\\")
            }

            'u' -> restoreCursor()
            ' ' -> continueSequence(ESC_CSI_ARGS_SPACE)
            else -> parseArg(b)
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see [...](http://en.wikipedia.org/wiki/ANSI_escape_code#graphics).
     */
    private fun selectGraphicRendition() {
        if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
        var i = 0
        while (i <= mArgIndex) {
            var code = mArgs[i]
            if (0 > code) {
                if (0 < mArgIndex) {
                    i++
                    continue
                } else code = 0

            }
            if (0 == code) {
                // reset
                mForeColor = COLOR_INDEX_FOREGROUND
                mBackColor = COLOR_INDEX_BACKGROUND
                mEffect = 0
            } else if (1 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_BOLD
            } else if (2 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_DIM
            } else if (3 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_ITALIC
            } else if (4 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_UNDERLINE
            } else if (5 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_BLINK
            } else if (7 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_INVERSE
            } else if (8 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_INVISIBLE
            } else if (9 == code) {
                mEffect = mEffect or CHARACTER_ATTRIBUTE_STRIKETHROUGH
            } else if (22 == code) {
                // Normal color or intensity, neither bright, bold nor faint.
                mEffect = mEffect and (CHARACTER_ATTRIBUTE_BOLD or CHARACTER_ATTRIBUTE_DIM).inv()
            } else if (23 == code) {
                // not italic, but rarely used as such; clears standout with TERM=console
                mEffect = mEffect and CHARACTER_ATTRIBUTE_ITALIC.inv()
            } else if (24 == code) {
                // underline: none
                mEffect = mEffect and CHARACTER_ATTRIBUTE_UNDERLINE.inv()
            } else if (25 == code) {
                // blink: none
                mEffect = mEffect and CHARACTER_ATTRIBUTE_BLINK.inv()
            } else if (27 == code) {
                // image: positive
                mEffect = mEffect and CHARACTER_ATTRIBUTE_INVERSE.inv()
            } else if (28 == code) {
                mEffect = mEffect and CHARACTER_ATTRIBUTE_INVISIBLE.inv()
            } else if (29 == code) {
                mEffect = mEffect and CHARACTER_ATTRIBUTE_STRIKETHROUGH.inv()
            } else if (code in 30..37) {
                mForeColor = code - 30
            } else if (38 == code || 48 == code) {
                if (i + 2 > mArgIndex) {
                    i++
                    continue
                }
                val firstArg = mArgs[i + 1]
                if (2 == firstArg) {
                    if (i + 4 <= mArgIndex) {
                        val red = mArgs[i + 2]
                        val green = mArgs[i + 3]
                        val blue = mArgs[i + 4]
                        if ((red !in 0..255) or (green !in 0..255) or (blue !in 0..255)) {
                            finishSequence()
                        } else {
                            val argbColor = -0x1000000 or (red shl 16) or (green shl 8) or blue
                            if (38 == code) {
                                mForeColor = argbColor
                            } else {
                                mBackColor = argbColor
                            }
                        }
                        // "2;P_r;P_g;P_r"
                        i += 4
                    }
                } else if (5 == firstArg) {
                    val color = mArgs[i + 2]
                    // "5;P_s"
                    i += 2
                    if (color in 0..<NUM_INDEXED_COLORS) {
                        if (38 == code) {
                            mForeColor = color
                        } else {
                            mBackColor = color
                        }
                    }
                } else {
                    finishSequence()
                }
            } else if (39 == code) {
                // Set default foreground color.
                mForeColor = COLOR_INDEX_FOREGROUND
            } else if (code in 40..47) {
                // Set background color.
                mBackColor = code - 40
            } else if (49 == code) {
                // Set default background color.
                mBackColor = COLOR_INDEX_BACKGROUND
            } else if (code in 90..97) {
                // Bright foreground colors (aixterm codes).
                mForeColor = code - 90 + 8
            } else if (code in 100..107) {
                // Bright background color (aixterm codes).
                mBackColor = code - 100 + 8
            }
            i++
        }
    }

    private fun doOsc(b: Int) {
        when (b) {
            7 -> doOscSetTextParameters("\u0007")
            27 -> continueSequence(ESC_OSC_ESC)
            else -> collectOSCArgs(b)
        }
    }

    private fun doOscEsc(b: Int) {
        if ('\\'.code == b) {
            doOscSetTextParameters("\u001b\\")
        } else { // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            collectOSCArgs(27)
            collectOSCArgs(b)
            continueSequence(ESC_OSC)
        }
    }

    /**
     * An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.
     */
    private fun doOscSetTextParameters(bellOrStringTerminator: String) {
        var value = -1
        var textParameter = ""
        // Extract initial $value from initial "$value;..." string.
        for (mOSCArgTokenizerIndex in mOSCOrDeviceControlArgs.indices) {
            val b = mOSCOrDeviceControlArgs[mOSCArgTokenizerIndex]
            if (';' == b) {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1)
                break
            } else if (b in '0'..'9') {
                value = (if (0 > value) 0 else value * 10) + (b.code - '0'.code)
            } else {
                finishSequence()
                return
            }
        }
        when (value) {
            0, 1, 2 -> {/*we used to set window title [textParameter]*/
            }

            4 -> {
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
                            if (colorIndex !in 0..255) {
                                finishSequence()
                                return
                            } else {
                                mColors.tryParseColor(
                                    colorIndex, textParameter.substring(parsingPairStart, i)
                                )
                                colorIndex = -1
                                parsingPairStart = -1
                            }
                        }
                    } else if (parsingPairStart >= 0) {
                        // We have passed a color index and are now going through color spec.
                    } else if (/*0 > parsingPairStart && */(b in '0'..'9')) {
                        colorIndex = (if (0 > colorIndex) 0 else colorIndex * 10) + (b - '0')
                    } else {
                        finishSequence()
                        return
                    }
                    if (endOfInput) break
                    i++
                }
            }

            10, 11, 12 -> {
                var specialIndex = COLOR_INDEX_FOREGROUND + (value - 10)
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
                                val b = (65535 * (rgb and 0x000000FF)) / 255
                                mSession.write(
                                    String.format(
                                        Locale.US,
                                        "\u001B]$value;rgb:%04x/%04x/%04x$bellOrStringTerminator",
                                        r,
                                        g,
                                        b
                                    )
                                )
                            } else {
                                mColors.tryParseColor(specialIndex, colorSpec)
                            }
                            specialIndex++
                            if (endOfInput || (COLOR_INDEX_CURSOR < specialIndex) || ++charIndex >= textParameter.length) break
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
                        Base64.decode(textParameter.substring(startIndex), 0), Charsets.UTF_8
                    )
                    mSession.onCopyTextToClipboard(clipboardText)
                } catch (ignored: Exception) {
                }
            }

            104 -> if (textParameter.isEmpty()) {
                mColors.reset()
            } else {
                var lastIndex = 0
                var charIndex = 0
                while (true) {
                    val endOfInput = charIndex == textParameter.length
                    if (endOfInput || ';' == textParameter[charIndex]) {
                        try {
                            val colorToReset = textParameter.substring(lastIndex, charIndex).toInt()
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

            110, 111, 112 -> mColors.reset(COLOR_INDEX_FOREGROUND + (value - 110))
            119 -> {}
            else -> finishSequence()
        }
        finishSequence()
    }

    private fun blockClear(sx: Int, sy: Int, w: Int, h: Int = 1) {
        screen.blockSet(sx, sy, w, h, ' '.code, style)
    }

    private val style: Long
        get() = encode(mForeColor, mBackColor, mEffect)

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private fun doSetMode(newValue: Boolean) {
        val modeBit = getArg0(0)
        when (modeBit) {
            4 -> mInsertMode = newValue
            34 -> {}
            else -> finishSequence()
        }
    }

    /**
     * NOTE: The parameters of this function respect the [.DECSET_BIT_ORIGIN_MODE]. Use
     * [.setCursorRowCol] for absolute pos.
     */
    private fun setCursorPosition(x: Int, y: Int) {
        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
        val effectiveTopMargin = if (originMode) mTopMargin else 0
        val effectiveBottomMargin = if (originMode) mBottomMargin else mRows
        val effectiveLeftMargin = if (originMode) mLeftMargin else 0
        val effectiveRightMargin = if (originMode) mRightMargin else mColumns
        val newRow = max(
            effectiveTopMargin, min((effectiveTopMargin + y), (effectiveBottomMargin - 1))
        )
        val newCol = max(
            effectiveLeftMargin, min((effectiveLeftMargin + x), (effectiveRightMargin - 1))
        )
        setCursorRowCol(newRow, newCol)
    }

    private fun scrollDownOneLine() {
        scrollCounter++
        if (0 != mLeftMargin || mRightMargin != mColumns) {
            // Horizontal margin: Do not put anything into scroll history, just non-margin part of console up.
            screen.blockCopy(
                mLeftMargin,
                mTopMargin + 1,
                mRightMargin - mLeftMargin,
                mBottomMargin - mTopMargin - 1,
                mLeftMargin,
                mTopMargin
            )
            // .. and blank bottom row between margins:
            screen.blockSet(
                mLeftMargin,
                mBottomMargin - 1,
                mRightMargin - mLeftMargin,
                1,
                ' '.code,
                mEffect.toLong()
            )
        } else {
            screen.scrollDownOneLine(
                mTopMargin, mBottomMargin, style
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
        val bytes = getInts(inputByte)
        mIsCSIStart = false
        for (b in bytes) {
            if (b in '0'.code..'9'.code) {
                if (mArgIndex < mArgs.size) {
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
                continueSequence(mEscapeState)
            } else if (';'.code == b) {
                if (mArgIndex < mArgs.size) {
                    mArgIndex++
                }
                continueSequence(mEscapeState)
            } else {
                finishSequence()
            }
            mLastCSIArg = b
        }
    }

    private fun getInts(inputByte: Int): IntArray {
        var bytes = intArrayOf(inputByte)
        // Only doing this for ESC_CSI and not for other ESC_CSI_* since they seem to be using their
        // own defaults with getArg*() calls, but there may be missed cases
        if (ESC_CSI == mEscapeState) {
            if ( // If sequence starts with a ; character, like \033[;m
                (mIsCSIStart && ';'.code == inputByte) || (!mIsCSIStart && null != mLastCSIArg && ';'.code == mLastCSIArg && ';'.code == inputByte)) {
                // If sequence contains sequential ; characters, like \033[;;m
                // Assume 0 was passed
                bytes = intArrayOf('0'.code, ';'.code)
            }
        }
        return bytes
    }

    private fun getArg0(defaultValue: Int): Int {
        return getArg(0, defaultValue, true)
    }

    private fun getArg1(defaultValue: Int): Int {
        return getArg(1, defaultValue, true)
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
            continueSequence(mEscapeState)
        } else {
            finishSequence()
        }
    }

    private fun finishSequence() {
        mEscapeState = ESC_NONE
    }

    /**
     * Send a Unicode code point to the console.
     *
     * @param codePoint The code point of the character to display
     */
    private fun emitCodePoint(codePoint: Int) {
        var codePoint1 = codePoint
        mLastEmittedCodePoint = codePoint
        if (if (mUseLineDrawingUsesG0) mUseLineDrawingG0 else mUseLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            codePoint1 = when (codePoint1.toChar()) {
                '_' ->                     // Blank.
                    ' '.code

                '`' ->                     // Diamond.
                    '◆'.code

                '0' ->                     // Solid block;
                    '█'.code

                'a' ->                     // Checker board.
                    '▒'.code

                'b' ->                     // Horizontal tab.
                    '␉'.code

                'c' ->                     // Form feed.
                    '␌'.code

                'd' ->                     // Carriage return.
                    '\r'.code

                'e' ->                     // Linefeed.
                    '␊'.code

                'f' ->                     // Degree.
                    '°'.code

                'g' ->                     // Plus-minus.
                    '±'.code

                'h' ->                     // Newline.
                    '\n'.code

                'i' ->                     // Vertical tab.
                    '␋'.code

                'j' ->                     // Lower right corner.
                    '┘'.code

                'k' ->                     // Upper right corner.
                    '┐'.code

                'l' ->                     // Upper left corner.
                    '┌'.code

                'm' ->                     // Left left corner.
                    '└'.code

                'n' ->                     // Crossing lines.
                    '┼'.code

                'o' ->                     // Horizontal line - scan 1.
                    '⎺'.code

                'p' ->                     // Horizontal line - scan 3.
                    '⎻'.code

                'q' ->                     // Horizontal line - scan 5.
                    '─'.code

                'r' ->                     // Horizontal line - scan 7.
                    '⎼'.code

                's' ->                     // Horizontal line - scan 9.
                    '⎽'.code

                't' ->                     // T facing rightwards.
                    '├'.code

                'u' ->                     // T facing leftwards.
                    '┤'.code

                'v' ->                     // T facing upwards.
                    '┴'.code

                'w' ->                     // T facing downwards.
                    '┬'.code

                'x' ->                     // Vertical line.
                    '│'.code

                'y' ->                     // Less than or equal to.
                    '≤'.code

                'z' ->                     // Greater than or equal to.
                    '≥'.code

                '{' ->                     // Pi.
                    'π'.code

                '|' ->                     // Not equal to.
                    '≠'.code

                '}' ->                     // UK pound.
                    '£'.code

                '~' ->                     // Centered dot.
                    '·'.code

                else -> codePoint
            }
        }
        val autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP)
        val displayWidth = WcWidth.width(codePoint1)
        val cursorInLastColumn = mCursorCol == mRightMargin - 1
        if (autoWrap) {
            if (cursorInLastColumn && ((mAboutToAutoWrap && 1 == displayWidth) || 2 == displayWidth)) {
                screen.setLineWrap(mCursorRow)
                mCursorCol = mLeftMargin
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++
                } else {
                    scrollDownOneLine()
                }
            }
        } else if (cursorInLastColumn && 2 == displayWidth) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return
        }
        if (mInsertMode && 0 < displayWidth) {
            // Move character to right one space.
            val destCol = mCursorCol + displayWidth
            if (destCol < mRightMargin) screen.blockCopy(
                mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow
            )
        }
        val column = getColumn(displayWidth)
        screen.setChar(
            column, mCursorRow, codePoint1, style
        )
        if (autoWrap && 0 < displayWidth) mAboutToAutoWrap =
            (mCursorCol == mRightMargin - displayWidth)
        mCursorCol = min((mCursorCol + displayWidth), (mRightMargin - 1))
    }

    private fun getColumn(displayWidth: Int): Int {
        val offsetDueToCombiningChar =
            (if (0 >= displayWidth && 0 < mCursorCol && !mAboutToAutoWrap) 1 else 0)
        var column = mCursorCol - offsetDueToCombiningChar
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
        setCursorPosition(col, mCursorRow)
    }

    /**
     * TODO: Better name, distinguished from [.setCursorPosition] by not regarding origin mode.
     */
    private fun setCursorRowCol(row: Int, col: Int) {
        mCursorRow = max(0, min(row, mRows - 1))
        mCursorCol = max(0, min(col, mColumns - 1))
        mAboutToAutoWrap = false
    }

    fun clearScrollCounter() {
        scrollCounter = 0
    }


    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    private fun reset() {
        mArgIndex = 0
        mContinueSequence = false
        mEscapeState = ESC_NONE
        mInsertMode = false
        mLeftMargin = 0
        mTopMargin = 0
        mBottomMargin = mRows
        mRightMargin = mColumns
        mAboutToAutoWrap = false
        mSavedStateAlt.mSavedForeColor = COLOR_INDEX_FOREGROUND
        mSavedStateMain.mSavedForeColor = COLOR_INDEX_FOREGROUND
        mForeColor = COLOR_INDEX_FOREGROUND
        mSavedStateAlt.mSavedBackColor = COLOR_INDEX_BACKGROUND
        mSavedStateMain.mSavedBackColor = COLOR_INDEX_BACKGROUND
        mBackColor = COLOR_INDEX_BACKGROUND
        setDefaultTabStops()
        mUseLineDrawingG1 = false
        mUseLineDrawingG0 = false
        mUseLineDrawingUsesG0 = true
        mSavedStateMain.mSavedEffect = 0
        mSavedStateMain.mSavedCursorCol = 0
        mSavedStateMain.mSavedCursorRow = 0
        mSavedStateAlt.mSavedEffect = 0
        mSavedStateAlt.mSavedCursorCol = 0
        mSavedStateAlt.mSavedCursorRow = 0
        mCurrentDecSetFlags = 0
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small console:
        setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true)
        setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true)
        mSavedStateAlt.mSavedDecFlags = 0
        mSavedStateMain.mSavedDecFlags = 0
        mSavedDecSetFlags = 0
        // XXX: Should we set terminal driver back to IUTF8 with termios?
        mUtf8ToFollow = 0
        mUtf8Index = 0
        mColors.reset()
    }

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return screen.getSelectedText(x1, y1, x2, y2)
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    fun paste(text: CharSequence) {
        // First: Always remove escape Key and C1 control characters [0x80,0x9F]:
        var text1 = REGEX.matcher(text).replaceAll("")
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        text1 = PATTERN.matcher(text1).replaceAll("\r")
        // Then: Implement bracketed paste mode if enabled:
        val bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE)
        if (bracketed) mSession.write("\u001b[200~")
        mSession.write(text1)
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
         * corner of the console, within the margins. The starting point for line numbers depends on the current top margin
         * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
         * upper-left corner of the console. The starting point for line numbers is independent of the margins. The cursor
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
    }
}
