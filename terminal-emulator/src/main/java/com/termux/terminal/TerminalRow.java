package com.termux.terminal;

import java.util.Arrays;

/**
 * A row in a terminal, composed of a fixed number of cells.
 * <p>
 * The text in the row is stored in a char[] array, {@link #mText}, for quick access during rendering.
 */
public final class TerminalRow {

    private static final float SPARE_CAPACITY_FACTOR = 1.5f;
    /**
     * The style bits of each cell in the row. See {@link TextStyle}.
     */
    final long[] mStyle;
    /**
     * The number of columns in this terminal row.
     */
    private final int mColumns;
    /**
     * The text filling this terminal row.
     */
    public char[] mText;
    /**
     * If this row has a bitmap. Used for performace only
     */
    public boolean mHasBitmap;
    /**
     * If this row has been line wrapped due to text output at the end of line.
     */
    boolean mLineWrap;
    /**
     * The number of java char:s used in {@link #mText}.
     */
    private short mSpaceUsed;
    /**
     * If this row might contain chars with width != 1, used for deactivating fast path
     */
    private boolean mHasNonOneWidthOrSurrogateChars;

    /**
     * Construct a blank row (containing only whitespace, ' ') with a specified style.
     */
    public TerminalRow(final int columns, final long style) {
        this.mColumns = columns;
        this.mText = new char[(int) (TerminalRow.SPARE_CAPACITY_FACTOR * columns)];
        this.mStyle = new long[columns];
        this.clear(style);
    }

    /**
     * NOTE: The sourceX2 is exclusive.
     */
    public void copyInterval(final TerminalRow line, int sourceX1, final int sourceX2, int destinationX) {
        this.mHasNonOneWidthOrSurrogateChars |= line.mHasNonOneWidthOrSurrogateChars;
        int x1 = line.findStartOfColumn(sourceX1);
        int x2 = line.findStartOfColumn(sourceX2);
        boolean startingFromSecondHalfOfWideChar = (0 < sourceX1 && line.wideDisplayCharacterStartingAt(sourceX1 - 1));
        char[] sourceChars = (this == line) ? Arrays.copyOf(line.mText, line.mText.length) : line.mText;
        int latestNonCombiningWidth = 0;
        for (int i = x1; i < x2; i++) {
            final char sourceChar = sourceChars[i];
            int codePoint;
            if (Character.isHighSurrogate(sourceChar)) {
                ++i;
                codePoint = Character.toCodePoint(sourceChar, sourceChars[i]);
            } else {
                codePoint = sourceChar;
            }
            if (startingFromSecondHalfOfWideChar) {
                // Just treat copying second half of wide char as copying whitespace.
                codePoint = ' ';
                startingFromSecondHalfOfWideChar = false;
            }
            final int w = WcWidth.width(codePoint);
            if (0 < w) {
                destinationX += latestNonCombiningWidth;
                sourceX1 += latestNonCombiningWidth;
                latestNonCombiningWidth = w;
            }
            this.setChar(destinationX, codePoint, line.getStyle(sourceX1));
        }
    }

    public int getSpaceUsed() {
        return this.mSpaceUsed;
    }

    /**
     * Note that the column may end of second half of wide character.
     */
    public int findStartOfColumn(final int column) {
        if (column == this.mColumns)
            return this.getSpaceUsed();
        int currentColumn = 0;
        int currentCharIndex = 0;
        while (true) {
            // 0<2 1 < 2
            int newCharIndex = currentCharIndex;
            // cci=1, cci=2
            final char c = this.mText[newCharIndex];
            newCharIndex++;
            final boolean isHigh = Character.isHighSurrogate(c);
            final int codePoint;
            if (isHigh) {
                codePoint = Character.toCodePoint(c, mText[newCharIndex]);
                newCharIndex++;
            } else {
                codePoint = c;
            }
            // 1, 2
            final int wcwidth = WcWidth.width(codePoint);
            if (0 < wcwidth) {
                currentColumn += wcwidth;
                if (currentColumn == column) {
                    while (newCharIndex < this.mSpaceUsed) {
                        // Skip combining chars.
                        if (Character.isHighSurrogate(this.mText[newCharIndex])) {
                            if (0 >= WcWidth.width(Character.toCodePoint(mText[newCharIndex], mText[newCharIndex + 1]))) {
                                newCharIndex += 2;
                            } else {
                                break;
                            }
                        } else if (0 >= WcWidth.width(mText[newCharIndex])) {
                            newCharIndex++;
                        } else {
                            break;
                        }
                    }
                    return newCharIndex;
                } else if (currentColumn > column) {
                    // Wide column going past end.
                    return currentCharIndex;
                }
            }
            currentCharIndex = newCharIndex;
        }
    }

    private boolean wideDisplayCharacterStartingAt(final int column) {
        for (int currentCharIndex = 0, currentColumn = 0; currentCharIndex < this.mSpaceUsed; ) {
            final char c = this.mText[currentCharIndex];
            currentCharIndex++;
            final int codePoint;
            if (Character.isHighSurrogate(c)) {
                codePoint = Character.toCodePoint(c, mText[currentCharIndex]);
                currentCharIndex++;
            } else {
                codePoint = c;
            }
            final int wcwidth = WcWidth.width(codePoint);
            if (0 < wcwidth) {
                if (currentColumn == column && 2 == wcwidth)
                    return true;
                currentColumn += wcwidth;
                if (currentColumn > column)
                    return false;
            }
        }
        return false;
    }

    public void clear(final long style) {
        Arrays.fill(this.mText, ' ');
        Arrays.fill(this.mStyle, style);
        this.mSpaceUsed = (short) this.mColumns;
        this.mHasNonOneWidthOrSurrogateChars = false;
        this.mHasBitmap = false;
    }

    // https://github.com/steven676/Android-Terminal-Emulator/commit/9a47042620bec87617f0b4f5d50568535668fe26
    public void setChar(int columnToSet, final int codePoint, final long style) {
        if (0 > columnToSet || columnToSet >= this.mStyle.length)
            throw new IllegalArgumentException("TerminalRow.setChar(): columnToSet=" + columnToSet + ", codePoint=" + codePoint + ", style=" + style);
        this.mStyle[columnToSet] = style;
        if (!this.mHasBitmap && TextStyle.isBitmap(style)) {
            this.mHasBitmap = true;
        }
        int newCodePointDisplayWidth = WcWidth.width(codePoint);
        // Fast path when we don't have any chars with width != 1
        if (!this.mHasNonOneWidthOrSurrogateChars) {
            if (Character.MIN_SUPPLEMENTARY_CODE_POINT <= codePoint || 1 != newCodePointDisplayWidth) {
                this.mHasNonOneWidthOrSurrogateChars = true;
            } else {
                this.mText[columnToSet] = (char) codePoint;
                return;
            }
        }
        boolean newIsCombining = 0 >= newCodePointDisplayWidth;
        final boolean wasExtraColForWideChar = (0 < columnToSet) && this.wideDisplayCharacterStartingAt(columnToSet - 1);
        if (newIsCombining) {
            // When standing at second half of wide character and inserting combining:
            if (wasExtraColForWideChar)
                columnToSet--;
        } else {
            // Check if we are overwriting the second half of a wide character starting at the previous column:
            if (wasExtraColForWideChar)
                this.setChar(columnToSet - 1, ' ', style);
            // Check if we are overwriting the first half of a wide character starting at the next column:
            final boolean overwritingWideCharInNextColumn = 2 == newCodePointDisplayWidth && this.wideDisplayCharacterStartingAt(columnToSet + 1);
            if (overwritingWideCharInNextColumn)
                this.setChar(columnToSet + 1, ' ', style);
        }
        char[] text = this.mText;
        int oldStartOfColumnIndex = this.findStartOfColumn(columnToSet);
        int oldCodePointDisplayWidth = WcWidth.width(text, oldStartOfColumnIndex);
        // Get the number of elements in the mText array this column uses now
        final int oldCharactersUsedForColumn;
        if (columnToSet + oldCodePointDisplayWidth < this.mColumns) {
            oldCharactersUsedForColumn = this.findStartOfColumn(columnToSet + oldCodePointDisplayWidth) - oldStartOfColumnIndex;
        } else {
            // Last character.
            oldCharactersUsedForColumn = this.mSpaceUsed - oldStartOfColumnIndex;
        }
        // Find how many chars this column will need
        int newCharactersUsedForColumn = Character.charCount(codePoint);
        if (newIsCombining) {
            // Combining characters are added to the contents of the column instead of overwriting them, so that they
            // modify the existing contents.
            // FIXME: Put a limit of combining characters.
            // FIXME: Unassigned characters also get width=0.
            newCharactersUsedForColumn += oldCharactersUsedForColumn;
        }
        final int oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn;
        final int newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn;
        int javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn;
        if (0 < javaCharDifference) {
            // Shift the rest of the line right.
            final int oldCharactersAfterColumn = this.mSpaceUsed - oldNextColumnIndex;
            if (this.mSpaceUsed + javaCharDifference > text.length) {
                // We need to grow the array
                final char[] newText = new char[text.length + this.mColumns];
                System.arraycopy(text, 0, newText, 0, oldStartOfColumnIndex + oldCharactersUsedForColumn);
                System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn);
                this.mText = text = newText;
            } else {
                System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn);
            }
        } else if (0 > javaCharDifference) {
            // Shift the rest of the line left.
            System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, this.mSpaceUsed - oldNextColumnIndex);
        }
        this.mSpaceUsed += (short) javaCharDifference;
        // Store char. A combining character is stored at the end of the existing contents so that it modifies them:
        //noinspection ResultOfMethodCallIgnored - since we already now how many java chars is used.
        Character.toChars(codePoint, text, oldStartOfColumnIndex + (newIsCombining ? oldCharactersUsedForColumn : 0));
        if (2 == oldCodePointDisplayWidth && 1 == newCodePointDisplayWidth) {
            // Replace second half of wide char with a space. Which mean that we actually add a ' ' java character.
            if (this.mSpaceUsed + 1 > text.length) {
                final char[] newText = new char[text.length + this.mColumns];
                System.arraycopy(text, 0, newText, 0, newNextColumnIndex);
                System.arraycopy(text, newNextColumnIndex, newText, newNextColumnIndex + 1, this.mSpaceUsed - newNextColumnIndex);
                this.mText = text = newText;
            } else {
                System.arraycopy(text, newNextColumnIndex, text, newNextColumnIndex + 1, this.mSpaceUsed - newNextColumnIndex);
            }
            text[newNextColumnIndex] = ' ';
            ++this.mSpaceUsed;
        } else if (1 == oldCodePointDisplayWidth && 2 == newCodePointDisplayWidth) {
            if (columnToSet == this.mColumns - 1) {
                throw new IllegalArgumentException("Cannot put wide character in last column");
            } else if (columnToSet == this.mColumns - 2) {
                // Truncate the line to the second part of this wide char:
                this.mSpaceUsed = (short) newNextColumnIndex;
            } else {
                // Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
                // check at the beginning of this method we know that we are not overwriting a wide char.
                final int newNextNextColumnIndex = newNextColumnIndex + (Character.isHighSurrogate(this.mText[newNextColumnIndex]) ? 2 : 1);
                final int nextLen = newNextNextColumnIndex - newNextColumnIndex;
                // Shift the array leftwards.
                System.arraycopy(text, newNextNextColumnIndex, text, newNextColumnIndex, this.mSpaceUsed - newNextNextColumnIndex);
                this.mSpaceUsed -= (short) nextLen;
            }
        }
    }

    boolean isBlank() {
        for (int charIndex = 0, charLen = this.getSpaceUsed(); charIndex < charLen; charIndex++)
            if (' ' != mText[charIndex])
                return false;
        return true;
    }

    public long getStyle(final int column) {
        return this.mStyle[column];
    }
}
