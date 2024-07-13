package com.termux.data

import com.termux.view.Console


fun inCircle(
    centerX: Float, centerY: Float, radius: Float, pointX: Float, pointY: Float
): Boolean {
    return (pointX - centerX) * (pointX - centerX) + (pointY - centerY) * (pointY - centerY) <= radius * radius
}

lateinit var console: Console
