package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.terminal.SessionManager.addNewSession;
import static com.termux.terminal.SessionManager.removeFinishedSession;
import static com.termux.terminal.SessionManager.sessions;
import static com.termux.utils.Theme.textOnPrimary;

import android.app.Activity;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;

import com.termux.R;
import com.termux.data.ConfigManager;
import com.termux.terminal.TerminalSession;

public final class ControlsUI extends Activity {

    private ViewGroup consoleParent;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control_ui);
        final int[] child = {-1, -1};

        consoleParent = (ViewGroup) console.getParent();
        View sessionView = findViewById(R.id.session_view);
        final var childCount = consoleParent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (consoleParent.getChildAt(i) instanceof Extrakeys) child[0] = i;
            if (consoleParent.getChildAt(i) instanceof FrameLayout) child[1] = i;
        }
        setupButton(R.id.new_session, true, v -> {
            addNewSession(false);
            sessionView.requestLayout();
        });
        findViewById(R.id.new_session).setOnLongClickListener(v -> {
            addNewSession(true);
            sessionView.requestLayout();
            return true;
        });
        setupButton(R.id.plus, true, b -> console.changeFontSize(true));
        setupButton(R.id.minus, true, b -> console.changeFontSize(false));
        setupButton(R.id.extrakeys, -1 != child[0], v -> {
            if (-1 == child[0]) {
                consoleParent.addView(new Extrakeys());
                child[0] = consoleParent.getChildCount() - 1;
            } else {
                consoleParent.removeViewAt(child[0]);
                child[0] = -1;
            }
            ((Button) v).toogle();
        });

        setupButton(R.id.win, -1 != child[1], v -> {
            if (-1 == child[1]) {
                consoleParent.addView(new WM(this));
                child[1] = consoleParent.getChildCount() - 1;
            } else {
                consoleParent.removeViewAt(child[1]);
                child[1] = -1;
            }
            ((Button) v).toogle();
        });

        setupButton(R.id.close, true, v -> {
            for (final TerminalSession session : sessions) {
                removeFinishedSession(session);
            }
            finish();
        });

        TextClock textClock = findViewById(R.id.clock);
        textClock.setTypeface(ConfigManager.typeface);
        textClock.setTextColor(textOnPrimary);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            consoleParent.setRenderEffect(RenderEffect.createBlurEffect(6, 5, Shader.TileMode.CLAMP));
    }

    private void setupButton(final int id, final boolean enabled, final View.OnClickListener onClick) {
        final Button button = findViewById(id);
        button.setCheck(enabled);
        button.setOnClickListener(onClick);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) consoleParent.setRenderEffect(null);
        super.onDestroy();
    }
}

