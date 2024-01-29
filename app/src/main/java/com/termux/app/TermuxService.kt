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
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession

/**
 * A service holding a list of [TerminalSession] in [sessions]
 * in , showing a foreground notification while running so that it is not terminated.
 * The user interacts with the session through [TermuxActivity], but this service may outlive
 * the activity when the user or the system disposes of the activity. In that case the user may
 * restart [TermuxActivity] later to yet again access the sessions.
 *
 *
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, [Service.startForeground].
 *
 *
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * [.buildNotification].
 */
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

    override fun onUnbind(intent: Intent): Boolean {
        // Since we cannot rely on {@link TermuxActivity.onDestroy()} to always complete,
        // we unset clients here as well if it failed, so that we do not leave service and session
        // clients with references to the activity.
        unsetTermuxTermuxTerminalSessionClientBase()
        return false
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

    /**
     * Kill all TerminalSessions and TermuxTasks by sending SIGKILL to their processes.
     *
     *
     * For TerminalSessions, all sessions will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown. The processing of results
     * will only be done if user manually exited termux or if the session was started by a plugin
     * which **expects** the result back via a pending intent.
     *
     *
     * For TermuxTasks, only tasks that were started by a plugin which **expects** the result
     * back via a pending intent will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown. The processing of results
     * will always be done for the tasks that are killed. The remaining processes will keep on
     * running until the termux app process is killed by android, like by OOM, so we let them run
     * as long as they can.
     *
     *
     * Some plugin execution commands may not have been processed and added to mTerminalSessions and
     * mTermuxTasks lists before the service is killed, so we maintain a separate
     * mPendingPluginExecutionCommands list for those, so that we can notify the pending intent
     * creators that execution was cancelled.
     *
     *
     * Note that if user didn't manually exit Termux and if onDestroy() was directly called because
     * of unintended shutdown, like android deciding to kill the service, then there will be no
     * guarantee that onDestroy() will be allowed to finish and termux app process may be killed before
     * it has finished. This means that in those cases some results may not be sent back to their
     * creators for plugin commands but we still try to process whatever results can be processed
     * despite the unreliable behaviour of onDestroy().
     *
     *
     * Note that if don't kill the processes started by plugins which **expect** the result back
     * and notify their creators that they have been killed, then they may get stuck waiting for
     * the results forever like in case of commands started by Termux:Tasker or RUN_COMMAND intent,
     * since once TermuxService has been killed, no result will be sent back. They may still get
     * stuck if termux app process gets killed, so for this case reasonable timeout values should
     * be used, like in Tasker for the Termux:Tasker actions.
     *
     *
     * We make copies of each list since items are removed inside the loop.
     */
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


    /**
     * This should be called when [TermuxActivity.onServiceConnected] is called to set the
     * [TermuxService.mTermuxTerminalSessionActivityClient] variable and update the [TerminalSession]
     * and [TerminalEmulator] clients in case they were passed [TermuxTerminalSessionServiceClient]
     * earlier.
     *
     * @param termuxTerminalSessionActivityClient The [TermuxTerminalSessionActivityClient] object that fully
     * implements the {link TermuxTerminalSessionClientBase} interface.
     */
    @Synchronized
    fun setTermuxTermuxTerminalSessionClientBase(termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient) {
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient
        for (i in sessions.indices) sessions[i].updateTerminalSessionClient(
            mTermuxTerminalSessionActivityClient
        )
    }

    /**
     * This should be called when [TermuxActivity] has been destroyed and in [.onUnbind]
     * so that the  and [TerminalSession] and [TerminalEmulator]
     * clients do not hold an activity references.
     */
    @Synchronized
    fun unsetTermuxTermuxTerminalSessionClientBase() {
        //TODO
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
