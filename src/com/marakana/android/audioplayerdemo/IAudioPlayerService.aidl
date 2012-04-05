package com.marakana.android.audioplayerdemo;

interface IAudioPlayerService {
    void play();
    boolean pause();
    void stop();
    int duration();
    int position();
    int seek(int position);
    void mute();
    void unmute();
    boolean isPlaying();
    boolean isPaused();
    boolean isStopped();
    boolean isMuted();
}