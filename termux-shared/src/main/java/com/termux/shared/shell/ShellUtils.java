package com.termux.shared.shell;


import com.termux.shared.file.FileUtils;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShellUtils {

    /**
     * Setup shell command arguments for the execute.
     */

    public static String[] setupShellCommandArguments(String executable, String[] arguments) {
        List<String> result = new ArrayList<>();
        result.add(executable);
        if (arguments != null)
            Collections.addAll(result, arguments);
        return result.toArray(new String[0]);
    }

    /**
     * Get basename for executable.
     */

    public static String getExecutableBasename(String executable) {
        return FileUtils.getFileBasename(executable);
    }

    /**
     * Get transcript for {@link TerminalSession}.
     */
    public static String getTerminalSessionTranscriptText(TerminalSession terminalSession, boolean linesJoined, boolean trim) {
        if (terminalSession == null)
            return null;
        TerminalEmulator terminalEmulator = terminalSession.getEmulator();
        if (terminalEmulator == null)
            return null;
        TerminalBuffer terminalBuffer = terminalEmulator.getScreen();
        if (terminalBuffer == null)
            return null;
        String transcriptText;
        if (linesJoined)
            transcriptText = terminalBuffer.getTranscriptTextWithFullLinesJoined();
        else
            transcriptText = terminalBuffer.getTranscriptTextWithoutJoinedLines();
        if (trim)
            transcriptText = transcriptText.trim();
        return transcriptText;
    }
}
