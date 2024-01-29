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
        mCurrentColors[index] = COLOR_SCHEME.mDefaultColors[index]
    }

    /**
     * Reset all indexed colors with the default color from the color theme.
     */
    fun reset() {
        System.arraycopy(
            COLOR_SCHEME.mDefaultColors,
            0,
            this.mCurrentColors,
            0,
            TextStyle.NUM_INDEXED_COLORS
        )
    }

    /**
     * Try parse a color from a text parameter and into a specified index.
     */
    fun tryParseColor(intoIndex: Int, textParameter: String) {
        val c = parse(textParameter)
        if (0 != c) mCurrentColors[intoIndex] = c
    }

    companion object {
        /**
         * Static data - a bit ugly but ok for now.
         */
        private val COLOR_SCHEME = TerminalColorScheme()

        /**
         * Parse color according to [...](http://manpages.ubuntu.com/manpages/intrepid/man3/XQueryColor.3.html)
         *
         *
         * Highest bit is set if successful, so return value is 0xFF${R}${G}${B}. Return 0 if failed.
         */
        private fun parse(c: String): Int {
            try {
                val skipInitial: Int
                val skipBetween: Int
                if ('#' == c[0]) {
                    // #RGB, #RRGGBB, #RRRGGGBBB or #RRRRGGGGBBBB. Most significant bits.
                    skipInitial = 1
                    skipBetween = 0
                } else if (c.startsWith("rgb:")) {
                    // rgb:<red>/<green>/<blue> where <red>, <green>, <blue> := h | hh | hhh | hhhh. Scaled.
                    skipInitial = 4
                    skipBetween = 1
                } else {
                    return 0
                }
                val charsForColors = c.length - skipInitial - 2 * skipBetween
                // Unequal lengths.
                if (0 != charsForColors % 3) return 0
                val componentLength = charsForColors / 3
                val mult = 255 / (StrictMath.pow(2.0, (componentLength shl 2).toDouble()) - 1)
                var currentPosition = skipInitial
                val rString = c.substring(currentPosition, currentPosition + componentLength)
                currentPosition += componentLength + skipBetween
                val gString = c.substring(currentPosition, currentPosition + componentLength)
                currentPosition += componentLength + skipBetween
                val bString = c.substring(currentPosition, currentPosition + componentLength)
                val r = (rString.toInt(16) * mult).toInt()
                val g = (gString.toInt(16) * mult).toInt()
                val b = (bString.toInt(16) * mult).toInt()
                return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
            } catch (e: NumberFormatException) {
                return 0
            } catch (e: IndexOutOfBoundsException) {
                return 0
            }
        }
    }
}
