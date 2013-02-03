/*
 * Created: 22.01.2013
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
package su.comp.bk.arch.io;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import android.os.Bundle;

/**
 * BK0011 system timer (50 Hz, connected to CPU IRQ2 pin).
 */
public class SystemTimer implements Device {

    /** Timer state register address */
    public final static int STATE_REGISTER_ADDRESS = 0177662;

    private final static int[] ADDRESSES = { STATE_REGISTER_ADDRESS };

    /** Timer frequency (in Hz) */
    public final static int TIMER_FREQUENCY = 50;

    /** Timer state enabled flag */
    public final static int STATE_ENABLED_FLAG = (1 << 14);

    // State save/restore: Timer interrupt enabled state
    private static final String STATE_IRQ_ENABLED = SystemTimer.class.getName() + "#irq_enabled";
    // State save/restore: Last timer event timestamp
    private static final String STATE_LAST_TIMER_EVENT_TIMESTAMP = SystemTimer.class.getName() +
            "#last_timer_event_timestamp";

    private final Cpu cpu;

    private final long timerPeriod;

    private boolean isInterruptEnabled = false;

    private long lastTimerEventTimestamp;

    public SystemTimer(Computer computer) {
        cpu = computer.getCpu();
        timerPeriod = computer.nanosToCpuTime(Computer.NANOSECS_IN_MSEC * 1000L / TIMER_FREQUENCY);
    }

    public boolean isInterruptEnabled() {
        return isInterruptEnabled;
    }

    public void setInterruptEnabled(boolean isEnabled) {
        this.isInterruptEnabled = isEnabled;
    }

    public long getLastTimerEventTimestamp() {
        return lastTimerEventTimestamp;
    }

    public void setLastTimerEventTimestamp(long timestamp) {
        this.lastTimerEventTimestamp = timestamp;
    }

    private int getTimerLogicLevel(long cpuTime) {
        return (int) ((cpuTime / timerPeriod) & 1);
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        setLastTimerEventTimestamp(cpuTime);
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putBoolean(STATE_IRQ_ENABLED, isInterruptEnabled());
        outState.putLong(STATE_LAST_TIMER_EVENT_TIMESTAMP, getLastTimerEventTimestamp());
    }

    @Override
    public void restoreState(Bundle inState) {
        setInterruptEnabled(inState.getBoolean(STATE_IRQ_ENABLED));
        setLastTimerEventTimestamp(inState.getLong(STATE_LAST_TIMER_EVENT_TIMESTAMP));
    }

    @Override
    public void timer(long cpuTime) {
        if (isInterruptEnabled()) {
            int currentTimerLogicLevel = getTimerLogicLevel(cpuTime);
            int lastTimerLogicLevel = getTimerLogicLevel(getLastTimerEventTimestamp());
            if (((currentTimerLogicLevel ^ lastTimerLogicLevel) & lastTimerLogicLevel) != 0) {
                // Timer logic level transition from high to low detected
                cpu.requestIrq2();
            }
        }
        setLastTimerEventTimestamp(cpuTime);
    }

    @Override
    public int read(long cpuTime, int address) {
        // write only device
        return 0;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        if (!isByteMode) {
            setInterruptEnabled((value & STATE_ENABLED_FLAG) == 0);
            return true;
        }
        return false;
    }

}
