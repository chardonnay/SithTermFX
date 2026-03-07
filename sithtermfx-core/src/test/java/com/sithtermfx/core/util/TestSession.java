package com.sithtermfx.core.util;

import com.sithtermfx.core.Color;
import com.sithtermfx.core.ArrayTerminalDataStream;
import com.sithtermfx.core.HyperlinkStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.emulator.Emulator;
import com.sithtermfx.core.emulator.SithEmulator;
import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.model.hyperlinks.TextProcessing;
import org.jetbrains.annotations.NotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;

public class TestSession {

    public static final Color BLUE = new Color(0, 0, 255);

    private final BackBufferTerminal myTerminal;

    private final TextProcessing myTextProcessing;

    private final TerminalTextBuffer myTerminalTextBuffer;

    private final StyleState myStyleState;

    public TestSession(int width, int height) {
        myStyleState = new StyleState();
        TextStyle hyperlinkTextStyle = new TextStyle(TerminalColor.fromColor(BLUE), TerminalColor.WHITE);
        myTextProcessing = new TextProcessing(hyperlinkTextStyle,
                HyperlinkStyle.HighlightMode.ALWAYS_WITH_CUSTOM_COLOR);
        myTerminalTextBuffer = new TerminalTextBuffer(width, height, myStyleState, myTextProcessing);
        myTextProcessing.setTerminalTextBuffer(myTerminalTextBuffer);
        myTerminal = new BackBufferTerminal(myTerminalTextBuffer, myStyleState);
    }

    public @NotNull BackBufferTerminal getTerminal() {
        return myTerminal;
    }

    public @NotNull BackBufferDisplay getDisplay() {
        return myTerminal.getDisplay();
    }

    public @NotNull TerminalTextBuffer getTerminalTextBuffer() {
        return myTerminalTextBuffer;
    }

    public @NotNull TextProcessing getTextProcessing() {
        return myTextProcessing;
    }

    public @NotNull TextStyle getCurrentStyle() {
        return myStyleState.getCurrent();
    }

    public void process(@NotNull String data) throws IOException {
        ArrayTerminalDataStream fileStream = new ArrayTerminalDataStream(data.toCharArray());
        Emulator emulator = new SithEmulator(fileStream, myTerminal);
        while (emulator.hasNext()) {
            emulator.next();
        }
    }

    public void assertCursorPosition(int expectedOneBasedCursorX, int expectedOneBasedCursorY) {
        assertEquals(stringifyCursor(expectedOneBasedCursorX, expectedOneBasedCursorY),
                stringifyCursor(myTerminal.getCursorX(), myTerminal.getCursorY()));
    }

    private static @NotNull String stringifyCursor(int cursorX, int cursorY) {
        return "cursorX=" + cursorX + ", cursorY=" + cursorY;
    }
}
