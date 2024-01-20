package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {


    final Typeface mTypeface;

    final Typeface mItalicTypeface;
    /**
     * The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'.
     */
    final float mFontWidth;
    /**
     * The {@link Paint#getFontSpacing()}. See <a href="http://www.fampennings.nl/maarten/android/08numgrid/font.png">...</a>
     */
    final int mFontLineSpacing;
    /**
     * The {@link #mFontLineSpacing} + {@link #mFontAscent}.
     */
    final int mFontLineSpacingAndAscent;
    /**
     * The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'.
     */
    private final float mItalicFontWidth;
    /**
     * The {@link Paint#getFontSpacing()}. See <a href="http://www.fampennings.nl/maarten/android/08numgrid/font.png">...</a>
     */
    private final int mItalicFontLineSpacing;
    /**
     * The {@link #mFontLineSpacing} + {@link #mFontAscent}.
     */
    private final int mItalicFontLineSpacingAndAscent;
    private final Paint mTextPaint = new Paint();
    /**
     * The {@link Paint#ascent()}. See <a href="http://www.fampennings.nl/maarten/android/08numgrid/font.png">...</a>
     */
    private final int mFontAscent;
    private final float[] asciiMeasures = new float[127];
    /**
     * The {@link Paint#ascent()}. See <a href="http://www.fampennings.nl/maarten/android/08numgrid/font.png">...</a>
     */
    private final int mItalicFontAscent;

    public TerminalRenderer(int textSize, Typeface typeface, Typeface italicTypeface) {
        mTypeface = typeface;
        mItalicTypeface = italicTypeface;
        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);
        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
        mTextPaint.setTypeface(italicTypeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);
        mItalicFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mItalicFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mItalicFontLineSpacingAndAscent = mItalicFontLineSpacing + mItalicFontAscent;
        mItalicFontWidth = mTextPaint.measureText("X");
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public void render(TerminalEmulator mEmulator, Canvas canvas, int topRow, int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean boldWithBright = mEmulator.isBoldWithBright();
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();
        mEmulator.setCellSize((int) mFontWidth, mFontLineSpacing);
        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1)
                    selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }
            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();
            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.0f;
            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final long style = lineObject.getStyle(column);
                if (TextStyle.isBitmap(style)) {
                    Bitmap bm = mEmulator.getScreen().getSixelBitmap(style);
                    if (null != bm) {
                        float left = column * mFontWidth;
                        float top = heightOffset - mFontLineSpacing;
                        RectF r = new RectF(left, top, left + mFontWidth, top + mFontLineSpacing);
                        canvas.drawBitmap(mEmulator.getScreen().getSixelBitmap(style), mEmulator.getScreen().getSixelRect(style), r, null);
                    }
                    column += 1;
                    measuredWidthForRun = 0.0f;
                    lastRunStyle = 0;
                    lastRunInsideCursor = false;
                    lastRunStartColumn = column + 1;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = false;
                    currentCharIndex += charsForCodePoint;
                    continue;
                }
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (2 == codePointWcWidth && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = 0.01 < Math.abs(measuredCodePointWidth / this.mFontWidth - codePointWcWidth);
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (0 != column && column != lastRunStartColumn) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
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
                while (currentCharIndex < charsUsedInLine && 0 >= WcWidth.width(line, currentCharIndex)) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }
            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns, int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle, long textStyle, boolean boldWithBright, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = 0 != (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK));
        final boolean underline = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE);
        final boolean italic = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC);
        final boolean strikeThrough = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH);
        final boolean dim = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM);
        final float fontWidth = italic ? mItalicFontWidth : mFontWidth;
        final int fontLineSpacing = italic ? mItalicFontLineSpacing : mFontLineSpacing;
        final int fontAscent = italic ? mItalicFontAscent : mFontAscent;
        final int fontLineSpacingAndAscent = italic ? mItalicFontLineSpacingAndAscent : mFontLineSpacingAndAscent;
        if (0xff000000 != (foreColor & 0xff000000)) {
            // If enabled, let bold have bright colors if applicable (one of the first 8):
            if (boldWithBright && bold && 0 <= foreColor && 8 > foreColor)
                foreColor += 8;
            foreColor = palette[foreColor];
        }
        if (0xff000000 != (backColor & 0xff000000)) {
            backColor = palette[backColor];
        }
        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ 0 != (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE));
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }
        float left = startColumn * fontWidth;
        float right = left + runWidthColumns * fontWidth;
        mes = mes / fontWidth;
        boolean savedMatrix = false;
        if (0.01 < Math.abs(mes - runWidthColumns)) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.0f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }
        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - fontLineSpacingAndAscent + fontAscent, right, y, mTextPaint);
        }
        if (0 != cursor) {
            mTextPaint.setColor(cursor);
            // fontLineSpacingAndAscent - fontAscent isn't equals to
            // fontLineSpacing?
            float cursorHeight = fontLineSpacing;
            if (TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE == cursorStyle)
                cursorHeight /= 4.0F;
            else if (TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR == cursorStyle)
                right -= (float) (((right - left) * 3) / 4.0);
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }
        if (0 == (effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE)) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = (red << 1) / 3;
                green = (green << 1) / 3;
                blue = (blue << 1) / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }
            mTextPaint.setTypeface(mTypeface);
            if (italic)
                mTextPaint.setTypeface(mItalicTypeface);
            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(0.0f);
            if (italic && mItalicTypeface.equals(mTypeface))
                mTextPaint.setTextSkewX(-0.35f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);
            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }
        if (savedMatrix)
            canvas.restore();
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }

}
