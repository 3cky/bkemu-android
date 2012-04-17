/*
 * Created: 01.04.2012
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
 * Double operand operation codes base class.
 */
public abstract class DoubleOperandOpcode extends BaseOpcode {

    private int srcOperandRegister;
    private AddressingMode srcOperandAddressingMode;

    private int destOperandRegister;
    private AddressingMode destOperandAddressingMode;

    public DoubleOperandOpcode(Cpu cpu) {
        super(cpu);
    }

    private void decodeSrcOperandRegister() {
        this.srcOperandRegister = (getInstruction() >> 6) & 7;
    }

    private void decodeSrcOperandAddressingMode() {
        this.srcOperandAddressingMode = getCpu().getAddressingMode(getInstruction() >> 9);
    }

    private void decodeDestOperandRegister() {
        this.destOperandRegister = getInstruction() & 7;
    }

    private void decodeDestOperandAddressingMode() {
        this.destOperandAddressingMode = getCpu().getAddressingMode(getInstruction() >> 3);
    }

    protected int getSrcOperandRegister() {
        return srcOperandRegister;
    }

    protected AddressingMode getSrcOperandAddressingMode() {
        return srcOperandAddressingMode;
    }

    protected int getDestOperandRegister() {
        return destOperandRegister;
    }

    protected AddressingMode getDestOperandAddressingMode() {
        return destOperandAddressingMode;
    }

    @Override
    public void decode(int instruction) {
        super.decode(instruction);
        decodeSrcOperandRegister();
        decodeSrcOperandAddressingMode();
        decodeDestOperandRegister();
        decodeDestOperandAddressingMode();
    }

}
