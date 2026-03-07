package com.sithtermfx.core;

import org.jetbrains.annotations.NotNull;

/**
 * Sends a response from the terminal emulator.
 *
 * @author Daniel Mengel
 */
public interface TerminalOutputStream {

    void sendBytes(byte @NotNull [] response, boolean userInput);

    void sendString(@NotNull String string, boolean userInput);
}
