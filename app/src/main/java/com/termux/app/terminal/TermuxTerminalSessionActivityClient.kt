package com.termux.app.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import com.termux.app.TermuxActivity
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * The {link TermuxTerminalSessionClientBase} implementation that may require an [TermuxActivity] for its interface methods.
 */
class TermuxTerminalSessionActivityClient(private val mActivity: TermuxActivity) :
    TerminalSessionClient {
    /**
     * Should be called when mActivity.onStart() is called
     */
    fun onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        mActivity.termuxService
        setCurrentSession(mActivity.termuxService.lastTermuxSession!!.terminalSession)

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        mActivity.terminalView.onScreenUpdated()
        // Set background image or color. The display orientation may have changed
        // while being away, force a background update.
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    override fun onTextChanged(changedSession: TerminalSession) {
        if (mActivity.currentSession == changedSession) mActivity.terminalView.onScreenUpdated()
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val service = mActivity.termuxService
        if (service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mActivity.finishActivityIfNotFinishing()
            return
        }
        val index = service.getIndexOfSession(finishedSession)
        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting fo the user to press enter.
        // The plugin can handle/show errors itself.
        if (finishedSession != mActivity.currentSession) {
            // Show toast for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0) mActivity.showToast(toToastTitle(finishedSession) + " - exited", true)
        }
        // Once we have a separate launcher icon for the failsafe session, it
        // should be safe to auto-close session on exit code '0' or '130'.
        if (finishedSession.exitStatus == 0 || finishedSession.exitStatus == 130) {
            removeFinishedSession(finishedSession)
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = mActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = mActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip!!.getItemAt(0)
        mActivity.terminalView.mEmulator.paste(item.text.toString())
    }

    override fun setTerminalShellPid(terminalSession: TerminalSession, pid: Int) {
        val service = mActivity.termuxService
        val termuxSession = service.getTermuxSessionForTerminalSession(terminalSession)
        if (termuxSession != null) termuxSession.executionCommand.mPid = pid
    }

    override fun getTerminalCursorStyle(): Int {
        return 0
    }

    /**
     * Try switching to session.
     */
    fun setCurrentSession(session: TerminalSession?) {
        if (session == null) return
        if (mActivity.terminalView.attachSession(session)) {
            // notify about switched session if not already displaying the session
            notifyOfSessionChange()
        }
        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.

        // Background color may have changed. If the background is image and already set,
        // no need for update.
    }

    private fun notifyOfSessionChange() {
        val session = mActivity.currentSession
        mActivity.showToast(toToastTitle(session), false)
    }

    fun addNewSession(isFailSafe: Boolean, sessionName: String?) {
        val service = mActivity.termuxService
        val currentSession = mActivity.currentSession
        val workingDirectory: String = if (currentSession == null) {
            TermuxConstants.TERMUX_HOME_DIR_PATH
        } else {
            currentSession.getCwd()
        }
        val newTermuxSession =
            service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName)
                ?: return
        val newTerminalSession = newTermuxSession.terminalSession
        setCurrentSession(newTerminalSession)
    }

    fun removeFinishedSession(finishedSession: TerminalSession?) {
        // Return pressed with finished session - remove it.
        val service = mActivity.termuxService
        var index = service.removeTermuxSession(finishedSession)
        val size = service.termuxSessionsSize
        if (size == 0) {
            // There are no sessions to show, so finish the activity.
            mActivity.finishActivityIfNotFinishing()
        } else {
            if (index >= size) {
                index = size - 1
            }
            val termuxSession = service.getTermuxSession(index)
            if (termuxSession != null) setCurrentSession(termuxSession.terminalSession)
        }
    }

    private fun toToastTitle(session: TerminalSession?): String? {
        val service = mActivity.termuxService
        val indexOfSession = service.getIndexOfSession(session)
        if (indexOfSession < 0) return null
        val toastTitle = StringBuilder("[" + (indexOfSession + 1) + "]")
        if (!TextUtils.isEmpty(session!!.mSessionName)) {
            toastTitle.append(" ").append(session.mSessionName)
        }
        val title = session.title
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(if (session.mSessionName == null) " " else "\n")
            toastTitle.append(title)
        }
        return toastTitle.toString()
    }
}
