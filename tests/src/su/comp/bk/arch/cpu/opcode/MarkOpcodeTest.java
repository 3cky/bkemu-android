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

/**
 * MARK operation tests.
 */
public class MarkOpcodeTest {

    private static final int PSW_STATE = 0340;

    @Test
    public void testMarkInstructionExecute() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100004));
        computer.addMemory(new RandomAccessMemory(0100000, new short[] {
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

}
