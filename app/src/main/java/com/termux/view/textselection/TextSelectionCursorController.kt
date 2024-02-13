package com.termux.view.textselection

import android.graphics.Rect
import android.text.TextUtils
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver.OnTouchModeChangeListener
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.WcWidth.width
import com.termux.view.Con
import kotlin.math.max

class TextSelectionCursorController(private val con: Con) :
    OnTouchModeChangeListener {
    private val mStartHandle = TextSelectionHandleView(con, this)
    private val mEndHandle = TextSelectionHandleView(con, this)
    private val mHandleHeight = max(
        mStartHandle.handleHeight.toDouble(),
        mEndHandle.handleHeight.toDouble()
    ).toInt()
    var isActive: Boolean = false
        private set
    private var mShowStartTime = System.currentTimeMillis()
    private var mSelX1 = -1
    private var mSelX2 = -1
    private var mSelY1 = -1
    private var mSelY2 = -1

    /**
     * Unset the selected text stored before "MORE" button was pressed on the context menu.
     */
    lateinit var actionMode: ActionMode
        private set

    fun show(event: MotionEvent) {
        this.setInitialTextSelectionPosition(event)
        mStartHandle.positionAtCursor(this.mSelX1, this.mSelY1, true)
        mEndHandle.positionAtCursor(this.mSelX2 + 1, this.mSelY2, true)
        this.setActionModeCallBacks()
        this.mShowStartTime = System.currentTimeMillis()
        this.isActive = true
    }

    fun hide(): Boolean {
        if (!this.isActive) return false
        // prevent hide calls right after a show call, like long pressing the down key
        // 300ms seems long enough that it wouldn't cause hide problems if action button
        // is quickly clicked after the show, otherwise decrease it
        if (300 > System.currentTimeMillis() - mShowStartTime) {
            return false
        }
        mStartHandle.hide()
        mEndHandle.hide()
        actionMode.finish()
        this.mSelY2 = -1
        this.mSelX2 = this.mSelY2
        this.mSelY1 = this.mSelX2
        this.mSelX1 = this.mSelY1
        this.isActive = false
        return true
    }

    fun render() {
        if (!this.isActive) return
        mStartHandle.positionAtCursor(this.mSelX1, this.mSelY1, false)
        mEndHandle.positionAtCursor(this.mSelX2 + 1, this.mSelY2, false)
        actionMode.invalidate()
    }

    private fun setInitialTextSelectionPosition(event: MotionEvent) {
        val columnAndRow = con.getColumnAndRow(event, true)
        this.mSelX2 = columnAndRow[0]
        this.mSelX1 = this.mSelX2
        this.mSelY2 = columnAndRow[1]
        this.mSelY1 = this.mSelY2
        val screen = con.mEmulator.screen
        if (" " != screen.getSelectedText(
                this.mSelX1,
                mSelY1,
                mSelX1,
                mSelY1
            )
        ) {
            // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && screen.getSelectedText(
                    this.mSelX1 - 1,
                    this.mSelY1,
                    this.mSelX1 - 1,
                    this.mSelY1
                ).isNotEmpty()
            ) {
                mSelX1--
            }
            while (this.mSelX2 < con.mEmulator.mColumns - 1 && screen.getSelectedText(
                    this.mSelX2 + 1,
                    this.mSelY1,
                    this.mSelX2 + 1,
                    this.mSelY1
                ).isNotEmpty()
            ) {
                mSelX2++
            }
        }
    }

    private fun setActionModeCallBacks() {
        val callback: ActionMode.Callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, "Copy")
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, "Paste")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (!this@TextSelectionCursorController.isActive) {
                    // Fix issue where the dialog is pressed while being dismissed.
                    return true
                }
                when (item.itemId) {
                    ACTION_COPY -> {
                        val selectedText = this@TextSelectionCursorController.selectedText
                        con.currentSession.onCopyTextToClipboard(selectedText)
                        con.stopTextSelectionMode()
                    }

                    ACTION_PASTE -> {
                        con.stopTextSelectionMode()
                        con.currentSession.onPasteTextFromClipboard()
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
            }
        }
        this.actionMode = con.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                return callback.onCreateActionMode(mode, menu)
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return callback.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Ignore.
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                var y1 =
                    (this@TextSelectionCursorController.mSelY1 - con.topRow) * con.mRenderer.fontLineSpacing
                val y2 =
                    (this@TextSelectionCursorController.mSelY2 - con.topRow) * con.mRenderer.fontLineSpacing
                y1 += if ((y1 < con.mRenderer.fontLineSpacing shl 1)) (this@TextSelectionCursorController.mHandleHeight) else (-this@TextSelectionCursorController.mHandleHeight)
                outRect[0, y1, 0] = y2
            }
        }, ActionMode.TYPE_FLOATING)
    }

    fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val screen = con.mEmulator.screen
        val scrollRows = screen.activeRows - con.mEmulator.mRows
        if (handle === this.mStartHandle) {
            this.mSelX1 = con.getCursorX(x.toFloat())
            this.mSelY1 = con.getCursorY(y.toFloat())
            if (0 > mSelX1) {
                this.mSelX1 = 0
            }
            if (this.mSelY1 < -scrollRows) {
                this.mSelY1 = -scrollRows
            } else if (this.mSelY1 > con.mEmulator.mRows - 1) {
                this.mSelY1 = con.mEmulator.mRows - 1
            }
            if (this.mSelY1 > this.mSelY2) {
                this.mSelY1 = this.mSelY2
            }
            if (this.mSelY1 == this.mSelY2 && this.mSelX1 > this.mSelX2) {
                this.mSelX1 = this.mSelX2
            }
            if (!con.mEmulator.isAlternateBufferActive) {
                var topRow = con.topRow
                if (this.mSelY1 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (this.mSelY1 >= topRow + con.mEmulator.mRows) {
                    topRow++
                    if (0 < topRow) {
                        topRow = 0
                    }
                }
                con.topRow = topRow
            }
            this.mSelX1 = getValidCurX(screen, this.mSelY1, this.mSelX1)
        } else {
            this.mSelX2 = con.getCursorX(x.toFloat())
            this.mSelY2 = con.getCursorY(y.toFloat())
            if (0 > mSelX2) {
                this.mSelX2 = 0
            }
            if (this.mSelY2 < -scrollRows) {
                this.mSelY2 = -scrollRows
            } else if (this.mSelY2 > con.mEmulator.mRows - 1) {
                this.mSelY2 = con.mEmulator.mRows - 1
            }
            if (this.mSelY1 > this.mSelY2) {
                this.mSelY2 = this.mSelY1
            }
            if (this.mSelY1 == this.mSelY2 && this.mSelX1 > this.mSelX2) {
                this.mSelX2 = this.mSelX1
            }
            if (!con.mEmulator.isAlternateBufferActive) {
                var topRow = con.topRow
                if (this.mSelY2 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (this.mSelY2 >= topRow + con.mEmulator.mRows) {
                    topRow++
                    if (0 < topRow) {
                        topRow = 0
                    }
                }
                con.topRow = topRow
            }
            this.mSelX2 = getValidCurX(screen, this.mSelY2, this.mSelX2)
        }
        con.invalidate()
    }

    fun decrementYTextSelectionCursors(decrement: Int) {
        this.mSelY1 -= decrement
        this.mSelY2 -= decrement
    }

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (!isInTouchMode) {
            con.stopTextSelectionMode()
        }
    }

    fun getSelectors(sel: IntArray?) {
        if (null == sel || 4 != sel.size) {
            return
        }
        sel[0] = this.mSelY1
        sel[1] = this.mSelY2
        sel[2] = this.mSelX1
        sel[3] = this.mSelX2
    }

    private val selectedText: String
        /**
         * Get the currently selected text.
         */
        get() = con.mEmulator.getSelectedText(
            this.mSelX1,
            this.mSelY1,
            this.mSelX2,
            this.mSelY2
        )

    companion object {
        private const val ACTION_COPY = 1
        private const val ACTION_PASTE = 2
        private fun getValidCurX(screen: TerminalBuffer, cy: Int, cx: Int): Int {
            val line = screen.getSelectedText(0, cy, cx, cy)
            if (!TextUtils.isEmpty(line)) {
                var col = 0
                var i = 0
                val len = line.length
                while (i < len) {
                    val ch1 = line[i]
                    if (0 == ch1.code) {
                        break
                    }
                    val wc: Int
                    if (Character.isHighSurrogate(ch1) && i + 1 < len) {
                        ++i
                        val ch2 = line[i]
                        wc = width(Character.toCodePoint(ch1, ch2))
                    } else {
                        wc = width(ch1.code)
                    }
                    val cend = col + wc
                    if (cx in (col + 1)..<cend) {
                        return cend
                    }
                    if (cend == col) {
                        return col
                    }
                    col = cend
                    i++
                }
            }
            return cx
        }
    }
}
