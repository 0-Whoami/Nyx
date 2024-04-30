package com.termux.utils.data

import android.graphics.Typeface
import android.view.KeyEvent
import com.termux.terminal.TerminalColorScheme
import com.termux.utils.data.ConfigManager.CONFIG_PATH
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object NYX_SERVICE {
    /**
     * Termux app notification channel id used by
     */
    const val CHANNEL_ID: String = "n_channel"

    /**
     * Termux app unique notification id
     */
    const val NOTIFICATION_ID: Int = 4

    const val ACTION_STOP_SERVICE: String = "s"
}

object RENDERING {
    const val padding: Float = 5f
    var font_size: Int = 14
    var typeface: Typeface
    var italicTypeface: Typeface

    init {
        val settingsMap = try {
            ObjectInputStream(FileInputStream("$CONFIG_PATH/configConsole")).use { it.readObject() as Map<String, Int> }
        } catch (e: Exception) {
            mapOf()
        }
        font_size = (settingsMap["font_size"] ?: font_size)
        typeface = try {
            Typeface.createFromFile("$CONFIG_PATH/font.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
        italicTypeface = try {
            Typeface.createFromFile("$CONFIG_PATH/italic.ttf")
        } catch (e: Exception) {
            typeface
        }
    }
}

object ConfigManager {
    /**
     * Termux app Files directory path
     */
    // Default: "/data/data/com.termux/files"
    const val FILES_DIR_PATH = "/data/data/com.termux/files"
    const val CONFIG_PATH: String = "$FILES_DIR_PATH/.nyx"


    const val EXTRA_NORMAL_BACKGROUND: String = "$CONFIG_PATH/wallpaper.jpg"
    const val EXTRA_BLUR_BACKGROUND: String = "$CONFIG_PATH/wallpaperBlur.jpg"
    var enableBlur: Boolean = true
    var enableBorder: Boolean = true
    fun loadConfigs() {
        loadBool()
        loadColors()
        with(File("$CONFIG_PATH/keys")) {
            if (!this.exists()) {
                this.parentFile?.mkdirs()
                ObjectOutputStream(FileOutputStream(this)).use {
                    it.writeObject(mapOf("⌫" to KeyEvent.KEYCODE_DEL))
                }
            }
        }
    }

    private fun loadBool() {
        val settingsMap = try {
            ObjectInputStream(FileInputStream("$CONFIG_PATH/config")).use { it.readObject() as Map<String, Boolean> }
        } catch (e: Exception) {
            return
        }
        enableBorder = (settingsMap["border"] ?: true)
        enableBlur = (settingsMap["blur"] ?: true)
    }


    private fun loadColors() {
        val colorsMap = try {
            ObjectInputStream(FileInputStream("$CONFIG_PATH/colors")).use { it.readObject() as Map<Int, Int> }
        } catch (e: Exception) {
            return
        }
        colorsMap.forEach { (key, value) ->
            TerminalColorScheme.DEFAULT_COLORSCHEME[key] = value
        }
    }


}
