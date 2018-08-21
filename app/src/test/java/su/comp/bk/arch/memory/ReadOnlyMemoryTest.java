/*
 * Created: 31.03.2012
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
 * ROM implementation tests.
 */
public class ReadOnlyMemoryTest {

    /**
     * Test method for {@link su.comp.bk.arch.memory.ReadOnlyMemory#writeWord(int, int)}.
     */
    @Test
    public void testWriteWord() {
        short[] romData = new short[] { 0, (short) 0177777 };
        ReadOnlyMemory rom = new ReadOnlyMemory("TestRom", 01000, romData);
        rom.write(false, 01000, 0377);
        assertEquals(0, rom.read(false, 01000));
        rom.write(false, 01001, 0377);
        assertEquals(0, rom.read(false, 01000));
        rom.write(false, 01002, 0);
        assertEquals(0177777, rom.read(false, 01002));
        rom.write(false, 01003, 0);
        assertEquals(0177777, rom.read(false, 01002));
    }

    /**
     * Test method for {@link su.comp.bk.arch.memory.ReadOnlyMemory#writeByte(int, int)}.
     */
    @Test
    public void testWriteByte() {
        byte[] romData = new byte[] { 0, (byte) 0377 };
        ReadOnlyMemory rom = new ReadOnlyMemory("TestRom", 01000, romData);
        rom.write(true, 01000, 0377);
        assertEquals(0377 << 8, rom.read(false, 01000));
        rom.write(true, 01001, 0);
        assertEquals(0377 << 8, rom.read(false, 01000));
    }

}
