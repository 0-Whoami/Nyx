@file:OptIn(ExperimentalWearFoundationApi::class)

package com.termux.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.termux.R
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess


class Navigation : Fragment() {
    private lateinit var mActivity: TermuxActivity


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent { QuickSwitch() }
        }
    }

    @Composable
    fun QuickSwitch() {
        val coroutine = rememberCoroutineScope()
        var state by remember {
            mutableIntStateOf(
                (mActivity.termuxService.getIndexOfSession(
                    mActivity.currentSession
                ))
            )
        }
        val ssize by remember { mutableIntStateOf(mActivity.termuxService.termuxSessionsSize) }
        val max by remember { mutableIntStateOf(9 + ssize) }

        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, 65),
            properties = PopupProperties(focusable = true),
            onDismissRequest = {
                if (state in 0..<ssize) mActivity.termuxTerminalSessionClient.setCurrentSession(
                    mActivity.termuxService.termuxSessions[state].terminalSession
                )
                else when (state) {
                    -1 -> newSession()
                    ssize + 0 -> scroll()
                    ssize + 1 -> lr()
                    ssize + 2 -> ud()
                    ssize + 3 -> moveWindow()
                    ssize + 4 -> textSizeChanger()
                    ssize + 5 -> extraKeysToogle()
                    ssize + 6 -> textFieldToogle()
                    ssize + 7 -> connectIonPhone()
                    ssize + 8 -> kill()
                }
                exit()
            }) {
            val focus = rememberActiveFocusRequester()
            Box(modifier = Modifier
                .onRotaryScrollEvent {
                    coroutine.launch {
                        state = if (it.horizontalScrollPixels > 0) {
                            min(state + 1, max - 1)
                        } else {
                            max(state - 1, -1)
                        }
                    }
                    true
                }
                .focusRequester(focus)
                .focusable()
                .size(30.dp)) {
                if (state in 0..<ssize) Tiles(text = "$state")
                else when (state) {
                    -1 -> Tiles(text = "+")
                    ssize + 0 -> Scroll()
                    ssize + 1 -> LR_Arrow()
                    ssize + 2 -> UD_Arrow()
                    ssize + 3 -> Window()
                    ssize + 4 -> TextSizeChanger()
                    ssize + 5 -> ExtraKeys()
                    ssize + 6 -> TextField()
                    ssize + 7 -> ConnectionPhone()
                    ssize + 8 -> Kill()
                }
            }
        }

    }

    @Composable
    fun Kill(modifier: Modifier = Modifier) {
        Text(text = "✕",
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.surface,
            modifier = modifier
                .fillMaxSize()
                .background(shape = CircleShape, color = MaterialTheme.colors.error)
                .wrapContentSize()
                .clickable {
                    kill()
                })
    }

    private fun kill() {
        mActivity.startService(
            Intent(
                context, TermuxService::class.java
            ).setAction(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE)
        )
        mActivity.finishActivityIfNotFinishing()
        exitProcess(0)
    }

    @Composable
    private fun TextSizeChanger() {
        Tiles(text = "Tt") { textSizeChanger() }
    }

    private fun textSizeChanger() {
        mActivity.supportFragmentManager.beginTransaction()
            .add(R.id.compose_fragment_container, TextSizeChanger::class.java, null, "Tt").commit()
    }

    @Composable
    private fun ConnectionPhone() {
        Tiles(text = "P") { connectIonPhone() }
    }

    private fun connectIonPhone() {
        if (mActivity.supportFragmentManager.findFragmentByTag("wear") != null) mActivity.supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true).remove(
                mActivity.supportFragmentManager.findFragmentByTag(
                    "wear"
                )!!
            ).commit()
        else mActivity.supportFragmentManager.beginTransaction().setReorderingAllowed(true).add(
                R.id.compose_fragment_container, WearReceiverFragment::class.java, null, "wear"
            ).commit()
    }

    private fun scroll() {
        mActivity.terminalView.setRotaryNavigationMode(0)
    }

    private fun lr() {
        mActivity.terminalView.setRotaryNavigationMode(1)
    }

    private fun ud() {
        mActivity.terminalView.setRotaryNavigationMode(2)
    }

    private fun moveWindow() {
        mActivity.supportFragmentManager.beginTransaction().add(R.id.compose_fragment_container,WindowChanger::class.java,null,"win").commit()
    }

    private fun extraKeysToogle() {
        if (mActivity.supportFragmentManager.findFragmentByTag("extra") != null) mActivity.supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true).remove(
                mActivity.supportFragmentManager.findFragmentByTag(
                    "extra"
                )!!
            ).commit()
        else mActivity.supportFragmentManager.beginTransaction().setReorderingAllowed(true).add(
                R.id.compose_fragment_container,
                ExtraKeysFragment::class.java,
                Bundle().apply { putInt("key", 0) },
                "extra"
            ).commit()
    }

    private fun textFieldToogle() {
        if (mActivity.supportFragmentManager.findFragmentByTag("edit") != null) mActivity.supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true).remove(
                mActivity.supportFragmentManager.findFragmentByTag(
                    "edit"
                )!!
            ).commit()
        else mActivity.supportFragmentManager.beginTransaction().setReorderingAllowed(true).add(
                R.id.compose_fragment_container, InputBarFragment::class.java, null, "edit"
            ).commit()
    }

    private fun newSession() {
        mActivity.supportFragmentManager.beginTransaction().setReorderingAllowed(true).replace(
                R.id.compose_fragment_container, SessionConfirmation::class.java, null, "session"
            ).commit()
    }

    private fun exit() {
        mActivity.supportFragmentManager.beginTransaction().remove(this@Navigation).commit()
    }


    @Composable
    fun Scroll() {
        Tiles(text = "⊻", onclick = { scroll() })
    }

    @Composable
    fun LR_Arrow() {
        Tiles(text = "◂▸", onclick = { lr() })
    }

    @Composable
    fun UD_Arrow() {
        Tiles(text = "▴▾", onclick =  { ud() })
    }

    @Composable
    fun Window() {
        Tiles(text = "◳") { moveWindow() }
    }

    @Composable
    fun ExtraKeys() {
        Tiles(text = "E") { extraKeysToogle() }
    }

    @Composable
    fun TextField() {
        Tiles(text = "T") { textFieldToogle() }
    }
}
