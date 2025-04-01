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
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Double operand opcodes test.
 */
public class DoubleOperandOpcodesTest {

    private static final int PSW_STATE = 0340;

    private Computer computer;

    @Before
    public void setUp() {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
    }

    /**
     * MOV operation tests.
     */
    @Test
    public void testMovInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                MovOpcode.OPCODE + Cpu.R1, // MOV R0, R1
                (short) ((MovOpcode.OPCODE + Cpu.R1) | Opcode.BYTE_OPERATION_FLAG), // MOVB R0, R1
        }));
        computer.reset();
        // MOV R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().writeRegister(false, Cpu.R1, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // MOVB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().writeRegister(false, Cpu.R1, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
    }

    /**
     * CMP operation tests.
     */
    @Test
    public void testCmpInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                CmpOpcode.OPCODE + Cpu.R1, // CMP R0, R1
                (short) ((CmpOpcode.OPCODE + Cpu.R1) | Opcode.BYTE_OPERATION_FLAG), // CMPB R0, R1
        }));
        computer.reset();
        // Check for Z flag
        // CMP R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // CMPB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 01001);
        computer.getCpu().writeRegister(false, Cpu.R1, 0401);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(01001, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0401, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // CMP R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().writeRegister(false, Cpu.R1, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // CMPB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 01377);
        computer.getCpu().writeRegister(false, Cpu.R1, 0401);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // Check for V and C flags
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // CMP R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 077777);
        computer.getCpu().writeRegister(false, Cpu.R1, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C,
                computer.getCpu().getPswState());
        // CMPB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0577);
        computer.getCpu().writeRegister(false, Cpu.R1, 01377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C,
                computer.getCpu().getPswState());
    }

    /**
     * ADD operation tests.
     */
    @Test
    public void testAddInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                (short) (AddOpcode.OPCODE + Cpu.R1), // ADD R0, R1
        }));
        computer.reset();
        // Check for Z and C flags
        // ADD R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z | Cpu.PSW_FLAG_C, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ADD R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 0177776);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // Check for V and C flags
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ADD R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().writeRegister(false, Cpu.R1, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V | Cpu.PSW_FLAG_Z | Cpu.PSW_FLAG_C,
                computer.getCpu().getPswState());
    }

    /**
     * SUB operation tests.
     */
    @Test
    public void testSubInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                (short) (SubOpcode.OPCODE + Cpu.R1), // SUB R0, R1
        }));
        computer.reset();
        // Check for Z flag
        // SUB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // SUB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0177776, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // Check for V and C flags
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // SUB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().writeRegister(false, Cpu.R1, 077777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C,
                computer.getCpu().getPswState());
    }

    /**
     * BIT operation tests.
     */
    @Test
    public void testBitInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                BitOpcode.OPCODE + Cpu.R1, // BIT R0, R1
                (short) ((BitOpcode.OPCODE + Cpu.R1) | Opcode.BYTE_OPERATION_FLAG), // BITB R0, R1
        }));
        computer.reset();
        // Check for Z flag
        // BIT R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 2);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // BITB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0401);
        computer.getCpu().writeRegister(false, Cpu.R1, 0402);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // BIT R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().writeRegister(false, Cpu.R1, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // BITB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 01200);
        computer.getCpu().writeRegister(false, Cpu.R1, 01377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
    }

    /**
     * BIC operation tests.
     */
    @Test
    public void testBicInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                BicOpcode.OPCODE + Cpu.R1, // BIC R0, R1
                (short) ((BicOpcode.OPCODE + Cpu.R1) | Opcode.BYTE_OPERATION_FLAG), // BICB R0, R1
        }));
        computer.reset();
        // Check for Z flag
        // BIC R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // BICB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0401);
        computer.getCpu().writeRegister(false, Cpu.R1, 0401);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0401, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0400, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // BIC R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0177776, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // BICB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().writeRegister(false, Cpu.R1, 01377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(01376, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
    }

    /**
     * BIS operation tests.
     */
    @Test
    public void testBisInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                BisOpcode.OPCODE + Cpu.R1, // BIS R0, R1
                (short) ((BisOpcode.OPCODE + Cpu.R1) | Opcode.BYTE_OPERATION_FLAG), // BISB R0, R1
        }));
        computer.reset();
        // Check for Z flag
        // BIS R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().writeRegister(false, Cpu.R1, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // BISB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0400);
        computer.getCpu().writeRegister(false, Cpu.R1, 0400);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0400, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0400, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // BIS R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 077777);
        computer.getCpu().writeRegister(false, Cpu.R1, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(077777, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // BISB R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 02200);
        computer.getCpu().writeRegister(false, Cpu.R1, 01177);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(02200, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(01377, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
    }

    /**
     * XOR operation tests.
     */
    @Test
    public void testXorInstructionExecute() {
        computer.addMemory(0100000, new ReadOnlyMemory("TestRom", new short[] {
                XorOpcode.OPCODE + Cpu.R1 // XOR R0, R1
        }));
        computer.reset();
        // Check for Z flag
        // XOR R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 0101);
        computer.getCpu().writeRegister(false, Cpu.R1, 0101);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0101, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // Check for N flag
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // XOR R0, R1
        computer.getCpu().writeRegister(false, Cpu.R0, 077777);
        computer.getCpu().writeRegister(false, Cpu.R1, 0100001);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(077777, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0177776, computer.getCpu().readRegister(false, Cpu.R1));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
    }

}
