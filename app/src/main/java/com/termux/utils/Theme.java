package com.termux.utils;

import android.graphics.Color;

public enum Theme {
    ;
    public static int primary = Color.WHITE;
    public static int secondary = adjustAlpha(primary);
    private static boolean lum = true;

    public static void setPrimary(int value) {
        primary = value;
        lum = (Color.luminance(value) > 0.5);
        secondary = adjustAlpha(lum ? Color.WHITE : Color.BLACK);
    }

    public static int getContrastColor(int bgColor) {
        return bgColor == primary && lum ? Color.BLACK : Color.WHITE;
    }

    private static int adjustAlpha(int color) {
        return (color & 0x00FFFFFF) | (75 << 24);
    }
}
