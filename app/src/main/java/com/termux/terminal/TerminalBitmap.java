package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * A circular buffer of {@link TerminalRow}:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 * <p>
 * See  for how to map from logical screen rows to array indices.
 */
class TerminalBitmap {

    public Bitmap bitmap;

    public int cellWidth;

    public int cellHeight;

    public int scrollLines;

    public int[] cursorDelta;

    TerminalBitmap(final int num, final WorkingTerminalBitmap sixel, final int Y, final int X, final int cellW, final int cellH, final TerminalBuffer screen) {
        super();
        Bitmap bm = sixel.bitmap;
        bm = TerminalBitmap.resizeBitmapConstraints(bm, sixel.width, sixel.height, cellW, cellH, screen.mColumns - X);
        this.addBitmap(num, bm, Y, X, cellW, cellH, screen);
    }

    TerminalBitmap(final int num, final byte[] image, final int Y, final int X, final int cellW, final int cellH, final int width, final int height, final boolean aspect, final TerminalBuffer screen) {
        super();
        Bitmap bm = null;
        final int imageHeight;
        final int imageWidth;
        int newWidth = width;
        int newHeight = height;
        if (0 < height || 0 < width) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try {
                BitmapFactory.decodeByteArray(image, 0, image.length, options);
            } catch (final Exception ignored) {

            }
            imageHeight = options.outHeight;
            imageWidth = options.outWidth;
            if (aspect) {
                double wFactor = 9999.0;
                double hFactor = 9999.0;
                if (0 < width) {
                    wFactor = (double) width / imageWidth;
                }
                if (0 < height) {
                    hFactor = (double) height / imageHeight;
                }
                final double factor = Math.min(wFactor, hFactor);
                newWidth = (int) (factor * imageWidth);
                newHeight = (int) (factor * imageHeight);
            } else {
                if (0 >= height) {
                    newHeight = imageHeight;
                }
                if (0 >= width) {
                    newWidth = imageWidth;
                }
            }
            int scaleFactor = 1;
            while (imageHeight >= 2 * newHeight * scaleFactor && imageWidth >= 2 * newWidth * scaleFactor) {
                scaleFactor = scaleFactor << 1;
            }
            final BitmapFactory.Options scaleOptions = new BitmapFactory.Options();
            scaleOptions.inSampleSize = scaleFactor;
            try {
                bm = BitmapFactory.decodeByteArray(image, 0, image.length, scaleOptions);
            } catch (final Exception e) {

                this.bitmap = null;
                return;
            }
            if (null == bm) {

                this.bitmap = null;
                return;
            }
            final int maxWidth = (screen.mColumns - X) * cellW;
            if (newWidth > maxWidth) {
                final int cropWidth = bm.getWidth() * maxWidth / newWidth;
                try {
                    bm = Bitmap.createBitmap(bm, 0, 0, cropWidth, bm.getHeight());
                    newWidth = maxWidth;
                } catch (final OutOfMemoryError e) {
                    // This is just a memory optimization. If it fails,
                    // continue (and probably fail later).
                }
            }
            try {
                bm = Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
            } catch (final OutOfMemoryError e) {

                bm = null;
            }
        } else {
            try {
                bm = BitmapFactory.decodeByteArray(image, 0, image.length);
            } catch (final Exception ignored) {

            }
        }
        if (null == bm) {

            this.bitmap = null;
            return;
        }
        bm = TerminalBitmap.resizeBitmapConstraints(bm, bm.getWidth(), bm.getHeight(), cellW, cellH, screen.mColumns - X);
        this.addBitmap(num, bm, Y, X, cellW, cellH, screen);
        this.cursorDelta = new int[]{this.scrollLines, (this.bitmap.getWidth() + cellW - 1) / cellW};
    }

    public static Bitmap resizeBitmap(final Bitmap bm, final int w, final int h) {
        final int[] pixels = new int[bm.getAllocationByteCount()];
        bm.getPixels(pixels, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
        final Bitmap newbm;
        try {
            newbm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (final OutOfMemoryError e) {
            // Only a minor display glitch in this case
            return bm;
        }
        final int newWidth = Math.min(bm.getWidth(), w);
        final int newHeight = Math.min(bm.getHeight(), h);
        newbm.setPixels(pixels, 0, bm.getWidth(), 0, 0, newWidth, newHeight);
        return newbm;
    }

    private static Bitmap resizeBitmapConstraints(Bitmap bm, final int w, final int h, final int cellW, final int cellH, final int Columns) {
        // Width and height must be multiples of the cell width and height
        // Bitmap should not extend beyonf screen width
        if (w > cellW * Columns || 0 != (w % cellW) || 0 != (h % cellH)) {
            final int newW = Math.min(cellW * Columns, ((w - 1) / cellW) * cellW + cellW);
            final int newH = ((h - 1) / cellH) * cellH + cellH;
            try {
                bm = TerminalBitmap.resizeBitmap(bm, newW, newH);
            } catch (final OutOfMemoryError e) {
                // Only a minor display glitch in this case
            }
        }
        return bm;
    }

    private void addBitmap(final int num, Bitmap bm, final int Y, final int X, final int cellW, final int cellH, final TerminalBuffer screen) {
        if (null == bm) {
            this.bitmap = null;
            return;
        }
        final int width = bm.getWidth();
        final int height = bm.getHeight();
        this.cellWidth = cellW;
        this.cellHeight = cellH;
        final int w = Math.min(screen.mColumns - X, (width + cellW - 1) / cellW);
        final int h = (height + cellH - 1) / cellH;
        int s = 0;
        for (int i = 0; i < h; i++) {
            if (Y + i - s == screen.mScreenRows) {
                screen.scrollDownOneLine(0, screen.mScreenRows, TextStyle.NORMAL);
                s++;
            }
            for (int j = 0; j < w; j++) {
                screen.setChar(X + j, Y + i - s, '+', TextStyle.encodeBitmap(num, j, i));
            }
        }
        if (w * cellW < width) {
            try {
                bm = Bitmap.createBitmap(bm, 0, 0, w * cellW, height);
            } catch (final OutOfMemoryError e) {
                // Image cannot be cropped to only visible part due to out of memory.
                // This causes memory waste.
            }
        }
        this.bitmap = bm;
        this.scrollLines = h - s;
    }
}
