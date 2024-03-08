package com.termux.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.termux.R
import com.termux.terminal.TerminalSession
import com.termux.terminal.TermuxTerminalSessionActivityClient
import com.termux.utils.data.ConfigManager.ACTION_STOP_SERVICE
import com.termux.utils.data.ConfigManager.CHANNEL_ID
import com.termux.utils.data.ConfigManager.NOTIFICATION_ID
import com.termux.utils.data.ConfigManager.PREFIX_DIR

class service : Service() {
    private val mBinder: IBinder = LocalBinder()

    /**
     * If the user has executed the [ACTION_STOP_SERVICE] intent.
     */
    private var mWantsToStop = false

    /**
     * The full implementation of the {link TermuxTerminalSessionClientBase} interface to be used by [TerminalSession]
     * that holds activity references for activity related functions.
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    lateinit var mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient

    /**
     * List of Sessions
     */
    private val sessions = mutableListOf<TerminalSession>()
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        runStartForeground()
        val action: String? = intent.action
        if (null != action) {
            if (action == ACTION_STOP_SERVICE) {
                actionStopService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!mWantsToStop) killAllTermuxExecutionCommands()
        runStopForeground()
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    /**
     * Make service run in foreground mode.
     */
    private fun runStartForeground() {
        this.startForeground(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Make service leave foreground mode.
     */
    private fun runStopForeground() {
        this.stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Request to stop service.
     */
    private fun requestStopService() {
        runStopForeground()
        this.stopSelf()
    }

    /**
     * Process action to stop service.
     */
    private fun actionStopService() {
        mWantsToStop = true
        killAllTermuxExecutionCommands()
        requestStopService()
    }

    @Synchronized
    private fun killAllTermuxExecutionCommands() {
        var processResult: Boolean
        for (i in TerminalSessions.indices) {
            processResult = mWantsToStop
            TerminalSessions[i].finishIfRunning()
            if (!processResult) sessions.removeAt(i)
        }
    }

    /**
     * Create a [TerminalSession].
     */
    @Synchronized
    fun createTerminalSession(isFailSafe: Boolean): TerminalSession {
        val failsafeCheck = isFailSafe || !PREFIX_DIR.exists()
        val newTerminalSession =
            TerminalSession(failsafeCheck, mTermuxTerminalSessionActivityClient)
        return newTerminalSession
    }

    /**
     * Remove a TerminalSession.
     */
    @Synchronized
    fun removeTerminalSession(sessionToRemove: TerminalSession): Int {
        val index = getIndexOfSession(sessionToRemove)
        sessions[index].finishIfRunning()
        sessions.remove(sessionToRemove)
        return index
    }

    @Synchronized
    fun setTermuxTermuxTerminalSessionClientBase(termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient) {
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient
        for (i in sessions.indices) sessions[i].updateTerminalSessionClient(
            mTermuxTerminalSessionActivityClient
        )
    }

    private fun buildNotification(): Notification {
        (this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "s",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setOngoing(true).setSmallIcon(
                R.mipmap.text_select_handle_material
            ).setContentIntent(
                PendingIntent.getService(
                    this,
                    0,
                    Intent(
                        this,
                        service::class.java
                    ).setAction(ACTION_STOP_SERVICE),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).setContentText("Exit").build()
    }

    @get:Synchronized
    val isTerminalSessionsEmpty: Boolean
        get() = sessions.isEmpty()

    @get:Synchronized
    val TerminalSessionsSize: Int
        get() = sessions.size

    @get:Synchronized
    val TerminalSessions: MutableList<TerminalSession>
        get() = sessions

    @Synchronized
    fun getIndexOfSession(terminalSession: TerminalSession): Int {
        return sessions.indexOf(terminalSession)
    }

    fun wantsToStop(): Boolean {
        return mWantsToStop
    }

    /**
     * This service is only bound from inside the same process and never uses IPC.
     */
    internal inner class LocalBinder : Binder() {
        val service = this@service
    }
}
