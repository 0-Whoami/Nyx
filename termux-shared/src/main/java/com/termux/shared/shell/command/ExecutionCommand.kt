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

}
