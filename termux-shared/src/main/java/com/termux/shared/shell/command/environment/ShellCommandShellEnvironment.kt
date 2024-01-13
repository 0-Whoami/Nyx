package com.termux.shared.shell.command.environment

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner.Companion.runnerOf

/**
 * Environment for [ExecutionCommand].
 */
open class ShellCommandShellEnvironment {
    /**
     * Get shell environment containing info for [ExecutionCommand].
     */
    open fun getEnvironment(
        currentPackageContext: Context,
        executionCommand: ExecutionCommand
    ): HashMap<String, String> {
        val environment = HashMap<String, String>()
        val runner = runnerOf(executionCommand.runner!!) ?: return environment
        ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_SHELL_CMD__RUNNER_NAME, runner.value)
        ShellEnvironmentUtils.putToEnvIfSet(
            environment,
            ENV_SHELL_CMD__PACKAGE_NAME,
            currentPackageContext.packageName
        )
        ShellEnvironmentUtils.putToEnvIfSet(
            environment,
            ENV_SHELL_CMD__SHELL_ID,
            executionCommand.id.toString()
        )
        ShellEnvironmentUtils.putToEnvIfSet(
            environment,
            ENV_SHELL_CMD__SHELL_NAME,
            executionCommand.shellName
        )
        return environment
    }

    companion object {
        /**
         * Environment variable prefix for the [ExecutionCommand].
         */
        private const val SHELL_CMD_ENV_PREFIX = "SHELL_CMD__"

        /**
         * Environment variable for the  number since app start.
         */
        const val ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START: String =
            SHELL_CMD_ENV_PREFIX + "APP_SHELL_NUMBER_SINCE_APP_START"

        /**
         * Environment variable for the  number since app start.
         */
        const val ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START: String =
            SHELL_CMD_ENV_PREFIX + "TERMINAL_SESSION_NUMBER_SINCE_APP_START"

        /**
         * Environment variable for the [ExecutionCommand.Runner] name.
         */
        private const val ENV_SHELL_CMD__RUNNER_NAME = SHELL_CMD_ENV_PREFIX + "RUNNER_NAME"

        /**
         * Environment variable for the package name running the [ExecutionCommand].
         */
        private const val ENV_SHELL_CMD__PACKAGE_NAME = SHELL_CMD_ENV_PREFIX + "PACKAGE_NAME"

        /**
         * Environment variable for the [ExecutionCommand.id]/TermuxShellManager.SHELL_ID name.
         * This will be common for all runners.
         */
        private const val ENV_SHELL_CMD__SHELL_ID = SHELL_CMD_ENV_PREFIX + "SHELL_ID"

        /**
         * Environment variable for the [ExecutionCommand.shellName] name.
         */
        private const val ENV_SHELL_CMD__SHELL_NAME = SHELL_CMD_ENV_PREFIX + "SHELL_NAME"
    }
}
