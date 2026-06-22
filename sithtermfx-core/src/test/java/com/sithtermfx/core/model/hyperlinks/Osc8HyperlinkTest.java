package com.sithtermfx.core.model.hyperlinks;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.model.CharBuffer;
import com.sithtermfx.core.model.TerminalLine;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.TestSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Exercises the OSC 8 explicit-hyperlink path ({@code SithTerminal.setLinkUriStarted}), which is
 * decoupled from the autolink filters and instead resolves the supplied URI through a
 * {@link LinkInfoProvider}.
 *
 * <p>No autolink {@link HyperlinkFilter} is registered here on purpose: that keeps
 * {@code TextProcessing.processHyperlinks} from re-styling the written text, so the asserted style
 * is exactly the one produced by {@code setLinkUriStarted}. {@link HyperlinkStyle#equals} compares
 * only the outer {@link TextStyle} (class + colors + options), not the {@link LinkInfo}, so the
 * exact target URI is asserted by capturing it in the provider.
 */
public class Osc8HyperlinkTest {

    private static final char ESC = 0x1B;
    private static final char BACKSLASH = 0x5C;
    // String Terminator: ESC \
    private static final String ST = "" + ESC + BACKSLASH;

    private TestSession mySession;
    private TextStyle myPlainStyle;
    private HyperlinkStyle myLinkStyle;

    @BeforeEach
    public void setUp() {
        mySession = new TestSession(100, 5);
        myPlainStyle = mySession.getCurrentStyle();
        myLinkStyle = new HyperlinkStyle(myPlainStyle, new LinkInfo(() -> {
        }));
    }

    private @NotNull TerminalTextBuffer getTextBuffer() {
        return mySession.getTerminalTextBuffer();
    }

    /** Builds an OSC 8 hyperlink: {@code OSC 8 ; <params> ; <uri> ST <text> OSC 8 ; ; ST}. */
    private static @NotNull String osc8(@NotNull String params, @NotNull String uri, @NotNull String text) {
        return "" + ESC + "]8;" + params + ";" + uri + ST + text + ESC + "]8;;" + ST;
    }

    @Test
    public void testFileUriBecomesHyperlink() throws IOException {
        // file:// is the dominant real-world OSC 8 producer (ls --hyperlink) and the case the old
        // autolink-regex-coupled implementation silently dropped.
        String[] captured = {null};
        mySession.getTextProcessing().setLinkInfoProvider(uri -> {
            captured[0] = uri;
            return new LinkInfo(() -> {
            });
        });
        mySession.process(osc8("", "file:///tmp/x", "hello") + " after");
        assertEntries(
                Arrays.asList(
                        new TerminalLine.TextEntry(myLinkStyle, new CharBuffer("hello")),
                        new TerminalLine.TextEntry(myPlainStyle, new CharBuffer(" after"))
                ),
                getTextBuffer().getLine(0).getEntries()
        );
        Assertions.assertEquals("file:///tmp/x", captured[0]);
    }

    @Test
    public void testRejectedUriRendersPlainText() throws IOException {
        // Provider rejects the URI (e.g. a disallowed scheme): the text must remain plain.
        mySession.getTextProcessing().setLinkInfoProvider(uri -> null);
        mySession.process(osc8("", "javascript:alert(1)", "nope"));
        assertEntries(
                Collections.singletonList(new TerminalLine.TextEntry(myPlainStyle, new CharBuffer("nope"))),
                getTextBuffer().getLine(0).getEntries()
        );
    }

    @Test
    public void testBlankUriIsNoop() {
        mySession.getTextProcessing().setLinkInfoProvider(uri -> new LinkInfo(() -> {
        }));
        mySession.getTerminal().setLinkUriStarted("   ");
        Assertions.assertEquals(myPlainStyle, mySession.getCurrentStyle());
    }

    @Test
    public void testLinkFinishedRestoresStyle() throws IOException {
        mySession.getTextProcessing().setLinkInfoProvider(uri -> new LinkInfo(() -> {
        }));
        mySession.process(osc8("", "https://example.com", "inside") + "outside");
        assertEntries(
                Arrays.asList(
                        new TerminalLine.TextEntry(myLinkStyle, new CharBuffer("inside")),
                        new TerminalLine.TextEntry(myPlainStyle, new CharBuffer("outside"))
                ),
                getTextBuffer().getLine(0).getEntries()
        );
    }

    @Test
    public void testNoProviderRegisteredRendersPlainText() throws IOException {
        // No LinkInfoProvider registered: createExplicitLink returns null, link stays plain text.
        mySession.process(osc8("", "https://example.com", "text"));
        assertEntries(
                Collections.singletonList(new TerminalLine.TextEntry(myPlainStyle, new CharBuffer("text"))),
                getTextBuffer().getLine(0).getEntries()
        );
    }

    @Test
    public void testUriWithSemicolonPreserved() throws IOException {
        // OSC args split on every ';'; case 8 rejoins everything after the params, so a URI that
        // itself contains ';' must reach setLinkUriStarted intact.
        String[] captured = {null};
        mySession.getTextProcessing().setLinkInfoProvider(uri -> {
            captured[0] = uri;
            return new LinkInfo(() -> {
            });
        });
        mySession.process(osc8("", "https://example.com/a;b=c", "link"));
        Assertions.assertEquals("https://example.com/a;b=c", captured[0]);
        assertEntries(
                Collections.singletonList(new TerminalLine.TextEntry(myLinkStyle, new CharBuffer("link"))),
                getTextBuffer().getLine(0).getEntries()
        );
    }

    private static void assertEntries(@NotNull List<TerminalLine.TextEntry> expected,
                                      @NotNull List<TerminalLine.TextEntry> actual) {
        Assertions.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assertions.assertEquals(expected.get(i).getText().toString(), actual.get(i).getText().toString());
            Assertions.assertEquals(expected.get(i).getStyle(), actual.get(i).getStyle());
        }
    }
}
