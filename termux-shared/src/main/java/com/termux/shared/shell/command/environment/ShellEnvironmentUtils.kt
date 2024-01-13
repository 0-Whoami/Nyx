package com.termux.shared.shell.command.environment

import com.termux.shared.file.FileUtils.createDirectoryFile
import java.util.regex.Pattern

object ShellEnvironmentUtils {
    private val PATTERN: Pattern = Pattern.compile("([\"`\\\\$])")
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
    fun convertEnvironmentToEnviron(environmentMap: Map<String, String>): List<String> {
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
     * Convert environment [HashMap] to [String] where each item equals "key=value".
     */
    fun convertEnvironmentToDotEnvFile(environmentMap: HashMap<String, String>): String {
        return convertEnvironmentToDotEnvFile(
            convertEnvironmentMapToEnvironmentVariableList(
                environmentMap
            )
        )
    }

    /**
     * Convert environment [HashMap] to `.env` file [String].
     *
     *
     * The items in the `.env` file have the format `export name="value"`.
     *
     *
     * If the  is set to `true`, then
     * will be considered to be a literal value that has
     * already been escaped by the caller, otherwise all the `"`\$` in the value will be escaped
     * with `a backslash `\`, like `\"`. Note that if `$` is escaped and if its part of variable,
     * then variable expansion will not happen if `.env` file is sourced.
     *
     *
     * The `\` at the end of a value line means line continuation. Value can contain newline characters.
     *
     *
     * Check [.isValidEnvironmentVariableName] and [.isValidEnvironmentVariableValue]
     * for valid variable names and values[* 
 *
 *
 * https://github.com/ko1nksm/shdo](.
      )tenv#env[* https://github.com/ko1nksm/shdotenv/blob/main/d](-file-syntax
      )ocs/specification.md
     */
    private fun convertEnvironmentToDotEnvFile(environmentList: List<ShellEnvironmentVariable>): String {
        val environment = StringBuilder()
        for ((name, value, escaped) in environmentList) {
            if (isValidEnvironmentVariableNameValuePair(name, value)) {
                environment.append("export ").append(name).append("=\"").append(
                    if (escaped) value else PATTERN.matcher(
                        value
                    ).replaceAll("\\\\$1")
                ).append("\"\n")
            }
        }
        return environment.toString()
    }

    /**
     * Convert environment [HashMap] to [<]. Each item
     * will have its  set to `false`.
     */
    private fun convertEnvironmentMapToEnvironmentVariableList(environmentMap: Map<String, String>): List<ShellEnvironmentVariable> {
        val environmentList: MutableList<ShellEnvironmentVariable> = ArrayList()
        for ((key, value) in environmentMap) {
            environmentList.add(ShellEnvironmentVariable(key, value, false))
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
