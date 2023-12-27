package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.Fragment

class TextSizeChanger : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {val mActivity=activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
                Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(dismissOnBackPress = true), onDismissRequest = {mActivity.supportFragmentManager.beginTransaction()
                    .remove(this@TextSizeChanger).commit()}){
                    Row(modifier = Modifier.size(width=90.dp, height = 40.dp)) {
                        Tiles(text = "+", modifier = Modifier.weight(1f).padding(horizontal = 2.5.dp)) { mActivity.mTermuxTerminalViewClient.changeFontSize(true) }
                        Tiles(text = "-", modifier = Modifier.weight(1f).padding(horizontal = 2.5.dp)) { mActivity.mTermuxTerminalViewClient.changeFontSize(false) }
                    }
                }
            }
        }
    }
}
