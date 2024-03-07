package com.termux.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import com.termux.terminal.CHARACTER_ATTRIBUTE_BLINK
import com.termux.terminal.CHARACTER_ATTRIBUTE_BOLD
import com.termux.terminal.CHARACTER_ATTRIBUTE_DIM
import com.termux.terminal.CHARACTER_ATTRIBUTE_INVERSE
import com.termux.terminal.CHARACTER_ATTRIBUTE_INVISIBLE
import com.termux.terminal.CHARACTER_ATTRIBUTE_ITALIC
import com.termux.terminal.CHARACTER_ATTRIBUTE_STRIKETHROUGH
import com.termux.terminal.CHARACTER_ATTRIBUTE_UNDERLINE
import com.termux.terminal.COLOR_INDEX_BACKGROUND
import com.termux.terminal.COLOR_INDEX_CURSOR
import com.termux.terminal.COLOR_INDEX_FOREGROUND
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.WcWidth.width
import com.termux.terminal.decodeBackColor
import com.termux.terminal.decodeEffect
import com.termux.terminal.decodeForeColor
import com.termux.terminal.isBitmap
import com.termux.utils.data.italicTypeface
import com.termux.utils.data.typeface
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Renderer of a [TerminalEmulator] into a [Canvas].
 *
 *
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
class TerminalRenderer(
    textSize: Int
) {
    /**
     * The width of a single mono spaced character obtained by [Paint.measureText] on a single 'X'.
     */
    val fontWidth: Float

    val mTypeface: Typeface = typeface
    private val mItalicTypeface: Typeface = italicTypeface

    /**
     * The [Paint.getFontSpacing]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    val fontLineSpacing: Int

    /**
     * The [.mFontLineSpacing] + [.mFontAscent].
     */

    val mFontLineSpacingAndAscent: Int

    /**
     * The width of a single mono spaced character obtained by [Paint.measureText] on a single 'X'.
     */
    private val mItalicFontWidth: Float

    /**
     * The [Paint.getFontSpacing]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    private val mItalicFontLineSpacing: Int

    /**
     * The [.mFontLineSpacing] + [.mFontAscent].
     */
    private val mItalicFontLineSpacingAndAscent: Int
    private val mTextPaint = Paint()

    /**
     * The [Paint.ascent]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    private val mFontAscent: Int
    private val asciiMeasures = FloatArray(127)

    /**
     * The [Paint.ascent]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    private val mItalicFontAscent: Int

    init {
        mTextPaint.setTypeface(mTypeface)
        mTextPaint.isAntiAlias = true
        mTextPaint.textSize = textSize.toFloat()
        this.fontLineSpacing = ceil(mTextPaint.fontSpacing.toDouble())
            .toInt()
        this.mFontAscent = ceil(mTextPaint.ascent().toDouble())
            .toInt()
        this.mFontLineSpacingAndAscent = this.fontLineSpacing + this.mFontAscent
        this.fontWidth = mTextPaint.measureText("X")
        val sb = StringBuilder(" ")
        for (i in asciiMeasures.indices) {
            sb.setCharAt(0, i.toChar())
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1)
        }
        mTextPaint.setTypeface(mItalicTypeface)
        mTextPaint.isAntiAlias = true
        mTextPaint.textSize = textSize.toFloat()
        this.mItalicFontLineSpacing = ceil(mTextPaint.fontSpacing.toDouble())
            .toInt()
        this.mItalicFontAscent = ceil(mTextPaint.ascent().toDouble())
            .toInt()
        this.mItalicFontLineSpacingAndAscent = this.mItalicFontLineSpacing + this.mItalicFontAscent
        this.mItalicFontWidth = mTextPaint.measureText("X")
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    fun render(
        mEmulator: TerminalEmulator,
        canvas: Canvas,
        topRow: Int,
        selectionY1: Int,
        selectionY2: Int,
        selectionX1: Int,
        selectionX2: Int
    ) {
        val reverseVideo = mEmulator.isReverseVideo
        val endRow = topRow + mEmulator.mRows
        val columns = mEmulator.mColumns
        val cursorCol = mEmulator.cursorCol
        val cursorRow = mEmulator.cursorRow
        val cursorVisible = mEmulator.shouldCursorBeVisible()
        val screen = mEmulator.screen
        val palette = mEmulator.mColors.mCurrentColors
        val cursorShape = mEmulator.cursorStyle
        mEmulator.setCellSize(fontWidth.toInt(), this.fontLineSpacing)
        if (reverseVideo) canvas.drawColor(
            palette[COLOR_INDEX_FOREGROUND],
            PorterDuff.Mode.SRC
        )
        var heightOffset = 0f
        for (row in topRow until endRow) {
            heightOffset += fontLineSpacing.toFloat()
            val cursorX = if ((row == cursorRow && cursorVisible)) cursorCol else -1
            var selx1 = -1
            var selx2 = -1
            if (row in selectionY1..selectionY2) {
                if (row == selectionY1) selx1 = selectionX1
                selx2 = if ((row == selectionY2)) selectionX2 else mEmulator.mColumns
            }
            val lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row))
            val line = lineObject.mText
            val charsUsedInLine = lineObject.spaceUsed
            var lastRunStyle: Long = 0
            var lastRunInsideCursor = false
            var lastRunInsideSelection = false
            var lastRunStartColumn = -1
            var lastRunStartIndex = 0
            var lastRunFontWidthMismatch = false
            var currentCharIndex = 0
            var measuredWidthForRun = 0.0f
            var column = 0
            while (column < columns) {
                val charAtIndex = line[currentCharIndex]
                val charIsHighsurrogate = Character.isHighSurrogate(charAtIndex)
                val charsForCodePoint = if (charIsHighsurrogate) 2 else 1
                val codePoint = if (charIsHighsurrogate) Character.toCodePoint(
                    charAtIndex,
                    line[currentCharIndex + 1]
                ) else charAtIndex.code
                val style = lineObject.getStyle(column)
                if (isBitmap(style)) {
                    val bm = mEmulator.screen.getSixelBitmap(style)
                    if (null != bm) {
                        val left = column * this.fontWidth
                        val top = heightOffset - this.fontLineSpacing
                        val r = RectF(left, top, left + this.fontWidth, top + this.fontLineSpacing)
                        canvas.drawBitmap(
                            mEmulator.screen.getSixelBitmap(style)!!,
                            mEmulator.screen.getSixelRect(style),
                            r,
                            null
                        )
                    }
                    column += 1
                    measuredWidthForRun = 0.0f
                    lastRunStyle = 0
                    lastRunInsideCursor = false
                    lastRunStartColumn = column + 1
                    lastRunStartIndex = currentCharIndex
                    lastRunFontWidthMismatch = false
                    currentCharIndex += charsForCodePoint
                    continue
                }
                val codePointWcWidth = width(codePoint)
                val insideCursor =
                    (cursorX == column || (2 == codePointWcWidth && cursorX == column + 1))
                val insideSelection = column in selx1..selx2
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                val measuredCodePointWidth =
                    if ((codePoint < asciiMeasures.size)) asciiMeasures[codePoint] else mTextPaint.measureText(
                        line,
                        currentCharIndex,
                        charsForCodePoint
                    )
                val fontWidthMismatch =
                    0.01 < abs((measuredCodePointWidth / fontWidth - codePointWcWidth).toDouble())
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (0 != column && column != lastRunStartColumn) {
                        val columnWidthSinceLastRun = column - lastRunStartColumn
                        val charsSinceLastRun = currentCharIndex - lastRunStartIndex
                        val cursorColor =
                            if (lastRunInsideCursor) mEmulator.mColors.mCurrentColors[COLOR_INDEX_CURSOR] else 0
                        val invertCursorTextColor =
                            lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape
                        this.drawTextRun(
                            canvas,
                            line,
                            palette,
                            heightOffset,
                            lastRunStartColumn,
                            columnWidthSinceLastRun,
                            lastRunStartIndex,
                            charsSinceLastRun,
                            measuredWidthForRun,
                            cursorColor,
                            cursorShape,
                            lastRunStyle,
                            reverseVideo || invertCursorTextColor || lastRunInsideSelection
                        )
                    }
                    measuredWidthForRun = 0.0f
                    lastRunStyle = style
                    lastRunInsideCursor = insideCursor
                    lastRunInsideSelection = insideSelection
                    lastRunStartColumn = column
                    lastRunStartIndex = currentCharIndex
                    lastRunFontWidthMismatch = fontWidthMismatch
                }
                measuredWidthForRun += measuredCodePointWidth
                column += codePointWcWidth
                currentCharIndex += charsForCodePoint
                while (currentCharIndex < charsUsedInLine && 0 >= width(line, currentCharIndex)) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += if (Character.isHighSurrogate(line[currentCharIndex])) 2 else 1
                }
            }
            val columnWidthSinceLastRun = columns - lastRunStartColumn
            val charsSinceLastRun = currentCharIndex - lastRunStartIndex
            val cursorColor =
                if (lastRunInsideCursor) mEmulator.mColors.mCurrentColors[COLOR_INDEX_CURSOR] else 0
            val invertCursorTextColor =
                lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape
            this.drawTextRun(
                canvas,
                line,
                palette,
                heightOffset,
                lastRunStartColumn,
                columnWidthSinceLastRun,
                lastRunStartIndex,
                charsSinceLastRun,
                measuredWidthForRun,
                cursorColor,
                cursorShape,
                lastRunStyle,
                reverseVideo || invertCursorTextColor || lastRunInsideSelection
            )
        }
    }

    private fun drawTextRun(
        canvas: Canvas,
        text: CharArray,
        palette: IntArray,
        y: Float,
        startColumn: Int,
        runWidthColumns: Int,
        startCharIndex: Int,
        runWidthChars: Int,
        mes: Float,
        cursor: Int,
        cursorStyle: Int,
        textStyle: Long,
        reverseVideo: Boolean
    ) {
        var mes1 = mes
        var foreColor = decodeForeColor(textStyle)
        val effect = decodeEffect(textStyle)
        var backColor = decodeBackColor(textStyle)
        val bold =
            0 != (effect and (CHARACTER_ATTRIBUTE_BOLD or CHARACTER_ATTRIBUTE_BLINK))
        val underline = 0 != (effect and CHARACTER_ATTRIBUTE_UNDERLINE)
        val italic = 0 != (effect and CHARACTER_ATTRIBUTE_ITALIC)
        val strikeThrough = 0 != (effect and CHARACTER_ATTRIBUTE_STRIKETHROUGH)
        val dim = 0 != (effect and CHARACTER_ATTRIBUTE_DIM)
        val fontWidth = if (italic) this.mItalicFontWidth else this.fontWidth
        val fontLineSpacing = if (italic) this.mItalicFontLineSpacing else this.fontLineSpacing
        val fontAscent = if (italic) this.mItalicFontAscent else this.mFontAscent
        val fontLineSpacingAndAscent =
            if (italic) this.mItalicFontLineSpacingAndAscent else this.mFontLineSpacingAndAscent
        if (-0x1000000 != (foreColor and -0x1000000)) {
            // If enabled, let bold have bright colors if applicable (one of the first 8):
            if (bold && 0 <= foreColor && 8 > foreColor) foreColor += 8
            foreColor = palette[foreColor]
        }
        if (-0x1000000 != (backColor and -0x1000000)) {
            backColor = palette[backColor]
        }
        // Reverse video here if _one and only one_ of the reverse flags are set:
        val reverseVideoHere =
            reverseVideo xor (0 != (effect and (CHARACTER_ATTRIBUTE_INVERSE)))
        if (reverseVideoHere) {
            val tmp = foreColor
            foreColor = backColor
            backColor = tmp
        }
        var left = startColumn * fontWidth
        var right = left + runWidthColumns * fontWidth
        mes1 /= fontWidth
        var savedMatrix = false
        if (0.01 < abs((mes1 - runWidthColumns).toDouble())) {
            canvas.save()
            canvas.scale(runWidthColumns / mes1, 1.0f)
            left *= mes1 / runWidthColumns
            right *= mes1 / runWidthColumns
            savedMatrix = true
        }
        if (backColor != palette[COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.color = backColor
            canvas.drawRect(
                left,
                y - fontLineSpacingAndAscent + fontAscent,
                right,
                y,
                this.mTextPaint
            )
        }
        if (0 != cursor) {
            mTextPaint.color = cursor
            // fontLineSpacingAndAscent - fontAscent isn't equals to
            // fontLineSpacing?
            var cursorHeight = fontLineSpacing.toFloat()
            if (TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE == cursorStyle) cursorHeight /= 4.0f
            else if (TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR == cursorStyle) right -= (((right - left) * 3) / 4.0).toFloat()
            canvas.drawRect(left, y - cursorHeight, right, y, this.mTextPaint)
        }
        if (0 == (effect and CHARACTER_ATTRIBUTE_INVISIBLE)) {
            if (dim) {
                var red = (0xFF and (foreColor shr 16))
                var green = (0xFF and (foreColor shr 8))
                var blue = (0xFF and foreColor)
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = (red shl 1) / 3
                green = (green shl 1) / 3
                blue = (blue shl 1) / 3
                foreColor = -0x1000000 + (red shl 16) + (green shl 8) + blue
            }
            mTextPaint.setTypeface(this.mTypeface)
            if (italic) mTextPaint.setTypeface(this.mItalicTypeface)
            mTextPaint.isFakeBoldText = bold
            mTextPaint.isUnderlineText = underline
            mTextPaint.textSkewX = 0.0f
            if (italic && this.mItalicTypeface == this.mTypeface) mTextPaint.textSkewX = -0.35f
            mTextPaint.isStrikeThruText = strikeThrough
            mTextPaint.color = foreColor
            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(
                text,
                startCharIndex,
                runWidthChars,
                startCharIndex,
                runWidthChars,
                left,
                y - this.mFontLineSpacingAndAscent,
                false,
                this.mTextPaint
            )
        }
        if (savedMatrix) canvas.restore()
    }
}
