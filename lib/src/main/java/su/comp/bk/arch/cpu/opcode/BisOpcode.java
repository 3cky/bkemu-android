/*
 * Created: 15.04.2012
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
 * Bit set operation.
 */
public class BisOpcode extends DoubleOperandOpcode {

    public final static int OPCODE = 050000;

    public BisOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    public void execute() {
        boolean isByteMode = isByteModeOperation();
        AddressingMode srcMode = getSrcOperandAddressingMode();
        int srcRegister = getSrcOperandRegister();
        // Read source value
        srcMode.preAddressingAction(isByteMode, srcRegister);
        int srcValue = srcMode.readAddressedValue(isByteMode, srcRegister);
        srcMode.postAddressingAction(isByteMode, srcRegister);
        if (srcValue != Computer.BUS_ERROR) {
            // Read destination value
            AddressingMode destMode = getDestOperandAddressingMode();
            int destRegister = getDestOperandRegister();
            destMode.preAddressingAction(isByteMode, destRegister);
            int destValue = destMode.readAddressedValue(isByteMode, destRegister);
            if (destValue != Computer.BUS_ERROR) {
                int resultValue = srcValue | destValue;
                // Set flags
                Cpu cpu = getCpu();
                cpu.setPswFlagN(isByteMode, resultValue);
                cpu.setPswFlagZ(isByteMode, resultValue);
                cpu.clearPswFlagV();
                // Write result to destination
                destMode.writeAddressedValue(isByteMode, destRegister, resultValue);
                destMode.postAddressingAction(isByteMode, destRegister);
            }
        }
    }

}
