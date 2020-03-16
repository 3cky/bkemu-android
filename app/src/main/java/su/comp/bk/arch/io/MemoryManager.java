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
import su.comp.bk.arch.memory.PagedMemory;
import android.os.Bundle;

/**
 * BK-0011M memory manager.
 */
public class MemoryManager implements Device {

    private final static int[] ADDRESSES = { Cpu.REG_SEL1 };

    // BK-0011M write enable bit (1 enables memory configuration change)
    public final static int ENABLE_BIT = (1 << 11);

    /** Number of RAM pages in each paged memory space */
    public final static int NUM_RAM_PAGES = 8;

    /** Number of ROM pages in second paged memory space */
    public final static int NUM_ROM_PAGES = 4;

    // First paged memory space (addresses 040000-0100000)
    private final PagedMemory firstPagedMemory;

    // Second paged memory space (addresses 0100000-0140000)
    private final PagedMemory secondPagedMemory;

    /**
     * Create memory manager with given memory pages.
     * @param firstPagedMemory first paged memory space (addresses 040000-0100000)
     * @param secondPagedMemory second paged memory space (addresses 0100000-0140000)
     */
    public MemoryManager(PagedMemory firstPagedMemory, PagedMemory secondPagedMemory) {
        this.firstPagedMemory = firstPagedMemory;
        this.secondPagedMemory = secondPagedMemory;
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
    public void timer(long uptime) {
        // Do nothing
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
        if (!isByteMode && (value & ENABLE_BIT) != 0) {
            setMemoryConfiguration(value);
            return true;
        }
        return false;
    }

    private void setMemoryConfiguration(int value) {
        // Set first memory page configuration
        firstPagedMemory.setActivePageIndex((value >> 12) & 7);
        // Set second memory page configuration
        int romPageMask = value & 033;
        int romPageIndex = -1;
        if (romPageMask != 0) {
            // Get ROM page index
            switch (romPageMask) {
                case 001:
                    romPageIndex = NUM_RAM_PAGES;
                    break;
                case 002:
                    romPageIndex = (NUM_RAM_PAGES + 1);
                    break;
                case 010:
                    romPageIndex = (NUM_RAM_PAGES + 2);
                    break;
                case 020:
                    romPageIndex = (NUM_RAM_PAGES + 3);
                    break;
            }
        }
        secondPagedMemory.setActivePageIndex((romPageIndex < 0) ? (value >> 8) & 7 : romPageIndex);
    }

}
