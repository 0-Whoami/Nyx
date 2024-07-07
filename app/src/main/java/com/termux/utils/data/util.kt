package com.termux.utils.data


fun isPointInCircle(
    centerX: Float, centerY: Float, radius: Float, pointX: Float, pointY: Float
): Boolean {
    return (pointX - centerX) * (pointX - centerX) + (pointY - centerY) * (pointY - centerY) <= radius * radius
}
