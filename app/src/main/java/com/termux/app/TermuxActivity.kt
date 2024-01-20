package com.termux.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.termux.R
import com.termux.app.TermuxService.LocalBinder
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalViewClient
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.view.BackgroundBlur
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File

/**
 * A terminal emulator activity.
 *
 *
 * See
 *
 *  * [..[*  * https://code.google.com/p/android/iss](.</a></li>
  )ues/detail?id=6426](http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android)
 *
 * about memory leaks.
 */
class TermuxActivity : FragmentActivity(), ServiceConnection {
    /**
     * The [TerminalView] shown in  [TermuxActivity] that displays the terminal.
     */
    lateinit var terminalView: TerminalView

    /**
     * The [TerminalViewClient] interface implementation to allow for communication between
     * [TerminalView] and [TermuxActivity].
     */
    lateinit var mTermuxTerminalViewClient: TermuxTerminalViewClient

    /**
     * The connection to the [TermuxService]. Requested in [.onCreate] with a call to
     * [.bindService], and obtained and stored in
     * [.onServiceConnected].
     */
    lateinit var termuxService: TermuxService
        private set

    /**
     * The {link TermuxTerminalSessionClientBase} interface implementation to allow for communication between
     * [TerminalSession] and [TermuxActivity].
     */
    lateinit var termuxTermuxTerminalSessionClientBase: TermuxTerminalSessionActivityClient
        private set

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, TermuxService::class.java)
        startService(serviceIntent)
        this.bindService(serviceIntent, this, 0)
    }

    private fun setWallpaper() {
        if (File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND).exists()) this.window.decorView.background =
            Drawable.createFromPath(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND)
    }

    public override fun onDestroy() {
        super.onDestroy()
        // Do not leave service and session clients with references to activity.
        termuxService.unsetTermuxTermuxTerminalSessionClientBase()
        unbindService(this)
    }

    /**
     * Part of the [ServiceConnection] interface. The service is bound with
     * [.bindService] in [.onCreate] which will cause a call to this
     * callback method.
     */
    override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
        termuxService = (service as LocalBinder).service
        this.setContentView(R.layout.activity_termux)
        setTermuxTerminalViewAndClients()
        if (termuxService.isTermuxSessionsEmpty) {
            termuxTermuxTerminalSessionClientBase.addNewSession(
                false, null
            )
        }
        termuxService.setTermuxTermuxTerminalSessionClientBase(termuxTermuxTerminalSessionClientBase)
        terminalView.currentSession.write(intent.getStringExtra("cmd"))
        registerForContextMenu(terminalView)
        this.setWallpaper()
        termuxTermuxTerminalSessionClientBase.onStart()
        intent = null
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing()
    }

    private fun setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        termuxTermuxTerminalSessionClientBase = TermuxTerminalSessionActivityClient(this)
        mTermuxTerminalViewClient =
            TermuxTerminalViewClient(this, termuxTermuxTerminalSessionClientBase)
        // Set termux terminal view
        terminalView = findViewById(R.id.terminal_view)
        terminalView.setTerminalViewClient(mTermuxTerminalViewClient)
        mTermuxTerminalViewClient.onCreate()
        this.findViewById<BackgroundBlur>(R.id.background).apply {
            x = 66f
            y = 66f
            invalidate()
        }
        terminalView.requestFocus()
    }

    fun finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing) {
            finish()
        }
    }

    /**
     * Show a toast and dismiss the last one if still visible.
     */
    fun showToast(text: String?, longDuration: Boolean) {
        if (text.isNullOrEmpty()) return
        Toast.makeText(this, text, if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            .show()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) {
        val currentSession = currentSession ?: return
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, "Reset")
        menu.add(
            Menu.NONE,
            CONTEXT_MENU_KILL_PROCESS_ID,
            Menu.NONE,
            "Kill " + this.currentSession!!.pid
        ).setEnabled(currentSession.isRunning)
        menu.add(Menu.NONE, CONTEXT_MENU_REMOVE_BACKGROUND_IMAGE_ID, Menu.NONE, "Remove Background")
    }

    /**
     * Hook system menu to show context menu instead.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        terminalView.showContextMenu()
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val session = currentSession
        return when (item.itemId) {
            CONTEXT_MENU_RESET_TERMINAL_ID -> {
                onResetTerminalSession(session)
                true
            }

            CONTEXT_MENU_KILL_PROCESS_ID -> {
                session!!.finishIfRunning()
                true
            }

            CONTEXT_MENU_REMOVE_BACKGROUND_IMAGE_ID -> {
                this.window.decorView.setBackgroundColor(Color.BLACK)
                File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_NORMAL_BACKGROUND).delete()
                File(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_BLUR_BACKGROUND).delete()
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    private fun onResetTerminalSession(session: TerminalSession?) {
        session?.reset()
    }

    val currentSession: TerminalSession?
        get() = terminalView.currentSession

    companion object {
        private const val CONTEXT_MENU_RESET_TERMINAL_ID = 3
        private const val CONTEXT_MENU_KILL_PROCESS_ID = 4
        private const val CONTEXT_MENU_REMOVE_BACKGROUND_IMAGE_ID = 13
    }
}
