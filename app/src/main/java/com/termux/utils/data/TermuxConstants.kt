package com.termux.utils.data

import java.io.File

object TermuxConstants {
    /**
     * Termux package name
     */
    // Default: "com.termux"
    const val TERMUX_PACKAGE_NAME: String = "com.termux"

    /**
     * Termux app Files directory path
     */
    // Default: "/data/data/com.termux/files"
    const val TERMUX_FILES_DIR_PATH: String = "/data/data/$TERMUX_PACKAGE_NAME/files"

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
     * Termux app $PREFIX/tmp and $TMPDIR directory path
     */
    // Default: "/data/data/com.termux/files/usr/tmp"
    val TERMUX_TMP_PREFIX_DIR_PATH: String by lazy { "$TERMUX_PREFIX_DIR_PATH/tmp" }


    /**
     * Termux app usr-staging directory path
     */
    // Default: "/data/data/com.termux/files/usr-staging"
    val TERMUX_STAGING_PREFIX_DIR_PATH: String by lazy { "$TERMUX_FILES_DIR_PATH/usr-staging" }

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
     * Termux app unique notification id used by [TERMUX_APP.TERMUX_SERVICE]
     */
    const val TERMUX_APP_NOTIFICATION_ID: Int = 1337

    /**
     * Termux apps directory path
     */
    // Default: "/data/data/com.termux/files/apps/com.termux"
    val APPS_DIR_PATH: String by lazy { "$TERMUX_APPS_DIR_PATH/$TERMUX_PACKAGE_NAME" }
    const val EXTRA_NORMAL_BACKGROUND: String = "$TERMUX_HOME_DIR_PATH/wear/wallpaper.jpg"
    const val EXTRA_BLUR_BACKGROUND: String = "$TERMUX_HOME_DIR_PATH/wear/wallpaperBlur.jpg"
    const val ACTION_STOP_SERVICE: String = "stop"

}
