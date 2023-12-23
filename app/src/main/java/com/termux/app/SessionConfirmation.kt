package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class SessionConfirmation :Fragment() {
    @Composable
    fun Tiles(text: String = "",onclick:()->Unit={}) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.surface,
            modifier = Modifier
                .padding(5.dp)
                .background(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colors.onSurface)
                .padding(10.dp).wrapContentSize()
                .clickable {
                    onclick()
                }
        )
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val mActivity=activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
//                var sessionDialog by  remember { mutableStateOf(true) }
                var name by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(10.dp).background(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colors.background), horizontalAlignment = Alignment.CenterHorizontally){
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace,color = MaterialTheme.colors.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .padding(15.dp)
                                    .fillMaxWidth()
                                    .height(45.dp)
                                    .border(
                                        shape = RoundedCornerShape(25.dp),
                                        color = MaterialTheme.colors.onSurface,
                                        width = 1.dp
                                    ), contentAlignment = Alignment.Center
                            ) {
                                innerTextField()
                            }
                        })
                    Row {
                        Tiles("+"
                        ) { mActivity.termuxTerminalSessionClient.addNewSession(false, name)
                            mActivity.supportFragmentManager.beginTransaction().remove(this@SessionConfirmation).commitNow()}
                        Tiles("<!>"){mActivity.termuxTerminalSessionClient.addNewSession(true, name)
                            mActivity.supportFragmentManager.beginTransaction().remove(this@SessionConfirmation).commitNow()}
                        Tiles("✕"){mActivity.supportFragmentManager.beginTransaction().remove(this@SessionConfirmation).commitNow()}
                    }
                }
//                androidx.wear.compose.material.dialog.Dialog(
//                    showDialog = sessionDialog,
//                    onDismissRequest = {
//                        sessionDialog = false
//                        name = ""
//                    }) {
//                    Alert(
//                        title = { Text(fontFamily = FontFamily.Monospace,text = "Do you want to add Failsafe session?") },
//                        content = {
//                            item {
//                                BasicTextField(
//                                    value = name,
//                                    onValueChange = { name = it },
//                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace,color = MaterialTheme.colors.onSurface),
//                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
//                                    decorationBox = { innerTextField ->
//                                        Box(
//                                            modifier = Modifier
//                                                .padding(15.dp)
//                                                .fillMaxWidth()
//                                                .height(45.dp)
//                                                .border(
//                                                    color = MaterialTheme.colors.onSurface,
//                                                    width = 1.dp
//                                                ), contentAlignment = Alignment.Center
//                                        ) {
//                                            innerTextField()
//                                        }
//                                    })
//                            }
//                            items(mutableListOf(true, false)) {
//                                Chip(label = { Text(fontFamily = FontFamily.Monospace,text = "$it") }, onClick = {
//                                    mActivity.termuxTerminalSessionClient.addNewSession(it, name)
//                                    sessionDialog=false
//                                }, modifier = Modifier.fillMaxWidth(),
//                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
//                                    border = ChipDefaults.chipBorder(BorderStroke(width=1.dp, color = MaterialTheme.colors.onBackground)))
//                            }
//
//                        })
//                }
            }
        }
    }

}
