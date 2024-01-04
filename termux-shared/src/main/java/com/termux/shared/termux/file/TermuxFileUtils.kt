package com.termux.shared.termux.file;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.file.FileUtilsErrno;
import com.termux.shared.termux.TermuxConstants;

public class TermuxFileUtils {


    /**
     * Validate if {@link TermuxConstants#TERMUX_FILES_DIR_PATH} exists and has
     * {@link FileUtils#APP_WORKING_DIRECTORY_PERMISSIONS} permissions.
     * <p>
     * This is required because binaries compiled for termux are hard coded with
     * {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH} and the path must be accessible.
     * <p>
     * The permissions set to directory will be {@link FileUtils#APP_WORKING_DIRECTORY_PERMISSIONS}.
     * <p>
     * This function does not create the directory manually but by calling {@link Context#getFilesDir()}
     * so that android itself creates it. However, the call will not create its parent package
     * data directory `/data/user/0/[package_name]` if it does not already exist and a `logcat`
     * error will be logged by android.
     * {@code Failed to ensure /data/user/0/<package_name>/files: mkdir failed: ENOENT (No such file or directory)}
     * An android app normally can't create the package data directory since its parent `/data/user/0`
     * is owned by `system` user and is normally created at app install or update time and not at app startup.
     * <p>
     * Note that the path returned by {@link Context#getFilesDir()} may
     * be under `/data/user/[id]/[package_name]` instead of `/data/data/[package_name]`
     * defined by default by {@link TermuxConstants#TERMUX_FILES_DIR_PATH} where id will be 0 for
     * primary user and a higher number for other users/profiles. If app is running under work profile
     * or secondary user, then {@link TermuxConstants#TERMUX_FILES_DIR_PATH} will not be accessible
     * and will not be automatically created, unless there is a bind mount from `/data/data` to
     * `/data/user/[id]`, ideally in the right na<a href="mespace.
     ">* https://source.android.com/devices/tech/</a>admin/multi-user
     * <p>
     * On Android version `<=10`, the `/data/user/0` is a symlink to `/data/data<a href="`">directory.
     * https://cs.android.com/android/platform/superproject/+/android-10.0.0_r47:system/core/r</a>ootdir/init.rc;l=589
     * {@code
     * symlink /data/data /data/user/0
     * }
     *
     * {@code
     * /system/bin/ls -lhd /data/data /data/user/0
     * drwxrwx--x 179 system system 8.0K 2021-xx-xx xx:xx /data/data
     * lrwxrwxrwx   1 root   root     10 2021-xx-xx xx:xx /data/user/0 -> /data/data
     * }
     *
     * On Android version `>=11`, the `/data/data` directory is bind mounted at `/d<a href="ata/user/0`.
     ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:system/core/r</a>ootdir/i<a href="nit.rc;l=705
     ">* https://cs.android.com/android/_/android/platform/system/core/+/3cca270e95ca8d8bc8b8</a>00e2b5d7da1825fd7100
     * {@code
     * # Unlink /data/user/0 if we previously symlink it to /data/data
     * rm /data/user/0
     * <p>
     * # Bind mount /data/user/0 to /data/data
     * mkdir /data/user/0 0700 system system encryption=None
     * mount none /data/data /data/user/0 bind rec
     * }
     *
     * {@code
     * /system/bin/grep -E '( /data )|( /data/data )|( /data/user/[0-9]+ )' /proc/self/mountinfo 2>&1 | /system/bin/grep -v '/data_mirror' 2>&1
     * 87 32 253:5 / /data rw,nosuid,nodev,noatime shared:27 - ext4 /dev/block/dm-5 rw,seclabel,resgid=1065,errors=panic
     * 91 87 253:5 /data /data/user/0 rw,nosuid,nodev,noatime shared:27 - ext4 /dev/block/dm-5 rw,seclabel,resgid=1065,errors=panic
     * }
     *
     * The column 4 defines the root of the mount within the filesystem.
     * Basically, `/dev/block/dm-5/` is mounted at `/data` and `/dev/block/dm-5/data` is mounted at
     *<a href=" `/data/user/0`.
     * https://www.kernel.org/doc/Documentat">...</a>ion/filesystems/proc.t<a href="xt">(section 3.5)
     * https://www.kernel.org/doc/Documentation/files</a>ystems/s<a href="haredsubtree.txt
     ">* https://unix.st</a>ackexchange.com/a/571959
     * <p>
     * Also note that running `/system/bin/ls -lhd /data/user/0/com.termux` as secondary user will result
     * in `ls: /data/user/0/com.termux: Permission denied` where `0` is primary user id but running
     * `/system/bin/ls -lhd /data/user/10/com.termux` will result in
     * `drwx------ 6 u10_a149 u10_a149 4.0K 2021-xx-xx xx:xx /data/user/10/com.termux` where `10` is
     * secondary user id. So can't stat directory (not contents) of primary user from secondary user
     * but can the other way around. However, this is happening on android 10 avd, but not on android
     * 11 avd.
     *
     * @param context The {@link Context} for operations.
     * @param createDirectoryIfMissing The {@code boolean} that decides if directory file
     *                                 should be created if its missing.
     * @param setMissingPermissions The {@code boolean} that decides if permissions are to be
     *                              automatically set.
     * @return Returns the {@code error} if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise {@code null}.
     */
    public static Error isTermuxFilesDirectoryAccessible(@NonNull final Context context, boolean createDirectoryIfMissing, boolean setMissingPermissions) {
        if (createDirectoryIfMissing)
            context.getFilesDir();
        if (FileUtils.directoryFileExists(TermuxConstants.TERMUX_FILES_DIR_PATH, true))
            return FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError("termux files directory", TermuxConstants.TERMUX_FILES_DIR_PATH);
        if (setMissingPermissions)
            FileUtils.setMissingFilePermissions(TermuxConstants.TERMUX_FILES_DIR_PATH, FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS);
        return FileUtils.checkMissingFilePermissions("termux files directory", TermuxConstants.TERMUX_FILES_DIR_PATH, FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, false);
    }

    /**
     * Validate if {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH} exists and has
     * {@link FileUtils#APP_WORKING_DIRECTORY_PERMISSIONS} permissions.
     * .
     * <p>
     * The {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH} directory would not exist if termux has
     * not been installed or the bootstrap setup has not been run or if it was deleted by the user.
     *
     * @param createDirectoryIfMissing The {@code boolean} that decides if directory file
     *                                 should be created if its missing.
     * @param setMissingPermissions The {@code boolean} that decides if permissions are to be
     *                              automatically set.
     * @return Returns the {@code error} if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise {@code null}.
     */
    public static Error isTermuxPrefixDirectoryAccessible(boolean createDirectoryIfMissing, boolean setMissingPermissions) {
        return FileUtils.validateDirectoryFileExistenceAndPermissions("termux prefix directory", TermuxConstants.TERMUX_PREFIX_DIR_PATH, null, createDirectoryIfMissing, FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, setMissingPermissions, true, false, false);
    }

    /**
     * Validate if {@link TermuxConstants#TERMUX_STAGING_PREFIX_DIR_PATH} exists and has
     * {@link FileUtils#APP_WORKING_DIRECTORY_PERMISSIONS} permissions.
     *
     * @param createDirectoryIfMissing The {@code boolean} that decides if directory file
     *                                 should be created if its missing.
     * @param setMissingPermissions The {@code boolean} that decides if permissions are to be
     *                              automatically set.
     * @return Returns the {@code error} if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise {@code null}.
     */
    public static Error isTermuxPrefixStagingDirectoryAccessible(boolean createDirectoryIfMissing, boolean setMissingPermissions) {
        return FileUtils.validateDirectoryFileExistenceAndPermissions("termux prefix staging directory", TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH, null, createDirectoryIfMissing, FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, setMissingPermissions, true, false, false);
    }

    /**
     * Validate if {@link TermuxConstants.TERMUX_APP#APPS_DIR_PATH} exists and has
     * {@link FileUtils#APP_WORKING_DIRECTORY_PERMISSIONS} permissions.
     *
     * @param createDirectoryIfMissing The {@code boolean} that decides if directory file
     *                                 should be created if its missing.
     * @param setMissingPermissions The {@code boolean} that decides if permissions are to be
     *                              automatically set.
     * @return Returns the {@code error} if path is not a directory file, failed to create it,
     * or validating permissions failed, otherwise {@code null}.
     */
    public static Error isAppsTermuxAppDirectoryAccessible(boolean createDirectoryIfMissing, boolean setMissingPermissions) {
        return FileUtils.validateDirectoryFileExistenceAndPermissions("apps/termux-app directory", TermuxConstants.TERMUX_APP.APPS_DIR_PATH, null, createDirectoryIfMissing, FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, setMissingPermissions, true, false, false);
    }

    /**
     * If {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH} doesn't exist, is empty or only contains
     * files in {@link TermuxConstants#TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY}.
     */
    public static boolean isTermuxPrefixDirectoryEmpty() {
        Error error = FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFiles("termux prefix", TERMUX_PREFIX_DIR_PATH, TermuxConstants.TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY, true);
        return error != null;
    }

}
