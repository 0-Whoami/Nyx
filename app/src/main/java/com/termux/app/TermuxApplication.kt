package com.termux.app

import android.app.Application
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment

class TermuxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Init app wide shell manager
        TermuxShellManager.init()
        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        var error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(
            this,
            createDirectoryIfMissing = true,
            setMissingPermissions = true
        )
        val isTermuxFilesDirectoryAccessible = error
        if (isTermuxFilesDirectoryAccessible) {
            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(
                true,
                setMissingPermissions = true
            )
            if (!error) {
                return
            }
        }
        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this)
        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this)
        }
    }
}
