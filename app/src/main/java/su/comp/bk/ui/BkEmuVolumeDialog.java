/*
 * Copyright (C) 2020 Victor Antonovich (v.antonovich@gmail.com)
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

package su.comp.bk.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.fragment.app.DialogFragment;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import su.comp.bk.R;
import su.comp.bk.arch.io.audio.AudioOutput;
import su.comp.bk.arch.io.audio.Ay8910;
import su.comp.bk.arch.io.audio.Covox;
import su.comp.bk.arch.io.audio.Speaker;

/**
 * Sound device volumes control dialog.
 */
public class BkEmuVolumeDialog extends DialogFragment implements SeekBar.OnSeekBarChangeListener,
        View.OnClickListener {

    private static final int VOLUME_MUTE = AudioOutput.MIN_VOLUME;
    private static final int VOLUME_UNMUTE = AudioOutput.MAX_VOLUME / 2;

    private final Map<String, SeekBar> volumeSeekBars = new ArrayMap<>();
    private final Map<String, ImageView> muteImageViews = new ArrayMap<>();

    public static BkEmuVolumeDialog newInstance() {
        return new BkEmuVolumeDialog();
    }

    public BkEmuVolumeDialog() {
    }

    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.volume);
        LayoutInflater inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.volume_dialog, null));
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = Objects.requireNonNull(getDialog());
        setupOutputVolumeControls(dialog.findViewById(R.id.controls_speaker), Speaker.OUTPUT_NAME);
        setupOutputVolumeControls(dialog.findViewById(R.id.controls_ay8910), Ay8910.OUTPUT_NAME);
        setupOutputVolumeControls(dialog.findViewById(R.id.controls_covox), Covox.OUTPUT_NAME);
    }

    private void setupOutputVolumeControls(ViewGroup volumeControlsGroup, String outputName) {
        if (volumeControlsGroup == null) {
            return;
        }
        for (int i = 0; i < volumeControlsGroup.getChildCount(); i++) {
            View v = volumeControlsGroup.getChildAt(i);
            if (v instanceof ImageView) {
                ImageView muteImageView = (ImageView) v;
                muteImageViews.put(outputName, muteImageView);
                muteImageView.setOnClickListener(this);
                updateMuteButton(outputName);
            } else if (v instanceof SeekBar) {
                SeekBar volumeSeekBar = (SeekBar) v;
                volumeSeekBars.put(outputName, volumeSeekBar);
                volumeSeekBar.setOnSeekBarChangeListener(this);
                updateVolumeSeekBar(outputName);
            }
        }
    }

    private String getControlOutputName(View v) {
        return v.getTag().toString();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        storeVolume(getControlOutputName(seekBar));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int volume, boolean fromUser) {
        String outputName = getControlOutputName(seekBar);
        setVolume(outputName, volume);
        updateMuteButton(outputName);
    }

    @Override
    public void onClick(View muteImageView) {
        // Mute/unmute audio output
        String outputName = getControlOutputName(muteImageView);
        setMuted(outputName, !isMuted(outputName));
    }

    private List<AudioOutput<?>> getAudioOutputs() {
        return getBkEmuActivity().getComputer().getAudioOutputs();
    }

    private AudioOutput<?> getAudioOutput(String outputName) {
        List<AudioOutput<?>> audioOutputs = getAudioOutputs();
        for (AudioOutput<?> audioOutput : audioOutputs) {
            if (audioOutput.getName().equals(outputName)) {
                return audioOutput;
            }
        }
        return null;
    }

    private void setMuted(String outputName, boolean isMuted) {
        setVolume(outputName, isMuted ? VOLUME_MUTE : VOLUME_UNMUTE);
        storeVolume(outputName);
        updateMuteButton(outputName);
        updateVolumeSeekBar(outputName);
    }

    private boolean isMuted(String outputName) {
        return getVolume(outputName) == VOLUME_MUTE;
    }

    private void setVolume(String outputName, int volume) {
        Objects.requireNonNull(getAudioOutput(outputName)).setVolume(volume);
        checkOutputStates(outputName);
    }

    private int getVolume(String outputName) {
        return Objects.requireNonNull(getAudioOutput(outputName)).getVolume();
    }

    private void storeVolume(String outputName) {
        getBkEmuActivity().storeAudioOutputVolume(outputName, getVolume(outputName));
    }

    private void updateVolumeSeekBar(String outputName) {
        SeekBar volumeSeekBar = volumeSeekBars.get(outputName);
        if (volumeSeekBar != null) {
            volumeSeekBar.setProgress(getVolume(outputName));
        }
    }

    private void updateMuteButton(String outputName) {
        ImageView muteImageView = muteImageViews.get(outputName);
        if (muteImageView != null) {
            muteImageView.setSelected(isMuted(outputName));
        }
    }

    private void checkOutputStates(String updatedOutputName) {
        String checkMuteOutputName = null;
        if (Ay8910.OUTPUT_NAME.equals(updatedOutputName)) {
            checkMuteOutputName = Covox.OUTPUT_NAME;
        } else if (Covox.OUTPUT_NAME.equals(updatedOutputName)) {
            checkMuteOutputName = Ay8910.OUTPUT_NAME;
        }
        if (checkMuteOutputName != null && !isMuted(updatedOutputName)
                && !isMuted(checkMuteOutputName)) {
            setMuted(checkMuteOutputName, true);
        }
    }
}
