package com.termux.utils

import android.graphics.Color

var primary : Int = Color.WHITE
    set(value) {
        field = value
        lum = (Color.luminance(value) > 0.5)
        secondary = adjustAlpha(if (lum) Color.WHITE else Color.BLACK)
    }
var secondary : Int = 0
var lum : Boolean = true
fun getContrastColor(bgColor : Int) : Int = if (bgColor == primary && lum) Color.BLACK else Color.WHITE

private fun adjustAlpha(color : Int) : Int = (color and 0x00FFFFFF) or (75 shl 24)
