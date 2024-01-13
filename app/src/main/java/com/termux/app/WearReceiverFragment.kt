package com.termux.app

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class WearReceiverFragment : Fragment(), MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {
    private var mActivity: TermuxActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            mActivity = activity as TermuxActivity
        } catch (e: Exception) {
        }
        showToast("Listening....")
    }

    override fun onMessageReceived(p0: MessageEvent) {
        if (mActivity == null) return
        CoroutineScope(Dispatchers.IO).launch {
            var text = String(p0.data)
            when (p0.path) {
                "/cmd" -> {
                    if (text.isEmpty()) text = "\r"
                    mActivity!!.currentSession!!.write(text)
                }

                "/key" -> {
                    val keys =
                        text.splitToSequence(" ").filter { it.all { char -> char.isDigit() } }
                            .toList()
                    var keyMod = 0
                    if (keys[1].toInt() == 1) keyMod = keyMod or KeyEvent.META_CTRL_ON
                    if (keys[2].toInt() == 1) keyMod = keyMod or KeyEvent.META_ALT_ON
                    if (keys[3].toInt() == 1) keyMod = keyMod or KeyEvent.META_SHIFT_ON
                    val eventTime = SystemClock.uptimeMillis()
                    mActivity!!.terminalView.dispatchKeyEvent(
                        KeyEvent(
                            eventTime,
                            eventTime,
                            KeyEvent.ACTION_DOWN,
                            keys[0].toInt(),
                            0,
                            keyMod,
                            0,
                            0,
                            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                            InputDevice.SOURCE_KEYBOARD
                        )
                    )
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(requireContext()).addListener(this)
        Wearable.getMessageClient(requireContext()).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(requireContext()).removeListener(this)
        Wearable.getMessageClient(requireContext()).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == "/files" }
            .forEach { event ->
                val listOfFiles =
                    DataMapItem.fromDataItem(event.dataItem).dataMap.getStringArray("files")
                for (path in listOfFiles!!) {
                    saveFileFromAsset(
                        DataMapItem.fromDataItem(event.dataItem).dataMap.getAsset(path)!!,
                        path
                    )
                }
            }
        dataEvents.release()
    }

    private fun showToast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun saveFileFromAsset(asset: Asset, path: String) {
        val file =
            File(path)
        if (file.exists()) file.delete()
        if (!file.parentFile?.exists()!!) file.parentFile?.mkdirs()
        Wearable.getDataClient(requireContext())
            .getFdForAsset(asset)
            .addOnCompleteListener { res ->
                res.result.inputStream.use { it.copyTo(file.outputStream()) }
                showToast(path)
            }
    }
}
