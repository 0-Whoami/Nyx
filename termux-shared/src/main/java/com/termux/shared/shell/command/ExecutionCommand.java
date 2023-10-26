package com.termux.shared.shell.command;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.shell.command.result.ResultConfig;
import com.termux.shared.shell.command.result.ResultData;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class ExecutionCommand {

    /*
    The {@link ExecutionState#SUCCESS} and {@link ExecutionState#FAILED} is defined based on
    successful execution of command without any internal errors or exceptions being raised.
    The shell command {@link #exitCode} being non-zero **does not** mean that execution command failed.
    Only the {@link #errCode} being non-zero means that execution command failed from the Termux app
    perspective.
    */
    /**
     * The {@link Enum} that defines {@link ExecutionCommand} state.
     */
    public enum ExecutionState {

        PRE_EXECUTION("Pre-Execution", 0), EXECUTING("Executing", 1), EXECUTED("Executed", 2), SUCCESS("Success", 3), FAILED("Failed", 4);

        private final String name;

        private final int value;

        ExecutionState(final String name, final int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Runner {

        /**
         * Run command in {@link TerminalSession}.
         */
        TERMINAL_SESSION("terminal-session"),
        /**
         * Run command in {@link AppShell}.
         */
        APP_SHELL("app-shell");

        ///** Run command in {@link AdbShell}. */
        //ADB_SHELL("adb-shell"),
        ///** Run command in {@link RootShell}. */
        //ROOT_SHELL("root-shell");
        private final String name;

        Runner(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public boolean equalsRunner(String runner) {
            return runner != null && runner.equals(this.name);
        }

        /**
         * Get {@link Runner} for {@code name} if found, otherwise {@code null}.
         */
        @Nullable
        public static Runner runnerOf(String name) {
            for (Runner v : Runner.values()) {
                if (v.name.equals(name)) {
                    return v;
                }
            }
            return null;
        }

    }

    public enum ShellCreateMode {

        /**
         * Always create {@link TerminalSession}.
         */
        ALWAYS("always"),
        /**
         * Create shell only if no shell with {@link #shellName} found.
         */
        NO_SHELL_WITH_NAME("no-shell-with-name");

        private final String mode;

        ShellCreateMode(final String mode) {
            this.mode = mode;
        }

        public String getMode() {
            return mode;
        }

        public boolean equalsMode(String sessionCreateMode) {
            return sessionCreateMode != null && sessionCreateMode.equals(this.mode);
        }

    }

    /**
     * The optional unique id for the {@link ExecutionCommand}. This should equal -1 if execution
     * command is not going to be managed by a shell manager.
     */
    public Integer id;

    /**
     * The process id of command.
     */
    public int mPid = -1;

    /**
     * The current state of the {@link ExecutionCommand}.
     */
    private ExecutionState currentState = ExecutionState.PRE_EXECUTION;

    /**
     * The previous state of the {@link ExecutionCommand}.
     */
    private ExecutionState previousState = ExecutionState.PRE_EXECUTION;

    /**
     * The executable for the {@link ExecutionCommand}.
     */
    public String executable;

    /**
     * The executable Uri for the {@link ExecutionCommand}.
     */
    public Uri executableUri;

    /**
     * The executable arguments array for the {@link ExecutionCommand}.
     */
    public String[] arguments;

    /**
     * The stdin string for the {@link ExecutionCommand}.
     */
    public String stdin;

    /**
     * The current working directory for the {@link ExecutionCommand}.
     */
    public String workingDirectory;

    /**
     * The terminal transcript rows for the {@link ExecutionCommand}.
     */
    public Integer terminalTranscriptRows;

    /**
     * The {@link Runner} for the {@link ExecutionCommand}.
     */
    public String runner;

    /**
     * If the {@link ExecutionCommand} is meant to start a failsafe terminal session.
     */
    public boolean isFailsafe;

    /**
     * The {@link ExecutionCommand} custom log level for background {@link AppShell}
     * commands. By default, @link com.termux.shared.shell.StreamGobbler} only logs stdout and
     * stderr if {Logger} `CURRENT_LOG_LEVEL` is >= { Logger#LOG_LEVEL_VERBOSE} and
     * {@link AppShell} only logs stdin if `CURRENT_LOG_LEVEL` is >=
     * { Logger#LOG_LEVEL_DEBUG}.
     */
    public Integer backgroundCustomLogLevel;

    /**
     * The session action of {@link Runner#TERMINAL_SESSION} commands.
     */
    public String sessionAction;

    /**
     * The shell name of commands.
     */
    public String shellName;

    /**
     * The {@link ShellCreateMode} of commands.
     */
    public String shellCreateMode;

    /**
     * Whether to set {@link ExecutionCommand} shell environment.
     */
    public boolean setShellCommandShellEnvironment;

    /**
     * The command label for the {@link ExecutionCommand}.
     */
    public String commandLabel;

    /**
     * The markdown text for the command description for the {@link ExecutionCommand}.
     */
    public String commandDescription;

    /**
     * The markdown text for the help of command for the {@link ExecutionCommand}. This can be used
     * to provide useful info to the user if an internal error is raised.
     */
    public String commandHelp;

    /**
     * Defines the markdown text for the help of the Termux plugin API that was used to start the
     * {@link ExecutionCommand}. This can be used to provide useful info to the user if an internal
     * error is raised.
     */
    public String pluginAPIHelp;

    /**
     * Defines the {@link Intent} received which started the command.
     */
    public Intent commandIntent;

    /**
     * Defines if {@link ExecutionCommand} was started because of an external plugin request
     * like with an intent or from within Termux app itself.
     */
    public boolean isPluginExecutionCommand;

    /**
     * Defines the {@link ResultConfig} for the {@link ExecutionCommand} containing information
     * on how to handle the result.
     */
    public final ResultConfig resultConfig = new ResultConfig();

    /**
     * Defines the {@link ResultData} for the {@link ExecutionCommand} containing information
     * of the result.
     */
    public final ResultData resultData = new ResultData();

    /**
     * Defines if processing results already called for this {@link ExecutionCommand}.
     */
    public boolean processingResultsAlreadyCalled;

    public ExecutionCommand() {
    }

    public ExecutionCommand(Integer id) {
        this.id = id;
    }

    public ExecutionCommand(Integer id, String executable, String[] arguments, String stdin, String workingDirectory, String runner, boolean isFailsafe) {
        this.id = id;
        this.executable = executable;
        this.arguments = arguments;
        this.stdin = stdin;
        this.workingDirectory = workingDirectory;
        this.runner = runner;
        this.isFailsafe = isFailsafe;
    }

    public boolean isPluginExecutionCommandWithPendingResult() {
        return isPluginExecutionCommand && resultConfig.isCommandWithPendingResult();
    }

    public synchronized boolean setState(ExecutionState newState) {
        // The state transition cannot go back or change if already at {@link ExecutionState#SUCCESS}
        if (newState.getValue() < currentState.getValue() || currentState == ExecutionState.SUCCESS) {
            return false;
        }
        // The {@link ExecutionState#FAILED} can be set again, like to add more errors, but we don't update
        // {@link #previousState} with the {@link #currentState} value if its at {@link ExecutionState#FAILED} to
        // preserve the last valid state
        if (currentState != ExecutionState.FAILED)
            previousState = currentState;
        currentState = newState;
        return true;
    }

    public synchronized boolean hasExecuted() {
        return currentState.getValue() >= ExecutionState.EXECUTED.getValue();
    }

    public synchronized boolean isExecuting() {
        return currentState == ExecutionState.EXECUTING;
    }


    public synchronized boolean setStateFailed() {
               return setState(ExecutionState.FAILED);
    }



    public synchronized boolean shouldNotProcessResults() {
        if (processingResultsAlreadyCalled) {
            return true;
        } else {
            processingResultsAlreadyCalled = true;
            return false;
        }
    }

    public synchronized boolean isStateFailed() {
        if (currentState != ExecutionState.FAILED)
            return false;
        return resultData.isStateFailed();
    }

    @NonNull
    @Override
    public String toString() {
        if (!hasExecuted())
            return getExecutionInputLogString(this, true, true);
        else {
            return getExecutionOutputLogString(this, true, true);
        }
    }

    /**
     * Get a log friendly {@link String} for {@link ExecutionCommand} execution input parameters.
     *
     * @param executionCommand The {@link ExecutionCommand} to convert.
     * @param ignoreNull Set to {@code true} if non-critical {@code null} values are to be ignored.
     * @param logStdin Set to {@code true} if {@link #stdin} should be logged.
     * @return Returns the log friendly {@link String}.
     */
    public static String getExecutionInputLogString(final ExecutionCommand executionCommand, boolean ignoreNull, boolean logStdin) {
        if (executionCommand == null)
            return "null";
        StringBuilder logString = new StringBuilder();
        logString.append(executionCommand.getCommandIdAndLabelLogString()).append(":");
        if (executionCommand.mPid != -1)
            logString.append("\n").append(executionCommand.getPidLogString());
        if (executionCommand.previousState != ExecutionState.PRE_EXECUTION)
            logString.append("\n").append(executionCommand.getPreviousStateLogString());
        logString.append("\n").append(executionCommand.getCurrentStateLogString());
        logString.append("\n").append(executionCommand.getExecutableLogString());
        logString.append("\n").append(executionCommand.getArgumentsLogString());
        logString.append("\n").append(executionCommand.getWorkingDirectoryLogString());
        logString.append("\n").append(executionCommand.getIsFailsafeLogString());
        if (Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
            if (logStdin && (!ignoreNull || !DataUtils.isNullOrEmpty(executionCommand.stdin)))
                logString.append("\n").append(executionCommand.getStdinLogString());
            if (!ignoreNull || executionCommand.backgroundCustomLogLevel != null)
                logString.append("\n").append(executionCommand.getBackgroundCustomLogLevelLogString());
        }
        logString.append("\n").append(executionCommand.getSetRunnerShellEnvironmentLogString());
        if (!ignoreNull || executionCommand.commandIntent != null)
            logString.append("\n").append(executionCommand.getCommandIntentLogString());
        logString.append("\n").append(executionCommand.getIsPluginExecutionCommandLogString());
        if (executionCommand.isPluginExecutionCommand)
            logString.append("\n").append(ResultConfig.getResultConfigLogString(executionCommand.resultConfig, ignoreNull));
        return logString.toString();
    }

    /**
     * Get a log friendly {@link String} for {@link ExecutionCommand} execution output parameters.
     *
     * @param executionCommand The {@link ExecutionCommand} to convert.
     * @param ignoreNull Set to {@code true} if non-critical {@code null} values are to be ignored.
     * @param logResultData Set to {@code true} if {@link #resultData} should be logged.
     * @return Returns the log friendly {@link String}.
     */
    public static String getExecutionOutputLogString(final ExecutionCommand executionCommand, boolean ignoreNull, boolean logResultData) {
        if (executionCommand == null)
            return "null";
        StringBuilder logString = new StringBuilder();
        logString.append(executionCommand.getCommandIdAndLabelLogString()).append(":");
        logString.append("\n").append(executionCommand.getPreviousStateLogString());
        logString.append("\n").append(executionCommand.getCurrentStateLogString());
        if (logResultData)
            logString.append("\n").append(ResultData.getResultDataLogString(executionCommand.resultData));
        return logString.toString();
    }

    public String getIdLogString() {
        if (id != null)
            return "(" + id + ") ";
        else
            return "";
    }

    public String getPidLogString() {
        return "Pid: `" + mPid + "`";
    }

    public String getCurrentStateLogString() {
        return "Current State: `" + currentState.getName() + "`";
    }

    public String getPreviousStateLogString() {
        return "Previous State: `" + previousState.getName() + "`";
    }

    public String getCommandLabelLogString() {
        if (commandLabel != null && !commandLabel.isEmpty())
            return commandLabel;
        else
            return "Execution Command";
    }

    public String getCommandIdAndLabelLogString() {
        return getIdLogString() + getCommandLabelLogString();
    }

    public String getExecutableLogString() {
        return "Executable: `" + executable + "`";
    }

    public String getArgumentsLogString() {
        return getArgumentsLogString("Arguments", arguments);
    }

    public String getWorkingDirectoryLogString() {
        return "Working Directory: `" + workingDirectory + "`";
    }


    public String getIsFailsafeLogString() {
        return "isFailsafe: `" + isFailsafe + "`";
    }

    public String getStdinLogString() {
        if (DataUtils.isNullOrEmpty(stdin))
            return "Stdin: -";
        else
            return null;
    }

    public String getBackgroundCustomLogLevelLogString() {
        return "Background Custom Log Level: `" + backgroundCustomLogLevel + "`";
    }



    public String getSetRunnerShellEnvironmentLogString() {
        return "Set Shell Command Shell Environment: `" + setShellCommandShellEnvironment + "`";
    }



    public String getCommandIntentLogString() {
        if (commandIntent == null)
            return "Command Intent: -";
        return null;
       }

    public String getIsPluginExecutionCommandLogString() {
        return "isPluginExecutionCommand: `" + isPluginExecutionCommand + "`";
    }

    /**
     * Get a log friendly {@link String} for {@link List<String>} argumentsArray.
     * If argumentsArray are null or of size 0, then `Arguments: -` is returned. Otherwise
     * following format is returned:
     * <p>
     * Arguments:
     * ```
     * Arg 1: `value`
     * Arg 2: 'value`
     * ```
     *
     * @param argumentsArray The {@link String[]} argumentsArray to convert.
     * @return Returns the log friendly {@link String}.
     */
    public static String getArgumentsLogString(String label, final String[] argumentsArray) {
        StringBuilder argumentsString = new StringBuilder(label + ":");
        if (argumentsArray != null && argumentsArray.length != 0) {
            argumentsString.append("\n```\n");

            argumentsString.append("```");
        } else {
            argumentsString.append(" -");
        }
        return argumentsString.toString();
    }

}
