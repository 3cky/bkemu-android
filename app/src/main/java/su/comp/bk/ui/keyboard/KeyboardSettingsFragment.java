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

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import java.util.ArrayList;
import java.util.List;

import su.comp.bk.R;
import su.comp.bk.ui.BkEmuActivity;
import su.comp.bk.ui.keyboard.KeyboardManager.OnScreenKeyboardDisplayMode;

/**
 * Keyboard settings fragment.
 */
public class KeyboardSettingsFragment extends PreferenceFragmentCompat {
    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_keyboard, rootKey);
        setupPreferences();
    }

    private Preference setupPreference(String preferenceKey,
                                       Preference.OnPreferenceChangeListener onPreferenceChangeListener) {
        Preference preference = findPreference(preferenceKey);
        if (preference == null) {
            return null;
        }
        preference.setOnPreferenceChangeListener(onPreferenceChangeListener);
        return preference;
    }

    private void setupPreferences() {
        final BkEmuActivity bkEmuActivity = getBkEmuActivity();
        KeyboardManager keyboardManager = bkEmuActivity.getKeyboardManager();
        setupOnScreenKeyboardModePreference(keyboardManager);
        setupOnScreenKeyboardOverlayAlphaPreference(keyboardManager);
    }

    private void setupOnScreenKeyboardModePreference(final KeyboardManager keyboardManager) {
        ListPreference osdKeyboardMode = (ListPreference) setupPreference("osd_keyboard_mode",
                (preference, newValue) -> {
                    OnScreenKeyboardDisplayMode config = OnScreenKeyboardDisplayMode.valueOf(newValue.toString());
                    keyboardManager.setOnScreenKeyboardDisplayMode(config);
                    return true;
                });
        if (osdKeyboardMode == null) {
            return;
        }
        List<String> modeNames = new ArrayList<>();
        List<String> modeDescriptions = new ArrayList<>();
        for (OnScreenKeyboardDisplayMode mode: OnScreenKeyboardDisplayMode.values()) {
            modeNames.add(mode.name());
            modeDescriptions.add(getOnScreenKeyboardDisplayModeDescription(mode));
        }
        osdKeyboardMode.setEntries(modeDescriptions.toArray(new String[0]));
        osdKeyboardMode.setEntryValues(modeNames.toArray(new String[0]));
        osdKeyboardMode.setValueIndex(keyboardManager.getOnScreenKeyboardDisplayMode().ordinal());
    }

    private String getOnScreenKeyboardDisplayModeDescription(OnScreenKeyboardDisplayMode mode) {
        if (mode == OnScreenKeyboardDisplayMode.NORMAL) {
            return getString(R.string.osd_keyboard_mode_normal);
        } else if (mode == OnScreenKeyboardDisplayMode.OVERLAY) {
            return getString(R.string.osd_keyboard_mode_overlay);
        }
        return null;
    }

    private void setupOnScreenKeyboardOverlayAlphaPreference(final KeyboardManager keyboardManager) {
        SeekBarPreference osdKeyboardAlpha = (SeekBarPreference) setupPreference("osd_keyboard_overlay_alpha",
                (preference, newValue) -> {
                    float newAlpha = seekBarPositionToOnScreenKeyboardOverlayAlpha((Integer) newValue);
                    keyboardManager.setOnScreenKeyboardOverlayAlpha(newAlpha);
                    return true;
                });
        if (osdKeyboardAlpha == null) {
            return;
        }
        osdKeyboardAlpha.setValue(onScreenKeyboardOverlayAlphaToSeekBarPosition(
                keyboardManager.getOnScreenKeyboardOverlayAlpha()));
    }

    private static int onScreenKeyboardOverlayAlphaToSeekBarPosition(float alpha) {
        return (int) (100 * (alpha - KeyboardManager.MIN_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA)
                / (KeyboardManager.MAX_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA
                    - KeyboardManager.MIN_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA));
    }

    private static float seekBarPositionToOnScreenKeyboardOverlayAlpha(int position) {
        return KeyboardManager.MIN_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA
                + position * (KeyboardManager.MAX_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA
                    - KeyboardManager.MIN_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA) / 100f;
    }
}
