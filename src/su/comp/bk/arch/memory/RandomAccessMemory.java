/*
 * Created: 25.03.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package su.comp.bk.arch.memory;

/**
 * RAM (read/write) class.
 */
public class RandomAccessMemory implements Memory {

    private final int startAddress;
    private final int size;
    private final short[] data;

    /**
     * Create new RAM page with initialization.
     * @param startAddress RAM page starting address
     * @param size RAM page size (in words)
     */
    public RandomAccessMemory(int startAddress, int size) {
        this.startAddress = startAddress;
        this.size = size;
        this.data = new short[getSize()];
        initMemoryData();
    }

    /**
     * Create new RAM page initialized from given data.
     * @param startAddress RAM page starting address
     * @param data data to copy into created RAM page
     */
    public RandomAccessMemory(int startAddress, short[] data) {
        this(startAddress, data.length);
        System.arraycopy(data, 0, this.data, 0, getSize());
    }

    /**
     * Create new RAM page initialized from given data.
     * @param startAddress RAM page starting address
     * @param data data to copy into created RAM page
     */
    public RandomAccessMemory(int startAddress, byte[] data) {
        this(startAddress, data.length >> 1);
        for (int idx = 0; idx < (getSize() << 1); idx++) {
            writeByte(getStartAddress() + idx, data[idx]);
        }
    }

    protected void initMemoryData() {
        // K565RU6 power-on pattern: 0177777/0000000 sequence, order switched every 0100 words
        for (int idx = 0; idx < getSize(); idx++) {
            data[idx] = (short) (((idx & 1) == ((idx >> 6) & 1) ? 0177777 : 0));
        }
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

    private int getWordIndex(int address) {
        return (address - getStartAddress()) >> 1;
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
    public void write(boolean isByteMode, int address, int value) {
        if (isByteMode) {
            writeByte(address, value);
        } else {
            writeWord(address, value);
        }
    }

    @Override
    public boolean isRelatedAddress(int address) {
        return address >= getStartAddress() && address < (getStartAddress() + (getSize() << 1));
    }

}
