package com.termux.utils;

import android.graphics.Color;

import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;

public enum Theme {
    ;
    public static int primary = Color.WHITE;
    public static int secondary = (Color.WHITE & 0x00FFFFFF) | (64 << 24);
    private static int textOnPrimary = Color.BLACK;
    private static int textOnSecondary = Color.WHITE;

    public static void setPrimary(final String p, final String s) {
        primary = Color.parseColor(p);
        secondary = Color.parseColor(s);
        textOnPrimary = 0.5 < Color.luminance(primary) ? Color.BLACK : Color.WHITE;
        textOnSecondary = 0.5 < Color.luminance(secondary) ? Color.BLACK : Color.WHITE;
    }

    public static int getContrastColor(final int color) {
        if (color == primary) return textOnPrimary;
        if (color == secondary) return textOnSecondary;
        return TerminalColorScheme.DEFAULT_COLORSCHEME[TextStyle.COLOR_INDEX_FOREGROUND];
    }

}
