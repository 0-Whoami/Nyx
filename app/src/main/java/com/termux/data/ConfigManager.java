package com.termux.data;

import static java.lang.Math.max;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.KeyEvent;

import com.termux.terminal.TerminalColorScheme;
import com.termux.utils.Theme;

import java.io.File;
import java.io.FileWriter;


public final class ConfigManager {
    /**
     * Termux app Files directory path
     */ // Default: "/data/data/com.termux/files"
    public static final String FILES_DIR_PATH = "/data/data/com.termux/files";
    public static final String CONFIG_PATH = FILES_DIR_PATH + "/home/.termux";
    public static final String EXTRA_NORMAL_BACKGROUND = CONFIG_PATH + "/wallpaper.jpg";
    public static final String EXTRA_BLUR_BACKGROUND = CONFIG_PATH + "/wallpaperBlur.jpg";
    public static int transcriptRows = 100;
    public static Typeface typeface = Typeface.MONOSPACE;

    public static void loadConfigs() {
        try {
            typeface = Typeface.createFromFile(CONFIG_PATH + "/font.ttf");
            File file = new File(CONFIG_PATH + "/keys");
            if (file.exists()) {
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write("⌫ : " + KeyEvent.KEYCODE_DEL);
                fw.close();
            }
        } catch (Throwable ignored) {
        }
        loadProp();
        loadColors();
    }

    private static void loadProp() {
        Properties properties = new Properties(CONFIG_PATH + "/config");
        transcriptRows = max(50, properties.getInt("transcript_rows", transcriptRows));
        try {
            Theme.setPrimary(Color.parseColor(properties.get("color", "#fff")));
        } catch (Throwable ignored) {
        }
    }

    private static void loadColors() {
        try {
            new Properties(CONFIG_PATH + "/colors").forEach((i, val) -> TerminalColorScheme.DEFAULT_COLORSCHEME[Integer.parseInt(i)] = Color.parseColor(val));
        } catch (Throwable ignored) {
        }
    }

}
