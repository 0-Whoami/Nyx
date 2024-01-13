package com.termux.app

import android.app.Application
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment

class TermuxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set crash handler for the app

        // Set log config for the app
        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        // Init app wide SharedProperties loaded from termux.properties
        // Init app wide shell manager
        TermuxShellManager.init()
        // Set NightMode.APP_NIGHT_MODE
        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        var error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(
            this,
            createDirectoryIfMissing = true,
            setMissingPermissions = true
        )
        val isTermuxFilesDirectoryAccessible = error == null
        if (isTermuxFilesDirectoryAccessible) {
            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(
                true,
                setMissingPermissions = true
            )
            if (error != null) {
                return
            }
            // Setup termux-am-socket server
        }
        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this)
        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this)
        }
    }
}