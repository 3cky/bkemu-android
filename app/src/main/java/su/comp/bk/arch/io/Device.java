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
package su.comp.bk.arch.io;

import su.comp.bk.state.StatefulEntity;

/**
 * I/O device interface.
 */
public interface Device extends StatefulEntity {
    /**
     * Get addresses this device is mapped to.
     * @return array of register addresses this device is mapped to (in range 0160000-0177776)
     */
    int[] getAddresses();

    /**
     * Handle bus INIT signal (on hardware reset or RESET instruction).
     * @param cpuTime current CPU time (in clock ticks)
     * @param isHardwareReset true if bus initialization is initiated by hardware reset,
     *                        false if it is initiated by RESET command
     */
    void init(long cpuTime, boolean isHardwareReset);

    /**
     * Read value from I/O device. Devices always read as word.
     * @param cpuTime current CPU time (in clock ticks)
     * @param address absolute address to read (from address list this device is mapped to)
     * @return read value
     */
    int read(long cpuTime, int address);

    /**
     * Write value to I/O device.
     * @param cpuTime current CPU time (in clock ticks)
     * @param address absolute address to write (from address list this device is mapped to)
     * @param value data bus word value to write to I/O device. In byte mode value is in word's
     *              low byte for even addresses and in word's high byte for odd addresses
     * @param isByteMode <code>true</code> to write byte value, <code>false</code> to write
     * word value
     * @return <code>true</code> if value successfully written, <code>false</code> if not
     */
    boolean write(long cpuTime, boolean isByteMode, int address, int value);
}
