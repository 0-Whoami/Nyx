package com.termux.shared.termux.shell

import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession

class TermuxShellManager {
    /**
     * The foreground TermuxSessions which this service manages.
     * Note that this list is observed by an activity, like TermuxActivity.mTermuxSessionListViewController,
     * so any changes must be made on the UI thread and followed by a call to
     * [ArrayAdapter.notifyDataSetChanged].
     */
    @JvmField
    val mTermuxSessions: List<TermuxSession> = ArrayList()

    companion object {
        /**
         * Get the [.shellManager].
         *
         * @return Returns the [TermuxShellManager].
         */
        @JvmStatic
        var shellManager: TermuxShellManager? = null
            private set

        private var SHELL_ID = 0

        /**
         * The  number after app process was started/restarted.
         */
        private var APP_SHELL_NUMBER_SINCE_APP_START = 0

        /**
         * The  number after app process was started/restarted.
         */
        private var TERMINAL_SESSION_NUMBER_SINCE_APP_START = 0

        /**
         * Initialize the [.shellManager].
         */
        fun init() {
            if (shellManager == null) shellManager = TermuxShellManager()
        }

        @JvmStatic
        fun onAppExit() {
            // Ensure any shells started after boot have valid ENV_SHELL_CMD__APP_SHELL_NUMBER_SINCE_APP_START and
            // ENV_SHELL_CMD__TERMINAL_SESSION_NUMBER_SINCE_APP_START exported
            APP_SHELL_NUMBER_SINCE_APP_START = 0
            TERMINAL_SESSION_NUMBER_SINCE_APP_START = 0
        }

        @JvmStatic
        @get:Synchronized
        val nextShellId: Int
            get() = SHELL_ID++

        @get:Synchronized
        val andIncrementAppShellNumberSinceAppStart: Int
            get() {
                // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
                var curValue = APP_SHELL_NUMBER_SINCE_APP_START
                if (curValue < 0) curValue = Int.MAX_VALUE
                APP_SHELL_NUMBER_SINCE_APP_START = curValue + 1
                if (APP_SHELL_NUMBER_SINCE_APP_START < 0) APP_SHELL_NUMBER_SINCE_APP_START =
                    Int.MAX_VALUE
                return curValue
            }

        @get:Synchronized
        val andIncrementTerminalSessionNumberSinceAppStart: Int
            get() {
                // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
                var curValue = TERMINAL_SESSION_NUMBER_SINCE_APP_START
                if (curValue < 0) curValue = Int.MAX_VALUE
                TERMINAL_SESSION_NUMBER_SINCE_APP_START = curValue + 1
                if (TERMINAL_SESSION_NUMBER_SINCE_APP_START < 0) TERMINAL_SESSION_NUMBER_SINCE_APP_START =
                    Int.MAX_VALUE
                return curValue
            }
    }
}