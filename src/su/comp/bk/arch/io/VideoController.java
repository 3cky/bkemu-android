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

import su.comp.bk.arch.memory.RandomAccessMemory;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;

/**
 * BK-0010 video output controller (К1801ВП1-037).
 */
public class VideoController implements Device {

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
    // Pixel palette in color mode
    private final static int[] PIXEL_PALETTE_COLOR = { Color.BLACK, Color.BLUE,
        Color.GREEN, Color.RED };

    // VideoRAM data byte to corresponding pixels lookup table
    private final int[] videoDataToPixelsTable = new int[8 * 256];

    // State save/restore: color/bw mode flag value
    private static final String STATE_COLOR_MODE =
            VideoController.class.getName() + "#color_mode";

    // State save/restore: scroll register value
    private static final String STATE_SCROLL_REGISTER =
            VideoController.class.getName() + "#scroll_reg";

    // Screen mode flag: true for color mode, false for black and white mode
    private boolean isColorMode;

    // Scroll register value
    private int scrollRegister;

    // VideoRAM reference
    private final RandomAccessMemory videoMemory;

    // Video buffer width (in pixels)
    private final static int VIDEO_BUFFER_WIDTH = 512;
    // Video buffer height (in pixels)
    private final static int VIDEO_BUFFER_HEIGHT = 256;
    // Video buffer pixels per videoRAM word
    private final static int VIDEO_BUFFER_PIXELS_PER_WORD = Short.SIZE;
    // Video buffer bitmap object
    private final Bitmap videoBuffer;

    public VideoController(RandomAccessMemory videoMemory) {
        this.videoMemory = videoMemory;
        this.videoBuffer = Bitmap.createBitmap(VIDEO_BUFFER_WIDTH, VIDEO_BUFFER_HEIGHT,
                Bitmap.Config.ARGB_8888);
        writeScrollRegister(SCROLL_EXTMEM_VALUE);
        setColorMode(true);
    }

    public boolean isColorMode() {
        return isColorMode;
    }

    public void setColorMode(boolean isColorMode) {
        this.isColorMode = isColorMode;
        int pixelTabIdx = 0;
        int bitsPerPixel = isColorMode ? SCREEN_BPP_COLOR : SCREEN_BPP_BW;
        int[] pixelPalette = isColorMode ? PIXEL_PALETTE_COLOR : PIXEL_PALETTE_BW;
        int pixelMask = isColorMode ? PIXEL_MASK_COLOR : PIXEL_MASK_BW;
        int pixelsPerByte = (isColorMode ? SCREEN_PPW_COLOR : SCREEN_PPW_BW) / 2;
        int pixelsPerScreenPixel = isColorMode ? PIXELS_PER_SCREEN_PIXEL_COLOR
                : PIXELS_PER_SCREEN_PIXEL_BW;
        synchronized (videoDataToPixelsTable) {
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

    public Bitmap getVideoBuffer() {
        return videoBuffer;
    }

    public Bitmap renderVideoBuffer() {
        short[] videoData = videoMemory.getData();
        videoBuffer.eraseColor(Color.BLACK);
        int videoDataOffset;
        int scrollShift;
        if (isFullFrameMode()) {
            videoDataOffset = 0;
            scrollShift = (readScrollRegister() - SCROLL_BASE_VALUE) & 0377;
        } else {
            videoDataOffset = videoData.length - SCREEN_HEIGHT_EXTMEM * SCREEN_SCANLINE_LENGTH;
            scrollShift = (SCROLL_EXTMEM_VALUE - SCROLL_BASE_VALUE) & 0377;
        }
        int videoBufferX;
        int videoBufferY;
        synchronized (videoDataToPixelsTable) {
            for (int videoDataIdx = videoDataOffset; videoDataIdx < videoData.length;
                    videoDataIdx++) {
                int videoDataWord = videoData[videoDataIdx];
                if (videoDataWord != 0) {
                    videoBufferX = (videoDataIdx % SCREEN_SCANLINE_LENGTH) * VIDEO_BUFFER_PIXELS_PER_WORD;
                    videoBufferY = (videoDataIdx / SCREEN_SCANLINE_LENGTH - scrollShift)
                            & (VIDEO_BUFFER_HEIGHT - 1);
                    videoBuffer.setPixels(videoDataToPixelsTable, (videoDataWord & 0377) << 3,
                            VIDEO_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                    videoBufferX += 8;
                    videoBuffer.setPixels(videoDataToPixelsTable, ((videoDataWord >> 8) & 0377) << 3,
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
    public void saveState(Bundle outState) {
        outState.putInt(STATE_SCROLL_REGISTER, scrollRegister);
        outState.putBoolean(STATE_COLOR_MODE, isColorMode());
    }

    @Override
    public void restoreState(Bundle inState) {
        scrollRegister = inState.getInt(STATE_SCROLL_REGISTER);
        setColorMode(inState.getBoolean(STATE_COLOR_MODE));
    }

    private boolean isFullFrameMode() {
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
