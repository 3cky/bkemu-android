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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.opcode.BaseOpcode;
import su.comp.bk.arch.io.Device;
import timber.log.Timber;

/**
 * Base sampled audio output device.
 */
public abstract class AudioOutput<U extends AudioOutputUpdate> implements Device, Runnable {
    private static final long NANOSECS_IN_SECOND = 1000000000L;

    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;

    public static final short MIN_OUTPUT = 0;
    public static final short MAX_OUTPUT = Short.MAX_VALUE;

    // Audio sample rate
    private final int sampleRate;

    // Audio samples buffer
    private final short[] samplesBuffer;

    // Audio output updates circular buffer, one per output state change
    private final U[] audioOutputUpdates;
    // Audio output updates circular buffer put index
    private int putAudioOutputUpdateIndex;
    // Audio output updates circular buffer get index
    private int getAudioOutputUpdateIndex;
    // Audio output updates circular buffer current capacity
    private int audioOutputUpdatesCapacity;

    private long lastAudioOutputUpdateTimestamp;
    private int numSamples;

    private final Computer computer;

    private final AudioTrack player;

    private Thread audioOutputThread;

    private boolean isRunning;

    private int volume = MAX_VOLUME;

    AudioOutput(Computer computer) {
        this.computer = computer;
        sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            throw new IllegalStateException("Invalid minimum audio buffer size: " + minBufferSize);
        }
        player = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);
        samplesBuffer = new short[minBufferSize / 2]; // two bytes per sample
        int audioOutputUpdatesSize = (int) (2 * getSamplesBufferSize() * computer.getClockFrequency()
                * 1000L / (getSampleRate() * BaseOpcode.getBaseExecutionTime()));
        audioOutputUpdates = createAudioOutputUpdates(audioOutputUpdatesSize);
        Timber.d("%s: created audio output, sample rate: %d, samples buffer size: %d, updates buffer size: %d",
                getName(), sampleRate, samplesBuffer.length, audioOutputUpdatesSize);
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getSamplesBufferSize() {
        return samplesBuffer.length;
    }

    Computer getComputer() {
        return computer;
    }

    @Override
    public void init(long cpuTime) {
        resetAudioOutputUpdates();
    }

    /**
     * Get default audio output volume.
     * @return default audio output volume.
     */
    public int getDefaultVolume() {
        return MAX_VOLUME;
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
        lastAudioOutputUpdateTimestamp = getComputer().getUptimeTicks() -
                samplesToCpuTime(getSamplesBufferSize());
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
                // Do nothing
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

    private synchronized void resetAudioOutputUpdates() {
        putAudioOutputUpdateIndex = 0;
        getAudioOutputUpdateIndex = 0;
        audioOutputUpdatesCapacity = audioOutputUpdates.length;
    }

    synchronized U putAudioOutputUpdate() {
        if (audioOutputUpdatesCapacity == 0) {
            Timber.w("%s: PCM samples buffer overflow!", getName());
            // Discard least recent element from the circular buffer
            getAudioOutputUpdateIndex = ++getAudioOutputUpdateIndex % audioOutputUpdates.length;
            audioOutputUpdatesCapacity++;
        }
        U pcmSample = audioOutputUpdates[putAudioOutputUpdateIndex++];
        putAudioOutputUpdateIndex %= audioOutputUpdates.length;
        audioOutputUpdatesCapacity--;
        return pcmSample;
    }

    private synchronized U getAudioOutputUpdate() {
        U audioOutputUpdate = null;
        if (audioOutputUpdatesCapacity < audioOutputUpdates.length) {
            audioOutputUpdate = audioOutputUpdates[getAudioOutputUpdateIndex++];
            getAudioOutputUpdateIndex %= audioOutputUpdates.length;
            audioOutputUpdatesCapacity++;
        }
        return audioOutputUpdate;
    }

    private long samplesToCpuTime(long numSamples) {
        return computer.nanosToCpuTime(numSamples * NANOSECS_IN_SECOND / getSampleRate());
    }

    private long cpuTimeToSamples(long cpuTime) {
        return computer.cpuTimeToNanos(cpuTime) * getSampleRate() / NANOSECS_IN_SECOND;
    }

    private int writeSamples(short[] samplesBuffer, int sampleIndex) {
        while (numSamples <= 0) {
            U audioOutputUpdate = getAudioOutputUpdate();
            if (audioOutputUpdate != null) {
                handleAudioOutputUpdate(audioOutputUpdate);
                numSamples = (int) (cpuTimeToSamples(audioOutputUpdate.timestamp
                        - lastAudioOutputUpdateTimestamp));
                lastAudioOutputUpdateTimestamp += samplesToCpuTime(numSamples);
            } else {
                numSamples = samplesBuffer.length - sampleIndex;
                lastAudioOutputUpdateTimestamp = getComputer().getUptimeTicks() -
                        samplesToCpuTime(getSamplesBufferSize());
            }
        }

        int numSamplesToWrite = Math.min(numSamples, samplesBuffer.length - sampleIndex);
        numSamples -= numSamplesToWrite;

        return writeSamples(samplesBuffer, sampleIndex, numSamplesToWrite);
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
     * Create array of audio output update event objects.
     * @param numAudioOutputUpdates number of audio output update event objects in array
     * @return array of audio output update event objects
     */
    protected abstract U[] createAudioOutputUpdates(int numAudioOutputUpdates);

    /**
     * Handle audio output update event.
     * @param audioOutputUpdate audio output update event object
     */
    protected abstract void handleAudioOutputUpdate(U audioOutputUpdate);

    /**
     * Write audio samples to samples buffer.
     * @param samplesBuffer buffer to write samples
     * @param sampleIndex buffer index to write samples
     * @param numSamples number of samples to write
     * @return updated samples buffer index
     */
    protected abstract int writeSamples(short[] samplesBuffer, int sampleIndex, int numSamples);
}
