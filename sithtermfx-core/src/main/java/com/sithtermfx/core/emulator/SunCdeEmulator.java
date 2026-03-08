package com.sithtermfx.core.emulator;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Sun CDE (dtterm) terminal emulator. Largely VT220-compatible with Sun-specific
 * function key sequences and dtterm-compatible OSC extensions.
 *
 * @author Daniel Mengel
 */
public class SunCdeEmulator extends VtEmulator {

    private static final Logger logger = LoggerFactory.getLogger(SunCdeEmulator.class);

    public SunCdeEmulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal, VtEmulationLevel.VT220);
    }

    @Override
    public void processChar(char ch, Terminal terminal) throws IOException {
        super.processChar(ch, terminal);
    }

    @Override
    protected boolean sendDeviceAttributes() {
        if (logger.isDebugEnabled()) {
            logger.debug("Identifying to remote system as Sun CDE (dtterm)");
        }
        myTerminal.deviceAttributes("\033[?62;1;2;6;7;8;9c".getBytes());
        return true;
    }
}
