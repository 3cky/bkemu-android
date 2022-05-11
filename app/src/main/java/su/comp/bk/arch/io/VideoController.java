/*
 * Created: 23.04.2012
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
package su.comp.bk.arch.io;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.memory.Memory;
import su.comp.bk.state.State;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * BK-0010 video output controller (К1801ВП1-037).
 */
public class VideoController implements Device, Computer.UptimeListener {

    /** Scroll/mode register address */
    public final static int CONTROL_REGISTER_ADDRESS = 0177664;

    private final static int[] ADDRESSES = { CONTROL_REGISTER_ADDRESS };

    // Scroll/mode register: scroll base value in normal mode
    private final static int SCROLL_BASE_VALUE = 0330;
    // Scroll/mode register: extended memory mode disable bit
    // (0 - extended memory mode, 1 - normal mode)
    private final static int EXTMEM_CONTROL_BIT = 01000;

    // Screen height (in lines) in normal mode
    private final static int SCREEN_HEIGHT_NORMAL = 256;
    // Screen height (in lines) in extended memory mode
    private final static int SCREEN_HEIGHT_EXTMEM = 64;
    // Screen scan line length (in words)
    private final static int SCREEN_SCANLINE_LENGTH = 040;
    // Screen data length (in words)
    public final static int SCREEN_DATA_LENGTH = SCREEN_HEIGHT_NORMAL * SCREEN_SCANLINE_LENGTH;
    // VideoRAM bits per screen pixel in black and white mode
    private final static int SCREEN_BPP_BW = 1;
    // VideoRAM bits per screen pixel in color mode
    private final static int SCREEN_BPP_COLOR = 2;
    // Screen pixels per videoRAM word in black and white mode
    private final static int SCREEN_PPW_BW = (Short.SIZE / SCREEN_BPP_BW);
    // Screen pixels per videoRAM word in color mode
    private final static int SCREEN_PPW_COLOR = (Short.SIZE / SCREEN_BPP_COLOR);

    // Video buffer pixels per screen pixel in black and white mode
    private final static int PIXELS_PER_SCREEN_PIXEL_BW = 1;
    // Video buffer pixels per screen pixel in color mode
    private final static int PIXELS_PER_SCREEN_PIXEL_COLOR = 2;

    // VideoRAM word pixel value mask for black and white mode
    private final static int PIXEL_MASK_BW = 1;
    // VideoRAM word pixel value mask for color mode
    private final static int PIXEL_MASK_COLOR = 3;

    // Pixel palette in black and white mode
    private final static int[] PIXEL_PALETTE_BW = { Color.BLACK, Color.WHITE };

    // Pixel palettes in color mode
    private final static int[][] PIXEL_PALETTES_COLOR = {
        { Color.BLACK, Color.BLUE, Color.GREEN, Color.RED },
        { Color.BLACK, Color.YELLOW, Color.MAGENTA, Color.RED },
        { Color.BLACK, Color.CYAN, Color.BLUE, Color.MAGENTA },
        { Color.BLACK, Color.GREEN, Color.CYAN, Color.YELLOW },
        { Color.BLACK, Color.MAGENTA, Color.CYAN, Color.WHITE },
        { Color.BLACK, Color.WHITE, Color.WHITE, Color.WHITE },
        { Color.BLACK, 0xffc00000, 0xff8e0000, Color.RED },
        { Color.BLACK, 0xffc0ff00, 0xff8eff00, Color.YELLOW },
        { Color.BLACK, 0xffc000ff, 0xff8e00ff, Color.MAGENTA },
        { Color.BLACK, 0xff8eff00, 0xff8e00ff, 0xff8e0000 },
        { Color.BLACK, 0xffc0ff00, 0xffc000ff, 0xffc00000 },
        { Color.BLACK, Color.CYAN, Color.YELLOW, Color.RED },
        { Color.BLACK, Color.RED, Color.GREEN, Color.CYAN },
        { Color.BLACK, Color.CYAN, Color.YELLOW, Color.WHITE },
        { Color.BLACK, Color.YELLOW, Color.GREEN, Color.WHITE },
        { Color.BLACK, Color.CYAN, Color.GREEN, Color.WHITE }
    };

    // Pixel palettes in grayscale mode
    private final static int[][] PIXEL_PALETTES_GRAYSCALE;

    static {
        // Create grayscale pixel palettes
        int numGrays = 4;
        int numPalettes = 16;
        PIXEL_PALETTES_GRAYSCALE = new int[numPalettes][numGrays];
        for (int paletteIndex = 0; paletteIndex < numPalettes; paletteIndex++) {
            for (int grayIndex = 0; grayIndex < numGrays; grayIndex++) {
                int grayValue;
                if (paletteIndex == 0) {
                    // BK0010 palette is directly translated to 4 levels of gray
                    grayValue = 255 * grayIndex / (numGrays - 1);
                } else {
                    // BK0011 palettes are mixed by PAL luma coding function
                    int colorValue = PIXEL_PALETTES_COLOR[paletteIndex][grayIndex];
                    int redValue = Color.red(colorValue);
                    int greenValue = Color.green(colorValue);
                    int blueValue = Color.blue(colorValue);
                    grayValue = (int)(0.299 * redValue + 0.587 * greenValue + 0.114 * blueValue);
                }
                PIXEL_PALETTES_GRAYSCALE[paletteIndex][grayIndex] =
                        Color.rgb(grayValue, grayValue, grayValue);
            }
        }
    }

    // Screen lines pixel data
    private short[] linesPixelData = new short[SCREEN_DATA_LENGTH];

    // Current color palette index
    private int colorPaletteIndex = 0;
    // Screen line color palette indexes
    private final int[] lineColorPaletteIndexes = new int[FRAME_LINES_VISIBLE];

    // VideoRAM data byte to corresponding pixels lookup table (16 palettes * 8 pixels * 256 byte values)
    private final int[] videoDataToPixelsTable = new int[16 * 8 * 256];

    // State save/restore: state variable prefix
    private static final String STATE_PREFIX = "VideoController";

    // State save/restore: display mode value
    public static final String STATE_DISPLAY_MODE = STATE_PREFIX + "#display_mode";

    // State save/restore: scroll register value
    public static final String STATE_SCROLL_REGISTER = STATE_PREFIX + "#scroll_reg";

    // State save/restore: palette index value
    public static final String STATE_PALETTE_INDEX = STATE_PREFIX + "#palette_index";

    // State save/restore: current displayed frame number
    public static final String STATE_CURRENT_FRAME = STATE_PREFIX + "#frame_num";

    // State save/restore: current displayed line number
    public static final String STATE_CURRENT_LINE = STATE_PREFIX + "#line_num";

    // Available display modes
    public enum DisplayMode {
        BW, // black and white
        GRAYSCALE, // four levels of gray
        COLOR; // four colors

        /**
         * Get next display mode (in circular order)
         * @return next display mode
         */
        public DisplayMode getNext() {
            DisplayMode[] displayModes = values();
            return displayModes[(this.ordinal() + 1) % displayModes.length];
        }
    }

    // Display output mode
    private DisplayMode displayMode;

    // Scroll register value
    private int scrollRegister;

    // Frame scroll shift value
    private int frameScrollShift;

    // Video memory reference
    private final Memory videoMemory;

    // Video buffer width (in pixels)
    public final static int VIDEO_BUFFER_WIDTH = 512;
    // Video buffer height (in lines)
    public final static int VIDEO_BUFFER_HEIGHT = SCREEN_HEIGHT_NORMAL;
    // Video buffer pixels per videoRAM word
    private final static int VIDEO_BUFFER_PIXELS_PER_WORD = Short.SIZE;
    // Video buffer bitmap object
    private final Bitmap videoBuffer;

    // Total lines per frame (including vertical sync)
    private final static int FRAME_LINES_TOTAL = 320;
    // Visible lines per frame
    private final static int FRAME_LINES_VISIBLE = SCREEN_HEIGHT_NORMAL;
    // Frame horizontal sync period (64 uS, in nanoseconds)
    private final static long FRAME_SYNC_PERIOD_HORIZONTAL = 64 * 1000L;
    // Frame vertical sync line number
    private final static long FRAME_SYNC_LINE_VERTICAL = FRAME_LINES_VISIBLE + 1;
    // Current displayed line number
    private long currentLine;
    // Current displayed frame number
    private long currentFrame;
    // List of frame horizontal/vertical sync listeners
    private final List<FrameSyncListener> frameSyncListeners = new ArrayList<>();
    // Last displayed frame data
    private final FrameData lastDisplayedFrameData = new FrameData();
    // Last rendered frame data
    private final FrameData lastRenderedFrameData = new FrameData();

    /**
     * Frame sync events listener interface.
     */
    public interface FrameSyncListener {
        void verticalSync(long frameNumber);
    }

    /**
     * Frame data (pixel data, palette data, etc).
     */
    static class FrameData {
        private final short[] pixelData;
        private final int[] linePaletteIndexes;
        private boolean isFullScreenMode;

        FrameData() {
            pixelData = new short[SCREEN_DATA_LENGTH];
            linePaletteIndexes = new int[FRAME_LINES_VISIBLE];
        }

        short[] getPixelData() {
            return pixelData;
        }

        void setPixelData(short[] pixelData) {
            System.arraycopy(pixelData, 0, this.pixelData, 0, pixelData.length);
        }

        int getPaletteIndex(int lineNum) {
            return linePaletteIndexes[lineNum];
        }

        void setPaletteIndex(int lineNum, int paletteIndex) {
            this.linePaletteIndexes[lineNum] = paletteIndex;
        }

        void setLinePaletteIndexes(int[] linePaletteIndexes) {
            System.arraycopy(linePaletteIndexes, 0,
                    this.linePaletteIndexes, 0,
                    this.linePaletteIndexes.length);
        }

        int[] getLinePaletteIndexes() {
            return this.linePaletteIndexes;
        }

        boolean isFullScreenMode() {
            return isFullScreenMode;
        }

        void setFullScreenMode(boolean isFullScreenMode) {
            this.isFullScreenMode = isFullScreenMode;
        }

        void init(short[] pixelData, int[] linePaletteIndexes, boolean isFullScreenMode) {
            setPixelData(pixelData);
            setLinePaletteIndexes(linePaletteIndexes);
            setFullScreenMode(isFullScreenMode);
        }

        void copyFrom(FrameData frameData) {
            init(frameData.getPixelData(), frameData.getLinePaletteIndexes(),
                    frameData.isFullScreenMode());
        }
    }

    public VideoController(Memory videoMemory) {
        this.videoMemory = videoMemory;
        this.videoBuffer = Bitmap.createBitmap(VIDEO_BUFFER_WIDTH, VIDEO_BUFFER_HEIGHT,
                Bitmap.Config.ARGB_8888);
        writeScrollRegister(EXTMEM_CONTROL_BIT | SCROLL_BASE_VALUE);
        setDisplayMode(DisplayMode.COLOR);
    }

    public void addFrameSyncListener(FrameSyncListener frameSyncListener) {
        frameSyncListeners.add(frameSyncListener);
    }

    public List<FrameSyncListener> getFrameSyncListeners() {
        return frameSyncListeners;
    }

    public int getColorPaletteIndex() {
        return colorPaletteIndex;
    }

    void setColorPaletteIndex(int paletteIndex) {
        this.colorPaletteIndex = paletteIndex;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public synchronized void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
        updateVideoDataToPixelsTable(displayMode);
    }

    private void updateVideoDataToPixelsTable(DisplayMode displayMode) {
        synchronized (videoDataToPixelsTable) {
            int pixelTabIdx = 0;
            for (int paletteIndex = 0; paletteIndex < PIXEL_PALETTES_COLOR.length; paletteIndex++) {
                boolean isBwMode = (displayMode == DisplayMode.BW);
                int bitsPerPixel = !isBwMode ? SCREEN_BPP_COLOR : SCREEN_BPP_BW;
                int[] pixelPalette;
                switch (displayMode) {
                    case BW:
                        pixelPalette = PIXEL_PALETTE_BW;
                        break;
                    case GRAYSCALE:
                        pixelPalette = PIXEL_PALETTES_GRAYSCALE[paletteIndex];
                        break;
                    default:
                        pixelPalette = PIXEL_PALETTES_COLOR[paletteIndex];
                }
                int pixelMask = !isBwMode ? PIXEL_MASK_COLOR : PIXEL_MASK_BW;
                int pixelsPerByte = (!isBwMode ? SCREEN_PPW_COLOR : SCREEN_PPW_BW) / 2;
                int pixelsPerScreenPixel = !isBwMode ? PIXELS_PER_SCREEN_PIXEL_COLOR
                        : PIXELS_PER_SCREEN_PIXEL_BW;
                for (int videoDataByte = 0; videoDataByte < 256; videoDataByte++) {
                    for (int videoDataBytePixelIndex = 0; videoDataBytePixelIndex < pixelsPerByte;
                         videoDataBytePixelIndex++) {
                        int pixelPaletteIndex = (videoDataByte >>> (videoDataBytePixelIndex
                                * bitsPerPixel)) & pixelMask;
                        int pixelColor = pixelPalette[pixelPaletteIndex];
                        for (int pixelIndex = 0; pixelIndex < pixelsPerScreenPixel; pixelIndex++) {
                            videoDataToPixelsTable[pixelTabIdx++] = pixelColor;
                        }
                    }
                }
            }
        }
    }

    public Bitmap renderVideoBuffer() {
        synchronized (lastDisplayedFrameData) {
            lastRenderedFrameData.copyFrom(lastDisplayedFrameData);
        }
        short[] videoData = lastRenderedFrameData.getPixelData();
        int numLines = lastRenderedFrameData.isFullScreenMode() ? SCREEN_HEIGHT_NORMAL
                : SCREEN_HEIGHT_EXTMEM;
        int videoDataIdx = 0;
        int videoBufferX, videoBufferY;
        synchronized (videoBuffer) {
            videoBuffer.eraseColor(Color.BLACK);
            synchronized (videoDataToPixelsTable) {
                for (int lineIdx = 0; lineIdx < numLines; lineIdx++) {
                    for (int lineWordIdx = 0; lineWordIdx < SCREEN_SCANLINE_LENGTH; lineWordIdx++) {
                        int videoDataWord = videoData[videoDataIdx];
                        if (videoDataWord != 0) {
                            int paletteOffset = lastDisplayedFrameData.getPaletteIndex(lineIdx)
                                    * (videoDataToPixelsTable.length >>> 4);
                            videoBufferX = (videoDataIdx % SCREEN_SCANLINE_LENGTH)
                                    * VIDEO_BUFFER_PIXELS_PER_WORD;
                            videoBufferY = lineIdx;
                            videoBuffer.setPixels(videoDataToPixelsTable,
                                    paletteOffset + ((videoDataWord & 0377) << 3),
                                    VIDEO_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                            videoBufferX += 8;
                            videoBuffer.setPixels(videoDataToPixelsTable,
                                    paletteOffset + (((videoDataWord >> 8) & 0377) << 3),
                                    VIDEO_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                        }
                        videoDataIdx++;
                    }
                }
            }
        }
        return videoBuffer;
    }

    public void drawLastRenderedVideoBuffer(Bitmap dest) {
        Canvas destCanvas = new Canvas(dest);
        Rect destRect = new Rect(0, 0, dest.getWidth(), dest.getHeight());
        synchronized (videoBuffer) {
            destCanvas.drawBitmap(videoBuffer, null, destRect, null);
        }
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime, boolean isHardwareReset) {
        // Do nothing
    }

    @Override
    public void uptimeUpdated(long uptime) {
        // Get current displayed screen line number since emulator start (numbered from 0)
        long line = uptime / FRAME_SYNC_PERIOD_HORIZONTAL;
        if (line > currentLine) {
            // New screen line started
            currentLine = line;
            // Get screen line number inside displayed frame (numbered from 0)
            int currentLineFrameLine = (int) (currentLine % FRAME_LINES_TOTAL);
            // Check for HSync
            if (currentLineFrameLine < FRAME_LINES_VISIBLE) {
                // HSync
                // Store pixel data for displayed screen line
                int videoDataIdx = ((currentLineFrameLine + frameScrollShift)
                        * SCREEN_SCANLINE_LENGTH) % SCREEN_DATA_LENGTH;
                int pixelDataIdx = currentLineFrameLine * SCREEN_SCANLINE_LENGTH;
                videoMemory.getData(linesPixelData, videoDataIdx, pixelDataIdx,
                        SCREEN_SCANLINE_LENGTH);
                // Store color palette index for displayed screen line
                lineColorPaletteIndexes[currentLineFrameLine] = colorPaletteIndex;
            } else {
                // Check for VSync
                // Get displayed frame number (numbered from 0)
                long currentLineFrame = currentLine / FRAME_LINES_TOTAL;
                if (currentLineFrameLine >= FRAME_SYNC_LINE_VERTICAL
                        && currentLineFrame >= currentFrame) {
                    // VSync
                    storeLastFrameVideoData();
                    notifyFrameSyncListenersVerticalSync();
                    currentFrame = currentLineFrame + 1;
                    // Frame scroll shift value is updated on VSync
                    frameScrollShift = (readScrollRegister() - SCROLL_BASE_VALUE) & 0377;
                }
            }
        }
    }

    private void storeLastFrameVideoData() {
        synchronized (lastDisplayedFrameData) {
            lastDisplayedFrameData.init(linesPixelData, lineColorPaletteIndexes,
                    isFullScreenMode());
        }
    }

    private void notifyFrameSyncListenersVerticalSync() {
        for (FrameSyncListener l : frameSyncListeners) {
            l.verticalSync(currentFrame);
        }
    }

    @Override
    public void saveState(State outState) {
        outState.putLong(STATE_CURRENT_FRAME, currentFrame);
        outState.putLong(STATE_CURRENT_LINE, currentLine);
        outState.putInt(STATE_SCROLL_REGISTER, scrollRegister);
        outState.putInt(STATE_PALETTE_INDEX, getColorPaletteIndex());
        outState.putString(STATE_DISPLAY_MODE, getDisplayMode().name());
    }

    @Override
    public void restoreState(State inState) {
        currentFrame = inState.getLong(STATE_CURRENT_FRAME);
        currentLine = inState.getLong(STATE_CURRENT_LINE);
        scrollRegister = inState.getInt(STATE_SCROLL_REGISTER);
        setColorPaletteIndex(inState.getInt(STATE_PALETTE_INDEX));
        setDisplayMode(DisplayMode.valueOf(inState.getString(STATE_DISPLAY_MODE)));
    }

    private boolean isFullScreenMode() {
        return (readScrollRegister() & EXTMEM_CONTROL_BIT) != 0;
    }

    private void writeScrollRegister(int value) {
        this.scrollRegister = (value & EXTMEM_CONTROL_BIT) | (value & 0377);
    }

    private int readScrollRegister() {
        return this.scrollRegister;
    }

    @Override
    public int read(long cpuTime, int address) {
        return readScrollRegister();
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        writeScrollRegister(value);
        return true;
    }
}
