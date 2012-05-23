/*
 * Created: 23.04.2012
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

import android.util.Log;
import android.view.KeyEvent;
import su.comp.bk.arch.cpu.Cpu;

/**
 * BK-0010 keyboard controller (К1801ВП1-014).
 */
public class KeyboardController implements Device {

    private static final String TAG = KeyboardController.class.getName();

    /** Keyboard status register address */
    public final static int STATUS_REGISTER_ADDRESS = 0177660;
    // Keyboard status register - vector interrupt masking flag (read/write)
    private final static int STATUS_VIRQ_MASK = (1 << 6);
    // Keyboard status register - data status flag (1 if pressed key code is written to data register,
    // 0 if pressed key code read from data register, read only)
    private final static int STATUS_DATA_READY = (1 << 7);

    /** Keyboard data register (bits 0-6 - pressed key code value, read only) */
    public final static int DATA_REGISTER_ADDRESS = 0177662;

    // Key pressed state flag in SEL1 register (0 - key is pressed, 1 - not pressed, read only)
    private final static int SEL1_REGISTER_KEY_PRESSED = (1 << 6);

    private final static int[] ADDRESSES = {
        Cpu.REG_SEL1, STATUS_REGISTER_ADDRESS, DATA_REGISTER_ADDRESS
    };

    // Normal (AR2 key is not pressed) VIRQ address
    private final static int VIRQ_ADDRESS_NORMAL = 060;
    // VIRQ address when AR2 key is pressed
    private final static int VIRQ_ADDRESS_AR2 = 0274;

    // AR2 (Alternative Register 2) key is pressed flag
    private boolean isAr2Pressed;

    // Status register value
    private int statusRegister;

    // Pressed key code data register value
    private int dataRegister;

    // Key pressed state flag in SEL1 register
    private boolean isKeyPressed;

    private final Cpu cpu;

    public KeyboardController(Cpu cpu) {
        this.cpu = cpu;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init() {
        setKeyPressed(false);
        writeDataRegister(0);
        setStatusRegisterDataReadyFlag(false);
        writeStatusRegister(STATUS_VIRQ_MASK);
    }

    protected boolean isKeyPressed() {
        return isKeyPressed;
    }

    protected void setKeyPressed(boolean isKeyPressed) {
        this.isKeyPressed = isKeyPressed;
    }

    private void setStatusRegisterDataReadyFlag(boolean isDataReady) {
        this.statusRegister = isDataReady ? (this.statusRegister | STATUS_DATA_READY)
                : (this.statusRegister & ~STATUS_DATA_READY);
        if (isDataReady) {
            if (isStatusRegisterVirqEnabled()) {
                cpu.requestVirq(isAr2Pressed ? VIRQ_ADDRESS_AR2 : VIRQ_ADDRESS_NORMAL);
            }
        } else {
            cpu.clearVirqRequest();
        }
    }

    private boolean isStatusRegisterVirqEnabled() {
        return (readStatusRegister() & STATUS_VIRQ_MASK) == 0;
    }

    private void writeStatusRegister(int value) {
        this.statusRegister = (value & STATUS_VIRQ_MASK) | (this.statusRegister & STATUS_DATA_READY);
    }

    private int readStatusRegister() {
        return this.statusRegister;
    }

    private void writeDataRegister(int value) {
        this.dataRegister = value;
        setStatusRegisterDataReadyFlag(true);
    }

    private int readDataRegister() {
        setStatusRegisterDataReadyFlag(false);
        return this.dataRegister;
    }

    @Override
    public int read(int address) {
        switch (address) {
            case STATUS_REGISTER_ADDRESS:
                return readStatusRegister();
            case DATA_REGISTER_ADDRESS:
                return readDataRegister();
            default:
                return isKeyPressed() ? 0 : SEL1_REGISTER_KEY_PRESSED;
        }
    }

    @Override
    public void write(boolean isByteMode, int address, int value) {
        switch (address & 0177776) {
            case STATUS_REGISTER_ADDRESS:
                writeStatusRegister(value);
                break;
            default:
                // Data register and corresponding SEL1 register bit are read only
                break;
        }
    }

    public boolean handleKeyCode(int keyCode, boolean isKeyPress) {
        Log.d(TAG, "handle key " + (isKeyPress ? "press" : "release") + ", code: " + keyCode);
        boolean isKeyCodeHandled = false;
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            setKeyPressed(isKeyPress);
            if (isKeyPress) {
                writeDataRegister(keyCode & 0177);
            }
            isKeyCodeHandled = true;
        }
        return isKeyCodeHandled;
    }

}
