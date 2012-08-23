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
 * Exclusive or operation.
 */
public class XorOpcode extends DoubleOperandOpcode {

    public final static int OPCODE = 074000;

    public XorOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return OPCODE;
    }

    @Override
    public int getExecutionTime() {
        return getBaseExecutionTime() + getAddressingTimeA2(
                getDestOperandAddressingMode().getCode());
    }

    @Override
    public void execute() {
        Cpu cpu = getCpu();
        // Read source value from register
        int srcRegister = getSrcOperandRegister();
        int srcValue = cpu.readRegister(false, srcRegister);
        // Read destination value
        AddressingMode destMode = getDestOperandAddressingMode();
        int destRegister = getDestOperandRegister();
        destMode.preAddressingAction(false, destRegister);
        int destValue = destMode.readAddressedValue(false, destRegister);
        if (destValue != Computer.BUS_ERROR) {
            int resultValue = srcValue ^ destValue;
            // Set flags
            cpu.setPswFlagN(false, resultValue);
            cpu.setPswFlagZ(false, resultValue);
            cpu.clearPswFlagV();
            // Write result to destination
            destMode.writeAddressedValue(false, destRegister, resultValue);
            destMode.postAddressingAction(false, destRegister);
        }
    }

}
