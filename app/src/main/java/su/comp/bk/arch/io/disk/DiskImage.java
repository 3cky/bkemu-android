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
 * Disk image operations interface.
 */
public interface DiskImage {
    /**
     * Get disk image location URI.
     * @return disk image location {@link Uri}
     */
    Uri getUri();

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
     * Read byte from disk image at specified offset.
     * @param offset read offset (in bytes)
     * @return read byte in eight lower bits of integer
     * @throws IOException in case of I/O error
     */
    int readByte(long offset) throws IOException;

    /**
     * Write byte to disk image at specified offset.
     * @param offset write offset (in bytes)
     * @param value value to write
     * @throws IOException in case of I/O error
     */
    void writeByte(long offset, byte value) throws IOException;

    /**
     * Read word (big-endian) from disk image at specified offset.
     * @param offset read offset (in bytes)
     * @return read word in sixteen lower bits of integer
     * @throws IOException in case of I/O error
     */
    int readWord(long offset) throws IOException;

    /**
     * Write word to disk image at specified offset.
     * @param offset write offset (in bytes)
     * @param value value to write
     * @throws IOException in case of I/O error
     */
    void writeWord(long offset, short value) throws IOException;
}
