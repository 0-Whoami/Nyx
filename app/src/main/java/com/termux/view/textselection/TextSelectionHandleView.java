package com.termux.view.textselection;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.primary;
import static com.termux.view.textselection.TextSelectionCursorController.consoleCord;
import static com.termux.view.textselection.TextSelectionCursorController.selectors;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.termux.view.Console;


final class TextSelectionHandleView extends View {
    private final PopupWindow mHandle = new PopupWindow(this, 40, 40);
    private final int cur;
    private final int n;
    private final TextSelectionCursorController ts;
    private float dx;
    private float dy;


    TextSelectionHandleView(final int num, final TextSelectionCursorController tsc) {
        super(console.getContext());
        n = num;
        cur = 0 == num ? 2 : 0;
        ts = tsc;
    }

    public void hide() {
        mHandle.dismiss();
        selectors[n] = selectors[n + 1] = -1;
    }

    public void positionAtCursor(final int cx, final int cy) {
        selectors[n] = cx;
        selectors[n + 1] = cy;
        update();
    }

    public void update() {
        final var x = console.getPointX(selectors[n]) + consoleCord[0];
        final var y = console.getPointY(selectors[n + 1] + 1) + consoleCord[1];
        if (mHandle.isShowing()) mHandle.update(x, y, -1, -1);
        else mHandle.showAtLocation(console, 0, x, y);
    }

    @Override
    protected void onDraw(final Canvas c) {
        c.drawColor(primary);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dx = event.getX();
                dy = event.getY();
                ts.hideFloatingMenu();
            }

            case MotionEvent.ACTION_MOVE ->
                    updatePosition(event.getRawX() - dx - consoleCord[0], event.getRawY() - dy - consoleCord[1]);

            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ts.showFloatingMenu();
        }
        return true;
    }


    private void updatePosition(final float x, final float y) {
        final var screen = console.mEmulator.screen;
        final var scrollRows = screen.activeRows() - console.mEmulator.mRows;
        selectors[n] = Console.getCursorX(x);
        selectors[n + 1] = console.getCursorY(y);
        if (0 > selectors[n]) selectors[n] = 0;

        if (selectors[n + 1] < -scrollRows) selectors[n + 1] = -scrollRows;
        else if (selectors[n + 1] > console.mEmulator.mRows - 1)
            selectors[n + 1] = console.mEmulator.mRows - 1;

        if (selectors[1] > selectors[3]) selectors[n + 1] = selectors[cur + 1];

        if (selectors[1] == selectors[3] && selectors[0] > selectors[2])
            selectors[n] = selectors[cur];

        if (!console.mEmulator.isAlternateBufferActive()) {
            var topRow = console.topRow;
            if (selectors[n + 1] <= topRow) {
                topRow--;
                if (topRow < -scrollRows) topRow = -scrollRows;

            } else if (selectors[n + 1] >= topRow + console.mEmulator.mRows) {
                topRow++;
                if (0 < topRow) topRow = 0;
            }
            console.topRow = topRow;
        }
        console.invalidate();
        update();
    }
}
