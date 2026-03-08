package com.sithtermfx.core.emulator;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates all supported terminal emulation types with their metadata.
 *
 * @author Daniel Mengel
 */
public enum EmulationType {

    XTERM("xterm-256color", "XTerm (256 color)", 80, 24, ScreenMode.STREAM, ColorSupport.TRUE_COLOR),

    VT100("vt100", "DEC VT100", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    VT102("vt102", "DEC VT102", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    VT220("vt220", "DEC VT220", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    VT320("vt320", "DEC VT320", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    VT420("vt420", "DEC VT420", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    VT520("vt520", "DEC VT520", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),

    TN3270("IBM-3279-2-E", "IBM TN3270", 80, 24, ScreenMode.BLOCK, ColorSupport.COLOR_16),
    TN5250("IBM-3179-2", "IBM TN5250", 80, 24, ScreenMode.BLOCK, ColorSupport.COLOR_16),

    SUN_CDE("dtterm", "Sun CDE (dtterm)", 80, 24, ScreenMode.STREAM, ColorSupport.COLOR_16),

    WY50("wy50", "Wyse 50", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    WY60("wy60", "Wyse 60", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    WY160("wy160", "Wyse 160", 80, 24, ScreenMode.STREAM, ColorSupport.COLOR_16),

    TVI910("tvi910", "TeleVideo 910", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    TVI920("tvi920", "TeleVideo 920", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    TVI925("tvi925", "TeleVideo 925", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),

    HP2392("hp2392", "HP 2392", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    HP700_92("hp700-92", "HP 700/92", 80, 24, ScreenMode.STREAM, ColorSupport.COLOR_16),

    SCOANSI("scoansi", "SCO ANSI", 80, 25, ScreenMode.STREAM, ColorSupport.COLOR_16),
    CTERM("cterm", "DEC CTERM", 80, 24, ScreenMode.STREAM, ColorSupport.MONO),
    PETSCII("petscii", "Commodore PETSCII", 40, 25, ScreenMode.STREAM, ColorSupport.COLOR_16);

    public enum ScreenMode {
        STREAM,
        BLOCK
    }

    public enum ColorSupport {
        MONO,
        COLOR_16,
        COLOR_256,
        TRUE_COLOR
    }

    private final String termName;
    private final String displayName;
    private final int defaultColumns;
    private final int defaultRows;
    private final ScreenMode screenMode;
    private final ColorSupport colorSupport;

    EmulationType(@NotNull String termName, @NotNull String displayName,
                  int defaultColumns, int defaultRows,
                  @NotNull ScreenMode screenMode, @NotNull ColorSupport colorSupport) {
        this.termName = termName;
        this.displayName = displayName;
        this.defaultColumns = defaultColumns;
        this.defaultRows = defaultRows;
        this.screenMode = screenMode;
        this.colorSupport = colorSupport;
    }

    public @NotNull String getTermName() {
        return termName;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }

    public int getDefaultColumns() {
        return defaultColumns;
    }

    public int getDefaultRows() {
        return defaultRows;
    }

    public @NotNull ScreenMode getScreenMode() {
        return screenMode;
    }

    public @NotNull ColorSupport getColorSupport() {
        return colorSupport;
    }

    public boolean isBlockMode() {
        return screenMode == ScreenMode.BLOCK;
    }

    public boolean isVtFamily() {
        return this == VT100 || this == VT102 || this == VT220
                || this == VT320 || this == VT420 || this == VT520 || this == XTERM;
    }
}
