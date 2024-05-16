package com.termux

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import com.termux.utils.GesturedView
import com.termux.utils.TerminalManager.addNewSession
import com.termux.utils.TerminalManager.console
import com.termux.utils.data.ConfigManager.EXTRA_NORMAL_BACKGROUND
import com.termux.utils.data.ConfigManager.loadConfigs
import java.io.File

/**
 * A terminal emulator activity.
 */
class NyxActivity : Activity() {

    private lateinit var navWindow: GesturedView


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfigs()
        setContentView(R.layout.activity_termux)
        setTermuxTerminalViewAndLayout()
        addNewSession(false)
        setWallpaper()
    }

    override fun onBackPressed() {
        navWindow.toogleVisibility()
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
        console = findViewById(R.id.terminal_view)
        navWindow = findViewById(R.id.nav_window)
        console.requestFocus()
    }


}
