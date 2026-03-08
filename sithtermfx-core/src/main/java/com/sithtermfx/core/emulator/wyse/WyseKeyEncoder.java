package com.sithtermfx.core.emulator.wyse;

import com.sithtermfx.core.TerminalKeyEncoder;
import com.sithtermfx.core.util.Ascii;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Configures key mappings for the Wyse terminal family.
 * <p>
 * Wyse native function keys send SOH (0x01) followed by an identifier
 * character: F1 = SOH '@', F2 = SOH 'A', ... F16 = SOH 'O'.
 * <p>
 * Arrow keys use single control characters rather than escape sequences:
 * <ul>
 *   <li>Left  = Ctrl-H (BS, 0x08)</li>
 *   <li>Right = Ctrl-L (0x0C)</li>
 *   <li>Up    = Ctrl-K (VT, 0x0B)</li>
 *   <li>Down  = Ctrl-J (LF, 0x0A)</li>
 * </ul>
 * <p>
 * Higher Wyse levels (60, 160) may define additional shifted function keys
 * (F17–F32) that send SOH followed by 'P'–'_'.
 *
 * @author Daniel Mengel
 */
public final class WyseKeyEncoder {

    private static final int SOH = 0x01;

    private WyseKeyEncoder() {
    }

    /**
     * Applies Wyse-specific key bindings to the given encoder.
     *
     * @param encoder    the key encoder to configure
     * @param wyseLevel  the Wyse level (50, 60, or 160)
     */
    public static void configureKeys(TerminalKeyEncoder encoder, int wyseLevel) {
        configureArrowKeys(encoder);
        configureFunctionKeys(encoder, wyseLevel);
        configureEditingKeys(encoder);
        configureSpecialKeys(encoder);
    }

    /**
     * Wyse arrow keys use single control characters:
     * Left=BS(0x08), Right=0x0C, Up=VT(0x0B), Down=LF(0x0A).
     */
    private static void configureArrowKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_LEFT, Ascii.BS);
        encoder.putCode(VK_RIGHT, 0x0C);
        encoder.putCode(VK_UP, 0x0B);
        encoder.putCode(VK_DOWN, Ascii.LF);

        encoder.putCode(VK_KP_LEFT, Ascii.BS);
        encoder.putCode(VK_KP_RIGHT, 0x0C);
        encoder.putCode(VK_KP_UP, 0x0B);
        encoder.putCode(VK_KP_DOWN, Ascii.LF);
    }

    /**
     * Wyse function keys: F1–F12 send SOH followed by '@' through 'K'.
     * WY-60 and WY-160 extend with F13–F16 (SOH 'L'–'O') and
     * F17–F32 (SOH 'P'–'_').
     */
    private static void configureFunctionKeys(TerminalKeyEncoder encoder, int wyseLevel) {
        encoder.putCode(VK_F1, SOH, '@');
        encoder.putCode(VK_F2, SOH, 'A');
        encoder.putCode(VK_F3, SOH, 'B');
        encoder.putCode(VK_F4, SOH, 'C');
        encoder.putCode(VK_F5, SOH, 'D');
        encoder.putCode(VK_F6, SOH, 'E');
        encoder.putCode(VK_F7, SOH, 'F');
        encoder.putCode(VK_F8, SOH, 'G');
        encoder.putCode(VK_F9, SOH, 'H');
        encoder.putCode(VK_F10, SOH, 'I');
        encoder.putCode(VK_F11, SOH, 'J');
        encoder.putCode(VK_F12, SOH, 'K');

        if (wyseLevel >= 60) {
            encoder.putCode(VK_F13, SOH, 'L');
            encoder.putCode(VK_F14, SOH, 'M');
            encoder.putCode(VK_F15, SOH, 'N');
            encoder.putCode(VK_F16, SOH, 'O');
            encoder.putCode(VK_F17, SOH, 'P');
            encoder.putCode(VK_F18, SOH, 'Q');
            encoder.putCode(VK_F19, SOH, 'R');
            encoder.putCode(VK_F20, SOH, 'S');
            encoder.putCode(VK_F21, SOH, 'T');
            encoder.putCode(VK_F22, SOH, 'U');
            encoder.putCode(VK_F23, SOH, 'V');
            encoder.putCode(VK_F24, SOH, 'W');
        }
    }

    /**
     * Editing keys: Home sends Ctrl-^ (0x1E, RS), End sends Ctrl-Y (0x19).
     * Insert = ESC q, Delete = ESC W, PageUp = ESC J, PageDown = ESC K.
     */
    private static void configureEditingKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_HOME, 0x1E);
        encoder.putCode(VK_END, 0x19);
        encoder.putCode(VK_INSERT, Ascii.ESC, 'q');
        encoder.putCode(VK_DELETE, Ascii.ESC, 'W');
        encoder.putCode(VK_PAGE_UP, Ascii.ESC, 'J');
        encoder.putCode(VK_PAGE_DOWN, Ascii.ESC, 'K');
    }

    /**
     * Special keys: Enter = CR, Backspace = BS, Tab = HT, Escape = ESC.
     */
    private static void configureSpecialKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_ENTER, Ascii.CR);
        encoder.putCode(VK_BACK_SPACE, Ascii.BS);
        encoder.putCode(VK_TAB, Ascii.HT);
        encoder.putCode(VK_ESCAPE, Ascii.ESC);
    }
}
