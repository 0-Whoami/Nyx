package com.termux.shared.shell.command.environment

import com.termux.shared.file.FileUtils.createDirectoryFile
import java.util.regex.Pattern

object ShellEnvironmentUtils {
    private val REGEX: Pattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*")

    /**
     * Convert environment [HashMap] to `environ` [&lt;String&gt;][List].
     *
     *
     * The items in the environ will have the format `name=value`.
     *
     *
     * Check [.isValidEnvironmentVariableName] and [.isValidEnvironmentVariableValue]
     * for valid variable names and values.
     * [...]( <p>
      https://manpages.debian.org/testing/manpages/envir)on.7.en.[* https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1](html
      )_chap08.html
     */
    fun convertEnvironmentToEnviron(environmentMap: Map<String, String>): MutableList<String> {
        val environmentList: MutableList<String> = ArrayList(environmentMap.size)
        var value: String
        for ((name, value1) in environmentMap) {
            value = value1
            if (isValidEnvironmentVariableNameValuePair(name, value)) environmentList.add(
                "$name=$value1"
            )
        }
        return environmentList
    }

    /**
     * Check if environment variable name and value pair is valid. Errors will be logged if
     * `logErrors` is `true`.
     *
     *
     * Check [.isValidEnvironmentVariableName] and [.isValidEnvironmentVariableValue]
     * for valid variable names and values.
     */
    private fun isValidEnvironmentVariableNameValuePair(name: String?, value: String?): Boolean {
        if (isValidEnvironmentVariableName(name)) {
            return false
        }
        return isValidEnvironmentVariableValue(value)
    }

    /**
     * Check if environment variable name is valid. It must not be `null` and must not contain
     * the null byte ('\0') and must only contain alphanumeric and underscore characters and must not
     * start with a digit.
     */
    private fun isValidEnvironmentVariableName(name: String?): Boolean {
        return name == null || name.contains("\u0000") || !REGEX.matcher(name).matches()
    }

    /**
     * Check if environment variable value is valid. It must not be `null` and must not contain
     * the null byte ('\0').
     */
    private fun isValidEnvironmentVariableValue(value: String?): Boolean {
        return value != null && !value.contains("\u0000")
    }

    /**
     * Put value in environment if variable exists in [) environment.][System]
     */
    fun putToEnvIfInSystemEnv(environment: MutableMap<String, String>, name: String) {
        val value = System.getenv(name)
        if (value != null) {
            environment[name] = value
        }
    }

    /**
     * Put [String] value in environment if value set.
     */
    @JvmStatic
    fun putToEnvIfSet(environment: MutableMap<String, String>, name: String, value: String?) {
        if (value != null) {
            environment[name] = value
        }
    }


    /**
     * Create HOME directory in environment [Map] if set.
     */
    fun createHomeDir(environment: HashMap<String, String>) {
        val homeDirectory = environment[UnixShellEnvironment.ENV_HOME]
        createDirectoryFile("shell home", homeDirectory)
    }
}
