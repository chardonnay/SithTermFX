package com.sithtermfx.core.emulator;

import org.jetbrains.annotations.NotNull;

/**
 * Declares capabilities for a specific terminal emulation type.
 *
 * @author Daniel Mengel
 */
public interface TerminalCapabilities {

    @NotNull EmulationType getEmulationType();

    int getMaxColors();

    boolean supportsDoubleWidthChars();

    boolean supports8BitControls();

    boolean supportsUserDefinedKeys();

    boolean supportsRectangularAreaOps();

    boolean supportsLeftRightMargins();

    boolean supportsStatusLine();

    int getFunctionKeyCount();

    byte @NotNull [] getDeviceAttributesResponse();

    @NotNull String getTermName();
}
