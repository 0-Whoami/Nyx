package com.termux.shared.termux;

import java.io.File;
import java.util.Arrays;
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
     * Termux app config $PREFIX directory path
     */
    // Default: "/data/data/com.termux/files/usr/etc/termux"
    public static final String TERMUX_CONFIG_PREFIX_DIR_PATH = TERMUX_ETC_PREFIX_DIR_PATH + "/termux";

    /**
     * Termux app storage home directory path
     */
    // Default: "/data/data/com.termux/files/home/storage"
    public static final String TERMUX_STORAGE_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/storage";

    /**
     * Termux app storage home directory
     */
    public static final File TERMUX_STORAGE_HOME_DIR = new File(TERMUX_STORAGE_HOME_DIR_PATH);

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


    /*
     * Termux app and Termux:Styling colors.properties file path
     */
    // Default: "/data/data/com.termux/files/home/.termux/colors.properties"
    //public static final String TERMUX_COLOR_PROPERTIES_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/colors.properties";

    /*
     * Termux app and Termux:Styling colors.properties file
     */
    //public static final File TERMUX_COLOR_PROPERTIES_FILE = new File(TERMUX_COLOR_PROPERTIES_FILE_PATH);

    // Default: "/data/data/com.termux/files/home/.termux/font.ttf"
    //public static final String TERMUX_FONT_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/font.ttf";

    //public static final File TERMUX_FONT_FILE = new File(TERMUX_FONT_FILE_PATH);

    // Default: "/data/data/com.termux/files/home/.termux/font-italic.ttf"

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

    // Default: "allow-external-apps"

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

        // Default: "com.termux.app.TermuxActivity"
        //public static final String TERMUX_ACTIVITY_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxActivity";

        /**
         * Termux app core activity.
         */
        public static final class TERMUX_ACTIVITY {

            /**
             * Intent extra for if termux failsafe session needs to be started and is used by {@link TERMUX_ACTIVITY} and {@link TERMUX_SERVICE#ACTION_STOP_SERVICE}
             */
            // Default: "com.termux.app.failsafe_session"
            public static final String EXTRA_FAILSAFE_SESSION ="failsafe";
            public static final String EXTRA_PHONE_LISTENER="con";

        }

        /**
         * Termux app core service.
         */
        public static final class TERMUX_SERVICE {

            /**
             * Intent action to stop TERMUX_SERVICE
             */
            // Default: "com.termux.service_stop"
            public static final String ACTION_STOP_SERVICE = "stop";


        }

    }

}
