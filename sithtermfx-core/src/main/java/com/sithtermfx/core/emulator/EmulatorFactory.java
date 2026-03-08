package com.sithtermfx.core.emulator;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import org.jetbrains.annotations.NotNull;

/**
 * Factory that creates the appropriate {@link Emulator} for a given {@link EmulationType}.
 *
 * @author Daniel Mengel
 */
public final class EmulatorFactory {

    private EmulatorFactory() {
    }

    public static @NotNull Emulator createEmulator(@NotNull EmulationType type,
                                                    @NotNull TerminalDataStream dataStream,
                                                    @NotNull Terminal terminal) {
        return switch (type) {
            case VT100, VT102, VT220, VT320, VT420, VT520 ->
                    new VtEmulator(dataStream, terminal, VtEmulationLevel.fromEmulationType(type));
            case XTERM -> new SithEmulator(dataStream, terminal);
            case SCOANSI -> new ScoAnsiEmulator(dataStream, terminal);
            case SUN_CDE -> new SunCdeEmulator(dataStream, terminal);
            case CTERM -> new CtermEmulator(dataStream, terminal);
            case WY50 -> new com.sithtermfx.core.emulator.wyse.Wy50Emulator(dataStream, terminal);
            case WY60 -> new com.sithtermfx.core.emulator.wyse.Wy60Emulator(dataStream, terminal);
            case WY160 -> new com.sithtermfx.core.emulator.wyse.Wy160Emulator(dataStream, terminal);
            case TVI910 -> new com.sithtermfx.core.emulator.tvi.Tvi910Emulator(dataStream, terminal);
            case TVI920 -> new com.sithtermfx.core.emulator.tvi.Tvi920Emulator(dataStream, terminal);
            case TVI925 -> new com.sithtermfx.core.emulator.tvi.Tvi925Emulator(dataStream, terminal);
            case HP2392 -> new com.sithtermfx.core.emulator.hp.Hp2392Emulator(dataStream, terminal);
            case HP700_92 -> new com.sithtermfx.core.emulator.hp.Hp700_92Emulator(dataStream, terminal);
            case TN3270 -> new com.sithtermfx.core.emulator.tn3270.Tn3270Emulator(dataStream, terminal);
            case TN5250 -> new com.sithtermfx.core.emulator.tn5250.Tn5250Emulator(dataStream, terminal);
            case PETSCII -> new com.sithtermfx.core.emulator.petscii.PetsciiEmulator(dataStream, terminal);
        };
    }
}
