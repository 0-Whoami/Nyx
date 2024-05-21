package com.termux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class WakeUp : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == "1") {
            stop()
        } else {
            createNotificationChannel()
            startForeground()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("id", "channel", NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForeground() {
        val notification =
            Notification.Builder(this, "id").setContentTitle("Terminal Running").setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher_foreground).build()
        startForeground(1, notification)
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

}
