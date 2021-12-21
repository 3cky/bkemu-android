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

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import su.comp.bk.util.FileUtils;
import timber.log.Timber;

/**
 * Android Storage Access Framework backed {@link DiskImage}.
 */
public class SafDiskImage implements DiskImage {
    private final Context context;
    private final Uri location;
    private final boolean isReadOnly;

    private final ByteBuffer diskImageBuffer;

    private final ParcelFileDescriptor diskImageFileDescriptor;

    private FileChannel diskImageFileChannel;
    private boolean isDiskImageFileChannelInInputMode;

    public SafDiskImage(Context context, Uri location) throws IOException {
        this.context = context;
        this.location = location;

        ParcelFileDescriptor diskImageFileDescriptor = null;
        boolean isReadOnly = false;

        try {
            diskImageFileDescriptor = context.getContentResolver().openFileDescriptor(location, "rw");
        } catch (SecurityException e) {
            Timber.d(e, "Can't open disk image for read/write: %s", location);
        }

        if (diskImageFileDescriptor == null) {
            try {
                diskImageFileDescriptor = context.getContentResolver().openFileDescriptor(location, "r");
                isReadOnly = true;
            } catch (SecurityException e) {
                throw new IOException("Can't open disk image: " + location, e);
            }
        }

        this.diskImageFileDescriptor = diskImageFileDescriptor;
        this.isReadOnly = isReadOnly;

        diskImageBuffer = ByteBuffer.allocate(2);
        diskImageBuffer.order(ByteOrder.BIG_ENDIAN);

        setDiskImageFileChannelInInputMode(true);
    }

    private void setDiskImageFileChannelInInputMode(boolean isInputMode) throws IOException {
        if (isDiskImageFileChannelInInputMode == isInputMode) {
            return;
        }

        if (!isInputMode && isReadOnly) {
            throw new IOException("Write attempt to read-only disk image");
        }

        close();

        if (isInputMode) {
            FileInputStream fis = new FileInputStream(diskImageFileDescriptor.getFileDescriptor());
            diskImageFileChannel = fis.getChannel();
        } else {
            FileOutputStream fos = new FileOutputStream(diskImageFileDescriptor.getFileDescriptor());
            diskImageFileChannel = fos.getChannel();
        }

        isDiskImageFileChannelInInputMode = isInputMode;
    }

    @Override
    public String getName() {
        return FileUtils.resolveUriFileName(context, location);
    }

    @Override
    public Uri getLocation() {
        return location;
    }

    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    @Override
    public long length() {
        try {
            return diskImageFileChannel.size();
        } catch (IOException ignored) {}

        return 0L;
    }

    @Override
    public void close() throws IOException {
        if (diskImageFileChannel != null) {
            diskImageFileChannel.close();
        }
    }

    private void readBuffer(long offset, int size) throws IOException {
        setDiskImageFileChannelInInputMode(true);
        diskImageFileChannel.position(offset);
        diskImageBuffer.rewind();
        diskImageBuffer.limit(size);
        do {
            int len = diskImageFileChannel.read(diskImageBuffer);
            if (len < 0) {
                throw new IOException("readBuffer(" + offset + ", " + size + "): " + len);
            }
        } while (diskImageBuffer.position() < size);
    }

    private void writeBuffer(long offset) throws IOException {
        setDiskImageFileChannelInInputMode(false);
        diskImageFileChannel.position(offset);
        diskImageBuffer.flip();
        do {
            int len = diskImageFileChannel.write(diskImageBuffer);
            if (len < 0) {
                throw new IOException("writeBuffer(" + offset + "): " + len);
            }
        } while (diskImageBuffer.remaining() > 0);
    }

    @Override
    public int readByte(long offset) throws IOException {
        readBuffer(offset, 1);
        return diskImageBuffer.get(0) & 0xFF;
    }

    @Override
    public void writeByte(long offset, byte value) throws IOException {
        diskImageBuffer.clear();
        diskImageBuffer.put(value);
        writeBuffer(offset);
    }

    @Override
    public int readWord(long offset) throws IOException {
        readBuffer(offset, 2);
        return diskImageBuffer.getShort(0) & 0xFFFF;
    }

    @Override
    public void writeWord(long offset, short value) throws IOException {
        diskImageBuffer.clear();
        diskImageBuffer.putShort(value);
        writeBuffer(offset);
    }

    @NonNull
    @Override
    public String toString() {
        return "SafDiskImage{" + location + '}';
    }
}
