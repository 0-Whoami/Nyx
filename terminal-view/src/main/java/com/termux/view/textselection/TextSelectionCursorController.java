package com.termux.view.textselection;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.WcWidth;
import com.termux.view.TerminalView;

public class TextSelectionCursorController implements ViewTreeObserver.OnTouchModeChangeListener {

    private static final int ACTION_COPY = 1;
    private static final int ACTION_PASTE = 2;
    private final TerminalView terminalView;
    private final TextSelectionHandleView mStartHandle, mEndHandle;
    private final int mHandleHeight;
    //    private String mStoredSelectedText;
    private boolean mIsSelectingText;
    private long mShowStartTime = System.currentTimeMillis();
    private int mSelX1 = -1, mSelX2 = -1, mSelY1 = -1, mSelY2 = -1;
    private ActionMode mActionMode;

    public TextSelectionCursorController(final TerminalView terminalView) {
        super();
        this.terminalView = terminalView;
        this.mStartHandle = new TextSelectionHandleView(terminalView, this);
        this.mEndHandle = new TextSelectionHandleView(terminalView, this);
        this.mHandleHeight = Math.max(this.mStartHandle.getHandleHeight(), this.mEndHandle.getHandleHeight());
    }

    private static int getValidCurX(final TerminalBuffer screen, final int cy, final int cx) {
        final String line = screen.getSelectedText(0, cy, cx, cy);
        if (!TextUtils.isEmpty(line)) {
            int col = 0;
            for (int i = 0, len = line.length(); i < len; i++) {
                final char ch1 = line.charAt(i);
                if (0 == ch1) {
                    break;
                }
                final int wc;
                if (Character.isHighSurrogate(ch1) && i + 1 < len) {
                    ++i;
                    final char ch2 = line.charAt(i);
                    wc = WcWidth.width(Character.toCodePoint(ch1, ch2));
                } else {
                    wc = WcWidth.width(ch1);
                }
                int cend = col + wc;
                if (cx > col && cx < cend) {
                    return cend;
                }
                if (cend == col) {
                    return col;
                }
                col = cend;
            }
        }
        return cx;
    }

    public final void show(final MotionEvent event) {
        this.setInitialTextSelectionPosition(event);
        this.mStartHandle.positionAtCursor(this.mSelX1, this.mSelY1, true);
        this.mEndHandle.positionAtCursor(this.mSelX2 + 1, this.mSelY2, true);
        this.setActionModeCallBacks();
        this.mShowStartTime = System.currentTimeMillis();
        this.mIsSelectingText = true;
    }

    public final boolean hide() {
        if (!this.mIsSelectingText)
            return false;
        // prevent hide calls right after a show call, like long pressing the down key
        // 300ms seems long enough that it wouldn't cause hide problems if action button
        // is quickly clicked after the show, otherwise decrease it
        if (300 > System.currentTimeMillis() - mShowStartTime) {
            return false;
        }
        this.mStartHandle.hide();
        this.mEndHandle.hide();
        if (null != mActionMode) {
            // This will hide the TextSelectionCursorController
            this.mActionMode.finish();
        }
        this.mSelX1 = this.mSelY1 = this.mSelX2 = this.mSelY2 = -1;
        this.mIsSelectingText = false;
        return true;
    }

    public final void render() {
        if (!this.mIsSelectingText)
            return;
        this.mStartHandle.positionAtCursor(this.mSelX1, this.mSelY1, false);
        this.mEndHandle.positionAtCursor(this.mSelX2 + 1, this.mSelY2, false);
        if (null != mActionMode) {
            this.mActionMode.invalidate();
        }
    }

    private void setInitialTextSelectionPosition(final MotionEvent event) {
        final int[] columnAndRow = this.terminalView.getColumnAndRow(event, true);
        this.mSelX1 = this.mSelX2 = columnAndRow[0];
        this.mSelY1 = this.mSelY2 = columnAndRow[1];
        final TerminalBuffer screen = this.terminalView.mEmulator.getScreen();
        if (!" ".equals(screen.getSelectedText(this.mSelX1, this.mSelY1, this.mSelX1, this.mSelY1))) {
            // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && !screen.getSelectedText(this.mSelX1 - 1, this.mSelY1, this.mSelX1 - 1, this.mSelY1).isEmpty()) {
                this.mSelX1--;
            }
            while (this.mSelX2 < this.terminalView.mEmulator.mColumns - 1 && !screen.getSelectedText(this.mSelX2 + 1, this.mSelY1, this.mSelX2 + 1, this.mSelY1).isEmpty()) {
                this.mSelX2++;
            }
        }
    }

    private void setActionModeCallBacks() {
        ActionMode.Callback callback = new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                final ClipboardManager clipboard = (ClipboardManager) TextSelectionCursorController.this.terminalView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                menu.add(Menu.NONE, TextSelectionCursorController.ACTION_COPY, Menu.NONE, "Copy");
                menu.add(Menu.NONE, TextSelectionCursorController.ACTION_PASTE, Menu.NONE, "Paste").setEnabled(null != clipboard && clipboard.hasPrimaryClip());
                return true;
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                if (!TextSelectionCursorController.this.isActive()) {
                    // Fix issue where the dialog is pressed while being dismissed.
                    return true;
                }
                switch (item.getItemId()) {
                    case TextSelectionCursorController.ACTION_COPY:
                        final String selectedText = TextSelectionCursorController.this.getSelectedText();
                        TextSelectionCursorController.this.terminalView.mTermSession.onCopyTextToClipboard(selectedText);
                        TextSelectionCursorController.this.terminalView.stopTextSelectionMode();
                        break;
                    case TextSelectionCursorController.ACTION_PASTE:
                        TextSelectionCursorController.this.terminalView.stopTextSelectionMode();
                        TextSelectionCursorController.this.terminalView.mTermSession.onPasteTextFromClipboard();
                        break;
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(final ActionMode mode) {
            }
        };
        this.mActionMode = this.terminalView.startActionMode(new ActionMode.Callback2() {

            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                return callback.onActionItemClicked(mode, item);
            }

            @Override
            public void onDestroyActionMode(final ActionMode mode) {
                // Ignore.
            }

            @Override
            public void onGetContentRect(final ActionMode mode, final View view, final Rect outRect) {

                int y1 = (TextSelectionCursorController.this.mSelY1 - TextSelectionCursorController.this.terminalView.getTopRow()) * TextSelectionCursorController.this.terminalView.mRenderer.getFontLineSpacing();
                final int y2 = (TextSelectionCursorController.this.mSelY2 - TextSelectionCursorController.this.terminalView.getTopRow()) * TextSelectionCursorController.this.terminalView.mRenderer.getFontLineSpacing();
                y1 += (y1 < TextSelectionCursorController.this.terminalView.mRenderer.getFontLineSpacing() << 1) ? (TextSelectionCursorController.this.mHandleHeight) : (-TextSelectionCursorController.this.mHandleHeight);
                outRect.set(0, y1, 0, y2);
            }
        }, ActionMode.TYPE_FLOATING);
    }

    public final void updatePosition(final TextSelectionHandleView handle, final int x, final int y) {
        final TerminalBuffer screen = this.terminalView.mEmulator.getScreen();
        int scrollRows = screen.getActiveRows() - this.terminalView.mEmulator.mRows;
        if (handle == this.mStartHandle) {
            this.mSelX1 = this.terminalView.getCursorX(x);
            this.mSelY1 = this.terminalView.getCursorY(y);
            if (0 > mSelX1) {
                this.mSelX1 = 0;
            }
            if (this.mSelY1 < -scrollRows) {
                this.mSelY1 = -scrollRows;
            } else if (this.mSelY1 > this.terminalView.mEmulator.mRows - 1) {
                this.mSelY1 = this.terminalView.mEmulator.mRows - 1;
            }
            if (this.mSelY1 > this.mSelY2) {
                this.mSelY1 = this.mSelY2;
            }
            if (this.mSelY1 == this.mSelY2 && this.mSelX1 > this.mSelX2) {
                this.mSelX1 = this.mSelX2;
            }
            if (!this.terminalView.mEmulator.isAlternateBufferActive()) {
                int topRow = this.terminalView.getTopRow();
                if (this.mSelY1 <= topRow) {
                    topRow--;
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows;
                    }
                } else if (this.mSelY1 >= topRow + this.terminalView.mEmulator.mRows) {
                    topRow++;
                    if (0 < topRow) {
                        topRow = 0;
                    }
                }
                this.terminalView.setTopRow(topRow);
            }
            this.mSelX1 = TextSelectionCursorController.getValidCurX(screen, this.mSelY1, this.mSelX1);
        } else {
            this.mSelX2 = this.terminalView.getCursorX(x);
            this.mSelY2 = this.terminalView.getCursorY(y);
            if (0 > mSelX2) {
                this.mSelX2 = 0;
            }
            if (this.mSelY2 < -scrollRows) {
                this.mSelY2 = -scrollRows;
            } else if (this.mSelY2 > this.terminalView.mEmulator.mRows - 1) {
                this.mSelY2 = this.terminalView.mEmulator.mRows - 1;
            }
            if (this.mSelY1 > this.mSelY2) {
                this.mSelY2 = this.mSelY1;
            }
            if (this.mSelY1 == this.mSelY2 && this.mSelX1 > this.mSelX2) {
                this.mSelX2 = this.mSelX1;
            }
            if (!this.terminalView.mEmulator.isAlternateBufferActive()) {
                int topRow = this.terminalView.getTopRow();
                if (this.mSelY2 <= topRow) {
                    topRow--;
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows;
                    }
                } else if (this.mSelY2 >= topRow + this.terminalView.mEmulator.mRows) {
                    topRow++;
                    if (0 < topRow) {
                        topRow = 0;
                    }
                }
                this.terminalView.setTopRow(topRow);
            }
            this.mSelX2 = TextSelectionCursorController.getValidCurX(screen, this.mSelY2, this.mSelX2);
        }
        this.terminalView.invalidate();
    }

    public final void decrementYTextSelectionCursors(final int decrement) {
        this.mSelY1 -= decrement;
        this.mSelY2 -= decrement;
    }

    public final void onTouchModeChanged(final boolean isInTouchMode) {
        if (!isInTouchMode) {
            this.terminalView.stopTextSelectionMode();
        }
    }

    public final boolean isActive() {
        return this.mIsSelectingText;
    }

    public final void getSelectors(final int[] sel) {
        if (null == sel || 4 != sel.length) {
            return;
        }
        sel[0] = this.mSelY1;
        sel[1] = this.mSelY2;
        sel[2] = this.mSelX1;
        sel[3] = this.mSelX2;
    }

    /**
     * Get the currently selected text.
     */
    private String getSelectedText() {
        return this.terminalView.mEmulator.getSelectedText(this.mSelX1, this.mSelY1, this.mSelX2, this.mSelY2);
    }

    /**
     * Unset the selected text stored before "MORE" button was pressed on the context menu.
     */
    public final ActionMode getActionMode() {
        return this.mActionMode;
    }

}
