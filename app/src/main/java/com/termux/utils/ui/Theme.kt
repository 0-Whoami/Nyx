package com.termux.utils.ui

import android.graphics.Color

var primary: Int = 0xffffffff.toInt()
    set(value) {
        field = value
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        secondary =
            (0xFF shl 24) or (((26 * r) / 255) shl 16) or (((26 * g) / 255) shl 8) or ((26 * b) / 255)
        lum = (Color.luminance(value) > 0.5)
    }
var secondary: Int = -15066598
const val secondaryTextColor: Int = 0x80ffffff.toInt()
var lum: Boolean = true
fun getContrastColor(bgColor: Int): Int =
    if (bgColor == primary && lum) 0xFF000000.toInt() else 0xffffffff.toInt()
