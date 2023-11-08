package com.termux.shared.shell.command.runner.app;

import com.termux.shared.shell.command.ExecutionCommand;

import java.io.File;

/**
 * A class that maintains info for background app shells run with {@link Runtime#exec(String[], String[], File)}.
 * It also provides a way to link each {@link Process} with the {@link ExecutionCommand}
 * that started it. The shell is run in the app user context.
 */
public final class AppShell {

    public interface AppShellClient {

    }
}
