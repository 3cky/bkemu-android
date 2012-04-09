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
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * CLR operation tests.
 */
public class ClrOpcodeTest {

    @Test
    public void testClrInstructionExecute() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addMemory(new RandomAccessMemory(01000, 3));
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                ClrOpcode.OPCODE_CLR, // CLR R0
                (short) (ClrOpcode.OPCODE_CLR | Opcode.BYTE_OPERATION_FLAG), // CLRB R0
                ClrOpcode.OPCODE_CLR | 010, // CLR (R0)
                (short) (ClrOpcode.OPCODE_CLR | 010 | Opcode.BYTE_OPERATION_FLAG), // CLRB (R0)
                ClrOpcode.OPCODE_CLR | 020, // CLR (R0)+
                (short) (ClrOpcode.OPCODE_CLR | 020 | Opcode.BYTE_OPERATION_FLAG), // CLRB (R0)+
                ClrOpcode.OPCODE_CLR | 030, // CLR @(R0)+
                (short) (ClrOpcode.OPCODE_CLR | 030 | Opcode.BYTE_OPERATION_FLAG), // CLRB @(R0)+
                ClrOpcode.OPCODE_CLR | 040, // CLR -(R0)
                (short) (ClrOpcode.OPCODE_CLR | 040 | Opcode.BYTE_OPERATION_FLAG), // CLRB -(R0)
                ClrOpcode.OPCODE_CLR | 050, // CLR -@(R0)
                (short) (ClrOpcode.OPCODE_CLR | 050 | Opcode.BYTE_OPERATION_FLAG), // CLRB -@(R0)
                ClrOpcode.OPCODE_CLR | 060, // CLR X(R0)
                -2, // X
                (short) (ClrOpcode.OPCODE_CLR | 060 | Opcode.BYTE_OPERATION_FLAG), // CLRB X(R0)
                -1, // X
                ClrOpcode.OPCODE_CLR | 070, // CLR @X(R0)
                -2, // X
                (short) (ClrOpcode.OPCODE_CLR | 070 | Opcode.BYTE_OPERATION_FLAG), // CLRB @X(R0)
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

}
