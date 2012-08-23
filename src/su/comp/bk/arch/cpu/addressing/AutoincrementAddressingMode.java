/*
 * Created: 31.03.2012
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
 * Autoincrement addressing mode: (Rn)+.
 * Rn contains the address of the operand, then increment Rn.
 */
public class AutoincrementAddressingMode extends BaseAddressingMode {

    public final static int CODE = 2;

    public AutoincrementAddressingMode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getCode() {
        return CODE;
    }

    @Override
    public void postAddressingAction(boolean isByteAddressing, int register) {
        cpu.incrementRegister(isByteAddressing, register);
    }

    @Override
    public int getAddress(int register) {
        return cpu.readRegister(false, register);
    }

}
