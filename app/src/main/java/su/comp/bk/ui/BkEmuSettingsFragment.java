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

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.List;

import su.comp.bk.R;
import su.comp.bk.arch.Computer;

/**
 * Emulator settings fragment.
 */
public class BkEmuSettingsFragment extends PreferenceFragmentCompat {
    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setupPreferences();
    }

    private void setupPreference(String preferenceKey, boolean isEnabled,
                                 Preference.OnPreferenceClickListener onPreferenceClickListener) {
        Preference preference = findPreference(preferenceKey);
        if (preference == null) {
            return;
        }
        preference.setEnabled(isEnabled);
        preference.setVisible(isEnabled);
        preference.setOnPreferenceClickListener(onPreferenceClickListener);
    }

    private void setupPreferences() {
        final BkEmuActivity bkEmuActivity = getBkEmuActivity();
        setupComputerConfigurationPreference(bkEmuActivity);
        setupPreference("gamepad", bkEmuActivity.isHardwareJoystickPresent(), preference -> {
            bkEmuActivity.showGamepadLayoutSetupDialog();;
            return true;
        });
        setupPreference("volume", true, preference -> {
            bkEmuActivity.showVolumeDialog();
            return true;
        });
        setupPreference("about", true, preference -> {
            bkEmuActivity.showAboutDialog();
            return true;
        });
    }

    private void setupComputerConfigurationPreference(final BkEmuActivity bkEmuActivity) {
        ListPreference ccp = findPreference(BkEmuActivity.PREFS_KEY_COMPUTER_CONFIGURATION);
        if (ccp == null) {
            return;
        }
        List<String> configNames = new ArrayList<>();
        List<String> configDescriptions = new ArrayList<>();
        for (Computer.Configuration configuration: Computer.Configuration.values()) {
            configNames.add(configuration.name());
            configDescriptions.add(bkEmuActivity.getComputerConfigurationDescription(configuration));
        }
        ccp.setEntries(configDescriptions.toArray(new String[0]));
        ccp.setEntryValues(configNames.toArray(new String[0]));
        final Computer computer = bkEmuActivity.getComputer();
        ccp.setValueIndex(computer.getConfiguration().ordinal());
        ccp.setOnPreferenceChangeListener((preference, newValue) -> {
            Computer.Configuration config = Computer.Configuration.valueOf(newValue.toString());
            if (computer.getConfiguration() != config) {
                bkEmuActivity.setComputerConfiguration(config);
                bkEmuActivity.restartActivity(null, null);
            }
            return true;
        });
    }
}
