package com.sithtermfx.ui;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import com.sithtermfx.ui.settings.BoldTextMode;
import com.sithtermfx.ui.settings.DefaultSettingsProvider;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bold text in one of the eight basic ANSI colours is drawn in the matching bright colour. */
public class TerminalPanelBoldColorTest {

    private final LinkInfo myLinkInfo = new LinkInfo(() -> {
    });

    private static TextStyle bold(TerminalColor foreground, TextStyle.Option... more) {
        EnumSet<TextStyle.Option> options = EnumSet.of(TextStyle.Option.BOLD);
        options.addAll(java.util.List.of(more));
        return new TextStyle(foreground, TerminalColor.index(4), options);
    }

    @Test
    public void theDefaultKeepsTheBoldFontAndTheColour() {
        assertSame(BoldTextMode.BOLD_FONT, new DefaultSettingsProvider().getBoldTextMode());
        assertTrue(BoldTextMode.BOLD_FONT.usesBoldFont());
        assertFalse(BoldTextMode.BOLD_FONT.usesBrightColor());
        assertFalse(BoldTextMode.BRIGHT_COLOR.usesBoldFont());
        assertTrue(BoldTextMode.BRIGHT_COLOR.usesBrightColor());
        assertTrue(BoldTextMode.BOLD_FONT_AND_BRIGHT_COLOR.usesBoldFont());
        assertTrue(BoldTextMode.BOLD_FONT_AND_BRIGHT_COLOR.usesBrightColor());
    }

    @Test
    public void everyBasicColourTakesItsBrightVariant() {
        for (int index = 0; index < 8; index++) {
            TextStyle brightened = TerminalPanel.brightenBoldColor(bold(TerminalColor.index(index)));
            assertEquals(index + 8, brightened.getForeground().getColorIndex());
            assertEquals(4, brightened.getBackground().getColorIndex(), "the background is kept");
            assertTrue(brightened.hasOption(TextStyle.Option.BOLD));
        }
    }

    @Test
    public void inverseIsKeptSoTheBrightColourBecomesTheBackground() {
        TextStyle brightened = TerminalPanel.brightenBoldColor(bold(TerminalColor.index(1), TextStyle.Option.INVERSE));
        assertEquals(9, brightened.getForeground().getColorIndex());
        assertTrue(brightened.hasOption(TextStyle.Option.INVERSE));
    }

    @Test
    public void otherColoursAndPlainTextAreLeftAsTheyAre() {
        TextStyle notBold = new TextStyle(TerminalColor.index(1), null);
        assertSame(notBold, TerminalPanel.brightenBoldColor(notBold));
        TextStyle alreadyBright = bold(TerminalColor.index(9));
        assertSame(alreadyBright, TerminalPanel.brightenBoldColor(alreadyBright));
        TextStyle cube = bold(TerminalColor.index(100));
        assertSame(cube, TerminalPanel.brightenBoldColor(cube));
        TextStyle trueColour = bold(TerminalColor.rgb(200, 0, 0));
        assertSame(trueColour, TerminalPanel.brightenBoldColor(trueColour));
        TextStyle defaultForeground = bold(null);
        assertSame(defaultForeground, TerminalPanel.brightenBoldColor(defaultForeground));
    }

    @Test
    public void anOsc8LinkKeepsTheLinkAndDrawsItsTextBright() {
        HyperlinkStyle link = new HyperlinkStyle(bold(TerminalColor.index(4)), myLinkInfo);

        HyperlinkStyle brightened = assertInstanceOf(HyperlinkStyle.class, TerminalPanel.brightenBoldColor(link));

        assertSame(myLinkInfo, brightened.getLinkInfo());
        TextStyle text = TerminalPanel.provideOriginalStyle(brightened);
        assertEquals(12, text.getForeground().getColorIndex());
        assertTrue(text.hasOption(TextStyle.Option.BOLD));
        assertEquals(12, brightened.getCustomStyle().getForeground().getColorIndex());
        assertTrue(brightened.getCustomStyle().hasOption(TextStyle.Option.UNDERLINED));
    }

    @Test
    public void aFilterLinkBrightensItsOriginalStyleButNotTheLinkColour() {
        HyperlinkStyle link = new HyperlinkStyle(TerminalColor.index(4), TerminalColor.WHITE, myLinkInfo,
                HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS, null, bold(TerminalColor.index(2)));

        HyperlinkStyle brightened = assertInstanceOf(HyperlinkStyle.class, TerminalPanel.brightenBoldColor(link));

        assertSame(myLinkInfo, brightened.getLinkInfo());
        assertEquals(10, TerminalPanel.provideOriginalStyle(brightened).getForeground().getColorIndex());
        assertEquals(4, brightened.getCustomStyle().getForeground().getColorIndex());
        assertNull(brightened.getPrevTextStyle());
    }

    @Test
    public void aLinkOverPlainTextIsLeftAsItIs() {
        HyperlinkStyle link = new HyperlinkStyle(new TextStyle(TerminalColor.index(4), null), myLinkInfo);
        assertSame(link, TerminalPanel.brightenBoldColor(link));
    }
}
