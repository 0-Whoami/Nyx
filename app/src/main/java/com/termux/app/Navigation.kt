@file:OptIn(ExperimentalFoundationApi::class)

package com.termux.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.termux.R
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import kotlinx.coroutines.launch
import java.util.Objects

@OptIn(ExperimentalWearFoundationApi::class)
class Navigation : Fragment() {
    private lateinit var mActivity: TermuxActivity
    override fun onDestroy() {
        super.onDestroy()
        mActivity.terminalView.focusable = View.FOCUSABLE
        mActivity.terminalView.requestFocus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        val isconnect = mActivity.supportFragmentManager.findFragmentByTag("wear") != null
        mActivity.terminalView.touchTransparency = false
        return ComposeView(requireContext()).apply {
            setContent {
                var showDialog by remember { mutableStateOf(true) }
                val connetionString = if (isconnect) "Disconnect" else "Connect"
                var key by remember { mutableStateOf("0") }
                val list = rememberScalingLazyListState()
                Dialog(
                    showDialog = showDialog,
                    onDismissRequest = {
                        mActivity.supportFragmentManager.beginTransaction().remove(this@Navigation)
                            .commit()
                        showDialog = false
                    }) {
                    val coroutine = rememberCoroutineScope()
                    val focus = rememberActiveFocusRequester()
                    Alert(title = { Text(fontFamily = FontFamily.Monospace, text = "Menu") },
                        modifier = Modifier
                            .onRotaryScrollEvent {
                                coroutine.launch {
                                    list.scrollBy(it.verticalScrollPixels)
                                    list.animateScrollBy(0f)
                                }
                                true
                            }
                            .focusRequester(focus)
                            .focusable(),
                        scrollState = list,
                        content = {
                            item {
                                Chip(
                                    label = {
                                        Text(
                                            color = MaterialTheme.colors.error,
                                            fontFamily = FontFamily.Monospace,
                                            text = "Exit"
                                        )
                                    },
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.error
                                        )
                                    ),
                                    onClick = {
                                        context?.startService(
                                            Intent(
                                                context,
                                                TermuxService::class.java
                                            ).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE)
                                        )
                                        mActivity.finishActivityIfNotFinishing()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                            item { Text(fontFamily = FontFamily.Monospace, text = "Sessions") }
                            items(mActivity.termuxService.termuxSessions) {
                                Chip(
                                    label = {
                                        Text(
                                            color = MaterialTheme.colors.background,
                                            fontFamily = FontFamily.Monospace,
                                            text = "● ${if (it.terminalSession.mSessionName != null) it.terminalSession.mSessionName else "Unamed Sesssion"}"
                                        )
                                    },
                                    onClick = {},
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                showDialog = false
                                                mActivity.termuxTerminalSessionClient.setCurrentSession(
                                                    it.terminalSession
                                                )
                                            },
                                            onLongClick = {
                                                mActivity.termuxTerminalSessionClient.renameSession(
                                                    it.terminalSession
                                                )
                                            }),
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.onBackground)
                                )
                            }
                            item {
                                Chip(
                                    label = {
                                        Text(
                                            color = MaterialTheme.colors.onBackground,
                                            fontFamily = FontFamily.Monospace,
                                            text = "+ Add New Session"
                                        )
                                    },
                                    onClick = {
                                        mActivity.supportFragmentManager.beginTransaction()
                                            .setReorderingAllowed(true).add(
                                                R.id.compose_fragment_container,
                                                SessionModifier::class.java,
                                                null,
                                                "session"
                                            ).commit()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.onBackground
                                        )
                                    )
                                )

                            }
                            item {
                                Text(
                                    fontFamily = FontFamily.Monospace,
                                    text = "Rotary Behaviour"
                                )
                            }
                            item {
                                val actions = listOf(
                                    "⊻",
                                    "◀▶",
                                    "▲▼"//▴▾◂▸
                                )
                                Row {
                                    for ((i, item) in actions.withIndex()) {

                                        val activate =
                                            mActivity.terminalView.rotaryNavigationMode == i
                                        val mod = if (activate) Modifier.padding(5.dp).background(
                                            shape = CircleShape,
                                            color = MaterialTheme.colors.onBackground
                                        ) else Modifier.padding(5.dp).border(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.onBackground,
                                            shape = CircleShape
                                        )
                                        Box(contentAlignment = Alignment.Center, modifier = mod.size(50.dp)
                                            .clickable {
                                                showDialog = false
                                                mActivity.showToast(item, false)
                                                mActivity.terminalView.rotaryNavigationMode = i
                                            }
                                            ) {
                                            Text(fontSize = 15.sp,
                                                color = if (activate) MaterialTheme.colors.background else MaterialTheme.colors.onBackground,
                                                fontFamily = FontFamily.Monospace,
                                                text = item
                                            )
                                        }
                                    }
                                }

                            }
                            item {
                                Chip(label = {
                                    Text(
                                        color = MaterialTheme.colors.onBackground,
                                        fontFamily = FontFamily.Monospace,
                                        text = "Adjust Window"
                                    )
                                }, onClick = {
                                    mActivity.terminalView.requestFocus()
                                    mActivity.terminalView.touchTransparency = true
                                    mActivity.terminalView.rotaryNavigationMode = 3
                                    showDialog = false
                                }, modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.onBackground
                                        )
                                    )
                                )
                            }
                            item {
                                BasicTextField(
                                    value = key,
                                    onValueChange = { key = it },
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colors.onSurface,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Done,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(45.dp)
                                                .border(
                                                    width = 1.dp,
                                                    shape = RoundedCornerShape(30.dp),
                                                    color = MaterialTheme.colors.surface
                                                ), contentAlignment = Alignment.Center
                                        ) {
                                            innerTextField()
                                        }
                                    })
                            }
                            item {
                                Chip(label = {
                                    Text(
                                        color = MaterialTheme.colors.onBackground,
                                        fontFamily = FontFamily.Monospace,
                                        text = "Toggle extra keys"
                                    )
                                }, onClick = {
                                    if (mActivity.supportFragmentManager.findFragmentByTag("extra") != null) {
                                        mActivity.supportFragmentManager.beginTransaction()
                                            .setReorderingAllowed(true).remove(
                                                Objects.requireNonNull<Fragment>(
                                                    mActivity.supportFragmentManager.findFragmentByTag(
                                                        "extra"
                                                    )
                                                )
                                            ).commit()
                                    } else {
                                        mActivity.supportFragmentManager.beginTransaction()
                                            .setReorderingAllowed(true).add(
                                                R.id.compose_fragment_container,
                                                ExtraKeysFragment::class.java,
                                                Bundle().apply { putInt("key", key.toInt()) },
                                                "extra"
                                            ).commit()
                                    }
                                    showDialog = false
                                }, modifier = Modifier
                                    .fillMaxWidth(),
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.onBackground
                                        )
                                    )
                                )
                            }
                            item {
                                Chip(label = {
                                    Text(
                                        color = MaterialTheme.colors.onBackground,
                                        fontFamily = FontFamily.Monospace,
                                        text = "Toggle TextField"
                                    )
                                }, onClick = {
                                    if (mActivity.supportFragmentManager.findFragmentByTag("edit") != null)
                                        mActivity.supportFragmentManager.beginTransaction()
                                            .setReorderingAllowed(true)
                                            .remove(
                                                mActivity.supportFragmentManager.findFragmentByTag(
                                                    "edit"
                                                )!!
                                            )
                                            .commit()
                                    else
                                        mActivity.supportFragmentManager.beginTransaction()
                                            .setReorderingAllowed(true).add(
                                                R.id.compose_fragment_container,
                                                InputBarFragment::class.java,
                                                null,
                                                "edit"
                                            ).commit()
                                    showDialog = false
                                }, modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.onBackground
                                        )
                                    )
                                )
                            }
                            item {
                                Chip(
                                    label = {
                                        Text(
                                            fontFamily = FontFamily.Monospace,
                                            text = "$connetionString Phone",
                                            color = MaterialTheme.colors.onSurfaceVariant
                                        )
                                    },
                                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.background),
                                    border = ChipDefaults.chipBorder(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colors.onSurfaceVariant
                                        )
                                    ),
                                    onClick = {
                                        if (isconnect)
                                            mActivity.supportFragmentManager.beginTransaction()
                                                .setReorderingAllowed(true)
                                                .remove(
                                                    mActivity.supportFragmentManager.findFragmentByTag(
                                                        "wear"
                                                    )!!
                                                )
                                                .commit()
                                        else
                                            mActivity.supportFragmentManager.beginTransaction()
                                                .setReorderingAllowed(true).add(
                                                    R.id.compose_fragment_container,
                                                    WearReceiverFragment::class.java,
                                                    null,
                                                    "wear"
                                                ).commit()
                                        showDialog = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                )
                            }
                        })
                }
            }
        }
    }

}
