package com.termux.utils;


import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.paint;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public final class Button extends View {
    private final String text;
    private final int color;
    private boolean check;

    public Button(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final var text = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "text");
        this.text = (null == text) ? "" : text;
        color = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "backgroundTint", primary);
    }

    void setCheck(final boolean value) {
        check = value;
        invalidate();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (0 == MeasureSpec.getSize(heightMeasureSpec)) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, w);
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void toogle() {
        setCheck(!check);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        final int bg = check ? color : secondary;
        canvas.drawColor(bg);
        paint.setColor(getContrastColor(bg));
        canvas.drawText(text, getWidth() / 2.0f, getHeight() / 2.0f + paint.descent(), paint);
    }
}

