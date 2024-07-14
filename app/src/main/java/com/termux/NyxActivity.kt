package com.termux

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.FrameLayout
import com.termux.data.ConfigManager.EXTRA_NORMAL_BACKGROUND
import com.termux.data.ConfigManager.loadConfigs
import com.termux.data.console
import com.termux.utils.ControlsUI
import com.termux.view.Console
import java.io.File

/** * A terminal emulator activity.
 */
class NyxActivity : Activity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        loadConfigs()
        startService(Intent(this, WakeUp::class.java).apply { action = "1" })
        console = Console(this)
        setWallpaper()
        setContentView(console)
        if (resources.configuration.isScreenRound) insertInCircle()
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            0
        ) {
            startNav()
        }
        super.onCreate(savedInstanceState)
    }

    private fun startNav() = startActivity(
        Intent(this, ControlsUI::class.java)
    )


    private fun setWallpaper() {
        if (File(EXTRA_NORMAL_BACKGROUND).exists()) window.decorView.background =
            Drawable.createFromPath(EXTRA_NORMAL_BACKGROUND)
    }

    override fun onResume() {
//        console.currentSession.write(intent.getStringExtra("cmd"))
        console.requestFocus()
        super.onResume()
    }

    private fun insertInCircle() {
        val width = (windowManager.currentWindowMetrics.bounds.height() * 0.7071f).toInt()
        console.layoutParams = FrameLayout.LayoutParams(width, width)
        console.x = width * 0.2071f
        console.y = console.x
    }

    fun destroy() {
        startService(Intent(this, WakeUp::class.java))
        finish()
    }
}
