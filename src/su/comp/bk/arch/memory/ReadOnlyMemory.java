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
 * ROM (read only) memory class.
 */
public class ReadOnlyMemory extends RandomAccessMemory {

    /**
     * Create new ROM page initialized from given data.
     * @param startAddress ROM page starting address
     * @param data data to copy into created ROM page
     */
    public ReadOnlyMemory(int startAddress, short[] data) {
        super(startAddress, data);
    }

    public ReadOnlyMemory(int startAddress, byte[] data) {
        super(startAddress, data);
    }

    @Override
    protected void initMemoryData() {
        // Do nothing
    }

    @Override
    public void write(boolean isByteMode, int address, int value) {
        // Do nothing
    }
}
