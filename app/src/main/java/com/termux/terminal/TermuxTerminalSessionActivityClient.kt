package com.termux.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.termux.app.main

/**
 * The {link TermuxTerminalSessionClientBase} implementation that may require an [main] for its interface methods.
 */
class TermuxTerminalSessionActivityClient(private val mActivity: main) {
    /**
     * Should be called when mActivity.onStart() is called
     */
    fun onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in utils preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        setCurrentSession(mActivity.mService.TerminalSessions[0])

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        mActivity.console.onScreenUpdated()
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    fun onTextChanged(changedSession: TerminalSession) {
        if (mActivity.console.currentSession == changedSession) mActivity.console.onScreenUpdated()
    }

    fun onSessionFinished(finishedSession: TerminalSession) {
        val service = mActivity.mService
        if (service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mActivity.finishActivityIfNotFinishing()
            return
        }
        // Once we have a separate launcher icon for the failsafe session, it
        // should be safe to auto-close session on exit code '0' or '130'.
        if (finishedSession.exitStatus == 0 || finishedSession.exitStatus == 130) {
            removeFinishedSession(finishedSession)
        }
    }

    fun onCopyTextToClipboard(text: String) {
        val clipboard = mActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text)
        clipboard.setPrimaryClip(clip)
    }

    fun onPasteTextFromClipboard() {
        val text: String =
            (mActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(
                0
            ).text.toString()
        mActivity.console.mEmulator.paste(text)
    }


    /**
     * Try switching to session.
     */
    fun setCurrentSession(session: TerminalSession) {
        mActivity.console.attachSession(session)
    }

    fun addNewSession(isFailSafe: Boolean) {
        val service = mActivity.mService
        val newTerminalSession =
            service.createTerminalSession(isFailSafe)
        service.TerminalSessions.add(newTerminalSession)
        setCurrentSession(newTerminalSession)
    }

    fun removeFinishedSession(finishedSession: TerminalSession) {
        // Return pressed with finished session - remove it.
        val service = mActivity.mService
        var index = service.removeTerminalSession(finishedSession)
        val size = service.TerminalSessionsSize
        if (size == 0) {
            // There are no sessions to show, so finish the activity.
            mActivity.finishActivityIfNotFinishing()
        } else {
            if (index >= size) {
                index = size - 1
            }
            val terminalSession = service.TerminalSessions[index]
            setCurrentSession(terminalSession)
        }
    }

}
