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

import su.comp.bk.arch.cpu.Cpu;

/**
 * Branch if greater than (Z|(N^V) = 0) opcode.
 */
public class BgtOpcode extends BranchOpcode {

    public final static int OPCODE = 03000;

    public BgtOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    protected boolean isBranchCondition(int psw) {
        boolean flagN = (psw & Cpu.PSW_FLAG_N) != 0;
        boolean flagV = (psw & Cpu.PSW_FLAG_V) != 0;
        boolean flagZ = (psw & Cpu.PSW_FLAG_Z) != 0;
        return !(flagZ || (flagN ^ flagV));
    }

}
