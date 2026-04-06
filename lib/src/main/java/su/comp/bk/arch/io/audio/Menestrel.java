/*
 * Copyright (C) 2026 Victor Antonovich (v.antonovich@gmail.com)
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package su.comp.bk.arch.io.audio;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.state.State;

/**
 * Simple stereo sound card with two КР580ВИ53 (i8253 clone) timer chips attached to the peripheral port.
 * <a href="https://github.com/afiskon/retro-computers/tree/main/bk001x/menestrel">Menestrel replica project</a>
 * <p>
 * КР580ВИ53 emulation is based on MAME's pit8253.cpp (BSD-3-Clause).
 */
public class Menestrel extends AudioOutput<Menestrel.MenestrelCommand> {
    private final static int[] ADDRESSES = { Cpu.REG_SEL2 };

    public static final String OUTPUT_NAME = "menestrel";

    /** Timers clock frequency in Hz */
    public static final int TIMER_CLOCK_FREQUENCY = 1000000;

    /**
     * Stereo cross-mix percentage: this fraction of each channel's output is
     * blended into the opposite channel, simulating a small amount of crosstalk.
     */
    public static final int CHANNELS_CROSS_MIX_PERCENT = 7;

    // Both i8253 GATE0/1/2 pins are connected to peripheral port pin DO15
    private final static int PIN_GATE = (1 << 15);
    // Both i8253 nWR pins are connected to peripheral port pin DO12
    private final static int PIN_WRITE = (1 << 12);
    // Left channel i8253 nCS pin is connected to peripheral port pin DO11
    private final static int PIN_SELECT_RIGHT = (1 << 11);
    // Right channel i8253 nCS pin is connected to peripheral port pin DO10
    private final static int PIN_SELECT_LEFT = (1 << 10);

    private boolean lastGatePinState = true;
    private boolean lastWritePinState = true;
    private int lastPinStates;

    // State key prefix
    private static final String STATE_PREFIX = "Menestrel";

    private final Timer53 leftTimer;
    private final Timer53 rightTimer;

    /**
     * Emulation of a single counter inside a КР580ВИ53 / i8253 timer chip.
     * <p>
     * Supports all six operating modes (0-5). Based on MAME's pit8253.cpp.
     */
    static class Counter53 {
        // Fixed-point scaling: one audio sample = STEP fixed-point units
        static final int STEP = 0x8000;

        // Phase state machine (mirrors MAME pit8253.cpp m_phase field)
        private static final int PHASE_UNLOADED = 0; // no control word / count written yet
        private static final int PHASE_LOAD = 1; // count written, pending load on next clock
        private static final int PHASE_HIGH = 2; // output high / active counting phase
        private static final int PHASE_LOW = 3; // output low / terminal-count phase

        // State key suffixes
        private static final String KEY_CTRL = "ctrl";
        private static final String KEY_PHASE = "phase";
        private static final String KEY_COUNT_REG = "count_reg";
        private static final String KEY_VALUE_REG = "value_reg";
        private static final String KEY_OUTPUT_STATE = "output_state";
        private static final String KEY_WAIT_MSB_VALUE = "wait_msb_value";
        private static final String KEY_LSB_VALUE = "lsb_value";
        private static final String KEY_FP_COUNT = "fp_count";

        /** Lower 6 bits of the written control byte (access mode + operating mode + BCD). */
        private int ctrlWord;

        /** Current phase (PHASE_* constants). */
        private int phase;

        /** Count register (CR): the value most recently written by the CPU. */
        private int countReg;

        /** Counter element (CE): the value transferred from CR at each reload. */
        private int valueReg;

        /** Current output state: 0 (low) or 1 (high). */
        private int outputState;

        /** True when the next count byte to expect is the MSB (RW mode 3 only). */
        private boolean isWaitHighByteValue;

        /** Stored LSB byte while waiting for the MSB in RW mode 3. */
        private int lowByteValue;

        /** Remaining fixed-point units until the next output transition. */
        private int fpCount;

        /**
         * Fixed-point duration of the high output phase.
         * Mode 3: ceil(N/2) i8253 ticks; Mode 2: N-1 ticks; others: N ticks.
         */
        private int fpPeriodHi;

        /**
         * Fixed-point duration of the low output phase.
         * Mode 3: floor(N/2) i8253 ticks; all others: 1 i8253 tick.
         */
        private int fpPeriodLo;

        /**
         * Fixed-point units consumed per audio sample.
         * {@code updateStep = STEP * sampleRate / TIMER_CLOCK_FREQUENCY}
         */
        private int updateStep;

        Counter53(int updateStep) {
            this.updateStep = updateStep;
            reset();
        }

        void reset() {
            ctrlWord = 0;
            phase = PHASE_UNLOADED;
            countReg = 0;
            valueReg = 0;
            outputState = 1; // high is a safe default before programming
            isWaitHighByteValue = false;
            lowByteValue = 0;
            fpCount = 0;
            fpPeriodHi = 0;
            fpPeriodLo = 0;
        }

        void setUpdateStep(int updateStep) {
            this.updateStep = updateStep;
        }

        /**
         * Access / RW mode: bits [5:4] of the stored control word.
         * 1=LSB only, 2=MSB only, 3=LSB then MSB.
         */
        private int access() {
            return (ctrlWord >> 4) & 0x03;
        }

        /**
         * Operating mode: bits [3:1] of the stored control word.
         * Modes 6 and 7 are aliased to modes 2 and 3 respectively (per i8253 spec).
         */
        private int mode() {
            return (ctrlWord >> 1) & (((ctrlWord & 0x04) != 0) ? 0x03 : 0x07);
        }

        /**
         * Process a write to the i8253 control register for this counter.
         * {@code data} is the full 8-bit control byte (SC bits already decoded by
         * {@link Timer53}); the lower 6 bits are stored as the control word.
         * Mirrors MAME's {@code control_w_deferred()}.
         */
        void writeControlWord(int data) {
            ctrlWord = data & 0x3F;
            isWaitHighByteValue = false;
            phase = PHASE_UNLOADED;
            // Mode 0 starts with output low; all other modes start with output high
            outputState = (mode() != 0) ? 1 : 0;
            fpCount = 0;
            fpPeriodHi = 0;
            fpPeriodLo = 0;
        }

        /**
         * Process a write of one count byte to this counter's data register.
         * Mirrors MAME's {@code count_w_deferred()}.
         */
        void writeCountByte(int data) {
            int mode = mode();
            int fullCount = -1;

            switch (access()) {
                case 1: // LSB only
                    fullCount = data;
                    break;
                case 2: // MSB only
                    fullCount = data << 8;
                    break;
                case 3: // LSB first, then MSB
                    if (!isWaitHighByteValue) {
                        lowByteValue = data;
                        if (mode == 0) {
                            // Writing the LSB in mode 0 aborts the current count cycle
                            phase  = PHASE_UNLOADED;
                            outputState = 0;
                        }
                    } else {
                        fullCount = lowByteValue | (data << 8);
                    }
                    isWaitHighByteValue = !isWaitHighByteValue;
                    break;
                default:
                    break;
            }

            if (fullCount >= 0) {
                doLoadCount(fullCount);
                if (mode == 0) {
                    outputState = 0; // output goes low when count is committed
                }
            }
        }

        /**
         * React to a GATE rising edge.
         * Mirrors the gate edge handling in MAME's {@code gate_w_deferred()}.
         */
        void onGateRise() {
            if (phase == PHASE_UNLOADED) {
                return;
            }
            int mode = mode();
            if (mode == 1 || mode == 5) {
                // Hardware-triggered modes: rising edge starts / restarts the one-shot
                phase = PHASE_LOAD;
            } else if ((mode == 2 || mode == 3) && phase >= PHASE_HIGH) {
                // Repeating modes: rising edge causes an immediate reload
                phase = PHASE_LOAD;
            }
        }

        /**
         * Advance this counter by one audio sample period and return the weighted
         * average output level in the range [0, {@link #STEP}].
         *
         * @param gateState current GATE pin level (shared across all counters on the chip)
         */
        int computeSample(boolean gateState) {
            int mode = mode();

            // PHASE_LOAD: count has been written — load CE from CR and start counting
            if (phase == PHASE_LOAD) {
                loadCounterValue();
                phase   = PHASE_HIGH;
                outputState = (mode == 0) ? 0 : 1;
                fpCount = fpPeriodHi;
            }

            // PHASE_UNLOADED: counter not yet configured
            if (phase == PHASE_UNLOADED) {
                return outputState * STEP;
            }

            // Gate = 0 is level-sensitive for modes 0, 2, 3, 4; edge-only for modes 1, 5
            if (!gateState && mode != 1 && mode != 5) {
                if (mode == 3) {
                    outputState = 1; // mode 3: gate low forces output high immediately
                }
                return outputState * STEP;
            }

            // Non-repeating modes that have finished — output is latched high
            if (phase == PHASE_LOW && (mode == 0 || mode == 1)) {
                return STEP;
            }

            return advanceCounter(mode);
        }

        /**
         * Store a new count value and advance the phase.
         * Mirrors MAME's {@code load_count()}.
         */
        private void doLoadCount(int newCount) {
            int mode = mode();

            // Special-case per MAME: count=1 is adjusted in modes 2 and 3
            if (newCount == 1) {
                if (mode == 2) {
                    newCount = 2;
                }
                else if (mode == 3) {
                    newCount = 0; // binary 0 means 65536
                }
            }

            countReg = newCount;

            if (mode == 2 || mode == 3) {
                // First write starts counting; subsequent writes take effect at the
                // next reload boundary (picked up by the next loadCounterValue() call)
                if (phase == PHASE_UNLOADED) {
                    phase = PHASE_LOAD;
                }
            } else if (mode == 0 || mode == 4) {
                // Software-triggered modes restart immediately on every count write
                phase = PHASE_LOAD;
            }
            // Modes 1 and 5 are gated — phase advances only on a rising GATE edge
        }

        /**
         * Transfer the count register (CR) to the counter element (CE) and
         * recompute the fixed-point half-period lengths.
         * Mirrors MAME's {@code load_counter_value()}.
         */
        private void loadCounterValue() {
            valueReg = countReg;
            recomputePeriods();
        }

        /**
         * Recompute {@link #fpPeriodHi} and {@link #fpPeriodLo} from the current
         * counter element ({@link #valueReg}) and operating mode.
         * <p>
         * Mode 3 (square wave) period derivation mirrors MAME's simulate():
         * <pre>
         *   HIGH phase ends when elapsed &ge; (adjusted_value + 1) &gt;&gt; 1
         *   LOW  phase ends when elapsed &ge;  adjusted_value       &gt;&gt; 1
         * </pre>
         */
        private void recomputePeriods() {
            int adj = adjustedCount();
            int mode = mode();

            switch (mode) {
                case 3: // Square wave: high = ceil(N/2) ticks, low = floor(N/2) ticks
                    fpPeriodHi = ((adj + 1) >> 1) * updateStep;
                    fpPeriodLo = (adj >> 1) * updateStep;
                    break;
                case 2: // Rate generator: high = N-1 ticks, low = 1 tick
                    fpPeriodHi = Math.max(1, adj - 1) * updateStep;
                    fpPeriodLo = updateStep;
                    break;
                default: // Modes 0, 1, 4, 5: count down for N ticks, then a 1-tick pulse
                    fpPeriodHi = adj * updateStep;
                    fpPeriodLo = updateStep;
                    break;
            }

            if (fpPeriodHi <= 0) {
                fpPeriodHi = updateStep;
            }
            if (fpPeriodLo <= 0) {
                fpPeriodLo = updateStep;
            }
        }

        /**
         * Return the effective count, treating binary 0 as 65536 (0x10000).
         * Mirrors MAME's {@code adjusted_count()}.
         */
        private int adjustedCount() {
            int v = valueReg & 0xFFFF;
            return (v == 0) ? 0x10000 : v;
        }

        /**
         * Advance the fixed-point accumulator by one audio sample ({@link #STEP} units)
         * and compute the weighted average output level for that sample.
         * <p>
         * Multiple transitions can occur within a single sample period (e.g. at very
         * high counter frequencies); the sub-sample fractions are tracked via
         * {@code vola} to maintain accurate pitch and duty cycle.
         */
        private int advanceCounter(int mode) {
            int vola = 0;
            int left = STEP;

            while (left > 0) {
                int consume = Math.min(fpCount, left);
                if (outputState == 1) vola += consume;
                fpCount -= consume;
                left -= consume;

                if (fpCount <= 0) {
                    switch (mode) {
                        case 0: // Output goes high at terminal count and stays
                            outputState = 1;
                            phase = PHASE_LOW;
                            fpCount = STEP;
                            left = 0;
                            break;

                        case 1: // One-shot ends; output goes high until re-triggered
                            outputState = 1;
                            phase = PHASE_LOW;
                            fpCount = STEP;
                            left = 0;
                            break;

                        case 2: // Rate generator: low pulse, then reload and go high
                            if (phase == PHASE_HIGH) {
                                outputState = 0;
                                phase = PHASE_LOW;
                                fpCount += fpPeriodLo;
                            } else {
                                loadCounterValue(); // picks up any newly written count
                                outputState = 1;
                                phase = PHASE_HIGH;
                                fpCount += fpPeriodHi;
                            }
                            break;

                        case 3: // Square wave: reload at every half-period boundary
                            if (phase == PHASE_HIGH) {
                                loadCounterValue(); // may pick up a new count
                                outputState = 0;
                                phase = PHASE_LOW;
                                fpCount += fpPeriodLo;
                            } else {
                                loadCounterValue(); // may pick up a new count
                                outputState = 1;
                                phase = PHASE_HIGH;
                                fpCount += fpPeriodHi;
                            }
                            break;

                        case 4: // Software triggered strobe: N ticks high, 1 tick low, stop
                            if (phase == PHASE_HIGH) {
                                outputState = 0;
                                phase = PHASE_LOW;
                                fpCount += fpPeriodLo;
                            } else {
                                outputState = 1;
                                phase = PHASE_UNLOADED; // needs software retrigger
                                fpCount = STEP;
                                left = 0;
                            }
                            break;

                        case 5: // Hardware triggered strobe: same, but gate-retriggered
                            if (phase == PHASE_HIGH) {
                                outputState = 0;
                                phase = PHASE_LOW;
                                fpCount += fpPeriodLo;
                            } else {
                                outputState = 1;
                                phase = PHASE_UNLOADED; // needs gate rising edge
                                fpCount = STEP;
                                left = 0;
                            }
                            break;

                        default:
                            left = 0;
                            break;
                    }

                    // Safety guard: prevent infinite loop if a period collapsed to zero
                    if (fpCount <= 0 && left > 0) {
                        fpCount = updateStep;
                        break;
                    }
                }
            }

            return vola;
        }

        void saveState(State outState, String prefix) {
            outState.putInt(prefix + KEY_CTRL, ctrlWord);
            outState.putInt(prefix + KEY_PHASE, phase);
            outState.putInt(prefix + KEY_COUNT_REG, countReg);
            outState.putInt(prefix + KEY_VALUE_REG, valueReg);
            outState.putInt(prefix + KEY_OUTPUT_STATE, outputState);
            outState.putBoolean(prefix + KEY_WAIT_MSB_VALUE, isWaitHighByteValue);
            outState.putInt(prefix + KEY_LSB_VALUE, lowByteValue);
            outState.putInt(prefix + KEY_FP_COUNT, fpCount);
            // fpPeriodHi / fpPeriodLo are recomputed from ctrlWord + valueReg on restore
        }

        void restoreState(State inState, String prefix, int newUpdateStep) {
            updateStep = newUpdateStep;
            ctrlWord = inState.getInt(prefix + KEY_CTRL, 0);
            phase = inState.getInt(prefix + KEY_PHASE, PHASE_UNLOADED);
            countReg = inState.getInt(prefix + KEY_COUNT_REG, 0);
            valueReg = inState.getInt(prefix + KEY_VALUE_REG, 0);
            outputState = inState.getInt(prefix + KEY_OUTPUT_STATE, 1);
            isWaitHighByteValue = inState.getBoolean(prefix + KEY_WAIT_MSB_VALUE, false);
            lowByteValue = inState.getInt(prefix + KEY_LSB_VALUE, 0);
            fpCount = inState.getInt(prefix + KEY_FP_COUNT, 0);
            recomputePeriods(); // derive fpPeriodHi / fpPeriodLo from restored state
        }
    }

    /**
     * Emulation of one КР580ВИ53 / i8253 programmable interval timer chip,
     * containing three independent {@link Counter53} counters.
     */
    static class Timer53 {
        private static final int NUM_COUNTERS = 3;

        private static final String KEY_GATE = "gate";

        private final Counter53[] counters = new Counter53[NUM_COUNTERS];

        /** GATE pin level (shared across all three counters). */
        private boolean gateState;

        Timer53(int updateStep) {
            for (int i = 0; i < NUM_COUNTERS; i++) {
                counters[i] = new Counter53(updateStep);
            }
            gateState = true;
        }

        void reset(int updateStep) {
            gateState = true;
            for (Counter53 c : counters) {
                c.setUpdateStep(updateStep);
                c.reset();
            }
        }

        /**
         * Write a byte to the chip.
         *
         * @param reg  i8253 address (0-2 = counter data, 3 = control register)
         * @param data byte value
         */
        void write(int reg, int data) {
            if (reg == 3) {
                // Control register: decode the counter select and dispatch
                int counterIdx = (data >> 6) & 0x03;
                if (counterIdx == 3) {
                    return; // read-back command, not needed for audio
                }
                int access = (data >> 4) & 0x03;
                if (access == 0) {
                    return;     // count-latch command, not needed for audio
                }
                counters[counterIdx].writeControlWord(data);
            } else {
                counters[reg].writeCountByte(data);
            }
        }

        /**
         * Update the GATE pin state and notify counters of any rising edge.
         * All three GATE inputs on this chip are tied to the same physical line.
         */
        void setGate(boolean newGateState) {
            boolean risingEdge = newGateState && !gateState;
            gateState = newGateState;
            if (risingEdge) {
                for (Counter53 c : counters) {
                    c.onGateRise();
                }
            }
        }

        /**
         * Compute one audio sample for this chip.
         * The three counter outputs are averaged to produce the channel signal.
         *
         * @return value in [0, {@link Counter53#STEP}] range
         */
        int computeOutput() {
            int total = 0;
            for (Counter53 c : counters) {
                total += c.computeSample(gateState);
            }
            return total / NUM_COUNTERS;
        }

        void saveState(State outState, String prefix) {
            outState.putBoolean(prefix + KEY_GATE, gateState);
            for (int i = 0; i < NUM_COUNTERS; i++) {
                counters[i].saveState(outState, prefix + "c" + i + "_");
            }
        }

        void restoreState(State inState, String prefix, int updateStep) {
            gateState = inState.getBoolean(prefix + KEY_GATE, true);
            for (int i = 0; i < NUM_COUNTERS; i++) {
                counters[i].restoreState(inState, prefix + "c" + i + "_", updateStep);
            }
        }
    }

    public static class MenestrelCommand extends AudioOutputUpdate {
        public enum Channel {
            NONE,
            LEFT,
            RIGHT,
            BOTH
        }
        private Channel channel;
        private int register;
        private int value;
        private boolean isGatePinStateChanged;
        private boolean gatePinState;

        @Override
        public String toString() {
            return "MenestrelCommand{" +
                    "channel=" + channel +
                    ", register=" + register +
                    ", value=" + value +
                    ", isGatePinStateChanged=" + isGatePinStateChanged +
                    ", gatePinState=" + gatePinState +
                    '}';
        }
    }

    public Menestrel(int sampleRate, int samplesBufferSize, Computer computer) {
        super(sampleRate, samplesBufferSize, computer);
        int updateStep = computeUpdateStep();
        leftTimer = new Timer53(updateStep);
        rightTimer = new Timer53(updateStep);
    }

    private int computeUpdateStep() {
        return Math.max(1, Counter53.STEP * getSampleRate() / TIMER_CLOCK_FREQUENCY);
    }

    @Override
    public String getName() {
        return OUTPUT_NAME;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public int getDefaultVolume() {
        return MIN_VOLUME; // Menestrel is muted by default
    }

    @Override
    public synchronized void init(long cpuTime, boolean isHardwareReset) {
        super.init(cpuTime, isHardwareReset);
        lastGatePinState = true;
        lastWritePinState = true;
        int updateStep = computeUpdateStep();
        leftTimer.reset(updateStep);
        rightTimer.reset(updateStep);
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        int pinStates = ~value & 0xFFFF;

        boolean writePinState = (pinStates & PIN_WRITE) != 0;
        boolean gatePinState = (pinStates & PIN_GATE) != 0;
        boolean isGatePinStateChanged = (lastGatePinState != gatePinState);

        boolean isCommandWritten = false;
        int commandRegister = 0;
        int commandValue = 0;
        MenestrelCommand.Channel commandChannel = MenestrelCommand.Channel.NONE;

        if (!writePinState) {
            // Store written pin states while nWR is low
            lastPinStates = pinStates;
        } else if (!lastWritePinState) {
            // Apply stored pin states on the nWR rising edge
            boolean isLeftChannelSelected  = (lastPinStates & PIN_SELECT_LEFT) == 0;
            boolean isRightChannelSelected = (lastPinStates & PIN_SELECT_RIGHT) == 0;
            if (isLeftChannelSelected || isRightChannelSelected) {
                isCommandWritten = true;
                commandChannel = (isLeftChannelSelected && isRightChannelSelected)
                        ? MenestrelCommand.Channel.BOTH
                        : (isLeftChannelSelected
                            ? MenestrelCommand.Channel.LEFT
                            : MenestrelCommand.Channel.RIGHT);
                commandRegister = (lastPinStates >> 8) & 0x03;
                commandValue = lastPinStates & 0xFF;
            }
        }

        if (isGatePinStateChanged || isCommandWritten) {
            putMenestrelCommand(isGatePinStateChanged, gatePinState,
                    commandChannel, commandRegister, commandValue, cpuTime);
        }

        lastGatePinState  = gatePinState;
        lastWritePinState = writePinState;

        return true;
    }

    private synchronized void putMenestrelCommand(boolean isGatePinStateChanged, boolean gatePinState,
                                                  MenestrelCommand.Channel channel,
                                                  int cmdRegister, int cmdValue,
                                                  long cmdTimestamp) {
        MenestrelCommand cmd = putAudioOutputUpdate();
        if (cmd == null) {
            return;
        }
        cmd.channel = channel;
        cmd.register = cmdRegister;
        cmd.value = cmdValue;
        cmd.timestamp = cmdTimestamp;
        cmd.isGatePinStateChanged = isGatePinStateChanged;
        cmd.gatePinState = gatePinState;
    }

    @Override
    public MenestrelCommand[] createAudioOutputUpdates(int numMenestrelCommands) {
        MenestrelCommand[] cmds = new MenestrelCommand[numMenestrelCommands];
        for (int i = 0; i < numMenestrelCommands; i++) {
            cmds[i] = new MenestrelCommand();
        }
        return cmds;
    }

    @Override
    protected synchronized void handleAudioOutputUpdate(MenestrelCommand cmd) {
        if (cmd.isGatePinStateChanged) {
            leftTimer.setGate(cmd.gatePinState);
            rightTimer.setGate(cmd.gatePinState);
        }
        if (cmd.channel != MenestrelCommand.Channel.NONE) {
            boolean applyLeft = (cmd.channel != MenestrelCommand.Channel.RIGHT);
            boolean applyRight = (cmd.channel != MenestrelCommand.Channel.LEFT);
            if (applyLeft) {
                leftTimer.write(cmd.register, cmd.value);
            }
            if (applyRight) {
                rightTimer.write(cmd.register, cmd.value);
            }
        }
    }

    @Override
    protected synchronized void writeSample(short[] sample) {
        int l = (int) ((long) leftTimer.computeOutput() * MAX_OUTPUT / Counter53.STEP);
        int r = (int) ((long) rightTimer.computeOutput() * MAX_OUTPUT / Counter53.STEP);
        sample[0] = (short) ((l * (100 - CHANNELS_CROSS_MIX_PERCENT)
                + r * CHANNELS_CROSS_MIX_PERCENT) / 100);
        sample[1] = (short) ((r * (100 - CHANNELS_CROSS_MIX_PERCENT)
                + l * CHANNELS_CROSS_MIX_PERCENT) / 100);
    }

    @Override
    public void saveState(State outState) {
        super.saveState(outState);
        leftTimer.saveState(outState, STATE_PREFIX + "#left_");
        rightTimer.saveState(outState, STATE_PREFIX + "#right_");
    }

    @Override
    public void restoreState(State inState) {
        super.restoreState(inState);
        int updateStep = computeUpdateStep();
        leftTimer.restoreState(inState, STATE_PREFIX + "#left_",  updateStep);
        rightTimer.restoreState(inState, STATE_PREFIX + "#right_", updateStep);
    }
}
