package com.termux.terminal;

import static com.termux.NyxActivityKt.console;
import static com.termux.data.ConfigManager.FILES_DIR_PATH;

import com.termux.NyxActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final public class SessionManager {
    /**
     * List of Sessions
     */
    public static final List<TerminalSession> sessions = new ArrayList<>();

    /**
     * Remove a TerminalSession.
     */
    private static int removeTerminalSession(TerminalSession sessionToRemove) {
        sessionToRemove.finishIfRunning();
        sessions.remove(sessionToRemove);
        return sessions.size() - 1;
    }

    public static void addNewSession(boolean isFailSafe) {
        sessions.add(createTerminalSession(isFailSafe));
        console.attachSession(sessions.size() - 1);
    }

    private static TerminalSession createTerminalSession(boolean isFailSafe) {
        boolean failsafeCheck = isFailSafe || !new File(FILES_DIR_PATH + "/usr").exists();
        return new TerminalSession(failsafeCheck);
    }

    public static void removeFinishedSession(TerminalSession finishedSession) { // Return pressed with finished session - remove it.
        int index = removeTerminalSession(finishedSession);
        if (index == -1) ((NyxActivity) console.getContext()).destroy();
        else console.attachSession(index);
    }
}
