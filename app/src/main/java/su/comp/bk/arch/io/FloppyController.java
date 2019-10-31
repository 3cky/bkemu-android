/*
 * Created: 11.10.2012
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

import static su.comp.bk.arch.Computer.NANOSECS_IN_MSEC;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import su.comp.bk.arch.Computer;
import su.comp.bk.util.Crc16;
import android.os.Bundle;
import android.util.Log;

/**
 * Floppy drive controller (К1801ВП1-128).
 */
public class FloppyController implements Device {

    protected static final String TAG = FloppyController.class.getName();

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

    /** Tracks per floppy disk */
    public final static int TRACKS_PER_DISK = 81;
    /** Sectors per track */
    public final static int SECTORS_PER_TRACK = 10;
    /** Bytes per sector */
    public final static int BYTES_PER_SECTOR = 512;

    /** Bytes per two-sided disk */
    public final static int BYTES_PER_DISK = TRACKS_PER_DISK * SECTORS_PER_TRACK * BYTES_PER_SECTOR * 2;

    private final static int[] ADDRESSES = { CONTROL_REGISTER_ADDRESS, DATA_REGISTER_ADDRESS };

    // State save/restore: Synchronous read flag state
    private static final String STATE_SYNCHRONOUS_READ = FloppyController.class.getName() +
            "#synch_read";
    // State save/restore: Marker found flag state
    private static final String STATE_MARKER_FOUND = FloppyController.class.getName() +
            "#marker_found";
    // State save/restore: Data ready flag state
    private static final String STATE_DATA_READY = FloppyController.class.getName() +
            "#data_ready";
    // State save/restore: Data ready read position
    private static final String STATE_DATA_READY_READ_POSITION = FloppyController.class.getName() +
            "#data_ready_read_position";
    // State save/restore: CRC correct flag state
    private static final String STATE_CRC_CORRECT = FloppyController.class.getName() +
            "#crc_correct";
    // State save/restore: Last data register read time
    private static final String STATE_LAST_DATA_REGISTER_READ_TIME = FloppyController.class.getName() +
            "#last_data_register_read_time";
    // State save/restore: Last controller access time
    private static final String STATE_LAST_ACCESS_TIME = FloppyController.class.getName() +
            "#last_access_time";
    // State save/restore: Selected floppy drive
    private static final String STATE_SELECTED_FLOPPY_DRIVE = FloppyController.class.getName() +
            "#selected_floppy_drive";
    // State save/restore: Motor started flag state
    private static final String STATE_MOTOR_STARTED = FloppyController.class.getName() +
            "#motor_started";
    // State save/restore: Drive disk image file URI
    private static final String STATE_DRIVE_IMAGE_FILE_URI = FloppyDrive.class.getName() +
            "#disk_image_file_uri";
    // State save/restore: Drive disk image read only flag
    private static final String STATE_DRIVE_IMAGE_READ_ONLY = FloppyDrive.class.getName() +
            "#disk_image_read_only";
    // State save/restore: Drive track number
    private static final String STATE_DRIVE_TRACK_NUMBER = FloppyDrive.class.getName() +
            "#track_number";
    // State save/restore: Drive track side
    private static final String STATE_DRIVE_TRACK_SIDE = FloppyDrive.class.getName() +
            "#track_side";

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

    // CRC is correct in synchronous read state flag
    private boolean isCrcCorrect;

    // Last data register read CPU time
    private long lastDataRegisterReadCpuTime;

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
     * Floppy drive track changed event listener.
     */
    interface OnFloppyDriveTrackChanged {
        /**
         * Notifies about floppy drive track changed.
         * @param trackNumber Track number [0, TRACKS_PER_DISK]
         * @param trackSide Track side [Side.Down, Side.UP]
         */
        void onFloppyDriveTrackChanged(int trackNumber, FloppyDriveSide trackSide);
    }

    /**
     * Floppy drive class.
     */
    class FloppyDrive {
        // This floppy drive identifier
        private final FloppyDriveIdentifier driveIdentifier;

        // Mounted disk image file URI (or null if no disk image mounted)
        private String mountedDiskImageFileUri;

        private RandomAccessFile mountedDiskImageFile;
        private ByteBuffer mountedDiskImageBuffer;

        private boolean isMountedDiskImageReadOnly;

        private int currentTrackNumber;

        private FloppyDriveSide currentTrackSide;

        private final FloppyDriveTrackArea[] currentTrackData = new FloppyDriveTrackArea[WORDS_PER_TRACK];

        protected final OnFloppyDriveTrackChanged[] trackChangedListeners =
                new OnFloppyDriveTrackChanged[SECTORS_PER_TRACK * 2];

        /**
         * Abstract floppy drive track area class.
         */
        abstract class FloppyDriveTrackArea {
            // Track area position in track
            private int startPosition;

            /**
             * Abstract floppy drive track area constructor.
             * @param position floppy drive track area position from track start (in words)
             */
            FloppyDriveTrackArea(int position) {
                this.startPosition = position;
            }

            /**
             * Get floppy drive track area index for given track position.
             * @param position floppy drive track area position from track start (in words)
             * @return this floppy drive track area index
             */
            protected int getPositionIndex(int position) {
                return (position - startPosition);
            }

            /**
             * Get floppy drive track area length.
             * @return floppy drive track area length (in words)
             */
            protected abstract int getLength();

            /**
             * Check given position is marker start position.
             * @param position floppy drive track area position from track start (in words)
             * @return <code>true</code> if given position is marker start position,
             * <code>false</code> otherwise
             */
            public boolean isMarkerPosition(int position) {
                return isDiskImageMounted() && isMarkerPositionInternal(getPositionIndex(position));
            }

            /**
             * Check given internal data index is marker start position.
             * @return <code>true</code> if given internal data index is marker start position,
             * <code>false</code> otherwise
             */
            protected abstract boolean isMarkerPositionInternal(int index);

            /**
             * Check given position is CRC position.
             * @param position floppy drive track area position from track start (in words)
             * @return <code>true</code> if given position is CRC position,
             * <code>false</code> otherwise
             */
            public boolean isCrcPosition(int position) {
                return isDiskImageMounted() && isCrcPositionInternal(getPositionIndex(position));
            }

            /**
             * Check given internal data index is CRC position.
             * @return <code>true</code> if given internal data index is CRC position,
             * <code>false</code> otherwise
             */
            protected abstract boolean isCrcPositionInternal(int index);

            /**
             * Read data from this floppy drive track area.
             * @param position floppy drive track area read position (from track start, in words)
             * @return read floppy drive track area word
             */
            public int read(int position) {
                return isDiskImageMounted() ? readInternal(getPositionIndex(position)) : 0;
            }

            /**
             * Internal data read from this floppy drive track area.
             * @param index floppy drive track area read index from this area start (in words)
             * @return read floppy drive track area data word
             */
            protected abstract int readInternal(int index);

            /**
             * Write data to this floppy drive track area.
             * @param position floppy drive track area write position (from track start, in words)
             * @param value word data value to write
             */
            public void write(int position, int value) {
                if (isDiskImageMounted()) {
                    writeInternal(getPositionIndex(position), value);
                }
            }

            /**
             * Internal data write to this floppy drive track area.
             * @param index floppy drive track area write index from this area start (in words)
             * @param value word data value to write
             */
            protected abstract void writeInternal(int index, int value);
        }

        /**
         * Floppy drive track gap/sync sequences class.
         */
        class FloppyDriveTrackSequence extends FloppyDriveTrackArea {
            public static final int SEQ_GAP = 0x4e4e;
            public static final int SEQ_SYNC = 0x0000;

            public static final int SEQ_SYNC_LENGTH = 6;
            public static final int SEQ_GAP1_LENGTH = 16;
            public static final int SEQ_GAP2_LENGTH = 11;
            public static final int SEQ_GAP3_LENGTH = 18;

            private final int sequenceValue;
            private final int sequenceLength;

            FloppyDriveTrackSequence(int position, int value, int length) {
                super(position);
                this.sequenceValue = value;
                this.sequenceLength = length;
            }

            @Override
            protected int getLength() {
                return sequenceLength;
            }

            @Override
            protected int readInternal(int index) {
                return sequenceValue;
            }

            @Override
            protected void writeInternal(int index, int value) {
                // Do nothing
            }

            @Override
            protected boolean isMarkerPositionInternal(int index) {
                return false;
            }

            @Override
            protected boolean isCrcPositionInternal(int index) {
                return false;
            }
        }

        /**
         * Floppy drive track sector header class.
         */
        class FloppyDriveTrackSectorHeader extends FloppyDriveTrackArea
                implements OnFloppyDriveTrackChanged {
            // Sector header - track number byte index
            private final static int TRACK_NUMBER_INDEX = 4;
            // Sector header - head number byte index
            private final static int HEAD_NUMBER_INDEX = 5;
            // Sector header - sector number byte index
            private final static int SECTOR_NUMBER_INDEX = 6;
            // Sector header - CRC value first byte index
            private final static int CRC_VALUE_INDEX = 8;

            private final byte[] data = {
                (byte) 0xa1, (byte) 0xa1, (byte) 0xa1, (byte) 0xfe, // IDAM
                0, 0, 0, // track number (0-79), head number(0-1), sector number(1-10)
                2, // 512 bytes per sector
                0, 0 // CRC value (big endian)
            };

            FloppyDriveTrackSectorHeader(int position, int sectorNumber) {
                super(position);
                data[SECTOR_NUMBER_INDEX] = (byte) sectorNumber;
            }

            @Override
            public void onFloppyDriveTrackChanged(int trackNumber, FloppyDriveSide trackSide) {
                data[TRACK_NUMBER_INDEX] = (byte) trackNumber;
                data[HEAD_NUMBER_INDEX] = (byte) trackSide.ordinal();
                correctCrcValue();
            }

            private void correctCrcValue() {
                short crcValue = Crc16.calculate(data, 0, CRC_VALUE_INDEX);
                data[CRC_VALUE_INDEX] = (byte) (crcValue >> 8);
                data[CRC_VALUE_INDEX + 1] = (byte) crcValue;
            }

            @Override
            protected int getLength() {
                return (data.length >> 1);
            }

            @Override
            protected int readInternal(int index) {
                int dataIndex = (index << 1);
                return ((data[dataIndex] << 8) & 0177400) | (data[dataIndex + 1] & 0377);
            }

            @Override
            protected void writeInternal(int index, int value) {
                // TODO Auto-generated method stub

            }

            @Override
            protected boolean isMarkerPositionInternal(int index) {
                return (index == 0);
            }

            @Override
            protected boolean isCrcPositionInternal(int index) {
                return ((index << 1) == CRC_VALUE_INDEX);
            }
        }

        /**
         * Floppy drive track sector data class.
         */
        class FloppyDriveTrackSectorData extends FloppyDriveTrackArea
                implements OnFloppyDriveTrackChanged {

            // Sector data address marker length (in words)
            private final static int SECTOR_DATA_AM_LENGTH = 2;
            // Sector CRC value index (in words)
            private final static int SECTOR_CRC_INDEX = SECTOR_DATA_AM_LENGTH +
                    (BYTES_PER_SECTOR >> 1);

            private final short[] dataMarker = {
                    (short) 0xa1a1, (short) 0xa1fb, // DATA AM
            };

            private final int sectorIndex;

            private short dataMarkerCrcValue;
            private short crcValue;

            FloppyDriveTrackSectorData(int position, int sectorNumber) {
                super(position);
                sectorIndex = sectorNumber - 1;
                dataMarkerCrcValue = Crc16.INIT_VALUE;
                for (short dataMarkerWord : dataMarker) {
                    dataMarkerCrcValue = Crc16.calculate(dataMarkerCrcValue,
                            (byte) (dataMarkerWord >> 8));
                    dataMarkerCrcValue = Crc16.calculate(dataMarkerCrcValue,
                            (byte) (dataMarkerWord));
                }
            }

            @Override
            public void onFloppyDriveTrackChanged(int trackNumber, FloppyDriveSide trackSide) {
                // Update sector CRC value
                int imageBufferStartIndex = getImageBufferStartIndex(trackNumber, trackSide);
                ByteBuffer diskImageBuffer = getMountedDiskImageBuffer();
                crcValue = dataMarkerCrcValue;
                for (int dataIndex = imageBufferStartIndex; dataIndex < (imageBufferStartIndex
                        + BYTES_PER_SECTOR); dataIndex++) {
                    crcValue = Crc16.calculate(crcValue, diskImageBuffer.get(dataIndex));
                }
            }

            @Override
            protected int getLength() {
                return (SECTOR_CRC_INDEX + 1); // DATA AM + Data + 2 bytes of CRC
            }

            @Override
            protected int readInternal(int index) {
                short data;
                if (index < SECTOR_DATA_AM_LENGTH) {
                    data = dataMarker[index];
                } else if (index < SECTOR_CRC_INDEX) {
                    int dataIndex = (index - SECTOR_DATA_AM_LENGTH) << 1;
                    int imageBufferDataIndex = getImageBufferStartIndex(getCurrentTrackNumber(),
                            getCurrentTrackSide()) + dataIndex;
                    ByteBuffer diskImageBuffer = getMountedDiskImageBuffer();
                    data = diskImageBuffer.getShort(imageBufferDataIndex);
                } else {
                    data = crcValue;
                }
                return (data & 0177777);
            }

            /**
             * Get this sector start index in mapped floppy drive image file.
             * @return this sector start index in mapped floppy drive image file (in bytes).
             */
            private int getImageBufferStartIndex(int trackNumber, FloppyDriveSide side) {
                return BYTES_PER_SECTOR * (SECTORS_PER_TRACK * (trackNumber * 2 + side.ordinal())
                        + sectorIndex);
            }

            @Override
            protected void writeInternal(int index, int value) {
                // TODO Auto-generated method stub

            }

            @Override
            protected boolean isMarkerPositionInternal(int index) {
                return (index == 0);
            }

            @Override
            protected boolean isCrcPositionInternal(int index) {
                return (index == SECTOR_CRC_INDEX);
            }
        }

        FloppyDrive(FloppyDriveIdentifier driveIdentifier) {
            this.mountedDiskImageBuffer = ByteBuffer.allocate(BYTES_PER_DISK);
            this.driveIdentifier = driveIdentifier;
            setCurrentTrack(0, FloppyDriveSide.DOWN);
            initializeCurrentTrackData();
        }

        private void initializeCurrentTrackData() {
            int position = 0;
            // GAP1
            position = initializeCurrentTrackData(position, new FloppyDriveTrackSequence(position,
                    FloppyDriveTrackSequence.SEQ_GAP, FloppyDriveTrackSequence.SEQ_GAP1_LENGTH));
            // Sectors
            for (int sectorNumber = 1; sectorNumber <= SECTORS_PER_TRACK; sectorNumber++) {
                // Header sync
                position = initializeCurrentTrackData(position, new FloppyDriveTrackSequence(position,
                        FloppyDriveTrackSequence.SEQ_SYNC, FloppyDriveTrackSequence.SEQ_SYNC_LENGTH));
                // Sector header - IDAM + descriptor + CRC
                FloppyDriveTrackSectorHeader sectorHeader = new FloppyDriveTrackSectorHeader(
                        position, sectorNumber);
                position = initializeCurrentTrackData(position, sectorHeader);
                trackChangedListeners[(sectorNumber - 1) * 2] = sectorHeader;
                // GAP2
                position = initializeCurrentTrackData(position, new FloppyDriveTrackSequence(position,
                        FloppyDriveTrackSequence.SEQ_GAP, FloppyDriveTrackSequence.SEQ_GAP2_LENGTH));
                // Data sync
                position = initializeCurrentTrackData(position, new FloppyDriveTrackSequence(position,
                        FloppyDriveTrackSequence.SEQ_SYNC, FloppyDriveTrackSequence.SEQ_SYNC_LENGTH));
                // Sector data - DATA AM + Data + CRC
                FloppyDriveTrackSectorData sectorData = new FloppyDriveTrackSectorData(
                        position, sectorNumber);
                position = initializeCurrentTrackData(position, sectorData);
                trackChangedListeners[(sectorNumber - 1) * 2 + 1] = sectorData;
                // GAP3 (19 words for 512 bytes per sector) or GAP4B (to end of track)
                position = initializeCurrentTrackData(position, new FloppyDriveTrackSequence(position,
                        FloppyDriveTrackSequence.SEQ_GAP, (sectorNumber < SECTORS_PER_TRACK)
                            ? FloppyDriveTrackSequence.SEQ_GAP3_LENGTH : WORDS_PER_TRACK - position));
            }
        }

        private int initializeCurrentTrackData(int position, FloppyDriveTrackArea area) {
            int dataIndex = position;
            while (dataIndex < position + area.getLength()) {
                currentTrackData[dataIndex++] = area;
            }
            return dataIndex;
        }

        /**
         * Read current track data at given position.
         * @param position track data position (in words from track start)
         * @return read data word
         */
        int readCurrentTrackData(int position) {
            return currentTrackData[position].read(position);
        }
        /**
         * Write data to current track at given position.
         * @param position track data position (in words from track start)
         * @param value data word to write
         */
        void writeCurrentTrackData(int position, int value) {
            currentTrackData[position].write(position, value);
        }

        /**
         * Check given position of current track data is marker start position.
         * @return <code>true</code> if given position is marker start position,
         * <code>false</code> otherwise
         */
        boolean isCurrentTrackDataMarkerPosition(int position) {
            return currentTrackData[position].isMarkerPosition(position);
        }

        /**
         * Check given position of current track data is CRC position.
         * @return <code>true</code> if given position is CRC position,
         * <code>false</code> otherwise
         */
        boolean isCurrentTrackDataCrcPosition(int position) {
            return currentTrackData[position].isCrcPosition(position);
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
                d(TAG, "set track: " + trackNumber + ", side: " + trackSide);
            }
            this.currentTrackNumber = trackNumber;
            this.currentTrackSide = trackSide;
            // OnFloppyDriveTrackChanged listeners notify if disk image mounted
            if (isDiskImageMounted()) {
                for (OnFloppyDriveTrackChanged listener : trackChangedListeners) {
                    listener.onFloppyDriveTrackChanged(trackNumber, trackSide);
                }
            }
        }

        /**
         * Get next current track number after single step to center or to edge.
         * @param isStepToCenter <code>true</code> is track changed with single step to center,
         * <code>false</code> if track changed with single step to edge
         * @return next track number in range [0, TRACKS_PER_DISK - 1]
         */
        int getNextTrackNumber(boolean isStepToCenter) {
            return Math.max(Math.min((getCurrentTrackNumber() + (isStepToCenter ? 1 : -1)),
                    TRACKS_PER_DISK - 1), 0);
        }

        boolean isDiskIndexHoleActive(long cpuTime) {
            return (isDiskImageMounted() && (cpuTime % clockTicksPerTrack) < clockTicksPerIndexHole);
        }

        /**
         * Get mounted disk image file URI.
         * @return mounted disk image file URI or <code>null</code> if no disk image mounted
         */
        String getMountedDiskImageFileUri() {
            return mountedDiskImageFileUri;
        }

        /**
         * Check is disk image mounted to this floppy drive or not.
         * @return <code>true</code> if disk image mounted to this floppy drive,
         * <code>false</code> if not mounted
         */
        boolean isDiskImageMounted() {
            return (getMountedDiskImageFileUri() != null);
        }

        /**
         * Check is mounted disk image is read only.
         * @return <code>true</code> if mounted disk image is read only,
         * <code>false</code> if not mounted or mounted in read/write mode
         */
        boolean isMountedDiskImageReadOnly() {
            return (isDiskImageMounted() && isMountedDiskImageReadOnly);
        }

        /**
         * Mount disk image to this drive.
         * @param diskImageFileUri Disk image file URI to mount
         * @param isReadOnly <code>true</code> to mount disk image as read only,
         * <code>false</code> to mount disk image in read/write mode
         * @throws Exception in case of mounting error
         */
        void mountDiskImage(String diskImageFileUri, boolean isReadOnly) throws Exception {
            File diskImageFile = new File(new URI(diskImageFileUri));
            // Check disk image size
            if (diskImageFile.length() > BYTES_PER_DISK) {
                throw new IllegalArgumentException("Invalid disk image size: " +
                            diskImageFile.length());
            }
            if (isDiskImageMounted()) {
                unmountDiskImage();
            }
            isMountedDiskImageReadOnly = isReadOnly;
            mountedDiskImageFile = new RandomAccessFile(diskImageFile, isReadOnly ? "r" : "rw");
            FileChannel mountedDiskImageFileChannel = mountedDiskImageFile.getChannel();
            mountedDiskImageBuffer.clear();
            mountedDiskImageFileChannel.read(mountedDiskImageBuffer);
            mountedDiskImageBuffer.flip();
            mountedDiskImageBuffer.limit(BYTES_PER_DISK);
            mountedDiskImageFileChannel.close();
            this.mountedDiskImageFileUri = diskImageFileUri;
            // Reload track data
            setCurrentTrack(getCurrentTrackNumber(), getCurrentTrackSide());
        }

        /**
         * Unmount current mounted disk image.
         * @throws Exception in case of unmounting error
         */
        void unmountDiskImage() throws Exception {
            mountedDiskImageFileUri = null;
            mountedDiskImageFile.close();
        }

        ByteBuffer getMountedDiskImageBuffer() {
            return mountedDiskImageBuffer;
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

    protected static void d(String tag, String message) {
        System.out.println("FDD: " + message);
    }

    protected FloppyDrive getFloppyDrive(FloppyDriveIdentifier drive) {
        return (drive != null) ? floppyDrives[drive.ordinal()] : null;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public synchronized void init(long cpuTime) {
        writeControlRegister(cpuTime, 0);
    }

    @Override
    public void timer(long cpuTime) {
        // Do nothing
    }

    @Override
    public synchronized void saveState(Bundle outState) {
        outState.putSerializable(STATE_SELECTED_FLOPPY_DRIVE, getSelectedFloppyDriveIdentifier());
        outState.putBoolean(STATE_SYNCHRONOUS_READ, isSynchronousReadState());
        outState.putBoolean(STATE_MARKER_FOUND, isMarkerFound());
        outState.putBoolean(STATE_DATA_READY, isDataReady());
        outState.putInt(STATE_DATA_READY_READ_POSITION, getDataReadyReadPosition());
        outState.putBoolean(STATE_CRC_CORRECT, isCrcCorrect());
        outState.putLong(STATE_LAST_DATA_REGISTER_READ_TIME, getLastDataRegisterReadCpuTime());
        outState.putLong(STATE_LAST_ACCESS_TIME, getLastAccessCpuTime());
        outState.putBoolean(STATE_MOTOR_STARTED, isMotorStarted());
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            FloppyDrive drive = getFloppyDrive(driveIdentifier);
            outState.putString(getFloppyDriveStateKey(STATE_DRIVE_IMAGE_FILE_URI, driveIdentifier),
                    drive.getMountedDiskImageFileUri());
            outState.putBoolean(getFloppyDriveStateKey(STATE_DRIVE_IMAGE_READ_ONLY, driveIdentifier),
                    drive.isMountedDiskImageReadOnly());
            outState.putInt(getFloppyDriveStateKey(STATE_DRIVE_TRACK_NUMBER, driveIdentifier),
                    drive.getCurrentTrackNumber());
            outState.putSerializable(getFloppyDriveStateKey(STATE_DRIVE_TRACK_SIDE, driveIdentifier),
                    drive.getCurrentTrackSide());
        }
    }

    @Override
    public synchronized void restoreState(Bundle inState) {
        selectFloppyDrive((FloppyDriveIdentifier) inState.getSerializable(STATE_SELECTED_FLOPPY_DRIVE));
        setSynchronousReadState(inState.getBoolean(STATE_SYNCHRONOUS_READ));
        setMarkerFound(inState.getBoolean(STATE_MARKER_FOUND));
        setDataReady(inState.getBoolean(STATE_DATA_READY));
        setDataReadyReadPosition(inState.getInt(STATE_DATA_READY_READ_POSITION));
        setCrcCorrect(inState.getBoolean(STATE_CRC_CORRECT));
        setLastDataRegisterReadCpuTime(inState.getLong(STATE_LAST_DATA_REGISTER_READ_TIME));
        setLastAccessCpuTime(inState.getLong(STATE_LAST_ACCESS_TIME));
        setMotorStarted(inState.getBoolean(STATE_MOTOR_STARTED));
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            FloppyDrive drive = getFloppyDrive(driveIdentifier);
            int driveTrackNumber = inState.getInt(getFloppyDriveStateKey(
                    STATE_DRIVE_TRACK_NUMBER, driveIdentifier));
            FloppyDriveSide driveTrackSide = (FloppyDriveSide) inState.getSerializable(
                    getFloppyDriveStateKey(STATE_DRIVE_TRACK_SIDE, driveIdentifier));
            drive.setCurrentTrack(driveTrackNumber, driveTrackSide);
            String diskImageFileUri = inState.getString(getFloppyDriveStateKey(
                    STATE_DRIVE_IMAGE_FILE_URI, driveIdentifier));
            if (diskImageFileUri != null) {
                try {
                    drive.mountDiskImage(diskImageFileUri, inState.getBoolean(
                            getFloppyDriveStateKey(STATE_DRIVE_IMAGE_READ_ONLY,driveIdentifier)));
                } catch (Exception e) {
                    Log.e(TAG, "can't remount disk file image: " + diskImageFileUri, e);
                    try {
                        drive.unmountDiskImage();
                    } catch (Exception e1) {
                    }
                }
            }
        }
    }

    private static String getFloppyDriveStateKey(String stateKey,
            FloppyDriveIdentifier driveIdentifier) {
        return stateKey + ":" + driveIdentifier.name();
    }

    @Override
    public synchronized int read(long cpuTime, int address) {
        setLastAccessCpuTime(cpuTime);
        return (address == CONTROL_REGISTER_ADDRESS)
                ? readControlRegister(cpuTime)
                : readDataRegister(cpuTime);
    }

    @Override
    public synchronized boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        if (isDebugEnabled) {
            d(TAG, "write: " + Integer.toOctalString(address) +
                    ", value: " + Integer.toOctalString(value) + ", isByteMode: " + isByteMode);
        }
        setLastAccessCpuTime(cpuTime);
        if (!isByteMode) {
            if (address == CONTROL_REGISTER_ADDRESS) {
                writeControlRegister(cpuTime, value);
            } else {
                writeDataRegister(cpuTime, value);
            }
        }
        return true;
    }

    protected int readControlRegister(long cpuTime) {
        int value = 0;
        FloppyDrive drive = getSelectedFloppyDrive();
        if (drive != null) {
            // Track 0 flag
            if (drive.getCurrentTrackNumber() == 0) {
                value |= TR0;
            }
            // Floppy disk write protect flag
            if (drive.isMountedDiskImageReadOnly()) {
                value |= WRP;
            }
            // Floppy disk index hole activity flag
            if (drive.isDiskIndexHoleActive(cpuTime)) {
                value |= IND;
            }
            int trackPosition = getTrackPosition(cpuTime);
            int lastDataRegisterReadPosition = getTrackPosition(getLastDataRegisterReadCpuTime());
            int numReadDataWords = getNumTrackDataWords(lastDataRegisterReadPosition, trackPosition);
            // Check data is ready
            if (isMarkerFound() && !isDataReady()) {
                setDataReady(numReadDataWords > 0);
                if (isDataReady()) {
                    setDataReadyReadPosition((lastDataRegisterReadPosition + 1) % WORDS_PER_TRACK);
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
                            d(TAG, "marker found, position: " + trackPosition);
                        }
                        setMarkerFound(true);
                        setDataReady(true);
                        setDataReadyReadPosition(trackPosition);
                    }
                } else {
                    // Data marker found, check flags of synchronous read state
                    if (numReadDataWords > 1) {
                        // Data ready flag set, synchronous read completed
                        setSynchronousReadState(false);
                        // Check was CRC read as last data word
                        setCrcCorrect(drive.isCurrentTrackDataCrcPosition(getDataReadyReadPosition()));
                        if (isDebugEnabled) {
                            d(TAG, "synchronous read completed, position: " + trackPosition +
                                    ", dataReadyReadPosition: " + getDataReadyReadPosition() +
                                    ", readDataWords: " + numReadDataWords + ", isCrcCorrect: " + isCrcCorrect());
                        }

                    }
                }
            }
            if (isDataReady()) {
                value |= TR;
            }
            if (isCrcCorrect()) {
                value |= CRC;
            }
        }
        return value;
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
            // Check is track number or side changed
            FloppyDriveSide trackSide = (value & HS) != 0 ? FloppyDriveSide.UP : FloppyDriveSide.DOWN;
            int trackNumber = (value & ST) != 0 ? drive.getNextTrackNumber((value & DIR) != 0)
                    : drive.getCurrentTrackNumber();
            if (trackSide != drive.getCurrentTrackSide()
                    || trackNumber != drive.getCurrentTrackNumber()) {
                // Track changed, cancel synchronous read mode
                cancelSynchronousRead();
                // Set floppy drive track number and side
                drive.setCurrentTrack(trackNumber, trackSide);
            }
            // Process GOR flag
            if ((value & GOR) != 0) {
                startSynchronousRead();
            }
            // TODO Process WM flag
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

    private boolean isCrcCorrect() {
        return isCrcCorrect;
    }

    private void setCrcCorrect(boolean isCrcCorrect) {
        this.isCrcCorrect = isCrcCorrect;
    }

    private long getLastDataRegisterReadCpuTime() {
        return lastDataRegisterReadCpuTime;
    }

    private void setLastDataRegisterReadCpuTime(long lastDataRegisterReadCpuTime) {
        this.lastDataRegisterReadCpuTime = lastDataRegisterReadCpuTime;
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
        cancelSynchronousRead();
        setSynchronousReadState(true);
    }

    private void cancelSynchronousRead() {
        setSynchronousReadState(false);
        setMarkerFound(false);
        setDataReady(false);
        setCrcCorrect(false);
    }

    protected int readDataRegister(long cpuTime) {
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

    /**
     * Get track position for given CPU time.
     * @param cpuTime CPU time to get track position
     * @return track position (in words)
     */
    private int getTrackPosition(long cpuTime) {
        return (int) ((cpuTime / clockTicksPerWord) % WORDS_PER_TRACK);
    }

    protected void writeDataRegister(long cpuTime, int value) {
        // TODO
    }

    /**
     * Mount floppy drive disk image to given drive.
     * @param diskImageFileUri floppy drive disk image file URI string
     * @param drive {@link FloppyDriveIdentifier} of drive to mount disk image
     * @param isReadOnly <code>true</code> to mount disk image read only, <code>false</code>
     * to mount disk image read/write
     * @throws Exception in case of disk image mounting error
     */
    public synchronized void mountDiskImage(String diskImageFileUri, FloppyDriveIdentifier drive,
            boolean isReadOnly) throws Exception {
        getFloppyDrive(drive).mountDiskImage(diskImageFileUri, isReadOnly);
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
                Log.e(TAG, "Error while unmounting disk image from drive " + drive, e);
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
    protected FloppyDriveIdentifier getSelectedFloppyDriveIdentifier() {
        return selectedFloppyDriveIdentifier;
    }

    /**
     * Select floppy drive.
     * @param floppyDriveIdentifier selected floppy drive identifier (or <code>null</code>
     * to unselect all floppy drives)
     */
    protected void selectFloppyDrive(FloppyDriveIdentifier floppyDriveIdentifier) {
        if (isDebugEnabled) {
            d(TAG, "selected drive: " + floppyDriveIdentifier);
        }
        if (selectedFloppyDriveIdentifier != floppyDriveIdentifier) {
            // Cancel synchronous data read
            cancelSynchronousRead();
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
            d(TAG, "set floppy drives motor state: " + (isStarted ? "STARTED" : "STOPPED"));
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
     * Get mounted floppy drive image URI.
     * @param driveIdentifier {@link FloppyDriveIdentifier} of drive to get mounted disk image URI
     * @return mounted floppy drive image URI or <code>null</code> if no floppy drive image mounted
     */
    public synchronized String getFloppyDriveImageUri(FloppyDriveIdentifier driveIdentifier) {
        return getFloppyDrive(driveIdentifier).getMountedDiskImageFileUri();
    }
}
