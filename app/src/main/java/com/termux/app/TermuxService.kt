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
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.terminal.TerminalSession

class TermuxService : Service() {
    private val mBinder: IBinder = LocalBinder()

    /**
     * If the user has executed the [TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE] intent.
     */
    private var mWantsToStop = false

    /**
     * The full implementation of the {link TermuxTerminalSessionClientBase} interface to be used by [TerminalSession]
     * that holds activity references for activity related functions.
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    private lateinit var mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient

    /**
     * Termux app shell manager
     */
    private val sessions = mutableListOf<TerminalSession>()
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Run again in case service is already started and onCreate() is not called
        runStartForeground()
        val action: String? = intent.action
        if (null != action) {
            if (action == TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE) {
                actionStopService()
            }
        }
        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
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
        this.startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification())
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
        val failsafeCheck = isFailSafe || !TermuxConstants.TERMUX_PREFIX_DIR.exists()
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
                TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID,
                "s",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return Notification.Builder(this, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID)
            .setOngoing(true).setSmallIcon(
                R.drawable.rsq
            ).setContentIntent(
                PendingIntent.getService(
                    this,
                    0,
                    Intent(
                        this,
                        TermuxService::class.java
                    ).setAction(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE),
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
        val service = this@TermuxService
    }
}
