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
package su.comp.bk.arch;

import static org.junit.Assert.*;

import org.junit.Test;

import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.RandomAccessMemory;

/**
 * {@link Computer} class unit tests.
 */
public class ComputerTest {

    @Test
    public void testMemoryReading() {
        Computer computer = new Computer();
        byte[] ramData = new byte[] { 0, 1, 2, 3 };
        RandomAccessMemory ram = new RandomAccessMemory(01000, ramData);
        computer.addMemory(ram);
        // Memory byte read operations
        assertEquals(0, computer.readMemory(true, 01000));
        assertEquals(1, computer.readMemory(true, 01001));
        assertEquals(2, computer.readMemory(true, 01002));
        assertEquals(3, computer.readMemory(true, 01003));
        assertEquals(Computer.BUS_ERROR, computer.readMemory(true, 01004));
        assertEquals(Computer.BUS_ERROR, computer.readMemory(true, 0777));
        // Memory word read operations
        assertEquals(1 << 8, computer.readMemory(false, 01000));
        assertEquals(1 << 8, computer.readMemory(false, 01001));
        assertEquals((3 << 8) + 2, computer.readMemory(false, 01002));
        assertEquals((3 << 8) + 2, computer.readMemory(false, 01003));
        assertEquals(Computer.BUS_ERROR, computer.readMemory(false, 01004));
        assertEquals(Computer.BUS_ERROR, computer.readMemory(false, 0776));
    }

    @Test
    public void testMemoryWriting() {
        Computer computer = new Computer();
        byte[] ramData = new byte[] { 0, 1, 2, 3 };
        RandomAccessMemory ram = new RandomAccessMemory(01000, ramData);
        computer.addMemory(ram);
        // Memory byte write operations
        assertTrue(computer.writeMemory(true, 01000, 4));
        assertTrue(computer.writeMemory(true, 01001, 3));
        assertTrue(computer.writeMemory(true, 01002, 2));
        assertTrue(computer.writeMemory(true, 01003, 1));
        assertFalse(computer.writeMemory(true, 01004, 0));
        assertFalse(computer.writeMemory(true, 0777, 0));
        // Check written bytes
        assertEquals(4, computer.readMemory(true, 01000));
        assertEquals(3, computer.readMemory(true, 01001));
        assertEquals(2, computer.readMemory(true, 01002));
        assertEquals(1, computer.readMemory(true, 01003));
        // Memory word write operations
        assertTrue(computer.writeMemory(false, 01000, 4));
        assertTrue(computer.writeMemory(false, 01001, 3));
        assertTrue(computer.writeMemory(false, 01002, 2));
        assertTrue(computer.writeMemory(false, 01003, 1));
        assertFalse(computer.writeMemory(false, 01004, 0));
        assertFalse(computer.writeMemory(false, 0777, 0));
        // Check written words
        assertEquals(3, computer.readMemory(false, 01000));
        assertEquals(3, computer.readMemory(false, 01001));
        assertEquals(1, computer.readMemory(false, 01002));
        assertEquals(1, computer.readMemory(false, 01003));
        assertEquals(Computer.BUS_ERROR, computer.readMemory(false, 01004));
        assertEquals(Computer.BUS_ERROR, computer.readMemory(false, 0776));
    }

    @Test
    public void testReset() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.reset();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
    }

}
