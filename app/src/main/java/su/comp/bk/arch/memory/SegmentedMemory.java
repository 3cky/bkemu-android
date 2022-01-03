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

package su.comp.bk.arch.memory;

import su.comp.bk.arch.Computer;

/**
 * Memory class allowing to divide given memory to equally sized segments.
 * Only one selected segment is accessible at a time.
 */
public class SegmentedMemory extends AbstractMemory {
    private final Memory memory;
    private final int segmentSize;

    private int activeSegmentIndex = -1;

    private int maxReadableOffset;
    private int maxWritableOffset;

    /**
     * Create new segmented memory with given segment size.
     * @param id segmented memory ID
     * @param memory memory to divide to segments
     * @param segmentSize segment size (in words)
     */
    public SegmentedMemory(String id, Memory memory, int segmentSize) {
        super(id);
        this.memory = memory;
        this.segmentSize = segmentSize;
        setReadableSize(segmentSize);
        setWritableSize(segmentSize);
    }

    /**
     * Set segment readable part size (defaults to total segment size).
     * @param readableSize segment readable size (in words)
     */
    public void setReadableSize(int readableSize) {
        this.maxReadableOffset = (readableSize > 0) ? (readableSize - 1) * 2 : -1;
    }

    /**
     * Set segment writable part size (defaults to total segment size).
     * @param writableSize segment writable size (in words)
     */
    public void setWritableSize(int writableSize) {
        this.maxWritableOffset = (writableSize > 0) ? (writableSize - 1) * 2 : -1;
    }

    private boolean isReadableOffset(int offset) {
        return offset <= maxReadableOffset;
    }

    private boolean isWritableOffset(int offset) {
        return offset <= maxWritableOffset;
    }

    @Override
    public int getSize() {
        return segmentSize;
    }

    @Override
    public short[] getData() {
        return memory.getData();
    }

    @Override
    public int read(int offset) {
        return !isReadableOffset(offset) || (activeSegmentIndex < 0)
                ? Computer.BUS_ERROR
                : memory.read(getActiveSegmentOffset() + offset);
    }

    @Override
    public boolean write(boolean isByteMode, int offset, int value) {
        return isWritableOffset(offset) && (activeSegmentIndex >= 0) && memory.write(isByteMode,
                getActiveSegmentOffset() + offset, value);
    }

    /**
     * Set active segment index.
     * @param activeSegmentIndex active segment index or -1 to set no active segment
     */
    public void setActiveSegmentIndex(int activeSegmentIndex) {
        this.activeSegmentIndex = activeSegmentIndex;
    }

    /**
     * Get active segment offset in the memory.
     * @return active segment offset (in bytes) or -1 if no active segment is set
     */
    private int getActiveSegmentOffset() {
        return (activeSegmentIndex >= 0) ? activeSegmentIndex * segmentSize * 2 : -1;
    }
}
