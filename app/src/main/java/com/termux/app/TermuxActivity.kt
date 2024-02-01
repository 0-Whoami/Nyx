package com.termux.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import com.termux.R
import com.termux.app.TermuxService.LocalBinder
import com.termux.terminal.TerminalSession
import com.termux.terminal.TermuxTerminalSessionActivityClient
import com.termux.utils.data.TermuxConstants
import com.termux.utils.file.FileUtils
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
class TermuxActivity : Activity(), ServiceConnection {
    /**
     * The [TerminalView] shown in  [TermuxActivity] that displays the terminal.
     */
    lateinit var terminalView: TerminalView

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
    var termuxTerminalSessionClientBase: TermuxTerminalSessionActivityClient =
        TermuxTerminalSessionActivityClient(this)
        private set

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, TermuxService::class.java)
        startService(serviceIntent)
        this.bindService(serviceIntent, this, 0)
        FileUtils.setupStorageSymlinks(this)
    }

    private fun setWallpaper() {
        if (File(TermuxConstants.EXTRA_NORMAL_BACKGROUND).exists()) this.window.decorView.background =
            Drawable.createFromPath(TermuxConstants.EXTRA_NORMAL_BACKGROUND)
    }

    public override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    /**
     * Part of the [ServiceConnection] interface. The service is bound with
     * [.bindService] in [.onCreate] which will cause a call to this
     * callback method.
     */
    override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
        termuxService = (service as LocalBinder).service
        termuxService.setTermuxTermuxTerminalSessionClientBase(termuxTerminalSessionClientBase)
        this.setContentView(R.layout.activity_termux)
        setTermuxTerminalViewAndClients()
        if (termuxService.isTerminalSessionsEmpty) {
            val session = termuxService.createTerminalSession(false)
            termuxService.TerminalSessions.add(session)
            terminalView.mEmulator = session.emulator
        }
        termuxTerminalSessionClientBase.onStart()
        terminalView.currentSession.write(intent.getStringExtra("cmd"))
        registerForContextMenu(terminalView)
        this.setWallpaper()
        intent = null
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing()
    }

    private fun setTermuxTerminalViewAndClients() {
        terminalView = findViewById(R.id.terminal_view)
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
    fun showToast(text: String, longDuration: Boolean) {
        Toast.makeText(this, text, if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            .show()
    }

    val currentSession: TerminalSession
        get() = terminalView.currentSession

}
