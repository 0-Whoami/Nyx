package com.termux

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout.LayoutParams
import com.termux.utils.NavWindow
import com.termux.utils.data.ConfigManager.EXTRA_NORMAL_BACKGROUND
import com.termux.utils.data.ConfigManager.loadConfigs
import com.termux.utils.data.TerminalManager.TerminalSessions
import com.termux.utils.data.TerminalManager.addNewSession
import com.termux.utils.data.TerminalManager.console
import com.termux.view.Console
import java.io.File

/**
 * A terminal emulator activity.
 */
class NyxActivity : Activity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfigs()
        startService(Intent(this, WakeUp::class.java).apply { action = "1" })
        setTermuxTerminalViewAndLayout()
        if (TerminalSessions.isEmpty()) addNewSession(false)
        else console.attachSession(TerminalSessions[0])
        setWallpaper()
    }

    override fun onBackPressed() {
        startActivity(Intent(this, NavWindow::class.java))
    }

    private fun setWallpaper() {
        if (File(EXTRA_NORMAL_BACKGROUND).exists()) window.decorView.background =
            Drawable.createFromPath(EXTRA_NORMAL_BACKGROUND)
    }

    override fun onResume() {
        super.onResume()
        console.currentSession.write(intent.getStringExtra("cmd"))
    }


    private fun setTermuxTerminalViewAndLayout() {
        console = Console(this)
        setContentView(console)
        if (resources.configuration.isScreenRound) setMargin()
        console.requestFocus()
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun setMargin() {
        console.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ).apply {
            val margin = (windowManager.currentWindowMetrics.bounds.height() * 0.146f).toInt()
            setMargins(margin, margin, margin, margin)
        }
    }

    fun destroy() {
        startService(Intent(this, WakeUp::class.java))
        finish()
    }
}
