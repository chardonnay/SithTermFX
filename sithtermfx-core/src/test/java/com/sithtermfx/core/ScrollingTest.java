package com.sithtermfx.core;

import com.sithtermfx.core.RequestOrigin;
import com.sithtermfx.core.util.TermSize;
import com.sithtermfx.core.model.SithTerminal;
import com.sithtermfx.core.model.StyleState;
import com.sithtermfx.core.model.TerminalTextBuffer;
import com.sithtermfx.core.util.ArrayBasedTextConsumer;
import com.sithtermfx.core.util.BackBufferDisplay;
import com.sithtermfx.core.util.TestSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author Daniel Mengel
 */
public class ScrollingTest {

    private static final String ESC = "\u001B";

    @Test
    public void testScrollOnNewLine() {
        StyleState state = new StyleState();
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);
        SithTerminal terminal = new SithTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
        terminal.writeString("line");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("line2");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("line3");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("line4");
        assertEquals(1, terminalTextBuffer.getHistoryBuffer().getLineCount());
        assertEquals("line2\n" +
                "line3\n" +
                "line4\n", terminalTextBuffer.getScreenLines());
        assertEquals(3, terminal.getCursorY());
    }

    @Test
    public void testScrollOnTyping() {
        StyleState state = new StyleState();
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);
        SithTerminal terminal = new SithTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
        terminal.writeString("line");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("line2");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("line3");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("line4");
        terminal.writeString("4");
        terminal.writeString("4");
        assertEquals(2, terminalTextBuffer.getHistoryBuffer().getLineCount());
        assertEquals("line3\n" +
                "line4\n" +
                "44   \n", terminalTextBuffer.getScreenLines());
        assertEquals(3, terminal.getCursorY());
    }

    @Test
    public void testScrollAndResize() {
        TestSession session = new TestSession(10, 4);
        TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
        SithTerminal terminal = session.getTerminal();
        terminal.writeString("1234567890");
        terminal.crnl();
        terminal.writeString("2345678901");
        terminal.crnl();
        session.assertCursorPosition(1, 3);
        terminal.resize(new TermSize(7, 4), RequestOrigin.User);
        assertEquals("1234567", textBuffer.getHistoryBuffer().getLines());
        assertEquals(
                "890    \n" +
                        "2345678\n" +
                        "901    \n" +
                        "       \n"
                , textBuffer.getScreenLines());
        terminal.writeString("3456789");
        terminal.crnl();
        assertEquals(
                "1234567\n" +
                        "890", textBuffer.getHistoryBuffer().getLines());
        assertEquals(
                "2345678\n" +
                        "901    \n" +
                        "3456789\n" +
                        "       \n"
                , textBuffer.getScreenLines());
        session.assertCursorPosition(1, 4);
    }

    /**
     * With origin mode (DECOM) reset the scrolling region must not confine the cursor: text written
     * above the top margin belongs on the addressed line. tmux relies on this — it draws the first
     * pane row while the margins are still set to the scrolled sub-region, which used to push that
     * row one line down and strand the cursor on the blank line above it.
     */
    @Test
    public void testWriteAboveScrollRegionTopStaysOnAddressedLine() throws IOException {
        TestSession session = new TestSession(5, 4);
        session.process(ESC + "[2;3r" + ESC + "[1;1Hab");
        assertEquals("ab   \n" +
                "     \n" +
                "     \n" +
                "     \n", session.getTerminalTextBuffer().getScreenLines());
        session.assertCursorPosition(3, 1);
    }

    /** With origin mode set, the same write is confined to the region — line addressing is relative. */
    @Test
    public void testWriteAboveScrollRegionTopInOriginMode() throws IOException {
        TestSession session = new TestSession(5, 4);
        session.process(ESC + "[?6h" + ESC + "[2;3r" + ESC + "[1;1Hab");
        assertEquals("     \n" +
                "ab   \n" +
                "     \n" +
                "     \n", session.getTerminalTextBuffer().getScreenLines());
        session.assertCursorPosition(3, 2);
    }

    /**
     * The mirror image of the case above: a line below the bottom margin is addressable too, and
     * writing there must not scroll the region out from under the text that is already on screen.
     * tmux paints its status line exactly like this.
     */
    @Test
    public void testWriteBelowScrollRegionBottomStaysOnAddressedLine() throws IOException {
        TestSession session = new TestSession(5, 4);
        session.process(ESC + "[1;3r" + ESC + "[1;1Ha" + ESC + "[4;1Hzz");
        assertEquals("a    \n" +
                "     \n" +
                "     \n" +
                "zz   \n", session.getTerminalTextBuffer().getScreenLines());
        session.assertCursorPosition(3, 4);
    }

    /** A line feed below the bottom margin advances to the last screen line without scrolling. */
    @Test
    public void testLineFeedBelowScrollRegionDoesNotScroll() throws IOException {
        TestSession session = new TestSession(5, 4);
        session.process(ESC + "[1;3r" + ESC + "[1;1Ha" + ESC + "[2;1Hb" + ESC + "[3;1Hc"
                + ESC + "[4;1Hz" + "\n");
        assertEquals("a    \n" +
                "b    \n" +
                "c    \n" +
                "z    \n", session.getTerminalTextBuffer().getScreenLines());
        assertEquals(0, session.getTerminalTextBuffer().getHistoryLinesCount());
        session.assertCursorPosition(2, 4);
    }

    /** A line feed on the bottom margin still scrolls the region — and only the region. */
    @Test
    public void testLineFeedOnScrollRegionBottomScrollsRegionOnly() throws IOException {
        TestSession session = new TestSession(5, 4);
        session.process(ESC + "[1;3r" + ESC + "[4;1Hz" + ESC + "[1;1Ha" + ESC + "[2;1Hb"
                + ESC + "[3;1Hc" + "\n");
        assertEquals("b    \n" +
                "c    \n" +
                "     \n" +
                "z    \n", session.getTerminalTextBuffer().getScreenLines());
        session.assertCursorPosition(2, 3);
    }

    @Test
    public void testScrollingOrigin() {
        StyleState state = new StyleState();
        TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(2, 3, state);
        SithTerminal terminal = new SithTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
        terminal.writeString("1");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("2");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("3");
        terminal.newLine();
        terminal.carriageReturn();
        terminal.writeString("4");
        terminal.newLine();
        terminal.carriageReturn();
        assertEquals("3 \n" +
                "4 \n" +
                "  \n", terminalTextBuffer.getScreenLines());
        assertEquals("1\n2", terminalTextBuffer.getHistoryBuffer().getLines());
        ArrayBasedTextConsumer textConsumer =
                new ArrayBasedTextConsumer(terminalTextBuffer.getHeight(), terminalTextBuffer.getWidth());
        terminalTextBuffer.processHistoryAndScreenLines(0, terminalTextBuffer.getHeight(), textConsumer);
        assertEquals("3 \n" +
                "4 \n" +
                "  \n", textConsumer.getLines());
        textConsumer = new ArrayBasedTextConsumer(terminalTextBuffer.getHeight(), terminalTextBuffer.getWidth());
        terminalTextBuffer.processHistoryAndScreenLines(-1, terminalTextBuffer.getHeight(), textConsumer);
        assertEquals("2 \n" +
                "3 \n" +
                "4 \n", textConsumer.getLines());
        textConsumer = new ArrayBasedTextConsumer(terminalTextBuffer.getHeight(), terminalTextBuffer.getWidth());
        terminalTextBuffer.processHistoryAndScreenLines(-2, terminalTextBuffer.getHeight(), textConsumer);
        assertEquals("1 \n" +
                "2 \n" +
                "3 \n", textConsumer.getLines());
    }
}
