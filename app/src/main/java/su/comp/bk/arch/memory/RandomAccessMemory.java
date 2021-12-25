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
 * RAM (read/write) class.
 */
public class RandomAccessMemory implements Memory {

    private final String id;
    private final int startAddress;
    private final int size;
    private final short[] data;

    private final int endAddress;

    /**
     * RAM types enumeration.
     */
    public enum Type {
        K565RU6, // Dynamic RAM (clone of 4116)
        K565RU5, // Dynamic RAM (clone of 4164)
        K537RU10, // Static RAM (clone of I6264)
        OTHER // Other memory types
    }

    /**
     * Create new RAM of given type (dynamic/static).
     * @param id RAM ID
     * @param startAddress RAM starting address
     * @param size RAM size (in words)
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, int startAddress, int size, Type type) {
        this.id = id;
        this.startAddress = startAddress;
        this.endAddress = startAddress + (size << 1) - 1;
        this.size = size;
        this.data = new short[getSize()];
        initData(type);
    }

    /**
     * Create new RAM initialized from given data.
     * @param id RAM ID
     * @param startAddress RAM starting address
     * @param data data to copy into created RAM
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, int startAddress, short[] data, Type type) {
        this(id, startAddress, data.length, type);
        putData(data);
    }

    /**
     * Create new RAM initialized from given data.
     * @param id RAM ID
     * @param startAddress RAM starting address
     * @param data data to copy into created RAM
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, int startAddress, byte[] data, Type type) {
        this(id, startAddress, data.length >> 1, type);
        putData(data);
    }

    protected void initData(Type type) {
        switch (type) {
            case K565RU6:
                // K565RU6 power-on pattern: 0177777/0000000 sequence, order switched every 0100 words
                for (int idx = 0; idx < getSize(); idx++) {
                    data[idx] = (short) (((idx & 1) == ((idx >> 6) & 1) ? 0177777 : 0));
                }
                break;
            case K565RU5:
                // FIXME K565RU5 power-on pattern
                for (int idx = 0; idx < getSize(); idx++) {
                    data[idx] = (short) (((idx & 1) == ((idx >> 7) & 1) ? 0177777 : 0));
                }
                break;
            default:
                break;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getStartAddress() {
        return startAddress;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public short[] getData() {
        return data;
    }

    public void putData(short[] wordData) {
        System.arraycopy(wordData, 0, data, 0, wordData.length);
    }

    public void putData(byte[] byteData) {
        int idx = 0;
        while (idx < byteData.length) {
            int wordIdx = idx >> 1;
            int value = (byteData[idx++] & 0377) | ((byteData[idx++] << 8) & 0177400);
            data[wordIdx] = (short) value;
        }
    }

    private int getWordIndex(int address) {
        return (address - startAddress) >> 1;
    }

    protected int readWord(int address) {
        return data[getWordIndex(address)] & 0177777;
    }

    protected void writeWord(int address, int wordData) {
        data[getWordIndex(address)] = (short) wordData;
    }

    @Override
    public int read(int address) {
        return readWord(address);
    }

    @Override
    public boolean write(boolean isByteMode, int address, int value) {
        if (isByteMode) {
            int w = readWord(address);
            // Little-endian byte order
            if ((address & 1) == 0) {
                value = (w & 0177400) | (value & 0377);
            } else {
                value = (value & 0177400) | (w & 0377);
            }
        }
        writeWord(address, value);
        return true;
    }

    @Override
    public boolean isRelatedAddress(int address) {
        return (address >= startAddress) && (address <= endAddress);
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putShortArray(toString(), getData());
    }

    @Override
    public void restoreState(Bundle inState) {
        putData(inState.getShortArray(toString()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) || (o instanceof RandomAccessMemory
                && ((RandomAccessMemory) o).getId().equals(id));
    }

    @Override
    public String toString() {
        return getClass().getName() + "#" + id;
    }

}
