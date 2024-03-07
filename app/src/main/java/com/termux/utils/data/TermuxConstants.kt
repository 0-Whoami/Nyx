package com.termux.utils.data

import android.content.Context
import android.graphics.Typeface
import java.io.File

/**
 * Termux app Files directory path
 */
// Default: "/data/data/com.termux/files"
const val TERMUX_FILES_DIR_PATH: String = "/data/data/com.termux/files"

/**
 * Termux app $PREFIX directory path
 */
// Default: "/data/data/com.termux/files/usr"
const val TERMUX_PREFIX_DIR_PATH: String = "$TERMUX_FILES_DIR_PATH/usr"

/**
 * Termux app $PREFIX directory
 */
val TERMUX_PREFIX_DIR: File by lazy { File(TERMUX_PREFIX_DIR_PATH) }


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
 * Termux app notification channel id used by [TERMUX_APP.TERMUX_SERVICE]
 */
const val TERMUX_APP_NOTIFICATION_CHANNEL_ID: String = "n_channel"

/**
 * Termux app unique notification id used by [TERMUX_APP.TERMUX_SERVICE]
 */
const val TERMUX_APP_NOTIFICATION_ID: Int = 4

const val ACTION_STOP_SERVICE: String = "stop"

var typeface: Typeface = Typeface.MONOSPACE
var italicTypeface: Typeface = typeface
var EXTRA_NORMAL_BACKGROUND: String = "$TERMUX_FILES_DIR_PATH/wallpaper.jpg"
var EXTRA_BLUR_BACKGROUND: String = "$TERMUX_FILES_DIR_PATH/wallpaperBlur.jpg"
var enableBlur: Boolean = true
var enableBorder: Boolean = true
fun loadConfigs(context: Context) {
    val sharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    EXTRA_NORMAL_BACKGROUND = sharedPreferences.getString("wallpaper", EXTRA_NORMAL_BACKGROUND)!!
    EXTRA_BLUR_BACKGROUND = sharedPreferences.getString("wallpaperBlur", EXTRA_BLUR_BACKGROUND)!!
    enableBlur = sharedPreferences.getBoolean("blur", true)
    enableBorder = sharedPreferences.getBoolean("border", true)

    if (sharedPreferences.getBoolean("customFont", false)) return
    typeface = Typeface.createFromFile(sharedPreferences.getString("font", null))
    italicTypeface = Typeface.createFromFile(sharedPreferences.getString("fontItalic", null))
}
