/*
 * Created: 12.04.2012
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
 * @author avm
 *
 */
public class BranchOpcodesTest {

    private Computer computer;

    @Before
    public void setUp() throws Exception {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
    }

    /**
     * BR operation tests.
     */
    @Test
    public void testBrInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                BrOpcode.OPCODE | 1,             // 0100000: BR 0100004
                ConditionCodeOpcodes.OPCODE_NOP, // 0100002: NOP
                BrOpcode.OPCODE | (-2 & 0377)    // 0100004: BR 0100002
        }));
        computer.reset();
        // BR 0100004
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // BR 0100002
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BNE operation tests.
     */
    @Test
    public void testBneInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                BneOpcode.OPCODE | (-2 & 0377),   // 0100000: BNE 077776
                BneOpcode.OPCODE | (-2 & 0377)    // 0100002: BNE 0100000
        }));
        computer.reset();
        // BNE 077776 - not branching
        computer.getCpu().setPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BNE 0100000 - branching
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BEQ operation tests.
     */
    @Test
    public void testBeqInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                BeqOpcode.OPCODE | (-2 & 0377),   // 0100000: BEQ 077776
                BeqOpcode.OPCODE | (-2 & 0377)    // 0100002: BEQ 0100000
        }));
        computer.reset();
        // BEQ 077776 - not branching
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BEQ 0100000 - branching
        computer.getCpu().setPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BPL operation tests.
     */
    @Test
    public void testBplInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BplOpcode.OPCODE | (-2 & 0377)),   // 0100000: BPL 077776
                (short) (BplOpcode.OPCODE | (-2 & 0377))    // 0100002: BPL 0100000
        }));
        computer.reset();
        // BPL 077776 - not branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BPL 0100000 - branching
        computer.getCpu().clearPswFlagN();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BMI operation tests.
     */
    @Test
    public void testBmiInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BmiOpcode.OPCODE | (-2 & 0377)),   // 0100000: BMI 077776
                (short) (BmiOpcode.OPCODE | (-2 & 0377))    // 0100002: BMI 0100000
        }));
        computer.reset();
        // BMI 077776 - not branching
        computer.getCpu().clearPswFlagN();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BMI 0100000 - branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BVC operation tests.
     */
    @Test
    public void testBvcInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BvcOpcode.OPCODE | (-2 & 0377)),   // 0100000: BVC 077776
                (short) (BvcOpcode.OPCODE | (-2 & 0377))    // 0100002: BVC 0100000
        }));
        computer.reset();
        // BVC 077776 - not branching
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BVC 0100000 - branching
        computer.getCpu().clearPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BVS operation tests.
     */
    @Test
    public void testBvsInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BvsOpcode.OPCODE | (-2 & 0377)),   // 0100000: BVS 077776
                (short) (BvsOpcode.OPCODE | (-2 & 0377))    // 0100002: BVS 0100000
        }));
        computer.reset();
        // BVS 077776 - not branching
        computer.getCpu().clearPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BVS 0100000 - branching
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BCC operation tests.
     */
    @Test
    public void testBccInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BccOpcode.OPCODE | (-2 & 0377)),   // 0100000: BCC 077776
                (short) (BccOpcode.OPCODE | (-2 & 0377))    // 0100002: BCC 0100000
        }));
        computer.reset();
        // BCC 077776 - not branching
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BCC 0100000 - branching
        computer.getCpu().clearPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BCS operation tests.
     */
    @Test
    public void testBcsInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BcsOpcode.OPCODE | (-2 & 0377)),   // 0100000: BCS 077776
                (short) (BcsOpcode.OPCODE | (-2 & 0377))    // 0100002: BCS 0100000
        }));
        computer.reset();
        // BCS 077776 - not branching
        computer.getCpu().clearPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // BCS 0100000 - branching
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BGE operation tests.
     */
    @Test
    public void testBgeInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BgeOpcode.OPCODE | (-2 & 0377)),  // 0100000: BGE 077776
                (short) (BgeOpcode.OPCODE | (-3 & 0377)),  // 0100002: BGE 077776
                (short) (BgeOpcode.OPCODE | (-3 & 0377))   // 0100004: BGE 0100000
        }));
        computer.reset();
        // set N, clear V - not branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().clearPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // clear N, set V - not branching
        computer.getCpu().clearPswFlagN();
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, set V - branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BGT operation tests.
     */
    @Test
    public void testBgtInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BgtOpcode.OPCODE | (-2 & 0377)),  // 0100000: BGT 077776
                (short) (BgtOpcode.OPCODE | (-3 & 0377)),  // 0100002: BGT 077776
                (short) (BgtOpcode.OPCODE | (-3 & 0377)),  // 0100004: BGT 0100000
        }));
        computer.reset();
        // clear N, clear V, set Z - not branching
        computer.getCpu().clearPswFlagN();
        computer.getCpu().clearPswFlagV();
        computer.getCpu().setPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, set V, set Z - not branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, set V, clear Z - branching
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BLE operation tests.
     */
    @Test
    public void testBleInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BleOpcode.OPCODE | (-2 & 0377)),  // 0100000: BLE 077776
                (short) (BleOpcode.OPCODE | (-3 & 0377)),  // 0100002: BLE 077776
                (short) (BleOpcode.OPCODE | (-3 & 0377))   // 0100004: BLE 0100000
        }));
        computer.reset();
        // clear N, clear V, clear Z - not branching
        computer.getCpu().clearPswFlagN();
        computer.getCpu().clearPswFlagV();
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, set V, clear Z - not branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, set V, set Z - branching
        computer.getCpu().setPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BLT operation tests.
     */
    @Test
    public void testBltInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BltOpcode.OPCODE | (-2 & 0377)),  // 0100000: BLT 077776
                (short) (BltOpcode.OPCODE | (-3 & 0377)),  // 0100002: BLT 077776
                (short) (BltOpcode.OPCODE | (-3 & 0377)),  // 0100004: BLT 0100000
        }));
        computer.reset();
        // clear N, clear V - not branching
        computer.getCpu().clearPswFlagN();
        computer.getCpu().clearPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, set V - not branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().setPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // set N, clear V - branching
        computer.getCpu().setPswFlagN();
        computer.getCpu().clearPswFlagV();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BHI operation tests.
     */
    @Test
    public void testBhiInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BhiOpcode.OPCODE | (-2 & 0377)),  // 0100000: BHI 077776
                (short) (BhiOpcode.OPCODE | (-3 & 0377)),  // 0100002: BHI 077776
                (short) (BhiOpcode.OPCODE | (-4 & 0377)),  // 0100004: BHI 077776
                (short) (BhiOpcode.OPCODE | (-4 & 0377))   // 0100006: BHI 0100000
        }));
        computer.reset();
        // set C, clear Z - not branching
        computer.getCpu().setPswFlagC();
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        // clear C, set Z - not branching
        computer.getCpu().clearPswFlagC();
        computer.getCpu().setPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        // set C, set Z - not branching
        computer.getCpu().setPswFlagC();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100006, computer.getCpu().readRegister(false, Cpu.PC));
        // clear C, clear Z - branching
        computer.getCpu().clearPswFlagC();
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * BLOS operation tests.
     */
    @Test
    public void testBlosInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                (short) (BlosOpcode.OPCODE | (-2 & 0377)),  // 0100000: BLOS 077776
                (short) (BlosOpcode.OPCODE | (-3 & 0377)),  // 0100002: BLOS 077776
                (short) (BlosOpcode.OPCODE | (-4 & 0377)),  // 0100004: BLOS 077776
                (short) (BlosOpcode.OPCODE | (-6 & 0377))   // 0100006: BLOS 077776
        }));
        computer.reset();
        // set C, clear Z - branching
        computer.getCpu().setPswFlagC();
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().executeSingleInstruction();
        assertEquals(077776, computer.getCpu().readRegister(false, Cpu.PC));
        // clear C, set Z - branching
        computer.getCpu().clearPswFlagC();
        computer.getCpu().setPswFlagZ();
        computer.getCpu().writeRegister(false, Cpu.PC, 0100002);
        computer.getCpu().executeSingleInstruction();
        assertEquals(077776, computer.getCpu().readRegister(false, Cpu.PC));
        // set C, set Z - branching
        computer.getCpu().setPswFlagC();
        computer.getCpu().writeRegister(false, Cpu.PC, 0100004);
        computer.getCpu().executeSingleInstruction();
        assertEquals(077776, computer.getCpu().readRegister(false, Cpu.PC));
        // clear C, clear Z - not branching
        computer.getCpu().clearPswFlagC();
        computer.getCpu().clearPswFlagZ();
        computer.getCpu().writeRegister(false, Cpu.PC, 0100006);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
    }

    /**
     * SOB operation tests.
     */
    @Test
    public void testSobInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                SobOpcode.OPCODE | 1             // 0100000: SOB R0, 0100000
        }));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.R0, 3);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100000, computer.getCpu().readRegister(false, Cpu.PC));
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
    }

}
