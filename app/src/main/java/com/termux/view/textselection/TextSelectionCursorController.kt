package com.termux.view.textselection

import android.text.TextUtils
import android.view.MotionEvent
import android.view.ViewTreeObserver.OnTouchModeChangeListener
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.WcWidth.width
import com.termux.view.Console

class TextSelectionCursorController(private val console: Console) : OnTouchModeChangeListener {
    private val mStartHandle by lazy { TextSelectionHandleView(console, this) }
    private val mEndHandle by lazy { TextSelectionHandleView(console, this) }
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
        setInitialTextSelectionPosition(event)
        mStartHandle.positionAtCursor(mSelX1, mSelY1)
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2)
        floatingMenu.show(
            (mSelX1 * console.mRenderer.fontWidth + console.mActivity.linearLayout.x).toInt(),
            (mSelY1 * console.mRenderer.fontLineSpacing + console.mActivity.linearLayout.y - 40).toInt()
        )
        isActive = true
    }

    fun hide(): Boolean {
        if (!isActive) return false
        mStartHandle.hide()
        mEndHandle.hide()
        floatingMenu.popupWindow.dismiss()
        mSelY2 = -1
        mSelX2 = mSelY2
        mSelY1 = mSelX2
        mSelX1 = mSelY1
        isActive = false
        return true
    }

    fun render() {
        if (!isActive) return
        mStartHandle.positionAtCursor(mSelX1, mSelY1)
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2)
    }

    private fun setInitialTextSelectionPosition(event: MotionEvent) {
        val columnAndRow = console.getColumnAndRow(event, true)
        mSelX2 = columnAndRow[0]
        mSelX1 = mSelX2
        mSelY2 = columnAndRow[1]
        mSelY1 = mSelY2
        val screen = console.mEmulator.screen
        if (" " != screen.getSelectedText(
                mSelX1, mSelY1, mSelX1, mSelY1
            )
        ) {
            // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && screen.getSelectedText(
                    mSelX1 - 1, mSelY1, mSelX1 - 1, mSelY1
                ).isNotEmpty()
            ) {
                mSelX1--
            }
            while (mSelX2 < console.mEmulator.mColumns - 1 && screen.getSelectedText(
                    mSelX2 + 1, mSelY1, mSelX2 + 1, mSelY1
                ).isNotEmpty()
            ) {
                mSelX2++
            }
        }
    }

    fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val screen = console.mEmulator.screen
        val scrollRows = screen.activeRows - console.mEmulator.mRows
        if (handle === mStartHandle) {
            mSelX1 = console.getCursorX(x.toFloat())
            mSelY1 = console.getCursorY(y.toFloat())
            if (0 > mSelX1) {
                mSelX1 = 0
            }
            if (mSelY1 < -scrollRows) {
                mSelY1 = -scrollRows
            } else if (mSelY1 > console.mEmulator.mRows - 1) {
                mSelY1 = console.mEmulator.mRows - 1
            }
            if (mSelY1 > mSelY2) {
                mSelY1 = mSelY2
            }
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) {
                mSelX1 = mSelX2
            }
            if (!console.mEmulator.isAlternateBufferActive) {
                var topRow = console.topRow
                if (mSelY1 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (mSelY1 >= topRow + console.mEmulator.mRows) {
                    topRow++
                    if (0 < topRow) {
                        topRow = 0
                    }
                }
                console.topRow = topRow
            }
            mSelX1 = getValidCurX(screen, mSelY1, mSelX1)
        } else {
            mSelX2 = console.getCursorX(x.toFloat())
            mSelY2 = console.getCursorY(y.toFloat())
            if (0 > mSelX2) {
                mSelX2 = 0
            }
            if (mSelY2 < -scrollRows) {
                mSelY2 = -scrollRows
            } else if (mSelY2 > console.mEmulator.mRows - 1) {
                mSelY2 = console.mEmulator.mRows - 1
            }
            if (mSelY1 > mSelY2) {
                mSelY2 = mSelY1
            }
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) {
                mSelX2 = mSelX1
            }
            if (!console.mEmulator.isAlternateBufferActive) {
                var topRow = console.topRow
                if (mSelY2 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (mSelY2 >= topRow + console.mEmulator.mRows) {
                    topRow++
                    if (0 < topRow) {
                        topRow = 0
                    }
                }
                console.topRow = topRow
            }
            mSelX2 = getValidCurX(screen, mSelY2, mSelX2)
        }
        console.invalidate()
    }

    fun decrementYTextSelectionCursors(decrement: Int) {
        mSelY1 -= decrement
        mSelY2 -= decrement
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
        sel[0] = mSelY1
        sel[1] = mSelY2
        sel[2] = mSelX1
        sel[3] = mSelX2
    }

    private val selectedText: String
        /**
         * Get the currently selected text.
         */
        get() = console.mEmulator.getSelectedText(
            mSelX1, mSelY1, mSelX2, mSelY2
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
