package com.termux.utils;


import static com.termux.NyxActivity.console;

import android.content.Context;
import android.util.AttributeSet;

public final class Rotary extends SessionView {
    private static final String[] list = {"⇅", "◀▶", "▲▼"};

    public Rotary(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    int size() {
        return list.length;
    }

    @Override
    protected void onClick(final int i) {
        console.RotaryMode = i;
    }


    @Override
    protected String text(final int i) {
        return list[i];
    }

    @Override
    protected boolean enable(final int i) {
        return console.RotaryMode == i;
    }
}
