package com.sithtermfx.ui.settings;

import org.jetbrains.annotations.NotNull;
import javafx.scene.text.Font;
import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.emulator.ColorPalette;
import com.sithtermfx.core.model.TerminalTypeAheadSettings;

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

    /**
     * Decides whether links react to the pointer while the given modifier keys are held: whether a
     * primary click on a link follows it, and whether hovering a link shows the hand cursor and the
     * hover highlight of {@link #getHyperlinkHighlightingMode()}. A click that does not follow a link
     * acts as on plain text, so a double click selects a word and a triple click a line.
     * <p>
     * The default follows a link on every primary click, as before. An embedder that follows links
     * only on a modifier click, e.g. Cmd+click on macOS and Ctrl+click elsewhere, returns
     * {@code modifiers.isShortcutDown()} (and {@code && !modifiers.isAltDown()} to keep AltGr, which
     * Windows reports as Ctrl+Alt, out of it). The hover state follows the modifier keys as they are
     * pressed and released while the pointer rests over a link.
     *
     * @param modifiers the modifier keys held during the click or the hover
     * @return whether a link under the pointer is followed on a click and highlighted on hover
     */
    default boolean isFollowLinkGesture(@NotNull ModifierKeys modifiers) {
        return true;
    }

    /**
     * How text with the bold attribute is drawn: with a bold font (the default, as before), in the
     * bright variant of a basic ANSI foreground colour (xterm's {@code boldColors}), or both. See
     * {@link BoldTextMode}. The panel reads this on every repaint, so a change takes effect with the
     * next one.
     *
     * @return the bold text mode, never {@code null}
     */
    default @NotNull BoldTextMode getBoldTextMode() {
        return BoldTextMode.BOLD_FONT;
    }

    default boolean enableTextBlinking() {
        return false;
    }

    /**
     * Whether programs may show images with the kitty graphics protocol (for example {@code kitten icat},
     * yazi, ranger, timg, chafa or mpv's {@code --vo=kitty}). When this returns false the terminal ignores those
     * commands and does not answer them, so programs fall back as in a terminal without image support. Read
     * whenever a command arrives.
     *
     * @return true by default
     */
    default boolean isKittyGraphicsEnabled() {
        return true;
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

    /**
     * The most heap, in bytes, the scrollback of one terminal may hold, in addition to the line limit
     * of {@link #getBufferMaxLinesCount()}; 0 or less for no memory limit (the default). Read when a
     * terminal is created; {@link com.sithtermfx.core.model.TerminalTextBuffer#setHistoryMemoryLimit}
     * changes it for a running one.
     */
    default long getBufferMaxMemoryBytes() {
        return -1;
    }

    /**
     * Whether scrollback lines are compressed in the background while the terminal is idle (off by
     * default). Read when a terminal is created;
     * {@link com.sithtermfx.core.model.TerminalTextBuffer#setScrollbackCompressionEnabled} changes it
     * for a running one.
     */
    default boolean isScrollbackCompressionEnabled() {
        return false;
    }

    boolean altSendsEscape();

    boolean ambiguousCharsAreDoubleWidth();

    @NotNull
    TerminalTypeAheadSettings getTypeAheadSettings();

    boolean sendArrowKeysInAlternativeMode();
}
