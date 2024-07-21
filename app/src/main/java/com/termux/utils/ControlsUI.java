package com.termux.utils;

import static com.termux.NyxActivity.console;
import static com.termux.terminal.SessionManager.removeFinishedSession;
import static com.termux.terminal.SessionManager.sessions;

import android.app.Activity;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.termux.R;
import com.termux.data.ConfigManager;
import com.termux.terminal.SessionManager;
import com.termux.terminal.TerminalSession;

public final class ControlsUI extends Activity {

    private ViewGroup consoleParent;
    private View sessionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control_ui);
        int[] child = {-1, -1};

        consoleParent = (ViewGroup) console.getParent();
        sessionView = findViewById(R.id.session_view);
        var childCount = consoleParent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (consoleParent.getChildAt(i) instanceof Extrakeys) child[0] = i;
            if (consoleParent.getChildAt(i) instanceof WindowManager) child[1] = i;
        }

        findViewById(R.id.scroll).requestFocus();

        setupButton(R.id.new_session, true, button -> addNewSession(false));
        setupButton(R.id.failsafe, false, button -> addNewSession(true));

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

        setupButton(R.id.win, -1 != child[1], button -> {
            if (-1 == child[1]) {
                consoleParent.addView(new WindowManager());
                child[1] = consoleParent.getChildCount() - 1;
            } else {
                consoleParent.removeViewAt(child[1]);
                child[1] = -1;
            }
            ((Button) button).toogle();
        });

        setupButton(R.id.close, true, button -> {
            for (TerminalSession session : sessions) {
                removeFinishedSession(session);
            }
            finish();
        });

        ((TextView) findViewById(R.id.clock)).setTypeface(ConfigManager.typeface);
        consoleParent.setRenderEffect(RenderEffect.createBlurEffect(5.0f, 5.0f, Shader.TileMode.CLAMP));
    }

    private void addNewSession(boolean isFailSafe) {
        SessionManager.addNewSession(isFailSafe);
        sessionView.requestLayout();
    }

    private void setupButton(int id, boolean enabled, View.OnClickListener onClick) {
        Button button = findViewById(id);
        button.setCheck(enabled);
        button.setOnClickListener(onClick);
    }

    @Override
    protected void onDestroy() {
        consoleParent.setRenderEffect(null);
        super.onDestroy();
    }
}

