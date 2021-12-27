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
     * Test method for {@link su.comp.bk.arch.memory.RandomAccessMemory#RandomAccessMemory(String, int, su.comp.bk.arch.memory.RandomAccessMemory.Type)}.
     */
    @Test
    public void testRandomAccessMemoryIntInt() {
        RandomAccessMemory ram = new RandomAccessMemory("TestRam",
                4, RandomAccessMemory.Type.K565RU6);
        assertEquals(4, ram.getSize());
    }

    /**
     * Test method for {@link su.comp.bk.arch.memory.RandomAccessMemory#RandomAccessMemory(String, short[], su.comp.bk.arch.memory.RandomAccessMemory.Type)}.
     */
    @Test
    public void testRandomAccessMemoryIntShortArray() {
        short[] ramData = new short[] { 0, 1, 2, 3 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam",
                ramData, RandomAccessMemory.Type.K565RU6);
        assertEquals(4, ram.getSize());
        assertArrayEquals(ramData, ram.getData());
    }

    /**
     * Test method for {@link su.comp.bk.arch.memory.RandomAccessMemory#RandomAccessMemory(String, byte[], su.comp.bk.arch.memory.RandomAccessMemory.Type)}.
     */
    @Test
    public void testRandomAccessMemoryIntByteArray() {
        byte[] ramData = new byte[] { 0, 1, 2, 3 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam",
                ramData, RandomAccessMemory.Type.K565RU6);
        assertEquals(2, ram.getSize());
        assertArrayEquals(new short[] { 1 << 8, (3 << 8) + 2 }, ram.getData());
    }

    @Test
    public void testRead() {
        short[] ramData = new short[] { 0, 1, (short) 0177777, (short) 0177776 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam",
                ramData, RandomAccessMemory.Type.K565RU6);
        assertEquals(0, ram.read(0));
        assertEquals(0, ram.read(1));
        assertEquals(1, ram.read(2));
        assertEquals(1, ram.read(3));
        assertEquals(0177777, ram.read(4));
        assertEquals(0177777, ram.read(5));
        assertEquals(0177776, ram.read(6));
        assertEquals(0177776, ram.read(7));
    }

    @Test
    public void testWriteWord() {
        short[] ramData = new short[] { 0, 0 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam",
                ramData, RandomAccessMemory.Type.K565RU6);
        ram.write(false, 0, 0377);
        assertEquals(0377, ram.read(0));
        ram.write(false, 1, 0377);
        assertEquals(0377, ram.read(0));
        ram.write(false, 2, 0177777);
        assertEquals(0177777, ram.read(2));
        ram.write(false, 3, 0177777);
        assertEquals(0177777, ram.read(2));
        ram.write(false, 2, 01777777);
        assertEquals(0177777, ram.read(2));
    }

    @Test
    public void testWriteByte() {
        byte[] ramData = new byte[] { 0, 0 };
        RandomAccessMemory ram = new RandomAccessMemory("TestRam",
                ramData, RandomAccessMemory.Type.K565RU6);
        ram.write(true, 0, 1);
        assertEquals(1, ram.read(0));
        ram.write(true, 1, 0377 << 8);
        assertEquals((0377 << 8) + 1, ram.read(0));
        ram.write(true, 1, 0177777);
        assertEquals((0377 << 8) + 1, ram.read(0));
    }
}
