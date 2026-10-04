package com.sithtermfx.core.model.hyperlinks;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.TestSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Links found by a hyperlink filter in an original-colour highlight mode. A line is processed again
 * on every write to it, so every pass must leave the same result: each link cell keeps the colours of
 * its own text and points to the target found in the latest pass.
 */
public class TextProcessingOriginalColorTest {

    private static final String ESC = "\u001B";

    private TestSession mySession;

    /** The target each LinkInfo was made for. */
    private final Map<LinkInfo, String> myTargets = new IdentityHashMap<>();

    @BeforeEach
    public void setUp() {
        mySession = new TestSession(60, 3, HyperlinkStyle.HighlightMode.HOVER_WITH_BOTH_COLORS);
        mySession.getTextProcessing().addHyperlinkFilter(new AllLinksFilter());
    }

    @Test
    public void aLinkArrivingInChunksPointsToTheWholeTarget() throws IOException {
        mySession.process("see link:ab");
        mySession.process("cd");
        mySession.process("ef and more");

        for (int column = 4; column < 4 + "link:abcdef".length(); column++) {
            assertEquals("link:abcdef", targetAt(column), "column " + column);
        }
        assertFalse(styleAt(3) instanceof HyperlinkStyle);
        assertFalse(styleAt(15) instanceof HyperlinkStyle);
    }

    @Test
    public void eachColourRunOfALinkKeepsItsColourAcrossPasses() throws IOException {
        mySession.process(ESC + "[31mlink:a" + ESC + "[32mb" + ESC + "[31mc" + ESC + "[0m");
        // more output on the same line processes it again
        mySession.process(" tail");

        for (int column = 0; column < "link:a".length(); column++) {
            assertEquals(TerminalColor.index(1), underlyingAt(column).getForeground(), "column " + column);
        }
        assertEquals(TerminalColor.index(2), underlyingAt(6).getForeground());
        assertEquals(TerminalColor.index(1), underlyingAt(7).getForeground());
        for (int column = 0; column < "link:abc".length(); column++) {
            assertEquals("link:abc", targetAt(column), "column " + column);
        }
    }

    @Test
    public void severalLinksOnALineKeepTheirOwnColoursAndTargets() throws IOException {
        mySession.process(ESC + "[33mlink:one" + ESC + "[0m " + ESC + "[36mlink:two" + ESC + "[0m");
        mySession.process(" " + ESC + "[35mlink:three" + ESC + "[0m");

        assertEquals(TerminalColor.index(3), underlyingAt(0).getForeground());
        assertEquals("link:one", targetAt(0));
        assertEquals(TerminalColor.index(6), underlyingAt(9).getForeground());
        assertEquals("link:two", targetAt(9));
        assertEquals(TerminalColor.index(5), underlyingAt(18).getForeground());
        assertEquals("link:three", targetAt(18));
    }

    @Test
    public void anOsc8LinkIsNotReplacedByAFilterLink() throws IOException {
        LinkInfo explicit = new LinkInfo(() -> {
        });
        mySession.getTextProcessing().setLinkInfoProvider(uri -> explicit);

        mySession.process(ESC + "]8;;https://example.com" + ESC + "\\link:x" + ESC + "]8;;" + ESC + "\\ tail");

        assertSame(explicit, linkAt(0).getLinkInfo());
        assertSame(explicit, linkAt(5).getLinkInfo());
    }

    @Test
    public void anOsc8LinkIsNotReplacedByAFilterLinkInACustomColourMode() throws IOException {
        mySession = new TestSession(60, 3, HyperlinkStyle.HighlightMode.HOVER_WITH_CUSTOM_COLOR);
        mySession.getTextProcessing().addHyperlinkFilter(new AllLinksFilter());
        LinkInfo explicit = new LinkInfo(() -> {
        });
        mySession.getTextProcessing().setLinkInfoProvider(uri -> explicit);

        // the filter's match covers plain text on both sides of the OSC 8 span
        mySession.process("link:ab" + ESC + "]8;;https://example.com" + ESC + "\\cd" + ESC + "]8;;" + ESC
                + "\\ef tail");

        assertEquals("link:abcdef", targetAt(0));
        assertEquals("link:abcdef", targetAt(6));
        assertSame(explicit, linkAt(7).getLinkInfo());
        assertSame(explicit, linkAt(8).getLinkInfo());
        assertEquals("link:abcdef", targetAt(9));
        assertEquals("link:abcdef", targetAt(10));
        assertFalse(styleAt(11) instanceof HyperlinkStyle);
    }

    private @NotNull TextStyle styleAt(int column) {
        return mySession.getTerminalTextBuffer().getStyleAt(column, 0);
    }

    private @NotNull HyperlinkStyle linkAt(int column) {
        return assertInstanceOf(HyperlinkStyle.class, styleAt(column), "column " + column);
    }

    private @Nullable String targetAt(int column) {
        return myTargets.get(linkAt(column).getLinkInfo());
    }

    private @NotNull TextStyle underlyingAt(int column) {
        return linkAt(column).getUnderlyingStyle();
    }

    /** Every {@code link:<letters>} on the line, each with a fresh LinkInfo per pass. */
    private final class AllLinksFilter implements HyperlinkFilter {

        private final Pattern myPattern = Pattern.compile("link:[a-z]+");

        @Override
        public @Nullable LinkResult apply(@NotNull String line) {
            List<LinkResultItem> items = new ArrayList<>();
            Matcher matcher = myPattern.matcher(line);
            while (matcher.find()) {
                LinkInfo info = new LinkInfo(() -> {
                });
                myTargets.put(info, matcher.group());
                items.add(new LinkResultItem(matcher.start(), matcher.end(), info));
            }
            return items.isEmpty() ? null : new LinkResult(items);
        }
    }
}
