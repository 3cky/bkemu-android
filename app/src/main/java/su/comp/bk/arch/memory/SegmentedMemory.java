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

import android.os.Bundle;

public class SegmentedMemory implements Memory {
    @Override
    public String getId() {
        return null;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public short[] getData() {
        return new short[0];
    }

    @Override
    public int read(int offset) {
        return 0;
    }

    @Override
    public boolean write(boolean isByteMode, int offset, int value) {
        return false;
    }

    @Override
    public void saveState(Bundle outState) {

    }

    @Override
    public void restoreState(Bundle inState) {

    }
}
