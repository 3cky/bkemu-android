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

import android.os.Bundle;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.memory.Bk11MemoryManager;
import su.comp.bk.ui.keyboard.KeyboardManager.BkButton;

/**
 * BK-0010 keyboard controller (К1801ВП1-014).
 */
public class KeyboardController implements Device {
    /** Keyboard status register address */
    public final static int STATUS_REGISTER_ADDRESS = 0177660;
    // Keyboard status register - vector interrupt masking flag (read/write)
    private final static int STATUS_VIRQ_MASK = (1 << 6);
    // Keyboard status register - data status flag (1 if pressed key code is written to data register,
    // 0 if pressed key code read from data register, read only)
    private final static int STATUS_DATA_READY = (1 << 7);

    /** Keyboard data register (bits 0-6 - pressed key code value, read only) */
    public final static int DATA_REGISTER_ADDRESS = 0177662;

    // Button pressed state flag in SEL1 register (0 - key is pressed, 1 - not pressed, read only)
    private final static int SEL1_REGISTER_BUTTON_PRESSED = (1 << 6);

    // BK-0011M STOP button enabled flag in SEL1 register (1 - disabled, 0 - enabled, write only)
    private final static int SEL1_BK11M_STOP_BUTTON_ENABLED = (1 << 12);

    private final static int[] ADDRESSES = {
        Cpu.REG_SEL1, STATUS_REGISTER_ADDRESS, DATA_REGISTER_ADDRESS
    };

    // Normal (AR2 key is not pressed) VIRQ address
    private final static int VIRQ_ADDRESS_NORMAL = 060;
    // VIRQ address when AR2 key is pressed
    private final static int VIRQ_ADDRESS_AR2 = 0274;

    // State save/restore: Status register value
    private static final String STATE_STATUS_REGISTER =
            KeyboardController.class.getName() + "#status_reg";
    // State save/restore: STOP button is blocked flag value
    private static final String STATE_STOP_BUTTON_ENABLED_FLAG =
            KeyboardController.class.getName() + "#stop_button_enabled";

    // Is STOP button enabled flag
    private boolean isStopButtonEnabled = true;

    // Status register value
    private int statusRegister;

    // Pressed key code data register value
    private int dataRegister;

    /** Minimum key press time (in nanoseconds) */
    private final static long MIN_KEY_PRESS_TIME = (100L * Computer.NANOSECS_IN_MSEC);
    // Button pressed state flag in SEL1 register
    private boolean isButtonPressed;
    // Last button press timestamp (in CPU clock ticks)
    private long lastButtonPressTimestamp = -1L;
    // Last pressed button
    private BkButton lastPressedButton;

    private final Computer computer;

    private final boolean isComputerBk11m;

    public KeyboardController(Computer computer) {
        this.computer = computer;
        this.isComputerBk11m = computer.getConfiguration().isMemoryManagerPresent();
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        setButtonPressed(cpuTime, false);
        writeDataRegister(0);
        setStatusRegisterDataReadyFlag(false);
        writeStatusRegister(STATUS_VIRQ_MASK);
    }

    private boolean isStopButtonEnabled() {
        return isStopButtonEnabled;
    }

    private void setStopButtonEnabled(boolean isEnabled) {
        isStopButtonEnabled = isEnabled;
    }

    public void stopButtonPressed() {
        if (isStopButtonEnabled()) {
            computer.getCpu().requestIrq1();
        }
    }

    private boolean isButtonPressed() {
        return isButtonPressed;
    }

    // Check button was pressed in last MIN_KEY_PRESS_TIME
    private boolean checkButtonPressed(long cpuTime) {
        return isButtonPressed() || (lastButtonPressTimestamp >= 0 && computer.cpuTimeToNanos(
                cpuTime - lastButtonPressTimestamp) <= MIN_KEY_PRESS_TIME);
    }

    private void setButtonPressed(long cpuTime, boolean isPressed) {
        isButtonPressed = isPressed;
        if (isPressed) {
            lastButtonPressTimestamp = cpuTime;
        }
    }

    private void setButtonPressed(boolean isPressed) {
        setButtonPressed(computer.getCpu().getTime(), isPressed);
    }

    public void handleButton(BkButton bkButton, int bkKeyCode, boolean isPressed) {
        if (isPressed) {
            // Ignore repeated button press events if already in pressed state
            if (!isButtonPressed()) {
                // Write new key code to data register only if previous key code was read
                if (!isStatusRegisterDataReady()) {
                    writeDataRegister(bkKeyCode);
                }
                lastPressedButton = bkButton;
                setButtonPressed(true);
            }
        } else if (bkButton == lastPressedButton) {
            setButtonPressed(false);
        }
    }

    private void setStatusRegisterDataReadyFlag(boolean isDataReady) {
        statusRegister = isDataReady ? (statusRegister | STATUS_DATA_READY)
                : (statusRegister & ~STATUS_DATA_READY);
        if (isDataReady) {
            if (isStatusRegisterVirqEnabled()) {
                computer.getCpu().requestVirq((dataRegister & 0200) != 0
                        ? VIRQ_ADDRESS_AR2 : VIRQ_ADDRESS_NORMAL);
            }
        } else {
            computer.getCpu().clearVirqRequest();
        }
    }

    private boolean isStatusRegisterVirqEnabled() {
        return (readStatusRegister() & STATUS_VIRQ_MASK) == 0;
    }

    public boolean isStatusRegisterDataReady() {
        return (readStatusRegister() & STATUS_DATA_READY) != 0;
    }

    private void writeStatusRegister(int value) {
        statusRegister = (value & STATUS_VIRQ_MASK) | (statusRegister & STATUS_DATA_READY);
    }

    private int readStatusRegister() {
        return statusRegister;
    }

    public void writeDataRegister(int value) {
        dataRegister = value;
        setStatusRegisterDataReadyFlag(true);
    }

    private int readDataRegister() {
        setStatusRegisterDataReadyFlag(false);
        return dataRegister & 0177;
    }

    @Override
    public int read(long cpuTime, int address) {
        switch (address) {
            case STATUS_REGISTER_ADDRESS:
                return readStatusRegister();
            case DATA_REGISTER_ADDRESS:
                return readDataRegister();
            default:
                return checkButtonPressed(cpuTime) ? 0 : SEL1_REGISTER_BUTTON_PRESSED;
        }
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        boolean isWritten = false;
        switch (address & 0177776) {
            case STATUS_REGISTER_ADDRESS:
                if (isByteMode) {
                    int reg = readStatusRegister();
                    value |= (address & 1) == 0 ? reg & 0177400 : reg & 0377;
                }
                writeStatusRegister(value);
                isWritten = true;
                break;
            case Cpu.REG_SEL1:
                // Check for BK-0011M STOP button enabled flag state if register is selected
                if (isComputerBk11m && (value & Bk11MemoryManager.ENABLE_BIT) == 0) {
                    setStopButtonEnabled((value & SEL1_BK11M_STOP_BUTTON_ENABLED) == 0);
                    isWritten = true;
                }
                break;
            default:
                // Data register is read only and not respond to the CPU bus write request
                break;
        }
        return isWritten;
    }

    @Override
    public void saveState(Bundle outState) {
        // Save VIRQ mask state from status register
        outState.putInt(STATE_STATUS_REGISTER, readStatusRegister() & STATUS_VIRQ_MASK);
        // Save STOP button enabled flag state
        outState.putBoolean(STATE_STOP_BUTTON_ENABLED_FLAG, isStopButtonEnabled());
    }

    @Override
    public void restoreState(Bundle inState) {
        writeStatusRegister(inState.getInt(STATE_STATUS_REGISTER));
        setStopButtonEnabled(inState.getBoolean(STATE_STOP_BUTTON_ENABLED_FLAG));
    }
}
