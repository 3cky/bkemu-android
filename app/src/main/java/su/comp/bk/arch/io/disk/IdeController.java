/*
 * Copyright (C) 2022 Victor Antonovich (v.antonovich@gmail.com)
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

import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DHR_DRV;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DHR_HS0;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DHR_HS1;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DHR_HS2;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DHR_HS3;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DAR_DS0;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DAR_DS1;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.DAR_WTG;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_NUM_CYLINDERS;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_NUM_HEADS;
import static su.comp.bk.arch.io.disk.IdeController.IdeInterface.IDENTIFY_NUM_SECTORS;

import android.annotation.SuppressLint;
import android.os.Bundle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import timber.log.Timber;

/**
 * Integrated Drive Electronics (IDE) controller class.
 *
 * Based on code from JPC x86 PC emulator.
 * @see <a href="https://github.com/ianopolous/JPC/blob/master/src/org/jpc/emulator/pci/peripheral/IDEChannel.java">IDEChannel.java</a>
 */
public class IdeController {
    /** First (master) interface */
    public static final int IF_0 = 0;
    /** Second (slave) interface */
    public static final int IF_1 = 1;

    /** Data register address (read/write) */
    public static final int REG_DATA = 0x00;
    /** Error register address (read) */
    public static final int REG_ERROR = 0x01;
    /** Features register address (write) */
    public static final int REG_FEATURES = 0x01;
    /** Sector count register address (read/write) */
    public static final int REG_SECTOR_COUNT = 0x02;
    /** Sector number register address (read/write) */
    public static final int REG_SECTOR_NUMBER = 0x03;
    /** Cylinder low register address (read/write) */
    public static final int REG_CYLINDER_LOW = 0x04;
    /** Cylinder high register address (read/write) */
    public static final int REG_CYLINDER_HIGH = 0x05;
    /** Drive / Head select register address (read/write) */
    public static final int REG_DRIVE_HEAD = 0x06;
    /** Command register address (write) */
    public static final int REG_COMMAND = 0x07;
    /** Status register address (read) */
    public static final int REG_STATUS = 0x07;

    /** Sector size (in bytes) */
    public static final int SECTOR_SIZE = 512;

    private static final String STATE_PREFIX = "IdeController#";
    private static final String STATE_NEXT_DRIVE_SERIAL_NUMBER = STATE_PREFIX +
            "next_drive_serial_number";
    private static final String STATE_CURRENT_INTERFACE = STATE_PREFIX + "current_interface";

    private final IdeInterface[] interfaces;
    private IdeInterface currentInterface;

    private int nextDriveSerialNumber;

    public interface IdeDrive {
        /** IDE drive max number of heads. */
        int MAX_HEADS = 16;
        /** IDE drive max number of sectors per cylinder. */
        int MAX_SECTORS = 255;
        /** IDE drive max number of cylinders. */
        int MAX_CYLINDERS = 16383;

        /**
         * Get name of this drive.
         *
         * @return name of this drive
         */
        String getName();

        /**
         * Detaches the drive.  Once <code>detach</code> has been called any further reads
         * from or writes to the device will most likely fail.
         */
        void detach();

        /**
         * Reads sectors starting at given sector into the given array.
         * @param buffer array to read data into
         * @param sectorIndex index of the first sector to read
         * @param numSectors number of sectors to read
         * @return negative value on failure
         */
        int read(byte[] buffer, long sectorIndex, int numSectors);

        /**
         * Writes sectors starting at given sector from the given array.
         * @param buffer array of data to write
         * @param sectorIndex index of the first sector to write
         * @param numSectors number of sectors to write
         * @return negative value on failure
         */
        int write(byte[] buffer, long sectorIndex, int numSectors);

        /**
         * Returns the number of cylinders of the drive.
         * @return number of cylinders
         */
        int getNumCylinders();

        /**
         * Returns the number of heads of the drive.
         * @return number of heads
         */
        int getNumHeads();

        /**
         * Returns the number of sectors per cylinder of the drive.
         * @return number of sectors per cylinder
         */
        int getNumSectors();

        /**
         * Returns the total size of this drive in sectors.
         * @return total size in sectors
         */
        long getTotalNumSectors();
    }

    abstract static class IdeDriveImage implements IdeDrive {
        /** AltPro controller drive image: Partition table checksum seed */
        public static final int ALTPRO_PT_CHECKSUM_SEED = 012701;
        /** AltPro controller drive image: Max number of logical disks */
        public static final int ALTPRO_MAX_NUM_LD = 125;
        /** AltPro controller drive image: Partition table sector index */
        public static final int ALTPRO_PT_SECTOR_INDEX = 7;
        /** AltPro controller drive image: Number of logical disks */
        public static final int ALTPRO_NUM_LD_OFFSET = 0770;
        /** AltPro controller drive image: Number of sectors offset */
        public static final int ALTPRO_NUM_SECTORS_OFFSET = 0772;
        /** AltPro controller drive image: Number of heads offset */
        public static final int ALTPRO_NUM_HEADS_OFFSET = 0774;
        /** AltPro controller drive image: Number of cylinders offset */
        public static final int ALTPRO_NUM_CYLINDERS_OFFSET = 0776;

        private final DiskImage image;

        private int numCylinders;
        private int numHeads;
        private int numSectors;

        private long totalNumSectors;

        protected IdeDriveImage(DiskImage image) {
            this.image = image;
            init();
        }

        protected DiskImage getImage() {
            return image;
        }

        protected void init() {
            long imageDataSize = image.length() - getImageHeaderSize();
            totalNumSectors = imageDataSize / SECTOR_SIZE;
            if (imageDataSize % SECTOR_SIZE > 0) {
                totalNumSectors++;
            }
            setupGeometry();
            Timber.d("Geometry: cylinders: %d, heads: %d, sectors: %d. Total LBA sectors: %d",
                    getNumCylinders(), getNumHeads(), getNumSectors(), getTotalNumSectors());
        }

        protected void setupGeometry() {
            if (!setupAltProGeometry()) {
                setupDefaultGeometry();
            }
        }

        private boolean setupAltProGeometry() {
            long pos = getImageDataSectorPosition(ALTPRO_PT_SECTOR_INDEX);
            try {
                // Get number of logical disks (LD) in partition table
                int numLogicalDisks = readInt16Inv(pos, ALTPRO_NUM_LD_OFFSET) & 0xFF;
                if (numLogicalDisks > ALTPRO_MAX_NUM_LD) {
                    // Invalid number of logical disks
                    return false;
                }

                // AltPro partition table grows up like stack. Each LD is two words long.
                // Checksum is one word long, computed as a simple sum of all partition table
                // records and geometry data and written atop of partition table
                int checksumPos = ALTPRO_NUM_LD_OFFSET - numLogicalDisks * 4 - 2;
                int checksum = readInt16Inv(pos, checksumPos);
                do {
                    checksumPos += 2; // next word
                    checksum -= readInt16Inv(pos, checksumPos);
                } while (checksumPos < ALTPRO_NUM_CYLINDERS_OFFSET);

                if ((checksum & 0xFFFF) != ALTPRO_PT_CHECKSUM_SEED) {
                    // Partition table is invalid
                    return false;
                }

                // Partition table is valid, get geometry from it
                setNumSectors(readInt16Inv(pos, ALTPRO_NUM_SECTORS_OFFSET));
                setNumHeads(readInt16Inv(pos, ALTPRO_NUM_HEADS_OFFSET) & 0xFF);
                setNumCylinders(readInt16Inv(pos, ALTPRO_NUM_CYLINDERS_OFFSET));

                return true;
            } catch (Exception e) {
                Timber.d("Can't set up AltPro geometry: %s", e.getMessage());
            }
            return false;
        }

        protected abstract void setupDefaultGeometry();

        protected void setNumCylinders(int numCylinders) {
            checkGeometryParameter(numCylinders, MAX_CYLINDERS, "cylinders");
            this.numCylinders = numCylinders;
        }

        protected void setNumHeads(int numHeads) {
            checkGeometryParameter(numHeads, MAX_HEADS, "heads");
            this.numHeads = numHeads;
        }

        protected void setNumSectors(int numSectors) {
            checkGeometryParameter(numSectors, MAX_SECTORS, "sectors");
            this.numSectors = numSectors;
        }

        private static void checkGeometryParameter(int value, int maxValue, String name) {
            if (value <= 0 || value > maxValue) {
                throw new IllegalArgumentException("Invalid geometry parameter value: " +
                        name + ": " + value);
            }
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
        public String getName() {
            return image.getName();
        }

        @Override
        public void detach() {
            try {
                image.close();
            } catch (IOException e) {
                Timber.w(e, "Error while detaching image %s", image.getLocation());
            }
        }

        protected long getImageDataSectorPosition(long sectorIndex) {
            return getImageHeaderSize() + sectorIndex * SECTOR_SIZE;
        }

        @Override
        public int read(byte[] buffer, long sectorIndex, int numSectors) {
            long position = getImageDataSectorPosition(sectorIndex);
            try {
                image.readBytes(buffer, position, numSectors * SECTOR_SIZE);
            } catch (IOException e) {
                Timber.e(e, "Can't read image at position %d", position);
                return -1;
            }
            return numSectors;
        }

        @Override
        public int write(byte[] buffer, long sectorIndex, int numSectors) {
            long position = getImageDataSectorPosition(sectorIndex);
            try {
                image.writeBytes(buffer, position, numSectors * SECTOR_SIZE);
            } catch (IOException e) {
                Timber.e(e, "Can't write image at position %d", position);
                return -1;
            }
            return numSectors;
        }

        @Override
        public long getTotalNumSectors() {
            return totalNumSectors;
        }

        protected int readInt16(long position) {
            DiskImage image = getImage();
            try {
                return (image.readByte(position) & 0xFF)
                        | ((image.readByte(position + 1) & 0xFF) << 8);
            } catch (IOException e) {
                throw new IllegalStateException("Can't read image at position " + position, e);
            }
        }

        protected int readInt16Inv(long position) {
            return ~readInt16(position) & 0xFFFF;
        }

        protected int readInt16Inv(long position, int offset) {
            return readInt16Inv(position + offset);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IdeDriveImage)) return false;

            IdeDriveImage that = (IdeDriveImage) o;

            return image.equals(that.image);
        }

        @Override
        public int hashCode() {
            return image.hashCode();
        }

        /**
         * Get drive image header size.
         * @return Drive image header size in bytes
         */
        protected abstract long getImageHeaderSize();
    }

    /**
     * IDE drive image stored as raw (headerless) disk data.
     */
    public static class IdeDriveRawImage extends IdeDriveImage {
        /** IDE drive default number of heads. */
        public static final int DEFAULT_HEADS = MAX_HEADS;
        /** IDE drive default number of sectors per cylinder. */
        public static final int DEFAULT_SECTORS = 63;

        public IdeDriveRawImage(DiskImage image) {
            super(image);
        }

        @Override
        protected void setupDefaultGeometry() {
            setNumSectors(DEFAULT_SECTORS);
            setNumHeads(DEFAULT_HEADS);
            setNumCylinders(Math.min((int) (getTotalNumSectors() / (getNumSectors() * getNumHeads())),
                    MAX_CYLINDERS));
        }

        @Override
        protected long getImageHeaderSize() {
            return 0;
        }
    }

    /**
     * IDE drive image stored in .hdi image file.
     * File consists from 512 bytes long header in IDENTIFY command output format
     * and raw disk data.
     */
    public static class IdeDriveHdiImage extends IdeDriveImage {
        public IdeDriveHdiImage(DiskImage image) {
            super(image);
        }

        @Override
        protected void setupDefaultGeometry() {
            setNumCylinders(readInt16(IDENTIFY_NUM_CYLINDERS));
            setNumHeads(readInt16(IDENTIFY_NUM_HEADS));
            setNumSectors(readInt16(IDENTIFY_NUM_SECTORS));
        }

        @Override
        protected long getImageHeaderSize() {
            return SECTOR_SIZE;
        }
    }

    class IdeInterface {
        /** Device control register: Software reset bit */
        public static final int CR_SRST = 0x04;

        /** Status register: Error */
        public static final int SR_ERR = 0x01;
        /** Status register: Data request ready */
        public static final int SR_DRQ = 0x08;
        /** Status register: Drive seek complete */
        public static final int SR_DSC = 0x10;
        /** Status register: Drive ready */
        public static final int SR_DRDY = 0x40;
        /** Status register: Drive busy */
        public static final int SR_BSY = 0x80;

        /** Error register: No error */
        public static final int ER_NONE = 0x00;
        /** Error register: Command aborted */
        public static final int ER_ABRT = 0x04;

        /** Drive/head register: Head select bit 0 */
        public static final int DHR_HS0 = 0x01;
        /** Drive/head register: Head select bit 1 */
        public static final int DHR_HS1 = 0x02;
        /** Drive/head register: Head select bit 1 */
        public static final int DHR_HS2 = 0x04;
        /** Drive/head register: Head select bit 1 */
        public static final int DHR_HS3 = 0x08;
        /** Drive/head register: Drive select bit */
        public static final int DHR_DRV = 0x10;
        /** Drive/head register: LBA mode bit */
        public static final int DHR_L = 0x40;

        /** Drive address register: Drive 0 selected bit (active when 0) */
        public static final int DAR_DS0 = 0x01;
        /** Drive address register: Drive 1 selected bit (active when 0) */
        public static final int DAR_DS1 = 0x02;
        /** Drive address register: Head select bit 0 */
        public static final int DAR_HS0 = 0x04;
        /** Drive address register: Head select bit 1 */
        public static final int DAR_HS1 = 0x08;
        /** Drive address register: Head select bit 2 */
        public static final int DAR_HS2 = 0x10;
        /** Drive address register: Head select bit 3 */
        public static final int DAR_HS3 = 0x20;
        /** Drive address register: Write gate (active when 0) */
        public static final int DAR_WTG = 0x30;

        /** Command: Identify device */
        public static final int CMD_IDENTIFY = 0xEC;

        /** Identify: Device type */
        public static final int IDENTIFY_DEVICE_TYPE = 0;
        /** Identify: Number of tracks */
        public static final int IDENTIFY_NUM_CYLINDERS = 2;
        /** Identify: Number of heads */
        public static final int IDENTIFY_NUM_HEADS = 6;
        /** Identify: Number of sectors per track */
        public static final int IDENTIFY_NUM_SECTORS = 12;
        /** Identify: Device serial number */
        public static final int IDENTIFY_SERIAL = 20;
        /** Identify: Device model */
        public static final int IDENTIFY_MODEL = 54;
        /** Identify: Device capabilities */
        public static final int IDENTIFY_CAPABILITIES = 98;
        /** Identify: Fields validity flag */
        public static final int IDENTIFY_FIELD_VALID = 106;
        /** Identify: Number of LBA sectors */
        public static final int IDENTIFY_MAX_LBA = 120;

        /** Command: Read sector(s) (w/retry) */
        public static final int CMD_READ = 0x20;
        /** Command: Read sector(s) (w/o retry) */
        public static final int CMD_READ_ONCE = 0x21;

        /** Command: Write sector(s) (w/retry) */
        public static final int CMD_WRITE = 0x30;
        /** Command: Write sector(s) (w/o retry) */
        public static final int CMD_WRITE_ONCE = 0x31;

        /** Command: Set multiple mode */
        public static final int CMD_SET_MULTIPLE = 0xC6;
        /** Command: Read multiple */
        public static final int CMD_READ_MULTIPLE = 0xC4;
        /** Command: Write multiple */
        public static final int CMD_WRITE_MULTIPLE = 0xC5;
        /** Set to 1 to disable multiple read/write operations support */
        public static final int MAX_SECTORS_MULTIPLE = 16;

        /** Command: Initialize drive parameters */
        public static final int CMD_INIT_DRIVE_PARAMS = 0x91;
        /** Command: Recalibrate */
        public static final int CMD_RECALIBRATE = 0x10;
        /** Command: Check power mode */
        public static final int CMD_CHECK_POWER_MODE = 0xE5;
        /** Command: Read verify sector(s) (w/retry) */
        public static final int CMD_VERIFY = 0x40;
        /** Command: Read verify sector(s) (w/o retry) */
        public static final int CMD_VERIFY_ONCE = 0x41;
        /** Command: Flush cache */
        public static final int CMD_FLUSH_CACHE = 0xE7;
        /** Command: Execute drive diagnostic */
        public static final int CMD_EXEC_DIAGNOSTIC = 0x90;
        /** Command: Standby immediate */
        public static final int CMD_STANDBY_IMMEDIATE = 0xE0;
        /** Command: Idle immediate */
        public static final int CMD_IDLE_IMMEDIATE = 0xE1;

        private static final int ETF_TRANSFER_STOP = 0;
        private static final int ETF_SECTOR_WRITE = 1;
        private static final int ETF_SECTOR_READ = 2;
        private static final int ETF_FORCE_TRANSFER_STOP = 3;

        private static final String STATE_PREFIX = "IdeInterface";
        private static final String STATE_NUM_CYLINDERS = "num_cylinders";
        private static final String STATE_NUM_HEADS = "num_heads";
        private static final String STATE_NUM_SECTORS = "num_sectors";
        private static final String STATE_LAST_CONTROL_DATA = "last_control_data";
        private static final String STATE_FEATURES = "features";
        private static final String STATE_SECTOR_COUNT = "sector_count";
        private static final String STATE_SECTOR_NUMBER = "sector_number";
        private static final String STATE_CYLINDER_LOW = "cylinder_low";
        private static final String STATE_CYLINDER_HIGH = "cylinder_high";
        private static final String STATE_DRIVE_AND_HEAD = "drive_and_head";
        private static final String STATE_STATUS = "status";
        private static final String STATE_ERROR = "error";
        private static final String STATE_END_TRANSFER_FUNCTION = "end_transfer_function";
        private static final String STATE_REQUIRED_NUMBER_OF_SECTORS = "required_number_of_sectors";
        private static final String STATE_MULTIPLE_SECTOR_COUNT = "multiple_sector_count";
        private static final String STATE_DRIVE_SERIAL_NUMBER = "drive_serial_number";
        private static final String STATE_DATA_BUFFER = "data_buffer";
        private static final String STATE_DATA_BUFFER_OFFSET = "data_buffer_offset";
        private static final String STATE_DATA_BUFFER_END = "data_buffer_end";

        // Current drive geometry
        private int numCylinders, numHeads, numSectors;

        // Control register data
        private int lastControlData;

        // Task register states
        private int features;
        private int sectorCount, sectorNumber;
        private int cylinderLow, cylinderHigh;
        private int driveAndHead;
        private int status, error;

        private int endTransferFunction;
        private int requiredNumberOfSectors;
        private int multipleSectorCount;

        private int driveSerialNumber;

        private final byte[] dataBuffer;
        private int dataBufferOffset;
        private int dataBufferEnd;

        private IdeDrive drive;

        private long lastActivityTimestamp;

        IdeInterface() {
            dataBuffer = new byte[MAX_SECTORS_MULTIPLE * SECTOR_SIZE + 4];
        }

        void attachDrive(IdeDrive drive) {
            detachDrive();
            this.drive = drive;
            driveSerialNumber = ++nextDriveSerialNumber;
            if (drive != null) {
                reset();
            }
        }

        void detachDrive() {
            if (isDriveAttached()) {
                drive.detach();
                drive = null;
            }
        }

        IdeDrive getAttachedDrive() {
            return drive;
        }

        boolean isDriveAttached() {
            return (drive != null);
        }

        private String getStateKey(String var, int id) {
            return STATE_PREFIX + id + "#" + var;
        }

        void saveState(Bundle outState, int id) {
            outState.putInt(getStateKey(STATE_NUM_CYLINDERS, id), numCylinders);
            outState.putInt(getStateKey(STATE_NUM_HEADS, id), numHeads);
            outState.putInt(getStateKey(STATE_NUM_SECTORS, id), numSectors);
            outState.putInt(getStateKey(STATE_LAST_CONTROL_DATA, id), lastControlData);
            outState.putInt(getStateKey(STATE_FEATURES, id), features);
            outState.putInt(getStateKey(STATE_SECTOR_COUNT, id), sectorCount);
            outState.putInt(getStateKey(STATE_SECTOR_NUMBER, id), sectorNumber);
            outState.putInt(getStateKey(STATE_CYLINDER_LOW, id), cylinderLow);
            outState.putInt(getStateKey(STATE_CYLINDER_HIGH, id), cylinderHigh);
            outState.putInt(getStateKey(STATE_DRIVE_AND_HEAD, id), driveAndHead);
            outState.putInt(getStateKey(STATE_STATUS, id), status);
            outState.putInt(getStateKey(STATE_ERROR, id), error);
            outState.putInt(getStateKey(STATE_END_TRANSFER_FUNCTION, id), endTransferFunction);
            outState.putInt(getStateKey(STATE_REQUIRED_NUMBER_OF_SECTORS, id), requiredNumberOfSectors);
            outState.putInt(getStateKey(STATE_MULTIPLE_SECTOR_COUNT, id), multipleSectorCount);
            outState.putInt(getStateKey(STATE_DRIVE_SERIAL_NUMBER, id), driveSerialNumber);

            outState.putByteArray(getStateKey(STATE_DATA_BUFFER, id), dataBuffer);
            outState.putInt(getStateKey(STATE_DATA_BUFFER_OFFSET, id), dataBufferOffset);
            outState.putInt(getStateKey(STATE_DATA_BUFFER_END, id), dataBufferEnd);
        }

        void restoreState(Bundle inState, int id) {
            numCylinders = inState.getInt(getStateKey(STATE_NUM_CYLINDERS, id));
            numHeads = inState.getInt(getStateKey(STATE_NUM_HEADS, id));
            numSectors = inState.getInt(getStateKey(STATE_NUM_SECTORS, id));
            lastControlData = inState.getInt(getStateKey(STATE_LAST_CONTROL_DATA, id));
            features = inState.getInt(getStateKey(STATE_FEATURES, id));
            sectorCount = inState.getInt(getStateKey(STATE_SECTOR_COUNT, id));
            sectorNumber = inState.getInt(getStateKey(STATE_SECTOR_NUMBER, id));
            cylinderLow = inState.getInt(getStateKey(STATE_CYLINDER_LOW, id));
            cylinderHigh = inState.getInt(getStateKey(STATE_CYLINDER_HIGH, id));
            driveAndHead = inState.getInt(getStateKey(STATE_DRIVE_AND_HEAD, id));
            status = inState.getInt(getStateKey(STATE_STATUS, id));
            error = inState.getInt(getStateKey(STATE_ERROR, id));
            endTransferFunction = inState.getInt(getStateKey(STATE_END_TRANSFER_FUNCTION, id));
            requiredNumberOfSectors = inState.getInt(getStateKey(STATE_REQUIRED_NUMBER_OF_SECTORS, id));
            multipleSectorCount = inState.getInt(getStateKey(STATE_MULTIPLE_SECTOR_COUNT, id));
            driveSerialNumber = inState.getInt(getStateKey(STATE_DRIVE_SERIAL_NUMBER, id));

            byte[] stateDataBuffer = inState.getByteArray(getStateKey(STATE_DATA_BUFFER, id));
            if (stateDataBuffer != null) {
                System.arraycopy(stateDataBuffer, 0, dataBuffer, 0, dataBuffer.length);
                dataBufferOffset = inState.getInt(getStateKey(STATE_DATA_BUFFER_OFFSET, id));
                dataBufferEnd = inState.getInt(getStateKey(STATE_DATA_BUFFER_END, id));
            }
        }

        void reset() {
            Timber.d("reset");
            // Set default geometry
            numCylinders = drive.getNumCylinders();
            numHeads = drive.getNumHeads();
            numSectors = drive.getNumSectors();
            // Disable multiple read/write commands
            multipleSectorCount = 0;
            driveAndHead = 0xA0;
            status = SR_DRDY;
            setDefaults();
            endTransfer(ETF_FORCE_TRANSFER_STOP);
        }

        private void setDefaults() {
            // Clear head
            driveAndHead &= 0xF0;
            // Set register defaults
            sectorCount = 0x01;
            sectorNumber = 0x01;
            cylinderLow = 0x00;
            cylinderHigh = 0x00;
            error = 0x01;
        }

        private long getCurrentSectorNumber() {
            if ((driveAndHead & DHR_L) != 0) {
                // LBA
                return ((driveAndHead & 0x0FL) << 24) |
                        ((cylinderHigh & 0xFFL) << 16) |
                        ((cylinderLow & 0xFFL) << 8) |
                        (sectorNumber & 0xFFL);
            } else {
                // CHS
                return ((((cylinderHigh & 0xFFL) << 8) | (cylinderLow & 0xFFL)) * numHeads * numSectors)
                        + ((driveAndHead & 0x0FL) * numSectors) + ((sectorNumber & 0xFFL) - 1);
            }
        }

        private void setCurrentSectorNumber(long sectorNumber) {
            if ((driveAndHead & DHR_L) != 0) {
                // LBA
                driveAndHead = (int) ((driveAndHead & 0xF0) | (sectorNumber >>> 24));
                cylinderHigh = (int) (sectorNumber >>> 16);
                cylinderLow = (int) (sectorNumber >>> 8);
                this.sectorNumber = (int) (sectorNumber & 0xFF);
            } else {
                // CHS
                int cyl = (int) (sectorNumber / (numHeads * numSectors));
                int r = (int) (sectorNumber % (numHeads * numSectors));
                cylinderHigh = (int) (cyl >>> 8);
                cylinderLow = (int) (cyl);
                driveAndHead = (int) ((driveAndHead & 0xF0) | ((r / numSectors) & 0x0F));
                this.sectorNumber = (int) (((r % numSectors) + 1) & 0xFF);
            }
        }

        private void startTransfer(int size, int endTransferFunction) {
            this.endTransferFunction = endTransferFunction;
            dataBufferEnd = size;
            dataBufferOffset = 0;
            status |= SR_DRQ;
        }

        private void stopTransfer() {
            endTransferFunction = ETF_TRANSFER_STOP;
            dataBufferEnd = 0;
            dataBufferOffset = 0;
            status &= ~SR_DRQ;
        }

        private void forceStopTransfer() {
            endTransferFunction = ETF_FORCE_TRANSFER_STOP;
            dataBufferEnd = 0;
            dataBufferOffset = 0;
        }

        private void endTransfer() {
            endTransfer(endTransferFunction);
        }

        private void endTransfer(int mode) {
            switch (mode) {
                case ETF_TRANSFER_STOP:
                    stopTransfer();
                    break;
                case ETF_SECTOR_WRITE:
                    writeSector();
                    break;
                case ETF_SECTOR_READ:
                    readSector();
                    break;
                case ETF_FORCE_TRANSFER_STOP:
                    forceStopTransfer();
                    break;
            }
        }

        private void checkSectorCount() {
            if (sectorCount == 0) {
                sectorCount = 256;
            }
        }

        void handleCommand(int command) {
            switch (command) {
                case IdeInterface.CMD_IDENTIFY:
                    identify();
                    status = IdeInterface.SR_DRDY | IdeInterface.SR_DSC;
                    startTransfer(SECTOR_SIZE, IdeInterface.ETF_TRANSFER_STOP);
                    break;

                case IdeInterface.CMD_READ:
                case IdeInterface.CMD_READ_ONCE:
                    checkSectorCount();
                    requiredNumberOfSectors = 1;
                    readSector();
                    break;
                case IdeInterface.CMD_WRITE:
                case IdeInterface.CMD_WRITE_ONCE:
                    checkSectorCount();
                    error = IdeInterface.ER_NONE;
                    status = IdeInterface.SR_DSC | IdeInterface.SR_DRDY;
                    requiredNumberOfSectors = 1;
                    startTransfer(SECTOR_SIZE, IdeInterface.ETF_SECTOR_WRITE);
                    break;

                case IdeInterface.CMD_SET_MULTIPLE:
                    if (sectorCount > IdeInterface.MAX_SECTORS_MULTIPLE || sectorCount == 0
                            || (sectorCount & (sectorCount - 1)) != 0) {
                        abortCommand();
                    } else {
                        multipleSectorCount = sectorCount;
                        status = IdeInterface.SR_DRDY;
                    }
                    break;
                case IdeInterface.CMD_READ_MULTIPLE:
                    if (multipleSectorCount == 0) {
                        abortCommand();
                        return;
                    }
                    checkSectorCount();
                    requiredNumberOfSectors = multipleSectorCount;
                    readSector();
                    break;
                case IdeInterface.CMD_WRITE_MULTIPLE:
                    if (multipleSectorCount == 0) {
                        abortCommand();
                        return;
                    }
                    checkSectorCount();
                    error = IdeInterface.ER_NONE;
                    status = IdeInterface.SR_DSC | IdeInterface.SR_DRDY;
                    requiredNumberOfSectors = multipleSectorCount;
                    int n = sectorCount;
                    if (n > requiredNumberOfSectors) {
                        n = requiredNumberOfSectors;
                    }
                    startTransfer(SECTOR_SIZE * n, IdeInterface.ETF_SECTOR_WRITE);
                    break;

                case IdeInterface.CMD_INIT_DRIVE_PARAMS: // TODO
                case IdeInterface.CMD_RECALIBRATE:
                    error = IdeInterface.ER_NONE;
                    status = IdeInterface.SR_DRDY | IdeInterface.SR_DSC;
                    break;
                case IdeInterface.CMD_CHECK_POWER_MODE:
                    sectorCount = 0xFF; // Device active or idle
                    status = IdeInterface.SR_DRDY;
                    break;
                case IdeInterface.CMD_VERIFY:
                case IdeInterface.CMD_VERIFY_ONCE:
                case IdeInterface.CMD_FLUSH_CACHE:
                case IdeInterface.CMD_STANDBY_IMMEDIATE:
                case IdeInterface.CMD_IDLE_IMMEDIATE:
                    status = IdeInterface.SR_DRDY;
                    break;
                case IdeInterface.CMD_EXEC_DIAGNOSTIC:
                    setDefaults();
                    status = 0x00;
                    error = 0x01; // Diagnostic code: No error detected
                    break;
                default:
                    Timber.w("Unsupported command: 0x%02X", command & 0xFF);
                    abortCommand();
            }
        }

        private void abortCommand() {
            status = SR_DRDY | SR_ERR;
            error = ER_ABRT;
        }

        @SuppressLint("DefaultLocale")
        private void identify() {
            Arrays.fill(dataBuffer, (byte) 0);
            // General configuration: fixed drive
            putInt16(dataBuffer, IDENTIFY_DEVICE_TYPE, 0x0040);
            // Number of cylinders
            putInt16(dataBuffer, IDENTIFY_NUM_CYLINDERS, numCylinders);
            // Number of heads
            putInt16(dataBuffer, IDENTIFY_NUM_HEADS, numHeads);
            // Number of unformatted bytes per track
            putInt16(dataBuffer, 8, SECTOR_SIZE * numSectors);
            // Number of unformatted bytes per sector
            putInt16(dataBuffer, 10, SECTOR_SIZE);
            // Number of sectors per track
            putInt16(dataBuffer, IDENTIFY_NUM_SECTORS, numSectors);
            // Serial number (20 ASCII characters)
            putString(dataBuffer, IDENTIFY_SERIAL, 20, String.format("S/N:%03d", driveSerialNumber));
            // Buffer type: a dual ported multi-sector buffer
            putInt16(dataBuffer, 40, 3);
            // Buffer size in 512 byte increments
            putInt16(dataBuffer, 42, SECTOR_SIZE);
            // Number of ECC bytes avail on read/write long cmds
            putInt16(dataBuffer, 44, 4);
            // Firmware revision (8 ASCII characters)
            putString(dataBuffer, 46, 8, "v0.01");
            // Model number (40 ASCII characters)
            putString(dataBuffer, IDENTIFY_MODEL, 40, "BKEMU HDD");
            // 15-8 Vendor unique
            // 7-0  xxh = Maximum number of sectors that can be transferred per interrupt
            // on read and write multiple commands
            putInt16(dataBuffer, 94, 0x8000 | MAX_SECTORS_MULTIPLE);
            // 0001h = can perform double word I/O
            putInt16(dataBuffer, 96, 1);
            // Capabilities: LBA supported
            putInt16(dataBuffer, IDENTIFY_CAPABILITIES, (1 << 9));
            // PIO data transfer cycle timing mode
            putInt16(dataBuffer, 102, 0x200);
            // Words 54-58, 64-70, 88 are valid
            putInt16(dataBuffer, IDENTIFY_FIELD_VALID, 1);
            // Number of current cylinders
            putInt16(dataBuffer, 108, numCylinders);
            // Number of current heads
            putInt16(dataBuffer, 110, numHeads);
            // Number of current sectors per track
            putInt16(dataBuffer, 112, numSectors);
            // Current capacity in sectors
            int capacity = numCylinders * numHeads * numSectors;
            putInt16(dataBuffer, 114, capacity);
            putInt16(dataBuffer, 116, capacity >>> 16);
            // 15-9 Reserved
            // 8 1 = Multiple sector setting is valid
            // 7-0 xxh = Current setting for number of sectors that can be transferred on R/W multiple commands
            if (multipleSectorCount != 0) {
                putInt16(dataBuffer, 118, 0x100 | multipleSectorCount);
            }
            // Total number of user addressable sectors (LBA mode only)
            putInt16(dataBuffer, IDENTIFY_MAX_LBA, (short) drive.getTotalNumSectors());
            putInt16(dataBuffer, IDENTIFY_MAX_LBA + 2, (short) (drive.getTotalNumSectors() >>> 16));
        }

        private void writeSector() {
            status = SR_DRDY | SR_DSC;
            long sectorNumber = getCurrentSectorNumber();
            int n = sectorCount;
            if (n > requiredNumberOfSectors) {
                n = requiredNumberOfSectors;
            }
            drive.write(dataBuffer, sectorNumber, n);
            sectorCount -= n;
            if (sectorCount == 0) {
                stopTransfer();
            } else {
                int n1 = sectorCount;
                if (n1 > requiredNumberOfSectors) {
                    n1 = requiredNumberOfSectors;
                }
                startTransfer(SECTOR_SIZE * n1, ETF_SECTOR_WRITE);
            }
            setCurrentSectorNumber(sectorNumber + n);
            updateLastActivityTimestamp();
        }

        private void readSector() {
            status = SR_DRDY | SR_DSC;
            error = ER_NONE;
            long sectorNumber = getCurrentSectorNumber();
            int n = sectorCount;
            if (n == 0) {
                // No more sectors to read from disk
                stopTransfer();
            } else {
                n = Math.min(n, requiredNumberOfSectors);
                drive.read(dataBuffer, sectorNumber, n);
                startTransfer( SECTOR_SIZE * n, ETF_SECTOR_READ);
                setCurrentSectorNumber(sectorNumber + n);
                sectorCount -= n;
                updateLastActivityTimestamp();
            }
        }

        private synchronized void updateLastActivityTimestamp() {
            lastActivityTimestamp = System.nanoTime();
        }

        public synchronized long getLastActivityTimestamp() {
            return lastActivityTimestamp;
        }

        int readNextDataWord() {
            int data = dataBuffer[dataBufferOffset++] & 0xFF;
            data |= (dataBuffer[dataBufferOffset++] << 8) & 0xFF00;

            if (dataBufferOffset >= dataBufferEnd) {
                endTransfer();
            }

            return data;
        }

        void writeNextDataWord(int data) {
            dataBuffer[dataBufferOffset++] = (byte) (data);
            dataBuffer[dataBufferOffset++] = (byte) (data >> 8);

            if (dataBufferOffset >= dataBufferEnd) {
                endTransfer();
            }
        }
    }

    public IdeController() {
        this.nextDriveSerialNumber = 1;

        interfaces = new IdeInterface[2];
        interfaces[IF_0] = new IdeInterface(); // master
        interfaces[IF_1] = new IdeInterface(); // slave

        setCurrentInterface(IF_0);
    }

    public synchronized void saveState(Bundle outState) {
        outState.putInt(STATE_NEXT_DRIVE_SERIAL_NUMBER, nextDriveSerialNumber);
        for (int i = IF_0; i <= IF_1; i++) {
            IdeInterface ideInterface = interfaces[i];
            if (currentInterface == ideInterface) {
                outState.putInt(STATE_CURRENT_INTERFACE, i);
            }
            ideInterface.saveState(outState, i);
        }
    }

    public synchronized void restoreState(Bundle inState) {
        nextDriveSerialNumber = inState.getInt(STATE_NEXT_DRIVE_SERIAL_NUMBER);
        for (int i = IF_0; i <= IF_1; i++) {
            interfaces[i].restoreState(inState, i);
        }
        int currentInterfaceIndex = inState.getInt(STATE_CURRENT_INTERFACE);
        currentInterface = interfaces[currentInterfaceIndex];
    }

    private static void putInt16(byte[] dest, int offset, int data) {
        dest[offset] = (byte) data;
        dest[offset + 1] = (byte) (data >>> 8);
    }

    private static void putString(byte[] dest, int offset, int length, String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.US_ASCII);
        int i = 0;
        for (; i < Math.min(textBytes.length, length); i++) {
            dest[(offset + i) ^ 1] = textBytes[i];
        }
        for (; i < length; i++) {
            dest[(offset + i) ^ 1] = ' ';
        }
    }

    private IdeInterface getInterface(int interfaceId) {
        return interfaces[interfaceId];
    }

    private void setCurrentInterface(int interfaceId) {
        currentInterface = getInterface(interfaceId);
        getInterface(IF_0).driveAndHead &= ~DHR_DRV;
        getInterface(IF_1).driveAndHead |= DHR_DRV;
    }

    protected synchronized void reset() {
        Timber.d("reset");
        for (int i = IF_0; i <= IF_1; i++) {
            if (interfaces[i].isDriveAttached()) {
                interfaces[i].reset();
            }
        }
    }

    protected synchronized void writeControlRegister(int data) {
        // Common for both drives
        if (((interfaces[IF_0].lastControlData & IdeInterface.CR_SRST) == 0) &&
                ((data & IdeInterface.CR_SRST) != 0)) {
            // Reset low to high
            for (int i = IF_0; i <= IF_1; i++) {
                if (interfaces[i].isDriveAttached()) {
                    interfaces[i].status = IdeInterface.SR_BSY;
                }
            }
        } else if (((interfaces[IF_0].lastControlData & IdeInterface.CR_SRST) != 0) &&
                ((data & IdeInterface.CR_SRST) == 0)) {
            // Reset high to low
            for (int i = IF_0; i <= IF_1; i++) {
                if (interfaces[i].isDriveAttached()) {
                    interfaces[i].reset();
                }
            }
        }
        interfaces[IF_0].lastControlData = data & 0xFF;
        interfaces[IF_1].lastControlData = data & 0xFF;
    }

    protected synchronized int readAltStatusRegister() {
        return currentInterface.isDriveAttached() ? currentInterface.status : 0;
    }

    protected synchronized int readDriveAddressRegister() {
        if (!currentInterface.isDriveAttached()) {
            return 0;
        }

        // Write gate bit inactive (inverted)
        int driveAddress = DAR_WTG;

        // Drive select bits (inverted)
        if ((currentInterface.driveAndHead & DHR_DRV) == 0) {
            driveAddress |= DAR_DS1;
        } else {
            driveAddress |= DAR_DS0;
        }

        // Drive head select bits
        driveAddress |= ((currentInterface.driveAndHead & (DHR_HS3 | DHR_HS2 | DHR_HS1 | DHR_HS0)) << 2);

        return driveAddress;
    }

    protected synchronized void writeTaskRegister(int address, int data) {
        address &= 0x07;
        int byteData = data & 0xFF;
        switch (address) {
            case REG_DATA: // Data register
                currentInterface.writeNextDataWord(data);
                break;
            case REG_FEATURES: // Features register
                interfaces[IF_0].features = byteData;
                interfaces[IF_1].features = byteData;
                break;
            case REG_SECTOR_COUNT: // Sector count register
                interfaces[IF_0].sectorCount = byteData;
                interfaces[IF_1].sectorCount = byteData;
                break;
            case REG_SECTOR_NUMBER: // Sector number register
                interfaces[IF_0].sectorNumber = byteData;
                interfaces[IF_1].sectorNumber = byteData;
                break;
            case REG_CYLINDER_LOW: // Cylinder low register
                interfaces[IF_0].cylinderLow = byteData;
                interfaces[IF_1].cylinderLow = byteData;
                break;
            case REG_CYLINDER_HIGH: // Cylinder high register
                interfaces[IF_0].cylinderHigh = byteData;
                interfaces[IF_1].cylinderHigh = byteData;
                break;
            case REG_DRIVE_HEAD: // Drive / head select register
                interfaces[IF_0].driveAndHead = byteData | 0xA0;
                interfaces[IF_1].driveAndHead = byteData | 0xA0;
                // Set current interface
                setCurrentInterface((data & DHR_DRV) == 0 ? IF_0 : IF_1);
                break;
            default:
            case REG_COMMAND: // Command register
                // Ignore commands to non-attached drive
                if (currentInterface.isDriveAttached()) {
                    currentInterface.handleCommand(byteData);
                }
        }
    }

    protected synchronized int readTaskRegister(int address) {
        if (!currentInterface.isDriveAttached()) {
            return 0x00;
        }
        address &= 0x07;
        switch (address) {
            case REG_DATA:
                return currentInterface.readNextDataWord();
            case REG_ERROR:
                return currentInterface.error & 0xFF;
            case REG_SECTOR_COUNT:
                return currentInterface.sectorCount & 0xFF;
            case REG_SECTOR_NUMBER:
                return currentInterface.sectorNumber & 0xFF;
            case REG_CYLINDER_LOW:
                return currentInterface.cylinderLow & 0xFF;
            case REG_CYLINDER_HIGH:
                return currentInterface.cylinderHigh & 0xFF;
            case REG_DRIVE_HEAD:
                return currentInterface.driveAndHead & 0xFF;
            default:
            case REG_STATUS:
                return currentInterface.status & 0xFF;
        }
    }

    public synchronized void attachDrive(int interfaceId, IdeDrive drive) {
        getInterface(interfaceId).attachDrive(drive);
    }

    public synchronized void detachDrive(int interfaceId) {
        getInterface(interfaceId).detachDrive();
    }

    public synchronized void detachDrives() {
        detachDrive(IF_0);
        detachDrive(IF_1);
    }

    public synchronized IdeDrive getAttachedDrive(int interfaceId) {
        return getInterface(interfaceId).getAttachedDrive();
    }

    public synchronized boolean isDriveAttached(int interfaceId) {
        return getAttachedDrive(interfaceId) != null;
    }

    public synchronized long getLastDriveActivityTimestamp(int interfaceId) {
        return getInterface(interfaceId).getLastActivityTimestamp();
    }
}
