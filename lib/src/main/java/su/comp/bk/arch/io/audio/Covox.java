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
import su.comp.bk.arch.cpu.Cpu;

/**
 * Covox audio output (8-bit, attached to the peripheral port).
 * Supports stereo mode when a word write's high byte is not 0x00 or 0xFF.
 * Stereo mode deactivates after {@link #STEREO_DEACTIVATE_TIMEOUT_NANOS} of inactivity.
 */
public class Covox extends PcmOutput {
    private final static int[] ADDRESSES = { Cpu.REG_SEL2 };

    public static final String OUTPUT_NAME = "covox";

    public static final int BAND_PASS_FILTER_HIGH_CUTOFF_FREQUENCY_HZ = 12000;

    public static final long STEREO_DEACTIVATE_TIMEOUT_NANOS = 3 * 1000 * 1000 * 1000L; // 3s

    private int lastLeftSampleValue;
    private int lastRightSampleValue;

    private boolean isStereoMode = false;
    private long lastStereoActivationTimestamp;

    public Covox(int sampleRate, int samplesBufferSize, Computer computer) {
        super(sampleRate, samplesBufferSize, computer);
    }

    @Override
    protected int getBandPassFilterHighCutoffFrequency() {
        return BAND_PASS_FILTER_HIGH_CUTOFF_FREQUENCY_HZ;
    }

    @Override
    public String getName() {
        return OUTPUT_NAME;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public int getDefaultVolume() {
        return MIN_VOLUME; // covox is muted by default
    }

    @Override
    public synchronized void init(long cpuTime, boolean isHardwareReset) {
        super.init(cpuTime, isHardwareReset);
        isStereoMode = false;
        lastLeftSampleValue = 0;
        lastRightSampleValue = 0;
    }

    private void checkDeactivateStereoMode(long cpuTime) {
        if (!isStereoMode || getComputer().cpuTimeToNanos(cpuTime
                - lastStereoActivationTimestamp) < STEREO_DEACTIVATE_TIMEOUT_NANOS) {
            return;
        }
        isStereoMode = false;
    }

    private static short toPcmSampleValue(int byteValue) {
        return (short) (((byteValue + Byte.MIN_VALUE) << 8) | byteValue);
    }

    @Override
    public synchronized boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        int l = ~value & 0xff;
        int r = (~value >> 8) & 0xff;

        if (!isByteMode && r != 0x00 && r != 0xff) {
            isStereoMode = true;
            lastStereoActivationTimestamp = cpuTime;
        } else {
            checkDeactivateStereoMode(cpuTime);
        }

        if (!isStereoMode) {
            r = l;
        }

        if (l != lastLeftSampleValue || r != lastRightSampleValue) {
            putPcmSample(toPcmSampleValue(l), toPcmSampleValue(r), cpuTime);
        }

        lastLeftSampleValue = l;
        lastRightSampleValue = r;

        return true;
    }
}
