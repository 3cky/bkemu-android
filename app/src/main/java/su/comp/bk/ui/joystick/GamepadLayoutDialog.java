/*
 * Copyright (C) 2022 Victor Antonovich (v.antonovich@gmail.com)
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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import su.comp.bk.R;
import su.comp.bk.ui.BkEmuActivity;

/**
 * Hardware gamepad layout editor dialog.
 */
public class GamepadLayoutDialog extends DialogFragment implements DialogInterface.OnKeyListener,
        JoystickManager.HardwareJoystickEventListener, AdapterView.OnItemClickListener {
    private final static String KEYCODE_NAME_PREFIX = "KEYCODE_";

    private LayoutInflater inflater;

    private final List<ButtonItem> buttonItems = new ArrayList<>();

    private TextView gamepadNameTextView;

    private ListView buttonsListView;
    private ButtonAdapter buttonAdapter;

    private JoystickManager.HardwareJoystick activeGamepad;

    private ButtonItem currentRemapButtonItem;


    static class ButtonItem {
        private final JoystickManager.JoystickButton button;

        private String eventName;

        public ButtonItem(JoystickManager.JoystickButton button) {
            this.button = button;
        }

        public JoystickManager.JoystickButton getButton() {
            return button;
        }

        public String getEventName() {
            return eventName;
        }

        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        @Override
        public String toString() {
            return "ButtonItem{" +
                    "button=" + button +
                    ", eventName='" + eventName + '\'' +
                    '}';
        }
    }

    class ButtonAdapter extends ArrayAdapter<ButtonItem> {
        public ButtonAdapter(@NonNull Context context, int resource,
                             @NonNull List<ButtonItem> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.gamepad_layout_button_item, null);
            }

            ButtonItem buttonItem = getItem(position);
            if (buttonItem != null) {
                TextView buttonNameTextView = convertView.findViewById(R.id.button_name);
                buttonNameTextView.setText(getButtonName(buttonItem.getButton()));

                String buttonEventName = buttonItem.getEventName();
                TextView buttonEventNameTextView = convertView.findViewById(R.id.button_event);
                buttonEventNameTextView.setText(getButtonEventDisplayName(buttonEventName));
            }

            return convertView;
        }

        private String getButtonEventDisplayName(String buttonEventName) {
            if (buttonEventName == null) {
                return getString(R.string.gamepad_button_not_bound);
            }
            if (buttonEventName.startsWith(KEYCODE_NAME_PREFIX)) {
                return buttonEventName.substring(KEYCODE_NAME_PREFIX.length());
            }
            return buttonEventName;
        }

        private String getButtonName(JoystickManager.JoystickButton button) {
            switch (button) {
                case LEFT:
                    return getString(R.string.gamepad_button_left);
                case RIGHT:
                    return getString(R.string.gamepad_button_right);
                case UP:
                    return getString(R.string.gamepad_button_up);
                case DOWN:
                    return getString(R.string.gamepad_button_down);
                case A:
                    return getString(R.string.gamepad_button_a);
                case B:
                    return getString(R.string.gamepad_button_b);
                case START:
                    return getString(R.string.gamepad_button_start);
                case SELECT:
                    return getString(R.string.gamepad_button_select);
            }
            return null;
        }
    }

    public static GamepadLayoutDialog newInstance() {
        return new GamepadLayoutDialog();
    }

    public GamepadLayoutDialog() {
    }

    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
    }

    private JoystickManager getJoystickManager() {
        return getBkEmuActivity().getJoystickManager();
    }

    private Dialog getActiveDialog() {
        return Objects.requireNonNull(getDialog());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BkEmuActivity activity = getBkEmuActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.gamepad_layout_dialog, null));
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getActiveDialog();

        dialog.setOnKeyListener(this);
        JoystickManager joystickManager = getJoystickManager();
        joystickManager.addHardwareJoystickEventListener(this);

        for (JoystickManager.JoystickButton button : JoystickManager.JoystickButton.values()) {
            buttonItems.add(new ButtonItem(button));
        }

        gamepadNameTextView = dialog.findViewById(R.id.gamepad_name);

        buttonsListView = dialog.findViewById(R.id.gamepad_buttons);
        buttonAdapter = new ButtonAdapter(dialog.getContext(), R.layout.gamepad_layout_dialog,
                buttonItems);
        buttonsListView.setAdapter(buttonAdapter);
        buttonsListView.setFastScrollEnabled(true);
        buttonsListView.setOnItemClickListener(this);
        buttonsListView.requestFocus();

        updateActiveGamepad();
    }

    @Override
    public void onStop() {
        getActiveDialog().setOnKeyListener(null);
        JoystickManager joystickManager = getJoystickManager();
        joystickManager.removeHardwareJoystickEventListener(this);

        super.onStop();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        resetButtonItemsHighlighted();

        if (isButtonRemapInProgress()) {
            cancelButtonRemap();
        } else {
            startButtonRemap(position);
            setButtonItemHighlighted(position, true);
        }

        buttonAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        return getJoystickManager().handleKeyEvent(event, event.getAction() != KeyEvent.ACTION_UP);
    }

    @Override
    public void onConnected(JoystickManager.HardwareJoystick gamepad) {
        updateActiveGamepad();
    }

    @Override
    public void onDisconnected(JoystickManager.HardwareJoystick gamepad) {
        updateActiveGamepad();
    }

    @Override
    public void onButton(JoystickManager.HardwareJoystick gamepad, String buttonEventName,
                         JoystickManager.JoystickButton button, boolean isPressed) {
        if (gamepad != activeGamepad) {
            return;
        }

        if (isButtonRemapInProgress()) {
            if (!isPressed) {
                doButtonRemap(button, buttonEventName);
                resetButtonItemsHighlighted();
                updateGamepadButtonItems(activeGamepad);
            }
        } else if (button != null) {
            setButtonPressed(button, isPressed);
        }
    }

    private void setButtonPressed(JoystickManager.JoystickButton button, boolean isPressed) {
        ButtonItem buttonItem = getButtonItem(button);
        if (buttonItem != null) {
            int buttonItemPosition = buttonAdapter.getPosition(buttonItem);
            setButtonItemHighlighted(buttonItemPosition, isPressed);
            buttonsListView.smoothScrollToPosition(buttonItemPosition);
        }
    }

    private ButtonItem getButtonItem(JoystickManager.JoystickButton button) {
        for (ButtonItem buttonItem : buttonItems) {
            if (buttonItem.getButton() == button) {
                return buttonItem;
            }
        }
        return null;
    }

    private String getButtonItemEventName(JoystickManager.HardwareJoystick gamepad,
                                          ButtonItem buttonItem) {
        return gamepad.getButtonEventMapping(buttonItem.getButton());
    }

    private void resetButtonItemsHighlighted() {
        for (ButtonItem buttonItem : buttonItems) {
            setButtonItemHighlighted(buttonItem, false);
        }
    }

    private void setButtonItemHighlighted(ButtonItem buttonItem, boolean isHighlighted) {
        int buttonItemPosition = buttonAdapter.getPosition(buttonItem);
        setButtonItemHighlighted(buttonItemPosition, isHighlighted);
    }

    private void setButtonItemHighlighted(int buttonItemPosition, boolean isHighlighted) {
        buttonsListView.setItemChecked(buttonItemPosition, isHighlighted);
    }

    private void updateActiveGamepad() {
        JoystickManager.HardwareJoystick activeGamepad =
                getJoystickManager().getActiveHardwareJoystick();

        if (activeGamepad == null) {
            dismiss();
            return;
        }

        updateGamepadName(activeGamepad);
        updateGamepadButtonItems(activeGamepad);

        this.activeGamepad = activeGamepad;
    }

    private void updateGamepadName(JoystickManager.HardwareJoystick gamepad) {
        String gamepadName = gamepad.getDeviceName();
        gamepadNameTextView.setText((gamepadName != null) ? gamepadName
                : getString(R.string.gamepad_name_undefined));
    }

    private void updateGamepadButtonItems(JoystickManager.HardwareJoystick gamepad) {
        for (ButtonItem buttonItem : buttonItems) {
            updateButtonItemEventName(gamepad, buttonItem);
        }
        buttonAdapter.notifyDataSetChanged();
    }

    private void updateButtonItemEventName(JoystickManager.HardwareJoystick gamepad,
                                           ButtonItem buttonItem) {
        String buttonEventName = getButtonItemEventName(gamepad, buttonItem);
        buttonItem.setEventName(buttonEventName);
    }

    private boolean isButtonRemapInProgress() {
        return currentRemapButtonItem != null;
    }

    private void startButtonRemap(int buttonItemPosition) {
        currentRemapButtonItem = buttonItems.get(buttonItemPosition);
        currentRemapButtonItem.setEventName(null);
    }

    private void cancelButtonRemap() {
        updateButtonItemEventName(activeGamepad, currentRemapButtonItem);
        currentRemapButtonItem = null;
    }

    private void doButtonRemap(JoystickManager.JoystickButton oldButton, String buttonEventName) {
        activeGamepad.deleteButtonEventMapping(oldButton);
        activeGamepad.setButtonEventMapping(currentRemapButtonItem.getButton(), buttonEventName);
        getJoystickManager().saveHardwareJoystickMapping(activeGamepad);
        currentRemapButtonItem = null;
    }
}
