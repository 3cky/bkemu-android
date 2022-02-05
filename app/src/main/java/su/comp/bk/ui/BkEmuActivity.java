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

import static su.comp.bk.arch.io.disk.IdeController.IF_0;
import static su.comp.bk.arch.io.disk.IdeController.IF_1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import su.comp.bk.BuildConfig;
import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.Computer.Configuration;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.addressing.IndexDeferredAddressingMode;
import su.comp.bk.arch.cpu.opcode.EmtOpcode;
import su.comp.bk.arch.cpu.opcode.JmpOpcode;
import su.comp.bk.arch.io.disk.DiskImage;
import su.comp.bk.arch.io.disk.FloppyController;
import su.comp.bk.arch.io.disk.FloppyController.FloppyDriveIdentifier;
import su.comp.bk.arch.io.VideoController;
import su.comp.bk.arch.io.audio.AudioOutput;
import su.comp.bk.arch.io.disk.FileDiskImage;
import su.comp.bk.arch.io.disk.IdeController;
import su.comp.bk.arch.io.disk.SafDiskImage;
import su.comp.bk.ui.joystick.JoystickManager;
import su.comp.bk.ui.keyboard.KeyboardManager;
import su.comp.bk.util.FileUtils;
import timber.log.Timber;

/**
 * Main application activity.
 */
public class BkEmuActivity extends AppCompatActivity implements View.OnSystemUiVisibilityChangeListener {
    // State save/restore: Key prefix
    private static final String STATE_PREFIX = "BkEmuActivity#";
    // State save/restore: Last accessed emulator binary image file URI
    private static final String STATE_LAST_BIN_IMAGE_FILE_URI = STATE_PREFIX +
            "last_bin_image_file_uri";
    // State save/restore: Last accessed emulator binary image file address
    private static final String STATE_LAST_BIN_IMAGE_FILE_LENGTH = STATE_PREFIX +
            "last_bin_image_file_address";
    // State save/restore: Last accessed emulator binary image file length
    private static final String STATE_LAST_BIN_IMAGE_FILE_ADDRESS = STATE_PREFIX +
            "last_bin_image_file_length";
    // State save/restore: Last selected floppy disk image drive name
    private static final String STATE_LAST_FLOPPY_DISK_IMAGE_DRIVE_NAME = STATE_PREFIX +
            "last_floppy_disk_image_drive_name";
    // State save/restore: Last selected hard disk image IDE interface identifier
    private static final String STATE_LAST_IDE_DRIVE_IMAGE_INTERFACE_ID = STATE_PREFIX +
            "last_hard_disk_image_ide_interface_id";
    // State save/restore: Tape parameters block address
    private static final String STATE_TAPE_PARAMS_BLOCK_ADDRESS = STATE_PREFIX +
            "tape_params_block_addr";
    // State save/restore: On-screen joystick visibility state
    private static final String STATE_ON_SCREEN_JOYSTICK_VISIBLE = STATE_PREFIX +
            "on_screen_joystick_visible";

    /** Array of file extensions for binary images */
    public final static String[] FILE_EXT_BINARY_IMAGES = new String[] { ".BIN" };
    /** Array of file extensions for floppy disk images */
    public final static String[] FILE_EXT_FLOPPY_DISK_IMAGES = new String[] { ".BKD" };
    /** Array of file extensions for hard disk images */
    public final static String[] FILE_EXT_HARD_DISK_IMAGES = new String[] { ".HDI" };
    /** Array of file extensions for raw disk images */
    public final static String[] FILE_EXT_RAW_DISK_IMAGES = new String[] { ".IMG" };

    public final static int STACK_TOP_ADDRESS = 01000;

    // Dialog IDs
    private static final int DIALOG_COMPUTER_MODEL = 1;
    private static final int DIALOG_ABOUT = 2;
    private static final int DIALOG_FLOPPY_DISK_MOUNT_ERROR = 3;
    private static final int DIALOG_IDE_DRIVE_ATTACH_ERROR = 4;

    // Intent request IDs
    private static final int REQUEST_MENU_BIN_IMAGE_FILE_LOAD = 1;
    private static final int REQUEST_EMT_BIN_IMAGE_FILE_LOAD = 2;
    private static final int REQUEST_MENU_FLOPPY_DISK_IMAGE_FILE_SELECT = 3;
    private static final int REQUEST_EMT_BIN_IMAGE_FILE_SAVE = 4;
    private static final int REQUEST_MENU_IDE_DRIVE_IMAGE_FILE_SELECT = 5;

    // Google Play application URL to share
    private static final String APPLICATION_SHARE_URL = "https://play.google.com" +
            "/store/apps/details?id=su.comp.bk";

    public static final int MAX_TAPE_FILE_NAME_LENGTH = 16;

    private static final String PREFS_KEY_COMPUTER_CONFIGURATION = "su.comp.bk.a.c";
    private static final String PREFS_KEY_FLOPPY_DRIVE_PREFIX =
            "su.comp.bk.arch.io.FloppyController.FloppyDrive/";
    private static final String PREFS_KEY_FLOPPY_DRIVE_IMAGE =
            PREFS_KEY_FLOPPY_DRIVE_PREFIX + "image:";
    private static final String PREFS_KEY_FLOPPY_DRIVE_WRITE_PROTECT_MODE =
            PREFS_KEY_FLOPPY_DRIVE_PREFIX + "writeProtectMode:";
    private static final String PREFS_KEY_IDE_DRIVE_PREFIX =
            "su.comp.bk.arch.io.IdeController.IdeDrive";
    private static final String PREFS_KEY_IDE_DRIVE_IMAGE = "image:";
    private static final String PREFS_KEY_AUDIO_VOLUME =
            "su.comp.bk.arch.io.audio.AudioOutput/volume";

    // Last loaded emulator binary image address
    protected int lastBinImageAddress;
    // Last loaded emulator binary image length
    protected int lastBinImageLength;
    // Last loaded emulator binary image URI string
    protected String lastBinImageFileUri;

    // Last selected floppy disk image drive identifier
    protected FloppyDriveIdentifier lastFloppyDiskImageDrive;

    // Last selected hard disk image IDE interface identifier
    protected int lastIdeDriveImageInterfaceId;

    // BK0011M .BMB10 syscall - Read subroutine address
    private static final int BK11_BMB10_READ_ADDRESS = 0155560;
    // BK0011M .BMB10 syscall - Save subroutine address
    private static final int BK11_BMB10_SAVE_ADDRESS = 0155150;
    // BK0011M .BMB10 syscall exit address
    private static final int BK11_BMB10_EXIT_ADDRESS = 0155026;
    // BK0011M .BMB10 syscall parameters block address
    private static final int BK11_BMB10_PARAMS_ADDRESS = 042602;
    // BK0011M memory banks config address
    private static final int BK11_BANKS_CONFIG_ADDRESS = 0114;
    // BK0011M memory banks default config
    private static final int BK11_BANKS_DEFAULT_CONFIG = 054002;

    // Tape parameters block address
    protected int tapeParamsBlockAddr;

    protected ViewGroup mainView;

    protected Transition onScreenControlsTransition;

    protected BkEmuView bkEmuView;

    protected Computer computer;

    protected String intentDataProgramImageUri;

    protected String intentDataFloppyDiskImageUri;
    protected String intentDataHardDiskImageUri;

    protected Handler activityHandler;

    private Toolbar toolbar;

    private KeyboardManager keyboardManager;
    private JoystickManager joystickManager;

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
                int startAddress = loadBinImageFile(Uri.parse(intentDataProgramImageUri));
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
            showBeforeBinImageFileLoadToast(tapeFileName);
            showBinImageFileLoadDialog(REQUEST_EMT_BIN_IMAGE_FILE_LOAD, tapeFileName);
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
            if (trapVectorAddress == Cpu.TRAP_VECTOR_EMT) {
                onEmtTrap(cpu);
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
                    } else if (intentDataFloppyDiskImageUri != null) {
                        // Monitor command prompt, trying to boot from mounted floppy disk image
                        intentDataFloppyDiskImageUri = null;
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
                    int memoryMapConfig = computer.readMemory(false, BK11_BANKS_CONFIG_ADDRESS);
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
        restartActivity();
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

        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(this);

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

        keyboardManager = new KeyboardManager(this);
        joystickManager = new JoystickManager(this);

        setupOnScreenControls();

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

    private void setupOnScreenControls() {
        keyboardManager.setKeyboardController(computer.getKeyboardController());
        joystickManager.setPeripheralPort(computer.getPeripheralPort());

        keyboardManager.setOnScreenKeyboardVisibility(false);
        joystickManager.setOnScreenJoystickVisibility(false);
    }

    // Check intent data for program/disk image to mount
    private void checkIntentData() {
        // Check for last accessed program/disk file paths
        lastBinImageFileUri = getIntent().getStringExtra(STATE_LAST_BIN_IMAGE_FILE_URI);
        // Check for program/disk image file to run
        String intentDataString = getIntent().getDataString();
        if (intentDataString != null) {
            Uri intentDataUri = Uri.parse(intentDataString);
            String intentDataFileName = FileUtils.resolveUriFileName(this, intentDataUri);
            if (FileUtils.isFileNameExtensionMatched(intentDataFileName, FILE_EXT_BINARY_IMAGES)) {
                this.intentDataProgramImageUri = intentDataString;
            } else if (FileUtils.isFileNameExtensionMatched(intentDataFileName,
                    FILE_EXT_FLOPPY_DISK_IMAGES)) {
                this.intentDataFloppyDiskImageUri = intentDataString;
            } else if (FileUtils.isFileNameExtensionMatched(intentDataFileName,
                    FILE_EXT_HARD_DISK_IMAGES)) {
                this.intentDataHardDiskImageUri = intentDataString;
            } else if (FileUtils.isFileNameExtensionMatched(intentDataFileName,
                    FILE_EXT_RAW_DISK_IMAGES)) {
                // Try to determine intent data type from its file length
                long intentDataLength = FileUtils.getUriFileLength(this, intentDataUri);
                if (intentDataLength > FloppyController.MAX_BYTES_PER_DISK) {
                    this.intentDataHardDiskImageUri = intentDataString;
                } else if (intentDataLength > 0) {
                    this.intentDataFloppyDiskImageUri = intentDataString;
                }
            }
        }
    }

    // Mount intent disk image, if it's provided
    private void mountIntentDataDiskImage() {
        String intentDataDiskImageUri;
        if (intentDataFloppyDiskImageUri != null) {
            intentDataDiskImageUri = intentDataFloppyDiskImageUri;
        } else if (intentDataHardDiskImageUri != null) {
            intentDataDiskImageUri = intentDataHardDiskImageUri;
        } else {
            return;
        }

        Uri intentDataDiskImageLocalUri;
        try {
            intentDataDiskImageLocalUri = FileUtils.getLocalFileUri(getApplicationContext(),
                    intentDataDiskImageUri);
        } catch (IOException e) {
            Timber.e(e, "Can't get local file for intent disk image URI: %s",
                    intentDataDiskImageUri);
            return;
        }

        boolean isIntentDataDiskImageMounted = false;

        if (intentDataFloppyDiskImageUri != null) {
            DiskImage intentDataFloppyDiskImage = openDiskImage(intentDataDiskImageLocalUri);
            if (intentDataFloppyDiskImage != null) {
                isIntentDataDiskImageMounted = mountFloppyDiskImage(FloppyDriveIdentifier.A,
                        intentDataFloppyDiskImage, false);
                if (isIntentDataDiskImageMounted) {
                    detachIdeDrive(IF_0); // ensure we will boot from floppy disk
                }
            } else {
                Timber.w("Can't open intent floppy disk image: %s",
                        intentDataDiskImageLocalUri);
            }
        } else if (intentDataHardDiskImageUri != null) {
            DiskImage intentDataHardDiskImage = openDiskImage(intentDataDiskImageLocalUri);
            if (intentDataHardDiskImage != null) {
                isIntentDataDiskImageMounted = attachIdeDrive(IF_0, intentDataHardDiskImage);
            } else {
                Timber.w("Can't open intent hard disk image: %s",
                        intentDataDiskImageLocalUri);
            }
        }

        if (!isIntentDataDiskImageMounted) {
            intentDataHardDiskImageUri = null;
            intentDataFloppyDiskImageUri = null;
        }
    }

    private void initToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.icon_toolbar);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initializeComputer(Bundle savedInstanceState) {
        computer = new Computer();

        boolean isComputerInitialized = false;

        if (savedInstanceState != null) {
            // Trying to restore computer state
            try {
                Configuration storedConfiguration = Computer.getStoredConfiguration(savedInstanceState);
                if (storedConfiguration != null) {
                    computer.configure(getResources(), storedConfiguration);
                    initializeComputerDisks();
                    computer.restoreState(savedInstanceState);
                    isComputerInitialized = true;
                }
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
                } else if (intentDataFloppyDiskImageUri != null) {
                    startupConfiguration = Configuration.BK_0011M_KNGMD;
                } else if (intentDataHardDiskImageUri != null) {
                    startupConfiguration = Configuration.BK_0011M_SMK512;
                } else {
                    startupConfiguration = currentConfiguration;
                }
                computer.configure(getResources(), startupConfiguration);
                if (startupConfiguration != currentConfiguration) {
                    setComputerConfiguration(startupConfiguration);
                }
                initializeComputerDisks();
                computer.reset();
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
            for (AudioOutput audioOutput : computer.getAudioOutputs()) {
                audioOutput.setVolume(readAudioOutputVolume(audioOutput.getName(),
                        audioOutput.getDefaultVolume()));
            }
            bkEmuView.setComputer(computer);
        } else {
            throw new IllegalStateException("Can't initialize computer state");
        }
    }

    private void initializeComputerDisks() {
        if (computer.getFloppyController() != null) {
            mountAvailableFloppyDiskImages();
        }
        if (computer.getIdeController() != null) {
            attachAvailableIdeDriveImages();
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

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            leaveFullscreenMode();
            toolbar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Timber.d("onSaveInstanceState()");
        // Save last accessed emulator image file parameters
        outState.putString(STATE_LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        outState.putInt(STATE_LAST_BIN_IMAGE_FILE_ADDRESS, lastBinImageAddress);
        outState.putInt(STATE_LAST_BIN_IMAGE_FILE_LENGTH, lastBinImageLength);
        // Save last disk image drive name
        outState.putString(STATE_LAST_FLOPPY_DISK_IMAGE_DRIVE_NAME, (lastFloppyDiskImageDrive != null)
                ? lastFloppyDiskImageDrive.name() : null);
        // Save last selected IDE drive image interface identifier
        outState.putInt(STATE_LAST_IDE_DRIVE_IMAGE_INTERFACE_ID, lastIdeDriveImageInterfaceId);
        // Save tape parameters block address
        outState.putInt(STATE_TAPE_PARAMS_BLOCK_ADDRESS, tapeParamsBlockAddr);
        // Save on-screen control states
        outState.putBoolean(STATE_ON_SCREEN_JOYSTICK_VISIBLE, isOnScreenJoystickVisible());
        keyboardManager.saveState(outState);
        // Save computer state
        computer.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        Timber.d("onRestoreInstanceState()");
        // Restore last accessed emulator image file parameters
        lastBinImageFileUri = inState.getString(STATE_LAST_BIN_IMAGE_FILE_URI);
        lastBinImageAddress = inState.getInt(STATE_LAST_BIN_IMAGE_FILE_ADDRESS);
        lastBinImageLength = inState.getInt(STATE_LAST_BIN_IMAGE_FILE_LENGTH);
        // Restore last disk image drive
        String lastDiskImageDriveName = inState.getString(STATE_LAST_FLOPPY_DISK_IMAGE_DRIVE_NAME);
        lastFloppyDiskImageDrive = (lastDiskImageDriveName != null)
                ? FloppyDriveIdentifier.valueOf(lastDiskImageDriveName) : null;
        // Restore last selected IDE drive image interface identifier
        lastIdeDriveImageInterfaceId = inState.getInt(STATE_LAST_IDE_DRIVE_IMAGE_INTERFACE_ID);
        // Restore tape parameters block address
        tapeParamsBlockAddr = inState.getInt(STATE_TAPE_PARAMS_BLOCK_ADDRESS);
        // Restore on-screen control states
        switchOnScreenJoystickVisibility(inState.getBoolean(STATE_ON_SCREEN_JOYSTICK_VISIBLE));
        keyboardManager.restoreState(inState);
        super.onRestoreInstanceState(inState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyboardManager.handleKeyCode(keyCode, true)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return keyboardManager.handleKeyCode(keyCode, false)
                || super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (keyboardManager.isOnScreenKeyboardVisible()) {
            startOnScreenControlsTransition();
            keyboardManager.setOnScreenKeyboardVisibility(false);
        } else if (joystickManager.isOnScreenJoystickVisible()) {
            startOnScreenControlsTransition();
            joystickManager.setOnScreenJoystickVisibility(false);
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
        boolean isImmersiveModeSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
        menu.findItem(R.id.menu_fullscreen_mode).setEnabled(isImmersiveModeSupported);
        menu.findItem(R.id.menu_fullscreen_mode).setVisible(isImmersiveModeSupported);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_toggle_keyboard) {
            toggleOnScreenKeyboardVisibility();
            return true;
        } else if (itemId == R.id.menu_toggle_joystick) {
            toggleOnScreenJoystickVisibility();
            return true;
        } else if (itemId == R.id.menu_switch_display_mode) {
            switchDisplayMode();
            return true;
        } else if (itemId == R.id.menu_reset) {
            resetComputer();
            return true;
        } else if (itemId == R.id.menu_change_model) {
            showDialog(DIALOG_COMPUTER_MODEL);
            return true;
        } else if (itemId == R.id.menu_open_image) {
            showBinImageFileLoadDialog(REQUEST_MENU_BIN_IMAGE_FILE_LOAD, null);
            return true;
        } else if (itemId == R.id.menu_disk_manager) {
            showDiskManagerDialog();
            return true;
        } else if (itemId == R.id.menu_about) {
            showDialog(DIALOG_ABOUT);
            return true;
        } else if (itemId == R.id.menu_volume) {
            showVolumeDialog();
            return true;
        } else if (itemId == R.id.menu_fullscreen_mode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                enterFullscreenMode();
            }
            return true;
        } else if (itemId == R.id.menu_screenshot) {
            takeScreenshot();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_COMPUTER_MODEL:
                return createComputerModelDialog();
            case DIALOG_ABOUT:
                return createAboutDialog();
            case DIALOG_FLOPPY_DISK_MOUNT_ERROR:
                return createFloppyDiskMountErrorDialog();
            case DIALOG_IDE_DRIVE_ATTACH_ERROR:
                return createIdeDriveAttachErrorDialog();
        }
        return null;
    }

    private Dialog createFloppyDiskMountErrorDialog() {
        return new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.err)
            .setMessage(R.string.dialog_floppy_disk_mount_error)
            .setPositiveButton(R.string.ok, null)
            .create();
    }

    private Dialog createIdeDriveAttachErrorDialog() {
        return new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.err)
            .setMessage(R.string.dialog_ide_drive_attach_error)
            .setPositiveButton(R.string.ok, null)
            .create();
    }

    private Dialog createAboutDialog() {
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
            // Do nothing
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
            aboutDialog.dismiss();
            showChangelogDialog();
        });
        TextView shareTextView = aboutDialog.findViewById(R.id.about_share);
        shareTextView.setOnClickListener(v -> {
            aboutDialog.dismiss();
            shareApplication();
        });
        return aboutDialog;
    }

    private Dialog createComputerModelDialog() {
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
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_ABOUT) {
            prepareAboutDialog(dialog);
        }
        super.onPrepareDialog(id, dialog);
    }

    protected void prepareAboutDialog(Dialog aboutDialog) {
        TextView perfTextView = aboutDialog.findViewById(R.id.about_perf);
        float effectiveClockFrequency = this.computer.getEffectiveClockFrequency();
        perfTextView.setText(getResources().getString(R.string.about_perf,
                effectiveClockFrequency / 1000f, effectiveClockFrequency
                / this.computer.getClockFrequency() * 100f));
    }

    private DiskImage openDiskImage(Uri diskImageLocationUri) {
        return openDiskImage(diskImageLocationUri.toString());
    }

    private DiskImage openDiskImage(String diskImageLocation) {
        DiskImage diskImage = null;
        Uri diskImageLocationUri = Uri.parse(diskImageLocation);
        String diskImageLocationUriScheme = diskImageLocationUri.getScheme();
        if ("content".equals(diskImageLocationUriScheme)) {
            // Open as SAF disk image
            try {
                diskImage = new SafDiskImage(getApplicationContext(), diskImageLocationUri);
            } catch (IOException e) {
                Timber.i(e, "Can't open location %s as SAF disk image", diskImageLocation);
            }
        } else if (diskImageLocationUriScheme == null || "file".equals(diskImageLocationUriScheme)) {
            // Open as file disk image
            String diskImageFilePath = diskImageLocationUri.getPath();
            if (diskImageFilePath != null) {
                try {
                    diskImage = new FileDiskImage(new File(diskImageFilePath));
                } catch (IOException e) {
                    Timber.i(e, "Can't open location %s as file disk image", diskImageLocation);
                }
            }
        } else {
            Timber.w("Unknown disk image location type: %s", diskImageLocation);
        }
        return diskImage;
    }

    /**
     * Try to mount all available floppy disk images.
     */
    protected void mountAvailableFloppyDiskImages() {
        Timber.d("Mounting all available floppy disk images");
        FloppyController fddController = computer.getFloppyController();
        for (FloppyDriveIdentifier fddIdentifier : FloppyDriveIdentifier.values()) {
            String fddImageLocation = getLastMountedFloppyDiskImageLocation(fddIdentifier);
            boolean isFddWriteProtectMode = getLastMountedFloppyDiskImageWriteProtectMode(fddIdentifier);
            if (fddImageLocation != null) {
                DiskImage fddImage = openDiskImage(fddImageLocation);
                if (fddImage != null) {
                    doMountFloppyDiskImage(fddController, fddIdentifier, fddImage, isFddWriteProtectMode);
                } else {
                    resetLastFloppyDriveMountData(fddIdentifier);
                }
            }
        }
    }

    /**
     * Try to attach all available IDE drive images.
     */
    protected void attachAvailableIdeDriveImages() {
        Timber.d("Attaching all available IDE drive images");
        IdeController ideController = computer.getIdeController();
        for (int ideInterfaceId = IF_0; ideInterfaceId <= IF_1; ideInterfaceId++) {
            String ideDriveImageLocation = getLastAttachedIdeDriveImageLocation(ideInterfaceId);
            if (ideDriveImageLocation != null) {
                DiskImage ideDriveImage = openDiskImage(ideDriveImageLocation);
                if (ideDriveImage != null) {
                    doAttachIdeDrive(ideController, ideInterfaceId, ideDriveImage);
                } else {
                    setLastAttachedIdeDriveImageLocation(ideInterfaceId, null);
                }
            }
        }
    }

    /**
     * Try to mount floppy disk image to given floppy drive.
     * @param fddIdentifier floppy drive identifier to mount image
     * @param fddImage disk image
     * @param isFddWriteProtectMode true to mount in write-protect mode,
     *                              false to mount in read/write mode
     * @return true if image successfully mounted, false otherwise
     */
    protected boolean mountFloppyDiskImage(FloppyDriveIdentifier fddIdentifier, DiskImage fddImage,
                                           boolean isFddWriteProtectMode) {
        FloppyController fddController = computer.getFloppyController();
        if (doMountFloppyDiskImage(fddController, fddIdentifier, fddImage, isFddWriteProtectMode)) {
            setLastMountedFloppyDiskImageLocation(fddIdentifier, fddImage.getLocation().toString());
            setLastFloppyDriveWriteProtectMode(fddIdentifier,
                    fddImage.isReadOnly() || isFddWriteProtectMode);
            return true;
        }
        return false;
    }

    private boolean doMountFloppyDiskImage(FloppyController fddController,
                                           FloppyDriveIdentifier fddIdentifier,
                                           DiskImage fddImage, boolean isFddWriteProtectMode) {
        try {
            if (fddImage != null && fddController != null) {
                for (FloppyDriveIdentifier d : FloppyDriveIdentifier.values()) {
                    DiskImage mountedDiskImage = fddController.getFloppyDriveImage(d);
                    if (mountedDiskImage != null && fddImage.getLocation().equals(
                            mountedDiskImage.getLocation())) {
                        unmountFloppyDiskImage(d);
                    }
                }
                boolean isWriteProtectMode = fddImage.isReadOnly() || isFddWriteProtectMode;
                fddController.mountDiskImage(fddImage, fddIdentifier, isWriteProtectMode);
                Timber.d("Mounted floppy disk image %s to drive %s in %s mode",
                        fddImage, fddIdentifier, (isWriteProtectMode ? "read only" : "read/write"));
                return true;
            }
        } catch (Exception e) {
            Timber.e(e, "Can't mount floppy disk image %s to drive %s",
                    fddImage, fddIdentifier);
        }
        return false;
    }

    /**
     * Unmount floppy disk image from given floppy drive.
     * @param fddIdentifier floppy drive identifier to unmount image
     */
    protected void unmountFloppyDiskImage(FloppyDriveIdentifier fddIdentifier) {
        FloppyController fddController = computer.getFloppyController();
        if (doUnmountFloppyDiskImage(fddController, fddIdentifier)) {
            resetLastFloppyDriveMountData(fddIdentifier);
        }
    }

    private boolean doUnmountFloppyDiskImage(FloppyController fddController,
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
     * Try to attach IDE drive with given disk image to given IDE interface.
     * @param ideInterfaceId IDE interface identifier to attach drive
     * @param image disk image
     * @return true if drive successfully attached, false otherwise
     */
    protected boolean attachIdeDrive(int ideInterfaceId, DiskImage image) {
        IdeController ideController = computer.getIdeController();
        if (doAttachIdeDrive(ideController, ideInterfaceId, image)) {
            setLastAttachedIdeDriveImageLocation(ideInterfaceId, image.getLocation().toString());
            return true;
        }
        return false;
    }

    private boolean doAttachIdeDrive(IdeController ideController, int ideInterfaceId,
                                     DiskImage image) {
        try {
            if (image != null && ideController != null) {
                IdeController.IdeDrive ideDrive = FileUtils.isFileNameExtensionMatched(
                        image.getName(), FILE_EXT_HARD_DISK_IMAGES)
                        ? new IdeController.IdeDriveHdiImage(image)
                        : new IdeController.IdeDriveRawImage(image);
                // Check the same image is attached to the another channel
                int otherIdeInterfaceId = (ideInterfaceId == IF_0) ? IF_1 : IF_0;
                IdeController.IdeDrive otherIdeDrive = ideController.getAttachedDrive(otherIdeInterfaceId);
                if (otherIdeDrive != null && otherIdeDrive.equals(ideDrive)) {
                    detachIdeDrive(otherIdeInterfaceId);
                }
                // Attach drive to the given channel
                ideController.attachDrive(ideInterfaceId, ideDrive);
                Timber.d("Attached IDE drive with disk image %s to IDE interface %d",
                        image, ideInterfaceId);
                return true;
            }
        } catch (Exception e) {
            Timber.e(e, "Can't attach IDE drive with disk image %s to IDE interface %d",
                    image, ideInterfaceId);
        }
        return false;
    }

    /**
     * Detach IDE drive from given IDE interface.
     * @param ideInterfaceId IDE interface identifier to detach image
     */
    protected void detachIdeDrive(int ideInterfaceId) {
        IdeController ideController = computer.getIdeController();
        if (doDetachIdeDrive(ideController, ideInterfaceId)) {
            setLastAttachedIdeDriveImageLocation(ideInterfaceId, null);
        }
    }

    private boolean doDetachIdeDrive(IdeController ideController, int ideInterfaceId) {
        try {
            if (ideController != null && ideController.getAttachedDrive(ideInterfaceId) != null) {
                ideController.detachDrive(ideInterfaceId);
                Timber.d("Detached IDE drive from IDE interface %d", ideInterfaceId);
                return true;
            }
        } catch (Exception e) {
            Timber.e(e, "Can't detach IDE drive from IDE interface %d", ideInterfaceId);
        }
        return false;
    }

    /**
     * Show full changelog dialog.
     */
    private void showChangelogDialog() {
        new BkEmuChangeLog(this).getDialog(true).show();
    }

    protected void showBeforeBinImageFileLoadToast(String imageName) {
        if (imageName != null && !imageName.isEmpty()) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_load_info, imageName),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    protected void showAfterBinImageFileLoadToast(boolean isImageLoaded, String imageName) {
        if (isImageLoaded) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_load_success, imageName,
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, getResources().getString(
                R.string.toast_image_load_info, tapeFileName));
        startActivityForResult(chooserIntent, requestCode);
    }

    /**
     * Show BIN emulator image file save dialog.
     * @param tapeFileName file name to save
     */
    protected void showBinImageFileSaveDialog(int requestCode, String tapeFileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, tapeFileName);
        startActivityForResult(intent, requestCode);
    }

    /**
     * Show floppy disk image to mount selection dialog.
     * @param fddIdentifier floppy drive identifier to mount image
     */
    protected void showMountFloppyDiskImageFileDialog(FloppyDriveIdentifier fddIdentifier) {
        lastFloppyDiskImageDrive = fddIdentifier;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MENU_FLOPPY_DISK_IMAGE_FILE_SELECT);
    }

    /**
     * Show IDE drive image to attach selection dialog.
     * @param ideInterfaceId IDE interface identifier to attach image
     */
    protected void showAttachIdeDriveImageFileDialog(int ideInterfaceId) {
        lastIdeDriveImageInterfaceId = ideInterfaceId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MENU_IDE_DRIVE_IMAGE_FILE_SELECT);
    }

    /**
     * Show disk manager dialog.
     */
    private void showDiskManagerDialog() {
        BkEmuDiskManagerDialog bkEmuDiskManagerDialog = BkEmuDiskManagerDialog.newInstance();
        bkEmuDiskManagerDialog.show(getSupportFragmentManager(), "disk_manager");
    }

    /**
     * Show audio devices volume adjustment dialog.
     */
    private void showVolumeDialog() {
        BkEmuVolumeDialog bkEmuVolumeDialogFragment = BkEmuVolumeDialog.newInstance();
        bkEmuVolumeDialogFragment.show(getSupportFragmentManager(), "volume");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Timber.d("onActivityResult(): requestCode: %d, resultCode: %d, data: %s",
                requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_MENU_BIN_IMAGE_FILE_LOAD:
                if (resultCode == Activity.RESULT_OK) {
                    Uri binImageFileUri = data.getData();
                    Configuration configuration = computer.getConfiguration();
                    if (configuration.isMemoryManagerPresent() ||
                            configuration.isFloppyControllerPresent()) {
                        lastBinImageAddress = 0; // will get address from BIN image file header
                        binImageFileLoad(binImageFileUri);
                    } else {
                        restartActivity(binImageFileUri);
                    }
                }
                break;
            case REQUEST_EMT_BIN_IMAGE_FILE_LOAD:
                boolean isImageLoaded = false;
                if (resultCode == Activity.RESULT_OK) {
                    Uri binImageFileUri = data.getData();
                    isImageLoaded = binImageFileLoad(binImageFileUri);
                }
                doFinishBinImageLoad(isImageLoaded);
                break;
            case REQUEST_EMT_BIN_IMAGE_FILE_SAVE:
                boolean isImageSaved = false;
                if (resultCode == Activity.RESULT_OK) {
                    Uri binImageFileUri = data.getData();
                    isImageSaved = binImageFileSave(binImageFileUri);
                }
                doFinishBinImageSave(isImageSaved);
                break;
            case REQUEST_MENU_FLOPPY_DISK_IMAGE_FILE_SELECT:
                FloppyController floppyController = computer.getFloppyController();
                if (resultCode == Activity.RESULT_OK && floppyController != null) {
                    Uri floppyDiskImageUri = data.getData();
                    if (floppyDiskImageUri == null) {
                        break;
                    }
                    boolean isFloppyDiskImageMounted = false;
                    getContentResolver().takePersistableUriPermission(floppyDiskImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    DiskImage floppyDiskImage = openDiskImage(floppyDiskImageUri);
                    if (floppyDiskImage != null) {
                        isFloppyDiskImageMounted = mountFloppyDiskImage(lastFloppyDiskImageDrive,
                                floppyDiskImage, false);
                    }
                    if (!isFloppyDiskImageMounted) {
                        showDialog(DIALOG_FLOPPY_DISK_MOUNT_ERROR);
                    }
                }
                break;
            case REQUEST_MENU_IDE_DRIVE_IMAGE_FILE_SELECT:
                IdeController ideController = computer.getIdeController();
                if (resultCode == Activity.RESULT_OK && ideController != null) {
                    Uri ideDriveImageUri = data.getData();
                    if (ideDriveImageUri == null) {
                        break;
                    }
                    boolean isIdeDriveAttached = false;
                    getContentResolver().takePersistableUriPermission(ideDriveImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    DiskImage ideDriveImage = openDiskImage(ideDriveImageUri);
                    if (ideDriveImage != null) {
                        isIdeDriveAttached = attachIdeDrive(lastIdeDriveImageInterfaceId,
                                ideDriveImage);
                    }
                    if (!isIdeDriveAttached) {
                        showDialog(DIALOG_IDE_DRIVE_ATTACH_ERROR);
                    }
                }
                break;
            default:
                break;
        }
    }

    protected boolean binImageFileSave(Uri binImageFileUri) {
        boolean isImageSaved = doBinImageFileSave(binImageFileUri);
        String binImageFileName = FileUtils.resolveUriFileName(this, binImageFileUri);
        if (isImageSaved) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_save_info,
                            binImageFileName),
                            Toast.LENGTH_LONG)
                            .show();
        } else {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toast_image_save_error,
                            binImageFileName),
                            Toast.LENGTH_LONG)
                            .show();
        }
        return isImageSaved;
    }

    protected boolean doBinImageFileSave(Uri binImageFileUri) {
        boolean isImageSaved = false;
        try {
            saveBinImageFile(binImageFileUri);
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
            comp.writeMemory(false, Cpu.REG_SEL1, BK11_BANKS_DEFAULT_CONFIG);
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

    protected boolean binImageFileLoad(Uri binImageFileUri) {
        boolean isImageLoaded = doBinImageFileLoad(binImageFileUri);
        String imageName = FileUtils.resolveUriFileName(this, binImageFileUri);
        showAfterBinImageFileLoadToast(isImageLoaded, imageName);
        return isImageLoaded;
    }

    protected boolean doBinImageFileLoad(Uri binImageFileUri) {
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
            comp.writeMemory(false, Cpu.REG_SEL1, BK11_BANKS_DEFAULT_CONFIG);
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
        String tapeFileName = FileUtils.resolveUriFileName(this, Uri.parse(lastBinImageFileUri));
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
     * Do activity restart with given emulator image file.
     * @param binImageFileUri emulator image file {@link Uri} to set to restarted activity
     * (or <code>null</code> to start activity without emulator image set)
     */
    protected void restartActivity(Uri binImageFileUri) {
        Intent intent = getIntent();
        intent.setData(binImageFileUri);
        restartActivity();
    }

    /**
     * Do activity restart.
     */
    protected void restartActivity() {
        Intent intent = getIntent();
        // Pass last accessed program/disk image file paths to new activity
        intent.putExtra(STATE_LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private SharedPreferences getPreferences() {
        return getPreferences(MODE_PRIVATE);
    }

    /**
     * Get current computer configuration as {@link Configuration} enum value.
     * @return configuration enum value
     */
    protected Configuration getComputerConfiguration() {
        SharedPreferences prefs = getPreferences();
        String configName = prefs.getString(PREFS_KEY_COMPUTER_CONFIGURATION, null);
        return (configName == null) ? Configuration.BK_0010_BASIC : Configuration.valueOf(configName);
    }

    /**
     * Set current computer configuration set as {@link Configuration} enum value.
     * @param configuration configuration enum value to set
     */
    protected void setComputerConfiguration(Configuration configuration) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY_COMPUTER_CONFIGURATION, configuration.name());
        prefsEditor.apply();
    }

    private String getLastAttachedIdeDriveImageLocationPrefsKey(int ideInterfaceId) {
        return PREFS_KEY_IDE_DRIVE_PREFIX + ideInterfaceId + "/" + PREFS_KEY_IDE_DRIVE_IMAGE;
    }

    /**
     * Get last attached IDE drive disk image location for given IDE interface.
     * @param ideInterfaceId IDE interface identifier
     * @return last attached IDE drive disk image location (null if no drive was attached)
     */
    protected String getLastAttachedIdeDriveImageLocation(int ideInterfaceId) {
        SharedPreferences prefs = getPreferences();
        return prefs.getString(getLastAttachedIdeDriveImageLocationPrefsKey(ideInterfaceId),
                null);
    }

    /**
     * Set last attached IDE drive disk image location for given IDE interface.
     * @param ideInterfaceId IDE interface identifier
     * @param ideDriveImageLocation attached IDE drive disk image location
     * (null if no drive was attached)
     */
    protected void setLastAttachedIdeDriveImageLocation(int ideInterfaceId,
                                                        String ideDriveImageLocation) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(getLastAttachedIdeDriveImageLocationPrefsKey(ideInterfaceId),
                ideDriveImageLocation);
        prefsEditor.apply();
    }

    private String getLastMountedFloppyDiskImageLocationPrefsKey(
            FloppyDriveIdentifier fddIdentifier) {
        return PREFS_KEY_FLOPPY_DRIVE_IMAGE + fddIdentifier.name();
    }

    /**
     * Get last mounted floppy disk image location for given floppy drive.
     * @param fddIdentifier floppy drive identifier
     * @return last mounted floppy disk image location
     * (null if no floppy disk image was mounted in this drive)
     */
    protected String getLastMountedFloppyDiskImageLocation(FloppyDriveIdentifier fddIdentifier) {
        SharedPreferences prefs = getPreferences();
        return prefs.getString(getLastMountedFloppyDiskImageLocationPrefsKey(fddIdentifier),
                null);
    }

    /**
     * Set last mounted floppy disk image location for given floppy drive.
     * @param fddIdentifier floppy drive identifier
     * @param floppyDiskImageLocation mounted floppy disk image location
     * (null if no floppy disk image is mounted in this drive)
     */
    protected void setLastMountedFloppyDiskImageLocation(FloppyDriveIdentifier fddIdentifier,
                                                         String floppyDiskImageLocation) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(getLastMountedFloppyDiskImageLocationPrefsKey(fddIdentifier),
                floppyDiskImageLocation);
        prefsEditor.apply();
    }

    private String getLastFloppyDriveWriteProtectModePrefsKey(
            FloppyDriveIdentifier fddIdentifier) {
        return PREFS_KEY_FLOPPY_DRIVE_WRITE_PROTECT_MODE + fddIdentifier.name();
    }

    /**
     * Get last floppy drive write protect mode.
     * @param fddIdentifier floppy drive identifier
     * @return <code>true</code> if floppy drive is in write protect mode
     */
    protected boolean getLastMountedFloppyDiskImageWriteProtectMode(
            FloppyDriveIdentifier fddIdentifier) {
        SharedPreferences prefs = getPreferences();
        return prefs.getBoolean(getLastFloppyDriveWriteProtectModePrefsKey(fddIdentifier), false);
    }

    /**
     * Set last floppy drive write protect mode.
     * @param fddIdentifier floppy drive identifier
     * @param isWriteProtectMode <code>true</code> to set floppy drive in write protect mode
     */
    protected void setLastFloppyDriveWriteProtectMode(FloppyDriveIdentifier fddIdentifier,
                                                      boolean isWriteProtectMode) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(getLastFloppyDriveWriteProtectModePrefsKey(fddIdentifier),
                isWriteProtectMode);
        prefsEditor.apply();
    }

    private void resetLastFloppyDriveMountData(FloppyDriveIdentifier fddIdentifier) {
        setLastMountedFloppyDiskImageLocation(fddIdentifier, null);
        setLastFloppyDriveWriteProtectMode(fddIdentifier, false);
    }

    private String getPrefsAudioOutputKey(String audioOutputName) {
        return (audioOutputName == null) ? PREFS_KEY_AUDIO_VOLUME
                : PREFS_KEY_AUDIO_VOLUME + ":" + audioOutputName;
    }

    /**
     * Read audio output volume from shared preferences.
     * @param audioOutputName audio output name
     * @param defaultVolume default audio output volume
     * @return stored audio output volume
     */
    protected int readAudioOutputVolume(String audioOutputName, int defaultVolume) {
        SharedPreferences prefs = getPreferences();
        return prefs.getInt(getPrefsAudioOutputKey(audioOutputName), defaultVolume);
    }

    /**
     * Store audio output volume to shared preferences.
     * @param volume audio output volume to store
     * @param audioOutputName audio output name
     */
    protected void storeAudioOutputVolume(String audioOutputName, int volume) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putInt(getPrefsAudioOutputKey(audioOutputName), volume);
        prefsEditor.apply();
    }

    /**
     * Load program image in bin format (address/length/data) from given path.
     * @param binImageFileUri emulator image file URI
     * @return start address of loaded emulator image
     * @throws Exception in case of loading error
     */
    protected int loadBinImageFile(Uri binImageFileUri) throws Exception {
        Timber.d("Trying to load binary image: %s", binImageFileUri);
        byte[] binImageData = FileUtils.getUriContentData(getApplicationContext(), binImageFileUri);
        this.lastBinImageFileUri = binImageFileUri.toString();
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
     * @param binImageFileUri emulator image file URI
     * @throws Exception in case of saving error
     */
    protected void saveBinImageFile(Uri binImageFileUri) throws Exception {
        Timber.d("saving image: %s", binImageFileUri);
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
        saveBinImage(binImageFileUri, binImageOutput.toByteArray());
        this.lastBinImageFileUri = binImageFileUri.toString();
    }

    /**
     * Save image in bin format (address/length/data) from byte array.
     * @param imageUri image file URI
     * @param imageData image data byte array
     * @throws IOException in case of saving error
     */
    public void saveBinImage(Uri imageUri, byte[] imageData) throws IOException {
        try (BufferedOutputStream binImageOutput = new BufferedOutputStream(
                getContentResolver().openOutputStream(imageUri))) {
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
        return joystickManager.isOnScreenJoystickVisible();
    }

    protected boolean isOnScreenKeyboardVisible() {
        return keyboardManager.isOnScreenKeyboardVisible();
    }

    private void switchOnScreenKeyboardVisibility(boolean isVisible) {
        Timber.d("switch on-screen keyboard visibility state: %s", (isVisible ? "ON" : "OFF"));
        keyboardManager.setOnScreenKeyboardVisibility(isVisible);
    }

    private void switchOnScreenJoystickVisibility(boolean isVisible) {
        Timber.d("switch on-screen joystick visibility state: %s", (isVisible ? "ON" : "OFF"));
        joystickManager.setOnScreenJoystickVisibility(isVisible);
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

    private void switchDisplayMode() {
        VideoController videoController = computer.getVideoController();
        VideoController.DisplayMode displayMode = videoController.getDisplayMode().getNext();
        Timber.d("switching to display mode: %s", displayMode);
        videoController.setDisplayMode(displayMode);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void enterFullscreenMode() {
        Timber.d("entering fullscreen mode");
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void leaveFullscreenMode() {
        Timber.d("leaving fullscreen mode");
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void setSystemUiVisibility(int visibility) {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(visibility);
    }

    public Computer getComputer() {
        return computer;
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

    private void takeScreenshot() {
        Timber.d("taking screenshot");

        // Take screenshot to bitmap
        Bitmap screenshotBitmap = Bitmap.createBitmap(VideoController.VIDEO_BUFFER_WIDTH * 2,
                VideoController.VIDEO_BUFFER_HEIGHT * 3, Bitmap.Config.ARGB_8888);
        getComputer().getVideoController().drawLastRenderedVideoBuffer(screenshotBitmap);

        // Store screenshot bitmap to png file
        Uri screenshotBitmapUri = null;
        File screenshotsFolder = new File(getCacheDir(), "screenshots");
        try {
            screenshotsFolder.mkdirs();
            File screenshotFile = new File(screenshotsFolder, "BkEmu_screenshot.png");
            try (FileOutputStream stream = new FileOutputStream(screenshotFile)) {
                screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
            }
            screenshotBitmapUri = FileProvider.getUriForFile(this,
                    "su.comp.bk.fileprovider", screenshotFile);
        } catch (Exception e) {
            Timber.e(e,  "Can't store screenshot file");
        }

        if (screenshotBitmapUri == null) {
            Toast.makeText(getApplicationContext(), R.string.toast_take_screenshot_error,
                    Toast.LENGTH_LONG)
                    .show();
            return;
        }

        // Share screenshot png file
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, screenshotBitmapUri);
        startActivity(Intent.createChooser(intent, "Share"));
    }
}