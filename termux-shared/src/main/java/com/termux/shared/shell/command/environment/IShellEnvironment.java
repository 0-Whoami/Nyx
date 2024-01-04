package com.termux.shared.shell.command.environment;

import android.content.Context;


import com.termux.shared.shell.command.ExecutionCommand;

import java.util.HashMap;

public interface IShellEnvironment {

    /**
     * Get the default working directory path for the environment in case the path that was passed
     * was {@code null} or empty.
     *
     * @return Should return the default working directory path.
     */

    String getDefaultWorkingDirectoryPath();

    /**
     * Get the default "/bin" path, like $PREFIX/bin.
     *
     * @return Should return the "/bin" path.
     */

    String getDefaultBinPath();

    /**
     * Setup shell command arguments for the file to execute, like interpreter, etc.
     *
     * @param fileToExecute The file to execute.
     * @param arguments     The arguments to pass to the executable.
     * @return Should return the final process arguments.
     */

    String[] setupShellCommandArguments(String fileToExecute, String[] arguments);

    /**
     * Setup shell command environment to be used for commands.
     *
     * @param currentPackageContext The {@link Context} for the current package.
     * @param executionCommand      The {@link ExecutionCommand} for which to set environment.
     * @return Should return the shell environment.
     */

    HashMap<String, String> setupShellCommandEnvironment(Context currentPackageContext, ExecutionCommand executionCommand);
}
