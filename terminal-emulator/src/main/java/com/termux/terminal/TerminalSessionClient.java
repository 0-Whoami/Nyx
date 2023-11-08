package com.termux.terminal;


/**
 * The interface for communication between {@link TerminalSession} and its client. It is used to
 * send callbacks to the client when {@link TerminalSession} changes or for sending other
 * back data to the client like logs.
 */
public interface TerminalSessionClient {

    void onTextChanged( TerminalSession changedSession);

    void onTitleChanged( TerminalSession changedSession);

    void onSessionFinished( TerminalSession finishedSession);

    void onCopyTextToClipboard( TerminalSession session, String text);

    void onPasteTextFromClipboard(TerminalSession session);


    void setTerminalShellPid( TerminalSession session, int pid);

    Integer getTerminalCursorStyle();


}
