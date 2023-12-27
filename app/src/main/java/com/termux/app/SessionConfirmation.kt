package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.Fragment
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class SessionConfirmation : Fragment() {
    @Composable
    fun Tiles(text: String = "", onclick: () -> Unit = {}, modifier: Modifier) {
        Text(text = text,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.surface,
            modifier = modifier
                .padding(5.dp)
                .background(
                    shape = RoundedCornerShape(25.dp), color = MaterialTheme.colors.onSurface
                )
                .padding(10.dp)
                .wrapContentSize()
                .clickable {
                    onclick()
                })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val mActivity = activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
//                var sessionDialog by  remember { mutableStateOf(true) }
                var name by remember { mutableStateOf("") }
                Popup(alignment = Alignment.Center,
                    onDismissRequest = {
                        mActivity.supportFragmentManager.beginTransaction()
                            .remove(this@SessionConfirmation).commitNow()
                    },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .background(
                                shape = RoundedCornerShape(25.dp),
                                color = MaterialTheme.colors.background
                            ), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        InputBar(text = name, valueChange = { name = it }, imeAction = ImeAction.Done, modifier = Modifier.height(40.dp).padding(10.dp))
                        Row {
                            val modifier by remember { mutableStateOf(Modifier.weight(1f)) }
                            Tiles("Add", modifier = modifier, onclick = {
                                mActivity.termuxTerminalSessionClient.addNewSession(false, name)
                                mActivity.supportFragmentManager.beginTransaction()
                                    .remove(this@SessionConfirmation).commitNow()
                            })
                            Tiles("Failsafe", modifier = modifier, onclick = {
                                mActivity.termuxTerminalSessionClient.addNewSession(true, name)
                                mActivity.supportFragmentManager.beginTransaction()
                                    .remove(this@SessionConfirmation).commitNow()
                            })
                        }
                    }
                }
            }
        }
    }

}
