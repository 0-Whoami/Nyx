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
    private static final int ACTION_MORE = 3;
    private final TerminalView terminalView;
    private final TextSelectionHandleView mStartHandle, mEndHandle;
    private final int mHandleHeight;
    //    private String mStoredSelectedText;
    private boolean mIsSelectingText = false;
    private long mShowStartTime = System.currentTimeMillis();
    private int mSelX1 = -1, mSelX2 = -1, mSelY1 = -1, mSelY2 = -1;
    private ActionMode mActionMode;

    public TextSelectionCursorController(TerminalView terminalView) {
        this.terminalView = terminalView;
        mStartHandle = new TextSelectionHandleView(terminalView, this);
        mEndHandle = new TextSelectionHandleView(terminalView, this);
        mHandleHeight = Math.max(mStartHandle.getHandleHeight(), mEndHandle.getHandleHeight());
    }

    private static int getValidCurX(TerminalBuffer screen, int cy, int cx) {
        String line = screen.getSelectedText(0, cy, cx, cy);
        if (!TextUtils.isEmpty(line)) {
            int col = 0;
            for (int i = 0, len = line.length(); i < len; i++) {
                char ch1 = line.charAt(i);
                if (ch1 == 0) {
                    break;
                }
                int wc;
                if (Character.isHighSurrogate(ch1) && i + 1 < len) {
                    char ch2 = line.charAt(++i);
                    wc = WcWidth.width(Character.toCodePoint(ch1, ch2));
                } else {
                    wc = WcWidth.width(ch1);
                }
                final int cend = col + wc;
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

    public final void show(MotionEvent event) {
        setInitialTextSelectionPosition(event);
        mStartHandle.positionAtCursor(mSelX1, mSelY1, true);
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, true);
        setActionModeCallBacks();
        mShowStartTime = System.currentTimeMillis();
        mIsSelectingText = true;
    }

    public final boolean hide() {
        if (!mIsSelectingText)
            return false;
        // prevent hide calls right after a show call, like long pressing the down key
        // 300ms seems long enough that it wouldn't cause hide problems if action button
        // is quickly clicked after the show, otherwise decrease it
        if (System.currentTimeMillis() - mShowStartTime < 300) {
            return false;
        }
        mStartHandle.hide();
        mEndHandle.hide();
        if (mActionMode != null) {
            // This will hide the TextSelectionCursorController
            mActionMode.finish();
        }
        mSelX1 = mSelY1 = mSelX2 = mSelY2 = -1;
        mIsSelectingText = false;
        return true;
    }

    public final void render() {
        if (!mIsSelectingText)
            return;
        mStartHandle.positionAtCursor(mSelX1, mSelY1, false);
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, false);
        if (mActionMode != null) {
            mActionMode.invalidate();
        }
    }

    private void setInitialTextSelectionPosition(MotionEvent event) {
        int[] columnAndRow = terminalView.getColumnAndRow(event, true);
        mSelX1 = mSelX2 = columnAndRow[0];
        mSelY1 = mSelY2 = columnAndRow[1];
        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        if (!" ".equals(screen.getSelectedText(mSelX1, mSelY1, mSelX1, mSelY1))) {
            // Selecting something other than whitespace. Expand to word.
            while (mSelX1 > 0 && !screen.getSelectedText(mSelX1 - 1, mSelY1, mSelX1 - 1, mSelY1).isEmpty()) {
                mSelX1--;
            }
            while (mSelX2 < terminalView.mEmulator.mColumns - 1 && !screen.getSelectedText(mSelX2 + 1, mSelY1, mSelX2 + 1, mSelY1).isEmpty()) {
                mSelX2++;
            }
        }
    }

    private void setActionModeCallBacks() {
        final ActionMode.Callback callback = new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                ClipboardManager clipboard = (ClipboardManager) terminalView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, "Copy");
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, "Paste").setEnabled(clipboard != null && clipboard.hasPrimaryClip());
                menu.add(Menu.NONE, ACTION_MORE, Menu.NONE, "...");
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (!isActive()) {
                    // Fix issue where the dialog is pressed while being dismissed.
                    return true;
                }
                switch (item.getItemId()) {
                    case ACTION_COPY:
                        String selectedText = getSelectedText();
                        terminalView.mTermSession.onCopyTextToClipboard(selectedText);
                        terminalView.stopTextSelectionMode();
                        break;
                    case ACTION_PASTE:
                        terminalView.stopTextSelectionMode();
                        terminalView.mTermSession.onPasteTextFromClipboard();
                        break;
                    case ACTION_MORE:
                        // We first store the selected text in case TerminalViewClient needs the
                        // selected text before MORE button was pressed since we are going to
                        // stop selection mode
//                        mStoredSelectedText = getSelectedText();
                        // The text selection needs to be stopped before showing context menu,
                        // otherwise handles will show above popup
                        terminalView.stopTextSelectionMode();
                        terminalView.showContextMenu();
                        break;
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        };
        //noinspection NewApi
        mActionMode = terminalView.startActionMode(new ActionMode.Callback2() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return callback.onActionItemClicked(mode, item);
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Ignore.
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {

                int y1 = (mSelY1 - terminalView.getTopRow()) * terminalView.mRenderer.getFontLineSpacing();
                int y2 = (mSelY2 - terminalView.getTopRow()) * terminalView.mRenderer.getFontLineSpacing();
                y1 += (y1 < terminalView.mRenderer.getFontLineSpacing() << 1) ? (mHandleHeight) : (-mHandleHeight);
                outRect.set(0, y1, 0, y2);
            }
        }, ActionMode.TYPE_FLOATING);
    }

    public final void updatePosition(TextSelectionHandleView handle, int x, int y) {
        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        final int scrollRows = screen.getActiveRows() - terminalView.mEmulator.mRows;
        if (handle == mStartHandle) {
            mSelX1 = terminalView.getCursorX(x);
            mSelY1 = terminalView.getCursorY(y);
            if (mSelX1 < 0) {
                mSelX1 = 0;
            }
            if (mSelY1 < -scrollRows) {
                mSelY1 = -scrollRows;
            } else if (mSelY1 > terminalView.mEmulator.mRows - 1) {
                mSelY1 = terminalView.mEmulator.mRows - 1;
            }
            if (mSelY1 > mSelY2) {
                mSelY1 = mSelY2;
            }
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) {
                mSelX1 = mSelX2;
            }
            if (!terminalView.mEmulator.isAlternateBufferActive()) {
                int topRow = terminalView.getTopRow();
                if (mSelY1 <= topRow) {
                    topRow--;
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows;
                    }
                } else if (mSelY1 >= topRow + terminalView.mEmulator.mRows) {
                    topRow++;
                    if (topRow > 0) {
                        topRow = 0;
                    }
                }
                terminalView.setTopRow(topRow);
            }
            mSelX1 = getValidCurX(screen, mSelY1, mSelX1);
        } else {
            mSelX2 = terminalView.getCursorX(x);
            mSelY2 = terminalView.getCursorY(y);
            if (mSelX2 < 0) {
                mSelX2 = 0;
            }
            if (mSelY2 < -scrollRows) {
                mSelY2 = -scrollRows;
            } else if (mSelY2 > terminalView.mEmulator.mRows - 1) {
                mSelY2 = terminalView.mEmulator.mRows - 1;
            }
            if (mSelY1 > mSelY2) {
                mSelY2 = mSelY1;
            }
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) {
                mSelX2 = mSelX1;
            }
            if (!terminalView.mEmulator.isAlternateBufferActive()) {
                int topRow = terminalView.getTopRow();
                if (mSelY2 <= topRow) {
                    topRow--;
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows;
                    }
                } else if (mSelY2 >= topRow + terminalView.mEmulator.mRows) {
                    topRow++;
                    if (topRow > 0) {
                        topRow = 0;
                    }
                }
                terminalView.setTopRow(topRow);
            }
            mSelX2 = getValidCurX(screen, mSelY2, mSelX2);
        }
        terminalView.invalidate();
    }

    public final void decrementYTextSelectionCursors(int decrement) {
        mSelY1 -= decrement;
        mSelY2 -= decrement;
    }

    public final void onTouchModeChanged(boolean isInTouchMode) {
        if (!isInTouchMode) {
            terminalView.stopTextSelectionMode();
        }
    }

    public final boolean isActive() {
        return mIsSelectingText;
    }

    public final void getSelectors(int[] sel) {
        if (sel == null || sel.length != 4) {
            return;
        }
        sel[0] = mSelY1;
        sel[1] = mSelY2;
        sel[2] = mSelX1;
        sel[3] = mSelX2;
    }

    /**
     * Get the currently selected text.
     */
    private String getSelectedText() {
        return terminalView.mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2);
    }

    /**
     * Unset the selected text stored before "MORE" button was pressed on the context menu.
     */
    public final ActionMode getActionMode() {
        return mActionMode;
    }

}
