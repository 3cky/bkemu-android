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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * {@link AudioTrack} based {@link AudioPlayer}.
 */
public class AudioTrackPlayer implements AudioPlayer {
    private final int sampleRate;

    private final int minBufferSize;

    private final AudioTrack player;

    public AudioTrackPlayer() {
        sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) {
            throw new IllegalStateException("Invalid minimum audio buffer size: " + minBufferSize);
        }
        player = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public int getBufferSize() {
        return minBufferSize;
    }

    @Override
    public void setGain(float gain) {
        player.setVolume(gain);
    }

    @Override
    public void play(short[] audioData, int offsetInShorts, int sizeInShorts) {
        player.write(audioData, offsetInShorts, sizeInShorts);
    }

    @Override
    public void resume() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
        player.flush();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void release() {
        player.release();
    }
}
