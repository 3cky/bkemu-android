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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;

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
    // Scroll/mode register: scroll value in extended memory mode
    private final static int SCROLL_EXTMEM_VALUE = 0230;
    // Scroll/mode register: extended memory mode disable bit
    // (0 - extended memory mode, 1 - normal mode)
    private final static int EXTMEM_CONTROL_BIT = 01000;

    // Screen height (in lines) in extended memory mode
    private final static int SCREEN_HEIGHT_EXTMEM = 64;
    // Screen scan line length (in words)
    private final static int SCREEN_SCANLINE_LENGTH = 040;
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
        { Color.BLACK, 0xffbf0000, 0xff7f0000, Color.RED },
        { Color.BLACK, Color.GREEN, Color.CYAN, Color.YELLOW },
        { Color.BLACK, 0xffbf00bf, 0xff7f00ff, Color.MAGENTA },
        { Color.BLACK, Color.YELLOW, 0xffff00ff, 0xffbf0000 },
        { Color.BLACK, Color.YELLOW, 0xffbf00bf, Color.RED },
        { Color.BLACK, Color.CYAN, Color.YELLOW, Color.RED },
        { Color.BLACK, Color.RED, Color.GREEN, Color.CYAN },
        { Color.BLACK, Color.CYAN, Color.YELLOW, Color.WHITE },
        { Color.BLACK, Color.YELLOW, Color.GREEN, Color.WHITE },
        { Color.BLACK, Color.CYAN, Color.GREEN, Color.WHITE }
    };

    // Current color palette index
    private int colorPaletteIndex = 0;

    // VideoRAM data byte to corresponding pixels lookup table (16 palettes * 8 pixels * 256 byte values)
    private final int[] videoDataToPixelsTable = new int[16 * 8 * 256];

    // State save/restore: color/bw mode flag value
    private static final String STATE_COLOR_MODE =
            VideoController.class.getName() + "#color_mode";

    // State save/restore: scroll register value
    private static final String STATE_SCROLL_REGISTER =
            VideoController.class.getName() + "#scroll_reg";

    // State save/restore: palette index value
    private static final String STATE_PALETTE_INDEX =
            VideoController.class.getName() + "#palette_index";

    // State save/restore: current displayed frame number
    private static final String STATE_CURRENT_FRAME =
            VideoController.class.getName() + "#frame_num";

    // State save/restore: current displayed line number
    private static final String STATE_CURRENT_LINE =
            VideoController.class.getName() + "#line_num";

    // Screen mode flag: true for color mode, false for black and white mode
    private boolean isColorMode;

    // Scroll register value
    private int scrollRegister;

    // Video memory reference
    private final Memory videoMemory;

    // Video buffer width (in pixels)
    public final static int VIDEO_BUFFER_WIDTH = 512;
    // Video buffer height (in pixels)
    public final static int VIDEO_BUFFER_HEIGHT = 256;
    // Video buffer pixels per videoRAM word
    private final static int VIDEO_BUFFER_PIXELS_PER_WORD = Short.SIZE;
    // Video buffer bitmap object
    private final Bitmap videoBuffer;

    // Total lines per frame (including vertical sync)
    private final static int FRAME_LINES_TOTAL = 320;
    // Visible lines per frame
    private final static int FRAME_LINES_VISIBLE = 256;
    // Frame horizontal sync period (64 uS, in nanoseconds)
    private final static long FRAME_SYNC_PERIOD_HORIZONTAL = 64 * 1000L;
    // Frame vertical sync period
    private final static long FRAME_SYNC_PERIOD_VERTICAL =
            FRAME_SYNC_PERIOD_HORIZONTAL * FRAME_LINES_TOTAL;
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
    class FrameData {
        private final short[] pixelData;
        private int paletteIndex;
        private boolean isFullScreenMode;

        FrameData() {
            pixelData = new short[VIDEO_BUFFER_HEIGHT * SCREEN_SCANLINE_LENGTH];
        }

        short[] getPixelData() {
            return pixelData;
        }

        void setPixelData(short[] pixelData) {
            System.arraycopy(pixelData, 0, this.pixelData, 0, pixelData.length);
        }

        int getPaletteIndex() {
            return paletteIndex;
        }

        void setPaletteIndex(int paletteIndex) {
            this.paletteIndex = paletteIndex;
        }

        boolean isFullScreenMode() {
            return isFullScreenMode;
        }

        void setFullScreenMode(boolean isFullScreenMode) {
            this.isFullScreenMode = isFullScreenMode;
        }

        void init(short[] pixelData, int paletteIndex, boolean isFullScreenMode) {
            setPixelData(pixelData);
            setPaletteIndex(paletteIndex);
            setFullScreenMode(isFullScreenMode);
        }

        void copyFrom(FrameData frameData) {
            init(frameData.getPixelData(), frameData.getPaletteIndex(), frameData.isFullScreenMode());
        }
    }

    public VideoController(Memory videoMemory) {
        this.videoMemory = videoMemory;
        this.videoBuffer = Bitmap.createBitmap(VIDEO_BUFFER_WIDTH, VIDEO_BUFFER_HEIGHT,
                Bitmap.Config.ARGB_8888);
        writeScrollRegister(SCROLL_EXTMEM_VALUE);
        setColorMode(true);
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

    public boolean isColorMode() {
        return isColorMode;
    }

    public synchronized void setColorMode(boolean isColorMode) {
        this.isColorMode = isColorMode;
        updateVideoDataToPixelsTable(isColorMode);
    }

    private void updateVideoDataToPixelsTable(boolean isColorMode) {
        synchronized (videoDataToPixelsTable) {
            int pixelTabIdx = 0;
            for (int paletteIndex = 0; paletteIndex < PIXEL_PALETTES_COLOR.length; paletteIndex++) {
                int bitsPerPixel = isColorMode ? SCREEN_BPP_COLOR : SCREEN_BPP_BW;
                int[] pixelPalette = isColorMode ? PIXEL_PALETTES_COLOR[paletteIndex]
                        : PIXEL_PALETTE_BW;
                int pixelMask = isColorMode ? PIXEL_MASK_COLOR : PIXEL_MASK_BW;
                int pixelsPerByte = (isColorMode ? SCREEN_PPW_COLOR : SCREEN_PPW_BW) / 2;
                int pixelsPerScreenPixel = isColorMode ? PIXELS_PER_SCREEN_PIXEL_COLOR
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
        videoBuffer.eraseColor(Color.BLACK);
        short[] videoData = lastRenderedFrameData.getPixelData();
        int videoDataOffset;
        int scrollShift;
        if (lastRenderedFrameData.isFullScreenMode()) {
            videoDataOffset = 0;
            scrollShift = (readScrollRegister() - SCROLL_BASE_VALUE) & 0377;
        } else {
            videoDataOffset = videoData.length - SCREEN_HEIGHT_EXTMEM * SCREEN_SCANLINE_LENGTH;
            scrollShift = (SCROLL_EXTMEM_VALUE - SCROLL_BASE_VALUE) & 0377;
        }
        int paletteOffset = lastDisplayedFrameData.paletteIndex * (videoDataToPixelsTable.length >>> 4);
        int videoBufferX, videoBufferY;
        synchronized (videoDataToPixelsTable) {
            for (int videoDataIdx = videoDataOffset; videoDataIdx < videoData.length; videoDataIdx++) {
                int videoDataWord = videoData[videoDataIdx];
                if (videoDataWord != 0) {
                    videoBufferX = (videoDataIdx % SCREEN_SCANLINE_LENGTH) * VIDEO_BUFFER_PIXELS_PER_WORD;
                    videoBufferY = (videoDataIdx / SCREEN_SCANLINE_LENGTH - scrollShift)
                            & (VIDEO_BUFFER_HEIGHT - 1);
                    videoBuffer.setPixels(videoDataToPixelsTable,
                            paletteOffset + ((videoDataWord & 0377) << 3),
                            VIDEO_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                    videoBufferX += 8;
                    videoBuffer.setPixels(videoDataToPixelsTable,
                            paletteOffset + (((videoDataWord >> 8) & 0377) << 3),
                            VIDEO_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                }
            }
        }
        return videoBuffer;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        // Do nothing
    }

    @Override
    public void uptimeUpdated(long uptime) {
        currentLine = uptime / FRAME_SYNC_PERIOD_HORIZONTAL;
        long currentLineFrame = currentLine / FRAME_LINES_TOTAL;
        long currentLineFrameLine = currentLine % FRAME_LINES_TOTAL;
        if (currentLineFrame >= currentFrame && currentLineFrameLine > FRAME_LINES_VISIBLE) {
            storeLastFrameVideoData();
            notifyFrameSyncListenersVerticalSync();
            currentFrame = currentLineFrame + 1;
        }
    }

    private void storeLastFrameVideoData() {
        synchronized (lastDisplayedFrameData) {
            lastDisplayedFrameData.init(videoMemory.getData(), colorPaletteIndex, isFullScreenMode());
        }
    }

    private void notifyFrameSyncListenersVerticalSync() {
        for (FrameSyncListener l : frameSyncListeners) {
            l.verticalSync(currentFrame);
        }
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putLong(STATE_CURRENT_FRAME, currentFrame);
        outState.putLong(STATE_CURRENT_LINE, currentLine);
        outState.putInt(STATE_SCROLL_REGISTER, scrollRegister);
        outState.putInt(STATE_PALETTE_INDEX, getColorPaletteIndex());
        outState.putBoolean(STATE_COLOR_MODE, isColorMode());
    }

    @Override
    public void restoreState(Bundle inState) {
        currentFrame = inState.getLong(STATE_CURRENT_FRAME);
        currentLine = inState.getLong(STATE_CURRENT_LINE);
        scrollRegister = inState.getInt(STATE_SCROLL_REGISTER);
        setColorPaletteIndex(inState.getInt(STATE_PALETTE_INDEX));
        setColorMode(inState.getBoolean(STATE_COLOR_MODE));
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
        int registerValue;
        if (isByteMode) {
            if ((address & 1) != 0) {
                registerValue = (value << 8) | (readScrollRegister() & 0377);
            } else {
                registerValue = (readScrollRegister() & 0177400) | (value & 0377);
            }
        } else {
            registerValue = value;
        }
        writeScrollRegister(registerValue);
        return true;
    }
}
