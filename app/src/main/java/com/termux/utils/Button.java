package com.termux.utils;


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
        this.color = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "backgroundTint", Theme.primary);
    }

    void setCheck(final boolean value) {
        this.check = value;
        this.invalidate();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (0 == MeasureSpec.getSize(heightMeasureSpec)) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            this.setMeasuredDimension(w, w);
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void toogle() {
        this.setCheck(!this.check);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        final int bg = this.check ? this.color : Theme.secondary;
        canvas.drawColor(bg);
        UiElements.paint.setColor(Theme.getContrastColor(bg));
        canvas.drawText(this.text, this.getWidth() / 2.0f, this.getHeight() / 2.0f + UiElements.paint.descent(), UiElements.paint);
    }
}

