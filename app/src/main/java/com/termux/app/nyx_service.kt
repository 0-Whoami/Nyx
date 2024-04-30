package com.termux.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.termux.terminal.TerminalSession
import com.termux.utils.data.NYX_SERVICE.ACTION_STOP_SERVICE
import com.termux.utils.data.NYX_SERVICE.CHANNEL_ID
import com.termux.utils.data.NYX_SERVICE.NOTIFICATION_ID

class nyx_service : Service() {
    private val mBinder: IBinder = LocalBinder()

    /**
     * If the user has executed the [ACTION_STOP_SERVICE] intent.
     */
    private var mWantsToStop = false


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
     * Make nyx_service run in foreground mode.
     */
    private fun runStartForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Make nyx_service leave foreground mode.
     */
    private fun runStopForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Request to stop nyx_service.
     */
    private fun requestStopService() {
        runStopForeground()
        stopSelf()
    }

    /**
     * Process action to stop nyx_service.
     */
    private fun actionStopService() {
        mWantsToStop = true
        killAllTermuxExecutionCommands()
        requestStopService()
    }

    private fun killAllTermuxExecutionCommands() {
        var processResult: Boolean
        for (i in TerminalSessions.indices) {
            processResult = mWantsToStop
            TerminalSessions[i].finishIfRunning()
            if (!processResult) sessions.removeAt(i)
        }
    }


    /**
     * Remove a TerminalSession.
     */
    fun removeTerminalSession(sessionToRemove: TerminalSession): Int {
        sessionToRemove.finishIfRunning()
        sessions.remove(sessionToRemove)
        return sessions.size - 1
    }


    private fun buildNotification(): Notification {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "n",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getService(
                    this,
                    0,
                    Intent(
                        this,
                        nyx_service::class.java
                    ).setAction(ACTION_STOP_SERVICE),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).setContentText("Exit").build()
    }

    val isTerminalSessionsEmpty: Boolean
        get() = sessions.isEmpty()


    val TerminalSessions: MutableList<TerminalSession>
        get() = sessions


    fun wantsToStop(): Boolean {
        return mWantsToStop
    }

    /**
     * This nyx_service is only bound from inside the same process and never uses IPC.
     */
    internal inner class LocalBinder : Binder() {
        val nyx_service = this@nyx_service
    }
}
