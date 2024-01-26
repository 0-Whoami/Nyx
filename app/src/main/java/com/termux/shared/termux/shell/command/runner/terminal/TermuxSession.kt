package com.termux.shared.termux.shell.command.runner.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.shared.shell.ShellUtils.getExecutableBasename
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils.convertEnvironmentToEnviron
import com.termux.shared.shell.command.environment.UnixShellEnvironment
import java.io.File

/**
 * A class that maintains info for foreground Termux sessions.
 * It also provides a way to link each [TerminalSession] with the [ExecutionCommand]
 * that started it.
 */
class TermuxSession private constructor(
    val terminalSession: TerminalSession,
    val executionCommand: ExecutionCommand,
    termuxSessionClient: TermuxSessionClient
) {
    private val mTermuxSessionClient: TermuxSessionClient?

    init {
        mTermuxSessionClient = termuxSessionClient
    }

    /**
     * Signal that this  has finished.  This should be called when
     * callback is received by the caller.
     *
     *
     * and  for the [.mExecutionCommand] of the `termuxTask`
     * and then calls [.processTermuxSessionResult] to process the result}.
     */
    fun finish() {
        // If process is still running, then ignore the call
        if (terminalSession.isRunning) return
        // If the execution command has already failed, like SIGKILL was sent, then don't continue
        if (executionCommand.isStateFailed) {
            return
        }
        if (!executionCommand.setState(ExecutionCommand.ExecutionState.EXECUTED)) return
        processTermuxSessionResult(this, null)
    }

    /**
     * Kill this  by sending a [OsConstants.SIGILL] to its [.mTerminalSession]
     * if its still executing.
     *
     *
     * will be called to process the failure.
     */
    fun killIfExecuting() {
        // If execution command has already finished executing, then no need to process results or send SIGKILL
        if (executionCommand.hasExecuted()) {
            return
        }
        // Send SIGKILL to process
        terminalSession.finishIfRunning()
    }

    interface TermuxSessionClient {
        /**
         * Callback function for when [TermuxSession] exits.
         *
         * @param termuxSession The [TermuxSession] that exited.
         */
        fun onTermuxSessionExited(termuxSession: TermuxSession)
    }

    companion object {
        /**
         * Start execution of an [ExecutionCommand] with [Runtime.exec].
         *
         *
         * The [ExecutionCommand.executable], must be set, [ExecutionCommand.commandLabel],
         * [ExecutionCommand.arguments] and [ExecutionCommand.workingDirectory] may optionally
         * be set.
         *
         *
         * If [ExecutionCommand.executable] is `null`, then a default shell is automatically
         * chosen.
         *
         * @param currentPackageContext           The [Context] for operations. This must be the context for
         * the current package and not the context of a `sharedUserId` package,
         * since environment setup may be dependent on current package.
         * @param executionCommand                The [ExecutionCommand] containing the information for execution command.
         * @param termuxTerminalSessionClientBase The {link TermuxTerminalSessionClientBase} interface implementation.
         * @param termuxSessionClient             The [TermuxSessionClient] interface implementation.
         * @param shellEnvironmentClient          The [UnixShellEnvironment] interface implementation.
         * @param additionalEnvironment           The additional shell environment variables to export. Existing
         * variables will be overridden.
         * available in the [TermuxSessionClient.onTermuxSessionExited]
         * callback will be set to the [TerminalSession] transcript. The session
         * transcript will contain both stdout and stderr combined, basically
         * anything sent to the the pseudo terminal /dev/pts, including PS1 prefixes.
         * Set this to `true` only if the session transcript is required,
         * since this requires extra processing to get it.
         * @return Returns the . This will be `null` if failed to start the execution command.
         */
        fun execute(
            currentPackageContext: Context?,
            executionCommand: ExecutionCommand,
            termuxTerminalSessionClientBase: TerminalSessionClient?,
            termuxSessionClient: TermuxSessionClient,
            shellEnvironmentClient: UnixShellEnvironment,
            additionalEnvironment: Map<String, String>?
        ): TermuxSession? {
            if (null != executionCommand.executable && executionCommand.executable!!.isEmpty()) executionCommand.executable =
                null
            if (null == executionCommand.workingDirectory || executionCommand.workingDirectory!!.isEmpty()) executionCommand.workingDirectory =
                shellEnvironmentClient.defaultWorkingDirectoryPath
            if (executionCommand.workingDirectory!!.isEmpty()) executionCommand.workingDirectory =
                "/"
            var defaultBinPath = shellEnvironmentClient.defaultBinPath
            if (defaultBinPath.isEmpty()) defaultBinPath = "/system/bin"
            var isLoginShell = false
            if (null == executionCommand.executable) {
                if (!executionCommand.isFailsafe!!) {
                    for (shellBinary in UnixShellEnvironment.LOGIN_SHELL_BINARIES) {
                        val shellFile = File(defaultBinPath, shellBinary)
                        if (shellFile.canExecute()) {
                            executionCommand.executable = shellFile.absolutePath
                            break
                        }
                    }
                }
                if (null == executionCommand.executable) {
                    // Fall back to system shell as last resort:
                    // Do not start a login shell since ~/.profile may cause startup failure if its invalid.
                    // /system/bin/sh is provided by mksh (not toybox) and does load .mkshrc but for android its set
                    // to /system/etc/mkshrc even though its default is ~/.mkshrc.
                    // So /system/etc/mkshrc must still be valid for failsafe session to start properly.
                    // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/src/main.c;l=663
                    // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/src/main.c;l=41
                    // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:external/mksh/Android.bp;l=114
                    executionCommand.executable = "/system/bin/sh"
                } else {
                    isLoginShell = true
                }
            }
            // Setup command args
            val commandArgs = shellEnvironmentClient.setupShellCommandArguments(
                executionCommand.executable!!, executionCommand.arguments
            )
            executionCommand.executable = commandArgs!![0]
            val processName =
                (if (isLoginShell) "-" else "") + getExecutableBasename(executionCommand.executable)
            val arguments = commandArgs.copyOf()
            arguments[0] = processName
            if (1 < commandArgs.size) System.arraycopy(
                commandArgs,
                1,
                arguments,
                1,
                commandArgs.size - 1
            )
            executionCommand.arguments = arguments
            if (null == executionCommand.commandLabel) executionCommand.commandLabel = processName
            // Setup command environment
            val environment: MutableMap<String, String> =
                shellEnvironmentClient.setupShellCommandEnvironment(
                    currentPackageContext!!, executionCommand
                )
            if (null != additionalEnvironment) environment.putAll(additionalEnvironment)
            val environmentList = convertEnvironmentToEnviron(environment)
            environmentList.sort()
            val environmentArray = environmentList.toTypedArray<String>()
            if (!executionCommand.setState(ExecutionCommand.ExecutionState.EXECUTING)) {
                executionCommand.setStateFailed()
                processTermuxSessionResult(null, executionCommand)
                return null
            }
            val terminalSession = TerminalSession(
                executionCommand.executable,
                executionCommand.workingDirectory,
                executionCommand.arguments,
                environmentArray,
                executionCommand.terminalTranscriptRows,
                termuxTerminalSessionClientBase
            )
            if (null != executionCommand.shellName) {
                terminalSession.mSessionName = executionCommand.shellName
            }
            return TermuxSession(terminalSession, executionCommand, termuxSessionClient)
        }

        /**
         * Process the results of  or [ExecutionCommand].
         *
         *
         * Only one of `termuxSession` and `executionCommand` must be set.
         *
         *
         * If the `termuxSession` and its [.mTermuxSessionClient] are not `null`,
         * then the [TermuxSessionClient.onTermuxSessionExited]
         * callback will be called.
         *
         * @param termuxSession    The , which should be set if
         *
         *
         * successfully started the process.
         * @param executionCommand The [ExecutionCommand], which should be set if
         *
         *
         * failed to start the process.
         */
        private fun processTermuxSessionResult(
            termuxSession: TermuxSession?,
            executionCommand: ExecutionCommand?
        ) {
            var executionCommand = executionCommand
            if (null != termuxSession) executionCommand = termuxSession.executionCommand
            if (null == executionCommand) return
            if (executionCommand.shouldNotProcessResults()) {
                return
            }
            if (termuxSession?.mTermuxSessionClient != null) {
                termuxSession.mTermuxSessionClient.onTermuxSessionExited(termuxSession)
            } else {
                // If a callback is not set and execution command didn't fail, then we set success state now
                // Otherwise, the callback host can set it himself when its done with the termuxSession
                if (!executionCommand.isStateFailed) executionCommand.setState(ExecutionCommand.ExecutionState.SUCCESS)
            }
        }
    }
}
