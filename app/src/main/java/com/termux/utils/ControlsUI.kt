package com.termux.utils

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.termux.R
import com.termux.data.ConfigManager
import com.termux.data.console
import com.termux.terminal.SessionManager.addNewSession
import com.termux.terminal.SessionManager.removeFinishedSession
import com.termux.terminal.SessionManager.sessions

class ControlsUI : Activity() {
    private lateinit var console_parent: ViewGroup
    private lateinit var sessionView: View
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.control_ui)
        var extrakeys = -1
        var winman = -1
        console_parent = console.parent as ViewGroup
        sessionView = findViewById(R.id.session_view)

        for (i in 0 until console_parent.childCount) {
            when (console_parent.getChildAt(i)) {
                is Extrakeys -> extrakeys = i
                is WindowManager -> winman = i
            }
        }
        fV(R.id.scroll).requestFocus()
        setupButton(R.id.new_session) {
            addnewSes(false)
        }.setOnLongClickListener {
            addnewSes(true)
            true
        }
        setupButton(R.id.extrakeys, extrakeys != -1) {
            extrakeys = (it as Button).toggleView(Extrakeys(), extrakeys)
        }
        setupButton(R.id.win, winman != -1) {
            winman = (it as Button).toggleView(WindowManager(), winman)
            finish()
        }
        setupButton(R.id.close) {
            sessions.forEach {
                removeFinishedSession(it)
            }
            finish()
        }
        findViewById<TextView>(R.id.clock).typeface = ConfigManager.typeface
        console_parent.setRenderEffect(
            RenderEffect.createBlurEffect(
                5f, 5f, Shader.TileMode.CLAMP
            )
        )
    }

    private fun addnewSes(isFailSafe: Boolean) {
        addNewSession(isFailSafe)
        sessionView.requestLayout()
    }

    private fun Button.toggleView(view: View, index: Int): Int {
        toogle()
        return if (index != -1) {
            console_parent.removeViewAt(index)
            -1
        } else {
            console_parent.addView(view)
            console_parent.childCount - 1
        }
    }

    private fun setupButton(id: Int, enabled: Boolean = true, onClick: (View) -> Unit) =
        findViewById<Button>(id).apply {
            setCheck(enabled)
            setOnClickListener { onClick(it) }
        }

    private fun fV(id: Int) = findViewById<View>(id)
    override fun onDestroy() {
        console_parent.setRenderEffect(null)
        super.onDestroy()
    }
}
