package com.termux.utils.data

import android.graphics.Typeface
import android.view.KeyEvent
import com.termux.terminal.TerminalColorScheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object ConfigManager {
    /**
     * Termux app Files directory path
     */
// Default: "/data/data/com.termux/files"
    private const val FILES_DIR_PATH = "/data/data/com.termux/files"
    const val CONFIG_PATH: String = "$FILES_DIR_PATH/.nyx"


    /**
     * Termux app $PREFIX directory
     */
    val PREFIX_DIR: File = File("$FILES_DIR_PATH/usr")


    /**
     * Termux app notification channel id used by
     */
    const val CHANNEL_ID: String = "n_channel"

    /**
     * Termux app unique notification id
     */
    const val NOTIFICATION_ID: Int = 4

    const val ACTION_STOP_SERVICE: String = "stop"

    var padding_left: Float = 5f
    var padding_top: Float = padding_left
    var padding_right: Float = padding_left
    var padding_bottom: Float = padding_left
    var font_size: Int = 14
    var typeface: Typeface = Typeface.MONOSPACE
    var italicTypeface: Typeface = typeface
    const val EXTRA_NORMAL_BACKGROUND: String = "$CONFIG_PATH/wallpaper.jpg"
    const val EXTRA_BLUR_BACKGROUND: String = "$CONFIG_PATH/wallpaperBlur.jpg"
    var enableBlur: Boolean = true
    var enableBorder: Boolean = true
    fun loadConfigs() {
        loadValues()
        loadFonts()
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

    private fun loadValues() {
        val settingsMap = try {
            ObjectInputStream(FileInputStream("$CONFIG_PATH/configConsole")).use { it.readObject() as Map<String, Any> }
        } catch (e: Exception) {
            return
        }
        padding_left = (settingsMap["padding_left"] ?: padding_left) as Float
        padding_top = (settingsMap["padding_top"] ?: padding_left) as Float
        padding_right = (settingsMap["padding_right"] ?: padding_left) as Float
        padding_bottom = (settingsMap["padding_bottom"] ?: padding_left) as Float
        font_size = (settingsMap["font_size"] ?: font_size) as Int
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

    private fun loadFonts() {
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
