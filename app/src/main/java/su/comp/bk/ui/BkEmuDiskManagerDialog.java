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

import static su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier.A;
import static su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier.B;
import static su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier.C;
import static su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier.D;
import static su.comp.bk.arch.io.disk.IdeController.IF_0;
import static su.comp.bk.arch.io.disk.IdeController.IF_1;
import static su.comp.bk.util.FileUtils.ellipsizeFileName;

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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import su.comp.bk.R;
import su.comp.bk.arch.io.disk.DiskImage;
import su.comp.bk.arch.io.disk.FloppyController;
import su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier;
import su.comp.bk.arch.io.disk.IdeController;

/**
 * Disk drives manager dialog.
 */
public class BkEmuDiskManagerDialog extends DialogFragment {
    private static final int MAX_FILE_NAME_DISPLAY_LENGTH = 15;
    private static final int FILE_NAME_DISPLAY_SUFFIX_LENGTH = 3;

    private static final long SHOW_DRIVES_ACTIVITY_UPDATE_INTERVAL_MSECS = 100L;
    private static final long IDE_DRIVE_ACTIVITY_TIMEOUT_NANOS =
            SHOW_DRIVES_ACTIVITY_UPDATE_INTERVAL_MSECS * 1000000L;

    private final SparseArray<View> floppyDriveViews = new SparseArray<>();

    private final SparseArray<View> ideDriveViews = new SparseArray<>();

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

    private IdeController getIdeController() {
        return getBkEmuActivity().getComputer().getIdeController();
    }

    private void addFloppyDriveView(FloppyDriveIdentifier identifier, View view) {
        floppyDriveViews.append(identifier.ordinal(), view);
    }

    private View getFloppyDriveView(FloppyDriveIdentifier identifier) {
        return floppyDriveViews.get(identifier.ordinal());
    }

    private void addIdeDriveView(int ideDriveIdentifier, View view) {
        ideDriveViews.append(ideDriveIdentifier, view);
    }

    private View getIdeDriveView(int ideDriveIdentifier) {
        return ideDriveViews.get(ideDriveIdentifier);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = getBkEmuActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_disk_manager);
        LayoutInflater inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.disk_mgr_dialog, null));
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        setupDriveViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.isActive = true;
        handler.post(this::showDrivesActivity);
    }

    @Override
    public void onPause() {
        isActive = false;
        super.onPause();
    }

    private void showDrivesActivity() {
        if (isActive) {
            showSelectedFloppyDrive();
            showIdeDrivesActivity();
            handler.postDelayed(this::showDrivesActivity, SHOW_DRIVES_ACTIVITY_UPDATE_INTERVAL_MSECS);
        }
    }

    private void showIdeDrivesActivity() {
        IdeController ideController = getIdeController();
        if (ideController != null) {
            for (int ideInterfaceId = IF_0; ideInterfaceId <= IF_1; ideInterfaceId++) {
                View ideDriveView = getIdeDriveView(ideInterfaceId);
                if (isIdeDriveActive(ideController, ideInterfaceId)) {
                    ideDriveView.setSelected(!ideDriveView.isSelected());
                } else {
                    ideDriveView.setSelected(false);
                }
            }
        }
    }

    private boolean isIdeDriveActive(IdeController ideController, int ideInterfaceId) {
        long lastDriveActivityTimestamp = ideController.getLastDriveActivityTimestamp(ideInterfaceId);
        return System.nanoTime() - lastDriveActivityTimestamp <= IDE_DRIVE_ACTIVITY_TIMEOUT_NANOS;
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

    private void setupDriveViews() {
        Dialog dialog = Objects.requireNonNull(getDialog());
        IdeController ideController = getIdeController();
        if (ideController != null) {
            setupIdeDriveViews(dialog, true);
            setupFloppyDriveViews(dialog, A, B);
        } else {
            setupIdeDriveViews(dialog, false);
            setupFloppyDriveViews(dialog, A, B, C, D);
        }
    }

    private void setupFloppyDriveViews(Dialog dialog, FloppyDriveIdentifier... driveIdentifiers) {
        addFloppyDriveView(A, dialog.findViewById(R.id.fdd_layout_a));
        addFloppyDriveView(B, dialog.findViewById(R.id.fdd_layout_b));
        addFloppyDriveView(C, dialog.findViewById(R.id.fdd_layout_c));
        addFloppyDriveView(D, dialog.findViewById(R.id.fdd_layout_d));

        List<FloppyDriveIdentifier> driveIdentifierList = Arrays.asList(driveIdentifiers);
        for (FloppyDriveIdentifier driveIdentifier : FloppyDriveIdentifier.values()) {
            setupFloppyDriveView(driveIdentifier, driveIdentifierList.contains(driveIdentifier));
        }
    }

    private void setupFloppyDriveView(final FloppyDriveIdentifier fddIdentifier, boolean isVisible) {
        View fddView = getFloppyDriveView(fddIdentifier);
        if (isVisible) {
            fddView.setVisibility(View.VISIBLE);
            updateFloppyDriveView(fddView, fddIdentifier);
            BkEmuActivity bkEmuActivity = getBkEmuActivity();
            fddView.setOnClickListener(v ->
                    bkEmuActivity.showMountFloppyDiskImageFileDialog(fddIdentifier));
            fddView.setOnLongClickListener(v -> {
                bkEmuActivity.unmountFloppyDiskImage(fddIdentifier);
                updateFloppyDriveView(v, fddIdentifier);
                return true;
            });
        } else {
            fddView.setVisibility(View.GONE);
        }
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
            // Trim file name to display, if needed
            fddImageFileName = ellipsizeFileName(fddImageFileName, MAX_FILE_NAME_DISPLAY_LENGTH,
                    FILE_NAME_DISPLAY_SUFFIX_LENGTH);
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

    private void setupIdeDriveViews(Dialog dialog, boolean isVisible) {
        addIdeDriveView(IF_0, dialog.findViewById(R.id.hdd_layout_0));
        addIdeDriveView(IF_1, dialog.findViewById(R.id.hdd_layout_1));

        setupIdeDriveView(IF_0, isVisible);
        setupIdeDriveView(IF_1, isVisible);
    }

    private void setupIdeDriveView(final int ideDriveIdentifier, boolean isVisible) {
        View hddView = getIdeDriveView(ideDriveIdentifier);
        if (isVisible) {
            hddView.setVisibility(View.VISIBLE);
            updateIdeDriveView(hddView, ideDriveIdentifier);
            BkEmuActivity bkEmuActivity = getBkEmuActivity();
            hddView.setOnClickListener(v ->
                    bkEmuActivity.showAttachIdeDriveImageFileDialog(ideDriveIdentifier));
            hddView.setOnLongClickListener(v -> {
                bkEmuActivity.detachIdeDrive(ideDriveIdentifier);
                updateIdeDriveView(v, ideDriveIdentifier);
                return true;
            });
        } else {
            hddView.setVisibility(View.GONE);
        }
    }

    private void updateIdeDriveView(final View hddView,
                                    final int ideDriveIdentifier) {
        TextView hddLabelView = hddView.findViewWithTag("hdd_label");
        hddLabelView.setText(ideDriveIdentifier == IF_0 ? "1" : "2");
        IdeController ideController = getIdeController();
        IdeController.IdeDrive ideDrive = ideController.getAttachedDrive(ideDriveIdentifier);
        boolean isHddAttached = (ideDrive != null);
        ImageView hddImageView = hddView.findViewWithTag("hdd_image");
        hddImageView.setSelected(false);
        TextView hddNameTextView = hddView.findViewWithTag("hdd_file");
        if (isHddAttached) {
            hddNameTextView.setTextColor(getResources().getColor(R.color.hdd_attached));
            String hddImageFileName = ideDrive.getName();
            // Trim file name to display, if needed
            hddImageFileName = ellipsizeFileName(hddImageFileName, MAX_FILE_NAME_DISPLAY_LENGTH,
                    FILE_NAME_DISPLAY_SUFFIX_LENGTH);
            hddNameTextView.setText(hddImageFileName);
        } else {
            hddNameTextView.setTextColor(getResources().getColor(R.color.hdd_detached));
            hddNameTextView.setText(R.string.hdd_detached);
        }
    }
}
