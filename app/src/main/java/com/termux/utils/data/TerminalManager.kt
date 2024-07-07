package com.termux.utils.data

import com.termux.NyxActivity
import com.termux.terminal.TerminalSession
import com.termux.utils.data.ConfigManager.FILES_DIR_PATH
import com.termux.view.Console
import java.io.File

object TerminalManager {
    lateinit var console: Console

    /**
     * List of Sessions
     */
    val sessions: MutableList<TerminalSession> = mutableListOf<TerminalSession>()


    /**
     * Remove a TerminalSession.
     */
    private fun removeTerminalSession(sessionToRemove: TerminalSession): Int {
        sessionToRemove.finishIfRunning()
        sessions.remove(sessionToRemove)
        return sessions.size - 1
    }

    fun addNewSession(isFailSafe: Boolean) {
        val newTerminalSession = createTerminalSession(isFailSafe)
        sessions.add(newTerminalSession)
        console.attachSession(newTerminalSession)
    }

    private fun createTerminalSession(isFailSafe: Boolean): TerminalSession {
        val failsafeCheck = isFailSafe || !File("$FILES_DIR_PATH/usr").exists()
        val newTerminalSession = TerminalSession(failsafeCheck)
        return newTerminalSession
    }

    fun removeFinishedSession(finishedSession: TerminalSession) {
        // Return pressed with finished session - remove it.
        val index = removeTerminalSession(finishedSession)
        if (index == -1) {
            // There are no sessions to show, so finish the activity.
            (console.context as NyxActivity).destroy()
        } else {
            val terminalSession = sessions[index]
            console.attachSession(terminalSession)
        }
    }
}
