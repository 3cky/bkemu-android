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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.opcode.BaseOpcode;
import su.comp.bk.arch.io.Device;
import su.comp.bk.state.State;

/**
 * Base sampled audio output device.
 */
public abstract class AudioOutput<U extends AudioOutputUpdate> implements Device, Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

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

    private final AudioPlayer player;

    private Thread audioOutputThread;

    private boolean isRunning;
    private boolean isPaused = true;

    private int volume = MAX_VOLUME;

    AudioOutput(AudioPlayer player, Computer computer) {
        this.computer = computer;
        this.player = player;
        sampleRate = player.getSampleRate();
        logger.debug("audio player sample rate: {}", sampleRate);
        int minBufferSize = player.getBufferSize();
        if (minBufferSize <= 0) {
            throw new IllegalStateException("Invalid minimum audio buffer size: " + minBufferSize);
        }
        samplesBuffer = new short[minBufferSize / 2]; // two bytes per sample
        int audioOutputUpdatesSize = (int) (2 * getSamplesBufferSize() * computer.getNativeClockFrequency()
                * 1000L / (getSampleRate() * BaseOpcode.getBaseExecutionTime()));
        audioOutputUpdates = createAudioOutputUpdates(audioOutputUpdatesSize);
        logger.debug("created audio output, samples buffer size: {}, updates buffer size: {}",
                samplesBuffer.length, audioOutputUpdatesSize);
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
    public void init(long cpuTime, boolean isHardwareReset) {
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
        player.setGain(convertVolumeToGain(this.volume));
    }

    /**
     * Get audio output volume.
     * @return audio output volume in range [0, 100]
     */
    public int getVolume() {
        return volume;
    }

    // https://electronics.stackexchange.com/a/425776
    private static float convertVolumeToGain(int volume) {
        float a = volume / 100f;
        float K = 2.0f;
        return a / (1f + (1f - a) * K);
    }

    public void start() {
        logger.debug("starting audio output");
        isRunning = true;
        audioOutputThread = new Thread(this, "AudioOutputThread-" + getName());
        audioOutputThread.start();
    }

    public void stop() {
        logger.debug("stopping audio output");
        isRunning = false;
        player.stop();
        synchronized (this) {
            this.notify();
        }
        while (audioOutputThread.isAlive()) {
            try {
                audioOutputThread.join();
            } catch (InterruptedException e) {
                // Do nothing
            }
        }
        flushAudioOutputUpdates();
    }

    public void pause() {
        logger.debug("pausing audio output");
        isPaused = true;
        player.pause();
    }

    public void resume() {
        logger.debug("resuming audio output");
        player.resume();
        isPaused = false;
        synchronized (this) {
            this.notify();
        }
    }

    public void release() {
        logger.debug("releasing audio output");
        player.release();
    }

    @Override
    public void saveState(State outState) {
        // Do nothing
    }

    @Override
    public void restoreState(State inState) {
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
            // Warn about buffer overflows only if we are not in the free running CPU mode
            if (computer.getClockFrequency() > 0) {
                logger.warn("PCM samples buffer overflow!");
            }
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

    protected void flushAudioOutputUpdates() {
        if (isRunning) {
            throw new IllegalStateException("Can't flush audio output while it's running");
        }
        while (true) {
            U audioOutputUpdate = getAudioOutputUpdate();
            if (audioOutputUpdate == null) {
                break;
            }
            handleAudioOutputUpdate(audioOutputUpdate);
        }
    }

    private long samplesToCpuTime(long numSamples) {
        return computer.nanosToCpuTime(numSamples * NANOSECS_IN_SECOND / getSampleRate());
    }

    private long cpuTimeToSamples(long cpuTime) {
        return computer.cpuTimeToNanos(cpuTime) * getSampleRate() / NANOSECS_IN_SECOND;
    }

    private void syncLastAudioOutputUpdateTimestamp() {
        lastAudioOutputUpdateTimestamp = getComputer().getUptimeTicks() -
                samplesToCpuTime(getSamplesBufferSize());
    }

    private int writeSamples(short[] samplesBuffer, int sampleIndex) {
        while (numSamples <= 0) {
            U audioOutputUpdate = getAudioOutputUpdate();
            if (audioOutputUpdate != null) {
                handleAudioOutputUpdate(audioOutputUpdate);
                if (audioOutputUpdate.timestamp < lastAudioOutputUpdateTimestamp) {
                    continue;
                }
                numSamples = (int) (cpuTimeToSamples(audioOutputUpdate.timestamp
                        - lastAudioOutputUpdateTimestamp));
                lastAudioOutputUpdateTimestamp += samplesToCpuTime(numSamples);
            } else {
                numSamples = getSamplesBufferSize() - sampleIndex;
                if (sampleIndex > 0) {
                    lastAudioOutputUpdateTimestamp += samplesToCpuTime(numSamples);
                } else {
                    syncLastAudioOutputUpdateTimestamp();
                }
            }
        }

        int numSamplesToWrite = Math.min(numSamples, getSamplesBufferSize() - sampleIndex);
        numSamples -= numSamplesToWrite;

        return writeSamples(samplesBuffer, sampleIndex, numSamplesToWrite);
    }

    @Override
    public void run() {
        logger.debug("audio output started");
        syncLastAudioOutputUpdateTimestamp();
        Arrays.fill(samplesBuffer, (short) 0);
        AudioLoop:
        while (true) {
            if (isPaused) {
                if (!isRunning) {
                    break; // AudioLoop
                }
                synchronized (this) {
                    try {
                        this.wait(100L);
                    } catch (InterruptedException ignored) {
                    }
                }
                syncLastAudioOutputUpdateTimestamp();
                Arrays.fill(samplesBuffer, (short) 0);
            } else {
                player.play(samplesBuffer, 0, samplesBuffer.length);
                int sampleIndex = 0;
                while (sampleIndex < getSamplesBufferSize()) {
                    if (!isRunning) {
                        break AudioLoop;
                    }
                    if (isPaused) {
                        continue AudioLoop;
                    }
                    sampleIndex = writeSamples(samplesBuffer, sampleIndex);
                }
            }
        }
        logger.debug("audio output stopped");
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
