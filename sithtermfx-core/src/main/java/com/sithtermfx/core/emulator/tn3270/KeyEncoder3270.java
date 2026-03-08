package com.sithtermfx.core.emulator.tn3270;

import com.sithtermfx.core.TerminalKeyEncoder;
import com.sithtermfx.core.input.InputEvent;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Configures key mappings for the 3270 block-mode terminal emulator.
 * <p>
 * Unlike character-mode terminals that send escape sequences for each key,
 * the 3270 maps keys to AID bytes and cursor-navigation commands. The key
 * codes emitted here are later interpreted by {@link Tn3270Emulator} to
 * trigger the appropriate 3270 action.
 * <p>
 * Encoding convention: each AID or action is encoded as a two-byte sequence
 * where the first byte is 0x00 (NUL, acting as a 3270-specific sentinel)
 * and the second byte identifies the action.
 * <ul>
 *   <li>0x00 + AID byte → AID key (Enter, PF1–PF24, PA1–PA3, Clear)</li>
 *   <li>0x00 + 0x01 → Tab forward (next unprotected field)</li>
 *   <li>0x00 + 0x02 → Tab backward (previous unprotected field)</li>
 *   <li>0x00 + 0x03 → Home (first unprotected field)</li>
 *   <li>0x00 + 0x04 → Delete character</li>
 *   <li>0x00 + 0x05 → Erase (backspace)</li>
 *   <li>0x00 + 0x06 → Reset</li>
 *   <li>0x00 + 0x07 → Attn</li>
 * </ul>
 *
 * @author Daniel Mengel
 */
public final class KeyEncoder3270 {

    public static final int ACTION_TAB_FORWARD = 0x01;
    public static final int ACTION_TAB_BACKWARD = 0x02;
    public static final int ACTION_HOME = 0x03;
    public static final int ACTION_DELETE = 0x04;
    public static final int ACTION_ERASE = 0x05;
    public static final int ACTION_RESET = 0x06;
    public static final int ACTION_ATTN = 0x07;

    private static final int SENTINEL = 0x00;

    private KeyEncoder3270() {
    }

    /**
     * Applies 3270-specific key bindings to the given encoder.
     *
     * @param encoder the key encoder to configure
     */
    public static void configureKeys(TerminalKeyEncoder encoder) {
        configureAidKeys(encoder);
        configureNavigationKeys(encoder);
        configureEditingKeys(encoder);
        configureSpecialKeys(encoder);
    }

    private static void configureAidKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_ENTER, SENTINEL, Aid3270.ENTER);

        encoder.putCode(VK_F1, SENTINEL, Aid3270.PF1);
        encoder.putCode(VK_F2, SENTINEL, Aid3270.PF2);
        encoder.putCode(VK_F3, SENTINEL, Aid3270.PF3);
        encoder.putCode(VK_F4, SENTINEL, Aid3270.PF4);
        encoder.putCode(VK_F5, SENTINEL, Aid3270.PF5);
        encoder.putCode(VK_F6, SENTINEL, Aid3270.PF6);
        encoder.putCode(VK_F7, SENTINEL, Aid3270.PF7);
        encoder.putCode(VK_F8, SENTINEL, Aid3270.PF8);
        encoder.putCode(VK_F9, SENTINEL, Aid3270.PF9);
        encoder.putCode(VK_F10, SENTINEL, Aid3270.PF10);
        encoder.putCode(VK_F11, SENTINEL, Aid3270.PF11);
        encoder.putCode(VK_F12, SENTINEL, Aid3270.PF12);

        encoder.putModifiedCode(VK_F1, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF13);
        encoder.putModifiedCode(VK_F2, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF14);
        encoder.putModifiedCode(VK_F3, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF15);
        encoder.putModifiedCode(VK_F4, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF16);
        encoder.putModifiedCode(VK_F5, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF17);
        encoder.putModifiedCode(VK_F6, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF18);
        encoder.putModifiedCode(VK_F7, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF19);
        encoder.putModifiedCode(VK_F8, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF20);
        encoder.putModifiedCode(VK_F9, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF21);
        encoder.putModifiedCode(VK_F10, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF22);
        encoder.putModifiedCode(VK_F11, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF23);
        encoder.putModifiedCode(VK_F12, InputEvent.SHIFT_MASK, SENTINEL, Aid3270.PF24);
    }

    private static void configureNavigationKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_TAB, SENTINEL, ACTION_TAB_FORWARD);
        encoder.putModifiedCode(VK_TAB, InputEvent.SHIFT_MASK, SENTINEL, ACTION_TAB_BACKWARD);
        encoder.putCode(VK_HOME, SENTINEL, ACTION_HOME);
    }

    private static void configureEditingKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_DELETE, SENTINEL, ACTION_DELETE);
        encoder.putCode(VK_BACK_SPACE, SENTINEL, ACTION_ERASE);
    }

    private static void configureSpecialKeys(TerminalKeyEncoder encoder) {
        encoder.putCode(VK_ESCAPE, SENTINEL, ACTION_RESET);
        encoder.putModifiedCode('A', InputEvent.CTRL_MASK, SENTINEL, ACTION_ATTN);
    }
}
