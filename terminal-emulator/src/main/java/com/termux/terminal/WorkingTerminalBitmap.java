package com.termux.terminal;

import android.graphics.Bitmap;

/**
 * A circular buffer of {@link TerminalRow}:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 * <p>
 * See  for how to map from logical screen rows to array indices.
 */
final class WorkingTerminalBitmap {

    private final int[] colorMap;
    public int width;
    public int height;
    public Bitmap bitmap;
    private int curX;
    private int curY;
    private int color;

    WorkingTerminalBitmap(final int w, final int h) {
        super();
        try {
            this.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (final OutOfMemoryError e) {
            this.bitmap = null;
        }
        this.bitmap.eraseColor(0);
        this.width = 0;
        this.height = 0;
        this.curX = 0;
        this.curY = 0;
        this.colorMap = new int[256];
        final int[] sixelInitialColorMap = {0xFF000000, 0xFF3333CC, 0xFFCC2323, 0xFF33CC33, 0xFFCC33CC, 0xFF33CCCC, 0xFFCCCC33, 0xFF777777, 0xFF444444, 0xFF565699, 0xFF994444, 0xFF569956, 0xFF995699, 0xFF569999, 0xFF999956, 0xFFCCCCCC};
        System.arraycopy(sixelInitialColorMap, 0, this.colorMap, 0, 16);
        this.color = this.colorMap[0];
    }

    public void sixelChar(final int c, int rep) {
        if (null == bitmap) {
            return;
        }
        if ('$' == c) {
            this.curX = 0;
            return;
        }
        if ('-' == c) {
            this.curX = 0;
            this.curY += 6;
            return;
        }
        if (this.bitmap.getWidth() < this.curX + rep) {
            try {
                this.bitmap = TerminalBitmap.resizeBitmap(this.bitmap, this.curX + rep + 100, this.bitmap.getHeight());
            } catch (final OutOfMemoryError ignored) {
            }
        }
        if (this.bitmap.getHeight() < this.curY + 6) {
            // Very unlikely to resize both at the same time
            try {
                this.bitmap = TerminalBitmap.resizeBitmap(this.bitmap, this.bitmap.getWidth(), this.curY + 100);
            } catch (final OutOfMemoryError ignored) {
            }
        }
        if (this.curX + rep > this.bitmap.getWidth()) {
            rep = this.bitmap.getWidth() - this.curX;
        }
        if (this.curY + 6 > this.bitmap.getHeight()) {
            return;
        }
        if (0 < rep && '?' <= c && '~' >= c) {
            final int b = c - '?';
            if (this.curY + 6 > this.height) {
                this.height = this.curY + 6;
            }
            while (0 < rep) {
                rep--;
                for (int i = 0; 6 > i; i++) {
                    if (0 != (b & (1 << i))) {
                        this.bitmap.setPixel(this.curX, this.curY + i, this.color);
                    }
                }
                this.curX += 1;
                if (this.curX > this.width) {
                    this.width = this.curX;
                }
            }
        }
    }

    public void sixelSetColor(final int col) {
        if (0 <= col && 256 > col) {
            this.color = this.colorMap[col];
        }
    }

    public void sixelSetColor(final int col, final int r, final int g, final int b) {
        if (0 <= col && 256 > col) {
            final int red = Math.min(255, r * 255 / 100);
            final int green = Math.min(255, g * 255 / 100);
            final int blue = Math.min(255, b * 255 / 100);
            this.color = 0xff000000 + (red << 16) + (green << 8) + blue;
            this.colorMap[col] = this.color;
        }
    }
}
