package com.termux.utils.ui

import android.graphics.Paint
import com.termux.utils.data.ConfigManager

val paint: Paint = Paint().apply {
    typeface = ConfigManager.typeface
    textSize = 35f
    textAlign = Paint.Align.CENTER
    color = colorPrimaryAccent
}
const val colorPrimaryAccent: Int = 0xff729fcf.toInt()
const val primaryTextColor: Int = 0xffd3d7cf.toInt()
const val surface: Int = 0xff1a1a1a.toInt()
const val secondaryText: Int = 0xff888a85.toInt()
