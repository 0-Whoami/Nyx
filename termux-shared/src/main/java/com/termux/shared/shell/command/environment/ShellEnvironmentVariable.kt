package com.termux.shared.shell.command.environment

/**
 * @param name    The name for environment variable
 * @param value   The value for environment variable
 * @param escaped If environment variable [.value] is already escaped.
 */
@JvmRecord
data class ShellEnvironmentVariable(
    @JvmField val name: String, @JvmField val value: String,
    @JvmField val escaped: Boolean
) : Comparable<ShellEnvironmentVariable> {
    override fun compareTo(other: ShellEnvironmentVariable): Int {
        return name.compareTo(other.name)
    }
}
