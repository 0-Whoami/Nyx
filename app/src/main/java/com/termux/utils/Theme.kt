package com.termux.utils

import android.graphics.Color

private const val a = 100
var primary: Int = 0xffffffff.toInt()
    set(value) {
        field = value
        secondary = (value and 0x00FFFFFF) or (a shl 24)
        lum = (Color.luminance(value) > 0.5)
    }
var secondary: Int = 0xff1A1A1A.toInt()
var lum: Boolean = true
fun getContrastColor(bgColor: Int): Int =
    if (bgColor == primary && lum) Color.BLACK else Color.WHITE
