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
 * Index addressing mode: X(Rn).
 * Rn+X is the address of the operand.
 */
public class IndexAddressingMode extends BaseAddressingMode {

    public final static int CODE = 6;

    public IndexAddressingMode(Cpu cpu) {
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
        // Read value of register
        int registerValue = cpu.readRegister(false, register);
        // Read address of X
        int indexAddress = cpu.readRegister(false, Cpu.PC);
        // Read value of X
        int indexValue = cpu.readMemory(false, indexAddress);
        return (indexValue != Computer.BUS_ERROR) ? (registerValue + (short) indexValue) & 0177777
                : Computer.BUS_ERROR;
    }

}
