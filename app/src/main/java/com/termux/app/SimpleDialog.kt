package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert

class SimpleDialog:Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val title by remember{ mutableStateOf(requireArguments().getString("title","Wait!"))}
                val massage by remember { mutableStateOf( requireArguments().getString("massage","loading......"))}
                val show by remember { mutableStateOf(true) }
                val progress by remember{ mutableStateOf(requireArguments().getBoolean("pro",false))}
                androidx.wear.compose.material.dialog.Dialog(
                    showDialog = show,
                    onDismissRequest = {})
                {
                    Alert(
                        title = {
                            if(progress){
                            CircularProgressIndicator(
                            indicatorColor = MaterialTheme.colors.primaryVariant,
                            trackColor = MaterialTheme.colors.onPrimary,
                            strokeWidth = 5.dp
                        )}
                        },
                        content = {
                            item {

                                Column(
                                    modifier = Modifier
                                        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = title,
                                        color = MaterialTheme.colors.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        textAlign = TextAlign.Center,
                                        text = massage,
                                        color = MaterialTheme.colors.onSurface
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

}
