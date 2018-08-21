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

import su.comp.bk.arch.cpu.Cpu;

/**
 * Halt operation.
 */
public class HaltOpcode extends BaseOpcode {

    public final static int OPCODE = 0;

    private static final int EXECUTION_TIME = 144;

    public HaltOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    public int getExecutionTime() {
        return EXECUTION_TIME;
    }

    @Override
    public void execute() {
        getCpu().enterHaltMode();
    }

}
