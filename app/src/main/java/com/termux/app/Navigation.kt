package com.termux.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.termux.R
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE

class Navigation : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val mActivity = activity as TermuxActivity
        mActivity.terminalView.touchTransparency = false
        return ComposeView(requireContext()).apply {
            setContent {
                var showDialog by remember { mutableStateOf(true) }
                androidx.wear.compose.material.dialog.Dialog(
                    showDialog = showDialog,
                    onDismissRequest = {
                        mActivity.supportFragmentManager.beginTransaction().remove(this@Navigation).commitNow()
                        showDialog = false
                    }) {
                    Alert(title = { Text(text = "Menu") }, content = {
                        item {
                            Chip(label = { Text(text = "Exit") },
                                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.error),
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
                        item { Text(text = "Sessions") }
                        items(mActivity.termuxService.termuxSessions) {
                            Chip(
                                icon = {
                                    Button(
                                        onClick = { mActivity.termuxTerminalSessionClient.renameSession(it.terminalSession) },
                                    ) {
                                    }
                                },
                                label = { Text(text = if (it.terminalSession.mSessionName != null) it.terminalSession.mSessionName else "Unamed Sesssion") },
                                onClick = {
                                    showDialog = false
                                    mActivity.termuxTerminalSessionClient.setCurrentSession(it.terminalSession)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface)
                            )
                        }
                        item {
                            Chip(
                                label = { Text(text = "Add New Session") },
                                onClick = { mActivity.supportFragmentManager.beginTransaction()
                                    .setReorderingAllowed(true).add(
                                        R.id.compose_fragment_container,
                                        SessionModifier::class.java,
                                        null,
                                        "session"
                                    ).commitNow() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item { Text(text = "Rotary Behaviour") }
                        itemsIndexed(listOf("Scroll", "Left-Right", "Up-Down")) { index: Int, item ->
                            Chip(
                                onClick = {
                                    showDialog = false
                                    mActivity.showToast(item, false)
                                    mActivity.terminalView.rotaryNavigationMode = index
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
                                label = {
                                    Text(
                                        text = item
                                    )
                                })
                        }
                        item {
                            Chip(label = { Text(text = "Adjust Window") }, onClick = {
                                mActivity.terminalView.requestFocus()
                                mActivity.terminalView.touchTransparency = true
                                mActivity.terminalView.rotaryNavigationMode = 3
                                showDialog = false
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                            )

                        }
                        item {
                            Chip(label = { Text(text = "Place Window") }, onClick = {
                                mActivity.terminalView.touchTransparency = false
                                mActivity.terminalView.rotaryNavigationMode = 0
                                showDialog = false
                            }, modifier = Modifier
                                .fillMaxWidth()
                            )

                        }

                        item {
                            Chip(label = { Text(text = "Toggle extra keys") }, onClick = {
                                mActivity.toggleTerminalToolbar()
                                showDialog = false
                            }, modifier = Modifier
                                .fillMaxWidth()
                            )
                        }

                        item {
                            Chip(label = { Text(text = "Toggle TextField") }, onClick = {
                                if (mActivity.supportFragmentManager.findFragmentByTag("edit") != null)
                                    mActivity.supportFragmentManager.beginTransaction()
                                        .setReorderingAllowed(true)
                                        .remove(mActivity.supportFragmentManager.findFragmentByTag("edit")!!)
                                        .commitNow()
                                else
                                    mActivity.supportFragmentManager.beginTransaction()
                                        .setReorderingAllowed(true).add(
                                            R.id.compose_fragment_container,
                                            InputBarFragment::class.java,
                                            null,
                                            "edit"
                                        ).commitNow()
                                showDialog = false
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                            )
                        }
                        item {
                            Chip(label = { Text(text = "Toogle Connection with Phone") }, onClick = {
                                if (mActivity.supportFragmentManager.findFragmentByTag("wear") != null)
                                    mActivity.supportFragmentManager.beginTransaction()
                                        .setReorderingAllowed(true)
                                        .remove(mActivity.supportFragmentManager.findFragmentByTag("wear")!!)
                                        .commitNow()
                                else
                                    mActivity.supportFragmentManager.beginTransaction()
                                        .setReorderingAllowed(true).add(
                                            R.id.compose_fragment_container,
                                            WearReceiverFragment::class.java,
                                            null,
                                            "wear"
                                        ).commitNow()
                                showDialog = false
                            }, modifier = Modifier
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
