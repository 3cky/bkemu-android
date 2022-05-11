/*
 * Copyright (C) 2021 Victor Antonovich (v.antonovich@gmail.com)
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
package su.comp.bk.arch.io.memory;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.memory.BankedMemory;
import su.comp.bk.state.State;

/**
 * BK-0011M memory manager.
 */
public class Bk11MemoryManager implements Device {
    // State save/restore: Current memory configuration value
    public static final String STATE_MEMORY_CONFIGURATION = "Bk11MemoryManager#current_config";

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

    // Default memory configuration value
    private static final int DEFAULT_MEMORY_CONFIGURATION = 0;

    // Current memory configuration value
    private int currentMemoryConfiguration;

    /**
     * Create memory manager with given memory banks.
     * @param firstBankedMemory first banked memory window (addresses 040000-0100000)
     * @param secondBankedMemory second banked memory window (addresses 0100000-0140000)
     */
    public Bk11MemoryManager(BankedMemory firstBankedMemory, BankedMemory secondBankedMemory) {
        this.firstBankedMemory = firstBankedMemory;
        this.secondBankedMemory = secondBankedMemory;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        if (isHardwareReset) {
            setMemoryConfiguration(DEFAULT_MEMORY_CONFIGURATION);
        }
    }

    @Override
    public void saveState(State outState) {
        outState.putInt(STATE_MEMORY_CONFIGURATION, currentMemoryConfiguration);
    }

    @Override
    public void restoreState(State inState) {
        setMemoryConfiguration(inState.getInt(STATE_MEMORY_CONFIGURATION,
                DEFAULT_MEMORY_CONFIGURATION));
    }

    @Override
    public int read(long cpuTime, int address) {
        // Register is write only
        return Computer.BUS_ERROR;
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
        // Update current memory configuration value
        currentMemoryConfiguration = value;
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
