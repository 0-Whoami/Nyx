package com.termux.shared.termux.shell;

import android.widget.ArrayAdapter;

import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import java.util.ArrayList;
import java.util.List;

public class TermuxShellManager {

    private static TermuxShellManager shellManager;

    private static int SHELL_ID = 0;
    /**
     * The  number after app process was started/restarted.
     */
    private static int APP_SHELL_NUMBER_SINCE_APP_START;
    /**
     * The  number after app process was started/restarted.
     */
    private static int TERMINAL_SESSION_NUMBER_SINCE_APP_START;
    /**
     * The foreground TermuxSessions which this service manages.
     * Note that this list is observed by an activity, like TermuxActivity.mTermuxSessionListViewController,
     * so any changes must be made on the UI thread and followed by a call to
     * {@link ArrayAdapter#notifyDataSetChanged()}.
     */
    public final List<TermuxSession> mTermuxSessions = new ArrayList<>();

    /**
     * Initialize the {@link #shellManager}.
     */
    public static void init() {
        if (shellManager == null)
            shellManager = new TermuxShellManager();
    }

    /**
     * Get the {@link #shellManager}.
     *
     * @return Returns the {@link TermuxShellManager}.
     */
    public static TermuxShellManager getShellManager() {
        return shellManager;
    }

    public static void onAppExit() {
        // Ensure any shells started after boot have valid ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START and
        // ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START exported
        APP_SHELL_NUMBER_SINCE_APP_START = 0;
        TERMINAL_SESSION_NUMBER_SINCE_APP_START = 0;
    }

    public static synchronized int getNextShellId() {
        return SHELL_ID++;
    }

    public static synchronized int getAndIncrementAppShellNumberSinceAppStart() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        int curValue = APP_SHELL_NUMBER_SINCE_APP_START;
        if (curValue < 0)
            curValue = Integer.MAX_VALUE;
        APP_SHELL_NUMBER_SINCE_APP_START = curValue + 1;
        if (APP_SHELL_NUMBER_SINCE_APP_START < 0)
            APP_SHELL_NUMBER_SINCE_APP_START = Integer.MAX_VALUE;
        return curValue;
    }

    public static synchronized int getAndIncrementTerminalSessionNumberSinceAppStart() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        int curValue = TERMINAL_SESSION_NUMBER_SINCE_APP_START;
        if (curValue < 0)
            curValue = Integer.MAX_VALUE;
        TERMINAL_SESSION_NUMBER_SINCE_APP_START = curValue + 1;
        if (TERMINAL_SESSION_NUMBER_SINCE_APP_START < 0)
            TERMINAL_SESSION_NUMBER_SINCE_APP_START = Integer.MAX_VALUE;
        return curValue;
    }
}
