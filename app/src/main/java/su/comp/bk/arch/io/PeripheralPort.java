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

import su.comp.bk.arch.cpu.Cpu;

import android.os.Bundle;

/**
 * BK-0010 peripheral port.
 */
public class PeripheralPort implements Device {
    private final static int[] ADDRESSES = {Cpu.REG_SEL2 };

    // Current port state
    private int state;

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        resetState(cpuTime);
    }

    @Override
    public int read(long cpuTime, int address) {
        return getState();
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        // TODO
        return true;
    }

    @Override
    public void saveState(Bundle outState) {
        // Do nothing
    }

    @Override
    public void restoreState(Bundle inState) {
        // Do nothing
    }

    private void resetState(long cpuTime) {
        setState(0);
    }

    public synchronized int getState() {
        return state;
    }

    public synchronized void setState(int state) {
        this.state = state;
    }
}
