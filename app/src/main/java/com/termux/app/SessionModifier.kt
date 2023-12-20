package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert

class SessionModifier :Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val mActivity=activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
                var sessionDialog by  remember { mutableStateOf(true) }
                var name by remember { mutableStateOf("") }
                androidx.wear.compose.material.dialog.Dialog(
                    showDialog = sessionDialog,
                    onDismissRequest = {mActivity.supportFragmentManager.beginTransaction().remove(this@SessionModifier).commitNow()
                        sessionDialog = false
                        name = ""
                    }) {
                    Alert(
                        title = { Text(fontFamily = FontFamily.Monospace,text = "Do you want to add Failsafe session?") },
                        content = {
                            item {
                                BasicTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace,color = Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .padding(15.dp)
                                                .fillMaxWidth()
                                                .height(45.dp)
                                                .background(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.DarkGray
                                                ), contentAlignment = Alignment.Center
                                        ) {
                                            innerTextField()
                                        }
                                    })
                            }
                            items(mutableListOf(true, false)) {
                                Chip(label = { Text(fontFamily = FontFamily.Monospace,text = "$it") }, onClick = {
                                    mActivity.termuxTerminalSessionClient.addNewSession(it, name)
                                        mActivity.supportFragmentManager.beginTransaction()
                                            .setReorderingAllowed(true)
                                            .remove(mActivity.supportFragmentManager.findFragmentByTag("nav")!!).remove(this@SessionModifier)
                                            .commitNow()
                                }, modifier = Modifier.fillMaxWidth(),
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(BorderStroke(width=1.dp, color = MaterialTheme.colors.onBackground)))
                            }

                        })
                }
            }
        }
    }
}
