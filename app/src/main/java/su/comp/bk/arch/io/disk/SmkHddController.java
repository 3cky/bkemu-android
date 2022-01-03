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

import android.os.Bundle;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.io.Device;
import timber.log.Timber;

/**
 * SMK512 hard disk drive controller.
 */
public class SmkHddController implements Device {
    private final static int[] ADDRESSES = { 0177740, 0177742, 0177744, 0177746, 0177750, 0177752, 0177754, 0177756 };

    public SmkHddController(Computer computer) {

    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {

    }

    @Override
    public void saveState(Bundle outState) {

    }

    @Override
    public void restoreState(Bundle inState) {

    }

    @Override
    public int read(long cpuTime, int address) {
//        Timber.d("HDD read: address 0%o", address);
        return 0177777;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        Timber.d("HDD write: address 0%o, data 0%o", address, value);
        return true;
    }
}
