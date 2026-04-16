/*
 * Copyright (C) 2026 Victor Antonovich (v.antonovich@gmail.com)
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import su.comp.bk.arch.Computer;

/**
 * Audio mixer: mixes all registered {@link AudioOutput}s into a single stereo {@link AudioPlayer}.
 */
public class AudioMixer implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    private static final long NANOSECS_IN_SECOND = 1000000000L;

    private final AudioPlayer player;

    private final Computer computer;

    public static final String MASTER_OUTPUT_NAME = "master";
    private int masterVolume = AudioOutput.MAX_VOLUME;

    private final List<AudioOutput<?>> audioOutputs = new ArrayList<>();

    // Mix buffer written to AudioPlayer
    private final short[] mixBuffer;
    // Reusable single stereo sample scratch buffer [left, right]
    private final short[] sampleBuf = new short[2];

    private long nextSampleTimestamp;

    private static final float PLAYBACK_DELAY_SMOOTHING_ALPHA = 0.15f;
    private static final float PLAYBACK_DELAY_COMPENSATION_BETA = 0.005f;
    private long playbackDelay = 0L;

    private Thread mixerThread;
    private volatile boolean isRunning;
    private volatile boolean isPaused = true;

    public AudioMixer(AudioPlayer player, Computer computer) {
        this.player = player;
        this.computer = computer;
        this.mixBuffer = new short[getSamplesBufferSize() * 2]; // [left, right] * number of samples
    }

    public int getSampleRate() {
        return player.getSampleRate();
    }

    public int getSamplesBufferSize() {
        return player.getBufferSize() / 4; // 2 channels (left/right) x 2 bytes per channel
    }

    public int getMasterVolume() {
        return masterVolume;
    }

    public void setMasterVolume(int volume) {
        masterVolume = Math.max(AudioOutput.MIN_VOLUME, Math.min(AudioOutput.MAX_VOLUME, volume));
        updateAudioPlayerGain();
    }

    private void updateAudioPlayerGain() {
        player.setGain(convertVolumeToGain(masterVolume));
    }

    public void addOutput(AudioOutput<?> audioOutput) {
        if (isRunning) {
            throw new IllegalStateException("Can't add audio output while audio mixer is running: "
                    + audioOutput);
        }
        audioOutputs.add(audioOutput);
    }

    /**
     * Get list of available {@link AudioOutput}s.
     * @return audio outputs list
     */
    public List<AudioOutput<?>> getAudioOutputs() {
        return audioOutputs;
    }

    public void start() {
        logger.debug("starting audio mixer");
        isRunning = true;
        mixerThread = new Thread(this, "AudioMixerThread");
        mixerThread.start();
    }

    public void stop() {
        logger.debug("stopping audio mixer");
        isRunning = false;
        player.stop();
        synchronized (this) {
            this.notify();
        }
        while (mixerThread.isAlive()) {
            try {
                mixerThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        for (AudioOutput<?> o : audioOutputs) {
            o.flushAudioOutputUpdates();
        }
    }

    public void pause() {
        logger.debug("pausing audio mixer");
        isPaused = true;
        player.pause();
    }

    public void resume() {
        logger.debug("resuming audio mixer");
        player.resume();
        updateAudioPlayerGain();
        isPaused = false;
        synchronized (this) {
            this.notify();
        }
    }

    public void release() {
        logger.debug("releasing audio mixer");
        player.release();
    }

    @Override
    public void run() {
        logger.debug("audio mixer started");
        resetNextSampleTimestamp();
        MixerLoop:
        while (true) {
            if (isPaused) {
                if (!isRunning) {
                    break;
                }
                synchronized (this) {
                    try {
                        this.wait(100L);
                    } catch (InterruptedException ignored) {
                    }
                }
                resetNextSampleTimestamp();
            } else {
                if (!isRunning) {
                    break MixerLoop;
                }
                if (isPaused) {
                    continue MixerLoop;
                }
                mixAudioOutputs();
                player.play(mixBuffer, 0, mixBuffer.length);
            }
        }
        logger.debug("audio mixer stopped");
    }

    private long samplesToCpuTime(long numSamples) {
        return computer.nanosToCpuTime(numSamples * NANOSECS_IN_SECOND / getSampleRate());
    }

    private long getSamplesBufferSizeInCpuTicks() {
        return samplesToCpuTime(getSamplesBufferSize());
    }

    private long getSamplesBufferStartTimestamp() {
        return computer.getUptimeTicks() - getSamplesBufferSizeInCpuTicks();
    }

    private void resetNextSampleTimestamp() {
        nextSampleTimestamp = getSamplesBufferStartTimestamp();
        playbackDelay = 0L;
    }

     private void mixAudioOutputs() {
        long currentPlaybackDelay = getSamplesBufferStartTimestamp() - nextSampleTimestamp;
        playbackDelay = (long) (PLAYBACK_DELAY_SMOOTHING_ALPHA * currentPlaybackDelay
                + (1f - PLAYBACK_DELAY_SMOOTHING_ALPHA) * playbackDelay);
        if (playbackDelay < 0) {
            Arrays.fill(mixBuffer, (short) 0);
            return;
        }
        long playbackDelayCompensation = (long) (PLAYBACK_DELAY_COMPENSATION_BETA * playbackDelay);
        long sampleTimestep = (getSamplesBufferSizeInCpuTicks() + playbackDelayCompensation)
                / getSamplesBufferSize();
        int bufferIndex = 0;
        while (bufferIndex < mixBuffer.length) {
            int leftChannelAccum = 0;
            int rightChannelAccum = 0;
            for (AudioOutput<?> audioOutput : audioOutputs) {
                audioOutput.getSample(sampleBuf, nextSampleTimestamp);
                float audioOutputGain = convertVolumeToGain(audioOutput.getVolume());
                leftChannelAccum += (int) (sampleBuf[0] * audioOutputGain);
                rightChannelAccum += (int) (sampleBuf[1] * audioOutputGain);
            }
            mixBuffer[bufferIndex++] = clipSample(leftChannelAccum);
            mixBuffer[bufferIndex++] = clipSample(rightChannelAccum);
            nextSampleTimestamp += sampleTimestep;
        }
    }

    private static short clipSample(int sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
    }

    // https://electronics.stackexchange.com/a/425776
    private static float convertVolumeToGain(int volume) {
        float a = volume / 100f;
        float K = 2.0f;
        return a / (1f + (1f - a) * K);
    }
}
