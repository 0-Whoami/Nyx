package com.termux.shared.termux.shell.command.environment

import android.content.Context
import com.termux.shared.android.PackageUtils
import com.termux.shared.android.SELinuxUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils.putToEnvIfSet
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils

/**
 * Environment for [TermuxConstants.TERMUX_PACKAGE_NAME] app.
 */
object TermuxAppShellEnvironment {
    /**
     * Environment variable for the Termux app version.
     */
    private const val ENV_TERMUX_VERSION: String =
        TermuxConstants.TERMUX_ENV_PREFIX_ROOT + "_VERSION"

    /**
     * Environment variable prefix for the Termux app.
     */
    private const val TERMUX_APP_ENV_PREFIX: String =
        TermuxConstants.TERMUX_ENV_PREFIX_ROOT + "_APP__"

    /**
     * Environment variable for the Termux app version name.
     */
    private const val ENV_TERMUX_APP__VERSION_NAME: String = TERMUX_APP_ENV_PREFIX + "VERSION_NAME"

    /**
     * Environment variable for the Termux app version code.
     */
    private const val ENV_TERMUX_APP__VERSION_CODE: String = TERMUX_APP_ENV_PREFIX + "VERSION_CODE"

    /**
     * Environment variable for the Termux app package name.
     */
    private const val ENV_TERMUX_APP__PACKAGE_NAME: String = TERMUX_APP_ENV_PREFIX + "PACKAGE_NAME"

    /**
     * Environment variable for the Termux app process id.
     */
    private const val ENV_TERMUX_APP__PID: String = TERMUX_APP_ENV_PREFIX + "PID"

    /**
     * Environment variable for the Termux app uid.
     */
    private const val ENV_TERMUX_APP__UID: String = TERMUX_APP_ENV_PREFIX + "UID"

    /**
     * Environment variable for the Termux app targetSdkVersion.
     */
    private const val ENV_TERMUX_APP__TARGET_SDK: String = TERMUX_APP_ENV_PREFIX + "TARGET_SDK"

    /**
     * Environment variable for the Termux app install path.
     */
    private const val ENV_TERMUX_APP__APK_PATH: String = TERMUX_APP_ENV_PREFIX + "APK_PATH"

    /**
     * Environment variable for the Termux app process selinux context.
     */
    private const val ENV_TERMUX_APP__SE_PROCESS_CONTEXT: String =
        TERMUX_APP_ENV_PREFIX + "SE_PROCESS_CONTEXT"

    /**
     * Environment variable for the Termux app data files selinux context.
     */
    private const val ENV_TERMUX_APP__SE_FILE_CONTEXT: String =
        TERMUX_APP_ENV_PREFIX + "SE_FILE_CONTEXT"

    /**
     * Environment variable for the Termux app seInfo tag found in selinux policy used to set app process and app data files selinux context.
     */
    private const val ENV_TERMUX_APP__SE_INFO: String = TERMUX_APP_ENV_PREFIX + "SE_INFO"

    /**
     * Environment variable for the Termux app user id.
     */
    private const val ENV_TERMUX_APP__USER_ID: String = TERMUX_APP_ENV_PREFIX + "USER_ID"

    /**
     * Environment variable for the Termux app profile owner.
     */
    private const val ENV_TERMUX_APP__PROFILE_OWNER: String =
        TERMUX_APP_ENV_PREFIX + "PROFILE_OWNER"

    /**
     * Environment variable for the Termux app .
     */
    private const val ENV_TERMUX_APP__PACKAGE_MANAGER: String =
        TERMUX_APP_ENV_PREFIX + "PACKAGE_MANAGER"

    /**
     * Environment variable for the Termux app .
     */
    private const val ENV_TERMUX_APP__PACKAGE_VARIANT: String =
        TERMUX_APP_ENV_PREFIX + "PACKAGE_VARIANT"

    /**
     * Environment variable for the Termux app files directory.
     */
    private const val ENV_TERMUX_APP__FILES_DIR: String = TERMUX_APP_ENV_PREFIX + "FILES_DIR"

    /**
     * Termux app environment variables.
     */
    private var termuxAppEnvironment: HashMap<String, String>? = null

    /**
     * Get shell environment for Termux app.
     */
    @JvmStatic
    fun getEnvironment(currentPackageContext: Context): HashMap<String, String>? {
        setTermuxAppEnvironment(currentPackageContext)
        return termuxAppEnvironment
    }

    /**
     * Set Termux app environment variables in [.termuxAppEnvironment].
     */
    @JvmStatic
    @Synchronized
    fun setTermuxAppEnvironment(currentPackageContext: Context) {
        val isTermuxApp = TermuxConstants.TERMUX_PACKAGE_NAME == currentPackageContext.packageName
        // If current package context is of termux app and its environment is already set, then no need to set again since it won't change
        // Other apps should always set environment again since termux app may be installed/updated/deleted in background
        if (termuxAppEnvironment != null && isTermuxApp) return
        termuxAppEnvironment = null
        val packageName = TermuxConstants.TERMUX_PACKAGE_NAME
        val packageInfo = PackageUtils.getPackageInfoForPackage(currentPackageContext, packageName)
            ?: return
        val applicationInfo =
            PackageUtils.getApplicationInfoForPackage(currentPackageContext, packageName)
        if (!applicationInfo.enabled) return
        val environment = HashMap<String, String>()
        putToEnvIfSet(
            environment,
            ENV_TERMUX_VERSION,
            PackageUtils.getVersionNameForPackage(packageInfo)
        )
        putToEnvIfSet(
            environment,
            ENV_TERMUX_APP__VERSION_NAME,
            PackageUtils.getVersionNameForPackage(packageInfo)
        )
        putToEnvIfSet(
            environment,
            ENV_TERMUX_APP__VERSION_CODE,
            PackageUtils.getVersionCodeForPackage(packageInfo).toString()
        )
        putToEnvIfSet(environment, ENV_TERMUX_APP__PACKAGE_NAME, packageName)
        putToEnvIfSet(
            environment,
            ENV_TERMUX_APP__PID,
            TermuxUtils.getTermuxAppPID(currentPackageContext)
        )
        putToEnvIfSet(
            environment,
            ENV_TERMUX_APP__UID,
            PackageUtils.getUidForPackage(applicationInfo).toString()
        )
        putToEnvIfSet(
            environment,
            ENV_TERMUX_APP__TARGET_SDK,
            PackageUtils.getTargetSDKForPackage(applicationInfo).toString()
        )
        putToEnvIfSet(
            environment,
            ENV_TERMUX_APP__APK_PATH,
            PackageUtils.getBaseAPKPathForPackage(applicationInfo)
        )
        val termuxPackageContext = TermuxUtils.getTermuxPackageContext(currentPackageContext)
        if (termuxPackageContext != null) {
            // An app that does not have the same sharedUserId as termux app will not be able to get
            // get termux context's classloader to get BuildConfig.TERMUX_PACKAGE_VARIANT via reflection.
            // Check TermuxBootstrap.setTermuxPackageManagerAndVariantFromTermuxApp()

            environment[ENV_TERMUX_APP__PACKAGE_MANAGER] = "apt"
            environment[ENV_TERMUX_APP__PACKAGE_VARIANT] = "apt-android-7"
            // Will not be set for plugins
            //ShellEnvironmentUtils.putToEnvIfSet(environment, ENV_TERMUX_APP__AM_SOCKET_SERVER_ENABLED, TermuxAmSocketServer.getTermuxAppAMSocketServerEnabled(currentPackageContext));
            val filesDirPath = currentPackageContext.filesDir.absolutePath
            putToEnvIfSet(environment, ENV_TERMUX_APP__FILES_DIR, filesDirPath)
            putToEnvIfSet(
                environment,
                ENV_TERMUX_APP__SE_PROCESS_CONTEXT,
                SELinuxUtils.context
            )
            putToEnvIfSet(
                environment,
                ENV_TERMUX_APP__SE_FILE_CONTEXT,
                SELinuxUtils.getFileContext(filesDirPath)
            )
            val seInfoUser = PackageUtils.getApplicationInfoSeInfoUserForPackage(applicationInfo)
            putToEnvIfSet(
                environment,
                ENV_TERMUX_APP__SE_INFO,
                PackageUtils.getApplicationInfoSeInfoForPackage(applicationInfo) + (if (DataUtils.isNullOrEmpty(
                        seInfoUser
                    )
                ) "" else seInfoUser)
            )
            putToEnvIfSet(
                environment,
                ENV_TERMUX_APP__USER_ID,
                PackageUtils.getUserIdForPackage(currentPackageContext).toString()
            )
            putToEnvIfSet(
                environment,
                ENV_TERMUX_APP__PROFILE_OWNER,
                PackageUtils.getProfileOwnerPackageNameForUser(currentPackageContext)
            )
        }
        termuxAppEnvironment = environment
    }
}
