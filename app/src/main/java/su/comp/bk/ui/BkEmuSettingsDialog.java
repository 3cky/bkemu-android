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

package su.comp.bk.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import su.comp.bk.R;

/**
 * Emulator settings dialog.
 */
public class BkEmuSettingsDialog extends DialogFragment {

    public static BkEmuSettingsDialog newInstance() {
        return new BkEmuSettingsDialog();
    }

    public BkEmuSettingsDialog() {
    }

    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = getBkEmuActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_settings);
        builder.setView(R.layout.settings_dialog);
        return builder.create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        BkEmuSettingsFragment settingsFragment = (BkEmuSettingsFragment) getParentFragmentManager()
                .findFragmentById(R.id.settings_fragment);
        if (settingsFragment != null) {
            getParentFragmentManager().beginTransaction()
                    .remove(settingsFragment).commitAllowingStateLoss();
        }
    }
}
