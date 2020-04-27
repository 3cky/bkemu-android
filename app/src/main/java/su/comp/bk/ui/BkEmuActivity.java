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
import android.os.Handler;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.transition.ChangeBounds;
import androidx.transition.Explode;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import org.apache.commons.lang.StringUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import su.comp.bk.BuildConfig;
import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.Computer.Configuration;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.addressing.IndexDeferredAddressingMode;
import su.comp.bk.arch.cpu.opcode.EmtOpcode;
import su.comp.bk.arch.cpu.opcode.JmpOpcode;
import su.comp.bk.arch.io.FloppyController;
import su.comp.bk.arch.io.FloppyController.FloppyDriveIdentifier;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.util.FileUtils;
import timber.log.Timber;

/**
 * Main application activity.
 */
public class BkEmuActivity extends AppCompatActivity {

    // State save/restore: Last accessed emulator binary image file URI
    private static final String LAST_BIN_IMAGE_FILE_URI = BkEmuActivity.class.getName() +
            "#last_bin_image_file_uri";
    // State save/restore: Last accessed emulator binary image file address
    private static final String LAST_BIN_IMAGE_FILE_LENGTH = BkEmuActivity.class.getName() +
            "#last_bin_image_file_address";
    // State save/restore: Last accessed emulator binary image file length
    private static final String LAST_BIN_IMAGE_FILE_ADDRESS = BkEmuActivity.class.getName() +
            "#last_bin_image_file_length";
    // State save/restore: Last selected disk image file path
    private static final String LAST_DISK_IMAGE_FILE_PATH = BkEmuActivity.class.getName() +
            "#last_disk_image_file_path";
    // State save/restore: Tape parameters block address
    private static final String TAPE_PARAMS_BLOCK_ADDRESS = BkEmuActivity.class.getName() +
            "#tape_params_block_addr";
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
    private static final String PREFS_KEY_FLOPPY_DRIVE_IMAGE =
            "su.comp.bk.arch.io.FloppyController.FloppyDrive/image:";

    // Last loaded emulator binary image address
    protected int lastBinImageAddress;
    // Last loaded emulator binary image length
    protected int lastBinImageLength;
    // Last loaded emulator binary image URI string
    protected String lastBinImageFileUri;

    // Last selected disk image path
    protected String lastDiskImageFilePath;

    // BK0011M .BMB10 syscall - Read subroutine address
    private static final int BK11_BMB10_READ_ADDRESS = 0155560;
    // BK0011M .BMB10 syscall - Save subroutine address
    private static final int BK11_BMB10_SAVE_ADDRESS = 0155150;
    // BK0011M .BMB10 syscall exit address
    private static final int BK11_BMB10_EXIT_ADDRESS = 0155026;
    // BK0011M .BMB10 syscall parameters block address
    private static final int BK11_BMB10_PARAMS_ADDRESS = 042602;
    // BK0011M memory pages config address
    private static final int BK11_PAGES_CONFIG_ADDRESS = 0114;
    // BK0011M memory pages default config
    private static final int BK11_PAGES_DEFAULT_CONFIG = 054002;

    // Tape parameters block address
    protected int tapeParamsBlockAddr;

    protected ViewGroup mainView;

    protected Transition onScreenControlsTransition;

    protected BkEmuView bkEmuView;

    protected Computer computer;

    protected String intentDataProgramImageUri;

    protected String intentDataDiskImageUri;

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
                Timber.e(e, "Can't load bootstrap emulator program image");
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
            String loadedTapeFileName = null;
            if (lastBinImageFileUri != null && tapeFileName != null && !tapeFileName.isEmpty()) {
                // Trying to load image file from last used location
                String[] tapeFileNames = FileUtils.getFileNameVariants(tapeFileName,
                        FileUtils.FILE_EXT_BINARY_IMAGES);
                String binImageFileUri = null;
                for (String tapeFileName : tapeFileNames) {
                    try {
                        binImageFileUri = FileUtils.replaceLastPathElement(lastBinImageFileUri,
                                tapeFileName);
                        loadBinImageFile(binImageFileUri);
                        loadedTapeFileName = tapeFileName;
                        isBinImageLoaded = true;
                        break;
                    } catch (Exception e) {
                        Timber.d("Can't load binary image from '" + binImageFileUri +
                                "': " + e.getMessage());
                    }
                }
            }
            if (isBinImageLoaded) {
                showBinImageFileLoadToast(true, loadedTapeFileName);
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
                    } else if (intentDataDiskImageUri != null) {
                        // Monitor command prompt, trying to boot from mounted disk image
                        intentDataDiskImageUri = null;
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
                        Timber.d("EMT 36, R1=0%s", Integer.toOctalString(tapeParamsBlockAddr));
                        handleTapeOperation(cpu);
                    }
                    break;
                case Computer.BUS_ERROR:
                    Timber.w("Can't get EMT number");
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
                    String tapeFileName = FileUtils.getTapeFileName(tapeFileNameData);
                    lastBinImageAddress = cpu.readMemory(false, tapeParamsBlockAddr + 2);
                    if (tapeCmdCode == 2) {
                        lastBinImageLength = cpu.readMemory(false, tapeParamsBlockAddr + 4);
                        Timber.d("save file: '" + tapeFileName + "', address: 0" +
                                (lastBinImageAddress > 0 ? Integer.toOctalString(lastBinImageAddress) : "") +
                                ", length: " + lastBinImageLength);
                        activityHandler.post(new TapeSaverTask(tapeFileName));
                    } else {
                        Timber.d("load file: '" + tapeFileName + "', address: 0" +
                                (lastBinImageAddress > 0 ? Integer.toOctalString(lastBinImageAddress) : ""));
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
            int pc = cpu.readRegister(false, Cpu.PC);
            if (pc == BK11_BMB10_READ_ADDRESS || pc == BK11_BMB10_SAVE_ADDRESS) {
                // .BMB10 BK0011 system call
                tapeParamsBlockAddr = BK11_BMB10_PARAMS_ADDRESS;
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
                    // Get file name
                    byte[] tapeFileNameData = new byte[MAX_TAPE_FILE_NAME_LENGTH];
                    for (int idx = 0; idx < tapeFileNameData.length; idx++) {
                        tapeFileNameData[idx] = (byte) cpu.readMemory(true,
                                tapeParamsBlockAddr + idx + 6);
                    }
                    String tapeFileName = FileUtils.getTapeFileName(tapeFileNameData);
                    lastBinImageAddress = cpu.readMemory(false, tapeParamsBlockAddr + 2);
                    Runnable tapeOperationTask;
                    if (tapeCmdCode == 0) {
                        lastBinImageLength = cpu.readMemory(false, tapeParamsBlockAddr + 4);
                        Timber.d("save file: '" + tapeFileName + "', address: 0" +
                                (lastBinImageAddress > 0 ? Integer.toOctalString(lastBinImageAddress) : "") +
                                ", length: " + lastBinImageLength);
                        tapeOperationTask = new TapeSaverTask(tapeFileName);
                    } else {
                        Timber.d("load file: '" + tapeFileName + "', address: 0" +
                                (lastBinImageAddress > 0 ? Integer.toOctalString(lastBinImageAddress) : ""));
                        tapeOperationTask = new TapeLoaderTask(tapeFileName);
                    }
                    // Setup memory map
                    int memoryMapConfig = computer.readMemory(false, BK11_PAGES_CONFIG_ADDRESS);
                    computer.writeMemory(false, Cpu.REG_SEL1, memoryMapConfig);
                    // Execute tape operation task
                    activityHandler.post(tapeOperationTask);
                    break;
                default:
                    break;
            }
        }
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
        Timber.d("onNewIntent(), Intent: %s", intent);
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
        if (BuildConfig.DEBUG) {
            Timber.uprootAll();
            Timber.plant(new Timber.DebugTree());
        }
        Timber.d("onCreate(), Intent: %s", getIntent());
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
        lastDiskImageFilePath = getIntent().getStringExtra(LAST_DISK_IMAGE_FILE_PATH);
        // Check for program/disk image file to run
        String intentDataString = getIntent().getDataString();
        if (intentDataString != null) {
            if (FileUtils.isFileNameExtensionMatched(intentDataString,
                    FileUtils.FILE_EXT_BINARY_IMAGES)) {
                this.intentDataProgramImageUri = intentDataString;
                return true;
            } else if (FileUtils.isFileNameExtensionMatched(intentDataString,
                    FileUtils.FILE_EXT_FLOPPY_DISK_IMAGES)) {
                this.intentDataDiskImageUri = intentDataString;
                return true;
            }
        }
        return false;
    }

    // Mount intent disk image, if it's provided
    private void mountIntentDataDiskImage() {
        if (intentDataDiskImageUri == null) {
            return;
        }
        File intentDataDiskImageFile = null;
        try {
            intentDataDiskImageFile = FileUtils.getUriLocalFile(getApplicationContext(),
                    intentDataDiskImageUri);
        } catch (IOException e) {
            Timber.e(e, "Can't get local file for floppy disk image %s",
                    intentDataDiskImageUri);
        }
        if (intentDataDiskImageFile == null
                || !mountFddImage(FloppyDriveIdentifier.A, intentDataDiskImageFile)) {
            intentDataDiskImageUri = null;
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
                Timber.e(e, "Can't restore computer state");
            }
        }
        if (!isComputerInitialized) {
            // Computer state can't be restored, do startup initialization
            try {
                Configuration currentConfiguration = getComputerConfiguration();
                Configuration startupConfiguration;
                if (intentDataProgramImageUri != null) {
                    startupConfiguration = Configuration.BK_0010_MONITOR;
                } else if (intentDataDiskImageUri != null) {
                    startupConfiguration = Configuration.BK_0010_KNGMD;
                } else {
                    startupConfiguration = currentConfiguration;
                }
                this.computer.configure(getResources(), startupConfiguration);
                if (startupConfiguration != currentConfiguration) {
                    setComputerConfiguration(startupConfiguration);
                }
                if (this.computer.getFloppyController() != null) {
                    mountAvailableFddImages();
                }
                this.computer.reset();
                isComputerInitialized = true;
            } catch (Exception e) {
                Timber.e(e, "Error while computer configuring");
            }
        }
        if (isComputerInitialized) {
            if (!computer.getConfiguration().isMemoryManagerPresent()) {
                computer.getCpu().setOnTrapListener(new TapeOperations10Handler());
            } else {
                TapeOperations11Handler handler = new TapeOperations11Handler();
                // Trap for `Jmp @CmdTab(R0)` operation (.BMB10 command router at address 154736)
                computer.getCpu().setOnOpcodeListener(JmpOpcode.OPCODE | Cpu.R0
                            | (IndexDeferredAddressingMode.CODE << 3), handler);
            }
            bkEmuView.setComputer(computer);
        } else {
            throw new IllegalStateException("Can't initialize computer state");
        }
    }

    @Override
    protected void onStart() {
        Timber.d("onStart()");
        this.computer.start();
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Timber.d("onRestart()");
        super.onRestart();
    }

    @Override
    protected void onResume() {
        Timber.d("onResume()");
        this.computer.resume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        Timber.d("onPause()");
        this.computer.pause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Timber.d("onStop()");
        this.computer.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy()");
        this.computer.release();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Timber.d("onSaveInstanceState()");
        // Save last accessed emulator image file parameters
        outState.putString(LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        outState.putInt(LAST_BIN_IMAGE_FILE_ADDRESS, lastBinImageAddress);
        outState.putInt(LAST_BIN_IMAGE_FILE_LENGTH, lastBinImageLength);
        // Save last disk image file path
        outState.putString(LAST_DISK_IMAGE_FILE_PATH, lastDiskImageFilePath);
        // Save tape parameters block address
        outState.putInt(TAPE_PARAMS_BLOCK_ADDRESS, tapeParamsBlockAddr);
        // Save on-screen control states
        outState.putBoolean(ON_SCREEN_JOYSTICK_VISIBLE, isOnScreenJoystickVisible());
        outState.putBoolean(ON_SCREEN_KEYBOARD_VISIBLE, isOnScreenKeyboardVisible());
        // Save computer state
        this.computer.saveState(getResources(), outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        Timber.d("onRestoreInstanceState()");
        // Restore last accessed emulator image file parameters
        lastBinImageFileUri = inState.getString(LAST_BIN_IMAGE_FILE_URI);
        lastBinImageAddress = inState.getInt(LAST_BIN_IMAGE_FILE_ADDRESS);
        lastBinImageLength = inState.getInt(LAST_BIN_IMAGE_FILE_LENGTH);
        // Restore last disk image file path
        lastDiskImageFilePath = inState.getString(LAST_DISK_IMAGE_FILE_PATH);
        // Restore tape parameters block address
        tapeParamsBlockAddr = inState.getInt(TAPE_PARAMS_BLOCK_ADDRESS);
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
            unmountFddImage(fddIdentifier);
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
            String fddImageFileName = fddController.getFloppyDriveImageFile(fddIdentifier).getName();
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
     * Try to mount all available floppy drive disk images.
     */
    protected void mountAvailableFddImages() {
        Timber.d("Mounting all available floppy disk images");
        FloppyController fddController = computer.getFloppyController();
        for (FloppyDriveIdentifier fddIdentifier : FloppyDriveIdentifier.values()) {
            String fddImagePath = readFddImagePath(fddIdentifier);
            if (fddImagePath != null) {
                doMountFddImage(fddController, fddIdentifier, new File(fddImagePath));
            }
        }
    }

    /**
     * Try to mount disk image to given floppy drive.
     * @param fddIdentifier floppy drive identifier to mount image
     * @param fddImageFile disk image file
     * @return true if image successfully mounted, false otherwise
     */
    protected boolean mountFddImage(FloppyDriveIdentifier fddIdentifier, File fddImageFile) {
        FloppyController fddController = computer.getFloppyController();
        if (doMountFddImage(fddController, fddIdentifier, fddImageFile)) {
            storeFddImagePath(fddIdentifier, fddImageFile.getPath());
            return true;
        }
        return false;
    }

    private boolean doMountFddImage(FloppyController fddController,
                                    FloppyDriveIdentifier fddIdentifier,
                                    File fddImageFile) {
        try {
            if (fddController != null) {
                fddController.mountDiskImage(fddImageFile, fddIdentifier, true);
                Timber.d("Mounted floppy disk image %s to drive %s",
                        fddImageFile, fddIdentifier);
                return true;
            }
        } catch (Exception e) {
            Timber.e(e, "Can't mount floppy disk image %s to drive %s",
                    fddImageFile, fddIdentifier);
        }
        return false;
    }

    /**
     * Unmount disk image from given floppy drive.
     * @param fddIdentifier floppy drive identifier to unmount image
     */
    protected void unmountFddImage(FloppyDriveIdentifier fddIdentifier) {
        FloppyController fddController = computer.getFloppyController();
        if (doUnmountFddImage(fddController, fddIdentifier)) {
            storeFddImagePath(fddIdentifier, null);
        }
    }

    private boolean doUnmountFddImage(FloppyController fddController,
                                      FloppyDriveIdentifier fddIdentifier) {
        try {
            if (fddController != null && fddController.isFloppyDriveMounted(fddIdentifier)) {
                fddController.unmountDiskImage(fddIdentifier);
                Timber.d("Unmounted floppy disk image from drive %s", fddIdentifier);
                return true;
            }
        } catch (Exception e) {
            Timber.e(e, "Can't unmount floppy disk image from drive %s", fddIdentifier);
        }
        return false;
    }

    /**
     * Show full changelog dialog.
     */
    private void showChangelogDialog() {
        new BkEmuChangeLog(this).getDialog(true).show();
    }

    protected void showBinImageFileLoadToast(boolean isImageLoaded, String imageName) {
        if (isImageLoaded) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_load_info, imageName,
                            lastBinImageAddress, lastBinImageLength),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_load_error, imageName),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Show BIN emulator image file to load selection dialog.
     * @param tapeFileName file name to load (or <code>null</code> to load any file)
     */
    protected void showBinImageFileLoadDialog(int requestCode, String tapeFileName) {
        Intent intent = new Intent(getBaseContext(), BkEmuFileDialog.class);
        String startPath = FileUtils.getSafeFileUriDirectoryPath(lastBinImageFileUri);
        intent.putExtra(BkEmuFileDialog.INTENT_START_PATH, startPath);
        if (tapeFileName != null && !tapeFileName.isEmpty()) {
            String[] tapeFileNames = FileUtils.getFileNameVariants(tapeFileName,
                    FileUtils.FILE_EXT_BINARY_IMAGES);
            intent.putExtra(BkEmuFileDialog.INTENT_FORMAT_FILTER, tapeFileNames);
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
        String startPath = new File(FileUtils.getSafeFileUriDirectoryPath(lastBinImageFileUri),
                tapeFileName).getPath();
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
        String startPath = FileUtils.getSafeFileDirectoryPath(lastDiskImageFilePath);
        intent.putExtra(BkEmuFileDialog.INTENT_START_PATH, startPath);
        intent.putExtra(BkEmuFileDialog.INTENT_FORMAT_FILTER,
                FileUtils.FILE_EXT_FLOPPY_DISK_IMAGES);
        intent.putExtra(FloppyDriveIdentifier.class.getName(), fddIdentifier.name());
        intent.putExtra(BkEmuFileDialog.INTENT_MODE, BkEmuFileDialog.Mode.LOAD);
        startActivityForResult(intent, REQUEST_MENU_DISK_IMAGE_FILE_SELECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Timber.d("onActivityResult()");
        switch (requestCode) {
            case REQUEST_MENU_BIN_IMAGE_FILE_LOAD:
                if (resultCode == Activity.RESULT_OK) {
                    String binImageFilePath = data.getStringExtra(BkEmuFileDialog.INTENT_RESULT_PATH);
                    Configuration configuration = computer.getConfiguration();
                    if (configuration.isMemoryManagerPresent() ||
                            configuration.isFloppyControllerPresent()) {
                        lastBinImageAddress = 0; // will get address from BIN image file header
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
                    File diskImageFile = new File(diskImageFilePath);
                    FloppyDriveIdentifier driveIdentifier = FloppyDriveIdentifier
                            .valueOf(data.getStringExtra(FloppyDriveIdentifier.class.getName()));
                    if (mountFddImage(driveIdentifier, diskImageFile)) {
                        lastDiskImageFilePath = diskImageFilePath;
                        showDialog(DIALOG_DISK_MANAGER);
                    } else {
                        Timber.e("can't mount disk image %s'", diskImageFilePath);
                        showDialog(DIALOG_DISK_MOUNT_ERROR);
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
            Timber.e(e, "Can't save emulator image");
        }
        return isImageSaved;
    }

    protected void doFinishBinImageSave(boolean isSuccess) {
        // Set result in parameters block
        final Computer comp = computer;
        if (!comp.getConfiguration().isMemoryManagerPresent()) { // BK0010
            // Set result code
            int resultCode = isSuccess ? 0 : 3; // OK / STOP
            comp.writeMemory(true, tapeParamsBlockAddr + 1, resultCode);
            // Return from EMT 36
            comp.getCpu().returnFromTrap(false);
        } else { // BK0011
            // Restore memory map
            comp.writeMemory(false, Cpu.REG_SEL1, BK11_PAGES_DEFAULT_CONFIG);
            // Set result code
            if (isSuccess) {
                comp.getCpu().clearPswFlagC();
            } else {
                comp.getCpu().setPswFlagC();
                comp.writeMemory(true, 052, 4); // STOP
            }
            // Exit from tape save routine
            comp.getCpu().writeRegister(false, Cpu.PC, BK11_BMB10_EXIT_ADDRESS);
        }
    }

    protected boolean binImageFileLoad(String binImageFilePath) {
        String binImageFileUri = "file:" + binImageFilePath;
        boolean isImageLoaded = doBinImageFileLoad(binImageFileUri);
        String imageName = isImageLoaded ? Uri.parse(binImageFileUri).getLastPathSegment()
                : binImageFilePath;
        showBinImageFileLoadToast(isImageLoaded, imageName);
        return isImageLoaded;
    }

    protected boolean doBinImageFileLoad(String binImageFileUri) {
        boolean isImageLoaded = false;
        try {
            loadBinImageFile(binImageFileUri);
            isImageLoaded = true;
        } catch (Exception e) {
            Timber.e(e, "Can't load emulator image");
        }
        return isImageLoaded;
    }

    protected void doFinishBinImageLoad(boolean isSuccess) {
        final Computer comp = computer;
        boolean isBk10 = !comp.getConfiguration().isMemoryManagerPresent();
        if (!isBk10) {
            // Restore BK0011 default memory map
            comp.writeMemory(false, Cpu.REG_SEL1, BK11_PAGES_DEFAULT_CONFIG);
        }
        if (!isSuccess) {
            // In case of error we just return to tape loader subroutine, because actually it's
            // better to allow user to break the load operation manually by the STOP key
            return;
        }
        // Set result in parameters block
        int tapeParamsBlockAddrNameIdx;
        if (isBk10) { // BK0010
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
            // Clear tape load routine error code
            comp.getCpu().writeMemory(true, 052, 0);
            // Exit from tape load routine
            comp.getCpu().writeRegister(false, Cpu.PC, BK11_BMB10_EXIT_ADDRESS);
        }
        // Write loaded image name to tape parameters block
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
        intent.putExtra(LAST_DISK_IMAGE_FILE_PATH, lastDiskImageFilePath);
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

    private String getPrefsFddImageKey(FloppyDriveIdentifier fddIdentifier) {
        return PREFS_KEY_FLOPPY_DRIVE_IMAGE + fddIdentifier.name();
    }

    /**
     * Read floppy drive image path from shared preferences.
     * @param fddIdentifier floppy drive identifier
     * @return stored floppy drive image path (null if no floppy drive image was mounted)
     */
    protected String readFddImagePath(FloppyDriveIdentifier fddIdentifier) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        return prefs.getString(getPrefsFddImageKey(fddIdentifier), null);
    }

    /**
     * Store floppy drive image path to shared preferences.
     * @param fddIdentifier floppy drive identifier
     * @param floppyDriveImagePath floppy drive image path
     */
    protected void storeFddImagePath(FloppyDriveIdentifier fddIdentifier,
                                     String floppyDriveImagePath) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(getPrefsFddImageKey(fddIdentifier), floppyDriveImagePath);
        prefsEditor.apply();
    }

    /**
     * Load program image in bin format (address/length/data) from given path.
     * @param binImageFileUri emulator image file URI
     * @return start address of loaded emulator image
     * @throws Exception in case of loading error
     */
    protected int loadBinImageFile(String binImageFileUri) throws Exception {
        Timber.d("Trying to load binary image: %s", binImageFileUri);
        byte[] binImageData = FileUtils.getUriContentData(getApplicationContext(), binImageFileUri);
        this.lastBinImageFileUri = binImageFileUri;
        return loadBinImage(binImageData);
    }

    /**
     * Load image in bin format (address/length/data) from byte array.
     * @param binImageData image data byte array
     * @return image load address
     * @throws IOException in case of loading error
     */
    public int loadBinImage(byte[] binImageData) throws IOException {
        if (binImageData.length < 5 || binImageData.length > 01000000) {
            throw new IllegalArgumentException("Invalid binary image file length: " +
                    binImageData.length);
        }
        DataInputStream imageDataInputStream = new DataInputStream(
                new ByteArrayInputStream(binImageData, 0, binImageData.length));
        int binImageAddress = (imageDataInputStream.readByte() & 0377)
                | ((imageDataInputStream.readByte() & 0377) << 8);
        if (lastBinImageAddress == 0) {
            lastBinImageAddress = binImageAddress;
        }
        lastBinImageLength = (imageDataInputStream.readByte() & 0377)
                | ((imageDataInputStream.readByte() & 0377) << 8);
        final Computer comp = computer;
        for (int imageIndex = 0; imageIndex < lastBinImageLength; imageIndex++) {
            if (!comp.writeMemory(true, lastBinImageAddress + imageIndex,
                    imageDataInputStream.read())) {
                throw new IllegalStateException("Can't write binary image data to address: 0" +
                        Integer.toOctalString(lastBinImageAddress + imageIndex));
            }
        }
        Timber.d("Loaded bin image file: address 0" + Integer.toOctalString(lastBinImageAddress) +
                ", length: " + lastBinImageLength);
        return lastBinImageAddress;
    }

    /**
     * Save program image in bin format (address/length/data) from given path.
     * @param binImageFilePath emulator image file path
     * @throws Exception in case of saving error
     */
    protected void saveBinImageFile(String binImageFilePath) throws Exception {
        Timber.d("saving image: %s", binImageFilePath);
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
        Timber.d("saved bin image file: address 0" + Integer.toOctalString(lastBinImageAddress) +
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
        Timber.d("switch on-screen keyboard visibility state: %s", (isVisible ? "ON" : "OFF"));
        KeyboardController keyboardController = computer.getKeyboardController();
        keyboardController.setOnScreenKeyboardVisibility(isVisible);
    }

    private void switchOnScreenJoystickVisibility(boolean isVisible) {
        Timber.d("switch on-screen joystick visibility state: %s", (isVisible ? "ON" : "OFF"));
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
        Timber.d("toggling screen mode");
        VideoController videoController = computer.getVideoController();
        videoController.setColorMode(!videoController.isColorMode());
    }

    private void resetComputer() {
        Timber.d("resetting computer");
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