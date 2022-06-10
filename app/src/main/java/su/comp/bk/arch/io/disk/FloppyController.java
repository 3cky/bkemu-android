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

import static su.comp.bk.arch.Computer.NANOSECS_IN_MSEC;

import android.util.SparseBooleanArray;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.io.Device;
import su.comp.bk.state.State;
import su.comp.bk.util.Crc16Utils;
import timber.log.Timber;

/**
 * Floppy drive controller (К1801ВП1-128).
 */
public class FloppyController implements Device {

    protected boolean isDebugEnabled = false;

    /** Control register address */
    public final static int CONTROL_REGISTER_ADDRESS = 0177130;

    /** Control register (read): Track 0 */
    public final static int TR0 = 1;
    /** Control register (read): Drive ready */
    public final static int RDY = 2;
    /** Control register (read): Write protect */
    public final static int WRP = 4;
    /** Control register (read): Data ready */
    public final static int TR = 0200;
    /** Control register (read): CRC correct */
    public final static int CRC = 040000;
    /** Control register (read): Index hole */
    public final static int IND = 0100000;

    /** Control register (write): Select drive A */
    public final static int DS0 = 1;
    /** Control register (write): Select drive B */
    public final static int DS1 = 2;
    /** Control register (write): Select drive C */
    public final static int DS2 = 4;
    /** Control register (write): Select drive D */
    public final static int DS3 = 010;
    /** Control register (write): Start drive motor */
    public final static int MSW = 020;
    /** Control register (write): Select upper disk side */
    public final static int HS = 040;
    /** Control register (write): Set head moving direction */
    public final static int DIR = 0100;
    /** Control register (write): Do head moving step */
    public final static int ST = 0200;
    /** Control register (write): Start I/O sequence */
    public final static int GOR = 0400;
    /** Control register (write): Write marker */
    public final static int WM = 01000;

    /** Data register address */
    public final static int DATA_REGISTER_ADDRESS = 0177132;

    /** Floppy disk rotations per second */
    public final static int DISK_ROTATIONS_PER_SECOND = 5;

    /** Words per raw track */
    public final static int WORDS_PER_TRACK = 3125;

    /** Time to read/write per raw track (in nanoseconds) */
    public final static long NANOSECS_PER_TRACK = NANOSECS_IN_MSEC * 1000L
            / DISK_ROTATIONS_PER_SECOND;

    /** Index hole length (in nanoseconds) */
    public final static long NANOSECS_PER_INDEX_HOLE = NANOSECS_IN_MSEC;

    /** Maximum tracks per floppy disk */
    public final static int MAX_TRACKS_PER_DISK = 82;
    /** Sectors per track */
    public final static int SECTORS_PER_TRACK = 10;
    /** Bytes per sector */
    public final static int BYTES_PER_SECTOR = 512;
    /** Words per sector */
    public final static int WORDS_PER_SECTOR = BYTES_PER_SECTOR / 2;

    /** Max bytes per two-sided disk */
    public final static int MAX_BYTES_PER_DISK = MAX_TRACKS_PER_DISK * SECTORS_PER_TRACK
            * BYTES_PER_SECTOR * 2;

    private final static int[] ADDRESSES = { CONTROL_REGISTER_ADDRESS, DATA_REGISTER_ADDRESS };

    private static final String STATE_PREFIX = "FloppyController";
    // State save/restore: Synchronous read flag state
    public static final String STATE_SYNCHRONOUS_READ = STATE_PREFIX + "#sync_read";
    // State save/restore: Marker found flag state
    public static final String STATE_MARKER_FOUND = STATE_PREFIX + "#marker_found";
    // State save/restore: Data ready flag state
    public static final String STATE_DATA_READY = STATE_PREFIX + "#data_ready";
    // State save/restore: Write operation flag state
    public static final String STATE_WRITE_OPERATION = STATE_PREFIX + "#write_operation";
    // State save/restore: Data ready read position
    public static final String STATE_DATA_READY_READ_POSITION = STATE_PREFIX +
            "#data_ready_read_position";
    // State save/restore: Last marker data position
    public static final String STATE_LAST_MARKER_POSITION = STATE_PREFIX + "#last_marker_position";
    // State save/restore: CRC flag state
    public static final String STATE_CRC_FLAG = STATE_PREFIX + "#crc_flag";
    // State save/restore: Last data register read time
    public static final String STATE_LAST_DATA_REGISTER_READ_TIME = STATE_PREFIX +
            "#last_data_register_read_time";
    // State save/restore: Last data register write time
    public static final String STATE_LAST_DATA_REGISTER_WRITE_TIME = STATE_PREFIX +
            "#last_data_register_write_time";
    // State save/restore: Last controller access time
    public static final String STATE_LAST_ACCESS_TIME = STATE_PREFIX + "#last_access_time";
    // State save/restore: Selected floppy drive
    public static final String STATE_SELECTED_FLOPPY_DRIVE = STATE_PREFIX +
            "#selected_floppy_drive";
    // State save/restore: Motor started flag state
    public static final String STATE_MOTOR_STARTED = STATE_PREFIX + "#motor_started";

    private static final String STATE_DRIVE_PREFIX = "FloppyDrive";
    // State save/restore: Drive current track data
    public static final String STATE_DRIVE_CURRENT_TRACK_DATA = STATE_DRIVE_PREFIX +
            "#current_track_data";
    // State save/restore: Drive current track data is modified flag
    public static final String STATE_DRIVE_CURRENT_TRACK_DATA_MODIFIED = STATE_DRIVE_PREFIX +
            "#current_track_data_modified";
    // State save/restore: Drive current track data marker positions
    public static final String STATE_DRIVE_CURRENT_TRACK_DATA_MARKER_POSITIONS =
            STATE_DRIVE_PREFIX + "#current_track_data_marker_positions";
    // State save/restore: Drive write protect flag
    public static final String STATE_DRIVE_WRITE_PROTECT_MODE = STATE_DRIVE_PREFIX +
            "#write_protect";
    // State save/restore: Drive track number
    public static final String STATE_DRIVE_TRACK_NUMBER = STATE_DRIVE_PREFIX + "#track_number";
    // State save/restore: Drive track side
    public static final String STATE_DRIVE_TRACK_SIDE = STATE_DRIVE_PREFIX + "#track_side";

    // CPU clock ticks per track
    protected final long clockTicksPerTrack;

    // CPU clock ticks per word read/write
    protected final long clockTicksPerWord;

    // CPU clock ticks per index hole
    protected final long clockTicksPerIndexHole;

    // Controller is in synchronous data read state flag
    private boolean isSynchronousReadState;

    // Marker found in synchronous read state
    private boolean isMarkerFound;

    // Data ready in data register in synchronous read state flag
    private boolean isDataReady;

    // Data ready track read position in synchronous read state
    private int dataReadyReadPosition;

    // CRC flag (set if CRC is correct in synchronous read state, or if CRC is written in write state)
    private boolean isCrcFlag;

    // Last data register read CPU time
    private long lastDataRegisterReadCpuTime;

    // Write operation in progress flag
    private boolean isWriteOperation;

    // Last marker track position
    private int lastMarkerPosition;

    // Last data register write CPU time
    private long lastDataRegisterWriteCpuTime;

    // Last controller access CPU time
    private long lastAccessCpuTime;

    // Selected floppy drive identifier (or <code>null</code> if no floppy drive selected)
    private FloppyDriveIdentifier selectedFloppyDriveIdentifier;

    // Floppy drives array
    private final FloppyDrive[] floppyDrives = new FloppyDrive[4];

    // Floppy drives motor started flag
    private boolean isMotorStarted;

    /**
     * Floppy drive identifiers (A-D).
     */
    public enum FloppyDriveIdentifier {
        A, B, C, D,
    }

    // Floppy drive sides enumeration
    enum FloppyDriveSide {
        DOWN, UP
    }

    /**
     * Floppy drive class.
     */
    class FloppyDrive {
        public static final int SEQ_SYNC = 0x0000;
        public static final int SEQ_SYNC_LENGTH = 6;

        public static final int SEQ_GAP = 0x4e4e;
        public static final int SEQ_GAP1_LENGTH = 16;
        public static final int SEQ_GAP2_LENGTH = 11;
        public static final int SEQ_GAP3_LENGTH = 24;

        public static final int SEQ_MARK = 0xa1a1;
        public static final int SEQ_MARK_ID = 0xa1fe;
        public static final int SEQ_MARK_DATA = 0xa1fb;

        // This floppy drive identifier
        private final FloppyDriveIdentifier driveIdentifier;

        // Mounted disk image (null if no disk image mounted)
        private DiskImage mountedDiskImage;

        private boolean isWriteProtectMode;

        private int lastTrackNumber = MAX_TRACKS_PER_DISK;

        private int currentTrackNumber;

        private FloppyDriveSide currentTrackSide;

        private final byte[] currentSectorBytes = new byte[BYTES_PER_SECTOR];
        private final short[] currentTrackData = new short[WORDS_PER_TRACK];
        private final SparseBooleanArray currentTrackDataMarkerPositions = new SparseBooleanArray();

        private boolean isCurrentTrackDataModified;

        FloppyDrive(FloppyDriveIdentifier driveIdentifier) {
            this.driveIdentifier = driveIdentifier;
            setCurrentTrack(0, FloppyDriveSide.DOWN);
        }

        /**
         * Get current track data.
         * @return current track data
         */
        short[] getCurrentTrackData() {
            return currentTrackData;
        }

        private void loadCurrentTrackData() throws IOException {
            clearCurrentTrackDataMarkerPositions();
            int position = 0;
            // GAP1
            position = writeCurrentTrackDataSequence(position, SEQ_GAP, SEQ_GAP1_LENGTH);
            // Sectors
            for (int sectorNumber = 1; sectorNumber <= SECTORS_PER_TRACK; sectorNumber++) {
                // Header sync
                position = writeCurrentTrackDataSequence(position, SEQ_SYNC, SEQ_SYNC_LENGTH);
                // Sector header - IDAM + descriptor + CRC
                position = writeCurrentTrackSectorHeader(position, sectorNumber);
                // GAP2
                position = writeCurrentTrackDataSequence(position, SEQ_GAP, SEQ_GAP2_LENGTH);
                // Data sync
                position = writeCurrentTrackDataSequence(position, SEQ_SYNC, SEQ_SYNC_LENGTH);
                // Sector data - DATA AM + Data + CRC
                position = writeCurrentTrackSectorData(position, sectorNumber);
                // GAP3 or GAP4B for the last sector
                position = writeCurrentTrackDataSequence(position, SEQ_GAP,
                        (sectorNumber < SECTORS_PER_TRACK) ? SEQ_GAP3_LENGTH
                                : WORDS_PER_TRACK - position);
            }
            setCurrentTrackDataModified(false);
        }

        private int writeCurrentTrackDataSequence(int position, int value, int length) {
            int dataIndex = position;
            while (dataIndex < position + length) {
                writeCurrentTrackData(dataIndex++, value);
            }
            return dataIndex;
        }

        private int writeCurrentTrackSectorHeader(int position, int sectorNumber) {
            int dataIndex = position;
            // ID AM
            writeCurrentTrackData(dataIndex++, SEQ_MARK, true);
            writeCurrentTrackData(dataIndex++, SEQ_MARK_ID);
            // Track number (0-79), head number(0-1)
            writeCurrentTrackData(dataIndex++, currentTrackNumber << 8 | currentTrackSide.ordinal());
            // Sector number(1-10), sector size (2 for 512 bytes per sector)
            writeCurrentTrackData(dataIndex++, sectorNumber << 8 | 2);
            // CRC value (big endian)
            writeCurrentTrackData(dataIndex++, Crc16Utils.calculate(currentTrackData, position, 4));
            return dataIndex;
        }

        private int writeCurrentTrackSectorData(int position, int sectorNumber) throws IOException {
            int dataIndex = position;
            // DATA AM
            writeCurrentTrackData(dataIndex++, SEQ_MARK, true);
            writeCurrentTrackData(dataIndex++, SEQ_MARK_DATA);
            // Sector data
            int imageBufferOffset = getImageSectorOffset(currentTrackSide, currentTrackNumber, sectorNumber);
            mountedDiskImage.readBytes(currentSectorBytes, imageBufferOffset, BYTES_PER_SECTOR);
            for (int wordIndex = 0; wordIndex < WORDS_PER_SECTOR; wordIndex++) {
                writeCurrentTrackData(dataIndex++, currentSectorBytes[wordIndex * 2] << 8
                        | (currentSectorBytes[wordIndex * 2 + 1] & 0377));
            }
            // CRC value (big endian)
            int length = dataIndex - position;
            writeCurrentTrackData(dataIndex++, Crc16Utils.calculate(currentTrackData, position, length));
            return dataIndex;
        }

        private void saveCurrentTrackData() throws IOException {
            int position = 0;
            short[] trackData = getCurrentTrackData();

            // Loop by track data
            Loop:
            while (true) {
                // Find sector header start
                while (!isCurrentTrackDataMarkerPosition(position)) {
                    if (position >= WORDS_PER_TRACK - 5) {
                        break Loop;
                    }
                    position++;
                }

                int headerPosition = position;

                // Check sector header marker
                if (readCurrentTrackData(position++) != SEQ_MARK
                        || readCurrentTrackData(position++) != SEQ_MARK_ID) {
                    continue;
                }

                // Check sector header data
                int data = readCurrentTrackData(position++);
                int trackNumber = (data >> 8) & 0377;
                if (trackNumber != currentTrackNumber) {
                    Timber.w("Unexpected track number: expected: %d, found: %d",
                            currentTrackNumber, trackNumber);
                    continue;
                }
                int trackSide = data & 0377;
                if (trackSide != currentTrackSide.ordinal()) {
                    Timber.w("Unexpected track side: expected: %d, found: %d",
                            currentTrackSide.ordinal(), trackSide);
                    continue;
                }
                data = readCurrentTrackData(position++);
                int sectorNumber = (data >> 8) & 0377;
                if (sectorNumber < 1 || trackNumber > SECTORS_PER_TRACK) {
                    Timber.w("Unexpected sector number: %d", sectorNumber);
                    continue;
                }
                int sectorSize = data & 0377;
                if (sectorSize != 2) {
                    Timber.w("Unexpected sector size: %d", sectorSize);
                    continue;
                }

                // Check sector header CRC
                int length = position - headerPosition;
                int crcValue = readCurrentTrackData(position++);
                if (crcValue != (Crc16Utils.calculate(trackData, headerPosition, length) & 0177777)) {
                    Timber.w("Invalid sector header CRC, sector: %d", sectorNumber);
                    continue;
                }

                // Find sector data start
                while (!isCurrentTrackDataMarkerPosition(position)) {
                    if (position >= WORDS_PER_TRACK - (WORDS_PER_SECTOR + 3)) {
                        break Loop;
                    }
                    position++;
                }

                int dataPosition = position;

                // Check sector data marker
                if (readCurrentTrackData(position++) != SEQ_MARK
                        || readCurrentTrackData(position++) != SEQ_MARK_DATA) {
                    continue;
                }

                position += WORDS_PER_SECTOR;

                // Check sector data CRC
                length = position - dataPosition;
                crcValue = readCurrentTrackData(position++);
                if (crcValue != (Crc16Utils.calculate(trackData, dataPosition, length) & 0177777)) {
                    Timber.w("Invalid sector data CRC, sector: %d", sectorNumber);
                    continue;
                }

                // Save sector data to image
                Timber.d("Saving sector data, sector number: %d", sectorNumber);
                for (int wordIndex = 0; wordIndex < WORDS_PER_SECTOR; wordIndex++) {
                    data = readCurrentTrackData(dataPosition + 2 + wordIndex);
                    currentSectorBytes[wordIndex * 2] = (byte) (data >> 8);
                    currentSectorBytes[wordIndex * 2 + 1] = (byte) data;
                }
                int imageBufferOffset = getImageSectorOffset(currentTrackSide, currentTrackNumber,
                        sectorNumber);
                mountedDiskImage.writeBytes(currentSectorBytes, imageBufferOffset, BYTES_PER_SECTOR);
            }
            setCurrentTrackDataModified(false);
        }

        void flushCurrentTrackData() {
            if (isDiskImageMounted() && isCurrentTrackDataModified()) {
                try {
                    saveCurrentTrackData();
                } catch (IOException e) {
                    Timber.e(e, "Can't flush track data: drive %s, track %d, side %s",
                            driveIdentifier, getCurrentTrackNumber(), getCurrentTrackSide());
                }
            }
        }

        /**
         * Get sector offset in floppy drive image file.
         * @return sector offset in floppy drive image file (in bytes).
         */
        private int getImageSectorOffset(FloppyDriveSide side, int trackNumber, int sectorNumber) {
            return BYTES_PER_SECTOR * (SECTORS_PER_TRACK * (trackNumber * 2 + side.ordinal())
                    + sectorNumber - 1);
        }

        /**
         * Read current track data at given position.
         * @param position track data position (in words from track start)
         * @return read data word
         */
        int readCurrentTrackData(int position) {
            return currentTrackData[position] & 0177777;
        }

        /**
         * Write data to current track at given position.
         * @param position track data position (in words from the track start)
         * @param value data word to write
         * @param isMarker <code>true</code> if value is marker data
         */
        void writeCurrentTrackData(int position, int value, boolean isMarker) {
            currentTrackData[position] = (short) value;
            setCurrentTrackDataMarkerPosition(position, isMarker);
            setCurrentTrackDataModified(true);
        }

        /**
         * Write non-marker data to current track at given position.
         * @param position track data position (in words from the track start)
         * @param value data word to write
         */
        void writeCurrentTrackData(int position, int value) {
            writeCurrentTrackData(position, value, false);
        }

        /**
         * Check is current track data modified by write operations.
         * @return <code>true</code> if track data was modified, <code>false</code> otherwise
         */
        boolean isCurrentTrackDataModified() {
            return isCurrentTrackDataModified;
        }

        /**
         * Set current track data modified by write operations flag state
         * @param isCurrentTrackDataModified <code>true</code> to mark track data is modified,
         *                                 <code>false</code> to mark as not modified
         */
        void setCurrentTrackDataModified(boolean isCurrentTrackDataModified) {
            this.isCurrentTrackDataModified = isCurrentTrackDataModified;
        }

        /**
         * Set current track data position marker flag.
         * @param position track data position (in words from the track start)
         * @param isMarker <code>true</code> to set given position as marker position
         */
        void setCurrentTrackDataMarkerPosition(int position, boolean isMarker) {
            if (isMarker) {
                currentTrackDataMarkerPositions.put(position, true);
            } else {
                currentTrackDataMarkerPositions.delete(position);
            }
        }

        /**
         * Check given position of current track data is marker position.
         * @return <code>true</code> if given position is marker position,
         * <code>false</code> otherwise
         */
        boolean isCurrentTrackDataMarkerPosition(int position) {
            return currentTrackDataMarkerPositions.get(position);
        }

        /**
         * Clear current track data marker positions.
         */
        void clearCurrentTrackDataMarkerPositions() {
            currentTrackDataMarkerPositions.clear();
        }

        /**
         * Get current floppy drive track side.
         * @return current floppy drive track side
         */
        FloppyDriveSide getCurrentTrackSide() {
            return currentTrackSide;
        }

        /**
         * Get positioned track number.
         * @return current track number in range [0, TRACKS_PER_DISK)
         */
        int getCurrentTrackNumber() {
            return currentTrackNumber;
        }

        /**
         * Set positioned track number and side.
         * @param trackNumber track number to set
         * @param trackSide track side to set
         */
        void setCurrentTrack(int trackNumber, FloppyDriveSide trackSide) {
            if (isDebugEnabled) {
                d("set track: " + trackNumber + ", side: " + trackSide);
            }

            // Flush current track data
            flushCurrentTrackData();

            this.currentTrackNumber = trackNumber;
            this.currentTrackSide = trackSide;

            // Load current track data if disk image mounted
            if (isDiskImageMounted()) {
                try {
                    loadCurrentTrackData();
                } catch (IOException e) {
                    Timber.e(e, "Can't load track data: drive %s, track %d, side %s",
                            driveIdentifier, getCurrentTrackNumber(), getCurrentTrackSide());
                }
            }
        }

        /**
         * Get next current track number after single step to center or to edge.
         * @param isStepToCenter <code>true</code> is track changed with single step to center,
         * <code>false</code> if track changed with single step to edge
         * @return next track number in range [0, maxTrackNumber]
         */
        int getNextTrackNumber(boolean isStepToCenter) {
            return Math.max(Math.min((getCurrentTrackNumber() + (isStepToCenter ? 1 : -1)),
                    getLastTrackNumber()), 0);
        }

        /**
         * Get current disk image last track number.
         * @return last track number in range [0, MAX_TRACKS_PER_DISK - 1]
         */
        int getLastTrackNumber() {
            return lastTrackNumber;
        }

        /**
         * Set disk image last track number.
         * @param lastTrackNumber last track number in range [0, MAX_TRACKS_PER_DISK - 1]
         */
        void setLastTrackNumber(int lastTrackNumber) {
            if (lastTrackNumber < 0 || lastTrackNumber >= MAX_TRACKS_PER_DISK) {
                throw new IllegalArgumentException("Invalid lastTrackNumber value: "
                        + lastTrackNumber);
            }
            this.lastTrackNumber = lastTrackNumber;
        }

        boolean isDiskIndexHoleActive(long cpuTime) {
            return (isDiskImageMounted() && (cpuTime % clockTicksPerTrack) < clockTicksPerIndexHole);
        }

        /**
         * Get mounted disk image.
         * @return mounted disk image or <code>null</code> if no disk image mounted
         */
        DiskImage getMountedDiskImage() {
            return mountedDiskImage;
        }

        /**
         * Check is disk image mounted to this floppy drive or not.
         * @return <code>true</code> if disk image mounted to this floppy drive,
         * <code>false</code> if not mounted
         */
        boolean isDiskImageMounted() {
            return (getMountedDiskImage() != null);
        }

        /**
         * Check is drive in write protect mode.
         * @return <code>true</code> if drive is in write protect mode
         */
        boolean isWriteProtectMode() {
            return isWriteProtectMode;
        }

        /**
         * Set drive write protect mode.
         * @param isWriteProtectMode <code>true</code> to set drive write protect mode
         */
        void setWriteProtectMode(boolean isWriteProtectMode) {
            this.isWriteProtectMode = isWriteProtectMode;
        }

        /**
         * Mount disk image to this drive.
         * @param diskImage Disk image to mount
         * @param isWriteProtectMode <code>true</code> to mount disk image in write protect mode
         * @throws Exception in case of mounting error
         */
        void mountDiskImage(@NonNull DiskImage diskImage, boolean isWriteProtectMode)
                throws Exception {
            // Check disk image
            if (diskImage.length() > MAX_BYTES_PER_DISK) {
                throw new IllegalArgumentException("Invalid disk image size: " +
                            diskImage.length());
            }
            if (isDiskImageMounted()) {
                unmountDiskImage();
            }
            setWriteProtectMode(isWriteProtectMode);
            int lastDiskImageTrackNumber = (int) (diskImage.length()
                    / (SECTORS_PER_TRACK * BYTES_PER_SECTOR * 2) - 1);
            setLastTrackNumber(lastDiskImageTrackNumber);
            this.mountedDiskImage = diskImage;
            // Reload track data
            setCurrentTrack(getCurrentTrackNumber(), getCurrentTrackSide());
        }

        /**
         * Unmount current mounted disk image.
         * @throws Exception in case of unmount error
         */
        void unmountDiskImage() throws Exception {
            flushCurrentTrackData();
            mountedDiskImage.close();
            mountedDiskImage = null;
        }
    }

    public FloppyController(Computer computer) {
        this.clockTicksPerTrack = computer.nanosToCpuTime(NANOSECS_PER_TRACK);
        this.clockTicksPerWord = computer.nanosToCpuTime(NANOSECS_PER_TRACK / WORDS_PER_TRACK);
        this.clockTicksPerIndexHole = computer.nanosToCpuTime(NANOSECS_PER_INDEX_HOLE);
        // Create floppy drives
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            floppyDrives[driveIdentifier.ordinal()] = new FloppyDrive(driveIdentifier);
        }
    }

    public void setDebugEnabled(boolean state) {
        isDebugEnabled = state;
    }

    protected static void d(String message) {
        Timber.d(message);
    }

    protected FloppyDrive getFloppyDrive(FloppyDriveIdentifier drive) {
        return (drive != null) ? floppyDrives[drive.ordinal()] : null;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public synchronized void init(long cpuTime, boolean isHardwareReset) {
        writeControlRegister(cpuTime, 0);
    }

    @Override
    public synchronized void saveState(State outState) {
        FloppyDriveIdentifier selectedDriveIdentifier = getSelectedFloppyDriveIdentifier();
        if (selectedDriveIdentifier != null) {
            outState.putString(STATE_SELECTED_FLOPPY_DRIVE, selectedDriveIdentifier.name());
        }
        outState.putBoolean(STATE_SYNCHRONOUS_READ, isSynchronousReadState());
        outState.putBoolean(STATE_WRITE_OPERATION, isWriteOperation());
        outState.putBoolean(STATE_MARKER_FOUND, isMarkerFound());
        outState.putBoolean(STATE_DATA_READY, isDataReady());
        outState.putInt(STATE_DATA_READY_READ_POSITION, getDataReadyReadPosition());
        outState.putInt(STATE_LAST_MARKER_POSITION, getLastMarkerPosition());
        outState.putBoolean(STATE_CRC_FLAG, isCrcFlag());
        outState.putLong(STATE_LAST_DATA_REGISTER_READ_TIME, getLastDataRegisterReadCpuTime());
        outState.putLong(STATE_LAST_DATA_REGISTER_WRITE_TIME, getLastDataRegisterWriteCpuTime());
        outState.putLong(STATE_LAST_ACCESS_TIME, getLastAccessCpuTime());
        outState.putBoolean(STATE_MOTOR_STARTED, isMotorStarted());
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            FloppyDrive drive = getFloppyDrive(driveIdentifier);
            short[] trackData = drive.getCurrentTrackData();
            ByteBuffer trackDataBuffer = ByteBuffer.allocate(trackData.length * 2);
            for (short trackDataWord: trackData) {
                trackDataBuffer.putShort(trackDataWord);
            }
            outState.putByteArray(getFloppyDriveStateKey(STATE_DRIVE_CURRENT_TRACK_DATA,
                    driveIdentifier), trackDataBuffer.array());
            outState.putBoolean(getFloppyDriveStateKey(STATE_DRIVE_CURRENT_TRACK_DATA_MODIFIED,
                    driveIdentifier), drive.isCurrentTrackDataModified());
            ArrayList<Integer> markerPositions = new ArrayList<>();
            for (int position = 0; position < WORDS_PER_TRACK; position++) {
                if (drive.isCurrentTrackDataMarkerPosition(position)) {
                    markerPositions.add(position);
                }
            }
            outState.putIntegerArrayList(getFloppyDriveStateKey(
                    STATE_DRIVE_CURRENT_TRACK_DATA_MARKER_POSITIONS, driveIdentifier),
                    markerPositions);
            outState.putBoolean(getFloppyDriveStateKey(STATE_DRIVE_WRITE_PROTECT_MODE,
                    driveIdentifier), drive.isWriteProtectMode());
            outState.putInt(getFloppyDriveStateKey(STATE_DRIVE_TRACK_NUMBER, driveIdentifier),
                    drive.getCurrentTrackNumber());
            outState.putString(getFloppyDriveStateKey(STATE_DRIVE_TRACK_SIDE,
                    driveIdentifier), drive.getCurrentTrackSide().name());
        }
    }

    @Override
    public synchronized void restoreState(State inState) {
        String selectedDriveIdentifierName = inState.getString(STATE_SELECTED_FLOPPY_DRIVE, null);
        selectFloppyDrive((selectedDriveIdentifierName != null)
                ? FloppyDriveIdentifier.valueOf(selectedDriveIdentifierName) : null);
        setSynchronousReadState(inState.getBoolean(STATE_SYNCHRONOUS_READ));
        setWriteOperation(inState.getBoolean(STATE_WRITE_OPERATION));
        setMarkerFound(inState.getBoolean(STATE_MARKER_FOUND));
        setDataReady(inState.getBoolean(STATE_DATA_READY));
        setDataReadyReadPosition(inState.getInt(STATE_DATA_READY_READ_POSITION));
        setLastMarkerPosition(inState.getInt(STATE_LAST_MARKER_POSITION));
        setCrcFlag(inState.getBoolean(STATE_CRC_FLAG));
        setLastDataRegisterReadCpuTime(inState.getLong(STATE_LAST_DATA_REGISTER_READ_TIME));
        setLastDataRegisterWriteCpuTime(inState.getLong(STATE_LAST_DATA_REGISTER_WRITE_TIME));
        setLastAccessCpuTime(inState.getLong(STATE_LAST_ACCESS_TIME));
        setMotorStarted(inState.getBoolean(STATE_MOTOR_STARTED));
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            FloppyDrive drive = getFloppyDrive(driveIdentifier);
            int driveTrackNumber = inState.getInt(getFloppyDriveStateKey(
                    STATE_DRIVE_TRACK_NUMBER, driveIdentifier));
            FloppyDriveSide driveTrackSide = FloppyDriveSide.valueOf(inState.getString(
                    getFloppyDriveStateKey(STATE_DRIVE_TRACK_SIDE, driveIdentifier)));
            drive.setCurrentTrack(driveTrackNumber, driveTrackSide);
            drive.setWriteProtectMode(inState.getBoolean(getFloppyDriveStateKey(
                    STATE_DRIVE_WRITE_PROTECT_MODE, driveIdentifier)));
            byte[] trackDataBytes = inState.getByteArray(getFloppyDriveStateKey(
                    STATE_DRIVE_CURRENT_TRACK_DATA, driveIdentifier));
            if (trackDataBytes != null) {
                ByteBuffer trackDataBuffer = ByteBuffer.wrap(trackDataBytes);
                short[] trackData = ShortBuffer.allocate(WORDS_PER_TRACK)
                        .put(trackDataBuffer.asShortBuffer()).array();
                System.arraycopy(trackData, 0, drive.getCurrentTrackData(),
                        0, trackData.length);
            }
            ArrayList<Integer> markerPositions = inState.getIntegerArrayList(
                    getFloppyDriveStateKey(STATE_DRIVE_CURRENT_TRACK_DATA_MARKER_POSITIONS,
                            driveIdentifier));
            drive.clearCurrentTrackDataMarkerPositions();
            for (int markerPosition: markerPositions) {
                drive.setCurrentTrackDataMarkerPosition(markerPosition, true);
            }
            drive.setCurrentTrackDataModified(inState.getBoolean(getFloppyDriveStateKey(
                    STATE_DRIVE_CURRENT_TRACK_DATA_MODIFIED, driveIdentifier)));
        }
    }

    private static String getFloppyDriveStateKey(String stateKey,
            FloppyDriveIdentifier driveIdentifier) {
        return stateKey + ":" + driveIdentifier.name();
    }

    @Override
    public synchronized int read(long cpuTime, int address) {
        return (address == CONTROL_REGISTER_ADDRESS)
                ? readControlRegister(cpuTime)
                : readDataRegister(cpuTime);
    }

    @Override
    public synchronized boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        if (isDebugEnabled) {
            d("write: " + Integer.toOctalString(address) +
                    ", value: " + Integer.toOctalString(value) + ", isByteMode: " + isByteMode);
        }
        if ((address & 0177776) == CONTROL_REGISTER_ADDRESS) {
            writeControlRegister(cpuTime, value);
        } else {
            writeDataRegister(cpuTime, value);
        }
        return true;
    }

    protected int readControlRegister(long cpuTime) {
        FloppyDrive drive = getSelectedFloppyDrive();
        if (drive == null) {
            return 0;
        }

        int result = 0;

        // Track 0 flag
        if (drive.getCurrentTrackNumber() == 0) {
            result |= TR0;
        }

        // Floppy disk write protect flag
        if (drive.isDiskImageMounted() && drive.isWriteProtectMode()) {
            result |= WRP;
        }

        // Floppy disk index hole activity flag
        if (drive.isDiskIndexHoleActive(cpuTime)) {
            result |= IND;
        }

        int trackPosition = getTrackPosition(cpuTime);

        if (isWriteOperation()) {
            // Handle write state
            int lastDataRegisterWritePosition = getTrackPosition(getLastDataRegisterWriteCpuTime());
            int numDataWords = getNumTrackDataWords(lastDataRegisterWritePosition, trackPosition);

            // Set flags
            setDataReady(numDataWords > 0); // last word written

            if (numDataWords > 1) { // CRC written
                if (!isCrcFlag()) {
                    setCrcFlag(true);
                    // Write CRC value
                    int crcPosition = getNextTrackPosition(lastDataRegisterWritePosition);
                    short[] trackData = drive.getCurrentTrackData();
                    int length = getNumTrackDataWords(getLastMarkerPosition(), crcPosition);
                    short crcValue = Crc16Utils.calculate(trackData, getLastMarkerPosition(), length);
                    drive.writeCurrentTrackData(crcPosition, crcValue);
                }
            }

            // Check write operation end condition
            if (numDataWords > 2) { // last word and CRC written, end write operation
                endWriteOperation();
            }
        }

        if (!isWriteOperation()) {
            // Handle read state
            int lastDataRegisterReadPosition = getTrackPosition(getLastDataRegisterReadCpuTime());
            int numReadDataWords = getNumTrackDataWords(lastDataRegisterReadPosition, trackPosition);

            // Check data is ready
            if (isMarkerFound() && !isDataReady()) {
                setDataReady(numReadDataWords > 0);
                if (isDataReady()) {
                    setDataReadyReadPosition(getNextTrackPosition(lastDataRegisterReadPosition));
//                    d(TAG, "isDataReady, position: " + trackPosition +
//                            ", dataReadyReadPosition: " + getDataReadyReadPosition() +
//                            ", readDataWords: " + numReadDataWords + ", isCrcPosition: " +
//                            drive.isCurrentTrackDataCrcPosition(trackPosition));
                }
            }

            // Check for synchronous read state
            if (isSynchronousReadState()) {
                if (!isMarkerFound()) {
                    // Check for data marker position
                    if (drive.isCurrentTrackDataMarkerPosition(trackPosition)) {
                        if (isDebugEnabled) {
                            d("marker found, position: " + trackPosition);
                        }
                        setMarkerFound(true);
                        setLastMarkerPosition(trackPosition);
                        setDataReady(true);
                        setDataReadyReadPosition(trackPosition);
                    }
                } else {
                    // Data marker found, check flags of synchronous read state
                    if (numReadDataWords > 1) {
                        // Data ready flag set, synchronous read completed
                        setSynchronousReadState(false);
                        // Check CRC is correct
                        short[] trackData = drive.getCurrentTrackData();
                        int crcPosition = getDataReadyReadPosition();
                        int length = getNumTrackDataWords(getLastMarkerPosition(), crcPosition);
                        short crcValue = Crc16Utils.calculate(trackData, getLastMarkerPosition(), length);
                        setCrcFlag((crcValue & 0177777) == drive.readCurrentTrackData(crcPosition));
                        if (isDebugEnabled) {
                            d("synchronous read completed, position: " + trackPosition +
                                    ", dataReadyReadPosition: " + getDataReadyReadPosition() +
                                    ", readDataWords: " + numReadDataWords +
                                    ", isCrcCorrect: " + isCrcFlag());
                        }
                    }
                }
            }
        }

        if (isDataReady()) {
            result |= TR;
        }

        if (isCrcFlag()) {
            result |= CRC;
        }

        return result;
    }

    private static int getNumTrackDataWords(int oldPosition, int newPosition) {
        return (oldPosition <= newPosition) ? (newPosition - oldPosition)
                : (WORDS_PER_TRACK - oldPosition + newPosition);
    }

    protected void writeControlRegister(long cpuTime, int value) {
        // Set floppy drives motor state
        setMotorStarted((value & MSW) != 0);

        // Determine floppy drive to operate
        int driveMask = value & (DS0 | DS1 | DS2 | DS3);
        FloppyDriveIdentifier driveIdentifier = null;
        switch (Integer.lowestOneBit(driveMask)) {
            case DS0:
                driveIdentifier = FloppyDriveIdentifier.A;
                break;
            case DS1:
                driveIdentifier = FloppyDriveIdentifier.B;
                break;
            case DS2:
                driveIdentifier = FloppyDriveIdentifier.C;
                break;
            case DS3:
                driveIdentifier = FloppyDriveIdentifier.D;
                break;
            default:
                // Drive unselected
                break;
        }

        // Select floppy drive
        selectFloppyDrive(driveIdentifier);

        // Do operations on selected drive, if any
        if (driveIdentifier != null) {
            FloppyDrive drive = getFloppyDrive(driveIdentifier);

            // Handle Write Marker flag
            if ((value & WM) != 0 && isWriteOperation()) {
                int markerPosition = getTrackPosition(getLastDataRegisterWriteCpuTime());
                drive.setCurrentTrackDataMarkerPosition(markerPosition, true);
                setLastMarkerPosition(markerPosition);
            }

            // Check is track number or side changed
            FloppyDriveSide trackSide = (value & HS) != 0 ? FloppyDriveSide.UP : FloppyDriveSide.DOWN;
            int trackNumber = (value & ST) != 0 ? drive.getNextTrackNumber((value & DIR) != 0)
                    : drive.getCurrentTrackNumber();
            if (trackSide != drive.getCurrentTrackSide()
                    || trackNumber != drive.getCurrentTrackNumber()) {
                // Track changed, cancel synchronous read or write mode
                cancelSynchronousRead();
                endWriteOperation();
                // Set floppy drive track number and side
                drive.setCurrentTrack(trackNumber, trackSide);
            }

            // Process GOR flag
            if ((value & GOR) != 0) {
                startSynchronousRead();
            }
        }
    }

    private boolean isMarkerFound() {
        return isMarkerFound;
    }

    private void setMarkerFound(boolean isMarkerFound) {
        this.isMarkerFound = isMarkerFound;
    }

    private boolean isDataReady() {
        return isDataReady;
    }

    private void setDataReady(boolean isDataReady) {
        this.isDataReady = isDataReady;
    }

    private int getDataReadyReadPosition() {
        return dataReadyReadPosition;
    }

    private void setDataReadyReadPosition(int position) {
        this.dataReadyReadPosition = position;
    }

    private boolean isCrcFlag() {
        return isCrcFlag;
    }

    private void setCrcFlag(boolean isCrcFlag) {
        this.isCrcFlag = isCrcFlag;
    }

    private long getLastDataRegisterReadCpuTime() {
        return lastDataRegisterReadCpuTime;
    }

    private void setLastDataRegisterReadCpuTime(long lastDataRegisterReadCpuTime) {
        this.lastDataRegisterReadCpuTime = lastDataRegisterReadCpuTime;
    }

    private long getLastDataRegisterWriteCpuTime() {
        return lastDataRegisterWriteCpuTime;
    }

    private void setLastDataRegisterWriteCpuTime(long lastDataRegisterWriteCpuTime) {
        this.lastDataRegisterWriteCpuTime = lastDataRegisterWriteCpuTime;
    }

    public synchronized long getLastAccessCpuTime() {
        return lastAccessCpuTime;
    }

    public synchronized void setLastAccessCpuTime(long lastAccessCpuTime) {
        this.lastAccessCpuTime = lastAccessCpuTime;
    }

    private boolean isSynchronousReadState() {
        return isSynchronousReadState;
    }

    private void setSynchronousReadState(boolean state) {
        isSynchronousReadState = state;
    }

    private void startSynchronousRead() {
        endWriteOperation();
        cancelSynchronousRead();
        setSynchronousReadState(true);
    }

    private void cancelSynchronousRead() {
        setSynchronousReadState(false);
        setMarkerFound(false);
        setDataReady(false);
        setCrcFlag(false);
    }

    protected int readDataRegister(long cpuTime) {
        setLastAccessCpuTime(cpuTime);
        endWriteOperation();
        int value = 0;
        FloppyDrive selectedDrive = getSelectedFloppyDrive();
        if (selectedDrive != null && isMotorStarted()) {
            int readTrackPosition = isDataReady() ? getDataReadyReadPosition()
                    : getTrackPosition(cpuTime);
            value = selectedDrive.readCurrentTrackData(readTrackPosition);
//            d(TAG, "readDataRegister, position: " + readTrackPosition +
//                    ", getCurrentTrackPosition(" + cpuTime + "): " +
//                    getTrackPosition(cpuTime));
        }
        setDataReady(false);
        setLastDataRegisterReadCpuTime(cpuTime);
        return value;
    }

    private boolean isWriteOperation() {
        return isWriteOperation;
    }

    private void setWriteOperation(boolean isWriteOperation) {
        this.isWriteOperation = isWriteOperation;
    }

    private int getLastMarkerPosition() {
        return lastMarkerPosition;
    }

    private void setLastMarkerPosition(int position) {
        this.lastMarkerPosition = position;
    }

    private void startWriteOperation(int position) {
        cancelSynchronousRead();
        setWriteOperation(true);
    }

    private void endWriteOperation() {
        setCrcFlag(false);
        setWriteOperation(false);
        FloppyDrive floppyDrive = getSelectedFloppyDrive();
        if (floppyDrive != null) {
            floppyDrive.flushCurrentTrackData();
        }
    }

    protected void writeDataRegister(long cpuTime, int value) {
        setLastAccessCpuTime(cpuTime);
        FloppyDrive selectedDrive = getSelectedFloppyDrive();
        if (selectedDrive != null && isMotorStarted()) {
            int position = getTrackPosition(cpuTime);
            if (!isWriteOperation() && !selectedDrive.isWriteProtectMode()) {
                startWriteOperation(position);
            }
            if (isWriteOperation()) {
                // Mounted disk media is in big-endian mode, and low byte is written first,
                // so swap bytes before write
                int writeValue = ((value << 8) & 0177400) | ((value >> 8) & 0377);
                selectedDrive.writeCurrentTrackData(position, writeValue);
            }
        }
        setDataReady(false);
        setCrcFlag(false);
        setLastDataRegisterWriteCpuTime(cpuTime);
    }

    /**
     * Get track position for given CPU time.
     * @param cpuTime CPU time to get track position
     * @return track position (in words)
     */
    private int getTrackPosition(long cpuTime) {
        return (int) ((cpuTime / clockTicksPerWord) % WORDS_PER_TRACK);
    }

    /**
     * Get next track position.
     * @param position track position (in words)
     * @return next track position
     */
    private static int getNextTrackPosition(int position) {
        return (position + 1) % WORDS_PER_TRACK;
    }

    /**
     * Mount floppy drive disk image to given drive.
     * @param diskImage floppy drive disk image
     * @param drive {@link FloppyDriveIdentifier} of drive to mount disk image
     * @param isWriteProtectMode <code>true</code> to mount disk image in write protect mode
     * @throws Exception in case of disk image mounting error
     */
    public synchronized void mountDiskImage(DiskImage diskImage, FloppyDriveIdentifier drive,
            boolean isWriteProtectMode) throws Exception {
        getFloppyDrive(drive).mountDiskImage(diskImage, isWriteProtectMode);
    }

    /**
     * Unmount floppy drive disk image from given drive.
     * @param drive {@link FloppyDriveIdentifier} of drive to unmount disk image
     * @throws Exception in case of disk image unmounting error
     */
    public synchronized void unmountDiskImage(FloppyDriveIdentifier drive) throws Exception {
        getFloppyDrive(drive).unmountDiskImage();
    }

    /**
     * Unmount floppy drive disk image from all drives.
     */
    public synchronized void unmountDiskImages() {
        for (FloppyDriveIdentifier drive : FloppyDriveIdentifier.values()) {
            try {
                if (isFloppyDriveMounted(drive)) {
                    unmountDiskImage(drive);
                }
            } catch (Exception e) {
                Timber.e(e, "Error while unmounting disk image from drive %s", drive);
            }
        }
    }

    /**
     * Get selected floppy drive.
     * @return selected floppy drive or <code>null</code> if no floppy drive
     * currently selected
     */
    protected FloppyDrive getSelectedFloppyDrive() {
        return getFloppyDrive(getSelectedFloppyDriveIdentifier());
    }

    /**
     * Get selected floppy drive identifier.
     * @return selected floppy drive identifier or <code>null</code> if no floppy drive
     * currently selected
     */
    public FloppyDriveIdentifier getSelectedFloppyDriveIdentifier() {
        return selectedFloppyDriveIdentifier;
    }

    /**
     * Select floppy drive.
     * @param floppyDriveIdentifier selected floppy drive identifier (or <code>null</code>
     * to unselect all floppy drives)
     */
    protected void selectFloppyDrive(FloppyDriveIdentifier floppyDriveIdentifier) {
        if (isDebugEnabled) {
            d("selected drive: " + floppyDriveIdentifier);
        }
        if (selectedFloppyDriveIdentifier != floppyDriveIdentifier) {
            // Cancel synchronous data read or write operation
            cancelSynchronousRead();
            endWriteOperation();
        }
        this.selectedFloppyDriveIdentifier = floppyDriveIdentifier;
    }

    /**
     * Get floppy drives motor state.
     * @return <code>true</code> if motor started in all drives,
     * <code>false</code> if stopped
     */
    public synchronized boolean isMotorStarted() {
        return isMotorStarted;
    }

    /**
     * Set floppy drives motor state.
     * @param isStarted <code>true</code> to start motors in all drives,
     * <code>false</code> to stop
     */
    protected void setMotorStarted(boolean isStarted) {
        if (isDebugEnabled) {
            d("set floppy drives motor state: " + (isStarted ? "STARTED" : "STOPPED"));
        }
        this.isMotorStarted = isStarted;
    }

    /**
     * Check given floppy drive is mounted.
     * @param driveIdentifier {@link FloppyDriveIdentifier} of drive to check
     * @return <code>true</code> if floppy drive is mounted, <code>false</code> if not
     */
    public synchronized boolean isFloppyDriveMounted(FloppyDriveIdentifier driveIdentifier) {
        return getFloppyDrive(driveIdentifier).isDiskImageMounted();
    }

    /**
     * Get mounted floppy drive image.
     * @param driveIdentifier {@link FloppyDriveIdentifier} of drive to get mounted disk image
     * @return mounted floppy drive image or <code>null</code> if no floppy drive image mounted
     */
    public synchronized DiskImage getFloppyDriveImage(FloppyDriveIdentifier driveIdentifier) {
        return getFloppyDrive(driveIdentifier).getMountedDiskImage();
    }

    /**
     * Check floppy drive is in write protect mode.
     * @param driveIdentifier {@link FloppyDriveIdentifier} of drive to get mode
     * @return <code>true</code> if floppy drive is in write protect mode
     */
    public synchronized boolean isFloppyDriveInWriteProtectMode(FloppyDriveIdentifier driveIdentifier) {
        return getFloppyDrive(driveIdentifier).isWriteProtectMode();
    }

    /**
     * Set floppy drive write protect mode.
     * @param driveIdentifier {@link FloppyDriveIdentifier} of drive to set mode
     * @param isWriteProtectMode <code>true</code> to set floppy drive in write protect mode
     */
    public synchronized void setFloppyDriveWriteProtectMode(FloppyDriveIdentifier driveIdentifier,
                                                            boolean isWriteProtectMode) {
        getFloppyDrive(driveIdentifier).setWriteProtectMode(isWriteProtectMode);
    }
}
