package com.termux.shared.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public class TermuxTerminalSessionClientBase implements TerminalSessionClient {

    public TermuxTerminalSessionClientBase() {
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
    }


    @Override
    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null;
    }


}
