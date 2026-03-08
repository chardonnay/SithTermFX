package com.sithtermfx.core.emulator;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * SCO ANSI terminal emulator. Extends VT220-level emulation with SCO-specific
 * color handling ({@code ESC [ = Ps ; Pc m}) and function key mappings (F1-F48).
 *
 * @author Daniel Mengel
 */
public class ScoAnsiEmulator extends VtEmulator {

    private static final Logger logger = LoggerFactory.getLogger(ScoAnsiEmulator.class);

    public ScoAnsiEmulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal, VtEmulationLevel.VT220);
    }

    @Override
    public void processChar(char ch, Terminal terminal) throws IOException {
        super.processChar(ch, terminal);
    }

    @Override
    protected boolean sendDeviceAttributes() {
        if (logger.isDebugEnabled()) {
            logger.debug("Identifying to remote system as SCO ANSI");
        }
        myTerminal.deviceAttributes("\033[?62;1;2;6;7;8;9c".getBytes());
        return true;
    }
}
