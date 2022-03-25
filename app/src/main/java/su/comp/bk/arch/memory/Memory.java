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
     * Get this memory ID string.
     * @return memory ID
     */
    String getId();

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
     * Get memory data.
     * @param buf buffer to get data
     * @param srcOffset data offset to get
     * @param dstOffset buffer offset
     * @param length data length to get (in words)
     */
    void getData(short[] buf, int srcOffset, int dstOffset, int length);

    /**
     * Check memory is readable.
     * @param offset memory offset to check
     * @return true if memory at given offset is readable, false if not readable
     */
    boolean isReadable(int offset);

    /**
     * Read word value from the memory.
     * @param offset value offset to read
     * @return read word value
     */
    int read(int offset);

    /**
     * Check memory is writable.
     * @param offset memory offset to check
     * @return true if memory at given offset is writable, false if not writable
     */
    boolean isWritable(int offset);

    /**
     * Write word/byte value to the memory.
     * @param isByteMode <code>true</code> to write byte value, <code>false</code> to write
     * word value
     * @param offset value offset to write
     * @param value data bus word value to write to the memory. In byte mode value is in word's
     *              low byte for even offsets and in word's high byte for odd offsets)
     * @return <code>true</code> if value successfully written to memory,
     * <code>false</code> otherwise
     */
    boolean write(boolean isByteMode, int offset, int value);
}
