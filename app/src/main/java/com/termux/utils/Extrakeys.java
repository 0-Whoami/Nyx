package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.utils.Theme.getContrastColor;
import static com.termux.utils.Theme.primary;
import static com.termux.utils.Theme.secondary;
import static com.termux.utils.UiElements.inCircle;
import static com.termux.utils.UiElements.paint;
import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.termux.data.Properties;

import java.util.ArrayList;

import nyx.constants.Constant;

public final class Extrakeys extends View {
    private static final int buttonRadius = 30;
    private final float offsetText = paint.descent();
    private final ArrayList<Key> keys = new ArrayList<>();
    private float a;

    public Extrakeys() {
        super(console.getContext());
        setFocusable(false);
        keys.add(new Key("⇪", 0));
        keys.add(new Key("C", 0));
        keys.add(new Key("A", 0));
        keys.add(new Key("Fn", 0));
        new Properties(Constant.EXTRA_KEYS_CONFIG).forEach((it, value) -> keys.add(new Key(it, Integer.parseInt(value))));
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (0 == w || 0 == h) return;
        final var centerX = w / 2;
        final var numButtons = keys.size();
        final float angle = (float) asin(buttonRadius * 2.0f / centerX) + 0.07f;
        var centeringOffset = 3.14f / 2 + angle * (numButtons - 1) / 2;
        a = (centerX - (buttonRadius + 5));
        for (final Key i : keys) {
            i.x = (float) (centerX + a * cos(centeringOffset));
            i.y = (float) (centerX + a * sin(centeringOffset));
            centeringOffset -= angle;
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        int n = 0;
        for (final Key k : keys) {
            paint.setColor((4 > n && console.metaKeys[n]) ? primary : secondary);
            canvas.drawCircle(k.x, k.y, buttonRadius, paint);
            paint.setColor(getContrastColor(paint.getColor()));
            canvas.drawText(k.label, k.x, k.y + offsetText, paint);
            n++;
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final var x = event.getX();
        final var y = event.getY();
        if (MotionEvent.ACTION_DOWN == event.getAction() && !inCircle(getWidth() / 2.0f, getHeight() / 2.0f, (a - buttonRadius), x, y)) {
            int n = 0;
            for (final Key i : keys) {
                if (inCircle(i.x, i.y, buttonRadius, x, y)) {
                    if (4 > n) {
                        console.metaKeys[n] = !console.metaKeys[n];
                        invalidate();
                    } else console.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, i.code));
                    return true;
                }
                n++;
            }
        }
        return false;
    }

    private static class Key {
        final int code;
        final String label;
        float x;
        float y;

        Key(final String l, final int c) {
            label = l;
            code = c;
        }
    }

}