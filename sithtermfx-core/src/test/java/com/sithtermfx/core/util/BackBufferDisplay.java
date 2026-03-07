package com.sithtermfx.core.util;

import com.sithtermfx.core.Color;
import com.sithtermfx.core.CursorShape;
import com.sithtermfx.core.TerminalDisplay;
import com.sithtermfx.core.emulator.mouse.MouseFormat;
import com.sithtermfx.core.emulator.mouse.MouseMode;
import com.sithtermfx.core.model.TerminalSelection;
import com.sithtermfx.core.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Daniel Mengel
 */
public class BackBufferDisplay implements TerminalDisplay {

    private final TerminalTextBuffer myTerminalTextBuffer;

    private TerminalSelection mySelection = null;

    private String myWindowTitle;

    private @Nullable Color myForegroundColor;

    private @Nullable Color myBackgroundColor;

    public BackBufferDisplay(TerminalTextBuffer terminalTextBuffer) {
        myTerminalTextBuffer = terminalTextBuffer;
    }

    @Override
    public void setCursor(int x, int y) {
    }

    @Override
    public void setCursorShape(@Nullable CursorShape cursorShape) {
    }

    @Override
    public void beep() {
    }

    @Override
    public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
    }

    @Override
    public void setCursorVisible(boolean isCursorVisible) {
    }

    @Override
    public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
    }

    @Override
    public void setWindowTitle(@NotNull String windowTitle) {
        myWindowTitle = windowTitle;
    }

    public @Nullable String getWindowTitle() {
        return myWindowTitle;
    }

    @Override
    public @Nullable TerminalSelection getSelection() {
        return mySelection;
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }

    public void setSelection(TerminalSelection mySelection) {
        this.mySelection = mySelection;
    }

    @Override
    public void terminalMouseModeSet(@NotNull MouseMode mouseMode) {
    }

    @Override
    public void setMouseFormat(@NotNull MouseFormat mouseFormat) {
    }

    @Override
    public @Nullable Color getWindowForeground() {
        return myForegroundColor;
    }

    public void setWindowForeground(@Nullable Color foregroundColor) {
        myForegroundColor = foregroundColor;
    }

    @Override
    public @Nullable Color getWindowBackground() {
        return myBackgroundColor;
    }

    public void setWindowBackground(@Nullable Color backgroundColor) {
        myBackgroundColor = backgroundColor;
    }
}
