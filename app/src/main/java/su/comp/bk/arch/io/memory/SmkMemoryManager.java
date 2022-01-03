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

import android.os.Bundle;

import java.util.List;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.io.disk.FloppyController;
import su.comp.bk.arch.memory.SegmentedMemory;
import su.comp.bk.arch.memory.SelectableMemory;

/**
 * SMK512 controller extended memory manager.
 */
public class SmkMemoryManager implements Device {
    // State save/restore: Current memory layout value
    private static final String STATE_MEMORY_CONFIGURATION = SmkMemoryManager.class.getName() +
            "#current_layout";

    // Total size of SMK memory in words (512 Kbytes / 256 Kwords)
    public final static int MEMORY_TOTAL_SIZE = 512 * 1024 / 2;
    // Start address of SMK memory window
    public final static int MEMORY_START_ADDRESS = 0100000;
    // Number of segments in SMK memory window
    public final static int NUM_MEMORY_SEGMENTS = 8;
    // Size of single segment in words (4 Kbytes / 2 KWords )
    public final static int MEMORY_SEGMENT_SIZE = 4 * 1024 / 2;
    // BIOS ROM start addresses
    public final static int BIOS_ROM_0_START_ADDRESS = 0160000;
    public final static int BIOS_ROM_1_START_ADDRESS = 0170000;

    // Memory segment 7 non-restricted area size in words (address range 0170000 - 0177000)
    private final static int MEMORY_SEGMENT_7_NON_RESTRICTED_SIZE = 03400;

    // SMK controller (ab)uses floppy disk drive control register to set up memory layout
    private final static int[] ADDRESSES = { FloppyController.CONTROL_REGISTER_ADDRESS };

    // SMK memory modes
    private final static int MODE_SYS   = 0160;
    private final static int MODE_STD10 = 060;
    private final static int MODE_RAM10 = 0120;
    private final static int MODE_ALL   = 020;
    private final static int MODE_STD11 = 0140;
    private final static int MODE_RAM11 = 040;
    private final static int MODE_HLT10 = 0100;
    private final static int MODE_HLT11 = 0;

    // 8 memory segments 4K bytes each
    private final SegmentedMemory[] memorySegments = new SegmentedMemory[NUM_MEMORY_SEGMENTS];

    private final static int MEMORY_LAYOUT_UPDATE_STROBE_PATTERN = 0b0110;
    private static final int MEMORY_LAYOUT_MODE_MASK = 0160;
    private static final int MEMORY_LAYOUT_PAGE_MASK = 02015;

    private boolean memoryLayoutUpdateStrobe;

    private SelectableMemory bk11BosRom;
    private SelectableMemory bk11SecondBankedMemory;

    private SelectableMemory romSegment6;
    private SelectableMemory romSegment7;

    private int currentMemoryLayoutValue;

    public SmkMemoryManager(List<SegmentedMemory> smkMemorySegments,
                            SelectableMemory selectableSmkBiosRom0,
                            SelectableMemory selectableSmkBiosRom1) {
        for (int segment = 0; segment < NUM_MEMORY_SEGMENTS; segment++) {
            setMemorySegment(smkMemorySegments.get(segment), segment);
        }
        setSelectableBiosRoms(selectableSmkBiosRom0, selectableSmkBiosRom1);
    }

    public SegmentedMemory getMemorySegment(int segment) {
        return memorySegments[segment];
    }

    public void setMemorySegment(SegmentedMemory memory, int segment) {
        memorySegments[segment] = memory;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        if (isHardwareReset) {
            initMemoryLayout();
        }
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putInt(STATE_MEMORY_CONFIGURATION, currentMemoryLayoutValue);
    }

    @Override
    public void restoreState(Bundle inState) {
        updateMemoryLayout(inState.getInt(STATE_MEMORY_CONFIGURATION, MODE_SYS));
    }

    @Override
    public int read(long cpuTime, int address) {
        // Register is write only
        return Computer.BUS_ERROR;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        boolean lastMemoryLayoutUpdateStrobe = memoryLayoutUpdateStrobe;
        memoryLayoutUpdateStrobe = (value & 0b1111) == MEMORY_LAYOUT_UPDATE_STROBE_PATTERN;
        // Memory layout is updated on the falling edge of the strobe
        if (lastMemoryLayoutUpdateStrobe && !memoryLayoutUpdateStrobe) {
            updateMemoryLayout(value);
        }
        return true;
    }

    public void setSelectableBk11BosRom(SelectableMemory bk11BosRom) {
        this.bk11BosRom = bk11BosRom;
    }

    private void selectBk11BosRom(boolean isSelected) {
        if (bk11BosRom != null) {
            bk11BosRom.setSelected(isSelected);
        }
    }

    public void setSelectableBk11SecondBankedMemory(SelectableMemory bk11SecondBankedMemory) {
        this.bk11SecondBankedMemory = bk11SecondBankedMemory;
    }

    private void selectBk11SecondBankedMemory(boolean isSelected) {
        if (bk11SecondBankedMemory != null) {
            bk11SecondBankedMemory.setSelected(isSelected);
        }
    }

    public void setSelectableBiosRoms(SelectableMemory biosRom0, SelectableMemory biosRom1) {
        this.romSegment6 = biosRom0;
        this.romSegment7 = biosRom1;
    }

    private void resetMemoryLayout() {
        for (int i = 0; i < NUM_MEMORY_SEGMENTS; i++) {
            setupMemorySegment(i, -1);
        }
        romSegment6.setSelected(false);
        romSegment7.setSelected(false);
        selectBk11BosRom(true);
        selectBk11SecondBankedMemory(true);
    }

    public void initMemoryLayout() {
        updateMemoryLayout(MODE_SYS);
    }

    private void updateMemoryLayout(int memoryLayoutValue) {
        currentMemoryLayoutValue = memoryLayoutValue;
        int memoryLayoutPageValue = memoryLayoutValue & MEMORY_LAYOUT_PAGE_MASK;
        int memoryLayoutModeValue = memoryLayoutValue & MEMORY_LAYOUT_MODE_MASK;
        setupMemoryLayout(memoryLayoutPageValue, memoryLayoutModeValue);
    }

    private void setupMemoryLayout(int pageValue, int modeValue) {
        int pageStartIndex = 0;
        if ((pageValue & 02000) != 0) {
            pageStartIndex += 010;
        }
        if ((pageValue & 4) != 0) {
            pageStartIndex += 020;
        }
        if ((pageValue & 010) != 0) {
            pageStartIndex += 040;
        }
        if ((pageValue & 1) != 0) {
            pageStartIndex += 0100;
        }

        resetMemoryLayout();

        switch (modeValue) {
            case MODE_SYS:
                setupMemorySegment(2, pageStartIndex + 6);
                setupMemorySegment(3, pageStartIndex + 7);
                setupMemorySegment(4, pageStartIndex + 0);
                setupMemorySegment(5, pageStartIndex + 1);
                romSegment6.setSelected(true);
                romSegment7.setSelected(true);
                selectBk11SecondBankedMemory(false);
                break;
            case MODE_STD10:
                setupMemorySegment(2, pageStartIndex + 2);
                setupMemorySegment(3, pageStartIndex + 3);
                setupMemorySegment(4, pageStartIndex + 4);
                setupMemorySegment(5, pageStartIndex + 5);
                setupMemorySegment7(pageStartIndex + 7, false, false);
                romSegment6.setSelected(true);
                selectBk11BosRom(false);
                selectBk11SecondBankedMemory(false);
                break;
            case MODE_RAM10:
                setupMemorySegment(0, pageStartIndex + 0);
                setupMemorySegment(1, pageStartIndex + 1);
                setupMemorySegment(2, pageStartIndex + 2);
                setupMemorySegment(3, pageStartIndex + 3);
                setupMemorySegment(4, pageStartIndex + 4);
                setupMemorySegment(5, pageStartIndex + 5);
                setupMemorySegment(6, pageStartIndex + 6);
                setupMemorySegment7(pageStartIndex + 7, false, false);
                selectBk11SecondBankedMemory(false);
                break;
            case MODE_ALL:
                setupMemorySegment(0, pageStartIndex + 4);
                setupMemorySegment(1, pageStartIndex + 5);
                setupMemorySegment(2, pageStartIndex + 6);
                setupMemorySegment(3, pageStartIndex + 7);
                setupMemorySegment(4, pageStartIndex + 0);
                setupMemorySegment(5, pageStartIndex + 1);
                setupMemorySegment(6, pageStartIndex + 2);
                setupMemorySegment7(pageStartIndex + 3, true, false);
                selectBk11BosRom(false);
                selectBk11SecondBankedMemory(false);
                break;
            case MODE_STD11:
                setupMemorySegment7(pageStartIndex + 7, false, false);
                romSegment6.setSelected(true);
                break;
            case MODE_RAM11:
                setupMemorySegment(4, pageStartIndex + 4);
                setupMemorySegment(5, pageStartIndex + 5);
                setupMemorySegment(6, pageStartIndex + 6);
                setupMemorySegment7(pageStartIndex + 7, false, false);
                selectBk11BosRom(false);
                break;
            case MODE_HLT10:
                setupMemorySegment(0, pageStartIndex + 0,
                        MEMORY_SEGMENT_SIZE, -1);
                setupMemorySegment(1, pageStartIndex + 1);
                setupMemorySegment(2, pageStartIndex + 2);
                setupMemorySegment(3, pageStartIndex + 3);
                setupMemorySegment(4, pageStartIndex + 4);
                setupMemorySegment(5, pageStartIndex + 5);
                setupMemorySegment(6, pageStartIndex + 6);
                setupMemorySegment7(pageStartIndex + 7, false, true);
                break;
            case MODE_HLT11:
                setupMemorySegment(4, pageStartIndex + 4);
                setupMemorySegment(5, pageStartIndex + 5);
                setupMemorySegment(6, pageStartIndex + 6);
                setupMemorySegment7(pageStartIndex + 7, false, true);
                selectBk11BosRom(false);
                break;
        }
    }

    private void setupMemorySegment(int segment, int index) {
        setupMemorySegment(segment, index, MEMORY_SEGMENT_SIZE, MEMORY_SEGMENT_SIZE);
    }

    private void setupMemorySegment(int segment, int index, int readableSize, int writableSize) {
        SegmentedMemory memory = getMemorySegment(segment);
        memory.setActiveSegmentIndex(index);
        memory.setReadableSize(readableSize);
        memory.setWritableSize(writableSize);
    }

    private void setupMemorySegment7(int index, boolean isExtentReadable, boolean isExtentWritable) {
        setupMemorySegment(7, index,
                isExtentReadable ? MEMORY_SEGMENT_SIZE : MEMORY_SEGMENT_7_NON_RESTRICTED_SIZE,
                isExtentWritable ? MEMORY_SEGMENT_SIZE : MEMORY_SEGMENT_7_NON_RESTRICTED_SIZE);
    }
}
