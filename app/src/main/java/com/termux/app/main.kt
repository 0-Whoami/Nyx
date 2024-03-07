package com.termux.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.widget.LinearLayout
import com.termux.R
import com.termux.app.service.LocalBinder
import com.termux.terminal.TerminalSession
import com.termux.terminal.TermuxTerminalSessionActivityClient
import com.termux.utils.data.EXTRA_NORMAL_BACKGROUND
import com.termux.utils.file.setupStorageSymlinks
import com.termux.utils.ui.NavWindow
import com.termux.view.Console
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
class main : Activity(), ServiceConnection {
    /**
     * The [Console] shown in  [main] that displays the terminal.
     */
    lateinit var console: Console
    lateinit var blur: LinearLayout
    val navWindow: NavWindow = NavWindow(this)

    /**
     * The connection to the [mService]. Requested in [.onCreate] with a call to
     * [.bindService], and obtained and stored in
     * [.onServiceConnected].
     */
    lateinit var mService: service
        private set

    /**
     * The {link TermuxTerminalSessionClientBase} interface implementation to allow for communication between
     * [TerminalSession] and [main].
     */
    var termuxTerminalSessionClientBase: TermuxTerminalSessionActivityClient =
        TermuxTerminalSessionActivityClient(this)
        private set

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, service::class.java)
        startService(serviceIntent)
        this.bindService(serviceIntent, this, 0)
        setupStorageSymlinks(this)
    }

    private fun setWallpaper() {
        if (File(EXTRA_NORMAL_BACKGROUND).exists()) this.window.decorView.background =
            Drawable.createFromPath(EXTRA_NORMAL_BACKGROUND)
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
        this.mService = (service as LocalBinder).service
        this.mService.setTermuxTermuxTerminalSessionClientBase(termuxTerminalSessionClientBase)
        this.setContentView(R.layout.activity_termux)
        setTermuxTerminalViewAndClients()
        if (this.mService.isTerminalSessionsEmpty) {
            termuxTerminalSessionClientBase.addNewSession(false)
        }
        termuxTerminalSessionClientBase.onStart()
        console.currentSession.write(intent.getStringExtra("cmd"))
        registerForContextMenu(console)
        this.setWallpaper()
        intent = null
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // Respect being stopped from the {@link service} notification action.
        finishActivityIfNotFinishing()
    }

    private fun setTermuxTerminalViewAndClients() {
        console = findViewById(R.id.terminal_view)
        blur = findViewById(R.id.background)
        console.requestFocus()
    }

    fun finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing) {
            finish()
        }
    }
}
