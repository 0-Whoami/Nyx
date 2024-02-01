package com.termux.app

import android.app.Application
import com.termux.utils.file.FileUtils

class TermuxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        var error = FileUtils.isTermuxFilesDirectoryAccessible(
            this,
            createDirectoryIfMissing = true,
            setMissingPermissions = true
        )
        val isTermuxFilesDirectoryAccessible = error
        if (isTermuxFilesDirectoryAccessible) {
            error = FileUtils.isAppsTermuxAppDirectoryAccessible(
                true,
                setMissingPermissions = true
            )
            if (!error) {
                return
            }
        }
    }
}
