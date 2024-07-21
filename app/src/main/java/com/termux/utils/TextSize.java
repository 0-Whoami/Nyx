package com.termux.utils;

import static com.termux.NyxActivity.console;

import android.content.Context;
import android.util.AttributeSet;

public class TextSize extends SessionView {
    final String[] l = {"+", "-"};

    public TextSize(Context context, AttributeSet attrs) {
        super(context, attrs);
        padding = 5;
    }
    
    @Override
    int size() {
        return 2;
    }

    @Override
    boolean enable(int i) {
        return true;
    }

    @Override
    String text(int i) {
        return l[i];
    }

    @Override
    void onClick(int i) {
        console.changeFontSize(i < 1);
    }

    @Override
    void onLongClick(int i) {
    }
}
