/*
 * Created: 04.04.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package su.comp.bk.arch.io;

import su.comp.bk.arch.cpu.Cpu;

/**
 * SEL1 register (0177716) system bits (8-15 bits - power-on CPU startup address (read only),
 * bit 2 - write flag, set on register write, cleared after register read.
 */
public class Sel1RegisterSystemBits implements Device {

    // Write to register flag bit mask
    public final static int WRITE_FLAG = (1 << 2);

    private final static int[] addresses = { Cpu.REG_SEL1 };

    // System register state, read only
    private int state;

    public Sel1RegisterSystemBits(int cpuStartupAddress) {
        state = cpuStartupAddress & 0177400;
    }

    @Override
    public int[] getAddresses() {
        return addresses;
    }

    @Override
    public void reset() {
        setWriteFlagState(false);
    }

    private void setWriteFlagState(boolean flagState) {
        state = flagState ? (state | WRITE_FLAG) : (state & ~WRITE_FLAG);
    }

    @Override
    public int read(boolean isByteMode, int address) {
        // Return current state and clear write flag
        try {
            return state & (isByteMode ? 0377 : 0177777);
        } finally {
            setWriteFlagState(false);
        }

    }

    @Override
    public void write(boolean isByteMode, int address, int value) {
        // Only set write flag
        setWriteFlagState(true);
    }

}
