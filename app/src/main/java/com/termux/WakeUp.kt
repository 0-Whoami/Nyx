package com.termux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class WakeUp : Service() {

    override fun onBind(intent : Intent) : IBinder? {
        return null
    }

    override fun onStartCommand(intent : Intent, flags : Int, startId : Int) : Int {
        if (intent.action == "1") {
            startForeground()
        } else {
            stop()
        }
        return START_NOT_STICKY
    }

    private fun startForeground() {
        val channel = NotificationChannel("id", "channel", NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification =
            Notification.Builder(this, "id").setContentTitle("Terminal Running").setSmallIcon(R.drawable.icon).setColor(0x000000).setOngoing(true)
                .build()
        startForeground(1, notification)
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

}
