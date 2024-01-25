package com.termux.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalSessionServiceClient
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.TermuxShellManager.Companion.nextShellId
import com.termux.shared.termux.shell.TermuxShellManager.Companion.onAppExit
import com.termux.shared.termux.shell.TermuxShellManager.Companion.shellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession.TermuxSessionClient
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.R

/**
 * A service holding a list of [TermuxSession] in [TermuxShellManager.mTermuxSessions] and background {Appshell}
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
class TermuxService : Service(), TermuxSessionClient {
    private val mBinder: IBinder = LocalBinder()

    /**
     * The basic implementation of the {link TermuxTerminalSessionClientBase} interface to be used by [TerminalSession]
     * that does not hold activity references and only a service reference.
     */
    private val mTermuxTerminalSessionServiceClient = TermuxTerminalSessionServiceClient(this)

    /**
     * If the user has executed the [TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE] intent.
     */
    private var mWantsToStop = false

    /**
     * The full implementation of the {link TermuxTerminalSessionClientBase} interface to be used by [TerminalSession]
     * that holds activity references for activity related functions.
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    private var mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient? = null

    /**
     * Termux app shell manager
     */
    private lateinit var mShellManager: TermuxShellManager
    override fun onCreate() {
        // Get Termux app SharedProperties without loading from disk since TermuxApplication handles
        // load and TermuxActivity handles reloads
        mShellManager = shellManager!!
        runStartForeground()
    }

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
        onAppExit()
        runStopForeground()
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // Since we cannot rely on {@link TermuxActivity.onDestroy()} to always complete,
        // we unset clients here as well if it failed, so that we do not leave service and session
        // clients with references to the activity.
        if (null != mTermuxTerminalSessionActivityClient) unsetTermuxTermuxTerminalSessionClientBase()
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
     * Kill all TermuxSessions and TermuxTasks by sending SIGKILL to their processes.
     *
     *
     * For TermuxSessions, all sessions will be killed, whether user manually exited Termux or if
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
     * Some plugin execution commands may not have been processed and added to mTermuxSessions and
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
        val termuxSessions: List<TermuxSession> = ArrayList(
            mShellManager.mTermuxSessions
        )
        for (i in termuxSessions.indices) {
            processResult = mWantsToStop
            termuxSessions[i].killIfExecuting()
            if (!processResult) mShellManager.mTermuxSessions.remove(termuxSessions[i])
        }
    }

    /**
     * Create a [TermuxSession].
     * Currently called by [TermuxTerminalSessionActivityClient.addNewSession] to add a new [TermuxSession].
     */
    fun createTermuxSession(
        executablePath: String?,
        arguments: Array<String>?,
        workingDirectory: String?,
        isFailSafe: Boolean,
        sessionName: String?
    ): TermuxSession? {
        val executionCommand = ExecutionCommand(
            nextShellId,
            executablePath,
            arguments,
            workingDirectory,
            ExecutionCommand.Runner.TERMINAL_SESSION.value,
            isFailSafe
        )
        executionCommand.shellName = sessionName
        return this.createTermuxSession(executionCommand)
    }

    /**
     * Create a [TermuxSession].
     */
    @Synchronized
    private fun createTermuxSession(executionCommand: ExecutionCommand?): TermuxSession? {
        if (null == executionCommand) return null
        if (ExecutionCommand.Runner.TERMINAL_SESSION.value != executionCommand.runner) {
            return null
        }
        executionCommand.setShellCommandShellEnvironment = true
        executionCommand.terminalTranscriptRows = 150
        // If the execution command was started for a plugin, only then will the stdout be set
        // Otherwise if command was manually started by the user like by adding a new terminal session,
        // then no need to set stdout
        val newTermuxSession = TermuxSession.execute(
            this,
            executionCommand,
            termuxTermuxTerminalSessionClientBase,
            this,
            TermuxShellEnvironment(),
            null
        )
            ?: // If the execution command was started for a plugin, then process the error
            return null
        newTermuxSession.terminalSession.setBoldWithBright(true)
        mShellManager.mTermuxSessions.add(newTermuxSession)
        // Remove the execution command from the pending plugin execution commands list since it has
        // now been processed
        // Notify {@link TermuxSessionsListViewController} that sessions list has been updated if
        // activity in is foreground
        updateNotification()
        // No need to recreate the activity since it likely just started and theme should already have applied
//        TermuxActivity.updateTermuxActivityStyling(this, false);
        return newTermuxSession
    }

    /**
     * Remove a TermuxSession.
     */
    @Synchronized
    fun removeTermuxSession(sessionToRemove: TerminalSession?): Int {
        val index = getIndexOfSession(sessionToRemove)
        if (0 <= index) mShellManager.mTermuxSessions[index].finish()
        return index
    }

    /**
     * Callback received when a [TermuxSession] finishes.
     */
    override fun onTermuxSessionExited(termuxSession: TermuxSession) {
        mShellManager.mTermuxSessions.remove(termuxSession)
        updateNotification()
    }

    @get:Synchronized
    private val termuxTermuxTerminalSessionClientBase: TerminalSessionClient
        /**
         * If [TermuxActivity] has not bound to the  yet or is destroyed, then
         * interface functions requiring the activity should not be available to the terminal sessions,
         * so we just return the [.mTermuxTerminalSessionServiceClient]. Once [TermuxActivity] bind
         * callback is received, it should call [.setTermuxTermuxTerminalSessionClientBase] to set the
         * [TermuxService.mTermuxTerminalSessionActivityClient] so that further terminal sessions are directly
         * passed the [TermuxTerminalSessionActivityClient] object which fully implements the
         * {link TermuxTerminalSessionClientBase} interface.
         *
         * @return Returns the [TermuxTerminalSessionActivityClient] if [TermuxActivity] has bound with
         * , otherwise [TermuxTerminalSessionServiceClient].
         */
        get() =
            mTermuxTerminalSessionActivityClient ?: mTermuxTerminalSessionServiceClient


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
        for (i in mShellManager.mTermuxSessions.indices) mShellManager.mTermuxSessions[i].terminalSession.updateTerminalSessionClient(
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
        for (i in mShellManager.mTermuxSessions.indices) mShellManager.mTermuxSessions[i].terminalSession.updateTerminalSessionClient(
            mTermuxTerminalSessionServiceClient
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
            ).setContentText("Click to Exit").build()
    }

    /**
     * Update the shown foreground service notification after making any changes that affect it.
     */
    @Synchronized
    private fun updateNotification() {
        if (mShellManager.mTermuxSessions.isEmpty()) {
            // Exit if we are updating after the user disabled all locks with no sessions or tasks running.
            requestStopService()
        }
    }

    @get:Synchronized
    val isTermuxSessionsEmpty: Boolean
        get() = mShellManager.mTermuxSessions.isEmpty()

    @get:Synchronized
    val termuxSessionsSize: Int
        get() = mShellManager.mTermuxSessions.size

    @get:Synchronized
    val termuxSessions: List<TermuxSession>
        get() = mShellManager.mTermuxSessions

    @Synchronized
    fun getTermuxSession(index: Int): TermuxSession? {
        return if (0 <= index && index < mShellManager.mTermuxSessions.size) mShellManager.mTermuxSessions[index] else null
    }

    @Synchronized
    fun getTermuxSessionForTerminalSession(terminalSession: TerminalSession?): TermuxSession? {
        if (null == terminalSession) return null
        for (i in mShellManager.mTermuxSessions.indices) {
            if (mShellManager.mTermuxSessions[i].terminalSession == terminalSession) return mShellManager.mTermuxSessions[i]
        }
        return null
    }

    @get:Synchronized
    val lastTermuxSession: TermuxSession?
        get() = if (mShellManager.mTermuxSessions.isEmpty()) null else mShellManager.mTermuxSessions[mShellManager.mTermuxSessions.size - 1]

    @Synchronized
    fun getIndexOfSession(terminalSession: TerminalSession?): Int {
        if (null == terminalSession) return -1
        for (i in mShellManager.mTermuxSessions.indices) {
            if (mShellManager.mTermuxSessions[i].terminalSession == terminalSession) return i
        }
        return -1
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
