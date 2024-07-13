package com.termux.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import com.termux.data.ConfigManager
import com.termux.data.RENDERING.PADDING
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BLINK
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BOLD
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_DIM
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVERSE
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_ITALIC
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH
import com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
import com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND
import com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR
import com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND
import com.termux.terminal.TextStyle.decodeBackColor
import com.termux.terminal.TextStyle.decodeEffect
import com.termux.terminal.TextStyle.decodeForeColor
import com.termux.terminal.WcWidth.width
import com.termux.view.textselection.selectors
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


    /**
     * The [Paint.getFontSpacing]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    val fontLineSpacing: Int

    /**
     * The [.mFontLineSpacing] + [.mFontAscent].
     */

    val mFontLineSpacingAndAscent: Int

    val mTextPaint = Paint().apply {
        typeface = ConfigManager.typeface
        this.textSize = textSize.toFloat()
    }

    /**
     * The [Paint.ascent]. See [...](http://www.fampennings.nl/maarten/android/08numgrid/font.png)
     */
    private val mFontAscent: Int
    private val asciiMeasures = FloatArray(127)


    init {
        fontLineSpacing = ceil(mTextPaint.fontSpacing).toInt()
        mFontAscent = ceil(mTextPaint.ascent()).toInt()
        mFontLineSpacingAndAscent = fontLineSpacing + mFontAscent
        fontWidth = mTextPaint.measureText("X")
        for (i in asciiMeasures.indices) {
            asciiMeasures[i] = mTextPaint.measureText("${i.toChar()}", 0, 1)
        }
    }

    private fun setPaddings(canvas: Canvas) {
        canvas.translate(
            PADDING + (canvas.width % fontWidth) / 2f,
            PADDING + (canvas.height % fontLineSpacing) / 2f
        )
        canvas.scale(1 - (2 * PADDING / canvas.width), 1 - (2 * PADDING / canvas.height))
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    fun render(
        mEmulator: TerminalEmulator, canvas: Canvas, topRow: Int
    ) {
        setPaddings(canvas)
        val reverseVideo = mEmulator.isReverseVideo
        val endRow = topRow + mEmulator.mRows
        val columns = mEmulator.mColumns
        val cursorCol = mEmulator.cursorCol
        val cursorRow = mEmulator.cursorRow
        val cursorVisible = mEmulator.shouldCursorBeVisible()
        val screen = mEmulator.screen
        val palette = mEmulator.mColors.mCurrentColors
        val cursorShape = mEmulator.cursorStyle
        if (reverseVideo) canvas.drawColor(
            palette[COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC
        )
        var heightOffset = 0f
        for (row in topRow until endRow) {
            heightOffset += fontLineSpacing
            val cursorX = if ((row == cursorRow && cursorVisible)) cursorCol else -1
            var selx1 = -1
            var selx2 = -1
            if (row in selectors[1]..selectors[3]) {
                if (row == selectors[1]) selx1 = selectors[0]
                selx2 =
                    if (row == selectors[3]) selectors[2] else mEmulator.mColumns
            }
            val lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row))
            val line = lineObject.mText
            val charsUsedInLine = lineObject.mSpaceUsed
            var lastRunStyle = 0L
            var lastRunInsideCursor = false
            var lastRunInsideSelection = false
            var lastRunStartColumn = -1
            var lastRunStartIndex = 0
            var lastRunFontWidthMismatch = false
            var currentCharIndex = 0
            var measuredWidthForRun = 0f
            var column = 0
            while (column < columns) {
                val charAtIndex = line[currentCharIndex]
                val charIsHighsurrogate = Character.isHighSurrogate(charAtIndex)
                val charsForCodePoint = if (charIsHighsurrogate) 2 else 1
                val codePoint = if (charIsHighsurrogate) Character.toCodePoint(
                    charAtIndex, line[currentCharIndex + 1]
                ) else charAtIndex.code
                val codePointWcWidth = width(codePoint)
                val insideCursor =
                    (cursorX == column || (2 == codePointWcWidth && cursorX == column + 1))
                val insideSelection = column in selx1..selx2
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                val measuredCodePointWidth =
                    if (codePoint < asciiMeasures.size) asciiMeasures[codePoint] else mTextPaint.measureText(
                        line, currentCharIndex, charsForCodePoint
                    )
                val fontWidthMismatch =
                    0.01f < abs(measuredCodePointWidth / fontWidth - codePointWcWidth)
                val style = lineObject.getStyle(column)
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (0 != column) {
                        val columnWidthSinceLastRun = column - lastRunStartColumn
                        val charsSinceLastRun = currentCharIndex - lastRunStartIndex
                        val cursorColor =
                            if (lastRunInsideCursor) mEmulator.mColors.mCurrentColors[COLOR_INDEX_CURSOR] else 0
                        val invertCursorTextColor =
                            lastRunInsideCursor && TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK == cursorShape
                        drawTextRun(
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
                    measuredWidthForRun = 0f
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
            drawTextRun(
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
        val bold = 0 != (effect and (CHARACTER_ATTRIBUTE_BOLD or CHARACTER_ATTRIBUTE_BLINK))
        val underline = 0 != (effect and CHARACTER_ATTRIBUTE_UNDERLINE)
        val italic = 0 != (effect and CHARACTER_ATTRIBUTE_ITALIC)
        val strikeThrough = 0 != (effect and CHARACTER_ATTRIBUTE_STRIKETHROUGH)
        val dim = 0 != (effect and CHARACTER_ATTRIBUTE_DIM)
        if (-0x1000000 != (foreColor and -0x1000000)) {
            // If enabled, let bold have bright colors if applicable (one of the first 8):
            if (bold && 0 <= foreColor && 8 > foreColor) foreColor += 8
            foreColor = palette[foreColor]
        }
        if (-0x1000000 != (backColor and -0x1000000)) {
            backColor = palette[backColor]
        }
        // Reverse video here if _one and only one_ of the reverse flags are set:
        if (reverseVideo xor (0 != (effect and (CHARACTER_ATTRIBUTE_INVERSE)))) {
            foreColor = backColor.also { backColor = foreColor }
        }
        var left = startColumn * fontWidth
        var right = left + runWidthColumns * fontWidth
        mes1 /= fontWidth
        var savedMatrix = false
        if (0.01f < abs(mes1 - runWidthColumns)) {
            canvas.save()
            canvas.scale(runWidthColumns / mes1, 1f)
            left *= mes1 / runWidthColumns
            right *= mes1 / runWidthColumns
            savedMatrix = true
        }
        if (backColor != palette[COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.color = backColor
            canvas.drawRect(
                left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint
            )
        }
        if (0 != cursor) {
            mTextPaint.color = cursor
            // fontLineSpacingAndAscent - fontAscent isn't equals to
            // fontLineSpacing?
            var cursorHeight = fontLineSpacing.toFloat()
            if (TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE == cursorStyle) cursorHeight /= 4.0f
            else if (TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR == cursorStyle) right -= (((right - left) * 3) / 4)
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint)
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
            mTextPaint.isFakeBoldText = bold
            mTextPaint.isUnderlineText = underline
            mTextPaint.textSkewX = if (italic) -0.35f else 0f
            mTextPaint.isStrikeThruText = strikeThrough
            mTextPaint.color = foreColor
            canvas.drawTextRun(
                text,
                startCharIndex,
                runWidthChars,
                startCharIndex,
                runWidthChars,
                left,
                y - mFontLineSpacingAndAscent,
                false,
                mTextPaint
            )
        }
        if (savedMatrix) canvas.restore()
    }
}
