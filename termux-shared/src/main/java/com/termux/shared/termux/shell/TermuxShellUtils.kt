package com.termux.shared.termux.shell

import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Collections

object TermuxShellUtils {
    /**
     * Setup shell command arguments for the execute. The file interpreter may be prefixed to
     * command arguments if needed.
     */
    fun setupShellCommandArguments(
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
