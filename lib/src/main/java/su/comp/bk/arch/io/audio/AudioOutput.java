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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.opcode.BaseOpcode;
import su.comp.bk.arch.io.Device;
import su.comp.bk.state.State;

/**
 * Base sampled audio output device.
 */
public abstract class AudioOutput<U extends AudioOutputUpdate> implements Device {
    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    private static final long NANOSECS_IN_SECOND = 1000000000L;

    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;

    public static final short MIN_OUTPUT = 0;
    public static final short MAX_OUTPUT = Short.MAX_VALUE;

    // Audio sample rate
    private final int sampleRate;

    // Number of stereo sample frames in one mixer buffer
    private final int samplesBufferSize;

    // Audio output updates circular buffer, one per output state change
    private final U[] audioOutputUpdates;
    // Audio output updates circular buffer put index
    private int putAudioOutputUpdateIndex;
    // Audio output updates circular buffer get index
    private int getAudioOutputUpdateIndex;
    // Audio output updates circular buffer current capacity
    private int audioOutputUpdatesCapacity;

    private long lastUpdateTimestamp;
    private int numSamples;

    private final Computer computer;

    private int volume = MAX_VOLUME;

    AudioOutput(int sampleRate, int samplesBufferSize, Computer computer) {
        this.computer = computer;
        this.sampleRate = sampleRate;
        logger.debug("audio sample rate: {}", sampleRate);
        if (samplesBufferSize <= 0) {
            throw new IllegalStateException("Invalid audio buffer size: " + samplesBufferSize);
        }
        this.samplesBufferSize = samplesBufferSize;
        int audioOutputUpdatesSize = (int) (2 * getSamplesBufferSize() * computer.getNativeClockFrequency()
                * 1000L / (getSampleRate() * BaseOpcode.getBaseExecutionTime()));
        audioOutputUpdates = createAudioOutputUpdates(audioOutputUpdatesSize);
        logger.debug("created audio output, buffer frames: {}, updates buffer size: {}",
                this.samplesBufferSize, audioOutputUpdatesSize);
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getSamplesBufferSize() {
        return samplesBufferSize;
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
    }

    /**
     * Get audio output volume.
     * @return audio output volume in range [0, 100]
     */
    public int getVolume() {
        return volume;
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

    void flushAudioOutputUpdates() {
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

    void syncLastUpdateTimestamp() {
        lastUpdateTimestamp = getComputer().getUptimeTicks() -
                samplesToCpuTime(getSamplesBufferSize());
    }

    /**
     * Advance timing by one frame, consuming update events as needed, then fill
     * {@code sample[0]} (left) and {@code sample[1]} (right) with the current output value.
     * Called once per frame by {@link AudioMixer}.
     *
     * @param sample two-element array to receive the stereo sample [left, right]
     */
    void getSample(short[] sample) {
        while (numSamples <= 0) {
            U audioOutputUpdate = getAudioOutputUpdate();
            if (audioOutputUpdate != null) {
                handleAudioOutputUpdate(audioOutputUpdate);
                if (audioOutputUpdate.timestamp < lastUpdateTimestamp) {
                    continue;
                }
                numSamples = (int) cpuTimeToSamples(audioOutputUpdate.timestamp
                        - lastUpdateTimestamp);
                lastUpdateTimestamp += samplesToCpuTime(numSamples);
            } else {
                // No pending updates — hold current state for one more frame
                numSamples = 1;
                lastUpdateTimestamp += samplesToCpuTime(1);
            }
        }
        numSamples--;
        writeSample(sample);
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
     * Write one stereo sample frame into {@code sample}.
     * @param sample two-element array: {@code sample[0]} = left, {@code sample[1]} = right
     */
    protected abstract void writeSample(short[] sample);
}
