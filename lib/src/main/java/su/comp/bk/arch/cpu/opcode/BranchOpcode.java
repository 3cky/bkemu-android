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
 * Branch (BR, BNE, BEQ, etc) opcodes base class.
 */
public abstract class BranchOpcode extends BaseOpcode {

    // Branch instructions execution time (in CPU ticks)
    private static final int EXECUTION_TIME = 16;

    public BranchOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getExecutionTime() {
        return EXECUTION_TIME;
    }

    @Override
    public void execute() {
        Cpu cpu = getCpu();
        if (isBranchCondition(cpu.getPswState())) {
            int pc = cpu.readRegister(false, Cpu.PC);
            int offset = (byte) getInstruction() * 2;
            cpu.writeRegister(false, Cpu.PC, pc + offset);
        }
    }

    /**
     * Check given PSW value for branch condition.
     * @param psw PSW value to check
     * @return evaluated branch condition boolean value
     */
    protected abstract boolean isBranchCondition(int psw);

}
