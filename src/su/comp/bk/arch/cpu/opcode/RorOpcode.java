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

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.addressing.AddressingMode;

/**
 * Rotate right operation.
 */
public class RorOpcode extends SingleOperandOpcode {

    public final static short OPCODE = 06000;

    public RorOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    protected void executeSingleOperand(boolean isByteMode, int singleOperandRegister,
            AddressingMode singleOperandAddressingMode) {
        Cpu cpu = getCpu();
        int data = singleOperandAddressingMode.readAddressedValue(isByteMode,
                singleOperandRegister);
        if (data != Computer.BUS_ERROR) {
            boolean carryFlag = (data & 1) != 0;
            data >>= 1;
            if (cpu.isPswFlagSet(Cpu.PSW_FLAG_C)) {
                data |= isByteMode ? 0200 : 0100000;
            }
            cpu.setPswFlagZ(isByteMode, data);
            cpu.setPswFlagN(isByteMode, data);
            cpu.setPswFlagC(carryFlag);
            cpu.setPswFlagV(carryFlag != cpu.isPswFlagSet(Cpu.PSW_FLAG_N));
            singleOperandAddressingMode.writeAddressedValue(isByteMode,
                    singleOperandRegister, data);
        }
    }

}
