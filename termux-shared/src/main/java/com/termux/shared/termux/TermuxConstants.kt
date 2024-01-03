package com.termux.shared.termux

import java.io.File

/**
 * A class that defines shared constants of the Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 *
 *
 * Termux app default package name is "com.termux" and is used in [.TERMUX_PREFIX_DIR_PATH].
 * The binaries compiled for termux have [.TERMUX_PREFIX_DIR_PATH] hardcoded in them but it
 * can be changed during compilation.
 *
 *
 * The [.TERMUX_PACKAGE_NAME] must be the same as the applicationId of termux-app build.gradle
 * since its also used by [.TERMUX_FILES_DIR_PATH].
 * If [.TERMUX_PACKAGE_NAME] is changed, then binaries, specially used in bootstrap need to be
 * compiled appropriately[Check https://github.com/termux/termux-packages/wiki/Building-](.)packages
 * for more info.
 *
 *
 * Ideally the only places where changes should be required if changing package name are the following:
 * - The [.TERMUX_PACKAGE_NAME] in [TermuxConstants].
 * - The "applicationId" in "build.gradle" of termux-app. This is package name that android and app
 * stores will use and is also the final package name stored in "AndroidManifest.xml".
 * - The "manifestPlaceholders" values for [.TERMUX_PACKAGE_NAME] and *_APP_NAME in
 * "build.gradle" of termux-app.
 * - The "ENTITY" values for [.TERMUX_PACKAGE_NAME] and *_APP_NAME in "strings.xml" of
 * termux-app and of termux-shared.
 * - The "shortcut.xml" and "*_preferences.xml" files of termux-app since dynamic variables don't
 * work in it.
 * - Optionally the "package" in "AndroidManifest.xml" if modifying project structure of termux-app.
 * This is package name for java classes project structure and is prefixed if activity and service
 * names use dot (.) notation. This is currently not advisable since this will break lot of
 * stuff, including termux-* packages.
 * - Optionally the *_PATH variables in [TermuxConstants] containing the string "termux".
 * [...]( * <p>
  Check https://developer.android.com/studio/buil)d/application-id for info on "package" in
 * "AndroidManifest.xml" and "applicationId" in "build.gradle".
 *
 *
 * The [.TERMUX_PACKAGE_NAME] must be used in source code of Termux app and its plugins instead
 * of hardcoded "com.termux" paths.
 */
object TermuxConstants {
    /*
     * Termux organization variables.
     */
    /**
     * Termux app name
     */
    // Default: "Termux"
    const val TERMUX_APP_NAME: String = "Termux"

    /**
     * Termux package name
     */
    // Default: "com.termux"
    const val TERMUX_PACKAGE_NAME: String = "com.termux"

    /**
     * Termux app internal private app data directory path
     */
    private const val  // Default: "/data/data/com.termux"
        TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH: String = "/data/data/$TERMUX_PACKAGE_NAME"

    /**
     * Termux app Files directory path
     */
    // Default: "/data/data/com.termux/files"
    const val TERMUX_FILES_DIR_PATH: String = "$TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH/files"

    /**
     * Termux app $PREFIX directory path
     */
    // Default: "/data/data/com.termux/files/usr"
    const val TERMUX_PREFIX_DIR_PATH: String = "$TERMUX_FILES_DIR_PATH/usr"

    /**
     * Termux app $PREFIX directory
     */
    val TERMUX_PREFIX_DIR: File by lazy { File(TERMUX_PREFIX_DIR_PATH) }

    /**
     * Termux app $PREFIX/bin directory path
     */
    // Default: "/data/data/com.termux/files/usr/bin"
    const val TERMUX_BIN_PREFIX_DIR_PATH: String = "$TERMUX_PREFIX_DIR_PATH/bin"

    /**
     * Termux app $PREFIX/etc directory path
     */
    // Default: "/data/data/com.termux/files/usr/etc"
    private const val TERMUX_ETC_PREFIX_DIR_PATH: String = "$TERMUX_PREFIX_DIR_PATH/etc"

    /**
     * Termux app config $PREFIX directory path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux"
    private const val TERMUX_CONFIG_PREFIX_DIR_PATH: String = "$TERMUX_ETC_PREFIX_DIR_PATH/termux"

    /**
     * Termux app environment file path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env"
    const val TERMUX_ENV_FILE_PATH: String = "$TERMUX_CONFIG_PREFIX_DIR_PATH/termux.env"

    /**
     * Termux app environment temp file path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env.tmp"
    const val TERMUX_ENV_TEMP_FILE_PATH: String = "$TERMUX_CONFIG_PREFIX_DIR_PATH/termux.env.tmp"

    /**
     * Termux app $PREFIX/tmp and $TMPDIR directory path
     */
    // Default: "/data/data/com.termux/files/usr/tmp"
    const val TERMUX_TMP_PREFIX_DIR_PATH: String = "$TERMUX_PREFIX_DIR_PATH/tmp"

    /**
     * Termux app $PREFIX directory path ignored sub file paths to consider it empty
     */
    @JvmField
    val TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY: List<String> =
        arrayListOf(
            TERMUX_TMP_PREFIX_DIR_PATH, TERMUX_ENV_TEMP_FILE_PATH, TERMUX_ENV_FILE_PATH
        )

    /**
     * Termux app usr-staging directory path
     */
    // Default: "/data/data/com.termux/files/usr-staging"
    const val TERMUX_STAGING_PREFIX_DIR_PATH: String = "$TERMUX_FILES_DIR_PATH/usr-staging"

    /**
     * Termux app usr-staging directory
     */
    val TERMUX_STAGING_PREFIX_DIR: File by lazy { File(TERMUX_STAGING_PREFIX_DIR_PATH) }

    // Default: "/data/data/com.termux/files/home/.termux/background/background_portrait.jpeg"
    /// public static final String TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH = TERMUX_BACKGROUND_DIR_PATH + "/background_portrait.jpeg";
    /**
     * Termux app $HOME directory path
     */
    // Default: "/data/data/com.termux/files/home"
    const val TERMUX_HOME_DIR_PATH: String = "$TERMUX_FILES_DIR_PATH/home"

    /**
     * Termux app storage home directory path
     */
    // Default: "/data/data/com.termux/files/home/storage"
    private const val TERMUX_STORAGE_HOME_DIR_PATH: String = "$TERMUX_HOME_DIR_PATH/storage"

    /**
     * Termux app storage home directory
     */
    val TERMUX_STORAGE_HOME_DIR: File by lazy { File(TERMUX_STORAGE_HOME_DIR_PATH) }

    /**
     * Termux and plugin apps directory path
     */
    // Default: "/data/data/com.termux/files/apps"
    const val TERMUX_APPS_DIR_PATH: String = "$TERMUX_FILES_DIR_PATH/apps"

    /**
     * Termux app notification channel id used by [TERMUX_APP.TERMUX_SERVICE]
     */
    const val TERMUX_APP_NOTIFICATION_CHANNEL_ID: String = "termux_notification_channel"

    /**
     * Termux app notification channel name used by [TERMUX_APP.TERMUX_SERVICE]
     */
    const val TERMUX_APP_NOTIFICATION_CHANNEL_NAME: String = "$TERMUX_APP_NAME App"

    /**
     * Termux app unique notification id used by [TERMUX_APP.TERMUX_SERVICE]
     */
    const val TERMUX_APP_NOTIFICATION_ID: Int = 1337

    /**
     * Environment variable prefix root for the Termux app.
     */
    const val TERMUX_ENV_PREFIX_ROOT: String = "TERMUX"

    /**
     * Termux app constants.
     */
    object TERMUX_APP {
        /**
         * Termux apps directory path
         */
        // Default: "/data/data/com.termux/files/apps/com.termux"
        const val APPS_DIR_PATH: String = "$TERMUX_APPS_DIR_PATH/$TERMUX_PACKAGE_NAME"

        // Default: "com.termux.app.TermuxActivity"
        //public static final String TERMUX_ACTIVITY_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxActivity";
        /**
         * Termux app core activity.
         */
        object TERMUX_ACTIVITY {
            /**
             * Intent extra for if termux failsafe session needs to be started and is used by [TERMUX_ACTIVITY] and [TERMUX_SERVICE.ACTION_STOP_SERVICE]
             */
            // Default: "com.termux.app.failsafe_session"
            const val EXTRA_FAILSAFE_SESSION: String = "failsafe"
            const val EXTRA_PHONE_LISTENER: String = "con"
            const val EXTRA_NORMAL_BACKGROUND: String =
                "$TERMUX_HOME_DIR_PATH/wear/wallpaper.jpeg"
            const val EXTRA_BLUR_BACKGROUND: String =
                "$TERMUX_HOME_DIR_PATH/wear/wallpaperBlur.jpeg"
        }

        /**
         * Termux app core service.
         */
        object TERMUX_SERVICE {
            /**
             * Intent action to stop TERMUX_SERVICE
             */
            // Default: "com.termux.service_stop"
            const val ACTION_STOP_SERVICE: String = "stop"
        }
    }
}
