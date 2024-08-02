package com.termux.utils;

import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.termux.NyxActivity;
import com.termux.data.Properties;

import java.util.ArrayList;

import nyx.constants.Constant;

public final class Extrakeys extends View {
    private static final int buttonRadius = 30;
    private final float offsetText = UiElements.paint.descent();
    private final ArrayList<Key> keys = new ArrayList<>(5);
    private float a;

    public Extrakeys() {
        super(NyxActivity.console.getContext());
        this.setFocusable(false);
        this.keys.add(new Key("⇪", 0));
        this.keys.add(new Key("C", 0));
        this.keys.add(new Key("A", 0));
        this.keys.add(new Key("Fn", 0));
        new Properties(Constant.EXTRA_KEYS_CONFIG).forEach((it, value) -> this.keys.add(new Key(value, Integer.parseInt(it))));
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (0 == w || 0 == h) return;
        final var centerX = w / 2;
        final var numButtons = this.keys.size();
        final float angle = (float) Math.asin(Extrakeys.buttonRadius * 2.0f / centerX) + 0.07f;
        var centeringOffset = 3.14f / 2 + angle * (numButtons - 1) / 2;
        this.a = (centerX - (Extrakeys.buttonRadius + 5));
        for (final Key i : this.keys) {
            i.x = (float) (centerX + this.a * Math.cos(centeringOffset));
            i.y = (float) (centerX + this.a * Math.sin(centeringOffset));
            centeringOffset -= angle;
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        int n = 0;
        for (final Key k : this.keys) {
            UiElements.paint.setColor((4 > n && NyxActivity.console.metaKeys[n]) ? Theme.primary : Theme.secondary);
            canvas.drawCircle(k.x, k.y, Extrakeys.buttonRadius, UiElements.paint);
            UiElements.paint.setColor(Theme.getContrastColor(UiElements.paint.getColor()));
            canvas.drawText(k.label, k.x, k.y + this.offsetText, UiElements.paint);
            n++;
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final var x = event.getX();
        final var y = event.getY();
        if (MotionEvent.ACTION_DOWN == event.getAction() && !UiElements.inCircle(this.getWidth() / 2.0f, this.getHeight() / 2.0f, (this.a - Extrakeys.buttonRadius), x, y)) {
            int n = 0;
            for (final Key i : this.keys) {
                if (UiElements.inCircle(i.x, i.y, Extrakeys.buttonRadius, x, y)) {
                    if (4 > n) {
                        NyxActivity.console.metaKeys[n] = !NyxActivity.console.metaKeys[n];
                        this.invalidate();
                    } else
                        NyxActivity.console.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, i.code));
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
            this.label = l;
            this.code = c;
        }
    }

}