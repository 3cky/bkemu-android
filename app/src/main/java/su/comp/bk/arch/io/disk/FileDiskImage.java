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

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * File-backed {@link DiskImage}.
 */
public class FileDiskImage implements DiskImage {
    private final File diskImageFile;

    private final RandomAccessFile diskImageRandomAccessFile;

    public FileDiskImage(File diskImageFile) throws IOException {
        this.diskImageFile = diskImageFile;
        diskImageRandomAccessFile = new RandomAccessFile(diskImageFile, "rw");
    }

    @Override
    public String getName() {
        return diskImageFile.getName();
    }

    @Override
    public Uri getLocation() {
        return Uri.fromFile(diskImageFile);
    }

    @Override
    public boolean isReadOnly() {
        return !diskImageFile.canWrite();
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
    public int readByte(long position) throws IOException {
        diskImageRandomAccessFile.seek(position);
        return diskImageRandomAccessFile.readUnsignedByte();
    }

    @Override
    public void readBytes(byte[] buffer, long position, int length) throws IOException {
        diskImageRandomAccessFile.seek(position);
        diskImageRandomAccessFile.read(buffer, 0, length);
    }

    @Override
    public void writeByte(long position, byte value) throws IOException {
        diskImageRandomAccessFile.seek(position);
        diskImageRandomAccessFile.writeByte(value);
    }

    @Override
    public void writeBytes(byte[] buffer, long position, int length) throws IOException {
        diskImageRandomAccessFile.seek(position);
        diskImageRandomAccessFile.write(buffer, 0, length);
    }

    @Override
    public int readWord(long position) throws IOException {
        diskImageRandomAccessFile.seek(position);
        return diskImageRandomAccessFile.readUnsignedShort();
    }

    @Override
    public void writeWord(long position, short value) throws IOException {
        diskImageRandomAccessFile.seek(position);
        diskImageRandomAccessFile.writeShort(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileDiskImage)) return false;

        FileDiskImage that = (FileDiskImage) o;

        return diskImageFile.equals(that.diskImageFile);
    }

    @Override
    public int hashCode() {
        return diskImageFile.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "FileDiskImage{" + diskImageFile + '}';
    }
}
