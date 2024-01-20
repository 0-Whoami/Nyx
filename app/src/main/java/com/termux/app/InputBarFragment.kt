package com.termux.app

import android.os.Bundle
import android.view.KeyCharacterMap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.termux.shared.view.KeyboardUtils

class InputBarFragment : Fragment() {
    private lateinit var mActivity: TermuxActivity
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
                var text by remember { mutableStateOf("") }
                InputBar(
                    text = text,
                    valueChange = { text = it },
                    cornerRadius = 5.dp,
                    onAny = {
                        if (text.isEmpty()) (mActivity.currentSession
                            ?: return@InputBar).write("\r")
                        else if (text.length == 1) sendToTerminalAsKey(text)
                        else (mActivity.currentSession ?: return@InputBar).write(text)
                        KeyboardUtils.hideSoftKeyboard(
                            mActivity,
                            this@apply
                        )
                        mActivity.terminalView.requestFocus()
                        text = ""
                    }, imeAction = ImeAction.Go
                )
            }
        }
    }

    private fun sendToTerminalAsKey(text: String) {
        val characterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = characterMap.getEvents(text.toCharArray())
        events.forEach {
            mActivity.terminalView.dispatchKeyEvent(it)
        }
    }

    override fun onDestroyView() {
        mActivity.terminalView.requestFocus()
        super.onDestroyView()
    }

}
