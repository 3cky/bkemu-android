/*
 * Created: 17.01.2013
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
import su.comp.bk.arch.memory.BankedMemory;
import android.os.Bundle;

/**
 * BK-0011M memory manager.
 */
public class MemoryManager implements Device {

    private final static int[] ADDRESSES = { Cpu.REG_SEL1 };

    // BK-0011M write enable bit (1 enables memory configuration change)
    public final static int ENABLE_BIT = (1 << 11);

    /** Number of RAM banks in each banked memory window */
    public final static int NUM_RAM_BANKS = 8;

    /** Number of ROM banks in second banked memory window */
    public final static int NUM_ROM_BANKS = 4;

    // First banked memory window (addresses 040000-0100000)
    private final BankedMemory firstBankedMemory;

    // Second banked memory window (addresses 0100000-0140000)
    private final BankedMemory secondBankedMemory;

    /**
     * Create memory manager with given memory banks.
     * @param firstBankedMemory first banked memory window (addresses 040000-0100000)
     * @param secondBankedMemory second banked memory window (addresses 0100000-0140000)
     */
    public MemoryManager(BankedMemory firstBankedMemory, BankedMemory secondBankedMemory) {
        this.firstBankedMemory = firstBankedMemory;
        this.secondBankedMemory = secondBankedMemory;
        setMemoryConfiguration(0);
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        // FIXME Do nothing?
    }

    @Override
    public void saveState(Bundle outState) {
        // TODO Auto-generated method stub

    }

    @Override
    public void restoreState(Bundle inState) {
        // TODO Auto-generated method stub

    }

    @Override
    public int read(long cpuTime, int address) {
        // Register is write-only
        return 0;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        if ((value & ENABLE_BIT) != 0) {
            setMemoryConfiguration(value);
            return true;
        }
        return false;
    }

    private void setMemoryConfiguration(int value) {
        // Set first memory bank configuration
        firstBankedMemory.setActiveBankIndex((value >> 12) & 7);
        // Set second memory bank configuration
        int romBankMask = value & 033;
        int romBankIndex = -1;
        if (romBankMask != 0) {
            // Get ROM bank index
            switch (romBankMask) {
                case 001:
                    romBankIndex = NUM_RAM_BANKS;
                    break;
                case 002:
                    romBankIndex = (NUM_RAM_BANKS + 1);
                    break;
                case 010:
                    romBankIndex = (NUM_RAM_BANKS + 2);
                    break;
                case 020:
                    romBankIndex = (NUM_RAM_BANKS + 3);
                    break;
            }
        }
        secondBankedMemory.setActiveBankIndex((romBankIndex < 0) ? (value >> 8) & 7 : romBankIndex);
    }

}
