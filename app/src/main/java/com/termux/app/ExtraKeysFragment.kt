package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.termux.R
import com.termux.app.terminal.io.TermuxTerminalExtraKeys
import com.termux.shared.termux.extrakeys.ExtraKeysView

class ExtraKeysFragment : Fragment() {
    private var mActivity: TermuxActivity? = null
    private var mTermuxTerminalExtraKeys: TermuxTerminalExtraKeys? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        val layout = inflater.inflate(
            R.layout.view_terminal_toolbar_extra_keys,
            container,
            false
        ) as ExtraKeysView
        mTermuxTerminalExtraKeys = TermuxTerminalExtraKeys(
            mActivity, mActivity!!.terminalView,
            mActivity!!.mTermuxTerminalViewClient, mActivity!!.termuxTerminalSessionClient
        )
        layout.setmExtraKeysViewClient(mTermuxTerminalExtraKeys)
        layout.setButtonTextAllCaps(mActivity!!.properties.shouldExtraKeysTextBeAllCaps())
        layout.reload(mTermuxTerminalExtraKeys!!.extraKeysInfo)
        mActivity!!.setExtrakeysView(layout)
        return layout
    }

}
