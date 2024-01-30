package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.Fragment
import com.termux.R
import com.termux.shared.view.BackgroundBlur
import kotlinx.coroutines.launch

class WindowChanger : Fragment() {
    private lateinit var mActivity: TermuxActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        val background = mActivity.findViewById<BackgroundBlur>(R.id.background)!!
        val siseref = background.height
        return ComposeView(requireContext()).apply {
            setContent {
                Popup(
                    properties = PopupProperties(focusable = true, dismissOnBackPress = true),
                    onDismissRequest = { exit() }) {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember {
                        mutableStateOf(
                            Offset(
                                x = background.x,
                                y = background.y
                            )
                        )
                    }
                    val state =
                        rememberTransformableState { zoomChange, panChange, _ ->
                            scale *= zoomChange
                            offset += panChange
                        }
                    val focus = remember { FocusRequester() }
                    val coroutine = rememberCoroutineScope()
                    Box(modifier
                    = Modifier
                        .fillMaxSize()
                        .onRotaryScrollEvent {
                            coroutine.launch {
                                scale =
                                    if (it.horizontalScrollPixels > 0) scale * 1.05f else scale * .95f
                            }
                            true
                        }
                        .focusRequester(focus)
                        .focusable()
                        .clickable { exit() }
                        .transformable(state), contentAlignment = Alignment.BottomCenter) {
                        Row(modifier = Modifier.size(width = 90.dp, height = 40.dp)) {
                            Tiles(
                                text = "+",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.5.dp)
                            ) { mActivity.terminalView.changeFontSize(1.1f) }
                            Tiles(
                                text = "-",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.5.dp)
                            ) { mActivity.terminalView.changeFontSize(.9f) }
                        }
                    }
                    LaunchedEffect(scale, offset) {
                        background.x = offset.x
                        background.y = offset.y
                        val side = (siseref * scale).toInt()
                        background.layoutParams = FrameLayout.LayoutParams(side, side)
                    }
                }
            }
        }
    }

    private fun exit() {
        mActivity.supportFragmentManager.beginTransaction().remove(this).commit()
    }
}
