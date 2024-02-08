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

    // Constant: No code generated by controller for given BK key
    private final static int BK_KEY_CODE_NONE = -1;

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

    public enum BkButton {
        // Buttons - first row
        REPEAT(0201, KeyEvent.KEYCODE_F1), // Repeat (ПОВТ)
        KT(0003, KeyEvent.KEYCODE_F2),  // КТ
        ERASE(0213, KeyEvent.KEYCODE_F3), // Erase from cursor to end of line (=|=>|)
        COLLAPSE(0026, KeyEvent.KEYCODE_F4),  // Collapse edited line by one symbol (|<===)
        EXPAND(0027, KeyEvent.KEYCODE_F5),  // Expand edited line by one symbol (|===>)
        IND_CTRL_SYMBOL(0202, KeyEvent.KEYCODE_F6),  // Indication of control symbol (ИНД СУ)
        BLOCK_EDIT(0204, KeyEvent.KEYCODE_F7),  // Edit blocking (БЛОК РЕД)
        STEP(0200, KeyEvent.KEYCODE_F8),  // Step (ШАГ)
        CLEAR(0014, KeyEvent.KEYCODE_F9), // Clear (СБР)
        STOP(BK_KEY_CODE_NONE, KeyEvent.KEYCODE_F10), // STOP (СТОП aka "КРАСНАЯ КНОПКА")

        // Buttons - second row
        LOW_REGISTER(BK_KEY_CODE_NONE, KeyEvent.KEYCODE_SHIFT_LEFT), // Low register (НР)
        SEMICOLON(0073, KeyEvent.KEYCODE_SEMICOLON), // ; +
        ONE(0061, KeyEvent.KEYCODE_1),  // 1 !
        TWO(0062, KeyEvent.KEYCODE_2),  // 2 "
        THREE(0063, KeyEvent.KEYCODE_3),  // 3 #
        FOUR(0064, KeyEvent.KEYCODE_4),  // 4 $
        FIVE(0065, KeyEvent.KEYCODE_5),  // 5 %
        SIX(0066, KeyEvent.KEYCODE_6),  // 6 &
        SEVEN(0067, KeyEvent.KEYCODE_7),  // 7 '
        EIGHT(0070, KeyEvent.KEYCODE_8),  // 8 (
        NINE(0071, KeyEvent.KEYCODE_9),  // 9 )
        ZERO(0060, KeyEvent.KEYCODE_0),  // 0 {
        MINUS(0055, KeyEvent.KEYCODE_MINUS),  // - =
        SLASH(0057, KeyEvent.KEYCODE_EQUALS),  // / ?
        BACKSPACE(0030, KeyEvent.KEYCODE_DEL),  // Backspace

        // Buttons - third row
        TAB(0211, KeyEvent.KEYCODE_TAB),  // Tabulation (ТАБ)
        J(0112, KeyEvent.KEYCODE_J),  // Й J
        C(0103, KeyEvent.KEYCODE_C),  // Ц C
        U(0125, KeyEvent.KEYCODE_U),  // У U
        K(0113, KeyEvent.KEYCODE_K),  // К K
        E(0105, KeyEvent.KEYCODE_E),  // Е E
        N(0116, KeyEvent.KEYCODE_N),  // Н N
        G(0107, KeyEvent.KEYCODE_G),  // Г G
        LEFT_BRACKET(0133, KeyEvent.KEYCODE_LEFT_BRACKET),  // Ш [
        RIGHT_BRACKET(0135, KeyEvent.KEYCODE_RIGHT_BRACKET),  // Щ ]
        Z(0132, KeyEvent.KEYCODE_Z),  // З Z
        H(0110, KeyEvent.KEYCODE_H),  // Х H
        COLON(0072, KeyEvent.KEYCODE_APOSTROPHE),  // : *
        RIGHT_CURLY_BRACKET(0137, KeyEvent.KEYCODE_GRAVE),  // Ъ }
        LINE_RETURN(0023, KeyEvent.KEYCODE_PAGE_DOWN), // ВС

        // Buttons - fourth row
        CTRL_SYMBOL(BK_KEY_CODE_NONE, KeyEvent.KEYCODE_CTRL_LEFT),  // Control symbol (СУ)
        F(0106, KeyEvent.KEYCODE_F),  // Ф F
        Y(0131, KeyEvent.KEYCODE_Y),  // Ы Y
        W(0127, KeyEvent.KEYCODE_W),  // В W
        A(0101, KeyEvent.KEYCODE_A),  // А A
        P(0120, KeyEvent.KEYCODE_P),  // П P
        R(0122, KeyEvent.KEYCODE_R),  // Р R
        O(0117, KeyEvent.KEYCODE_O),  // О O
        L(0114, KeyEvent.KEYCODE_L),  // Л L
        D(0104, KeyEvent.KEYCODE_D),  // Д D
        V(0126, KeyEvent.KEYCODE_V),  // Ж V
        BACKSLASH(0134, KeyEvent.KEYCODE_BACKSLASH),  // Э Backslash
        PERIOD(0056, KeyEvent.KEYCODE_PERIOD),  // . >
        ENTER(0012, KeyEvent.KEYCODE_ENTER),  // ENTER

        // Buttons - fifth row
        UPPERCASE(BK_KEY_CODE_NONE, KEY_CODE_NONE),  // Uppercase mode (ЗАГЛ)
        LOWERCASE(BK_KEY_CODE_NONE, KEY_CODE_NONE),  // Lowercase mode (СТР)
        Q(0121, KeyEvent.KEYCODE_Q),  // Я Q
        ACCENT(0136, KeyEvent.KEYCODE_SLASH),  // Ч ^
        S(0123, KeyEvent.KEYCODE_S),  // С S
        M(0115, KeyEvent.KEYCODE_M),  // М M
        I(0111, KeyEvent.KEYCODE_I),  // И I
        T(0124, KeyEvent.KEYCODE_T),  // Т T
        X(0130, KeyEvent.KEYCODE_X),  // Ь X
        B(0102, KeyEvent.KEYCODE_B),  // Б B
        COMMERCIAL_AT(0100, KeyEvent.KEYCODE_DPAD_CENTER),  // Ю @
        COMMA(0054, KeyEvent.KEYCODE_COMMA),  // , <

        // Buttons - sixth row and arrows block
        RUS(0016, KeyEvent.KEYCODE_F11),  // Russian mode (РУС)
        AR2(BK_KEY_CODE_NONE, KeyEvent.KEYCODE_ALT_LEFT),  // Alternative register 2 (АР2)
        SPACE(0040, KeyEvent.KEYCODE_SPACE),  // Space bar
        LAT(0017, KeyEvent.KEYCODE_F12),  // Latin mode (ЛАТ)
        LEFT(0010, KeyEvent.KEYCODE_DPAD_LEFT), // Left
        UP(0032, KeyEvent.KEYCODE_DPAD_UP), // Up
        DOWN(0033, KeyEvent.KEYCODE_DPAD_DOWN), // Down
        RIGHT(0031, KeyEvent.KEYCODE_DPAD_RIGHT); // Right

        private final int bkKeyCode;
        private final int androidKeyCode;

        BkButton(int bkKeyCode, int androidKeyCode) {
            this.bkKeyCode = bkKeyCode;
            this.androidKeyCode = androidKeyCode;
        }

        public int getBkKeyCode() {
            return bkKeyCode;
        }

        public int getAndroidKeyCode() {
            return androidKeyCode;
        }

        public static BkButton getByAndroidKeyCode(int androidKeyCode) {
            BkButton result = null;
            for (BkButton code: values()) {
                if (code.getAndroidKeyCode() != KEY_CODE_NONE &&
                        code.getAndroidKeyCode() == androidKeyCode) {
                    result = code;
                    break;
                }
            }
            return result;
        }
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
        initOnScreenKeyboard(activity);
        restoreOnScreenKeyboardDisplayMode();
        restoreOnScreenKeyboardOverlayAlpha();
        setKeyboardController(keyboardController);
        setUppercaseMode(true);
        setLatinMode(true);
        setOnScreenKeyboardVisibility(false);
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
        BkButton bkButton = BkButton.getByAndroidKeyCode(keyCode);
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
