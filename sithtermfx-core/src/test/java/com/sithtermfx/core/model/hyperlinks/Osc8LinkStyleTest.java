package com.sithtermfx.core.model.hyperlinks;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.TextStyle.Option;
import com.sithtermfx.core.util.TestSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * OSC 8 links are independent of SGR: the text inside a link keeps its own colours and attributes,
 * SGR changes inside the link apply to that text, SGR 0 does not end the link, and closing the link
 * leaves the SGR state as it is.
 */
public class Osc8LinkStyleTest {

    private static final String ESC = "\u001B";
    private static final String ST = ESC + "\\";

    private TestSession mySession;

    private LinkInfo myLinkInfo;

    @BeforeEach
    public void setUp() {
        mySession = new TestSession(40, 3);
        myLinkInfo = new LinkInfo(() -> {
        });
        mySession.getTextProcessing().setLinkInfoProvider(uri -> myLinkInfo);
    }

    @Test
    public void aLinkStartedOverStyledTextKeepsItsColoursAndAttributes() throws IOException {
        mySession.process(ESC + "[1;34m" + open("https://example.com") + "dir" + close());

        HyperlinkStyle link = linkAt(0);
        assertStyle(link.getUnderlyingStyle(), TerminalColor.index(4), null, Option.BOLD);
        assertStyle(link.getCustomStyle(), TerminalColor.index(4), null, Option.BOLD, Option.UNDERLINED);
        assertSame(myLinkInfo, link.getLinkInfo());
    }

    @Test
    public void sgrInsideTheLinkChangesTheLinkText() throws IOException {
        // ls --hyperlink --color: the colour is set after the link has been opened
        mySession.process(open("file:///tmp") + ESC + "[01;31m" + "red" + ESC + "[0m" + "plain" + close() + "after");

        HyperlinkStyle red = linkAt(0);
        assertStyle(red.getUnderlyingStyle(), TerminalColor.index(1), null, Option.BOLD);
        assertStyle(red.getCustomStyle(), TerminalColor.index(1), null, Option.BOLD, Option.UNDERLINED);
        assertSame(myLinkInfo, red.getLinkInfo());

        // SGR 0 resets the attributes, not the link
        HyperlinkStyle plain = linkAt(3);
        assertStyle(plain.getUnderlyingStyle(), null, null);
        assertSame(myLinkInfo, plain.getLinkInfo());

        TextStyle after = styleAt(8);
        assertFalse(after instanceof HyperlinkStyle);
        assertEquals(new TextStyle(), after);
    }

    @Test
    public void closingTheLinkKeepsTheAttributesSetInsideIt() throws IOException {
        mySession.process(open("https://example.com") + ESC + "[32m" + "a" + close() + "b");

        assertStyle(linkAt(0).getUnderlyingStyle(), TerminalColor.index(2), null);
        TextStyle after = styleAt(1);
        assertFalse(after instanceof HyperlinkStyle);
        assertStyle(after, TerminalColor.index(2), null);
    }

    @Test
    public void anEmptySgrInsideTheLinkKeepsTheLink() throws IOException {
        mySession.process(ESC + "[1m" + open("https://example.com") + ESC + "[m" + "x" + close());

        HyperlinkStyle link = linkAt(0);
        assertStyle(link.getUnderlyingStyle(), null, null);
        assertSame(myLinkInfo, link.getLinkInfo());
    }

    @Test
    public void builderCarriesColoursAndOptionsOfAnOsc8Link() {
        TextStyle text = new TextStyle(TerminalColor.index(3), null, EnumSet.of(Option.ITALIC));
        HyperlinkStyle link = new HyperlinkStyle(text, myLinkInfo);

        HyperlinkStyle.Builder builder = link.toBuilder();
        builder.setBackground(TerminalColor.index(5));
        builder.setOption(Option.INVERSE, true);
        HyperlinkStyle rebuilt = builder.build();

        assertSame(myLinkInfo, rebuilt.getLinkInfo());
        assertStyle(rebuilt.getUnderlyingStyle(), TerminalColor.index(3), TerminalColor.index(5),
                Option.ITALIC, Option.INVERSE);
        assertStyle(rebuilt.getCustomStyle(), TerminalColor.index(3), TerminalColor.index(5),
                Option.ITALIC, Option.INVERSE, Option.UNDERLINED);
        assertEquals(HyperlinkStyle.HighlightMode.HOVER_WITH_CUSTOM_COLOR, rebuilt.getHighlightMode());
    }

    @Test
    public void builderKeepsTheLinkColourOfAFilterLink() {
        TextStyle original = new TextStyle(TerminalColor.index(2), null, EnumSet.of(Option.BOLD));
        HyperlinkStyle link = new HyperlinkStyle(TerminalColor.index(4), TerminalColor.WHITE, myLinkInfo,
                HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS, null, original);

        // selection painting: the selection colours go to the link style itself and its text
        HyperlinkStyle.Builder builder = link.toBuilder();
        builder.setForeground(TerminalColor.WHITE);
        builder.setBackground(TerminalColor.BLACK);
        HyperlinkStyle selected = builder.build(true);
        assertEquals(TerminalColor.WHITE, selected.getForeground());
        assertEquals(TerminalColor.BLACK, selected.getBackground());
        assertStyle(selected.getUnderlyingStyle(), TerminalColor.WHITE, TerminalColor.BLACK, Option.BOLD);
        assertSame(myLinkInfo, selected.getLinkInfo());

        // without keepColors the custom link colour stays
        HyperlinkStyle rebuilt = link.toBuilder().build();
        assertNull(rebuilt.getForeground());
        assertStyle(rebuilt.getCustomStyle(), TerminalColor.index(4), TerminalColor.WHITE, Option.UNDERLINED);
        assertStyle(rebuilt.getUnderlyingStyle(), TerminalColor.index(2), null, Option.BOLD);
    }

    private static @NotNull String open(@NotNull String uri) {
        return ESC + "]8;;" + uri + ST;
    }

    private static @NotNull String close() {
        return ESC + "]8;;" + ST;
    }

    private @NotNull TextStyle styleAt(int column) {
        return mySession.getTerminalTextBuffer().getStyleAt(column, 0);
    }

    private @NotNull HyperlinkStyle linkAt(int column) {
        return assertInstanceOf(HyperlinkStyle.class, styleAt(column));
    }

    private static void assertStyle(@NotNull TextStyle style, TerminalColor foreground, TerminalColor background,
                                    Option... options) {
        assertEquals(foreground, style.getForeground(), "foreground");
        assertEquals(background, style.getBackground(), "background");
        for (Option option : Option.values()) {
            assertEquals(Arrays.asList(options).contains(option), style.hasOption(option), option.name());
        }
    }
}
