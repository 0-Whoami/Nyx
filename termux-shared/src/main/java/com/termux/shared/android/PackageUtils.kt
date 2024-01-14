package com.termux.shared.android

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.UserHandle
import android.os.UserManager
import com.termux.shared.reflection.ReflectionUtils

object PackageUtils {
    /**
     * Get the [Context] for the package name with [Context.CONTEXT_RESTRICTED] flags.
     *
     * @param context     The [Context] to use to get the [Context] of the `packageName`.
     * @param packageName The package name whose [Context] to get.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    @JvmStatic
    fun getContextForPackage(context: Context, packageName: String?): Context? {
        return context.createPackageContext(packageName, Context.CONTEXT_RESTRICTED)
    }

    /**
     * Get the [PackageInfo] for the package associated with the `packageName`.
     *
     * @param context     The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the [PackageInfo]. This will be `null` if an exception is raised.
     */
    fun getPackageInfoForPackage(context: Context, packageName: String?): PackageInfo? {
        return context.packageManager.getPackageInfo(packageName ?: return null, 0)
    }


    /**
     * Get the [ApplicationInfo] for the `packageName`.
     *
     * @param context     The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the [ApplicationInfo]. This will be `null` if an exception is raised.
     */
    fun getApplicationInfoForPackage(context: Context, packageName: String?): ApplicationInfo {
        return context.packageManager.getApplicationInfo(packageName!!, 0)
    }

    /**
     * Get the `seInfo` [Field] of the [ApplicationInfo] class.
     *
     *
     * String retrieved from the seinfo tag found in selinux policy. This value can be set through
     * the mac_permissions.xml policy construct. This value is used for setting an SELinux security
     * context on the process as well as its data directory.
     *
     * [* https://cs.android.com/android/platform/superproject/+/android-7.1.0_r1:frameworks/base/core/java/android/content/pm/ApplicationInfo.ja](
      )va;l=609[* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/content/pm/ApplicationInfo.ja](
      )va;l=981[* https://cs.android.com/android/platform/superproject/+/android-7.0.0_r1:frameworks/base/services/core/java/com/android/server/pm/SELinuxMMAC.ja](
      )va;l=282[* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/pm/SELinuxMMAC.ja](
      )va;l=375[* https://cs.android.com/android/_/android/platform/frameworks/base/+/be0b8896d1bc385d4c8fb54c21929745](
      )935dcbea
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the selinux info or `null` if an exception was raised.
     */
    fun getApplicationInfoSeInfoForPackage(applicationInfo: ApplicationInfo): String? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            ReflectionUtils.invokeField(
                ApplicationInfo::class.java,
                "seInfo",
                applicationInfo
            ).value as String
        } catch (e: Exception) {
            // ClassCastException may be thrown
            null
        }
    }

    /**
     * Get the `seInfoUser` [Field] of the [ApplicationInfo] class.
     *
     *
     * Also check [.getApplicationInfoSeInfoForPackage].
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the selinux info user or `null` if an exception was raised.
     */
    fun getApplicationInfoSeInfoUserForPackage(applicationInfo: ApplicationInfo): String? {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        return try {
            ReflectionUtils.invokeField(
                ApplicationInfo::class.java,
                "seInfoUser",
                applicationInfo
            ).value as String
        } catch (e: Exception) {
            // ClassCastException may be thrown
            null
        }
    }

    /**
     * Get the uid for the package associated with the `context`.
     *
     * @param context The [Context] for the package.
     * @return Returns the uid.
     */
    private fun getUidForPackage(context: Context): Int {
        return getUidForPackage(context.applicationInfo)
    }

    /**
     * Get the uid for the package associated with the `applicationInfo`.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the uid.
     */
    fun getUidForPackage(applicationInfo: ApplicationInfo): Int {
        return applicationInfo.uid
    }

    /**
     * Get the `targetSdkVersion` for the package associated with the `applicationInfo`.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the `targetSdkVersion`.
     */
    fun getTargetSDKForPackage(applicationInfo: ApplicationInfo): Int {
        return applicationInfo.targetSdkVersion
    }

    /**
     * Get the base apk path for the package associated with the `applicationInfo`.
     *
     * @param applicationInfo The [ApplicationInfo] for the package.
     * @return Returns the base apk path.
     */
    fun getBaseAPKPathForPackage(applicationInfo: ApplicationInfo): String {
        return applicationInfo.publicSourceDir
    }


    /**
     * Get the `versionCode` for the `packageName`.
     *
     * @param packageInfo The [PackageInfo] for the package.
     * @return Returns the `versionCode`. This will be `null` if an exception is raised.
     */
    fun getVersionCodeForPackage(packageInfo: PackageInfo?): Int? {
        return packageInfo?.longVersionCode?.toInt()
    }

    /**
     * Get the `versionName` for the `packageName`.
     *
     * @param packageInfo The [PackageInfo] for the package.
     * @return Returns the `versionName`. This will be `null` if an `packageInfo`
     * is `null`.
     */
    fun getVersionNameForPackage(packageInfo: PackageInfo?): String? {
        return packageInfo?.versionName
    }


    /**
     * Get the serial number for the user for the package associated with the `context`.
     *
     * @param context The [Context] for the package.
     * @return Returns the serial number. This will be `null` if failed to get it.
     */
    fun getUserIdForPackage(context: Context): Long {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.getSerialNumberForUser(
            UserHandle.getUserHandleForUid(
                getUidForPackage(
                    context
                )
            )
        )
    }

    /**
     * Get the profile owner package name for the current user.
     *
     * @param context The [Context] for operations.
     * @return Returns the profile owner package name. This will be `null` if failed to get it
     * or no profile owner for the current user.
     */
    fun getProfileOwnerPackageNameForUser(context: Context): String? {
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activeAdmins = devicePolicyManager.activeAdmins
        if (activeAdmins != null) {
            for (admin in activeAdmins) {
                val packageName = admin.packageName
                if (devicePolicyManager.isProfileOwnerApp(packageName)) return packageName
            }
        }
        return null
    }

    /**
     * Get the process id of the main app process of a package. This will work for sharedUserId. Note
     * that some apps have multiple processes for the app like with `android:process=":background"`
     * attribute in AndroidManifest.xml.
     *
     * @param context     The [Context] for operations.
     * @param packageName The package name of the process.
     * @return Returns the process if found and running, otherwise `null`.
     */
    @JvmStatic
    fun getPackagePID(context: Context, packageName: String): String? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processInfos = activityManager.runningAppProcesses
        if (processInfos != null) {
            var processInfo: RunningAppProcessInfo
            for (i in processInfos.indices) {
                processInfo = processInfos[i]
                if (processInfo.processName == packageName) return processInfo.pid.toString()
            }
        }
        return null
    }
}
