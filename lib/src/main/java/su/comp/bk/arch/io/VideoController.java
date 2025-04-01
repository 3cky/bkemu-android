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
    public final static int SCREEN_HEIGHT_NORMAL = 256;
    // Screen height (in lines) in extended memory mode
    public final static int SCREEN_HEIGHT_EXTMEM = 64;
    // Screen scan line length (in words)
    public final static int SCREEN_SCANLINE_LENGTH = 040;
    // Screen data length (in words)
    public final static int SCREEN_DATA_LENGTH = SCREEN_HEIGHT_NORMAL * SCREEN_SCANLINE_LENGTH;
    // VideoRAM bits per screen pixel in black and white mode
    public final static int SCREEN_BPP_BW = 1;
    // VideoRAM bits per screen pixel in color mode
    public final static int SCREEN_BPP_COLOR = 2;

    // FrameRenderer reference
    private final FrameRenderer frameRenderer;

    // Screen lines pixel data
    private short[] linesPixelData = new short[SCREEN_DATA_LENGTH];

    // Current color palette index
    private int colorPaletteIndex = 0;
    // Screen line color palette indexes
    private final int[] lineColorPaletteIndexes = new int[FRAME_LINES_VISIBLE];

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

    // Total lines per frame (including vertical sync)
    public final static int FRAME_LINES_TOTAL = 320;
    // Visible lines per frame
    private final static int FRAME_LINES_VISIBLE = SCREEN_HEIGHT_NORMAL;
    // Frame horizontal sync period (64 uS, in nanoseconds)
    public final static long FRAME_SYNC_PERIOD_HORIZONTAL = 64 * 1000L;
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

    public interface FrameRenderer {
        void setDisplayMode(DisplayMode displayMode);

        void renderFrame(FrameData frameData);
    }

    public VideoController(Memory videoMemory, FrameRenderer frameRenderer) {
        this.videoMemory = videoMemory;
        this.frameRenderer = frameRenderer;
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
        frameRenderer.setDisplayMode(displayMode);
    }

    public void renderFrame() {
        synchronized (lastDisplayedFrameData) {
            lastRenderedFrameData.copyFrom(lastDisplayedFrameData);
        }
        frameRenderer.renderFrame(lastRenderedFrameData);
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
        if (line < currentLine) {
            // Resync current line and frame
            currentLine = line;
            currentFrame = line / FRAME_LINES_TOTAL;
        } else if (line > currentLine) {
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
        for (int i = 0; i < frameSyncListeners.size(); i++) {
            FrameSyncListener l = frameSyncListeners.get(i);
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
