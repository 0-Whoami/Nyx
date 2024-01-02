package com.termux.app

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.io.InputStream


class WearReceiverFragment : Fragment(), MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {
    private var mActivity: TermuxActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivity = activity as TermuxActivity
        Thread {
            Tasks.await(Wearable.getNodeClient(mActivity!!).connectedNodes).forEach { it ->
                Wearable.getMessageClient(mActivity!!)
                    .sendMessage(it.id, "/request-network", "".toByteArray()).addOnFailureListener {
                        Toast.makeText(
                            mActivity, "Failed to connect Mobile $it", Toast.LENGTH_SHORT
                        ).show()
                        mActivity!!.supportFragmentManager.beginTransaction().remove(this)
                            .commitNow()
                    }
            }
        }
    }

    override fun onMessageReceived(p0: MessageEvent) {
        Thread {
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
        Wearable.getDataClient(mActivity!!).addListener(this)
        Wearable.getMessageClient(requireContext()).addListener(this).addOnFailureListener {
            mActivity!!.supportFragmentManager.beginTransaction().remove(this).commitNow()
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(mActivity!!).removeListener(this)
        Wearable.getMessageClient(requireContext()).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                if (event.dataItem.uri.path == "/files") {
                    val fileAsset =
                        DataMapItem.fromDataItem(event.dataItem).dataMap.getAsset("file")
                    val filePath =
                        DataMapItem.fromDataItem(event.dataItem).dataMap.getString("path")
                    Thread {
                        saveFileFromAsset(fileAsset!!, filePath!!)
                    }.start()
                    mActivity!!.showToast("↧", false)
                } else if (event.dataItem.uri.path == "/delete") {
                    val filePath =
                        DataMapItem.fromDataItem(event.dataItem).dataMap.getString("path")
                    val file = File(filePath!!)
                    if (file.exists()) file.delete()
                    mActivity!!.showToast("deleted", false)
                }
            }
        dataEvents.release()
    }

    private fun saveFileFromAsset(asset: Asset, path: String) {
        val assetInputStream: InputStream? =
            Tasks.await(Wearable.getDataClient(mActivity!!).getFdForAsset(asset))?.inputStream
        val file =
            File(path)
        if (file.exists()) file.delete()
        if (!file.parentFile?.exists()!!) file.mkdirs()
        file.createNewFile()
        file.outputStream().use { assetInputStream!!.copyTo(it) }
    }
}
