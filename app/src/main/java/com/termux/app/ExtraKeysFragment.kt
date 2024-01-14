package com.termux.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment

class ExtraKeysFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val mActivity = activity as TermuxActivity
        val customKey = requireArguments().getInt("key", 0)
        return ComposeView(requireContext()).apply {
            setContent {
                var ctrl by remember { mutableStateOf(mActivity.terminalView.isControlKeydown) }
                var shift by remember { mutableStateOf(mActivity.terminalView.isReadShiftKey) }
                var alt by remember { mutableStateOf(mActivity.terminalView.isReadAltKey) }
                Row(modifier = Modifier.height(15.dp)) {
                    val modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                    if (customKey != 0) {
                        ColorButton(onClick = {
                            mActivity.terminalView.handleKeyCode(
                                customKey,
                                KeyEvent.ACTION_DOWN
                            )
                        }, active = false, title = "$customKey", modifier)
                    }
                    ColorButton(onClick = {
                        ctrl = !ctrl
                        mActivity.terminalView.isControlKeydown = ctrl
                    }, active = ctrl, title = "CTRL", modifier)
                    ColorButton(onClick = {
                        shift = !shift
                        mActivity.terminalView.isReadShiftKey = shift
                    }, active = shift, title = "SHFT", modifier)
                    ColorButton(onClick = {
                        alt = !alt
                        mActivity.terminalView.isReadAltKey = alt
                    }, active = alt, title = "ALT", modifier)
                    ColorButton(onClick = {
                        mActivity.mTerminalView.dispatchKeyEvent(
                            KeyEvent(
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DEL
                            )
                        )
                    }, active = true, title = "⌫", modifier = modifier)
                }
            }
        }
    }

    @Composable
    fun ColorButton(onClick: () -> Unit, active: Boolean, title: String, modifier: Modifier) {

        Tiles(
            size = 10.sp,
            text = title,
            textColor = if (!active) Color.White else Color.Black,
            modifier = modifier
                .border(
                    width = 1.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(15.dp)
                )
                .background(
                    shape = RoundedCornerShape(15.dp),
                    color = if (active) Color.White else Color.Transparent
                )
                .wrapContentSize(),
            onclick = onClick,
            customMod = true
        )

    }
}
