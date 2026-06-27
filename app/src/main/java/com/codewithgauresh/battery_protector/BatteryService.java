package com.codewithgauresh.battery_protector;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class BatteryService extends Service {
    private static final String TAG = "BatteryService";
    private static final String CHANNEL_ID = "BatteryProtectorChannel";
    private int batteryLimit = 80;
    private boolean alarmEnabled = true;
    private Ringtone ringtone;
    private String ringtoneUriString;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) ((level / (float) scale) * 100);

            updateNotification(batteryPct);

            if (batteryPct >= batteryLimit && alarmEnabled) {
                playAlarm();
            } else {
                stopAlarm();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            batteryLimit = intent.getIntExtra("limit", 80);
            alarmEnabled = intent.getBooleanExtra("alarm", true);
            ringtoneUriString = intent.getStringExtra("ringtoneUri");
        }

        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.battery_protector_active))
                .setContentText(getString(R.string.monitoring_battery))
                .setSmallIcon(R.drawable.ic_battery_bolt)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(batteryReceiver, filter);
        }

        return START_STICKY;
    }

    @SuppressWarnings("IfCanBeSwitch")
    private void updateNotification(int percentage) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.battery_protector_active))
                .setContentText(getString(R.string.notification_text, percentage, batteryLimit))
                .setSmallIcon(R.drawable.ic_battery_bolt)
                .setOngoing(true)
                .setSilent(true) // Ensure no sound or vibration on update
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(1, notification);
                }
            } else {
                manager.notify(1, notification);
            }
        }
    }

    private void playAlarm() {
        if (ringtone != null && ringtone.isPlaying()) return;

        Uri uri;
        if (ringtoneUriString != null) {
            uri = Uri.parse(ringtoneUriString);
        } else {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }

        try {
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing alarm", e);
        }
    }

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Battery Protector Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setSound(null, null); // Disable sound for this channel
        serviceChannel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(batteryReceiver);
        stopAlarm();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
