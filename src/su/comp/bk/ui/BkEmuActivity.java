/*
 * Created: 16.02.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
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
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package su.comp.bk.ui;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.Computer.Configuration;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.addressing.IndexDeferredAddressingMode;
import su.comp.bk.arch.cpu.opcode.EmtOpcode;
import su.comp.bk.arch.cpu.opcode.JsrOpcode;
import su.comp.bk.arch.io.FloppyController;
import su.comp.bk.arch.io.FloppyController.FloppyDriveIdentifier;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.arch.io.VideoController;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.AppCompatActivity;

import com.transitionseverywhere.AutoTransition;
import com.transitionseverywhere.Transition;
import com.transitionseverywhere.TransitionManager;

/**
 * Main application activity.
 */
public class BkEmuActivity extends AppCompatActivity {

    protected static final String TAG = BkEmuActivity.class.getName();

    // State save/restore: Last loaded emulator binary image file URI
    private static final String LAST_BIN_IMAGE_FILE_URI = BkEmuActivity.class.getName() +
            "#last_bin_image_file_uri";
    // State save/restore: Last selected disk image file URI
    private static final String LAST_DISK_IMAGE_FILE_URI = BkEmuActivity.class.getName() +
            "#last_disk_image_file_uri";

    public final static int STACK_TOP_ADDRESS = 01000;

    // Dialog IDs
    private static final int DIALOG_COMPUTER_MODEL = 1;
    private static final int DIALOG_ABOUT = 2;
    private static final int DIALOG_DISK_MOUNT_ERROR = 3;
    private static final int DIALOG_DISK_MANAGER = 4;

    // Intent request IDs
    private static final int REQUEST_MENU_BIN_IMAGE_FILE_LOAD = 1;
    private static final int REQUEST_EMT_BIN_IMAGE_FILE_LOAD = 2;
    private static final int REQUEST_MENU_DISK_IMAGE_FILE_SELECT = 3;

    // Google Play application URL to share
    private static final String APPLICATION_SHARE_URL = "https://play.google.com" +
    		"/store/apps/details?id=su.comp.bk";

    public static final int MAX_TAPE_FILE_NAME_LENGTH = 16;

    private static final int MAX_FILE_NAME_DISPLAY_LENGTH = 15;
    private static final int FILE_NAME_DISPLAY_SUFFIX_LENGTH = 3;

    // Last loaded emulator binary image address
    protected int lastBinImageAddress;
    // Last loaded emulator binary image length
    protected int lastBinImageLength;
    // Last loaded emulator binary image URI string
    protected String lastBinImageFileUri;

    // Last selected disk image URI string
    protected String lastDiskImageFileUri;

    // Tape parameters block address
    protected int tapeParamsBlockAddr;

    protected ViewGroup mainView;

    protected Transition onScreenControlsTransition;

    protected BkEmuView bkEmuView;

    protected Computer computer;

    protected String intentDataProgramImagePath;

    protected String intentDataDiskImagePath;

    protected Handler activityHandler;

    /**
     * Gesture listener
     */
    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
        @Override
        public void onLongPress(MotionEvent e) {
            bkEmuView.setFpsDrawingEnabled(!bkEmuView.isFpsDrawingEnabled());
        }
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            toggleOnScreenControlsType();
            return true;
        }
    }

    /**
     * Trying to load program image from path from intent data.
     */
    class IntentDataProgramImageLoader implements Runnable {
        @Override
        public void run() {
            try {
                int startAddress = loadBinImageFile(intentDataProgramImagePath);
                intentDataProgramImagePath = null;
                // Start loaded image
                synchronized (computer) {
                    if (startAddress < STACK_TOP_ADDRESS) {
                        // Loaded autostarting image
                        computer.getCpu().returnFromTrap(false);
                    } else {
                        // Loaded manually starting image
                        computer.getCpu().writeRegister(false, Cpu.PC, startAddress);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Can't load bootstrap emulator program image", e);
            }
        }
    }

    /**
     * Tape loader task.
     */
    class TapeLoaderTask implements Runnable {
        private final String tapeFileName;
        public TapeLoaderTask(String tapeFileName) {
            this.tapeFileName = tapeFileName;
        }
        @Override
        public void run() {
            boolean isBinImageLoaded = false;
            if (lastBinImageFileUri != null) {
                String binImageFilePath = null;
                try {
                    // Trying to load image file from last used location
                    binImageFilePath = lastBinImageFileUri.substring(0,
                            lastBinImageFileUri.lastIndexOf('/') + 1).concat(tapeFileName);
                    loadBinImageFile(binImageFilePath);
                    isBinImageLoaded = true;
                } catch (Exception e) {
                    Log.d(TAG, "Can't load image from '" + binImageFilePath +
                            "': " + e.getMessage());
                }
            }
            if (isBinImageLoaded) {
                doFinishBinImageLoad(true);
                computer.resume();
            } else {
                // Can't load image file from last used location, select file manually
                showBinImageFileLoadDialog(REQUEST_EMT_BIN_IMAGE_FILE_LOAD, tapeFileName);
            }
        }
    }

    /**
     * BK0010 tape operations handler.
     */
    class TapeOperations10Handler implements Cpu.OnTrapListener {
        @Override
        public void onTrap(Cpu cpu, int trapVectorAddress) {
            switch (trapVectorAddress) {
                case Cpu.TRAP_VECTOR_EMT:
                    onEmtTrap(cpu);
                    break;
                default:
                    break;
            }
        }

        /**
         * EMT trap handler.
         * @param cpu {@link Cpu} reference
         */
        private void onEmtTrap(Cpu cpu) {
            int emtNumber = getTrapNumber(cpu, EmtOpcode.OPCODE);
            switch (emtNumber) {
                case 6: // EMT 6 - read char from keyboard
                    if (intentDataProgramImagePath != null) {
                        // Monitor command prompt, load program from path from intent data
                        activityHandler.post(new IntentDataProgramImageLoader());
                    } else if (intentDataDiskImagePath != null) {
                        // Monitor command prompt, trying to boot from mounted disk image
                        intentDataDiskImagePath = null;
                        // FIXME simulate bus error trap in case of boot error
                        cpu.push(cpu.readMemory(false, Cpu.TRAP_VECTOR_BUS_ERROR));
                        // Start booting
                        cpu.writeRegister(false, Cpu.PC, 0160000);
                    }
                    break;
                case 036: // EMT 36 - tape I/O
                    // Check EMT handler isn't hooked
                    int emtHandlerAddress = cpu.readMemory(false, Cpu.TRAP_VECTOR_EMT);
                    if (computer.isReadOnlyMemoryAddress(emtHandlerAddress)) {
                        tapeParamsBlockAddr = cpu.readRegister(false, Cpu.R1);
                        Log.d(TAG, "EMT 36, R1=0" + Integer.toOctalString(tapeParamsBlockAddr));
                        handleTapeOperation(cpu);
                    }
                    break;
                case Computer.BUS_ERROR:
                    Log.w(TAG, "Can't get EMT number");
                    break;
                default:
                    break;
            }
        }

        /**
         * Handle tape operation.
         * @param cpu {@link Cpu} reference
         */
        private void handleTapeOperation(Cpu cpu) {
            // Read command code
            int tapeCmdCode = cpu.readMemory(true, tapeParamsBlockAddr);
            switch (tapeCmdCode) {
                case 3: // Read from tape
                    computer.pause();
                    // Read file name
                    byte[] tapeFileNameData = new byte[MAX_TAPE_FILE_NAME_LENGTH];
                    for (int idx = 0; idx < tapeFileNameData.length; idx++) {
                        tapeFileNameData[idx] = (byte) cpu.readMemory(true,
                                tapeParamsBlockAddr + idx + 6);
                    }
                    String tapeFileName = getFileName(tapeFileNameData);
                    Log.d(TAG, "BK0010 tape load file: '" + tapeFileName + "'");
                    activityHandler.post(new TapeLoaderTask(tapeFileName));
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * BK0011 tape operations handler.
     */
    class TapeOperations11Handler implements Cpu.OnOpcodeListener {
        @Override
        public void onOpcodeExecuted(Cpu cpu, int opcode) {
            if (cpu.readRegister(false, Cpu.PC) == 0154620) {
                // .BMB10 BK0011 system call
                tapeParamsBlockAddr = cpu.readRegister(false, Cpu.R0);
                handleTapeOperation(cpu);
            }
        }

        /**
         * Handle tape operation.
         * @param cpu {@link Cpu} reference
         */
        private void handleTapeOperation(Cpu cpu) {
            // Read command code
            int tapeCmdCode = cpu.readMemory(true, tapeParamsBlockAddr);
            switch (tapeCmdCode) {
                case 1: // Read from tape
                    computer.pause();
                    // FIXME handle memory pages setup
                    // Read file name
                    byte[] tapeFileNameData = new byte[MAX_TAPE_FILE_NAME_LENGTH];
                    for (int idx = 0; idx < tapeFileNameData.length; idx++) {
                        tapeFileNameData[idx] = (byte) cpu.readMemory(true,
                                tapeParamsBlockAddr + idx + 6);
                    }
                    String tapeFileName = getFileName(tapeFileNameData);
                    Log.d(TAG, "BK0011 tape load file: '" + tapeFileName + "'");
                    activityHandler.post(new TapeLoaderTask(tapeFileName));
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Get string tape file name from its 16 bytes array presentation.
     * @param fileNameData internal file name array data
     * @return string file name presentation
     */
    public static String getFileName(byte[] fileNameData) {
        String fileName;
        if (fileNameData[0] != 0) { // BK0011 flag for any file
            try {
                fileName = new String(fileNameData, "koi8-r");
            } catch (UnsupportedEncodingException e) {
                fileName = new String(fileNameData);
            }
            fileName = fileName.trim().toUpperCase();
            // Strip spaces before extension (like in "NAME  .COD" in Basic)
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex).trim().concat(
                        fileName.substring(dotIndex));
            }
        } else {
            fileName = "";
        }
        return fileName;
    }

    /**
     * Get trap (EMT/TRAP) number using pushed to stack PC.
     * @param cpu {@link Cpu} reference
     * @param trapBaseOpcode EMT/TRAP base opcode
     * @return trap number or BUS_ERROR in case of addressing error
     */
    public static int getTrapNumber(Cpu cpu, int trapBaseOpcode) {
        int trapNumber = Computer.BUS_ERROR;
        int pushedPc = cpu.readMemory(false, cpu.readRegister(false, Cpu.SP));
        if (pushedPc != Computer.BUS_ERROR) {
            // Read trap opcode
            int trapOpcode = cpu.readMemory(false, pushedPc - 2);
            if (trapOpcode != Computer.BUS_ERROR) {
                // Extract trap number from opcode
                trapNumber = trapOpcode - trapBaseOpcode;
            }
        }
        return trapNumber;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate(), Intent: " + getIntent());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initToolbar();
        this.activityHandler = new Handler();
        mainView = (ViewGroup) findViewById(R.id.main_view);
        bkEmuView = (BkEmuView) findViewById(R.id.emu_view);
        bkEmuView.setGestureListener(new GestureListener());
        String intentDataString = getIntent().getDataString();
        if (intentDataString != null) {
            if (BkEmuFileDialog.isFileNameFormatMatched(intentDataString,
                    BkEmuFileDialog.FORMAT_FILTER_BIN_IMAGES)) {
                this.intentDataProgramImagePath = intentDataString;
            } else if (BkEmuFileDialog.isFileNameFormatMatched(intentDataString,
                    BkEmuFileDialog.FORMAT_FILTER_DISK_IMAGES)) {
                this.intentDataDiskImagePath = intentDataString;
            }
        }
        initializeComputer(savedInstanceState);
        // Mount intent disk image, if set
        if (this.intentDataDiskImagePath != null) {
            try {
                computer.getFloppyController().mountDiskImage(intentDataDiskImagePath,
                        FloppyDriveIdentifier.A, true);
            } catch (Exception e) {
                Log.e(TAG, "Can't mount bootstrap emulator disk image", e);
                this.intentDataDiskImagePath = null;
            }
        }

        onScreenControlsTransition = new AutoTransition();
        onScreenControlsTransition.setDuration(200l);

        KeyboardController keyboardController = this.computer.getKeyboardController();
        ViewGroup keyboardView = (ViewGroup) findViewById(R.id.keyboard);
        keyboardController.setOnScreenKeyboardView(keyboardView);
        keyboardController.setOnScreenKeyboardVisibility(false);
        View joystickView = findViewById(R.id.joystick);
        View joystickDpadView = findViewById(R.id.joystick_dpad);
        View joystickButtonsView = findViewById(R.id.joystick_buttons);
        PeripheralPort peripheralPort = computer.getPeripheralPort();
        peripheralPort.setOnScreenJoystickViews(new View[] { joystickView,
                joystickDpadView, joystickButtonsView });
        peripheralPort.setOnScreenJoystickVisibility(false);

        // Show change log with latest changes once after application update
        BkEmuChangeLog changeLog = new BkEmuChangeLog(this);
        if (changeLog.isCurrentVersionGreaterThanLast()) {
            // Store current version to preferences store
            changeLog.saveCurrentVersionName();
            // Show change log dialog but not at the first run
            if (!changeLog.isFirstRun()) {
                changeLog.getDialog(false).show();
            }
        }
    }

    private void initToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.icon);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeComputer(Bundle savedInstanceState) {
        this.computer = new Computer();
        boolean isComputerInitialized = false;
        if (savedInstanceState != null) {
            // Trying to restore computer state
            try {
                this.computer.restoreState(getResources(), savedInstanceState);
                isComputerInitialized = true;
            } catch (Exception e) {
                Log.d(TAG, "Can't restore computer state", e);
            }
        }
        if (!isComputerInitialized) {
            // Computer state can't be restored, do startup initialization
            try {
                Configuration configuration = getComputerConfiguration();
                if (intentDataProgramImagePath != null) {
                    configuration = Configuration.BK_0010_MONITOR;
                } else if (intentDataDiskImagePath != null) {
                    configuration = Configuration.BK_0010_KNGMD;
                }
                this.computer.configure(getResources(), configuration);
                this.computer.reset();
                isComputerInitialized = true;
            } catch (Exception e) {
                Log.e(TAG, "Error while computer configuring", e);
            }
        }
        if (isComputerInitialized) {
            if (!computer.getConfiguration().isMemoryManagerPresent()) {
                computer.getCpu().setOnTrapListener(new TapeOperations10Handler());
            } else {
                TapeOperations11Handler handler = new TapeOperations11Handler();
                computer.getCpu().setOnOpcodeListener(JsrOpcode.OPCODE | Cpu.PC | (Cpu.PC << 6)
                            | (IndexDeferredAddressingMode.CODE << 3), handler);
            }
            bkEmuView.setComputer(computer);
        } else {
            throw new IllegalStateException("Can't initialize computer state");
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart()");
        this.computer.start();
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart()");
        super.onRestart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        this.computer.resume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        this.computer.pause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        this.computer.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        this.computer.release();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState()");
        // Save last emulator image file path
        outState.putString(LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        // Save last disk image file path
        outState.putString(LAST_DISK_IMAGE_FILE_URI, lastDiskImageFileUri);
        this.computer.saveState(getResources(), outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore last emulator image file path
        lastBinImageFileUri = savedInstanceState.getString(LAST_BIN_IMAGE_FILE_URI);
        // Restore last disk image file path
        lastDiskImageFileUri = savedInstanceState.getString(LAST_DISK_IMAGE_FILE_URI);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return this.computer.getKeyboardController().handleKeyCode(keyCode, true)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return this.computer.getKeyboardController().handleKeyCode(keyCode, false)
                || super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        KeyboardController keyboardController = this.computer.getKeyboardController();
        PeripheralPort peripheralPort = this.computer.getPeripheralPort();
        if (keyboardController.isOnScreenKeyboardVisible()) {
            startOnScreenControlsTransition();
            keyboardController.setOnScreenKeyboardVisibility(false);
        } else if (peripheralPort.isOnScreenJoystickVisible()) {
            startOnScreenControlsTransition();
            peripheralPort.setOnScreenJoystickVisibility(false);
        } else {
            this.computer.pause();
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.exit_confirm_title)
                    .setMessage(R.string.exit_confirm_message)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    BkEmuActivity.this.computer.resume();
                                }
                            })
                    .setOnKeyListener(new OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface dialog, int keyCode,
                                        KeyEvent event) {
                                    if (keyCode == KeyEvent.KEYCODE_BACK &&
                                            event.getAction() == KeyEvent.ACTION_UP &&
                                            !event.isCanceled()) {
                                        BkEmuActivity.this.computer.resume();
                                    }
                                    return false;
                                }
                            })
                    .show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isFloppyControllerAttached = computer.getConfiguration().isFloppyControllerPresent();
        menu.findItem(R.id.menu_disk_manager).setEnabled(isFloppyControllerAttached);
        menu.findItem(R.id.menu_disk_manager).setVisible(isFloppyControllerAttached);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_toggle_keyboard:
                toggleOnScreenControlsVisibility();
                return true;
            case R.id.menu_toggle_screen_mode:
                toggleScreenMode();
                return true;
            case R.id.menu_reset:
                resetComputer();
                return true;
            case R.id.menu_change_model:
                showDialog(DIALOG_COMPUTER_MODEL);
                return true;
            case R.id.menu_open_image:
                showBinImageFileLoadDialog(REQUEST_MENU_BIN_IMAGE_FILE_LOAD, null);
                return true;
            case R.id.menu_disk_manager:
                showDialog(DIALOG_DISK_MANAGER);
                return true;
            case R.id.menu_about:
                showDialog(DIALOG_ABOUT);
                return true;
            case R.id.menu_share:
                shareApplication();
                return true;
            case R.id.menu_changelog:
                showChangelogDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_COMPUTER_MODEL:
                final CharSequence[] models;
                List<String> modelList = new ArrayList<String>();
                for (Configuration model: Configuration.values()) {
                    int modelNameId = getResources().getIdentifier(model.name().toLowerCase(),
                            "string", getPackageName());
                    modelList.add((modelNameId != 0) ? getString(modelNameId) : model.name());
                }
                models = modelList.toArray(new String[modelList.size()]);
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.menu_select_model)
                    .setSingleChoiceItems(models, getComputerConfiguration().ordinal(),
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Mark selected item by tag
                            ListView listView = ((AlertDialog) dialog).getListView();
                            listView.setTag(Integer.valueOf(which));
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Get tagged selected item, if any
                            ListView listView = ((AlertDialog) dialog).getListView();
                            Integer selected = (Integer) listView.getTag();
                            if (selected != null) {
                                Configuration config = Configuration.values()[selected];
                                if (computer.getConfiguration() != config) {
                                    // Set new computer configuration and restart activity
                                    setComputerConfiguration(config);
                                    restartActivity(null);
                                }
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing on cancel
                        }
                    })
                   .create();
            case DIALOG_ABOUT:
                Dialog aboutDialog = new Dialog(this);
                aboutDialog.setTitle(R.string.menu_about);
                aboutDialog.requestWindowFeature(Window.FEATURE_LEFT_ICON);
                aboutDialog.setContentView(R.layout.about_dialog);
                aboutDialog.getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                        android.R.drawable.ic_dialog_info);
                TextView versionTextView = (TextView) aboutDialog.findViewById(R.id.about_version);
                try {
                    versionTextView.setText(getResources().getString(R.string.about_version,
                            getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
                } catch (NameNotFoundException e) {
                }
                return aboutDialog;
            case DIALOG_DISK_MANAGER:
                Dialog fddManagerDialog = new Dialog(this);
                fddManagerDialog.setTitle(R.string.menu_disk_manager);
                fddManagerDialog.setContentView(R.layout.fdd_mgr_dialog);
                return fddManagerDialog;
            case DIALOG_DISK_MOUNT_ERROR:
                return new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.err)
                    .setMessage(R.string.dialog_disk_mount_error)
                    .setPositiveButton(R.string.ok, null)
                    .create();
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_DISK_MANAGER:
                prepareDiskManagerDialog(dialog);
                break;
            case DIALOG_ABOUT:
                prepareAboutDialog(dialog);
                break;
        }
        super.onPrepareDialog(id, dialog);
    }

    protected void prepareDiskManagerDialog(Dialog dialog) {
        prepareFloppyDriveView(dialog.findViewById(R.id.fdd_layout_a),
                FloppyDriveIdentifier.A);
        prepareFloppyDriveView(dialog.findViewById(R.id.fdd_layout_b),
                FloppyDriveIdentifier.B);
        prepareFloppyDriveView(dialog.findViewById(R.id.fdd_layout_c),
                FloppyDriveIdentifier.C);
        prepareFloppyDriveView(dialog.findViewById(R.id.fdd_layout_d),
                FloppyDriveIdentifier.D);
    }

    protected void prepareFloppyDriveView(final View fddView,
            final FloppyDriveIdentifier fddIdentifier) {
        updateFloppyDriveView(fddView, fddIdentifier);
        fddView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMountDiskImageFileDialog(fddIdentifier);
            }
        });
        fddView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                unmountDiskImage(fddIdentifier);
                updateFloppyDriveView(v, fddIdentifier);
                return true;
            }
        });
    }

    protected void updateFloppyDriveView(final View fddView,
            final FloppyDriveIdentifier fddIdentifier) {
        FloppyController fddController = computer.getFloppyController();
        boolean isFddMounted = fddController.isFloppyDriveMounted(fddIdentifier);
        ImageView fddImageView = (ImageView) fddView.findViewWithTag("fdd_image");
        fddImageView.setImageResource(isFddMounted ? R.drawable.floppy_drive_loaded
                : R.drawable.floppy_drive);
        TextView fddFileTextView = (TextView) fddView.findViewWithTag("fdd_file");
        if (isFddMounted) {
            fddFileTextView.setTextColor(getResources().getColor(R.color.fdd_loaded));
            String fddImageFileName = Uri.parse(fddController.getFloppyDriveImageUri(
                    fddIdentifier)).getLastPathSegment();
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
            fddFileTextView.setTextColor(getResources().getColor(R.color.fdd_empty));
            fddFileTextView.setText(R.string.fdd_empty);
        }
    }

    protected void prepareAboutDialog(Dialog aboutDialog) {
        TextView perfTextView = (TextView) aboutDialog.findViewById(R.id.about_perf);
        float effectiveClockFrequency = this.computer.getEffectiveClockFrequency();
        perfTextView.setText(getResources().getString(R.string.about_perf,
                effectiveClockFrequency / 1000f, effectiveClockFrequency
                / this.computer.getClockFrequency() * 100f));
    }

    /**
     * Unmount disk image from given floppy drive.
     * @param fddIdentifier floppy drive identifier to unmount image
     */
    protected void unmountDiskImage(FloppyDriveIdentifier fddIdentifier) {
        try {
            FloppyController fddController = computer.getFloppyController();
            if (fddController != null && fddController.isFloppyDriveMounted(fddIdentifier)) {
                fddController.unmountDiskImage(fddIdentifier);
            }
        } catch (Exception e) {
            Log.e(TAG, "Floppy drive " + fddIdentifier + " unmounting error", e);
        }
    }

    /**
     * Show full changelog dialog.
     */
    private void showChangelogDialog() {
        new BkEmuChangeLog(this).getDialog(true).show();
    }

    /**
     * Get directory path for given file URI.
     * @param fileUriString file URI as string or <code>null</code>
     * @return directory path or external storage path if file URI is <code>null</code>
     * or given file directory doesn't exist
     */
    private static String getFileDirectoryPath(String fileUriString) {
        String directoryPath = Environment.getExternalStorageDirectory().getPath();
        if (fileUriString != null) {
            Uri fileUri = Uri.parse(fileUriString);
            File filePath = new File(fileUri.getPath());
            File fileDir = filePath.getParentFile();
            if (fileDir.exists()) {
                directoryPath = fileDir.getPath();
            }
        }
        return directoryPath;
    }

    /**
     * Show BIN emulator image file to load selection dialog.
     * @param tapeFileName file name to load (or <code>null</code> to load any file)
     */
    protected void showBinImageFileLoadDialog(int requestCode, String tapeFileName) {
        Intent intent = new Intent(getBaseContext(), BkEmuFileDialog.class);
        String startPath = getFileDirectoryPath(lastBinImageFileUri);
        intent.putExtra(BkEmuFileDialog.INTENT_START_PATH, startPath);
        if (tapeFileName != null && tapeFileName.length() > 0) {
            intent.putExtra(BkEmuFileDialog.INTENT_FORMAT_FILTER, new String[] { tapeFileName });
        }
        startActivityForResult(intent, requestCode);
    }

    /**
     * Show floppy disk image to mount selection dialog.
     * @param fddIdentifier floppy drive identifier to mount image
     */
    protected void showMountDiskImageFileDialog(FloppyDriveIdentifier fddIdentifier) {
        Intent intent = new Intent(getBaseContext(), BkEmuFileDialog.class);
        String startPath = getFileDirectoryPath(lastDiskImageFileUri);
        intent.putExtra(BkEmuFileDialog.INTENT_START_PATH, startPath);
        intent.putExtra(BkEmuFileDialog.INTENT_FORMAT_FILTER,
                BkEmuFileDialog.FORMAT_FILTER_DISK_IMAGES);
        intent.putExtra(FloppyDriveIdentifier.class.getName(), fddIdentifier.name());
        startActivityForResult(intent, REQUEST_MENU_DISK_IMAGE_FILE_SELECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult()");
        switch (requestCode) {
            case REQUEST_MENU_BIN_IMAGE_FILE_LOAD:
                if (resultCode == Activity.RESULT_OK) {
                    String binImageFilePath = data.getStringExtra(BkEmuFileDialog.INTENT_RESULT_PATH);
                    Configuration configuration = computer.getConfiguration();
                    if (configuration.isMemoryManagerPresent() ||
                            configuration.isFloppyControllerPresent()) {
                        binImageFileLoad(binImageFilePath);
                    } else {
                        Uri binImageFileUri = new Uri.Builder().scheme("file")
                                .path(binImageFilePath).build();
                        restartActivity(binImageFileUri);
                    }
                }
                break;
            case REQUEST_EMT_BIN_IMAGE_FILE_LOAD:
                boolean isImageLoaded = false;
                if (resultCode == Activity.RESULT_OK) {
                    String binImageFilePath = data.getStringExtra(BkEmuFileDialog.INTENT_RESULT_PATH);
                    isImageLoaded = binImageFileLoad(binImageFilePath);
                }
                doFinishBinImageLoad(isImageLoaded);
                break;
            case REQUEST_MENU_DISK_IMAGE_FILE_SELECT:
                FloppyController floppyController = computer.getFloppyController();
                if (resultCode == Activity.RESULT_OK && floppyController != null) {
                    String diskImageFilePath = data.getStringExtra(BkEmuFileDialog.INTENT_RESULT_PATH);
                    String diskImageFileUri = "file:" + diskImageFilePath;
                    try {
                        FloppyDriveIdentifier driveIdentifier = FloppyDriveIdentifier
                            .valueOf(data.getStringExtra(FloppyDriveIdentifier.class.getName()));
                        floppyController.mountDiskImage(diskImageFileUri, driveIdentifier, false);
                        lastDiskImageFileUri = diskImageFileUri;
                        showDialog(DIALOG_DISK_MANAGER);
                    } catch (Exception e) {
                        showDialog(DIALOG_DISK_MOUNT_ERROR);
                        Log.e(TAG, "can't mount disk image '" + diskImageFileUri + "'", e);
                    }
                }
                break;
            default:
                break;
        }
    }

    protected boolean binImageFileLoad(String binImageFilePath) {
        boolean isImageLoaded = doBinImageFileLoad(binImageFilePath);
        if (isImageLoaded) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_info,
                        lastBinImageAddress, lastBinImageLength),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_error,
                            binImageFilePath),
                    Toast.LENGTH_LONG)
                    .show();
        }
        return isImageLoaded;
    }

    protected boolean doBinImageFileLoad(String binImageFilePath) {
        boolean isImageLoaded = false;
        try {
            loadBinImageFile("file:" + binImageFilePath);
            isImageLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Can't load emulator image", e);
        }
        return isImageLoaded;
    }

    protected void doFinishBinImageLoad(boolean isImageLoadedSuccessfully) {
        // Set result in parameters block
        if (isImageLoadedSuccessfully) {
            synchronized (computer) {
                int tapeParamsBlockAddrNameIdx;
                if (!computer.getConfiguration().isMemoryManagerPresent()) { // BK0010
                    tapeParamsBlockAddrNameIdx = 26;
                    // Set "OK" result code
                    computer.writeMemory(true, tapeParamsBlockAddr + 1, 0);
                    // Write loaded image start address
                    computer.writeMemory(false, tapeParamsBlockAddr + 22, lastBinImageAddress);
                    // Write loaded image length
                    computer.writeMemory(false, tapeParamsBlockAddr + 24, lastBinImageLength);
                    // Return from EMT 36
                    computer.getCpu().returnFromTrap(false);
                } else { // BK0011
                    tapeParamsBlockAddrNameIdx = 28;
                    // Set "OK" result code
                    computer.getCpu().clearPswFlagC();
                    // Write loaded image start address
                    computer.writeMemory(false, tapeParamsBlockAddr + 24, lastBinImageAddress);
                    // Write loaded image length
                    computer.writeMemory(false, tapeParamsBlockAddr + 26, lastBinImageLength);
                    // Return from tape load routine
                    computer.getCpu().writeRegister(false, Cpu.PC, computer.getCpu().pop());
                }
                // Write loaded image name
                String tapeFileName = StringUtils.substringAfterLast(lastBinImageFileUri, "/");
                tapeFileName = StringUtils.substring(tapeFileName, 0, MAX_TAPE_FILE_NAME_LENGTH);
                byte[] tapeFileNameBuffer;
                try {
                    tapeFileNameBuffer = tapeFileName.getBytes("koi8-r");
                } catch (UnsupportedEncodingException e) {
                    tapeFileNameBuffer = tapeFileName.getBytes();
                }
                byte[] tapeFileNameData = new byte[MAX_TAPE_FILE_NAME_LENGTH];
                Arrays.fill(tapeFileNameData, (byte) ' ');
                System.arraycopy(tapeFileNameBuffer, 0, tapeFileNameData, 0,
                        Math.min(tapeFileNameBuffer.length, MAX_TAPE_FILE_NAME_LENGTH));
                for (int idx = 0; idx < tapeFileNameData.length; idx++) {
                    computer.getCpu().writeMemory(true, tapeParamsBlockAddr +
                            tapeParamsBlockAddrNameIdx + idx, tapeFileNameData[idx]);
                }
            }
        }
    }

    /**
     * Do activity restart.
     * @param binImageFileUri emulator image file {@link Uri} to set to restarted activity
     * (or <code>null</code> to start activity without emulator image set)
     */
    protected void restartActivity(Uri binImageFileUri) {
        Intent intent = getIntent();
        intent.setData(binImageFileUri);
        finish();
        startActivity(intent);
    }

    /**
     * Get current computer configuration as {@link Configuration} enum value.
     * @return configuration enum value
     */
    protected Configuration getComputerConfiguration() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String configName = prefs.getString(Configuration.class.getName(), null);
        return (configName == null) ? Configuration.BK_0010_BASIC : Configuration.valueOf(configName);
    }

    /**
     * Set current computer configuration set as {@link Configuration} enum value.
     * @param configuration configuration enum value to set
     */
    protected void setComputerConfiguration(Configuration configuration) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(Configuration.class.getName(), configuration.name());
        prefsEditor.commit();
    }

    /**
     * Load program image in bin format (address/length/data) from given path.
     * @param binImageFilePath emulator image file path
     * @return start address of loaded emulator image
     * @throws Exception in case of loading error
     */
    protected int loadBinImageFile(String binImageFilePath) throws Exception {
        Log.d(TAG, "loading image: " + binImageFilePath);
        BufferedInputStream binImageInput = new BufferedInputStream(
                new URL(binImageFilePath).openStream());
        ByteArrayOutputStream binImageOutput = new ByteArrayOutputStream();
        int readByte;
        while ((readByte = binImageInput.read()) != -1) {
            binImageOutput.write(readByte);
        }
        this.lastBinImageFileUri = binImageFilePath;
        return loadBinImage(binImageOutput.toByteArray());
    }

    /**
     * Load image in bin format (address/length/data) from byte array.
     * @param imageData image data byte array
     * @throws IOException in case of loading error
     */
    public int loadBinImage(byte[] imageData) throws IOException {
        if (imageData.length < 5 || imageData.length > 01000000) {
            throw new IllegalArgumentException("Invalid binary image file length: " +
                    imageData.length);
        }
        DataInputStream imageDataInputStream = new DataInputStream(
                new ByteArrayInputStream(imageData, 0, imageData.length));
        lastBinImageAddress = (imageDataInputStream.readByte() & 0377)
                | ((imageDataInputStream.readByte() & 0377) << 8);
        lastBinImageLength = (imageDataInputStream.readByte() & 0377)
                | ((imageDataInputStream.readByte() & 0377) << 8);
        synchronized (computer) {
            for (int imageIndex = 0; imageIndex < lastBinImageLength; imageIndex++) {
                if (!computer.writeMemory(true, lastBinImageAddress + imageIndex, imageDataInputStream.read())) {
                    throw new IllegalStateException("Can't write binary image data to address: 0" +
                            Integer.toOctalString(lastBinImageAddress) + imageIndex);
                }
            }
        }
        Log.d(TAG, "loaded bin image file: address 0" + Integer.toOctalString(lastBinImageAddress) +
                ", length: " + lastBinImageLength);
        return lastBinImageAddress;
    }

    private void shareApplication() {
        Intent appShareIntent = new Intent(Intent.ACTION_SEND);
        appShareIntent.setType("text/plain");
        appShareIntent.putExtra(Intent.EXTRA_TEXT, APPLICATION_SHARE_URL);
        startActivity(Intent.createChooser(appShareIntent, null));
    }

    private void switchOnScreenKeyboardVisibility(boolean isVisible) {
        Log.d(TAG, "switch on-screen keyboard visibility state: " + (isVisible ? "ON" : "OFF"));
        KeyboardController keyboardController = computer.getKeyboardController();
        keyboardController.setOnScreenKeyboardVisibility(isVisible);
    }

    private void switchOnScreenJoystickVisibility(boolean isVisible) {
        Log.d(TAG, "switch on-screen joystick visibility state: " + (isVisible ? "ON" : "OFF"));
        PeripheralPort peripheralPort = computer.getPeripheralPort();
        peripheralPort.setOnScreenJoystickVisibility(isVisible);
    }

    protected void toggleOnScreenControlsVisibility() {
        boolean isOnScreenKeyboardVisible = computer.getKeyboardController().isOnScreenKeyboardVisible();
        boolean isOnScreenJoystickVisible = computer.getPeripheralPort().isOnScreenJoystickVisible();
        startOnScreenControlsTransition();
        if (isOnScreenKeyboardVisible) {
            // hide on-screen keyboard
            switchOnScreenKeyboardVisibility(false);
        } else if (isOnScreenJoystickVisible) {
            // hide on-screen joystick
            switchOnScreenJoystickVisibility(false);
        } else {
            // show on-screen keyboard
            switchOnScreenKeyboardVisibility(true);
        }
    }

    protected void toggleOnScreenControlsType() {
        boolean isOnScreenKeyboardVisible = computer.getKeyboardController().isOnScreenKeyboardVisible();
        boolean isOnScreenJoystickVisible = computer.getPeripheralPort().isOnScreenJoystickVisible();
        if (isOnScreenJoystickVisible || isOnScreenKeyboardVisible) {
            startOnScreenControlsTransition();
            if (isOnScreenKeyboardVisible) {
                switchOnScreenKeyboardVisibility(false);
                switchOnScreenJoystickVisibility(true);
            } else {
                switchOnScreenJoystickVisibility(false);
                switchOnScreenKeyboardVisibility(true);
            }
        }
    }

    protected void startOnScreenControlsTransition() {
        TransitionManager.beginDelayedTransition(mainView, onScreenControlsTransition);
    }

    private void toggleScreenMode() {
        Log.d(TAG, "toggling screen mode");
        VideoController videoController = computer.getVideoController();
        videoController.setColorMode(!videoController.isColorMode());
    }

    private void resetComputer() {
        Log.d(TAG, "resetting computer");
        Configuration config = getComputerConfiguration();
        if (computer.getConfiguration() != config) {
            // Set new computer configuration and restart activity
            setComputerConfiguration(config);
            restartActivity(null);
        } else {
            computer.reset();
        }
    }
}