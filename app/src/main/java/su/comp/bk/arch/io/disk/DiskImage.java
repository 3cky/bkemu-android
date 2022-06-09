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

package su.comp.bk.arch.io.disk;

import android.net.Uri;

import java.io.IOException;

/**
 * Disk image abstraction interface.
 */
public interface DiskImage {
    /**
     * Get disk image name.
     * @return disk image name
     */
    String getName();

    /**
     * Get disk image location.
     * @return disk image location {@link Uri}
     */
    Uri getLocation();

    /**
     * Check disk image is read only.
     * @return true if disk image is read only, false if read/write
     */
    boolean isReadOnly();

    /**
     * Get disk image length (in bytes).
     * @return disk image length
     */
    long length();

    /**
     * Close disk image.
     * @throws IOException in case of I/O error
     */
    void close() throws IOException;

    /**
     * Read byte from disk image at specified position.
     * @param position read position (in bytes)
     * @return read byte in eight lower bits of integer
     * @throws IOException in case of I/O error
     */
    int readByte(long position) throws IOException;

    /**
     * Read bytes from disk image at specified position to given buffer.
     * @param buffer buffer to read
     * @param position read position (in bytes)
     * @param length number of bytes to read
     * @throws IOException in case of I/O error
     */
    void readBytes(byte[] buffer, long position, int length) throws IOException;

    /**
     * Write byte to disk image at specified position.
     * @param position write position (in bytes)
     * @param value value to write
     * @throws IOException in case of I/O error
     */
    void writeByte(long position, byte value) throws IOException;

    /**
     * Write bytes from buffer to disk image at specified position.
     * @param buffer buffer to write
     * @param position write position (in bytes)
     * @param length number of bytes to write
     * @throws IOException in case of I/O error
     */
    void writeBytes(byte[] buffer, long position, int length) throws IOException;
}
