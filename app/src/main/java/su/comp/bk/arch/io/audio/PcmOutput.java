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
    private short currentSample;
    // Last PCM sample timestamp (in CPU ticks)
    private long lastPcmTimestamp;
    // Number of PCM samples left to write to the samples buffer
    private int numPcmSamples = 0;

    // PCM samples circular buffer, one per output state change
    private final PcmSample[] pcmSamples;
    // PCM samples circular buffer put index
    private int putPcmSampleIndex = 0;
    // PCM samples circular buffer get index
    private int getPcmSampleIndex = 0;
    // PCM samples circular buffer current capacity
    private int pcmSamplesCapacity;

    private final Computer computer;

    private static class PcmSample {
        // PCM sample value
        private short value;
        // PCM sample timestamp
        private long timestamp;
    }

    protected PcmOutput(Computer computer) {
        super();
        this.computer = computer;
        int pcmTimestampsBufferSize = (int) (getSamplesBufferSize() * computer.getClockFrequency()
                * 1000L / (getSampleRate() * BaseOpcode.getBaseExecutionTime()));
        pcmSamples = new PcmSample[pcmTimestampsBufferSize];
        for (int i = 0; i < pcmSamples.length; i++) {
            pcmSamples[i] = new PcmSample();
        }
        pcmSamplesCapacity = pcmSamples.length;
    }

    @Override
    public void start() {
        super.start();
        lastPcmTimestamp = computer.getCpu().getTime() - pcmSamplesToCpuTime(getSamplesBufferSize());
    }

    protected synchronized void putPcmSample(short pcmValue, long pcmTimestamp) {
        if (pcmSamplesCapacity > 0) {
            PcmSample pcmSample = pcmSamples[putPcmSampleIndex++];
            pcmSample.value = pcmValue;
            pcmSample.timestamp = pcmTimestamp;
            putPcmSampleIndex %= pcmSamples.length;
            pcmSamplesCapacity--;
        } else {
            Timber.w("%s: PCM samples buffer overflow!", getName());
        }
    }

    private synchronized PcmSample getPcmSample() {
        PcmSample pcmSample = null;
        if (pcmSamplesCapacity < pcmSamples.length) {
            pcmSample = pcmSamples[getPcmSampleIndex++];
            getPcmSampleIndex %= pcmSamples.length;
            pcmSamplesCapacity++;
        }
        return pcmSample;
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
            PcmSample pcmSample = getPcmSample();
            if (pcmSample != null) {
                numPcmSamples = (int) (cpuTimeToPcmSamples(pcmSample.timestamp - lastPcmTimestamp));
                currentSample = pcmSample.value;
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
