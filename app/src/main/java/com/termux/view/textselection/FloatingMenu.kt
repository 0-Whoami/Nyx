package com.termux.view.textselection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow

class FloatingMenu(context: Context, val copy: () -> Unit, val paste: () -> Unit) : View(context) {
    private val paint = Paint().apply {
        typeface = typeface
        textSize = 18f
    }
    val popupWindow: PopupWindow by lazy {
        PopupWindow(this, 150, 50)
    }

    fun show(x: Int, y: Int) {
        popupWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, 150f, 50f, 25f, 25f, paint)
        paint.color = Color.BLACK
        canvas.drawText("Copy", 20f, 30f, paint)
        canvas.drawText("Paste", 85f, 30f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (event.x <= 70) {
                copy()
            } else {
                paste()
            }
        }
        return true
    }
}
