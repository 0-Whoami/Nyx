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

public class Extrakeys extends View {
    private static final int buttonRadius = 25;
    private final float offsetText = paint.descent();
    private final ArrayList<Key> keys = new ArrayList<>();
    private float a = 0f;

    public Extrakeys() {
        super(console.getContext());
        setFocusable(false);
        keys.add(new Key("⇪", 0));
        keys.add(new Key("C", 0));
        keys.add(new Key("A", 0));
        keys.add(new Key("Fn", 0));
        new Properties("$CONFIG_PATH/keys").forEach((it, value) -> keys.add(new Key(it, Integer.parseInt(value))));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        final var centerX = w / 2;
        final var numButtons = keys.size();
        final float angle = (float) asin(buttonRadius * 2f / centerX) + 0.07f;
        var centeringOffset = 3.14f / 2 + angle * (numButtons - 1) / 2;
        a = (centerX - (buttonRadius + 5));
        for (Key i : keys) {
            i.x = (float) (centerX + a * cos(centeringOffset));
            i.y = (float) (centerX + a * sin(centeringOffset));
            centeringOffset -= angle;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = 0;
        for (Key k : keys) {
            paint.setColor((n < 4 && console.metaKeys[n]) ? primary : secondary);
            canvas.drawCircle(k.x, k.y, buttonRadius, paint);
            paint.setColor(getContrastColor(paint.getColor()));
            canvas.drawText(k.label, k.x, k.y + offsetText, paint);
            n++;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final var x = event.getX();
        final var y = event.getY();
        int n = 0;
        if (event.getAction() == MotionEvent.ACTION_DOWN && !inCircle(getWidth() / 2f, getHeight() / 2f, (a - buttonRadius), x, y)) {
            for (Key i : keys) {
                if (inCircle(i.x, i.y, buttonRadius, x, y)) {
                    if (n < 4) console.metaKeys[n] = !console.metaKeys[n];
                    else console.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, i.code));
                    invalidate();
                    return true;
                }
                n++;
            }
        }
        return false;
    }

    private static class Key {
        public final int code;
        public final String label;
        public float x, y;

        public Key(String label, int code) {
            this.label = label;
            this.code = code;
        }
    }

}