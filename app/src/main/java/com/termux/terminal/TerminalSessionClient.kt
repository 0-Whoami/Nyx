package com.termux.terminal


/**
 * The interface for communication between [TerminalSession] and its client. It is used to
 * send callbacks to the client when [TerminalSession] changes or for sending other
 * back data to the client like logs.
 */
interface TerminalSessionClient {
    fun onTextChanged(changedSession: TerminalSession)

    fun onSessionFinished(finishedSession: TerminalSession)

    fun onCopyTextToClipboard(text: String)

    fun onPasteTextFromClipboard()

}
