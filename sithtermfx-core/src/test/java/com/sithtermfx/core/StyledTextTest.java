package com.sithtermfx.core;

import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.ArrayTerminalDataStream;
import com.sithtermfx.core.emulator.Emulator;
import com.sithtermfx.core.emulator.SithEmulator;
import com.sithtermfx.core.model.SithTerminal;
import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.BackBufferDisplay;
import org.jetbrains.annotations.NotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * @author Daniel Mengel
 */
public class StyledTextTest {

    private static final String CSI = "" + ((char) 27) + "[";

    private static final TextStyle GREEN = new TextStyle(TerminalColor.index(2), null);

    private static final TextStyle BLACK = new TextStyle(TerminalColor.BLACK, null);

    @Test
    public void test24BitForegroundColourParsing() throws IOException {
        TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "38;2;0;128;0mHello");
        TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
        assertEquals(new TerminalColor(0, 128, 0), style.getForeground());
    }

    @Test
    public void test24BitBackgroundColourParsing() throws IOException {
        TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "48;2;0;128;0mHello");
        TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
        assertEquals(new TerminalColor(0, 128, 0), style.getBackground());
    }

    @Test
    public void test24BitCombinedColourParsing() throws IOException {
        TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "0;38;2;0;128;0;48;2;0;255;0;1mHello");
        TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
        assertEquals(new TerminalColor(0, 128, 0), style.getForeground());
        assertEquals(new TerminalColor(0, 255, 0), style.getBackground());
        assertTrue(style.hasOption(TextStyle.Option.BOLD));
    }

    @Test
    public void testIndexedForegroundColourParsing() throws IOException {
        TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "38;5;46mHello");
        TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
        assertEquals(new TerminalColor(0, 255, 0), style.getForeground());
    }

    @Test
    public void testIndexedBackgroundColourParsing() throws IOException {
        TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "48;5;46mHello");
        TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
        assertEquals(new TerminalColor(0, 255, 0), style.getBackground());
    }

    @Test
    public void testIndexedCombinedColourParsing() throws IOException {
        TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "0;38;5;46;48;5;196;1mHello");
        TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
        assertEquals(new TerminalColor(0, 255, 0), style.getForeground());
        assertEquals(new TerminalColor(255, 0, 0), style.getBackground());
        assertTrue(style.hasOption(TextStyle.Option.BOLD));
    }

    private @NotNull TerminalTextBuffer getBufferFor(int width, int height, String content) throws IOException {
        StyleState state = new StyleState();
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(width, height, state);
        SithTerminal terminal = new SithTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
        Emulator emulator = new SithEmulator(new ArrayTerminalDataStream(content.toCharArray()), terminal);
        while (emulator.hasNext()) {
            emulator.next();
        }
        return terminalTextBuffer;
    }
}
