package com.termux.view.textselection;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.termux.NyxActivity;
import com.termux.utils.Theme;
import com.termux.view.Console;


final class TextSelectionHandleView extends View {
    private final PopupWindow mHandle = new PopupWindow(this, 40, 40);
    private final int cur;
    private final int n;
    private final TextSelectionCursorController ts;
    private float dx;
    private float dy;


    TextSelectionHandleView(final int num, final TextSelectionCursorController tsc) {
        super(NyxActivity.console.getContext());
        this.n = num;
        this.cur = 0 == num ? 2 : 0;
        this.ts = tsc;
    }

    public void hide() {
        this.mHandle.dismiss();
        TextSelectionCursorController.selectors[this.n] = TextSelectionCursorController.selectors[this.n + 1] = -1;
    }

    public void positionAtCursor(final int cx, final int cy) {
        TextSelectionCursorController.selectors[this.n] = cx;
        TextSelectionCursorController.selectors[this.n + 1] = cy;
        this.update();
    }

    public void update() {
        final var x = NyxActivity.console.getPointX(TextSelectionCursorController.selectors[this.n]) + TextSelectionCursorController.consoleCord[0];
        final var y = NyxActivity.console.getPointY(TextSelectionCursorController.selectors[this.n + 1] + 1) + TextSelectionCursorController.consoleCord[1];
        if (this.mHandle.isShowing()) this.mHandle.update(x, y, -1, -1);
        else this.mHandle.showAtLocation(NyxActivity.console, 0, x, y);
    }

    @Override
    protected void onDraw(final Canvas c) {
        c.drawColor(Theme.primary);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                this.dx = event.getX();
                this.dy = event.getY();
                this.ts.hideFloatingMenu();
            }

            case MotionEvent.ACTION_MOVE ->
                    this.updatePosition(event.getRawX() - this.dx - TextSelectionCursorController.consoleCord[0], event.getRawY() - this.dy - TextSelectionCursorController.consoleCord[1]);

            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> this.ts.showFloatingMenu();
        }
        return true;
    }


    private void updatePosition(final float x, final float y) {
        final var screen = NyxActivity.console.mEmulator.screen;
        final var scrollRows = screen.activeRows() - NyxActivity.console.mEmulator.mRows;
        TextSelectionCursorController.selectors[this.n] = Console.getCursorX(x);
        TextSelectionCursorController.selectors[this.n + 1] = NyxActivity.console.getCursorY(y);
        if (0 > TextSelectionCursorController.selectors[this.n])
            TextSelectionCursorController.selectors[this.n] = 0;

        if (TextSelectionCursorController.selectors[this.n + 1] < -scrollRows)
            TextSelectionCursorController.selectors[this.n + 1] = -scrollRows;
        else if (TextSelectionCursorController.selectors[this.n + 1] > NyxActivity.console.mEmulator.mRows - 1)
            TextSelectionCursorController.selectors[this.n + 1] = NyxActivity.console.mEmulator.mRows - 1;

        if (TextSelectionCursorController.selectors[1] > TextSelectionCursorController.selectors[3])
            TextSelectionCursorController.selectors[this.n + 1] = TextSelectionCursorController.selectors[this.cur + 1];

        if (TextSelectionCursorController.selectors[1] == TextSelectionCursorController.selectors[3] && TextSelectionCursorController.selectors[0] > TextSelectionCursorController.selectors[2])
            TextSelectionCursorController.selectors[this.n] = TextSelectionCursorController.selectors[this.cur];

        if (!NyxActivity.console.mEmulator.isAlternateBufferActive()) {
            var topRow = NyxActivity.console.topRow;
            if (TextSelectionCursorController.selectors[this.n + 1] <= topRow) {
                topRow--;
                if (topRow < -scrollRows) topRow = -scrollRows;

            } else if (TextSelectionCursorController.selectors[this.n + 1] >= topRow + NyxActivity.console.mEmulator.mRows) {
                topRow++;
                if (0 < topRow) topRow = 0;
            }
            NyxActivity.console.topRow = topRow;
        }
        NyxActivity.console.invalidate();
        this.update();
    }
}
