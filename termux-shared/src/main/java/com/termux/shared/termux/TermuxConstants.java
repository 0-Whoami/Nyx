package com.termux.shared.termux;

import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import java.io.File;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;


/**
 * A class that defines shared constants of the Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 * <p>
 * Termux app default package name is "com.termux" and is used in {@link #TERMUX_PREFIX_DIR_PATH}.
 * The binaries compiled for termux have {@link #TERMUX_PREFIX_DIR_PATH} hardcoded in them but it
 * can be changed during compilation.
 * <p>
 * The {@link #TERMUX_PACKAGE_NAME} must be the same as the applicationId of termux-app build.gradle
 * since its also used by {@link #TERMUX_FILES_DIR_PATH}.
 * If {@link #TERMUX_PACKAGE_NAME} is changed, then binaries, specially used in bootstrap need to be
 * compiled appropriately<a href=".">Check https://github.com/termux/termux-packages/wiki/Building-</a>packages
 * for more info.
 * <p>
 * Ideally the only places where changes should be required if changing package name are the following:
 * - The {@link #TERMUX_PACKAGE_NAME} in {@link TermuxConstants}.
 * - The "applicationId" in "build.gradle" of termux-app. This is package name that android and app
 *      stores will use and is also the final package name stored in "AndroidManifest.xml".
 * - The "manifestPlaceholders" values for {@link #TERMUX_PACKAGE_NAME} and *_APP_NAME in
 *      "build.gradle" of termux-app.
 * - The "ENTITY" values for {@link #TERMUX_PACKAGE_NAME} and *_APP_NAME in "strings.xml" of
 *      termux-app and of termux-shared.
 * - The "shortcut.xml" and "*_preferences.xml" files of termux-app since dynamic variables don't
 *      work in it.
 * - Optionally the "package" in "AndroidManifest.xml" if modifying project structure of termux-app.
 *      This is package name for java classes project structure and is prefixed if activity and service
 *      names use dot (.) notation. This is currently not advisable since this will break lot of
 *      stuff, including termux-* packages.
 * - Optionally the *_PATH variables in {@link TermuxConstants} containing the string "termux".
 <a href=" * <p>
 * Check https://developer.android.com/studio/buil">...</a>d/application-id for info on "package" in
 * "AndroidManifest.xml" and "applicationId" in "build.gradle".
 * <p>
 * The {@link #TERMUX_PACKAGE_NAME} must be used in source code of Termux app and its plugins instead
 * of hardcoded "com.termux" paths.
 */
public final class TermuxConstants {

    /*
     * Termux organization variables.
     */

    /**
     * Termux app name
     */
    // Default: "Termux"
    public static final String TERMUX_APP_NAME = "Termux";

    /**
     * Termux package name
     */
    // Default: "com.termux"
    public static final String TERMUX_PACKAGE_NAME = "com.termux";


    /**
     * Termux:Styling app package name
     */
    // Default: "com.termux.styling"
    public static final String TERMUX_STYLING_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".styling";



    /*
     * Termux plugin apps lists.
     */


    /*
     * Termux APK releases.
     */


    /*
     * Termux packages urls.
     */


    /*
     * Termux miscellaneous urls.
     */

 /*
     * Termux app core directory paths.
     */
    /**
     * Termux app internal private app data directory path
     */

    public static final String // Default: "/data/data/com.termux"
    TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME;

    /**
     * Termux app Files directory path
     */
    // Default: "/data/data/com.termux/files"
    public static final String TERMUX_FILES_DIR_PATH = TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH + "/files";

    /**
     * Termux app $PREFIX directory path
     */
    // Default: "/data/data/com.termux/files/usr"
    public static final String TERMUX_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr";

    /**
     * Termux app $PREFIX directory
     */
    public static final File TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);

    /**
     * Termux app $PREFIX/bin directory path
     */
    // Default: "/data/data/com.termux/files/usr/bin"
    public static final String TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin";

    /**
     * Termux app $PREFIX/etc directory path
     */
    // Default: "/data/data/com.termux/files/usr/etc"
    public static final String TERMUX_ETC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/etc";

    /**
     * Termux app $PREFIX/lib directory path
     */
    // Default: "/data/data/com.termux/files/usr/lib"
    public static final String TERMUX_LIB_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/lib";

    /**
     * Termux app $PREFIX/tmp and $TMPDIR directory path
     */
    // Default: "/data/data/com.termux/files/usr/tmp"
    public static final String TERMUX_TMP_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/tmp";

    /**
     * Termux app usr-staging directory path
     */
    // Default: "/data/data/com.termux/files/usr-staging"
    public static final String TERMUX_STAGING_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr-staging";

    /**
     * Termux app usr-staging directory
     */
    public static final File TERMUX_STAGING_PREFIX_DIR = new File(TERMUX_STAGING_PREFIX_DIR_PATH);

    /**
     * Termux app $HOME directory path
     */
    // Default: "/data/data/com.termux/files/home"
    public static final String TERMUX_HOME_DIR_PATH = TERMUX_FILES_DIR_PATH + "/home";

    /**
     * Termux app config home directory path
     */
    // Default: "/data/data/com.termux/files/home/.config/termux"
    public static final String TERMUX_CONFIG_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.config/termux";

    /**
     * Termux app config $PREFIX directory path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux"
    public static final String TERMUX_CONFIG_PREFIX_DIR_PATH = TERMUX_ETC_PREFIX_DIR_PATH + "/termux";

    /**
     * Termux app data home directory path
     */
    // Default: "/data/data/com.termux/files/home/.termux"
    public static final String TERMUX_DATA_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.termux";

    /**
     * Termux app storage home directory path
     */
    // Default: "/data/data/com.termux/files/home/storage"
    public static final String TERMUX_STORAGE_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/storage";

    /**
     * Termux app storage home directory
     */
    public static final File TERMUX_STORAGE_HOME_DIR = new File(TERMUX_STORAGE_HOME_DIR_PATH);

    /**
     * Termux app background directory path
     */
    // Default: "/data/data/com.termux/files/.termux/background"
    public static final String TERMUX_BACKGROUND_DIR_PATH = TERMUX_DATA_HOME_DIR_PATH + "/background";

    /**
     * Termux app backgorund original image file path
     */
    // Default: "/data/data/com.termux/files/home/.termux/background.jpeg"
    public static final String TERMUX_BACKGROUND_IMAGE_PATH = TERMUX_BACKGROUND_DIR_PATH + "/background.jpeg";

    // Default: "/data/data/com.termux/files/home/.termux/background/background_portrait.jpeg"
   /// public static final String TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH = TERMUX_BACKGROUND_DIR_PATH + "/background_portrait.jpeg";


    /**
     * Termux and plugin apps directory path
     */
    // Default: "/data/data/com.termux/files/apps"
    public static final String TERMUX_APPS_DIR_PATH = TERMUX_FILES_DIR_PATH + "/apps";

    /**
     * Termux app $PREFIX directory path ignored sub file paths to consider it empty
     */
    public static final List<String> TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY = Arrays.asList(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH, TermuxConstants.TERMUX_ENV_FILE_PATH);

    /*
     * Termux app and plugin preferences and properties file paths.
     */
    /**
     * Termux app default SharedPreferences file basename without extension
     */
    // Default: "com.termux_preferences"
    public static final String TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_PACKAGE_NAME + "_preferences";


    /**
     * Termux app properties primary file path
     */
    // Default: "/data/data/com.termux/files/home/.termux/termux.properties"
    public static final String TERMUX_PROPERTIES_PRIMARY_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/termux.properties";

    /**
     * Termux app properties secondary file path
     */
    // Default: "/data/data/com.termux/files/home/.config/termux/termux.properties"
    public static final String TERMUX_PROPERTIES_SECONDARY_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.properties";

    /**
     * Termux app properties file paths list. **DO NOT** allow these files to be modified by
     * {@link android.content.ContentProvider} exposed to external apps, since they may silently
     * modify the values for security properties like {@link #PROP_ALLOW_EXTERNAL_APPS} set by users
     * without their explicit consent.
     */
    public static final List<String> TERMUX_PROPERTIES_FILE_PATHS_LIST = Arrays.asList(TERMUX_PROPERTIES_PRIMARY_FILE_PATH, TERMUX_PROPERTIES_SECONDARY_FILE_PATH);

    /**
     * Termux app and Termux:Styling colors.properties file path
     */
    // Default: "/data/data/com.termux/files/home/.termux/colors.properties"
    public static final String TERMUX_COLOR_PROPERTIES_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/colors.properties";

    /**
     * Termux app and Termux:Styling colors.properties file
     */
    public static final File TERMUX_COLOR_PROPERTIES_FILE = new File(TERMUX_COLOR_PROPERTIES_FILE_PATH);

    /**
     * Termux app and Termux:Styling font.ttf file path
     */
    // Default: "/data/data/com.termux/files/home/.termux/font.ttf"
    public static final String TERMUX_FONT_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/font.ttf";

    /**
     * Termux app and Termux:Styling font.ttf file
     */
    public static final File TERMUX_FONT_FILE = new File(TERMUX_FONT_FILE_PATH);

    /**
     * Termux app only font-italic.ttf file path
     */
    // Default: "/data/data/com.termux/files/home/.termux/font-italic.ttf"
    public static final String TERMUX_ITALIC_FONT_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/font-italic.ttf";

    /**
     * Termux app only font-italic.ttf file
     */
    public static final File TERMUX_ITALIC_FONT_FILE = new File(TERMUX_ITALIC_FONT_FILE_PATH);

    /**
     * Termux app environment file path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env"
    public static final String TERMUX_ENV_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env";

    /**
     * Termux app environment temp file path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env.tmp"
    public static final String TERMUX_ENV_TEMP_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env.tmp";

    /*
     * Termux app plugin specific paths.
     */

    /*
     * Termux app and plugins notification variables.
     */
    /**
     * Termux app notification channel id used by {@link TERMUX_APP.TERMUX_SERVICE}
     */
    public static final String TERMUX_APP_NOTIFICATION_CHANNEL_ID = "termux_notification_channel";

    /**
     * Termux app notification channel name used by {@link TERMUX_APP.TERMUX_SERVICE}
     */
    public static final String TERMUX_APP_NOTIFICATION_CHANNEL_NAME = TermuxConstants.TERMUX_APP_NAME + " App";

    /**
     * Termux app unique notification id used by {@link TERMUX_APP.TERMUX_SERVICE}
     */
    public static final int TERMUX_APP_NOTIFICATION_ID = 1337;



    /*
     * Termux app and plugins miscellaneous variables.
     */

    /**
     * Termux property defined in termux.properties file as a secondary check to PERMISSION_RUN_COMMAND
     * to allow 3rd party apps to run various commands in Termux app context
     */
    // Default: "allow-external-apps"
    public static final String PROP_ALLOW_EXTERNAL_APPS = "allow-external-apps";

    /**
     * The broadcast action sent when Termux App opens
     */
    public static final String BROADCAST_TERMUX_OPENED = TERMUX_PACKAGE_NAME + ".app.OPENED";

    /**
     * Environment variable prefix root for the Termux app.
     */
    public static final String TERMUX_ENV_PREFIX_ROOT = "TERMUX";

    /**
     * Termux app constants.
     */
    public static final class TERMUX_APP {

        /**
         * Termux apps directory path
         */
        // Default: "/data/data/com.termux/files/apps/com.termux"
        public static final String APPS_DIR_PATH = TERMUX_APPS_DIR_PATH + "/" + TERMUX_PACKAGE_NAME;

        /**
         * termux-am socket file path
         */
        // Default: "/data/data/com.termux/files/apps/com.termux/termux-am/am.sock"
        public static final String TERMUX_AM_SOCKET_FILE_PATH = APPS_DIR_PATH + "/termux-am/am.sock";

        /**
         * Termux app BuildConfig class name
         */
        // Default: "com.termux.BuildConfig"
        public static final String BUILD_CONFIG_CLASS_NAME = TERMUX_PACKAGE_NAME + ".BuildConfig";

        /**
         * Termux app core activity name.
         */
        // Default: "com.termux.app.TermuxActivity"
        public static final String TERMUX_ACTIVITY_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxActivity";

        /**
         * Termux app core activity.
         */
        public static final class TERMUX_ACTIVITY {

            /**
             * Intent extra for if termux failsafe session needs to be started and is used by {@link TERMUX_ACTIVITY} and {@link TERMUX_SERVICE#ACTION_STOP_SERVICE}
             */
            // Default: "com.termux.app.failsafe_session"
            public static final String EXTRA_FAILSAFE_SESSION = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.failsafe_session";

            /**
             * Intent action to make termux app notify user that a crash happened.
             */
            // Default: "com.termux.app.notify_app_crash"
            public static final String ACTION_NOTIFY_APP_CRASH = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.notify_app_crash";

            /**
             * Intent action to make termux reload its termux session styling
             */
            // Default: "com.termux.app.reload_style"
            public static final String ACTION_RELOAD_STYLE = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.reload_style";

            /**
             * Intent {@code String} extra for what to reload for the TERMUX_ACTIVITY.ACTION_RELOAD_STYLE intent. This has been deperecated.
             */
            @Deprecated
            public static final String // Default: "com.termux.app.reload_style"
            EXTRA_RELOAD_STYLE = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.reload_style";

            /**
             *  Intent {@code boolean} extra for whether to recreate activity for the TERMUX_ACTIVITY.ACTION_RELOAD_STYLE intent.
             */
            // Default: "com.termux.app.TermuxActivity.EXTRA_RECREATE_ACTIVITY"
            public static final String EXTRA_RECREATE_ACTIVITY = TERMUX_APP.TERMUX_ACTIVITY_NAME + ".EXTRA_RECREATE_ACTIVITY";

            /**
             * Intent action to make termux request storage permissions
             */
            // Default: "com.termux.app.request_storage_permissions"
            public static final String ACTION_REQUEST_PERMISSIONS = TermuxConstants.TERMUX_PACKAGE_NAME + ".app.request_storage_permissions";
        }

        /**
         * Termux app core service.
         */
        public static final class TERMUX_SERVICE {

            /**
             * Intent action to stop TERMUX_SERVICE
             */
            // Default: "com.termux.service_stop"
            public static final String ACTION_STOP_SERVICE = TERMUX_PACKAGE_NAME + ".service_stop";

            /**
             * Intent action to make TERMUX_SERVICE acquire a wakelock
             */
            // Default: "com.termux.service_wake_lock"
            public static final String ACTION_WAKE_LOCK = TERMUX_PACKAGE_NAME + ".service_wake_lock";

            /**
             * Intent action to make TERMUX_SERVICE release wakelock
             */
            // Default: "com.termux.service_wake_unlock"
            public static final String ACTION_WAKE_UNLOCK = TERMUX_PACKAGE_NAME + ".service_wake_unlock";

            /**
             * Intent action to execute command with TERMUX_SERVICE
             */
            // Default: "com.termux.service_execute"
            public static final String ACTION_SERVICE_EXECUTE = TERMUX_PACKAGE_NAME + ".service_execute";

            /**
             * Intent {@code String[]} extra for arguments to the executable of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.arguments"
            public static final String EXTRA_ARGUMENTS = TERMUX_PACKAGE_NAME + ".execute.arguments";

            /**
             * Intent {@code String} extra for stdin of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.stdin"
            public static final String EXTRA_STDIN = TERMUX_PACKAGE_NAME + ".execute.stdin";

            /**
             * Intent {@code String} extra for command current working directory for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.cwd"
            public static final String EXTRA_WORKDIR = TERMUX_PACKAGE_NAME + ".execute.cwd";

            /**
             * Intent {@code boolean} extra for whether to run command in background {@link Runner#APP_SHELL} or foreground {@link Runner#TERMINAL_SESSION} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            @Deprecated
            public static final String // Default: "com.termux.execute.background"
            EXTRA_BACKGROUND = TERMUX_PACKAGE_NAME + ".execute.background";

            /**
             * Intent {@code String} extra for command the {@link Runner} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.runner"
            public static final String EXTRA_RUNNER = TERMUX_PACKAGE_NAME + ".execute.runner";

            /**
             * Intent {@code String} extra for custom log level for background commands defined by { com.termux.shared.logger.Logger} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.background_custom_log_level"
            public static final String EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = TERMUX_PACKAGE_NAME + ".execute.background_custom_log_level";

            /**
             * Intent {@code String} extra for session action for {@link Runner#TERMINAL_SESSION} commands for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.session_action"
            public static final String EXTRA_SESSION_ACTION = TERMUX_PACKAGE_NAME + ".execute.session_action";

            /**
             * Intent {@code String} extra for shell name for commands for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.shell_name"
            public static final String EXTRA_SHELL_NAME = TERMUX_PACKAGE_NAME + ".execute.shell_name";

            /**
             * Intent {@code String} extra for the {@link ExecutionCommand.ShellCreateMode}  for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent.
             */
            // Default: "com.termux.execute.shell_create_mode"
            public static final String EXTRA_SHELL_CREATE_MODE = TERMUX_PACKAGE_NAME + ".execute.shell_create_mode";

            /**
             * Intent {@code String} extra for label of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.command_label"
            public static final String EXTRA_COMMAND_LABEL = TERMUX_PACKAGE_NAME + ".execute.command_label";

            /**
             * Intent markdown {@code String} extra for description of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.command_description"
            public static final String EXTRA_COMMAND_DESCRIPTION = TERMUX_PACKAGE_NAME + ".execute.command_description";

            /**
             * Intent markdown {@code String} extra for help of the command for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.command_help"
            public static final String EXTRA_COMMAND_HELP = TERMUX_PACKAGE_NAME + ".execute.command_help";

            /**
             * Intent markdown {@code String} extra for help of the plugin API for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent (Internal Use Only)
             */
            // Default: "com.termux.execute.plugin_help"
            public static final String EXTRA_PLUGIN_API_HELP = TERMUX_PACKAGE_NAME + ".execute.plugin_api_help";

            /**
             * Intent {@code Parcelable} extra for the pending intent that should be sent with the
             * result of the execution command to the execute command caller for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "pendingIntent"
            public static final String EXTRA_PENDING_INTENT = "pendingIntent";

            /**
             * Intent {@code String} extra for the directory path in which to write the result of the
             * execution command for the execute command caller for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.result_directory"
            public static final String EXTRA_RESULT_DIRECTORY = TERMUX_PACKAGE_NAME + ".execute.result_directory";

            /**
             * Intent {@code boolean} extra for whether the result should be written to a single file
             * or multiple files (err, errmsg, stdout, stderr, exit_code) in
             * {@link #EXTRA_RESULT_DIRECTORY} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.result_single_file"
            public static final String EXTRA_RESULT_SINGLE_FILE = TERMUX_PACKAGE_NAME + ".execute.result_single_file";

            /**
             * Intent {@code String} extra for the basename of the result file that should be created
             * in {@link #EXTRA_RESULT_DIRECTORY} if {@link #EXTRA_RESULT_SINGLE_FILE} is {@code true}
             * for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.result_file_basename"
            public static final String EXTRA_RESULT_FILE_BASENAME = TERMUX_PACKAGE_NAME + ".execute.result_file_basename";

            /**
             * Intent {@code String} extra for the output {@link Formatter} format of the
             * {@link #EXTRA_RESULT_FILE_BASENAME} result file for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.result_file_output_format"
            public static final String EXTRA_RESULT_FILE_OUTPUT_FORMAT = TERMUX_PACKAGE_NAME + ".execute.result_file_output_format";

            /**
             * Intent {@code String} extra for the error {@link Formatter} format of the
             * {@link #EXTRA_RESULT_FILE_BASENAME} result file for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.result_file_error_format"
            public static final String EXTRA_RESULT_FILE_ERROR_FORMAT = TERMUX_PACKAGE_NAME + ".execute.result_file_error_format";

            /**
             * Intent {@code String} extra for the optional suffix of the result files that should
             * be created in {@link #EXTRA_RESULT_DIRECTORY} if {@link #EXTRA_RESULT_SINGLE_FILE} is
             * {@code false} for the TERMUX_SERVICE.ACTION_SERVICE_EXECUTE intent
             */
            // Default: "com.termux.execute.result_files_suffix"
            public static final String EXTRA_RESULT_FILES_SUFFIX = TERMUX_PACKAGE_NAME + ".execute.result_files_suffix";

            /**
             * The value for {@link #EXTRA_SESSION_ACTION} extra that will set the new session as
             * the current session and will start {@link TERMUX_ACTIVITY} if its not running to bring
             * the new session to foreground.
             */
            public static final int VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY = 0;

            /**
             * The value for {@link #EXTRA_SESSION_ACTION} extra that will keep any existing session
             * as the current session and will start {@link TERMUX_ACTIVITY} if its not running to
             * bring the existing session to foreground. The new session will be added to the left
             * sidebar in the sessions list.
             */
            public static final int VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY = 1;

            /**
             * The value for {@link #EXTRA_SESSION_ACTION} extra that will set the new session as
             * the current session but will not start {@link TERMUX_ACTIVITY} if its not running
             * and session(s) will be seen in Termux notification and can be clicked to bring new
             * session to foreground. If the {@link TERMUX_ACTIVITY} is already running, then this
             * will behave like {@link #VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY}.
             */
            public static final int VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY = 2;

            /**
             * The value for {@link #EXTRA_SESSION_ACTION} extra that will keep any existing session
             * as the current session but will not start {@link TERMUX_ACTIVITY} if its not running
             * and session(s) will be seen in Termux notification and can be clicked to bring
             * existing session to foreground. If the {@link TERMUX_ACTIVITY} is already running,
             * then this will behave like {@link #VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY}.
             */
            public static final int VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY = 3;

        }

        /**
         * Termux app run command service to receive commands sent by 3rd party apps.
         */
        public static final class RUN_COMMAND_SERVICE {


            /**
             * Intent {@code boolean} extra for whether to run command in background {@link Runner#APP_SHELL} or foreground {@link Runner#TERMINAL_SESSION} for the RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND intent
             */
            @Deprecated
            public static final String // Default: "com.termux.RUN_COMMAND_BACKGROUND"
            EXTRA_BACKGROUND = TERMUX_PACKAGE_NAME + ".RUN_COMMAND_BACKGROUND";

        }
    }

    /**
     * Termux:Styling app constants.
     */
    public static final class TERMUX_STYLING {

        /**
         * Termux:Styling app core activity name.
         */
        // Default: "com.termux.styling.TermuxStyleActivity"
        public static final String TERMUX_STYLING_ACTIVITY_NAME = TERMUX_STYLING_PACKAGE_NAME + ".TermuxStyleActivity";
    }

}
