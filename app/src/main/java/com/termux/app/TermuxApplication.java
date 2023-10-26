package com.termux.app;

import android.app.Application;
import android.content.Context;

import com.termux.BuildConfig;
import com.termux.shared.errors.Error;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

public class TermuxApplication extends Application {


    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        // Set crash handler for the app
        // Set log config for the app
        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);
        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties.init(context);
        // Init app wide shell manager
        TermuxShellManager.init(context);
        // Set NightMode.APP_NIGHT_MODE
        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                return;
            }
            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        }
        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);
        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }
    }


}
