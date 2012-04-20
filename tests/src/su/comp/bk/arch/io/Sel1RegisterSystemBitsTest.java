/*
 * Created: 20.04.2012
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
package su.comp.bk.arch.io;

import static org.junit.Assert.*;

import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;

/**
 * SEL1 register system bits tests.
 */
public class Sel1RegisterSystemBitsTest {

    @Test
    public void testSel1RegisterSystemBits() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.reset();
        // High byte defines startup address
        assertEquals(0100000, computer.readMemory(false, Cpu.REG_SEL1) & 0177400);
        // Bit 2 is set on write / clear on read
        assertTrue((computer.readMemory(false, Cpu.REG_SEL1) & 004) == 0);
        assertTrue(computer.writeMemory(false, Cpu.REG_SEL1, 0));
        assertTrue((computer.readMemory(false, Cpu.REG_SEL1) & 004) != 0);
        assertTrue((computer.readMemory(false, Cpu.REG_SEL1) & 004) == 0);
    }

}
