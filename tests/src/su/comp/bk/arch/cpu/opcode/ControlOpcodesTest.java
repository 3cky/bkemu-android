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

import static org.junit.Assert.*;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.anyObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.easymock.PowerMock.*;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.os.Bundle;
import android.util.Log;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * Control opcodes (RESET/WAIT/HALT) tests.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(value=Log.class)
public class ControlOpcodesTest {

    private Computer computer;

    @Before
    public void setUp() throws Exception {
        mockStatic(Log.class);
        expect(Log.d(anyObject(String.class), anyObject(String.class))).andReturn(0).anyTimes();
        replay(Log.class);
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    @Test
    public void testResetInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                ResetOpcode.OPCODE
        }));
        computer.reset();
        computer.getCpu().setPswState((short) 0377);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0340, computer.getCpu().getPswState());
    }

    @Test
    public void testWaitInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                WaitOpcode.OPCODE
        }));
        computer.reset();
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertTrue(computer.getCpu().isInterruptWaitMode());
    }

    @Test
    public void testHaltInstructionExecute() {
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                HaltOpcode.OPCODE,                   // 0100000: HALT
                (short) 0100010,                     // 0100002: <vector - PC>
                0377,                                // 0100004: <vector - PSW>
                ConditionCodeOpcodes.OPCODE_NOP,     // 0100006: NOP
                ConditionCodeOpcodes.OPCODE_NOP      // 0100010: NOP
        }));

        // Halt PC/PSW store registers mock device
        Device haltStateRegisters = new Device() {
            int regHaltPc;
            int regHaltPsw;
            @Override
            public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
                if (address == Cpu.REG_HALT_PC) {
                    regHaltPc = value;
                } else {
                    regHaltPsw = value;
                }
                return true;
            }
            @Override
            public int read(long cpuTime, int address) {
                if (address == Cpu.REG_HALT_PC) {
                    return regHaltPc;
                }
                return regHaltPsw;
            }
            @Override
            public int[] getAddresses() {
                return new int[] { Cpu.REG_HALT_PC, Cpu.REG_HALT_PSW };
            }
            @Override
            public void init(long cpuTime) {
            }
            @Override
            public void saveState(Bundle outState) {
            }
            @Override
            public void restoreState(Bundle inState) {
            }
        };

        Device haltBitRegister = new Device() {
            int haltBitValue;
            @Override
            public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
                haltBitValue = value;
                return true;
            }
            @Override
            public int read(long cpuTime, int address) {
                return haltBitValue;
            }
            @Override
            public int[] getAddresses() {
                return new int[] { Cpu.REG_SEL1 };
            }
            @Override
            public void init(long cpuTime) {
            }
            @Override
            public void saveState(Bundle outState) {
            }
            @Override
            public void restoreState(Bundle inState) {
            }
        };

        computer.addDevice(haltStateRegisters);
        computer.addDevice(haltBitRegister);

        computer.reset();
        assertTrue((computer.getCpu().readMemory(false, Cpu.REG_SEL1) & 014) == 0);
        // HALT
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100010, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(0377, computer.getCpu().getPswState());
        assertTrue((computer.getCpu().readMemory(false, Cpu.REG_SEL1) & 004) != 0);
        assertTrue((computer.getCpu().readMemory(false, Cpu.REG_SEL1) & 010) != 0);
        assertEquals(0100002, computer.getCpu().readMemory(false, Cpu.REG_HALT_PC));
        assertEquals(0340, computer.getCpu().readMemory(false, Cpu.REG_HALT_PSW));
        assertTrue(computer.getCpu().isHaltMode());
    }

}
