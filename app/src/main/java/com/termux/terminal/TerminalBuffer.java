package com.termux.terminal;

import static java.lang.Math.max;

import java.util.Arrays;


public final class TerminalBuffer {
    public int mColumns, mTotalRows, mScreenRows;
    /**
     * The number of rows kept in history.
     */
    public int activeTranscriptRows;
    private TerminalRow[] mLines;
    /**
     * The index in the circular buffer where the visible console starts.
     */
    private int mScreenFirstRow;

    /**
     * Create a transcript console.
     *
     * @param cols       the width of the console in characters.
     * @param rows       the height of the entire text area, in rows of text.
     * @param screenRows the height of just the console, not including the transcript that holds lines that have scrolled off
     *                   the top of the console.
     */
    public TerminalBuffer(final int cols, final int rows, final int screenRows) {
        mColumns = cols;
        mTotalRows = rows;
        mScreenRows = screenRows;
        mLines = new TerminalRow[rows];
        blockSet(0, 0, cols, screenRows, ' ', TextStyle.NORMAL);
    }

    public int activeRows() {
        return activeTranscriptRows + mScreenRows;
    }

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
     * </pre> *
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    public int externalToInternalRow(final int externalRow) {
        final int internalRow = mScreenFirstRow + externalRow;
        return (0 > internalRow) ? (mTotalRows + internalRow) : (internalRow % mTotalRows);
    }

    public void setLineWrap(final int row) {
        mLines[externalToInternalRow(row)].mLineWrap = true;
    }

    public boolean getLineWrap(final int row) {
        return mLines[externalToInternalRow(row)].mLineWrap;
    }

    public void clearLineWrap(final int row) {
        mLines[externalToInternalRow(row)].mLineWrap = false;
    }

    /**
     * Resize the console which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the console should have.
     * @param newRows    The number of rows the console should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    public void resize(final int newColumns, final int newRows, final int newTotalRows, final int[] cursor, final long currentStyle, final boolean altScreen) { // newRows > mTotalRows should not normally happen since mTotalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == mColumns && newRows <= mTotalRows) { // Fast resize where just the rows changed.
            int shiftDownOfTopRow = mScreenRows - newRows;
            if (0 < shiftDownOfTopRow && shiftDownOfTopRow < mScreenRows) { // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for (int i = mScreenRows - 1; 0 < i; i--) {
                    if (cursor[1] >= i) break;
                    final int r = externalToInternalRow(i);
                    if (null == mLines[r] || mLines[r].isBlank()) {
                        --shiftDownOfTopRow;
                        if (0 == shiftDownOfTopRow) break;
                    }
                }
            } else if (0 > shiftDownOfTopRow) { // Negative shift down = expanding. Only move console up if there is transcript to show:
                final int actualShift = max(shiftDownOfTopRow, -activeTranscriptRows);

                if (shiftDownOfTopRow != actualShift) { // The new lines revealed by the resizing are not all from the transcript. Blank the below ones.
                    for (int i = 0; i < actualShift - shiftDownOfTopRow; i++)
                        allocateFullLineIfNecessary((mScreenFirstRow + mScreenRows + i) % mTotalRows).clear(currentStyle);
                    shiftDownOfTopRow = actualShift;
                }
            }
            mScreenFirstRow += shiftDownOfTopRow;
            mScreenFirstRow = (0 > mScreenFirstRow) ? (mScreenFirstRow + mTotalRows) : (mScreenFirstRow % mTotalRows);
            mTotalRows = newTotalRows;
            activeTranscriptRows = altScreen ? 0 : max(0, (activeTranscriptRows + shiftDownOfTopRow));
            cursor[1] -= shiftDownOfTopRow;
            mScreenRows = newRows;
        } else { // Copy away old state and update new:
            final TerminalRow[] oldLines = mLines;
            mLines = new TerminalRow[newTotalRows];
            for (int i = 0; i < newTotalRows; i++)
                mLines[i] = new TerminalRow(newColumns, currentStyle);
            final int oldActiveTranscriptRows = activeTranscriptRows;
            final int oldScreenFirstRow = mScreenFirstRow;
            final int oldScreenRows = mScreenRows;
            final int oldTotalRows = mTotalRows;
            mTotalRows = newTotalRows;
            mScreenRows = newRows;
            mColumns = newColumns;
            activeTranscriptRows = mScreenFirstRow = 0;
            int newCursorRow = -1;
            int newCursorColumn = -1;
            final int oldCursorRow = cursor[1];
            final int oldCursorColumn = cursor[0];
            boolean newCursorPlaced = false;
            int currentOutputExternalRow = 0;
            int currentOutputExternalColumn = 0; // Loop over every character in the initial state. // Blank lines should be skipped only if at end of transcript (just as is done in the "fast" resize), so we // keep track how many blank lines we have skipped if we later on find a non-blank line.
            int skippedBlankLines = 0;
            for (int externalOldRow = -oldActiveTranscriptRows; externalOldRow < oldScreenRows; externalOldRow++) { // Do what externalToInternalRow() does but for the old state:
                int internalOldRow = oldScreenFirstRow + externalOldRow;
                internalOldRow = (0 > internalOldRow) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);
                final TerminalRow oldLine = oldLines[internalOldRow];
                final boolean cursorAtThisRow = externalOldRow == oldCursorRow; // The cursor may only be on a non-null line, which we should not skip:
                if (null == oldLine || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank()) {
                    skippedBlankLines++;
                    continue;
                } else if (0 < skippedBlankLines) { // After skipping some blank lines we encounter a non-blank line. Insert the skipped blank lines.
                    for (int i = 0; i < skippedBlankLines; i++) {
                        if (currentOutputExternalRow == mScreenRows - 1)
                            scrollDownOneLine(0, mScreenRows, currentStyle);
                        else currentOutputExternalRow++;
                        currentOutputExternalColumn = 0;
                    }
                    skippedBlankLines = 0;
                }
                int lastNonSpaceIndex = 0;
                boolean justToCursor = false;
                if (cursorAtThisRow || oldLine.mLineWrap) { // Take the whole line, either because of cursor on it, or if line wrapping.
                    lastNonSpaceIndex = oldLine.mSpaceUsed;
                    if (cursorAtThisRow) justToCursor = true;
                } else {
                    for (int i = 0; i < oldLine.mSpaceUsed; i++)
                        if (' ' != oldLine.mText[i]) lastNonSpaceIndex = i + 1;
                }
                int currentOldCol = 0;
                long styleAtCol = 0;
                for (int i = 0; i < lastNonSpaceIndex; i++) { // Note that looping over java character, not cells.
                    final char c = oldLine.mText[i];
                    final int codePoint;
                    if (Character.isHighSurrogate(c)) {
                        ++i;
                        codePoint = Character.toCodePoint(c, oldLine.mText[i]);
                    } else {
                        codePoint = c;
                    }
                    final int displayWidth = WcWidth.width(codePoint); // Use the last style if this is a zero-width character:
                    if (0 < displayWidth)
                        styleAtCol = oldLine.mStyle[currentOldCol]; // Line wrap as necessary:

                    if (currentOutputExternalColumn + displayWidth > mColumns) {
                        setLineWrap(currentOutputExternalRow);
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            if (newCursorPlaced) newCursorRow--;
                            scrollDownOneLine(0, mScreenRows, currentStyle);
                        } else currentOutputExternalRow++;
                        currentOutputExternalColumn = 0;
                    }
                    final int offsetDueToCombiningChar = (0 >= displayWidth && 0 < currentOutputExternalColumn) ? 1 : 0;
                    final int outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar;
                    setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol);
                    if (0 < displayWidth) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn;
                            newCursorRow = currentOutputExternalRow;
                            newCursorPlaced = true;
                        }
                        currentOldCol += displayWidth;
                        currentOutputExternalColumn += displayWidth;
                        if (justToCursor && newCursorPlaced) break;
                    }
                } // Old row has been copied. Check if we need to insert newline if old line was not wrapping:
                if (externalOldRow != (oldScreenRows - 1) && !oldLine.mLineWrap) {
                    if (currentOutputExternalRow == mScreenRows - 1) {
                        if (newCursorPlaced) newCursorRow--;
                        scrollDownOneLine(0, mScreenRows, currentStyle);
                    } else currentOutputExternalRow++;

                    currentOutputExternalColumn = 0;
                }
            }
            cursor[0] = newCursorColumn;
            cursor[1] = newCursorRow;
        } // Handle cursor scrolling off console:
        if (0 > cursor[0] || 0 > cursor[1]) cursor[1] = cursor[0] = 0;

    }

    /**
     * Block copy lines and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of lines to be copied.
     */
    private void blockCopyLinesDown(final int srcInternal, final int len) {
        if (0 == len) return;
        final int start = len - 1; // Save away line to be overwritten:
        final var lineToBeOverWritten = mLines[(srcInternal + start + 1) % mTotalRows]; // Do the copy from bottom to top.
        for (int i = start; 0 <= i; --i)
            mLines[(srcInternal + i + 1) % mTotalRows] = mLines[(srcInternal + i) % mTotalRows]; // Put back overwritten line, now above the block:
        mLines[srcInternal % mTotalRows] = lineToBeOverWritten;
    }

    /**
     * Scroll the console down one line. To scroll the whole console of a 24 line console, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    public void scrollDownOneLine(final int topMargin, final int bottomMargin, final long style) { // Copy the fixed topMargin lines one line down so that they remain on console in same position:
        blockCopyLinesDown(mScreenFirstRow, topMargin); // Copy the fixed mScreenRows-bottomMargin lines one line down so that they remain on console in same
        // position:
        blockCopyLinesDown(externalToInternalRow(bottomMargin), mScreenRows - bottomMargin); // Update the console location in the ring buffer:
        mScreenFirstRow = (mScreenFirstRow + 1) % mTotalRows; // Note that the history has grown if not already full:
        if (activeTranscriptRows < mTotalRows - mScreenRows) activeTranscriptRows++;
        // Blank the newly revealed line above the bottom margin:
        final int blankRow = externalToInternalRow(bottomMargin - 1);
        if (null == mLines[blankRow]) mLines[blankRow] = new TerminalRow(mColumns, style);
        else mLines[blankRow].clear(style);

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
    public void blockCopy(final int sx, final int sy, final int w, final int h, final int dx, final int dy) {
        if (0 == w) return;
        final boolean copyingUp = sy > dy;
        for (int y = 0; y < h; y++) {
            final int y2 = copyingUp ? y : (h - (y + 1));
            final TerminalRow sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2));
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx);
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the console, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    public void blockSet(final int sx, final int sy, final int w, final int h, final int value, final long style) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                setChar(sx + x, sy + y, value, style);
    }

    public TerminalRow allocateFullLineIfNecessary(final int row) {
        return (null == mLines[row]) ? (mLines[row] = new TerminalRow(mColumns, 0)) : mLines[row];
    }

    public void setChar(final int column, final int row, final int codePoint, final long style) {
        allocateFullLineIfNecessary(externalToInternalRow(row)).setChar(column, codePoint, style);
    }

    public long getStyleAt(final int externalRow, final int column) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).mStyle[column];
    }


    /**
     * Support for [](<a href="http://vt100.net/docs/vt510-rm/DEC">...</a><a href=)">...">and <a href="http://vt100.net/docs/vt510-rm/DECCARA">...</a>
     */
    public void setOrClearEffect(final int bits, final boolean setOrClear, final boolean reverse, final boolean rectangular, final int leftMargin, final int rightMargin, final int top, final int left, final int bottom, final int right) {
        for (int y = top; y < bottom; y++) {
            final TerminalRow line = mLines[externalToInternalRow(y)];
            final int startOfLine = (rectangular || y == top) ? left : leftMargin;
            final int endOfLine = (rectangular || y + 1 == bottom) ? right : rightMargin;
            for (int x = startOfLine; x < endOfLine; x++) {
                final long currentStyle = line.mStyle[x];
                final int foreColor = TextStyle.decodeForeColor(currentStyle);
                final int backColor = TextStyle.decodeBackColor(currentStyle);
                int effect = TextStyle.decodeEffect(currentStyle);
                if (reverse)
                    effect = (effect & ~bits) | (bits & ~effect); // Clear out the bits to reverse and add them back in reversed:
                else if (setOrClear) effect |= bits;
                else effect &= ~bits;

                line.mStyle[x] = TextStyle.encode(foreColor, backColor, effect);
            }
        }
    }

    public void clearTranscript() {
        if (mScreenFirstRow < activeTranscriptRows) {
            Arrays.fill(mLines, mTotalRows + mScreenFirstRow - activeTranscriptRows, mTotalRows, null);
            Arrays.fill(mLines, 0, mScreenFirstRow, null);
        } else Arrays.fill(mLines, mScreenFirstRow - activeTranscriptRows, mScreenFirstRow, null);
        activeTranscriptRows = 0;
    }

    public String getSelectedText(final int selX1, int y1, final int selX2, int y2) {
        final StringBuilder builder = new StringBuilder();
        if (y1 < -activeTranscriptRows) y1 = -activeTranscriptRows;
        if (y2 >= mScreenRows) y2 = mScreenRows - 1;
        for (int row = y1; row <= y2; row++) {
            final int x1 = (row == y1) ? selX1 : 0;
            int x2;
            if (row == y2) {
                x2 = selX2 + 1;
                if (x2 > mColumns) x2 = mColumns;
            } else x2 = mColumns;

            final TerminalRow lineObject = mLines[externalToInternalRow(row)];
            final int x1Index = lineObject.findStartOfColumn(x1);
            int x2Index = (x2 < mColumns) ? lineObject.findStartOfColumn(x2) : lineObject.mSpaceUsed;
            if (x2Index == x1Index)
                x2Index = lineObject.findStartOfColumn(x2 + 1); // Selected the start of a wide character.
            final char[] line = lineObject.mText;
            int lastPrintingCharIndex = -1;
            final boolean rowLineWrap = getLineWrap(row);
            if (rowLineWrap && x2 == mColumns)
                lastPrintingCharIndex = x2Index - 1; // If the line was wrapped, we shouldn't lose trailing space:
            else {
                for (int i = x1; i < x2Index; i++)
                    if (' ' != line[i]) lastPrintingCharIndex = i;
            }
            final int len = lastPrintingCharIndex - x1Index + 1;
            if (-1 != lastPrintingCharIndex && 0 < len) builder.append(line, x1Index, len);
            if ((!rowLineWrap) && (row < y2) && (row < mScreenRows - 1)) builder.append('\n');
        }
        return builder.toString();
    }
}
