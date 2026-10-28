package com.sithtermfx.core;

import com.sithtermfx.core.util.PixelSize;
import com.sithtermfx.core.util.TermSize;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface TtyConnector {

    int read(char[] buf, int offset, int length) throws IOException;

    void write(byte[] bytes) throws IOException;

    void write(String string) throws IOException;

    boolean isConnected();

    default void resize(@NotNull TermSize termSize) {
        // support old implementations not overriding this method
        resize(new java.awt.Dimension(termSize.getColumns(), termSize.getRows()));
    }

    /**
     * Resizes the terminal and tells the size of its text area in pixels, which programs that draw images read
     * (the {@code ws_xpixel} and {@code ws_ypixel} of {@code TIOCGWINSZ}, or the pixel size of an SSH window
     * change). The terminal calls this instead of {@link #resize(TermSize)} when it knows its cell size; a
     * connector that can pass the pixel size on overrides it, the default ignores the pixel size.
     */
    default void resize(@NotNull TermSize termSize, @NotNull PixelSize pixelSize) {
        resize(termSize);
    }

    int waitFor() throws InterruptedException;

    boolean ready() throws IOException;

    String getName();

    void close();

    /**
     * @deprecated use {@link #resize(TermSize)} instead
     */
    @Deprecated
    default void resize(@NotNull java.awt.Dimension termWinSize) {
        // support old implementations overriding neither `resize(Dimension)` nor this method
        resize(termWinSize, new java.awt.Dimension(0, 0));
    }

    /**
     * @deprecated use {@link #resize(TermSize)} instead
     */
    @SuppressWarnings("unused")
    @Deprecated
    default void resize(java.awt.Dimension termWinSize, java.awt.Dimension pixelSize) {
        throw new IllegalStateException("This method shouldn't be called. " +
                getClass() + " should override TtyConnector.resize(com.jediterm.core.util.TermSize)");
    }

    /**
     * @deprecated Collect extra information when creating {@link TtyConnector}
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    default boolean init(Questioner q) {
        return true;
    }
}
