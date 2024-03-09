package com.termux.terminal

/**
 * Current terminal colors (if different from default).
 */
class TerminalColors {
    /**
     * The current terminal colors, which are normally set from the color theme, but may be set dynamically with the OSC
     * 4 control sequence.
     */
    val mCurrentColors: IntArray = IntArray(TextStyle.NUM_INDEXED_COLORS)

    /**
     * Create a new instance with default colors from the theme.
     */
    init {
        this.reset()
    }

    /**
     * Reset a particular indexed color with the default color from the color theme.
     */
    fun reset(index: Int) {
        mCurrentColors[index] = TerminalColorScheme.DEFAULT_COLORSCHEME[index]
    }

    /**
     * Reset all indexed colors with the default color from the color theme.
     */
    fun reset() =
        System.arraycopy(
            TerminalColorScheme.DEFAULT_COLORSCHEME,
            0,
            this.mCurrentColors,
            0,
            TextStyle.NUM_INDEXED_COLORS
        )


    /**
     * Try parse a color from a text parameter and into a specified index.
     */
    fun tryParseColor(intoIndex: Int, textParameter: String) {
        val c = parse(textParameter)
        if (0 != c) mCurrentColors[intoIndex] = c
    }

    companion object {

        /**
         * Parse color according to [...](http://manpages.ubuntu.com/manpages/intrepid/man3/XQueryColor.3.html)
         *
         *
         * Highest bit is set if successful, so return value is 0xFF${R}${G}${B}. Return 0 if failed.
         */
        private fun parse(c: String): Int {
            val length = c.length
            if (length < 4 || (c[0] != '#' && !c.startsWith("rgb:"))) return 0

            val isHexFormat = c[0] == '#'
            val skipInitial = if (isHexFormat) 1 else 4
            val skipBetween = if (isHexFormat) 0 else 1

            val charsForColors = length - skipInitial - 2 * skipBetween
            if (charsForColors % 3 != 0 || charsForColors > 12) return 0

            val componentLength = charsForColors / 3
            val maxComponentValue = (1 shl (componentLength shl 2)) - 1
            val mult = 255.0 / maxComponentValue

            var currentPosition = skipInitial
            val r = parseComponent(c, currentPosition, componentLength, mult)
            if (r == -1) return 0
            currentPosition += componentLength + skipBetween

            val g = parseComponent(c, currentPosition, componentLength, mult)
            if (g == -1) return 0
            currentPosition += componentLength + skipBetween

            val b = parseComponent(c, currentPosition, componentLength, mult)
            if (b == -1) return 0

            return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }

        private fun parseComponent(c: String, start: Int, length: Int, mult: Double): Int {
            var value = 0
            var multiplier = 1
            for (i in start until start + length) {
                val digit = c[i].digitToInt(16)
                if (digit == -1) return -1
                value += digit * multiplier
                multiplier *= 16
            }
            return (value * mult).toInt()
        }
    }

}
