package com.example.assigmnt3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private Button btnPlay, btnStop;
    private TextView tvStatus, tvElapsed, tvDuration;
    private SeekBar seekBar;

    private MusicService musicService;
    private boolean bound = false;

    private Thread updateThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean userSeeking = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.LocalBinder localBinder = (MusicService.LocalBinder) binder;
            musicService = localBinder.getService();
            bound = true;
            startUpdateThread();
            musicService.updateNotification();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            musicService = null;
            stopUpdateThread();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlay = findViewById(R.id.btnPlay);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvElapsed = findViewById(R.id.tvElapsed);
        tvDuration = findViewById(R.id.tvDuration);
        seekBar = findViewById(R.id.seekBar);

        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY);
            ContextCompat.startForegroundService(this, intent);
            bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
        });

        btnStop.setOnClickListener(v -> {
            Intent intent = new Intent(this, MusicService.class);
            intent.setAction(MusicService.ACTION_STOP);
            ContextCompat.startForegroundService(this, intent);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) tvElapsed.setText(formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                if (musicService != null) musicService.seekTo(sb.getProgress());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        stopUpdateThread();
    }

    private void startUpdateThread() {
        stopUpdateThread();
        running.set(true);
        updateThread = new Thread(() -> {
            while (running.get()) {
                try {
                    if (musicService != null) {
                        boolean playing = musicService.isPlaying();
                        int pos = musicService.getCurrentPosition();
                        int dur = musicService.getDuration();

                        runOnUiThread(() -> {
                            tvStatus.setText(playing ? getString(R.string.status_playing) : getString(R.string.status_stopped));
                            tvElapsed.setText(formatTime(pos));
                            tvDuration.setText(formatTime(dur));
                            if (!userSeeking) {
                                seekBar.setMax(dur > 0 ? dur : 100);
                                seekBar.setProgress(pos);
                            }
                        });
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        updateThread.start();
    }

    private void stopUpdateThread() {
        running.set(false);
        if (updateThread != null) {
            updateThread.interrupt();
            updateThread = null;
        }
    }

    private String formatTime(int ms) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }
}
