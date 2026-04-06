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

/**
 * Audio mixer: mixes all registered {@link AudioOutput}s into a single stereo {@link AudioPlayer}.
 */
public class AudioMixer implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    private final AudioPlayer player;

    private final List<AudioOutput<?>> audioOutputs = new ArrayList<>();

    // Mix buffer written to AudioPlayer
    private final short[] mixBuffer;
    // Reusable single stereo sample scratch buffer [left, right]
    private final short[] sampleBuf = new short[2];

    private Thread mixerThread;
    private volatile boolean isRunning;
    private volatile boolean isPaused = true;

    public AudioMixer(AudioPlayer player) {
        this.player = player;
        this.mixBuffer = new short[getSamplesBufferSize() * 2]; // [left, right] * number of samples
    }

    public int getSampleRate() {
        return player.getSampleRate();
    }

    public int getSamplesBufferSize() {
        return player.getBufferSize() / 4; // 2 channels (left/right) x 2 bytes per channel
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
        syncAudioOutputLastUpdateTimestamps();
        Arrays.fill(mixBuffer, (short) 0);
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
                syncAudioOutputLastUpdateTimestamps();
                Arrays.fill(mixBuffer, (short) 0);
            } else {
                player.play(mixBuffer, 0, mixBuffer.length);
                if (!isRunning) {
                    break MixerLoop;
                }
                if (isPaused) {
                    continue MixerLoop;
                }
                mixAudioOutputs();
            }
        }
        logger.debug("audio mixer stopped");
    }

    private void syncAudioOutputLastUpdateTimestamps() {
        for (AudioOutput<?> audioOutput : audioOutputs) {
            audioOutput.syncLastUpdateTimestamp();
        }
    }

    private void mixAudioOutputs() {
        int i = 0;
        while (i < mixBuffer.length) {
            int leftChannelAccum = 0;
            int rightChannelAccum = 0;
            for (AudioOutput<?> audioOutput : audioOutputs) {
                audioOutput.getSample(sampleBuf);
                float audioOutputGain = convertVolumeToGain(audioOutput.getVolume());
                leftChannelAccum += (int) (sampleBuf[0] * audioOutputGain);
                rightChannelAccum += (int) (sampleBuf[1] * audioOutputGain);
            }
            mixBuffer[i++] = clipSample(leftChannelAccum);
            mixBuffer[i++] = clipSample(rightChannelAccum);
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
