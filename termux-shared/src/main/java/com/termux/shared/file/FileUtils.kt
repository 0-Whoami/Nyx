package com.termux.shared.file

import com.termux.shared.file.filesystem.FileType
import com.termux.shared.file.filesystem.FileTypes
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
     * @param ensureUnder If set to `true`, then it will be ensured that `path` is
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
     * @param ensureUnder If set to `true`, then it will be ensured that `path` is
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
     * Validate that directory is empty or contains only files in `ignoredSubFilePaths`.
     *
     *
     * If parent path of an ignored file exists, but ignored file itself does not exist, then directory
     * is not considered empty.
     *
     * @param label                 The optional label for directory to check. This can optionally be `null`.
     * @param filePath              The `path` for directory to check.
     * @param ignoredSubFilePaths   The list of absolute file paths under `filePath` dir.
     * Validation is done for the paths.
     * @param ignoreNonExistentFile The `boolean` that decides if it should be considered an
     * error if file to be checked doesn't exist.
     * @return Returns `null` if directory is empty or contains only files in `ignoredSubFilePaths`.
     * Returns `FileUtilsErrno#ERRNO_NON_EMPTY_DIRECTORY_FILE` if a file was found that did not
     * exist in the `ignoredSubFilePaths`, otherwise returns an appropriate `error` if
     * checking was not successful.
     */
    fun validateDirectoryFileEmptyOrOnlyContainsSpecificFiles(
        filePath: String?,
        ignoredSubFilePaths: List<String>?,
        ignoreNonExistentFile: Boolean
    ): Boolean {
        if (filePath.isNullOrEmpty()) return false
        try {
            val file = File(filePath)
            val fileType = getFileType(filePath)
            // If file exists but not a directory file
            if (fileType !== FileType.NO_EXIST && fileType !== FileType.DIRECTORY) {
                return false
            }
            // If file does not exist
            if (fileType === FileType.NO_EXIST) {
                // If checking is to be ignored if file does not exist
                return ignoreNonExistentFile
            }
            val subFiles = file.listFiles()
            if (subFiles == null || subFiles.isEmpty()) return true
            // If sub files exists but no file should be ignored
            if (ignoredSubFilePaths.isNullOrEmpty()) return false
            // If a sub file does not exist in ignored file path
            if (nonIgnoredSubFileExists(subFiles, ignoredSubFilePaths)) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * Check if `subFiles` contains contains a file not in `ignoredSubFilePaths`.
     *
     *
     * If parent path of an ignored file exists, but ignored file itself does not exist, then directory
     * is not considered empty.
     *
     *
     * This function should ideally not be called by itself but through
     * [.validateDirectoryFileEmptyOrOnlyContainsSpecificFiles].
     *
     * @param subFiles            The list of files of a directory to check.
     * @param ignoredSubFilePaths The list of absolute file paths under `filePath` dir.
     * Validation is done for the paths.
     * @return Returns `true` if a file was found that did not exist in the `ignoredSubFilePaths`,
     * otherwise  `false`.
     */
    private fun nonIgnoredSubFileExists(
        subFiles: Array<File>?,
        ignoredSubFilePaths: List<String>
    ): Boolean {
        if (subFiles.isNullOrEmpty()) return false
        var subFilePath: String
        for (subFile in subFiles) {
            subFilePath = subFile.absolutePath
            // If sub file does not exist in ignored sub file paths
            if (!ignoredSubFilePaths.contains(subFilePath)) {
                var isParentPath = false
                for (ignoredSubFilePath in ignoredSubFilePaths) {
                    if (ignoredSubFilePath.startsWith("$subFilePath/") && fileExists(
                            ignoredSubFilePath
                        )
                    ) {
                        isParentPath = true
                        break
                    }
                }
                // If sub file is not a parent of any existing ignored sub file paths
                if (!isParentPath) {
                    return true
                }
            }
            if (getFileType(subFilePath) === FileType.DIRECTORY) {
                // If non ignored sub file found, then early exit, otherwise continue looking
                if (nonIgnoredSubFileExists(subFile.listFiles(), ignoredSubFilePaths)) return true
            }
        }
        return false
    }

    /**
     * Checks whether a directory file exists at `filePath`.
     *
     * @param filePath    The `path` for directory file to check.
     * @param followLinks The `boolean` that decides if symlinks will be followed while
     * finding if file exists. Check [.getFileType]
     * for details.
     * @return Returns `true` if directory file exists, otherwise `false`.
     */
    @JvmStatic
    fun directoryFileExists(filePath: String?): Boolean {
        return getFileType(filePath) !== FileType.DIRECTORY
    }

    /**
     * Checks whether any file exists at `filePath`.
     *
     * @param filePath    The `path` for file to check.
     * @param followLinks The `boolean` that decides if symlinks will be followed while
     * finding if file exists. Check [.getFileType]
     * for details.
     * @return Returns `true` if file exists, otherwise `false`.
     */
    private fun fileExists(filePath: String?): Boolean {
        return getFileType(filePath) !== FileType.NO_EXIST
    }

    /**
     * Get the type of file that exists at `filePath`.
     *
     *
     * This function is a wrapper for
     * [FileTypes.getFileType]
     *
     * @param filePath    The `path` for file to check.
     * @param followLinks The `boolean` that decides if symlinks will be followed while
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
     * @param label                               The optional label for the directory file. This can optionally be `null`.
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
    @JvmStatic
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
            if (fileType !== FileType.NO_EXIST && fileType !== FileType.DIRECTORY) {
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
                    ) === FileType.DIRECTORY)
                ) {
                    // If createDirectoryIfMissing is enabled and no file exists at path, then create directory
                    if (createDirectoryIfMissing && fileType === FileType.NO_EXIST) {
                        // Create directory and update fileType if successful, otherwise return with error
                        // It "might" be possible that mkdirs returns false even though directory was created
                        val result = file.mkdirs()
                        fileType = getFileType(filePath)
                        if (!result && fileType !== FileType.DIRECTORY) return false
                    }
                    // If setPermissions is enabled and path is a directory
                    if (setPermissions && permissionsToCheck != null && fileType === FileType.DIRECTORY) {
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
                if (fileType !== FileType.DIRECTORY) {
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
     * Create parent directory of file at path.
     *
     *
     * This function is a wrapper for
     * [.validateDirectoryFileExistenceAndPermissions].
     *
     * @param label    The optional label for the parent directory file. This can optionally be `null`.
     * @param filePath The `path` for file whose parent needs to be created.
     * @return Returns the `error` if parent path is not a directory file or failed to create it,
     * otherwise `null`.
     */
    private fun createParentDirectoryFile(filePath: String?): Boolean {
        if (filePath.isNullOrEmpty()) return false
        val file = File(filePath)
        val fileParentPath = file.parent
        return if (fileParentPath != null) createDirectoryFile(
            fileParentPath,
            null,
            setPermissions = false,
            setMissingPermissionsOnly = false
        )
        else true
    }

    private fun copyDir(src: Path, dest: Path) {
        Files.walk(src).forEach {
            Files.copy(
                it, dest.resolve(src.relativize(it)),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
    /**
     * Create a directory file at path.
     *
     *
     * This function is a wrapper for
     * [.validateDirectoryFileExistenceAndPermissions].
     *
     * @param label                     The optional label for the directory file. This can optionally be `null`.
     * @param filePath                  The `path` for directory file to create.
     * @param permissionsToCheck        The 3 character string that contains the "r", "w", "x" or "-" in-order.
     * @param setPermissions            The `boolean` that decides if permissions are to be
     * automatically set defined by `permissionsToCheck`.
     * @param setMissingPermissionsOnly The `boolean` that decides if only missing permissions
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
     * @param label    The optional label for the directory file. This can optionally be `null`.
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
     * Move a regular file from `sourceFilePath` to `destFilePath`.
     *
     *
     * This function is a wrapper for
     * [.copyOrMoveFile].
     *
     *
     * If destination file already exists, then it will be overwritten, but only if its a regular
     * file, otherwise an error will be returned.
     *
     * @param label                    The optional label for file to move. This can optionally be `null`.
     * @param srcFilePath              The `source path` for file to move.
     * @param destFilePath             The `destination path` for file to move.
     * @param ignoreNonExistentSrcFile The `boolean` that decides if it should be considered an
     * error if source file to moved doesn't exist.
     */
    @JvmStatic
    fun moveRegularFile(
        srcFilePath: String?,
        destFilePath: String?,
    ) {
        copyOrMoveFile(
            srcFilePath,
            destFilePath,
            FileType.REGULAR.value
        )
    }

    /**
     * Copy or move a file from `sourceFilePath` to `destFilePath`.
     *
     *
     * The `sourceFilePath` and `destFilePath` must be the canonical path to the source
     * and destination since symlinks will not be followed.
     *
     *
     * If the `sourceFilePath` or `destFilePath` is a canonical path to a directory,
     * then any symlink files found under the directory will be deleted, but not their targets when
     * deleting source after move and deleting destination before copy/move.
     *
     * @param label                                The optional label for file to copy or move. This can optionally be `null`.
     * @param srcFilePath                          The `source path` for file to copy or move.
     * @param destFilePath                         The `destination path` for file to copy or move.
     * @param moveFile                             The `boolean` that decides if source file needs to be copied or moved.
     * If set to `true`, then source file will be moved, otherwise it will be
     * copied.
     * @param ignoreNonExistentSrcFile             The `boolean` that decides if it should be considered an
     * error if source file to copied or moved doesn't exist.
     * @param allowedFileTypeFlags                 The flags that are matched against the source file's [FileType]
     * to see if it should be copied/moved or not. This is a safety measure
     * to prevent accidental copy/move/delete of the wrong type of file,
     * like a directory instead of a regular file. You can pass
     * to allow copy/move of any file type.
     * @param overwrite                            The `boolean` that decides if destination file should be overwritten if
     * it already exists. If set to `true`, then destination file will be
     * deleted before source is copied or moved.
     * @param overwriteOnlyIfDestSameFileTypeAsSrc The `boolean` that decides if overwrite should
     * only be done if destination file is also the same file
     * type as the source file.
     */
    private fun copyOrMoveFile(
        srcFilePath: String?,
        destFilePath: String?,
        allowedFileTypeFlags: Int
    ) {
        if (srcFilePath.isNullOrEmpty()) {
            return
        }
        if (destFilePath.isNullOrEmpty()) {
            return
        }
        var error: Boolean
        try {
            val srcFile = File(srcFilePath)
            val destFile = File(destFilePath)
            val srcFileType = getFileType(srcFilePath)
            val destFileType = getFileType(destFilePath)
            val srcFileCanonicalPath = srcFile.canonicalPath
            val destFileCanonicalPath = destFile.canonicalPath
            // If source file does not exist
            if (srcFileType === FileType.NO_EXIST) {
                // If copy or move is to be ignored if source file is not found

                // Else return with error
                return
            }
            // If the file type of the source file does not exist in the allowedFileTypeFlags, then return with error
            if ((allowedFileTypeFlags and srcFileType.value) <= 0) {

                return
            }
            // If source and destination file path are the same
            if (srcFileCanonicalPath == destFileCanonicalPath) {
                return
            }
            // If destination exists
            if (destFileType !== FileType.NO_EXIST) {
                // If destination must not be overwritten
                // If overwriteOnlyIfDestSameFileTypeAsSrc is enabled but destination file does not match source file type
                if (destFileType !== srcFileType) {
                    return
                }
                // Delete the destination file
                error = deleteFile(destFilePath, true)
                if (error) return
            }
            // Copy or move source file to dest
            var copyFile = false
            // If moveFile is true
            if (!srcFile.renameTo(destFile)) {
                // If destination directory is a subdirectory of the source directory
                // Copying is still allowed by copyDirectory() by excluding destination directory files
                if (srcFileType === FileType.DIRECTORY && destFileCanonicalPath.startsWith(
                        srcFileCanonicalPath + File.separator
                    )
                ) {
                    return
                }
                // If rename failed, then we copy
                copyFile = true
            }
            // If moveFile is false or renameTo failed while moving
            if (copyFile) {
                // Create the dest file parent directory
                error = createParentDirectoryFile(destFilePath)
                if (error) return
                if (srcFileType === FileType.DIRECTORY) {
                    // Will give runtime exceptions on android < 8 due to missing classes like java.nio.file.Path if org.apache.commons.io version > 2.5
                    copyDir(srcFile.toPath(), destFile.toPath())
                } else if (srcFileType === FileType.SYMLINK) {
                    Files.copy(
                        srcFile.toPath(),
                        destFile.toPath(),
                        LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } else {
                    Files.copy(
                        srcFile.toPath(),
                        destFile.toPath(),
                        LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
            // If source file had to be moved
            deleteFile(srcFilePath, true)
        } catch (e: Exception) {
        }
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
     * @param label                 The optional label for file to delete. This can optionally be `null`.
     * @param filePath              The `path` for file to delete.
     * @param ignoreNonExistentFile The `boolean` that decides if it should be considered an
     * error if file to deleted doesn't exist.
     * @param ignoreWrongFileType   The `boolean` that decides if it should be considered an
     * error if file type is not one from `allowedFileTypeFlags`.
     * @param allowedFileTypeFlags  The flags that are matched against the file's [FileType] to
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
     * @param label                 The optional label for file to delete. This can optionally be `null`.
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
            if (fileType === FileType.NO_EXIST) {
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
            if (fileType !== FileType.NO_EXIST) return false
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
     * @param label    The optional label for directory to clear. This can optionally be `null`.
     * @param filePath The `path` for directory to clear.
     * @return Returns the `error` if clearing was not successful, otherwise `null`.
     */
    fun clearDirectory(filePath: String?): Boolean {
        if (filePath.isNullOrEmpty()) return false
        try {
            val file = File(filePath)
            val fileType = getFileType(filePath)
            // If file exists but not a directory file
            if (fileType !== FileType.NO_EXIST && fileType !== FileType.DIRECTORY) {
                return false
            }
            // If directory exists, clear its contents
            if (fileType === FileType.DIRECTORY) {
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
     * Write text `dataString` with a specific [Charset] to file at path.
     *
     * @param label      The optional label for file to write. This can optionally be `null`.
     * @param filePath   The `path` for file to write.
     * @param charset    The [Charset] of the `dataString`. If this is `null`,
     * then default [Charset] will be used.
     * @param dataString The data to write to file.
     * @param append     The `boolean` that decides if file should be appended to or not.
     * @return Returns the `error` if writing was not successful, otherwise `null`.
     */
    @JvmStatic
    fun writeTextToFile(
        filePath: String?,
        charset: Charset?,
        dataString: String?,
        append: Boolean
    ): Boolean {
        var charset1 = charset
        if (filePath.isNullOrEmpty()) return false
        var error = preWriteToFile(filePath)
        if (!error) return false
        if (charset1 == null) charset1 = Charset.defaultCharset()
        // Check if charset is supported
        error = isCharsetSupported(charset1)
        if (!error) return false
        var fileOutputStream: FileOutputStream? = null
        var bufferedWriter: BufferedWriter? = null
        try {
            // Write text to file
            fileOutputStream = FileOutputStream(filePath, append)
            bufferedWriter = BufferedWriter(OutputStreamWriter(fileOutputStream, charset1))
            bufferedWriter.write(dataString)
            bufferedWriter.flush()
        } catch (e: Exception) {
            return false
        } finally {
            closeCloseable(fileOutputStream)
            closeCloseable(bufferedWriter)
        }
        return true
    }

    private fun preWriteToFile(filePath: String): Boolean {
        val fileType = getFileType(filePath)
        // If file exists but not a regular file
        if (fileType !== FileType.NO_EXIST && fileType !== FileType.REGULAR) {
            return false
        }
        // Create the file parent directory
        return createParentDirectoryFile(filePath)
    }

    /**
     * Check if a specific [Charset] is supported.
     *
     * @param charset The [Charset] to check.
     * @return Returns the `error` if charset is not supported or failed to check it, otherwise `null`.
     */
    private fun isCharsetSupported(charset: Charset?): Boolean {
        if (charset == null) return false
        try {
            if (!Charset.isSupported(charset.name())) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * Close a [Closeable] object if not `null` and ignore any exceptions raised.
     *
     * @param closeable The [Closeable] object to close.
     */
    private fun closeCloseable(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
                // ignore
            }
        }
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
    @JvmStatic
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
     * @param label                 The optional label for the file. This can optionally be `null`.
     * @param filePath              The `path` for file to check permissions for.
     * @param permissionsToCheck    The 3 character string that contains the "r", "w", "x" or "-" in-order.
     * @param ignoreIfNotExecutable The `boolean` that decides if missing executable permission
     * error is to be ignored.
     * @return Returns the `error` if validating permissions failed, otherwise `null`.
     */
    @JvmStatic
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

    /**
     * Get file basename for file at `filePath`.
     *
     * @param filePath The `path` for file.
     * @return Returns the file basename if not `null`.
     */
    @JvmStatic
    fun getFileBasename(filePath: String): String? {
        if (filePath.isEmpty()) return null
        val lastSlash = filePath.lastIndexOf('/')
        return if ((lastSlash == -1)) filePath else filePath.substring(lastSlash + 1)
    }
}
