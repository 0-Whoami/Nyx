package com.termux.shared.shell

import com.termux.shared.file.FileUtils.getFileBasename
import com.termux.terminal.TerminalSession
import java.util.Collections


object ShellUtils {
    /**
     * Setup shell command arguments for the execute.
     */
    fun setupShellCommandArguments(executable: String, arguments: Array<String>?): Array<String> {
        val result: MutableList<String> = ArrayList()
        result.add(executable)
        if (arguments != null) Collections.addAll(result, *arguments)
        return result.toTypedArray<String>()
    }

    /**
     * Get basename for executable.
     */
    @JvmStatic
    fun getExecutableBasename(executable: String?): String? {
        return getFileBasename(executable!!)
    }

    /**
     * Get transcript for [TerminalSession].
     */
    @JvmStatic
    fun getTerminalSessionTranscriptText(
        terminalSession: TerminalSession?,
        linesJoined: Boolean,
        trim: Boolean
    ): String? {
        if (terminalSession == null) return null
        val terminalEmulator = terminalSession.emulator ?: return null
        val terminalBuffer = terminalEmulator.screen ?: return null
        var transcriptText: String
        transcriptText = if (linesJoined) terminalBuffer.transcriptTextWithFullLinesJoined
        else terminalBuffer.transcriptTextWithoutJoinedLines
        if (trim) transcriptText = transcriptText.trim { it <= ' ' }
        return transcriptText
    }
}
