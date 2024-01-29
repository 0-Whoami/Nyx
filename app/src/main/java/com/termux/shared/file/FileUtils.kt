package com.termux.shared.file

import com.termux.shared.file.filesystem.FileType
import com.termux.shared.file.filesystem.FileTypes
import java.io.File
import java.util.regex.Pattern


object FileUtils {
    /**
     * Required file permissions for the working directory for app usage. Working directory must have read and write permissions.
     * Execute permissions should be attempted to be set, but ignored if they are missing
     */
    // Default: "rwx"
    const val APP_WORKING_DIRECTORY_PERMISSIONS: String = "rwx"

    /**
     * Removes one or more forward slashes "//" with single slash "/"
     * Removes "./"
     * Removes trailing forward slash "/"
     *
     * @param path The `path` to convert.
     * @return Returns the `normalized path`.
     */
    private fun normalizePath(path: String?): String? {
        var path1 = path ?: return null
        path1 = path1.replace("/+".toRegex(), "/")
        path1 = path1.replace("\\./".toRegex(), "")
        if (path1.endsWith("/")) {
            path1 = path1.replace("/+$".toRegex(), "")
        }
        return path1
    }

    /**
     * Determines whether path is in `dirPath`. The `dirPath` is not canonicalized and
     * only normalized.
     *
     * @param path        The `path` to check.
     * @param dirPath     The `directory path` to check in.
     * under the directory and does not equal it.
     * @return Returns `true` if path in `dirPath`, otherwise returns `false`.
     */
    private fun isPathInDirPath(path: String?, dirPath: String): Boolean {
        return isPathInDirPaths(path, listOf(dirPath))
    }

    /**
     * Determines whether path is in one of the `dirPaths`. The `dirPaths` are not
     * canonicalized and only normalized.
     *
     * @param path        The `path` to check.
     * @param dirPaths    The `directory paths` to check in.
     * under the directories and does not equal it.
     * @return Returns `true` if path in `dirPaths`, otherwise returns `false`.
     */
    private fun isPathInDirPaths(
        path: String?,
        dirPaths: List<String?>?
    ): Boolean {
        var path1 = path
        if (path1.isNullOrEmpty() || dirPaths.isNullOrEmpty()) return false
        try {
            path1 = File(path1).canonicalPath
        } catch (e: Exception) {
            return false
        }
        var isPathInDirPaths: Boolean
        for (dirPath in dirPaths) {
            val normalizedDirPath = normalizePath(dirPath)
            isPathInDirPaths =
                path1.startsWith("$normalizedDirPath/")
            if (isPathInDirPaths) return true
        }
        return false
    }

    /**
     * Checks whether a directory file exists at `filePath`.
     *
     * @param filePath    The `path` for directory file to check.
     * finding if file exists. Check [.getFileType]
     * for details.
     * @return Returns `true` if directory file exists, otherwise `false`.
     */

    fun directoryFileExists(filePath: String?): Boolean {
        return getFileType(filePath) != FileType.DIRECTORY
    }

    /**
     * Get the type of file that exists at `filePath`.
     *
     *
     * This function is a wrapper for
     * [FileTypes.getFileType]
     *
     * @param filePath    The `path` for file to check.
     * finding type. If set to `true`, then type of symlink target will
     * be returned if file at `filePath` is a symlink. If set to
     * `false`, then type of file at `filePath` itself will be
     * returned.
     * @return Returns the [FileType] of file.
     */
    private fun getFileType(filePath: String?): FileType {
        return FileTypes.getFileType(filePath, false)
    }

    /**
     * Validate the existence and permissions of directory file at path.
     *
     *
     * If the `parentDirPath` is not `null`, then creation of missing directory and
     * setting of missing permissions will only be done if `path` is under
     * `parentDirPath` or equals `parentDirPath`.
     *
     * @param filePath                            The `path` for file to validate or create. Symlinks will not be followed.
     * @param parentDirPath                       The optional `parent directory path` to restrict operations to.
     * This can optionally be `null`. It is not canonicalized and only normalized.
     * @param createDirectoryIfMissing            The `boolean` that decides if directory file
     * should be created if its missing.
     * @param permissionsToCheck                  The 3 character string that contains the "r", "w", "x" or "-" in-order.
     * @param setPermissions                      The `boolean` that decides if permissions are to be
     * automatically set defined by `permissionsToCheck`.
     * @param setMissingPermissionsOnly           The `boolean` that decides if only missing permissions
     * are to be set or if they should be overridden.
     * @param ignoreErrorsIfPathIsInParentDirPath The `boolean` that decides if existence
     * and permission errors are to be ignored if path is
     * in `parentDirPath`.
     * @param ignoreIfNotExecutable               The `boolean` that decides if missing executable permission
     * error is to be ignored. This allows making an attempt to set
     * executable permissions, but ignoring if it fails.
     * @return Returns the `error` if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise `null`.
     */

    fun validateDirectoryFileExistenceAndPermissions(
        filePath: String?,
        parentDirPath: String?,
        createDirectoryIfMissing: Boolean,
        permissionsToCheck: String?,
        setPermissions: Boolean,
        setMissingPermissionsOnly: Boolean,
        ignoreErrorsIfPathIsInParentDirPath: Boolean,
        ignoreIfNotExecutable: Boolean
    ): Boolean {
        if (filePath.isNullOrEmpty()) return false
        try {
            val file = File(filePath)
            var fileType = getFileType(filePath)
            // If file exists but not a directory file
            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                return false
            }
            var isPathInParentDirPath = false
            if (parentDirPath != null) {
                // The path can be equal to parent directory path or under it
                isPathInParentDirPath = isPathInDirPath(filePath, parentDirPath)
            }
            if (createDirectoryIfMissing || setPermissions) {
                // If there is not parentDirPath restriction or path is in parentDirPath
                if (parentDirPath == null || (isPathInParentDirPath && getFileType(
                        parentDirPath
                    ) == FileType.DIRECTORY)
                ) {
                    // If createDirectoryIfMissing is enabled and no file exists at path, then create directory
                    if (createDirectoryIfMissing && fileType == FileType.NO_EXIST) {
                        // Create directory and update fileType if successful, otherwise return with error
                        // It "might" be possible that mkdirs returns false even though directory was created
                        val result = file.mkdirs()
                        fileType = getFileType(filePath)
                        if (!result && fileType != FileType.DIRECTORY) return false
                    }
                    // If setPermissions is enabled and path is a directory
                    if (setPermissions && permissionsToCheck != null && fileType == FileType.DIRECTORY) {
                        if (setMissingPermissionsOnly) setMissingFilePermissions(
                            filePath,
                            permissionsToCheck
                        )
                        else setFilePermissions(filePath, permissionsToCheck)
                    }
                }
            }
            // If there is not parentDirPath restriction or path is not in parentDirPath or
            // if existence or permission errors must not be ignored for paths in parentDirPath
            if (parentDirPath == null || !isPathInParentDirPath || !ignoreErrorsIfPathIsInParentDirPath) {
                // If path is not a directory
                // Directories can be automatically created so we can ignore if missing with above check
                if (fileType != FileType.DIRECTORY) {
                    return false
                }
                if (permissionsToCheck != null) {
                    // Check if permissions are missing
                    return checkMissingFilePermissions(
                        filePath,
                        permissionsToCheck,
                        ignoreIfNotExecutable
                    )
                }
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * Create a directory file at path.
     *
     *
     * This function is a wrapper for
     * [.validateDirectoryFileExistenceAndPermissions].
     *
     * are to be set or if they should be overridden.
     * @return Returns the `error` if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise `null`.
     */
    /**
     * Create a directory file at path.
     *
     *
     * This function is a wrapper for
     * [.validateDirectoryFileExistenceAndPermissions].
     *
     * @param filePath The `path` for directory file to create.
     * @return Returns the `error` if path is not a directory file or failed to create it,
     * otherwise `null`.
     */
    @JvmOverloads
    fun createDirectoryFile(
        filePath: String?,
        permissionsToCheck: String? = null,
        setPermissions: Boolean = false,
        setMissingPermissionsOnly: Boolean = false
    ): Boolean {
        return validateDirectoryFileExistenceAndPermissions(
            filePath,
            null,
            true,
            permissionsToCheck,
            setPermissions,
            setMissingPermissionsOnly,
            ignoreErrorsIfPathIsInParentDirPath = false,
            ignoreIfNotExecutable = false
        )
    }

    /**
     * Delete file at path.
     *
     *
     * The `filePath` must be the canonical path to the file to be deleted since symlinks will
     * not be followed.
     * If the `filePath` is a canonical path to a directory, then any symlink files found under
     * the directory will be deleted, but not their targets.
     *
     * see if it should be deleted or not. This is a safety measure to
     * prevent accidental deletion of the wrong type of file, like a
     * directory instead of a regular file. You can pass
     * to allow deletion of any file type.
     * @return Returns the `error` if deletion was not successful, otherwise `null`.
     */
    /**
     * Delete regular, directory or symlink file at path.
     *
     *
     * This function is a wrapper for [.deleteFile].
     *
     * @param filePath              The `path` for file to delete.
     * @param ignoreNonExistentFile The `boolean` that decides if it should be considered an
     * error if file to deleted doesn't exist.
     * @return Returns the `error` if deletion was not successful, otherwise `null`.
     */
    @JvmOverloads
    fun deleteFile(
        filePath: String?,
        ignoreNonExistentFile: Boolean,
        ignoreWrongFileType: Boolean = false,
        allowedFileTypeFlags: Int = FileTypes.FILE_TYPE_NORMAL_FLAGS
    ): Boolean {
        if (filePath.isNullOrEmpty()) return false
        try {
            val file = File(filePath)
            var fileType = getFileType(filePath)
            // If file does not exist
            if (fileType == FileType.NO_EXIST) {
                // If delete is to be ignored if file does not exist
                return ignoreNonExistentFile  // Else return with error
            }
            // If the file type of the file does not exist in the allowedFileTypeFlags
            if ((allowedFileTypeFlags and fileType.value) <= 0) {
                // If wrong file type is to be ignored
                return ignoreWrongFileType
                // Else return with error
            }
            /*
             * Try to use {@link SecureDirectoryStream} if available for safer directory
             * deletion, it should be available for android >= 8.0
             * https://guava.dev/releases/24.1-jre/api/docs/com/google/common/io/MoreFiles.html#deleteRecursively-java.nio.file.Path-com.google.common.io.RecursiveDeleteOption...-
             * https://github.com/google/guava/issues/365
             * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/sun/nio/fs/UnixSecureDirectoryStream.java
             *
             * MoreUtils is marked with the @Beta annotation so the API may be removed in
             * future but has been there for a few years now.
             *
             * If an exception is thrown, the exception message might not contain the full errors.
             * Individual failures get added to suppressed throwables which can be extracted
             * from the exception object by calling `Throwable[] getSuppressed()`. So just logging
             * the exception message and stacktrace may not be enough, the suppressed throwables
             * need to be logged as well, which the Logger class does if they are found in the
             * exception added to the Error that's returned by this function.
             * https://github.com/google/guava/blob/v30.1.1/guava/src/com/google/common/io/MoreFiles.java#L775
             */
            file.deleteRecursively()

            // If file still exists after deleting it
            fileType = getFileType(filePath)
            if (fileType != FileType.NO_EXIST) return false
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * Clear contents of directory at path without deleting the directory. If directory does not exist
     * it will be created automatically.
     *
     *
     * The `filePath` must be the canonical path to a directory since symlinks will not be followed.
     * Any symlink files found under the directory will be deleted, but not their targets.
     *
     * @param filePath The `path` for directory to clear.
     * @return Returns the `error` if clearing was not successful, otherwise `null`.
     */
    fun clearDirectory(filePath: String?): Boolean {
        if (filePath.isNullOrEmpty()) return false
        try {
            val file = File(filePath)
            val fileType = getFileType(filePath)
            // If file exists but not a directory file
            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                return false
            }
            // If directory exists, clear its contents
            if (fileType == FileType.DIRECTORY) {
                /* If an exception is thrown, the exception message might not contain the full errors.
                 * Individual failures get added to suppressed throwables. */
                file.deleteRecursively()
            } else  // Else create it
            {
                return createDirectoryFile(filePath)
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * Set permissions for file at path. Existing permission outside the `permissionsToSet`
     * will be removed.
     *
     * @param filePath         The `path` for file to set permissions to.
     * @param permissionsToSet The 3 character string that contains the "r", "w", "x" or "-" in-order.
     */
    private fun setFilePermissions(filePath: String?, permissionsToSet: String) {
        if (filePath.isNullOrEmpty()) return
        if (isValidPermissionString(permissionsToSet)) {
            return
        }
        val file = getFile(filePath, permissionsToSet)
        if (permissionsToSet.contains("x")) {
            if (!file.canExecute()) {
                file.setExecutable(true)
            }
        } else {
            if (file.canExecute()) {
                file.setExecutable(false)
            }
        }
    }


    private fun getFile(filePath: String, permissionsToSet: String): File {
        val file = File(filePath)
        if (permissionsToSet.contains("r")) {
            if (!file.canRead()) {
                file.setReadable(true)
            }
        } else {
            if (file.canRead()) {
                file.setReadable(false)
            }
        }
        if (permissionsToSet.contains("w")) {
            if (!file.canWrite()) {
                file.setWritable(true)
            }
        } else {
            if (file.canWrite()) {
                file.setWritable(false)
            }
        }
        return file
    }

    /**
     * Set missing permissions for file at path. Existing permission outside the `permissionsToSet`
     * will not be removed.
     *
     * @param filePath         The `path` for file to set permissions to.
     * @param permissionsToSet The 3 character string that contains the "r", "w", "x" or "-" in-order.
     */

    fun setMissingFilePermissions(filePath: String?, permissionsToSet: String) {
        if (filePath.isNullOrEmpty()) return
        if (isValidPermissionString(permissionsToSet)) {
            return
        }
        val file = File(filePath)
        if (permissionsToSet.contains("r") && !file.canRead()) {
            file.setReadable(true)
        }
        if (permissionsToSet.contains("w") && !file.canWrite()) {
            file.setWritable(true)
        }
        if (permissionsToSet.contains("x") && !file.canExecute()) {
            file.setExecutable(true)
        }
    }

    /**
     * Checking missing permissions for file at path.
     *
     * @param filePath              The `path` for file to check permissions for.
     * @param permissionsToCheck    The 3 character string that contains the "r", "w", "x" or "-" in-order.
     * @param ignoreIfNotExecutable The `boolean` that decides if missing executable permission
     * error is to be ignored.
     * @return Returns the `error` if validating permissions failed, otherwise `null`.
     */

    fun checkMissingFilePermissions(
        filePath: String?,
        permissionsToCheck: String,
        ignoreIfNotExecutable: Boolean
    ): Boolean {
        if (filePath.isNullOrEmpty()) return false
        if (isValidPermissionString(permissionsToCheck)) {
            return false
        }
        val file = File(filePath)
        // If file is not readable
        if (permissionsToCheck.contains("r") && !file.canRead()) {
            return false
        }
        // If file is not writable
        if (permissionsToCheck.contains("w") && !file.canWrite()) {
            return false
        } else  // If file is not executable
        // This canExecute() will give "avc: granted { execute }" warnings for target sdk 29
            if (permissionsToCheck.contains("x") && !file.canExecute() && !ignoreIfNotExecutable) {
                return false
            }
        return true
    }

    /**
     * Checks whether string exactly matches the 3 character permission string that
     * contains the "r", "w", "x" or "-" in-order.
     *
     * @param string The [String] to check.
     * @return Returns `true` if string exactly matches a permission string, otherwise `false`.
     */
    private fun isValidPermissionString(string: String?): Boolean {
        if (string.isNullOrEmpty()) return true
        return !Pattern.compile("^([r-])[w-][x-]$", 0).matcher(string).matches()
    }

}
