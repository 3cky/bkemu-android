/*
 * Created: 25.03.2012
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

/**
 * Memory (RAM/ROM) interface.
 */
public interface Memory {
    /**
     * Get memory address.
     * @return memory absolute address (0000000-0177777)
     */
    int getStartAddress();

    /**
     * Get memory size (in words!).
     * @return memory size in words
     */
    int getSize();

    /**
     * Get memory data.
     * @return memory data as words
     */
    short[] getData();

    /**
     * Read value from memory.
     * @param isByteAddressing <code>true</code> to read byte value, <code>false</code> to read
     * word value
     * @param address absolute address to read (0000000-0177777)
     * @return read value
     */
    int read(boolean isByteMode, int address);

    /**
     * Write value to memory.
     * @param isByteAddressing <code>true</code> to write byte value, <code>false</code> to write
     * word value
     * @param address absolute address to write (0000000-0177777)
     * @param value value to write to memory
     * @return <code>true</code> if value successfully written to memory,
     * <code>false</code> otherwise
     */
    boolean write(boolean isByteMode, int address, int value);

    /**
     * Check is given address related to this memory.
     * @param address absolute address to check (0000000-0177777)
     * @return <code>true</code> if given address is related to this memory,
     * <code>false</code> otherwise
     */
    boolean isRelatedAddress(int address);
}
