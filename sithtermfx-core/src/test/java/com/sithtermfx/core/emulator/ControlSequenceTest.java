package com.sithtermfx.core.emulator;

import com.sithtermfx.core.ArrayTerminalDataStream;
import com.sithtermfx.core.util.TestSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chars a CSI cannot place — an intermediate byte, a control character, a misplaced {@code ?} —
 * are pushed back to be read again before the sequence itself, through a 1024-char array. What does
 * not fit is dropped, so an overlong sequence in the output cannot overrun it.
 */
public class ControlSequenceTest {

    private static final String ESC = "\u001B";
    private static final int PUSH_BACK_LENGTH = 1024;

    @Test
    public void pushesBackStrayCharsAheadOfTheSequence() throws IOException {
        assertEquals("\b" + ESC + "[2C", pushedBack("2\bC"));
        assertEquals("$" + ESC + "[?25p", pushedBack("?25$p"));
        assertEquals(" " + ESC + "[1;22;333q", pushedBack("1;22;333 q"));
    }

    @Test
    public void doesNotPushBackASequenceWithoutStrayChars() throws IOException {
        ArrayTerminalDataStream stream = new ArrayTerminalDataStream("1;2H".toCharArray());
        ControlSequence sequence = new ControlSequence(stream);

        assertFalse(sequence.pushBackReordered(stream));
        assertTrue(stream.isEmpty());
    }

    @Test
    public void keepsEveryCharOfASequenceThatExactlyFillsThePushBack() throws IOException {
        String stray = "\0".repeat(PUSH_BACK_LENGTH - "\u001B[5A".length());

        assertEquals(stray + ESC + "[5A", pushedBack(stray + "5A"));
    }

    @Test
    public void dropsStrayCharsThatDoNotFitAndKeepsTheSequenceTerminated() throws IOException {
        assertEquals("\0".repeat(PUSH_BACK_LENGTH - 3) + ESC + "[A", pushedBack("\0".repeat(5_000) + "A"));
        assertEquals("\0".repeat(PUSH_BACK_LENGTH - 4) + ESC + "[?h", pushedBack("?" + "\0".repeat(5_000) + "h"));
    }

    @Test
    public void dropsWholeParametersThatDoNotFit() throws IOException {
        String replay = pushedBack("123;".repeat(600) + "123\bm");

        assertTrue(replay.length() <= PUSH_BACK_LENGTH, "pushed back " + replay.length() + " chars");
        assertTrue(replay.length() > PUSH_BACK_LENGTH - ";123".length(), "pushed back " + replay.length() + " chars");
        assertTrue(replay.matches("\b\u001B\\[123(;123)*m"), replay);
    }

    @Test
    public void anOverlongSequenceDoesNotStopTheEmulator() throws IOException {
        TestSession session = new TestSession(10, 2);

        session.process(ESC + "[" + "\0".repeat(5_000) + "1m" + "after");

        assertEquals("after     \n          \n", session.getTerminalTextBuffer().getScreenLines());
    }

    @Test
    public void outputAfterOverlongParametersStillRenders() throws IOException {
        TestSession session = new TestSession(10, 2);

        session.process(ESC + "[" + "1;".repeat(3_000) + "1\0m" + "after");

        assertEquals("after     \n          \n", session.getTerminalTextBuffer().getScreenLines());
    }

    /** The chars a CSI body (everything after {@code ESC [}) is pushed back as. */
    private static String pushedBack(String body) throws IOException {
        ArrayTerminalDataStream stream = new ArrayTerminalDataStream(body.toCharArray());
        ControlSequence sequence = new ControlSequence(stream);
        assertTrue(stream.isEmpty());

        assertTrue(sequence.pushBackReordered(stream));

        StringBuilder replay = new StringBuilder();
        while (!stream.isEmpty()) {
            replay.append(stream.getChar());
        }
        return replay.toString();
    }
}
