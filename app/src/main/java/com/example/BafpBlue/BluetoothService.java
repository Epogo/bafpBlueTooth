package com.example.BafpBlue;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class BluetoothService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Run your background Bluetooth operations here
        startForegroundService();

        // If the service gets killed, restart it with this flag
        return START_STICKY;
    }

    private void startForegroundService() {
        // Create a notification to show while the service is running
        Notification notification = new NotificationCompat.Builder(this, "CHANNEL_ID")
                .setContentTitle("Bluetooth Service")
                .setContentText("Running background operations")
                .setSmallIcon(R.drawable.ic_bluetooth)
                .build();

        // Start the service in the foreground with this notification
        startForeground(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return null because we're not binding to this service
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up any resources when service is destroyed
    }
}

