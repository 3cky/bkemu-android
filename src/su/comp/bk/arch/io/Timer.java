/*
 * Created: 09.06.2012
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
package su.comp.bk.arch.io;

import android.os.Bundle;

/**
 * K1801VM1 on-chip timer (legacy of K1801VE1 MCU).
 */
public class Timer implements Device {

    /** Timer preset register address */
    public final static int PRESET_REGISTER_ADDRESS = 0177706;

    /** Timer preset register initial state */
    public static final int PRESET_REGISTER_INITIAL_STATE = 0104004;

    /** Maximum counter divider value (for preset register zero value) */
    public static final int MAX_COUNTER_DIVIDER_VALUE = 01000000;

    /** Timer counter register address */
    public final static int COUNTER_REGISTER_ADDRESS = 0177710;

    /** Timer control and status register address */
    public final static int CONTROL_REGISTER_ADDRESS = 0177712;

    /** Timer control and status register initial state */
    public static final int CONTROL_REGISTER_INITIAL_STATE = 0;

    /** Timer control and status register - timer is paused flag, read/write */
    public final static int CONTROL_TIMER_PAUSED = 1;
    /** Timer control and status register - wrap-around mode flag (0 - reload counter by preset value,
     * 1 - counting next from 0177777, read/write) */
    public final static int CONTROL_WRAPAROUND_MODE = 2;
    /** Timer control and status register - timer expiry monitor flag, read/write */
    public final static int CONTROL_EXPIRY_MONITOR = 4;
    /** Timer control and status register - one-shot mode flag, read/write */
    public final static int CONTROL_ONESHOT_MODE = 010;
    /** Timer control and status register - timer is started flag, read/write */
    public final static int CONTROL_TIMER_STARTED = 020;
    /** Timer control and status register - enable prescaler with divide ratio of 16 flag, read/write */
    public final static int CONTROL_PRESCALER_16 = 040;
    /** Timer control and status register - enable prescaler with divide ratio of 4 flag, read/write */
    public final static int CONTROL_PRESCALER_4 = 0100;
    /** Timer control and status register - timer expired flag, read/write */
    public final static int CONTROL_TIMER_EXPIRED = 0200;

    /** CPU clock prescaler divide ratio */
    public final static int PRESCALER = 128;

    // State save/restore: Preset register value
    private static final String STATE_PRESET_REGISTER = Timer.class.getName() +
            "#preset_reg";
    // State save/restore: Control register value
    private static final String STATE_CONTROL_REGISTER = Timer.class.getName() +
            "#control_reg";
    // State save/restore: Last timer settings change time (in CPU clock ticks)
    private static final String STATE_SETTINGS_CHANGE_TIME = Timer.class.getName() +
            "#settings_change_time";
    // State save/restore: Counter start value
    private static final String STATE_COUNTER_START_VALUE = Timer.class.getName() +
            "#counter_start_value";

    private final static int[] ADDRESSES = {
        PRESET_REGISTER_ADDRESS, COUNTER_REGISTER_ADDRESS, CONTROL_REGISTER_ADDRESS
    };

    // Timer preset register
    private int presetRegister;

    // Timer control register
    private int controlRegister;

    // Last timer settings change time (in CPU clock ticks)
    private long settingsChangeTime;

    // Counter register value at last settings change time value (preset value if
    // control register was changed or counter value if preset register was changed)
    private int counterStartValue;

    /**
     * Timer constructor.
     */
    public Timer() {
        setControlRegister(0L, CONTROL_REGISTER_INITIAL_STATE);
        setPresetRegister(0L, PRESET_REGISTER_INITIAL_STATE);
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        setControlRegister(cpuTime, CONTROL_REGISTER_INITIAL_STATE);
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putInt(STATE_PRESET_REGISTER, presetRegister);
        outState.putInt(STATE_CONTROL_REGISTER, controlRegister);
        outState.putInt(STATE_COUNTER_START_VALUE, counterStartValue);
        outState.putLong(STATE_SETTINGS_CHANGE_TIME, settingsChangeTime);
    }

    @Override
    public void restoreState(Bundle inState) {
        presetRegister = inState.getInt(STATE_PRESET_REGISTER);
        controlRegister = inState.getInt(STATE_CONTROL_REGISTER);
        counterStartValue = inState.getInt(STATE_COUNTER_START_VALUE);
        settingsChangeTime = inState.getLong(STATE_SETTINGS_CHANGE_TIME);
    }

    @Override
    public int read(long cpuTime, int address) {
        switch (address) {
            case PRESET_REGISTER_ADDRESS:
                return getPresetRegister();
            case CONTROL_REGISTER_ADDRESS:
                return getControlRegister(cpuTime);
            default:
                return getCounterRegister(cpuTime);
        }
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        switch (address & 0177776) {
            case PRESET_REGISTER_ADDRESS:
                setPresetRegister(cpuTime, value);
                break;
            case CONTROL_REGISTER_ADDRESS:
                setControlRegister(cpuTime, value);
                break;
            default:
                // Counter register is read only
                break;
        }
        return true;
    }

    private void updateSettingsChangeTime(long cpuTime) {
        settingsChangeTime = cpuTime;
    }

    private boolean isTimerEnabled() {
        return (controlRegister & CONTROL_TIMER_PAUSED) == 0
                && (controlRegister & CONTROL_TIMER_STARTED) != 0;
    }

    private int getPresetRegister() {
        return presetRegister;
    }

    private int getCounterDividerValue() {
        return (presetRegister > 0) ? presetRegister : MAX_COUNTER_DIVIDER_VALUE;
    }

    private void setPresetRegister(long cpuTime, int value) {
        this.presetRegister = value & 0177777;
        getControlRegister(cpuTime); // update control register state
        counterStartValue = getCounterRegister(cpuTime);
        updateSettingsChangeTime(cpuTime);
    }

    private int getControlRegister(long cpuTime) {
        if (isTimerEnabled()) {
            long timerTicksElapsed = getElapsedTimerTicks(cpuTime);
            if (timerTicksElapsed >= counterStartValue) {
                if ((controlRegister & CONTROL_WRAPAROUND_MODE) == 0) {
                    // Counter reloading with preset value mode
                    if ((controlRegister & CONTROL_ONESHOT_MODE) != 0) {
                        // One-shot mode counting completed, stop timer
                        controlRegister &= ~CONTROL_TIMER_STARTED;
                    }
                    // Set timer expire flag if enabled
                    if ((controlRegister & CONTROL_EXPIRY_MONITOR) != 0) {
                        controlRegister |= CONTROL_TIMER_EXPIRED;
                    }
                }
            }
        }
        return controlRegister;
    }

    private void setControlRegister(long cpuTime, int value) {
        this.controlRegister = value | 0177400;
        counterStartValue = getCounterDividerValue();
        updateSettingsChangeTime(cpuTime);
    }

    private int getCounterRegister(long cpuTime) {
        int value = getPresetRegister();
        if (isTimerEnabled()) {
            long timerTicksElapsed = getElapsedTimerTicks(cpuTime);
            if (counterStartValue > timerTicksElapsed) {
                // First counting pass in progress
                value = (int) (counterStartValue - timerTicksElapsed);
            } else {
                if ((controlRegister & CONTROL_WRAPAROUND_MODE) == 0) {
                    // Counter reloading by preset value
                    if ((controlRegister & CONTROL_ONESHOT_MODE) == 0) {
                        value = getCounterDividerValue() - (int) ((timerTicksElapsed -
                                    counterStartValue) % getCounterDividerValue());
                        if (value == 0) {
                            value = getPresetRegister();
                        }
                    } // else {
                        // In one-shot mode counter is reloaded by preset register value
                    // }
                } else {
                    // Wrap-around mode, one-shot mode disabled
                    value = MAX_COUNTER_DIVIDER_VALUE - (int) ((timerTicksElapsed -
                                counterStartValue) % MAX_COUNTER_DIVIDER_VALUE);
                }
            }
        }
        return value & 0177777;
    }

    private long getElapsedTimerTicks(long cpuTime) {
        long prescalerValue = PRESCALER;
        if ((controlRegister & CONTROL_PRESCALER_4) != 0) {
            prescalerValue *= 4;
        }
        if ((controlRegister & CONTROL_PRESCALER_16) != 0) {
            prescalerValue *= 16;
        }
        long timerTicksElapsed = (cpuTime - settingsChangeTime) / prescalerValue;
        return timerTicksElapsed;
    }

}
