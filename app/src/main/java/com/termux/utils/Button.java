package com.termux.utils;


import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.drawRoundedBg;
import static com.termux.utils.UiElements.paint;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public final class Button extends View {
    private final String text;
    private final int color;
    private boolean check = true;

    public Button(Context context, AttributeSet attrs) {
        super(context, attrs);
        var text = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "text");
        this.text = (null == text) ? "" : text;
        color = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "backgroundTint", primary);
    }

    void setCheck(boolean value) {
        check = value;
        invalidate();
    }

    public void toogle() {
        setCheck(!check);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawRoundedBg(canvas, check ? color : secondary, 50);
        paint.setColor(getContrastColor(paint.getColor()));
        canvas.drawText(text, getWidth() / 2.0f, getHeight() / 2.0f + paint.descent(), paint);
    }
}

