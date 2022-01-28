/*
 * Copyright (C) 2021 Victor Antonovich (v.antonovich@gmail.com)
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

package su.comp.bk.arch.io.disk;

import android.os.Bundle;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.io.Device;

/**
 * SMK512 IDE drive(s) controller.
 */
public class SmkIdeController extends IdeController implements Device {
    public static final int REG_DATA = 0177756;
    public static final int REG_ERROR = 0177754;
    public static final int REG_SECTOR_COUNT = 0177752;
    public static final int REG_SECTOR_NUMBER = 0177750;
    public static final int REG_CYLINDER_LOW = 0177746;
    public static final int REG_CYLINDER_HIGH = 0177744;
    public static final int REG_COMP_1 = 0177742;
    public static final int REG_COMP_0 = 0177740;

    private final static int[] ADDRESSES = {REG_COMP_0, REG_COMP_1, REG_CYLINDER_HIGH,
            REG_CYLINDER_LOW, REG_SECTOR_NUMBER, REG_SECTOR_COUNT, REG_ERROR, REG_DATA};

    public SmkIdeController(Computer computer) {
        // Do nothing
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        if (isHardwareReset) {
            super.reset();
        }
    }

    @Override
    public void saveState(Bundle outState) {
        super.saveState(outState);
    }

    @Override
    public void restoreState(Bundle inState) {
        super.restoreState(inState);
    }

    @Override
    public int read(long cpuTime, int address) {
        int result = 0;

        switch (address) {
            case REG_DATA:
                result = readTaskRegister(IdeController.REG_DATA);
                break;
            case REG_ERROR:
                result = readTaskRegister(IdeController.REG_ERROR);
                break;
            case REG_SECTOR_COUNT:
                result = readTaskRegister(IdeController.REG_SECTOR_COUNT);
                break;
            case REG_SECTOR_NUMBER:
                result = readTaskRegister(IdeController.REG_SECTOR_NUMBER);
                break;
            case REG_CYLINDER_LOW:
                result = readTaskRegister(IdeController.REG_CYLINDER_LOW);
                break;
            case REG_CYLINDER_HIGH:
                result = readTaskRegister(IdeController.REG_CYLINDER_HIGH);
                break;
            case REG_COMP_1:
                result = readTaskRegister(IdeController.REG_DRIVE_HEAD) | (readAltStatusRegister() << 8);
                break;
            case REG_COMP_0:
                result = readTaskRegister(IdeController.REG_STATUS) | (readDriveAddressRegister() << 8);
                break;
        }

        return ~result & 0xFFFF;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        int wordAddress = address & 0177776;
        int invValue = ~value & 0xFFFF;

        switch (wordAddress) {
            case REG_DATA:
                writeTaskRegister(IdeController.REG_DATA, invValue);
                break;
            case REG_ERROR:
                writeTaskRegister(IdeController.REG_ERROR, invValue);
                break;
            case REG_SECTOR_COUNT:
                writeTaskRegister(IdeController.REG_SECTOR_COUNT, invValue);
                break;
            case REG_SECTOR_NUMBER:
                writeTaskRegister(IdeController.REG_SECTOR_NUMBER, invValue);
                break;
            case REG_CYLINDER_LOW:
                writeTaskRegister(IdeController.REG_CYLINDER_LOW, invValue);
                break;
            case REG_CYLINDER_HIGH:
                writeTaskRegister(IdeController.REG_CYLINDER_HIGH, invValue);
                break;
            case REG_COMP_1:
                if (address == REG_COMP_1) { // check full (byte) address
                    writeTaskRegister(IdeController.REG_DRIVE_HEAD, invValue);
                } else {
                    writeControlRegister(isByteMode ? invValue >>> 8 : invValue);
                }
                break;
            case REG_COMP_0:
                if (address == REG_COMP_0) { // check full (byte) address
                    writeTaskRegister(IdeController.REG_COMMAND, invValue);
                }
                break;
        }

        return true;
    }
}
