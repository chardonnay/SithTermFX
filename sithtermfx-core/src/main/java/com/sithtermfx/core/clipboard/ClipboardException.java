package com.sithtermfx.core.clipboard;

import org.jetbrains.annotations.NotNull;

/**
 * Completes a {@link TerminalClipboardHandler} stage exceptionally with the status the program gets back.
 * Any other exception is answered with {@link ClipboardError#EIO}.
 */
public final class ClipboardException extends Exception {

    private final ClipboardError myError;

    public ClipboardException(@NotNull ClipboardError error) {
        this(error, error.name());
    }

    public ClipboardException(@NotNull ClipboardError error, @NotNull String message) {
        super(message);
        myError = error;
    }

    public @NotNull ClipboardError getError() {
        return myError;
    }
}
