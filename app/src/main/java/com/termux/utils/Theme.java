package com.termux.utils;

import android.graphics.Color;

import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;

public final class Theme {
    public static int primary = Color.WHITE;
    public static int secondary = (Color.WHITE & 0x00FFFFFF) | (0x40 << 24);
    private static int textOnPrimary = Color.BLACK;
    private static int textOnSecondary = Color.WHITE;

    public static void setPrimary(final String p, final String s) {
        try {
            Theme.primary = Color.parseColor(p);
            Theme.textOnPrimary = 0.5 < Color.luminance(Theme.primary) ? Color.BLACK : Color.WHITE;
        } catch (final Throwable ignore) {
        }
        try {
            Theme.secondary = Color.parseColor(s);
            Theme.textOnSecondary = 0.5 < Color.luminance(Theme.secondary) ? Color.BLACK : Color.WHITE;
        } catch (final Throwable ignore) {
        }
    }

    public static int getContrastColor(final int color) {
        if (color == Theme.primary) return Theme.textOnPrimary;
        if (color == Theme.secondary) return Theme.textOnSecondary;
        return TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_FOREGROUND];
    }

}
