package com.termux.view.textselection;

import static com.termux.NyxActivity.console;

import android.view.MotionEvent;


public final class TextSelectionCursorController {
    public static final int[] selectors = {-1, -1, -1, -1};
    public static final int[] consoleCord = {0, 0};
    public static boolean isSelectingText;
    private final TextSelectionHandleView mStartHandle;
    private final TextSelectionHandleView mEndHandle;
    private final FloatingMenu floatingMenu;

    public TextSelectionCursorController() {
        mStartHandle = new TextSelectionHandleView(0, this);
        mEndHandle = new TextSelectionHandleView(2, this);
        floatingMenu = new FloatingMenu();
    }

    public static void decrementYTextSelectionCursors(int decrement) {
        TextSelectionCursorController.selectors[1] -= decrement;
        TextSelectionCursorController.selectors[3] -= decrement;
    }

    public void showTextSelectionCursor(MotionEvent event) {
        console.getLocationInWindow(TextSelectionCursorController.consoleCord);
        setInitialTextSelectionPosition(event);
        showFloatingMenu();
        TextSelectionCursorController.isSelectingText = true;
    }

    public void showFloatingMenu() {
        floatingMenu.popupWindow.showAtLocation(console, 0, console.getPointX(TextSelectionCursorController.selectors[0]) + TextSelectionCursorController.consoleCord[0], console.getPointY(TextSelectionCursorController.selectors[1]) + TextSelectionCursorController.consoleCord[1] - 60);
    }

    public boolean hideTextSelectionCursor() {
        if (!TextSelectionCursorController.isSelectingText) return false;
        mStartHandle.hide();
        mEndHandle.hide();
        hideFloatingMenu();
        TextSelectionCursorController.isSelectingText = false;
        return true;
    }

    public void hideFloatingMenu() {
        floatingMenu.popupWindow.dismiss();
    }

    private void setInitialTextSelectionPosition(MotionEvent event) {
        int[] p = console.getColumnAndRow(event, true);
        var mSelX1 = p[0];
        var mSelX2 = mSelX1 + 1;
        var screen = console.mEmulator.screen;
        if (!" ".equals(screen.getSelectedText(mSelX1, p[1], mSelX1, p[1]))) { // Selecting something other than whitespace. Expand to word.
            while (0 < mSelX1 && !screen.getSelectedText(mSelX1 - 1, p[1], mSelX1 - 1, p[1]).isEmpty())
                mSelX1--;
            while (mSelX2 < console.mEmulator.mColumns - 1 && !screen.getSelectedText(mSelX2 + 1, p[1], mSelX2 + 1, p[1]).isEmpty())
                mSelX2++;
        }
        mStartHandle.positionAtCursor(mSelX1, p[1]);
        mEndHandle.positionAtCursor(mSelX2, p[1]);
        console.invalidate();
    }

    public void updateSelHandles() {
        mStartHandle.update();
        mEndHandle.update();
    }


}
