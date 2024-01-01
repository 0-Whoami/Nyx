@file:OptIn(ExperimentalWearFoundationApi::class)

package com.termux.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.MaterialTheme
import com.termux.R
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.launch
import kotlin.system.exitProcess


class Navigation : Fragment() {
    private lateinit var mActivity: TermuxActivity
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        return ComposeView(requireContext()).apply { setContent { QuickSwitch() } }
    }

    private fun dissmiss(current: Int, ssize: Int) {
        if (current in 1..ssize) mActivity.termuxTerminalSessionClient.setCurrentSession(
            mActivity.termuxService.termuxSessions[current - 1].terminalSession
        )
        else when (current) {
            0 -> newSession()
            ssize + 1 -> scroll()
            ssize + 2 -> lr()
            ssize + 3 -> ud()
            ssize + 4 -> moveWindow()
            ssize + 5 -> textSizeChanger()
            ssize + 6 -> extraKeysToogle()
            ssize + 7 -> textFieldToogle()
            ssize + 8 -> connectIonPhone()
            ssize + 9 -> kill()
        }
        exit()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun QuickSwitch() {

        val coroutine = rememberCoroutineScope()
        val ssize by remember { mutableIntStateOf(mActivity.termuxService.termuxSessionsSize) }
        //val max by remember { mutableIntStateOf(9 + ssize) }
        val pagerState = rememberPagerState(
            pageCount = { 10 + ssize },
            initialPage = mActivity.termuxService.getIndexOfSession(mActivity.currentSession) + 1
        )
        val indication = remember {
            MutableInteractionSource()
        }
        Popup(
            properties = PopupProperties(focusable = true)
        ) {
            val focus = rememberActiveFocusRequester()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clickable(
                        indication = null,
                        interactionSource = indication
                    ) { dissmiss(pagerState.currentPage, ssize) }
                    .alpha(.8f),
                contentAlignment = Alignment.BottomCenter
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier
                    .onRotaryScrollEvent {
                        coroutine.launch {
                            if (it.horizontalScrollPixels > 0)
                                pagerState.scrollToPage(pagerState.currentPage + 1)
                            else
                                pagerState.scrollToPage(pagerState.currentPage - 1)
                        }
                        true
                    }
                    .focusRequester(focus)
                    .focusable()
                    .size(40.dp)) {
                    if (it in 1..ssize) Tiles(text = "$it")
                    else when (it) {
                        0 -> Tiles(text = "+")
                        ssize + 1 -> Scroll()
                        ssize + 2 -> LR_Arrow()
                        ssize + 3 -> UD_Arrow()
                        ssize + 4 -> Window()
                        ssize + 5 -> TextSizeChanger()
                        ssize + 6 -> ExtraKeys()
                        ssize + 7 -> TextField()
                        ssize + 8 -> ConnectionPhone()
                        ssize + 9 -> Kill()
                    }
                }
            }
        }

    }

    @Composable
    fun Kill() {
        Tiles(
            text = "✕",
            modifier = Modifier
                .fillMaxSize()
                .background(shape = CircleShape, color = MaterialTheme.colors.error)
                .wrapContentSize(), customMod = true
        ) { kill() }
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
        mActivity.supportFragmentManager.beginTransaction()
            .add(R.id.compose_fragment_container, WindowChanger::class.java, null, "win").commit()
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
        Tiles(text = "▴▾", onclick = { ud() })
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
