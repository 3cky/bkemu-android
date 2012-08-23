/*
 * Created: 01.04.2012
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
 * Index deferred addressing mode: @X(Rn).
 * Rn+X is the address of the address.
 */
public class IndexDeferredAddressingMode extends BaseAddressingMode {

    public final static int CODE = 7;

    public IndexDeferredAddressingMode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    public void postAddressingAction(boolean isByteAddressing, int register) {
        cpu.incrementRegister(false, Cpu.PC);
    }

    @Override
    public int getAddress(int register) {
        int address = Computer.BUS_ERROR;
        // Read address of X
        int indexAddress = cpu.readRegister(false, Cpu.PC);
        // Read value of X
        int indexValue = cpu.readMemory(false, indexAddress);
        if (indexValue != Computer.BUS_ERROR) {
            // Read value of register
            int registerValue = cpu.readRegister(false, register);
            if (register == Cpu.PC) {
                // Take into account PC postincrementing after index value reading
                // for relative deferred addressing mode (code 77)
                registerValue += 2;
            }
            address = cpu.readMemory(false, (registerValue + (short) indexValue) & 0177777);
        }
        return address;
    }

}
