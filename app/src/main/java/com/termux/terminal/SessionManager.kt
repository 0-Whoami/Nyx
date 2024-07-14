package com.termux.terminal

import com.termux.NyxActivity
import com.termux.data.ConfigManager.FILES_DIR_PATH
import com.termux.data.console
import java.io.File

object SessionManager {
    /**
     * List of Sessions
     */
    val sessions: MutableList<TerminalSession> = mutableListOf()


    /**
     * Remove a TerminalSession.
     */
    private fun removeTerminalSession(sessionToRemove: TerminalSession): Int {
        sessionToRemove.finishIfRunning()
        sessions.remove(sessionToRemove)
        return sessions.size - 1
    }

    fun addNewSession(isFailSafe: Boolean) {
        sessions.add(createTerminalSession(isFailSafe))
        console.attachSession(sessions.size - 1)
    }

    private fun createTerminalSession(isFailSafe: Boolean): TerminalSession {
        val failsafeCheck = isFailSafe || !File("$FILES_DIR_PATH/usr").exists()
        val newTerminalSession = TerminalSession(failsafeCheck)
        return newTerminalSession
    }

    fun removeFinishedSession(finishedSession: TerminalSession) {
        // Return pressed with finished session - remove it.
        val index = removeTerminalSession(finishedSession)
        if (index == -1) (console.context as NyxActivity).destroy()
        else console.attachSession(index)

    }
}
