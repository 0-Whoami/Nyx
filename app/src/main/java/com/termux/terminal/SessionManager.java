package com.termux.terminal;

import com.termux.NyxActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import nyx.constants.Constant;

public final class SessionManager {
    /**
     * List of Sessions
     */
    public static final List<TerminalSession> sessions = new ArrayList<>(1);

    /**
     * Remove a TerminalSession.
     */
    private static int removeTerminalSession(final TerminalSession sessionToRemove) {
        sessionToRemove.finishIfRunning();
        SessionManager.sessions.remove(sessionToRemove);
        return SessionManager.sessions.size() - 1;
    }

    public static void addNewSession(final boolean isFailSafe) {
        SessionManager.sessions.add(SessionManager.createTerminalSession(isFailSafe));
        NyxActivity.console.attachSession(SessionManager.sessions.size() - 1);
    }

    private static TerminalSession createTerminalSession(final boolean isFailSafe) {
        final boolean failsafeCheck = isFailSafe || !new File(Constant.USR_DIR).exists();
        return new TerminalSession(failsafeCheck);
    }

    public static void removeFinishedSession(final TerminalSession finishedSession) { // Return pressed with finished session - remove it.
        final int index = SessionManager.removeTerminalSession(finishedSession);
        if (-1 == index) System.exit(0);
        else NyxActivity.console.attachSession(index);
    }

    public static void removeAll() {
        for (final TerminalSession i : SessionManager.sessions)
            SessionManager.removeFinishedSession(i);
    }
}
