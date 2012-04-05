
package com.marakana.android.audioplayerdemo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AudioPlayerService extends Service {

    public static final String ACTION_PLAY_PAUSE = "com.marakana.android.audioplayerdemo.AudioPlayerService.ACTION_PLAY_PAUSE";

    public static final String ACTION_STOP = "com.marakana.android.audioplayerdemo.AudioPlayerService.ACTION_PLAY_PAUSE";

    private static final String TAG = "AudioPlayerService";

    private IAudioPlayerServiceImpl service;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        this.service = new IAudioPlayerServiceImpl(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand(" + intent + "," + flags + "," + startId + ")");
        if (ACTION_PLAY_PAUSE.equals(intent.getAction())) {
            if (this.service.isPlaying()) {
                this.service.pause();
            } else {
                this.service.play();
            }
        } else if (ACTION_STOP.equals(intent.getAction())) {
            this.service.stop();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind(" + intent + ")");
        return this.service;
    }
}
