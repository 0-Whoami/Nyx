package com.termux.shared.shell.command

import com.termux.shared.shell.command.ExecutionCommand.ExecutionState
import com.termux.shared.shell.command.ExecutionCommand.Runner


/**
 * The [ExecutionState.SUCCESS] and [ExecutionState.FAILED] is defined based on
 * successful execution of command without any internal errors or exceptions being raised.
 * The shell command  being non-zero **does not** mean that execution command failed.
 * Only the  being non-zero means that execution command failed from the Termux app
 * perspective.
 */
class ExecutionCommand(
    /**
     * The optional unique id for the [ExecutionCommand]. This should equal -1 if execution
     * command is not going to be managed by a shell manager.
     */
    @JvmField val id: Int?,
    /**
     * The executable for the [ExecutionCommand].
     */
    @JvmField var executable: String?,
    /**
     * The executable arguments array for the [ExecutionCommand].
     */
    @JvmField var arguments: Array<String>?,
    /**
     * The stdin string for the [ExecutionCommand].
     */
    val stdin: String?,
    /**
     * The current working directory for the [ExecutionCommand].
     */
    @JvmField var workingDirectory: String?,
    /**
     * The [Runner] for the [ExecutionCommand].
     */
    @JvmField val runner: String?,
    /**
     * If the [ExecutionCommand] is meant to start a failsafe terminal session.
     */
    @JvmField val isFailsafe: Boolean?
) {
    /**
     * The [Enum] that defines [ExecutionCommand] state.
     */
    sealed class ExecutionState(val name: String, val value: Int) {
        data object PRE_EXECUTION : ExecutionState("Pre-Execution", 0)
        data object EXECUTING : ExecutionState("Executing", 1)
        data object EXECUTED : ExecutionState("Executed", 2)
        data object SUCCESS : ExecutionState("Success", 3)
        data object FAILED : ExecutionState("Failed", 4)

    }

    sealed class Runner(val value: String) {
        /**
         * Run command in [TerminalSession].
         */
        data object TERMINAL_SESSION : Runner("terminal-session")

        /**
         * Run command in [AppShell].
         */
        data object APP_SHELL : Runner("app-shell")

        companion object {
            /**
             * Get [Runner] for `name` if found, otherwise `null`.
             */
            @JvmStatic
            fun runnerOf(value: String): Runner? {
                return when (value) {
                    TERMINAL_SESSION.value -> TERMINAL_SESSION
                    APP_SHELL.value -> APP_SHELL
                    else -> null
                }
            }
        }
    }

    /**
     * The process id of command.
     */
    @JvmField
    var mPid = -1

    /**
     * The current state of the [ExecutionCommand].
     */
    private var currentState: ExecutionState = ExecutionState.PRE_EXECUTION

    /**
     * The previous state of the [ExecutionCommand].
     */
    private var previousState: ExecutionState = ExecutionState.PRE_EXECUTION

    /**
     * The terminal transcript rows for the [ExecutionCommand].
     */
    @JvmField
    var terminalTranscriptRows: Int? = null

    /**
     * The shell name of commands.
     */
    @JvmField
    var shellName: String? = null

    /**
     * Whether to set [ExecutionCommand] shell environment.
     */
    @JvmField
    var setShellCommandShellEnvironment = false

    /**
     * The command label for the [ExecutionCommand].
     */
    @JvmField
    var commandLabel: String? = null

    /**
     * Defines if processing results already called for this [ExecutionCommand].
     */
    private var processingResultsAlreadyCalled = false

    @Synchronized
    fun setState(newState: ExecutionState): Boolean {
        // The state transition cannot go back or change if already at {@link ExecutionState#SUCCESS}
        if (newState.value < currentState.value || currentState == ExecutionState.SUCCESS) {
            return false
        }
        // The {@link ExecutionState#FAILED} can be set again, like to add more errors, but we don't update
        // {@link #previousState} with the {@link #currentState} value if its at {@link ExecutionState#FAILED} to
        // preserve the last valid state
        if (currentState != ExecutionState.FAILED) previousState = currentState
        currentState = newState
        return true
    }

    @Synchronized
    fun hasExecuted(): Boolean {
        return currentState.value >= ExecutionState.EXECUTED.value
    }

    @Synchronized
    fun setStateFailed() {
        setState(ExecutionState.FAILED)
    }

    @Synchronized
    fun shouldNotProcessResults(): Boolean {
        return if (processingResultsAlreadyCalled) {
            true
        } else {
            processingResultsAlreadyCalled = true
            false
        }
    }

    @get:Synchronized
    val isStateFailed: Boolean
        get() = currentState.value == ExecutionState.FAILED.value

    override fun toString(): String {
        return if (!hasExecuted()) getExecutionInputLogString(
            this,
            true,
            logStdin = true
        ) else {
            getExecutionOutputLogString(this)
        }
    }

    private val idLogString: String
        get() = if (id != null) "($id) " else ""
    val pidLogString: String
        get() = "Pid: `$mPid`"
    val currentStateLogString: String
        get() = "Current State: `" + currentState.name + "`"
    val previousStateLogString: String
        get() = "Previous State: `" + previousState.name + "`"
    private val commandLabelLogString: String
        get() = if (commandLabel != null && commandLabel!!.isNotEmpty()) commandLabel!! else "Execution Command"
    val commandIdAndLabelLogString: String
        get() = idLogString + commandLabelLogString
    val executableLogString: String
        get() = "Executable: `$executable`"
    val argumentsLogString: String
        get() = getArgumentsLogString("Arguments", arguments)
    val workingDirectoryLogString: String
        get() = "Working Directory: `$workingDirectory`"
    val isFailsafeLogString: String
        get() = "isFailsafe: `$isFailsafe`"
    val stdinLogString: String?
        get() = if (stdin.isNullOrEmpty()) "Stdin: -" else null
    val setRunnerShellEnvironmentLogString: String
        get() = "Set Shell Command Shell Environment: `$setShellCommandShellEnvironment`"

    companion object {
        /**
         * Get a log friendly [String] for [ExecutionCommand] execution input parameters.
         *
         * @param executionCommand The [ExecutionCommand] to convert.
         * @param ignoreNull Set to `true` if non-critical `null` values are to be ignored.
         * @param logStdin Set to `true` if [.stdin] should be logged.
         * @return Returns the log friendly [String].
         */
        fun getExecutionInputLogString(
            executionCommand: ExecutionCommand?,
            ignoreNull: Boolean,
            logStdin: Boolean
        ): String {
            if (executionCommand == null) return "null"
            val logString = StringBuilder()
            logString.append(executionCommand.commandIdAndLabelLogString).append(":")
            if (executionCommand.mPid != -1) logString.append("\n")
                .append(executionCommand.pidLogString)
            if (executionCommand.previousState != ExecutionState.PRE_EXECUTION) logString.append("\n")
                .append(
                    executionCommand.previousStateLogString
                )
            logString.append("\n").append(executionCommand.currentStateLogString)
            logString.append("\n").append(executionCommand.executableLogString)
            logString.append("\n").append(executionCommand.argumentsLogString)
            logString.append("\n").append(executionCommand.workingDirectoryLogString)
            logString.append("\n").append(executionCommand.isFailsafeLogString)
            if (Runner.APP_SHELL.value == executionCommand.runner) {
                if (logStdin && (!ignoreNull || !executionCommand.stdin.isNullOrEmpty())) logString.append(
                    "\n"
                ).append(
                    executionCommand.stdinLogString
                )
            }
            logString.append("\n").append(executionCommand.setRunnerShellEnvironmentLogString)
            return logString.toString()
        }

        /**
         * Get a log friendly [String] for [ExecutionCommand] execution output parameters.
         *
         * @param executionCommand The [ExecutionCommand] to convert.
         * @param ignoreNull Set to `true` if non-critical `null` values are to be ignored.
         * @param logResultData Set to `true` if [.resultData] should be logged.
         * @return Returns the log friendly [String].
         */
        fun getExecutionOutputLogString(
            executionCommand: ExecutionCommand?,
        ): String {
            if (executionCommand == null) return "null"
            val logString = StringBuilder()
            logString.append(executionCommand.commandIdAndLabelLogString).append(":")
            logString.append("\n").append(executionCommand.previousStateLogString)
            logString.append("\n").append(executionCommand.currentStateLogString)
            return logString.toString()
        }

        /**
         * Get a log friendly [String] for [<] argumentsArray.
         * If argumentsArray are null or of size 0, then `Arguments: -` is returned. Otherwise
         * following format is returned:
         *
         *
         * Arguments:
         * ```
         * Arg 1: `value`
         * Arg 2: 'value`
         * ```
         *
         * @param argumentsArray The [String[]] argumentsArray to convert.
         * @return Returns the log friendly [String].
         */
        fun getArgumentsLogString(label: String, argumentsArray: Array<String>?): String {
            val argumentsString = StringBuilder("$label:")
            if (!argumentsArray.isNullOrEmpty()) {
                argumentsString.append("\n```\n")
                argumentsString.append("```")
            } else {
                argumentsString.append(" -")
            }
            return argumentsString.toString()
        }
    }
}
