/*
 * Created: 31.03.2012
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
package su.comp.bk.arch.cpu;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.addressing.AddressingMode;
import su.comp.bk.arch.cpu.addressing.AutodecrementAddressingMode;
import su.comp.bk.arch.cpu.addressing.AutodecrementDeferredAddressingMode;
import su.comp.bk.arch.cpu.addressing.AutoincrementAddressingMode;
import su.comp.bk.arch.cpu.addressing.AutoincrementDeferredAddressingMode;
import su.comp.bk.arch.cpu.addressing.IndexAddressingMode;
import su.comp.bk.arch.cpu.addressing.IndexDeferredAddressingMode;
import su.comp.bk.arch.cpu.addressing.RegisterAddressingMode;
import su.comp.bk.arch.cpu.addressing.RegisterDeferredAddressingMode;
import su.comp.bk.arch.cpu.opcode.ClrOpcode;
import su.comp.bk.arch.cpu.opcode.ConditionCodeOpcodes;
import su.comp.bk.arch.cpu.opcode.Opcode;

/**
 * PDP-11 compatible 1801VM1 CPU implementation.
 */
public class Cpu {

    /** SEL1 I/O register address */
    public final static int REG_SEL1 = 0177716;
    /** SEL2 I/O register address */
    public final static int REG_SEL2 = 0177714;

    /** Register R0 */
    public final static int R0 = 0;
    /** Register R1 */
    public final static int R1 = 1;
    /** Register R2 */
    public final static int R2 = 2;
    /** Register R3 */
    public final static int R3 = 3;
    /** Register R4 */
    public final static int R4 = 4;
    /** Register R5 */
    public final static int R5 = 5;
    /** Register R6 */
    public final static int R6 = 6;
    /** Register R7 */
    public final static int R7 = 7;
    /** Common alias for register R6 - Stack Pointer */
    public final static int SP = R6;
    /** Common alias for register R7 - Processor Counter */
    public final static int PC = R7;

    private final Computer computer;

    // Opcodes lookup table
    private final Opcode[] opcodesTable = new Opcode[1 << 16];

    // Addressing modes lookup table
    private final AddressingMode[] addressingModes = new AddressingMode[8];

    /** PSW: Carry */
    public final static int PSW_FLAG_C = 1;
    /** PSW: Arithmetic overflow */
    public final static int PSW_FLAG_V = 2;
    /** PSW: Zero result */
    public final static int PSW_FLAG_Z = 4;
    /** PSW: Negative result */
    public final static int PSW_FLAG_N = 010;
    /** PSW: Trace bit  */
    public final static int PSW_FLAG_T = 020;
    /** PSW: Priority */
    public final static int PSW_FLAG_P = 0200;
    /** PSW: Halt */
    public final static int PSW_FLAG_H = 0400;

    // Processor Status Word (PSW)
    private short processorStatusWord;

    // Registers (R0-R7)
    private final short[] registers = new short[8];

    public Cpu(Computer computer) {
        this.computer = computer;
        initializeAddressingModes();
        initializeOpcodesTable();
    }

    private void initializeAddressingModes() {
        addAddressingMode(new RegisterAddressingMode(this));
        addAddressingMode(new RegisterDeferredAddressingMode(this));
        addAddressingMode(new AutoincrementAddressingMode(this));
        addAddressingMode(new AutoincrementDeferredAddressingMode(this));
        addAddressingMode(new AutodecrementAddressingMode(this));
        addAddressingMode(new AutodecrementDeferredAddressingMode(this));
        addAddressingMode(new IndexAddressingMode(this));
        addAddressingMode(new IndexDeferredAddressingMode(this));
    }

    private void initializeOpcodesTable() {
        // Zero operand opcodes
        addOpcode(new ConditionCodeOpcodes(this), ConditionCodeOpcodes.OPCODE_NOP,
                ConditionCodeOpcodes.OPCODE_SCC);
        // Single operand opcodes
        addOpcode(new ClrOpcode(this), ClrOpcode.OPCODE_CLR, ClrOpcode.OPCODE_CLR + 077);
        addOpcode(new ClrOpcode(this), ClrOpcode.OPCODE_CLR | Opcode.BYTE_OPERATION_FLAG,
                (ClrOpcode.OPCODE_CLR | Opcode.BYTE_OPERATION_FLAG) + 077);
    }

    private void addOpcode(Opcode opcode, int startOpcode, int endOpcode) {
        for (int opcodeTableIdx = startOpcode; opcodeTableIdx <= endOpcode; opcodeTableIdx++ ) {
            opcodesTable[opcodeTableIdx] = opcode;
        }
    }

    private void addAddressingMode(AddressingMode addressingMode) {
        this.addressingModes[addressingMode.getCode()] = addressingMode;
    }

    /**
     * Get addressing mode by its code.
     * @param addressingModeCode addressing mode code to get (only three LSB are taken in account)
     * @return {@link AddressingMode} for given addressing mode code
     */
    public AddressingMode getAddressingMode(int addressingModeCode) {
        return addressingModes[addressingModeCode & 7];
    }

    /**
     * Get processor status word (PSW).
     * @return processor status word value
     */
    public short getPswState() {
        return processorStatusWord;
    }

    /**
     * Set processor status word (PSW).
     * @param psw processor status word value to set
     */
    public void setPswState(short psw) {
        this.processorStatusWord = psw;
    }

    /**
     * Get PSW flag state.
     * @param flag PSW flag to get bit mask
     * @return <code>true</code> if flag is set, <code>false</code> otherwise
     */
    public boolean isPswFlagSet(int flag) {
        return (getPswState() & flag) != 0;
    }

    /**
     * Set PSW flag state.
     * @param flag PSW flag to set bit mask
     * @param state PSW flag state to set
     */
    public void setPswFlagState(int flag, boolean state) {
        setPswState((short) (state ? getPswState() | flag : getPswState() & ~flag));
    }

    /**
     * Clear all PSW flags.
     */
    public void clearPswFlags() {
        setPswState((short) (getPswState() & ~(PSW_FLAG_C | PSW_FLAG_N | PSW_FLAG_V | PSW_FLAG_Z)));
    }

    /**
     * Read register value in byte or word mode.
     * @param isByteMode <code>true</code> to read in byte mode (without sign extension!),
     * <code>false</code> to read in word mode
     * @param register register number to read (only three LSB are taken in account)
     */
    public int readRegister(boolean isByteMode, int register) {
        return registers[register & 7] & (isByteMode ? 0377 : 0177777);
    }

    /**
     * Write register value in byte or word mode.
     * @param isByteMode <code>true</code> to write in byte mode (without sign extension!),
     * <code>false</code> to write in word mode
     * @param register register number to write (only three LSB are taken in account)
     * @param data data to write (byte or word)
     */
    public void writeRegister(boolean isByteMode, int register, int data) {
        register &= 7;
        if (isByteMode) {
            registers[register] &= 0177400;
            registers[register] |= data & 0377;
        } else {
            registers[register] = (short) data;
        }
    }

    /**
     * Increment register by 1 in byte mode and by 2 in word mode (except PC ans SP which
     * both always incremented by 2).
     * @param isByteMode <code>true</code> to increment by 1, <code>false</code> to increment by 2
     * @param register register number to increment (only three LSB are taken in account)
     */
    public void incrementRegister(boolean isByteMode, int register) {
        register &= 7;
        registers[register] += (isByteMode && register != SP && register != PC) ? 1 : 2;
    }

    /**
     * Decrement register by 1 in byte mode and by 2 in word mode (except PC ans SP which
     * both always decremented by 2).
     * @param isByteMode <code>true</code> to decrement by 1, <code>false</code> to decrement by 2
     * @param register register number to decrement (only three LSB are taken in account)
     */
    public void decrementRegister(boolean isByteMode, int register) {
        register &= 7;
        registers[register] -= (isByteMode && register != SP && register != PC) ? 1 : 2;
    }

    public int readMemory(boolean isByteMode, int address) {
        return computer.readMemory(isByteMode, address);
    }

    public boolean writeMemory(boolean isByteMode, int address, int value) {
        return computer.writeMemory(isByteMode, address, value);
    }

    /**
     * Reset processor state.
     */
    public void reset() {
        setPswState((short) 0340);
        int sel1RegisterValue = readMemory(false, REG_SEL1);
        if (sel1RegisterValue != Computer.BUS_ERROR) {
            writeRegister(false, PC, sel1RegisterValue & 0177400);
        } else {
            // FIXME Deal with bus error while read startup address
        }
    }

    /**
     * Fetch instruction pointed by PC and increment PC by 2.
     * @return fetched instruction code or BUS_ERROR in case of error
     */
    public int fetchInstruction() {
        int instruction = readMemory(false, readRegister(false, PC));
        incrementRegister(false, PC);
        return instruction;
    }

    /**
     * Decode opcode for given instruction code.
     * @param instruction instruction code to decode
     * @return decoded {@link Opcode} for given instruction code or <code>null</code> if no
     * opcode exist for given instruction code
     */
    public Opcode decodeInstruction(int instruction) {
        return opcodesTable[instruction & 0177777];
    }

    /**
     * Fetches single instruction and executes it.
     */
    public void executeSingleInstruction() {
        int instruction = fetchInstruction();
        if (instruction != Computer.BUS_ERROR) {
            Opcode instructionOpcode = decodeInstruction(instruction);
            if (instructionOpcode != null) {
                instructionOpcode.decode(instruction);
                instructionOpcode.execute();
            } else {
                // TODO Illegal instruction
            }
        } else {
            // TODO Bus error while fetching instruction
        }
    }

}
