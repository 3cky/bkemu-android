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

package su.comp.bk.arch.io;

import static su.comp.bk.arch.io.VideoController.SCREEN_HEIGHT_EXTMEM;
import static su.comp.bk.arch.io.VideoController.SCREEN_HEIGHT_NORMAL;
import static su.comp.bk.arch.io.VideoController.SCREEN_SCANLINE_LENGTH;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * {@link su.comp.bk.arch.io.VideoController.FrameRenderer} implementation
 * using the internal {@link Bitmap} for emulator screen frame rendering.
 */
public class VideoControllerFrameRenderer implements VideoController.FrameRenderer {
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

    // Frame buffer width (in pixels)
    public final static int FRAME_BUFFER_WIDTH = 512;
    // Frame buffer height (in lines)
    public final static int FRAME_BUFFER_HEIGHT = SCREEN_HEIGHT_NORMAL;
    // Frame buffer pixels per videoRAM word
    private final static int FRAME_BUFFER_PIXELS_PER_WORD = Short.SIZE;
    // Screen pixels per videoRAM word in black and white mode
    private final static int SCREEN_PPW_BW = (Short.SIZE / VideoController.SCREEN_BPP_BW);
    // Screen pixels per videoRAM word in color mode
    private final static int SCREEN_PPW_COLOR = (Short.SIZE / VideoController.SCREEN_BPP_COLOR);

    // Video buffer pixels per screen pixel in black and white mode
    private final static int PIXELS_PER_SCREEN_PIXEL_BW = 1;
    // Video buffer pixels per screen pixel in color mode
    private final static int PIXELS_PER_SCREEN_PIXEL_COLOR = 2;

    // VideoRAM word pixel value mask for black and white mode
    private final static int PIXEL_MASK_BW = 1;
    // VideoRAM word pixel value mask for color mode
    private final static int PIXEL_MASK_COLOR = 3;


    // Frame buffer bitmap object
    private final Bitmap frameBuffer;

    // VideoRAM data byte to corresponding pixels lookup table (16 palettes * 8 pixels * 256 byte values)
    private final int[] frameDataToPixelsTable = new int[16 * 8 * 256];

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

    public VideoControllerFrameRenderer() {
        this.frameBuffer = Bitmap.createBitmap(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT,
                Bitmap.Config.ARGB_8888);
    }

    public Bitmap getFrameBuffer() {
        return frameBuffer;
    }

    public void drawFrameBuffer(Bitmap dest) {
        Canvas destCanvas = new Canvas(dest);
        Rect destRect = new Rect(0, 0, dest.getWidth(), dest.getHeight());
        synchronized (frameBuffer) {
            destCanvas.drawBitmap(frameBuffer, null, destRect, null);
        }
    }

    private void updateFrameDataToPixelsTable(VideoController.DisplayMode displayMode) {
        synchronized (frameDataToPixelsTable) {
            int pixelTabIdx = 0;
            for (int paletteIndex = 0; paletteIndex < PIXEL_PALETTES_COLOR.length; paletteIndex++) {
                boolean isBwMode = (displayMode == VideoController.DisplayMode.BW);
                int bitsPerPixel = !isBwMode ? VideoController.SCREEN_BPP_COLOR
                        : VideoController.SCREEN_BPP_BW;
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
                            frameDataToPixelsTable[pixelTabIdx++] = pixelColor;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setDisplayMode(VideoController.DisplayMode displayMode) {
        updateFrameDataToPixelsTable(displayMode);
    }

    @Override
    public void renderFrame(VideoController.FrameData frameData) {
        short[] videoData = frameData.getPixelData();
        int numLines = frameData.isFullScreenMode() ? SCREEN_HEIGHT_NORMAL
                : SCREEN_HEIGHT_EXTMEM;
        int videoDataIdx = 0;
        int videoBufferX, videoBufferY;
        synchronized (frameBuffer) {
            frameBuffer.eraseColor(Color.BLACK);
            synchronized (frameDataToPixelsTable) {
                for (int lineIdx = 0; lineIdx < numLines; lineIdx++) {
                    for (int lineWordIdx = 0; lineWordIdx < SCREEN_SCANLINE_LENGTH; lineWordIdx++) {
                        int videoDataWord = videoData[videoDataIdx];
                        if (videoDataWord != 0) {
                            int paletteOffset = frameData.getPaletteIndex(lineIdx)
                                    * (frameDataToPixelsTable.length >>> 4);
                            videoBufferX = (videoDataIdx % SCREEN_SCANLINE_LENGTH)
                                    * FRAME_BUFFER_PIXELS_PER_WORD;
                            videoBufferY = lineIdx;
                            frameBuffer.setPixels(frameDataToPixelsTable,
                                    paletteOffset + ((videoDataWord & 0377) << 3),
                                    FRAME_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                            videoBufferX += 8;
                            frameBuffer.setPixels(frameDataToPixelsTable,
                                    paletteOffset + (((videoDataWord >> 8) & 0377) << 3),
                                    FRAME_BUFFER_WIDTH, videoBufferX, videoBufferY, 8, 1);
                        }
                        videoDataIdx++;
                    }
                }
            }
        }
    }
}
