package com.termux.data;

import static java.lang.Math.max;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.KeyEvent;

import com.termux.terminal.TerminalColorScheme;
import com.termux.utils.Theme;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public enum ConfigManager {
    ;
    /**
     * Termux app Files directory path
     */ // Default: "/data/data/com.termux/files"
    public static final String FILES_DIR_PATH = "/data/data/com.termux/files";
    public static final String CONFIG_PATH = ConfigManager.FILES_DIR_PATH + "/home/.termux";
    public static final String EXTRA_NORMAL_BACKGROUND = ConfigManager.CONFIG_PATH + "/wallpaper.jpg";
    public static final String EXTRA_BLUR_BACKGROUND = ConfigManager.CONFIG_PATH + "/wallpaperBlur.jpg";
    public static int transcriptRows = 100;
    public static Typeface typeface = Typeface.MONOSPACE;

    public static void loadConfigs() {
        try {
            ConfigManager.typeface = Typeface.createFromFile(ConfigManager.CONFIG_PATH + "/font.ttf");

        } catch (Throwable ignored) {
        }
        ConfigManager.loadProp();
        ConfigManager.loadColors();
        try {
            File file = new File(ConfigManager.CONFIG_PATH + "/keys");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write("⌫ : " + KeyEvent.KEYCODE_DEL);
                fw.close();
            }
        } catch (IOException e) {
        }
    }

    private static void loadProp() {
        Properties properties = new Properties(ConfigManager.CONFIG_PATH + "/config");
        ConfigManager.transcriptRows = max(50, properties.getInt("transcript_rows", ConfigManager.transcriptRows));
        try {
            Theme.setPrimary(Color.parseColor(properties.get("color", "#f0f")));
        } catch (Throwable ignored) {
        }
    }

    private static void loadColors() {
        try {
            new Properties(ConfigManager.CONFIG_PATH + "/colors").forEach((i, val) -> TerminalColorScheme.DEFAULT_COLORSCHEME[Integer.parseInt(i)] = Color.parseColor(val));
        } catch (Throwable ignored) {
        }
    }

}
