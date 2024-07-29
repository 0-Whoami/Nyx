package com.termux.terminal;

import static com.termux.NyxActivity.console;
import static java.lang.System.exit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import nyx.constants.Constant;

public final class SessionManager {
    /**
     * List of Sessions
     */
    public static final List<TerminalSession> sessions = new ArrayList<>();

    /**
     * Remove a TerminalSession.
     */
    private static int removeTerminalSession(final TerminalSession sessionToRemove) {
        sessionToRemove.finishIfRunning();
        sessions.remove(sessionToRemove);
        return sessions.size() - 1;
    }

    public static void addNewSession(final boolean isFailSafe) {
        sessions.add(createTerminalSession(isFailSafe));
        console.attachSession(sessions.size() - 1);
    }

    private static TerminalSession createTerminalSession(final boolean isFailSafe) {
        final boolean failsafeCheck = isFailSafe || !new File(Constant.USR_DIR).exists();
        return new TerminalSession(failsafeCheck);
    }

    public static void removeFinishedSession(final TerminalSession finishedSession) { // Return pressed with finished session - remove it.
        final int index = removeTerminalSession(finishedSession);
        if (-1 == index) exit(0);
        else console.attachSession(index);
    }

    public static void removeAll() {
        for (final TerminalSession i : sessions) removeFinishedSession(i);
    }
}
