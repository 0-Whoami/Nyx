package com.termux.utils.ui

const val primary: Int = 0xffffffff.toInt()
const val secondary: Int = 0xff1a1a1a.toInt()
const val secondaryTextColor: Int = 0x80ffffff.toInt()

fun getContrastColor(bgColor: Int): Int {
    return if (bgColor == primary) 0xFF000000.toInt() else 0xffffffff.toInt()
}
