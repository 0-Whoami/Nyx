package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class TextSizeChanger : Fragment() {

    @Composable
    fun Tiles(text: String = "",onclick:()->Unit={}) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.surface,
            modifier = Modifier.padding(2.dp)
                .size(25.dp)
                .background(shape = CircleShape, color = MaterialTheme.colors.onSurface)
                .wrapContentSize()
                .clickable {
                    onclick()
                }
        )
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {val mActivity=activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
                Row {
                    Tiles("+"){mActivity.mTermuxTerminalViewClient.changeFontSize(true)}
                    Tiles("✓"){mActivity.supportFragmentManager.beginTransaction().remove(this@TextSizeChanger).commit()}
                    Tiles("-"){mActivity.mTermuxTerminalViewClient.changeFontSize(false)}
                }
            }
        }
    }
}
