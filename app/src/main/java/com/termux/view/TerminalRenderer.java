package com.termux.view;

import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BLINK;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BOLD;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_DIM;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
import static com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
import static com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND;
import static com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR;
import static com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND;
import static com.termux.terminal.TextStyle.decodeBackColor;
import static com.termux.terminal.TextStyle.decodeEffect;
import static com.termux.terminal.TextStyle.decodeForeColor;
import static com.termux.terminal.WcWidth.width;
import static com.termux.view.textselection.TextSelectionCursorController.selectors;
import static java.lang.Math.abs;
import static java.lang.Math.ceil;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;

import com.termux.data.ConfigManager;
import com.termux.terminal.TerminalEmulator;

/**
 * Renderer of a [TerminalEmulator] into a [Canvas].
 * <p>
 * <p>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
final class TerminalRenderer {
    private static final float PADDING = 5.0f;
    /**
     * The width of a single mono spaced character obtained by [Paint.measureText] on a single 'X'.
     */
    public final float fontWidth;
    /**
     * The [Paint.getFontSpacing]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    public final int fontLineSpacing;

    /**
     * The [.mFontLineSpacing] + [.mFontAscent].
     */

    public final int mFontLineSpacingAndAscent;

    private final Paint mTextPaint = new Paint();

    /**
     * The [Paint.ascent]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    private final int mFontAscent;
    private final float[] asciiMeasures = new float[127];


    TerminalRenderer(int textSize) {
        mTextPaint.setTypeface(ConfigManager.typeface);
        mTextPaint.setTextSize(textSize);
        fontLineSpacing = (int) ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = fontLineSpacing + mFontAscent;
        fontWidth = mTextPaint.measureText("X");
        for (int i = 0; 127 > i; i++)
            asciiMeasures[i] = mTextPaint.measureText(Character.toString(i), 0, 1);
    }

    private void setPaddings(Canvas canvas) {
        var height = canvas.getHeight();
        var width = canvas.getWidth();
        canvas.translate(TerminalRenderer.PADDING + (width % fontWidth) / 2.0f, TerminalRenderer.PADDING + (height % fontLineSpacing) / 2.0f);
        canvas.scale(1 - (2 * TerminalRenderer.PADDING / width), 1 - (2 * TerminalRenderer.PADDING / height));
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public void render(TerminalEmulator mEmulator, Canvas canvas, int topRow) {
        setPaddings(canvas);
        var reverseVideo = mEmulator.isReverseVideo();
        var endRow = topRow + mEmulator.mRows;
        var columns = mEmulator.mColumns;
        var cursorCol = mEmulator.mCursorCol;
        var cursorRow = mEmulator.mCursorRow;
        var cursorVisible = mEmulator.shouldCursorBeVisible();
        var screen = mEmulator.screen;
        var palette = mEmulator.mColors.mCurrentColors;
        var cursorShape = mEmulator.cursorStyle;
        if (reverseVideo) canvas.drawColor(palette[COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        var heightOffset = 0.0f;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += fontLineSpacing;
            var cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectors[1] && row <= selectors[3]) {
                if (row == selectors[1]) selx1 = selectors[0];
                selx2 = (row == selectors[3]) ? selectors[2] : mEmulator.mColumns;
            }
            var lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            var line = lineObject.mText;
            var charsUsedInLine = lineObject.mSpaceUsed;
            long lastRunStyle = 0;
            var lastRunInsideCursor = false;
            var lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            var lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0;
            int column = 0;
            while (column < columns) {
                var charAtIndex = line[currentCharIndex];
                var charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                var charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                var codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                var codePointWcWidth = width(codePoint);
                var insideCursor = (cursorX == column || (2 == codePointWcWidth && cursorX == column + 1));
                var insideSelection = column >= selx1 && column <= selx2; // Check if the measured text width for this code point is not the same as that expected by wcwidth(). // This could happen for some fonts which are not truly monospace, or for more exotic characters such as // smileys which android font renders as wide. // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                var measuredCodePointWidth = codePoint < asciiMeasures.length ? asciiMeasures[codePoint] : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                var fontWidthMismatch = 0.01f < abs(measuredCodePointWidth / fontWidth - codePointWcWidth);
                var style = lineObject.mStyle[column];
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (0 != column) {
                        var columnWidthSinceLastRun = column - lastRunStartColumn;
                        var charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        var cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[COLOR_INDEX_CURSOR] : 0;
                        var invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.0f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && 0 >= width(line, currentCharIndex))
                    // Eat combining chars so that they are treated as part of the last non-combining code point, instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;

            }
            var columnWidthSinceLastRun = columns - lastRunStartColumn;
            var charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            var cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[COLOR_INDEX_CURSOR] : 0;
            var invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns, int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle, long textStyle, boolean reverseVideo) {
        var foreColor = decodeForeColor(textStyle);
        var effect = decodeEffect(textStyle);
        var backColor = decodeBackColor(textStyle);
        var bold = 0 != (effect & (CHARACTER_ATTRIBUTE_BOLD | CHARACTER_ATTRIBUTE_BLINK));
        var underline = 0 != (effect & CHARACTER_ATTRIBUTE_UNDERLINE);
        var italic = 0 != (effect & CHARACTER_ATTRIBUTE_ITALIC);
        var strikeThrough = 0 != (effect & CHARACTER_ATTRIBUTE_STRIKETHROUGH);
        var dim = 0 != (effect & CHARACTER_ATTRIBUTE_DIM);
        if (0xff000000 != (foreColor & 0xff000000)) { // If enabled, let bold have bright colors if applicable (one of the first 8):
            if (bold && 0 <= foreColor && 8 > foreColor) foreColor += 8;
            foreColor = palette[foreColor];
        }
        if (-0x1000000 != (backColor & -0x1000000)) backColor = palette[backColor];
        // Reverse video here if _one and only one_ of the reverse flags are set:
        if (reverseVideo ^ (0 != (effect & (CHARACTER_ATTRIBUTE_INVERSE)))) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }
        var left = startColumn * fontWidth;
        var right = left + runWidthColumns * fontWidth;
        mes /= fontWidth;
        var savedMatrix = false;
        if (0.01f < abs(mes - runWidthColumns)) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.0f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }
        if (backColor != palette[COLOR_INDEX_BACKGROUND]) { // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }
        if (0 != cursor) {
            mTextPaint.setColor(cursor);
            float cursorHeight = fontLineSpacing;
            if (TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE == cursorStyle)
                cursorHeight /= 4.0f;
            else if (TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR == cursorStyle)
                right -= (((right - left) * 3) / 4);
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }
        if (0 == (effect & CHARACTER_ATTRIBUTE_INVISIBLE)) {
            if (dim) {
                var red = (0xFF & (foreColor >> 16));
                var green = (0xFF & (foreColor >> 8));
                var blue = (0xFF & foreColor); // Dim color handling used by libvte which in turn took it from xterm // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = (red << 1) / 3;
                green = (green << 1) / 3;
                blue = (blue << 1) / 3;
                foreColor = -0x1000000 + (red << 16) + (green << 8) + blue;
            }
            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.0f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }
        if (savedMatrix) canvas.restore();
    }
}
