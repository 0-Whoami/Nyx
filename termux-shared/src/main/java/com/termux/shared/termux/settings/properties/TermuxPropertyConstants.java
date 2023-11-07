package com.termux.shared.termux.settings.properties;

import com.google.common.collect.ImmutableBiMap;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class that defines shared constants of the SharedProperties used by Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 * <p>
 * The properties are loaded from the first file found at
 * {@link TermuxConstants#TERMUX_PROPERTIES_PRIMARY_FILE_PATH} or
 * {@link TermuxConstants#TERMUX_PROPERTIES_SECONDARY_FILE_PATH}
 */
public final class TermuxPropertyConstants {

    /**
     * Defines the key for whether a toast will be shown when user changes the terminal session
     */
    // Default: "disable-terminal-session-change-toast"
    public static final String KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST = "disable-terminal-session-change-toast";

    /**
     * Defines the key for whether to enforce character based input to fix the issue where for some devices like Samsung, the letters might not appear until enter is pressed
     */
    // Default: "enforce-char-based-input"
    public static final String KEY_ENFORCE_CHAR_BASED_INPUT = "enforce-char-based-input";

    /**
     * Defines the key for whether the {@link TermuxAmSocketServer} should be run at app startup
     */
    // Default: "run-termux-am-socket-server"
    public static final String KEY_RUN_TERMUX_AM_SOCKET_SERVER = "run-termux-am-socket-server";

    /**
     * Defines the key for whether url links in terminal transcript will automatically open on click or on tap
     */
    // Default: "terminal-onclick-url-open"
    public static final String KEY_TERMINAL_ONCLICK_URL_OPEN = "terminal-onclick-url-open";

    /**
     * Defines the key for whether to use bright colors for bold text
     */
    // Default: "draw-bold-text-with-bright-colors"
    public static final String KEY_DRAW_BOLD_TEXT_WITH_BRIGHT_COLORS = "draw-bold-text-with-bright-colors";

    /**
     * Defines the key for whether to use ctrl space workaround to fix the issue where ctrl+space does not work on some ROMs
     */
    // Default: "ctrl-space-workaround"
    public static final String KEY_USE_CTRL_SPACE_WORKAROUND = "ctrl-space-workaround";


    /**
     * Defines the key for whether termux will remove itself from the recent apps screen when
     * it closes itself.
     */
    // Default: "remove-termux-activity-from-recents-on-exit"
    public static final String KEY_ACTIVITY_FINISH_REMOVE_TASK = "remove-termux-activity-from-recents-on-exit";

    /* int */
    /**
     * Defines the key for the bell behaviour
     */
    // Default: "bell-character"
    public static final String KEY_BELL_BEHAVIOUR = "bell-character";

    public static final String VALUE_BELL_BEHAVIOUR_VIBRATE = "vibrate";

    public static final String VALUE_BELL_BEHAVIOUR_BEEP = "beep";

    public static final String VALUE_BELL_BEHAVIOUR_IGNORE = "ignore";

    public static final int IVALUE_BELL_BEHAVIOUR_VIBRATE = 1;

    public static final int IVALUE_BELL_BEHAVIOUR_BEEP = 2;

    public static final int IVALUE_BELL_BEHAVIOUR_IGNORE = 3;

    public static final int DEFAULT_IVALUE_BELL_BEHAVIOUR = IVALUE_BELL_BEHAVIOUR_VIBRATE;

    /**
     * Defines the bidirectional map for bell behaviour values and their internal values
     */
    public static final ImmutableBiMap<String, Integer> MAP_BELL_BEHAVIOUR = new ImmutableBiMap.Builder<String, Integer>().put(VALUE_BELL_BEHAVIOUR_VIBRATE, IVALUE_BELL_BEHAVIOUR_VIBRATE).put(VALUE_BELL_BEHAVIOUR_BEEP, IVALUE_BELL_BEHAVIOUR_BEEP).put(VALUE_BELL_BEHAVIOUR_IGNORE, IVALUE_BELL_BEHAVIOUR_IGNORE).build();

    /**
     * Defines the key for the terminal cursor blink rate
     */
    // Default: "terminal-cursor-blink-rate"
    public static final String KEY_TERMINAL_CURSOR_BLINK_RATE = "terminal-cursor-blink-rate";

    public static final int IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN = TerminalView.TERMINAL_CURSOR_BLINK_RATE_MIN;

    public static final int IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX = TerminalView.TERMINAL_CURSOR_BLINK_RATE_MAX;

    public static final int DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE = 0;

    /**
     * Defines the key for the terminal cursor style
     */
    // Default: "terminal-cursor-style"
    public static final String KEY_TERMINAL_CURSOR_STYLE = "terminal-cursor-style";

    public static final String VALUE_TERMINAL_CURSOR_STYLE_BLOCK = "block";

    public static final String VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE = "underline";

    public static final String VALUE_TERMINAL_CURSOR_STYLE_BAR = "bar";

    public static final int IVALUE_TERMINAL_CURSOR_STYLE_BLOCK = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;

    public static final int IVALUE_TERMINAL_CURSOR_STYLE_UNDERLINE = TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;

    public static final int IVALUE_TERMINAL_CURSOR_STYLE_BAR = TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;

    public static final int DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;

    /**
     * Defines the bidirectional map for terminal cursor styles and their internal values
     */
    public static final ImmutableBiMap<String, Integer> MAP_TERMINAL_CURSOR_STYLE = new ImmutableBiMap.Builder<String, Integer>().put(VALUE_TERMINAL_CURSOR_STYLE_BLOCK, IVALUE_TERMINAL_CURSOR_STYLE_BLOCK).put(VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE, IVALUE_TERMINAL_CURSOR_STYLE_UNDERLINE).put(VALUE_TERMINAL_CURSOR_STYLE_BAR, IVALUE_TERMINAL_CURSOR_STYLE_BAR).build();

    /**
     * Defines the key for how many days old the access time should be of files that should be
     * deleted from $TMPDIR on termux exit.
     * `-1` for none, `0` for all and `> 0` for x days.
     */
    // Default: "delete-tmpdir-files-older-than-x-days-on-exit"
    public static final String KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT = "delete-tmpdir-files-older-than-x-days-on-exit";

    public static final int IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MIN = -1;

    public static final int IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MAX = 10;

    public static final int DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT = 3;

    /**
     * Defines the key for the terminal transcript rows
     */
    // Default: "terminal-transcript-rows"
    public static final String KEY_TERMINAL_TRANSCRIPT_ROWS = "terminal-transcript-rows";

    public static final int IVALUE_TERMINAL_TRANSCRIPT_ROWS_MIN = TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MIN;

    public static final int IVALUE_TERMINAL_TRANSCRIPT_ROWS_MAX = TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MAX;

    public static final int DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS = TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS;

    /* float */
    /**
     * Defines the key for the terminal toolbar height
     */
    // Default: "terminal-toolbar-height"
    public static final String KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR = "terminal-toolbar-height";

    public static final float IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN = 0.4f;

    public static final float IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MAX = 3;

    public static final float DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR = 1;

    /* Integer */

    /* String */


    /**
     * Defines the key for the default working directory
     */
    // Default: "default-working-directory"
    public static final String KEY_DEFAULT_WORKING_DIRECTORY = "default-working-directory";

    /**
     * Defines the default working directory
     */
    public static final String DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY = TermuxConstants.TERMUX_HOME_DIR_PATH;

    /**
     * Defines the key for whether toggle soft keyboard request will show/hide or enable/disable keyboard
     */
    // Default: "soft-keyboard-toggle-behaviour"
    public static final String KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR = "soft-keyboard-toggle-behaviour";

    public static final String IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE = "show/hide";

    public static final String IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE = "enable/disable";

    public static final String DEFAULT_IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR = IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE;

    /**
     * Defines the bidirectional map for toggle soft keyboard behaviour values and their internal values
     */
    public static final ImmutableBiMap<String, String> MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR = new ImmutableBiMap.Builder<String, String>().put(IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE, IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE).put(IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE, IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE).build();


    /**
     * Defines the set for keys loaded by termux
     * Setting this to {@code null} will make {@link SharedProperties} throw an exception.
     */
    public static final Set<String> TERMUX_APP_PROPERTIES_LIST = new HashSet<>(Arrays.asList(/* boolean */
       KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST,
        KEY_ENFORCE_CHAR_BASED_INPUT,
        KEY_RUN_TERMUX_AM_SOCKET_SERVER,
        KEY_TERMINAL_ONCLICK_URL_OPEN,
        KEY_DRAW_BOLD_TEXT_WITH_BRIGHT_COLORS,
        KEY_USE_CTRL_SPACE_WORKAROUND,
        KEY_ACTIVITY_FINISH_REMOVE_TASK,
        /* int */
    KEY_BELL_BEHAVIOUR,
        KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT,
        KEY_TERMINAL_CURSOR_BLINK_RATE,
        KEY_TERMINAL_CURSOR_STYLE,
        KEY_TERMINAL_TRANSCRIPT_ROWS,
        /* float */
    KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
        /* Integer */
     /* String */
        KEY_DEFAULT_WORKING_DIRECTORY, KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR));

    /**
     * Defines the set for keys loaded by termux that have default boolean behaviour with false as default.
     * "true" -> true
     * "false" -> false
     * default: false
     */
    public static final Set<String> TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST,
        KEY_ENFORCE_CHAR_BASED_INPUT,
        KEY_TERMINAL_ONCLICK_URL_OPEN,
        KEY_USE_CTRL_SPACE_WORKAROUND,
        KEY_ACTIVITY_FINISH_REMOVE_TASK,
        KEY_DRAW_BOLD_TEXT_WITH_BRIGHT_COLORS));

    /**
     * Defines the set for keys loaded by termux that have default boolean behaviour with true as default.
     * "true" -> true
     * "false" -> false
     * default: true
     */
    public static final Set<String> TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(List.of(KEY_RUN_TERMUX_AM_SOCKET_SERVER));

}
