/*
 * Created: 21.01.2013
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

import su.comp.bk.arch.memory.BankedMemory;
import su.comp.bk.state.State;

/**
 * BK-0011M video output controller manager.
 */
public class VideoControllerManager implements Device {

    /** Palette register (write only) */
    public final static int PALETTE_REGISTER_ADDRESS = 0177662;

    // Video memory page select bit
    private final static int SCREEN_PAGE_SELECT_BIT = (1 << 15);
    // Video palette mask shift bits number
    private final static int SCREEN_PALETTE_MASK_SHIFT_BITS = 8;

    private final static int[] ADDRESSES = { PALETTE_REGISTER_ADDRESS };

    // State save/restore: video page index value
    public static final String STATE_VIDEO_PAGE_INDEX = "VideoControllerManager#video_page_index";

    private final VideoController videoController;

    private final BankedMemory videoMemory;

    /**
     * Video output controller manager constructor.
     * @param videoController {@link VideoController} to manage
     * @param videoMemory video output controller manager video memory to manage
     */
    public VideoControllerManager(VideoController videoController, BankedMemory videoMemory) {
        this.videoController = videoController;
        this.videoMemory = videoMemory;
        setVideoControllerConfiguration(0);
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
    public void saveState(State outState) {
        outState.putInt(STATE_VIDEO_PAGE_INDEX, videoMemory.getActiveBankIndex());
    }

    @Override
    public void restoreState(State inState) {
        videoMemory.setActiveBankIndex(inState.getInt(STATE_VIDEO_PAGE_INDEX));
    }

    @Override
    public int read(long cpuTime, int address) {
        // Register is write only
        return 0;
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        setVideoControllerConfiguration(value);
        return true;
    }

    private void setVideoControllerConfiguration(int paletteRegisterValue) {
        synchronized (videoController) {
            videoMemory.setActiveBankIndex((paletteRegisterValue
                    & SCREEN_PAGE_SELECT_BIT) != 0 ? 1 : 0);
            videoController.setColorPaletteIndex((paletteRegisterValue
                    >> SCREEN_PALETTE_MASK_SHIFT_BITS) & 017);
        }
    }
}
