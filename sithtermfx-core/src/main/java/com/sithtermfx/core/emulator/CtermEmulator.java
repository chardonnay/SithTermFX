package com.sithtermfx.core.emulator;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * DEC CTERM terminal emulator. VT320-compatible with CTERM-specific
 * host-initiated operations (read/write ops, out-of-band data).
 *
 * @author Daniel Mengel
 */
public class CtermEmulator extends VtEmulator {

    private static final Logger logger = LoggerFactory.getLogger(CtermEmulator.class);

    public CtermEmulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal, VtEmulationLevel.VT320);
    }

    @Override
    public void processChar(char ch, Terminal terminal) throws IOException {
        super.processChar(ch, terminal);
    }

    @Override
    protected boolean sendDeviceAttributes() {
        if (logger.isDebugEnabled()) {
            logger.debug("Identifying to remote system as CTERM (VT320)");
        }
        myTerminal.deviceAttributes("\033[?63;1;2;6;7;8;9c".getBytes());
        return true;
    }
}
