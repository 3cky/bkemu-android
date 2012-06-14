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

import su.comp.bk.arch.cpu.Cpu;

/**
 * Operation code base class.
 */
public abstract class BaseOpcode implements Opcode {

    // Instruction base execution time (in CPU ticks)
    private static final int BASE_EXECUTION_TIME = 12;

    // Addressing times for source operand by addressing code
    private static final int[] ADDRESSING_TIME_A = { 0, 12, 12, 20, 12, 20, 20, 28 };
    // Addressing times for destination operand by addressing code
    private static final int[] ADDRESSING_TIME_B = { 0, 20, 20, 32, 20, 32, 32, 40 };
    // Addressing times for operand which is both source and destination by addressing code
    private static final int[] ADDRESSING_TIME_AB = { 0, 16, 16, 24, 16, 24, 24, 32 };
    // Addressing times for unchanged destination operand by addressing code
    private static final int[] ADDRESSING_TIME_A2 = { 0, 20, 20, 28, 20, 28, 28, 36 };

    private final Cpu cpu;

    private int instruction;

    public BaseOpcode(Cpu cpu) {
        this.cpu = cpu;
    }

    /**
     * Check is this instruction uses byte-mode operation.
     * @return <code>true</code> if this instruction uses byte-mode operation,
     * <code>false</code> if uses word-mode.
     */
    protected boolean isByteModeOperation() {
        return (instruction & BYTE_OPERATION_FLAG) != 0;
    }

    protected Cpu getCpu() {
        return cpu;
    }

    protected int getInstruction() {
        return instruction;
    }

    public static int getBaseExecutionTime() {
        return BASE_EXECUTION_TIME;
    }

    protected static int getAddressingTimeA(int addressingModeCode) {
        return ADDRESSING_TIME_A[addressingModeCode];
    }

    protected static int getAddressingTimeB(int addressingModeCode) {
        return ADDRESSING_TIME_B[addressingModeCode];
    }

    protected static int getAddressingTimeAb(int addressingModeCode) {
        return ADDRESSING_TIME_AB[addressingModeCode];
    }

    protected static int getAddressingTimeA2(int addressingModeCode) {
        return ADDRESSING_TIME_A2[addressingModeCode];
    }

    @Override
    public void decode(int instructionToDecode) {
        this.instruction = instructionToDecode;
    }

}
