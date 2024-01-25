package com.termux.app.terminal

import com.termux.app.TermuxService
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * The {link TermuxTerminalSessionClientBase} implementation that may require a [Service] for its interface methods.
 */
class TermuxTerminalSessionServiceClient(// --Commented out by Inspection (07-10-2023 11:13 am):private static final String LOG_TAG = "TermuxTerminalSessionServiceClient";
    private val mService: TermuxService
) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    override fun onCopyTextToClipboard(text: String) {}
    override fun onPasteTextFromClipboard() {}
    override fun setTerminalShellPid(terminalSession: TerminalSession, pid: Int) {
        val termuxSession = mService.getTermuxSessionForTerminalSession(terminalSession)
        if (termuxSession != null) termuxSession.executionCommand.mPid = pid
    }

}
