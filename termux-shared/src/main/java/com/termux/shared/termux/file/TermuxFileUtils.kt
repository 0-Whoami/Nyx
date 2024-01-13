package com.termux.shared.termux.file

import android.content.Context
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.file.FileUtils.checkMissingFilePermissions
import com.termux.shared.file.FileUtils.directoryFileExists
import com.termux.shared.file.FileUtils.setMissingFilePermissions
import com.termux.shared.file.FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFiles
import com.termux.shared.file.FileUtils.validateDirectoryFileExistenceAndPermissions
import com.termux.shared.file.FileUtilsErrno
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH

object TermuxFileUtils {
    /**
     * Validate if [TermuxConstants.TERMUX_FILES_DIR_PATH] exists and has
     * [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS] permissions.
     *
     *
     * This is required because binaries compiled for termux are hard coded with
     * [TermuxConstants.TERMUX_PREFIX_DIR_PATH] and the path must be accessible.
     *
     *
     * The permissions set to directory will be [FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS].
     *
     *
     * This function does not create the directory manually but by calling [Context.getFilesDir]
     * so that android itself creates it. However, the call will not create its parent package
     * data directory `/data/user/0/[package_name]` if it does not already exist and a `logcat`
     * error will be logged by android.
     * `Failed to ensure /data/user/0/<package_name>/files: mkdir failed: ENOENT (No such file or directory)`
     * An android app normally can't create the package data directory since its parent `/data/user/0`
     * is owned by `system` user and is normally created at app install or update time and not at app startup.
     *
     *
     * Note that the path returned by [Context.getFilesDir] may
     * be under `/data/user/[id]/[package_name]` instead of `/data/data/[package_name]`
     * defined by default by [TermuxConstants.TERMUX_FILES_DIR_PATH] where id will be 0 for
     * primary user and a higher number for other users/profiles. If app is running under work profile
     * or secondary user, then [TermuxConstants.TERMUX_FILES_DIR_PATH] will not be accessible
     * and will not be automatically created, unless there is a bind mount from `/data/data` to
     * `/data/user/[id]`, ideally in the right na[* https://source.android.com/devices/tech/](mespace.
      )admin/multi-user
     *
     *
     * On Android version `<=10`, the `/data/user/0` is a symlink to `/data/data[directory.
 * https://cs.android.com/android/platform/superproject/+/android-10.0.0_r47:system/core/r](`)ootdir/init.rc;l=589
     * `symlink /data/data /data/user/0
    ` *
     *
     *
     * `/system/bin/ls -lhd /data/data /data/user/0
     * drwxrwx--x 179 system system 8.0K 2021-xx-xx xx:xx /data/data
     * lrwxrwxrwx   1 root   root     10 2021-xx-xx xx:xx /data/user/0 -> /data/data
    ` *
     *
     *
     * On Android version `>=11`, the `/data/data` directory is bind mounted at `/d[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:system/core/r](ata/user/0`.
      )ootdir/i[* https://cs.android.com/android/_/android/platform/system/core/+/3cca270e95ca8d8bc8b8](nit.rc;l=705
      )00e2b5d7da1825fd7100
     * `# Unlink /data/user/0 if we previously symlink it to /data/data
     * rm /data/user/0
     * <p>
     * # Bind mount /data/user/0 to /data/data
     * mkdir /data/user/0 0700 system system encryption=None
     * mount none /data/data /data/user/0 bind rec
    ` *
     *
     *
     * `/system/bin/grep -E '( /data )|( /data/data )|( /data/user/[0-9]+ )' /proc/self/mountinfo 2>&1 | /system/bin/grep -v '/data_mirror' 2>&1
     * 87 32 253:5 / /data rw,nosuid,nodev,noatime shared:27 - ext4 /dev/block/dm-5 rw,seclabel,resgid=1065,errors=panic
     * 91 87 253:5 /data /data/user/0 rw,nosuid,nodev,noatime shared:27 - ext4 /dev/block/dm-5 rw,seclabel,resgid=1065,errors=panic
    ` *
     *
     *
     * The column 4 defines the root of the mount within the filesystem.
     * Basically, `/dev/block/dm-5/` is mounted at `/data` and `/dev/block/dm-5/data` is mounted at
     * [...]( `/data/user/0`.
      https://www.kernel.org/doc/Documentat)ion/filesystems/proc.t[(section 3.5)
 * https://www.kernel.org/doc/Documentation/files](xt)ystems/s[* https://unix.st](haredsubtree.txt
      )ackexchange.com/a/571959
     *
     *
     * Also note that running `/system/bin/ls -lhd /data/user/0/com.termux` as secondary user will result
     * in `ls: /data/user/0/com.termux: Permission denied` where `0` is primary user id but running
     * `/system/bin/ls -lhd /data/user/10/com.termux` will result in
     * `drwx------ 6 u10_a149 u10_a149 4.0K 2021-xx-xx xx:xx /data/user/10/com.termux` where `10` is
     * secondary user id. So can't stat directory (not contents) of primary user from secondary user
     * but can the other way around. However, this is happening on android 10 avd, but not on android
     * 11 avd.
     *
     * @param context                  The [Context] for operations.
     * @param createDirectoryIfMissing The `boolean` that decides if directory file
     * should be created if its missing.
     * @param setMissingPermissions    The `boolean` that decides if permissions are to be
     * automatically set.
     * @return Returns the `error` if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise `null`.
     */
    fun isTermuxFilesDirectoryAccessible(
        context: Context,
        createDirectoryIfMissing: Boolean,
        setMissingPermissions: Boolean
    ): Error? {
        if (createDirectoryIfMissing) context.filesDir
        if (directoryFileExists(TermuxConstants.TERMUX_FILES_DIR_PATH)) return FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.error
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
    ): Error? {
        return validateDirectoryFileExistenceAndPermissions(
            "termux prefix directory",
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
    ): Error? {
        return validateDirectoryFileExistenceAndPermissions(
            "termux prefix staging directory",
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
    ): Error? {
        return validateDirectoryFileExistenceAndPermissions(
            "apps/termux-app directory",
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

    val isTermuxPrefixDirectoryEmpty: Boolean
        /**
         * If [TermuxConstants.TERMUX_PREFIX_DIR_PATH] doesn't exist, is empty or only contains
         * files in [TermuxConstants.TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY].
         */
        get() {
            val error = validateDirectoryFileEmptyOrOnlyContainsSpecificFiles(
                "termux prefix",
                TERMUX_PREFIX_DIR_PATH,
                TermuxConstants.TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY,
                true
            )
            return error != null
        }
}
