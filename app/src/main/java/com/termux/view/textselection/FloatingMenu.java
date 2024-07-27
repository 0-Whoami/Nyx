package com.termux.view.textselection;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.UiElements.paint;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

final class FloatingMenu extends View {
    public final PopupWindow popupWindow = new PopupWindow(this, 180, 60);

    FloatingMenu() {
        super(console.getContext());
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        canvas.drawColor(primary);
        paint.setColor(getContrastColor(primary));
        canvas.drawText("Copy", 45.0f, 38.0f, paint);
        canvas.drawText("Paste", 135.0f, 38.0f, paint);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            if (90 >= event.getX())
                console.onCopyTextToClipboard(console.mEmulator.getSelectedText());
            else console.onPasteTextFromClipboard();

            console.stopTextSelectionMode();
        }
        return true;
    }
}
