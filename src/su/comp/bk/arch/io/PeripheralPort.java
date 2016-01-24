/*
 * Created: 23.04.2012
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
package su.comp.bk.arch.io;

import su.comp.bk.arch.Computer;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * BK-0010 peripheral port.
 */
public class PeripheralPort implements Device, OnTouchListener {

    private static final String TAG = PeripheralPort.class.getName();

    public final static int DATA_REGISTER_ADDRESS = 0177714;

    public final static int JOYSTICK_BUTTON1 = 1;
    public final static int JOYSTICK_BUTTON2 = 2;
    public final static int JOYSTICK_BUTTON3 = 4;
    public final static int JOYSTICK_BUTTON4 = 010;
    public final static int JOYSTICK_LEFT = 01000;
    public final static int JOYSTICK_DOWN = 040;
    public final static int JOYSTICK_RIGHT = 020;
    public final static int JOYSTICK_UP = 02000;

    public enum JoystickButton {
        ONE(JOYSTICK_BUTTON1),
        TWO(JOYSTICK_BUTTON2),
        LEFT(JOYSTICK_LEFT),
        RIGHT(JOYSTICK_RIGHT),
        UP(JOYSTICK_UP),
        DOWN(JOYSTICK_DOWN);

        private final int joystickButtonMask;

        private JoystickButton(int joystickButtonMask) {
            this.joystickButtonMask = joystickButtonMask;
        }

        public int getJoystickButtonMask() {
            return joystickButtonMask;
        }
    }

    /** Joystick buttons pressing delay (in nanoseconds) */
    public final static long JOYSTICK_PRESS_DELAY = (250L * Computer.NANOSECS_IN_MSEC);

    /** Joystick buttons pressing scroll threshold (in pixels) */
    public final static float JOYSTICK_PRESS_THRESHOLD = 1f;

    private final static int[] ADDRESSES = { DATA_REGISTER_ADDRESS };

    private final Computer computer;

    private View[] onScreenJoystickViews;

    private boolean isOnScreenJoystickVisible;

    // Current port state
    private int state;

    public PeripheralPort(Computer computer) {
        this.computer = computer;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        resetState(cpuTime);
    }

    @Override
    public void timer(long cpuTime) {
        // Do nothing
    }

    @Override
    public int read(long cpuTime, int address) {
        return getState();
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        // TODO
        return true;
    }

    @Override
    public void saveState(Bundle outState) {
        // Do nothing
    }

    @Override
    public void restoreState(Bundle inState) {
        // Do nothing
    }

    private void resetState(long cpuTime) {
        setState(0);
    }

    private synchronized int getState() {
        return state;
    }

    private synchronized void setState(int state) {
        this.state = state;
    }

    public void setOnScreenJoystickViews(View... joystickViews) {
        this.onScreenJoystickViews = joystickViews;
        for (JoystickButton joystickButton : JoystickButton.values()) {
            boolean isJoystickButtonFound = false;
            for (View joystickView : joystickViews) {
                if (joystickView != null) {
                    View joystickButtonView = joystickView.findViewWithTag(joystickButton.name());
                    if (joystickButtonView != null) {
                        joystickButtonView.setOnTouchListener(this);
                        isJoystickButtonFound = true;
                        break;
                    }
                }
            }
            if (!isJoystickButtonFound) {
                Log.w(TAG, "Can't find view for button: " + joystickButton.name());
            }
        }
    }

    public void setOnScreenJoystickVisibility(boolean isVisible) {
        this.isOnScreenJoystickVisible = isVisible;
        for (View joystickView : onScreenJoystickViews) {
            if (joystickView != null) {
                joystickView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
            }
        }
    }

    public boolean isOnScreenJoystickVisible() {
        return isOnScreenJoystickVisible;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_DOWN) {
            JoystickButton joystickButton = JoystickButton.valueOf(v.getTag().toString());
            boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;
            handleJoystickButton(joystickButton, isPressed);
        }
        return false;
    }

    private void handleJoystickButton(JoystickButton joystickButton, boolean isPressed) {
        int currentState = getState();
        if (isPressed) {
            setState(currentState | joystickButton.getJoystickButtonMask());
        } else {
            setState(currentState & ~joystickButton.getJoystickButtonMask());
        }
    }

}
