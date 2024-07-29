package com.termux;

import static com.termux.terminal.SessionManager.addNewSession;
import static com.termux.terminal.SessionManager.sessions;
import static nyx.constants.Constant.EXTRA_NORMAL_BACKGROUND;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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
        setContentView(console);
        setWallpaper();
        insertInCircle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, () -> startActivity(new Intent(this, ControlsUI.class)));
        new Handler(getMainLooper()).postDelayed(() -> {
            if (sessions.isEmpty()) addNewSession(false);
            console.setBlurBonds();
        }, 200);
    }

    private void setWallpaper() {
        if (new File(EXTRA_NORMAL_BACKGROUND).exists())
            ((View) console.getParent()).setBackground(Drawable.createFromPath(EXTRA_NORMAL_BACKGROUND));
    }

    @Override
    protected void onResume() {
        super.onResume();
        console.requestFocus();
    }

    @TargetApi(Build.VERSION_CODES.R)
    private void insertInCircle() {
        if (!getResources().getConfiguration().isScreenRound()) return;
        final var l = (int) (getWindowManager().getCurrentWindowMetrics().getBounds().height() * 0.7071f);
        console.setLayoutParams(new FrameLayout.LayoutParams(l, l));
        final var c = l * 0.2071f;
        console.setX(c);
        console.setY(c);
    }

}
