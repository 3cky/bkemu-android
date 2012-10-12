/*
 * Created: 11.10.2012
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
package su.comp.bk.arch.io;

import android.os.Bundle;

/**
 * Floppy drive controller (К1801ВП1-128).
 */
public class FloppyController implements Device {

    private static final String TAG = FloppyController.class.getName();

    /** Control register address */
    public final static int CONTROL_REGISTER_ADDRESS = 0177130;

    /** Data register address */
    public final static int DATA_REGISTER_ADDRESS = 0177132;

    private final static int[] ADDRESSES = { CONTROL_REGISTER_ADDRESS, DATA_REGISTER_ADDRESS };

    /** Drive A identifier */
    public final static int DRIVE_A = 0;
    /** Drive B identifier */
    public final static int DRIVE_B = 1;
    /** Drive C identifier */
    public final static int DRIVE_C = 2;
    /** Drive D identifier */
    public final static int DRIVE_D = 3;

    // Floppy drives array
    private final FloppyDrive[] floppyDrives = new FloppyDrive[4];

    class FloppyDrive {

    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        // TODO Auto-generated method stub

    }

    @Override
    public void saveState(Bundle outState) {
        // TODO Auto-generated method stub

    }

    @Override
    public void restoreState(Bundle inState) {
        // TODO Auto-generated method stub

    }

    @Override
    public int read(long cpuTime, int address) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void write(long cpuTime, boolean isByteMode, int address, int value) {
        // TODO Auto-generated method stub

    }

}
