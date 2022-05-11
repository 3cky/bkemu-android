/*
 * Created: 04.04.2012
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

import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.state.State;

/**
 * SEL1 register (0177716) system bits (8-15 bits - power-on CPU startup address (read only),
 * bit 2 - write flag, set on register write, cleared after register read.
 */
public class Sel1RegisterSystemBits implements Device {

    // Write to register flag bit mask
    public final static int WRITE_FLAG = (1 << 2);

    private final static int[] ADDRESSES = { Cpu.REG_SEL1 };

    // System register state, read only
    private int state;

    public Sel1RegisterSystemBits(int cpuStartupAddress) {
        state = cpuStartupAddress & 0177400;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        setWriteFlagState(false);
    }

    private void setWriteFlagState(boolean flagState) {
        state = flagState ? (state | WRITE_FLAG) : (state & ~WRITE_FLAG);
    }

    @Override
    public int read(long cpuTime, int address) {
        // Return current state and clear write flag
        int result = state;
        setWriteFlagState(false);
        return result;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        // Only set write flag
        setWriteFlagState(true);
        return true;
    }


    @Override
    public void saveState(State outState) {
        // TODO
    }

    @Override
    public void restoreState(State inState) {
        // TODO
    }

}
