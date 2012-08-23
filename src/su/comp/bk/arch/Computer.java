/*
 * Created: 31.03.2012
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
package su.comp.bk.arch;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import su.comp.bk.R;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.AudioOutput;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.io.Timer;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.arch.memory.Memory;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * BK001x computer implementation.
 */
public class Computer implements Runnable {

    private static final String TAG = Computer.class.getName();

    // State save/restore: Computer uptime (in nanoseconds)
    private static final String STATE_UPTIME = Computer.class.getName() + "#uptime";
    // State save/restore: RandomAccessMemory pages addresses
    private static final String STATE_RAM_ADDRESSES =
            RandomAccessMemory.class.getName() + "#addresses";
    // State save/restore: RandomAccessMemory page data
    private static final String STATE_RAM_DATA = RandomAccessMemory.class.getName() + "@";

    /** Bus error constant */
    public final static int BUS_ERROR = -1;

    // Computer configuration
    private Configuration configuration;

    // Memory table mapped by 8KB blocks
    private final Memory[] memoryTable = new Memory[8];

    // CPU implementation reference
    private final Cpu cpu;

    // Video controller reference
    private VideoController videoController;

    // Keyboard controller reference
    private KeyboardController keyboardController;

    // Audio output reference
    private AudioOutput audioOutput;

    /** I/O registers space start address */
    public final static int IO_REGISTERS_START_ADDRESS = 0177600;
    // I/O devices list
    private final List<Device> deviceList = new ArrayList<Device>();
    // I/O registers space addresses mapped to devices
    private final List<?>[] deviceTable = new List[4096];

    private boolean isRunning = false;

    private boolean isPaused = true;

    private Thread clockThread;

    /** Amount of nanoseconds in one millisecond */
    public static final long NANOSECS_IN_MSEC = 1000000L;

    /** BK0010 clock frequency (in kHz) */
    public final static int CLOCK_FREQUENCY_BK0010 = 3000;

    // Computer clock frequency (in kHz)
    private int clockFrequency;

    /** Uptime sync threshold (in nanoseconds) */
    public static final long SYNC_UPTIME_THRESHOLD = (10L * NANOSECS_IN_MSEC);
    // Uptime sync threshold (in CPU clock ticks, depends from CPU clock frequency)
    private long syncUptimeThresholdCpuTicks;

    // Last uptime sync timestamp (in nanoseconds, absolute value)
    private long lastUptimeSyncTimestamp;
    // Last CPU time sync timestamp (in ticks)
    private long lastCpuTimeSyncTimestamp;

    // Computer uptime (in nanoseconds)
    private long uptime;

    public enum Configuration {
        /** BK0010 - monitor only */
        BK_0010_MONITOR,
        /** BK0010 - monitor and Basic */
        BK_0010_BASIC,
        /** BK0010 - monitor, Focal and tests */
        BK_0010_MSTD
    }

    public Computer() {
        this.cpu = new Cpu(this);
    }

    /**
     * Configure this computer.
     * @param resources Android resources object reference
     * @param config computer configuration as {@link Configuration} value
     * @throws Exception in case of error while configuring
     */
    public void configure(Resources resources, Configuration config) throws Exception {
        setConfiguration(config);
        RandomAccessMemory workMemory = new RandomAccessMemory(0, 020000);
        addMemory(workMemory);
        RandomAccessMemory videoMemory = new RandomAccessMemory(040000, 020000);
        addMemory(videoMemory);
        videoController = new VideoController(videoMemory);
        addDevice(videoController);
        addDevice(new Sel1RegisterSystemBits(0100000));
        keyboardController = new KeyboardController(this);
        addDevice(keyboardController);
        addDevice(new PeripheralPort());
        addDevice(new Timer());
        switch (config) {
            case BK_0010_BASIC:
                setClockFrequency(CLOCK_FREQUENCY_BK0010);
                addReadOnlyMemory(resources, R.raw.monit10, 0100000);
                addReadOnlyMemory(resources, R.raw.basic10_1, 0120000);
                addReadOnlyMemory(resources, R.raw.basic10_2, 0140000);
                addReadOnlyMemory(resources, R.raw.basic10_3, 0160000);
                break;
            case BK_0010_MSTD:
                setClockFrequency(CLOCK_FREQUENCY_BK0010);
                addReadOnlyMemory(resources, R.raw.monit10, 0100000);
                addReadOnlyMemory(resources, R.raw.focal, 0120000);
                addReadOnlyMemory(resources, R.raw.tests, 0160000);
                break;
            default:
                setClockFrequency(CLOCK_FREQUENCY_BK0010);
                addReadOnlyMemory(resources, R.raw.monit10, 0100000);
                break;
        }
        audioOutput = new AudioOutput(this);
        addDevice(audioOutput);
    }

    /**
     * Set computer configuration.
     * @param config computer configuration as {@link Configuration} value
     */
    public void setConfiguration(Configuration config) {
        this.configuration = config;
    }

    /**
     * Get computer configuration.
     * @return computer configuration as {@link Configuration} value
     */
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Save computer state.
     * @param resources Android {@link Resources} object reference
     * @param outState {@link Bundle} to save state
     */
    public synchronized void saveState(Resources resources, Bundle outState) {
        // Save computer configuration
        outState.putString(Configuration.class.getName(), getConfiguration().name());
        // Save computer uptime
        outState.putLong(STATE_UPTIME, getUptime());
        // Save RAM data
        ArrayList<Integer> ramAddresses = new ArrayList<Integer>();
        for (int memoryBlockIdx = 0; memoryBlockIdx < memoryTable.length; memoryBlockIdx++) {
            if (memoryTable[memoryBlockIdx] instanceof RandomAccessMemory) {
                RandomAccessMemory ram = (RandomAccessMemory) memoryTable[memoryBlockIdx];
                Integer ramAddress = ram.getStartAddress();
                if (!ramAddresses.contains(ramAddress)) {
                    ramAddresses.add(ramAddress);
                }
            }
        }
        if (ramAddresses.size() > 0) {
            outState.putIntegerArrayList(STATE_RAM_ADDRESSES, ramAddresses);
            for (Integer ramAddress: ramAddresses) {
                outState.putShortArray(STATE_RAM_DATA + Integer.toOctalString(ramAddress),
                        ((RandomAccessMemory) getMemory(ramAddress)).getData());
            }
        }
        // Save CPU state
        getCpu().saveState(outState);
        // Save device states
        for (Device device : deviceList) {
            device.saveState(outState);
        }
    }

    /**
     * Restore computer state.
     * @param resources Android {@link Resources} object reference
     * @param inState {@link Bundle} to restore state
     * @throws Exception in case of error while state restoring
     */
    public synchronized void restoreState(Resources resources, Bundle inState) throws Exception {
        // Restore computer configuration
        Configuration config = Configuration.valueOf(inState
                .getString(Configuration.class.getName()));
        configure(resources, config);
        // Restore computer uptime
        setUptime(inState.getLong(STATE_UPTIME));
        // Initialize CPU and devices
        cpu.initDevices();
        // Restore RAM data
        ArrayList<Integer> ramAddresses = inState.getIntegerArrayList(STATE_RAM_ADDRESSES);
        if (ramAddresses != null && ramAddresses.size() > 0) {
            for (Integer ramAddress: ramAddresses) {
                short[] ramData = inState.getShortArray(STATE_RAM_DATA +
                        Integer.toOctalString(ramAddress));
                ((RandomAccessMemory) getMemory(ramAddress)).putData(ramData);
            }
        }
        // Restore CPU state
        getCpu().restoreState(inState);
        // Restore device states
        for (Device device : deviceList) {
            device.restoreState(inState);
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
        this.syncUptimeThresholdCpuTicks = nanosToCpuTime(SYNC_UPTIME_THRESHOLD);
    }

    private void addReadOnlyMemory(Resources resources, int romDataResId, int address)
            throws IOException {
        byte[] romData = loadReadOnlyMemoryData(resources, romDataResId);
        addMemory(new ReadOnlyMemory(address, romData));
    }

    /**
     * Load ROM data from raw resource.
     * @param resources {@link Resources} reference
     * @param romDataResId ROM raw resource ID
     * @return loaded ROM data
     * @throws IOException in case of ROM loading error
     */
    private byte[] loadReadOnlyMemoryData(Resources resources, int romDataResId)
            throws IOException {
        return loadRawResourceData(resources, romDataResId);
    }

    /**
     * Load data of raw resource.
     * @param resources {@link Resources} reference
     * @param resourceId raw resource ID
     * @return read raw resource data
     * @throws IOException in case of loading error
     */
    private byte[] loadRawResourceData(Resources resources, int resourceId) throws IOException {
        InputStream resourceDataStream = resources.openRawResource(resourceId);
        byte[] resourceData = new byte[resourceDataStream.available()];
        resourceDataStream.read(resourceData);
        return resourceData;
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
     * Get {@link KeyboardController} reference
     * @return keyboard controller reference
     */
    public KeyboardController getKeyboardController() {
        return keyboardController;
    }

    /**
     * Set computer uptime (in nanoseconds).
     * @param uptime computer uptime to set (in nanoseconds)
     */
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    /**
     * Get this computer uptime (in nanoseconds).
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
     * Initialize bus devices state (on power-on cycle or RESET opcode).
     */
    public void initDevices() {
        for (Device device: deviceList) {
            device.init(getCpu().getTime());
        }
    }

    /**
     * Reset computer state.
     */
    public synchronized void reset() {
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
                long cpuClock = getCpu().getTime();
                readValue = 0;
                for (Device subdevice: subdevices) {
                    // Read subdevice state value in word mode
                    int subdeviceState = subdevice.read(cpuClock, address & 0177776);
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
                long cpuClock = getCpu().getTime();
                for (Device device: devices) {
                    device.write(cpuClock, isByteMode, address, value);
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
            // Waiting to emulation thread start
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
            audioOutput.start();
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
            audioOutput.stop();
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
        Log.d(TAG, "pausing computer");
        isPaused = true;
        this.notifyAll();
        audioOutput.pause();
    }

    /**
     * Resume computer.
     */
    public synchronized void resume() {
        Log.d(TAG, "resuming computer");
        lastUptimeSyncTimestamp = System.nanoTime();
        lastCpuTimeSyncTimestamp = cpu.getTime();
        isPaused = false;
        audioOutput.resume();
        this.notifyAll();
    }

    /**
     * Release computer resources.
     */
    public void release() {
        Log.d(TAG, "releasing computer");
        audioOutput.release();
    }

    /**
     * Get effective emulation clock frequency.
     * @return effective emulation clock frequency (in kHz)
     */
    public float getEffectiveClockFrequency() {
        return (float) getCpu().getTime() * NANOSECS_IN_MSEC / getUptime();
    }

    /**
     * Get CPU time (converted from clock ticks to nanoseconds).
     * @return current CPU time in nanoseconds
     */
    public long getCpuTimeNanos() {
        return cpuTimeToNanos(cpu.getTime());
    }

    /**
     * Get CPU time (converted from clock ticks to nanoseconds).
     * @param cpuTime CPU time (in clock ticks) to convert
     * @return CPU time in nanoseconds
     */
    public long cpuTimeToNanos(long cpuTime) {
        return cpuTime * NANOSECS_IN_MSEC / clockFrequency;
    }

    /**
     * Get number of CPU clock ticks for given time in nanoseconds.
     * @param nanosecs time (in nanoseconds) to convert to CPU ticks
     * @return CPU ticks for given time
     */
    public long nanosToCpuTime(long nanosecs) {
        return nanosecs * clockFrequency / NANOSECS_IN_MSEC;
    }

    /**
     * Check is time to sync CPU time with computer uptime.
     */
    public void checkSyncUptime() {
        long cpuTimeUptimeDifference = cpu.getTime() - lastCpuTimeSyncTimestamp;
        if (cpuTimeUptimeDifference >= syncUptimeThresholdCpuTicks) {
            doSyncUptime();
        }
    }

    /**
     * Sync CPU time with computer uptime.
     */
    public void doSyncUptime() {
        long timestamp = System.nanoTime();
        uptime += timestamp - lastUptimeSyncTimestamp;
        lastUptimeSyncTimestamp = timestamp;
        lastCpuTimeSyncTimestamp = cpu.getTime();
        long uptimeCpuTimeDifference = getCpuTimeNanos() - uptime;
        uptimeCpuTimeDifference = (uptimeCpuTimeDifference > 0) ? uptimeCpuTimeDifference : 1L;
        long uptimeCpuTimeDifferenceMillis = uptimeCpuTimeDifference / NANOSECS_IN_MSEC;
        int uptimeCpuTimeDifferenceNanos = (int) (uptimeCpuTimeDifference % NANOSECS_IN_MSEC);
        try {
            this.wait(uptimeCpuTimeDifferenceMillis, uptimeCpuTimeDifferenceNanos);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "computer started");
        synchronized (this) {
            this.notifyAll();
            while (isRunning) {
                if (isPaused) {
                    try {
                        Log.d(TAG, "computer paused");
                        this.wait();
                    } catch (InterruptedException e) {
                    }
                    Log.d(TAG, "computer resumed");
                } else {
                    cpu.executeNextOperation();
                    checkSyncUptime();
                }
            }
        }
        Log.d(TAG, "computer stopped");
    }

}
