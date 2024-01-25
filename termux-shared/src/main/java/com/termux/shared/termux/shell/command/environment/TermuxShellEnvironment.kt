package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.shell.command.environment.AndroidShellEnvironment
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Collections

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
        environment[ENV_HOME] = TermuxConstants.TERMUX_HOME_DIR_PATH
        environment["PREFIX"] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
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
    ): Array<String> {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        var interpreter: String? = null
        try {
            val file = File(executable)
            FileInputStream(file).use { `in` ->
                val buffer = ByteArray(256)
                val bytesRead = `in`.read(buffer)
                if (4 < bytesRead) {
                    if (0x7F == buffer[0].toInt() && 'E'.code.toByte() == buffer[1] && 'L'.code.toByte() == buffer[2] && 'F'.code.toByte() == buffer[3]) {
                        // Elf file, do nothing.
                    } else if ('#'.code.toByte() == buffer[0] && '!'.code.toByte() == buffer[1]) {
                        // Try to parse shebang.
                        val builder = StringBuilder()
                        for (i in 2 until bytesRead) {
                            val c = Char(buffer[i].toUShort())
                            if (' ' == c || '\n' == c) {
                                if (builder.isNotEmpty()) {
                                    // End of shebang.
                                    val shebangExecutable = builder.toString()
                                    if (shebangExecutable.startsWith("/usr") || shebangExecutable.startsWith(
                                            "/bin"
                                        )
                                    ) {
                                        val parts = shebangExecutable.split("/".toRegex())
                                            .dropLastWhile { it.isEmpty() }
                                            .toTypedArray()
                                        val binary = parts[parts.size - 1]
                                        interpreter =
                                            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + binary
                                    }
                                    break
                                }
                            } else {
                                builder.append(c)
                            }
                        }
                    } else {
                        // No shebang and no ELF, use standard shell.
                        interpreter = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh"
                    }
                }
            }
        } catch (e: IOException) {
            // Ignore.
        }
        val result: ArrayList<String> = ArrayList()
        if (null != interpreter) result.add(interpreter!!)
        result.add(executable)
        if (null != arguments) Collections.addAll(result, *arguments)
        return result.toTypedArray()
    }
}
