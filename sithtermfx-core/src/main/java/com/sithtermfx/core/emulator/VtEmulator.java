package com.sithtermfx.core.emulator;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VT-family emulator (VT100 through VT520) that extends {@link SithEmulator}
 * with level-dependent feature gating.
 *
 * @author Daniel Mengel
 */
public class VtEmulator extends SithEmulator {

    private static final Logger logger = LoggerFactory.getLogger(VtEmulator.class);

    private final VtEmulationLevel myLevel;

    public VtEmulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal,
                      @NotNull VtEmulationLevel level) {
        super(dataStream, terminal);
        myLevel = level;
    }

    public @NotNull VtEmulationLevel getLevel() {
        return myLevel;
    }

    @Override
    protected boolean sendDeviceAttributes() {
        byte[] response = getDeviceAttributesResponse();
        if (logger.isDebugEnabled()) {
            logger.debug("Identifying to remote system as " + myLevel.name());
        }
        myTerminal.deviceAttributes(response);
        return true;
    }

    private byte[] getDeviceAttributesResponse() {
        return switch (myLevel) {
            case VT100 -> "\033[?1;2c".getBytes();
            case VT102 -> CharUtils.VT102_RESPONSE;
            case VT220 -> "\033[?62;1;2;6;7;8;9c".getBytes();
            case VT320 -> "\033[?63;1;2;6;7;8;9c".getBytes();
            case VT420 -> "\033[?64;1;2;6;7;8;9;15;18;21c".getBytes();
            case VT520 -> "\033[?65;1;2;6;7;8;9;15;18;21c".getBytes();
        };
    }
}
