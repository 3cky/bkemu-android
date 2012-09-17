/*
 * Created: 23.04.2012
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

import su.comp.bk.arch.Computer;
import android.os.Bundle;

/**
 * BK-0010 peripheral port.
 */
public class PeripheralPort implements Device {

    public final static int DATA_REGISTER_ADDRESS = 0177714;

    public final static int JOYSTICK_BUTTON1 = 1;
    public final static int JOYSTICK_BUTTON2 = 2;
    public final static int JOYSTICK_BUTTON3 = 4;
    public final static int JOYSTICK_BUTTON4 = 010;
    public final static int JOYSTICK_LEFT = 01000;
    public final static int JOYSTICK_DOWN = 040;
    public final static int JOYSTICK_RIGHT = 020;
    public final static int JOYSTICK_UP = 02000;

    /** Joystick buttons pressing delay (in nanoseconds) */
    public final static long JOYSTICK_PRESS_DELAY = (250L * Computer.NANOSECS_IN_MSEC);

    /** Joystick buttons pressing scroll threshold (in pixels) */
    public final static float JOYSTICK_PRESS_THRESHOLD = 1f;

    private final static int[] ADDRESSES = { DATA_REGISTER_ADDRESS };

    private final Computer computer;

    // Last state change timestamp (in CPU clock ticks)
    private long stateTimestamp;
    // Current port state
    private int state;

    public PeripheralPort(Computer computer) {
        this.computer = computer;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        resetState(cpuTime);
    }

    @Override
    public int read(long cpuTime, int address) {
        return getState(cpuTime);
    }

    @Override
    public void write(long cpuTime, boolean isByteMode, int address, int value) {
        // TODO
    }

    @Override
    public void saveState(Bundle outState) {
        // Do nothing
    }

    @Override
    public void restoreState(Bundle inState) {
        // Do nothing
    }

    private long getStateTimestamp() {
        return this.stateTimestamp;
    }

    private void setStateTimestamp(long timestamp) {
        this.stateTimestamp = timestamp;
    }

    private void resetState(long cpuTime) {
        setState(cpuTime, 0);
    }

    private synchronized int getState(long cpuTime) {
        if (computer.cpuTimeToNanos(cpuTime - getStateTimestamp()) > JOYSTICK_PRESS_DELAY) {
            resetState(cpuTime);
        }
        return state;
    }

    private synchronized void setState(long cpuTime, int state) {
        this.state = state;
        setStateTimestamp(cpuTime);
    }

    public boolean handleMotionEvent(long cpuTime, float distanceX, float distanceY) {
        int nextState = getState(cpuTime);
        nextState &= ~(JOYSTICK_LEFT | JOYSTICK_RIGHT | JOYSTICK_UP | JOYSTICK_DOWN);
        if (Math.abs(distanceX) > JOYSTICK_PRESS_THRESHOLD) {
            nextState |= (Math.signum(distanceX) > 0 ? JOYSTICK_LEFT : JOYSTICK_RIGHT);
        }
        if (Math.abs(distanceY) > JOYSTICK_PRESS_THRESHOLD) {
            nextState |= (Math.signum(distanceY) > 0 ? JOYSTICK_UP : JOYSTICK_DOWN);
        }
        setState(cpuTime, nextState);
        return true;
    }

    public boolean handleSingleTapEvent(long cpuTime) {
        setState(cpuTime, (getState(cpuTime) | JOYSTICK_BUTTON1));
        return true;
    }

    public boolean handleDoubleTapEvent(long cpuTime) {
        setState(cpuTime, (getState(cpuTime) | JOYSTICK_BUTTON2));
        return true;
    }
}
