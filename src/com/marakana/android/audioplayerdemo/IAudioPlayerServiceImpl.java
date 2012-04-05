
package com.marakana.android.audioplayerdemo;

import java.lang.ref.WeakReference;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.PowerManager;
import android.util.Log;

public class IAudioPlayerServiceImpl extends IAudioPlayerService.Stub implements
        OnPreparedListener, OnErrorListener, OnAudioFocusChangeListener, OnCompletionListener {
    private static final String TAG = "IAudioPlayerServiceImpl";

    private final IntentFilter AUDIO_BECOMING_NOISY_INTENT_FILTER = new IntentFilter(
            AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private final WeakReference<AudioPlayerService> audioPlayerService;

    private final Context context;

    private MediaPlayer mediaPlayer;

    private AudioManager audioManager;

    private NoisyAudioReceiver noisyAudioReceiver;

    private ComponentName remoteControlReceiverName;

    private boolean muted = false;

    public IAudioPlayerServiceImpl(AudioPlayerService audioPlayerService) {
        this.context = audioPlayerService.getApplicationContext();
        this.audioPlayerService = new WeakReference<AudioPlayerService>(audioPlayerService);
        this.audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        this.noisyAudioReceiver = new NoisyAudioReceiver();
        this.remoteControlReceiverName = new ComponentName(context, RemoteControlReceiver.class);
    }

    public synchronized void play() {
        if (this.mediaPlayer == null) {
            Log.d(TAG, "Initializing playback");
            this.mediaPlayer = new MediaPlayer();
            this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.test_cbr);
                try {
                    this.mediaPlayer.setDataSource(afd.getFileDescriptor());
                    Log.d(TAG, "Successfully set the data source");
                } finally {
                    afd.close();
                }
            } catch (Exception e) {
                Log.wtf(TAG, "Failed to initialize audio stream", e);
                this.stop();
            }
            this.mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            this.mediaPlayer.setOnErrorListener(this);
            this.mediaPlayer.setOnPreparedListener(this);
            this.mediaPlayer.prepareAsync(); // calls onPrepared when finished
            Log.d(TAG, "Waiting for prepare to finish");
        } else if (!this.mediaPlayer.isPlaying()) {
            Log.d(TAG, "Resuming playback.");
            this.mediaPlayer.start();
        } else {
            Log.d(TAG, "Going back to full volume.");
            this.mediaPlayer.setVolume(1.0f, 1.0f);
        }
    }

    public synchronized boolean pause() {
        if (this.mediaPlayer != null && this.mediaPlayer.isPlaying()) {
            Log.d(TAG, "Pausing playback.");
            this.mediaPlayer.pause();
            return true;
        } else {
            Log.d(TAG, "Not playing. Nothing to pause.");
            return false;
        }
    }

    public synchronized void stop() {
        if (this.mediaPlayer == null) {
            Log.d(TAG, "No media player. Nothing to release");
        } else {
            if (this.mediaPlayer.isPlaying()) {
                Log.d(TAG, "Stopping playback.");
                this.mediaPlayer.stop();
            }
            Log.d(TAG, "Releasing audio player.");
            this.mediaPlayer.release();
            this.mediaPlayer = null;

            Log.d(TAG, "Abandoning audio focus.");
            this.audioManager.abandonAudioFocus(this);

            Log.d(TAG, "Unregistering noisy audio receiver.");
            context.unregisterReceiver(this.noisyAudioReceiver);

            Log.d(TAG, "Unregistering remote audio control receiver.");
            this.audioManager.unregisterMediaButtonEventReceiver(this.remoteControlReceiverName);

            Log.d(TAG, "Stopping service.");
            this.audioPlayerService.get().stopForeground(true);
            this.audioPlayerService.get().stopSelf();
        }
    }

    public synchronized int duration() {
        return this.isStopped() ? 0 : this.mediaPlayer.getDuration();
    }

    public synchronized int position() {
        return this.isStopped() ? 0 : this.mediaPlayer.getCurrentPosition();
    }

    public synchronized int seek(int position) {
        if (this.isStopped()) {
            return 0;
        } else {
            this.mediaPlayer.seekTo(position);
            return this.mediaPlayer.getCurrentPosition();
        }
    }

    public synchronized boolean isPlaying() {
        return this.mediaPlayer != null && this.mediaPlayer.isPlaying();
    }

    public synchronized boolean isPaused() {
        return this.mediaPlayer != null && !this.mediaPlayer.isPlaying();
    }

    public synchronized boolean isStopped() {
        return this.mediaPlayer == null;
    }

    public synchronized void mute() {
        if (this.mediaPlayer != null) {
            this.mediaPlayer.setVolume(0.05f, 0.05f);
            this.muted = true;
        }
    }

    public synchronized void unmute() {
        if (this.mediaPlayer != null) {
            this.mediaPlayer.setVolume(1.0f, 1.0f);
            this.muted = false;
        }
    }

    public synchronized boolean isMuted() {
        return this.mediaPlayer != null && this.muted;
    }

    public synchronized void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "Media player is ready (prepared). Requesting audio focus.");
        if (this.audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Starting as foreground service");
            this.context.startService(new Intent(this.context, AudioPlayerService.class));
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context,
                    AudioPlayerActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification(android.R.drawable.ic_media_play,
                    context.getText(R.string.foreground_service_notificaton_ticker_text),
                    System.currentTimeMillis());
            notification.setLatestEventInfo(context,
                    context.getText(R.string.foreground_service_notification_title),
                    context.getText(R.string.foreground_service_notification_message),
                    pendingIntent);
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            this.audioPlayerService.get().startForeground(1, notification);

            Log.d(TAG, "Starting playback");
            this.mediaPlayer.setOnCompletionListener(this);
            this.mediaPlayer.start();

            Log.d(TAG, "Registering for noisy audio events");
            context.registerReceiver(this.noisyAudioReceiver, AUDIO_BECOMING_NOISY_INTENT_FILTER);

            Log.d(TAG, "Registering for audio remote control");
            this.audioManager.registerMediaButtonEventReceiver(this.remoteControlReceiverName);

            this.muted = false;
        } else {
            Log.w(TAG, "Failed to get audio focus");
            this.stop();
        }
    }

    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "Completed playback");
        this.stop();
    }

    // Called when MediaPlayer has encountered a problem from an async operation
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG,
                String.format("Music player encountered an error: what=%d, extra=%d", what, extra));
        this.stop();
        return true;
    }

    public synchronized void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "Re/gained focus.");
                this.play();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d(TAG, "Lost focus for an unbounded amount of time.");
                this.stop();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "Lost focus for a short time.");
                this.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "Lost focus for a short time. Can duck. Lowering volume");
                if (this.mediaPlayer != null) {
                    this.mediaPlayer.setVolume(0.1f, 0.1f);
                }
                break;
            default:
                Log.w(TAG, "Unexpected onAudioFocusChange(" + focusChange + ")");
        }
    }

    private class NoisyAudioReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Log.d(TAG, "Audio becoming noisy. Pausing.");
                IAudioPlayerServiceImpl.this.pause();
            }
        }
    }
}
