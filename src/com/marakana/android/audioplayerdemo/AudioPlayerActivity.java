
package com.marakana.android.audioplayerdemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class AudioPlayerActivity extends Activity implements ServiceConnection,
        OnSeekBarChangeListener, Runnable {
    private static final String TAG = "AudioPlayerActivity";

    private static final int JUMP_OFFSET = 3000;

    private Handler handler;

    private IAudioPlayerService service;

    private TextView status;

    private SeekBar seekBar;

    private ImageButton goToBeginningButton;

    private ImageButton reverseButton;

    private ImageButton playButton;

    private ImageButton pauseButton;

    private ImageButton stopButton;

    private ImageButton fastForwardButton;

    private ImageButton goToEndButton;

    private ImageButton muteButton;

    private ImageButton unmuteButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        this.status = (TextView)super.findViewById(R.id.status);
        this.seekBar = (SeekBar)super.findViewById(R.id.seekBar);
        this.seekBar.setOnSeekBarChangeListener(this);
        this.goToBeginningButton = (ImageButton)super.findViewById(R.id.goToBeginningButton);
        this.reverseButton = (ImageButton)super.findViewById(R.id.reverseButton);
        this.playButton = (ImageButton)super.findViewById(R.id.playButton);
        this.pauseButton = (ImageButton)super.findViewById(R.id.pauseButton);
        this.stopButton = (ImageButton)super.findViewById(R.id.stopButton);
        this.fastForwardButton = (ImageButton)super.findViewById(R.id.fastForwardButton);
        this.goToEndButton = (ImageButton)super.findViewById(R.id.goToEndButton);
        this.muteButton = (ImageButton)super.findViewById(R.id.muteButton);
        this.unmuteButton = (ImageButton)super.findViewById(R.id.unmuteButton);
        this.handler = new Handler();
        this.toggleButtons(false, false);
        Log.d(TAG, "onCreate(" + savedInstanceState + ")");
    }

    private void toggleButtons(boolean enablePlay, boolean enableOthers) {
        this.goToBeginningButton.setEnabled(enableOthers);
        this.reverseButton.setEnabled(enableOthers);
        this.playButton.setEnabled(enablePlay);
        this.pauseButton.setEnabled(enableOthers);
        this.stopButton.setEnabled(enableOthers);
        this.fastForwardButton.setEnabled(enableOthers);
        this.goToEndButton.setEnabled(enableOthers);
        this.muteButton.setEnabled(enableOthers);
        this.unmuteButton.setEnabled(enableOthers);
        this.togglePlayPauseButtons(false);
        this.toggleMuteUnmuteButtons(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Binding to service...");
        if (super.bindService(new Intent(this, AudioPlayerService.class), this, BIND_AUTO_CREATE)) {
            Log.d(TAG, " done");
        } else {
            Log.e(TAG, " failed");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "Unbinding from service...");
        this.unbindService(this);
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "Connected to service " + name);
        this.service = IAudioPlayerService.Stub.asInterface(service);
        try {
            this.toggleButtons(true, !this.service.isStopped());
            boolean playing = this.service.isPlaying();
            this.togglePlayPauseButtons(playing);
            if (playing) {
                this.handler.post(this);
            }
        } catch (RemoteException e) {
            Log.wtf(TAG, "Failed", e);
        }
    }

    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "Disconnected from service " + name);
        this.service = null;
        this.toggleButtons(false, false);
    }

    public void goToBeginning(View v) throws RemoteException {
        Log.d(TAG, "goToBeginning()");
        this.service.seek(0);
    }

    public void reverse(View v) throws RemoteException {
        Log.d(TAG, "reverse()");
        this.service.seek(Math.max(0, this.service.position() - JUMP_OFFSET));
    }

    public void play(View v) throws RemoteException {
        Log.d(TAG, "play()");
        this.service.play();
        this.toggleButtons(true, true);
        this.togglePlayPauseButtons(true);
        this.handler.postDelayed(this, 100);
    }

    public void pause(View v) throws RemoteException {
        Log.d(TAG, "pause()");
        this.service.pause();
        this.togglePlayPauseButtons(false);
    }

    public void stop(View v) throws RemoteException {
        Log.d(TAG, "stop()");
        this.service.stop();
        this.toggleButtons(true, false);
        this.togglePlayPauseButtons(false);
        this.handler.removeCallbacks(this);
        this.seekBar.setEnabled(false);
        this.seekBar.setProgress(0);
    }

    public void fastForward(View v) throws RemoteException {
        Log.d(TAG, "fastForward()");
        this.service.seek(Math.min(this.service.duration(), this.service.position() + JUMP_OFFSET));
    }

    public void goToEnd(View v) throws RemoteException {
        Log.d(TAG, "goToEnd()");
        this.service.pause();
        this.service.seek(this.service.duration());
        this.togglePlayPauseButtons(false);
    }

    public void mute(View v) throws RemoteException {
        Log.d(TAG, "mute()");
        this.service.mute();
        this.toggleMuteUnmuteButtons(true);
    }

    public void unmute(View v) throws RemoteException {
        Log.d(TAG, "unmute()");
        this.service.unmute();
        this.toggleMuteUnmuteButtons(false);
    }

    private void togglePlayPauseButtons(boolean playing) {
        this.pauseButton.setVisibility(playing ? View.VISIBLE : View.GONE);
        this.playButton.setVisibility(playing ? View.GONE : View.VISIBLE);
    }

    private void toggleMuteUnmuteButtons(boolean muted) {
        this.unmuteButton.setVisibility(muted ? View.VISIBLE : View.GONE);
        this.muteButton.setVisibility(muted ? View.GONE : View.VISIBLE);
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // ignored
    }

    private boolean wasPlayingBeforeSeeking = false;

    public void onStartTrackingTouch(SeekBar seekBar) {
        try {
            this.wasPlayingBeforeSeeking = this.service.isPlaying();
            if (this.wasPlayingBeforeSeeking) {
                this.service.pause();
            }
            this.handler.removeCallbacks(this);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Failed to talk to the service", e);
        }
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        try {
            this.service.seek(seekBar.getProgress());
            if (this.wasPlayingBeforeSeeking) {
                this.service.play();
            }
            this.handler.post(this);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Failed to talk to the service", e);
        }
    }

    private static String formatAsTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public void run() {
        try {
            if (this.service == null || this.service.isStopped()) {
                this.seekBar.setProgress(0);
                this.seekBar.setEnabled(false);
                this.toggleButtons(true, false);
                this.togglePlayPauseButtons(false);
                this.status.setText(R.string.init_time);
            } else {
                int position = this.service.position();
                // update the status
                this.status.setText(formatAsTime(position));

                // update the seekbar
                this.seekBar.setMax(this.service.duration());
                this.seekBar.setProgress(position);
                this.seekBar.setEnabled(true);

                if (this.service.isPlaying()) {
                    // schedule a callback of this method in 500 ms
                    this.handler.postDelayed(this, 500);
                }
            }
        } catch (RemoteException e) {
            Log.wtf(TAG, "Failed to talk to the service", e);
        }
    }
}
