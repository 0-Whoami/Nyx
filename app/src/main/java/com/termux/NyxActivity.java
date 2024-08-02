package com.termux;

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
import com.termux.terminal.SessionManager;
import com.termux.utils.ControlsUI;
import com.termux.view.Console;

import java.io.File;

import nyx.constants.Constant;

/**
 * A terminal emulator activity.
 */


public final class NyxActivity extends Activity {
    public static Console console;

    private static void setWallpaper() {
        if (new File(Constant.EXTRA_NORMAL_BACKGROUND).exists())
            ((View) NyxActivity.console.getParent()).setBackground(Drawable.createFromPath(Constant.EXTRA_NORMAL_BACKGROUND));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigManager.loadConfigs();
        this.startService(new Intent(this, WakeUp.class).setAction("1"));
        NyxActivity.console = new Console(this);
        this.setContentView(NyxActivity.console);
        NyxActivity.setWallpaper();
        this.insertInCircle();
        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT)
            this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, () -> this.startActivity(new Intent(this, ControlsUI.class)));
        new Handler(this.getMainLooper()).postDelayed(() -> {
            if (SessionManager.sessions.isEmpty()) SessionManager.addNewSession(false);
            NyxActivity.console.setBlurBonds();
        }, 200);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NyxActivity.console.requestFocus();
    }

    @TargetApi(Build.VERSION_CODES.R)
    private void insertInCircle() {
        if (!this.getResources().getConfiguration().isScreenRound()) return;
        final var l = (int) (this.getWindowManager().getCurrentWindowMetrics().getBounds().height() * 0.7071f);
        NyxActivity.console.setLayoutParams(new FrameLayout.LayoutParams(l, l));
        final var c = l * 0.2071f;
        NyxActivity.console.setX(c);
        NyxActivity.console.setY(c);
    }

}
