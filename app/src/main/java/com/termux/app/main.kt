package com.termux.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.widget.FrameLayout
import com.termux.R
import com.termux.app.nyx_service.LocalBinder
import com.termux.terminal.TerminalSession
import com.termux.utils.data.ConfigManager
import com.termux.utils.data.ConfigManager.EXTRA_NORMAL_BACKGROUND
import com.termux.utils.data.ConfigManager.loadConfigs
import com.termux.utils.ui.NavWindow
import com.termux.view.Console
import java.io.File
import kotlin.math.abs

/**
 * A terminal emulator activity.
 */
class main : Activity(), ServiceConnection {
    /**
     * The [Console] shown in  [main] that displays the terminal.
     */
    lateinit var console: Console
    lateinit var frameLayout: FrameLayout
    private val navWindow: NavWindow by lazy { NavWindow(this) }

    /**
     * The connection to the [mNyxService]. Requested in [.onCreate] with a call to
     * [.bindService], and obtained and stored in
     * [.onServiceConnected].
     */
    lateinit var mNyxService: nyx_service
        private set


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfigs()
        val nyxServiceIntent = Intent(this, nyx_service::class.java)
        startService(nyxServiceIntent)
        bindService(nyxServiceIntent, this, 0)
    }

    private fun setWallpaper() {
        if (File(EXTRA_NORMAL_BACKGROUND).exists()) window.decorView.background =
            Drawable.createFromPath(EXTRA_NORMAL_BACKGROUND)
    }

    public override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    /**
     * Part of the [ServiceConnection] interface. The nyx_service is bound with
     * [.bindService] in [.onCreate] which will cause a call to this
     * callback method.
     */
    override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
        mNyxService = (service as LocalBinder).nyx_service
        setContentView(R.layout.activity_termux)
        setTermuxTerminalViewAndLayout()
        if (mNyxService.isTerminalSessionsEmpty) {
            addNewSession(false)
        }
        console.attachSession(mNyxService.TerminalSessions[0])
        console.onScreenUpdated()
        setWallpaper()
        intent = null
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // Respect being stopped from the {@link nyx_service} notification action.
        finishActivityIfNotFinishing()
    }

    private fun setTermuxTerminalViewAndLayout() {
        console = findViewById(R.id.terminal_view)
        frameLayout = findViewById<FrameLayout?>(R.id.background).also {
            var dx = 0f
            it.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = event.rawX
                    }

                    MotionEvent.ACTION_UP -> {
                        with(event.rawX - dx) {
                            if (abs(this) < 100) return@with
                            if (this > 0) {
                                navWindow.showSessionChooser()
                            } else {
                                navWindow.showModeMenu()
                            }
                        }
                    }
                }
                true
            }
        }
        console.requestFocus()
    }

    private fun finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing) {
            finish()
        }
    }

    fun onSessionFinished(finishedSession: TerminalSession) {
        if (mNyxService.wantsToStop()) {
            // The nyx_service wants to stop as soon as possible.
            finishActivityIfNotFinishing()
            return
        }
        // Once we have a separate launcher icon for the failsafe session, it
        // should be safe to auto-close session on exit code '0' or '130'.
        if (finishedSession.exitStatus == 0 || finishedSession.exitStatus == 130) {
            removeFinishedSession(finishedSession)
        }
    }


    fun addNewSession(isFailSafe: Boolean) {
        val newTerminalSession =
            createTerminalSession(isFailSafe)
        mNyxService.TerminalSessions.add(newTerminalSession)
        console.attachSession(newTerminalSession)
    }

    private fun createTerminalSession(isFailSafe: Boolean): TerminalSession {
        val failsafeCheck = isFailSafe || !ConfigManager.PREFIX_DIR.exists()
        val newTerminalSession =
            TerminalSession(failsafeCheck, console)
        return newTerminalSession
    }

    fun removeFinishedSession(finishedSession: TerminalSession) {
        // Return pressed with finished session - remove it.
        val index = mNyxService.removeTerminalSession(finishedSession)
        if (index == -1) {
            // There are no sessions to show, so finish the activity.
            finishActivityIfNotFinishing()
            return
        }
        val terminalSession = mNyxService.TerminalSessions[index]
        console.attachSession(terminalSession)
    }
}
