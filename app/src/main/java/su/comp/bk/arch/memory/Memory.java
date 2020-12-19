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

import android.os.Bundle;

/**
 * Memory (RAM/ROM) interface.
 */
public interface Memory {
    /**
     * Get this memory ID string.
     * @return memory ID
     */
    String getId();

    /**
     * Get memory address.
     * @return memory absolute address (0000000-0177776)
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
     * Read word value from the memory.
     * @param address absolute address to read (0000000-0177776)
     * @return read word value
     */
    int read(int address);

    /**
     * Write word/byte value to the memory.
     * @param isByteMode <code>true</code> to write byte value, <code>false</code> to write
     * word value
     * @param address absolute address to write (0000000-0177777)
     * @param value data bus word value to write to the memory. In byte mode value is in word's
     *              low byte for even addresses and in word's high byte for odd addresses)
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


    /**
     * Save memory state.
     * @param outState {@link Bundle} to save memory state
     */
    void saveState(Bundle outState);

    /**
     * Read memory state.
     * @param inState {@link Bundle} to restore memory state
     */
    void restoreState(Bundle inState);
}
