package com.sithtermfx.core;

import com.sithtermfx.core.TerminalMode;
import com.sithtermfx.core.model.SithTerminal;
import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.BackBufferDisplay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class ModesTest {

    @Test
    public void testAutoWrap() {
        StyleState state = new StyleState();
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 3, state);
        SithTerminal terminal = new SithTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
        terminal.setModeEnabled(TerminalMode.AutoWrap, false);
        //                             1234567890123456789
        terminal.writeUnwrappedString("this is a long line");
        assertEquals("long line \n" +
                "          \n" +
                "          \n", terminalTextBuffer.getScreenLines());
        assertEquals(10, terminal.getCursorX());
        assertEquals(1, terminal.getCursorY());
        terminal.cursorPosition(1, 1);
        terminal.setModeEnabled(TerminalMode.AutoWrap, true);
        //                             1234567890123456789
        terminal.writeUnwrappedString("this is a long line");
        assertEquals("this is a \n" +
                "long line \n" +
                "          \n", terminalTextBuffer.getScreenLines());
        assertEquals(10, terminal.getCursorX());
        assertEquals(2, terminal.getCursorY());
    }

}
