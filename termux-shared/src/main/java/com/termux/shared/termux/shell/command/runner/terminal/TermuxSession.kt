package com.termux.shared.termux.shell.command.runner.terminal;

import android.content.Context;
import android.system.OsConstants;

import com.termux.shared.shell.ShellUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.shell.command.environment.UnixShellEnvironment;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A class that maintains info for foreground Termux sessions.
 * It also provides a way to link each {@link TerminalSession} with the {@link ExecutionCommand}
 * that started it.
 */
public final class TermuxSession {

    private final TerminalSession mTerminalSession;

    private final ExecutionCommand mExecutionCommand;

    private final TermuxSessionClient mTermuxSessionClient;

    private TermuxSession(final TerminalSession terminalSession, final ExecutionCommand executionCommand, final TermuxSessionClient termuxSessionClient) {
        this.mTerminalSession = terminalSession;
        this.mExecutionCommand = executionCommand;
        this.mTermuxSessionClient = termuxSessionClient;
    }

    /**
     * Start execution of an {@link ExecutionCommand} with {@link Runtime#exec(String[], String[], File)}.
     * <p>
     * The {@link ExecutionCommand#executable}, must be set, {@link ExecutionCommand#commandLabel},
     * {@link ExecutionCommand#arguments} and {@link ExecutionCommand#workingDirectory} may optionally
     * be set.
     * <p>
     * If {@link ExecutionCommand#executable} is {@code null}, then a default shell is automatically
     * chosen.
     *
     * @param currentPackageContext           The {@link Context} for operations. This must be the context for
     *                                        the current package and not the context of a `sharedUserId` package,
     *                                        since environment setup may be dependent on current package.
     * @param executionCommand                The {@link ExecutionCommand} containing the information for execution command.
     * @param TermuxTerminalSessionClientBase The {link TermuxTerminalSessionClientBase} interface implementation.
     * @param termuxSessionClient             The {@link TermuxSessionClient} interface implementation.
     * @param shellEnvironmentClient          The {@link UnixShellEnvironment} interface implementation.
     * @param additionalEnvironment           The additional shell environment variables to export. Existing
     *                                        variables will be overridden.
     *                                        available in the {@link TermuxSessionClient#onTermuxSessionExited(TermuxSession)}
     *                                        callback will be set to the {@link TerminalSession} transcript. The session
     *                                        transcript will contain both stdout and stderr combined, basically
     *                                        anything sent to the the pseudo terminal /dev/pts, including PS1 prefixes.
     *                                        Set this to {@code true} only if the session transcript is required,
     *                                        since this requires extra processing to get it.
     * @return Returns the . This will be {@code null} if failed to start the execution command.
     */
    public static TermuxSession execute(final Context currentPackageContext, ExecutionCommand executionCommand, final TerminalSessionClient TermuxTerminalSessionClientBase, final TermuxSessionClient termuxSessionClient, final UnixShellEnvironment shellEnvironmentClient, Map<String, String> additionalEnvironment) {
        if (null != executionCommand.executable && executionCommand.executable.isEmpty())
            executionCommand.executable = null;
        if (null == executionCommand.workingDirectory || executionCommand.workingDirectory.isEmpty())
            executionCommand.workingDirectory = shellEnvironmentClient.getDefaultWorkingDirectoryPath();
        if (executionCommand.workingDirectory.isEmpty())
            executionCommand.workingDirectory = "/";
        String defaultBinPath = shellEnvironmentClient.getDefaultBinPath();
        if (defaultBinPath.isEmpty())
            defaultBinPath = "/system/bin";
        boolean isLoginShell = false;
        if (null == executionCommand.executable) {
            if (!executionCommand.isFailsafe) {
                for (String shellBinary : UnixShellEnvironment.LOGIN_SHELL_BINARIES) {
                    File shellFile = new File(defaultBinPath, shellBinary);
                    if (shellFile.canExecute()) {
                        executionCommand.executable = shellFile.getAbsolutePath();
                        break;
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
                executionCommand.executable = "/system/bin/sh";
            } else {
                isLoginShell = true;
            }
        }
        // Setup command args
        String[] commandArgs = shellEnvironmentClient.setupShellCommandArguments(executionCommand.executable, executionCommand.arguments);
        executionCommand.executable = commandArgs[0];
        String processName = (isLoginShell ? "-" : "") + ShellUtils.getExecutableBasename(executionCommand.executable);
        String[] arguments = new String[commandArgs.length];
        arguments[0] = processName;
        if (1 < commandArgs.length)
            System.arraycopy(commandArgs, 1, arguments, 1, commandArgs.length - 1);
        executionCommand.arguments = arguments;
        if (null == executionCommand.commandLabel)
            executionCommand.commandLabel = processName;
        // Setup command environment
        Map<String, String> environment = shellEnvironmentClient.setupShellCommandEnvironment(currentPackageContext, executionCommand);
        if (null != additionalEnvironment)
            environment.putAll(additionalEnvironment);
        List<String> environmentList = ShellEnvironmentUtils.INSTANCE.convertEnvironmentToEnviron(environment);
        Collections.sort(environmentList);
        String[] environmentArray = environmentList.toArray(new String[0]);
        if (!executionCommand.setState(ExecutionCommand.ExecutionState.EXECUTING.INSTANCE)) {
            executionCommand.setStateFailed();
            TermuxSession.processTermuxSessionResult(null, executionCommand);
            return null;
        }
        TerminalSession terminalSession = new TerminalSession(executionCommand.executable, executionCommand.workingDirectory, executionCommand.arguments, environmentArray, executionCommand.terminalTranscriptRows, TermuxTerminalSessionClientBase);
        if (null != executionCommand.shellName) {
            terminalSession.mSessionName = executionCommand.shellName;
        }
        return new TermuxSession(terminalSession, executionCommand, termuxSessionClient);
    }

    /**
     * Process the results of  or {@link ExecutionCommand}.
     * <p>
     * Only one of {@code termuxSession} and {@code executionCommand} must be set.
     * <p>
     * If the {@code termuxSession} and its {@link #mTermuxSessionClient} are not {@code null},
     * then the {@link TermuxSessionClient#onTermuxSessionExited(TermuxSession)}
     * callback will be called.
     *
     * @param termuxSession    The , which should be set if
     *                         <p>
     *                         successfully started the process.
     * @param executionCommand The {@link ExecutionCommand}, which should be set if
     *                         <p>
     *                         failed to start the process.
     */
    private static void processTermuxSessionResult(final TermuxSession termuxSession, ExecutionCommand executionCommand) {
        if (null != termuxSession)
            executionCommand = termuxSession.mExecutionCommand;
        if (null == executionCommand)
            return;
        if (executionCommand.shouldNotProcessResults()) {
            return;
        }
        if (null != termuxSession && null != termuxSession.mTermuxSessionClient) {
            termuxSession.mTermuxSessionClient.onTermuxSessionExited(termuxSession);
        } else {
            // If a callback is not set and execution command didn't fail, then we set success state now
            // Otherwise, the callback host can set it himself when its done with the termuxSession
            if (!executionCommand.isStateFailed())
                executionCommand.setState(ExecutionCommand.ExecutionState.SUCCESS.INSTANCE);
        }
    }

    /**
     * Signal that this  has finished.  This should be called when
     * callback is received by the caller.
     * <p>
     * and  for the {@link #mExecutionCommand} of the {@code termuxTask}
     * and then calls {@link #processTermuxSessionResult(TermuxSession, ExecutionCommand)} to process the result}.
     */
    public final void finish() {
        // If process is still running, then ignore the call
        if (mTerminalSession.isRunning())
            return;
        // If the execution command has already failed, like SIGKILL was sent, then don't continue
        if (mExecutionCommand.isStateFailed()) {
            return;
        }

        if (!mExecutionCommand.setState(ExecutionCommand.ExecutionState.EXECUTED.INSTANCE))
            return;
        TermuxSession.processTermuxSessionResult(this, null);
    }

    /**
     * Kill this  by sending a {@link OsConstants#SIGILL} to its {@link #mTerminalSession}
     * if its still executing.
     * <p>
     * will be called to process the failure.
     */
    public final void killIfExecuting() {
        // If execution command has already finished executing, then no need to process results or send SIGKILL
        if (mExecutionCommand.hasExecuted()) {
            return;
        }
        // Send SIGKILL to process
        mTerminalSession.finishIfRunning();
    }

    public final TerminalSession getTerminalSession() {
        return mTerminalSession;
    }

    public final ExecutionCommand getExecutionCommand() {
        return mExecutionCommand;
    }

    public interface TermuxSessionClient {

        /**
         * Callback function for when {@link TermuxSession} exits.
         *
         * @param termuxSession The {@link TermuxSession} that exited.
         */
        void onTermuxSessionExited(TermuxSession termuxSession);
    }
}
