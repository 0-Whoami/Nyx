package com.termux.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.widget.LinearLayout
import com.main
import com.termux.R
import com.termux.app.service.LocalBinder
import com.termux.terminal.TerminalSession
import com.termux.terminal.TermuxTerminalSessionActivityClient
import com.termux.utils.data.ConfigManager.EXTRA_NORMAL_BACKGROUND
import com.termux.utils.data.ConfigManager.enableBackground
import com.termux.utils.data.ConfigManager.loadConfigs
import com.termux.utils.ui.NavWindow
import com.termux.view.Console
import java.io.File

/**
 * A terminal emulator activity.
 */
class main : Activity(), ServiceConnection {
    /**
     * The [Console] shown in  [main] that displays the terminal.
     */
    lateinit var console: Console
    lateinit var linearLayout: LinearLayout
    val navWindow: NavWindow by lazy { NavWindow(this) }

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
        loadConfigs()
        val serviceIntent = Intent(this, service::class.java)
        startService(serviceIntent)
        this.bindService(serviceIntent, this, 0)
    }

    private fun setWallpaper() {
        if (File(EXTRA_NORMAL_BACKGROUND).exists() && enableBackground) this.window.decorView.background =
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
        setTermuxTerminalViewAndLayout()
        if (this.mService.isTerminalSessionsEmpty) {
            termuxTerminalSessionClientBase.addNewSession(false)
        }
        termuxTerminalSessionClientBase.onStart()
        console.currentSession.write(intent.getStringExtra("cmd"))
        this.setWallpaper()
        intent = null
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // Respect being stopped from the {@link service} notification action.
        finishActivityIfNotFinishing()
    }

    private fun setTermuxTerminalViewAndLayout() {
        console = findViewById(R.id.terminal_view)
        linearLayout = findViewById(R.id.background)
        console.requestFocus()
    }

    fun finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing) {
            finish()
        }
    }
}
