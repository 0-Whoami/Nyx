package com.termux.data

import android.graphics.Color
import android.graphics.Typeface
import android.view.KeyEvent
import com.termux.terminal.TerminalColorScheme
import com.termux.utils.primary
import java.io.File
import kotlin.math.max


object RENDERING {
    const val PADDING : Float = 5f
}

object ConfigManager {
    /**
     * Termux app Files directory path
     */ // Default: "/data/data/com.termux/files"
    const val FILES_DIR_PATH : String = "/data/data/com.termux/files"
    const val CONFIG_PATH : String = "$FILES_DIR_PATH/home/.termux"

    var font_size : Int = 14

    const val EXTRA_NORMAL_BACKGROUND : String = "$CONFIG_PATH/wallpaper.jpg"
    const val EXTRA_BLUR_BACKGROUND : String = "$CONFIG_PATH/wallpaperBlur.jpg"

    var enableBlur : Boolean = true
    var enableBorder : Boolean = true
    var cornerRadius : Int = 5
    var transcriptRows : Int = 100
    var typeface : Typeface = Typeface.MONOSPACE
    fun loadConfigs() {
        try {
            typeface = Typeface.createFromFile("$CONFIG_PATH/font.ttf")
        } catch (_ : Throwable) {
        }
        loadProp()
        loadColors()
        with(File("$CONFIG_PATH/keys")) {
            if (!this.exists()) {
                this.parentFile?.mkdirs()
                this.writeText("⌫ : ${KeyEvent.KEYCODE_DEL}")
            }
        }
    }

    private fun loadProp() {
        val properties = Properties("$CONFIG_PATH/config")
        font_size = properties.getInt("font_size", font_size)
        enableBlur = properties.getBoolean("blur", enableBlur)
        enableBorder = properties.getBoolean("border", enableBorder)
        transcriptRows = max(50, properties.getInt("transcript_rows", transcriptRows))
        cornerRadius = properties.getInt("corner_radius", cornerRadius)
        primary = try {
            Color.parseColor(properties.get("color"))
        } catch (_ : Throwable) {
            primary
        }
    }

    private fun loadColors() {
        val properties = Properties("$CONFIG_PATH/colors")
        properties.forEach { index1, value ->
            try {
                TerminalColorScheme.DEFAULT_COLORSCHEME[index1.toInt()] = Color.parseColor(value)
            } catch (_ : Throwable) {
            }
        }
    }

}
