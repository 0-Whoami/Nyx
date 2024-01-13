package com.termux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalSessionServiceClient;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A service holding a list of {@link TermuxSession} in {@link TermuxShellManager#mTermuxSessions} and background {Appshell}
 * in , showing a foreground notification while running so that it is not terminated.
 * The user interacts with the session through {@link TermuxActivity}, but this service may outlive
 * the activity when the user or the system disposes of the activity. In that case the user may
 * restart {@link TermuxActivity} later to yet again access the sessions.
 * <p/>
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, {@link Service#startForeground(int, Notification)}.
 * <p/>
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * {@link #buildNotification()}.
 */
public final class TermuxService extends Service implements TermuxSession.TermuxSessionClient {

    private final IBinder mBinder = new LocalBinder();
    /**
     * The basic implementation of the {link TermuxTerminalSessionClientBase} interface to be used by {@link TerminalSession}
     * that does not hold activity references and only a service reference.
     */
    private final TermuxTerminalSessionServiceClient mTermuxTerminalSessionServiceClient = new TermuxTerminalSessionServiceClient(this);
    /**
     * If the user has executed the {@link TermuxConstants.TERMUX_APP.TERMUX_SERVICE#ACTION_STOP_SERVICE} intent.
     */
    private boolean mWantsToStop;
    /**
     * The full implementation of the {link TermuxTerminalSessionClientBase} interface to be used by {@link TerminalSession}
     * that holds activity references for activity related functions.
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    private TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shell manager
     */
    private TermuxShellManager mShellManager;


    @Override
    public void onCreate() {
        // Get Termux app SharedProperties without loading from disk since TermuxApplication handles
        // load and TermuxActivity handles reloads

        this.mShellManager = TermuxShellManager.getShellManager();
        this.runStartForeground();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        // Run again in case service is already started and onCreate() is not called
        this.runStartForeground();
        String action = null;
        if (null != intent) {
            action = intent.getAction();
        }
        if (null != action) {
            if (action.equals(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE)) {
                this.actionStopService();
            }
        }
        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (!this.mWantsToStop)
            this.killAllTermuxExecutionCommands();
        TermuxShellManager.onAppExit();
        this.runStopForeground();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return this.mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        // Since we cannot rely on {@link TermuxActivity.onDestroy()} to always complete,
        // we unset clients here as well if it failed, so that we do not leave service and session
        // clients with references to the activity.
        if (null != mTermuxTerminalSessionActivityClient)
            this.unsetTermuxTermuxTerminalSessionClientBase();
        return false;
    }

    /**
     * Make service run in foreground mode.
     */
    private void runStartForeground() {
        this.startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, this.buildNotification());
    }

    /**
     * Make service leave foreground mode.
     */
    private void runStopForeground() {
        this.stopForeground(Service.STOP_FOREGROUND_REMOVE);
    }

    /**
     * Request to stop service.
     */
    private void requestStopService() {
        this.runStopForeground();
        this.stopSelf();
    }

    /**
     * Process action to stop service.
     */
    private void actionStopService() {
        this.mWantsToStop = true;
        this.killAllTermuxExecutionCommands();
        this.requestStopService();
    }

    /**
     * Kill all TermuxSessions and TermuxTasks by sending SIGKILL to their processes.
     * <p>
     * For TermuxSessions, all sessions will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown. The processing of results
     * will only be done if user manually exited termux or if the session was started by a plugin
     * which **expects** the result back via a pending intent.
     * <p>
     * For TermuxTasks, only tasks that were started by a plugin which **expects** the result
     * back via a pending intent will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown. The processing of results
     * will always be done for the tasks that are killed. The remaining processes will keep on
     * running until the termux app process is killed by android, like by OOM, so we let them run
     * as long as they can.
     * <p>
     * Some plugin execution commands may not have been processed and added to mTermuxSessions and
     * mTermuxTasks lists before the service is killed, so we maintain a separate
     * mPendingPluginExecutionCommands list for those, so that we can notify the pending intent
     * creators that execution was cancelled.
     * <p>
     * Note that if user didn't manually exit Termux and if onDestroy() was directly called because
     * of unintended shutdown, like android deciding to kill the service, then there will be no
     * guarantee that onDestroy() will be allowed to finish and termux app process may be killed before
     * it has finished. This means that in those cases some results may not be sent back to their
     * creators for plugin commands but we still try to process whatever results can be processed
     * despite the unreliable behaviour of onDestroy().
     * <p>
     * Note that if don't kill the processes started by plugins which **expect** the result back
     * and notify their creators that they have been killed, then they may get stuck waiting for
     * the results forever like in case of commands started by Termux:Tasker or RUN_COMMAND intent,
     * since once TermuxService has been killed, no result will be sent back. They may still get
     * stuck if termux app process gets killed, so for this case reasonable timeout values should
     * be used, like in Tasker for the Termux:Tasker actions.
     * <p>
     * We make copies of each list since items are removed inside the loop.
     */
    private synchronized void killAllTermuxExecutionCommands() {
        boolean processResult;
        final List<TermuxSession> termuxSessions = new ArrayList<>(this.mShellManager.mTermuxSessions);
        for (int i = 0; i < termuxSessions.size(); i++) {
            processResult = this.mWantsToStop;
            termuxSessions.get(i).killIfExecuting();
            if (!processResult)
                this.mShellManager.mTermuxSessions.remove(termuxSessions.get(i));
        }
    }
// --Commented out by Inspection START (07-10-2023 11:13 am):
//    /**
//     * Create a TermuxTask.
//     */
//    @Nullable
//    public AppShell createTermuxTask(String executablePath, String[] arguments, String stdin, String workingDirectory) {
//        return createTermuxTask(new ExecutionCommand(TermuxShellManager.getNextShellId(), executablePath, arguments, stdin, workingDirectory, Runner.APP_SHELL.getName(), false));
//    }
// --Commented out by Inspection STOP (07-10-2023 11:13 am)

    /**
     * Create a {@link TermuxSession}.
     * Currently called by {@link TermuxTerminalSessionActivityClient#addNewSession(boolean, String)} to add a new {@link TermuxSession}.
     */
    @Nullable
    public TermuxSession createTermuxSession(final String executablePath, final String[] arguments, final String stdin, final String workingDirectory, final boolean isFailSafe, final String sessionName) {
        final ExecutionCommand executionCommand = new ExecutionCommand(TermuxShellManager.getNextShellId(), executablePath, arguments, stdin, workingDirectory, ExecutionCommand.Runner.TERMINAL_SESSION.INSTANCE.getValue(), isFailSafe);
        executionCommand.shellName = sessionName;
        return this.createTermuxSession(executionCommand);
    }

    /**
     * Create a {@link TermuxSession}.
     */
    @Nullable
    private synchronized TermuxSession createTermuxSession(final ExecutionCommand executionCommand) {
        if (null == executionCommand)
            return null;
        if (!ExecutionCommand.Runner.TERMINAL_SESSION.INSTANCE.getValue().equals(executionCommand.runner)) {
            return null;
        }
        executionCommand.setShellCommandShellEnvironment = true;
        executionCommand.terminalTranscriptRows = 150;
        // If the execution command was started for a plugin, only then will the stdout be set
        // Otherwise if command was manually started by the user like by adding a new terminal session,
        // then no need to set stdout
        final TermuxSession newTermuxSession = TermuxSession.execute(this, executionCommand, this.getTermuxTermuxTerminalSessionClientBase(), this, new TermuxShellEnvironment(), null);
        if (null == newTermuxSession) {
            // If the execution command was started for a plugin, then process the error
            return null;
        }
        newTermuxSession.getTerminalSession().setBoldWithBright(true);
        this.mShellManager.mTermuxSessions.add(newTermuxSession);
        // Remove the execution command from the pending plugin execution commands list since it has
        // now been processed
        // Notify {@link TermuxSessionsListViewController} that sessions list has been updated if
        // activity in is foreground

        this.updateNotification();
        // No need to recreate the activity since it likely just started and theme should already have applied
//        TermuxActivity.updateTermuxActivityStyling(this, false);
        return newTermuxSession;
    }

    /**
     * Remove a TermuxSession.
     */
    public synchronized int removeTermuxSession(final TerminalSession sessionToRemove) {
        final int index = this.getIndexOfSession(sessionToRemove);
        if (0 <= index)
            this.mShellManager.mTermuxSessions.get(index).finish();
        return index;
    }

    /**
     * Callback received when a {@link TermuxSession} finishes.
     */
    @Override
    public void onTermuxSessionExited(TermuxSession termuxSession) {
        if (null != termuxSession) {
//            ExecutionCommand executionCommand = termuxSession.getExecutionCommand();
            // If the execution command was started for a plugin, then process the results
            this.mShellManager.mTermuxSessions.remove(termuxSession);
            // Notify {@link TermuxSessionsListViewController} that sessions list has been updated if
            // activity in is foreground

        }
        this.updateNotification();
    }

    /**
     * If {@link TermuxActivity} has not bound to the  yet or is destroyed, then
     * interface functions requiring the activity should not be available to the terminal sessions,
     * so we just return the {@link #mTermuxTerminalSessionServiceClient}. Once {@link TermuxActivity} bind
     * callback is received, it should call {@link #setTermuxTermuxTerminalSessionClientBase} to set the
     * {@link TermuxService#mTermuxTerminalSessionActivityClient} so that further terminal sessions are directly
     * passed the {@link TermuxTerminalSessionActivityClient} object which fully implements the
     * {link TermuxTerminalSessionClientBase} interface.
     *
     * @return Returns the {@link TermuxTerminalSessionActivityClient} if {@link TermuxActivity} has bound with
     * , otherwise {@link TermuxTerminalSessionServiceClient}.
     */
    private synchronized TerminalSessionClient getTermuxTermuxTerminalSessionClientBase() {
        return Objects.requireNonNullElse(this.mTermuxTerminalSessionActivityClient, this.mTermuxTerminalSessionServiceClient);
    }

    /**
     * This should be called when {@link TermuxActivity#onServiceConnected} is called to set the
     * {@link TermuxService#mTermuxTerminalSessionActivityClient} variable and update the {@link TerminalSession}
     * and {@link TerminalEmulator} clients in case they were passed {@link TermuxTerminalSessionServiceClient}
     * earlier.
     *
     * @param termuxTerminalSessionActivityClient The {@link TermuxTerminalSessionActivityClient} object that fully
     *                                            implements the {link TermuxTerminalSessionClientBase} interface.
     */
    public synchronized void setTermuxTermuxTerminalSessionClientBase(final TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
        for (int i = 0; i < this.mShellManager.mTermuxSessions.size(); i++)
            this.mShellManager.mTermuxSessions.get(i).getTerminalSession().updateTerminalSessionClient(this.mTermuxTerminalSessionActivityClient);
    }

    /**
     * This should be called when {@link TermuxActivity} has been destroyed and in {@link #onUnbind(Intent)}
     * so that the  and {@link TerminalSession} and {@link TerminalEmulator}
     * clients do not hold an activity references.
     */
    public synchronized void unsetTermuxTermuxTerminalSessionClientBase() {
        for (int i = 0; i < this.mShellManager.mTermuxSessions.size(); i++)
            this.mShellManager.mTermuxSessions.get(i).getTerminalSession().updateTerminalSessionClient(this.mTermuxTerminalSessionServiceClient);
        this.mTermuxTerminalSessionActivityClient = null;
    }

    private Notification buildNotification() {
        ((NotificationManager) this.getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID, "s", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID).setOngoing(true).setSmallIcon(com.termux.view.R.drawable.rsq).setContentIntent(PendingIntent.getService(this, 0, new Intent(this, TermuxService.class).setAction(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE), PendingIntent.FLAG_IMMUTABLE)).setContentText("Click to Exit").build();
    }

    /**
     * Update the shown foreground service notification after making any changes that affect it.
     */
    private synchronized void updateNotification() {
        if (this.mShellManager.mTermuxSessions.isEmpty()) {
            // Exit if we are updating after the user disabled all locks with no sessions or tasks running.
            this.requestStopService();
        }
    }

    public synchronized boolean isTermuxSessionsEmpty() {
        return this.mShellManager.mTermuxSessions.isEmpty();
    }

    public synchronized int getTermuxSessionsSize() {
        return this.mShellManager.mTermuxSessions.size();
    }

    public synchronized List<TermuxSession> getTermuxSessions() {
        return this.mShellManager.mTermuxSessions;
    }

    @Nullable
    public synchronized TermuxSession getTermuxSession(final int index) {
        if (0 <= index && index < this.mShellManager.mTermuxSessions.size())
            return this.mShellManager.mTermuxSessions.get(index);
        else
            return null;
    }

    @Nullable
    public synchronized TermuxSession getTermuxSessionForTerminalSession(final TerminalSession terminalSession) {
        if (null == terminalSession)
            return null;
        for (int i = 0; i < this.mShellManager.mTermuxSessions.size(); i++) {
            if (this.mShellManager.mTermuxSessions.get(i).getTerminalSession().equals(terminalSession))
                return this.mShellManager.mTermuxSessions.get(i);
        }
        return null;
    }

    public synchronized TermuxSession getLastTermuxSession() {
        return this.mShellManager.mTermuxSessions.isEmpty() ? null : this.mShellManager.mTermuxSessions.get(this.mShellManager.mTermuxSessions.size() - 1);
    }

    public synchronized int getIndexOfSession(final TerminalSession terminalSession) {
        if (null == terminalSession)
            return -1;
        for (int i = 0; i < this.mShellManager.mTermuxSessions.size(); i++) {
            if (this.mShellManager.mTermuxSessions.get(i).getTerminalSession().equals(terminalSession))
                return i;
        }
        return -1;
    }

    public boolean wantsToStop() {
        return this.mWantsToStop;
    }

    /**
     * This service is only bound from inside the same process and never uses IPC.
     */
    class LocalBinder extends Binder {

        public final TermuxService service = TermuxService.this;
    }
}
