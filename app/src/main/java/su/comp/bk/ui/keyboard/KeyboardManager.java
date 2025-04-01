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

package su.comp.bk.ui.keyboard;

import static su.comp.bk.arch.io.KeyboardController.BK_KEY_CODE_NONE;
import su.comp.bk.arch.io.KeyboardController.BkButton;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.RelativeLayout;

import java.util.HashMap;
import java.util.Map;

import su.comp.bk.R;
import su.comp.bk.arch.io.KeyboardController;
import su.comp.bk.ui.BkEmuActivity;
import timber.log.Timber;

/**
 * On-screen and hardware keyboards manager.
 */
public class KeyboardManager implements OnTouchListener, View.OnClickListener {
    // Settings save/restore: on-screen keyboard display mode
    private static final String PREFS_KEY_ON_SCREEN_KEYBOARD_DISPLAY_MODE =
            BkEmuActivity.APP_PACKAGE_NAME + ".ui.keyboard.KeyboardManager" +
                    "/onScreenKeyboardDisplayMode";
    // Settings save/restore: on-screen keyboard overlay alpha
    private static final String PREFS_KEY_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA =
            BkEmuActivity.APP_PACKAGE_NAME + ".ui.keyboard.KeyboardManager" +
                    "/onScreenKeyboardOverlayAlpha";

    // State save/restore key prefix
    private static final String STATE_PREFIX = KeyboardManager.class.getName();
    // State save/restore: On-screen keyboard is visible flag state
    private static final String STATE_KEYBOARD_VISIBLE = STATE_PREFIX + "#keyboard_visible";
    // State save/restore: Latin mode flag state
    private static final String STATE_LATIN_MODE = STATE_PREFIX + "#latin_mode";
    // State save/restore: Uppercase mode flag state
    private static final String STATE_UPPERCASE_MODE = STATE_PREFIX + "#uppercase_mode";
    // State save/restore: Low register key sticky mode flag
    private static final String STATE_LOW_REGISTER_STICKY_MODE = STATE_PREFIX + "#low_register_sticky_mode";
    // State save/restore: AR2 key sticky mode flag
    private static final String STATE_AR2_STICKY_MODE = STATE_PREFIX + "#ar2_sticky_mode";
    // State save/restore: Control symbol key sticky mode flag
    private static final String STATE_CTRL_STICKY_MODE = STATE_PREFIX + "#ctrl_sticky_mode";

    // Constant: No android keyboard button for given BK key
    private final static int KEY_CODE_NONE = -1;

    private final Map<Integer, BkButton> hardwareKeyboardMapping = new HashMap<>();

    private boolean isOnScreenKeyboardVisible = false;

    private View onScreenKeyboardView;
    private View onScreenKeyboardOverlayFrame;

    public enum OnScreenKeyboardDisplayMode {
        NORMAL,
        OVERLAY
    }

    private final static OnScreenKeyboardDisplayMode DEFAULT_ON_SCREEN_KEYBOARD_DISPLAY_MODE =
            OnScreenKeyboardDisplayMode.NORMAL;
    private OnScreenKeyboardDisplayMode onScreenKeyboardDisplayMode;

    public final static float MIN_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA = 0.1f;
    public final static float MAX_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA = 0.9f;
    private final static float DEFAULT_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA = 0.5f;
    private float onScreenKeyboardOverlayAlpha;

    // Low register BK key codes lookup table
    private static final byte[] lowRegisterBkKeyCodeTable = new byte[256];

    // Latin mode flag
    private boolean isLatinMode;

    // Uppercase mode flag
    private boolean isUppercaseMode;

    // Low register modifier key is pressed flag
    private boolean isLowRegisterPressed;
    // Low register modifier key is pressed sticky flag
    private boolean isLowRegisterPressedSticky;

    // AR2 (Alternative Register 2) key is pressed flag
    private boolean isAr2Pressed;
    // AR2 (Alternative Register 2) key is pressed sticky flag
    private boolean isAr2PressedSticky;

    // Control symbol key is pressed flag
    private boolean isCtrlSymbolPressed;
    // Control symbol key is pressed sticky flag
    private boolean isCtrlSymbolPressedSticky;

    // Non-modifier button was pressed
    private boolean wasNonModifierButtonPressed;

    private ModifierButton ar2Button;
    private ModifierButton ctrlSymbolButton;
    private ModifierButton lowRegisterButton;

    private KeyboardController keyboardController;

    private Activity activity;

    static {
        initializeLookupTables();
    }

    private static void initializeLookupTables() {
        // Initialize low register modifier key codes lookup table
        for (int i = 0; i < 256; i++) {
            lowRegisterBkKeyCodeTable[i] = (byte) i;
        }
        for (int i = 0100; i <= 0137; i++) {
            lowRegisterBkKeyCodeTable[i] = (byte) (i + 040);
        }
        lowRegisterBkKeyCodeTable[';'] = '+';
        lowRegisterBkKeyCodeTable['1'] = '!';
        lowRegisterBkKeyCodeTable['2'] = '"';
        lowRegisterBkKeyCodeTable['3'] = '#';
        lowRegisterBkKeyCodeTable['4'] = '$';
        lowRegisterBkKeyCodeTable['5'] = '%';
        lowRegisterBkKeyCodeTable['6'] = '&';
        lowRegisterBkKeyCodeTable['7'] = '\'';
        lowRegisterBkKeyCodeTable['8'] = '(';
        lowRegisterBkKeyCodeTable['9'] = ')';
        lowRegisterBkKeyCodeTable['0'] = ' ';
        lowRegisterBkKeyCodeTable['-'] = '=';
        lowRegisterBkKeyCodeTable['/'] = '?';
        lowRegisterBkKeyCodeTable[':'] = '*';
        lowRegisterBkKeyCodeTable['.'] = '>';
        lowRegisterBkKeyCodeTable[','] = '<';
    }

    public KeyboardManager() {
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void init(BkEmuActivity activity, KeyboardController keyboardController) {
        setActivity(activity);
        setupHardwareKeyboardLayout();
        initOnScreenKeyboard(activity);
        restoreOnScreenKeyboardDisplayMode();
        restoreOnScreenKeyboardOverlayAlpha();
        setKeyboardController(keyboardController);
        setUppercaseMode(true);
        setLatinMode(true);
        setOnScreenKeyboardVisibility(false);
    }

    private void setupHardwareKeyboardLayout() {
        // Buttons - first row
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F1, BkButton.REPEAT); // Repeat (ПОВТ)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F2, BkButton.KT);  // КТ
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F3, BkButton.ERASE); // Erase from cursor to end of line (=|=>|)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F4, BkButton.COLLAPSE);  // Collapse edited line by one symbol (|<===)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F5, BkButton.EXPAND);  // Expand edited line by one symbol (|===>)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F6, BkButton.IND_CTRL_SYMBOL);  // Indication of control symbol (ИНД СУ)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F7, BkButton.BLOCK_EDIT);  // Edit blocking (БЛОК РЕД)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F8, BkButton.STEP);  // Step (ШАГ)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F9, BkButton.CLEAR); // Clear (СБР)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F10, BkButton.STOP); // STOP (СТОП aka "КРАСНАЯ КНОПКА")

        // Buttons - second row
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_SHIFT_LEFT, BkButton.LOW_REGISTER); // Low register (НР)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_SEMICOLON, BkButton.SEMICOLON); // ; +
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_1, BkButton.ONE);  // 1 !
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_2, BkButton.TWO);  // 2 "
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_3, BkButton.THREE);  // 3 #
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_4, BkButton.FOUR);  // 4 $
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_5, BkButton.FIVE);  // 5 %
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_6, BkButton.SIX);  // 6 &
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_7, BkButton.SEVEN);  // 7 '
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_8, BkButton.EIGHT);  // 8 (
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_9, BkButton.NINE);  // 9 )
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_0, BkButton.ZERO);  // 0 {
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_MINUS, BkButton.MINUS);  // - =
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_EQUALS, BkButton.SLASH);  // / ?
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_DEL, BkButton.BACKSPACE);  // Backspace

        // Buttons - third row
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_TAB, BkButton.TAB);  // Tabulation (ТАБ)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_J, BkButton.J);  // Й J
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_C, BkButton.C);  // Ц C
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_U, BkButton.U);  // У U
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_K, BkButton.K);  // К K
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_E, BkButton.E);  // Е E
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_N, BkButton.N);  // Н N
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_G, BkButton.G);  // Г G
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_LEFT_BRACKET, BkButton.LEFT_BRACKET);  // Ш [
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_RIGHT_BRACKET, BkButton.RIGHT_BRACKET);  // Щ ]
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_Z, BkButton.Z);  // З Z
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_H, BkButton.H);  // Х H
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_APOSTROPHE, BkButton.COLON);  // : *
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_GRAVE, BkButton.RIGHT_CURLY_BRACKET);  // Ъ }
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_PAGE_UP, BkButton.LINE_RETURN); // ВС

        // Buttons - fourth row
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_CTRL_LEFT, BkButton.CTRL_SYMBOL);  // Control symbol (СУ)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F, BkButton.F);  // Ф F
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_Y, BkButton.Y);  // Ы Y
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_W, BkButton.W);  // В W
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_A, BkButton.A);  // А A
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_P, BkButton.P);  // П P
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_R, BkButton.R);  // Р R
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_O, BkButton.O);  // О O
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_L, BkButton.L);  // Л L
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_D, BkButton.D);  // Д D
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_V, BkButton.V);  // Ж V
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_BACKSLASH, BkButton.BACKSLASH);  // Э Backslash
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_PERIOD, BkButton.PERIOD);  // . >
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_ENTER, BkButton.ENTER);  // ENTER

        // Buttons - fifth row
        addHardwareKeyboardMapping(KEY_CODE_NONE, BkButton.UPPERCASE);  // Uppercase mode (ЗАГЛ)
        addHardwareKeyboardMapping(KEY_CODE_NONE, BkButton.LOWERCASE);  // Lowercase mode (СТР)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_Q, BkButton.Q);  // Я Q
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_SLASH, BkButton.ACCENT);  // Ч ^
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_S, BkButton.S);  // С S
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_M, BkButton.M);  // М M
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_I, BkButton.I);  // И I
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_T, BkButton.T);  // Т T
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_X, BkButton.X);  // Ь X
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_B, BkButton.B);  // Б B
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_PAGE_DOWN, BkButton.COMMERCIAL_AT);  // Ю @
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_COMMA, BkButton.COMMA);  // , <

        // Buttons - sixth row and arrows block
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F11, BkButton.RUS);  // Russian mode (РУС)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_ALT_LEFT, BkButton.AR2);  // Alternative register 2 (АР2)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_SPACE, BkButton.SPACE);  // Space bar
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_F12, BkButton.LAT);  // Latin mode (ЛАТ)
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_DPAD_LEFT, BkButton.LEFT); // Left
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_DPAD_UP, BkButton.UP); // Up
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_DPAD_DOWN, BkButton.DOWN); // Down
        addHardwareKeyboardMapping(KeyEvent.KEYCODE_DPAD_RIGHT, BkButton.RIGHT); // Right
    }

    private void addHardwareKeyboardMapping(int androidKeyCode, BkButton bkButton) {
        hardwareKeyboardMapping.put(androidKeyCode, bkButton);
    }

    private BkButton getHardwareKeyboardMapping(int androidKeyCode) {
        return hardwareKeyboardMapping.get(androidKeyCode);
    }

    private void initOnScreenKeyboard(BkEmuActivity bkEmuActivity) {
        onScreenKeyboardOverlayFrame = bkEmuActivity.findViewById(R.id.overlay_frame);
        onScreenKeyboardView = bkEmuActivity.findViewById(R.id.keyboard);
        for (BkButton bkButton : BkButton.values()) {
            View buttonView = onScreenKeyboardView.findViewWithTag(bkButton.name());
            if (buttonView != null) {
                buttonView.setOnTouchListener(this);
                buttonView.setOnClickListener(this);
            } else {
                Timber.w("Can't find view for button: %s", bkButton.name());
            }
        }
        ctrlSymbolButton = onScreenKeyboardView.findViewById(R.id.btn_ctrl_symbol);
        ar2Button = onScreenKeyboardView.findViewById(R.id.btn_ar2);
        lowRegisterButton = onScreenKeyboardView.findViewById(R.id.btn_low_register);
        releaseStickyButtons();
    }

    private void prepareOnScreenKeyboard() {
        if (!(onScreenKeyboardOverlayFrame instanceof RelativeLayout)) {
            return;
        }
        updateOnScreenKeyboardOverlayAlpha();
        RelativeLayout.LayoutParams overlayFrameLayoutParams =
                (RelativeLayout.LayoutParams) onScreenKeyboardOverlayFrame.getLayoutParams();
        if (onScreenKeyboardDisplayMode == OnScreenKeyboardDisplayMode.OVERLAY) {
            overlayFrameLayoutParams.removeRule(RelativeLayout.ABOVE);
        } else {
            overlayFrameLayoutParams.addRule(RelativeLayout.ABOVE, R.id.keyboard);
        }
        onScreenKeyboardOverlayFrame.requestLayout();
    }

    private SharedPreferences getPreferences() {
        return getActivity().getPreferences(Context.MODE_PRIVATE);
    }

    private void saveOnScreenKeyboardOverlayAlpha() {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putFloat(PREFS_KEY_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA,
                getOnScreenKeyboardOverlayAlpha());
        prefsEditor.apply();
    }

    private void restoreOnScreenKeyboardOverlayAlpha() {
        SharedPreferences prefs = getPreferences();
        onScreenKeyboardOverlayAlpha = prefs.getFloat(PREFS_KEY_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA,
                DEFAULT_ON_SCREEN_KEYBOARD_OVERLAY_ALPHA);
    }

    private void updateOnScreenKeyboardOverlayAlpha() {
        onScreenKeyboardView.setAlpha(onScreenKeyboardDisplayMode
                == OnScreenKeyboardDisplayMode.OVERLAY ? onScreenKeyboardOverlayAlpha : 1.0f);
    }

    public void setOnScreenKeyboardOverlayAlpha(float alpha) {
        this.onScreenKeyboardOverlayAlpha = alpha;
        updateOnScreenKeyboardOverlayAlpha();
        saveOnScreenKeyboardOverlayAlpha();
    }

    public float getOnScreenKeyboardOverlayAlpha() {
        return onScreenKeyboardOverlayAlpha;
    }

    private void saveOnScreenKeyboardDisplayMode() {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY_ON_SCREEN_KEYBOARD_DISPLAY_MODE,
                getOnScreenKeyboardDisplayMode().name());
        prefsEditor.apply();
    }

    private void restoreOnScreenKeyboardDisplayMode() {
        SharedPreferences prefs = getPreferences();
        String modeName = prefs.getString(PREFS_KEY_ON_SCREEN_KEYBOARD_DISPLAY_MODE,
                DEFAULT_ON_SCREEN_KEYBOARD_DISPLAY_MODE.name());
        onScreenKeyboardDisplayMode = OnScreenKeyboardDisplayMode.valueOf(modeName);
    }

    public void setOnScreenKeyboardDisplayMode(OnScreenKeyboardDisplayMode mode) {
        this.onScreenKeyboardDisplayMode = mode;
        prepareOnScreenKeyboard();
        saveOnScreenKeyboardDisplayMode();
    }

    public OnScreenKeyboardDisplayMode getOnScreenKeyboardDisplayMode() {
        return onScreenKeyboardDisplayMode;
    }

    public void setOnScreenKeyboardVisibility(boolean isVisible) {
        isOnScreenKeyboardVisible = isVisible;
        if (isVisible) {
            prepareOnScreenKeyboard();
        }
        onScreenKeyboardView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    public boolean isOnScreenKeyboardVisible() {
        return isOnScreenKeyboardVisible;
    }

    public void setKeyboardController(KeyboardController keyboardController) {
        this.keyboardController = keyboardController;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_DOWN
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            BkButton bkButton = BkButton.valueOf(v.getTag().toString());
            boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;
            Timber.d("handle button touch event: %s %s", bkButton,
                    isPressed ? "pressed" : "released");
            v.setPressed(isPressed);
            return handleBkButton(bkButton, isPressed, true);
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        BkButton bkButton = BkButton.valueOf(v.getTag().toString());
        Timber.d("handle button click event: %s", bkButton);
        handleBkButton(bkButton, true, true);
        handleBkButton(bkButton, false, true);
    }

    public void saveState(Bundle outState) {
        outState.putBoolean(STATE_KEYBOARD_VISIBLE, isOnScreenKeyboardVisible);
        outState.putBoolean(STATE_LATIN_MODE, isLatinMode);
        outState.putBoolean(STATE_UPPERCASE_MODE, isUppercaseMode);
        outState.putBoolean(STATE_LOW_REGISTER_STICKY_MODE, isLowRegisterPressedSticky);
        outState.putBoolean(STATE_AR2_STICKY_MODE, isAr2PressedSticky);
        outState.putBoolean(STATE_CTRL_STICKY_MODE, isCtrlSymbolPressedSticky);
    }

    public void restoreState(Bundle inState) {
        setCtrlSymbolPressedSticky(inState.getBoolean(STATE_CTRL_STICKY_MODE));
        setAr2PressedSticky(inState.getBoolean(STATE_AR2_STICKY_MODE));
        setLowRegisterPressedSticky(inState.getBoolean(STATE_LOW_REGISTER_STICKY_MODE));
        setUppercaseMode(inState.getBoolean(STATE_UPPERCASE_MODE, true));
        setLatinMode(inState.getBoolean(STATE_LATIN_MODE, true));
        setOnScreenKeyboardVisibility(inState.getBoolean(STATE_KEYBOARD_VISIBLE));
    }

    private boolean isUppercaseMode() {
        return isUppercaseMode;
    }

    private void setUppercaseMode(boolean isUppercaseMode) {
        this.isUppercaseMode = isUppercaseMode;
    }

    private boolean isLatinMode() {
        return isLatinMode;
    }

    private void setLatinMode(boolean isLatinMode) {
        this.isLatinMode = isLatinMode;
    }

    private void releaseStickyButtons() {
        setLowRegisterPressedSticky(false);
        setCtrlSymbolPressedSticky(false);
        setAr2PressedSticky(false);
    }

    private boolean isCtrlSymbolPressed() {
        return isCtrlSymbolPressed;
    }

    private void setCtrlSymbolPressed(boolean isPressed) {
        isCtrlSymbolPressed = isPressed;
    }

    private boolean isCtrlSymbolPressedSticky() {
        return isCtrlSymbolPressedSticky;
    }

    private void setCtrlSymbolPressedSticky(boolean isPressed) {
        if (isPressed || isCtrlSymbolPressedSticky) {
            setCtrlSymbolPressed(isPressed);
        }
        isCtrlSymbolPressedSticky = isPressed;
        ctrlSymbolButton.setChecked(isPressed);
    }

    private boolean isAr2Pressed() {
        return isAr2Pressed;
    }

    private void setAr2Pressed(boolean isPressed) {
        isAr2Pressed = isPressed;
    }

    public boolean isAr2PressedSticky() {
        return isAr2PressedSticky;
    }

    private void setAr2PressedSticky(boolean isPressed) {
        if (isPressed || isAr2PressedSticky) {
            setAr2Pressed(isPressed);
        }
        isAr2PressedSticky = isPressed;
        ar2Button.setChecked(isPressed);
    }

    private boolean isLowRegisterPressed() {
        return isLowRegisterPressed;
    }

    private void setLowRegisterPressed(boolean isPressed) {
        isLowRegisterPressed = isPressed;
    }

    private boolean isLowRegisterPressedSticky() {
        return isLowRegisterPressedSticky;
    }

    private void setLowRegisterPressedSticky(boolean isPressed) {
        if (isPressed || isLowRegisterPressedSticky) {
            setLowRegisterPressed(isPressed);
        }
        isLowRegisterPressedSticky = isPressed;
        lowRegisterButton.setChecked(isPressed);
    }

    private boolean isWasNonModifierButtonPressed() {
        return wasNonModifierButtonPressed;
    }

    private void setWasNonModifierButtonPressed(boolean wasPressed) {
        wasNonModifierButtonPressed = wasPressed;
    }

    /**
     * Handle android keyboard (hardware or virtual) key press/release.
     * @param keyCode Key code (see {@link KeyEvent} constants)
     * @param isKeyPress <code>true</code> if key was pressed, <code>false</code> if released
     * @return <code>true</code> if key code was handled by keyboard controller,
     * <code>false</code> otherwise
     */
    public boolean handleKeyCode(int keyCode, boolean isKeyPress) {
        Timber.d("handle key " + (isKeyPress ? "press" : "release") + ", code: " + keyCode);
        // Handle special cases
        if (keyCode == KeyEvent.KEYCODE_AT) {
            // Some hardware keyboards (i.e. emulator) translate SHIFT + 2 in KEYCODE_AT
            // instead of two events (KEYCODE_SHIFT_LEFT + KEYCODE_2)
            setLowRegisterPressed(isKeyPress);
            keyCode = KeyEvent.KEYCODE_2;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            keyCode = KeyEvent.KEYCODE_ENTER;
        }
        BkButton bkButton = getHardwareKeyboardMapping(keyCode);
        showOnScreenKeyboardButtonState(bkButton, isKeyPress);
        return handleBkButton(bkButton, isKeyPress, false);
    }

    private void showOnScreenKeyboardButtonState(BkButton bkButton, boolean isPressed) {
        if (bkButton == null || onScreenKeyboardView == null) {
            return;
        }
        View buttonView = onScreenKeyboardView.findViewWithTag(bkButton.name());
        if (buttonView != null) {
            buttonView.setPressed(isPressed);
        }
    }

    private static int getLowRegisterBkKeyCode(int keyCode) {
        return lowRegisterBkKeyCodeTable[keyCode] & 0377;
    }

    private static boolean isLetterBkButton(int bkKeyCode) {
        return (0100 <= bkKeyCode && bkKeyCode <= 0137);
    }

    /**
     * Handle BK keyboard button key press/release.
     * @param bkButton {@link BkButton} to handle
     * @param isPressed <code>true</code> if button was pressed, <code>false</code> if released
     * @param isOnScreenKeyboard <code>true</code> if pressed/released on-screen keyboard button,
     *                         <code>false</code> if pressed/released physical (USB) keyboard button
     * @return <code>true</code> if key code was handled by keyboard controller,
     * <code>false</code> otherwise
     */
    private synchronized boolean handleBkButton(BkButton bkButton, boolean isPressed,
                                                boolean isOnScreenKeyboard) {
        boolean isKeyCodeHandled = false;
        if (bkButton != null) {
            int bkKeyCode = bkButton.getBkKeyCode();
            if (bkKeyCode != BK_KEY_CODE_NONE) {
                // Handle button with key code
                if (bkButton == BkButton.LAT || bkButton == BkButton.RUS) {
                    if (isPressed) {
                        setLatinMode(bkButton == BkButton.LAT);
                    }
                } else {
                    // Check Control Symbol modifier state
                    if (isCtrlSymbolPressed() && (bkKeyCode & 0100) != 0) {
                        bkKeyCode &= 037;
                    }
                    // Check Low Register modifier and uppercase mode states
                    boolean isLowRegister = !isLetterBkButton(bkKeyCode) ? isLowRegisterPressed() :
                            isLatinMode() ^ (isUppercaseMode() || isLowRegisterPressed());
                    if (isLowRegister) {
                        bkKeyCode = getLowRegisterBkKeyCode(bkKeyCode);
                    }
                }
                // Apply AR2 modifier state
                bkKeyCode = isAr2Pressed() ? (bkKeyCode | 0200) : bkKeyCode;
                // Handle button press or release in keyboard controller
                keyboardController.handleButton(bkButton, bkKeyCode, isPressed);
                // Set non-modifier button pressed flag
                setWasNonModifierButtonPressed(true);
                // Release sticky buttons on non-modifier on-screen keyboard button release
                if (isOnScreenKeyboard && !isPressed) {
                    releaseStickyButtons();
                }
            } else {
                // Handle special buttons
                switch (bkButton) {
                    case STOP:
                        if (isPressed) {
                            keyboardController.stopButtonPressed();
                        }
                        break;
                    case LOW_REGISTER:
                        if (isOnScreenKeyboard) {
                            if (isPressed) {
                                setLowRegisterPressed(true);
                                setWasNonModifierButtonPressed(false);
                            } else if (isLowRegisterPressedSticky()) {
                                setLowRegisterPressedSticky(false);
                            } else if (!isWasNonModifierButtonPressed()) {
                                setLowRegisterPressedSticky(true);
                            }
                        } else {
                            if (!isPressed) {
                                releaseStickyButtons();
                            }
                            setLowRegisterPressed(isPressed);
                        }
                        break;
                    case AR2:
                        if (isOnScreenKeyboard) {
                            if (isPressed) {
                                setAr2Pressed(true);
                                setWasNonModifierButtonPressed(false);
                            } else if (isAr2PressedSticky()) {
                                setAr2PressedSticky(false);
                            } else if (!isWasNonModifierButtonPressed()) {
                                setAr2PressedSticky(true);
                            }
                        } else {
                            if (!isPressed) {
                                releaseStickyButtons();
                            }
                            setAr2Pressed(isPressed);
                        }
                        break;
                    case CTRL_SYMBOL:
                        if (isOnScreenKeyboard) {
                            if (isPressed) {
                                setCtrlSymbolPressed(true);
                                setWasNonModifierButtonPressed(false);
                            } else if (isCtrlSymbolPressedSticky()) {
                                setCtrlSymbolPressedSticky(false);
                            } else if (!isWasNonModifierButtonPressed()) {
                                setCtrlSymbolPressedSticky(true );
                            }
                        } else {
                            if (!isPressed) {
                                releaseStickyButtons();
                            }
                            setCtrlSymbolPressed(isPressed);
                        }
                        break;
                    case UPPERCASE:
                        if (isPressed) {
                            setUppercaseMode(true);
                        }
                        break;
                    case LOWERCASE:
                        if (isPressed) {
                            setUppercaseMode(false);
                        }
                        break;
                    default:
                        break;
                }
            }
            isKeyCodeHandled = true;
        }
        return isKeyCodeHandled;
    }
}
