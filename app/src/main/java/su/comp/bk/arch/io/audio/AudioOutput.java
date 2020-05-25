/*
 * Copyright (C) 2020 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package su.comp.bk.arch.io.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;

import su.comp.bk.arch.io.Device;
import timber.log.Timber;

/**
 * Base sampled audio output device.
 */
public abstract class AudioOutput implements Device, Runnable {

    private final static int OUTPUT_SAMPLE_RATE = 22050;

    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;

    private final AudioTrack player;

    // Audio samples buffer
    private final short[] samplesBuffer;

    private Thread audioOutputThread;

    private boolean isRunning;

    private int volume = MAX_VOLUME;

    public AudioOutput() {
        int minBufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            throw new IllegalStateException("Invalid minimum audio buffer size: " + minBufferSize);
        }
        player = new AudioTrack(AudioManager.STREAM_MUSIC, OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);
        samplesBuffer = new short[minBufferSize / 2]; // two bytes per sample
        Timber.d("%s: created audio output, player buffer size: %d",
                getName(), minBufferSize);
    }

    public int getSampleRate() {
        return OUTPUT_SAMPLE_RATE;
    }

    public int getSamplesBufferSize() {
        return samplesBuffer.length;
    }

    @Override
    public void init(long cpuTime) {
        // Do nothing
    }

    /**
     * Set audio output volume.
     * @param volume audio output volume in range [0, 100]
     */
    public void setVolume(int volume) {
        this.volume = Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
        float gain = convertVolumeToGain(this.volume);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setVolume(gain);
        } else {
            player.setStereoVolume(gain, gain);
        }
    }

    /**
     * Get audio output volume.
     * @return audio output volume in range [0, 100]
     */
    public int getVolume() {
        return volume;
    }

    private static float convertVolumeToGain(int volume) {
        float minGain = AudioTrack.getMinVolume();
        float maxGain = AudioTrack.getMaxVolume();
        float gain = (float) ((Math.exp(volume / 100.) - 1.) / (Math.E - 1.));
        return minGain + gain * (maxGain - minGain);
    }

    public void start() {
        Timber.d("%s: starting audio output", getName());
        isRunning = true;
        audioOutputThread = new Thread(this, "AudioOutputThread-" + getName());
        audioOutputThread.start();
    }

    public void stop() {
        Timber.d("%s: stopping audio output", getName());
        isRunning = false;
        player.stop();
        while (audioOutputThread.isAlive()) {
            try {
                audioOutputThread.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public void pause() {
        Timber.d("%s: pausing audio output", getName());
        player.pause();
    }

    public void resume() {
        Timber.d("%s: resuming audio output", getName());
        player.play();
    }

    public void release() {
        Timber.d("%s: releasing audio output", getName());
        player.release();
    }

    @Override
    public void saveState(Bundle outState) {
        // Do nothing
    }

    @Override
    public void restoreState(Bundle inState) {
        // Do nothing
    }

    @Override
    public int read(long cpuTime, int address) {
        return 0;
    }

    @Override
    public void run() {
        Timber.d("%s: audio output started", getName());
        AudioLoop:
        while (true) {
            int sampleIndex = 0;
            while (sampleIndex < samplesBuffer.length) {
                if (!isRunning) {
                    break AudioLoop;
                }
                sampleIndex = writeSamples(samplesBuffer, sampleIndex);
            }
            player.write(samplesBuffer, 0, samplesBuffer.length);
        }
        Timber.d("%s: audio output stopped", getName());
    }

    /**
     * Get this audio output name.
     * @return audio output name
     */
    public abstract String getName();

    /**
     * Write audio samples to samples buffer.
     * @param samplesBuffer buffer to write samples
     * @param sampleIndex buffer index to write samples
     * @return updated samples buffer index
     */
    protected abstract int writeSamples(short[] samplesBuffer, int sampleIndex);
}
