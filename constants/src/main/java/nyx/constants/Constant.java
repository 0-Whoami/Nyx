package nyx.constants;

public final class Constant {
    public static final String KEY_TRANSCRIPT_ROWS = "transcript_rows";
    public static final String KEY_COLOR_PRIMARY = "primary_color";
    public static final String KEY_COLOR_SECONDARY = "secondary_color";
    public static final String KEY_CORNER_RADIUS = "corner_radius";
    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_ENABLE_BORDER = "enable_border";
    public static final String KEY_BLUR_ENABLED = "enable_blur";
    private static final String FILES_DIR_PATH = "/data/data/com.termux/files";
    public static final String USR_DIR = FILES_DIR_PATH + "/usr";
    private static final String CONFIG_PATH = FILES_DIR_PATH + "/home/.termux";
    public static final String EXTRA_NORMAL_BACKGROUND = CONFIG_PATH + "/wallpaper.jpg";
    public static final String EXTRA_BLUR_BACKGROUND = CONFIG_PATH + "/wallpaperBlur.jpg";
    public static final String EXTRA_FONT = CONFIG_PATH + "/font.ttf";
    public static final String EXTRA_CONFIG = CONFIG_PATH + "/config";
    public static final String EXTRA_COLORS_CONFIG = CONFIG_PATH + "/colors";
    public static final String EXTRA_KEYS_CONFIG = CONFIG_PATH + "/extrakeys";

}