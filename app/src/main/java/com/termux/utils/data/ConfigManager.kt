package com.termux.utils.data

import android.graphics.Typeface
import android.view.KeyEvent
import com.termux.terminal.TerminalColorScheme
import com.termux.utils.data.ConfigManager.CONFIG_PATH
import org.json.JSONObject
import java.io.File

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
    var typeface: Typeface = try {
        Typeface.createFromFile("$CONFIG_PATH/font.ttf")
    } catch (e: Exception) {
        Typeface.MONOSPACE
    }
    var italicTypeface: Typeface = try {
        Typeface.createFromFile("$CONFIG_PATH/italic.ttf")
    } catch (e: Exception) {
        typeface
    }
}

object ConfigManager {
    /**
     * Termux app Files directory path
     */
    // Default: "/data/data/com.termux/files"
    const val FILES_DIR_PATH = "/data/data/com.termux/files"
    const val CONFIG_PATH: String = "$FILES_DIR_PATH/.nyx"

    var font_size: Int = 14

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
                val jsonObject = JSONObject()
                jsonObject.put("⌫", KeyEvent.KEYCODE_DEL)
                this.writeText(jsonObject.toString())
            }
        }
    }

    private fun loadBool() {
        val properties = Properties("$CONFIG_PATH/config")
        font_size = properties.getInt("font_size", 14)
        enableBlur = properties.getBoolean("blur", true)
        enableBorder = properties.getBoolean("border", true)
    }


    private fun loadColors() {
        val properties = Properties("$CONFIG_PATH/colors")
        properties.forEach { index1, value ->
            val index = index1.toInt()
            val color = value.toInt()
            TerminalColorScheme.DEFAULT_COLORSCHEME[index] = color
        }
    }


}
