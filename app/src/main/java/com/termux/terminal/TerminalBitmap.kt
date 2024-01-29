package com.termux.terminal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.min

/**
 * A circular buffer of [TerminalRow]:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 *
 *
 * See  for how to map from logical screen rows to array indices.
 */
internal class TerminalBitmap {

    var bitmap: Bitmap? = null


    var cellWidth: Int = 0


    var cellHeight: Int = 0


    var scrollLines: Int = 0

    lateinit var cursorDelta: IntArray

    constructor(
        num: Int,
        sixel: WorkingTerminalBitmap,
        Y: Int,
        X: Int,
        cellW: Int,
        cellH: Int,
        screen: TerminalBuffer
    ) : super() {
        var bm = sixel.bitmap
        bm = resizeBitmapConstraints(
            bm,
            sixel.width,
            sixel.height,
            cellW,
            cellH,
            screen.mColumns - X
        )
        this.addBitmap(num, bm, Y, X, cellW, cellH, screen)
    }

    constructor(
        num: Int,
        image: ByteArray,
        Y: Int,
        X: Int,
        cellW: Int,
        cellH: Int,
        width: Int,
        height: Int,
        aspect: Boolean,
        screen: TerminalBuffer
    ) : super() {
        var bm: Bitmap? = null
        val imageHeight: Int
        val imageWidth: Int
        var newWidth = width
        var newHeight = height
        if (0 < height || 0 < width) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            try {
                BitmapFactory.decodeByteArray(image, 0, image.size, options)
            } catch (ignored: Exception) {
            }
            imageHeight = options.outHeight
            imageWidth = options.outWidth
            if (aspect) {
                var wFactor = 9999.0
                var hFactor = 9999.0
                if (0 < width) {
                    wFactor = width.toDouble() / imageWidth
                }
                if (0 < height) {
                    hFactor = height.toDouble() / imageHeight
                }
                val factor = min(wFactor, hFactor)
                newWidth = (factor * imageWidth).toInt()
                newHeight = (factor * imageHeight).toInt()
            } else {
                if (0 >= height) {
                    newHeight = imageHeight
                }
                if (0 >= width) {
                    newWidth = imageWidth
                }
            }
            var scaleFactor = 1
            while (imageHeight >= 2 * newHeight * scaleFactor && imageWidth >= 2 * newWidth * scaleFactor) {
                scaleFactor = scaleFactor shl 1
            }
            val scaleOptions = BitmapFactory.Options()
            scaleOptions.inSampleSize = scaleFactor
            try {
                bm = BitmapFactory.decodeByteArray(image, 0, image.size, scaleOptions)
            } catch (e: Exception) {
                this.bitmap = null
                return
            }
            if (null == bm) {
                this.bitmap = null
                return
            }
            val maxWidth = (screen.mColumns - X) * cellW
            if (newWidth > maxWidth) {
                val cropWidth = bm.width * maxWidth / newWidth
                try {
                    bm = Bitmap.createBitmap(bm, 0, 0, cropWidth, bm.height)
                    newWidth = maxWidth
                } catch (e: OutOfMemoryError) {
                    // This is just a memory optimization. If it fails,
                    // continue (and probably fail later).
                }
            }
            bm = try {
                Bitmap.createScaledBitmap(bm!!, newWidth, newHeight, true)
            } catch (e: OutOfMemoryError) {
                null
            }
        } else {
            try {
                bm = BitmapFactory.decodeByteArray(image, 0, image.size)
            } catch (ignored: Exception) {
            }
        }
        if (null == bm) {
            this.bitmap = null
            return
        }
        bm = resizeBitmapConstraints(bm, bm.width, bm.height, cellW, cellH, screen.mColumns - X)
        this.addBitmap(num, bm, Y, X, cellW, cellH, screen)
        this.cursorDelta = intArrayOf(this.scrollLines, (bitmap!!.width + cellW - 1) / cellW)
    }

    private fun addBitmap(
        num: Int,
        bm: Bitmap?,
        Y: Int,
        X: Int,
        cellW: Int,
        cellH: Int,
        screen: TerminalBuffer
    ) {
        var bm = bm
        if (null == bm) {
            this.bitmap = null
            return
        }
        val width = bm.width
        val height = bm.height
        this.cellWidth = cellW
        this.cellHeight = cellH
        val w = min((screen.mColumns - X).toDouble(), ((width + cellW - 1) / cellW).toDouble())
            .toInt()
        val h = (height + cellH - 1) / cellH
        var s = 0
        for (i in 0 until h) {
            if (Y + i - s == screen.mScreenRows) {
                screen.scrollDownOneLine(0, screen.mScreenRows, TextStyle.NORMAL)
                s++
            }
            for (j in 0 until w) {
                screen.setChar(X + j, Y + i - s, '+'.code, TextStyle.encodeBitmap(num, j, i))
            }
        }
        if (w * cellW < width) {
            try {
                bm = Bitmap.createBitmap(bm, 0, 0, w * cellW, height)
            } catch (e: OutOfMemoryError) {
                // Image cannot be cropped to only visible part due to out of memory.
                // This causes memory waste.
            }
        }
        this.bitmap = bm
        this.scrollLines = h - s
    }

    companion object {

        fun resizeBitmap(bm: Bitmap, w: Int, h: Int): Bitmap {
            val pixels = IntArray(bm.allocationByteCount)
            bm.getPixels(pixels, 0, bm.width, 0, 0, bm.width, bm.height)
            val newbm: Bitmap
            try {
                newbm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                // Only a minor display glitch in this case
                return bm
            }
            val newWidth = min(bm.width.toDouble(), w.toDouble()).toInt()
            val newHeight = min(bm.height.toDouble(), h.toDouble()).toInt()
            newbm.setPixels(pixels, 0, bm.width, 0, 0, newWidth, newHeight)
            return newbm
        }

        private fun resizeBitmapConstraints(
            bm: Bitmap,
            w: Int,
            h: Int,
            cellW: Int,
            cellH: Int,
            Columns: Int
        ): Bitmap {
            // Width and height must be multiples of the cell width and height
            // Bitmap should not extend beyonf screen width
            var bm = bm
            if ((w > cellW * Columns || 0 != w % cellW) || 0 != (h % cellH)) {
                val newW = min(
                    (cellW * Columns).toDouble(),
                    (((w - 1) / cellW) * cellW + cellW).toDouble()
                )
                    .toInt()
                val newH = ((h - 1) / cellH) * cellH + cellH
                try {
                    bm = resizeBitmap(bm, newW, newH)
                } catch (e: OutOfMemoryError) {
                    // Only a minor display glitch in this case
                }
            }
            return bm
        }
    }
}
