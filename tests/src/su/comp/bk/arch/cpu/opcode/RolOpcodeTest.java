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
 * ROL operation tests.
 */
public class RolOpcodeTest {

    private static final int PSW_STATE = 0340;

    @Test
    public void testRolInstructionExecute() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                RolOpcode.OPCODE, // ROL R0
                (short) (RolOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // ROLB R0
                }));
        computer.reset();
        // Shift of 1
        // ROL R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(2, computer.getCpu().readRegister(false, Cpu.R0));
        // ROLB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().clearPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(2, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of 0 with carry in
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ROL R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        // ROLB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of -1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ROL R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177776, computer.getCpu().readRegister(false, Cpu.R0));
        // ROLB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().clearPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0376, computer.getCpu().readRegister(false, Cpu.R0));
    }

}
