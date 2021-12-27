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

    @Test
    public void testWriteWord() {
        short[] romData = new short[] { 0, (short) 0177777 };
        ReadOnlyMemory rom = new ReadOnlyMemory("TestRom", romData);
        rom.write(false, 0, 0377);
        assertEquals(0, rom.read(0));
        rom.write(false, 1, 0377);
        assertEquals(0, rom.read(0));
        rom.write(false, 2, 0);
        assertEquals(0177777, rom.read(2));
        rom.write(false, 3, 0);
        assertEquals(0177777, rom.read(2));
    }

    @Test
    public void testWriteByte() {
        byte[] romData = new byte[] { 0, (byte) 0377 };
        ReadOnlyMemory rom = new ReadOnlyMemory("TestRom", romData);
        assertFalse(rom.write(true, 0, 0377));
        assertEquals(0377 << 8, rom.read(0));
        assertFalse(rom.write(true, 1, 0));
        assertEquals(0377 << 8, rom.read(0));
    }
}
