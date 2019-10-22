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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
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

import org.apache.commons.lang.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.transition.ChangeBounds;
import androidx.transition.Explode;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
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

/**
 * Main application activity.
 */
public class BkEmuActivity extends AppCompatActivity {

    protected static final String TAG = BkEmuActivity.class.getName();

    // State save/restore: Last accessed emulator binary image file URI
    private static final String LAST_BIN_IMAGE_FILE_URI = BkEmuActivity.class.getName() +
            "#last_bin_image_file_uri";
    // State save/restore: Last accessed emulator binary image file address
    private static final String LAST_BIN_IMAGE_FILE_LENGTH = BkEmuActivity.class.getName() +
            "#last_bin_image_file_address";
    // State save/restore: Last accessed emulator binary image file length
    private static final String LAST_BIN_IMAGE_FILE_ADDRESS = BkEmuActivity.class.getName() +
            "#last_bin_image_file_length";
    // State save/restore: Last selected disk image file URI
    private static final String LAST_DISK_IMAGE_FILE_URI = BkEmuActivity.class.getName() +
            "#last_disk_image_file_uri";
    // State save/restore: On-screen joystick visibility state
    private static final String ON_SCREEN_JOYSTICK_VISIBLE = BkEmuActivity.class.getName() +
            "#on_screen_joystick_visible";
    // State save/restore: On-screen keyboard visibility state
    private static final String ON_SCREEN_KEYBOARD_VISIBLE =  BkEmuActivity.class.getName() +
            "#on_screen_keyboard_visible";


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
    private static final int REQUEST_EMT_BIN_IMAGE_FILE_SAVE = 4;

    // Google Play application URL to share
    private static final String APPLICATION_SHARE_URL = "https://play.google.com" +
    		"/store/apps/details?id=su.comp.bk";

    public static final int MAX_TAPE_FILE_NAME_LENGTH = 16;

    private static final int MAX_FILE_NAME_DISPLAY_LENGTH = 15;
    private static final int FILE_NAME_DISPLAY_SUFFIX_LENGTH = 3;

    private static final String PREFS_KEY_COMPUTER_CONFIGURATION = "su.comp.bk.a.c";

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

    protected String intentDataProgramImageUri;

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
            toggleOnScreenControlsVisibility();
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
                int startAddress = loadBinImageFile(intentDataProgramImageUri);
                intentDataProgramImageUri = null;
                // Start loaded image
                final Computer comp = computer;
                if (startAddress < STACK_TOP_ADDRESS) {
                    // Loaded autostarting image
                    comp.getCpu().returnFromTrap(false);
                } else {
                    // Loaded manually starting image
                    comp.getCpu().writeRegister(false, Cpu.PC, startAddress);
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
        TapeLoaderTask(String tapeFileName) {
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
     * Tape saver task.
     */
    class TapeSaverTask implements Runnable {
        private final String tapeFileName;
        TapeSaverTask(String tapeFileName) {
            this.tapeFileName = tapeFileName;
        }
        @Override
        public void run() {
            showBinImageFileSaveDialog(REQUEST_EMT_BIN_IMAGE_FILE_SAVE, tapeFileName);
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
                    if (intentDataProgramImageUri != null) {
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
                case 2: // Save to tape
                case 3: // Read from tape
                    computer.pause();
                    // Get file name
                    byte[] tapeFileNameData = new byte[MAX_TAPE_FILE_NAME_LENGTH];
                    for (int idx = 0; idx < tapeFileNameData.length; idx++) {
                        tapeFileNameData[idx] = (byte) cpu.readMemory(true,
                                tapeParamsBlockAddr + idx + 6);
                    }
                    String tapeFileName = getFileName(tapeFileNameData);
                    if (tapeCmdCode == 2) {
                        lastBinImageAddress = cpu.readMemory(false, tapeParamsBlockAddr + 2);
                        lastBinImageLength = cpu.readMemory(false, tapeParamsBlockAddr + 4);
                        Log.d(TAG, "BK0010 tape save file: '" + tapeFileName + "', address: 0" +
                                Integer.toOctalString(lastBinImageAddress) +
                                ", length: " + lastBinImageLength);
                        activityHandler.post(new TapeSaverTask(tapeFileName));
                    } else {
                        Log.d(TAG, "BK0010 tape load file: '" + tapeFileName + "'");
                        activityHandler.post(new TapeLoaderTask(tapeFileName));
                    }
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
                case 0: // Save to tape
                case 1: // Read from tape
                    computer.pause();
                    // FIXME handle memory pages setup
                    // Get file name
                    byte[] tapeFileNameData = new byte[MAX_TAPE_FILE_NAME_LENGTH];
                    for (int idx = 0; idx < tapeFileNameData.length; idx++) {
                        tapeFileNameData[idx] = (byte) cpu.readMemory(true,
                                tapeParamsBlockAddr + idx + 6);
                    }
                    String tapeFileName = getFileName(tapeFileNameData);
                    if (tapeCmdCode == 0) {
                        lastBinImageAddress = cpu.readMemory(false, tapeParamsBlockAddr + 2);
                        lastBinImageLength = cpu.readMemory(false, tapeParamsBlockAddr + 4);
                        Log.d(TAG, "BK0011 tape save file: '" + tapeFileName + "', address: " +
                                lastBinImageAddress + ", length: " + lastBinImageLength);
                        activityHandler.post(new TapeSaverTask(tapeFileName));
                    } else {
                        Log.d(TAG, "BK0011 tape load file: '" + tapeFileName + "'");
                        activityHandler.post(new TapeLoaderTask(tapeFileName));
                    }
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

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent(), Intent: " + intent);
        super.onNewIntent(intent);
        setIntent(intent);
        if (checkIntentData()) {
            initializeComputer(null);
            mountIntentDataDiskImage();
            setupOnScreenControls(false);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate(), Intent: " + getIntent());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initToolbar();
        this.activityHandler = new Handler();
        mainView = findViewById(R.id.main_view);
        bkEmuView = findViewById(R.id.emu_view);
        bkEmuView.setGestureListener(new GestureListener());

        checkIntentData();
        initializeComputer(savedInstanceState);
        mountIntentDataDiskImage();

        TransitionSet ts = new TransitionSet();
        ts.setOrdering(TransitionSet.ORDERING_TOGETHER);

        ChangeBounds cbt = new ChangeBounds();
        ts.addTransition(cbt);

        final Rect bkEmuViewRect = new Rect();
        Transition et = new Explode();
        et.setEpicenterCallback(new Transition.EpicenterCallback() {
            @Override
            public Rect onGetEpicenter(@NonNull Transition transition) {
                bkEmuView.getGlobalVisibleRect(bkEmuViewRect);
                return bkEmuViewRect;
            }
        });
        ts.addTransition(et);

        ts.setDuration(250L);

        onScreenControlsTransition = ts;

        setupOnScreenControls(true);

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

    private void setupOnScreenControls(boolean hideAllControls) {
        KeyboardController keyboardController = this.computer.getKeyboardController();
        ViewGroup keyboardView = findViewById(R.id.keyboard);
        keyboardController.setOnScreenKeyboardView(keyboardView);
        View joystickView = findViewById(R.id.joystick);
        View joystickDpadView = findViewById(R.id.joystick_dpad);
        View joystickButtonsView = findViewById(R.id.joystick_buttons);
        PeripheralPort peripheralPort = computer.getPeripheralPort();
        peripheralPort.setOnScreenJoystickViews(joystickView, joystickDpadView,
                joystickButtonsView);
        if (hideAllControls) {
            keyboardController.setOnScreenKeyboardVisibility(false);
            peripheralPort.setOnScreenJoystickVisibility(false);
        }
    }

    // Check intent data for program/disk image to mount
    private boolean checkIntentData() {
        // Check for last accessed program/disk file paths
        lastBinImageFileUri = getIntent().getStringExtra(LAST_BIN_IMAGE_FILE_URI);
        lastDiskImageFileUri = getIntent().getStringExtra(LAST_DISK_IMAGE_FILE_URI);
        // Check for program/disk image file to run
        String intentDataString = getIntent().getDataString();
        if (intentDataString != null) {
            if (BkEmuFileDialog.isFileNameFormatMatched(intentDataString,
                    BkEmuFileDialog.FORMAT_FILTER_BIN_IMAGES)) {
                this.intentDataProgramImageUri = intentDataString;
                return true;
            } else if (BkEmuFileDialog.isFileNameFormatMatched(intentDataString,
                    BkEmuFileDialog.FORMAT_FILTER_DISK_IMAGES)) {
                this.intentDataDiskImagePath = intentDataString;
                return true;
            }
        }
        return false;
    }

    // Mount intent disk image, if set
    private void mountIntentDataDiskImage() {
        if (this.intentDataDiskImagePath != null) {
            try {
                computer.getFloppyController().mountDiskImage(intentDataDiskImagePath,
                        FloppyDriveIdentifier.A, true);
            } catch (Exception e) {
                Log.e(TAG, "Can't mount bootstrap emulator disk image", e);
                this.intentDataDiskImagePath = null;
            }
        }
    }

    private void initToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.icon_toolbar);
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
                if (intentDataProgramImageUri != null) {
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d(TAG, "onSaveInstanceState()");
        // Save last accessed emulator image file parameters
        outState.putString(LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        outState.putInt(LAST_BIN_IMAGE_FILE_ADDRESS, lastBinImageAddress);
        outState.putInt(LAST_BIN_IMAGE_FILE_LENGTH, lastBinImageLength);
        // Save last disk image file path
        outState.putString(LAST_DISK_IMAGE_FILE_URI, lastDiskImageFileUri);
        // Save on-screen control states
        outState.putBoolean(ON_SCREEN_JOYSTICK_VISIBLE, isOnScreenJoystickVisible());
        outState.putBoolean(ON_SCREEN_KEYBOARD_VISIBLE, isOnScreenKeyboardVisible());
        // Save computer state
        this.computer.saveState(getResources(), outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        Log.d(TAG, "onRestoreInstanceState()");
        // Restore last accessed emulator image file parameters
        lastBinImageFileUri = inState.getString(LAST_BIN_IMAGE_FILE_URI);
        lastBinImageAddress = inState.getInt(LAST_BIN_IMAGE_FILE_ADDRESS);
        lastBinImageLength = inState.getInt(LAST_BIN_IMAGE_FILE_LENGTH);
        // Restore last disk image file path
        lastDiskImageFileUri = inState.getString(LAST_DISK_IMAGE_FILE_URI);
        // Restore on-screen control states
        switchOnScreenJoystickVisibility(inState.getBoolean(ON_SCREEN_JOYSTICK_VISIBLE));
        switchOnScreenKeyboardVisibility(inState.getBoolean(ON_SCREEN_KEYBOARD_VISIBLE));
        super.onRestoreInstanceState(inState);
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
            AlertDialog exitConfirmDialog = new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.exit_confirm_title)
                    .setMessage(R.string.exit_confirm_message)
                    .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                    .setNegativeButton(R.string.cancel, (dialog, which) -> BkEmuActivity.this.computer.resume())
                    .setOnKeyListener((dialog, keyCode, event) -> {
                        if (keyCode == KeyEvent.KEYCODE_BACK &&
                                event.getAction() == KeyEvent.ACTION_UP &&
                                !event.isCanceled()) {
                            BkEmuActivity.this.computer.resume();
                        }
                        return false;
                    })
                    .create();
            exitConfirmDialog.setCanceledOnTouchOutside(true);
            exitConfirmDialog.setOnCancelListener(dialog -> BkEmuActivity.this.computer.resume());
            exitConfirmDialog.show();
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
                toggleOnScreenKeyboardVisibility();
                return true;
            case R.id.menu_toggle_joystick:
                toggleOnScreenJoystickVisibility();
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
                List<String> modelList = new ArrayList<>();
                for (Configuration model: Configuration.values()) {
                    int modelNameId = getResources().getIdentifier(model.name().toLowerCase(),
                            "string", getPackageName());
                    modelList.add((modelNameId != 0) ? getString(modelNameId) : model.name());
                }
                models = modelList.toArray(new String[0]);
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.menu_select_model)
                    .setSingleChoiceItems(models, getComputerConfiguration().ordinal(),
                            (dialog, which) -> {
                                // Mark selected item by tag
                                ListView listView = ((AlertDialog) dialog).getListView();
                                listView.setTag(which);
                            })
                    .setPositiveButton(R.string.ok, (dialog, whichButton) -> {
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
                    })
                    .setNegativeButton(R.string.cancel, (dialog, whichButton) -> {
                        // Do nothing on cancel
                    })
                   .create();
            case DIALOG_ABOUT:
                Dialog aboutDialog = new Dialog(this);
                aboutDialog.setTitle(R.string.menu_about);
                aboutDialog.requestWindowFeature(Window.FEATURE_LEFT_ICON);
                aboutDialog.setContentView(R.layout.about_dialog);
                aboutDialog.getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                        android.R.drawable.ic_dialog_info);
                TextView versionTextView = aboutDialog.findViewById(R.id.about_version);
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
        fddView.setOnClickListener(v -> showMountDiskImageFileDialog(fddIdentifier));
        fddView.setOnLongClickListener(v -> {
            unmountDiskImage(fddIdentifier);
            updateFloppyDriveView(v, fddIdentifier);
            return true;
        });
    }

    protected void updateFloppyDriveView(final View fddView,
            final FloppyDriveIdentifier fddIdentifier) {
        FloppyController fddController = computer.getFloppyController();
        boolean isFddMounted = fddController.isFloppyDriveMounted(fddIdentifier);
        ImageView fddImageView = fddView.findViewWithTag("fdd_image");
        fddImageView.setImageResource(isFddMounted ? R.drawable.floppy_drive_loaded
                : R.drawable.floppy_drive);
        TextView fddFileTextView = fddView.findViewWithTag("fdd_file");
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
        TextView perfTextView = aboutDialog.findViewById(R.id.about_perf);
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
            String filePathString = fileUri.getPath();
            if (filePathString != null) {
                File filePath = new File(filePathString);
                File fileDir = filePath.getParentFile();
                if (fileDir.exists()) {
                    directoryPath = fileDir.getPath();
                }
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
        intent.putExtra(BkEmuFileDialog.INTENT_MODE, BkEmuFileDialog.Mode.LOAD);
        startActivityForResult(intent, requestCode);
    }

    /**
     * Show BIN emulator image file save dialog.
     * @param tapeFileName file name to save
     */
    protected void showBinImageFileSaveDialog(int requestCode, String tapeFileName) {
        Intent intent = new Intent(getBaseContext(), BkEmuFileDialog.class);
        String startPath = new File(getFileDirectoryPath(lastBinImageFileUri), tapeFileName).getPath();
        intent.putExtra(BkEmuFileDialog.INTENT_START_PATH, startPath);
        intent.putExtra(BkEmuFileDialog.INTENT_MODE, BkEmuFileDialog.Mode.SAVE);
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
        intent.putExtra(BkEmuFileDialog.INTENT_MODE, BkEmuFileDialog.Mode.LOAD);
        startActivityForResult(intent, REQUEST_MENU_DISK_IMAGE_FILE_SELECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
            case REQUEST_EMT_BIN_IMAGE_FILE_SAVE:
                boolean isImageSaved = false;
                if (resultCode == Activity.RESULT_OK) {
                    String binImageFilePath = data.getStringExtra(BkEmuFileDialog.INTENT_RESULT_PATH);
                    isImageSaved = binImageFileSave(binImageFilePath);
                }
                doFinishBinImageSave(isImageSaved);
                break;
            case REQUEST_MENU_DISK_IMAGE_FILE_SELECT:
                FloppyController floppyController = computer.getFloppyController();
                if (resultCode == Activity.RESULT_OK && floppyController != null) {
                    String diskImageFilePath = data.getStringExtra(BkEmuFileDialog.INTENT_RESULT_PATH);
                    if (diskImageFilePath == null) {
                        break;
                    }
                    String diskImageFileUri = new File(diskImageFilePath).toURI().toString();
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

    protected boolean binImageFileSave(String binImageFilePath) {
        boolean isImageSaved = doBinImageFileSave(binImageFilePath);
        if (isImageSaved) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_save_info,
                            binImageFilePath),
                            Toast.LENGTH_LONG)
                            .show();
        } else {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_save_error,
                            binImageFilePath),
                            Toast.LENGTH_LONG)
                            .show();
        }
        return isImageSaved;
    }

    protected boolean doBinImageFileSave(String binImageFilePath) {
        boolean isImageSaved = false;
        try {
            saveBinImageFile(binImageFilePath);
            isImageSaved = true;
        } catch (Exception e) {
            Log.e(TAG, "Can't save emulator image", e);
        }
        return isImageSaved;
    }

    protected void doFinishBinImageSave(boolean isImageSavedSuccessfully) {
        // Set result in parameters block
        final Computer comp = computer;
        if (!comp.getConfiguration().isMemoryManagerPresent()) { // BK0010
            // Set result code
            int resultCode = isImageSavedSuccessfully ? 0 : 3; // OK / STOP
            comp.writeMemory(true, tapeParamsBlockAddr + 1, resultCode);
            // Return from EMT 36
            comp.getCpu().returnFromTrap(false);
        } else { // BK0011
            // Set result code
            if (isImageSavedSuccessfully) {
                comp.getCpu().clearPswFlagC();
            } else {
                comp.getCpu().setPswFlagC();
                comp.writeMemory(true, 052, 4); // STOP
            }
            // Return from tape save routine
            comp.getCpu().writeRegister(false, Cpu.PC, computer.getCpu().pop());
        }
    }

    protected boolean binImageFileLoad(String binImageFilePath) {
        String binImageFileUri = "file:" + binImageFilePath;
        boolean isImageLoaded = doBinImageFileLoad(binImageFileUri);
        if (isImageLoaded) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_load_info,
                        lastBinImageAddress, lastBinImageLength),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_load_error,
                            binImageFilePath),
                    Toast.LENGTH_LONG)
                    .show();
        }
        return isImageLoaded;
    }

    protected boolean doBinImageFileLoad(String binImageFileUri) {
        boolean isImageLoaded = false;
        try {
            loadBinImageFile(binImageFileUri);
            isImageLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Can't load emulator image", e);
        }
        return isImageLoaded;
    }

    protected void doFinishBinImageLoad(boolean isImageLoadedSuccessfully) {
        // Set result in parameters block
        if (isImageLoadedSuccessfully) {
            final Computer comp = computer;
            int tapeParamsBlockAddrNameIdx;
            if (!comp.getConfiguration().isMemoryManagerPresent()) { // BK0010
                tapeParamsBlockAddrNameIdx = 26;
                // Set "OK" result code
                comp.writeMemory(true, tapeParamsBlockAddr + 1, 0);
                // Write loaded image start address
                comp.writeMemory(false, tapeParamsBlockAddr + 22, lastBinImageAddress);
                // Write loaded image length
                comp.writeMemory(false, tapeParamsBlockAddr + 24, lastBinImageLength);
                // Return from EMT 36
                comp.getCpu().returnFromTrap(false);
            } else { // BK0011
                tapeParamsBlockAddrNameIdx = 28;
                // Set "OK" result code
                comp.getCpu().clearPswFlagC();
                // Write loaded image start address
                comp.writeMemory(false, tapeParamsBlockAddr + 24, lastBinImageAddress);
                // Write loaded image length
                comp.writeMemory(false, tapeParamsBlockAddr + 26, lastBinImageLength);
                // Return from tape load routine
                comp.getCpu().writeRegister(false, Cpu.PC, comp.getCpu().pop());
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
                comp.getCpu().writeMemory(true, tapeParamsBlockAddr +
                        tapeParamsBlockAddrNameIdx + idx, tapeFileNameData[idx]);
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
        // Pass last accessed program/disk image file paths to new activity
        intent.putExtra(LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        intent.putExtra(LAST_DISK_IMAGE_FILE_URI, lastDiskImageFileUri);
        finish();
        startActivity(intent);
    }

    /**
     * Get current computer configuration as {@link Configuration} enum value.
     * @return configuration enum value
     */
    protected Configuration getComputerConfiguration() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String configName = prefs.getString(PREFS_KEY_COMPUTER_CONFIGURATION, null);
        return (configName == null) ? Configuration.BK_0010_BASIC : Configuration.valueOf(configName);
    }

    /**
     * Set current computer configuration set as {@link Configuration} enum value.
     * @param configuration configuration enum value to set
     */
    protected void setComputerConfiguration(Configuration configuration) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY_COMPUTER_CONFIGURATION, configuration.name());
        prefsEditor.apply();
    }

    /**
     * Load program image in bin format (address/length/data) from given path.
     * @param binImageFileUri emulator image file URI
     * @return start address of loaded emulator image
     * @throws Exception in case of loading error
     */
    protected int loadBinImageFile(String binImageFileUri) throws Exception {
        Log.d(TAG, "loading image: " + binImageFileUri);
        ByteArrayOutputStream binImageOutput = new ByteArrayOutputStream();
        try (BufferedInputStream binImageInput = new BufferedInputStream(
                new URL(binImageFileUri).openStream())) {
            int readByte;
            while ((readByte = binImageInput.read()) != -1) {
                binImageOutput.write(readByte);
            }
        }
        // Do nothing
        this.lastBinImageFileUri = binImageFileUri;
        return loadBinImage(binImageOutput.toByteArray());
    }

    /**
     * Load image in bin format (address/length/data) from byte array.
     * @param imageData image data byte array
     * @return image load address
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
        final Computer comp = computer;
        for (int imageIndex = 0; imageIndex < lastBinImageLength; imageIndex++) {
            if (!comp.writeMemory(true, lastBinImageAddress + imageIndex, imageDataInputStream.read())) {
                throw new IllegalStateException("Can't write binary image data to address: 0" +
                        Integer.toOctalString(lastBinImageAddress + imageIndex));
            }
        }
        Log.d(TAG, "loaded bin image file: address 0" + Integer.toOctalString(lastBinImageAddress) +
                ", length: " + lastBinImageLength);
        return lastBinImageAddress;
    }

    /**
     * Save program image in bin format (address/length/data) from given path.
     * @param binImageFilePath emulator image file path
     * @throws Exception in case of saving error
     */
    protected void saveBinImageFile(String binImageFilePath) throws Exception {
        Log.d(TAG, "saving image: " + binImageFilePath);
        ByteArrayOutputStream binImageOutput = new ByteArrayOutputStream();
        binImageOutput.write(lastBinImageAddress & 0377);
        binImageOutput.write((lastBinImageAddress >> 8) & 0377);
        binImageOutput.write(lastBinImageLength & 0377);
        binImageOutput.write((lastBinImageLength >> 8) & 0377);
        final Computer comp = computer;
        for (int imageIndex = 0; imageIndex < lastBinImageLength; imageIndex++) {
            int imageData = comp.readMemory(true, lastBinImageAddress + imageIndex);
            if (imageData == Computer.BUS_ERROR) {
                throw new IllegalStateException("Can't read binary image data from address: 0" +
                        Integer.toOctalString(lastBinImageAddress + imageIndex));
            }
            binImageOutput.write(imageData);
        }
        saveBinImage(binImageFilePath, binImageOutput.toByteArray());
        this.lastBinImageFileUri = "file:" + binImageFilePath;
    }

    /**
     * Save image in bin format (address/length/data) from byte array.
     * @param imagePath image file path
     * @param imageData image data byte array
     * @throws IOException in case of saving error
     */
    public void saveBinImage(String imagePath, byte[] imageData) throws IOException {
        try (BufferedOutputStream binImageOutput = new BufferedOutputStream(
                new FileOutputStream(imagePath))) {
            binImageOutput.write(imageData);
            binImageOutput.flush();
        }
        // Do nothing
        Log.d(TAG, "saved bin image file: address 0" + Integer.toOctalString(lastBinImageAddress) +
                ", length: " + lastBinImageLength);
    }

    private void shareApplication() {
        Intent appShareIntent = new Intent(Intent.ACTION_SEND);
        appShareIntent.setType("text/plain");
        appShareIntent.putExtra(Intent.EXTRA_TEXT, APPLICATION_SHARE_URL);
        startActivity(Intent.createChooser(appShareIntent, null));
    }

    protected boolean isOnScreenJoystickVisible() {
        return computer.getPeripheralPort().isOnScreenJoystickVisible();
    }

    protected boolean isOnScreenKeyboardVisible() {
        return computer.getKeyboardController().isOnScreenKeyboardVisible();
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
        startOnScreenControlsTransition();
        if (isOnScreenKeyboardVisible()) {
            // hide on-screen keyboard, show joystick
            switchOnScreenKeyboardVisibility(false);
            switchOnScreenJoystickVisibility(true);
        } else if (isOnScreenJoystickVisible()) {
            // hide on-screen controls
            switchOnScreenKeyboardVisibility(false);
            switchOnScreenJoystickVisibility(false);
        } else {
            // show on-screen keyboard
            switchOnScreenKeyboardVisibility(true);
        }
    }

    protected void toggleOnScreenJoystickVisibility() {
        startOnScreenControlsTransition();
        if (isOnScreenKeyboardVisible()) {
            switchOnScreenKeyboardVisibility(false);
        }
        switchOnScreenJoystickVisibility(!isOnScreenJoystickVisible());
    }

    protected void toggleOnScreenKeyboardVisibility() {
        startOnScreenControlsTransition();
        if (isOnScreenJoystickVisible()) {
            switchOnScreenJoystickVisibility(false);
        }
        switchOnScreenKeyboardVisibility(!isOnScreenKeyboardVisible());
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