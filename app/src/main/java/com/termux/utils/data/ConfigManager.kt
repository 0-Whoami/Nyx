package com.termux.utils.data

import android.content.Context
import android.graphics.Typeface
import java.io.File

object ConfigManager {
    /**
     * Termux app Files directory path
     */
// Default: "/data/data/com.termux/files"
    private const val FILES_DIR_PATH: String = "/data/data/com.termux/files"

    /**
     * Termux app $PREFIX directory
     */
    val PREFIX_DIR: File by lazy { File("$FILES_DIR_PATH/usr") }

    /**
     * Termux app notification channel id used by
     */
    const val CHANNEL_ID: String = "n_channel"

    /**
     * Termux app unique notification id
     */
    const val NOTIFICATION_ID: Int = 4

    const val ACTION_STOP_SERVICE: String = "stop"

    var typeface: Typeface = Typeface.MONOSPACE
    var italicTypeface: Typeface = typeface
    var EXTRA_NORMAL_BACKGROUND: String = "$FILES_DIR_PATH/wallpaper.jpg"
    var EXTRA_BLUR_BACKGROUND: String = "$FILES_DIR_PATH/wallpaperBlur.jpg"
    var enableBlur: Boolean = false
    var enableBackground = false
    var enableBorder: Boolean = false
    fun loadConfigs(context: Context) {
        val sharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("enableConfig", true)) return
        EXTRA_NORMAL_BACKGROUND =
            sharedPreferences.getString("wallpaper", EXTRA_NORMAL_BACKGROUND)!!
        EXTRA_BLUR_BACKGROUND =
            sharedPreferences.getString("wallpaperBlur", EXTRA_BLUR_BACKGROUND)!!
        enableBlur = sharedPreferences.getBoolean("blur", true)
        enableBorder = sharedPreferences.getBoolean("border", true)
        enableBackground = sharedPreferences.getBoolean("background", true)

        if (sharedPreferences.getBoolean("customFont", false)) return
        try {
            typeface = Typeface.createFromFile(sharedPreferences.getString("font", null))
            italicTypeface =
                Typeface.createFromFile(sharedPreferences.getString("fontItalic", null))
        } catch (e: Exception) {
            typeface = Typeface.MONOSPACE
            italicTypeface = typeface
        }
    }
}
