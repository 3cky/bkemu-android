/*
 * Copyright (C) 2024 Victor Antonovich (v.antonovich@gmail.com)
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
package su.comp.bk.arch;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.io.SystemTimer;
import su.comp.bk.arch.io.Timer;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.arch.io.VideoControllerManager;
import su.comp.bk.arch.io.audio.AudioManager;
import su.comp.bk.arch.io.audio.AudioOutput;
import su.comp.bk.arch.io.audio.Ay8910;
import su.comp.bk.arch.io.audio.Covox;
import su.comp.bk.arch.io.audio.Speaker;
import su.comp.bk.arch.io.disk.FloppyController;
import su.comp.bk.arch.io.disk.IdeController;
import su.comp.bk.arch.io.disk.SmkIdeController;
import su.comp.bk.arch.io.memory.Bk11MemoryManager;
import su.comp.bk.arch.io.memory.SmkMemoryManager;
import su.comp.bk.arch.memory.BankedMemory;
import su.comp.bk.arch.memory.Memory;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.RandomAccessMemory.Type;
import su.comp.bk.arch.memory.ReadOnlyMemory;
import su.comp.bk.arch.memory.SegmentedMemory;
import su.comp.bk.arch.memory.SelectableMemory;
import su.comp.bk.resource.ResourceManager;
import su.comp.bk.state.State;
import su.comp.bk.state.StatefulEntity;

/**
 * BK001x computer implementation.
 */
public class Computer implements Runnable, StatefulEntity {
    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    private static final String STATE_PREFIX = "Computer";
    // State save/restore: Computer configuration name
    public static final String STATE_CONFIGURATION = STATE_PREFIX + "#configuration";
    // State save/restore: Computer system uptime (in nanoseconds)
    public static final String STATE_SYSTEM_UPTIME = STATE_PREFIX + "#sys_uptime";
    // State save/restore: Computer RAM data
    public static final String STATE_RAM_DATA = STATE_PREFIX + "#ram_data";

    /** Bus error constant */
    public final static int BUS_ERROR = -1;

    // Computer configuration
    private Configuration configuration;

    // Memory table mapped by 4KB blocks
    private final List<?>[] memoryTable = new List[16];

    // List of created RandomAccessMemory instances
    private final List<RandomAccessMemory> randomAccessMemoryList = new ArrayList<>();

    // CPU implementation reference
    private final Cpu cpu;

    // Video controller reference
    private VideoController videoController;

    // Keyboard controller reference
    private KeyboardController keyboardController;

    // Peripheral port reference
    private PeripheralPort peripheralPort;

    // Audio outputs list
    private final List<AudioOutput<?>> audioOutputs = new ArrayList<>();

    // Floppy controller reference (<code>null</code> if no floppy controller attached)
    private FloppyController floppyController;

    /** BK0010 screen memory start address */
    public static final int BK0010_SCREEN_MEMORY_START_ADDRESS = 040000;

    /** BK0011 first banked memory block address */
    public static final int BK0011_BANKED_MEMORY_0_ADDRESS = 040000;
    /** BK0011 second banked memory block address */
    public static final int BK0011_BANKED_MEMORY_1_ADDRESS = 0100000;

    /** I/O registers space min start address */
    public final static int IO_REGISTERS_MIN_ADDRESS = 0177000;
    // I/O devices list
    private final List<Device> deviceList = new ArrayList<>();
    // I/O registers space addresses mapped to devices
    private final List<?>[] deviceTable = new List[2048];

    private boolean isRunning = false;

    private boolean isPaused = true;

    private Thread clockThread;

    /** Amount of nanoseconds in one microsecond */
    public static final long NANOSECS_IN_USEC = 1000L;
    /** Amount of nanoseconds in one millisecond */
    public static final long NANOSECS_IN_MSEC = 1000000L;

    /** BK0010 clock frequency (in kHz) */
    public final static int CLOCK_FREQUENCY_BK0010 = 3000;
    /** BK0011 clock frequency (in kHz) */
    public final static int CLOCK_FREQUENCY_BK0011 = 4000;
    /** Turbo clock frequency (in kHz) */
    public final static int CLOCK_FREQUENCY_TURBO = 6000;
    /** Maximum reachable clock frequency */
    public final static int CLOCK_FREQUENCY_MAXIMUM = 0;

    // Computer clock frequency (in kHz)
    private int clockFrequency = CLOCK_FREQUENCY_BK0010;

    /** Uptime sync threshold (in nanoseconds) */
    private static final long UPTIME_SYNC_THRESHOLD = (10L * NANOSECS_IN_MSEC);
    // Last computer system uptime update timestamp (unix timestamp, in nanoseconds)
    private long systemUptimeUpdateTimestamp;
    // Computer system uptime since start (in nanoseconds)
    private long systemUptime;
    /** Uptime sync check interval (in nanoseconds) */
    private static final long UPTIME_SYNC_CHECK_INTERVAL = (1L * NANOSECS_IN_MSEC);
    // Computer system uptime sync check interval (in CPU ticks)
    private long systemUptimeSyncCheckIntervalTicks;
    // Last computer system uptime sync check timestamp (in CPU ticks)
    private long systemUptimeSyncCheckTimestampTicks;

    // Last effective clock frequency calculation CPU time
    private long lastEffectiveClockFrequencyCpuUptime;
    // Last effective clock frequency calculation system uptime
    private long lastEffectiveClockFrequencySystemUptime;

    private final List<UptimeListener> uptimeListeners = new ArrayList<>();

    // IDE controller reference (<code>null</code> if no IDE controller present)
    private IdeController ideController;

    /**
     * Computer uptime updates listener.
     */
    public interface UptimeListener {
        void uptimeUpdated(long uptime);
    }

    public enum Model {
        // BK-0010(-01)
        BK_0010,
        // BK-0011M
        BK_0011M
    }

    public enum Configuration {
        /** BK0010 - monitor only */
        BK_0010_MONITOR(Model.BK_0010, false),
        /** BK0010 - monitor and Basic */
        BK_0010_BASIC(Model.BK_0010, false),
        /** BK0010 - monitor, Focal and tests */
        BK_0010_MSTD(Model.BK_0010, false),
        /** BK0010 with connected floppy drive controller (КНГМД) */
        BK_0010_KNGMD(Model.BK_0010, true),
        /** BK0010 with connected SMK512 controller */
        BK_0010_SMK512(Model.BK_0010, true),
        /** BK0011M - MSTD block attached */
        BK_0011M_MSTD(Model.BK_0011M, false),
        /** BK0011M with connected floppy drive controller (КНГМД) */
        BK_0011M_KNGMD(Model.BK_0011M, true),
        /** BK0011M with connected SMK512 controller */
        BK_0011M_SMK512(Model.BK_0011M, true);

        private final Model model;
        private final boolean isFloppyControllerPresent;

        Configuration(Model model, boolean isFloppyControllerPresent) {
            this.model = model;
            this.isFloppyControllerPresent = isFloppyControllerPresent;
        }

        /**
         * Get this BK model as {@link Model} enum value.
         * @return this BK model
         */
        public Model getModel() {
            return model;
        }

        /**
         * Check is {@link FloppyController} present in this configuration.
         * @return <code>true</code> if floppy controller present, <code>false</code> otherwise
         */
        public boolean isFloppyControllerPresent() {
            return isFloppyControllerPresent;
        }
    }

    public static class MemoryRange {
        private final int startAddress;
        private final Memory memory;
        private final int endAddress;

        public MemoryRange(int startAddress, Memory memory) {
            this.startAddress = startAddress;
            this.memory = memory;
            this.endAddress = startAddress + (memory.getSize() << 1) - 1;
        }

        public int getStartAddress() {
            return startAddress;
        }

        public Memory getMemory() {
            return memory;
        }

        public boolean isRelatedAddress(int address) {
            return (address >= startAddress) && (address <= endAddress);
        }
    }

    public Computer() {
        this.cpu = new Cpu(this);
    }

    /**
     * Configure this computer.
     * @param audioManager {@link AudioManager} reference
     * @param resourceManager {@link ResourceManager} reference
     * @param frameRenderer {@link su.comp.bk.arch.io.VideoController.FrameRenderer} reference
     * @param config computer configuration as {@link Configuration} value
     * @param cpuClockFrequency CPU clock frequency
     * @throws Exception in case of error while configuring
     */
    public void configure(AudioManager audioManager, ResourceManager resourceManager,
                          VideoController.FrameRenderer frameRenderer,
                          Configuration config, int cpuClockFrequency) throws Exception {
        setConfiguration(config);
        // Apply shared configuration
        addDevice(new Sel1RegisterSystemBits(config.getModel() == Model.BK_0010 ? 0100000 : 0140000));
        keyboardController = new KeyboardController(this);
        addDevice(keyboardController);
        peripheralPort = new PeripheralPort();
        addDevice(peripheralPort);
        addDevice(new Timer());
        // Apply computer specific configuration
        if (config.getModel() == Model.BK_0010) {
            // BK-0010 configurations
            setClockFrequency(cpuClockFrequency);
            // Set RAM configuration
            RandomAccessMemory workMemory = createRandomAccessMemory("WorkMemory",
                    020000, Type.K565RU6);
            addMemory(0, workMemory);
            RandomAccessMemory videoMemory = createRandomAccessMemory("VideoMemory",
                    020000, Type.K565RU6);
            addMemory(BK0010_SCREEN_MEMORY_START_ADDRESS, videoMemory);
            // Add video controller
            videoController = new VideoController(videoMemory, frameRenderer);
            addDevice(videoController);
            // Set ROM configuration
            ReadOnlyMemory monitorRom = new ReadOnlyMemory(ResourceManager.ROM_MONITOR_10,
                    loadReadOnlyMemoryData(resourceManager, ResourceManager.ROM_MONITOR_10));
            SelectableMemory selectableMonitorRom = new SelectableMemory(monitorRom.getId(),
                    monitorRom, true);
            addMemory( 0100000, selectableMonitorRom);
            switch (config) {
                case BK_0010_BASIC:
                    addReadOnlyMemory(resourceManager, 0120000,
                            ResourceManager.ROM_BASIC_10_1);
                    addReadOnlyMemory(resourceManager, 0140000,
                            ResourceManager.ROM_BASIC_10_2);
                    addReadOnlyMemory(resourceManager, 0160000,
                            ResourceManager.ROM_BASIC_10_3);
                    break;
                case BK_0010_MSTD:
                    addReadOnlyMemory(resourceManager, 0120000,
                            ResourceManager.ROM_FOCAL_10);
                    addReadOnlyMemory(resourceManager, 0160000,
                            ResourceManager.ROM_MSTD_10);
                    break;
                case BK_0010_KNGMD:
                    addMemory(0120000, createRandomAccessMemory("ExtMemory",
                            020000, Type.K537RU10));
                    addReadOnlyMemory(resourceManager, 0160000,
                            ResourceManager.ROM_FLOPPY_BIOS);
                    floppyController = new FloppyController(this);
                    addDevice(floppyController);
                    break;
                case BK_0010_SMK512:
                    SmkMemoryManager smkMemoryManager = getSmkMemoryManager(resourceManager);
                    smkMemoryManager.setSelectableBk10MonitorRom(selectableMonitorRom);
                    addDevice(smkMemoryManager);
                    floppyController = new FloppyController(this);
                    addDevice(floppyController);
                    SmkIdeController smkIdeController = new SmkIdeController(this);
                    ideController = smkIdeController;
                    addDevice(smkIdeController);
                    break;
                default:
                    break;
            }
        } else {
            // BK-0011M configurations
            setClockFrequency(cpuClockFrequency);
            // Set RAM configuration
            BankedMemory firstBankedMemory = new BankedMemory("BankedMemory0",
                    020000, Bk11MemoryManager.NUM_RAM_BANKS);
            BankedMemory secondBankedMemory = new BankedMemory("BankedMemory1",
                    020000,
                    Bk11MemoryManager.NUM_RAM_BANKS + Bk11MemoryManager.NUM_ROM_BANKS);
            for (int memoryBankIndex = 0; memoryBankIndex < Bk11MemoryManager.NUM_RAM_BANKS;
                    memoryBankIndex++) {
                Memory memoryBank = createRandomAccessMemory("MemoryBank" + memoryBankIndex,
                        020000, Type.K565RU5);
                firstBankedMemory.setBank(memoryBankIndex, memoryBank);
                secondBankedMemory.setBank(memoryBankIndex, memoryBank);
            }
            addMemory(0, firstBankedMemory.getBank(6)); // Fixed RAM page at address 0
            addMemory(BK0011_BANKED_MEMORY_0_ADDRESS, firstBankedMemory); // First banked memory window at address 040000
            SelectableMemory selectableSecondBankedMemory = new SelectableMemory(
                    secondBankedMemory.getId(), secondBankedMemory, true);
            addMemory(BK0011_BANKED_MEMORY_1_ADDRESS, selectableSecondBankedMemory); // Second banked memory window at address 0100000
            // Set ROM configuration
            secondBankedMemory.setBank(Bk11MemoryManager.NUM_RAM_BANKS, new ReadOnlyMemory(
                    ResourceManager.ROM_BASIC_11M_0, loadReadOnlyMemoryData(resourceManager,
                        ResourceManager.ROM_BASIC_11M_0)));
            secondBankedMemory.setBank(Bk11MemoryManager.NUM_RAM_BANKS + 1,
                    new ReadOnlyMemory(ResourceManager.ROM_BASIC_11M_1 + '/' +
                                ResourceManager.ROM_EXT_BOS_11M,
                            loadReadOnlyMemoryData(resourceManager,
                                    ResourceManager.ROM_BASIC_11M_1,
                                    ResourceManager.ROM_EXT_BOS_11M)));
            ReadOnlyMemory bosRom = new ReadOnlyMemory(ResourceManager.ROM_BOS_11M,
                    loadReadOnlyMemoryData(resourceManager, ResourceManager.ROM_BOS_11M));
            SelectableMemory selectableBosRom = new SelectableMemory(bosRom.getId(), bosRom, true);
            addMemory( 0140000, selectableBosRom);
            switch (config) {
                case BK_0011M_MSTD:
                    addReadOnlyMemory(resourceManager, 0160000, ResourceManager.ROM_MSTD_11M);
                    break;
                case BK_0011M_KNGMD:
                    addReadOnlyMemory(resourceManager, 0160000, ResourceManager.ROM_FLOPPY_BIOS);
                    floppyController = new FloppyController(this);
                    addDevice(floppyController);
                    break;
                case BK_0011M_SMK512:
                    SmkMemoryManager smkMemoryManager = getSmkMemoryManager(resourceManager);
                    smkMemoryManager.setSelectableBk11BosRom(selectableBosRom);
                    smkMemoryManager.setSelectableBk11SecondBankedMemory(selectableSecondBankedMemory);
                    addDevice(smkMemoryManager);
                    floppyController = new FloppyController(this);
                    addDevice(floppyController);
                    SmkIdeController smkIdeController = new SmkIdeController(this);
                    ideController = smkIdeController;
                    addDevice(smkIdeController);
                    break;
                default:
                    break;
            }
            // Configure BK0011M memory manager
            addDevice(new Bk11MemoryManager(firstBankedMemory, secondBankedMemory));
            // Add video controller with palette/screen manager
            BankedMemory videoMemory = new BankedMemory("VideoPagesMemory", 020000, 2);
            videoMemory.setBank(0, firstBankedMemory.getBank(1));
            videoMemory.setBank(1, firstBankedMemory.getBank(7));
            videoController = new VideoController(videoMemory, frameRenderer);
            addDevice(videoController);
            addDevice(new VideoControllerManager(videoController, videoMemory));
            // Add system timer
            SystemTimer systemTimer = new SystemTimer(this);
            addDevice(systemTimer);
            videoController.addFrameSyncListener(systemTimer);
        }
        // Notify video controller about computer time updates
        addUptimeListener(videoController);
        // Add audio outputs
        addAudioOutput(new Speaker(audioManager.createAudioPlayer(), this));
        addAudioOutput(new Covox(audioManager.createAudioPlayer(), this));
        addAudioOutput(new Ay8910(audioManager.createAudioPlayer(), this));
    }

    private SmkMemoryManager getSmkMemoryManager(ResourceManager resourceManager)
            throws IOException {
        ReadOnlyMemory smkBiosRom = new ReadOnlyMemory(ResourceManager.ROM_SMK_BIOS,
                loadReadOnlyMemoryData(resourceManager, ResourceManager.ROM_SMK_BIOS));
        SelectableMemory selectableSmkBiosRom0 = new SelectableMemory(
                smkBiosRom.getId() + ":0", smkBiosRom, false);
        SelectableMemory selectableSmkBiosRom1 = new SelectableMemory(
                smkBiosRom.getId() + ":1", smkBiosRom, false);
        addMemory(SmkMemoryManager.BIOS_ROM_0_START_ADDRESS, selectableSmkBiosRom0);
        addMemory(SmkMemoryManager.BIOS_ROM_1_START_ADDRESS, selectableSmkBiosRom1);
        RandomAccessMemory smkRam = createRandomAccessMemory("SmkRam",
                SmkMemoryManager.MEMORY_TOTAL_SIZE, Type.K565RU5);
        List<SegmentedMemory> smkRamSegments = new ArrayList<>(
                SmkMemoryManager.NUM_MEMORY_SEGMENTS);
        for (int i = 0; i < SmkMemoryManager.NUM_MEMORY_SEGMENTS; i++) {
            SegmentedMemory smkRamSegment = new SegmentedMemory("SmkRamSegment" + i,
                    smkRam, SmkMemoryManager.MEMORY_SEGMENT_SIZE);
            smkRamSegments.add(smkRamSegment);
            addMemory(SmkMemoryManager.MEMORY_START_ADDRESS +
                    i * SmkMemoryManager.MEMORY_SEGMENT_SIZE * 2, smkRamSegment);
        }
        return new SmkMemoryManager(smkRamSegments, selectableSmkBiosRom0, selectableSmkBiosRom1);
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
     * Create new {@link RandomAccessMemory} and add it to the RAM list for further saving/restoring.
     * @param memoryId RAM identifier
     * @param memorySize RAM size (in words)
     * @param memoryType RAM type
     * @return created {@link RandomAccessMemory} reference
     */
    private RandomAccessMemory createRandomAccessMemory(String memoryId, int memorySize,
                                                        Type memoryType) {
        RandomAccessMemory memory = new RandomAccessMemory(memoryId, memorySize, memoryType);
        addToRandomAccessMemoryList(memory);
        return memory;
    }

    private void addToRandomAccessMemoryList(RandomAccessMemory memory) {
        randomAccessMemoryList.add(memory);
    }

    private List<RandomAccessMemory> getRandomAccessMemoryList() {
        return randomAccessMemoryList;
    }

    /**
     * Save computer state.
     * @param outState {@link State} to save state
     */
    public void saveState(State outState) {
        // Save computer configuration
        outState.putString(STATE_CONFIGURATION, getConfiguration().name());
        // Save RAM data
        saveRandomAccessMemoryData(outState);
        // Save CPU state
        getCpu().saveState(outState);
        // Save device states
        for (Device device : deviceList) {
            device.saveState(outState);
        }
    }

    /**
     * Restore computer state.
     * @param inState {@link State} to restore state
     */
    public void restoreState(State inState) {
        // Initialize CPU and devices
        cpu.initDevices(true);
        // Restore RAM data
        restoreRandomAccessMemoryData(inState);
        // Restore CPU state
        getCpu().restoreState(inState);
        // Restore device states
        for (Device device : deviceList) {
            device.restoreState(inState);
        }
    }

    private void saveRandomAccessMemoryData(State outState) {
        List<RandomAccessMemory> randomAccessMemoryList = getRandomAccessMemoryList();
        for (RandomAccessMemory memory: randomAccessMemoryList) {
            ByteBuffer memoryDataBuf = ByteBuffer.allocate(memory.getSize() * 2);
            memoryDataBuf.asShortBuffer().put(memory.getData(), 0, memory.getSize());
            outState.putByteArray(STATE_RAM_DATA + ":" + memory.getId(), memoryDataBuf.array());
        }
    }

    private void restoreRandomAccessMemoryData(State inState) {
        for (RandomAccessMemory memory: randomAccessMemoryList) {
            byte[] memoryDataBytes = inState.getByteArray(STATE_RAM_DATA + ":" + memory.getId());
            ByteBuffer memoryDataBuf = ByteBuffer.wrap(memoryDataBytes);
            short[] memoryData = new short[memory.getSize()];
            memoryDataBuf.asShortBuffer().get(memoryData);
            memory.putData(memoryData);
        }
    }

    public static Configuration getStoredConfiguration(State inState) {
        return (inState != null)
                ? Configuration.valueOf(inState.getString(STATE_CONFIGURATION))
                : null;
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
        if (clockFrequency < 0) { // Zero value is valid value CLOCK_FREQUENCY_MAXIMUM
            throw new IllegalArgumentException("Invalid clock frequency to set: " + clockFrequency);
        }
        this.clockFrequency = clockFrequency;
        systemUptimeSyncCheckIntervalTicks = nanosToCpuTime(UPTIME_SYNC_CHECK_INTERVAL);
    }

    /**
     * Get native clock frequency (in kHz)
     * @return native clock frequency
     */
    public int getNativeClockFrequency() {
        return (clockFrequency != CLOCK_FREQUENCY_MAXIMUM)
                    ? clockFrequency : (configuration.model == Model.BK_0010
                        ? CLOCK_FREQUENCY_BK0010 : CLOCK_FREQUENCY_BK0011);
    }

    private void addReadOnlyMemory(ResourceManager resourceManager, int address, String romId)
            throws IOException {
        byte[] romData = loadReadOnlyMemoryData(resourceManager, romId);
        addMemory(address, new ReadOnlyMemory(romId, romData));
    }

    /**
     * Load ROM data from resources.
     * @param resourceManager {@link ResourceManager} reference
     * @param romIds ROM resource IDs
     * @return loaded ROM data
     * @throws IOException in case of ROM data loading error
     */
    private byte[] loadReadOnlyMemoryData(ResourceManager resourceManager, String... romIds)
            throws IOException {
        byte[] romData = null;
        for (String romId : romIds) {
            romData = ArrayUtils.addAll(romData, resourceManager.getReadOnlyMemoryData(romId));
        }
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
        return peripheralPort;
    }

    /**
     * Get {@link FloppyController} reference.
     * @return floppy controller reference or <code>null</code> if floppy controller is not present
     */
    public FloppyController getFloppyController() {
        return floppyController;
    }

    /**
     * Get {@link IdeController} reference.
     * @return IDE controller reference or <code>null</code> if IDE controller is not present
     */
    public IdeController getIdeController() {
        return ideController;
    }

    /**
     * Get list of available {@link AudioOutput}s.
     * @return audio outputs list
     */
    public List<AudioOutput<?>> getAudioOutputs() {
        return audioOutputs;
    }

    /**
     * Add {@link AudioOutput} device.
     * @param audioOutput audio output device to add
     */
    public void addAudioOutput(AudioOutput<?> audioOutput) {
        audioOutputs.add(audioOutput);
        addDevice(audioOutput);
    }

    /**
     * Set computer system uptime (in nanoseconds).
     * @param systemUptime computer system uptime to set (in nanoseconds)
     */
    public void setSystemUptime(long systemUptime) {
        this.systemUptime = systemUptime;
    }

    /**
     * Get computer system uptime (in nanoseconds).
     * @return computer system uptime (in nanoseconds)
     */
    public long getSystemUptime() {
        return systemUptime;
    }

    /**
     * Get computer time (in nanoseconds).
     * @return current computer uptime
     */
    public long getUptime() {
        return cpuTimeToNanos(cpu.getTime());
    }

    /**
     * Get computer time (in CPU ticks).
     * @return current computer uptime
     */
    public long getUptimeTicks() {
        return cpu.getTime();
    }

    /**
     * Add computer uptime updates listener.
     * @param uptimeListener {@link UptimeListener} object reference to add
     */
    public void addUptimeListener(UptimeListener uptimeListener) {
        uptimeListeners.add(uptimeListener);
    }

    private void notifyUptimeListeners() {
        long uptime = (clockFrequency != CLOCK_FREQUENCY_MAXIMUM) ? getUptime() : getSystemUptime();
        for (int i = 0; i < uptimeListeners.size(); i++) {
            UptimeListener uptimeListener = uptimeListeners.get(i);
            uptimeListener.uptimeUpdated(uptime);
        }
    }

    /**
     * Add memory (RAM/ROM) to address space.
     * @param address address to add memory
     * @param memory {@link Memory} to add
     */
    public void addMemory(int address, Memory memory) {
        int memoryStartBlock = address >> 12;
        int memoryBlocksCount = memory.getSize() >> 11; // ((size << 1) >> 12)
        if ((memory.getSize() & 03777) != 0) {
            memoryBlocksCount++;
        }
        MemoryRange memoryRange = new MemoryRange(address, memory);
        for (int i = 0; i < memoryBlocksCount; i++) {
            int memoryBlockIdx = memoryStartBlock + i;
            @SuppressWarnings("unchecked")
            List<MemoryRange> memoryRanges = (List<MemoryRange>) memoryTable[memoryBlockIdx];
            if (memoryRanges == null) {
                memoryRanges = new ArrayList<>(1);
                memoryTable[memoryBlockIdx] = memoryRanges;
            }
            memoryRanges.add(0, memoryRange);
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
                addressDevices = new ArrayList<>(1);
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
        if (address >= 0) {
            List<MemoryRange> memoryRanges = getMemoryRanges(address);
            if (memoryRanges == null || memoryRanges.isEmpty()) {
                return false;
            }
            for (MemoryRange memoryRange : memoryRanges) {
                if (memoryRange != null && memoryRange.isRelatedAddress(address)) {
                    int offset = address - memoryRange.getStartAddress();
                    Memory memory = memoryRange.getMemory();
                    if (memory.isReadable(offset) && !memory.isWritable(offset)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<MemoryRange> getMemoryRanges(int address) {
        return (List<MemoryRange>) memoryTable[address >> 12];
    }

    @SuppressWarnings("unchecked")
    private List<Device> getDevices(int address) {
        return (List<Device>) deviceTable[(address - IO_REGISTERS_MIN_ADDRESS) >> 1];
    }

    /**
     * Initialize bus devices state (on power-on cycle or RESET opcode).
     * @param isHardwareReset true if bus initialization is initiated by hardware reset,
     *                        false if it is initiated by RESET command
     */
    public void initDevices(boolean isHardwareReset) {
        for (Device device: deviceList) {
            device.init(getCpu().getTime(), isHardwareReset);
        }
    }

    /**
     * Reset computer state.
     */
    public void reset() {
        pause();
        synchronized (this) {
            logger.debug("resetting computer");
            getCpu().reset();
        }
        resume();
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

        int wordAddress = address & 0177776;

        // Check for memories at given address
        List<MemoryRange> memoryRanges = getMemoryRanges(address);
        if (memoryRanges != null) {
            for (int i = 0, memoryRangesSize = memoryRanges.size(); i < memoryRangesSize; i++) {
                MemoryRange memoryRange = memoryRanges.get(i);
                if (memoryRange.isRelatedAddress(address)) {
                    int memoryReadValue = memoryRange.getMemory().read(
                            wordAddress - memoryRange.getStartAddress());
                    if (memoryReadValue != BUS_ERROR) {
                        readValue = memoryReadValue;
                        break;
                    }
                }
            }
        }

        // Check for I/O registers
        if (address >= IO_REGISTERS_MIN_ADDRESS) {
            List<Device> subdevices = getDevices(address);
            if (subdevices != null) {
                long cpuClock = getCpu().getTime();
                for (int i = 0, subdevicesSize = subdevices.size(); i < subdevicesSize; i++) {
                    Device subdevice = subdevices.get(i);
                    // Read and combine subdevice state values in word mode
                    int subdeviceReadValue = subdevice.read(cpuClock, wordAddress);
                    if (subdeviceReadValue != BUS_ERROR) {
                        readValue = (readValue == BUS_ERROR)
                                ? subdeviceReadValue
                                : readValue | subdeviceReadValue;
                    }
                }
            }
        }

        if (readValue != BUS_ERROR) {
            if (isByteMode && (address & 1) != 0) {
                // Extract high byte if byte mode read from odd address
                readValue >>= 8;
            }
            readValue &= (isByteMode ? 0377 : 0177777);
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

        if (isByteMode && (address & 1) != 0) {
            value <<= 8;
        }

        // Check for memories at given address
        List<MemoryRange> memoryRanges = getMemoryRanges(address);
        if (memoryRanges != null) {
            for (int i = 0, memoryRangesSize = memoryRanges.size(); i < memoryRangesSize; i++) {
                MemoryRange memoryRange = memoryRanges.get(i);
                if (memoryRange.isRelatedAddress(address)) {
                    if (memoryRange.getMemory().write(isByteMode,
                            address - memoryRange.getStartAddress(), value)) {
                        isWritten = true;
                    }
                }
            }
        }

        // Check for I/O registers
        if (address >= IO_REGISTERS_MIN_ADDRESS) {
            List<Device> devices = getDevices(address);
            if (devices != null) {
                long cpuClock = getCpu().getTime();
                for (int i = 0, devicesSize = devices.size(); i < devicesSize; i++) {
                    Device device = devices.get(i);
                    if (device.write(cpuClock, isByteMode, address, value)) {
                        isWritten = true;
                    }
                }
            }
        }

        return isWritten;
    }

    /**
     * Start computer.
     */
    public synchronized void start() {
        if (!isRunning) {
            logger.debug("starting computer");
            clockThread = new Thread(this, "ComputerClockThread");
            isRunning = true;
            isPaused = true;
            clockThread.start();
            // Waiting for emulation thread start
            try {
                this.wait();
            } catch (InterruptedException e) {
                // Do nothing
            }
            for (AudioOutput<?> audioOutput : audioOutputs) {
                audioOutput.start();
            }
        } else {
            throw new IllegalStateException("Computer is already running!");
        }
    }

    /**
     * Stop computer.
     */
    public void stop() {
        if (isRunning) {
            logger.debug("stopping computer");
            isRunning = false;
            for (AudioOutput<?> audioOutput : audioOutputs) {
                audioOutput.stop();
            }
            synchronized (this) {
                this.notifyAll();
            }
            while (clockThread.isAlive()) {
                try {
                    clockThread.join();
                } catch (InterruptedException e) {
                    // Do nothing
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
    public void pause() {
        if (!isPaused) {
            logger.debug("pausing computer");
            isPaused = true;
            for (AudioOutput<?> audioOutput : audioOutputs) {
                audioOutput.pause();
            }
            // Wait for the running thread actual pausing
            synchronized (this) {
                this.notifyAll(); // wake up uptime sync wait if needed
            }
        }
    }

    /**
     * Resume computer.
     */
    public void resume() {
        if (isPaused) {
            logger.debug("resuming computer");
            // Sync system uptime to CPU uptime
            setSystemUptime(getUptime());
            systemUptimeUpdateTimestamp = System.nanoTime();
            systemUptimeSyncCheckTimestampTicks = getUptimeTicks();
            lastEffectiveClockFrequencyCpuUptime = getUptimeTicks();
            lastEffectiveClockFrequencySystemUptime = getSystemUptime();
            isPaused = false;
            synchronized (this) {
                this.notifyAll();
            }
            for (AudioOutput<?> audioOutput : audioOutputs) {
                audioOutput.resume();
            }
        }
    }

    /**
     * Release computer resources.
     */
    public void release() {
        logger.debug("releasing computer");
        for (AudioOutput<?> audioOutput : audioOutputs) {
            audioOutput.release();
        }
        if (floppyController != null) {
            floppyController.unmountDiskImages();
        }
        if (ideController != null) {
            ideController.detachDrives();
        }
    }

    /**
     * Get effective emulation clock frequency.
     * @return effective emulation clock frequency (in kHz)
     */
    public float getEffectiveClockFrequency() {
        long cpuUptime = getUptimeTicks();
        long systemUptime = getSystemUptime();
        long cpuTimeElapsed = cpuUptime - lastEffectiveClockFrequencyCpuUptime;
        long systemUptimeElapsed = systemUptime - lastEffectiveClockFrequencySystemUptime;
        lastEffectiveClockFrequencyCpuUptime = cpuUptime;
        lastEffectiveClockFrequencySystemUptime = systemUptime;
        return (cpuTimeElapsed > 0 && systemUptimeElapsed > 0)
                ? (float) cpuTimeElapsed * NANOSECS_IN_MSEC / systemUptimeElapsed : 0;
    }

    /**
     * Get CPU time (converted from clock ticks to nanoseconds).
     * @param cpuTime CPU time (in clock ticks) to convert
     * @return CPU time in nanoseconds
     */
    public long cpuTimeToNanos(long cpuTime) {
        return cpuTime * NANOSECS_IN_MSEC / getNativeClockFrequency();
    }

    /**
     * Get number of CPU clock ticks for given time in nanoseconds.
     * @param nanosecs time (in nanoseconds) to convert to CPU ticks
     * @return CPU ticks for given time
     */
    public long nanosToCpuTime(long nanosecs) {
        return nanosecs * getNativeClockFrequency() / NANOSECS_IN_MSEC;
    }

    /**
     * Check computer uptime is in sync with system time.
     */
    private void checkUptimeSync() {
        long uptimeTicks = getUptimeTicks();
        if (uptimeTicks - systemUptimeSyncCheckTimestampTicks < systemUptimeSyncCheckIntervalTicks) {
            return;
        }
        long systemTime = System.nanoTime();
        systemUptime += systemTime - systemUptimeUpdateTimestamp;
        systemUptimeUpdateTimestamp = systemTime;
        systemUptimeSyncCheckTimestampTicks = uptimeTicks;
        if (clockFrequency == CLOCK_FREQUENCY_MAXIMUM) {
            // Do not throttle emulation if it's maximum speed mode
            return;
        }
        long uptimesDifference = getUptime() - systemUptime;
        if (uptimesDifference >= UPTIME_SYNC_THRESHOLD) {
            long uptimesDifferenceMillis = uptimesDifference / NANOSECS_IN_MSEC;
            int uptimesDifferenceNanos = (int) (uptimesDifference % NANOSECS_IN_MSEC);
            try {
                this.wait(uptimesDifferenceMillis, uptimesDifferenceNanos);
            } catch (InterruptedException e) {
                // Do nothing
            }
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            logger.debug("computer started");
            this.notifyAll();
            while (isRunning) {
                if (isPaused) {
                    logger.debug("computer paused");
                    while (isRunning && isPaused) {
                        try {
                            this.wait(100);
                        } catch (InterruptedException e) {
                            // Do nothing
                        }
                    }
                    logger.debug("computer resumed");
                } else {
                    cpu.executeNextOperation();
                    notifyUptimeListeners();
                    checkUptimeSync();
                }
            }
        }
        logger.debug("computer stopped");
    }
}
