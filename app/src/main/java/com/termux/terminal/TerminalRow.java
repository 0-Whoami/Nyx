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
    public boolean mLineWrap = false;

    /**
     * The number of java char:s used in [.mText].
     */
    private short mSpaceUsed;
    /**
     * If this row might contain chars with width != 1, used for deactivating fast path
     */
    private boolean mHasNonOneWidthOrSurrogateChars = false;

    /**
     * Construct a blank row (containing only whitespace, ' ') with a specified style.
     */
    public TerminalRow(int colm, long style) {
        mColumns = colm;
        mStyle = new long[colm];
        mText = new char[colm];
        clear(style);
    }

    public short spaceUsed() {
        return mSpaceUsed;
    }

    /**
     * NOTE: The sourceX2 is exclusive.
     */
    public void copyInterval(TerminalRow line, int sourceX1, int sourceX2, int destinationX) {
        mHasNonOneWidthOrSurrogateChars |= line.mHasNonOneWidthOrSurrogateChars;
        final int x1 = line.findStartOfColumn(sourceX1);
        final int x2 = line.findStartOfColumn(sourceX2);
        boolean startingFromSecondHalfOfWideChar = (0 < sourceX1 && line.wideDisplayCharacterStartingAt(sourceX1 - 1));
        final char[] sourceChars = line.mText;
        var latestNonCombiningWidth = 0;
        for (int i = x1; i < x2; i++) {
            final char sourceChar = sourceChars[i];
            int codePoint = Character.isHighSurrogate(sourceChar) ? Character.toCodePoint(sourceChar, sourceChars[++i]) : sourceChar;
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
            setChar(destinationX, codePoint, line.mStyle[sourceX1]);
        }
    }

    /**
     * Note that the column may end of second half of wide character.
     */
    public int findStartOfColumn(int column) {
        if (column == mColumns) return mSpaceUsed;
        int currentColumn = 0;
        int currentCharIndex = 0;
        while (true) { // 0<2 1 < 2
            int newCharIndex = currentCharIndex; // cci=1, cci=2
            final char c = mText[newCharIndex++];
            int codePoint = Character.isHighSurrogate(c) ? Character.toCodePoint(c, mText[newCharIndex++]) : c; // 1, 2
            final int wcwidth = WcWidth.width(codePoint);
            if (0 < wcwidth) {
                currentColumn += wcwidth;
                if (currentColumn == column) {
                    while (newCharIndex < mSpaceUsed) { // Skip combining chars.
                        if (Character.isHighSurrogate(mText[newCharIndex])) {
                            if (0 >= WcWidth.width(Character.toCodePoint(mText[newCharIndex], mText[newCharIndex + 1])))
                                newCharIndex += 2;
                            else break;
                        } else if (0 >= WcWidth.width(mText[newCharIndex])) newCharIndex++;
                        else break;
                    }
                    return newCharIndex;
                } else if (currentColumn > column)
                    return currentCharIndex;// Wide column going past end.

            }
            currentCharIndex = newCharIndex;
        }
    }

    private boolean wideDisplayCharacterStartingAt(int column) {
        for (int currentCharIndex = 0, currentColumn = 0; currentCharIndex < mSpaceUsed; ) {
            final char c = mText[currentCharIndex++];
            final int codePoint = Character.isHighSurrogate(c) ? Character.toCodePoint(c, mText[currentCharIndex++]) : c;
            final int wcwidth = WcWidth.width(codePoint);
            if (0 < wcwidth) {
                if (currentColumn == column && 2 == wcwidth) return true;
                currentColumn += wcwidth;
                if (currentColumn > column) return false;
            }
        }
        return false;
    }

    public void clear(long style) {
        Arrays.fill(mText, ' ');
        Arrays.fill(mStyle, style);
        mSpaceUsed = (short) mColumns;
        mHasNonOneWidthOrSurrogateChars = false;
    }

    // https://github.com/steven676/Android-Terminal-Emulator/commit/9a47042620bec87617f0b4f5d50568535668fe26
    public void setChar(int columnToSet, int codePoint, long style) {
        mStyle[columnToSet] = style;
        final int newCodePointDisplayWidth = WcWidth.width(codePoint); // Fast path when we don't have any chars with width != 1
        if (!mHasNonOneWidthOrSurrogateChars) {
            if (Character.MIN_SUPPLEMENTARY_CODE_POINT <= codePoint || 1 != newCodePointDisplayWidth)
                mHasNonOneWidthOrSurrogateChars = true;
            else {
                mText[columnToSet] = (char) codePoint;
                return;
            }
        }
        final boolean newIsCombining = 0 >= newCodePointDisplayWidth;
        final boolean wasExtraColForWideChar = (0 < columnToSet) && wideDisplayCharacterStartingAt(columnToSet - 1);
        if (newIsCombining) { // When standing at second half of wide character and inserting combining:
            if (wasExtraColForWideChar) columnToSet--;
        } else { // Check if we are overwriting the second half of a wide character starting at the previous column:
            if (wasExtraColForWideChar) setChar(columnToSet - 1, ' ', style);
            // Check if we are overwriting the first half of a wide character starting at the next column:
            final boolean overwritingWideCharInNextColumn = 2 == newCodePointDisplayWidth && wideDisplayCharacterStartingAt(columnToSet + 1);
            if (overwritingWideCharInNextColumn) setChar(columnToSet + 1, ' ', style);
        }
        char[] text = mText;
        final int oldStartOfColumnIndex = findStartOfColumn(columnToSet);
        final int oldCodePointDisplayWidth = WcWidth.width(text, oldStartOfColumnIndex); // Get the number of elements in the mText array this column uses now
        final int oldCharactersUsedForColumn = (columnToSet + oldCodePointDisplayWidth < mColumns) ? findStartOfColumn(columnToSet + oldCodePointDisplayWidth) - oldStartOfColumnIndex : mSpaceUsed - oldStartOfColumnIndex;
        // Find how many chars this column will need
        var newCharactersUsedForColumn = Character.charCount(codePoint);
        if (newIsCombining) { // Combining characters are added to the contents of the column instead of overwriting them, so that they
            // modify the existing contents.
            // FIXME: Unassigned characters also get width=0.
            if (newCharactersUsedForColumn + oldCharactersUsedForColumn > 15) return;
            newCharactersUsedForColumn += oldCharactersUsedForColumn;
        }
        final int oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn;
        final int newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn;
        final int javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn;
        if (0 < javaCharDifference) { // Shift the rest of the line right.
            final int oldCharactersAfterColumn = mSpaceUsed - oldNextColumnIndex;
            if (mSpaceUsed + javaCharDifference > text.length) { // We need to grow the array
                final char[] newText = new char[text.length + mColumns];
                System.arraycopy(text, 0, newText, 0, oldNextColumnIndex);
                System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn);
                mText = text = newText;
            } else
                System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn);
        } else if (0 > javaCharDifference)  // Shift the rest of the line left.
            System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - oldNextColumnIndex);

        mSpaceUsed += (short) javaCharDifference; // Store char. A combining character is stored at the end of the existing contents so that it modifies them:
        Character.toChars(codePoint, text, oldStartOfColumnIndex + (newIsCombining ? oldCharactersUsedForColumn : 0));
        if (2 == oldCodePointDisplayWidth && 1 == newCodePointDisplayWidth) { // Replace second half of wide char with a space. Which mean that we actually add a ' ' java character.
            if (mSpaceUsed + 1 > text.length) {
                final char[] newText = new char[text.length + mColumns];
                System.arraycopy(text, 0, newText, 0, newNextColumnIndex);
                System.arraycopy(text, newNextColumnIndex, newText, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex);
                mText = text = newText;
            } else
                System.arraycopy(text, newNextColumnIndex, text, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex);

            text[newNextColumnIndex] = ' ';
            ++mSpaceUsed;
        } else if (1 == oldCodePointDisplayWidth && 2 == newCodePointDisplayWidth) {
            if (columnToSet == mColumns - 2) { // Truncate the line to the second part of this wide char:
                mSpaceUsed = (short) newNextColumnIndex;
            } else { // Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
                // check at the beginning of this method we know that we are not overwriting a wide char.
                final int newNextNextColumnIndex = newNextColumnIndex + (Character.isHighSurrogate(mText[newNextColumnIndex]) ? 2 : 1);
                final int nextLen = newNextNextColumnIndex - newNextColumnIndex; // Shift the array leftwards.
                System.arraycopy(text, newNextNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - newNextNextColumnIndex);
                mSpaceUsed -= (short) nextLen;
            }
        }
    }

    public boolean isBlank() {
        for (int charIndex = 0; charIndex < mSpaceUsed; charIndex++) {
            if (' ' != mText[charIndex]) return false;
        }
        return true;
    }

}
