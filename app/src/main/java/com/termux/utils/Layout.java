package com.termux.utils;


import static com.termux.utils.Theme.secondary;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

public final class Layout extends LinearLayout {
    public Layout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackground(new GradientDrawable() {{
            setColor(secondary);
        }});
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        ((GradientDrawable) getBackground()).setCornerRadius(h / 4f);
    }
}