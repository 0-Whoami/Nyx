package com.termux.view.textselection

import android.text.TextUtils
import android.view.MotionEvent
import android.view.ViewTreeObserver.OnTouchModeChangeListener
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.WcWidth.width
import com.termux.view.Console

class TextSelectionCursorController(private val console: Console) : OnTouchModeChangeListener {
    private val mStartHandle = TextSelectionHandleView(console, this)
    private val mEndHandle = TextSelectionHandleView(console, this)
    var isActive: Boolean = false
        private set

    private var mSelX1 = -1
    private var mSelX2 = -1
    private var mSelY1 = -1
    private var mSelY2 = -1
    private val floatingMenu = FloatingMenu(console.context, {
        console.currentSession.onCopyTextToClipboard(selectedText)
        console.stopTextSelectionMode()
    }, {
        console.stopTextSelectionMode()
        console.currentSession.onPasteTextFromClipboard()
    })

    fun show(event: MotionEvent) {
        this.setInitialTextSelectionPosition(event)
        mStartHandle.positionAtCursor(this.mSelX1, this.mSelY1)
        mEndHandle.positionAtCursor(this.mSelX2 + 1, this.mSelY2)
        floatingMenu.show(
            (mSelX1 * console.mRenderer.fontWidth + console.mActivity.linearLayout.x).toInt(),
            (mSelY1 * console.mRenderer.fontLineSpacing + console.mActivity.linearLayout.y - 40).toInt()
        )
        this.isActive = true
    }

    fun hide(): Boolean {
        if (!this.isActive) return false
        mStartHandle.hide()
        mEndHandle.hide()
        floatingMenu.popupWindow.dismiss()
        this.mSelY2 = -1
        this.mSelX2 = this.mSelY2
        this.mSelY1 = this.mSelX2
        this.mSelX1 = this.mSelY1
        this.isActive = false
        return true
    }

    fun render() {
        if (!this.isActive) return
        mStartHandle.positionAtCursor(this.mSelX1, this.mSelY1)
        mEndHandle.positionAtCursor(this.mSelX2 + 1, this.mSelY2)
    }

    private fun setInitialTextSelectionPosition(event: MotionEvent) {
        val columnAndRow = console.getColumnAndRow(event, true)
        this.mSelX2 = columnAndRow[0]
        this.mSelX1 = this.mSelX2
        this.mSelY2 = columnAndRow[1]
        this.mSelY1 = this.mSelY2
        val screen = console.mEmulator.screen
        if (" " != screen.getSelectedText(
                this.mSelX1, mSelY1, mSelX1, mSelY1
            )
        ) {
            // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && screen.getSelectedText(
                    this.mSelX1 - 1, this.mSelY1, this.mSelX1 - 1, this.mSelY1
                ).isNotEmpty()
            ) {
                mSelX1--
            }
            while (this.mSelX2 < this.console.mEmulator.mColumns - 1 && screen.getSelectedText(
                    this.mSelX2 + 1, this.mSelY1, this.mSelX2 + 1, this.mSelY1
                ).isNotEmpty()
            ) {
                mSelX2++
            }
        }
    }

    fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val screen = console.mEmulator.screen
        val scrollRows = screen.activeRows - this.console.mEmulator.mRows
        if (handle === this.mStartHandle) {
            this.mSelX1 = this.console.getCursorX(x.toFloat())
            this.mSelY1 = this.console.getCursorY(y.toFloat())
            if (0 > mSelX1) {
                this.mSelX1 = 0
            }
            if (this.mSelY1 < -scrollRows) {
                this.mSelY1 = -scrollRows
            } else if (this.mSelY1 > this.console.mEmulator.mRows - 1) {
                this.mSelY1 = this.console.mEmulator.mRows - 1
            }
            if (this.mSelY1 > this.mSelY2) {
                this.mSelY1 = this.mSelY2
            }
            if (this.mSelY1 == this.mSelY2 && this.mSelX1 > this.mSelX2) {
                this.mSelX1 = this.mSelX2
            }
            if (!this.console.mEmulator.isAlternateBufferActive) {
                var topRow = this.console.topRow
                if (this.mSelY1 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (this.mSelY1 >= topRow + this.console.mEmulator.mRows) {
                    topRow++
                    if (0 < topRow) {
                        topRow = 0
                    }
                }
                this.console.topRow = topRow
            }
            this.mSelX1 = getValidCurX(screen, this.mSelY1, this.mSelX1)
        } else {
            this.mSelX2 = this.console.getCursorX(x.toFloat())
            this.mSelY2 = this.console.getCursorY(y.toFloat())
            if (0 > mSelX2) {
                this.mSelX2 = 0
            }
            if (this.mSelY2 < -scrollRows) {
                this.mSelY2 = -scrollRows
            } else if (this.mSelY2 > this.console.mEmulator.mRows - 1) {
                this.mSelY2 = this.console.mEmulator.mRows - 1
            }
            if (this.mSelY1 > this.mSelY2) {
                this.mSelY2 = this.mSelY1
            }
            if (this.mSelY1 == this.mSelY2 && this.mSelX1 > this.mSelX2) {
                this.mSelX2 = this.mSelX1
            }
            if (!this.console.mEmulator.isAlternateBufferActive) {
                var topRow = this.console.topRow
                if (this.mSelY2 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (this.mSelY2 >= topRow + this.console.mEmulator.mRows) {
                    topRow++
                    if (0 < topRow) {
                        topRow = 0
                    }
                }
                this.console.topRow = topRow
            }
            this.mSelX2 = getValidCurX(screen, this.mSelY2, this.mSelX2)
        }
        this.console.invalidate()
    }

    fun decrementYTextSelectionCursors(decrement: Int) {
        this.mSelY1 -= decrement
        this.mSelY2 -= decrement
    }

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (!isInTouchMode) {
            console.stopTextSelectionMode()
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
        get() = console.mEmulator.getSelectedText(
            this.mSelX1, this.mSelY1, this.mSelX2, this.mSelY2
        )


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
