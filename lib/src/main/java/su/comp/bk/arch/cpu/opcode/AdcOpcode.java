/*
 * Created: 09.04.2012
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

/**
 * Add carry operation.
 */
public class AdcOpcode extends SingleOperandOpcode {

    public final static int OPCODE = 05500;

    public AdcOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    protected void executeSingleOperand(boolean isByteMode, int singleOperandRegister,
            AddressingMode singleOperandAddressingMode) {
        Cpu cpu = getCpu();
        int data = singleOperandAddressingMode.readAddressedValue(isByteMode,
                singleOperandRegister);
        if (data != Computer.BUS_ERROR) {
            boolean carryFlag = cpu.isPswFlagSet(Cpu.PSW_FLAG_C);
            if (carryFlag) {
                data += 1;
            }
            cpu.setPswFlagZ(isByteMode, data);
            cpu.setPswFlagN(isByteMode, data);
            cpu.setPswFlagV(carryFlag && data == (isByteMode ? Byte.MIN_VALUE & 0377
                    : Short.MIN_VALUE & 0177777));
            cpu.setPswFlagC(carryFlag && cpu.isPswFlagSet(Cpu.PSW_FLAG_Z));
            singleOperandAddressingMode.writeAddressedValue(isByteMode,
                    singleOperandRegister, data);
        }
    }

}
