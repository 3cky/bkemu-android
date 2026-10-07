/*
 * Copyright (C) 2026 Victor Antonovich (v.antonovich@gmail.com)
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
 */

package su.comp.bk.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import su.comp.bk.R;
import su.comp.bk.arch.Computer.Configuration;

/**
 * Quick computer configuration selection dialog.
 */
public class BkEmuConfigurationDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BkEmuActivity activity = (BkEmuActivity) requireActivity();
        Configuration[] configurations = Configuration.values();
        String[] descriptions = new String[configurations.length];
        for (int i = 0; i < configurations.length; i++) {
            descriptions[i] = activity.getComputerConfigurationDescription(configurations[i]);
        }
        return new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_select_config)
                .setSingleChoiceItems(descriptions,
                        activity.getComputer().getConfiguration().ordinal(), (dialog, which) -> {
                            dismiss();
                            activity.setComputerConfiguration(configurations[which]);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
