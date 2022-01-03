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
 * Memory class that allows to select/unselect the delegated memory.
 */
public class SelectableMemory extends AbstractMemory {
    private final Memory memory;

    private boolean isSelected;

    public SelectableMemory(String id, Memory memory, boolean isSelected) {
        super(id);
        this.memory = memory;
        setSelected(isSelected);
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    @Override
    public int getSize() {
        return memory.getSize();
    }

    @Override
    public short[] getData() {
        return memory.getData();
    }

    @Override
    public int read(int offset) {
        return isSelected ? memory.read(offset) : Computer.BUS_ERROR;
    }

    @Override
    public boolean write(boolean isByteMode, int offset, int value) {
        return isSelected && memory.write(isByteMode, offset, value);
    }

}
