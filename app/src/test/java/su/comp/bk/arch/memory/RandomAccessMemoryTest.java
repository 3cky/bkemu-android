/*
 * Created: 30.03.2012
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
package su.comp.bk.arch.memory;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * RAM implementation tests.
 */
public class RandomAccessMemoryTest {

    /**
     * Test method for {@link su.comp.bk.arch.memory.RandomAccessMemory#RandomAccessMemory(String, int,int)}.
     */
    @Test
    public void testRandomAccessMemoryIntInt() {
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, 4);
        assertEquals(01000, ram.getStartAddress());
        assertEquals(4, ram.getSize());
    }

    /**
     * Test method for {@link su.comp.bk.arch.memory.RandomAccessMemory#RandomAccessMemory(String, int, short[])}.
     */
    @Test
    public void testRandomAccessMemoryIntShortArray() {
        short[] ramData = new short[] { 0, 1, 2, 3 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, ramData);
        assertEquals(01000, ram.getStartAddress());
        assertEquals(4, ram.getSize());
        assertArrayEquals(ramData, ram.getData());
    }

    /**
     * Test method for {@link su.comp.bk.arch.memory.RandomAccessMemory#RandomAccessMemory(String, int, byte[])}.
     */
    @Test
    public void testRandomAccessMemoryIntByteArray() {
        byte[] ramData = new byte[] { 0, 1, 2, 3 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, ramData);
        assertEquals(01000, ram.getStartAddress());
        assertEquals(2, ram.getSize());
        assertArrayEquals(new short[] { 1 << 8, (3 << 8) + 2 }, ram.getData());
    }

    @Test
    public void testReadByte() {
        byte[] ramData = new byte[] { 0, 1, (byte) 0377, (byte) 0376 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, ramData);
        assertEquals(0, ram.read(true, 01000));
        assertEquals(1, ram.read(true, 01001));
        assertEquals(0377, ram.read(true, 01002));
        assertEquals(0376, ram.read(true, 01003));
    }

    @Test
    public void testReadWord() {
        short[] ramData = new short[] { 0, 1, (short) 0177777, (short) 0177776 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, ramData);
        assertEquals(0, ram.read(false, 01000));
        assertEquals(0, ram.read(false, 01001));
        assertEquals(1, ram.read(false, 01002));
        assertEquals(1, ram.read(false, 01003));
        assertEquals(0177777, ram.read(false, 01004));
        assertEquals(0177777, ram.read(false, 01005));
        assertEquals(0177776, ram.read(false, 01006));
        assertEquals(0177776, ram.read(false, 01007));
    }

    @Test
    public void testWriteWord() {
        short[] ramData = new short[] { 0, 0 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, ramData);
        ram.write(false, 01000, 0377);
        assertEquals(0377, ram.read(false, 01000));
        ram.write(false, 01001, 0377);
        assertEquals(0377, ram.read(false, 01000));
        ram.write(false, 01002, 0177777);
        assertEquals(0177777, ram.read(false, 01002));
        ram.write(false, 01003, 0177777);
        assertEquals(0177777, ram.read(false, 01002));
        ram.write(false, 01002, 01777777);
        assertEquals(0177777, ram.read(false, 01002));
    }

    @Test
    public void testWriteByte() {
        byte[] ramData = new byte[] { 0, 0 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam", 01000, ramData);
        ram.write(true, 01000, 1);
        assertEquals(1, ram.read(false, 01000));
        ram.write(true, 01001, 0377);
        assertEquals((0377 << 8) + 1, ram.read(false, 01000));
        ram.write(true, 01001, 0177777);
        assertEquals((0377 << 8) + 1, ram.read(false, 01000));
    }

}
