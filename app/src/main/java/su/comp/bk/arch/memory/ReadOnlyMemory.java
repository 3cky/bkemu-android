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
 * ROM (read only) memory class.
 */
public class ReadOnlyMemory extends RandomAccessMemory {

    /**
     * Create new ROM initialized from given data.
     * @param id ROM ID
     * @param startAddress ROM starting address
     * @param data data to copy into created ROM
     */
    public ReadOnlyMemory(String id, int startAddress, short[] data) {
        super(id, startAddress, data, Type.OTHER);
    }

    public ReadOnlyMemory(String id, int startAddress, byte[] data) {
        super(id, startAddress, data, Type.OTHER);
    }

    @Override
    protected void initData(Type type) {
        // Do nothing
    }

    @Override
    public boolean write(boolean isByteMode, int address, int value) {
        return false;
    }
}
