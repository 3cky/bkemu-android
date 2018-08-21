/*
 * Created: 31.03.2012
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
package su.comp.bk.arch.cpu;

import android.os.Bundle;
import android.util.Log;
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
import su.comp.bk.arch.cpu.opcode.AdcOpcode;
import su.comp.bk.arch.cpu.opcode.AddOpcode;
import su.comp.bk.arch.cpu.opcode.AslOpcode;
import su.comp.bk.arch.cpu.opcode.AsrOpcode;
import su.comp.bk.arch.cpu.opcode.BccOpcode;
import su.comp.bk.arch.cpu.opcode.BcsOpcode;
import su.comp.bk.arch.cpu.opcode.BeqOpcode;
import su.comp.bk.arch.cpu.opcode.BgeOpcode;
import su.comp.bk.arch.cpu.opcode.BgtOpcode;
import su.comp.bk.arch.cpu.opcode.BhiOpcode;
import su.comp.bk.arch.cpu.opcode.BicOpcode;
import su.comp.bk.arch.cpu.opcode.BisOpcode;
import su.comp.bk.arch.cpu.opcode.BitOpcode;
import su.comp.bk.arch.cpu.opcode.BleOpcode;
import su.comp.bk.arch.cpu.opcode.BlosOpcode;
import su.comp.bk.arch.cpu.opcode.BltOpcode;
import su.comp.bk.arch.cpu.opcode.BmiOpcode;
import su.comp.bk.arch.cpu.opcode.BneOpcode;
import su.comp.bk.arch.cpu.opcode.BplOpcode;
import su.comp.bk.arch.cpu.opcode.BptOpcode;
import su.comp.bk.arch.cpu.opcode.BrOpcode;
import su.comp.bk.arch.cpu.opcode.BvcOpcode;
import su.comp.bk.arch.cpu.opcode.BvsOpcode;
import su.comp.bk.arch.cpu.opcode.ClrOpcode;
import su.comp.bk.arch.cpu.opcode.CmpOpcode;
import su.comp.bk.arch.cpu.opcode.ComOpcode;
import su.comp.bk.arch.cpu.opcode.ConditionCodeOpcodes;
import su.comp.bk.arch.cpu.opcode.DecOpcode;
import su.comp.bk.arch.cpu.opcode.EmtOpcode;
import su.comp.bk.arch.cpu.opcode.HaltOpcode;
import su.comp.bk.arch.cpu.opcode.IncOpcode;
import su.comp.bk.arch.cpu.opcode.IotOpcode;
import su.comp.bk.arch.cpu.opcode.JmpOpcode;
import su.comp.bk.arch.cpu.opcode.JsrOpcode;
import su.comp.bk.arch.cpu.opcode.MarkOpcode;
import su.comp.bk.arch.cpu.opcode.MfpsOpcode;
import su.comp.bk.arch.cpu.opcode.MovOpcode;
import su.comp.bk.arch.cpu.opcode.MtpsOpcode;
import su.comp.bk.arch.cpu.opcode.NegOpcode;
import su.comp.bk.arch.cpu.opcode.Opcode;
import su.comp.bk.arch.cpu.opcode.ResetOpcode;
import su.comp.bk.arch.cpu.opcode.RolOpcode;
import su.comp.bk.arch.cpu.opcode.RorOpcode;
import su.comp.bk.arch.cpu.opcode.RtiOpcode;
import su.comp.bk.arch.cpu.opcode.RtsOpcode;
import su.comp.bk.arch.cpu.opcode.RttOpcode;
import su.comp.bk.arch.cpu.opcode.SbcOpcode;
import su.comp.bk.arch.cpu.opcode.SobOpcode;
import su.comp.bk.arch.cpu.opcode.SubOpcode;
import su.comp.bk.arch.cpu.opcode.SwabOpcode;
import su.comp.bk.arch.cpu.opcode.SxtOpcode;
import su.comp.bk.arch.cpu.opcode.TrapOpcode;
import su.comp.bk.arch.cpu.opcode.TstOpcode;
import su.comp.bk.arch.cpu.opcode.WaitOpcode;
import su.comp.bk.arch.cpu.opcode.XorOpcode;

/**
 * PDP-11 compatible 1801VM1 CPU implementation.
 */
public class Cpu {

    private static final String TAG = Cpu.class.getName();

    /** SEL1 I/O register address */
    public final static int REG_SEL1 = 0177716;
    /** SEL2 I/O register address */
    public final static int REG_SEL2 = 0177714;

    /** HALT mode PC store register */
    public final static int REG_HALT_PC = 0177674;
    /** HALT mode PSW store register */
    public final static int REG_HALT_PSW = 0177676;

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

    /** Bus error trap vector address */
    public static final int TRAP_VECTOR_BUS_ERROR = 004;
    /** Reserved opcode trap vector address */
    public static final int TRAP_VECTOR_RESERVED_OPCODE = 010;
    /** Breakpoint trap vector address */
    public static final int TRAP_VECTOR_BPT = 014;
    /** IOT instruction trap vector address */
    public static final int TRAP_VECTOR_IOT = 020;
    /** Power fail trap vector address */
    public static final int TRAP_VECTOR_ACLO = 024;
    /** EMT instruction trap vector address */
    public static final int TRAP_VECTOR_EMT = 030;
    /** TRAP instruction trap vector address */
    public static final int TRAP_VECTOR_TRAP = 034;
    /** IRQ2 vector address */
    public static final int TRAP_IRQ2 = 0100;

    // State save/restore: CPU time (in clock ticks)
    private static final String STATE_TIME =
            Cpu.class.getName() + "#time";
    // State save/restore: Reserved opcode was fetched flag
    private static final String STATE_RESERVED_OPCODE =
            Cpu.class.getName() + "#reserved_opcode";
    // State save/restore: Interrupt request waiting flag
    private static final String STATE_IRQ_WAIT =
            Cpu.class.getName() + "#irq_wait";
    // State save/restore: Deferred trace trap flag
    private static final String STATE_DEFERRED_TRACE_TRAP =
            Cpu.class.getName() + "#deferred_trace_trap";
    // State save/restore: CPU is in HALT mode flag
    private static final String STATE_HALT_MODE =
            Cpu.class.getName() + "#halt_mode";
    // State save/restore: Bus error caused flag
    private static final String STATE_BUS_ERROR =
            Cpu.class.getName() + "#bus_error";
    // State save/restore: Registers data
    private static final String STATE_REGISTERS =
            Cpu.class.getName() + "#registers";
    // State save/restore: Processor status word data
    private static final String STATE_PSW =
            Cpu.class.getName() + "#psw";

    // First radial interrupt (IRQ1) requested
    private boolean isIrq1Requested;

    // Second radial interrupt (IRQ2) requested
    private boolean isIrq2Requested;

    // Vector interrupt (VIRQ) requested flag
    private boolean isVirqRequested;
    // Requested vector interrupt (VIRQ) address
    private int virqAddress;

    // Bus error flag
    private boolean isBusError;

    // Halt mode flag
    private boolean isHaltMode;

    // Interrupt wait mode flag
    private boolean isInterruptWaitMode;

    // Reserved opcode flag
    private boolean isReservedOpcodeFetched;

    // Deferred trace trap (after RTT instruction executing) flag
    private boolean isDeferredTraceTrap;

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
    /** PSW: Halt bit */
    public final static int PSW_FLAG_H = 02000;
    /** PSW: IRQ1 mask */
    public final static int PSW_FLAG_M = 04000;

    // Processor Status Word (PSW)
    private int processorStatusWord;

    // Registers (R0-R7)
    private final short[] registers = new short[8];

    // Reset time (in CPU ticks)
    private final static int RESET_TIME = 1024000;

    // Reserved instruction decode time (in CPU ticks)
    private final static int RESERVED_OPCODE_DECODE_TIME = 144;

    // Bus error timeout (in CPU ticks)
    private final static int BUS_ERROR_TIMEOUT = 64;

    // CPU time (in clock ticks)
    private long time;

    private OnTrapListener onTrapListener;

    /**
     * Hardware/software interrupts event listener interface.
     */
    public interface OnTrapListener {
        /**
         * Called on hardware/software interrupt immediately after
         * pushing PSW/PC to stack.
         * @param cpu interrupted CPU object reference
         * @param trapVectorAddress interrupt trap vector address
         */
        void onTrap(Cpu cpu, int trapVectorAddress);
    }

    /**
     * Opcode intercepter class.
     */
    protected class OpcodeIntercepter implements Opcode {
        private final Opcode interceptedOpcode;
        private final OnOpcodeListener opcodeListener;

        OpcodeIntercepter(Opcode interceptedOpcode, OnOpcodeListener opcodeListener) {
            this.interceptedOpcode = interceptedOpcode;
            this.opcodeListener = opcodeListener;
        }

        @Override
        public int getOpcode() {
            return interceptedOpcode.getOpcode();
        }

        @Override
        public void decode(int instruction) {
            interceptedOpcode.decode(instruction);
        }

        @Override
        public int getExecutionTime() {
            return interceptedOpcode.getExecutionTime();
        }

        @Override
        public void execute() {
            interceptedOpcode.execute();
            opcodeListener.onOpcodeExecuted(Cpu.this, interceptedOpcode.getOpcode());
        }
    }

    /**
     * Opcode listener interface.
     */
    public interface OnOpcodeListener {
        /**
         * Called after intercepted opcode execution.
         * @param cpu CPU object reference
         * @param opcode executed opcode
         */
        void onOpcodeExecuted(Cpu cpu, int opcode);
    }

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
        addOpcode(new ClrOpcode(this), ClrOpcode.OPCODE, ClrOpcode.OPCODE + 077);
        addOpcode(new ClrOpcode(this), ClrOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (ClrOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new ComOpcode(this), ComOpcode.OPCODE, ComOpcode.OPCODE + 077);
        addOpcode(new ComOpcode(this), ComOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (ComOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new IncOpcode(this), IncOpcode.OPCODE, IncOpcode.OPCODE + 077);
        addOpcode(new IncOpcode(this), IncOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (IncOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new DecOpcode(this), DecOpcode.OPCODE, DecOpcode.OPCODE + 077);
        addOpcode(new DecOpcode(this), DecOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (DecOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new NegOpcode(this), NegOpcode.OPCODE, NegOpcode.OPCODE + 077);
        addOpcode(new NegOpcode(this), NegOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (NegOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new TstOpcode(this), TstOpcode.OPCODE, TstOpcode.OPCODE + 077);
        addOpcode(new TstOpcode(this), TstOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (TstOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new AsrOpcode(this), AsrOpcode.OPCODE, AsrOpcode.OPCODE + 077);
        addOpcode(new AsrOpcode(this), AsrOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (AsrOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new AslOpcode(this), AslOpcode.OPCODE, AslOpcode.OPCODE + 077);
        addOpcode(new AslOpcode(this), AslOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (AslOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new RorOpcode(this), RorOpcode.OPCODE, RorOpcode.OPCODE + 077);
        addOpcode(new RorOpcode(this), RorOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (RorOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new RolOpcode(this), RolOpcode.OPCODE, RolOpcode.OPCODE + 077);
        addOpcode(new RolOpcode(this), RolOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (RolOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new AdcOpcode(this), AdcOpcode.OPCODE, AdcOpcode.OPCODE + 077);
        addOpcode(new AdcOpcode(this), AdcOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (AdcOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new SbcOpcode(this), SbcOpcode.OPCODE, SbcOpcode.OPCODE + 077);
        addOpcode(new SbcOpcode(this), SbcOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (SbcOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 077);
        addOpcode(new SxtOpcode(this), SxtOpcode.OPCODE, SxtOpcode.OPCODE + 077);
        addOpcode(new SwabOpcode(this), SwabOpcode.OPCODE, SwabOpcode.OPCODE + 077);
        addOpcode(new MarkOpcode(this), MarkOpcode.OPCODE, MarkOpcode.OPCODE + 077);
        addOpcode(new MfpsOpcode(this), MfpsOpcode.OPCODE, MfpsOpcode.OPCODE + 077);
        addOpcode(new MtpsOpcode(this), MtpsOpcode.OPCODE, MtpsOpcode.OPCODE + 077);
        // Branch opcodes
        addOpcode(new BrOpcode(this), BrOpcode.OPCODE, BrOpcode.OPCODE + 0377);
        addOpcode(new BneOpcode(this), BneOpcode.OPCODE, BneOpcode.OPCODE + 0377);
        addOpcode(new BeqOpcode(this), BeqOpcode.OPCODE, BeqOpcode.OPCODE + 0377);
        addOpcode(new BgeOpcode(this), BgeOpcode.OPCODE, BgeOpcode.OPCODE + 0377);
        addOpcode(new BltOpcode(this), BltOpcode.OPCODE, BltOpcode.OPCODE + 0377);
        addOpcode(new BgtOpcode(this), BgtOpcode.OPCODE, BgtOpcode.OPCODE + 0377);
        addOpcode(new BleOpcode(this), BleOpcode.OPCODE, BleOpcode.OPCODE + 0377);
        addOpcode(new BplOpcode(this), BplOpcode.OPCODE, BplOpcode.OPCODE + 0377);
        addOpcode(new BmiOpcode(this), BmiOpcode.OPCODE, BmiOpcode.OPCODE + 0377);
        addOpcode(new BhiOpcode(this), BhiOpcode.OPCODE, BhiOpcode.OPCODE + 0377);
        addOpcode(new BlosOpcode(this), BlosOpcode.OPCODE, BlosOpcode.OPCODE + 0377);
        addOpcode(new BvcOpcode(this), BvcOpcode.OPCODE, BvcOpcode.OPCODE + 0377);
        addOpcode(new BvsOpcode(this), BvsOpcode.OPCODE, BvsOpcode.OPCODE + 0377);
        addOpcode(new BccOpcode(this), BccOpcode.OPCODE, BccOpcode.OPCODE + 0377);
        addOpcode(new BcsOpcode(this), BcsOpcode.OPCODE, BcsOpcode.OPCODE + 0377);
        addOpcode(new SobOpcode(this), SobOpcode.OPCODE, SobOpcode.OPCODE + 0777);
        // Double operand opcodes
        addOpcode(new MovOpcode(this), MovOpcode.OPCODE, MovOpcode.OPCODE + 07777);
        addOpcode(new MovOpcode(this), MovOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (MovOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 07777);
        addOpcode(new CmpOpcode(this), CmpOpcode.OPCODE, CmpOpcode.OPCODE + 07777);
        addOpcode(new CmpOpcode(this), CmpOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (CmpOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 07777);
        addOpcode(new BitOpcode(this), BitOpcode.OPCODE, BitOpcode.OPCODE + 07777);
        addOpcode(new BitOpcode(this), BitOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (BitOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 07777);
        addOpcode(new BicOpcode(this), BicOpcode.OPCODE, BicOpcode.OPCODE + 07777);
        addOpcode(new BicOpcode(this), BicOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (BicOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 07777);
        addOpcode(new BisOpcode(this), BisOpcode.OPCODE, BisOpcode.OPCODE + 07777);
        addOpcode(new BisOpcode(this), BisOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG,
                (BisOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG) + 07777);
        addOpcode(new AddOpcode(this), AddOpcode.OPCODE, AddOpcode.OPCODE + 07777);
        addOpcode(new SubOpcode(this), SubOpcode.OPCODE, SubOpcode.OPCODE + 07777);
        addOpcode(new XorOpcode(this), XorOpcode.OPCODE, XorOpcode.OPCODE + 0777);
        // Jump and subroutine opcodes
        addOpcode(new JmpOpcode(this), JmpOpcode.OPCODE, JmpOpcode.OPCODE + 077);
        addOpcode(new JsrOpcode(this), JsrOpcode.OPCODE, JsrOpcode.OPCODE + 0777);
        addOpcode(new RtsOpcode(this), RtsOpcode.OPCODE, RtsOpcode.OPCODE + 7);
        // Control opcodes
        addOpcode(new HaltOpcode(this), HaltOpcode.OPCODE, HaltOpcode.OPCODE);
        addOpcode(new WaitOpcode(this), WaitOpcode.OPCODE, WaitOpcode.OPCODE);
        addOpcode(new RtiOpcode(this), RtiOpcode.OPCODE, RtiOpcode.OPCODE);
        addOpcode(new BptOpcode(this), BptOpcode.OPCODE, BptOpcode.OPCODE);
        addOpcode(new IotOpcode(this), IotOpcode.OPCODE, IotOpcode.OPCODE);
        addOpcode(new ResetOpcode(this), ResetOpcode.OPCODE, ResetOpcode.OPCODE);
        addOpcode(new RttOpcode(this), RttOpcode.OPCODE, RttOpcode.OPCODE);
        addOpcode(new EmtOpcode(this), EmtOpcode.OPCODE, EmtOpcode.OPCODE + 0377);
        addOpcode(new TrapOpcode(this), TrapOpcode.OPCODE, TrapOpcode.OPCODE + 0377);
    }

    private void addOpcode(Opcode opcode, int startOpcode, int endOpcode) {
        for (int opcodeTableIdx = startOpcode; opcodeTableIdx <= endOpcode; opcodeTableIdx++ ) {
            if (opcodesTable[opcodeTableIdx] != null) {
                throw new IllegalArgumentException(String.format("Opcodes table conflict: " +
                		"trying to set %s for instruction code 0%o while it already set to %s",
                		opcode.getClass().getName(), opcodeTableIdx,
                		opcodesTable[opcodeTableIdx].getClass().getName()));
            }
            opcodesTable[opcodeTableIdx] = opcode;
        }
    }

    private void addAddressingMode(AddressingMode addressingMode) {
        this.addressingModes[addressingMode.getCode()] = addressingMode;
    }

    public OnTrapListener getOnTrapListener() {
        return onTrapListener;
    }

    public void setOnTrapListener(OnTrapListener onTrapListener) {
        this.onTrapListener = onTrapListener;
    }

    public void setOnOpcodeListener(int instruction, OnOpcodeListener opcodeListener) {
        Opcode opcode = decodeInstruction(instruction);
        if (opcode != null) {
            opcodesTable[instruction] = new OpcodeIntercepter(opcode, opcodeListener);
        }
    }

    /**
     * Save CPU state.
     * @param outState {@link Bundle} to save state
     */
    public void saveState(Bundle outState) {
        // Save PSW
        outState.putInt(STATE_PSW, getPswState());
        // Save registers
        outState.putShortArray(STATE_REGISTERS, getRegisters());
        // Save state flags
        outState.putBoolean(STATE_BUS_ERROR, isBusError());
        outState.putBoolean(STATE_HALT_MODE, isHaltMode());
        outState.putBoolean(STATE_DEFERRED_TRACE_TRAP, isDeferredTraceTrap());
        outState.putBoolean(STATE_IRQ_WAIT, isInterruptWaitMode());
        outState.putBoolean(STATE_RESERVED_OPCODE, isReservedOpcodeFetched());
        // Save CPU time
        outState.putLong(STATE_TIME, getTime());
    }

    /**
     * Restore CPU state.
     * @param inState {@link Bundle} to restore state
     * @throws Exception in case of error while state restoring
     */
    public void restoreState(Bundle inState) throws Exception {
        // Restore PSW
        setPswState(inState.getInt(STATE_PSW));
        // Restore registers
        putRegisters(inState.getShortArray(STATE_REGISTERS));
        // Restore state flags
        setBusError(inState.getBoolean(STATE_BUS_ERROR));
        setHaltMode(inState.getBoolean(STATE_HALT_MODE));
        setDeferredTraceTrap(inState.getBoolean(STATE_DEFERRED_TRACE_TRAP));
        setInterruptWaitMode(inState.getBoolean(STATE_IRQ_WAIT));
        setReservedOpcodeFetched(inState.getBoolean(STATE_RESERVED_OPCODE));
        // Restore CPU time
        setTime(inState.getLong(STATE_TIME));
    }

    /**
     * Get CPU time (in clock ticks).
     * @return CPU time
     */
    public long getTime() {
        return time;
    }

    /**
     * Set CPU time (in clock ticks).
     * @param CPU time to set
     */
    public void setTime(long timeToSet) {
        time = timeToSet;
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
     * Get processor registers.
     * @return processor registers as array of shorts
     */
    public short[] getRegisters() {
        return registers;
    }

    /**
     * Put processor registers.
     * @param registersToPut processor registers to put as array of shorts
     */
    public void putRegisters(short[] registersToPut) {
        System.arraycopy(registersToPut, R0, registers, R0, registersToPut.length);
    }

    /**
     * Get processor status word (PSW).
     * @return processor status word value
     */
    public int getPswState() {
        return processorStatusWord;
    }

    /**
     * Set processor status word (PSW).
     * @param psw processor status word value to set
     */
    public void setPswState(int psw) {
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
    public void setPswFlag(int flag, boolean state) {
        setPswState((short) (state ? getPswState() | flag : getPswState() & ~flag));
    }

    /**
     * Clear all PSW flags.
     */
    public void clearPswFlags() {
        setPswState((short) (getPswState() & ~(PSW_FLAG_C | PSW_FLAG_N | PSW_FLAG_V | PSW_FLAG_Z)));
    }

    public void setPswFlagC(boolean state) {
        setPswFlag(PSW_FLAG_C, state);
    }

    public void setPswFlagV(boolean state) {
        setPswFlag(PSW_FLAG_V, state);
    }

    public void setPswFlagZ(boolean state) {
        setPswFlag(PSW_FLAG_Z, state);
    }

    public void setPswFlagN(boolean state) {
        setPswFlag(PSW_FLAG_N, state);
    }

    public void setPswFlagC() {
        setPswFlagC(true);
    }

    public void setPswFlagV() {
        setPswFlagV(true);
    }

    public void setPswFlagZ() {
        setPswFlagZ(true);
    }

    public void setPswFlagN() {
        setPswFlagN(true);
    }

    public void clearPswFlagC() {
        setPswFlagC(false);
    }

    public void clearPswFlagV() {
        setPswFlagV(false);
    }

    public void clearPswFlagZ() {
        setPswFlagZ(false);
    }

    public void clearPswFlagN() {
        setPswFlagN(false);
    }

    /**
     * Set PSW N flag state depend on operation result value.
     * @param isByteMode <code>true</code> to analyze value as result of byte operation,
     * <code>false</code> to analyze value as result of word operation
     * @param value value to analyze
     */
    public void setPswFlagN(boolean isByteMode, int value) {
        setPswFlag(PSW_FLAG_N, (value & (isByteMode ? 0200 : 0100000)) != 0);
    }

    /**
     * Set PSW N flag state depend on operation result value.
     * @param isByteMode <code>true</code> to analyze value as result of byte operation,
     * <code>false</code> to analyze value as result of word operation
     * @param value value to analyze
     */
    public void setPswFlagZ(boolean isByteMode, int value) {
        setPswFlag(PSW_FLAG_Z, (value & (isByteMode ? 0377 : 0177777)) == 0);
    }

    /**
     * Get bus error flag state.
     * @return <code>true</code> if was bus error, <code>false</code> otherwise
     */
    public boolean isBusError() {
        return this.isBusError;
    }

    /**
     * Set bus error flag state.
     * @param isBusError bus error flag state to set
     */
    public void setBusError(boolean isBusError) {
        this.isBusError = isBusError;
    }

    /**
     * Set bus error flag state.
     */
    public void setBusError() {
        setBusError(true);
    }

    /**
     * Clear bus error flag state.
     */
    public void clearBusError() {
        setBusError(false);
    }

    /**
     * Get halt mode flag state.
     * @return <code>true</code> if CPU is in halt mode , <code>false</code> otherwise
     */
    public boolean isHaltMode() {
        return this.isHaltMode;
    }

    /**
     * Set halt mode flag state.
     * @param isHaltMode halt mode flag state to set
     */
    public void setHaltMode(boolean isHaltMode) {
        this.isHaltMode = isHaltMode;
    }

    /**
     * Set halt mode flag state.
     */
    public void setHaltMode() {
        setHaltMode(true);
    }

    /**
     * Clear halt mode flag state.
     */
    public void clearHaltMode() {
        setHaltMode(false);
    }

    /**
     * Check first radial interrupt (IRQ1) is requested.
     * @return <code>true</code> if IRQ1 requested, <code>false</code> otherwise
     */
    public boolean isIrq1Requested() {
        return isIrq1Requested;
    }

    /**
     * Request first radial interrupt (IRQ1).
     */
    public void requestIrq1() {
        this.isIrq1Requested = true;
    }

    /**
     * Clear pending first radial interrupt (IRQ1) request.
     */
    public void clearIrq1Request() {
        this.isIrq1Requested = false;
    }

    /**
     * Check second radial interrupt (IRQ2) is requested.
     * @return <code>true</code> if IRQ2 requested, <code>false</code> otherwise
     */
    public boolean isIrq2Requested() {
        return isIrq2Requested;
    }

    /**
     * Request second radial interrupt (IRQ2).
     */
    public void requestIrq2() {
        this.isIrq2Requested = true;
    }

    /**
     * Clear pending second radial interrupt (IRQ2) request.
     */
    public void clearIrq2Request() {
        this.isIrq2Requested = false;
    }

    /**
     * Request vector interrupt (VIRQ).
     * @param address vector interrupt address
     */
    public synchronized void requestVirq(int address) {
        this.isVirqRequested = true;
        this.virqAddress = address;
    }

    /**
     * Check is vector interrupt (VIRQ) requested.
     * @return <code>true</code> if vector interrupt is requested, <code>false</code> if not
     */
    public boolean isVirqRequested() {
        return this.isVirqRequested;
    }

    /**
     * Clear pending vector interrupt (VIRQ) request.
     */
    public void clearVirqRequest() {
        this.isVirqRequested = false;
    }

    /**
     * Get pending vector interrupt (VIRQ) address.
     * @return pending vector interrupt address
     */
    public synchronized int getVirqAddress() {
        return virqAddress;
    }

    /**
     * Get interrupt wait mode flag state.
     * @return <code>true</code> if processor in interrupt wait mode, <code>false</code> otherwise
     */
    public boolean isInterruptWaitMode() {
        return this.isInterruptWaitMode;
    }

    /**
     * Set interrupt wait mode flag state.
     * @param isInterruptWaitMode interrupt wait mode flag state to set
     */
    public void setInterruptWaitMode(boolean isInterruptWaitMode) {
        this.isInterruptWaitMode = isInterruptWaitMode;
    }

    /**
     * Set interrupt wait mode flag state.
     */
    public void setInterruptWaitMode() {
        Log.d(TAG, "entering WAIT mode, PC: 0" + Integer.toOctalString(readRegister(false, PC)));
        setInterruptWaitMode(true);
    }

    /**
     * Clear interrupt wait mode flag state.
     */
    public void clearInterruptWaitMode() {
        if (isInterruptWaitMode()) {
            Log.d(TAG, "leaving WAIT mode, PC: 0" + Integer.toOctalString(readRegister(false, PC)));
        }
        setInterruptWaitMode(false);
    }

    /**
     * Get reserved opcode fetched flag state.
     * @return <code>true</code> if last fetched opcode was reserved (unknown) opcode,
     * <code>false</code> if last fetched opcode was decoded successfully
     */
    public boolean isReservedOpcodeFetched() {
        return isReservedOpcodeFetched;
    }

    /**
     * Set reserved opcode fetched flag state.
     * @param isReservedOpcodeFetched reserved opcode fetched flag state to set
     */
    public void setReservedOpcodeFetched(boolean isReservedOpcodeFetched) {
        this.isReservedOpcodeFetched = isReservedOpcodeFetched;
    }

    /**
     * Set reserved opcode fetched flag state.
     */
    public void setReservedOpcodeFetched() {
        setReservedOpcodeFetched(true);
    }

    /**
     * Clear reserved opcode fetched flag state.
     */
    public void clearReservedOpcodeFetched() {
        setReservedOpcodeFetched(false);
    }

    /**
     * Check deferred trace trap (RTT instruction) flag state.
     * @return <code>true</code> if last executed instruction was RTT,
     * <code>false</code> otherwise
     */
    public boolean isDeferredTraceTrap() {
        return isDeferredTraceTrap;
    }

    /**
     * Set deferred trace trap flag state.
     * @param isDeferredTraceTrap deferred trace trap flag state to set
     */
    public void setDeferredTraceTrap(boolean isDeferredTraceTrap) {
        this.isDeferredTraceTrap = isDeferredTraceTrap;
    }

    /**
     * Set deferred trace trap flag state.
     */
    public void setDeferredTraceTrap() {
        setDeferredTraceTrap(true);
    }

    /**
     * Clear deferred trace trap flag state.
     */
    public void clearDeferredTraceTrap() {
        setDeferredTraceTrap(false);
    }

    /**
     * Execute 1801VM1-specific halt mode entering sequence
     */
    public void enterHaltMode() {
        Log.d(TAG, "entering HALT mode, PC: 0" + Integer.toOctalString(readRegister(false, PC)));
        // Set bit 3 in SEL1 register
        int sel1 = readMemory(false, REG_SEL1);
        if (sel1 != Computer.BUS_ERROR) {
            sel1 |= 010;
            if (writeMemory(false, REG_SEL1, sel1) &&
                    // Store PSW to 0177676
                    writeMemory(false, REG_HALT_PSW, getPswState()) &&
                    // Store PC to 0177674
                    writeMemory(false, REG_HALT_PC, readRegister(false, PC)) &&
                    // Trap to HALT handler
                    processTrap((sel1 & 0177400) + 2, false)) {
                setHaltMode();
            }
        }
    }

    /**
     * Process trap with given vector address.
     * @param trapVectorAddress trap vector address
     * @param pushReturnState <code>true</code> to push PC/PSW state to stack,
     * <code>false</code> to not push
     * @return <code>true</code> if trap vector successfully loaded
     * or <false> if bus error happens while vector loading
     */
    public boolean processTrap(int trapVectorAddress, boolean pushReturnState) {
//        Log.d(TAG, ">>> TRAP " + Integer.toOctalString(trapVectorAddress) +
//                ", PC: 0" + Integer.toOctalString(readRegister(false, PC)));
        boolean isVectorLoaded = false;
        if (!pushReturnState || (push(getPswState()) && push(readRegister(false, PC)))) {
            int trapAddress = readMemory(false, trapVectorAddress);
            if (trapAddress != Computer.BUS_ERROR) {
                writeRegister(false, PC, trapAddress);
                int trapPsw = readMemory(false, trapVectorAddress + 2);
                if (trapPsw != Computer.BUS_ERROR) {
                    setPswState((short) (trapPsw & 0377));
                    isVectorLoaded = true;
                }
            }
        }
        OnTrapListener listener = getOnTrapListener();
        if (isVectorLoaded && onTrapListener != null) {
            listener.onTrap(this, trapVectorAddress);
        }
//        Log.d(TAG, "<<< TRAP " + Integer.toOctalString(trapVectorAddress) +
//                ", PC: 0" + Integer.toOctalString(readRegister(false, PC)));
        return isVectorLoaded;
    }

    /**
     * Return from trap.
     * @param isTraceTrap <code>true</code> in case of return from trace interrupt,
     * <code>false</code> otherwise
     */
    public void returnFromTrap(boolean isTraceTrap) {
//        Log.d(TAG, ">>> return from TRAP, PC: 0" + Integer.toOctalString(readRegister(false, PC)));
        int pc = pop();
        if (pc != Computer.BUS_ERROR) {
            writeRegister(false, PC, pc);
            int psw = pop();
            if (psw != Computer.BUS_ERROR) {
                setPswState((short) (psw & 0377));
                setDeferredTraceTrap();
            }
        }
//        Log.d(TAG, "<<< return from TRAP, PC: 0" + Integer.toOctalString(readRegister(false, PC)));
    }

    /**
     * Read register value in byte or word mode.
     * @param isByteMode <code>true</code> to read in byte mode (without sign extension!),
     * <code>false</code> to read in word mode
     * @param register register number to read (only three LSB are taken in account)
     * @return read register value
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
     * @return register value after operation
     */
    public int incrementRegister(boolean isByteMode, int register) {
        register &= 7;
        registers[register] += (isByteMode && register != SP && register != PC) ? 1 : 2;
        return readRegister(isByteMode, register);
    }

    /**
     * Decrement register by 1 in byte mode and by 2 in word mode (except PC ans SP which
     * both always decremented by 2).
     * @param isByteMode <code>true</code> to decrement by 1, <code>false</code> to decrement by 2
     * @param register register number to decrement (only three LSB are taken in account)
     * @return register value after operation
     */
    public int decrementRegister(boolean isByteMode, int register) {
        register &= 7;
        registers[register] -= (isByteMode && register != SP && register != PC) ? 1 : 2;
        return readRegister(isByteMode, register);
    }

    /**
     * Push value to stack.
     * @param value word value to push
     * @return <code>true</code> if value successfully pushed to stack or <code>false</code>
     * SP pointing to address which is not mapped to memory or device
     */
    public boolean push(int value) {
        AddressingMode pushMode = getAddressingMode(AutodecrementAddressingMode.CODE);
        pushMode.preAddressingAction(false, Cpu.SP);
        boolean isPushed = pushMode.writeAddressedValue(false, Cpu.SP, value);
        if (isPushed) {
            pushMode.postAddressingAction(false, Cpu.SP);
        }
        return isPushed;
    }

    /**
     * Pop value from stack.
     * @return popped word value or <code>Computer.BUS_ERROR</code> if SP pointing to address
     * which is not mapped to memory or device
     */
    public int pop() {
        AddressingMode popMode = getAddressingMode(AutoincrementAddressingMode.CODE);
        popMode.preAddressingAction(false, Cpu.SP);
        int value = popMode.readAddressedValue(false, Cpu.SP);
        if (value != Computer.BUS_ERROR) {
            popMode.postAddressingAction(false, Cpu.SP);
        }
        return value;
    }

    /**
     * Read byte or word from given memory location.
     * @param isByteMode <code>true</code> to read byte, <code>false</code> to read word
     * @param address memory location address to read
     * @return read data or <code>Computer.BUS_ERROR</code> if given address is
     * not mapped to memory or register
     */
    public int readMemory(boolean isByteMode, int address) {
        int value = computer.readMemory(isByteMode, address);
        if (value == Computer.BUS_ERROR) {
            setBusError();
        }
        return value;
    }

    /**
     * Write byte or word to given memory location.
     * @param isByteMode <code>true</code> to write byte, <code>false</code> to write word
     * @param address memory location address to write
     * @param value value to write
     * @return <code>true</code> if value successfully written or <code>false</code> if
     * given address is not mapped to memory or register
     */
    public boolean writeMemory(boolean isByteMode, int address, int value) {
        boolean isWritten = computer.writeMemory(isByteMode, address, value);
        if (!isWritten) {
            setBusError();
        }
        return isWritten;
    }

    /**
     * Reset processor state.
     */
    public void reset() {
        clearFlags();
        initDevices();
        int sel1RegisterValue = readMemory(false, REG_SEL1);
        if (sel1RegisterValue != Computer.BUS_ERROR) {
            writeRegister(false, PC, sel1RegisterValue & 0177400);
        } else {
            // FIXME Deal with bus error while read startup address
        }
        time += RESET_TIME;
    }

    /**
     * Clear CPU flags.
     */
    private void clearFlags() {
        clearBusError();
        clearDeferredTraceTrap();
        clearHaltMode();
        clearInterruptWaitMode();
        clearReservedOpcodeFetched();
    }

    /**
     * Initialize computer devices.
     */
    public void initDevices() {
        clearIrq1Request();
        clearIrq2Request();
        clearVirqRequest();
        computer.initDevices();
        setPswState((short) 0340);
    }

    /**
     * Fetch instruction pointed by PC and increment PC by 2.
     * @return fetched instruction code or BUS_ERROR in case of error
     */
    private int fetchInstruction() {
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
    private Opcode decodeInstruction(int instruction) {
        return opcodesTable[instruction & 0177777];
    }

    /**
     * Checks is instructions executing allowed
     * (CPU is not in WAIT mode, no unhandled bus errors).
     * @return <code>true</code> if instructions executing is allowed,
     * <code>false</code> if not allowed
     */
    private boolean isInstructionsExecutingAllowed() {
        return !isInterruptWaitMode() && !isBusError();
    }

    /**
     * Process pending interrupt requests.
     */
    private void processPendingInterrupts() {
        if (isBusError()) {
            // Bus error handling
            if (processTrap(TRAP_VECTOR_BUS_ERROR, true)) {
                clearBusError();
            }
            time += BUS_ERROR_TIMEOUT;
            // TODO double bus error handling
        } else if (isReservedOpcodeFetched()) {
            // Reserved opcode handling
            clearReservedOpcodeFetched();
            processTrap(TRAP_VECTOR_RESERVED_OPCODE, true);
            time += RESERVED_OPCODE_DECODE_TIME;
        } else if (isPswFlagSet(PSW_FLAG_T) && !isInterruptWaitMode()) {
            // Trace bit handling (if not in WAIT mode)
            if (!isDeferredTraceTrap()) {
                processTrap(TRAP_VECTOR_BPT, true);
            }
        } else {
            // Handle hardware interrupts if not masked
            boolean isInterruptHandled = false;
            if (!isPswFlagSet(PSW_FLAG_H)) { // bit 10 in PSW masks all hardware interrupts
                // Check for pending IRQ1
                if (!isPswFlagSet(PSW_FLAG_M) && isIrq1Requested()) { // bit 11 in PSW masks IRQ1
                    enterHaltMode();
                    clearIrq1Request();
                    isInterruptHandled = true;
                } else if (!isPswFlagSet(PSW_FLAG_P)) {
                    if (isIrq2Requested()) { // Check for pending radial interrupt request IRQ2
                        processTrap(TRAP_IRQ2, true);
                        clearIrq2Request();
                        isInterruptHandled = true;
                    } else if (isVirqRequested()) {  // Check for pending vector interrupt request
                        processTrap(getVirqAddress(), true);
                        clearVirqRequest();
                        isInterruptHandled = true;
                    }
                }
            }
            // Leave WAIT mode if interrupt was handled
            if (isInterruptHandled) {
                clearInterruptWaitMode();
            }
        }
        if (isInterruptWaitMode()) {
            time += Computer.SYNC_UPTIME_THRESHOLD; // FIXME
        }
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
                time += instructionOpcode.getExecutionTime();
                // Clear deferred trace trap flag if instruction was executed
                // while trace bit is set
                if (isPswFlagSet(PSW_FLAG_T) && instructionOpcode
                        .getOpcode() != RttOpcode.OPCODE) {
                    clearDeferredTraceTrap();
                }
            } else {
//                Log.d(TAG, "fetched unknown instruction: " + Integer.toOctalString(instruction)
//                        + ", PC: 0" + Integer.toOctalString(readRegister(false, PC)));
                setReservedOpcodeFetched();
            }
        }
    }

    /**
     * Execute next operation (instruction executing and/or interrupts processing).
     */
    public void executeNextOperation() {
        if (isInstructionsExecutingAllowed()) {
            executeSingleInstruction();
        }
        processPendingInterrupts();
    }

}
