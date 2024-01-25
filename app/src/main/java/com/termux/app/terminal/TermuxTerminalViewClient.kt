package com.termux.app.terminal

import android.content.Context
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.R
import com.termux.app.Navigation
import com.termux.app.TermuxActivity
import com.termux.shared.view.KeyboardUtils.showSoftKeyboard
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import kotlin.math.max
import kotlin.math.min

class TermuxTerminalViewClient(
    private val mActivity: TermuxActivity,
    private val mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient
) : TerminalViewClient {
    var CURRENT_FONTSIZE = 0
    private var MIN_FONTSIZE = 0
    private var MAX_FONTSIZE = 0
    private var DEFAULT_FONTSIZE = 0

    /**
     * Should be called when mActivity.onCreate() is called
     */
    fun onCreate() {
        setDefaultFontSizes(mActivity)
        mActivity.terminalView.setTextSize(DEFAULT_FONTSIZE)
        mActivity.terminalView.keepScreenOn = true
        CURRENT_FONTSIZE = DEFAULT_FONTSIZE
    }

    override fun onScale(scale: Float): Float {
        if (0.9f > scale || 1.1f < scale) {
            val increase = 1.0f < scale
            changeFontSize(increase)
            return 1.0f
        }
        return scale
    }

    override fun onSwipe() {
        mActivity.supportFragmentManager.beginTransaction()
            .add(R.id.compose_fragment_container, Navigation::class.java, null, "nav").commit()
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val term = mActivity.currentSession!!.emulator
        if (!term.isMouseTrackingActive && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            showSoftKeyboard(
                mActivity, mActivity.terminalView
            )
        }
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, currentSession: TerminalSession): Boolean {
        if (KeyEvent.KEYCODE_ENTER == keyCode && !currentSession.isRunning) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession)
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (KeyEvent.KEYCODE_BACK == keyCode && null == mActivity.terminalView.mEmulator) {
            mActivity.finishActivityIfNotFinishing()
            return true
        }
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        if (ctrlDown) {
            if (106 == codePoint &&  /* Ctrl+j or \n */
                !session.isRunning
            ) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session)
                return true
            }
        }
        return false
    }

    fun changeFontSize(increase: Boolean) {
        CURRENT_FONTSIZE += if (increase) 1 else -1
        CURRENT_FONTSIZE = max(
            MIN_FONTSIZE.toDouble(),
            min(CURRENT_FONTSIZE.toDouble(), MAX_FONTSIZE.toDouble())
        ).toInt()
        mActivity.terminalView.setTextSize(CURRENT_FONTSIZE)
    }

    private fun setDefaultFontSizes(context: Context) {
        val dipInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            context.resources.displayMetrics
        )
        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        // min
        MIN_FONTSIZE = dipInPixels.toInt()
        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        var defaultFontSize = Math.round(7 * dipInPixels)
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (1 == defaultFontSize % 2) defaultFontSize--
        // default
        DEFAULT_FONTSIZE = defaultFontSize
        // max
        MAX_FONTSIZE = 256
    }

}
