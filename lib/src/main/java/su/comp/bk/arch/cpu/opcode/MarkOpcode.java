/*
 * Created: 05.04.2012
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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.addressing.AddressingMode;
import su.comp.bk.arch.cpu.addressing.AutoincrementAddressingMode;

/**
 * MARK operation.
 */
public class MarkOpcode extends BaseOpcode {

    public final static int OPCODE = 06400;

    private static final int EXECUTION_TIME = 36;

    public MarkOpcode(Cpu cpu) {
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
        Cpu cpu = getCpu();
        int n = getInstruction() & 077;
        cpu.writeRegister(false, Cpu.SP, cpu.readRegister(false, Cpu.PC) + (n << 1));
        cpu.writeRegister(false, Cpu.PC, cpu.readRegister(false, Cpu.R5));
        AddressingMode autoincrementMode = cpu.getAddressingMode(AutoincrementAddressingMode.CODE);
        autoincrementMode.preAddressingAction(false, Cpu.SP);
        int value = cpu.pop();
        if (value != Computer.BUS_ERROR) {
            cpu.writeRegister(false, Cpu.R5, value);
        }
    }

}
