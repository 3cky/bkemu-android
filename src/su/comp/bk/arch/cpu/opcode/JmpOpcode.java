/*
 * Created: 18.04.2012
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
 * Jump instruction.
 */
public class JmpOpcode extends BaseOpcode {

    public final static int OPCODE = 0100;

    private int register;
    private AddressingMode addressingMode;

    public JmpOpcode(Cpu cpu) {
        super(cpu);
    }

    private void decodeRegister() {
        this.register = getInstruction() & 7;
    }

    private void decodeAddressingMode() {
        this.addressingMode = getCpu().getAddressingMode(getInstruction() >> 3);
    }

    @Override
    public void decode(int instruction) {
        super.decode(instruction);
        decodeRegister();
        decodeAddressingMode();
    }

    @Override
    public void execute() {
        addressingMode.preAddressingAction(false, register);
        int jumpAddress = addressingMode.getAddress(register);
        if (jumpAddress != Computer.BUS_ERROR) {
            getCpu().writeRegister(false, Cpu.PC, jumpAddress);
        }
        addressingMode.postAddressingAction(false, register);
    }

}
