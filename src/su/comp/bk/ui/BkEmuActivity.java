/*
 * Created: 16.02.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package su.comp.bk.ui;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.Computer.Configuration;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.cpu.opcode.EmtOpcode;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.arch.io.VideoController;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

/**
 * Main application activity.
 */
public class BkEmuActivity extends Activity {

    protected static final String TAG = BkEmuActivity.class.getName();

    public final static int STACK_TOP_ADDRESS = 01000;

    private static final int DIALOG_COMPUTER_MODEL = 1;

    private BkEmuView bkEmuView;

    protected Computer computer;

    protected String intentDataProgramImagePath;

    protected Handler activityHandler;

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
                Log.e(TAG, "Can't load bootstrap emulator image", e);
            }
        }
    }

    /**
     * CPU hardware/software trap listener.
     */
    class CpuOnTrapListener implements Cpu.OnTrapListener {
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
                    }
                    break;
                case 036: // EMT 36 - tape I/O
                    Log.d(TAG, "EMT 36, R1=0" + Integer.toOctalString(
                            cpu.readRegister(false, Cpu.R1)));
                    break;
                case Computer.BUS_ERROR:
                    Log.w(TAG, "Can't get EMT number");
                    break;
                default:
                    break;
            }
        }

        /**
         * Get trap (EMT/TRAP) number using pushed to stack PC.
         * @param cpu {@link Cpu} reference
         * @param trapBaseOpcode EMT/TRAP base opcode
         * @return trap number or BUS_ERROR in case of addressing error
         */
        private int getTrapNumber(Cpu cpu, int trapBaseOpcode) {
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
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate(), Intent: " + getIntent());
        super.onCreate(savedInstanceState);
        this.activityHandler = new Handler();
        LayoutInflater layoutInflater = getLayoutInflater();
        View mainView = layoutInflater.inflate(R.layout.main, null);
        bkEmuView = (BkEmuView) mainView.findViewById(R.id.emu_view);
        this.intentDataProgramImagePath = getIntent().getDataString();
        initializeComputer(savedInstanceState);
        View keyboardView = mainView.findViewById(R.id.keyboard);
        KeyboardController keyboardController = this.computer.getKeyboardController();
        keyboardController.setOnScreenKeyboardView(keyboardView);
        keyboardController.setOnScreenKeyboardVisibility(false);
        setContentView(mainView);
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
                this.computer.configure(getResources(), (intentDataProgramImagePath == null)
                        ? getComputerConfiguration() : Computer.Configuration.BK_0010_MONITOR);
                this.computer.reset();
                isComputerInitialized = true;
            } catch (Exception e) {
                Log.e(TAG, "Error while computer configuring", e);
            }
        }
        if (isComputerInitialized) {
            computer.getCpu().setOnTrapListener(new CpuOnTrapListener());
            bkEmuView.setComputer(computer);
        } else {
            throw new IllegalStateException("Can't initialize computer state");
        }
    }

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
        this.computer.saveState(getResources(), outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return this.computer.getKeyboardController().handleKeyCode(keyCode, true)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean isHandled = false;
        if (keyCode == KeyEvent.KEYCODE_BACK && this.computer
                .getKeyboardController().isOnScreenKeyboardVisible()) {
            this.computer.getKeyboardController().setOnScreenKeyboardVisibility(false);
            isHandled = true;
        } else {
            isHandled = this.computer.getKeyboardController().handleKeyCode(keyCode, false)
                    || super.onKeyUp(keyCode, event);
        }
        return isHandled;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.keyboard:
                toggleOnScreenKeyboard();
                return true;
            case R.id.screen_mode:
                toggleScreenMode();
                return true;
            case R.id.reset:
                resetComputer();
                return true;
            case R.id.change_model:
                showDialog(DIALOG_COMPUTER_MODEL);
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
                    .setTitle(R.string.select_model)
                    .setSingleChoiceItems(models, getComputerConfiguration().ordinal(),
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Mark selected item by tag
                            ListView listView = ((AlertDialog) dialog).getListView();
                            listView.setTag(Integer.valueOf(which));
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Get tagged selected item, if any
                            ListView listView = ((AlertDialog) dialog).getListView();
                            Integer selected = (Integer) listView.getTag();
                            if (selected != null) {
                                Configuration config = Configuration.values()[selected];
                                if (computer.getConfiguration() != config) {
                                    // Set new computer configuration and restart activity
                                    setComputerConfiguration(config);
                                    restartActivity();
                                }
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing on cancel
                        }
                    })
                   .create();
        }
        return null;
    }

    /**
     * Do actifity restart.
     */
    protected void restartActivity() {
        Intent intent = getIntent();
        intent.setData(null);
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
        return computer.loadBinImage(binImageOutput.toByteArray());
    }

    private void toggleOnScreenKeyboard() {
        Log.d(TAG, "toggling on-screen keyboard");
        KeyboardController keyboardController = computer.getKeyboardController();
        keyboardController.setOnScreenKeyboardVisibility(!keyboardController
                .isOnScreenKeyboardVisible());
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
            restartActivity();
        } else {
            computer.reset();
        }
    }
}