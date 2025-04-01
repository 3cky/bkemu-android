/*
 * Created: 01.04.2012
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
 * Single operand operation codes base class.
 */
public abstract class SingleOperandOpcode extends BaseOpcode {

    private int operandRegister;
    private AddressingMode operandAddressingMode;

    public SingleOperandOpcode(Cpu cpu) {
        super(cpu);
    }

    private void decodeOperandRegister() {
        this.operandRegister = getInstruction() & 7;
    }

    private void decodeOperandAddressingMode() {
        this.operandAddressingMode = getCpu().getAddressingMode(getInstruction() >> 3);
    }

    protected int getOperandRegister() {
        return operandRegister;
    }

    protected AddressingMode getOperandAddressingMode() {
        return operandAddressingMode;
    }

    @Override
    public void decode(int instruction) {
        super.decode(instruction);
        decodeOperandRegister();
        decodeOperandAddressingMode();
    }

    @Override
    public int getExecutionTime() {
        return getBaseExecutionTime() + getAddressingTimeAb(operandAddressingMode.getCode());
    }

    @Override
    public void execute() {
        boolean isByteModeOperation = isByteModeOperation();
        operandAddressingMode.preAddressingAction(isByteModeOperation, operandRegister);
        executeSingleOperand(isByteModeOperation(), operandRegister, operandAddressingMode);
        operandAddressingMode.postAddressingAction(isByteModeOperation, operandRegister);
    }

    protected abstract void executeSingleOperand(boolean isByteMode, int singleOperandRegister,
            AddressingMode singleOperandAddressingMode);

}
