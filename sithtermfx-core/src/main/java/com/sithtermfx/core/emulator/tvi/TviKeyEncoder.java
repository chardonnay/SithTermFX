package com.sithtermfx.core.emulator.tvi;

import com.sithtermfx.core.TerminalKeyEncoder;
import com.sithtermfx.core.util.Ascii;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Configures key mappings for TeleVideo terminal models.
 * <p>
 * TVI function keys send Ctrl-A (SOH, 0x01) followed by a single
 * character that identifies the key. The mapping is:
 * <pre>
 *   F1  = ^A @    F7  = ^A F
 *   F2  = ^A A    F8  = ^A G
 *   F3  = ^A B    F9  = ^A H
 *   F4  = ^A C    F10 = ^A I
 *   F5  = ^A D    F11 = ^A J
 *   F6  = ^A E    F12 = ^A K
 * </pre>
 * Arrow keys send single-byte control codes. The 925 extends this with
 * shifted function keys (Ctrl-A followed by '`' + offset).
 *
 * @author Daniel Mengel
 */
public final class TviKeyEncoder {

    private static final int CTRL_A = 0x01;

    private TviKeyEncoder() {
    }

    /**
     * Configures the given encoder with TVI-family key mappings appropriate
     * for the specified model.
     *
     * @param encoder  the key encoder to configure
     * @param tviModel one of 910, 920, or 925
     */
    public static void configureKeys(TerminalKeyEncoder encoder, int tviModel) {
        configureCommonKeys(encoder);
        configureFunctionKeys(encoder);
        if (tviModel >= 920) {
            configureExtendedKeys(encoder);
        }
        if (tviModel >= 925) {
            configureKeypadKeys(encoder);
        }
    }

    private static void configureCommonKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_UP, Ascii.VT);
        encoder.putCode(VK_DOWN, Ascii.LF);
        encoder.putCode(VK_LEFT, Ascii.BS);
        encoder.putCode(VK_RIGHT, Ascii.FF);
        encoder.putCode(VK_HOME, 0x1E);
        encoder.putCode(VK_BACK_SPACE, Ascii.BS);
        encoder.putCode(VK_ENTER, Ascii.CR);
        encoder.putCode(VK_TAB, Ascii.HT);
    }

    /**
     * F1–F12: Ctrl-A followed by '@', 'A', 'B', ... , 'K'.
     */
    private static void configureFunctionKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_F1, CTRL_A, '@');
        encoder.putCode(VK_F2, CTRL_A, 'A');
        encoder.putCode(VK_F3, CTRL_A, 'B');
        encoder.putCode(VK_F4, CTRL_A, 'C');
        encoder.putCode(VK_F5, CTRL_A, 'D');
        encoder.putCode(VK_F6, CTRL_A, 'E');
        encoder.putCode(VK_F7, CTRL_A, 'F');
        encoder.putCode(VK_F8, CTRL_A, 'G');
        encoder.putCode(VK_F9, CTRL_A, 'H');
        encoder.putCode(VK_F10, CTRL_A, 'I');
        encoder.putCode(VK_F11, CTRL_A, 'J');
        encoder.putCode(VK_F12, CTRL_A, 'K');
    }

    private static void configureExtendedKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_INSERT, Ascii.ESC, 'Q');
        encoder.putCode(VK_DELETE, Ascii.ESC, 'W');
        encoder.putCode(VK_PAGE_UP, Ascii.ESC, 'J');
        encoder.putCode(VK_PAGE_DOWN, Ascii.ESC, 'K');
        encoder.putCode(VK_END, Ascii.ESC, 'T');
    }

    /**
     * Keypad arrow keys on the 925: maps VK_KP_* to the same control codes as regular arrows.
     */
    private static void configureKeypadKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_KP_UP, Ascii.VT);
        encoder.putCode(VK_KP_DOWN, Ascii.LF);
        encoder.putCode(VK_KP_LEFT, Ascii.BS);
        encoder.putCode(VK_KP_RIGHT, Ascii.FF);
    }
}
