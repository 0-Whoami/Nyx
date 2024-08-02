package com.termux.view.textselection;

import android.view.MotionEvent;

import com.termux.NyxActivity;


public final class TextSelectionCursorController {
    public static final int[] selectors = {-1, -1, -1, -1};
    public static final int[] consoleCord = {0, 0};
    public static boolean isSelectingText;
    private final TextSelectionHandleView mStartHandle;
    private final TextSelectionHandleView mEndHandle;
    private final FloatingMenu floatingMenu;

    public TextSelectionCursorController() {
        this.mStartHandle = new TextSelectionHandleView(0, this);
        this.mEndHandle = new TextSelectionHandleView(2, this);
        this.floatingMenu = new FloatingMenu();
    }

    public static void decrementYTextSelectionCursors(final int decrement) {
        TextSelectionCursorController.selectors[1] -= decrement;
        TextSelectionCursorController.selectors[3] -= decrement;
    }

    public void showTextSelectionCursor(final MotionEvent event) {
        NyxActivity.console.getLocationInWindow(TextSelectionCursorController.consoleCord);
        this.setInitialTextSelectionPosition(event);
        this.showFloatingMenu();
        TextSelectionCursorController.isSelectingText = true;
    }

    public void showFloatingMenu() {
        this.floatingMenu.popupWindow.showAtLocation(NyxActivity.console, 0, NyxActivity.console.getPointX(TextSelectionCursorController.selectors[0]) + TextSelectionCursorController.consoleCord[0], NyxActivity.console.getPointY(TextSelectionCursorController.selectors[1]) + TextSelectionCursorController.consoleCord[1] - 60);
    }

    public boolean hideTextSelectionCursor() {
        if (!TextSelectionCursorController.isSelectingText) return false;
        this.mStartHandle.hide();
        this.mEndHandle.hide();
        this.hideFloatingMenu();
        TextSelectionCursorController.isSelectingText = false;
        return true;
    }

    public void hideFloatingMenu() {
        this.floatingMenu.popupWindow.dismiss();
    }

    private void setInitialTextSelectionPosition(final MotionEvent event) {
        final int[] p = NyxActivity.console.getColumnAndRow(event, true);
        var mSelX1 = p[0];
        var mSelX2 = mSelX1 + 1;
        final var screen = NyxActivity.console.mEmulator.screen;
        if (!" ".equals(screen.getSelectedText(mSelX1, p[1], mSelX1, p[1]))) { // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && !screen.getSelectedText(mSelX1 - 1, p[1], mSelX1 - 1, p[1]).isEmpty())
                mSelX1--;
            while (mSelX2 < NyxActivity.console.mEmulator.mColumns - 1 && !screen.getSelectedText(mSelX2 + 1, p[1], mSelX2 + 1, p[1]).isEmpty())
                mSelX2++;
        }
        this.mStartHandle.positionAtCursor(mSelX1, p[1]);
        this.mEndHandle.positionAtCursor(mSelX2, p[1]);
        NyxActivity.console.invalidate();
    }

    public void updateSelHandles() {
        this.mStartHandle.update();
        this.mEndHandle.update();
    }


}
