/*
 * Copyright (C) 2024 Victor Antonovich (v.antonovich@gmail.com)
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package su.comp.bk.arch.cpu.opcode;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Jump and subroutine opcodes tests.
 */
public class JumpSubroutineOpcodesTest {

    private Computer computer;

    @Before
    public void setUp() {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
    }

    @Test
    public void testJmpInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                JmpOpcode.OPCODE | 037,          // 0100000: JMP @#0100004
                (short) 0100004,
                ConditionCodeOpcodes.OPCODE_NOP, // 0100002: NOP
                JmpOpcode.OPCODE                 // 0100004: JMP R0
        }));
        computer.reset();
        // JMP @#0100004
        computer.getCpu().writeRegister(false, Cpu.R0, 0100004);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // JMP R0 - BUS_ERROR
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
    }

    @Test
    public void testJsrRtsInstructionsExecute() {
        computer.addMemory(0776, new RandomAccessMemory("TestRam",
                1, RandomAccessMemory.Type.K565RU6));
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                JsrOpcode.OPCODE | 037,    // 0100000: JSR R0, @#0100010
                (short) 0100010,
                JsrOpcode.OPCODE | 0737,   // 0100004: JSR PC, @#0100012
                (short) 0100012,
                RtsOpcode.OPCODE,          // 0100010: RTS R0
                RtsOpcode.OPCODE | 7       // 0100012: RTS PC
        }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
        computer.getCpu().writeRegister(false, Cpu.R0, 0123456);
        // JSR R0, @#0100010
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0776, computer.getCpu().readRegister(false, Cpu.SP));
        assertEquals(0123456, computer.getCpu().readMemory(false, 0776));
        // RTS R0
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0123456, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(01000, computer.getCpu().readRegister(false, Cpu.SP));
        // JSR PC, @#0100012
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100012, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0776, computer.getCpu().readRegister(false, Cpu.SP));
        assertEquals(0100010, computer.getCpu().readMemory(false, 0776));
        // RTS RC
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(01000, computer.getCpu().readRegister(false, Cpu.SP));
    }

}
