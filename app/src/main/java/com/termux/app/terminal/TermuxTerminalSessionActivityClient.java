package com.termux.app.terminal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

/**
 * The {link TermuxTerminalSessionClientBase} implementation that may require an {@link Activity} for its interface methods.
 */
public class TermuxTerminalSessionActivityClient implements TerminalSessionClient {

    private final TermuxActivity mActivity;

    public TermuxTerminalSessionActivityClient(TermuxActivity activity) {
        this.mActivity = activity;
    }


    /**
     * Should be called when mActivity.onStart() is called
     */
    public final void onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        if (mActivity.getTermuxService() != null) {
            setCurrentSession(mActivity.getTermuxService().getLastTermuxSession().getTerminalSession());

        }
        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        mActivity.getTerminalView().onScreenUpdated();
        // Set background image or color. The display orientation may have changed
        // while being away, force a background update.

    }


    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */


    @Override
    public final void onTextChanged(@NonNull TerminalSession changedSession) {
        if (!mActivity.isVisible())
            return;
        if (mActivity.getCurrentSession() == changedSession)
            mActivity.getTerminalView().onScreenUpdated();
    }

    @Override
    public final void onSessionFinished(@NonNull TerminalSession finishedSession) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null || service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mActivity.finishActivityIfNotFinishing();
            return;
        }
        int index = service.getIndexOfSession(finishedSession);
        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting fo the user to press enter.
        // The plugin can handle/show errors itself.
        if (mActivity.isVisible() && finishedSession != mActivity.getCurrentSession()) {
            // Show toast for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0)
                mActivity.showToast(toToastTitle(finishedSession) + " - exited", true);
        }
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // On Android TV devices we need to use older behaviour because we may
            // not be able to have multiple launcher icons.
            if (service.getTermuxSessionsSize() > 1) {
                removeFinishedSession(finishedSession);
            }
        } else {
            // Once we have a separate launcher icon for the failsafe session, it
            // should be safe to auto-close session on exit code '0' or '130'.
            if (finishedSession.getExitStatus() == 0 || finishedSession.getExitStatus() == 130) {
                removeFinishedSession(finishedSession);
            }
        }
    }

    @Override
    public final void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mActivity.isVisible())
            return;
        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("", text);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public final void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mActivity.isVisible())
            return;
        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        mActivity.getTerminalView().mEmulator.paste(item.getText().toString());
    }


    @Override
    public final void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null)
            return;
        TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }


    @Override
    public final Integer getTerminalCursorStyle() {
        return 0;
    }


    /**
     * Try switching to session.
     */
    public final void setCurrentSession(TerminalSession session) {
        if (session == null)
            return;
        if (mActivity.getTerminalView().attachSession(session)) {
            // notify about switched session if not already displaying the session
            notifyOfSessionChange();
        }
        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.

        // Background color may have changed. If the background is image and already set,
        // no need for update.

    }

    private final void notifyOfSessionChange() {
        if (!mActivity.isVisible())
            return;

        TerminalSession session = mActivity.getCurrentSession();
        mActivity.showToast(toToastTitle(session), false);

    }


    public final void addNewSession(boolean isFailSafe, String sessionName) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null)
            return;

        TerminalSession currentSession = mActivity.getCurrentSession();
        String workingDirectory;
        if (currentSession == null) {
            workingDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
        } else {
            workingDirectory = currentSession.getCwd();
        }
        TermuxSession newTermuxSession = service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName);
        if (newTermuxSession == null)
            return;
        TerminalSession newTerminalSession = newTermuxSession.getTerminalSession();
        setCurrentSession(newTerminalSession);


    }

    public final void removeFinishedSession(TerminalSession finishedSession) {
        // Return pressed with finished session - remove it.
        TermuxService service = mActivity.getTermuxService();
        if (service == null)
            return;
        int index = service.removeTermuxSession(finishedSession);
        int size = service.getTermuxSessionsSize();
        if (size == 0) {
            // There are no sessions to show, so finish the activity.
            mActivity.finishActivityIfNotFinishing();
        } else {
            if (index >= size) {
                index = size - 1;
            }
            TermuxSession termuxSession = service.getTermuxSession(index);
            if (termuxSession != null)
                setCurrentSession(termuxSession.getTerminalSession());
        }
    }


    private final String toToastTitle(TerminalSession session) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null)
            return null;
        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0)
            return null;
        StringBuilder toastTitle = new StringBuilder("[" + (indexOfSession + 1) + "]");
        if (!TextUtils.isEmpty(session.mSessionName)) {
            toastTitle.append(" ").append(session.mSessionName);
        }
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(session.mSessionName == null ? " " : "\n");
            toastTitle.append(title);
        }
        return toastTitle.toString();
    }

}
