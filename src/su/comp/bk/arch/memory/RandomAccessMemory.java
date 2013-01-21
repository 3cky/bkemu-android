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
        K537RU10 // Static RAM (clone of I6264)
    }

    /**
     * Create new RAM page of given type (dynamic/static).
     * @param id RAM page ID
     * @param startAddress RAM page starting address
     * @param size RAM page size (in words)
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, int startAddress, int size, Type type) {
        this.id = id;
        this.startAddress = startAddress;
        this.endAddress = startAddress + (size << 1) - 1;
        this.size = size;
        this.data = new short[getSize()];
        initMemoryData(type);
    }

    /**
     * Create new dynamic RAM page.
     * @param id RAM page ID
     * @param startAddress RAM page starting address
     * @param size RAM page size (in words)
     */
    public RandomAccessMemory(String id, int startAddress, int size) {
        this(id, startAddress, size, Type.K565RU6);
    }

    /**
     * Create new RAM page initialized from given data.
     * @param id RAM page ID
     * @param startAddress RAM page starting address
     * @param data data to copy into created RAM page
     */
    public RandomAccessMemory(String id, int startAddress, short[] data) {
        this(id, startAddress, data.length);
        System.arraycopy(data, 0, this.data, 0, getSize());
    }

    /**
     * Create new RAM page initialized from given data.
     * @param id RAM page ID
     * @param startAddress RAM page starting address
     * @param data data to copy into created RAM page
     */
    public RandomAccessMemory(String id, int startAddress, byte[] data) {
        this(id, startAddress, data.length >> 1);
        for (int idx = 0; idx < (getSize() << 1); idx++) {
            writeByte(getStartAddress() + idx, data[idx]);
        }
    }

    protected void initMemoryData(Type type) {
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
                    data[idx] = (short) (((idx & 4) == ((idx >> 6) & 1) ? 0177777 : 0));
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

    public void putData(short[] dataToPut) {
        System.arraycopy(dataToPut, 0, data, 0, dataToPut.length);
    }

    private int getWordIndex(int address) {
        return (address - startAddress) >> 1;
    }

    private int readByte(int address) {
        int wordData = readWord(address);
        // Little-endian byte order
        return (address & 1) == 0 ? wordData & 0377 : wordData >> 8;
    }

    private int readWord(int address) {
        return data[getWordIndex(address)] & 0177777;
    }

    private void writeByte(int address, int byteData) {
        byteData &= 0377;
        int wordData = readWord(address);
        // Little-endian byte order
        if ((address & 1) == 0) {
            wordData &= 0177400;
            wordData |= byteData;
        } else {
            wordData &= 0377;
            wordData |= byteData << 8;
        }
        writeWord(address, wordData);
    }

    private void writeWord(int address, int wordData) {
        data[getWordIndex(address)] = (short) wordData;
    }

    @Override
    public int read(boolean isByteMode, int address) {
        return isByteMode ? readByte(address) : readWord(address);
    }

    @Override
    public boolean write(boolean isByteMode, int address, int value) {
        if (isByteMode) {
            writeByte(address, value);
        } else {
            writeWord(address, value);
        }
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
