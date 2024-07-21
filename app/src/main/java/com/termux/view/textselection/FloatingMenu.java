package com.termux.view.textselection;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.termux.data.ConfigManager;

final class FloatingMenu extends View {
    public final PopupWindow popupWindow = new PopupWindow(this, 180, 60);
    private final Paint paint = new Paint();

    FloatingMenu() {
        super(console.getContext());
        paint.setTypeface(ConfigManager.typeface);
        paint.setTextSize(25.0f);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(primary);
        canvas.drawRoundRect(0.0f, 0.0f, 180.0f, 60.0f, 30.0f, 30.0f, paint);
        paint.setColor(getContrastColor(primary));
        canvas.drawText("Copy", 45.0f, 38.0f, paint);
        canvas.drawText("Paste", 135.0f, 38.0f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            if (90 >= event.getX())
                console.onCopyTextToClipboard(console.mEmulator.getSelectedText());
            else console.onPasteTextFromClipboard();

            console.stopTextSelectionMode();
        }
        return true;
    }
}
