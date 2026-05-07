/*
 * Copyright (C) 2026 Victor Antonovich (v.antonovich@gmail.com)
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;
import su.comp.bk.arch.memory.SegmentedMemory;
import su.comp.bk.arch.memory.SelectableMemory;

/**
 * {@link SmkMemoryManager} memory layout mode unit tests.
 */
public class SmkMemoryManagerTest {
    // Strobe pattern that arms the memory layout trigger
    private static final int STROBE = 0b0110;

    // Byte offset of the I/O area within segment 7 (= 03400 words * 2)
    private static final int SEG7_IO_AREA_OFFSET = 03400 * 2;

    private SmkMemoryManager manager;

    private SelectableMemory smkBiosRom0;
    private SelectableMemory smkBiosRom1;
    private SelectableMemory bk10MonitorRom;
    private SelectableMemory bk11BosRom;
    private SelectableMemory bk11SecondBankedMemory;

    @Before
    public void setUp() {
        // Create SMK RAM, pre-filling word 0 of each segment with its own index
        // so that segment.read(0) returns the active segment index for page verification.
        RandomAccessMemory smkRam = new RandomAccessMemory("SmkRam",
                SmkMemoryManager.MEMORY_TOTAL_SIZE, RandomAccessMemory.Type.K565RU6);
        short[] ramData = smkRam.getData();
        int totalSegments = SmkMemoryManager.MEMORY_TOTAL_SIZE / SmkMemoryManager.MEMORY_SEGMENT_SIZE;
        for (int i = 0; i < totalSegments; i++) {
            ramData[i * SmkMemoryManager.MEMORY_SEGMENT_SIZE] = (short) i;
        }

        List<SegmentedMemory> segments = new ArrayList<>();
        for (int i = 0; i < SmkMemoryManager.NUM_MEMORY_SEGMENTS; i++) {
            segments.add(new SegmentedMemory("SmkSeg" + i,
                    smkRam, SmkMemoryManager.MEMORY_SEGMENT_SIZE));
        }

        ReadOnlyMemory biosRom = new ReadOnlyMemory("SmkBiosRom",
                new short[SmkMemoryManager.MEMORY_SEGMENT_SIZE]);
        smkBiosRom0 = new SelectableMemory("SmkBiosRom0", biosRom, false);
        smkBiosRom1 = new SelectableMemory("SmkBiosRom1", biosRom, false);

        ReadOnlyMemory dummyRom = new ReadOnlyMemory("DummyRom", new short[1]);
        bk10MonitorRom = new SelectableMemory("Bk10MonitorRom", dummyRom, true);
        bk11BosRom = new SelectableMemory("Bk11BosRom", dummyRom, true);
        bk11SecondBankedMemory = new SelectableMemory("Bk11SecondBankedMemory", dummyRom, true);

        manager = new SmkMemoryManager(segments, smkBiosRom0, smkBiosRom1);
        manager.setSelectableBk10MonitorRom(bk10MonitorRom);
        manager.setSelectableBk11BosRom(bk11BosRom);
        manager.setSelectableBk11SecondBankedMemory(bk11SecondBankedMemory);
    }

    // Trigger a memory layout mode change (page bits = 0, i.e. pageStartIndex = 0)
    private void setMode(int mode) {
        manager.write(0, false, 0, STROBE);
        manager.write(0, false, 0, mode);
    }

    private boolean isSegmentActive(int seg) {
        return manager.getMemorySegment(seg).read(0) != Computer.BUS_ERROR;
    }

    // Returns the active SMK RAM page index for a segment (pre-filled: segment N holds value N at word 0)
    private int getSegmentPage(int seg) {
        return manager.getMemorySegment(seg).read(0);
    }

    // Uses offset 2 (word 1) to avoid corrupting the page-identification word at offset 0
    private boolean isSegmentWritable(int seg) {
        return manager.getMemorySegment(seg).write(false, 2, 0);
    }

    private boolean isSegment7IoAreaReadable() {
        return manager.getMemorySegment(7).read(SEG7_IO_AREA_OFFSET) != Computer.BUS_ERROR;
    }

    private boolean isSegment7IoAreaWritable() {
        return manager.getMemorySegment(7).write(false, SEG7_IO_AREA_OFFSET, 0);
    }

    @Test
    public void testModeHlt11() {
        setMode(0);

        assertFalse(isSegmentActive(0));
        assertFalse(isSegmentActive(1));
        assertFalse(isSegmentActive(2));
        assertFalse(isSegmentActive(3));
        assertEquals(4, getSegmentPage(4));
        assertEquals(5, getSegmentPage(5));
        assertEquals(6, getSegmentPage(6));
        assertEquals(7, getSegmentPage(7));
        assertTrue(isSegmentWritable(4));
        assertTrue(isSegmentWritable(5));
        assertTrue(isSegmentWritable(6));
        assertTrue(isSegmentWritable(7));
        assertFalse(isSegment7IoAreaReadable());
        assertTrue(isSegment7IoAreaWritable());

        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertFalse(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertTrue(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeAll() {
        setMode(020);

        assertEquals(4, getSegmentPage(0));
        assertEquals(5, getSegmentPage(1));
        assertEquals(6, getSegmentPage(2));
        assertEquals(7, getSegmentPage(3));
        assertEquals(0, getSegmentPage(4));
        assertEquals(1, getSegmentPage(5));
        assertEquals(2, getSegmentPage(6));
        assertEquals(3, getSegmentPage(7));
        for (int seg = 0; seg < SmkMemoryManager.NUM_MEMORY_SEGMENTS; seg++) {
            assertTrue(isSegmentWritable(seg));
        }
        assertTrue(isSegment7IoAreaReadable());
        assertFalse(isSegment7IoAreaWritable());

        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertFalse(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertFalse(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeRam11() {
        setMode(040);

        assertFalse(isSegmentActive(0));
        assertFalse(isSegmentActive(1));
        assertFalse(isSegmentActive(2));
        assertFalse(isSegmentActive(3));
        assertEquals(4, getSegmentPage(4));
        assertEquals(5, getSegmentPage(5));
        assertEquals(6, getSegmentPage(6));
        assertEquals(7, getSegmentPage(7));
        assertTrue(isSegmentWritable(4));
        assertTrue(isSegmentWritable(5));
        assertTrue(isSegmentWritable(6));
        assertTrue(isSegmentWritable(7));
        assertFalse(isSegment7IoAreaReadable());
        assertFalse(isSegment7IoAreaWritable());

        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertTrue(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertTrue(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeStd10() {
        setMode(060);

        assertFalse(isSegmentActive(0));
        assertFalse(isSegmentActive(1));
        assertEquals(2, getSegmentPage(2));
        assertEquals(3, getSegmentPage(3));
        assertEquals(4, getSegmentPage(4));
        assertEquals(5, getSegmentPage(5));
        assertFalse(isSegmentActive(6));  // segment 6 is ROM
        assertEquals(7, getSegmentPage(7));
        assertTrue(isSegmentWritable(2));
        assertTrue(isSegmentWritable(3));
        assertTrue(isSegmentWritable(4));
        assertTrue(isSegmentWritable(5));
        assertTrue(isSegmentWritable(7));
        assertFalse(isSegment7IoAreaReadable());
        assertFalse(isSegment7IoAreaWritable());

        assertTrue(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertTrue(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertFalse(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeHlt10() {
        setMode(0100);

        assertEquals(0, getSegmentPage(0));
        assertEquals(1, getSegmentPage(1));
        assertEquals(2, getSegmentPage(2));
        assertEquals(3, getSegmentPage(3));
        assertEquals(4, getSegmentPage(4));
        assertEquals(5, getSegmentPage(5));
        assertEquals(6, getSegmentPage(6));
        assertEquals(7, getSegmentPage(7));
        assertFalse(isSegmentWritable(0));  // segment 0 is read-only
        for (int seg = 1; seg < SmkMemoryManager.NUM_MEMORY_SEGMENTS; seg++) {
            assertTrue(isSegmentWritable(seg));
        }
        assertFalse(isSegment7IoAreaReadable());
        assertTrue(isSegment7IoAreaWritable());

        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertFalse(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertFalse(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeRam10() {
        setMode(0120);

        for (int seg = 0; seg < SmkMemoryManager.NUM_MEMORY_SEGMENTS; seg++) {
            assertEquals(seg, getSegmentPage(seg));
            assertTrue(isSegmentWritable(seg));
        }
        assertFalse(isSegment7IoAreaReadable());
        assertFalse(isSegment7IoAreaWritable());

        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertFalse(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertFalse(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeStd11() {
        setMode(0140);

        for (int seg = 0; seg < 6; seg++) {
            assertFalse(isSegmentActive(seg));
        }
        assertFalse(isSegmentActive(6));  // segment 6 is ROM
        assertEquals(7, getSegmentPage(7));
        assertTrue(isSegmentWritable(7));
        assertFalse(isSegment7IoAreaReadable());
        assertFalse(isSegment7IoAreaWritable());

        assertTrue(smkBiosRom0.isReadable(0));
        assertFalse(smkBiosRom1.isReadable(0));
        assertTrue(bk10MonitorRom.isReadable(0));
        assertTrue(bk11BosRom.isReadable(0));
        assertTrue(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeSys() {
        setMode(0160);

        assertFalse(isSegmentActive(0));
        assertFalse(isSegmentActive(1));
        assertEquals(6, getSegmentPage(2));
        assertEquals(7, getSegmentPage(3));
        assertEquals(0, getSegmentPage(4));
        assertEquals(1, getSegmentPage(5));
        assertFalse(isSegmentActive(6));  // segment 6 is ROM
        assertFalse(isSegmentActive(7));  // segment 7 is ROM
        assertTrue(isSegmentWritable(2));
        assertTrue(isSegmentWritable(3));
        assertTrue(isSegmentWritable(4));
        assertTrue(isSegmentWritable(5));

        assertTrue(smkBiosRom0.isReadable(0));
        assertTrue(smkBiosRom1.isReadable(0));
        assertTrue(bk10MonitorRom.isReadable(0));
        assertFalse(bk11BosRom.isReadable(0));
        assertFalse(bk11SecondBankedMemory.isReadable(0));
    }

    @Test
    public void testModeTransitions() {
        // Verify that switching between modes correctly resets all state
        setMode(0120);  // RAM10: all segments active, all ROMs off
        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(bk10MonitorRom.isReadable(0));

        setMode(0140);  // STD11: segments 0-6 off, ROMs on
        assertFalse(isSegmentActive(0));
        assertTrue(smkBiosRom0.isReadable(0));
        assertTrue(bk10MonitorRom.isReadable(0));

        setMode(020);   // ALL: all segments active, all ROMs off
        assertTrue(isSegmentActive(0));
        assertFalse(smkBiosRom0.isReadable(0));
        assertFalse(bk10MonitorRom.isReadable(0));
    }

    @Test
    public void testStrobeProtocol() {
        // Layout must not change on strobe write itself, only on the following write
        setMode(0120);  // RAM10: all segments active

        manager.write(0, false, 0, STROBE);  // arm trigger, no mode change yet
        assertTrue(isSegmentActive(0));       // still in RAM10

        manager.write(0, false, 0, 0140);    // falling edge → switch to STD11
        assertFalse(isSegmentActive(0));      // now in STD11

        // A non-strobe write without prior strobe must not trigger any change
        manager.write(0, false, 0, 0120);    // no prior strobe, ignored
        assertFalse(isSegmentActive(0));      // still in STD11
    }
}
