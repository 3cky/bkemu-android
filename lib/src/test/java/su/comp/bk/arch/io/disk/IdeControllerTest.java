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
package su.comp.bk.arch.io.disk;

import static org.junit.Assert.assertEquals;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;

import su.comp.bk.ResourceFileTestBase;
import su.comp.bk.arch.io.disk.IdeController.IdeInterface;

/**
 * {@link IdeController} class unit tests.
 */
public class IdeControllerTest extends ResourceFileTestBase {
    public final static int TEST_DRIVE_NUM_CYLINDERS = 10;
    public final static int TEST_DRIVE_NUM_HEADS = 4;
    public final static int TEST_DRIVE_NUM_SECTORS = 16;

    private final static String TEST_HDI_IMAGE_FILE_NAME = "test.hdi";
    private final static String TEST_RAW_IMAGE_FILE_NAME = "test_hdd.img";

    public final static int TEST_IMAGE_NUM_CYLINDERS = 40;
    public final static int TEST_IMAGE_NUM_HEADS = 16;
    public final static int TEST_IMAGE_NUM_SECTORS = 63;

    private IdeController ideController;

    @Before
    public void setUp() throws Exception {
        ideController = new IdeController();
    }

    @After
    public void tearDown() {
    }

    public static class TestIdeDrive implements IdeController.IdeDrive {
        private final int numCylinders;
        private final int numHeads;
        private final int numSectors;

        private final byte[] data;

        // Linear sector number at which read/write must report a failure (-1 to disable)
        private long failSector = -1;

        public TestIdeDrive(int numCylinders, int numHeads, int numSectors) {
            this.numCylinders = numCylinders;
            this.numHeads = numHeads;
            this.numSectors = numSectors;
            data = new byte[(int) (getTotalNumSectors() * IdeController.SECTOR_SIZE)];
        }

        public void setFailSector(long failSector) {
            this.failSector = failSector;
        }

        private boolean isFailingRange(long sectorIndex, int numSectors) {
            return failSector >= sectorIndex && failSector < sectorIndex + numSectors;
        }

        @Override
        public String getName() {
            return "TestIdeDrive";
        }

        @Override
        public void detach() {
            // Do nothing
        }

        @Override
        public int read(byte[] buffer, long sectorIndex, int numSectors) {
            if (isFailingRange(sectorIndex, numSectors)) {
                return -1;
            }
            for (int i = 0; i < numSectors * IdeController.SECTOR_SIZE; i++) {
                buffer[i] = data[(int) (sectorIndex * IdeController.SECTOR_SIZE + i)];
            }
            return numSectors;
        }

        @Override
        public int write(byte[] buffer, long sectorIndex, int numSectors) {
            if (isFailingRange(sectorIndex, numSectors)) {
                return -1;
            }
            for (int i = 0; i < numSectors * IdeController.SECTOR_SIZE; i++) {
                data[(int) (sectorIndex * IdeController.SECTOR_SIZE + i)] = buffer[i];
            }
            return numSectors;
        }

        @Override
        public int getNumCylinders() {
            return numCylinders;
        }

        @Override
        public int getNumHeads() {
            return numHeads;
        }

        @Override
        public int getNumSectors() {
            return numSectors;
        }

        @Override
        public long getTotalNumSectors() {
            return (long) numHeads * numCylinders * numSectors;
        }

        public byte[] getData() {
            return data;
        }
    }

    private TestIdeDrive createTestIdeDrive(int iface) {
        TestIdeDrive testIdeDrive = new TestIdeDrive(TEST_DRIVE_NUM_CYLINDERS,
                TEST_DRIVE_NUM_HEADS, TEST_DRIVE_NUM_SECTORS);
        byte[] data = testIdeDrive.getData();
        new Random(iface).nextBytes(data);
        return testIdeDrive;
    }

    private byte[] readData() {
        return readData(IdeController.SECTOR_SIZE);
    }

    private byte[] readData(int numBytes) {
        ByteBuffer buf = ByteBuffer.allocate(numBytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuf = buf.asShortBuffer();
        for (int i = 0; i < numBytes / 2; i++) {
            assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DRQ | IdeInterface.SR_DSC, ideController.readTaskRegister(IdeController.REG_STATUS));
            shortBuf.put((short) ideController.readTaskRegister(IdeController.REG_DATA));
        }
        assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DSC, ideController.readTaskRegister(IdeController.REG_STATUS));
        buf.flip();
        return buf.array();
    }

    private void writeData(byte[] data) {
        writeData(data, data.length);
    }

    private void writeData(byte[] data, int numBytes) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuf = buf.asShortBuffer();
        for (int i = 0; i < numBytes / 2; i++) {
            assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DRQ | IdeInterface.SR_DSC, ideController.readTaskRegister(IdeController.REG_STATUS));
            ideController.writeTaskRegister(IdeController.REG_DATA, shortBuf.get(i) & 0xFFFF);
        }
        assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DSC, ideController.readTaskRegister(IdeController.REG_STATUS));
    }

    /**
     * Program the task registers (CHS mode, master drive) to point at the given linear
     * sector number using the drive geometry.
     */
    private void setChsAddress(TestIdeDrive drive, int lbaSector) {
        int sectorsPerCylinder = drive.getNumHeads() * drive.getNumSectors();
        int cylinder = lbaSector / sectorsPerCylinder;
        int remainder = lbaSector % sectorsPerCylinder;
        int head = remainder / drive.getNumSectors();
        int sector = remainder % drive.getNumSectors() + 1;
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_LOW, cylinder & 0xFF);
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_HIGH, (cylinder >> 8) & 0xFF);
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, head & 0x0F);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_NUMBER, sector);
    }

    /**
     * Assert the task registers (CHS mode, master drive) hold the address of the given
     * linear sector number.
     */
    private void assertChsAddress(TestIdeDrive drive, int lbaSector) {
        int sectorsPerCylinder = drive.getNumHeads() * drive.getNumSectors();
        int cylinder = lbaSector / sectorsPerCylinder;
        int remainder = lbaSector % sectorsPerCylinder;
        int head = remainder / drive.getNumSectors();
        int sector = remainder % drive.getNumSectors() + 1;
        assertEquals("Cylinder low mismatch for sector " + lbaSector,
                cylinder & 0xFF, ideController.readTaskRegister(IdeController.REG_CYLINDER_LOW));
        assertEquals("Cylinder high mismatch for sector " + lbaSector,
                (cylinder >> 8) & 0xFF, ideController.readTaskRegister(IdeController.REG_CYLINDER_HIGH));
        assertEquals("Head mismatch for sector " + lbaSector,
                head, ideController.readTaskRegister(IdeController.REG_DRIVE_HEAD) & 0x0F);
        assertEquals("Sector number mismatch for sector " + lbaSector,
                sector, ideController.readTaskRegister(IdeController.REG_SECTOR_NUMBER));
    }

    private static void compareData(byte[] expectedData, byte[] data, int expectedDataOffset) {
        for (int i = 0; i < data.length; i++) {
            int offset = expectedDataOffset + i;
            assertEquals("Data mismatch at offset " + offset,
                    expectedData[offset] & 0xFF, data[i] & 0xFF);
        }
    }

    private static int getInt16(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }

    @Test
    public void testIdeController() {
        // Check registers with no attached drives
        assertEquals(0, ideController.readAltStatusRegister());
        assertEquals(0, ideController.readTaskRegister(IdeController.REG_STATUS));
        // Check reset with no attached drives
        ideController.writeControlRegister(IdeInterface.CR_SRST);
        ideController.writeControlRegister(0);
        // Attach test drives
        TestIdeDrive testIdeDrive0 = createTestIdeDrive(IdeController.IF_0);
        TestIdeDrive testIdeDrive1 = createTestIdeDrive(IdeController.IF_1);
        ideController.attachDrive(IdeController.IF_0, testIdeDrive0);
        ideController.attachDrive(IdeController.IF_1, testIdeDrive1);
        // Reset drives
        ideController.writeControlRegister(IdeInterface.CR_SRST);
        // Check drives busy status while reset active
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        assertEquals(IdeInterface.SR_BSY, ideController.readTaskRegister(IdeController.REG_STATUS));
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, IdeInterface.DHR_DRV);
        assertEquals(IdeInterface.SR_BSY, ideController.readTaskRegister(IdeController.REG_STATUS));
        // Deactivate reset
        ideController.writeControlRegister(0);
        // Check drive states
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0); // master
        assertEquals(IdeInterface.SR_DRDY, ideController.readTaskRegister(IdeController.REG_STATUS));
        assertEquals(0x01, ideController.readTaskRegister(IdeController.REG_ERROR));
        assertEquals(0x00, ideController.readTaskRegister(IdeController.REG_CYLINDER_LOW));
        assertEquals(0x00, ideController.readTaskRegister(IdeController.REG_CYLINDER_HIGH));
        assertEquals(0x01, ideController.readTaskRegister(IdeController.REG_SECTOR_COUNT));
        assertEquals(0x01, ideController.readTaskRegister(IdeController.REG_SECTOR_NUMBER));
        assertEquals(0xA0, ideController.readTaskRegister(IdeController.REG_DRIVE_HEAD));
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, IdeInterface.DHR_DRV); // slave
        assertEquals(IdeInterface.SR_DRDY, ideController.readTaskRegister(IdeController.REG_STATUS));
        assertEquals(0x01, ideController.readTaskRegister(IdeController.REG_ERROR));
        assertEquals(0x00, ideController.readTaskRegister(IdeController.REG_CYLINDER_LOW));
        assertEquals(0x00, ideController.readTaskRegister(IdeController.REG_CYLINDER_HIGH));
        assertEquals(0x01, ideController.readTaskRegister(IdeController.REG_SECTOR_COUNT));
        assertEquals(0x01, ideController.readTaskRegister(IdeController.REG_SECTOR_NUMBER));
        assertEquals(0xA0 | IdeInterface.DHR_DRV, ideController.readTaskRegister(IdeController.REG_DRIVE_HEAD));
        // Send identify command
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0); // master
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_IDENTIFY);
        byte[] identifyData = readData();
        assertEquals(0x40, getInt16(identifyData, IdeInterface.IDENTIFY_DEVICE_TYPE));
        assertEquals(testIdeDrive0.getNumCylinders(), getInt16(identifyData, IdeInterface.IDENTIFY_NUM_CYLINDERS));
        assertEquals(testIdeDrive0.getNumHeads(), getInt16(identifyData, IdeInterface.IDENTIFY_NUM_HEADS));
        assertEquals(testIdeDrive0.getNumSectors(), getInt16(identifyData, IdeInterface.IDENTIFY_NUM_SECTORS));
        // Single sector read (address is set explicitly before every command, as real
        // software must do - after a command the registers hold the last transferred
        // sector, not the next one)
        byte[] driveData = testIdeDrive0.getData();
        for (int s = 0; s < testIdeDrive0.getTotalNumSectors(); s++) {
            setChsAddress(testIdeDrive0, s);
            ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 1);
            ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_READ);
            compareData(driveData, readData(), IdeController.SECTOR_SIZE * s);
            // After the read the registers point at the just-transferred sector
            assertChsAddress(testIdeDrive0, s);
        }
        // Multiple sector read
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_NUMBER, 2);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 0);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_READ);
        compareData(driveData, readData(IdeController.SECTOR_SIZE * 256), IdeController.SECTOR_SIZE);
        // Registers hold the last transferred sector (started at LBA 1, read 256 sectors
        // across cylinder/head boundaries)
        assertChsAddress(testIdeDrive0, 1 + 256 - 1);
        // Single sector write (address is set explicitly before every command)
        byte[] writeData = new byte[IdeController.SECTOR_SIZE];
        for (int s = 0; s < testIdeDrive0.getTotalNumSectors(); s++) {
            new Random(s).nextBytes(writeData);
            setChsAddress(testIdeDrive0, s);
            ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 1);
            ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_WRITE);
            writeData(writeData);
            compareData(driveData, writeData, IdeController.SECTOR_SIZE * s);
            // After the write the registers point at the just-transferred sector
            assertChsAddress(testIdeDrive0, s);
        }
        // Multiple sector write
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_NUMBER, 2);
        writeData = new byte[IdeController.SECTOR_SIZE * 256];
        new Random(-1).nextBytes(writeData);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 0);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_WRITE);
        writeData(writeData);
        compareData(driveData, writeData, IdeController.SECTOR_SIZE);
        // Registers hold the last transferred sector (started at LBA 1, wrote 256 sectors
        // across cylinder/head boundaries)
        assertChsAddress(testIdeDrive0, 1 + 256 - 1);
    }

    @Test
    public void testTransferError() {
        TestIdeDrive testIdeDrive = createTestIdeDrive(IdeController.IF_0);
        ideController.attachDrive(IdeController.IF_0, testIdeDrive);
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0); // master, CHS mode
        // Fail on the first sector of the second cylinder (a cylinder boundary)
        int failSector = testIdeDrive.getNumHeads() * testIdeDrive.getNumSectors();
        testIdeDrive.setFailSector(failSector);

        // Single sector read of the failing sector: error is reported and the task
        // registers point at the failing sector (no data is made available)
        setChsAddress(testIdeDrive, failSector);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 1);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_READ);
        assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DSC | IdeInterface.SR_ERR,
                ideController.readTaskRegister(IdeController.REG_STATUS));
        assertEquals(IdeInterface.ER_UNC, ideController.readTaskRegister(IdeController.REG_ERROR));
        assertChsAddress(testIdeDrive, failSector);

        // Multi-sector read failing on the third sector (which crosses a cylinder
        // boundary): the two good sectors are transferred, then the error is raised
        // with the registers left at the failing sector
        int startSector = failSector - 2;
        setChsAddress(testIdeDrive, startSector);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 5);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_READ);
        // Drain the two good sectors; reading the last word triggers the failing read
        for (int i = 0; i < 2 * IdeController.SECTOR_SIZE / 2; i++) {
            assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DRQ | IdeInterface.SR_DSC,
                    ideController.readTaskRegister(IdeController.REG_STATUS));
            ideController.readTaskRegister(IdeController.REG_DATA);
        }
        assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DSC | IdeInterface.SR_ERR,
                ideController.readTaskRegister(IdeController.REG_STATUS));
        assertEquals(IdeInterface.ER_UNC, ideController.readTaskRegister(IdeController.REG_ERROR));
        assertChsAddress(testIdeDrive, failSector);

        // Single sector write of the failing sector behaves the same way
        setChsAddress(testIdeDrive, failSector);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 1);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_WRITE);
        for (int i = 0; i < IdeController.SECTOR_SIZE / 2; i++) {
            assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DRQ | IdeInterface.SR_DSC,
                    ideController.readTaskRegister(IdeController.REG_STATUS));
            ideController.writeTaskRegister(IdeController.REG_DATA, 0);
        }
        assertEquals(IdeInterface.SR_DRDY | IdeInterface.SR_DSC | IdeInterface.SR_ERR,
                ideController.readTaskRegister(IdeController.REG_STATUS));
        assertEquals(IdeInterface.ER_UNC, ideController.readTaskRegister(IdeController.REG_ERROR));
        assertChsAddress(testIdeDrive, failSector);
    }

    @Test
    public void testIdeDriveHdiImage() throws Exception {
        File testImageFile = getTestResourceFile(TEST_HDI_IMAGE_FILE_NAME);
        byte[] testImageData = FileUtils.readFileToByteArray(testImageFile);
        FileDiskImage testImage = new FileDiskImage(testImageFile);
        IdeController.IdeDriveHdiImage testIdeDriveHdiImage =
                new IdeController.IdeDriveHdiImage(testImage);
        assertEquals(TEST_IMAGE_NUM_CYLINDERS, testIdeDriveHdiImage.getNumCylinders());
        assertEquals(TEST_IMAGE_NUM_HEADS, testIdeDriveHdiImage.getNumHeads());
        assertEquals(TEST_IMAGE_NUM_SECTORS, testIdeDriveHdiImage.getNumSectors());
        ideController.attachDrive(IdeController.IF_0, testIdeDriveHdiImage);
        // Send identify command
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_IDENTIFY);
        byte[] identifyData = readData();
        assertEquals(0x40, getInt16(identifyData, IdeInterface.IDENTIFY_DEVICE_TYPE));
        assertEquals(TEST_IMAGE_NUM_CYLINDERS, getInt16(identifyData, IdeInterface.IDENTIFY_NUM_CYLINDERS));
        assertEquals(TEST_IMAGE_NUM_HEADS, getInt16(identifyData, IdeInterface.IDENTIFY_NUM_HEADS));
        assertEquals(TEST_IMAGE_NUM_SECTORS, getInt16(identifyData, IdeInterface.IDENTIFY_NUM_SECTORS));
        // Read first sector
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_NUMBER, 1);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 1);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_READ);
        compareData(testImageData, readData(), IdeController.SECTOR_SIZE);
    }

    @Test
    public void testIdeDriveRawImage() throws Exception {
        File testImageFile = getTestResourceFile(TEST_RAW_IMAGE_FILE_NAME);
        byte[] testImageData = FileUtils.readFileToByteArray(testImageFile);
        FileDiskImage testImage = new FileDiskImage(testImageFile);
        IdeController.IdeDriveRawImage testIdeDriveRawImage =
                new IdeController.IdeDriveRawImage(testImage);
        assertEquals(TEST_IMAGE_NUM_CYLINDERS, testIdeDriveRawImage.getNumCylinders());
        assertEquals(TEST_IMAGE_NUM_HEADS, testIdeDriveRawImage.getNumHeads());
        assertEquals(TEST_IMAGE_NUM_SECTORS, testIdeDriveRawImage.getNumSectors());
        ideController.attachDrive(IdeController.IF_0, testIdeDriveRawImage);
        // Send identify command
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_IDENTIFY);
        byte[] identifyData = readData();
        assertEquals(0x40, getInt16(identifyData, IdeInterface.IDENTIFY_DEVICE_TYPE));
        assertEquals(TEST_IMAGE_NUM_CYLINDERS, getInt16(identifyData, IdeInterface.IDENTIFY_NUM_CYLINDERS));
        assertEquals(TEST_IMAGE_NUM_HEADS, getInt16(identifyData, IdeInterface.IDENTIFY_NUM_HEADS));
        assertEquals(TEST_IMAGE_NUM_SECTORS, getInt16(identifyData, IdeInterface.IDENTIFY_NUM_SECTORS));
        // Read first sector
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(IdeController.REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(IdeController.REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_NUMBER, 1);
        ideController.writeTaskRegister(IdeController.REG_SECTOR_COUNT, 1);
        ideController.writeTaskRegister(IdeController.REG_COMMAND, IdeInterface.CMD_READ);
        compareData(testImageData, readData(), 0);
    }
}
