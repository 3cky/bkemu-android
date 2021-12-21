/*
 * Copyright (C) 2021 Victor Antonovich (v.antonovich@gmail.com)
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
import android.os.Handler;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;

import su.comp.bk.R;
import su.comp.bk.arch.io.disk.DiskImage;
import su.comp.bk.arch.io.disk.FloppyController;
import su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier;

/**
 * Disk drives manager dialog.
 */
public class BkEmuDiskManagerDialog extends DialogFragment {
    private static final int MAX_FILE_NAME_DISPLAY_LENGTH = 15;
    private static final int FILE_NAME_DISPLAY_SUFFIX_LENGTH = 3;

    private static final long SHOW_SELECTED_DRIVES_UPDATE_PERIOD = 250L;

    private final SparseArray<View> floppyDriveViews = new SparseArray<>();

    private final Handler handler = new Handler();

    private boolean isActive;

    public static BkEmuDiskManagerDialog newInstance() {
        return new BkEmuDiskManagerDialog();
    }

    public BkEmuDiskManagerDialog() {
    }

    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    private FloppyController getFloppyController() {
        return getBkEmuActivity().getComputer().getFloppyController();
    }

    private void addFloppyDriveView(FloppyDriveIdentifier identifier, View view) {
        floppyDriveViews.append(identifier.ordinal(), view);
    }

    private View getFloppyDriveView(FloppyDriveIdentifier identifier) {
        return floppyDriveViews.get(identifier.ordinal());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = getBkEmuActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_disk_manager);
        LayoutInflater inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.fdd_mgr_dialog, null));
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        setupFloppyDriveViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.isActive = true;
        handler.post(this::showSelectedDrives);
    }

    @Override
    public void onPause() {
        isActive = false;
        super.onPause();
    }

    private void showSelectedDrives() {
        if (isActive) {
            showSelectedFloppyDrive();
            handler.postDelayed(this::showSelectedDrives, SHOW_SELECTED_DRIVES_UPDATE_PERIOD);
        }
    }

    private void showSelectedFloppyDrive() {
        FloppyDriveIdentifier selectedFloppyDrive;
        FloppyController floppyController = getFloppyController();
        if (floppyController != null) {
            selectedFloppyDrive = floppyController.getSelectedFloppyDriveIdentifier();
            for (FloppyDriveIdentifier floppyDrive : FloppyDriveIdentifier.values()) {
                View floppyDriveView = getFloppyDriveView(floppyDrive);
                floppyDriveView.setSelected(floppyDrive == selectedFloppyDrive);
            }
        }
    }

    private void setupFloppyDriveViews() {
        Dialog dialog = Objects.requireNonNull(getDialog());

        addFloppyDriveView(FloppyDriveIdentifier.A, dialog.findViewById(R.id.fdd_layout_a));
        addFloppyDriveView(FloppyDriveIdentifier.B, dialog.findViewById(R.id.fdd_layout_b));
        addFloppyDriveView(FloppyDriveIdentifier.C, dialog.findViewById(R.id.fdd_layout_c));
        addFloppyDriveView(FloppyDriveIdentifier.D, dialog.findViewById(R.id.fdd_layout_d));

        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            setupFloppyDriveView(driveIdentifier);
        }
    }

    private void setupFloppyDriveView(final FloppyDriveIdentifier fddIdentifier) {
        View fddView = getFloppyDriveView(fddIdentifier);
        updateFloppyDriveView(fddView, fddIdentifier);
        BkEmuActivity bkEmuActivity = getBkEmuActivity();
        fddView.setOnClickListener(v -> bkEmuActivity.showMountDiskImageFileDialog(fddIdentifier));
        fddView.setOnLongClickListener(v -> {
            bkEmuActivity.unmountFddImage(fddIdentifier);
            updateFloppyDriveView(v, fddIdentifier);
            return true;
        });
    }

    private void updateFloppyDriveView(final View fddView,
                                       final FloppyDriveIdentifier fddIdentifier) {
        TextView fddLabelView = fddView.findViewWithTag("fdd_label");
        fddLabelView.setText(fddIdentifier.name());
        FloppyController fddController = getFloppyController();
        SwitchCompat fddWriteProtectSwitch = fddView.findViewWithTag("fdd_wp_switch");
        fddWriteProtectSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                setFddWriteProtectMode(fddController, fddIdentifier, isChecked));
        boolean isFddMounted = fddController.isFloppyDriveMounted(fddIdentifier);
        ImageView fddImageView = fddView.findViewWithTag("fdd_image");
        fddImageView.setImageResource(isFddMounted ? R.drawable.floppy_drive_loaded
                : R.drawable.floppy_drive);
        fddImageView.setSelected(false);
        TextView fddFileTextView = fddView.findViewWithTag("fdd_file");
        if (isFddMounted) {
            DiskImage mountedDiskImage = fddController.getFloppyDriveImage(fddIdentifier);
            fddWriteProtectSwitch.setChecked(fddController.isFloppyDriveInWriteProtectMode(fddIdentifier));
            fddWriteProtectSwitch.setClickable(!mountedDiskImage.isReadOnly());
            fddFileTextView.setTextColor(getResources().getColor(R.color.fdd_loaded));
            String fddImageFileName = mountedDiskImage.getName();
            if (fddImageFileName.length() > MAX_FILE_NAME_DISPLAY_LENGTH) {
                // Trim file name to display
                int nameDotIndex = fddImageFileName.lastIndexOf('.');
                if (nameDotIndex < 0) {
                    nameDotIndex = fddImageFileName.length();
                }
                int nameSuffixIndex = nameDotIndex - FILE_NAME_DISPLAY_SUFFIX_LENGTH;
                int namePrefixIndex = MAX_FILE_NAME_DISPLAY_LENGTH - (fddImageFileName.length()
                        - nameSuffixIndex);
                fddImageFileName = fddImageFileName.substring(0, namePrefixIndex)
                        .concat("...").concat(fddImageFileName.substring(nameSuffixIndex));
            }
            fddFileTextView.setText(fddImageFileName);
        } else {
            fddWriteProtectSwitch.setClickable(false);
            fddWriteProtectSwitch.setChecked(false);
            fddFileTextView.setTextColor(getResources().getColor(R.color.fdd_empty));
            fddFileTextView.setText(R.string.fdd_empty);
        }
    }

    private void setFddWriteProtectMode(FloppyController fddController,
                                        FloppyDriveIdentifier fddIdentifier,
                                        boolean isWriteProtectMode) {
        fddController.setFloppyDriveWriteProtectMode(fddIdentifier, isWriteProtectMode);
        getBkEmuActivity().setLastFloppyDriveWriteProtectMode(fddIdentifier, isWriteProtectMode);
    }
}
