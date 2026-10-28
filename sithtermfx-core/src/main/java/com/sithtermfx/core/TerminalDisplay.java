package com.sithtermfx.core;

import com.sithtermfx.core.util.PixelSize;
import com.sithtermfx.core.util.TermSize;
import com.sithtermfx.core.emulator.mouse.MouseFormat;
import com.sithtermfx.core.emulator.mouse.MouseMode;
import com.sithtermfx.core.model.TerminalSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TerminalDisplay {

    void setCursor(int x, int y);

    /**
     * Sets cursor shape, null means default.
     */
    void setCursorShape(@Nullable CursorShape cursorShape);

    void beep();

    default void onResize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin) {
    }

    void scrollArea(final int scrollRegionTop, final int scrollRegionSize, int dy);

    void setCursorVisible(boolean isCursorVisible);

    void useAlternateScreenBuffer(boolean useAlternateScreenBuffer);

    String getWindowTitle();

    void setWindowTitle(@NotNull String windowTitle);

    @Nullable
    TerminalSelection getSelection();

    void terminalMouseModeSet(@NotNull MouseMode mouseMode);

    void setMouseFormat(@NotNull MouseFormat mouseFormat);

    boolean ambiguousCharsAreDoubleWidth();

    default void setBracketedPasteMode(boolean bracketedPasteModeEnabled) {
    }

    default @Nullable Color getWindowForeground() {
        return null;
    }

    default @Nullable Color getWindowBackground() {
        return null;
    }

    /**
     * @return the size of one character cell in pixels, or null for a display that does not know it. Programs
     * that draw images read it, from the window size or by asking ({@code CSI 14 t}, {@code CSI 16 t}).
     */
    default @Nullable PixelSize getCellPixelSize() {
        return null;
    }

    /**
     * @return whether the display draws the images programs send with the kitty graphics protocol. While it
     * does not, the terminal ignores those commands and does not answer them, so programs see no support.
     */
    default boolean isKittyGraphicsEnabled() {
        return false;
    }
}
