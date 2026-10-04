package com.sithtermfx.ui;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertSame;

/** A link that is not highlighted is drawn in the style of the text beneath it. */
public class TerminalPanelLinkStyleTest {

    private final LinkInfo myLinkInfo = new LinkInfo(() -> {
    });

    @Test
    public void anOsc8LinkIsDrawnInItsOwnTextStyle() {
        TextStyle text = new TextStyle(TerminalColor.index(4), null, EnumSet.of(TextStyle.Option.BOLD));
        HyperlinkStyle link = new HyperlinkStyle(text, myLinkInfo);

        assertSame(text, TerminalPanel.provideOriginalStyle(link));
    }

    @Test
    public void aFilterLinkIsDrawnInTheOriginalStyleItKept() {
        TextStyle original = new TextStyle(TerminalColor.index(2), null);
        HyperlinkStyle link = new HyperlinkStyle(TerminalColor.index(4), TerminalColor.WHITE, myLinkInfo,
                HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS, null, original);

        assertSame(original, TerminalPanel.provideOriginalStyle(link));
    }

    @Test
    public void aFilterLinkWithoutAnOriginalStyleIsDrawnInItsOwnColours() {
        HyperlinkStyle link = new HyperlinkStyle(TerminalColor.index(4), TerminalColor.WHITE, myLinkInfo,
                HyperlinkStyle.HighlightMode.ALWAYS_WITH_CUSTOM_COLOR, null, null);

        assertSame(link, TerminalPanel.provideOriginalStyle(link));
    }
}
