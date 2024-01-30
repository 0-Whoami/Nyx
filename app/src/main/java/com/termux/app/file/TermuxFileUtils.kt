package com.termux.app.file

import android.content.Context
import com.termux.app.TermuxConstants
import com.termux.app.TermuxConstants.TERMUX_PREFIX_DIR_PATH
import com.termux.shared.file.FileUtils
import com.termux.shared.file.FileUtils.checkMissingFilePermissions
import com.termux.shared.file.FileUtils.directoryFileExists
import com.termux.shared.file.FileUtils.setMissingFilePermissions
import com.termux.shared.file.FileUtils.validateDirectoryFileExistenceAndPermissions

object TermuxFileUtils {

    fun isTermuxFilesDirectoryAccessible(
        context: Context,
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Boolean {
        if (createDirectoryIfMissing) context.filesDir
        if (directoryFileExists(TermuxConstants.TERMUX_FILES_DIR_PATH)) return false
        if (setMissingPermissions) setMissingFilePermissions(
            TermuxConstants.TERMUX_FILES_DIR_PATH,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS
        )
        return checkMissingFilePermissions(
            TermuxConstants.TERMUX_FILES_DIR_PATH,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS,
            false
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_PREFIX_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     * .
     *
     *
     * The [TermuxConstants.TERMUX_PREFIX_DIR_PATH] directory would not exist if termux has
     * not been installed or the bootstrap setup has not been run or if it was deleted by the user.
     *
     * @param createDirectoryIfMissing The `boolean` that decides if directory file
     * should be created if its missing.
     * @param setMissingPermissions    The `boolean` that decides if permissions are to be
     * automatically set.
     * @return Returns the `error` if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise `null`.
     */
    fun isTermuxPrefixDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Boolean {
        return validateDirectoryFileExistenceAndPermissions(
            TERMUX_PREFIX_DIR_PATH,
            null,
            createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS,
            setMissingPermissions,
            setMissingPermissionsOnly = true,
            ignoreErrorsIfPathIsInParentDirPath = false,
            ignoreIfNotExecutable = false
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     *
     * @param createDirectoryIfMissing The `boolean` that decides if directory file
     * should be created if its missing.
     * @param setMissingPermissions    The `boolean` that decides if permissions are to be
     * automatically set.
     * @return Returns the `error` if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise `null`.
     */
    fun isTermuxPrefixStagingDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Boolean {
        return validateDirectoryFileExistenceAndPermissions(
            TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH,
            null,
            createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS,
            setMissingPermissions,
            setMissingPermissionsOnly = true,
            ignoreErrorsIfPathIsInParentDirPath = false,
            ignoreIfNotExecutable = false
        )
    }

    /**
     * Validate if [TermuxConstants.TERMUX_APP.APPS_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     *
     * @param createDirectoryIfMissing The `boolean` that decides if directory file
     * should be created if its missing.
     * @param setMissingPermissions    The `boolean` that decides if permissions are to be
     * automatically set.
     * @return Returns the `error` if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise `null`.
     */
    fun isAppsTermuxAppDirectoryAccessible(
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Boolean {
        return validateDirectoryFileExistenceAndPermissions(
            TermuxConstants.TERMUX_APP.APPS_DIR_PATH,
            null,
            createDirectoryIfMissing,
            FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS,
            setMissingPermissions,
            setMissingPermissionsOnly = true,
            ignoreErrorsIfPathIsInParentDirPath = false,
            ignoreIfNotExecutable = false
        )
    }

}
