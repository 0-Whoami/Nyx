package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.environment.ShellCommandShellEnvironment
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils.putToEnvIfSet
import com.termux.shared.termux.shell.TermuxShellManager

/**
 * Environment for Termux [ExecutionCommand].
 */
class TermuxShellCommandShellEnvironment : ShellCommandShellEnvironment() {
    /**
     * Get shell environment containing info for Termux [ExecutionCommand].
     */
    override fun getEnvironment(
        currentPackageContext: Context,
        executionCommand: ExecutionCommand
    ): HashMap<String, String> {
        val environment = super.getEnvironment(currentPackageContext, executionCommand)

        if (ExecutionCommand.Runner.APP_SHELL.value == executionCommand.runner) {
            putToEnvIfSet(
                environment,
                ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START,
                TermuxShellManager.andIncrementAppShellNumberSinceAppStart.toString()
            )
        } else if (ExecutionCommand.Runner.TERMINAL_SESSION.value == executionCommand.runner) {
            putToEnvIfSet(
                environment,
                ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START,
                TermuxShellManager.andIncrementTerminalSessionNumberSinceAppStart.toString()
            )
        } else {
            return environment
        }
        return environment
    }
}
