/*
 * Created: 12.04.2012
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
package su.comp.bk.arch.cpu.opcode;

import su.comp.bk.arch.cpu.Cpu;

/**
 * Subtract one and branch opcode.
 */
public class SobOpcode extends BaseOpcode {

    public final static int OPCODE = 077000;

    private int branchOffset;
    private int subtractRegister;

    public SobOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public void decode(int instructionToDecode) {
        super.decode(instructionToDecode);
        this.branchOffset = instructionToDecode & 077;
        this.subtractRegister = (instructionToDecode >> 6) & 7;
    }

    @Override
    public void execute() {
        Cpu cpu = getCpu();
        int registerValue = cpu.readRegister(false, subtractRegister);
        registerValue -= 1;
        cpu.writeRegister(false, subtractRegister, registerValue);
        if (registerValue != 0) {
            int pc = cpu.readRegister(false, Cpu.PC);
            cpu.writeRegister(false, Cpu.PC, pc - branchOffset * 2);
        }
    }
}
