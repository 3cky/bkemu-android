/*
 * Created: 01.04.2012
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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;

/**
 * Autoincrement deferred addressing mode: @(Rn)+.
 * Rn contains the address of the address, then increment Rn by 2.
 */
public class AutoincrementDeferredAddressingMode implements AddressingMode {

    public final static int CODE = 3;

    private final Cpu cpu;

    public AutoincrementDeferredAddressingMode(Cpu cpu) {
        this.cpu = cpu;
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    public int readAddressedValue(boolean isByteAddressing, int register) {
        int addressedValue = cpu.readRegister(false, register);
        addressedValue = cpu.readMemory(false, addressedValue);
        if (addressedValue != Computer.BUS_ERROR) {
            addressedValue = cpu.readMemory(isByteAddressing, addressedValue);
        }
        return addressedValue;
    }

    @Override
    public boolean writeAddressedValue(boolean isByteAddressing, int register, int value) {
        boolean isWritten = false;
        int address = cpu.readRegister(false, register);
        address = cpu.readMemory(false, address);
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
        cpu.incrementRegister(false, register);
    }

}
