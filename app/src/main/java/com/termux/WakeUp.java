package com.termux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class WakeUp extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ("1".equals(intent.getAction())) startForeground();
        else stop();
        return Service.START_NOT_STICKY;
    }

    private void startForeground() {
        var channel = new NotificationChannel("id", "channel", NotificationManager.IMPORTANCE_DEFAULT);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        var notification = new Notification.Builder(this, "id").setContentTitle("Terminal Running").setSmallIcon(R.drawable.icon).setColor(0x000000).setOngoing(true).build();
        startForeground(1, notification);
    }

    private void stop() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
