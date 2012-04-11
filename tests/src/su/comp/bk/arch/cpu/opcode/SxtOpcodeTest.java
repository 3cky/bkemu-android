/*
 * Created: 09.04.2012
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

import static org.junit.Assert.*;

import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * SXT operation tests.
 */
public class SxtOpcodeTest {

    @Test
    public void testSxtInstructionExecute() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                SxtOpcode.OPCODE, // SXT R0
                (short) (SxtOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) // SXTB R0
                }));
        computer.reset();
        // N flag cleared
        // SXT R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().clearPswFlagN();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // N flag set
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // SXT R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagN();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R0));
    }

}
