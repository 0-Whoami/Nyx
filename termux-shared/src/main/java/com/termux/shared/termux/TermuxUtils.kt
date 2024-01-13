package com.termux.shared.termux

import android.content.Context
import com.termux.shared.android.PackageUtils.getContextForPackage
import com.termux.shared.android.PackageUtils.getPackagePID

object TermuxUtils {
    /**
     * Get the [Context] for [TermuxConstants.TERMUX_PACKAGE_NAME] package with the
     * [Context.CONTEXT_RESTRICTED] flag.
     *
     * @param context The [Context] to use to get the [Context] of the package.
     * @return Returns the [Context]. This will `null` if an exception is raised.
     */
    fun getTermuxPackageContext(context: Context?): Context? {
        return getContextForPackage(context!!, TermuxConstants.TERMUX_PACKAGE_NAME)
    }

    /**
     * Get a process id of the main app process of the [TermuxConstants.TERMUX_PACKAGE_NAME]
     * package.
     *
     * @param context The context for operations.
     * @return Returns the process if found and running, otherwise `null`.
     */
    fun getTermuxAppPID(context: Context?): String? {
        return getPackagePID(context!!, TermuxConstants.TERMUX_PACKAGE_NAME)
    }
}
