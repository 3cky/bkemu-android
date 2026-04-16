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

/**
 * Abstract base class for PCM audio outputs (speaker/covox).
 */
public abstract class PcmOutput extends AudioOutput<PcmOutput.PcmSample> {
    // Current PCM sample value
    private short pcmSampleValue;

    // Output low pass IIR filter alpha coefficient
    private float outputLowPassFilterAlpha;

    // Last output value
    private short lastOutputValue = 0;

    public static class PcmSample extends AudioOutputUpdate {
        // PCM sample value
        private short value;
    }

    PcmOutput(int sampleRate, int samplesBufferSize, Computer computer) {
        super(sampleRate, samplesBufferSize, computer);
        setupOutputLowPassFilter(getOutputLowPassFilterCutoffFrequency());
    }

    /**
     * Get output low pass IIR filter cutoff frequency.
     *
     * @return cutoff frequency (in Hz)
     */
    protected abstract int getOutputLowPassFilterCutoffFrequency();

    private void setupOutputLowPassFilter(int cutoffFrequency) {
        // https://en.wikipedia.org/wiki/Low-pass_filter#Simple_infinite_impulse_response_filter
        float T = 1f / getSampleRate();
        outputLowPassFilterAlpha = T / (T + 1f / cutoffFrequency);
    }

    @Override
    protected PcmSample[] createAudioOutputUpdates(int numPcmSamples) {
        PcmSample[] pcmSamples = new PcmSample[numPcmSamples];
        for (int i = 0; i < numPcmSamples; i++) {
            pcmSamples[i] = new PcmSample();
        }
        return pcmSamples;
    }

    synchronized void putPcmSample(short pcmSampleValue, long pcmSampleTimestamp) {
        if (getComputer().getClockFrequency() <= 0) {
            // Drop PCM samples in CPU free running mode
            return;
        }
        PcmSample pcmSample = putAudioOutputUpdate();
        if (pcmSample != null) {
            pcmSample.value = pcmSampleValue;
            pcmSample.timestamp = pcmSampleTimestamp;
        }
    }

    @Override
    protected void handleAudioOutputUpdate(PcmSample pcmSample) {
        pcmSampleValue = pcmSample.value;
    }

    @Override
    protected void writeSample(short[] sample) {
        short value = getFilteredOutputValue();
        sample[0] = value;
        sample[1] = value; // mono duplicated to stereo
    }

    private short getFilteredOutputValue() {
        // Apply low pass first order IIR filter to the input PCM samples
        short outputValue = (short) (lastOutputValue + outputLowPassFilterAlpha
                * (pcmSampleValue - lastOutputValue));
        lastOutputValue = outputValue;
        return outputValue;
    }
}
