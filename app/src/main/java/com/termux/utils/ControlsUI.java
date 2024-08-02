package com.termux.utils;

import android.app.Activity;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;

import com.termux.NyxActivity;
import com.termux.R;
import com.termux.data.ConfigManager;
import com.termux.terminal.SessionManager;
import com.termux.terminal.TerminalSession;

public final class ControlsUI extends Activity {

    private ViewGroup consoleParent;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.control_ui);
        final int[] child = {-1, -1};

        this.consoleParent = (ViewGroup) NyxActivity.console.getParent();
        final View sessionView = this.findViewById(R.id.session_view);
        final var childCount = this.consoleParent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (this.consoleParent.getChildAt(i) instanceof Extrakeys) child[0] = i;
            if (this.consoleParent.getChildAt(i) instanceof FrameLayout) child[1] = i;
        }
        this.setupButton(R.id.new_session, true, v -> {
            SessionManager.addNewSession(false);
            sessionView.requestLayout();
        });
        this.findViewById(R.id.new_session).setOnLongClickListener(v -> {
            SessionManager.addNewSession(true);
            sessionView.requestLayout();
            return true;
        });
        this.setupButton(R.id.plus, true, b -> NyxActivity.console.changeFontSize(true));
        this.setupButton(R.id.minus, true, b -> NyxActivity.console.changeFontSize(false));
        this.setupButton(R.id.extrakeys, -1 != child[0], v -> {
            if (-1 == child[0]) {
                this.consoleParent.addView(new Extrakeys());
                child[0] = this.consoleParent.getChildCount() - 1;
            } else {
                this.consoleParent.removeViewAt(child[0]);
                child[0] = -1;
            }
            ((Button) v).toogle();
        });

        this.setupButton(R.id.win, -1 != child[1], v -> {
            if (-1 == child[1]) {
                this.consoleParent.addView(new WM(this));
                child[1] = this.consoleParent.getChildCount() - 1;
            } else {
                this.consoleParent.removeViewAt(child[1]);
                child[1] = -1;
            }
            ((Button) v).toogle();
        });

        this.setupButton(R.id.close, true, v -> {
            for (final TerminalSession session : SessionManager.sessions) {
                SessionManager.removeFinishedSession(session);
            }
            this.finish();
        });

        final TextClock textClock = this.findViewById(R.id.clock);
        textClock.setTypeface(ConfigManager.typeface);
        textClock.setTextColor(Theme.primary);
        if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT)
            this.consoleParent.setRenderEffect(RenderEffect.createBlurEffect(6, 5, Shader.TileMode.CLAMP));
    }

    private void setupButton(final int id, final boolean enabled, final View.OnClickListener onClick) {
        final Button button = this.findViewById(id);
        button.setCheck(enabled);
        button.setOnClickListener(onClick);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT)
            this.consoleParent.setRenderEffect(null);
        super.onDestroy();
    }
}

