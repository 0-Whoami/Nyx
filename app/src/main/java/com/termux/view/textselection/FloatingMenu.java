package com.termux.view.textselection;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.termux.NyxActivity;
import com.termux.utils.Theme;
import com.termux.utils.UiElements;

final class FloatingMenu extends View {
    public final PopupWindow popupWindow = new PopupWindow(this, 180, 60);

    FloatingMenu() {
        super(NyxActivity.console.getContext());
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        canvas.drawColor(Theme.primary);
        UiElements.paint.setColor(Theme.getContrastColor(Theme.primary));
        canvas.drawText("Copy", 45.0f, 38.0f, UiElements.paint);
        canvas.drawText("Paste", 135.0f, 38.0f, UiElements.paint);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            if (90 >= event.getX())
                NyxActivity.console.onCopyTextToClipboard(NyxActivity.console.mEmulator.getSelectedText());
            else NyxActivity.console.onPasteTextFromClipboard();

            NyxActivity.console.stopTextSelectionMode();
        }
        return true;
    }
}
