package com.termux.view.textselection

import android.view.MotionEvent
import com.termux.data.console

val selectors : IntArray = intArrayOf(-1, -1, -1, -1)

internal object TextSelectionCursorController {
    private val mStartHandle by lazy { TextSelectionHandleView(0) }
    private val mEndHandle by lazy { TextSelectionHandleView(2) }
    private val floatingMenu by lazy { FloatingMenu() }
    var isSelectingText : Boolean = false
    val consoleCord = IntArray(2)

    fun showTextSelectionCursor(event : MotionEvent) {
        console.getLocationInWindow(consoleCord)
        setInitialTextSelectionPosition(event)
        showFloatingMenu()
        isSelectingText = true
    }

    fun showFloatingMenu() = floatingMenu.show()
    fun hideTextSelectionCursor() : Boolean {
        if (!isSelectingText) return false
        mStartHandle.hide()
        mEndHandle.hide()
        hideFloatingMenu()
        isSelectingText = false
        return true
    }

    fun hideFloatingMenu() = floatingMenu.dismiss()

    private fun setInitialTextSelectionPosition(event : MotionEvent) {
        val (x, y) = console.getColumnAndRow(event, true)
        var mSelX1 = x
        var mSelX2 = x + 1
        val screen = console.mEmulator.screen
        if (" " != screen.getSelectedText(mSelX1, y, mSelX1, y)) { // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && screen.getSelectedText(mSelX1 - 1, y, mSelX1 - 1, y).isNotEmpty()) mSelX1--

            while (mSelX2 < console.mEmulator.mColumns - 1 && screen.getSelectedText(mSelX2 + 1, y, mSelX2 + 1, y).isNotEmpty()) mSelX2++
        }
        mStartHandle.positionAtCursor(mSelX1, y)
        mEndHandle.positionAtCursor(mSelX2, y)
        console.invalidate()
    }

    fun updateSelHandles() {
        mStartHandle.update()
        mEndHandle.update()
    }

    fun decrementYTextSelectionCursors(decrement : Int) {
        selectors[1] -= decrement
        selectors[3] -= decrement
    }


}
