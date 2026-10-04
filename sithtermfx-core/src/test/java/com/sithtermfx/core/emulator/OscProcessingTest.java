package com.sithtermfx.core.emulator;

import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.model.hyperlinks.LinkInfo;
import com.sithtermfx.core.util.TestSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class OscProcessingTest {

    private static final String ESC = "\u001B";
    private static final String BEL = "\u0007";

    private TestSession mySession;

    @BeforeEach
    public void setUp() {
        mySession = new TestSession(20, 2);
    }

    @Test
    public void anOverlongTitleIsDroppedAndTheOutputAfterItRenders() throws IOException {
        mySession.process(ESC + "]2;short" + BEL);
        String title = "t".repeat(SystemCommandSequence.MAX_BODY_LENGTH);

        mySession.process(ESC + "]2;" + title + BEL + "after");

        assertEquals("short", mySession.getDisplay().getWindowTitle());
        assertEquals("after", mySession.getTerminalTextBuffer().getLine(0).getText().trim());
    }

    @Test
    public void anOverlongLinkTargetMakesNoLink() throws IOException {
        mySession.getTextProcessing().setLinkInfoProvider(uri -> new LinkInfo(() -> {
        }));
        String uri = "https://example.com/" + "a".repeat(SystemCommandSequence.MAX_BODY_LENGTH);

        mySession.process(ESC + "]8;;" + uri + ESC + "\\" + "text" + ESC + "]8;;" + ESC + "\\");

        assertFalse(mySession.getTerminalTextBuffer().getStyleAt(0, 0) instanceof HyperlinkStyle);
        assertEquals("text", mySession.getTerminalTextBuffer().getLine(0).getText().trim());
    }
}
