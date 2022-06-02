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

import static su.comp.bk.ui.BkEmuActivity.APP_PACKAGE_NAME;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import su.comp.bk.R;
import su.comp.bk.arch.io.PeripheralPort;
import su.comp.bk.ui.BkEmuActivity;
import su.comp.bk.util.DataUtils;
import timber.log.Timber;

/**
 * On-screen and hardware joysticks manager.
 */
public class JoystickManager implements OnTouchListener, InputManager.InputDeviceListener,
        View.OnKeyListener {
    private static final String PREFS_KEY_PREFERRED_HARDWARE_JOYSTICK_DEVICE_DESCRIPTOR =
            APP_PACKAGE_NAME + ".ui.joystick.JoystickManager" +
                    "/preferredHardwareJoystickDeviceDescriptor";

    // State save/restore key prefix
    private static final String STATE_PREFIX = JoystickManager.class.getName();
    // State save/restore: On-screen joystick visibility state
    private static final String STATE_ON_SCREEN_JOYSTICK_VISIBLE = STATE_PREFIX +
            "on_screen_joystick_visible";

    public final static int JOYSTICK_UP            = 1;
    public final static int JOYSTICK_RIGHT         = 1 << 1;
    public final static int JOYSTICK_DOWN          = 1 << 2;
    public final static int JOYSTICK_LEFT          = 1 << 3;
    public final static int JOYSTICK_BUTTON_START  = 1 << 4;
    public final static int JOYSTICK_BUTTON_A      = 1 << 5;
    public final static int JOYSTICK_BUTTON_B      = 1 << 6;
    public final static int JOYSTICK_BUTTON_SELECT = 1 << 7;

    private Activity activity;

    private PeripheralPort peripheralPort;

    private View[] onScreenJoystickViews;

    private boolean isOnScreenJoystickVisible;

    private InputManager inputManager;

    private final SparseArray<HardwareJoystick> hardwareJoysticks = new SparseArray<>();

    private final List<HardwareJoystickEventListener> hardwareJoystickEventListeners =
            new ArrayList<>();

    private int selectedHardwareJoystickDeviceId = -1;

    public enum JoystickButton {
        UP(JOYSTICK_UP),
        DOWN(JOYSTICK_DOWN),
        LEFT(JOYSTICK_LEFT),
        RIGHT(JOYSTICK_RIGHT),
        A(JOYSTICK_BUTTON_A),
        B(JOYSTICK_BUTTON_B),
        SELECT(JOYSTICK_BUTTON_SELECT),
        START(JOYSTICK_BUTTON_START);

        private final int joystickButtonMask;

        JoystickButton(int joystickButtonMask) {
            this.joystickButtonMask = joystickButtonMask;
        }

        public int getJoystickButtonMask() {
            return joystickButtonMask;
        }
    }

    public static class HardwareJoystick {
        private final InputDevice device;

        private final Map<String, JoystickButton> eventButtonMap = new HashMap<>();

        public HardwareJoystick(InputDevice device, Map<JoystickButton, String> buttonEventMap) {
            this.device = device;
            if (buttonEventMap != null) {
                for (Map.Entry<JoystickButton, String> entry : buttonEventMap.entrySet()) {
                    eventButtonMap.put(entry.getValue(), entry.getKey());
                }
            }
        }

        public int getDeviceId() {
            return device.getId();
        }

        public String getDeviceName() {
            return device.getName();
        }

        public String getDeviceDescriptor() {
            return device.getDescriptor();
        }

        public JoystickButton getButton(String keyName) {
            return eventButtonMap.get(keyName);
        }

        public String getButtonEventMapping(JoystickButton button) {
            if (button != null) {
                for (Map.Entry<String, JoystickButton> eventButtonEntry
                        : eventButtonMap.entrySet()) {
                    if (eventButtonEntry.getValue() == button) {
                        return eventButtonEntry.getKey();
                    }
                }
            }
            return null;
        }

        public void deleteButtonEventMapping(JoystickButton button) {
            String buttonEventName = getButtonEventMapping(button);
            if (buttonEventName != null) {
                eventButtonMap.remove(buttonEventName);
                Timber.d("Deleted button %s mapping to event %s", button, buttonEventName);
            }
        }

        public void setButtonEventMapping(JoystickButton button, String buttonEventName) {
            deleteButtonEventMapping(button);
            eventButtonMap.put(buttonEventName, button);
            Timber.d("Set button %s mapping to event %s", button, buttonEventName);
        }

        public Map<JoystickButton, String> getButtonEventMap() {
            Map<JoystickButton, String> buttonEventMap = new TreeMap<>();
            for (Map.Entry<String, JoystickButton> eventButtonEntry : eventButtonMap.entrySet()) {
                buttonEventMap.put(eventButtonEntry.getValue(), eventButtonEntry.getKey());
            }
            return buttonEventMap;
        }

        @NonNull
        @Override
        public String toString() {
            return "HardwareJoystick{" + "device=" + device + '}';
        }
    }

    public interface HardwareJoystickEventListener {
        void onConnected(HardwareJoystick joystick);

        void onDisconnected(HardwareJoystick joystick);

        void onButton(HardwareJoystick joystick, String buttonEventName,
                      JoystickButton button, boolean isPressed);
    }

    public JoystickManager() {
    }

    public void init(BkEmuActivity activity, PeripheralPort peripheralPort) {
        setActivity(activity);
        setPeripheralPort(peripheralPort);
        initOnScreenJoystick();
        initHardwareJoysticks();
    }

    public void release() {
        releaseHardwareJoysticks();
    }

    private void initOnScreenJoystick() {
        Activity activity = getActivity();
        View joystickView = activity.findViewById(R.id.joystick);
        View joystickDpadView = activity.findViewById(R.id.joystick_dpad);
        View joystickButtonsView = activity.findViewById(R.id.joystick_buttons);
        setupOnScreenJoystickViews(joystickView, joystickDpadView, joystickButtonsView);
        setOnScreenJoystickVisibility(false);
    }

    public void setupOnScreenJoystickViews(View... joystickViews) {
        this.onScreenJoystickViews = joystickViews;
        for (JoystickButton joystickButton : JoystickButton.values()) {
            View joystickButtonView = findOnScreenJoystickButtonView(joystickButton);
            if (joystickButtonView != null) {
                joystickButtonView.setOnTouchListener(this);
                joystickButtonView.setOnKeyListener(this);
            }
        }
    }

    private View findOnScreenJoystickButtonView(JoystickButton joystickButton) {
        for (View joystickView : onScreenJoystickViews) {
            if (joystickView != null) {
                View joystickButtonView = joystickView.findViewWithTag(joystickButton.name());
                if (joystickButtonView != null) {
                    return joystickButtonView;
                }
            }
        }
        return null;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
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
            handleJoystickButton(joystickButton, isPressed);
            v.setPressed(isPressed);
            if (!isPressed) {
                v.performClick();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0) {
                JoystickButton joystickButton = JoystickButton.valueOf(v.getTag().toString());
                boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;
                handleJoystickButton(joystickButton, isPressed);
            }
            return true;
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
            Timber.d("Joystick button %s is %s", joystickButton.name(),
                    isPressed ? "pressed" : "released");
        }
    }

    private static boolean hasSources(int sources, int sourcesMask) {
        return (sources & sourcesMask) == sourcesMask;
    }

    public static boolean isHardwareJoystickKeyEvent(KeyEvent event) {
        return hasSources(event.getSource(), InputDevice.SOURCE_GAMEPAD)
                || hasSources(event.getSource(), InputDevice.SOURCE_JOYSTICK);
    }

    public static boolean isDpadKeyEvent(KeyEvent event) {
        return hasSources(event.getSource(), InputDevice.SOURCE_DPAD);
    }

    @Override
    public void onInputDeviceAdded(int inputDeviceId) {
        if (isInputDeviceHardwareJoystick(inputDeviceId)) {
            addHardwareJoystick(inputDeviceId);
        }
    }

    @Override
    public void onInputDeviceRemoved(int inputDeviceId) {
        removeHardwareJoystick(inputDeviceId);
    }

    @Override
    public void onInputDeviceChanged(int inputDeviceId) {
        // Do nothing
    }

    private void initHardwareJoysticks() {
        Activity activity = getActivity();
        inputManager = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);
        for (int inputDeviceId : inputManager.getInputDeviceIds()) {
            if (isInputDeviceHardwareJoystick(inputDeviceId)) {
                addHardwareJoystick(inputDeviceId);
            }
        }
    }

    private void releaseHardwareJoysticks() {
        inputManager.unregisterInputDeviceListener(this);
    }

    private boolean isInputDeviceHardwareJoystick(int deviceId) {
        InputDevice device = inputManager.getInputDevice(deviceId);
        if (device == null) {
            return false;
        }
        return hasSources(device.getSources(), InputDevice.SOURCE_GAMEPAD
                | InputDevice.SOURCE_JOYSTICK);
    }

    private void addHardwareJoystick(int deviceId) {
        InputDevice device = inputManager.getInputDevice(deviceId);
        Map<JoystickButton, String> buttonMap = loadHardwareJoystickMapping(device);
        if (buttonMap == null) {
            buttonMap = getHardwareJoystickDefaultMapping();
        }
        HardwareJoystick joystick = new HardwareJoystick(device, buttonMap);
        hardwareJoysticks.append(deviceId, joystick);
        if (!hasSelectedHardwareJoystick() || isPreferredHardwareJoystick(joystick)) {
            setSelectedHardwareJoystickDeviceId(deviceId);
        }
        notifyHardwareJoystickConnected(joystick);
        Timber.d("Added hardware joystick: %s", joystick);
    }

    private void removeHardwareJoystick(int deviceId) {
        HardwareJoystick joystick = getHardwareJoystickByDeviceId(deviceId);
        if (joystick != null) {
            hardwareJoysticks.remove(deviceId);
            if (getSelectedHardwareJoystickDeviceId() == deviceId) {
                if (getNumHardwareJoysticks() > 0) {
                    setSelectedHardwareJoystickDeviceId(getHardwareJoystickByIndex(0).getDeviceId());
                } else {
                    setSelectedHardwareJoystickDeviceId(-1);
                }
            }
            notifyHardwareJoystickDisconnected(joystick);
            Timber.d("Removed hardware joystick: %s", joystick);
        }
    }

    private void notifyHardwareJoystickConnected(HardwareJoystick joystick) {
        for (HardwareJoystickEventListener listener : hardwareJoystickEventListeners) {
            try {
                listener.onConnected(joystick);
            } catch (Exception e) {
                Timber.e(e, "Can't notify event listener: %s", listener);
            }
        }
    }

    private void notifyHardwareJoystickDisconnected(HardwareJoystick joystick) {
        for (HardwareJoystickEventListener listener : hardwareJoystickEventListeners) {
            try {
                listener.onDisconnected(joystick);
            } catch (Exception e) {
                Timber.e(e, "Can't notify event listener: %s", listener);
            }
        }
    }

    private void notifyHardwareJoystickButtonEvent(HardwareJoystick joystick,
                                                   String buttonEventName,
                                                   JoystickButton button,
                                                   boolean isPressed) {
        for (HardwareJoystickEventListener listener : hardwareJoystickEventListeners) {
            try {
                listener.onButton(joystick, buttonEventName, button, isPressed);
            } catch (Exception e) {
                Timber.e(e, "Can't notify event listener: %s", listener);
            }
        }
    }

    public void addHardwareJoystickEventListener(HardwareJoystickEventListener listener) {
        if (!hardwareJoystickEventListeners.contains(listener)) {
            hardwareJoystickEventListeners.add(listener);
        }
    }

    public void removeHardwareJoystickEventListener(HardwareJoystickEventListener listener) {
        hardwareJoystickEventListeners.remove(listener);
    }

    public boolean isHardwareJoystickPresent() {
        return (getNumHardwareJoysticks() > 0);
    }

    public int getNumHardwareJoysticks() {
        return hardwareJoysticks.size();
    }

    public List<HardwareJoystick> getHardwareJoysticks() {
        int numJoysticks = getNumHardwareJoysticks();
        List<HardwareJoystick> joysticks = new ArrayList<>(numJoysticks);
        for (int i = 0; i < numJoysticks; i++) {
            joysticks.add(getHardwareJoystickByIndex(i));
        }
        return Collections.unmodifiableList(joysticks);
    }

    public HardwareJoystick getHardwareJoystickByDeviceId(int deviceId) {
        return hardwareJoysticks.get(deviceId);
    }

    public HardwareJoystick getHardwareJoystickByIndex(int index) {
        return hardwareJoysticks.valueAt(index);
    }

    public boolean hasSelectedHardwareJoystick() {
        return getSelectedHardwareJoystick() != null;
    }

    public HardwareJoystick getSelectedHardwareJoystick() {
        return (selectedHardwareJoystickDeviceId >= 0)
                ? getHardwareJoystickByDeviceId(selectedHardwareJoystickDeviceId)
                : null;
    }

    public int getSelectedHardwareJoystickDeviceId() {
        return selectedHardwareJoystickDeviceId;
    }

    public void setSelectedHardwareJoystickDeviceId(int deviceId) {
        selectedHardwareJoystickDeviceId = deviceId;
    }

    public int getSelectedHardwareJoystickIndex() {
        return hardwareJoysticks.indexOfKey(selectedHardwareJoystickDeviceId);
    }

    private boolean isPreferredHardwareJoystick(HardwareJoystick joystick) {
        String preferredJoystickDeviceDescriptor = readPreferredHardwareJoystickDeviceDescriptor();
        return joystick.getDeviceDescriptor().equals(preferredJoystickDeviceDescriptor);
    }

    public void setSelectedHardwareJoystickAsPreferred() {
        HardwareJoystick joystick = getSelectedHardwareJoystick();
        if (joystick == null) {
            return;
        }
        String joystickDeviceDescriptor = joystick.getDeviceDescriptor();
        storePreferredHardwareJoystickDeviceDescriptor(joystickDeviceDescriptor);
    }

    private String readPreferredHardwareJoystickDeviceDescriptor() {
        SharedPreferences prefs = getPreferences();
        return prefs.getString(PREFS_KEY_PREFERRED_HARDWARE_JOYSTICK_DEVICE_DESCRIPTOR, null);
    }

    private void storePreferredHardwareJoystickDeviceDescriptor(String descriptor) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY_PREFERRED_HARDWARE_JOYSTICK_DEVICE_DESCRIPTOR, descriptor);
        prefsEditor.apply();
    }

    private SharedPreferences getPreferences() {
        return activity.getPreferences(Context.MODE_PRIVATE);
    }

    private Map<JoystickButton, String> getHardwareJoystickDefaultMapping() {
        Map<JoystickButton, String> buttonMap = new TreeMap<>();
        buttonMap.put(JoystickButton.A, KeyEvent.keyCodeToString(KeyEvent.KEYCODE_BUTTON_A));
        buttonMap.put(JoystickButton.B, KeyEvent.keyCodeToString(KeyEvent.KEYCODE_BUTTON_B));
        buttonMap.put(JoystickButton.SELECT, KeyEvent.keyCodeToString(
                KeyEvent.KEYCODE_BUTTON_SELECT));
        buttonMap.put(JoystickButton.START, KeyEvent.keyCodeToString(
                KeyEvent.KEYCODE_BUTTON_START));
        buttonMap.put(JoystickButton.LEFT, KeyEvent.keyCodeToString(KeyEvent.KEYCODE_DPAD_LEFT));
        buttonMap.put(JoystickButton.RIGHT, KeyEvent.keyCodeToString(KeyEvent.KEYCODE_DPAD_RIGHT));
        buttonMap.put(JoystickButton.UP, KeyEvent.keyCodeToString(KeyEvent.KEYCODE_DPAD_UP));
        buttonMap.put(JoystickButton.DOWN, KeyEvent.keyCodeToString(KeyEvent.KEYCODE_DPAD_DOWN));
        return buttonMap;
    }

    public void saveHardwareJoystickMapping(HardwareJoystick joystick) {
        try {
            String mappingJson = getHardwareJoystickMappingJson(joystick);
            File mappingFile = getHardwareJoystickMappingFile(joystick.getDeviceDescriptor());
            DataUtils.writeDataFile(mappingFile, mappingJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Timber.e(e, "Can't save mapping for %s", joystick.getDeviceName());
        }
    }

    private String getHardwareJoystickMappingJson(HardwareJoystick joystick) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", joystick.getDeviceName());
        jsonObject.put("descriptor", joystick.getDeviceDescriptor());
        jsonObject.put("mapping", joystick.getButtonEventMap());
        return jsonObject.toString();
    }

    private Map<JoystickButton, String> loadHardwareJoystickMapping(InputDevice inputDevice) {
        try {
            Map<JoystickButton, String> buttonMap = new TreeMap<>();
            File mappingFile = getHardwareJoystickMappingFile(inputDevice.getDescriptor());
            byte[] mappingJsonData = DataUtils.readDataFile(mappingFile);
            String mappingJson = new String(mappingJsonData, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(mappingJson);
            JSONObject mappingJsonObject = new JSONObject(jsonObject.getString("mapping"));
            Iterator<String> keys = mappingJsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = mappingJsonObject.getString(key);
                buttonMap.put(JoystickButton.valueOf(key), value);
            }
            return buttonMap;
        } catch (Exception e) {
            Timber.e(e, "Can't load mapping for %s", inputDevice.getName());
            return null;
        }
    }

    private File getHardwareJoystickMappingFile(String deviceDescriptor) throws IOException {
        File mappingDir = new File(getActivity().getFilesDir(), "joystick_mapping");
        if (!mappingDir.exists() && !mappingDir.mkdirs()) {
            throw new IOException("Can't create mapping directory: " + mappingDir);
        }
        return new File(mappingDir, deviceDescriptor + ".json");
    }

    public boolean handleKeyEvent(KeyEvent event, boolean isPressed) {
        int deviceId = event.getDeviceId();
        HardwareJoystick joystick = getSelectedHardwareJoystick();
        if (joystick != null && joystick.getDeviceId() == deviceId) {
            String buttonEventName = KeyEvent.keyCodeToString(event.getKeyCode());
            JoystickButton button = joystick.getButton(buttonEventName);
            if (event.getRepeatCount() == 0) {
                if (button != null) {
                    handleJoystickButton(button, isPressed);
                    View joystickButtonView = findOnScreenJoystickButtonView(button);
                    if (joystickButtonView != null) {
                        joystickButtonView.setPressed(isPressed);
                    }
                }
                notifyHardwareJoystickButtonEvent(joystick, buttonEventName, button, isPressed);
            }
            return true;
        }
        return false;
    }

    public void saveState(Bundle outState) {
        outState.putBoolean(STATE_ON_SCREEN_JOYSTICK_VISIBLE, isOnScreenJoystickVisible());
    }

    public void restoreState(Bundle inState) {
        setOnScreenJoystickVisibility(inState.getBoolean(STATE_ON_SCREEN_JOYSTICK_VISIBLE));
    }
}
