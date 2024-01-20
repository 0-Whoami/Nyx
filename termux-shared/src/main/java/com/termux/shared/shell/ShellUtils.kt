package com.termux.shared.shell

import com.termux.shared.file.FileUtils.getFileBasename
import java.util.Collections


object ShellUtils {
    /**
     * Setup shell command arguments for the execute.
     */
    fun setupShellCommandArguments(executable: String, arguments: Array<String>?): Array<String> {
        val result: MutableList<String> = ArrayList()
        result.add(executable)
        if (arguments != null) Collections.addAll(result, *arguments)
        return result.toTypedArray<String>()
    }

    /**
     * Get basename for executable.
     */
    @JvmStatic
    fun getExecutableBasename(executable: String?): String? {
        return getFileBasename(executable ?: return null)
    }

}
