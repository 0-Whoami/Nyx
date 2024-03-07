package com.termux.terminal

/**
 *
 *
 * Encodes effects, foreground and background colors into a 64 bit long, which are stored for each cell in a terminal
 * row in [TerminalRow.mStyle].
 *
 *
 *
 * The bit layout is:
 *
 * - 16 flags (11 currently used).
 * - 24 for foreground color (only 9 first bits if a color index).
 * - 24 for background color (only 9 first bits if a color index).
 */
const val CHARACTER_ATTRIBUTE_BOLD: Int = 1

const val CHARACTER_ATTRIBUTE_ITALIC: Int = 2

const val CHARACTER_ATTRIBUTE_UNDERLINE: Int = 4

const val CHARACTER_ATTRIBUTE_BLINK: Int = 8

const val CHARACTER_ATTRIBUTE_INVERSE: Int = 16

const val CHARACTER_ATTRIBUTE_INVISIBLE: Int = 32

const val CHARACTER_ATTRIBUTE_STRIKETHROUGH: Int = 64

/**
 * The selective erase control functions (DECSED and DECSEL) can only erase characters defined as erasable.
 *
 *
 * This bit is set if DECSCA (Select Character Protection Attribute) has been used to define the characters that
 * come after it as erasable from the console.
 *
 */
const val CHARACTER_ATTRIBUTE_PROTECTED: Int = 128

/**
 * Dim colors. Also known as faint or half intensity.
 */
const val CHARACTER_ATTRIBUTE_DIM: Int = 256
const val COLOR_INDEX_FOREGROUND: Int = 256
const val COLOR_INDEX_BACKGROUND: Int = 257
const val COLOR_INDEX_CURSOR: Int = 258

/**
 * The 256 standard color entries and the three special (foreground, background and cursor) ones.
 */
const val NUM_INDEXED_COLORS: Int = 259

/**
 * If true (24-bit) color is used for the cell for foreground.
 */
private const val CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND = 512

/**
 * If true (24-bit) color is used for the cell for foreground.
 */
private const val CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND = 1024

/**
 * Normal foreground and background colors and no effects.
 */
val NORMAL: Long = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0)

/**
 * If true, character represents a bitmap slice, not text.
 */
private const val BITMAP = 32768

fun encode(foreColor: Int, backColor: Int, effect: Int): Long {
    var result = (effect and 511).toLong()
    result = if (-0x1000000 == (-0x1000000 and foreColor)) {
        // 24-bit color.
        result or (CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND.toLong() or ((foreColor.toLong() and 0x00ffffffL) shl 40L.toInt()))
    } else {
        // Indexed color.
        result or ((foreColor.toLong() and 511L) shl 40)
    }
    result = if (-0x1000000 == (-0x1000000 and backColor)) {
        // 24-bit color.
        result or (CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND.toLong() or ((backColor.toLong() and 0x00ffffffL) shl 16L.toInt()))
    } else {
        // Indexed color.
        result or ((backColor.toLong() and 511L) shl 16L.toInt())
    }
    return result
}

fun decodeForeColor(style: Long): Int =
    if (0L == (style and CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND.toLong())) {
        ((style ushr 40) and 511L).toInt()
    } else {
        -0x1000000 or ((style ushr 40) and 0x00ffffffL).toInt()
    }


fun decodeBackColor(style: Long): Int =
    if (0L == (style and CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND.toLong())) {
        ((style ushr 16) and 511L).toInt()
    } else {
        -0x1000000 or ((style ushr 16) and 0x00ffffffL).toInt()
    }


fun decodeEffect(style: Long): Int = (style and 2047L).toInt()


fun encodeBitmap(num: Int, X: Int, Y: Int): Long =
    (num.toLong() shl 16) or (Y.toLong() shl 32) or (X.toLong() shl 48) or BITMAP.toLong()


fun isBitmap(style: Long): Boolean = 0L != (style and 0x8000L)


fun bitmapNum(style: Long): Int = (style and 0xffff0000L).toInt() shr 16


fun bitmapX(style: Long): Int = ((style shr 48) and 0xfffL).toInt()


fun bitmapY(style: Long): Int = ((style shr 32) and 0xfffL).toInt()

