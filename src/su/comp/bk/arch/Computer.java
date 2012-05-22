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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.res.Resources;
import android.util.Log;

import su.comp.bk.R;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.arch.memory.Memory;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * BK001x computer implementation.
 */
public class Computer implements Runnable {

    private static final String TAG = Computer.class.getName();

    /** Bus error constant */
    public final static int BUS_ERROR = -1;

    // Memory table mapped by 8KB blocks
    private final Memory[] memoryTable = new Memory[8];

    // CPU implementation reference
    private final Cpu cpu;

    // Video controller reference
    private VideoController videoController;

    /** I/O registers space start address */
    public final static int IO_REGISTERS_START_ADDRESS = 0177600;
    // I/O devices list
    private final List<Device> deviceList = new ArrayList<Device>();
    // I/O registers space addresses mapped to devices
    private final List<?>[] deviceTable = new List[4096];

    private boolean isRunning = false;

    private boolean isPaused = false;

    private Thread clockThread;

    // BK0010 clock frequency (in kHz)
    public final static int CLOCK_FREQUENCY_BK0010 = 3000;

    // Computer clock frequency (in kHz)
    private int clockFrequency;

    // Uptime sync threshold (in nanoseconds)
    public static final long SYNC_UPTIME_THRESHOLD = 1000000L;

    // Last uptime sync timestamp (in nanoseconds, absolute value)
    private long lastUptimeSyncTimestamp;

    // Computer uptime (in nanoseconds)
    private long uptime;

    public enum Configuration {
        BK_0010_BASIC
    }

    public Computer() {
        this.cpu = new Cpu(this);
    }

    public void configure(Resources resources, Configuration config) throws IOException {
        RandomAccessMemory workMemory = new RandomAccessMemory(0, 020000);
        addMemory(workMemory);
        RandomAccessMemory videoMemory = new RandomAccessMemory(040000, 020000);
        addMemory(videoMemory);
        videoController = new VideoController(videoMemory);
        addDevice(videoController);
        addDevice(new Sel1RegisterSystemBits(0100000));
        addDevice(new KeyboardController());
        addDevice(new PeripheralPort());
        switch (config) {
            case BK_0010_BASIC:
                setClockFrequency(CLOCK_FREQUENCY_BK0010);
                addReadOnlyMemory(resources, R.raw.monit10, 0100000);
                addReadOnlyMemory(resources, R.raw.basic10_1, 0120000);
                addReadOnlyMemory(resources, R.raw.basic10_2, 0140000);
                addReadOnlyMemory(resources, R.raw.basic10_3, 0160000);
                break;
            default:
                break;
        }
    }

    /**
     * Get clock frequency (in kHz)
     * @return clock frequency
     */
    public int getClockFrequency() {
        return clockFrequency;
    }

    /**
     * Set clock frequency (in kHz)
     * @param clockFrequency clock frequency to set
     */
    public void setClockFrequency(int clockFrequency) {
        this.clockFrequency = clockFrequency;
    }

    private void addReadOnlyMemory(Resources resources, int romDataResId, int address)
            throws IOException {
        byte[] romData = loadReadOnlyMemoryData(resources, romDataResId);
        addMemory(new ReadOnlyMemory(address, romData));
    }

    private byte[] loadReadOnlyMemoryData(Resources resources, int romDataResId)
            throws IOException {
        InputStream romDataStream = resources.openRawResource(romDataResId);
        byte[] romData = new byte[romDataStream.available()];
        romDataStream.read(romData);
        return romData;
    }

    /**
     * Get {@link Cpu} reference.
     * @return this <code>Computer</code> CPU object reference
     */
    public Cpu getCpu() {
        return cpu;
    }

    /**
     * Get {@link VideoController} reference
     * @return video controller reference
     */
    public VideoController getVideoController() {
        return videoController;
    }

    /**
     * Get this computer uptime (in nanoseconds)
     * @return computer uptime (in nanoseconds)
     */
    public long getUptime() {
        return uptime;
    }

    /**
     * Add memory (RAM/ROM) to address space.
     * @param memory {@link Memory} to add
     */
    public void addMemory(Memory memory) {
        int memoryStartBlock = memory.getStartAddress() >> 13;
        int memoryBlocksCount = (memory.getSize() >> 12) + 1; // ((size << 1) >> 13) + 1
        for (int memoryBlockIdx = 0; memoryBlockIdx < memoryBlocksCount; memoryBlockIdx++) {
            memoryTable[memoryStartBlock + memoryBlockIdx] = memory;
        }
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
            List<Device> subdevices = getDevices(address);
            if (subdevices != null) {
                readValue = 0;
                for (Device subdevice: subdevices) {
                    // Read subdevice status value in word mode
                    int subdeviceState = subdevice.read(address);
                    // For byte mode read and odd address - extract high byte
                    if (isByteMode && (address & 1) != 0) {
                        subdeviceState >>= 8;
                    }
                    // Concatenate this subdevice state value with values of other subdevices
                    readValue |= (subdeviceState & (isByteMode ? 0377 : 0177777));
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

    /**
     * Start computer.
     */
    public synchronized void start() {
        if (!isRunning) {
            Log.d(TAG, "starting computer");
            this.clockThread = new Thread(this, "ComputerClockThread");
            isRunning = true;
            clockThread.start();
            Log.d(TAG, "computer started");
        } else {
            throw new IllegalStateException("Computer is already running!");
        }
    }

    /**
     * Stop computer.
     */
    public void stop() {
        if (isRunning) {
            Log.d(TAG, "stopping computer");
            synchronized (this) {
                isRunning = false;
                this.notifyAll();
            }
            while (clockThread.isAlive()) {
                try {
                    this.clockThread.join();
                } catch (InterruptedException e) {
                }
            }
        } else {
            throw new IllegalStateException("Computer is already stopped!");
        }
    }

    /**
     * Pause computer.
     */
    public synchronized void pause() {
        isPaused = true;
        Log.d(TAG, "computer paused");
    }

    /**
     * Resume computer.
     */
    public synchronized void resume() {
        lastUptimeSyncTimestamp = System.nanoTime();
        if (isPaused) {
            isPaused = false;
            this.notifyAll();
            Log.d(TAG, "computer resumed");
        }
    }

    /**
     * Get CPU time (converted from clock ticks to nanoseconds).
     * @return CPU time in nanoseconds
     */
    private long getCpuTimeNanos() {
        // cpuTimeNanos = cpuTime * 1000000 / clockFrequency
        return cpu.getTime() * 1000000L / clockFrequency;
    }

    /**
     * Sync CPU time with computer uptime.
     */
    private void doSyncUptime() {
        long timestamp = System.nanoTime();
        uptime += timestamp - lastUptimeSyncTimestamp;
        lastUptimeSyncTimestamp = timestamp;
        long uptimeCpuTimeDifference = getCpuTimeNanos() - uptime;
        synchronized (this) {
            if (isRunning && (isPaused || uptimeCpuTimeDifference >= SYNC_UPTIME_THRESHOLD)) {
                try {
                    this.wait(0L, isPaused ? 0 : (int) uptimeCpuTimeDifference);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "computer started");
        while (isRunning) {
            cpu.executeNextOperation();
            doSyncUptime();
        }
        Log.d(TAG, "computer stopped");
    }

}
