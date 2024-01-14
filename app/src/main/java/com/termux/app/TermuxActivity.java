package com.termux.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li><a href="http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android">..<a href=".</a></li>
 * ">* <li>https://code.google.com/p/android/iss</a>ues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_REMOVE_BACKGROUND_IMAGE_ID = 13;
    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;
    /**
     * The {@link TerminalViewClient} interface implementation to allow for communication between
     * {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;
    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    private TermuxService mTermuxService;
    /**
     * The {link TermuxTerminalSessionClientBase} interface implementation to allow for communication between
     * {@link TerminalSession} and {@link TermuxActivity}.
     */
    private TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        // Delete ReportInfo serialized object files from cache older than 14 days
        // Load Termux app SharedProperties from disk

        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_termux);
        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        // Must be done every time activity is created in order to registerForActivityResult,
        // Even if the logic of launching is based on user input.

        this.setTermuxTerminalViewAndClients();

        this.registerForContextMenu(this.mTerminalView);
        this.setWallpaper();
        // Start the {@link TermuxService} and make it run regardless of who is bound to it
        final Intent serviceIntent = new Intent(this, TermuxService.class);
        this.startService(serviceIntent);
        // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
        // callback if it succeeds.
        this.bindService(serviceIntent, this, 0);

    }

    @Override
    public void onStart() {
        super.onStart();
        if (null != mTermuxTerminalSessionActivityClient)
            this.mTermuxTerminalSessionActivityClient.onStart();
        //registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mTermuxTerminalViewClient) this.mTermuxTerminalViewClient.onResume();
        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
    }

    private void setWallpaper() {
        if (new File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND).exists())
            this.getWindow().getDecorView().setBackground(Drawable.createFromPath(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mTermuxService) {
            // Do not leave service and session clients with references to activity.
            this.mTermuxService.unsetTermuxTermuxTerminalSessionClientBase();
            this.mTermuxService = null;
        }
        this.unbindService(this);

    }

    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(final ComponentName componentName, final IBinder service) {
        this.mTermuxService = ((TermuxService.LocalBinder) service).service;
        Uri uri = null == getIntent().getData() ? Uri.parse("") : this.getIntent().getData();
        this.setIntent(null);
        if (this.mTermuxService.isTermuxSessionsEmpty()) {
            this.mTermuxTerminalSessionActivityClient.addNewSession(uri.getBooleanQueryParameter("fail", false), null);
            if (uri.getBooleanQueryParameter("con", false))
                this.getSupportFragmentManager().beginTransaction().add(R.id.compose_fragment_container, WearReceiverFragment.class, null, "wear").commit();
        }
        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        this.mTermuxService.setTermuxTermuxTerminalSessionClientBase(this.mTermuxTerminalSessionActivityClient);
        this.mTerminalView.getCurrentSession().write(uri.getQueryParameter("cmd"));
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        // Respect being stopped from the {@link TermuxService} notification action.
        this.finishActivityIfNotFinishing();
    }


    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        this.mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        this.mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, this.mTermuxTerminalSessionActivityClient);
        // Set termux terminal view
        this.mTerminalView = this.findViewById(R.id.terminal_view);
        this.mTerminalView.setTerminalViewClient(this.mTermuxTerminalViewClient);
        if (null != mTermuxTerminalViewClient) this.mTermuxTerminalViewClient.onCreate();

    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing()) {
            this.finish();
        }
    }

    /**
     * Show a toast and dismiss the last one if still visible.
     */
    public void showToast(final String text, final boolean longDuration) {
        if (null == text || text.isEmpty()) return;
        Toast.makeText(this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenu.ContextMenuInfo menuInfo) {
        final TerminalSession currentSession = this.getCurrentSession();
        if (null == currentSession) return;
        menu.add(Menu.NONE, TermuxActivity.CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, "Reset");
        menu.add(Menu.NONE, TermuxActivity.CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, "Kill " + this.getCurrentSession().getPid()).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, TermuxActivity.CONTEXT_MENU_REMOVE_BACKGROUND_IMAGE_ID, Menu.NONE, "Remove Background");
    }

    /**
     * Hook system menu to show context menu instead.
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        this.mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        final TerminalSession session = this.getCurrentSession();
        return switch (item.getItemId()) {
            case TermuxActivity.CONTEXT_MENU_RESET_TERMINAL_ID -> {
                this.onResetTerminalSession(session);
                yield true;
            }
            case TermuxActivity.CONTEXT_MENU_KILL_PROCESS_ID -> {
                session.finishIfRunning();
                yield true;
            }
            case TermuxActivity.CONTEXT_MENU_REMOVE_BACKGROUND_IMAGE_ID -> {
                this.getWindow().getDecorView().setBackgroundColor(Color.BLACK);
                new File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND).delete();
                new File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_BLUR_BACKGROUND).delete();
                yield true;
            }

            default -> super.onContextItemSelected(item);
        };
    }


    private void onResetTerminalSession(final TerminalSession session) {
        if (null != session) {
            session.reset();
        }
    }

    public TermuxService getTermuxService() {
        return this.mTermuxService;
    }

    public TerminalView getTerminalView() {
        return this.mTerminalView;
    }

    public TermuxTerminalSessionActivityClient getTermuxTermuxTerminalSessionClientBase() {
        return this.mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (null != mTerminalView) return this.mTerminalView.getCurrentSession();
        else return null;
    }

}
