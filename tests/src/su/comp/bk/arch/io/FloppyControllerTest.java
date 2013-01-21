/*
 * Created: 25.10.2012
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

import static org.junit.Assert.*;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.FloppyController.FloppyDrive;
import su.comp.bk.arch.io.FloppyController.FloppyDrive.FloppyDriveTrackSequence;
import su.comp.bk.arch.io.FloppyController.FloppyDriveIdentifier;
import su.comp.bk.arch.io.FloppyController.FloppyDriveSide;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;
import su.comp.bk.util.Crc16;

/**
 * {@link FloppyController} class unit tests.
 */
public class FloppyControllerTest {

    private final static String TEST_DISK_IMAGE_FILE_NAME = "target/test-classes/test.img";
    private final static String FDD_ROM_FILE_NAME = "res/raw/disk_327.rom";
    private final static String MONITOR_ROM_FILE_NAME = "res/raw/monit10.rom";

    private static final int FDD_BLOCK_START_ADDR = 02000;
    private static final int FDD_BLOCK_DRIVE_NUM = FDD_BLOCK_START_ADDR + 034;

    private final static int MAX_CPU_OPS = Integer.MAX_VALUE;

    private Computer computer;
    private FloppyController floppyController;

    @Before
    public void setUp() throws Exception {
        // Set test computer configuration
        computer = new Computer();
        computer.setClockFrequency(Computer.CLOCK_FREQUENCY_BK0010);
        RandomAccessMemory workMemory = new RandomAccessMemory("TestWorkMemory", 0, 020000);
        computer.addMemory(workMemory);
        RandomAccessMemory videoMemory = new RandomAccessMemory("TestVideoMemory", 040000, 020000);
        computer.addMemory(videoMemory);
        computer.addMemory(new ReadOnlyMemory("TestMonitorRom", 0100000,
                FileUtils.readFileToByteArray(new File(MONITOR_ROM_FILE_NAME))));
        computer.addMemory(new ReadOnlyMemory("TestFloppyRom", 0160000,
                FileUtils.readFileToByteArray(new File(FDD_ROM_FILE_NAME))));
        floppyController = new FloppyController(computer);
        computer.addDevice(floppyController);
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.reset();
        computer.getCpu().writeRegister(false, Cpu.SP, 01000);
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test method for {@link su.comp.bk.arch.io.FloppyController#FloppyController(su.comp.bk.arch.Computer)}.
     * @throws Exception
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
        drive.setCurrentTrack(FloppyController.TRACKS_PER_DISK - 1, FloppyDriveSide.DOWN);
        assertEquals(FloppyController.TRACKS_PER_DISK - 2, drive.getNextTrackNumber(false));
        assertEquals(FloppyController.TRACKS_PER_DISK - 1, drive.getNextTrackNumber(true));
        drive.setCurrentTrack(0, FloppyDriveSide.DOWN);
        // Check unmounted track data reading
        for (int position = 0; position < FloppyController.WORDS_PER_TRACK; position++) {
            assertEquals(0, drive.readCurrentTrackData(position));
        }
        // Mount disk image file
        byte[] testDiskImageData = mountTestDiskImage();
        int testDiskImageDataIndex = 0;
        // Check mounted disk image data reading
        for (int trackNumber = 0; trackNumber < FloppyController.TRACKS_PER_DISK; trackNumber++) {
            for (FloppyDriveSide trackSide : FloppyDriveSide.values()) {
                drive.setCurrentTrack(trackNumber, trackSide);
                int trackPosition = 0;
                int wordsToCheck = FloppyDriveTrackSequence.SEQ_GAP1_LENGTH;
                // Check GAP1
                while (wordsToCheck-- > 0) {
                    assertEquals("GAP1 position: " + trackPosition, FloppyDriveTrackSequence.SEQ_GAP,
                            drive.readCurrentTrackData(trackPosition++));
                }
                // Check sectors
                for (int sectorNumber = 1; sectorNumber <= FloppyController.SECTORS_PER_TRACK; sectorNumber++) {
                    // Check header sync
                    wordsToCheck = FloppyDriveTrackSequence.SEQ_SYNC_LENGTH;
                    while (wordsToCheck-- > 0) {
                        assertEquals("header sync position: " + trackPosition, FloppyDriveTrackSequence.SEQ_SYNC,
                                drive.readCurrentTrackData(trackPosition++));
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
                    // Check sector head CRC
                    assertTrue("sector header CRC position: " + trackPosition,
                            drive.isCurrentTrackDataCrcPosition(trackPosition));
                    assertEquals("sector header CRC position: " + trackPosition,
                            Crc16.calculate(new byte[] {
                                (byte) 0xa1, (byte) 0xa1, (byte) 0xa1, (byte) 0xfe,
                                (byte) drive.getCurrentTrackNumber(),
                                (byte) drive.getCurrentTrackSide().ordinal(),
                                (byte) sectorNumber, 2 }) & 0177777,
                        drive.readCurrentTrackData(trackPosition++) & 0177777);
                    // Check GAP2
                    wordsToCheck = FloppyDriveTrackSequence.SEQ_GAP2_LENGTH;
                    while (wordsToCheck-- > 0) {
                        assertEquals("GAP2 position: " + trackPosition,
                                FloppyDriveTrackSequence.SEQ_GAP,
                                drive.readCurrentTrackData(trackPosition++));
                    }
                    // Check data sync
                    wordsToCheck = FloppyDriveTrackSequence.SEQ_SYNC_LENGTH;
                    while (wordsToCheck-- > 0) {
                        assertEquals("data sync position: " + trackPosition,
                                FloppyDriveTrackSequence.SEQ_SYNC,
                                drive.readCurrentTrackData(trackPosition++));
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
                    wordsToCheck = FloppyController.BYTES_PER_SECTOR >> 1;
                    while (wordsToCheck-- > 0) {
                        byte dataByte1 = testDiskImageData[testDiskImageDataIndex++];
                        byte dataByte2 = testDiskImageData[testDiskImageDataIndex++];
                        assertEquals("Sector data position: " + trackPosition +
                                ", image index: " + (testDiskImageDataIndex - 2),
                                ((dataByte1 << 8) & 0177400) | (dataByte2 & 0377),
                                drive.readCurrentTrackData(trackPosition++));
                        crcValue = Crc16.calculate(crcValue, dataByte1);
                        crcValue = Crc16.calculate(crcValue, dataByte2);
                    }
                    // Check sector data CRC
                    assertTrue("sector data CRC position: " + trackPosition,
                            drive.isCurrentTrackDataCrcPosition(trackPosition));
                    assertEquals("sector data CRC", crcValue & 0177777,
                            drive.readCurrentTrackData(trackPosition++) & 0177777);
                    // Check GAP3/GAP4B
                    wordsToCheck = (sectorNumber < FloppyController.SECTORS_PER_TRACK)
                            ?  FloppyDriveTrackSequence.SEQ_GAP3_LENGTH
                                    : (FloppyController.WORDS_PER_TRACK - trackPosition );
                    while (wordsToCheck-- > 0) {
                        assertEquals("GAP3/GAP4B position: " + trackPosition,
                                FloppyDriveTrackSequence.SEQ_GAP,
                                drive.readCurrentTrackData(trackPosition++));
                    }
                }
            }
        }
    }

    @Test
    public void testFloppyControllerOperations() throws Exception {
//        floppyController.setDebugEnabled(true);
        // Mount disk image file
        byte[] testDiskImageData = mountTestDiskImage();
        Cpu cpu = computer.getCpu();
        // Initialize FDD
        cpu.writeRegister(false, Cpu.R3, FDD_BLOCK_START_ADDR);
        assertTrue("can't initialize FDD", execute(0160010));
        // Single sector read
        int dataIndex = 0;
        cpu.writeMemory(true, FDD_BLOCK_DRIVE_NUM, FloppyDriveIdentifier.A.ordinal()); // Select drive
        for (int blockNumber = 0; blockNumber < FloppyController.BYTES_PER_DISK
                / FloppyController.BYTES_PER_SECTOR; blockNumber++) {
            cpu.writeRegister(false, Cpu.R0, blockNumber); // Sector number
            cpu.writeRegister(false, Cpu.R1, 0400); // Data length
            cpu.writeRegister(false, Cpu.R2, 01000); // Data read address
            assertTrue("can't read sector " + blockNumber, execute(0160004));
            assertTrue("sector " + blockNumber + " read error " + computer.readMemory(false, 052),
                    !cpu.isPswFlagSet(Cpu.PSW_FLAG_C));
            // Check read data
            for (int address = 01000; address < 02000; address++) {
                assertEquals("sector " + blockNumber + " read error at address " +
                        Integer.toOctalString(address), testDiskImageData[dataIndex++] & 0377,
                        computer.readMemory(true, address));
            }
        }
        // Multisector read
        dataIndex = 0;
        cpu.writeRegister(false, Cpu.R0, 0); // Sector number
        cpu.writeRegister(false, Cpu.R1, 020000); // Data length
        cpu.writeRegister(false, Cpu.R2, 040000); // Data read address
        assertTrue("can't read block", execute(0160004));
        for (int address = 040000; address < 0100000; address++) {
            assertEquals("block read error at address " + Integer.toOctalString(address),
                    testDiskImageData[dataIndex++] & 0377,
                    computer.readMemory(true, address));
        }
    }

    private byte[] mountTestDiskImage() throws Exception {
        File testDiskImageFile = new File(TEST_DISK_IMAGE_FILE_NAME);
        byte[] testDiskImageData = FileUtils.readFileToByteArray(testDiskImageFile);
        floppyController.mountDiskImage(testDiskImageFile.toURI().toString(),
                FloppyDriveIdentifier.A, true);
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
