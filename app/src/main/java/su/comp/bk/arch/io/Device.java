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

import android.os.Bundle;

/**
 * I/O device interface.
 */
public interface Device {
    /**
     * Get addresses this device is mapped to.
     * @return array of register addresses this device is mapped to (in range 0160000-0177776)
     */
    int[] getAddresses();

    /**
     * Handle bus INIT signal (on hardware reset or RESET instruction).
     * @param cpuTime current CPU time (in clock ticks)
     */
    void init(long cpuTime);

    /**
     * Save device state.
     * @param outState {@link Bundle} to save device state
     */
    void saveState(Bundle outState);

    /**
     * Read device state.
     * @param inState {@link Bundle} to restore device state
     */
    void restoreState(Bundle inState);

    /**
     * Do timer task.
     * @param uptime current computer uptime (in nanoseconds)
     */
    void timer(long uptime);

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
     * @param value value to write to device
     * @param isByteMode <code>true</code> to write byte value, <code>false</code> to write
     * word value
     * @return <code>true</code> if value successfully written, <code>false</code> if not
     */
    boolean write(long cpuTime, boolean isByteMode, int address, int value);
}
