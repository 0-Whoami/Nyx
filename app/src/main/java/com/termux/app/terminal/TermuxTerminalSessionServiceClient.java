package com.termux.app.terminal;

import android.app.Service;

import androidx.annotation.NonNull;

import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

/**
 * The {link TermuxTerminalSessionClientBase} implementation that may require a {@link Service} for its interface methods.
 */
public class TermuxTerminalSessionServiceClient implements TerminalSessionClient {

    // --Commented out by Inspection (07-10-2023 11:13 am):private static final String LOG_TAG = "TermuxTerminalSessionServiceClient";

    private final TermuxService mService;

    public TermuxTerminalSessionServiceClient(TermuxService service) {
        this.mService = service;
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {

    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {

    }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {

    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {

    }

    @Override
    public final void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxSession termuxSession = mService.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }

    @Override
    public final Integer getTerminalCursorStyle() {
        return null;
    }
}
