/*
 * Created: 15.04.2012
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
 * Add operation.
 */
public class AddOpcode extends DoubleOperandOpcode {

    public final static int OPCODE = 060000;

    public AddOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    public void execute() {
        AddressingMode srcMode = getSrcOperandAddressingMode();
        int srcRegister = getSrcOperandRegister();
        // Read source value
        srcMode.preAddressingAction(false, srcRegister);
        int srcValue = srcMode.readAddressedValue(false, srcRegister);
        srcMode.postAddressingAction(false, srcRegister);
        if (srcValue != Computer.BUS_ERROR) {
            // Read destination value
            AddressingMode destMode = getDestOperandAddressingMode();
            int destRegister = getDestOperandRegister();
            destMode.preAddressingAction(false, destRegister);
            int destValue = destMode.readAddressedValue(false, destRegister);
            if (destValue != Computer.BUS_ERROR) {
                int resultValue = destValue + srcValue;
                // Set flags
                Cpu cpu = getCpu();
                cpu.setPswFlagN(false, resultValue);
                cpu.setPswFlagZ(false, resultValue);
                cpu.setPswFlagV((((~srcValue ^ destValue) & (srcValue ^ resultValue))
                        & 0100000) != 0);
                cpu.setPswFlagC((resultValue & ~0177777) != 0);
                // Write result to destination
                destMode.writeAddressedValue(false, destRegister, resultValue);
                destMode.postAddressingAction(false, destRegister);
            }
        }
    }
}

