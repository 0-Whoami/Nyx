package com.termux.view.textselection

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import com.termux.console
import com.termux.data.ConfigManager
import com.termux.utils.Theme.getContrastColor
import com.termux.utils.Theme.primary
import com.termux.view.textselection.TextSelectionCursorController.consoleCord

class FloatingMenu : View(console.context) {
    private val paint = Paint().apply {
        typeface = ConfigManager.typeface
        textSize = 25f
        textAlign = Paint.Align.CENTER
    }
    private val popupWindow : PopupWindow = PopupWindow(this, 180, 60)

    fun show() {
        popupWindow.showAtLocation(this, 0, console.getPointX(selectors[0]) + consoleCord[0], console.getPointY(selectors[1]) + consoleCord[1] - 60)
    }

    fun dismiss() : Unit = popupWindow.dismiss()

    override fun onDraw(canvas : Canvas) {
        paint.color = primary
        canvas.drawRoundRect(0f, 0f, 180f, 60f, 30f, 30f, paint)
        paint.color = getContrastColor(primary)
        canvas.drawText("Copy", 45f, 38f, paint)
        canvas.drawText("Paste", 135f, 38f, paint)
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (event.x <= 90) {
                console.onCopyTextToClipboard(console.mEmulator.getSelectedText())
            } else {
                console.onPasteTextFromClipboard()
            }
            console.stopTextSelectionMode()
        }
        return true
    }
}
