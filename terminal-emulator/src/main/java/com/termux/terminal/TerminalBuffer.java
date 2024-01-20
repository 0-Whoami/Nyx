package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A circular buffer of {@link TerminalRow}:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 * <p>
 * See {@link #externalToInternalRow(int)} for how to map from logical screen rows to array indices.
 */
public final class TerminalBuffer {

    private final HashMap<Integer, TerminalBitmap> bitmaps;
    /**
     * The length of {@link #mLines}.
     */
    int mTotalRows;
    /**
     * The number of rows and columns visible on the screen.
     */
    int mScreenRows, mColumns;
    private WorkingTerminalBitmap workingBitmap;
    private TerminalRow[] mLines;
    /**
     * The number of rows kept in history.
     */
    private int mActiveTranscriptRows;
    /**
     * The index in the circular buffer where the visible screen starts.
     */
    private int mScreenFirstRow;
    private boolean hasBitmaps;

    private long bitmapLastGC;

    /**
     * Create a transcript screen.
     *
     * @param columns    the width of the screen in characters.
     * @param totalRows  the height of the entire text area, in rows of text.
     * @param screenRows the height of just the screen, not including the transcript that holds lines that have scrolled off
     *                   the top of the screen.
     */
    public TerminalBuffer(final int columns, final int totalRows, final int screenRows) {
        this.mColumns = columns;
        this.mTotalRows = totalRows;
        this.mScreenRows = screenRows;
        this.mLines = new TerminalRow[totalRows];
        this.blockSet(0, 0, columns, screenRows, ' ', TextStyle.NORMAL);
        this.hasBitmaps = false;
        this.bitmaps = new HashMap<>();
        this.bitmapLastGC = SystemClock.uptimeMillis();
    }

    public int getActiveTranscriptRows() {
        return this.mActiveTranscriptRows;
    }

    public int getActiveRows() {
        return this.mActiveTranscriptRows + this.mScreenRows;
    }

    /**
     * Convert a row value from the public external coordinate system to our internal private coordinate system.
     *
     * <pre>
     * - External coordinate system: -mActiveTranscriptRows to mScreenRows-1, with the screen being 0..mScreenRows-1.
     * - Internal coordinate system: the mScreenRows lines starting at mScreenFirstRow comprise the screen, while the
     *   mActiveTranscriptRows lines ending at mScreenFirstRow-1 form the transcript (as a circular buffer).
     *
     * External ↔ Internal:
     *
     * [ ...                            ]     [ ...                                     ]
     * [ -mActiveTranscriptRows         ]     [ mScreenFirstRow - mActiveTranscriptRows ]
     * [ ...                            ]     [ ...                                     ]
     * [ 0 (visible screen starts here) ]  ↔  [ mScreenFirstRow                         ]
     * [ ...                            ]     [ ...                                     ]
     * [ mScreenRows-1                  ]     [ mScreenFirstRow + mScreenRows-1         ]
     * </pre>
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    public int externalToInternalRow(final int externalRow) {
        if (externalRow < -this.mActiveTranscriptRows || externalRow > this.mScreenRows)
            throw new IllegalArgumentException("extRow=" + externalRow + ", mScreenRows=" + this.mScreenRows + ", mActiveTranscriptRows=" + this.mActiveTranscriptRows);
        int internalRow = this.mScreenFirstRow + externalRow;
        return (0 > internalRow) ? (this.mTotalRows + internalRow) : (internalRow % this.mTotalRows);
    }

    public void setLineWrap(final int row) {
        this.mLines[this.externalToInternalRow(row)].mLineWrap = true;
    }

    public boolean getLineWrap(final int row) {
        return this.mLines[this.externalToInternalRow(row)].mLineWrap;
    }

    public void clearLineWrap(final int row) {
        this.mLines[this.externalToInternalRow(row)].mLineWrap = false;
    }

    /**
     * Resize the screen which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the screen should have.
     * @param newRows    The number of rows the screen should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    public void resize(final int newColumns, final int newRows, final int newTotalRows, final int[] cursor, final long currentStyle, final boolean altScreen) {
        // newRows > mTotalRows should not normally happen since mTotalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == this.mColumns && newRows <= this.mTotalRows) {
            // Fast resize where just the rows changed.
            int shiftDownOfTopRow = this.mScreenRows - newRows;
            if (0 < shiftDownOfTopRow && shiftDownOfTopRow < this.mScreenRows) {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for (int i = this.mScreenRows - 1; 0 < i; i--) {
                    if (cursor[1] >= i)
                        break;
                    final int r = this.externalToInternalRow(i);
                    if (null == mLines[r] || this.mLines[r].isBlank()) {
                        if (0 == --shiftDownOfTopRow)
                            break;
                    }
                }
            } else if (0 > shiftDownOfTopRow) {
                // Negative shift down = expanding. Only move screen up if there is transcript to show:
                final int actualShift = Math.max(shiftDownOfTopRow, -this.mActiveTranscriptRows);
                if (shiftDownOfTopRow != actualShift) {
                    // The new lines revealed by the resizing are not all from the transcript. Blank the below ones.
                    for (int i = 0; i < actualShift - shiftDownOfTopRow; i++)
                        this.allocateFullLineIfNecessary((this.mScreenFirstRow + this.mScreenRows + i) % this.mTotalRows).clear(currentStyle);
                    shiftDownOfTopRow = actualShift;
                }
            }
            this.mScreenFirstRow += shiftDownOfTopRow;
            this.mScreenFirstRow = (0 > mScreenFirstRow) ? (this.mScreenFirstRow + this.mTotalRows) : (this.mScreenFirstRow % this.mTotalRows);
            this.mTotalRows = newTotalRows;
            this.mActiveTranscriptRows = altScreen ? 0 : Math.max(0, this.mActiveTranscriptRows + shiftDownOfTopRow);
            cursor[1] -= shiftDownOfTopRow;
            this.mScreenRows = newRows;
        } else {
            // Copy away old state and update new:
            final TerminalRow[] oldLines = this.mLines;
            this.mLines = new TerminalRow[newTotalRows];
            for (int i = 0; i < newTotalRows; i++)
                this.mLines[i] = new TerminalRow(newColumns, currentStyle);
            int oldActiveTranscriptRows = this.mActiveTranscriptRows;
            int oldScreenFirstRow = this.mScreenFirstRow;
            int oldScreenRows = this.mScreenRows;
            int oldTotalRows = this.mTotalRows;
            this.mTotalRows = newTotalRows;
            this.mScreenRows = newRows;
            this.mActiveTranscriptRows = this.mScreenFirstRow = 0;
            this.mColumns = newColumns;
            int newCursorRow = -1;
            int newCursorColumn = -1;
            final int oldCursorRow = cursor[1];
            final int oldCursorColumn = cursor[0];
            boolean newCursorPlaced = false;
            int currentOutputExternalRow = 0;
            int currentOutputExternalColumn = 0;
            // Loop over every character in the initial state.
            // Blank lines should be skipped only if at end of transcript (just as is done in the "fast" resize), so we
            // keep track how many blank lines we have skipped if we later on find a non-blank line.
            int skippedBlankLines = 0;
            for (int externalOldRow = -oldActiveTranscriptRows; externalOldRow < oldScreenRows; externalOldRow++) {
                // Do what externalToInternalRow() does but for the old state:
                int internalOldRow = oldScreenFirstRow + externalOldRow;
                internalOldRow = (0 > internalOldRow) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);
                final TerminalRow oldLine = oldLines[internalOldRow];
                final boolean cursorAtThisRow = externalOldRow == oldCursorRow;
                // The cursor may only be on a non-null line, which we should not skip:
                if (null == oldLine || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank()) {
                    skippedBlankLines++;
                    continue;
                } else if (0 < skippedBlankLines) {
                    // After skipping some blank lines we encounter a non-blank line. Insert the skipped blank lines.
                    for (int i = 0; i < skippedBlankLines; i++) {
                        if (currentOutputExternalRow == this.mScreenRows - 1) {
                            this.scrollDownOneLine(0, this.mScreenRows, currentStyle);
                        } else {
                            currentOutputExternalRow++;
                        }
                        currentOutputExternalColumn = 0;
                    }
                    skippedBlankLines = 0;
                }
                int lastNonSpaceIndex = 0;
                boolean justToCursor = false;
                if (cursorAtThisRow || oldLine.mLineWrap) {
                    // Take the whole line, either because of cursor on it, or if line wrapping.
                    lastNonSpaceIndex = oldLine.getSpaceUsed();
                    if (cursorAtThisRow)
                        justToCursor = true;
                } else {
                    for (int i = 0; i < oldLine.getSpaceUsed(); i++) {
                        if (' ' != oldLine.mText[i])
                            lastNonSpaceIndex = i + 1;
                    }
                }
                int currentOldCol = 0;
                long styleAtCol = 0;
                for (int i = 0; i < lastNonSpaceIndex; i++) {
                    // Note that looping over java character, not cells.
                    final char c = oldLine.mText[i];
                    final int codePoint;
                    if ((Character.isHighSurrogate(c))) {
                        ++i;
                        codePoint = Character.toCodePoint(c, oldLine.mText[i]);
                    } else {
                        codePoint = c;
                    }
                    final int displayWidth = WcWidth.width(codePoint);
                    // Use the last style if this is a zero-width character:
                    if (0 < displayWidth)
                        styleAtCol = oldLine.getStyle(currentOldCol);
                    // Line wrap as necessary:
                    if (currentOutputExternalColumn + displayWidth > this.mColumns) {
                        this.setLineWrap(currentOutputExternalRow);
                        if (currentOutputExternalRow == this.mScreenRows - 1) {
                            if (newCursorPlaced)
                                newCursorRow--;
                            this.scrollDownOneLine(0, this.mScreenRows, currentStyle);
                        } else {
                            currentOutputExternalRow++;
                        }
                        currentOutputExternalColumn = 0;
                    }
                    final int offsetDueToCombiningChar = ((0 >= displayWidth && 0 < currentOutputExternalColumn) ? 1 : 0);
                    final int outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar;
                    this.setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol);
                    if (0 < displayWidth) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn;
                            newCursorRow = currentOutputExternalRow;
                            newCursorPlaced = true;
                        }
                        currentOldCol += displayWidth;
                        currentOutputExternalColumn += displayWidth;
                        if (justToCursor && newCursorPlaced)
                            break;
                    }
                }
                // Old row has been copied. Check if we need to insert newline if old line was not wrapping:
                if (externalOldRow != (oldScreenRows - 1) && !oldLine.mLineWrap) {
                    if (currentOutputExternalRow == this.mScreenRows - 1) {
                        if (newCursorPlaced)
                            newCursorRow--;
                        this.scrollDownOneLine(0, this.mScreenRows, currentStyle);
                    } else {
                        currentOutputExternalRow++;
                    }
                    currentOutputExternalColumn = 0;
                }
            }
            cursor[0] = newCursorColumn;
            cursor[1] = newCursorRow;
        }
        // Handle cursor scrolling off screen:
        if (0 > cursor[0] || 0 > cursor[1])
            cursor[0] = cursor[1] = 0;
    }

    /**
     * Block copy lines and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of lines to be copied.
     */
    private void blockCopyLinesDown(final int srcInternal, final int len) {
        if (0 == len)
            return;
        final int totalRows = this.mTotalRows;
        final int start = len - 1;
        // Save away line to be overwritten:
        final TerminalRow lineToBeOverWritten = this.mLines[(srcInternal + start + 1) % totalRows];
        // Do the copy from bottom to top.
        for (int i = start; 0 <= i; --i)
            this.mLines[(srcInternal + i + 1) % totalRows] = this.mLines[(srcInternal + i) % totalRows];
        // Put back overwritten line, now above the block:
        this.mLines[(srcInternal) % totalRows] = lineToBeOverWritten;
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line screen, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    public void scrollDownOneLine(final int topMargin, final int bottomMargin, final long style) {
        if (topMargin > bottomMargin - 1 || 0 > topMargin || bottomMargin > this.mScreenRows)
            throw new IllegalArgumentException("topMargin=" + topMargin + ", bottomMargin=" + bottomMargin + ", mScreenRows=" + this.mScreenRows);
        // Copy the fixed topMargin lines one line down so that they remain on screen in same position:
        this.blockCopyLinesDown(this.mScreenFirstRow, topMargin);
        // Copy the fixed mScreenRows-bottomMargin lines one line down so that they remain on screen in same
        // position:
        this.blockCopyLinesDown(this.externalToInternalRow(bottomMargin), this.mScreenRows - bottomMargin);
        // Update the screen location in the ring buffer:
        this.mScreenFirstRow = (this.mScreenFirstRow + 1) % this.mTotalRows;
        // Note that the history has grown if not already full:
        if (this.mActiveTranscriptRows < this.mTotalRows - this.mScreenRows)
            this.mActiveTranscriptRows++;
        // Blank the newly revealed line above the bottom margin:
        final int blankRow = this.externalToInternalRow(bottomMargin - 1);
        if (null == mLines[blankRow]) {
            this.mLines[blankRow] = new TerminalRow(this.mColumns, style);
        } else {
            // find if a bitmap is completely scrolled out
            final Collection<Integer> used = new HashSet<>();
            if (this.mLines[blankRow].mHasBitmap) {
                for (int column = 0; column < this.mColumns; column++) {
                    long st = this.mLines[blankRow].getStyle(column);
                    if (TextStyle.isBitmap(st)) {
                        used.add((int) (st >> 16) & 0xffff);
                    }
                }
                final TerminalRow nextLine = this.mLines[(blankRow + 1) % this.mTotalRows];
                if (nextLine.mHasBitmap) {
                    for (int column = 0; column < this.mColumns; column++) {
                        long st = nextLine.getStyle(column);
                        if (TextStyle.isBitmap(st)) {
                            used.remove((int) (st >> 16) & 0xffff);
                        }
                    }
                }
                for (final Integer bm : used) {
                    this.bitmaps.remove(bm);
                }
            }
            this.mLines[blankRow].clear(style);
        }
    }

    /**
     * Block copy characters from one position in the screen to another. The two positions can overlap. All characters
     * of the source and destination must be within the bounds of the screen, or else an InvalidParameterException will
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
        if (0 == w)
            return;
        if (0 > sx || sx + w > this.mColumns || 0 > sy || sy + h > this.mScreenRows || 0 > dx || dx + w > this.mColumns || 0 > dy || dy + h > this.mScreenRows)
            throw new IllegalArgumentException();
        final boolean copyingUp = sy > dy;
        for (int y = 0; y < h; y++) {
            final int y2 = copyingUp ? y : (h - (y + 1));
            final TerminalRow sourceRow = this.allocateFullLineIfNecessary(this.externalToInternalRow(sy + y2));
            this.allocateFullLineIfNecessary(this.externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx);
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the screen, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    public void blockSet(final int sx, final int sy, final int w, final int h, final int val, final long style) {
        if (0 > sx || sx + w > this.mColumns || 0 > sy || sy + h > this.mScreenRows) {
            throw new IllegalArgumentException("Illegal arguments! blockSet(" + sx + ", " + sy + ", " + w + ", " + h + ", " + val + ", " + this.mColumns + ", " + this.mScreenRows + ")");
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) this.setChar(sx + x, sy + y, val, style);
            if (sx + w == this.mColumns && ' ' == val) {
                this.clearLineWrap(sy + y);
            }
        }
    }

    public TerminalRow allocateFullLineIfNecessary(final int row) {
        return (null == mLines[row]) ? (this.mLines[row] = new TerminalRow(this.mColumns, 0)) : this.mLines[row];
    }

    public void setChar(final int column, int row, final int codePoint, final long style) {
        if (0 > row || row >= this.mScreenRows || 0 > column || column >= this.mColumns)
            throw new IllegalArgumentException("TerminalBuffer.setChar(): row=" + row + ", column=" + column + ", mScreenRows=" + this.mScreenRows + ", mColumns=" + this.mColumns);
        row = this.externalToInternalRow(row);
        this.allocateFullLineIfNecessary(row).setChar(column, codePoint, style);
    }

    public long getStyleAt(final int externalRow, final int column) {
        return this.allocateFullLineIfNecessary(this.externalToInternalRow(externalRow)).getStyle(column);
    }

    /**
     * Support for <a href="http://vt100.net/docs/vt510-rm/DEC<a href="CARA">...</a>">and http://vt100.net/doc</a>s/vt510-rm/DECCARA
     */
    public void setOrClearEffect(final int bits, final boolean setOrClear, final boolean reverse, final boolean rectangular, final int leftMargin, final int rightMargin, final int top, final int left, final int bottom, final int right) {
        for (int y = top; y < bottom; y++) {
            final TerminalRow line = this.mLines[this.externalToInternalRow(y)];
            final int startOfLine = (rectangular || y == top) ? left : leftMargin;
            final int endOfLine = (rectangular || y + 1 == bottom) ? right : rightMargin;
            for (int x = startOfLine; x < endOfLine; x++) {
                final long currentStyle = line.getStyle(x);
                final int foreColor = TextStyle.decodeForeColor(currentStyle);
                final int backColor = TextStyle.decodeBackColor(currentStyle);
                int effect = TextStyle.decodeEffect(currentStyle);
                if (reverse) {
                    // Clear out the bits to reverse and add them back in reversed:
                    effect = (effect & ~bits) | (bits & ~effect);
                } else if (setOrClear) {
                    effect |= bits;
                } else {
                    effect &= ~bits;
                }
                line.mStyle[x] = TextStyle.encode(foreColor, backColor, effect);
            }
        }
    }

    public void clearTranscript() {
        if (this.mScreenFirstRow < this.mActiveTranscriptRows) {
            Arrays.fill(this.mLines, this.mTotalRows + this.mScreenFirstRow - this.mActiveTranscriptRows, this.mTotalRows, null);
            Arrays.fill(this.mLines, 0, this.mScreenFirstRow, null);
        } else {
            Arrays.fill(this.mLines, this.mScreenFirstRow - this.mActiveTranscriptRows, this.mScreenFirstRow, null);
        }
        this.mActiveTranscriptRows = 0;
        this.bitmaps.clear();
        this.hasBitmaps = false;
    }

    public Bitmap getSixelBitmap(final long style) {
        return this.bitmaps.get(TextStyle.bitmapNum(style)).bitmap;
    }

    public Rect getSixelRect(final long style) {
        final TerminalBitmap bm = this.bitmaps.get(TextStyle.bitmapNum(style));
        final int x = TextStyle.bitmapX(style);
        final int y = TextStyle.bitmapY(style);
        return new Rect(x * bm.cellWidth, y * bm.cellHeight, (x + 1) * bm.cellWidth, (y + 1) * bm.cellHeight);

    }

    public void sixelStart(final int width, final int height) {
        this.workingBitmap = new WorkingTerminalBitmap(width, height);
    }

    public void sixelChar(final int c, final int rep) {
        this.workingBitmap.sixelChar(c, rep);
    }

    public void sixelSetColor(final int col) {
        this.workingBitmap.sixelSetColor(col);
    }

    public void sixelSetColor(final int col, final int r, final int g, final int b) {
        this.workingBitmap.sixelSetColor(col, r, g, b);
    }

    private int findFreeBitmap() {
        int i = 0;
        while (this.bitmaps.containsKey(i)) {
            i++;
        }
        return i;
    }

    public int sixelEnd(final int Y, final int X, final int cellW, final int cellH) {
        final int num = this.findFreeBitmap();
        this.bitmaps.put(num, new TerminalBitmap(num, this.workingBitmap, Y, X, cellW, cellH, this));
        this.workingBitmap = null;
        if (null == bitmaps.get(num).bitmap) {
            this.bitmaps.remove(num);
            return 0;
        }
        this.hasBitmaps = true;
        this.bitmapGC(30000);
        return this.bitmaps.get(num).scrollLines;
    }

    public int[] addImage(final byte[] image, final int Y, final int X, final int cellW, final int cellH, final int width, final int height, final boolean aspect) {
        final int num = this.findFreeBitmap();
        this.bitmaps.put(num, new TerminalBitmap(num, image, Y, X, cellW, cellH, width, height, aspect, this));
        if (null == bitmaps.get(num).bitmap) {
            this.bitmaps.remove(num);
            return new int[]{0, 0};
        }
        this.hasBitmaps = true;
        this.bitmapGC(30000);
        return this.bitmaps.get(num).cursorDelta;
    }

    public void bitmapGC(final int timeDelta) {
        if (!this.hasBitmaps || this.bitmapLastGC + timeDelta > SystemClock.uptimeMillis()) {
            return;
        }
        final Collection<Integer> used = new HashSet<>();
        for (final TerminalRow mLine : this.mLines) {
            if (null != mLine && mLine.mHasBitmap) {
                for (int column = 0; column < this.mColumns; column++) {
                    long st = mLine.getStyle(column);
                    if (TextStyle.isBitmap(st)) {
                        used.add((int) (st >> 16) & 0xffff);
                    }
                }
            }
        }
        final Iterable<Integer> keys = new HashSet<>(this.bitmaps.keySet());
        for (final Integer bn : keys) {
            if (!used.contains(bn)) {
                this.bitmaps.remove(bn);
            }
        }
        this.bitmapLastGC = SystemClock.uptimeMillis();
    }

    public String getSelectedText(final int selX1, int selY1, final int selX2, int selY2) {
        StringBuilder builder = new StringBuilder();
        int columns = this.mColumns;
        if (selY1 < -this.mActiveTranscriptRows)
            selY1 = -this.mActiveTranscriptRows;
        if (selY2 >= this.mScreenRows)
            selY2 = this.mScreenRows - 1;
        for (int row = selY1; row <= selY2; row++) {
            final int x1 = (row == selY1) ? selX1 : 0;
            int x2;
            if (row == selY2) {
                x2 = selX2 + 1;
                if (x2 > columns)
                    x2 = columns;
            } else {
                x2 = columns;
            }
            final TerminalRow lineObject = this.mLines[this.externalToInternalRow(row)];
            final int x1Index = lineObject.findStartOfColumn(x1);
            int x2Index = (x2 < this.mColumns) ? lineObject.findStartOfColumn(x2) : lineObject.getSpaceUsed();
            if (x2Index == x1Index) {
                // Selected the start of a wide character.
                x2Index = lineObject.findStartOfColumn(x2 + 1);
            }
            final char[] line = lineObject.mText;
            int lastPrintingCharIndex = -1;
            int i;
            final boolean rowLineWrap = this.getLineWrap(row);
            if (rowLineWrap && x2 == columns) {
                // If the line was wrapped, we shouldn't lose trailing space:
                lastPrintingCharIndex = x2Index - 1;
            } else {
                for (i = x1Index; i < x2Index; ++i) {
                    final char c = line[i];
                    if (' ' != c)
                        lastPrintingCharIndex = i;
                }
            }
            final int len = lastPrintingCharIndex - x1Index + 1;
            if (-1 != lastPrintingCharIndex && 0 < len)
                builder.append(line, x1Index, len);
            if ((!rowLineWrap) && row < selY2 && row < this.mScreenRows - 1)
                builder.append('\n');
        }
        return builder.toString();
    }
}
