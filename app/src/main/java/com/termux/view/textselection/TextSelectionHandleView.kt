package com.termux.view.textselection

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import com.termux.utils.data.TerminalManager.console
import com.termux.utils.ui.primary
import com.termux.view.textselection.TextSelectionCursorController.consoleCord
import com.termux.view.textselection.TextSelectionCursorController.hideFloatingMenu
import com.termux.view.textselection.TextSelectionCursorController.showFloatingMenu

private val paint by lazy { Paint().apply { color = primary } }

class TextSelectionHandleView(val int: Int) : View(console.context) {
    private val mHandle = PopupWindow(this, 40, 40)
    private val cur = if (int == 0) 2 else 0

    fun hide() {
        mHandle.dismiss()
        selectors[int] = -1
        selectors[int + 1] = -1
    }


    fun positionAtCursor(cx: Int, cy: Int) {
        selectors[int] = cx
        selectors[int + 1] = cy
        update()
    }

    fun update() {
        val x = console.getPointX(selectors[int]) + consoleCord[0]
        val y = console.getPointY(selectors[int + 1] + 1) + consoleCord[1]
        if (isShowing) mHandle.update(x, y, -1, -1)
        else mHandle.showAtLocation(console, 0, x, y)
    }


    public override fun onDraw(c: Canvas): Unit = c.drawCircle(20f, 20f, 20f, paint)

    private var dx = 0f
    private var dy = 0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dx = event.x
                dy = event.y
                hideFloatingMenu()
            }

            MotionEvent.ACTION_MOVE -> {
                updatePosition(event.rawX - dx - consoleCord[0], event.rawY - dy - consoleCord[1])
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showFloatingMenu()
        }
        return true
    }

    private val isShowing: Boolean
        get() = mHandle.isShowing

    private fun updatePosition(x: Float, y: Float) {
        val screen = console.mEmulator.screen
        val scrollRows = screen.activeRows - console.mEmulator.mRows
        selectors[int] = console.getCursorX(x)
        selectors[int + 1] = console.getCursorY(y)
        if (0 > selectors[int]) {
            selectors[int] = 0
        }
        if (selectors[int + 1] < -scrollRows) {
            selectors[int + 1] = -scrollRows
        } else if (selectors[int + 1] > console.mEmulator.mRows - 1) {
            selectors[int + 1] = console.mEmulator.mRows - 1
        }
        if (selectors[1] > selectors[3]) {
            selectors[int + 1] = selectors[cur + 1]
        }
        if (selectors[1] == selectors[3] && selectors[0] > selectors[2]) {
            selectors[int] = selectors[cur]
        }
        if (!console.mEmulator.isAlternateBufferActive) {
            var topRow = console.topRow
            if (selectors[int + 1] <= topRow) {
                topRow--
                if (topRow < -scrollRows) {
                    topRow = -scrollRows
                }
            } else if (selectors[int + 1] >= topRow + console.mEmulator.mRows) {
                topRow++
                if (0 < topRow) {
                    topRow = 0
                }
            }
            console.topRow = topRow
        }
        console.invalidate()
        update()
    }
}
