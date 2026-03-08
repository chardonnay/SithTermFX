package com.sithtermfx.core.emulator.tn5250;

import com.sithtermfx.core.TerminalKeyEncoder;
import com.sithtermfx.core.input.InputEvent;
import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Configures 5250-specific key mappings on a {@link TerminalKeyEncoder}.
 * <p>
 * Maps standard PC keys to 5250 AID codes and field-editing functions.
 * The byte sequences used here are internal markers; the {@link Tn5250Emulator}
 * intercepts them and translates into 5250 protocol actions.
 * <pre>
 *   Enter    → AID Enter (0xF1)
 *   F1-F12   → AID F1-F12
 *   F13-F24  → Shift+F1-F12 → AID F13-F24
 *   Tab      → next input field
 *   Shift+Tab→ previous input field
 *   Home     → first input field
 *   PgUp     → Roll Down
 *   PgDn     → Roll Up
 *   Delete   → field delete
 *   Backspace→ field backspace
 *   Ctrl+A   → Attention
 *   Ctrl+S   → System Request
 * </pre>
 *
 * @author Daniel Mengel
 */
public final class KeyEncoder5250 {

    private static final int ESC = Ascii.ESC;

    /**
     * Internal prefix for 5250 AID key sequences: ESC [ 5 <aid> ~
     * These do not overlap with any standard VT sequence.
     */
    private static final int PREFIX_AID = '5';

    private KeyEncoder5250() {
    }

    /**
     * Applies 5250 key bindings to the given encoder.
     *
     * @param encoder the encoder to configure
     */
    public static void configureKeys(@NotNull TerminalKeyEncoder encoder) {
        // Enter → AID Enter
        encoder.putCode(VK_ENTER, ESC, '[', PREFIX_AID, Command5250.AID_ENTER, '~');

        // F1-F12 → AID F1-F12
        encoder.putCode(VK_F1, ESC, '[', PREFIX_AID, Command5250.AID_F1, '~');
        encoder.putCode(VK_F2, ESC, '[', PREFIX_AID, Command5250.AID_F2, '~');
        encoder.putCode(VK_F3, ESC, '[', PREFIX_AID, Command5250.AID_F3, '~');
        encoder.putCode(VK_F4, ESC, '[', PREFIX_AID, Command5250.AID_F4, '~');
        encoder.putCode(VK_F5, ESC, '[', PREFIX_AID, Command5250.AID_F5, '~');
        encoder.putCode(VK_F6, ESC, '[', PREFIX_AID, Command5250.AID_F6, '~');
        encoder.putCode(VK_F7, ESC, '[', PREFIX_AID, Command5250.AID_F7, '~');
        encoder.putCode(VK_F8, ESC, '[', PREFIX_AID, Command5250.AID_F8, '~');
        encoder.putCode(VK_F9, ESC, '[', PREFIX_AID, Command5250.AID_F9, '~');
        encoder.putCode(VK_F10, ESC, '[', PREFIX_AID, Command5250.AID_F10, '~');
        encoder.putCode(VK_F11, ESC, '[', PREFIX_AID, Command5250.AID_F11, '~');
        encoder.putCode(VK_F12, ESC, '[', PREFIX_AID, Command5250.AID_F12, '~');

        // Shift+F1-F12 → AID F13-F24
        encoder.putModifiedCode(VK_F1, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F13, '~');
        encoder.putModifiedCode(VK_F2, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F14, '~');
        encoder.putModifiedCode(VK_F3, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F15, '~');
        encoder.putModifiedCode(VK_F4, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F16, '~');
        encoder.putModifiedCode(VK_F5, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F17, '~');
        encoder.putModifiedCode(VK_F6, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F18, '~');
        encoder.putModifiedCode(VK_F7, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F19, '~');
        encoder.putModifiedCode(VK_F8, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F20, '~');
        encoder.putModifiedCode(VK_F9, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F21, '~');
        encoder.putModifiedCode(VK_F10, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F22, '~');
        encoder.putModifiedCode(VK_F11, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F23, '~');
        encoder.putModifiedCode(VK_F12, InputEvent.SHIFT_MASK, ESC, '[', PREFIX_AID, Command5250.AID_F24, '~');

        // Tab → next field (internal marker 0x09)
        encoder.putCode(VK_TAB, Ascii.HT);

        // Shift+Tab → previous field (backtab)
        encoder.putModifiedCode(VK_TAB, InputEvent.SHIFT_MASK, ESC, '[', 'Z');

        // Page Up → Roll Down (host convention: PgUp = scroll backward = Roll Down)
        encoder.putCode(VK_PAGE_UP, ESC, '[', PREFIX_AID, Command5250.AID_ROLL_DOWN, '~');

        // Page Down → Roll Up (host convention: PgDn = scroll forward = Roll Up)
        encoder.putCode(VK_PAGE_DOWN, ESC, '[', PREFIX_AID, Command5250.AID_ROLL_UP, '~');

        // Home → first input field (internal marker)
        encoder.putCode(VK_HOME, ESC, '[', 'H');

        // Delete
        encoder.putCode(VK_DELETE, ESC, '[', '3', '~');

        // Backspace
        encoder.putCode(VK_BACK_SPACE, Ascii.BS);

        // Arrow keys (for cursor movement within fields)
        encoder.putCode(VK_UP, ESC, '[', 'A');
        encoder.putCode(VK_DOWN, ESC, '[', 'B');
        encoder.putCode(VK_RIGHT, ESC, '[', 'C');
        encoder.putCode(VK_LEFT, ESC, '[', 'D');

        // Ctrl+Enter → Attention
        encoder.putModifiedCode(VK_ENTER, InputEvent.CTRL_MASK,
                ESC, '[', PREFIX_AID, Command5250.AID_ATTN, '~');

        // Ctrl+S → System Request (mapped to 'S' with CTRL)
        // Using 0x13 (Ctrl+S = DC3/XOFF), intercepted by emulator
        encoder.putModifiedCode('S', InputEvent.CTRL_MASK,
                ESC, '[', PREFIX_AID, Command5250.AID_SYSREQ, '~');

        // Field Exit: Ctrl+E as internal marker
        encoder.putModifiedCode('E', InputEvent.CTRL_MASK, ESC, '[', '5', 'x', '~');

        // Field+: Ctrl+P (plus sign for numeric)
        encoder.putModifiedCode('P', InputEvent.CTRL_MASK, ESC, '[', '5', 'p', '~');

        // Dup: Ctrl+D
        encoder.putModifiedCode('D', InputEvent.CTRL_MASK, ESC, '[', '5', 'd', '~');

        // Erase Input: Ctrl+R
        encoder.putModifiedCode('R', InputEvent.CTRL_MASK, ESC, '[', '5', 'e', '~');

        // Help: Ctrl+H is backspace on most systems, so map Shift+F1 to Help as well
        // (already F13 maps to AID_F13; a dedicated Help AID is available separately)
        encoder.putModifiedCode(VK_F1, InputEvent.CTRL_MASK,
                ESC, '[', PREFIX_AID, Command5250.AID_HELP, '~');

        // Print: Ctrl+F6
        encoder.putModifiedCode(VK_F6, InputEvent.CTRL_MASK,
                ESC, '[', PREFIX_AID, Command5250.AID_PRINT, '~');
    }
}
