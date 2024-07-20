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

    public FloatingMenu() {
        super(console.getContext());
        paint.setTypeface(ConfigManager.typeface);
        paint.setTextSize(25f);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(primary);
        canvas.drawRoundRect(0f, 0f, 180f, 60f, 30f, 30f, paint);
        paint.setColor(getContrastColor(primary));
        canvas.drawText("Copy", 45f, 38f, paint);
        canvas.drawText("Paste", 135f, 38f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (event.getX() <= 90) {
                console.onCopyTextToClipboard(console.mEmulator.getSelectedText());
            } else {
                console.onPasteTextFromClipboard();
            }
            console.stopTextSelectionMode();
        }
        return true;
    }
}
