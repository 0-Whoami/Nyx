package com.termux.shared.android

import com.termux.shared.reflection.ReflectionUtils.bypassHiddenAPIReflectionRestrictions
import com.termux.shared.reflection.ReflectionUtils.getDeclaredMethod
import com.termux.shared.reflection.ReflectionUtils.invokeMethod


object SELinuxUtils {
    private const val ANDROID_OS_SELINUX_CLASS: String = "android.os.SELinux"


    val context: String?
        /**
         * Gets the security context of the current process.
         *
         * @return Returns a [String] representing the security context of the current process.
         * This will be `null` if an exception is raised.
         */
        get() {
            bypassHiddenAPIReflectionRestrictions()
            val methodName = "getContext"
            try {
                val clazz = Class.forName(ANDROID_OS_SELINUX_CLASS)
                val method = getDeclaredMethod(clazz, methodName) ?: return null
                return invokeMethod(method, null).value as String?
            } catch (e: Exception) {
                return null
            }
        }

    /**
     * Get the security context of a file object.
     *
     * @param path The pathname of the file object.
     * @return Returns a [String] representing the security context of the file.
     * This will be `null` if an exception is raised.
     */
    fun getFileContext(path: String?): String? {
        bypassHiddenAPIReflectionRestrictions()
        val methodName = "getFileContext"
        try {
            val clazz = Class.forName(ANDROID_OS_SELINUX_CLASS)
            val method = getDeclaredMethod(clazz, methodName, String::class.java)
                ?: return null
            return invokeMethod(method, null, path).value as String?
        } catch (e: Exception) {
            return null
        }
    }
}
