package com.sithtermfx.core.emulator.petscii;

import com.sithtermfx.core.TerminalKeyEncoder;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Configures PC keyboard-to-PETSCII code mappings for the Commodore 64/128.
 * <p>
 * PETSCII uses single-byte codes for all keyboard input, unlike ANSI terminals
 * which use multi-byte escape sequences. Arrow keys, function keys, and editing
 * keys each send a single control byte.
 * <p>
 * The C64 had only four physical function keys (F1, F3, F5, F7); the even-numbered
 * keys (F2, F4, F6, F8) were produced by holding Shift. All eight are mapped to
 * consecutive codes 0x85-0x8C.
 *
 * @author Daniel Mengel
 */
public final class PetsciiKeyEncoder {

    private PetsciiKeyEncoder() {
    }

    /**
     * Applies PETSCII key mappings to the given encoder, replacing any
     * default ANSI/VT key bindings.
     *
     * @param encoder the key encoder to configure
     */
    public static void configureKeys(TerminalKeyEncoder encoder) {
        // Cursor movement (single-byte PETSCII codes)
        encoder.putCode(VK_DOWN, 0x11);
        encoder.putCode(VK_UP, 0x91);
        encoder.putCode(VK_RIGHT, 0x1D);
        encoder.putCode(VK_LEFT, 0x9D);

        // Home and Clear Screen (Shift+Home on C64)
        encoder.putCode(VK_HOME, 0x13);

        // Delete / Insert
        encoder.putCode(VK_BACK_SPACE, 0x14);
        encoder.putCode(VK_DELETE, 0x14);
        encoder.putCode(VK_INSERT, 0x94);

        // Return
        encoder.putCode(VK_ENTER, 0x0D);

        // Function keys F1-F8 → PETSCII 0x85-0x8C
        encoder.putCode(VK_F1, 0x85);
        encoder.putCode(VK_F2, 0x86);
        encoder.putCode(VK_F3, 0x87);
        encoder.putCode(VK_F4, 0x88);
        encoder.putCode(VK_F5, 0x89);
        encoder.putCode(VK_F6, 0x8A);
        encoder.putCode(VK_F7, 0x8B);
        encoder.putCode(VK_F8, 0x8C);
    }
}
