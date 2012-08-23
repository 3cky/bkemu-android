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

import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.addressing.AddressingMode;

/**
 * Sign extension operation.
 */
public class SxtOpcode extends SingleOperandOpcode {

    public final static int OPCODE = 06700;

    public SxtOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    protected void executeSingleOperand(boolean isByteMode, int operandRegister,
            AddressingMode operandAddressingMode) {
        Cpu cpu = getCpu();
        boolean negativeFlag = cpu.isPswFlagSet(Cpu.PSW_FLAG_N);
        cpu.setPswFlagZ(!negativeFlag);
        cpu.clearPswFlagV();
        int data = negativeFlag ? 0177777 : 0;
        operandAddressingMode.writeAddressedValue(false, operandRegister, data);
    }

}
