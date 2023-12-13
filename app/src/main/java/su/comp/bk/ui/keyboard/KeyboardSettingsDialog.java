/*
 * Copyright (C) 2023 Victor Antonovich (v.antonovich@gmail.com)
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

package su.comp.bk.ui.keyboard;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import su.comp.bk.R;

/**
 * Keyboard settings dialog.
 */
public class KeyboardSettingsDialog extends DialogFragment {

    public static KeyboardSettingsDialog newInstance() {
        return new KeyboardSettingsDialog();
    }

    public KeyboardSettingsDialog() {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.keyboard_settings);
        LayoutInflater inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.keyboard_settings_dialog, null));
        return builder.create();
    }

    @Override
    public void onDestroyView() {
        KeyboardSettingsFragment settingsFragment = (KeyboardSettingsFragment) getParentFragmentManager()
                .findFragmentById(R.id.keyboard_settings_fragment);
        if (settingsFragment != null) {
            getParentFragmentManager().beginTransaction()
                    .remove(settingsFragment).commitAllowingStateLoss();
        }
        super.onDestroyView();
    }
}
