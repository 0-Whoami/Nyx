package com.termux.utils;


import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.drawRoundedBg;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public final class Rotary extends SessionView {
    private static final String[] list = {"⇅", "◀▶", "▲▼"};

    public Rotary(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected Object[] list() {
        return list;
    }

    @Override
    protected void onClick(int i) {
        console.RotaryMode = i;
    }

    @Override
    protected void onLongClick(int i) {
    }

    @Override
    protected String text(int i) {
        return list[i];
    }

    @Override
    protected boolean enable(int i) {
        return console.RotaryMode == i;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawRoundedBg(canvas, secondary, 50);
        super.onDraw(canvas);
    }

}
