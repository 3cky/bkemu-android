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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static su.comp.bk.arch.io.disk.IdeController.IF_0;
import static su.comp.bk.arch.io.disk.IdeController.IF_1;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.CMD_IDENTIFY;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.CMD_READ;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.CMD_WRITE;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.CR_SRST;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DHR_DRV;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_NUM_CYLINDERS;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_DEVICE_TYPE;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_NUM_HEADS;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_NUM_SECTORS;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.SR_BSY;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.SR_DRDY;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.SR_DRQ;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.SR_DSC;
import static su.comp.bk.arch.io.disk.IdeController.REG_COMMAND;
import static su.comp.bk.arch.io.disk.IdeController.REG_CYLINDER_HIGH;
import static su.comp.bk.arch.io.disk.IdeController.REG_CYLINDER_LOW;
import static su.comp.bk.arch.io.disk.IdeController.REG_DATA;
import static su.comp.bk.arch.io.disk.IdeController.REG_DRIVE_HEAD;
import static su.comp.bk.arch.io.disk.IdeController.REG_ERROR;
import static su.comp.bk.arch.io.disk.IdeController.REG_SECTOR_COUNT;
import static su.comp.bk.arch.io.disk.IdeController.REG_SECTOR_NUMBER;
import static su.comp.bk.arch.io.disk.IdeController.REG_STATUS;
import static su.comp.bk.arch.io.disk.IdeController.SECTOR_SIZE;

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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;

import su.comp.bk.ResourceFileTestBase;

/**
 * {@link IdeController} class unit tests.
 */
@RunWith(RobolectricTestRunner.class)
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

    @Rule
    public TimberTestRule timberTestRule = TimberTestRule.builder()
            .minPriority(Log.WARN)
            .showThread(false)
            .showTimestamp(false)
            .onlyLogWhenTestFails(false)
            .build();

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

        public TestIdeDrive(int numCylinders, int numHeads, int numSectors) {
            this.numCylinders = numCylinders;
            this.numHeads = numHeads;
            this.numSectors = numSectors;
            data = new byte[(int) (getTotalNumSectors() * SECTOR_SIZE)];
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
            for (int i = 0; i < numSectors * SECTOR_SIZE; i++) {
                buffer[i] = data[(int) (sectorIndex * SECTOR_SIZE + i)];
            }
            return numSectors;
        }

        @Override
        public int write(byte[] buffer, long sectorIndex, int numSectors) {
            for (int i = 0; i < numSectors * SECTOR_SIZE; i++) {
                data[(int) (sectorIndex * SECTOR_SIZE + i)] = buffer[i];
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
            return (long) numSectors * numCylinders * numSectors;
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
        return readData(SECTOR_SIZE);
    }

    private byte[] readData(int numBytes) {
        ByteBuffer buf = ByteBuffer.allocate(numBytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuf = buf.asShortBuffer();
        for (int i = 0; i < numBytes / 2; i++) {
            assertEquals(SR_DRDY | SR_DRQ | SR_DSC, ideController.readTaskRegister(REG_STATUS));
            shortBuf.put((short) ideController.readTaskRegister(REG_DATA));
        }
        assertEquals(SR_DRDY | SR_DSC, ideController.readTaskRegister(REG_STATUS));
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
            assertEquals(SR_DRDY | SR_DRQ | SR_DSC, ideController.readTaskRegister(REG_STATUS));
            ideController.writeTaskRegister(REG_DATA, shortBuf.get(i) & 0xFFFF);
        }
        assertEquals(SR_DRDY | SR_DSC, ideController.readTaskRegister(REG_STATUS));
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
        assertEquals(0, ideController.readTaskRegister(REG_STATUS));
        // Check reset with no attached drives
        ideController.writeControlRegister(CR_SRST);
        ideController.writeControlRegister(0);
        // Attach test drives
        TestIdeDrive testIdeDrive0 = createTestIdeDrive(IF_0);
        TestIdeDrive testIdeDrive1 = createTestIdeDrive(IF_1);
        ideController.attachDrive(IF_0, testIdeDrive0);
        ideController.attachDrive(IF_1, testIdeDrive1);
        // Reset drives
        ideController.writeControlRegister(CR_SRST);
        // Check drives busy status while reset active
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        assertEquals(SR_BSY, ideController.readTaskRegister(REG_STATUS));
        ideController.writeTaskRegister(REG_DRIVE_HEAD, DHR_DRV);
        assertEquals(SR_BSY, ideController.readTaskRegister(REG_STATUS));
        // Deactivate reset
        ideController.writeControlRegister(0);
        // Check drive states
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0); // master
        assertEquals(SR_DRDY, ideController.readTaskRegister(REG_STATUS));
        assertEquals(0x01, ideController.readTaskRegister(REG_ERROR));
        assertEquals(0x00, ideController.readTaskRegister(REG_CYLINDER_LOW));
        assertEquals(0x00, ideController.readTaskRegister(REG_CYLINDER_HIGH));
        assertEquals(0x01, ideController.readTaskRegister(REG_SECTOR_COUNT));
        assertEquals(0x01, ideController.readTaskRegister(REG_SECTOR_NUMBER));
        assertEquals(0xA0, ideController.readTaskRegister(REG_DRIVE_HEAD));
        ideController.writeTaskRegister(REG_DRIVE_HEAD, DHR_DRV); // slave
        assertEquals(SR_DRDY, ideController.readTaskRegister(REG_STATUS));
        assertEquals(0x01, ideController.readTaskRegister(REG_ERROR));
        assertEquals(0x00, ideController.readTaskRegister(REG_CYLINDER_LOW));
        assertEquals(0x00, ideController.readTaskRegister(REG_CYLINDER_HIGH));
        assertEquals(0x01, ideController.readTaskRegister(REG_SECTOR_COUNT));
        assertEquals(0x01, ideController.readTaskRegister(REG_SECTOR_NUMBER));
        assertEquals(0xA0 | DHR_DRV, ideController.readTaskRegister(REG_DRIVE_HEAD));
        // Send identify command
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0); // master
        ideController.writeTaskRegister(REG_COMMAND, CMD_IDENTIFY);
        byte[] identifyData = readData();
        assertEquals(0x40, getInt16(identifyData, IDENTIFY_DEVICE_TYPE));
        assertEquals(testIdeDrive0.getNumCylinders(), getInt16(identifyData, IDENTIFY_NUM_CYLINDERS));
        assertEquals(testIdeDrive0.getNumHeads(), getInt16(identifyData, IDENTIFY_NUM_HEADS));
        assertEquals(testIdeDrive0.getNumSectors(), getInt16(identifyData, IDENTIFY_NUM_SECTORS));
        // Single sector read
        byte[] driveData = testIdeDrive0.getData();
        ideController.writeTaskRegister(REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_SECTOR_NUMBER, 1);
        for (int s = 0; s < testIdeDrive0.getTotalNumSectors(); s++) {
            ideController.writeTaskRegister(REG_SECTOR_COUNT, 1);
            ideController.writeTaskRegister(REG_COMMAND, CMD_READ);
            compareData(driveData, readData(), SECTOR_SIZE * s);
        }
        // Multiple sector read
        ideController.writeTaskRegister(REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_SECTOR_NUMBER, 2);
        ideController.writeTaskRegister(REG_SECTOR_COUNT, 0);
        ideController.writeTaskRegister(REG_COMMAND, CMD_READ);
        compareData(driveData, readData(SECTOR_SIZE * 256), SECTOR_SIZE);
        // Single sector write
        ideController.writeTaskRegister(REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_SECTOR_NUMBER, 1);
        byte[] writeData = new byte[SECTOR_SIZE];
        for (int s = 0; s < testIdeDrive0.getTotalNumSectors(); s++) {
            new Random(s).nextBytes(writeData);
            ideController.writeTaskRegister(REG_SECTOR_COUNT, 1);
            ideController.writeTaskRegister(REG_COMMAND, CMD_WRITE);
            writeData(writeData);
            compareData(driveData, writeData, SECTOR_SIZE * s);
        }
        // Multiple sector write
        ideController.writeTaskRegister(REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_SECTOR_NUMBER, 2);
        writeData = new byte[SECTOR_SIZE * 256];
        new Random(-1).nextBytes(writeData);
        ideController.writeTaskRegister(REG_SECTOR_COUNT, 0);
        ideController.writeTaskRegister(REG_COMMAND, CMD_WRITE);
        writeData(writeData);
        compareData(driveData, writeData, SECTOR_SIZE);
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
        ideController.attachDrive(IF_0, testIdeDriveHdiImage);
        // Send identify command
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_COMMAND, CMD_IDENTIFY);
        byte[] identifyData = readData();
        assertEquals(0x40, getInt16(identifyData, IDENTIFY_DEVICE_TYPE));
        assertEquals(TEST_IMAGE_NUM_CYLINDERS, getInt16(identifyData, IDENTIFY_NUM_CYLINDERS));
        assertEquals(TEST_IMAGE_NUM_HEADS, getInt16(identifyData, IDENTIFY_NUM_HEADS));
        assertEquals(TEST_IMAGE_NUM_SECTORS, getInt16(identifyData, IDENTIFY_NUM_SECTORS));
        // Read first sector
        ideController.writeTaskRegister(REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_SECTOR_NUMBER, 1);
        ideController.writeTaskRegister(REG_SECTOR_COUNT, 1);
        ideController.writeTaskRegister(REG_COMMAND, CMD_READ);
        compareData(testImageData, readData(), SECTOR_SIZE);
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
        ideController.attachDrive(IF_0, testIdeDriveRawImage);
        // Send identify command
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_COMMAND, CMD_IDENTIFY);
        byte[] identifyData = readData();
        assertEquals(0x40, getInt16(identifyData, IDENTIFY_DEVICE_TYPE));
        assertEquals(TEST_IMAGE_NUM_CYLINDERS, getInt16(identifyData, IDENTIFY_NUM_CYLINDERS));
        assertEquals(TEST_IMAGE_NUM_HEADS, getInt16(identifyData, IDENTIFY_NUM_HEADS));
        assertEquals(TEST_IMAGE_NUM_SECTORS, getInt16(identifyData, IDENTIFY_NUM_SECTORS));
        // Read first sector
        ideController.writeTaskRegister(REG_CYLINDER_LOW, 0);
        ideController.writeTaskRegister(REG_CYLINDER_HIGH, 0);
        ideController.writeTaskRegister(REG_DRIVE_HEAD, 0);
        ideController.writeTaskRegister(REG_SECTOR_NUMBER, 1);
        ideController.writeTaskRegister(REG_SECTOR_COUNT, 1);
        ideController.writeTaskRegister(REG_COMMAND, CMD_READ);
        compareData(testImageData, readData(), 0);
    }
}
