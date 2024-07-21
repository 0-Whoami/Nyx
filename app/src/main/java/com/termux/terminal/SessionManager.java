package com.termux.terminal;

import static com.termux.NyxActivity.console;
import static com.termux.data.ConfigManager.FILES_DIR_PATH;
import static java.lang.System.exit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public enum SessionManager {
    ;
    /**
     * List of Sessions
     */
    public static final List<TerminalSession> sessions = new ArrayList<>();

    /**
     * Remove a TerminalSession.
     */
    private static int removeTerminalSession(TerminalSession sessionToRemove) {
        sessionToRemove.finishIfRunning();
        SessionManager.sessions.remove(sessionToRemove);
        return SessionManager.sessions.size() - 1;
    }

    public static void addNewSession(boolean isFailSafe) {
        SessionManager.sessions.add(SessionManager.createTerminalSession(isFailSafe));
        console.attachSession(SessionManager.sessions.size() - 1);
    }

    private static TerminalSession createTerminalSession(boolean isFailSafe) {
        boolean failsafeCheck = isFailSafe || !new File(FILES_DIR_PATH + "/usr").exists();
        return new TerminalSession(failsafeCheck);
    }

    public static void removeFinishedSession(TerminalSession finishedSession) { // Return pressed with finished session - remove it.
        int index = SessionManager.removeTerminalSession(finishedSession);
        if (-1 == index) exit(0);
        else console.attachSession(index);
    }
}
