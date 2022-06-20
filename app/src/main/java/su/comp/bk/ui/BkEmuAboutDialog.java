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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import su.comp.bk.BuildConfig;
import su.comp.bk.R;
import su.comp.bk.arch.Computer;

/**
 * Sound device volumes control dialog.
 */
public class BkEmuAboutDialog extends DialogFragment {
    private static final long STATS_UPDATE_INTERVAL_MSECS = 2000L;

    private final Handler handler = new Handler();

    private boolean isActive;

    private TextView cpuStatsTextView;
    private TextView renderStatsTextView;

    public static BkEmuAboutDialog newInstance() {
        return new BkEmuAboutDialog();
    }

    public BkEmuAboutDialog() {
    }

    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_about);
        builder.setView(R.layout.about_dialog);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog aboutDialog = Objects.requireNonNull(getDialog());
        final BkEmuActivity bkEmuActivity = getBkEmuActivity();
        cpuStatsTextView = aboutDialog.findViewById(R.id.about_cpu_stats);
        renderStatsTextView = aboutDialog.findViewById(R.id.about_render_stats);
        TextView versionTextView = aboutDialog.findViewById(R.id.about_version);
        try {
            versionTextView.setText(getResources().getString(R.string.about_version,
                    bkEmuActivity.getPackageManager().getPackageInfo(
                            bkEmuActivity.getPackageName(), 0).versionName));
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        Date buildDate = new Date(BuildConfig.BUILD_TIMESTAMP);
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
        df.setTimeZone(tz);
        String build = df.format(buildDate);
        TextView buildTextView = aboutDialog.findViewById(R.id.about_build);
        buildTextView.setText(getResources().getString(R.string.about_build, build));
        TextView changelogTextView = aboutDialog.findViewById(R.id.about_changelog);
        changelogTextView.setOnClickListener(v -> {
            dismiss();
            bkEmuActivity.showChangelogDialog();
        });
        TextView shareTextView = aboutDialog.findViewById(R.id.about_share);
        shareTextView.setOnClickListener(v -> {
            dismiss();
            bkEmuActivity.shareApplication();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        this.isActive = true;
        handler.post(this::updateStats);
    }

    private void updateStats() {
        if (isActive) {
            updateCpuStats();
            updateRenderStats();
            handler.postDelayed(this::updateStats, STATS_UPDATE_INTERVAL_MSECS);
        }
    }

    @Override
    public void onPause() {
        isActive = false;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private Computer getComputer() {
        return getBkEmuActivity().getComputer();
    }

    private void updateCpuStats() {
        Computer computer = getComputer();
        float effectiveClockFrequency = computer.getEffectiveClockFrequency();
        cpuStatsTextView.setText(getResources().getString(R.string.about_cpu_stats,
                effectiveClockFrequency / 1000f, effectiveClockFrequency
                        / computer.getClockFrequency() * 100f));
    }

    private void updateRenderStats() {
        BkEmuView bkEmuView = getBkEmuActivity().getBkEmuView();
        if (bkEmuView == null) {
            return;
        }
        float uiUpdateThreadCpuLoad = bkEmuView.getUiUpdateThreadCpuLoad();
        renderStatsTextView.setText(getResources().getString(R.string.about_render_stats,
                uiUpdateThreadCpuLoad));
    }
}
