package com.termux;

import static com.termux.data.ConfigManager.EXTRA_NORMAL_BACKGROUND;
import static com.termux.terminal.SessionManager.addNewSession;
import static com.termux.terminal.SessionManager.sessions;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.widget.FrameLayout;

import com.termux.data.ConfigManager;
import com.termux.utils.ControlsUI;
import com.termux.view.Console;

import java.io.File;

/**
 * A terminal emulator activity.
 */


public final class NyxActivity extends Activity {
    public static Console console;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigManager.loadConfigs();
        startService(new Intent(this, WakeUp.class).setAction("1"));
        console = new Console(this);
        setWallpaper();
        setContentView(console);
        insertInCircle();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, () -> startActivity(new Intent(this, ControlsUI.class)));
        new Handler(getMainLooper()).postDelayed(() -> {
            if (sessions.isEmpty()) addNewSession(false);
        }, 200);
    }

    private void setWallpaper() {
        if (new File(EXTRA_NORMAL_BACKGROUND).exists())
            getWindow().getDecorView().setBackground(Drawable.createFromPath(EXTRA_NORMAL_BACKGROUND));
    }

    @Override
    protected void onResume() {
        super.onResume();
        console.requestFocus();
    }

    private void insertInCircle() {
        if (!getResources().getConfiguration().isScreenRound()) return;
        final var l = (int) (getWindowManager().getCurrentWindowMetrics().getBounds().height() * 0.7071f);
        console.setLayoutParams(new FrameLayout.LayoutParams(l, l));
        final var c = l * 0.2071f;
        console.setX(c);
        console.setY(c);
    }

}
