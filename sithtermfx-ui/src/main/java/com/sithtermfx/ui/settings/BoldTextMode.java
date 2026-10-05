package com.sithtermfx.ui.settings;

/**
 * How text with the bold attribute (SGR 1) is drawn; see {@link UserSettingsProvider#getBoldTextMode()}.
 * <p>
 * "Bright colour" is xterm's {@code boldColors} resource, the "draw bold text in bright colours"
 * option of most terminals: bold text whose foreground is one of the eight basic ANSI colours (SGR
 * 30&ndash;37, palette indices 0&ndash;7) is drawn in the matching bright colour (indices 8&ndash;15)
 * of the terminal's palette, custom palettes included. The default foreground, the bright colours,
 * the 256-colour cube and truecolour are left as they are. The colour is changed on the text's own
 * foreground before inverse video, selection, search highlighting or link highlighting are applied,
 * so inverse bold text gets the bright colour as its background, and colours that come from the
 * settings (selection, found pattern, link colour) are never brightened.
 */
public enum BoldTextMode {

    /** Bold text is drawn with a bold font in its own colour. The default, as before 1.2.4. */
    BOLD_FONT(true, false),

    /** Bold text is drawn with the normal font weight, a basic ANSI foreground in its bright colour. */
    BRIGHT_COLOR(false, true),

    /** Bold text is drawn with a bold font, a basic ANSI foreground in its bright colour. */
    BOLD_FONT_AND_BRIGHT_COLOR(true, true);

    private final boolean myBoldFont;

    private final boolean myBrightColor;

    BoldTextMode(boolean boldFont, boolean brightColor) {
        myBoldFont = boldFont;
        myBrightColor = brightColor;
    }

    /** @return whether bold text is drawn with a bold font */
    public boolean usesBoldFont() {
        return myBoldFont;
    }

    /** @return whether bold text in a basic ANSI colour is drawn in the matching bright colour */
    public boolean usesBrightColor() {
        return myBrightColor;
    }
}
