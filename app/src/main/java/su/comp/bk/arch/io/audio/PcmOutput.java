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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.opcode.BaseOpcode;
import timber.log.Timber;

public abstract class PcmOutput extends AudioOutput {
    private static final long NANOSECS_IN_SECOND = 1000000000L;

    // Current PCM sample value
    private short currentSample = Short.MIN_VALUE;
    // Last PCM sample timestamp (in CPU ticks)
    private long lastPcmTimestamp;
    // Number of PCM samples left to write to the samples buffer
    private int numPcmSamples = 0;

    // PCM timestamps circular buffer, one per output state change
    private final long[] pcmTimestamps;
    // PCM timestamps circular buffer put index
    private int putPcmTimestampIndex = 0;
    // PCM timestamps circular buffer get index
    private int getPcmTimestampIndex = 0;
    // PCM timestamps circular buffer current capacity
    private int pcmTimestampsCapacity;

    private final Computer computer;

    protected PcmOutput(Computer computer) {
        super();
        this.computer = computer;
        int pcmTimestampsBufferSize = (int) (getSamplesBufferSize() * computer.getClockFrequency()
                * 1000L / (getSampleRate() * BaseOpcode.getBaseExecutionTime()));
        pcmTimestamps = new long[pcmTimestampsBufferSize];
        pcmTimestampsCapacity = pcmTimestamps.length;
    }

    @Override
    public void start() {
        super.start();
        lastPcmTimestamp = computer.getCpu().getTime() - pcmSamplesToCpuTime(getSamplesBufferSize());
    }

    protected synchronized void putPcmTimestamp(long pcmTimestamp) {
        if (pcmTimestampsCapacity > 0) {
            pcmTimestamps[putPcmTimestampIndex++] = pcmTimestamp;
            putPcmTimestampIndex %= pcmTimestamps.length;
            pcmTimestampsCapacity--;
        } else {
            Timber.w("%s: PCM buffer overflow!", getName());
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

    private long pcmSamplesToCpuTime(long numPcmSamples) {
        return computer.nanosToCpuTime(numPcmSamples * NANOSECS_IN_SECOND / getSampleRate());
    }

    private long cpuTimeToPcmSamples(long cpuTime) {
        return computer.cpuTimeToNanos(cpuTime) * getSampleRate() / NANOSECS_IN_SECOND;
    }

    @Override
    protected int writeSamples(short[] samplesBuffer, int sampleIndex) {
        if (numPcmSamples <= 0) {
            long pcmTimestamp = getPcmTimestamp();
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

        return sampleIndex;
    }
}
