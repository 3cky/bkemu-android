/*
 * Created: 01.04.2012
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

import su.comp.bk.arch.cpu.Cpu;

/**
 * Operation code base class.
 */
public abstract class BaseOpcode implements Opcode {

    private final Cpu cpu;

    private int instruction;

    public BaseOpcode(Cpu cpu) {
        this.cpu = cpu;
    }

    /**
     * Check is this instruction uses byte-mode operation.
     * @return <code>true</code> if this instruction uses byte-mode operation,
     * <code>false</code> if uses word-mode.
     */
    protected boolean isByteModeOperation() {
        return (instruction & BYTE_OPERATION_FLAG) != 0;
    }

    protected Cpu getCpu() {
        return cpu;
    }

    protected int getInstruction() {
        return instruction;
    }

    @Override
    public void decode(int instructionToDecode) {
        this.instruction = instructionToDecode;
    }

}
