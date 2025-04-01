/*
 * Created: 18.04.2012
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
package su.comp.bk.arch.cpu.addressing;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;

/**
 * Base addressing mode class.
 */
public abstract class BaseAddressingMode implements AddressingMode {

    protected final Cpu cpu;

    public BaseAddressingMode(Cpu cpu) {
        this.cpu = cpu;
    }

    @Override
    public int readAddressedValue(boolean isByteAddressing, int register) {
        int address = getAddress(register);
        return (address != Computer.BUS_ERROR) ? cpu.readMemory(isByteAddressing, address)
                : Computer.BUS_ERROR;
    }

    @Override
    public boolean writeAddressedValue(boolean isByteAddressing, int register, int value) {
        boolean isWritten = false;
        int address = getAddress(register);
        if (address != Computer.BUS_ERROR) {
            isWritten = cpu.writeMemory(isByteAddressing, address, value);
        }
        return isWritten;
    }

    @Override
    public void preAddressingAction(boolean isByteAddressing, int register) {
        // Do nothing
    }

    @Override
    public void postAddressingAction(boolean isByteAddressing, int register) {
        // Do nothing
    }

}
