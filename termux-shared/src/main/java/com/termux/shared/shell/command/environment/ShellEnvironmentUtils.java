package com.termux.shared.shell.command.environment;

import static com.termux.shared.shell.command.environment.UnixShellEnvironment.ENV_HOME;

import com.termux.shared.file.FileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellEnvironmentUtils {


    /**
     * Convert environment {@link HashMap} to `environ` {@link List <String>}.
     * <p>
     * The items in the environ will have the format `name=value`.
     * <p>
     * Check {@link #isValidEnvironmentVariableName(String)} and {@link #isValidEnvironmentVariableValue(String)}
     * for valid variable names and values.
     * <a href=" <p>
     * https://manpages.debian.org/testing/manpages/envir">...</a>on.7.en.<a href="html
     * ">* https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1</a>_chap08.html
     */

    public static List<String> convertEnvironmentToEnviron(HashMap<String, String> environmentMap) {
        List<String> environmentList = new ArrayList<>(environmentMap.size());
        String value;
        for (Map.Entry<String, String> entry : environmentMap.entrySet()) {
            String name = entry.getKey();
            value = entry.getValue();
            if (isValidEnvironmentVariableNameValuePair(name, value))
                environmentList.add(name + "=" + entry.getValue());
        }
        return environmentList;
    }

    /**
     * Convert environment {@link HashMap} to {@link String} where each item equals "key=value".
     */

    public static String convertEnvironmentToDotEnvFile(HashMap<String, String> environmentMap) {
        return convertEnvironmentToDotEnvFile(convertEnvironmentMapToEnvironmentVariableList(environmentMap));
    }

    /**
     * Convert environment {@link HashMap} to `.env` file {@link String}.
     * <p>
     * The items in the `.env` file have the format `export name="value"`.
     * <p>
     * If the  is set to {@code true}, then
     * will be considered to be a literal value that has
     * already been escaped by the caller, otherwise all the `"`\$` in the value will be escaped
     * with `a backslash `\`, like `\"`. Note that if `$` is escaped and if its part of variable,
     * then variable expansion will not happen if `.env` file is sourced.
     * <p>
     * The `\` at the end of a value line means line continuation. Value can contain newline characters.
     * <p>
     * Check {@link #isValidEnvironmentVariableName(String)} and {@link #isValidEnvironmentVariableValue(String)}
     * for valid variable names and values<a href=".
     * ">* <p>
     * https://github.com/ko1nksm/shdo</a>tenv#env<a href="-file-syntax
     * ">* https://github.com/ko1nksm/shdotenv/blob/main/d</a>ocs/specification.md
     */

    public static String convertEnvironmentToDotEnvFile(List<ShellEnvironmentVariable> environmentList) {
        StringBuilder environment = new StringBuilder();
        Collections.sort(environmentList);
        for (ShellEnvironmentVariable variable : environmentList) {
            if (isValidEnvironmentVariableNameValuePair(variable.name(), variable.value()) && variable.value() != null) {
                environment.append("export ").append(variable.name()).append("=\"").append(variable.escaped() ? variable.value() : variable.value().replaceAll("([\"`\\\\$])", "\\\\$1")).append("\"\n");
            }
        }
        return environment.toString();
    }

    /**
     * Convert environment {@link HashMap} to {@link List<ShellEnvironmentVariable>}. Each item
     * will have its  set to {@code false}.
     */

    public static List<ShellEnvironmentVariable> convertEnvironmentMapToEnvironmentVariableList(HashMap<String, String> environmentMap) {
        List<ShellEnvironmentVariable> environmentList = new ArrayList<>();
        for (Map.Entry<String, String> entry : environmentMap.entrySet()) {
            environmentList.add(new ShellEnvironmentVariable(entry.getKey(), entry.getValue(), false));
        }
        return environmentList;
    }

    /**
     * Check if environment variable name and value pair is valid. Errors will be logged if
     * {@code logErrors} is {@code true}.
     * <p>
     * Check {@link #isValidEnvironmentVariableName(String)} and {@link #isValidEnvironmentVariableValue(String)}
     * for valid variable names and values.
     */
    public static boolean isValidEnvironmentVariableNameValuePair(String name, String value) {
        if (isValidEnvironmentVariableName(name)) {
            return false;
        }
        return isValidEnvironmentVariableValue(value);
    }

    /**
     * Check if environment variable name is valid. It must not be {@code null} and must not contain
     * the null byte ('\0') and must only contain alphanumeric and underscore characters and must not
     * start with a digit.
     */
    public static boolean isValidEnvironmentVariableName(String name) {
        return name == null || name.contains("\0") || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    /**
     * Check if environment variable value is valid. It must not be {@code null} and must not contain
     * the null byte ('\0').
     */
    public static boolean isValidEnvironmentVariableValue(String value) {
        return value != null && !value.contains("\0");
    }

    /**
     * Put value in environment if variable exists in {@link System) environment.
     */
    public static void putToEnvIfInSystemEnv(HashMap<String, String> environment, String name) {
        String value = System.getenv(name);
        if (value != null) {
            environment.put(name, value);
        }
    }

    /**
     * Put {@link String} value in environment if value set.
     */
    public static void putToEnvIfSet(HashMap<String, String> environment, String name, String value) {
        if (value != null) {
            environment.put(name, value);
        }
    }

    /**
     * Put {@link Boolean} value "true" or "false" in environment if value set.
     */
    public static void putToEnvIfSet(HashMap<String, String> environment, String name, Boolean value) {
        if (value != null) {
            environment.put(name, String.valueOf(value));
        }
    }

    /**
     * Create HOME directory in environment {@link Map} if set.
     */
    public static void createHomeDir(HashMap<String, String> environment) {
        String homeDirectory = environment.get(ENV_HOME);
        FileUtils.INSTANCE.createDirectoryFile("shell home", homeDirectory);
    }
}
