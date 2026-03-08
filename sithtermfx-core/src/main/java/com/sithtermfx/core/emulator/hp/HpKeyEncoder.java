package com.sithtermfx.core.emulator.hp;

import com.sithtermfx.core.TerminalKeyEncoder;
import com.sithtermfx.core.input.InputEvent;
import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Configures an HP-specific key mapping on a {@link TerminalKeyEncoder}.
 * <p>
 * HP terminals use a unique set of function key (softkey) escape sequences:
 * <pre>
 *   f1  = ESC p     f5  = ESC t
 *   f2  = ESC q     f6  = ESC u
 *   f3  = ESC r     f7  = ESC v
 *   f4  = ESC s     f8  = ESC w
 * </pre>
 * Arrow keys and editing keys are also remapped to the HP convention.
 *
 * @author Daniel Mengel
 */
public final class HpKeyEncoder {

    private static final int ESC = Ascii.ESC;

    private HpKeyEncoder() {
    }

    /**
     * Applies HP key bindings to the given encoder.
     *
     * @param encoder the encoder to configure
     * @param hpModel the HP model identifier (e.g. "2392", "700/92")
     */
    public static void configureKeys(@NotNull TerminalKeyEncoder encoder, @NotNull String hpModel) {
        configureCommonHpKeys(encoder);
        switch (hpModel) {
            case "700/92":
                configure700_92Keys(encoder);
                break;
            default:
                break;
        }
    }

    /**
     * Keys common to all HP terminal models.
     */
    private static void configureCommonHpKeys(@NotNull TerminalKeyEncoder encoder) {
        // Softkeys f1-f8: ESC p … ESC w
        encoder.putCode(VK_F1, ESC, 'p');
        encoder.putCode(VK_F2, ESC, 'q');
        encoder.putCode(VK_F3, ESC, 'r');
        encoder.putCode(VK_F4, ESC, 's');
        encoder.putCode(VK_F5, ESC, 't');
        encoder.putCode(VK_F6, ESC, 'u');
        encoder.putCode(VK_F7, ESC, 'v');
        encoder.putCode(VK_F8, ESC, 'w');

        // F9-F12 are not standard on classic HP, but map them for convenience
        encoder.putCode(VK_F9, ESC, '[', '2', '0', '~');
        encoder.putCode(VK_F10, ESC, '[', '2', '1', '~');
        encoder.putCode(VK_F11, ESC, '[', '2', '3', '~');
        encoder.putCode(VK_F12, ESC, '[', '2', '4', '~');

        // Arrow keys – HP convention
        encoder.putCode(VK_UP, ESC, 'A');
        encoder.putCode(VK_DOWN, ESC, 'B');
        encoder.putCode(VK_RIGHT, ESC, 'C');
        encoder.putCode(VK_LEFT, ESC, 'D');

        // Home / end
        encoder.putCode(VK_HOME, ESC, 'h');
        encoder.putCode(VK_END, ESC, 'F');

        // Insert / delete char
        encoder.putCode(VK_INSERT, ESC, 'Q');
        encoder.putCode(VK_DELETE, ESC, 'P');

        // Page up / down – HP roll up / roll down
        encoder.putCode(VK_PAGE_UP, ESC, 'S');
        encoder.putCode(VK_PAGE_DOWN, ESC, 'T');

        // Backspace
        encoder.putCode(VK_BACK_SPACE, Ascii.BS);

        // Tab and backtab
        encoder.putCode(VK_TAB, Ascii.HT);

        // Enter sends CR
        encoder.putCode(VK_ENTER, Ascii.CR);
    }

    /**
     * Additional / overridden keys specific to the HP 700/92 which supports
     * ANSI-compatible cursor sequences alongside HP-native ones.
     */
    private static void configure700_92Keys(@NotNull TerminalKeyEncoder encoder) {
        // Shift+F1-F8 send extended softkey sequences (ESC + upper-case)
        encoder.putModifiedCode(VK_F1, InputEvent.SHIFT_MASK, ESC, 'P');
        encoder.putModifiedCode(VK_F2, InputEvent.SHIFT_MASK, ESC, 'Q');
        encoder.putModifiedCode(VK_F3, InputEvent.SHIFT_MASK, ESC, 'R');
        encoder.putModifiedCode(VK_F4, InputEvent.SHIFT_MASK, ESC, 'S');
        encoder.putModifiedCode(VK_F5, InputEvent.SHIFT_MASK, ESC, 'T');
        encoder.putModifiedCode(VK_F6, InputEvent.SHIFT_MASK, ESC, 'U');
        encoder.putModifiedCode(VK_F7, InputEvent.SHIFT_MASK, ESC, 'V');
        encoder.putModifiedCode(VK_F8, InputEvent.SHIFT_MASK, ESC, 'W');
    }
}
