package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import kotlinx.coroutines.launch

class WindowChanger : Fragment() {
    private lateinit var mActivity: TermuxActivity

    @OptIn(ExperimentalWearFoundationApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                mActivity = activity as TermuxActivity
                Popup(
                    properties = PopupProperties(focusable = true, dismissOnBackPress = true),
                    onDismissRequest = { exit() }) {
                    val background by remember { mutableStateOf(mActivity.terminalView.parent as View) }
                    var scale by remember { mutableFloatStateOf(background.scaleX) }
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
                    val focus = rememberActiveFocusRequester()
                    val coroutine = rememberCoroutineScope()
                    Box(modifier = Modifier
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
                        .transformable(state), contentAlignment = Alignment.BottomCenter) {
                        Tiles(text = "Ok", modifier = Modifier.size(40.dp), onclick = { exit() })
                    }
                    LaunchedEffect(scale, offset) {
                        background.animate().x(offset.x).y(offset.y).scaleX(scale).scaleY(scale)
                            .setDuration(0).start()
                    }
                }
            }
        }
    }

    private fun exit() {
        mActivity.supportFragmentManager.beginTransaction().remove(this).commit()
    }
}
