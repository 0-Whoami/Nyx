package com.termux.shared.termux.settings.properties;

import com.google.common.collect.ImmutableBiMap;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/*
 * Version: v0.18.0
 * SPDX-License-Identifier: MIT
 *
 * Changelog
 *
 * - 0.1.0 (2021-03-11)
 *      - Initial Release.
 *
 * - 0.2.0 (2021-03-11)
 *      - Renamed `HOME_PATH` to `TERMUX_HOME_DIR_PATH`.
 *      - Renamed `TERMUX_PROPERTIES_PRIMARY_PATH` to `TERMUX_PROPERTIES_PRIMARY_FILE_PATH`.
 *      - Renamed `TERMUX_PROPERTIES_SECONDARY_FILE_PATH` to `TERMUX_PROPERTIES_SECONDARY_FILE_PATH`.
 *
 * - 0.3.0 (2021-03-16)
 *      - Add `*TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR*`.
 *
 * - 0.4.0 (2021-03-16)
 *      - Removed `MAP_GENERIC_BOOLEAN` and `MAP_GENERIC_INVERTED_BOOLEAN`.
 *
 * - 0.5.0 (2021-03-25)
 *      - Add `KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP`.
 *
 * - 0.6.0 (2021-04-07)
 *      - Updated javadocs.
 *
 * - 0.7.0 (2021-05-09)
 *      - Add `*SOFT_KEYBOARD_TOGGLE_BEHAVIOUR*`.
 *
 * - 0.8.0 (2021-05-10)
 *      - Change the `KEY_USE_BACK_KEY_AS_ESCAPE_KEY` and `KEY_VIRTUAL_VOLUME_KEYS_DISABLED` booleans
 *          to `KEY_BACK_KEY_BEHAVIOUR` and `KEY_VOLUME_KEYS_BEHAVIOUR` String internal values.
 *      - Renamed `SOFT_KEYBOARD_TOGGLE_BEHAVIOUR` to `KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR`.
 *
 * - 0.9.0 (2021-05-14)
 *      - Add `*KEY_TERMINAL_CURSOR_BLINK_RATE*`.
 *
 * - 0.10.0 (2021-05-15)
 *      - Add `MAP_BACK_KEY_BEHAVIOUR`, `MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR`, `MAP_VOLUME_KEYS_BEHAVIOUR`.
 *
 * - 0.11.0 (2021-06-10)
 *      - Add `*KEY_TERMINAL_TRANSCRIPT_ROWS*`.
 *
 * - 0.12.0 (2021-06-10)
 *      - Add `*KEY_TERMINAL_CURSOR_STYLE*`.
 *
 * - 0.13.0 (2021-08-25)
 *      - Add `*KEY_TERMINAL_MARGIN_HORIZONTAL*` and `*KEY_TERMINAL_MARGIN_VERTICAL*`.
 *
 * - 0.14.0 (2021-09-02)
 *      - Add `getTermuxFloatPropertiesFile()`.
 *
 * - 0.15.0 (2021-09-05)
 *      - Add `KEY_EXTRA_KEYS_TEXT_ALL_CAPS`.
 *
 * - 0.16.0 (2021-10-21)
 *      - Add `KEY_NIGHT_MODE`.
 *
 * - 0.17.0 (2022-03-17)
 *      - Add `KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT`.
 *
 * - 0.18.0 (2022-06-13)
 *      - Add `KEY_DISABLE_FILE_SHARE_RECEIVER` and `KEY_DISABLE_FILE_VIEW_RECEIVER`
 *
 * - 0.18.5 (2022-08-18)
 *      - Add `KEY_ACTIVITY_FINISH_REMOVE_TASK`.
 *      - Add `KEY_DISABLE_FILE_SHARE_RECEIVER` and `KEY_DISABLE_FILE_VIEW_RECEIVER`.
 * 
 * - 0.19.0 (2022-11-04)
 *      - Add `KEY_BACKGROUND_OVERLAY_COLOR` and `DEFAULT_IVALUE_BACKGROUND_OVERLAY_COLOR`
 */
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

    /* boolean */
    /**
     * Defines the key for whether file share receiver of the app is enabled.
     */
    // Default: "disable-file-share-receiver"
    public static final String KEY_DISABLE_FILE_SHARE_RECEIVER = "disable-file-share-receiver";

    /**
     * Defines the key for whether file view receiver of the app is enabled.
     */
    // Default: "disable-file-view-receiver"
    public static final String KEY_DISABLE_FILE_VIEW_RECEIVER = "disable-file-view-receiver";

    /**
     * Defines the key for whether hardware keyboard shortcuts are enabled.
     */
    // Default: "disable-hardware-keyboard-shortcuts"
    public static final String KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS = "disable-hardware-keyboard-shortcuts";

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
     * Defines the key for whether text for the extra keys buttons should be all capitalized automatically
     */
    // Default: "extra-keys-text-all-caps"
    public static final String KEY_EXTRA_KEYS_TEXT_ALL_CAPS = "extra-keys-text-all-caps";

    /**
     * Defines the key for whether to hide soft keyboard when termux app is started
     */
    // Default: "hide-soft-keyboard-on-startup"
    public static final String KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP = "hide-soft-keyboard-on-startup";

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
     * Defines the key for whether to use black UI
     */
    @Deprecated
    public static final String // Default: "use-black-ui"
    KEY_USE_BLACK_UI = "use-black-ui";

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
     * Defines the key for whether to use fullscreen
     */
    // Default: "fullscreen"
    public static final String KEY_USE_FULLSCREEN = "fullscreen";

    /**
     * Defines the key for whether to use fullscreen workaround
     */
    // Default: "use-fullscreen-workaround"
    public static final String KEY_USE_FULLSCREEN_WORKAROUND = "use-fullscreen-workaround";

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

    public static final int IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MAX = 100000;

    public static final int DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT = 3;

    /**
     * Defines the key for the terminal margin on left and right in dp units
     */
    // Default: "terminal-margin-horizontal"
    public static final String KEY_TERMINAL_MARGIN_HORIZONTAL = "terminal-margin-horizontal";

    public static final int IVALUE_TERMINAL_MARGIN_HORIZONTAL_MIN = 0;

    public static final int IVALUE_TERMINAL_MARGIN_HORIZONTAL_MAX = 100;

    public static final int DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL = 0;

    /**
     * Defines the key for the terminal margin on top and bottom in dp units
     */
    // Default: "terminal-margin-vertical"
    public static final String KEY_TERMINAL_MARGIN_VERTICAL = "terminal-margin-vertical";

    public static final int IVALUE_TERMINAL_MARGIN_VERTICAL_MIN = 0;

    public static final int IVALUE_TERMINAL_MARGIN_VERTICAL_MAX = 100;

    public static final int DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL = 0;

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
    /**
     * Defines the key for create session shortcut
     */
    // Default: "shortcut.create-session"
    public static final String KEY_SHORTCUT_CREATE_SESSION = "shortcut.create-session";

    /**
     * Defines the key for next session shortcut
     */
    // Default: "shortcut.next-session"
    public static final String KEY_SHORTCUT_NEXT_SESSION = "shortcut.next-session";

    /**
     * Defines the key for previous session shortcut
     */
    // Default: "shortcut.previous-session"
    public static final String KEY_SHORTCUT_PREVIOUS_SESSION = "shortcut.previous-session";

    /**
     * Defines the key for rename session shortcut
     */
    // Default: "shortcut.rename-session"
    public static final String KEY_SHORTCUT_RENAME_SESSION = "shortcut.rename-session";

    public static final int ACTION_SHORTCUT_CREATE_SESSION = 1;

    public static final int ACTION_SHORTCUT_NEXT_SESSION = 2;

    public static final int ACTION_SHORTCUT_PREVIOUS_SESSION = 3;

    public static final int ACTION_SHORTCUT_RENAME_SESSION = 4;

    /**
     * Defines the bidirectional map for session shortcut values and their internal actions
     */
    public static final ImmutableBiMap<String, Integer> MAP_SESSION_SHORTCUTS = new ImmutableBiMap.Builder<String, Integer>().put(KEY_SHORTCUT_CREATE_SESSION, ACTION_SHORTCUT_CREATE_SESSION).put(KEY_SHORTCUT_NEXT_SESSION, ACTION_SHORTCUT_NEXT_SESSION).put(KEY_SHORTCUT_PREVIOUS_SESSION, ACTION_SHORTCUT_PREVIOUS_SESSION).put(KEY_SHORTCUT_RENAME_SESSION, ACTION_SHORTCUT_RENAME_SESSION).build();

    /* String */
    /**
     * Defines the key for whether back key will behave as escape key or literal back key
     */
    // Default: "back-key"
    public static final String KEY_BACK_KEY_BEHAVIOUR = "back-key";

    public static final String IVALUE_BACK_KEY_BEHAVIOUR_BACK = "back";

    public static final String IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE = "escape";

    public static final String DEFAULT_IVALUE_BACK_KEY_BEHAVIOUR = IVALUE_BACK_KEY_BEHAVIOUR_BACK;

    /**
     * Defines the bidirectional map for back key behaviour values and their internal values
     */
    public static final ImmutableBiMap<String, String> MAP_BACK_KEY_BEHAVIOUR = new ImmutableBiMap.Builder<String, String>().put(IVALUE_BACK_KEY_BEHAVIOUR_BACK, IVALUE_BACK_KEY_BEHAVIOUR_BACK).put(IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE, IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE).build();

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
     * Defines the key for extra keys
     */
    // Default: "extra-keys"
    public static final String KEY_EXTRA_KEYS = "extra-keys";
    public static final String KEY_EXTRA_KEYS2 = "extra-keys2";


    //public static final String DEFAULT_IVALUE_EXTRA_KEYS = "[[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]"; // Single row
    // Double row
    public static final String DEFAULT_IVALUE_EXTRA_KEYS = "[['ESC', '/', {key: '|', popup: '-'}, 'HOME', 'UP', 'END', 'PGUP', 'PASTE'], ['TAB', {key: 'CTRL', popup: 'SHIFT'}, 'ALT', 'LEFT', 'DOWN', 'RIGHT', 'PGDN', {key: 'KEYBOARD', popup: {macro: 'CTRL c'}}]]";
    public static final String DEFAULT_IVALUE_EXTRA_KEYS2 = "[[F1, F2, F3, F4, F5, F6, F7, F8, F9, F10], ['[', ']', '{', '}', '$', '~', '=', -, _, '\"']]";

    /**
     * Defines the key for extra keys style
     */
    // Default: "extra-keys-style"
    public static final String KEY_EXTRA_KEYS_STYLE = "extra-keys-style";

    public static final String DEFAULT_IVALUE_EXTRA_KEYS_STYLE = "default";

    /**
     */
    // Default: "night-mode"
    public static final String KEY_NIGHT_MODE = "night-mode";



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
     * Defines the key for whether volume keys will behave as virtual or literal volume keys
     */
    // Default: "volume-keys"
    public static final String KEY_VOLUME_KEYS_BEHAVIOUR = "volume-keys";

    public static final String IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL = "virtual";

    public static final String IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME = "volume";

    public static final String DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR = IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL;

    /**
     * Defines the bidirectional map for volume keys behaviour values and their internal values
     */
    public static final ImmutableBiMap<String, String> MAP_VOLUME_KEYS_BEHAVIOUR = new ImmutableBiMap.Builder<String, String>().put(IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL, IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL).put(IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME, IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME).build();

    /**
     * Defines the key for background overlay color
     */
    // Default: "background-overlay-color
    public static final String KEY_BACKGROUND_OVERLAY_COLOR = "background-overlay-color";

    public static final int DEFAULT_IVALUE_BACKGROUND_OVERLAY_COLOR = 0x59000000;

    /**
     * Defines the set for keys loaded by termux
     * Setting this to {@code null} will make {@link SharedProperties} throw an exception.
     */
    public static final Set<String> TERMUX_APP_PROPERTIES_LIST = new HashSet<>(Arrays.asList(/* boolean */
    KEY_DISABLE_FILE_SHARE_RECEIVER, KEY_DISABLE_FILE_VIEW_RECEIVER, KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS, KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST, KEY_ENFORCE_CHAR_BASED_INPUT, KEY_EXTRA_KEYS_TEXT_ALL_CAPS, KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP, KEY_RUN_TERMUX_AM_SOCKET_SERVER, KEY_TERMINAL_ONCLICK_URL_OPEN, KEY_DRAW_BOLD_TEXT_WITH_BRIGHT_COLORS, KEY_USE_CTRL_SPACE_WORKAROUND, KEY_USE_FULLSCREEN, KEY_USE_FULLSCREEN_WORKAROUND, TermuxConstants.PROP_ALLOW_EXTERNAL_APPS, KEY_ACTIVITY_FINISH_REMOVE_TASK, /* int */
    KEY_BELL_BEHAVIOUR, KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT, KEY_TERMINAL_CURSOR_BLINK_RATE, KEY_TERMINAL_CURSOR_STYLE, KEY_TERMINAL_MARGIN_HORIZONTAL, KEY_TERMINAL_MARGIN_VERTICAL, KEY_TERMINAL_TRANSCRIPT_ROWS, /* float */
    KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR, /* Integer */
    KEY_SHORTCUT_CREATE_SESSION, KEY_SHORTCUT_NEXT_SESSION, KEY_SHORTCUT_PREVIOUS_SESSION, KEY_SHORTCUT_RENAME_SESSION, /* String */
    KEY_BACK_KEY_BEHAVIOUR, KEY_DEFAULT_WORKING_DIRECTORY, KEY_EXTRA_KEYS, KEY_EXTRA_KEYS2, KEY_EXTRA_KEYS_STYLE, KEY_NIGHT_MODE, KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR, KEY_VOLUME_KEYS_BEHAVIOUR, KEY_BACKGROUND_OVERLAY_COLOR));

    /**
     * Defines the set for keys loaded by termux that have default boolean behaviour with false as default.
     * "true" -> true
     * "false" -> false
     * default: false
     */
    public static final Set<String> TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(KEY_DISABLE_FILE_SHARE_RECEIVER, KEY_DISABLE_FILE_VIEW_RECEIVER, KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS, KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST, KEY_ENFORCE_CHAR_BASED_INPUT, KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP, KEY_TERMINAL_ONCLICK_URL_OPEN, KEY_USE_CTRL_SPACE_WORKAROUND, KEY_USE_FULLSCREEN, KEY_USE_FULLSCREEN_WORKAROUND, KEY_ACTIVITY_FINISH_REMOVE_TASK, KEY_DRAW_BOLD_TEXT_WITH_BRIGHT_COLORS, TermuxConstants.PROP_ALLOW_EXTERNAL_APPS));

    /**
     * Defines the set for keys loaded by termux that have default boolean behaviour with true as default.
     * "true" -> true
     * "false" -> false
     * default: true
     */
    public static final Set<String> TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST = new HashSet<>(Arrays.asList(KEY_EXTRA_KEYS_TEXT_ALL_CAPS, KEY_RUN_TERMUX_AM_SOCKET_SERVER));

}
