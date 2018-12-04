/*
 * Created: 12.04.2012
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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Single operand opcodes test.
 */
public class SingleOperandOpcodesTest {

    private static final int PSW_STATE = 0340;

    private Computer computer;

    @Before
    public void setUp() {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
    }

    /**
     * CLR operation tests.
     */
    @Test
    public void testClrInstructionExecute() {
        computer.addMemory(new RandomAccessMemory("TestRam", 01000, 3));
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                ClrOpcode.OPCODE, // CLR R0
                (short) (ClrOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // CLRB R0
                ClrOpcode.OPCODE | 010, // CLR (R0)
                (short) (ClrOpcode.OPCODE | 010 | Opcode.BYTE_OPERATION_FLAG), // CLRB (R0)
                ClrOpcode.OPCODE | 020, // CLR (R0)+
                (short) (ClrOpcode.OPCODE | 020 | Opcode.BYTE_OPERATION_FLAG), // CLRB (R0)+
                ClrOpcode.OPCODE | 030, // CLR @(R0)+
                (short) (ClrOpcode.OPCODE | 030 | Opcode.BYTE_OPERATION_FLAG), // CLRB @(R0)+
                ClrOpcode.OPCODE | 040, // CLR -(R0)
                (short) (ClrOpcode.OPCODE | 040 | Opcode.BYTE_OPERATION_FLAG), // CLRB -(R0)
                ClrOpcode.OPCODE | 050, // CLR -@(R0)
                (short) (ClrOpcode.OPCODE | 050 | Opcode.BYTE_OPERATION_FLAG), // CLRB -@(R0)
                ClrOpcode.OPCODE | 060, // CLR X(R0)
                -2, // X
                (short) (ClrOpcode.OPCODE | 060 | Opcode.BYTE_OPERATION_FLAG), // CLRB X(R0)
                -1, // X
                ClrOpcode.OPCODE | 070, // CLR @X(R0)
                -2, // X
                (short) (ClrOpcode.OPCODE | 070 | Opcode.BYTE_OPERATION_FLAG), // CLRB @X(R0)
                -1, // X
                }));
        computer.reset();
        // CLR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // CLRB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0177400, computer.getCpu().readRegister(false, Cpu.R0));
        // CLR (R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        assertEquals(0177777, computer.readMemory(false, 01000));
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        // CLRB (R0)
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01000));
        // CLR (R0)+
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100012, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // CLRB (R0)+
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100014, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01002));
        assertEquals(01003, computer.getCpu().readRegister(false, Cpu.R0));
        // CLR @(R0)+
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 01002);
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100016, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01002));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // CLRB @(R0)+
        computer.getCpu().writeMemory(false, 01002, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100020, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01000));
        assertEquals(01004, computer.getCpu().readRegister(false, Cpu.R0));
        // CLR -(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01004);
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100022, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.readMemory(false, 01002));
        // CLRB -(R0)
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100024, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01000));
        assertEquals(01001, computer.getCpu().readRegister(false, Cpu.R0));
        // CLR @-(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01004);
        computer.getCpu().writeMemory(false, 01002, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100026, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // CLRB @-(R0)
        computer.getCpu().writeMemory(false, 01000, 01002);
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100030, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01002));
        assertEquals(01000, computer.getCpu().readRegister(false, Cpu.R0));
        // CLR X(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01002);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100034, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // CLRB X(R0)
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100040, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // CLR @X(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01002);
        computer.getCpu().writeMemory(false, 01000, 01004);
        computer.getCpu().writeMemory(false, 01004, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100044, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01004));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // CLRB @X(R0)
        computer.getCpu().writeMemory(false, 01004, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100050, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01004));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * COM operation tests.
     */
    @Test
    public void testComInstructionExecute() {
        computer.addMemory(new RandomAccessMemory("TestRam", 01000, 3));
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                ComOpcode.OPCODE, // COM R0
                (short) (ComOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // COMB R0
                ComOpcode.OPCODE | 010, // COM (R0)
                (short) (ComOpcode.OPCODE | 010 | Opcode.BYTE_OPERATION_FLAG), // COMB (R0)
                ComOpcode.OPCODE | 020, // COM (R0)+
                (short) (ComOpcode.OPCODE | 020 | Opcode.BYTE_OPERATION_FLAG), // COMB (R0)+
                ComOpcode.OPCODE | 030, // COM @(R0)+
                (short) (ComOpcode.OPCODE | 030 | Opcode.BYTE_OPERATION_FLAG), // COMB @(R0)+
                ComOpcode.OPCODE | 040, // COM -(R0)
                (short) (ComOpcode.OPCODE | 040 | Opcode.BYTE_OPERATION_FLAG), // COMB -(R0)
                ComOpcode.OPCODE | 050, // COM -@(R0)
                (short) (ComOpcode.OPCODE | 050 | Opcode.BYTE_OPERATION_FLAG), // COMB -@(R0)
                ComOpcode.OPCODE | 060, // COM X(R0)
                -2, // X
                (short) (ComOpcode.OPCODE | 060 | Opcode.BYTE_OPERATION_FLAG), // COMB X(R0)
                -1, // X
                ComOpcode.OPCODE | 070, // COM @X(R0)
                -2, // X
                (short) (ComOpcode.OPCODE | 070 | Opcode.BYTE_OPERATION_FLAG), // COMB @X(R0)
                -1, // X
                }));
        computer.reset();
        // Ones -> Zeroes
        // COM R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0177400, computer.getCpu().readRegister(false, Cpu.R0));
        // COM (R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        assertEquals(0177777, computer.readMemory(false, 01000));
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        // COMB (R0)
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01000));
        // COM (R0)+
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100012, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB (R0)+
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100014, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01002));
        assertEquals(01003, computer.getCpu().readRegister(false, Cpu.R0));
        // COM @(R0)+
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 01002);
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100016, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01002));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB @(R0)+
        computer.getCpu().writeMemory(false, 01002, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100020, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01000));
        assertEquals(01004, computer.getCpu().readRegister(false, Cpu.R0));
        // COM -(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01004);
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100022, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0, computer.readMemory(false, 01002));
        // COMB -(R0)
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100024, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01000));
        assertEquals(01001, computer.getCpu().readRegister(false, Cpu.R0));
        // COM @-(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01004);
        computer.getCpu().writeMemory(false, 01002, 01000);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100026, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB @-(R0)
        computer.getCpu().writeMemory(false, 01000, 01002);
        computer.getCpu().writeMemory(false, 01002, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100030, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01002));
        assertEquals(01000, computer.getCpu().readRegister(false, Cpu.R0));
        // COM X(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01002);
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100034, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB X(R0)
        computer.getCpu().writeMemory(false, 01000, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100040, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COM @X(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01002);
        computer.getCpu().writeMemory(false, 01000, 01004);
        computer.getCpu().writeMemory(false, 01004, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100044, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0, computer.readMemory(false, 01004));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB @X(R0)
        computer.getCpu().writeMemory(false, 01004, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100050, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01004));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));

        // Zeroes -> Ones
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // COM R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0377, computer.getCpu().readRegister(false, Cpu.R0));
        // COM (R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177777, computer.readMemory(false, 01000));
        // COMB (R0)
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0377, computer.readMemory(false, 01000));

        // COM (R0)+
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100012, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB (R0)+
        computer.getCpu().writeMemory(false, 01002, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100014, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01002));
        assertEquals(01003, computer.getCpu().readRegister(false, Cpu.R0));
        // COM @(R0)+
        computer.getCpu().writeRegister(false, Cpu.R0, 01000);
        computer.getCpu().writeMemory(false, 01000, 01002);
        computer.getCpu().writeMemory(false, 01002, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100016, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.readMemory(false, 01002));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB @(R0)+
        computer.getCpu().writeMemory(false, 01002, 01000);
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100020, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01000));
        assertEquals(01004, computer.getCpu().readRegister(false, Cpu.R0));
        // COM -(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01004);
        computer.getCpu().writeMemory(false, 01002, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100022, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        assertEquals(0177777, computer.readMemory(false, 01002));
        // COMB -(R0)
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100024, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01000));
        assertEquals(01001, computer.getCpu().readRegister(false, Cpu.R0));
        // COM @-(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01004);
        computer.getCpu().writeMemory(false, 01002, 01000);
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100026, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB @-(R0)
        computer.getCpu().writeMemory(false, 01000, 01002);
        computer.getCpu().writeMemory(false, 01002, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100030, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01002));
        assertEquals(01000, computer.getCpu().readRegister(false, Cpu.R0));
        // COM X(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01002);
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100034, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB X(R0)
        computer.getCpu().writeMemory(false, 01000, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100040, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177400, computer.readMemory(false, 01000));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COM @X(R0)
        computer.getCpu().writeRegister(false, Cpu.R0, 01002);
        computer.getCpu().writeMemory(false, 01000, 01004);
        computer.getCpu().writeMemory(false, 01004, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100044, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0177777, computer.readMemory(false, 01004));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
        // COMB @X(R0)
        computer.getCpu().writeMemory(false, 01004, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100050, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.readMemory(false, 01004));
        assertEquals(01002, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * ADC operation tests.
     */
    @Test
    public void testAdcInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                AdcOpcode.OPCODE, // ADC R0
                (short) (AdcOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // ADCB R0
                }));
        computer.reset();
        // Add with no carry to 0
        // ADC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // ADCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Add carry to 0
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ADC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        // ADCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));

        // Add carry to least negative value
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ADC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // ADCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Add carry to most positive value
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ADC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 077777);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.R0));
        // ADCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0200, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * ASL operation tests.
     */
    @Test
    public void testAslInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                AslOpcode.OPCODE, // ASL R0
                (short) (AslOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // ASLB R0
                }));
        computer.reset();
        // Shift of 0
        // ASL R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // ASLB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of 1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ASL R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE,
                computer.getCpu().getPswState());
        assertEquals(2, computer.getCpu().readRegister(false, Cpu.R0));
        // ASLB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE,
                computer.getCpu().getPswState());
        assertEquals(2, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of -1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ASL R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177776, computer.getCpu().readRegister(false, Cpu.R0));
        // ASLB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0376, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * ASR operation tests.
     */
    @Test
    public void testAsrInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                AsrOpcode.OPCODE, // ASR R0
                (short) (AsrOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // ASRB R0
                }));
        computer.reset();
        // Shift of 0
        // ASR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // ASRB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of 1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ASR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z | Cpu.PSW_FLAG_V,
                computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // ASRB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z | Cpu.PSW_FLAG_V,
                computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of -1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ASR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R0));
        // ASRB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0377, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * DEC operation tests.
     */
    @Test
    public void testDecInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                DecOpcode.OPCODE, // DEC R0
                (short) (DecOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // DECB R0
                }));
        computer.reset();
        // Check decrement of 1
        // DEC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // DECB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Check overflow
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // DEC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(077777, computer.getCpu().readRegister(false, Cpu.R0));
        // DECB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0200);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0177, computer.getCpu().readRegister(false, Cpu.R0));

        // Check underrun
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // DEC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R0));
        // DECB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0377, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * INC operation tests.
     */
    @Test
    public void testIncInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                IncOpcode.OPCODE, // INC R0
                (short) (IncOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // INCB R0
                }));
        computer.reset();
        // Check increment of 0
        // INC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        // INCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));

        // Check overflow
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // INC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 077777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.R0));
        // INCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0200, computer.getCpu().readRegister(false, Cpu.R0));

        // Check overrun
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // INC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // INCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * MARK operation tests.
     */
    @Test
    public void testMarkInstructionExecute() {
        computer.addMemory(new RandomAccessMemory("TestRam", 0100000, new short[] {
                MarkOpcode.OPCODE + 1, // MARK 1
                0, // skipped parameter
                (short) 0123456, // R5 to restore
                }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.R5, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0123456, computer.getCpu().readRegister(false, Cpu.R5));
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.SP));
        assertEquals(PSW_STATE, computer.getCpu().getPswState());
    }

    /**
     * MFPS operation tests.
     */
    @Test
    public void testMfpsInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                (short) MfpsOpcode.OPCODE // MFPS R0
                }));
        computer.reset();
        // MFPS R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(((byte) PSW_STATE) & 0177777, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * MTPS operation tests.
     */
    @Test
    public void testMtpsInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                (short) MtpsOpcode.OPCODE // MTPS R0
                }));
        computer.reset();
        // MTPS R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377 & ~Cpu.PSW_FLAG_T, computer.getCpu().getPswState());
    }

    /**
     * NEG operation tests.
     */
    @Test
    public void testNegInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                NegOpcode.OPCODE, // NEG R0
                (short) (NegOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // NEGB R0
                }));
        computer.reset();
        // Check negation of 1
        // NEG R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C, computer.getCpu().getPswState());
        // NEGB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C, computer.getCpu().getPswState());
        assertEquals(0377, computer.getCpu().readRegister(false, Cpu.R0));

        // Check negation of 0
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // NEG R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // NEGB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Check negation of -1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // NEG R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));
        // NEGB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C, computer.getCpu().getPswState());
        assertEquals(1, computer.getCpu().readRegister(false, Cpu.R0));

        // Check negation with V flag set
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // NEG R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_V,
                computer.getCpu().getPswState());
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.R0));
        // NEGB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0200);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_V,
                computer.getCpu().getPswState());
        assertEquals(0200, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * ROL operation tests.
     */
    @Test
    public void testRolInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
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

    /**
     * ROR operation tests.
     */
    @Test
    public void testRorInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                RorOpcode.OPCODE, // ROR R0
                (short) (RorOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // RORB R0
                }));
        computer.reset();
        // Shift of 1
        // ROR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z | Cpu.PSW_FLAG_V,
                computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // RORB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 1);
        computer.getCpu().clearPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_Z | Cpu.PSW_FLAG_V,
                computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of 0 with carry in
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ROR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.R0));
        // RORB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0200, computer.getCpu().readRegister(false, Cpu.R0));

        // Shift of -1
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // ROR R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0177777);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(077777, computer.getCpu().readRegister(false, Cpu.R0));
        // RORB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().clearPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0177, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * SBC operation tests.
     */
    @Test
    public void testSbcInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                SbcOpcode.OPCODE, // SBC R0
                (short) (SbcOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // SBCB R0
                }));
        computer.reset();
        // Subtract no carry from 0
        // SBC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));
        // SBCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0, computer.getCpu().readRegister(false, Cpu.R0));

        // Subtract carry from 0
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // SBC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0177777, computer.getCpu().readRegister(false, Cpu.R0));
        // SBCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_C | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        assertEquals(0377, computer.getCpu().readRegister(false, Cpu.R0));

        // Subtract carry from most negative value
        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // SBC R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(077777, computer.getCpu().readRegister(false, Cpu.R0));
        // SBCB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0200);
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_V, computer.getCpu().getPswState());
        assertEquals(0177, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * SWAB operation tests.
     */
    @Test
    public void testSwabInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                SwabOpcode.OPCODE, // SWAB R0
                }));
        computer.reset();
        // SWAB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340 | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        assertEquals(0177400, computer.getCpu().readRegister(false, Cpu.R0));
    }

    /**
     * SXT operation tests.
     */
    @Test
    public void testSxtInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
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

    /**
     * TST operation tests.
     */
    @Test
    public void testTstInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory("TestRom", 0100000, new short[] {
                TstOpcode.OPCODE, // TST R0
                (short) (TstOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // TSTB R0
                }));
        computer.reset();
        // TST R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // TSTB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());

        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // TST R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // TSTB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0200);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
    }

}
