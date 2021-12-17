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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * File-based {@link DiskImage}.
 */
public class FileDiskImage implements DiskImage {
    private final File diskImageFile;

    private final RandomAccessFile diskImageRandomAccessFile;

    public FileDiskImage(File diskImageFile) throws IOException {
        this.diskImageFile = diskImageFile;
        diskImageRandomAccessFile = new RandomAccessFile(diskImageFile, "rw");
    }

    @Override
    public Uri getUri() {
        return Uri.fromFile(diskImageFile);
    }

    @Override
    public long length() {
        return diskImageFile.length();
    }

    @Override
    public void close() throws IOException {
        diskImageRandomAccessFile.close();
    }

    @Override
    public int readByte(long offset) throws IOException {
        diskImageRandomAccessFile.seek(offset);
        return diskImageRandomAccessFile.readUnsignedByte();
    }

    @Override
    public void writeByte(long offset, byte value) throws IOException {
        diskImageRandomAccessFile.seek(offset);
        diskImageRandomAccessFile.writeByte(value);
    }

    @Override
    public int readWord(long offset) throws IOException {
        diskImageRandomAccessFile.seek(offset);
        return diskImageRandomAccessFile.readUnsignedShort();
    }

    @Override
    public void writeWord(long offset, short value) throws IOException {
        diskImageRandomAccessFile.seek(offset);
        diskImageRandomAccessFile.writeShort(value);
    }
}
