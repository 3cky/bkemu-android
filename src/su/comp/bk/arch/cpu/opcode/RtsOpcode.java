/*
 * Created: 18.04.2012
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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;

/**
 * Return from subroutine instruction.
 */
public class RtsOpcode extends BaseOpcode {

    public final static int OPCODE = 0200;

    private int linkageRegister;

    public RtsOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    private void decodeLinkageRegister() {
        this.linkageRegister = getInstruction() & 7;
    }

    @Override
    public void decode(int instruction) {
        super.decode(instruction);
        decodeLinkageRegister();
    }

    @Override
    public void execute() {
        Cpu cpu = getCpu();
        // Write linkage register value to PC
        cpu.writeRegister(false, Cpu.PC, cpu.readRegister(false, linkageRegister));
        // Pop linkage register value from stack
        int linkageRegisterValue = cpu.pop();
        if (linkageRegisterValue != Computer.BUS_ERROR) {
            cpu.writeRegister(false, linkageRegister, linkageRegisterValue);
        }
    }

}
