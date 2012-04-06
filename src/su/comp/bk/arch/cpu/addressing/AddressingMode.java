/*
 * Created: 31.03.2012
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
package su.comp.bk.arch.cpu.addressing;

/**
 * PDP-11 addressing mode interface.
 */
public interface AddressingMode {

    /**
     * Get addressing mode code.
     * @return addressing mode code (0-7)
     */
    int getCode();

    /**
     * Do preaddressing action (like predecrement)
     * @param isByteAddressing <code>true</code> if byte value addressed, <code>false</code> if
     * word value
     * @param register register number (0-7)
     */
    void preAddressingAction(boolean isByteAddressing, int register);

    /**
     * Do postaddressing action (like postincrement)
     * @param isByteAddressing <code>true</code> if byte value addressed, <code>false</code> if
     * word value
     * @param register register number (0-7)
     */
    void postAddressingAction(boolean isByteAddressing, int register);

    /**
     * Read value addressed by this addressing mode.
     * @param isByteAddressing <code>true</code> to get byte value, <code>false</code> to get
     * word value
     * @param register register number (0-7)
     * @return value addressed by this addressing mode or Computer.INVALID_ADDRESS in case
     * if no memory/device is mapped to addressed location
     */
    int readAddressedValue(boolean isByteAddressing, int register);

    /**
     * Write value addressed by this addressing mode.
     * @param isByteAddressing <code>true</code> to write byte value, <code>false</code> to write
     * word value
     * @param register register number (0-7)
     * @param value value to write to address addressed by this addressing mode
     * @return <code>true</code> if value successfully written, <code>false</code> if
     * no memory/device is mapped to addressed location
     */
    boolean writeAddressedValue(boolean isByteAddressing, int register, int value);
}
