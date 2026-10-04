package com.sithtermfx.core;

import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Daniel Mengel
 */
public class HyperlinkStyle extends TextStyle {

    @NotNull
    private final LinkInfo myLinkInfo;

    @NotNull
    private final TextStyle myCustomStyle;

    @Nullable
    private final TextStyle myPrevTextStyle;

    @NotNull
    private final HighlightMode myHighlightMode;

    @Nullable
    private final TextStyle myOriginalStyle;

    /**
     * Whether {@link #myCustomStyle} is the link's own text style plus an underline (an explicit OSC 8
     * link) rather than a fixed link colour (a link found by a hyperlink filter). Such a link keeps
     * following its text style when SGR attributes change inside it.
     */
    private final boolean myCustomStyleFromText;

    /**
     * An explicit (OSC 8) link over text drawn in {@code prevTextStyle}. The link keeps that style's
     * colours and attributes; hovering adds an underline.
     */
    public HyperlinkStyle(@NotNull TextStyle prevTextStyle, @NotNull LinkInfo hyperlinkInfo) {
        this(false, prevTextStyle.getForeground(), prevTextStyle.getBackground(), underlined(prevTextStyle),
                true, hyperlinkInfo, HighlightMode.HOVER_WITH_CUSTOM_COLOR, prevTextStyle, null);
    }

    public HyperlinkStyle(@Nullable TerminalColor foreground, @Nullable TerminalColor background,
                          @NotNull LinkInfo hyperlinkInfo, @NotNull HighlightMode mode,
                          @Nullable TextStyle prevTextStyle, @Nullable TextStyle originalStyle) {
        this(false, foreground, background, underlined(foreground, background), false, hyperlinkInfo, mode,
                prevTextStyle, originalStyle);
    }

    private HyperlinkStyle(boolean keepColors, @Nullable TerminalColor foreground,
                           @Nullable TerminalColor background, @NotNull TextStyle customStyle,
                           boolean customStyleFromText, @NotNull LinkInfo hyperlinkInfo,
                           @NotNull HighlightMode mode, @Nullable TextStyle prevTextStyle,
                           @Nullable TextStyle originalStyle) {
        super(keepColors ? foreground : null, keepColors ? background : null);
        myCustomStyle = customStyle;
        myCustomStyleFromText = customStyleFromText;
        myLinkInfo = hyperlinkInfo;
        myHighlightMode = mode;
        myPrevTextStyle = prevTextStyle;
        myOriginalStyle = originalStyle;
    }

    private static @NotNull TextStyle underlined(@Nullable TerminalColor foreground,
                                                 @Nullable TerminalColor background) {
        return new TextStyle.Builder()
                .setBackground(background)
                .setForeground(foreground)
                .setOption(Option.UNDERLINED, true)
                .build();
    }

    private static @NotNull TextStyle underlined(@NotNull TextStyle textStyle) {
        return new TextStyle.Builder(textStyle).setOption(Option.UNDERLINED, true).build();
    }

    /**
     * The style of the text beneath the link, as it is drawn when the link is not highlighted: the
     * original style a hyperlink filter found, else the text style an OSC 8 link was started over,
     * else (a filter link that kept no original style) the colours this style carries itself.
     */
    @NotNull
    public TextStyle getUnderlyingStyle() {
        if (myOriginalStyle != null) {
            return myOriginalStyle;
        }
        if (myPrevTextStyle != null) {
            return myPrevTextStyle;
        }
        return new TextStyle.Builder(this).build();
    }

    @Nullable
    public TextStyle getPrevTextStyle() {
        return myPrevTextStyle;
    }

    @Nullable
    public TextStyle getOriginalStyle() {
        return myOriginalStyle;
    }

    @NotNull
    public TextStyle getCustomStyle() {
        return myCustomStyle;
    }

    @NotNull
    public LinkInfo getLinkInfo() {
        return myLinkInfo;
    }

    @NotNull
    public HighlightMode getHighlightMode() {
        return myHighlightMode;
    }

    @NotNull
    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    public enum HighlightMode {

        ALWAYS_WITH_ORIGINAL_COLOR(true),

        ALWAYS_WITH_CUSTOM_COLOR(false),

        NEVER_WITH_ORIGINAL_COLOR(true),

        /**
         * It requires original to resolve if it is required to underline.
         */
        NEVER_WITH_CUSTOM_COLOR(true),

        HOVER_WITH_ORIGINAL_COLOR(true),

        HOVER_WITH_CUSTOM_COLOR(false),

        HOVER_WITH_BOTH_COLORS(true);

        private final boolean originalColorUsed;

        private HighlightMode(boolean originalColorUsed) {
            this.originalColorUsed = originalColorUsed;
        }

        public boolean isOriginalColorUsed() {
            return originalColorUsed;
        }
    }

    /**
     * Changes the text attributes of a link while keeping the link. The builder starts from
     * {@link #getUnderlyingStyle()}, so its colours and options are those of the text beneath the
     * link, and whatever is set on it becomes that text's style: an SGR sequence inside an OSC 8 span
     * changes the link text's colours and attributes, and so do selection or search highlighting.
     */
    public static class Builder extends TextStyle.Builder {

        @NotNull
        private final LinkInfo myLinkInfo;

        @NotNull
        private final TextStyle myHighlightStyle;

        private final boolean myCustomStyleFromText;

        @Nullable
        private final TextStyle myPrevTextStyle;

        @Nullable
        private final TextStyle myOriginalStyle;

        @NotNull
        private final HighlightMode myHighlightMode;

        private Builder(@NotNull HyperlinkStyle style) {
            super(style.getUnderlyingStyle());
            myLinkInfo = style.myLinkInfo;
            myHighlightStyle = style.myCustomStyle;
            myCustomStyleFromText = style.myCustomStyleFromText;
            myPrevTextStyle = style.myPrevTextStyle;
            myOriginalStyle = style.myOriginalStyle;
            myHighlightMode = style.myHighlightMode;
        }

        /**
         * Resets the text attributes to the default (SGR 0) without ending the link: OSC 8 links are
         * independent of SGR, so only a closing OSC 8 ends one.
         */
        @NotNull
        public Builder resetTextAttributes() {
            setForeground(null);
            setBackground(null);
            for (Option option : Option.values()) {
                setOption(option, false);
            }
            return this;
        }

        @NotNull
        @Override
        public HyperlinkStyle build() {
            return build(false);
        }

        /**
         * @param keepColors whether the link style itself carries the built colours (as selection
         *                   painting needs) instead of leaving them to the text beneath the link
         */
        @NotNull
        public HyperlinkStyle build(boolean keepColors) {
            TextStyle text = super.build();
            TerminalColor foreground = myHighlightStyle.getForeground();
            TerminalColor background = myHighlightStyle.getBackground();
            if (keepColors || myCustomStyleFromText) {
                foreground = text.getForeground() != null ? text.getForeground() : foreground;
                background = text.getBackground() != null ? text.getBackground() : background;
            }
            TextStyle customStyle = myCustomStyleFromText ? underlined(text) : underlined(foreground, background);
            // The text beneath the link takes the built style: an OSC 8 link's text style, which is
            // also what closing the link restores, or the original style a filter link keeps.
            TextStyle prevTextStyle = myCustomStyleFromText ? text : myPrevTextStyle;
            TextStyle originalStyle = myOriginalStyle != null ? text : null;
            return new HyperlinkStyle(keepColors, foreground, background, customStyle, myCustomStyleFromText,
                    myLinkInfo, myHighlightMode, prevTextStyle, originalStyle);
        }
    }
}
