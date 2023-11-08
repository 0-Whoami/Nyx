package com.termux.shared.shell.command.environment;

/**
 * @param name    The name for environment variable
 * @param value   The value for environment variable
 * @param escaped If environment variable {@link #value} is already escaped.
 */
public record ShellEnvironmentVariable(String name, String value,
                                       boolean escaped) implements Comparable<ShellEnvironmentVariable> {

    @Override
    public int compareTo(ShellEnvironmentVariable other) {
        return this.name.compareTo(other.name);
    }
}
