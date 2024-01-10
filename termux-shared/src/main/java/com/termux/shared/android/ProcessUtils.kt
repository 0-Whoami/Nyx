package com.termux.shared.android

import android.app.ActivityManager
import android.content.Context

object ProcessUtils {
    /**
     * Get the app process name for a pid with a call to [ActivityManager.getRunningAppProcesses].
     *
     *
     * This will not return child process names. Android did not keep track of them before android 12
     * phantom process addition, but there is no API via IActivityManager to get them.
     *
     *
     * To get process name for pids of own app's child processes, check `get_process_name_from_cmdline()`
     * in `local-socket.cpp`.
     * [...]( <p>
      https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ActivityManager).java;l=[* https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/ActivityManagerService](3362
      ).java;l=[* https://cs.android.com/android/_/android/platform/frameworks/base/+/refs/tags/android-12.0.0_r32:services/core/java/com/android/server/am/PhantomProc](8434
      )essList.[* https://cs.android.com/android/_/android/platform/frameworks/base/+/refs/tags/android-12.0.0_r32:services/core/java/com/android/server/am/PhantomProces](java
      )sRecord.java
     *
     * @param context The [Context] for operations.
     * @param pid     The pid of the process.
     * @return Returns the app process name if found, otherwise `null`.
     */
    @JvmStatic
    fun getAppProcessNameForPid(context: Context, pid: Int): String? {
        if (pid < 0) return null
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            val runningApps = activityManager.runningAppProcesses ?: return null
            for (procInfo in runningApps) {
                if (procInfo.pid == pid) {
                    return procInfo.processName
                }
            }
        } catch (ignored: Exception) {
        }
        return null
    }
}
