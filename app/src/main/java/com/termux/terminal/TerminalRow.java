package com.termux.terminal;

import java.util.Arrays;

/**
 * A row in a terminal, composed of a fixed number of cells.
 * <p>
 * <p>
 * The text in the row is stored in a char[] array, [.mText], for quick access during rendering.
 */
public final class TerminalRow {
    /**
     * The style bits of each cell in the row. See [TextStyle].
     */

    public final long[] mStyle;
    private final int mColumns;
    /**
     * The text filling this terminal row.
     */

    public char[] mText;


    /**
     * If this row has been line wrapped due to text output at the end of line.
     */
    public boolean mLineWrap;

    /**
     * The number of java char:s used in [.mText].
     */
    public short mSpaceUsed;
    /**
     * If this row might contain chars with width != 1, used for deactivating fast path
     */
    private boolean mHasNonOneWidthOrSurrogateChars;

    /**
     * Construct a blank row (containing only whitespace, ' ') with a specified style.
     */
    public TerminalRow(final int colm, final long style) {
        this.mColumns = colm;
        this.mStyle = new long[colm];
        this.mText = new char[colm];
        this.clear(style);
    }

    /**
     * NOTE: The sourceX2 is exclusive.
     */
    public void copyInterval(final TerminalRow line, int sourceX1, final int sourceX2, int destinationX) {
        this.mHasNonOneWidthOrSurrogateChars |= line.mHasNonOneWidthOrSurrogateChars;
        final int x1 = line.findStartOfColumn(sourceX1);
        final int x2 = line.findStartOfColumn(sourceX2);
        boolean startingFromSecondHalfOfWideChar = (0 < sourceX1 && line.wideDisplayCharacterStartingAt(sourceX1 - 1));
        final char[] sourceChars = line.mText;
        var latestNonCombiningWidth = 0;
        for (int i = x1; i < x2; i++) {
            final char sourceChar = sourceChars[i];
            int codePoint;
            if (Character.isHighSurrogate(sourceChar)) {
                ++i;
                codePoint = Character.toCodePoint(sourceChar, sourceChars[i]);
            } else {
                codePoint = sourceChar;
            }
            if (startingFromSecondHalfOfWideChar) { // Just treat copying second half of wide char as copying whitespace.
                codePoint = ' ';
                startingFromSecondHalfOfWideChar = false;
            }
            final int w = WcWidth.width(codePoint);
            if (0 < w) {
                destinationX += latestNonCombiningWidth;
                sourceX1 += latestNonCombiningWidth;
                latestNonCombiningWidth = w;
            }
            this.setChar(destinationX, codePoint, line.mStyle[sourceX1]);
        }
    }

    /**
     * Note that the column may end of second half of wide character.
     */
    public int findStartOfColumn(final int column) {
        if (column == this.mColumns) return this.mSpaceUsed;
        int currentColumn = 0;
        int currentCharIndex = 0;
        while (true) { // 0<2 1 < 2
            int newCharIndex = currentCharIndex; // cci=1, cci=2
            final char c = this.mText[newCharIndex];
            newCharIndex++;
            final int codePoint; // 1, 2
            if (Character.isHighSurrogate(c)) {
                codePoint = Character.toCodePoint(c, this.mText[newCharIndex]);
                newCharIndex++;
            } else {
                codePoint = c;
            }
            final int wcwidth = WcWidth.width(codePoint);
            if (0 < wcwidth) {
                currentColumn += wcwidth;
                if (currentColumn == column) {
                    while (newCharIndex < this.mSpaceUsed) { // Skip combining chars.
                        if (Character.isHighSurrogate(this.mText[newCharIndex])) {
                            if (0 >= WcWidth.width(Character.toCodePoint(this.mText[newCharIndex], this.mText[newCharIndex + 1])))
                                newCharIndex += 2;
                            else break;
                        } else if (0 >= WcWidth.width(this.mText[newCharIndex])) newCharIndex++;
                        else break;
                    }
                    return newCharIndex;
                } else if (currentColumn > column)
                    return currentCharIndex;// Wide column going past end.

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
                codePoint = Character.toCodePoint(c, this.mText[currentCharIndex]);
                currentCharIndex++;
            } else {
                codePoint = c;
            }
            final int wcwidth = WcWidth.width(codePoint);
            if (0 < wcwidth) {
                if (currentColumn == column && 2 == wcwidth) return true;
                currentColumn += wcwidth;
                if (currentColumn > column) return false;
            }
        }
        return false;
    }

    public void clear(final long style) {
        Arrays.fill(this.mText, ' ');
        Arrays.fill(this.mStyle, style);
        this.mSpaceUsed = (short) this.mColumns;
        this.mHasNonOneWidthOrSurrogateChars = false;
    }

    // https://github.com/steven676/Android-Terminal-Emulator/commit/9a47042620bec87617f0b4f5d50568535668fe26
    public void setChar(int columnToSet, final int codePoint, final long style) {
        this.mStyle[columnToSet] = style;
        final int newCodePointDisplayWidth = WcWidth.width(codePoint); // Fast path when we don't have any chars with width != 1
        if (!this.mHasNonOneWidthOrSurrogateChars) {
            if (Character.MIN_SUPPLEMENTARY_CODE_POINT <= codePoint || 1 != newCodePointDisplayWidth)
                this.mHasNonOneWidthOrSurrogateChars = true;
            else {
                this.mText[columnToSet] = (char) codePoint;
                return;
            }
        }
        final boolean newIsCombining = 0 >= newCodePointDisplayWidth;
        final boolean wasExtraColForWideChar = (0 < columnToSet) && this.wideDisplayCharacterStartingAt(columnToSet - 1);
        if (newIsCombining) { // When standing at second half of wide character and inserting combining:
            if (wasExtraColForWideChar) columnToSet--;
        } else { // Check if we are overwriting the second half of a wide character starting at the previous column:
            if (wasExtraColForWideChar) this.setChar(columnToSet - 1, ' ', style);
            // Check if we are overwriting the first half of a wide character starting at the next column:
            final boolean overwritingWideCharInNextColumn = 2 == newCodePointDisplayWidth && this.wideDisplayCharacterStartingAt(columnToSet + 1);
            if (overwritingWideCharInNextColumn) this.setChar(columnToSet + 1, ' ', style);
        }
        char[] text = this.mText;
        final int oldStartOfColumnIndex = this.findStartOfColumn(columnToSet);
        final int oldCodePointDisplayWidth = WcWidth.width(text, oldStartOfColumnIndex); // Get the number of elements in the mText array this column uses now
        final int oldCharactersUsedForColumn = (columnToSet + oldCodePointDisplayWidth < this.mColumns) ? this.findStartOfColumn(columnToSet + oldCodePointDisplayWidth) - oldStartOfColumnIndex : this.mSpaceUsed - oldStartOfColumnIndex;
        // Find how many chars this column will need
        var newCharactersUsedForColumn = Character.charCount(codePoint);
        if (newIsCombining) { // Combining characters are added to the contents of the column instead of overwriting them, so that they
            // modify the existing contents.
            // FIXME: Unassigned characters also get width=0.
            if (15 < newCharactersUsedForColumn + oldCharactersUsedForColumn) return;
            newCharactersUsedForColumn += oldCharactersUsedForColumn;
        }
        final int oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn;
        final int newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn;
        final int javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn;
        if (0 < javaCharDifference) { // Shift the rest of the line right.
            final int oldCharactersAfterColumn = this.mSpaceUsed - oldNextColumnIndex;
            if (this.mSpaceUsed + javaCharDifference > text.length) { // We need to grow the array
                final char[] newText = new char[text.length + this.mColumns];
                System.arraycopy(text, 0, newText, 0, oldNextColumnIndex);
                System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn);
                this.mText = text = newText;
            } else
                System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn);
        } else if (0 > javaCharDifference)  // Shift the rest of the line left.
            System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, this.mSpaceUsed - oldNextColumnIndex);

        this.mSpaceUsed += (short) javaCharDifference; // Store char. A combining character is stored at the end of the existing contents so that it modifies them:
        Character.toChars(codePoint, text, oldStartOfColumnIndex + (newIsCombining ? oldCharactersUsedForColumn : 0));
        if (2 == oldCodePointDisplayWidth && 1 == newCodePointDisplayWidth) { // Replace second half of wide char with a space. Which mean that we actually add a ' ' java character.
            if (this.mSpaceUsed + 1 > text.length) {
                final char[] newText = new char[text.length + this.mColumns];
                System.arraycopy(text, 0, newText, 0, newNextColumnIndex);
                System.arraycopy(text, newNextColumnIndex, newText, newNextColumnIndex + 1, this.mSpaceUsed - newNextColumnIndex);
                this.mText = text = newText;
            } else
                System.arraycopy(text, newNextColumnIndex, text, newNextColumnIndex + 1, this.mSpaceUsed - newNextColumnIndex);

            text[newNextColumnIndex] = ' ';
            ++this.mSpaceUsed;
        } else if (1 == oldCodePointDisplayWidth && 2 == newCodePointDisplayWidth) {
            if (columnToSet == this.mColumns - 2) { // Truncate the line to the second part of this wide char:
                this.mSpaceUsed = (short) newNextColumnIndex;
            } else { // Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
                // check at the beginning of this method we know that we are not overwriting a wide char.
                final int newNextNextColumnIndex = newNextColumnIndex + (Character.isHighSurrogate(this.mText[newNextColumnIndex]) ? 2 : 1);
                final int nextLen = newNextNextColumnIndex - newNextColumnIndex; // Shift the array leftwards.
                System.arraycopy(text, newNextNextColumnIndex, text, newNextColumnIndex, this.mSpaceUsed - newNextNextColumnIndex);
                this.mSpaceUsed -= (short) nextLen;
            }
        }
    }

    public boolean isBlank() {
        for (int charIndex = 0; charIndex < this.mSpaceUsed; charIndex++) {
            if (' ' != this.mText[charIndex]) return false;
        }
        return true;
    }

}
