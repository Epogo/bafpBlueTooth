package com.example.BafpBlue;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class AlertManager {
    private Context context;

    public AlertManager(Context context) {
        this.context = context;
    }

    public void showAlert(String message) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create intent to open MainActivity when the notification is clicked
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Default notification sound
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "DISCONNECT_CHANNEL_ID")
                .setSmallIcon(R.drawable.ic_bluetooth)  // Ensure this icon exists
                .setContentTitle("Bluetooth Disconnected")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setSound(soundUri)
                .setAutoCancel(true);

        // Show the notification
        if (notificationManager != null) {
            notificationManager.notify(1, builder.build());
        }
    }

}
