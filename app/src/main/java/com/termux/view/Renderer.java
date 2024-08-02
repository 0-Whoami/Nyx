package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;

import com.termux.data.ConfigManager;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;
import com.termux.view.textselection.TextSelectionCursorController;

/**
 * Renderer of a [TerminalEmulator] into a [Canvas].
 * <p>
 * <p>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
final class Renderer {
    private static final float PADDING = 5.0f;
    private static final Paint mTextPaint = new Paint();
    private static final float[] asciiMeasures = new float[127];
    /**
     * The width of a single mono spaced character obtained by [Paint.measureText] on a single 'X'.
     */
    public static float fontWidth;
    /**
     * The [Paint.getFontSpacing]. See [...](<a href="http://www.fampennings.nl/maarten/android/08numgrid/font.png">...</a>)
     */
    public static int fontLineSpacing;
    /**
     * The [.mFontLineSpacing] + [.mFontAscent].
     */

    public static int mFontLineSpacingAndAscent;
    /**
     * The [Paint.ascent]. See [...](<a href="http://www.fampennings.nl/maarten/android/08numgrid/font.png">...</a>)
     */
    private static int mFontAscent;

    static void setTypeface() {
        Renderer.mTextPaint.setTypeface(ConfigManager.typeface);
    }

    static void setTextSize(final int textSize) {
        Renderer.mTextPaint.setTextSize(textSize);
        Renderer.fontLineSpacing = (int) Math.ceil(Renderer.mTextPaint.getFontSpacing());
        Renderer.mFontAscent = (int) Math.ceil(Renderer.mTextPaint.ascent());
        Renderer.mFontLineSpacingAndAscent = Renderer.fontLineSpacing + Renderer.mFontAscent;
        Renderer.fontWidth = Renderer.mTextPaint.measureText("X");
        for (int i = 0; 127 > i; i++)
            Renderer.asciiMeasures[i] = Renderer.mTextPaint.measureText(Character.toString(i), 0, 1);
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public static void render(final TerminalEmulator mEmulator, final Canvas canvas, final int topRow) {
        Renderer.setPaddings(canvas);
        final var reverseVideo = mEmulator.isReverseVideo();
        final var endRow = topRow + mEmulator.mRows;
        final var columns = mEmulator.mColumns;
        final var cursorCol = mEmulator.mCursorCol;
        final var cursorRow = mEmulator.mCursorRow;
        final var cursorVisible = mEmulator.shouldCursorBeVisible();
        final var screen = mEmulator.screen;
        final var palette = mEmulator.mColors.mCurrentColors;
        final var cursorShape = mEmulator.cursorStyle;
        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        var heightOffset = 0.0f;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += Renderer.fontLineSpacing;
            final var cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= TextSelectionCursorController.selectors[1] && row <= TextSelectionCursorController.selectors[3]) {
                if (row == TextSelectionCursorController.selectors[1])
                    selx1 = TextSelectionCursorController.selectors[0];
                selx2 = (row == TextSelectionCursorController.selectors[3]) ? TextSelectionCursorController.selectors[2] : mEmulator.mColumns;
            }
            final var lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final var line = lineObject.mText;
            final var charsUsedInLine = lineObject.mSpaceUsed;
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
                final var charAtIndex = line[currentCharIndex];
                final var charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final var charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final var codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final var codePointWcWidth = WcWidth.width(codePoint);
                final var insideCursor = (cursorX == column || (2 == codePointWcWidth && cursorX == column + 1));
                final var insideSelection = column >= selx1 && column <= selx2; // Check if the measured text width for this code point is not the same as that expected by wcwidth(). // This could happen for some fonts which are not truly monospace, or for more exotic characters such as // smileys which android font renders as wide. // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final var measuredCodePointWidth = codePoint < Renderer.asciiMeasures.length ? Renderer.asciiMeasures[codePoint] : Renderer.mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                final var fontWidthMismatch = 0.01f < Math.abs(measuredCodePointWidth / Renderer.fontWidth - codePointWcWidth);
                final var style = lineObject.mStyle[column];
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (0 != column) {
                        final var columnWidthSinceLastRun = column - lastRunStartColumn;
                        final var charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        final var cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        final var invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
                        Renderer.drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
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
                while (currentCharIndex < charsUsedInLine && 0 >= WcWidth.width(line, currentCharIndex))
                    // Eat combining chars so that they are treated as part of the last non-combining code point, instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;

            }
            final var columnWidthSinceLastRun = columns - lastRunStartColumn;
            final var charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            final var cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            final var invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
            Renderer.drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private static void setPaddings(final Canvas canvas) {
        final var height = canvas.getHeight();
        final var width = canvas.getWidth();
        canvas.translate(Renderer.PADDING + (width % Renderer.fontWidth) / 2.0f, Renderer.PADDING + (height % Renderer.fontLineSpacing) / 2.0f);
        canvas.scale(1 - (2 * Renderer.PADDING / width), 1 - (2 * Renderer.PADDING / height));
    }

    private static void drawTextRun(final Canvas canvas, final char[] text, final int[] palette, final float y, final int startColumn, final int runWidthColumns, final int startCharIndex, final int runWidthChars, float mes, final int cursor, final int cursorStyle, final long textStyle, final boolean reverseVideo) {
        var foreColor = TextStyle.decodeForeColor(textStyle);
        final var effect = TextStyle.decodeEffect(textStyle);
        var backColor = TextStyle.decodeBackColor(textStyle);
        final var bold = 0 != (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK));
        final var underline = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE);
        final var italic = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC);
        final var strikeThrough = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH);
        final var dim = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM);
        if (0xff000000 != (foreColor & 0xff000000)) { // If enabled, let bold have bright colors if applicable (one of the first 8):
            if (bold && 0 <= foreColor && 8 > foreColor) foreColor += 8;
            foreColor = palette[foreColor];
        }
        if (-0x1000000 != (backColor & -0x1000000)) backColor = palette[backColor];
        // Reverse video here if _one and only one_ of the reverse flags are set:
        if (reverseVideo ^ (0 != (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)))) {
            final int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }
        var left = startColumn * Renderer.fontWidth;
        var right = left + runWidthColumns * Renderer.fontWidth;
        mes /= Renderer.fontWidth;
        var savedMatrix = false;
        if (0.01f < Math.abs(mes - runWidthColumns)) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.0f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }
        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) { // Only draw non-default background.
            Renderer.mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - Renderer.mFontLineSpacingAndAscent + Renderer.mFontAscent, right, y, Renderer.mTextPaint);
        }
        if (0 != cursor) {
            Renderer.mTextPaint.setColor(cursor);
            float cursorHeight = Renderer.fontLineSpacing;
            if (TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE == cursorStyle)
                cursorHeight /= 4.0f;
            else if (TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR == cursorStyle)
                right -= (((right - left) * 3) / 4);
            canvas.drawRect(left, y - cursorHeight, right, y, Renderer.mTextPaint);
        }
        if (0 == (effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE)) {
            if (dim) {
                var red = (0xFF & (foreColor >> 16));
                var green = (0xFF & (foreColor >> 8));
                var blue = (0xFF & foreColor); // Dim color handling used by libvte which in turn took it from xterm // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = (red << 1) / 3;
                green = (green << 1) / 3;
                blue = (blue << 1) / 3;
                foreColor = -0x1000000 + (red << 16) + (green << 8) + blue;
            }
            Renderer.mTextPaint.setFakeBoldText(bold);
            Renderer.mTextPaint.setUnderlineText(underline);
            Renderer.mTextPaint.setTextSkewX(italic ? -0.35f : 0.0f);
            Renderer.mTextPaint.setStrikeThruText(strikeThrough);
            Renderer.mTextPaint.setColor(foreColor);
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - Renderer.mFontLineSpacingAndAscent, false, Renderer.mTextPaint);
        }
        if (savedMatrix) canvas.restore();
    }
}
