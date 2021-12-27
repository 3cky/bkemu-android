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

import android.util.Log;

import net.lachlanmckee.timberjunit.TimberTestRule;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;

import su.comp.bk.ResourceFileTestBase;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.io.disk.FloppyController.FloppyDrive;
import su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier;
import su.comp.bk.arch.io.disk.FloppyController.FloppyDriveSide;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;
import su.comp.bk.util.Crc16;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link FloppyController} class unit tests.
 */
@RunWith(RobolectricTestRunner.class)
public class FloppyControllerTest extends ResourceFileTestBase {
    private final static int TEST_DISK_NUM_TRACKS = 80;
    private final static String TEST_DISK_IMAGE_FILE_NAME = "test disk.img";
    private final static String FDD_ROM_FILE_NAME = "disk_327.rom";
    private final static String MONITOR_ROM_FILE_NAME = "monit10.rom";

    private static final int FDD_ERROR_CODE_ADDR = 052;
    private static final int FDD_BLOCK_START_ADDR = 02000;
    private static final int FDD_BLOCK_FORMAT_BYTE = FDD_BLOCK_START_ADDR + 017;
    private static final int FDD_BLOCK_FLAGS_A = FDD_BLOCK_START_ADDR + 022;
    private static final int FDD_BLOCK_DISK_SIDE = FDD_BLOCK_START_ADDR + 032;
    private static final int FDD_BLOCK_DISK_TRACK = FDD_BLOCK_START_ADDR + 033;
    private static final int FDD_BLOCK_DRIVE_NUM = FDD_BLOCK_START_ADDR + 034;
    private static final int FDD_BLOCK_SECLEN = FDD_BLOCK_START_ADDR + 064;

    private final static int MAX_CPU_OPS = Integer.MAX_VALUE;

    private Computer computer;
    private FloppyController floppyController;

    @Rule
    public TimberTestRule timberTestRule = TimberTestRule.builder()
            .minPriority(Log.WARN)
            .showThread(false)
            .showTimestamp(false)
            .onlyLogWhenTestFails(false)
            .build();

    @Before
    public void setUp() throws Exception {
        // Set test computer configuration
        computer = new Computer();
        computer.setClockFrequency(Computer.CLOCK_FREQUENCY_BK0010);
        RandomAccessMemory workMemory = new RandomAccessMemory("TestWorkMemory",
                020000, RandomAccessMemory.Type.K565RU6);
        computer.addMemory(0, workMemory);
        RandomAccessMemory videoMemory = new RandomAccessMemory("TestVideoMemory",
                020000, RandomAccessMemory.Type.K565RU6);
        computer.addMemory(040000, videoMemory);
        computer.addMemory(0100000, new ReadOnlyMemory("TestMonitorRom",
                FileUtils.readFileToByteArray(getTestResourceFile(MONITOR_ROM_FILE_NAME))));
        computer.addMemory(0160000, new ReadOnlyMemory("TestFloppyRom",
                FileUtils.readFileToByteArray(getTestResourceFile(FDD_ROM_FILE_NAME))));
        floppyController = new FloppyController(computer);
        computer.addDevice(floppyController);
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test method for {@link FloppyController#FloppyController(su.comp.bk.arch.Computer)}.
     * @throws Exception in case of error
     */
    @Test
    public void testFloppyController() throws Exception {
        // Check drives are positioned to track 0
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            FloppyDrive drive = floppyController.getFloppyDrive(driveIdentifier);
            assertNotNull(drive);
            assertEquals(0, drive.getCurrentTrackNumber());
            assertEquals(FloppyDriveSide.DOWN, drive.getCurrentTrackSide());
        }
        // Check drive head positioning
        FloppyDrive drive = floppyController.getFloppyDrive(FloppyDriveIdentifier.A);
        assertEquals(0, drive.getNextTrackNumber(false));
        assertEquals(1, drive.getNextTrackNumber(true));
        drive.setCurrentTrack(drive.getLastTrackNumber(), FloppyDriveSide.DOWN);
        assertEquals(drive.getLastTrackNumber() - 1, drive.getNextTrackNumber(false));
        assertEquals(drive.getLastTrackNumber(), drive.getNextTrackNumber(true));
        drive.setCurrentTrack(0, FloppyDriveSide.DOWN);
        // Check unmounted track data reading
        for (int position = 0; position < FloppyController.WORDS_PER_TRACK; position++) {
            assertEquals(0, drive.readCurrentTrackData(position));
        }
        // Mount disk image file
        byte[] testDiskImageData = mountTestDiskImage(true);
        int testDiskImageDataIndex = 0;
        // Check mounted disk image data reading
        for (int trackNumber = 0; trackNumber < TEST_DISK_NUM_TRACKS; trackNumber++) {
            for (FloppyDriveSide trackSide : FloppyDriveSide.values()) {
                drive.setCurrentTrack(trackNumber, trackSide);
                testDiskImageDataIndex = checkCurrentTrackData(drive, testDiskImageData,
                        testDiskImageDataIndex);
            }
        }
    }

    private int checkCurrentTrackData(FloppyDrive drive, byte[] data, int offset) {
        int trackPosition = 0;
        // Skip GAP1
        while (drive.readCurrentTrackData(trackPosition) == FloppyDrive.SEQ_GAP) {
            trackPosition++;
        }
        // Check sectors
        for (int sectorNumber = 1; sectorNumber <= FloppyController.SECTORS_PER_TRACK; sectorNumber++) {
            // Check header sync
            int wordsToCheck = FloppyDrive.SEQ_SYNC_LENGTH;
            while (wordsToCheck-- > 0) {
                assertEquals("header sync position: " + trackPosition,
                        FloppyDrive.SEQ_SYNC, drive.readCurrentTrackData(trackPosition++));
            }
            // Check IDAM
            assertTrue("IDAM position: " + trackPosition,
                    drive.isCurrentTrackDataMarkerPosition(trackPosition));
            assertEquals("IDAM word 1 position: " + trackPosition, 0xa1a1,
                    drive.readCurrentTrackData(trackPosition++));
            assertEquals("IDAM word 2 position: " + trackPosition, 0xa1fe,
                    drive.readCurrentTrackData(trackPosition++));
            // Check head / track numbers
            assertEquals("head/track numbers position: " + trackPosition,
                    (drive.getCurrentTrackNumber() << 8) | drive.getCurrentTrackSide().ordinal(),
                    drive.readCurrentTrackData(trackPosition++));
            // Check sector number / sector size
            assertEquals("sector number / size position: " + trackPosition,
                    (sectorNumber << 8) | 2,
                    drive.readCurrentTrackData(trackPosition++));
            // Check sector header CRC
            assertEquals("sector header CRC position: " + trackPosition,
                    Crc16.calculate(new byte[] {
                            (byte) 0xa1, (byte) 0xa1, (byte) 0xa1, (byte) 0xfe,
                            (byte) drive.getCurrentTrackNumber(),
                            (byte) drive.getCurrentTrackSide().ordinal(),
                            (byte) sectorNumber, 2 }) & 0177777,
                    drive.readCurrentTrackData(trackPosition++) & 0177777);
            // Skip GAP2
            while (drive.readCurrentTrackData(trackPosition) == FloppyDrive.SEQ_GAP) {
                trackPosition++;
            }
            // Check data sync
            wordsToCheck = FloppyDrive.SEQ_SYNC_LENGTH;
            while (wordsToCheck-- > 0) {
                assertEquals("data sync position: " + trackPosition,
                        FloppyDrive.SEQ_SYNC, drive.readCurrentTrackData(trackPosition++));
            }
            // Check DATA AM
            assertTrue("DATA AM position: " + trackPosition,
                    drive.isCurrentTrackDataMarkerPosition(trackPosition));
            assertEquals("DATA AM word 1 position: " + trackPosition, 0xa1a1,
                    drive.readCurrentTrackData(trackPosition++));
            assertEquals("DATA AM word 2 position: " + trackPosition, 0xa1fb,
                    drive.readCurrentTrackData(trackPosition++));
            // Check data
            short crcValue = Crc16.calculate(new byte[] { (byte) 0xa1, (byte) 0xa1,
                    (byte) 0xa1, (byte) 0xfb });
            wordsToCheck = FloppyController.WORDS_PER_SECTOR;
            while (wordsToCheck-- > 0) {
                byte dataByte1 = data[offset++];
                byte dataByte2 = data[offset++];
                assertEquals("Sector data position: " + trackPosition +
                                ", image index: " + (offset - 2),
                        ((dataByte1 << 8) & 0177400) | (dataByte2 & 0377),
                        drive.readCurrentTrackData(trackPosition++));
                crcValue = Crc16.calculate(crcValue, dataByte1);
                crcValue = Crc16.calculate(crcValue, dataByte2);
            }
            // Check sector data CRC
            assertEquals("sector data CRC", crcValue & 0177777,
                    drive.readCurrentTrackData(trackPosition++) & 0177777);
            if (sectorNumber < FloppyController.SECTORS_PER_TRACK) {
                // Skip GAP3
                while (drive.readCurrentTrackData(trackPosition) == FloppyDrive.SEQ_GAP) {
                    trackPosition++;
                    assertTrue("GAP3", trackPosition < FloppyController.WORDS_PER_TRACK);
                }
            } else {
                // Check GAP4B
                while (trackPosition < FloppyController.WORDS_PER_TRACK) {
                    assertEquals("GAP4B position: " + trackPosition,
                            FloppyDrive.SEQ_GAP, drive.readCurrentTrackData(trackPosition++));
                }
            }
        }
        return offset;
    }

    @Test
    public void testFloppyControllerReadOperations() throws Exception {
//        floppyController.setDebugEnabled(true);
        // Mount disk image file
        byte[] testDiskImageData = mountTestDiskImage(true);
        Cpu cpu = computer.getCpu();
        // Initialize FDD
        cpu.writeRegister(false, Cpu.R3, FDD_BLOCK_START_ADDR);
        assertTrue("can't initialize FDD", execute(0160010));
        // Single sector read
        int dataIndex = 0;
        cpu.writeMemory(true, FDD_BLOCK_DRIVE_NUM, FloppyDriveIdentifier.A.ordinal()); // Select drive
        for (int blockNumber = 0; blockNumber < testDiskImageData.length
                / FloppyController.BYTES_PER_SECTOR; blockNumber++) {
            cpu.writeRegister(false, Cpu.R0, blockNumber); // Sector number
            cpu.writeRegister(false, Cpu.R1, 0400); // Data length
            cpu.writeRegister(false, Cpu.R2, 01000); // Data read address
            assertTrue("can't read sector " + blockNumber, execute(0160004));
            assertFalse("sector " + blockNumber + " read error " +
                    computer.readMemory(false, FDD_ERROR_CODE_ADDR),
                    cpu.isPswFlagSet(Cpu.PSW_FLAG_C));
            // Check read data
            for (int address = 01000; address < 02000; address++) {
                assertEquals("sector " + blockNumber + " read error at address " +
                        Integer.toOctalString(address), testDiskImageData[dataIndex++] & 0377,
                        computer.readMemory(true, address));
            }
        }
        // Multisector read
        cpu.writeRegister(false, Cpu.R0, 0); // Sector number
        cpu.writeRegister(false, Cpu.R1, 020000); // Data length
        cpu.writeRegister(false, Cpu.R2, 040000); // Data read address
        assertTrue("can't read block", execute(0160004));
        assertFalse("block read error " + computer.readMemory(true, FDD_ERROR_CODE_ADDR),
                cpu.isPswFlagSet(Cpu.PSW_FLAG_C));
        dataIndex = 0;
        for (int address = 040000; address < 0100000; address++) {
            assertEquals("block read error at address " + Integer.toOctalString(address),
                    testDiskImageData[dataIndex++] & 0377,
                    computer.readMemory(true, address));
        }
    }

    @Test
    public void testFloppyControllerWriteOperations() throws Exception {
//        floppyController.setDebugEnabled(true);
        // Mount disk image file
        byte[] testDiskImageData = mountTestDiskImage(false);
        DiskImage testDiskImage = floppyController.getFloppyDrive(FloppyDriveIdentifier.A).getMountedDiskImage();
        Cpu cpu = computer.getCpu();
        // Initialize FDD
        cpu.writeRegister(false, Cpu.R3, FDD_BLOCK_START_ADDR);
        assertTrue("can't initialize FDD", execute(0160010));
        cpu.writeMemory(true, FDD_BLOCK_DRIVE_NUM, FloppyDriveIdentifier.A.ordinal()); // Select drive
        // Track formatting
        FloppyDrive drive = floppyController.getFloppyDrive(FloppyDriveIdentifier.A);
        cpu.writeMemory(true, FDD_BLOCK_FLAGS_A, 0); // Standard disk format
        cpu.writeMemory(true, FDD_BLOCK_FORMAT_BYTE, 0x42); // Byte to write
        cpu.writeMemory(true, FDD_BLOCK_DISK_SIDE, 0); // Select side
        cpu.writeMemory(true, FDD_BLOCK_DISK_TRACK, 0); // Select track
        cpu.writeMemory(false, FDD_BLOCK_SECLEN, 0400); // Sector length
        assertTrue("can't format track", execute(0160012));
        assertFalse("track formatting error " + computer.readMemory(true, FDD_ERROR_CODE_ADDR),
                cpu.isPswFlagSet(Cpu.PSW_FLAG_C));
        byte[] trackData = new byte[FloppyController.SECTORS_PER_TRACK * FloppyController.BYTES_PER_SECTOR];
        Arrays.fill(trackData, (byte) 0x42);
        checkCurrentTrackData(drive, trackData, 0);
        for (int i = 0; i < FloppyController.SECTORS_PER_TRACK * FloppyController.BYTES_PER_SECTOR; i++) {
            assertEquals("track formatting error at " + i,
                    trackData[i] & 0377, testDiskImage.readByte(i) & 0377);
        }
        // Multisector write
        for (int i = 0; i < 020000; i++) {
            cpu.writeMemory(true, 040000 + i, testDiskImageData[i]);
        }
        cpu.writeRegister(false, Cpu.R0, 0); // Sector number
        cpu.writeRegister(false, Cpu.R1, -020000); // Data length (negative for write)
        cpu.writeRegister(false, Cpu.R2, 040000); // Data write address
        assertTrue("can't write block", execute(0160004));
        assertFalse("block write error " + computer.readMemory(true, FDD_ERROR_CODE_ADDR),
                cpu.isPswFlagSet(Cpu.PSW_FLAG_C));
        for (int i = 0; i < 020000; i++) {
            assertEquals("block write error at " + i,
                    testDiskImageData[i] & 0377, testDiskImage.readByte(i) & 0377);
        }
    }

    private byte[] mountTestDiskImage(boolean isWriteProtected) throws Exception {
        File testDiskImageFile = getTestResourceFile(TEST_DISK_IMAGE_FILE_NAME);
        byte[] testDiskImageData = FileUtils.readFileToByteArray(testDiskImageFile);
        FileDiskImage testDiskImage = new FileDiskImage(testDiskImageFile);
        floppyController.mountDiskImage(testDiskImage, FloppyDriveIdentifier.A, isWriteProtected);
        return testDiskImageData;
    }

    private boolean execute(int address) {
        boolean isSuccess = false;
        Cpu cpu = computer.getCpu();
        int initialAddress = cpu.readRegister(false, Cpu.PC);
        assertTrue("can't push PC to stack", cpu.push(initialAddress));
        cpu.writeRegister(false, Cpu.PC, address);
        int cpuOps = MAX_CPU_OPS;
        while (cpuOps-- > 0) {
            try {
                cpu.executeNextOperation();
            } catch (Exception e) {
                e.printStackTrace();
                fail("can't execute operation, PC: " + Integer.toOctalString(cpu
                        .readRegister(false, Cpu.PC)));
            }
            if (initialAddress == cpu.readRegister(false, Cpu.PC)) {
                isSuccess = true;
                break;
            }
        }
        return isSuccess;
    }
}
