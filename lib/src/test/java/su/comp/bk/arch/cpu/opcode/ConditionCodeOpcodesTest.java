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

import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Condition-code instructions test.
 */
public class ConditionCodeOpcodesTest {

    @Test
    public void testConditionInstructionsExecute() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                ConditionCodeOpcodes.OPCODE_NOP,
                ConditionCodeOpcodes.OPCODE_SEC,
                ConditionCodeOpcodes.OPCODE_CLC,
                ConditionCodeOpcodes.OPCODE_SEN,
                ConditionCodeOpcodes.OPCODE_CLN,
                ConditionCodeOpcodes.OPCODE_SEV,
                ConditionCodeOpcodes.OPCODE_CLV,
                ConditionCodeOpcodes.OPCODE_SEZ,
                ConditionCodeOpcodes.OPCODE_CLZ,
                ConditionCodeOpcodes.OPCODE_SCC,
                ConditionCodeOpcodes.OPCODE_CCC
                }));
        computer.reset();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        // NOP
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        // SEC
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_C, computer.getCpu().getPswState());
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // CLC
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // SEN
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // CLN
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100012, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // SEV
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100014, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // CLV
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100016, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // SEZ
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100020, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // CLZ
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100022, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // SCC
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100024, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V | Cpu.PSW_FLAG_Z,
                computer.getCpu().getPswState());
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertTrue(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
        // CCC
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100026, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_C));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_N));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_V));
        assertFalse(computer.getCpu().isPswFlagSet(Cpu.PSW_FLAG_Z));
    }

}
