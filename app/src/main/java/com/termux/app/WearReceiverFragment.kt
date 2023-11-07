package com.termux.app

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable


class WearReceiverFragment : Fragment(),
    MessageClient.OnMessageReceivedListener{
    private var mActivity: TermuxActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivity = activity as TermuxActivity
        Thread{
            Tasks.await(Wearable.getNodeClient(mActivity!!).connectedNodes).forEach { it ->
                Wearable.getMessageClient(mActivity!!)
                    .sendMessage(it.id, "/request-network", "".toByteArray()).addOnFailureListener {
                    Toast.makeText(mActivity, "Failed to connect Mobile $it", Toast.LENGTH_SHORT).show()
                    mActivity!!.supportFragmentManager.beginTransaction().remove(this).commitNow()
                }
            }
        }
    }

    override fun onMessageReceived(p0: MessageEvent) {
       Thread{ var text = String(p0.data)
        when (p0.path) {
            "/cmd" -> {
                if (text.isEmpty())
                    text = "\r"
                mActivity!!.currentSession!!.write(text)
            }
            "/key" -> mActivity!!.terminalView.handleKeyCode(text.toInt(), KeyEvent.ACTION_DOWN)
        }
       }.start()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(requireContext()).addListener(this).addOnFailureListener { mActivity!!.supportFragmentManager.beginTransaction().remove(this).commitNow() }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(requireContext()).removeListener(this)
    }
}
