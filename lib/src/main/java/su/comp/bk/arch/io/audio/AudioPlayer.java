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

package su.comp.bk.arch.io.audio;

/**
 * Audio player interface.
 */
public interface AudioPlayer {
    /**
     * Get the output sample rate in Hz for the this audio player.
     * @return output sample rate in Hz
     */
    int getSampleRate();

    /**
     * Get the estimated audio player buffer size.
     * @return buffer size expressed in bytes
     */
    int getBufferSize();

    /**
     * Sets the specified output gain value on all channels of this audio player..
     * @param gain output gain for all channels to set, in range [0, 1.0]
     */
    void setGain(float gain);

    /**
     * Play the audio data.
     * @param audioData array that holds the data to play
     * @param offsetInShorts offset expressed in shorts in audioData where the data to play starts
     * @param sizeInShorts number of shorts to read in audioData after the offset
     */
    void play(short[] audioData, int offsetInShorts, int sizeInShorts);

    /**
     * Resumes audio player.
     */
    void resume();

    /**
     * Pauses audio player.
     */
    void pause();

    /**
     * Stops audio player.
     */
    void stop();

    /**
     * Releases audio player data.
     */
    void release();
}
