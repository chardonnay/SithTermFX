package com.techsenger.jeditermfx.ui.settings;

import org.jetbrains.annotations.NotNull;
import javafx.scene.text.Font;
import com.techsenger.jeditermfx.core.HyperlinkStyle;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.emulator.ColorPalette;
import com.techsenger.jeditermfx.core.model.TerminalTypeAheadSettings;

public interface UserSettingsProvider {

    ColorPalette getTerminalColorPalette();

    Font getTerminalFont();

    float getTerminalFontSize();

    /**
     * @return minimum font size in points (for zoom)
     */
    default float getMinFontSize() {
        return 8;
    }

    /**
     * @return maximum font size in points (for zoom)
     */
    default float getMaxFontSize() {
        return 72;
    }

    /**
     * Whether this provider supports dynamic font size changes at runtime (e.g. via Ctrl+Plus/Minus).
     * Used to show font size menu items in the context menu.
     */
    default boolean supportsDynamicFontSize() {
        return this instanceof MutableFontSizeProvider;
    }

    /**
     * @return vertical scaling factor
     */
    default float getLineSpacing() {
        return 1.0f;
    }

    default boolean shouldDisableLineSpacingForAlternateScreenBuffer() {
        return false;
    }

    default boolean shouldFillCharacterBackgroundIncludingLineSpacing() {
        return true;
    }

    default @NotNull TerminalColor getDefaultForeground() {
        return TerminalColor.BLACK;
    }

    default @NotNull TerminalColor getDefaultBackground() {
        return TerminalColor.WHITE;
    }

    @NotNull
    TextStyle getSelectionColor();

    @NotNull
    TextStyle getFoundPatternColor();

    TextStyle getHyperlinkColor();

    HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode();

    default boolean enableTextBlinking() {
        return false;
    }

    default int slowTextBlinkMs() {
        return 1000;
    }

    default int rapidTextBlinkMs() {
        return 500;
    }

    boolean useInverseSelectionColor();

    boolean copyOnSelect();

    boolean pasteOnMiddleMouseClick();

    boolean emulateX11CopyPaste();

    boolean useAntialiasing();

    int maxRefreshRate();

    boolean audibleBell();

    boolean enableMouseReporting();

    int caretBlinkingMs();

    boolean scrollToBottomOnTyping();

    boolean DECCompatibilityMode();

    boolean forceActionOnMouseReporting();

    int getBufferMaxLinesCount();

    boolean altSendsEscape();

    boolean ambiguousCharsAreDoubleWidth();

    @NotNull
    TerminalTypeAheadSettings getTypeAheadSettings();

    boolean sendArrowKeysInAlternativeMode();
}
