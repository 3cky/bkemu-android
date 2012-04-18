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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Jump and subroutine opcodes tests.
 */
public class JumpSubroutineOpcodesTest {

    private Computer computer;

    @Before
    public void setUp() throws Exception {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
    }

    @Test
    public void testJmpInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                JmpOpcode.OPCODE | (1 << 3),     // 0100000: JMP (R0)
                ConditionCodeOpcodes.OPCODE_NOP, // 0100002: NOP
                JmpOpcode.OPCODE                 // 0100004: JMP R0
        }));
        computer.reset();
        // JMP (R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 0100004);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // JMP R0 - BUS_ERROR
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
    }

}
