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
 * RAM (read/write) class.
 */
public class RandomAccessMemory extends AbstractMemory {
    private final int size;
    private final short[] data;

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
     * @param size RAM size (in words)
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, int size, Type type) {
        super(id);
        this.size = size;
        this.data = new short[getSize()];
        initData(type);
    }

    /**
     * Create new RAM initialized from given data.
     * @param id RAM ID
     * @param data data to copy into created RAM
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, short[] data, Type type) {
        this(id, data.length, type);
        putData(data);
    }

    /**
     * Create new RAM initialized from given data.
     * @param id RAM ID
     * @param data data to copy into created RAM
     * @param type RAM {@link Type}
     */
    public RandomAccessMemory(String id, byte[] data, Type type) {
        this(id, data.length >> 1, type);
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

    private int getWordIndex(int offset) {
        return offset >> 1;
    }

    protected int readWord(int offset) {
        return data[getWordIndex(offset)] & 0177777;
    }

    protected void writeWord(int offset, int wordData) {
        data[getWordIndex(offset)] = (short) wordData;
    }

    @Override
    public int read(int offset) {
        return readWord(offset);
    }

    @Override
    public boolean write(boolean isByteMode, int offset, int value) {
        if (isByteMode) {
            int w = readWord(offset);
            // Little-endian byte order
            if ((offset & 1) == 0) {
                value = (w & 0177400) | (value & 0377);
            } else {
                value = (value & 0177400) | (w & 0377);
            }
        }
        writeWord(offset, value);
        return true;
    }

}
