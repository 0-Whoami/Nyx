package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.material.MaterialTheme

class InputBarFragment : Fragment() {
    private var mActivity: TermuxActivity? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        return ComposeView(requireContext()).apply {
            setContent {
                var text by remember { mutableStateOf("") }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    textStyle = TextStyle(color = MaterialTheme.colors.onSurface),
                    keyboardActions = KeyboardActions(onSend = {
                        val session = mActivity!!.currentSession
                        if (session != null) {
                            if (session.isRunning) {
                                if (text.isEmpty()) text = "\r"
                                session.write(text)
                            } else {
                                mActivity!!.termuxTerminalSessionClient.removeFinishedSession(
                                    session
                                )
                            }
                            text = ""
                        }
                    }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(2.dp)
                                .background(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colors.surface
                                )
                        ) {
                            innerTextField()
                        }
                    })
            }
        }
    }

    override fun onDestroyView() {
        mActivity!!.mTerminalView.requestFocus()
        super.onDestroyView()
    }

}
