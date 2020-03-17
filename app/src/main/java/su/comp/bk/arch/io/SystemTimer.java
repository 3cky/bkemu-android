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
import android.os.Bundle;

/**
 * BK0011 system timer (50 Hz, connected to CPU IRQ2 pin).
 */
public class SystemTimer implements Device, VideoController.FrameSyncListener {

    /** Timer state register address */
    public final static int STATE_REGISTER_ADDRESS = 0177662;

    private final static int[] ADDRESSES = { STATE_REGISTER_ADDRESS };

    /** Timer state enabled flag */
    public final static int STATE_ENABLED_FLAG = (1 << 14);

    // State save/restore: Timer interrupt enabled state
    private static final String STATE_IRQ_ENABLED = SystemTimer.class.getName() + "#irq_enabled";

    private final Computer computer;

    private boolean isInterruptEnabled = false;

    public SystemTimer(Computer computer) {
        this.computer = computer;
    }

    public boolean isInterruptEnabled() {
        return isInterruptEnabled;
    }

    public void setInterruptEnabled(boolean isEnabled) {
        this.isInterruptEnabled = isEnabled;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        // Do nothing
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putBoolean(STATE_IRQ_ENABLED, isInterruptEnabled());
    }

    @Override
    public void restoreState(Bundle inState) {
        setInterruptEnabled(inState.getBoolean(STATE_IRQ_ENABLED));
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

    @Override
    public void verticalSync(long frameNumber) {
        if (isInterruptEnabled()) {
            computer.getCpu().requestIrq2();
        }
    }
}
