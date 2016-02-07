/*
 * Created: 19.04.2012
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
package su.comp.bk.arch.cpu.opcode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Trap opcodes (EMT/TRAP/RTI) tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21)
public class TrapOpcodesTest {

    private Computer computer;

    @Before
    public void setUp() throws Exception {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addMemory(new RandomAccessMemory("TestRam", 0, 01000));
    }

    @Test
    public void testBptInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                BptOpcode.OPCODE,                    // 0100000: BPT
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100002: NOP
                RtiOpcode.OPCODE                     // 0100004: RTI
        }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_BPT, 0100004));
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_BPT + 2, 0340));
        computer.getCpu().setPswState((short) 0347);
        // BPT
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        // RTI
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0347, computer.getCpu().getPswState());
    }

    @Test
    public void testIotInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                IotOpcode.OPCODE,                    // 0100000: IOT
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100002: NOP
                RtiOpcode.OPCODE                     // 0100004: RTI
        }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_IOT, 0100004));
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_IOT + 2, 0340));
        computer.getCpu().setPswState((short) 0347);
        // IOT
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        // RTI
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0347, computer.getCpu().getPswState());
    }

    @Test
    public void testEmtInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                (short) EmtOpcode.OPCODE,            // 0100000: EMT 0
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100002: NOP
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100004: NOP
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100006: NOP
                RtiOpcode.OPCODE                     // 0100010: RTI
        }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_EMT, 0100010));
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_EMT + 2, 0340));
        computer.getCpu().setPswState((short) 0347);
        // EMT 0
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        // RTI
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0347, computer.getCpu().getPswState());
    }

    @Test
    public void testTrapInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                (short) TrapOpcode.OPCODE,           // 0100000: TRAP 0
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100002: NOP
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100004: NOP
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100006: NOP
                RtiOpcode.OPCODE                     // 0100010: RTI
        }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_TRAP, 0100010));
        assertTrue(computer.getCpu().writeMemory(false, Cpu.TRAP_VECTOR_TRAP + 2, 0340));
        computer.getCpu().setPswState((short) 0347);
        // TRAP 0
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
        // RTI
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0347, computer.getCpu().getPswState());
    }

}
