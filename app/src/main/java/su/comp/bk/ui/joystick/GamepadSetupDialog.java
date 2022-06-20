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

import static su.comp.bk.ui.joystick.JoystickManager.HardwareJoystick;

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
import android.widget.Spinner;
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
import timber.log.Timber;

/**
 * Hardware gamepad selection and layout setup dialog.
 */
public class GamepadSetupDialog extends DialogFragment implements DialogInterface.OnKeyListener,
        JoystickManager.HardwareJoystickEventListener, AdapterView.OnItemClickListener,
        AdapterView.OnItemSelectedListener {
    private final static String KEYCODE_NAME_PREFIX = "KEYCODE_";

    private LayoutInflater inflater;

    private JoystickManager joystickManager;

    private final List<ButtonItem> gamepadButtonItems = new ArrayList<>();

    private Spinner gamepadSelectorSpinner;
    private ArrayAdapter<String> gamepadSelectorAdapter;

    private ListView gamepadButtonsListView;
    private ButtonAdapter gamepadButtonAdapter;

    private HardwareJoystick selectedGamepad;

    private boolean isGamepadSelectedInternally;

    private ButtonItem remapButtonItem;


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

        @NonNull
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
                convertView = inflater.inflate(R.layout.gamepad_layout_item, null);
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

    public static GamepadSetupDialog newInstance() {
        return new GamepadSetupDialog();
    }

    public GamepadSetupDialog() {
    }

    private BkEmuActivity getBkEmuActivity() {
        return (BkEmuActivity) requireActivity();
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
        builder.setView(inflater.inflate(R.layout.gamepad_setup_dialog, null));
        return builder.create();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBkEmuActivity().pauseEmulation();
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getActiveDialog();

        dialog.setOnKeyListener(this);

        joystickManager = getBkEmuActivity().getJoystickManager();
        joystickManager.addHardwareJoystickEventListener(this);

        gamepadSelectorSpinner = dialog.findViewById(R.id.gamepad_selector);
        gamepadSelectorAdapter = new ArrayAdapter<>(dialog.getContext(),
                android.R.layout.simple_spinner_item);
        gamepadSelectorAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        gamepadSelectorSpinner.setAdapter(gamepadSelectorAdapter);
        gamepadSelectorSpinner.setOnItemSelectedListener(this);

        for (JoystickManager.JoystickButton button : JoystickManager.JoystickButton.values()) {
            gamepadButtonItems.add(new ButtonItem(button));
        }

        gamepadButtonsListView = dialog.findViewById(R.id.gamepad_layout);
        gamepadButtonAdapter = new ButtonAdapter(dialog.getContext(),
                R.layout.gamepad_setup_dialog, gamepadButtonItems);
        gamepadButtonsListView.setAdapter(gamepadButtonAdapter);
        gamepadButtonsListView.setFastScrollEnabled(true);
        gamepadButtonsListView.setOnItemClickListener(this);
        gamepadButtonsListView.requestFocus();

        gamepadListUpdated();
    }

    @Override
    public void onStop() {
        getActiveDialog().setOnKeyListener(null);
        joystickManager.removeHardwareJoystickEventListener(this);

        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getBkEmuActivity().resumeEmulation();
    }

    /**
     * Gamepad selector spinner item selected handler.
     *
     * @param position the position of the item in the adapter
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Timber.d("onItemSelected: %d%s", position, isGamepadSelectedInternally
                ? " (internally)" : "");
        if (isGamepadSelectedInternally) {
            isGamepadSelectedInternally = false;
            return;
        }
        int selectedDeviceId = joystickManager.getHardwareJoystickByIndex(position).getDeviceId();
        joystickManager.setSelectedHardwareJoystickDeviceId(selectedDeviceId);
        joystickManager.setSelectedHardwareJoystickAsPreferred();
        updateSelectedGamepad();
    }

    /**
     * Gamepad selector spinner nothing selected handler.
     *
     * @param parent The AdapterView where the selection happened
     */
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Timber.d("onNothingSelected");
    }

    /**
     * Gamepad layout button list item click handler.
     *
     * @param position the position of the item in the adapter
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        resetButtonItemsHighlighted();

        if (isButtonRemapInProgress()) {
            cancelButtonRemap();
        } else {
            startButtonRemap(position);
            setButtonItemHighlighted(position, true);
        }

        gamepadButtonAdapter.notifyDataSetChanged();
    }

    /**
     * Gamepad setup dialog key press event handler.
     *
     * @param dialog the dialog the key has been dispatched to
     * @param keyCode the code for the physical key that was pressed
     * @param event the KeyEvent object containing full information about
     *              the event
     * @return {@code true} if the key press event handled as the gamepad button press event,
     *         {@code false} otherwise
     */
    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        return joystickManager.handleKeyEvent(event, event.getAction() != KeyEvent.ACTION_UP);
    }

    /**
     * Joystick manager gamepad connected event handler.
     *
     * @param gamepad connected gamepad reference
     */
    @Override
    public void onConnected(HardwareJoystick gamepad) {
        gamepadListUpdated();
    }

    /**
     * Joystick manager gamepad disconnected event handler.
     *
     * @param gamepad disconnected gamepad reference
     */
    @Override
    public void onDisconnected(HardwareJoystick gamepad) {
        gamepadListUpdated();
    }

    /**
     * Joystick manager gamepad button event handler.
     *
     * @param gamepad event source gamepad reference
     * @param buttonEventName button event name (like "KEYCODE_BUTTON_A")
     * @param button joystick button mapped to event (null if no mapping defined for event)
     * @param isPressed {@code true} if button is pressed, {@code false} if released
     */
    @Override
    public void onButton(HardwareJoystick gamepad, String buttonEventName,
                         JoystickManager.JoystickButton button, boolean isPressed) {
        if (gamepad != selectedGamepad) {
            return;
        }

        if (isButtonRemapInProgress()) {
            if (!isPressed) {
                doButtonRemap(button, buttonEventName);
                resetButtonItemsHighlighted();
                updateGamepadButtonItems(selectedGamepad);
            }
        } else if (button != null) {
            setButtonPressed(button, isPressed);
        }
    }

    private void setButtonPressed(JoystickManager.JoystickButton button, boolean isPressed) {
        ButtonItem buttonItem = getButtonItem(button);
        if (buttonItem != null) {
            int buttonItemPosition = gamepadButtonAdapter.getPosition(buttonItem);
            setButtonItemHighlighted(buttonItemPosition, isPressed);
            gamepadButtonsListView.smoothScrollToPosition(buttonItemPosition);
        }
    }

    private ButtonItem getButtonItem(JoystickManager.JoystickButton button) {
        for (ButtonItem buttonItem : gamepadButtonItems) {
            if (buttonItem.getButton() == button) {
                return buttonItem;
            }
        }
        return null;
    }

    private String getButtonItemEventName(HardwareJoystick gamepad, ButtonItem buttonItem) {
        return gamepad.getButtonEventMapping(buttonItem.getButton());
    }

    private void resetButtonItemsHighlighted() {
        for (ButtonItem buttonItem : gamepadButtonItems) {
            setButtonItemHighlighted(buttonItem, false);
        }
    }

    private void setButtonItemHighlighted(ButtonItem buttonItem, boolean isHighlighted) {
        int buttonItemPosition = gamepadButtonAdapter.getPosition(buttonItem);
        setButtonItemHighlighted(buttonItemPosition, isHighlighted);
    }

    private void setButtonItemHighlighted(int buttonItemPosition, boolean isHighlighted) {
        gamepadButtonsListView.setItemChecked(buttonItemPosition, isHighlighted);
    }

    private void gamepadListUpdated() {
        if (!joystickManager.hasSelectedHardwareJoystick()) {
            dismiss();
            return;
        }
        updateGamepadSelectorState();
        updateSelectedGamepad();
    }

    private void updateSelectedGamepad() {
        HardwareJoystick selectedGamepad = joystickManager.getSelectedHardwareJoystick();
        if (this.selectedGamepad != selectedGamepad) {
            this.selectedGamepad = selectedGamepad;
            updateSelectedGamepadLayout();
        }
    }

    private void updateGamepadSelectorState() {
        List<HardwareJoystick> joysticks = joystickManager.getHardwareJoysticks();
        gamepadSelectorAdapter.clear();
        for (HardwareJoystick joystick : joysticks) {
            gamepadSelectorAdapter.add(joystick.getDeviceName());
        }
        gamepadSelectorAdapter.notifyDataSetChanged();

        int selectedGamepadIndex = joystickManager.getSelectedHardwareJoystickIndex();
        isGamepadSelectedInternally = (gamepadSelectorSpinner
                .getSelectedItemPosition() != selectedGamepadIndex);
        gamepadSelectorSpinner.setSelection(selectedGamepadIndex);
    }

    private void updateSelectedGamepadLayout() {
        updateGamepadButtonItems(selectedGamepad);
    }

    private void updateGamepadButtonItems(HardwareJoystick gamepad) {
        for (ButtonItem buttonItem : gamepadButtonItems) {
            updateButtonItemEventName(gamepad, buttonItem);
        }
        gamepadButtonAdapter.notifyDataSetChanged();
    }

    private void updateButtonItemEventName(HardwareJoystick gamepad, ButtonItem buttonItem) {
        String buttonEventName = getButtonItemEventName(gamepad, buttonItem);
        buttonItem.setEventName(buttonEventName);
    }

    private boolean isButtonRemapInProgress() {
        return remapButtonItem != null;
    }

    private void startButtonRemap(int buttonItemPosition) {
        remapButtonItem = gamepadButtonItems.get(buttonItemPosition);
        remapButtonItem.setEventName(null);
    }

    private void cancelButtonRemap() {
        updateButtonItemEventName(selectedGamepad, remapButtonItem);
        remapButtonItem = null;
    }

    private void doButtonRemap(JoystickManager.JoystickButton oldButton, String buttonEventName) {
        selectedGamepad.deleteButtonEventMapping(oldButton);
        selectedGamepad.setButtonEventMapping(remapButtonItem.getButton(), buttonEventName);
        joystickManager.saveHardwareJoystickMapping(selectedGamepad);
        remapButtonItem = null;
    }
}
