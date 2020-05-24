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
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;

import su.comp.bk.R;
import su.comp.bk.arch.io.audio.AudioOutput;

public class BkEmuVolumeDialog extends DialogFragment implements SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private static final int VOLUME_MUTE = AudioOutput.MIN_VOLUME;
    private static final int VOLUME_UNMUTE = AudioOutput.MAX_VOLUME / 5;

    private final BkEmuActivity bkEmuActivity;

    private SeekBar volumeSeekBar;
    private ImageView muteImageView;

    BkEmuVolumeDialog(BkEmuActivity bkEmuActivity) {
        this.bkEmuActivity = bkEmuActivity;
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
        volumeSeekBar = dialog.findViewById(R.id.volume_speaker);
        updateVolumeSeekBar();
        volumeSeekBar.setOnSeekBarChangeListener(this);
        muteImageView = dialog.findViewById(R.id.mute_speaker);
        updateMuteButton();
        muteImageView.setOnClickListener(this);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        storeVolume();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int volume, boolean fromUser) {
        setVolume(volume);
        updateMuteButton();
    }

    @Override
    public void onClick(View v) {
        // Mute/unmute audio output
        setVolume(isMuted() ? VOLUME_UNMUTE : VOLUME_MUTE);
        storeVolume();
        updateMuteButton();
        updateVolumeSeekBar();
    }

    private AudioOutput getAudioOutput() {
        return bkEmuActivity.getComputer().getAudioOutput();
    }

    private boolean isMuted() {
        return getVolume() == VOLUME_MUTE;
    }

    private void setVolume(int volume) {
        getAudioOutput().setVolume(volume);
    }

    private int getVolume() {
        return getAudioOutput().getVolume();
    }

    private void storeVolume() {
        bkEmuActivity.storeAudioVolume(getVolume());
    }

    private void updateVolumeSeekBar() {
        volumeSeekBar.setProgress(getVolume());
    }

    private void updateMuteButton() {
        muteImageView.setImageResource(isMuted() ? R.drawable.ic_volume_off_white_24
                : R.drawable.ic_volume_white_24);
    }
}
