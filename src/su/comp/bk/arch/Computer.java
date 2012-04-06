/*
 * Created: 31.03.2012
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
package su.comp.bk.arch;

import java.util.ArrayList;
import java.util.List;

import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.memory.Memory;

/**
 * BK001x computer implementation.
 */
public class Computer {

    /** Bus error constant */
    public final static int BUS_ERROR = -1;

    // Memory table mapped by 8K blocks
    private final Memory[] memoryTable = new Memory[8];

    // CPU implementation reference
    private final Cpu cpu;

    /** I/O registers space start address */
    public final static int IO_REGISTERS_START_ADDRESS = 0177600;
    // I/O devices list
    private final List<Device> deviceList = new ArrayList<Device>();
    // I/O registers space addresses mapped to devices
    private final List<?>[] deviceTable = new List[4096];

    public Computer() {
        this.cpu = new Cpu(this);
    }

    /**
     * Get {@link Cpu} reference.
     * @return this <code>Computer</code> CPU object reference
     */
    public Cpu getCpu() {
        return cpu;
    }

    /**
     * Add memory (RAM/ROM) to address space.
     * @param memory {@link Memory} to add
     */
    public void addMemory(Memory memory) {
        memoryTable[memory.getStartAddress() >> 13] = memory;
    }

    /**
     * Add I/O device to address space.
     * @param device {@link Device} to add
     */
    public void addDevice(Device device) {
        deviceList.add(device);
        int[] deviceAddresses = device.getAddresses();
        for (int deviceAddress : deviceAddresses) {
            int deviceTableIndex = (deviceAddress - IO_REGISTERS_START_ADDRESS) >> 1;
            @SuppressWarnings("unchecked")
            List<Device> addressDevices = (List<Device>) deviceTable[deviceTableIndex];
            if (addressDevices == null) {
                addressDevices = new ArrayList<Device>(1);
                deviceTable[deviceTableIndex] = addressDevices;
            }
            addressDevices.add(device);
        }
    }

    private Memory getMemory(int address) {
        Memory memory = memoryTable[address >> 13];
        return (memory != null && memory.isRelatedAddress(address)) ? memory : null;
    }

    @SuppressWarnings("unchecked")
    private List<Device> getDevices(int address) {
        return (List<Device>) deviceTable[(address - IO_REGISTERS_START_ADDRESS) >> 1];
    }

    /**
     * Reset bus devices state (on power-on cycle or RESET opcode).
     */
    public void resetDevices() {
        for (Device device: deviceList) {
            device.reset();
        }
    }

    /**
     * Reset computer state.
     */
    public void reset() {
        resetDevices();
        getCpu().reset();
    }

    /**
     * Read byte or word from memory or I/O device mapped to given address.
     * @param isByteMode <code>true</code> to read byte, <code>false</code> to read word
     * @param address address or memory location to read
     * @return read memory or I/O device data or <code>BUS_ERROR</code> in case if given memory
     * location is not mapped
     */
    public int readMemory(boolean isByteMode, int address) {
        int readValue = BUS_ERROR;
        // First check for I/O registers
        if (address >= IO_REGISTERS_START_ADDRESS) {
            List<Device> devices = getDevices(address);
            if (devices != null) {
                readValue = 0;
                for (Device device: devices) {
                    readValue |= device.read(isByteMode, address);
                }
            }
        } else {
            // Check for memory at given address
            Memory memory = getMemory(address);
            if (memory != null) {
                readValue = memory.read(isByteMode, address);
            }
        }
        return readValue;
    }

    /**
     * Write byte or word to memory or I/O device mapped to given address.
     * @param isByteMode <code>true</code> to write byte, <code>false</code> to write word
     * @param address address or memory location to write
     * @param value value (byte/word) to write to given address
     * @return <code>true</code> if data successfully written or <code>false</code>
     * in case if given memory location is not mapped
     */
    public boolean writeMemory(boolean isByteMode, int address, int value) {
        boolean isWritten = false;
        // First check for I/O registers
        if (address >= IO_REGISTERS_START_ADDRESS) {
            List<Device> devices = getDevices(address);
            if (devices != null) {
                for (Device device: devices) {
                    device.write(isByteMode, address, value);
                }
                isWritten = true;
            }
        } else {
            // Check for memory at given address
            Memory memory = getMemory(address);
            if (memory != null) {
                memory.write(isByteMode, address, value);
                isWritten = true;
            }
        }
        return isWritten;
    }

}
