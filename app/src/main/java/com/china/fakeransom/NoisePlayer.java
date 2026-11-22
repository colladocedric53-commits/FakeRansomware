package com.china.fakeransom;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

final class NoisePlayer {

    private static final int BASE_RATE = 8000;
    private AudioTrack track;

    synchronized void start(float volume) {
        stop();
        try {
            int seconds = 2;
            int frames = BASE_RATE * seconds;
            short[] pcm = new short[frames];
            Random r = new Random();
            int last = 0;
            for (int i = 0; i < frames; i++) {
                int n = (r.nextInt() % 900) - 450;
                last = (int) (last * 0.25 + n);
                pcm[i] = (short) Math.max(-1400, Math.min(1400, last * 3));
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(BASE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            track = new AudioTrack(attrs, fmt, frames * 2, AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.write(pcm, 0, frames);
            track.setLoopPoints(0, frames, -1);
            track.setVolume(volume);
            track.play();
        } catch (Throwable t) {
            track = null;
        }
    }

    synchronized void setVolume(float volume) {
        if (track != null) {
            try {
                track.setVolume(Math.max(0f, Math.min(1f, volume)));
            } catch (Throwable ignored) {
            }
        }
    }

    synchronized void stop() {
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Throwable ignored) {
            }
            track = null;
        }
    }
}
