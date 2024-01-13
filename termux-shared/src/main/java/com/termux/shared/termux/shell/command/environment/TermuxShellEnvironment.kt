package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.file.FileUtils.moveRegularFile
import com.termux.shared.file.FileUtils.writeTextToFile
import com.termux.shared.shell.command.environment.AndroidShellEnvironment
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils.convertEnvironmentToDotEnvFile
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.TermuxShellUtils
import com.termux.shared.termux.shell.command.environment.TermuxAppShellEnvironment.getEnvironment
import com.termux.shared.termux.shell.command.environment.TermuxAppShellEnvironment.setTermuxAppEnvironment
import java.nio.charset.Charset

/**
 * Environment for Termux.
 */
class TermuxShellEnvironment : AndroidShellEnvironment() {
    init {
        shellCommandShellEnvironment = TermuxShellCommandShellEnvironment()
    }

    /**
     * Get shell environment for Termux.
     */
    override fun getEnvironment(
        currentPackageContext: Context,
        isFailSafe: Boolean
    ): HashMap<String, String> {
        // Termux environment builds upon the Android environment
        val environment = super.getEnvironment(currentPackageContext, isFailSafe)
        val termuxAppEnvironment: Map<String, String>? = getEnvironment(currentPackageContext)
        if (termuxAppEnvironment != null) environment.putAll(termuxAppEnvironment)
        environment[ENV_HOME] = TermuxConstants.TERMUX_HOME_DIR_PATH
        environment[ENV_PREFIX] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that system binaries can be used
        if (!isFailSafe) {
            environment[ENV_TMPDIR] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH

            // Termux binaries on Android 7+ rely on DT_RUNPATH, so LD_LIBRARY_PATH should be unset by default
            environment[ENV_PATH] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
            environment.remove(ENV_LD_LIBRARY_PATH)
        }
        return environment
    }


    override val defaultWorkingDirectoryPath: String
        get() = TermuxConstants.TERMUX_HOME_DIR_PATH


    override val defaultBinPath: String
        get() = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH


    override fun setupShellCommandArguments(
        executable: String,
        arguments: Array<String>?
    ): Array<String>? {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments)
    }

    companion object {
        /**
         * Environment variable for the termux [TermuxConstants.TERMUX_PREFIX_DIR_PATH].
         */
        private const val ENV_PREFIX = "PREFIX"

        /**
         * Init [TermuxShellEnvironment] constants and caches.
         */
        @Synchronized
        fun init(currentPackageContext: Context?) {
            setTermuxAppEnvironment(currentPackageContext ?: return)
        }

        /**
         * Init [TermuxShellEnvironment] constants and caches.
         */
        @Synchronized
        fun writeEnvironmentToFile(currentPackageContext: Context) {
            val environmentMap =
                TermuxShellEnvironment().getEnvironment(currentPackageContext, false)
            val environmentString = convertEnvironmentToDotEnvFile(environmentMap)
            // Write environment string to temp file and then move to final location since otherwise
            // writing may happen while file is being sourced/read
            val error = writeTextToFile(
                TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
                Charset.defaultCharset(),
                environmentString,
                false
            )
            if (!error) {
                return
            }
            moveRegularFile(
                TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
                TermuxConstants.TERMUX_ENV_FILE_PATH
            )
        }
    }
}
