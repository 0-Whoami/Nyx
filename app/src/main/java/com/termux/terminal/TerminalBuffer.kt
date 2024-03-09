package com.termux.terminal

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import kotlin.math.max

/**
 * A circular buffer of [TerminalRow]:s which keeps notes about what is visible on a logical console and the scroll
 * history.
 *
 *
 * See [.externalToInternalRow] for how to map from logical console rows to array indices.
 */
/**
 * Create a transcript console.
 *
 * @param mColumns    the width of the console in characters.
 * @param mTotalRows  the height of the entire text area, in rows of text.
 * @param mScreenRows the height of just the console, not including the transcript that holds lines that have scrolled off
 * the top of the console.
 */
class TerminalBuffer(
    var mColumns: Int,
    /**
     * The length of [.mLines].
     */
    var mTotalRows: Int,
    /**
     * The number of rows and columns visible on the console.
     */
    var mScreenRows: Int
) {
    private val bitmaps: HashMap<Int, TerminalBitmap> = HashMap()
    private var workingBitmap: WorkingTerminalBitmap? = null
    private var mLines: Array<TerminalRow?> = arrayOfNulls(mTotalRows)

    /**
     * The number of rows kept in history.
     */
    var activeTranscriptRows: Int = 0
        private set

    /**
     * The index in the circular buffer where the visible console starts.
     */
    private var mScreenFirstRow = 0
    private var hasBitmaps = false

    private var bitmapLastGC = SystemClock.uptimeMillis()


    init {
        blockSet(0, 0, mColumns, mScreenRows, ' '.code, TextStyle.NORMAL)
    }

    val activeRows: Int
        get() = activeTranscriptRows + mScreenRows

    /**
     * Convert a row value from the public external coordinate system to our internal private coordinate system.
     *
     * <pre>
     * - External coordinate system: -mActiveTranscriptRows to mScreenRows-1, with the console being 0..mScreenRows-1.
     * - Internal coordinate system: the mScreenRows lines starting at mScreenFirstRow comprise the console, while the
     * mActiveTranscriptRows lines ending at mScreenFirstRow-1 form the transcript (as a circular buffer).
     *
     * External ↔ Internal:
     *
     * [ ...                            ]     [ ...                                     ]
     * [ -mActiveTranscriptRows         ]     [ mScreenFirstRow - mActiveTranscriptRows ]
     * [ ...                            ]     [ ...                                     ]
     * [ 0 (visible console starts here) ]  ↔  [ mScreenFirstRow                         ]
     * [ ...                            ]     [ ...                                     ]
     * [ mScreenRows-1                  ]     [ mScreenFirstRow + mScreenRows-1         ]
    </pre> *
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    fun externalToInternalRow(externalRow: Int): Int {
        val internalRow = mScreenFirstRow + externalRow
        return if (0 > internalRow) (mTotalRows + internalRow) else (internalRow % mTotalRows)
    }

    fun setLineWrap(row: Int) {
        mLines[externalToInternalRow(row)]!!.mLineWrap = true
    }

    fun getLineWrap(row: Int): Boolean {
        return mLines[externalToInternalRow(row)]!!.mLineWrap
    }

    fun clearLineWrap(row: Int) {
        mLines[externalToInternalRow(row)]!!.mLineWrap = false
    }

    /**
     * Resize the console which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the console should have.
     * @param newRows    The number of rows the console should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    fun resize(
        newColumns: Int,
        newRows: Int,
        newTotalRows: Int,
        cursor: IntArray,
        currentStyle: Long,
        altScreen: Boolean
    ) {
        // newRows > mTotalRows should not normally happen since mTotalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == mColumns && newRows <= mTotalRows) {
            // Fast resize where just the rows changed.
            var shiftDownOfTopRow = mScreenRows - newRows
            if (shiftDownOfTopRow in 1..<mScreenRows) {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                var i = mScreenRows - 1
                while (0 < i) {
                    if (cursor[1] >= i) break
                    val r = externalToInternalRow(i)
                    if (null == mLines[r] || mLines[r]!!.isBlank) {
                        if (0 == --shiftDownOfTopRow) break
                    }
                    i--
                }
            } else if (0 > shiftDownOfTopRow) {
                // Negative shift down = expanding. Only move console up if there is transcript to show:
                val actualShift =
                    max(shiftDownOfTopRow, -activeTranscriptRows)

                if (shiftDownOfTopRow != actualShift) {
                    // The new lines revealed by the resizing are not all from the transcript. Blank the below ones.
                    for (i in 0 until actualShift - shiftDownOfTopRow) allocateFullLineIfNecessary((mScreenFirstRow + mScreenRows + i) % mTotalRows).clear(
                        currentStyle
                    )
                    shiftDownOfTopRow = actualShift
                }
            }
            mScreenFirstRow += shiftDownOfTopRow
            mScreenFirstRow =
                if ((0 > this.mScreenFirstRow)) (mScreenFirstRow + mTotalRows) else (mScreenFirstRow % mTotalRows)
            mTotalRows = newTotalRows
            activeTranscriptRows = if (altScreen) 0 else max(
                0,
                (activeTranscriptRows + shiftDownOfTopRow)
            )
            cursor[1] -= shiftDownOfTopRow
            mScreenRows = newRows
        } else {
            // Copy away old state and update new:
            val oldLines = mLines
            mLines = arrayOfNulls(newTotalRows)
            for (i in 0 until newTotalRows) mLines[i] = TerminalRow(newColumns, currentStyle)
            val oldActiveTranscriptRows = activeTranscriptRows
            val oldScreenFirstRow = mScreenFirstRow
            val oldScreenRows = mScreenRows
            val oldTotalRows = mTotalRows
            mTotalRows = newTotalRows
            mScreenRows = newRows
            mScreenFirstRow = 0
            activeTranscriptRows = mScreenFirstRow
            mColumns = newColumns
            var newCursorRow = -1
            var newCursorColumn = -1
            val oldCursorRow = cursor[1]
            val oldCursorColumn = cursor[0]
            var newCursorPlaced = false
            var currentOutputExternalRow = 0
            var currentOutputExternalColumn = 0
            // Loop over every character in the initial state.
            // Blank lines should be skipped only if at end of transcript (just as is done in the "fast" resize), so we
            // keep track how many blank lines we have skipped if we later on find a non-blank line.
            var skippedBlankLines = 0
            for (externalOldRow in -oldActiveTranscriptRows until oldScreenRows) {
                // Do what externalToInternalRow() does but for the old state:
                var internalOldRow = oldScreenFirstRow + externalOldRow
                internalOldRow =
                    if ((0 > internalOldRow)) (oldTotalRows + internalOldRow) else (internalOldRow % oldTotalRows)
                val oldLine = oldLines[internalOldRow]
                val cursorAtThisRow = externalOldRow == oldCursorRow
                // The cursor may only be on a non-null line, which we should not skip:
                if (null == oldLine || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank) {
                    skippedBlankLines++
                    continue
                } else if (0 < skippedBlankLines) {
                    // After skipping some blank lines we encounter a non-blank line. Insert the skipped blank lines.
                    for (i in 0 until skippedBlankLines) {
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            scrollDownOneLine(0, mScreenRows, currentStyle)
                        } else {
                            currentOutputExternalRow++
                        }
                        currentOutputExternalColumn = 0
                    }
                    skippedBlankLines = 0
                }
                var lastNonSpaceIndex = 0
                var justToCursor = false
                if (cursorAtThisRow || oldLine.mLineWrap) {
                    // Take the whole line, either because of cursor on it, or if line wrapping.
                    lastNonSpaceIndex = oldLine.spaceUsed
                    if (cursorAtThisRow) justToCursor = true
                } else {
                    for (i in 0 until oldLine.spaceUsed) {
                        if (' ' != oldLine.mText[i]) lastNonSpaceIndex = i + 1
                    }
                }
                var currentOldCol = 0
                var styleAtCol: Long = 0
                var i = 0
                while (i < lastNonSpaceIndex) {
                    // Note that looping over java character, not cells.
                    val c = oldLine.mText[i]
                    var codePoint: Int
                    if ((Character.isHighSurrogate(c))) {
                        ++i
                        codePoint = Character.toCodePoint(c, oldLine.mText[i])
                    } else {
                        codePoint = c.code
                    }
                    val displayWidth = WcWidth.width(codePoint)
                    // Use the last style if this is a zero-width character:
                    if (0 < displayWidth) styleAtCol = oldLine.getStyle(currentOldCol)
                    // Line wrap as necessary:
                    if (currentOutputExternalColumn + displayWidth > mColumns) {
                        setLineWrap(currentOutputExternalRow)
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            if (newCursorPlaced) newCursorRow--
                            scrollDownOneLine(0, mScreenRows, currentStyle)
                        } else {
                            currentOutputExternalRow++
                        }
                        currentOutputExternalColumn = 0
                    }
                    val offsetDueToCombiningChar =
                        (if ((0 >= displayWidth && 0 < currentOutputExternalColumn)) 1 else 0)
                    val outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar
                    setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol)
                    if (0 < displayWidth) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn
                            newCursorRow = currentOutputExternalRow
                            newCursorPlaced = true
                        }
                        currentOldCol += displayWidth
                        currentOutputExternalColumn += displayWidth
                        if (justToCursor && newCursorPlaced) break
                    }
                    i++
                }
                // Old row has been copied. Check if we need to insert newline if old line was not wrapping:
                if (externalOldRow != (oldScreenRows - 1) && !oldLine.mLineWrap) {
                    if (currentOutputExternalRow == mScreenRows - 1) {
                        if (newCursorPlaced) newCursorRow--
                        scrollDownOneLine(0, mScreenRows, currentStyle)
                    } else {
                        currentOutputExternalRow++
                    }
                    currentOutputExternalColumn = 0
                }
            }
            cursor[0] = newCursorColumn
            cursor[1] = newCursorRow
        }
        // Handle cursor scrolling off console:
        if (0 > cursor[0] || 0 > cursor[1]) {
            cursor[1] = 0
            cursor[0] = 0
        }
    }

    /**
     * Block copy lines and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of lines to be copied.
     */
    private fun blockCopyLinesDown(srcInternal: Int, len: Int) {
        if (0 == len) return
        val totalRows = mTotalRows
        val start = len - 1
        // Save away line to be overwritten:
        val lineToBeOverWritten = mLines[(srcInternal + start + 1) % totalRows]
        // Do the copy from bottom to top.
        var i = start
        while (0 <= i) {
            mLines[(srcInternal + i + 1) % totalRows] = mLines[(srcInternal + i) % totalRows]
            --i
        }
        // Put back overwritten line, now above the block:
        mLines[srcInternal % totalRows] = lineToBeOverWritten
    }

    /**
     * Scroll the console down one line. To scroll the whole console of a 24 line console, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    fun scrollDownOneLine(topMargin: Int, bottomMargin: Int, style: Long) {
//        require(!(topMargin > bottomMargin - 1 || 0 > topMargin || bottomMargin > mScreenRows)) { "topMargin=$topMargin, bottomMargin=$bottomMargin, mScreenRows=$mScreenRows" }
        // Copy the fixed topMargin lines one line down so that they remain on console in same position:
        blockCopyLinesDown(mScreenFirstRow, topMargin)
        // Copy the fixed mScreenRows-bottomMargin lines one line down so that they remain on console in same
        // position:
        blockCopyLinesDown(externalToInternalRow(bottomMargin), mScreenRows - bottomMargin)
        // Update the console location in the ring buffer:
        mScreenFirstRow = (mScreenFirstRow + 1) % mTotalRows
        // Note that the history has grown if not already full:
        if (activeTranscriptRows < mTotalRows - mScreenRows) activeTranscriptRows++
        // Blank the newly revealed line above the bottom margin:
        val blankRow = externalToInternalRow(bottomMargin - 1)
        if (null == mLines[blankRow]) {
            mLines[blankRow] = TerminalRow(mColumns, style)
        } else {
            // find if a bitmap is completely scrolled out
            val used: MutableCollection<Int> = HashSet()
            if (mLines[blankRow]!!.mHasBitmap) {
                for (column in 0 until mColumns) {
                    val st = mLines[blankRow]!!.getStyle(column)
                    if (TextStyle.isBitmap(st)) {
                        used.add((st shr 16).toInt() and 0xffff)
                    }
                }
                val nextLine = mLines[(blankRow + 1) % mTotalRows]
                if (nextLine!!.mHasBitmap) {
                    for (column in 0 until mColumns) {
                        val st = nextLine.getStyle(column)
                        if (TextStyle.isBitmap(st)) {
                            used.remove((st shr 16).toInt() and 0xffff)
                        }
                    }
                }
                for (bm in used) {
                    bitmaps.remove(bm)
                }
            }
            mLines[blankRow]!!.clear(style)
        }
    }

    /**
     * Block copy characters from one position in the console to another. The two positions can overlap. All characters
     * of the source and destination must be within the bounds of the console, or else an InvalidParameterException will
     * be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w  width
     * @param h  height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    fun blockCopy(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        if (0 == w) return
//        require(!(0 > sx || sx + w > mColumns || 0 > sy || sy + h > mScreenRows || 0 > dx || dx + w > mColumns || 0 > dy || dy + h > mScreenRows))
        val copyingUp = sy > dy
        for (y in 0 until h) {
            val y2 = if (copyingUp) y else (h - (y + 1))
            val sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2))
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(
                sourceRow,
                sx,
                sx + w,
                dx
            )
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the console, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    fun blockSet(sx: Int, sy: Int, w: Int, h: Int, `val`: Int, style: Long) {
//        require(!(0 > sx || sx + w > mColumns || 0 > sy || sy + h > mScreenRows)) { "Illegal arguments! blockSet($sx, $sy, $w, $h, $`val`, $mColumns, $mScreenRows)" }
        for (y in 0 until h) {
            for (x in 0 until w) setChar(sx + x, sy + y, `val`, style)
            if (sx + w == mColumns && ' '.code == `val`) {
                clearLineWrap(sy + y)
            }
        }
    }

    fun allocateFullLineIfNecessary(row: Int): TerminalRow {
        return if ((null == mLines[row])) (TerminalRow(mColumns, 0).also {
            mLines[row] = it
        }) else mLines[row]!!
    }

    fun setChar(column: Int, row: Int, codePoint: Int, style: Long) {
        val row1 = externalToInternalRow(row)
        allocateFullLineIfNecessary(row1).setChar(column, codePoint, style)
    }

    fun getStyleAt(externalRow: Int, column: Int): Long {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column)
    }

    /**
     * Support for [](http://vt100.net/docs/vt510-rm/DEC<a href=)">...">and http://vt100.net/docs/vt510-rm/DECCARA
     */
    fun setOrClearEffect(
        bits: Int,
        setOrClear: Boolean,
        reverse: Boolean,
        rectangular: Boolean,
        leftMargin: Int,
        rightMargin: Int,
        top: Int,
        left: Int,
        bottom: Int,
        right: Int
    ) {
        for (y in top until bottom) {
            val line = mLines[externalToInternalRow(y)]
            val startOfLine = if ((rectangular || y == top)) left else leftMargin
            val endOfLine = if ((rectangular || y + 1 == bottom)) right else rightMargin
            for (x in startOfLine until endOfLine) {
                val currentStyle = line!!.getStyle(x)
                val foreColor = TextStyle.decodeForeColor(currentStyle)
                val backColor = TextStyle.decodeBackColor(currentStyle)
                var effect = TextStyle.decodeEffect(currentStyle)
                effect = if (reverse) {
                    // Clear out the bits to reverse and add them back in reversed:
                    effect and bits.inv() or (bits and effect.inv())
                } else if (setOrClear) {
                    effect or bits
                } else {
                    effect and bits.inv()
                }
                line.mStyle[x] = TextStyle.encode(foreColor, backColor, effect)
            }
        }
    }

    fun clearTranscript() {
        if (mScreenFirstRow < activeTranscriptRows) {
            mLines.fill(
                null,
                mTotalRows + mScreenFirstRow - activeTranscriptRows,
                mTotalRows
            )
            mLines.fill(null, 0, mScreenFirstRow)
        } else {
            mLines.fill(null, mScreenFirstRow - activeTranscriptRows, mScreenFirstRow)
        }
        activeTranscriptRows = 0
        bitmaps.clear()
        hasBitmaps = false
    }

    fun getSixelBitmap(style: Long): Bitmap? {
        return bitmaps[TextStyle.bitmapNum(style)]!!.bitmap
    }

    fun getSixelRect(style: Long): Rect {
        val bm = bitmaps[TextStyle.bitmapNum(style)]
        val x = TextStyle.bitmapX(style)
        val y = TextStyle.bitmapY(style)
        return Rect(
            x * bm!!.cellWidth,
            y * bm.cellHeight,
            (x + 1) * bm.cellWidth,
            (y + 1) * bm.cellHeight
        )
    }

    fun sixelStart(width: Int, height: Int) {
        workingBitmap = WorkingTerminalBitmap(width, height)
    }

    fun sixelChar(c: Int, rep: Int): Unit =
        workingBitmap!!.sixelChar(c, rep)


    fun sixelSetColor(col: Int): Unit =
        workingBitmap!!.sixelSetColor(col)


    fun sixelSetColor(col: Int, r: Int, g: Int, b: Int): Unit =
        workingBitmap!!.sixelSetColor(col, r, g, b)


    private fun findFreeBitmap(): Int {
        var i = 0
        while (bitmaps.containsKey(i)) {
            i++
        }
        return i
    }

    fun sixelEnd(Y: Int, X: Int, cellW: Int, cellH: Int): Int {
        val num = findFreeBitmap()
        bitmaps[num] = TerminalBitmap(num, workingBitmap!!, Y, X, cellW, cellH, this)
        workingBitmap = null
        if (null == bitmaps[num]!!.bitmap) {
            bitmaps.remove(num)
            return 0
        }
        hasBitmaps = true
        bitmapGC(30000)
        return bitmaps[num]!!.scrollLines
    }

    fun addImage(
        image: ByteArray?,
        Y: Int,
        X: Int,
        cellW: Int,
        cellH: Int,
        width: Int,
        height: Int,
        aspect: Boolean
    ): IntArray {
        val num = findFreeBitmap()
        bitmaps[num] =
            TerminalBitmap(num, image!!, Y, X, cellW, cellH, width, height, aspect, this)
        if (null == bitmaps[num]!!.bitmap) {
            bitmaps.remove(num)
            return intArrayOf(0, 0)
        }
        hasBitmaps = true
        bitmapGC(30000)
        return bitmaps[num]!!.cursorDelta
    }

    fun bitmapGC(timeDelta: Int) {
        if (!hasBitmaps || bitmapLastGC + timeDelta > SystemClock.uptimeMillis()) {
            return
        }
        val used: MutableCollection<Int> = HashSet()
        for (mLine in mLines) {
            if (null != mLine && mLine.mHasBitmap) {
                for (column in 0 until mColumns) {
                    val st = mLine.getStyle(column)
                    if (TextStyle.isBitmap(st)) {
                        used.add((st shr 16).toInt() and 0xffff)
                    }
                }
            }
        }
        val keys: Iterable<Int> = HashSet(bitmaps.keys)
        for (bn in keys) {
            if (!used.contains(bn)) {
                bitmaps.remove(bn)
            }
        }
        bitmapLastGC = SystemClock.uptimeMillis()
    }

    fun getSelectedText(selX1: Int, selY1: Int, selX2: Int, selY2: Int): String {
        var y1 = selY1
        var y2 = selY2
        val builder = StringBuilder()
        val columns = mColumns
        if (y1 < -activeTranscriptRows) y1 = -activeTranscriptRows
        if (y2 >= mScreenRows) y2 = mScreenRows - 1
        for (row in y1..y2) {
            val x1 = if ((row == y1)) selX1 else 0
            var x2: Int
            if (row == y2) {
                x2 = selX2 + 1
                if (x2 > columns) x2 = columns
            } else {
                x2 = columns
            }
            val lineObject = mLines[externalToInternalRow(row)]
            val x1Index = lineObject!!.findStartOfColumn(x1)
            var x2Index =
                if ((x2 < mColumns)) lineObject.findStartOfColumn(x2) else lineObject.spaceUsed
            if (x2Index == x1Index) {
                // Selected the start of a wide character.
                x2Index = lineObject.findStartOfColumn(x2 + 1)
            }
            val line = lineObject.mText
            var lastPrintingCharIndex = -1
            var i: Int
            val rowLineWrap = getLineWrap(row)
            if (rowLineWrap && x2 == columns) {
                // If the line was wrapped, we shouldn't lose trailing space:
                lastPrintingCharIndex = x2Index - 1
            } else {
                i = x1Index
                while (i < x2Index) {
                    val c = line[i]
                    if (' ' != c) lastPrintingCharIndex = i
                    ++i
                }
            }
            val len = lastPrintingCharIndex - x1Index + 1
            if (-1 != lastPrintingCharIndex && 0 < len) builder.appendRange(
                line, x1Index,
                x1Index + len
            )
            if ((!rowLineWrap) && (row < y2) && (row < mScreenRows - 1)) builder.append('\n')
        }
        return builder.toString()
    }
}
