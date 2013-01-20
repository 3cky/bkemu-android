/*
 * Created: 11.06.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
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
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.comp.bk.arch.io;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.Computer.Configuration;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.opcode.BaseOpcode;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;

/**
 * Audio output (one bit PCM, bit 6 in SEL1 register).
 */
public class AudioOutput implements Device, Runnable {

    private static final String TAG = AudioOutput.class.getName();

    // Audio output bit
    public final static int OUTPUT_BIT = (1 << 6);

    // BK-0011M enable bit (0 enables audio output)
    public final static int BK0011M_ENABLE_BIT = (1 << 11);

    private final static int[] ADDRESSES = { Cpu.REG_SEL1 };

    private final static int OUTPUT_SAMPLE_RATE = 22050;

    private static final long NANOSECS_IN_SECOND = 1000000000L;

    private final Computer computer;

    private final AudioTrack player;

    // Audio samples buffer
    private final short[] samplesBuffer;

    // Current PCM sample value
    private short currentSample = Short.MIN_VALUE;
    // Last PCM sample timestamp (in CPU ticks)
    private long lastPcmTimestamp;

    // PCM timestamps circular buffer, one per output state change
    private final long[] pcmTimestamps;
    // PCM timestamps circular buffer put index
    private int putPcmTimestampIndex = 0;
    // PCM timestamps circular buffer get index
    private int getPcmTimestampIndex = 0;
    // PCM timestamps circular buffer current capacity
    private int pcmTimestampsCapacity;

    private Thread audioOutputThread;

    private boolean isRunning;

    private int lastOutputState;

    private final boolean isBk0011mMode;

    public AudioOutput(Computer computer, boolean isBk0011m) {
        this.computer = computer;
        this.isBk0011mMode = isBk0011m;
        int minBufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            throw new IllegalStateException("Invalid minimum audio buffer size: " + minBufferSize);
        }
        player = new AudioTrack(AudioManager.STREAM_MUSIC, OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);
        samplesBuffer = new short[minBufferSize / 2]; // two bytes per sample
        int pcmTimestampsBufferSize = (int) (samplesBuffer.length * computer.getClockFrequency()
                * 1000L / (OUTPUT_SAMPLE_RATE * BaseOpcode.getBaseExecutionTime()));
        pcmTimestamps = new long[pcmTimestampsBufferSize];
        pcmTimestampsCapacity = pcmTimestamps.length;
        Log.d(TAG, "created audio output, player buffer size: " + minBufferSize +
                ", PCM buffer size: " + pcmTimestampsCapacity);
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
    }

    public void start() {
        Log.d(TAG, "starting audio output");
        isRunning = true;
        audioOutputThread = new Thread(this, "AudioOutputThread");
        audioOutputThread.start();
    }

    public void stop() {
        Log.d(TAG, "stopping audio output");
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
        Log.d(TAG, "pausing audio output");
        player.pause();
    }

    public void resume() {
        Log.d(TAG, "resuming audio output");
        player.play();
    }

    public void release() {
        Log.d(TAG, "releasing audio output");
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
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        if (isBk0011mMode && (value & BK0011M_ENABLE_BIT) == 0) {
            int outputState = value & OUTPUT_BIT;
            if ((outputState ^ lastOutputState) != 0) {
                putPcmTimestamp(cpuTime);
            }
            lastOutputState = outputState;
            return true;
        }
        return false;
    }

    private synchronized void putPcmTimestamp(long pcmTimestamp) {
        if (pcmTimestampsCapacity > 0) {
            pcmTimestamps[putPcmTimestampIndex++] = pcmTimestamp;
            putPcmTimestampIndex %= pcmTimestamps.length;
            pcmTimestampsCapacity--;
        } else {
            Log.w(TAG, "PCM buffer overflow!");
        }
    }

    private synchronized long getPcmTimestamp() {
        long pcmTimestamp = -1L;
        if (pcmTimestampsCapacity < pcmTimestamps.length) {
            pcmTimestamp = pcmTimestamps[getPcmTimestampIndex++];
            getPcmTimestampIndex %= pcmTimestamps.length;
            pcmTimestampsCapacity++;
        }
        return pcmTimestamp;
    }

    private final long pcmSamplesToCpuTime(long numPcmSamples) {
        return computer.nanosToCpuTime(numPcmSamples * NANOSECS_IN_SECOND / OUTPUT_SAMPLE_RATE);
    }

    private final long cpuTimeToPcmSamples(long cpuTime) {
        return computer.cpuTimeToNanos(cpuTime) * OUTPUT_SAMPLE_RATE / NANOSECS_IN_SECOND;
    }

    @Override
    public void run() {
        Log.d(TAG, "audio output started");
        long pcmTimestamp;
        int numPcmSamples = 0;
        lastPcmTimestamp = computer.getCpu().getTime() - pcmSamplesToCpuTime(samplesBuffer.length);
        while (isRunning) {
            int sampleIndex = 0;
            while (sampleIndex < samplesBuffer.length) {
                if (numPcmSamples <= 0) {
                    pcmTimestamp = getPcmTimestamp();
                    if (pcmTimestamp >= 0) {
                        numPcmSamples = (int) (cpuTimeToPcmSamples(pcmTimestamp - lastPcmTimestamp));
                        currentSample = (currentSample > 0) ? Short.MIN_VALUE : Short.MAX_VALUE;
                        lastPcmTimestamp += pcmSamplesToCpuTime(numPcmSamples);
                    } else {
                        numPcmSamples = samplesBuffer.length - sampleIndex;
                        lastPcmTimestamp = computer.getCpu().getTime() -
                                pcmSamplesToCpuTime(samplesBuffer.length);
                    }
                }
                int numPcmSamplesToWrite = Math.min(numPcmSamples, samplesBuffer.length - sampleIndex);
                numPcmSamples -= numPcmSamplesToWrite;
                while (numPcmSamplesToWrite-- > 0) {
                    samplesBuffer[sampleIndex++] = currentSample;
                }
            }
            player.write(samplesBuffer, 0, samplesBuffer.length);
        }
        Log.d(TAG, "audio output stopped");
    }

}
