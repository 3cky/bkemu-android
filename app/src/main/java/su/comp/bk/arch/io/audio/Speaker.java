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
 * Speaker audio output (one bit PCM, bit 6 in SEL1 register).
 */
public class Speaker extends PcmOutput {
    // Speaker output bit
    public final static int OUTPUT_BIT = (1 << 6);

    // BK-0011M enable bit (0 enables audio output)
    public final static int BK0011M_ENABLE_BIT = (1 << 11);

    private final static int[] ADDRESSES = { Cpu.REG_SEL1 };

    private final boolean isBk0011mMode;

    private int lastOutputState;

    public Speaker(Computer computer, boolean isBk0011m) {
        super(computer);
        this.isBk0011mMode = isBk0011m;
    }

    @Override
    protected String getName() {
        return "speaker";
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public int read(long cpuTime, int address) {
        return 0;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        if (!isBk0011mMode || (value & BK0011M_ENABLE_BIT) == 0) {
            int outputState = value & OUTPUT_BIT;
            if ((outputState ^ lastOutputState) != 0) {
                putPcmSample(outputState != 0 ? Short.MAX_VALUE : Short.MIN_VALUE, cpuTime);
            }
            lastOutputState = outputState;
            return true;
        }
        return false;
    }
}
