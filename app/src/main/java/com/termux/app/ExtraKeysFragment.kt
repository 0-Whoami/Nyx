package com.termux.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text

class ExtraKeysFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val mActivity = activity as TermuxActivity
        val customKey=requireArguments().getInt("key",0)
        return ComposeView(requireContext()).apply {
            setContent {
                var ctrl by remember { mutableStateOf(mActivity.terminalView.isControlKeydown) }
                var shift by remember { mutableStateOf(mActivity.terminalView.isReadShiftKey) }
                var alt by remember { mutableStateOf(mActivity.terminalView.isReadAltKey) }
                Row(modifier = Modifier.height(15.dp)) {
                    val modifier = Modifier.weight(1f)
                    if(customKey!=0){
                        ColorButton(onklick = {
                            mActivity.terminalView.handleKeyCode(
                                customKey,
                                KeyEvent.ACTION_DOWN
                            )
                        }, active = false, title = "$customKey",modifier)
                    }
                    ColorButton(onklick = {
                        ctrl = !ctrl
                        mActivity.terminalView.isControlKeydown = ctrl
                    }, active = ctrl, title = "ctrl",modifier)
                    ColorButton(onklick = {
                        shift = !shift
                        mActivity.terminalView.isReadShiftKey = shift
                    }, active = shift, title = "shift",modifier)
                    ColorButton(onklick = {
                        alt = !alt
                        mActivity.terminalView.isReadAltKey = alt
                    }, active = alt, title = "alt",modifier)
                }
            }
        }
    }
    @Composable
    fun ColorButton(onklick:()->Unit,active:Boolean,title:String,modifier: Modifier){
        Button(onClick = onklick, modifier = modifier, colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)) {
            Text(text = title, color = if (active) Color.Cyan else Color.White)
        }
    }
}
