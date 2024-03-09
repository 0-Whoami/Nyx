package com.termux.terminal

import android.graphics.Bitmap
import com.termux.terminal.TerminalBitmap.Companion.resizeBitmap
import kotlin.math.min

/**
 * A circular buffer of [TerminalRow]:s which keeps notes about what is visible on a logical console and the scroll
 * history.
 *
 *
 * See  for how to map from logical console rows to array indices.
 */
internal class WorkingTerminalBitmap(w: Int, h: Int) {
    private val colorMap: IntArray = IntArray(256)
    var width: Int = 0
    var height: Int = 0
    var bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private var curX: Int = 0
    private var curY: Int = 0
    private var color: Int

    init {
        bitmap.eraseColor(0)
        val sixelInitialColorMap = intArrayOf(
            -0x1000000,
            -0xcccc34,
            -0x33dcdd,
            -0xcc33cd,
            -0x33cc34,
            -0xcc3334,
            -0x3333cd,
            -0x888889,
            -0xbbbbbc,
            -0xa9a967,
            -0x66bbbc,
            -0xa966aa,
            -0x66a967,
            -0xa96667,
            -0x6666aa,
            -0x333334
        )
        System.arraycopy(sixelInitialColorMap, 0, this.colorMap, 0, 16)
        this.color = colorMap[0]
    }

    fun sixelChar(c: Int, rep: Int) {
        var rep1 = rep
        if ('$'.code == c) {
            this.curX = 0
            return
        }
        if ('-'.code == c) {
            this.curX = 0
            this.curY += 6
            return
        }
        if (bitmap.width < this.curX + rep1) {
            try {
                this.bitmap = resizeBitmap(
                    bitmap, this.curX + rep1 + 100,
                    bitmap.height
                )
            } catch (ignored: OutOfMemoryError) {
            }
        }
        if (bitmap.height < this.curY + 6) {
            // Very unlikely to resize both at the same time
            try {
                this.bitmap = resizeBitmap(
                    bitmap,
                    bitmap.width, this.curY + 100
                )
            } catch (ignored: OutOfMemoryError) {
            }
        }
        if (this.curX + rep1 > bitmap.width) {
            rep1 = bitmap.width - this.curX
        }
        if (this.curY + 6 > bitmap.height) {
            return
        }
        if (0 < rep1 && '?'.code <= c && '~'.code >= c) {
            val b = c - '?'.code
            if (this.curY + 6 > this.height) {
                this.height = this.curY + 6
            }
            while (0 < rep1) {
                rep1--
                var i = 0
                while (6 > i) {
                    if (0 != (b and (1 shl i))) {
                        bitmap.setPixel(this.curX, this.curY + i, this.color)
                    }
                    i++
                }
                this.curX += 1
                if (this.curX > this.width) {
                    this.width = this.curX
                }
            }
        }
    }

    fun sixelSetColor(col: Int) {
        if (col in 0..255) {
            this.color = colorMap[col]
        }
    }

    fun sixelSetColor(col: Int, r: Int, g: Int, b: Int) {
        if (col in 0..255) {
            val red = min(255, (r * 255 / 100))
            val green = min(255, (g * 255 / 100))
            val blue = min(255, (b * 255 / 100))
            color = -0x1000000 or (red shl 16) or (green shl 8) or blue
            colorMap[col] = color
        }
    }
}
