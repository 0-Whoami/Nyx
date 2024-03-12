package com.termux.utils.data

import android.graphics.Typeface
import android.view.KeyEvent
import com.termux.terminal.TerminalColorScheme
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

object ConfigManager {
    /**
     * Termux app Files directory path
     */
// Default: "/data/data/com.termux/files"
    private const val FILES_DIR_PATH = "/data/data/com.termux/files"
    private const val CONFIG_PATH = "$FILES_DIR_PATH/.nyx"

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

    var typeface: Typeface = Typeface.MONOSPACE
    var italicTypeface: Typeface = typeface
    const val EXTRA_NORMAL_BACKGROUND = "$CONFIG_PATH/wallpaper.jpg"
    const val EXTRA_BLUR_BACKGROUND = "$CONFIG_PATH/wallpaperBlur.jpg"
    val keyLabel = mutableListOf("C", "A", "S", "⌫")
    val keys = mutableListOf(KeyEvent.KEYCODE_DEL)
    var enableBlur = true
    var enableBackground = true
    var enableBorder = true
    fun loadConfigs() {
        loadBool()
        loadFonts()
        loadColors()
        loadKeys()
    }

    private fun loadBool() {
        val settingsMap = try {
            ObjectInputStream(FileInputStream("$CONFIG_PATH/config")).use { it.readObject() as Map<String, Boolean> }
        } catch (e: Exception) {
            return
        }
        enableBorder = settingsMap["border"] ?: true
        enableBlur = settingsMap["blur"] ?: true
        enableBackground = settingsMap["background"] ?: true
    }

    private fun loadKeys() {
        val keysMap = try {
            ObjectInputStream(FileInputStream("$CONFIG_PATH/keys")).use { it.readObject() as Map<String, Int> }
        } catch (e: Exception) {
            return
        }
        keysMap.forEach { (key, value) ->
            keyLabel.add(key)
            keys.add(value)
        }
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
        try {
            typeface = Typeface.createFromFile("$CONFIG_PATH/font.ttf")
            italicTypeface = Typeface.createFromFile("fonts/italic.ttf")
        } catch (_: Exception) {
        }
    }

}
