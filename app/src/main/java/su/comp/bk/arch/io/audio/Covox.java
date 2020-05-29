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
 */
public class Covox extends PcmOutput {
    private final static int[] ADDRESSES = { Cpu.REG_SEL2 };

    public static final String OUTPUT_NAME = "covox";

    private int lastSampleValue;

    public Covox(Computer computer) {
        super(computer);
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
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        int sampleValue = ((value & 0377) - 128) & 0377;
        if (sampleValue != lastSampleValue) {
            putPcmSample((short)(sampleValue << 8), cpuTime);
        }
        lastSampleValue = sampleValue;
        return true;
    }
}
