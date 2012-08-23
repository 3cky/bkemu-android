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

import su.comp.bk.arch.cpu.Cpu;

/**
 * Autoincrement deferred addressing mode: @(Rn)+.
 * Rn contains the address of the address, then increment Rn by 2.
 */
public class AutoincrementDeferredAddressingMode extends BaseAddressingMode {

    public final static int CODE = 3;

    public AutoincrementDeferredAddressingMode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    public void postAddressingAction(boolean isByteAddressing, int register) {
        cpu.incrementRegister(false, register);
    }

    @Override
    public int getAddress(int register) {
        int address = cpu.readRegister(false, register);
        return cpu.readMemory(false, address);
    }

}
