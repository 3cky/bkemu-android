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

import org.apache.commons.lang.ArrayUtils;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import su.comp.bk.R;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.AudioOutput;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.io.FloppyController;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.MemoryManager;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.io.SystemTimer;
import su.comp.bk.arch.io.Timer;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.arch.io.VideoControllerManager;
import su.comp.bk.arch.memory.Memory;
import su.comp.bk.arch.memory.PagedMemory;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.RandomAccessMemory.Type;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * BK001x computer implementation.
 */
public class Computer implements Runnable {

    private static final String TAG = Computer.class.getName();

    // State save/restore: Computer uptime (in nanoseconds)
    private static final String STATE_UPTIME = Computer.class.getName() + "#uptime";

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

    // Periferal port reference
    private PeripheralPort periferalPort;

    // Audio output reference
    private AudioOutput audioOutput;

    // FLoppy controller reference (<code>null</code> if no floppy controller attached)
    private FloppyController floppyController;

    /** I/O registers space min start address */
    public final static int IO_REGISTERS_MIN_ADDRESS = 0170000;
    /** I/O registers space max start address */
    public final static int IO_REGISTERS_MAX_ADDRESS = 0177600;
    // I/O devices list
    private final List<Device> deviceList = new ArrayList<Device>();
    // I/O registers space addresses mapped to devices
    private final List<?>[] deviceTable = new List[2048];
    // Devices start address (depends from connected RAM/ROM)
    private int devicesStartAddress = IO_REGISTERS_MIN_ADDRESS;

    private boolean isRunning = false;

    private boolean isPaused = true;

    private Thread clockThread;

    /** Amount of nanoseconds in one millisecond */
    public static final long NANOSECS_IN_MSEC = 1000000L;

    /** BK0010 clock frequency (in kHz) */
    public final static int CLOCK_FREQUENCY_BK0010 = 3000;
    /** BK0011 clock frequency (in kHz) */
    public final static int CLOCK_FREQUENCY_BK0011 = 4000;

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
        BK_0010_MSTD,
        /** BK0010 with connected floppy drive controller (КНГМД) */
        BK_0010_KNGMD(true),
        /** BK0011M - MSTD block attached */
        BK_0011M_MSTD(false, true),
        /** BK0011M with connected floppy drive controller (КНГМД) */
        BK_0011M_KNGMD(true, true);

        private final boolean isFloppyControllerPresent;
        private final boolean isMemoryManagerPresent;

        private Configuration() {
            this(false, false);
        }

        private Configuration(boolean isFloppyControllerPresent) {
            this(isFloppyControllerPresent, false);
        }

        private Configuration(boolean isFloppyControllerPresent, boolean isMemoryManagerPresent) {
            this.isFloppyControllerPresent = isFloppyControllerPresent;
            this.isMemoryManagerPresent = isMemoryManagerPresent;
        }

        /**
         * Check is {@link FloppyController} present in this configuration.
         * @return <code>true</code> if floppy controller present, <code>false</code> otherwise
         */
        public boolean isFloppyControllerPresent() {
            return isFloppyControllerPresent;
        }

        /**
         * Check is BK-0011 {@link MemoryManager} present in configuration.
         * @return <code>true</code> if memory manager present, <code>false</code> otherwise
         */
        public boolean isMemoryManagerPresent() {
            return isMemoryManagerPresent;
        }
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
        // Apply shared configuration
        addDevice(new Sel1RegisterSystemBits(!config.isMemoryManagerPresent() ? 0100000 : 0140000));
        keyboardController = new KeyboardController(this);
        addDevice(keyboardController);
        periferalPort = new PeripheralPort(this);
        addDevice(periferalPort);
        addDevice(new Timer());
        // Apply computer specific configuration
        if (!config.isMemoryManagerPresent()) {
            // BK-0010 configurations
            setClockFrequency(CLOCK_FREQUENCY_BK0010);
            // Set RAM configuration
            RandomAccessMemory workMemory = new RandomAccessMemory("WorkMemory", 0, 020000);
            addMemory(workMemory);
            RandomAccessMemory videoMemory = new RandomAccessMemory("VideoMemory", 040000, 020000);
            addMemory(videoMemory);
            // Add video controller
            videoController = new VideoController(videoMemory);
            addDevice(videoController);
            // Set ROM configuration
            addReadOnlyMemory(resources, R.raw.monit10, "Monitor10", 0100000);
            switch (config) {
                case BK_0010_BASIC:
                    addReadOnlyMemory(resources, R.raw.basic10_1, "Basic10:1", 0120000);
                    addReadOnlyMemory(resources, R.raw.basic10_2, "Basic10:2", 0140000);
                    addReadOnlyMemory(resources, R.raw.basic10_3, "Basic10:3", 0160000);
                    break;
                case BK_0010_MSTD:
                    addReadOnlyMemory(resources, R.raw.focal, "Focal", 0120000);
                    addReadOnlyMemory(resources, R.raw.tests, "MSTD10", 0160000);
                    break;
                case BK_0010_KNGMD:
                    addMemory(new RandomAccessMemory("ExtMemory", 0120000, 020000, Type.K537RU10));
                    addReadOnlyMemory(resources, R.raw.disk_327, "FloppyBios", 0160000);
                    floppyController = new FloppyController(this);
                    addDevice(floppyController);
                    break;
                default:
                    break;
            }
        } else {
            // BK-0011 configurations
            setClockFrequency(CLOCK_FREQUENCY_BK0011);
            // Set RAM configuration
            PagedMemory firstPagedMemory = new PagedMemory("PagedMemory0", 040000, 020000,
                    MemoryManager.NUM_RAM_PAGES);
            PagedMemory secondPagedMemory = new PagedMemory("PagedMemory1", 0100000, 020000,
                    MemoryManager.NUM_RAM_PAGES + MemoryManager.NUM_ROM_PAGES);
            for (int memoryPageIndex = 0; memoryPageIndex < MemoryManager.NUM_RAM_PAGES;
                    memoryPageIndex++) {
                Memory memoryPage = new RandomAccessMemory("MemoryPage" + memoryPageIndex,
                        0, 020000);
                firstPagedMemory.setPage(memoryPageIndex, memoryPage);
                secondPagedMemory.setPage(memoryPageIndex, memoryPage);
            }
            addMemory(firstPagedMemory.getPage(6)); // Static RAM page at address 0
            addMemory(firstPagedMemory); // First paged memory space at address 040000
            addMemory(secondPagedMemory); // Second paged memory space at address 0100000
            // Set ROM configuration
            secondPagedMemory.setPage(MemoryManager.NUM_RAM_PAGES, new ReadOnlyMemory(
                    "Basic11M:0", 0, loadReadOnlyMemoryData(resources, R.raw.basic11m_0)));
            secondPagedMemory.setPage(MemoryManager.NUM_RAM_PAGES + 1, new ReadOnlyMemory(
                    "Basic11M:1/ExtBOS11M", 0, loadReadOnlyMemoryData(resources,
                            R.raw.basic11m_1, R.raw.ext11m)));
            addReadOnlyMemory(resources, R.raw.bos11m, "BOS11M", 0140000);
            switch (config) {
                case BK_0011M_MSTD:
                    addReadOnlyMemory(resources, R.raw.mstd11m, "MSTD11M", 0160000);
                    break;
                case BK_0011M_KNGMD:
                    addReadOnlyMemory(resources, R.raw.disk_327, "FloppyBios", 0160000);
                    floppyController = new FloppyController(this);
                    addDevice(floppyController);
                    break;
                default:
                    break;
            }
            // Configure memory manager
            addDevice(new MemoryManager(firstPagedMemory, secondPagedMemory));
            // Add video controller with palette/screen manager
            PagedMemory pagedVideoMemory = new PagedMemory("PagedVideoMemory", 0, 020000, 2);
            pagedVideoMemory.setPage(0, firstPagedMemory.getPage(1));
            pagedVideoMemory.setPage(1, firstPagedMemory.getPage(7));
            videoController = new VideoController(pagedVideoMemory);
            addDevice(videoController);
            addDevice(new VideoControllerManager(videoController, pagedVideoMemory));
            // Add system timer
            addDevice(new SystemTimer(this));
        }
        // Add audio output
        audioOutput = new AudioOutput(this, config.isMemoryManagerPresent());
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

    private List<Memory> getStatefulMemoryList() {
        List<Memory> statefulMemoryList = new ArrayList<Memory>();
        for (int memoryBlockIdx = 0; memoryBlockIdx < memoryTable.length; memoryBlockIdx++) {
            Memory memoryBlock = memoryTable[memoryBlockIdx];
            if (!(memoryBlock instanceof ReadOnlyMemory)) {
                if (memoryBlock instanceof RandomAccessMemory) {
                    if (!statefulMemoryList.contains(memoryBlock)) {
                        statefulMemoryList.add(memoryBlock);
                    }
                } else if (memoryBlock instanceof PagedMemory) {
                    PagedMemory pagedMemory = (PagedMemory) memoryBlock;
                    for (Memory memoryPage : pagedMemory.getPages()) {
                        if (memoryPage != null && !(memoryPage instanceof ReadOnlyMemory)
                                && !statefulMemoryList.contains(memoryPage)) {
                            statefulMemoryList.add(memoryPage);
                        }
                    }
                    statefulMemoryList.add(pagedMemory);
                }
            }
        }
        return statefulMemoryList;
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
        for (Memory memory: getStatefulMemoryList()) {
            memory.saveState(outState);
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
        for (Memory memory: getStatefulMemoryList()) {
            memory.restoreState(inState);
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

    private void addReadOnlyMemory(Resources resources, int romDataResId, String romId, int address)
            throws IOException {
        byte[] romData = loadReadOnlyMemoryData(resources, romDataResId);
        addMemory(new ReadOnlyMemory(romId, address, romData));
    }

    /**
     * Load ROM data from raw resources.
     * @param resources {@link Resources} reference
     * @param romDataResIds ROM raw resource IDs
     * @return loaded ROM data
     * @throws IOException in case of ROM data loading error
     */
    private byte[] loadReadOnlyMemoryData(Resources resources, int... romDataResIds)
            throws IOException {
        byte[] romData = null;
        for (int romDataResId : romDataResIds) {
            romData = ArrayUtils.addAll(romData, loadRawResourceData(resources, romDataResId));
        }
        return romData;
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
     * Get {@link VideoController} reference.
     * @return video controller reference
     */
    public VideoController getVideoController() {
        return videoController;
    }

    /**
     * Get {@link KeyboardController} reference.
     * @return keyboard controller reference
     */
    public KeyboardController getKeyboardController() {
        return keyboardController;
    }

    /**
     * Get {@link PeripheralPort} reference.
     * @return peripheral port reference
     */
    public PeripheralPort getPeripheralPort() {
        return periferalPort;
    }

    /**
     * Get {@link FloppyController} reference.
     * @return floppyController reference or <code>null</code> if not attached
     */
    public FloppyController getFloppyController() {
        return floppyController;
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
        int memoryBlocksCount = memory.getSize() >> 12; // ((size << 1) >> 13)
        if ((memory.getSize() & 07777) != 0) {
            memoryBlocksCount++;
        }
        for (int memoryBlockIdx = 0; memoryBlockIdx < memoryBlocksCount; memoryBlockIdx++) {
            memoryTable[memoryStartBlock + memoryBlockIdx] = memory;
        }
        // Correct devices start address, if needed
        int memoryEndAddress = memory.getStartAddress() + (memory.getSize() << 1);
        if (getDevicesStartAddress() < memoryEndAddress) {
            setDevicesStartAddress(Math.min(memoryEndAddress, IO_REGISTERS_MAX_ADDRESS));
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
            int deviceTableIndex = (deviceAddress - IO_REGISTERS_MIN_ADDRESS) >> 1;
            @SuppressWarnings("unchecked")
            List<Device> addressDevices = (List<Device>) deviceTable[deviceTableIndex];
            if (addressDevices == null) {
                addressDevices = new ArrayList<Device>(1);
                deviceTable[deviceTableIndex] = addressDevices;
            }
            addressDevices.add(device);
        }
    }

    /**
     * Check is given address is mapped to ROM area.
     * @param address address to check
     * @return <code>true</code> if given address is mapped to ROM area,
     * <code>false</code> otherwise
     */
    public boolean isReadOnlyMemoryAddress(int address) {
        return (address >= 0) && (getMemory(address) instanceof ReadOnlyMemory);
    }

    private Memory getMemory(int address) {
        Memory memory = memoryTable[address >> 13];
        return (memory != null && memory.isRelatedAddress(address)) ? memory : null;
    }

    /**
     * Get I/O devices start address.
     * @return I/O devices start address value
     */
    public int getDevicesStartAddress() {
        return devicesStartAddress;
    }

    /**
     * Set I/O devices start address.
     * @param devicesStartAddress I/O devices start address value to set
     */
    public void setDevicesStartAddress(int devicesStartAddress) {
        this.devicesStartAddress = devicesStartAddress;
    }

    @SuppressWarnings("unchecked")
    private List<Device> getDevices(int address) {
        return (List<Device>) deviceTable[(address - IO_REGISTERS_MIN_ADDRESS) >> 1];
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
        if (address >= getDevicesStartAddress()) {
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
        if (address >= getDevicesStartAddress()) {
            List<Device> devices = getDevices(address);
            if (devices != null) {
                long cpuClock = getCpu().getTime();
                for (Device device: devices) {
                    if (device.write(cpuClock, isByteMode, address, value)) {
                        isWritten = true;
                    }
                }

            }
        } else {
            // Check for memory at given address
            Memory memory = getMemory(address);
            if (memory != null) {
                isWritten = memory.write(isByteMode, address, value);
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

    public boolean isPaused() {
        return isPaused;
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
        if (floppyController != null) {
            floppyController.unmountDiskImages();
        }
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
            doTimerTasks();
        }
    }

    /**
     * Do timer tasks.
     */
    public void doTimerTasks() {
        for (Device device: deviceList) {
            device.timer(uptime);
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
