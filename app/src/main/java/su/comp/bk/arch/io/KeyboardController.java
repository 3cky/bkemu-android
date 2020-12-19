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

import su.comp.bk.R;
import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.ui.keyboard.ModifierButton;
import timber.log.Timber;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ImageButton;

/**
 * BK-0010 keyboard controller (К1801ВП1-014).
 */
@SuppressLint({ "InlinedApi", "RtlHardcoded" })
public class KeyboardController implements Device, OnTouchListener {

    /** Keyboard status register address */
    public final static int STATUS_REGISTER_ADDRESS = 0177660;
    // Keyboard status register - vector interrupt masking flag (read/write)
    private final static int STATUS_VIRQ_MASK = (1 << 6);
    // Keyboard status register - data status flag (1 if pressed key code is written to data register,
    // 0 if pressed key code read from data register, read only)
    private final static int STATUS_DATA_READY = (1 << 7);

    /** Keyboard data register (bits 0-6 - pressed key code value, read only) */
    public final static int DATA_REGISTER_ADDRESS = 0177662;

    // Button pressed state flag in SEL1 register (0 - key is pressed, 1 - not pressed, read only)
    private final static int SEL1_REGISTER_BUTTON_PRESSED = (1 << 6);

    // BK-0011M STOP button enabled flag in SEL1 register (1 - disabled, 0 - enabled, write only)
    private final static int SEL1_BK11M_STOP_BUTTON_ENABLED = (1 << 12);

    private final static int[] ADDRESSES = {
        Cpu.REG_SEL1, STATUS_REGISTER_ADDRESS, DATA_REGISTER_ADDRESS
    };

    // Normal (AR2 key is not pressed) VIRQ address
    private final static int VIRQ_ADDRESS_NORMAL = 060;
    // VIRQ address when AR2 key is pressed
    private final static int VIRQ_ADDRESS_AR2 = 0274;

    // Constant: No android keyboard button for given BK key
    private final static int KEY_CODE_NONE = -1;

    // Constant: No code generated by controller for given BK key
    private final static int BK_KEY_CODE_NONE = -1;

    // State save/restore: Status register value
    private static final String STATE_STATUS_REGISTER =
            KeyboardController.class.getName() + "#status_reg";
    // State save/restore: STOP button is blocked flag value
    private static final String STATE_STOP_BUTTON_ENABLED_FLAG =
            KeyboardController.class.getName() + "#stop_button_enabled";

    // Low register modifier key codes lookup table
    private static final byte[] lowRegisterKeyCodeTable = new byte[256];

    // Is STOP button enabled flag
    private boolean isStopButtonEnabled = true;

    // Latin mode flag
    private boolean isLatinMode;

    // Uppercase mode flag
    private boolean isUppercaseMode;

    // Low register modifier key is pressed flag
    private boolean isLowRegisterPressed;

    // AR2 (Alternative Register 2) key is pressed flag
    private boolean isAr2Pressed;

    // Control symbol key is pressed flag
    private boolean isCtrlSymbolPressed;

    // Status register value
    private int statusRegister;

    // Pressed key code data register value
    private int dataRegister;

    /** Key pressing delay (in nanoseconds) */
    private final static long KEY_PRESS_DELAY = (100L * Computer.NANOSECS_IN_MSEC);
    // Button pressed state flag in SEL1 register
    private boolean isButtonPressed;
    // Non-modifier button was pressed flag
    private boolean wasButtonPressed;
    // Last button press timestamp (in CPU clock ticks)
    private long lastButtonPressTimestamp = -1L;

    private final Computer computer;

    private final boolean isComputerBk11m;

    private boolean isOnScreenKeyboardVisible = false;

    private View onScreenKeyboardView;

    private ModifierButton ar2Button;
    private ModifierButton ctrlSymbolButton;
    private ImageButton lowRegisterButton;

    static {
        initializeLookupTables();
    }

    public enum BkButton {
        // Buttons - first row
        REPEAT(0201, KeyEvent.KEYCODE_F1), // Repeat (ПОВТ)
        KT(0003, KeyEvent.KEYCODE_F2),  // КТ
        ERASE(0231, KeyEvent.KEYCODE_F3), // Erase from cursor to end of line (=|=>|)
        COLLAPSE(0026, KeyEvent.KEYCODE_F4),  // Collapse edited line by one symbol (|<===)
        EXPAND(0027, KeyEvent.KEYCODE_F5),  // Expand edited line by one symbol (|===>)
        IND_CTRL_SYMBOL(0202, KeyEvent.KEYCODE_F6),  // Indication of control symbol (ИНД СУ)
        BLOCK_EDIT(0204, KeyEvent.KEYCODE_F7),  // Edit blocking (БЛОК РЕД)
        STEP(0220, KeyEvent.KEYCODE_F8),  // Step (ШАГ)
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

    public KeyboardController(Computer computer) {
        this.computer = computer;
        this.isComputerBk11m = computer.getConfiguration().isMemoryManagerPresent();
    }

    private static void initializeLookupTables() {
        // Initialize low register modifier key codes lookup table
        for (int i = 0; i < 256; i++) {
            lowRegisterKeyCodeTable[i] = (byte) i;
        }
        for (int i = 0100; i <= 0137; i++) {
            lowRegisterKeyCodeTable[i] = (byte) (i + 040);
        }
        lowRegisterKeyCodeTable[';'] = '+';
        lowRegisterKeyCodeTable['1'] = '!';
        lowRegisterKeyCodeTable['2'] = '"';
        lowRegisterKeyCodeTable['3'] = '#';
        lowRegisterKeyCodeTable['4'] = '$';
        lowRegisterKeyCodeTable['5'] = '%';
        lowRegisterKeyCodeTable['6'] = '&';
        lowRegisterKeyCodeTable['7'] = '\'';
        lowRegisterKeyCodeTable['8'] = '(';
        lowRegisterKeyCodeTable['9'] = ')';
        lowRegisterKeyCodeTable['0'] = '{';
        lowRegisterKeyCodeTable['-'] = '=';
        lowRegisterKeyCodeTable['/'] = '?';
        lowRegisterKeyCodeTable[':'] = '*';
        lowRegisterKeyCodeTable['.'] = '>';
        lowRegisterKeyCodeTable[','] = '<';
    }

    public void setOnScreenKeyboardView(ViewGroup keyboardView) {
        this.onScreenKeyboardView = keyboardView;
        for (BkButton bkButton : BkButton.values()) {
            View buttonView = keyboardView.findViewWithTag(bkButton.name());
            if (buttonView != null) {
                buttonView.setOnTouchListener(this);
            } else {
                Timber.w("Can't find view for button: %s", bkButton.name());
            }
        }
        this.ctrlSymbolButton = onScreenKeyboardView.findViewById(R.id.btn_ctrl_symbol);
        this.ar2Button = onScreenKeyboardView.findViewById(R.id.btn_ar2);
        this.lowRegisterButton = onScreenKeyboardView.findViewById(R.id.btn_low_register);
        clearModifierFlags();
    }

    public void setOnScreenKeyboardVisibility(boolean isVisible) {
        this.isOnScreenKeyboardVisible = isVisible;
        onScreenKeyboardView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    public boolean isOnScreenKeyboardVisible() {
        return this.isOnScreenKeyboardVisible;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_DOWN) {
            BkButton bkButton = BkButton.valueOf(v.getTag().toString());
            boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;
            Timber.d("handle button touch event " + (isPressed ? "press" : "release") +
                    ", button: " + bkButton);
            handleBkButton(bkButton, isPressed);
        }
        return false;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init(long cpuTime) {
        setButtonPressed(cpuTime, false);
        setUppercaseMode(true);
        setLatinMode(true);
        writeDataRegister(0);
        setStatusRegisterDataReadyFlag(false);
        writeStatusRegister(STATUS_VIRQ_MASK);
    }

    private int getLowRegisterKeyCode(int keyCode) {
        return lowRegisterKeyCodeTable[keyCode] & 0377;
    }

    private boolean isStopButtonEnabled() {
        return isStopButtonEnabled;
    }

    private void setStopButtonEnabled(boolean isStopButtonEnabled) {
        this.isStopButtonEnabled = isStopButtonEnabled;
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

    private boolean isButtonPressed(long cpuTime) {
        return (lastButtonPressTimestamp >= 0 && computer.cpuTimeToNanos(cpuTime -
                lastButtonPressTimestamp) <= KEY_PRESS_DELAY) || isButtonPressed;
    }

    private void setButtonPressed(long cpuTime, boolean isPressed) {
        this.isButtonPressed = isPressed;
        if (isPressed) {
            this.wasButtonPressed = true;
            this.lastButtonPressTimestamp = cpuTime;
        }
    }

    private boolean wasButtonPressed() {
        return wasButtonPressed;
    }

    private void clearModifierFlags() {
        setLowRegisterPressed(false);
        setCtrlSymbolPressed(false);
        setAr2Pressed(false);
        this.wasButtonPressed = false;
    }

    private boolean isCtrlSymbolPressed() {
        return isCtrlSymbolPressed;
    }

    private void setCtrlSymbolPressed(boolean isCtrlSymbolPressed) {
        this.isCtrlSymbolPressed = isCtrlSymbolPressed;
        this.ctrlSymbolButton.setChecked(isCtrlSymbolPressed);
    }

    private boolean isAr2Pressed() {
        return isAr2Pressed;
    }

    private void setAr2Pressed(boolean isAr2Pressed) {
        this.isAr2Pressed = isAr2Pressed;
        this.ar2Button.setChecked(isAr2Pressed);
    }

    private boolean isLowRegisterPressed() {
        return isLowRegisterPressed;
    }

    private void setLowRegisterPressed(boolean isLowRegisterPressed) {
        this.isLowRegisterPressed = isLowRegisterPressed;
        this.lowRegisterButton.setImageResource(isLowRegisterPressed
                ? R.drawable.arrow_shift_on : R.drawable.arrow_shift);
    }

    private void setStatusRegisterDataReadyFlag(boolean isDataReady) {
        this.statusRegister = isDataReady ? (this.statusRegister | STATUS_DATA_READY)
                : (this.statusRegister & ~STATUS_DATA_READY);
        if (isDataReady) {
            if (isStatusRegisterVirqEnabled()) {
                computer.getCpu().requestVirq((dataRegister & 0200) != 0
                        ? VIRQ_ADDRESS_AR2 : VIRQ_ADDRESS_NORMAL);
            }
        } else {
            computer.getCpu().clearVirqRequest();
        }
    }

    private boolean isStatusRegisterVirqEnabled() {
        return (readStatusRegister() & STATUS_VIRQ_MASK) == 0;
    }

    private boolean isStatusRegisterDataReady() {
        return (readStatusRegister() & STATUS_DATA_READY) != 0;
    }

    private void writeStatusRegister(int value) {
        this.statusRegister = (value & STATUS_VIRQ_MASK)
                | (this.statusRegister & STATUS_DATA_READY);
    }

    private int readStatusRegister() {
        return this.statusRegister;
    }

    private void writeDataRegister(int value) {
        this.dataRegister = value;
        setStatusRegisterDataReadyFlag(true);
    }

    private int readDataRegister() {
        setStatusRegisterDataReadyFlag(false);
        return this.dataRegister & 0177;
    }

    @Override
    public int read(long cpuTime, int address) {
        switch (address) {
            case STATUS_REGISTER_ADDRESS:
                return readStatusRegister();
            case DATA_REGISTER_ADDRESS:
                return readDataRegister();
            default:
                return isButtonPressed(cpuTime) ? 0 : SEL1_REGISTER_BUTTON_PRESSED;
        }
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        boolean isWritten = false;
        switch (address & 0177776) {
            case STATUS_REGISTER_ADDRESS:
                if (isByteMode) {
                    int reg = readStatusRegister();
                    value |= (address & 1) == 0 ? reg & 0177400 : reg & 0377;
                }
                writeStatusRegister(value);
                isWritten = true;
                break;
            case Cpu.REG_SEL1:
                // Check for BK-0011M STOP button enabled flag state if register is selected
                if (isComputerBk11m && (value & MemoryManager.ENABLE_BIT) == 0) {
                    setStopButtonEnabled((value & SEL1_BK11M_STOP_BUTTON_ENABLED) == 0);
                    isWritten = true;
                }
                break;
            default:
                // Data register is read only and not respond to the CPU bus write request
                break;
        }
        return isWritten;
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
        }
        BkButton bkButton = BkButton.getByAndroidKeyCode(keyCode);
        return handleBkButton(bkButton, isKeyPress);
    }

    /**
     * Handle BK keyboard button key press/release.
     * @param bkButton {@link BkButton} to handle
     * @param isPressed <code>true</code> if button was pressed, <code>false</code> if released
     * @return <code>true</code> if key code was handled by keyboard controller,
     * <code>false</code> otherwise
     */
    private synchronized boolean handleBkButton(BkButton bkButton, boolean isPressed) {
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
                    boolean isLowRegister = !isLetterButton(bkKeyCode) ? isLowRegisterPressed() :
                            isLatinMode() ^ (isUppercaseMode() || isLowRegisterPressed());
                    if (isLowRegister) {
                        bkKeyCode = getLowRegisterKeyCode(bkKeyCode);
                    }
                }
                // Set button pressed state
                setButtonPressed(computer.getCpu().getTime(), isPressed);
                // Write new key code to data register only if previous key code was read
                if (isPressed && !isStatusRegisterDataReady()) {
                    writeDataRegister(isAr2Pressed() ? (bkKeyCode | 0200) : bkKeyCode);
                }
            } else {
                // Handle special buttons
                switch (bkButton) {
                    case STOP:
                        if (isPressed && isStopButtonEnabled()) {
                            computer.getCpu().requestIrq1();
                        }
                        break;
                    case LOW_REGISTER:
                        boolean lowRegisterModifierState = isLowRegisterPressed();
                        if (isPressed || wasButtonPressed()) {
                            clearModifierFlags();
                            setLowRegisterPressed(!lowRegisterModifierState);
                        }
                        break;
                    case AR2:
                        boolean ar2ModifierState = isAr2Pressed();
                        if (isPressed || wasButtonPressed()) {
                            clearModifierFlags();
                            setAr2Pressed(!ar2ModifierState);
                        }
                        break;
                    case CTRL_SYMBOL:
                        boolean ctrlSymbolrState = isCtrlSymbolPressed();
                        if (isPressed || wasButtonPressed()) {
                            clearModifierFlags();
                            setCtrlSymbolPressed(!ctrlSymbolrState);
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

    private static boolean isLetterButton(int bkKeyCode) {
        return (0100 <= bkKeyCode && bkKeyCode <= 0137);
    }

    @Override
    public void saveState(Bundle outState) {
        // Save VIRQ mask state from status register
        outState.putInt(STATE_STATUS_REGISTER, readStatusRegister() & STATUS_VIRQ_MASK);
        // Save STOP button enabled flag state
        outState.putBoolean(STATE_STOP_BUTTON_ENABLED_FLAG, isStopButtonEnabled());
    }

    @Override
    public void restoreState(Bundle inState) {
        writeStatusRegister(inState.getInt(STATE_STATUS_REGISTER));
        setStopButtonEnabled(inState.getBoolean(STATE_STOP_BUTTON_ENABLED_FLAG));
    }

}
