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

import java.io.IOException;

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater layoutInflater = getLayoutInflater();
        View mainView = layoutInflater.inflate(R.layout.main, null);
        BkEmuView bkEmuView = (BkEmuView) mainView.findViewById(R.id.emu_view);
        Computer computer = new Computer();
        try {
            computer.configure(getResources(), Computer.Configuration.BK_0010_BASIC);
            bkEmuView.setComputer(computer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        setContentView(mainView);
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void toggleOnScreenKeyboard() {
        Log.d(TAG, "Toggling on-screen keyboard");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }
}