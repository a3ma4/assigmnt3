package com.example.assigmnt3;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

public class MusicService extends Service {

    public static final String ACTION_PLAY = "com.example.assigmnt3.action.PLAY";
    public static final String ACTION_STOP = "com.example.assigmnt3.action.STOP";
    private static final String CHANNEL_ID = "music_channel";
    private static final int NOTIF_ID = 101;

    private MediaPlayer mediaPlayer;
    private final IBinder binder = new LocalBinder();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable notifUpdater;

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    startPlaying();
                    break;
                case ACTION_STOP:
                    stopPlaying();
                    stopSelf();
                    break;
            }
        }
        return START_STICKY;
    }

    private void startPlaying() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.sample_music);
            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlaying();
                stopSelf();
            });
        }

        mediaPlayer.start();
        startForeground(NOTIF_ID, buildNotification(getCurrentPosition(), getDuration(), true));
        startNotificationProgressUpdates();
    }

    private void stopPlaying() {
        stopNotificationProgressUpdates();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(true);
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public void seekTo(int pos) {
        if (mediaPlayer != null) mediaPlayer.seekTo(pos);
    }

    public void updateNotification() {
        Notification n = buildNotification(getCurrentPosition(), getDuration(), isPlaying());
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "تشغيل الموسيقى",
                    NotificationManager.IMPORTANCE_LOW);
            chan.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(chan);
        }
    }

    private Notification buildNotification(int progress, int duration, boolean isPlaying) {
        Intent stopIntent = new Intent(this, MusicService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPending = PendingIntent.getService(
                this, 0, stopIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("تشغيل الموسيقى")
                .setContentText(isPlaying ? "قيد التشغيل" : "متوقف")
                .setSmallIcon(R.drawable.ic_music)
                .addAction(0, "إيقاف", stopPending)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying);

        if (duration > 0) {
            builder.setProgress(duration, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private void startNotificationProgressUpdates() {
        stopNotificationProgressUpdates();
        notifUpdater = new Runnable() {
            @SuppressLint("NotificationPermission")
            @Override
            public void run() {
                Notification n = buildNotification(getCurrentPosition(), getDuration(), isPlaying());
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIF_ID, n);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(notifUpdater);
    }

    private void stopNotificationProgressUpdates() {
        if (notifUpdater != null) {
            handler.removeCallbacks(notifUpdater);
            notifUpdater = null;
        }
    }

    @Override
    public void onDestroy() {
        stopNotificationProgressUpdates();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
