package com.sithtermfx.core;

import com.sithtermfx.core.emulator.EmulationType;
import com.sithtermfx.core.emulator.hp.HpKeyEncoder;
import com.sithtermfx.core.emulator.petscii.PetsciiKeyEncoder;
import com.sithtermfx.core.emulator.tn3270.KeyEncoder3270;
import com.sithtermfx.core.emulator.tn5250.KeyEncoder5250;
import com.sithtermfx.core.emulator.tvi.TviKeyEncoder;
import com.sithtermfx.core.emulator.wyse.WyseKeyEncoder;
import com.sithtermfx.core.util.Ascii;
import com.sithtermfx.core.util.Platform;
import org.jetbrains.annotations.NotNull;

import static com.sithtermfx.core.input.KeyEvent.*;

/**
 * Factory that produces {@link TerminalKeyEncoder} instances configured for each {@link EmulationType}.
 *
 * @author Daniel Mengel
 */
public final class TerminalKeyEncoderFactory {

    private static final int ESC = Ascii.ESC;

    private TerminalKeyEncoderFactory() {
    }

    public static @NotNull TerminalKeyEncoder create(@NotNull EmulationType type, @NotNull Platform platform) {
        TerminalKeyEncoder encoder = new TerminalKeyEncoder(platform);
        switch (type) {
            case VT100 -> configureVt100(encoder);
            case VT102 -> configureVt100(encoder);
            case VT220, VT320, VT420, VT520 -> configureVt220Plus(encoder);
            case SCOANSI -> configureScoAnsi(encoder);
            case SUN_CDE -> configureSunCde(encoder);
            case CTERM -> configureVt220Plus(encoder);
            case TVI910 -> TviKeyEncoder.configureKeys(encoder, 910);
            case TVI920 -> TviKeyEncoder.configureKeys(encoder, 920);
            case TVI925 -> TviKeyEncoder.configureKeys(encoder, 925);
            case HP2392 -> HpKeyEncoder.configureKeys(encoder, "2392");
            case HP700_92 -> HpKeyEncoder.configureKeys(encoder, "700/92");
            case TN3270 -> KeyEncoder3270.configureKeys(encoder);
            case TN5250 -> KeyEncoder5250.configureKeys(encoder);
            case WY50 -> WyseKeyEncoder.configureKeys(encoder, 50);
            case WY60 -> WyseKeyEncoder.configureKeys(encoder, 60);
            case WY160 -> WyseKeyEncoder.configureKeys(encoder, 160);
            case PETSCII -> PetsciiKeyEncoder.configureKeys(encoder);
            default -> { /* XTERM and types intentionally using xterm defaults */ }
        }
        return encoder;
    }

    private static void configureVt100(@NotNull TerminalKeyEncoder encoder) {
        encoder.putCode(VK_F1, ESC, 'O', 'P');
        encoder.putCode(VK_F2, ESC, 'O', 'Q');
        encoder.putCode(VK_F3, ESC, 'O', 'R');
        encoder.putCode(VK_F4, ESC, 'O', 'S');
    }

    private static void configureVt220Plus(@NotNull TerminalKeyEncoder encoder) {
        encoder.putCode(VK_F1, ESC, 'O', 'P');
        encoder.putCode(VK_F2, ESC, 'O', 'Q');
        encoder.putCode(VK_F3, ESC, 'O', 'R');
        encoder.putCode(VK_F4, ESC, 'O', 'S');
        encoder.putCode(VK_F5, ESC, '[', '1', '5', '~');
        encoder.putCode(VK_F6, ESC, '[', '1', '7', '~');
        encoder.putCode(VK_F7, ESC, '[', '1', '8', '~');
        encoder.putCode(VK_F8, ESC, '[', '1', '9', '~');
        encoder.putCode(VK_F9, ESC, '[', '2', '0', '~');
        encoder.putCode(VK_F10, ESC, '[', '2', '1', '~');
        encoder.putCode(VK_F11, ESC, '[', '2', '3', '~');
        encoder.putCode(VK_F12, ESC, '[', '2', '4', '~');
        encoder.putCode(VK_INSERT, ESC, '[', '2', '~');
        encoder.putCode(VK_DELETE, ESC, '[', '3', '~');
        encoder.putCode(VK_PAGE_UP, ESC, '[', '5', '~');
        encoder.putCode(VK_PAGE_DOWN, ESC, '[', '6', '~');
        encoder.putCode(VK_HOME, ESC, '[', '1', '~');
        encoder.putCode(VK_END, ESC, '[', '4', '~');
    }

    private static void configureScoAnsi(@NotNull TerminalKeyEncoder encoder) {
        encoder.putCode(VK_F1, ESC, '[', 'M');
        encoder.putCode(VK_F2, ESC, '[', 'N');
        encoder.putCode(VK_F3, ESC, '[', 'O');
        encoder.putCode(VK_F4, ESC, '[', 'P');
        encoder.putCode(VK_F5, ESC, '[', 'Q');
        encoder.putCode(VK_F6, ESC, '[', 'R');
        encoder.putCode(VK_F7, ESC, '[', 'S');
        encoder.putCode(VK_F8, ESC, '[', 'T');
        encoder.putCode(VK_F9, ESC, '[', 'U');
        encoder.putCode(VK_F10, ESC, '[', 'V');
        encoder.putCode(VK_F11, ESC, '[', 'W');
        encoder.putCode(VK_F12, ESC, '[', 'X');
        encoder.putCode(VK_HOME, ESC, '[', 'H');
        encoder.putCode(VK_END, ESC, '[', 'F');
        encoder.putCode(VK_INSERT, ESC, '[', 'L');
    }

    private static void configureSunCde(@NotNull TerminalKeyEncoder encoder) {
        encoder.putCode(VK_F1, ESC, '[', '2', '2', '4', 'z');
        encoder.putCode(VK_F2, ESC, '[', '2', '2', '5', 'z');
        encoder.putCode(VK_F3, ESC, '[', '2', '2', '6', 'z');
        encoder.putCode(VK_F4, ESC, '[', '2', '2', '7', 'z');
        encoder.putCode(VK_F5, ESC, '[', '2', '2', '8', 'z');
        encoder.putCode(VK_F6, ESC, '[', '2', '2', '9', 'z');
        encoder.putCode(VK_F7, ESC, '[', '2', '3', '0', 'z');
        encoder.putCode(VK_F8, ESC, '[', '2', '3', '1', 'z');
        encoder.putCode(VK_F9, ESC, '[', '2', '3', '2', 'z');
        encoder.putCode(VK_F10, ESC, '[', '2', '3', '3', 'z');
        encoder.putCode(VK_F11, ESC, '[', '2', '3', '4', 'z');
        encoder.putCode(VK_F12, ESC, '[', '2', '3', '5', 'z');
    }
}
