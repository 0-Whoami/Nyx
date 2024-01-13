package com.termux.terminal;

/**
 * Current terminal colors (if different from default).
 */
public final class TerminalColors {

    /**
     * Static data - a bit ugly but ok for now.
     */
    private static final TerminalColorScheme COLOR_SCHEME = new TerminalColorScheme();

    /**
     * The current terminal colors, which are normally set from the color theme, but may be set dynamically with the OSC
     * 4 control sequence.
     */
    public final int[] mCurrentColors = new int[TextStyle.NUM_INDEXED_COLORS];

    /**
     * Create a new instance with default colors from the theme.
     */
    public TerminalColors() {
        this.reset();
    }

    /**
     * Parse color according to <a href="http://manpages.ubuntu.com/manpages/intrepid/man3/XQueryColor.3.html">...</a>
     * <p/>
     * Highest bit is set if successful, so return value is 0xFF${R}${G}${B}. Return 0 if failed.
     */
    private static int parse(final String c) {
        try {
            final int skipInitial;
            final int skipBetween;
            if ('#' == c.charAt(0)) {
                // #RGB, #RRGGBB, #RRRGGGBBB or #RRRRGGGGBBBB. Most significant bits.
                skipInitial = 1;
                skipBetween = 0;
            } else if (c.startsWith("rgb:")) {
                // rgb:<red>/<green>/<blue> where <red>, <green>, <blue> := h | hh | hhh | hhhh. Scaled.
                skipInitial = 4;
                skipBetween = 1;
            } else {
                return 0;
            }
            final int charsForColors = c.length() - skipInitial - 2 * skipBetween;
            // Unequal lengths.
            if (0 != charsForColors % 3)
                return 0;
            final int componentLength = charsForColors / 3;
            final double mult = 255 / (StrictMath.pow(2, componentLength << 2) - 1);
            int currentPosition = skipInitial;
            final String rString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            final String gString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            final String bString = c.substring(currentPosition, currentPosition + componentLength);
            final int r = (int) (Integer.parseInt(rString, 16) * mult);
            final int g = (int) (Integer.parseInt(gString, 16) * mult);
            final int b = (int) (Integer.parseInt(bString, 16) * mult);
            return 0xFF << 24 | r << 16 | g << 8 | b;
        } catch (final NumberFormatException | IndexOutOfBoundsException e) {
            return 0;
        }
    }

    /**
     * Reset a particular indexed color with the default color from the color theme.
     */
    public void reset(final int index) {
        this.mCurrentColors[index] = TerminalColors.COLOR_SCHEME.mDefaultColors[index];
    }

    /**
     * Reset all indexed colors with the default color from the color theme.
     */
    public void reset() {
        System.arraycopy(TerminalColors.COLOR_SCHEME.mDefaultColors, 0, this.mCurrentColors, 0, TextStyle.NUM_INDEXED_COLORS);
    }

    /**
     * Try parse a color from a text parameter and into a specified index.
     */
    public void tryParseColor(final int intoIndex, final String textParameter) {
        final int c = TerminalColors.parse(textParameter);
        if (0 != c)
            this.mCurrentColors[intoIndex] = c;
    }

}
