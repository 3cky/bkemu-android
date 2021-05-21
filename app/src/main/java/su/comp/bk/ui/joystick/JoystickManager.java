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

package su.comp.bk.ui.joystick;

import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import su.comp.bk.R;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.ui.BkEmuActivity;
import timber.log.Timber;

/**
 * On-screen joystick manager.
 */
public class JoystickManager implements OnTouchListener {
    public final static int JOYSTICK_UP       = 1;
    public final static int JOYSTICK_RIGHT    = 1 << 1;
    public final static int JOYSTICK_DOWN     = 1 << 2;
    public final static int JOYSTICK_LEFT     = 1 << 3;
    public final static int JOYSTICK_BUTTON_A = 1 << 5;
    public final static int JOYSTICK_BUTTON_B = 1 << 6;

    private PeripheralPort peripheralPort;

    private View[] onScreenJoystickViews;

    private boolean isOnScreenJoystickVisible;

    public enum JoystickButton {
        A(JOYSTICK_BUTTON_A),
        B(JOYSTICK_BUTTON_B),
        LEFT(JOYSTICK_LEFT),
        RIGHT(JOYSTICK_RIGHT),
        UP(JOYSTICK_UP),
        DOWN(JOYSTICK_DOWN);

        private final int joystickButtonMask;

        JoystickButton(int joystickButtonMask) {
            this.joystickButtonMask = joystickButtonMask;
        }

        public int getJoystickButtonMask() {
            return joystickButtonMask;
        }
    }

    public JoystickManager(BkEmuActivity activity) {
        View joystickView = activity.findViewById(R.id.joystick);
        View joystickDpadView = activity.findViewById(R.id.joystick_dpad);
        View joystickButtonsView = activity.findViewById(R.id.joystick_buttons);
        setOnScreenJoystickViews(joystickView, joystickDpadView, joystickButtonsView);
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
                Timber.w("Can't find view for button: %s", joystickButton.name());
            }
        }
    }

    public void setPeripheralPort(PeripheralPort peripheralPort) {
        this.peripheralPort = peripheralPort;
    }

    public PeripheralPort getPeripheralPort() {
        return peripheralPort;
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
            Timber.d("Joystick button %s is %s", joystickButton.name(),
                    isPressed ? "pressed" : "released");
            handleJoystickButton(joystickButton, isPressed);
            if (!isPressed) {
                v.performClick();
            }
        }
        return false;
    }

    private void handleJoystickButton(JoystickButton joystickButton, boolean isPressed) {
        PeripheralPort port = getPeripheralPort();
        if (port != null) {
            int currentState = port.getState();
            if (isPressed) {
                port.setState(currentState | joystickButton.getJoystickButtonMask());
            } else {
                port.setState(currentState & ~joystickButton.getJoystickButtonMask());
            }
        }
    }
}
