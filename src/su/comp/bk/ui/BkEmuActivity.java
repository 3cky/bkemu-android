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

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Main application activity.
 */
public class BkEmuActivity extends Activity {

    private static final String TAG = BkEmuActivity.class.getName();

    private BkEmuView bkEmuView;

    private Computer computer;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        LayoutInflater layoutInflater = getLayoutInflater();
        View mainView = layoutInflater.inflate(R.layout.main, null);
        bkEmuView = (BkEmuView) mainView.findViewById(R.id.emu_view);
        initializeComputer(savedInstanceState);
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
                this.computer.configure(getResources(), Computer.Configuration.BK_0010_BASIC);
                this.computer.reset();
                isComputerInitialized = true;
            } catch (Exception e) {
                Log.e(TAG, "Error while computer configuring", e);
            }
        }
        if (isComputerInitialized) {
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
                ? true : super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return this.computer.getKeyboardController().handleKeyCode(keyCode, false)
                ? true : super.onKeyUp(keyCode, event);
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
            case R.id.load_image:
                Log.d(TAG, "loading image file");
                try {
                    computer.loadImage(getResources(), R.raw.image);
                } catch (Exception e) {
                    Log.e(TAG, "can't load image file", e);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void toggleOnScreenKeyboard() {
        Log.d(TAG, "toggling on-screen keyboard");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }
}