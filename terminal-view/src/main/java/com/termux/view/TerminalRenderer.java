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

    public TerminalRenderer(final int textSize, final Typeface typeface, final Typeface italicTypeface) {
        this.mTypeface = typeface;
        this.mItalicTypeface = italicTypeface;
        this.mTextPaint.setTypeface(typeface);
        this.mTextPaint.setAntiAlias(true);
        this.mTextPaint.setTextSize(textSize);
        this.mFontLineSpacing = (int) Math.ceil(this.mTextPaint.getFontSpacing());
        this.mFontAscent = (int) Math.ceil(this.mTextPaint.ascent());
        this.mFontLineSpacingAndAscent = this.mFontLineSpacing + this.mFontAscent;
        this.mFontWidth = this.mTextPaint.measureText("X");
        final StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < this.asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            this.asciiMeasures[i] = this.mTextPaint.measureText(sb, 0, 1);
        }
        this.mTextPaint.setTypeface(italicTypeface);
        this.mTextPaint.setAntiAlias(true);
        this.mTextPaint.setTextSize(textSize);
        this.mItalicFontLineSpacing = (int) Math.ceil(this.mTextPaint.getFontSpacing());
        this.mItalicFontAscent = (int) Math.ceil(this.mTextPaint.ascent());
        this.mItalicFontLineSpacingAndAscent = this.mItalicFontLineSpacing + this.mItalicFontAscent;
        this.mItalicFontWidth = this.mTextPaint.measureText("X");
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public void render(final TerminalEmulator mEmulator, final Canvas canvas, final int topRow, final int selectionY1, final int selectionY2, final int selectionX1, final int selectionX2) {
        boolean boldWithBright = mEmulator.isBoldWithBright();
        boolean reverseVideo = mEmulator.isReverseVideo();
        int endRow = topRow + mEmulator.mRows;
        int columns = mEmulator.mColumns;
        int cursorCol = mEmulator.getCursorCol();
        int cursorRow = mEmulator.getCursorRow();
        boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        TerminalBuffer screen = mEmulator.getScreen();
        int[] palette = mEmulator.mColors.mCurrentColors;
        int cursorShape = mEmulator.getCursorStyle();
        mEmulator.setCellSize((int) this.mFontWidth, this.mFontLineSpacing);
        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        float heightOffset = this.mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += this.mFontLineSpacing;
            int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1)
                    selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }
            final TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            char[] line = lineObject.mText;
            int charsUsedInLine = lineObject.getSpaceUsed();
            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.0f;
            for (int column = 0; column < columns; ) {
                char charAtIndex = line[currentCharIndex];
                boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                long style = lineObject.getStyle(column);
                if (TextStyle.isBitmap(style)) {
                    final Bitmap bm = mEmulator.getScreen().getSixelBitmap(style);
                    if (null != bm) {
                        final float left = column * this.mFontWidth;
                        final float top = heightOffset - this.mFontLineSpacing;
                        final RectF r = new RectF(left, top, left + this.mFontWidth, top + this.mFontLineSpacing);
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
                int codePointWcWidth = WcWidth.width(codePoint);
                boolean insideCursor = (cursorX == column || (2 == codePointWcWidth && cursorX == column + 1));
                boolean insideSelection = column >= selx1 && column <= selx2;
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                float measuredCodePointWidth = (codePoint < this.asciiMeasures.length) ? this.asciiMeasures[codePoint] : this.mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                boolean fontWidthMismatch = 0.01 < Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth);
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (0 != column && column != lastRunStartColumn) {
                        int columnWidthSinceLastRun = column - lastRunStartColumn;
                        int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        final int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        final boolean invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
                        this.drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
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
            int columnWidthSinceLastRun = columns - lastRunStartColumn;
            int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            final int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            final boolean invertCursorTextColor = lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape;
            this.drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private void drawTextRun(final Canvas canvas, final char[] text, final int[] palette, final float y, final int startColumn, final int runWidthColumns, final int startCharIndex, final int runWidthChars, float mes, final int cursor, final int cursorStyle, final long textStyle, final boolean boldWithBright, final boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        boolean bold = 0 != (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK));
        boolean underline = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE);
        boolean italic = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC);
        boolean strikeThrough = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH);
        boolean dim = 0 != (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM);
        float fontWidth = italic ? this.mItalicFontWidth : this.mFontWidth;
        int fontLineSpacing = italic ? this.mItalicFontLineSpacing : this.mFontLineSpacing;
        int fontAscent = italic ? this.mItalicFontAscent : this.mFontAscent;
        int fontLineSpacingAndAscent = italic ? this.mItalicFontLineSpacingAndAscent : this.mFontLineSpacingAndAscent;
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
        boolean reverseVideoHere = reverseVideo ^ 0 != (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE));
        if (reverseVideoHere) {
            final int tmp = foreColor;
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
            this.mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - fontLineSpacingAndAscent + fontAscent, right, y, this.mTextPaint);
        }
        if (0 != cursor) {
            this.mTextPaint.setColor(cursor);
            // fontLineSpacingAndAscent - fontAscent isn't equals to
            // fontLineSpacing?
            float cursorHeight = fontLineSpacing;
            if (TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE == cursorStyle)
                cursorHeight /= 4.0F;
            else if (TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR == cursorStyle)
                right -= (float) (((right - left) * 3) / 4.0);
            canvas.drawRect(left, y - cursorHeight, right, y, this.mTextPaint);
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
            this.mTextPaint.setTypeface(this.mTypeface);
            if (italic)
                this.mTextPaint.setTypeface(this.mItalicTypeface);
            this.mTextPaint.setFakeBoldText(bold);
            this.mTextPaint.setUnderlineText(underline);
            this.mTextPaint.setTextSkewX(0.0f);
            if (italic && this.mItalicTypeface.equals(this.mTypeface))
                this.mTextPaint.setTextSkewX(-0.35f);
            this.mTextPaint.setStrikeThruText(strikeThrough);
            this.mTextPaint.setColor(foreColor);
            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - this.mFontLineSpacingAndAscent, false, this.mTextPaint);
        }
        if (savedMatrix)
            canvas.restore();
    }

    public float getFontWidth() {
        return this.mFontWidth;
    }

    public int getFontLineSpacing() {
        return this.mFontLineSpacing;
    }

}
