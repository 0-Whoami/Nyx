package com.termux.shared.termux.terminal;


import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public class TermuxTerminalSessionClientBase implements TerminalSessionClient {

    public TermuxTerminalSessionClientBase() {
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
    }

    @Override
    public void onTitleChanged(TerminalSession updatedSession) {
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
    public void setTerminalShellPid(TerminalSession session, int pid) {
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null;
    }


}
