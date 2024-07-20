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
    private boolean check = true;

    public Button(Context context, AttributeSet attrs) {
        super(context, attrs);
        var text = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "text");
        this.text = (text == null) ? "" : text;
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
        drawRoundedBg(canvas, check ? primary : secondary, 50);
        paint.setColor(getContrastColor(paint.getColor()));
        canvas.drawText(text, getWidth() / 2f, getHeight() / 2f + paint.descent(), paint);
    }
}

