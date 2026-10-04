package com.sithtermfx.core.emulator;

import com.sithtermfx.core.util.Ascii;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.ArrayList;

public class ControlSequence {

    /**
     * {@link #pushBackReordered} hands the stray characters and the re-emitted sequence back in one
     * array of this size. How long a sequence gets is up to whoever writes to the terminal, so what
     * does not fit is dropped.
     */
    private static final int MAX_PUSH_BACK_LENGTH = 1024;

    private int myArgc;

    private int[] myArgv;

    private char myFinalChar;

    private ArrayList<Character> myUnhandledChars;

    private boolean myStartsWithExclamationMark = false; // true when CSI !

    private boolean myStartsWithQuestionMark = false; // true when CSI ?

    private boolean myStartsWithMoreMark = false; // true when CSI >

    private final StringBuilder mySequenceString = new StringBuilder();

    ControlSequence(final TerminalDataStream channel) throws IOException {
        myArgv = new int[5];
        myArgc = 0;
        readControlSequence(channel);
    }

    private void readControlSequence(final TerminalDataStream channel) throws IOException {
        myArgc = 0;
        // Read integer arguments
        int digit = 0;
        int seenDigit = 0;
        int pos = -1;
        while (true) {
            final char b = channel.getChar();
            mySequenceString.append(b);
            pos++;
            if (b == '!' && pos == 0) {
                myStartsWithExclamationMark = true;
            } else if (b == '?' && pos == 0) {
                myStartsWithQuestionMark = true;
            } else if (b == '>' && pos == 0) {
                myStartsWithMoreMark = true;
            } else if (b == ';') {
                if (digit > 0) {
                    myArgc++;
                    if (myArgc == myArgv.length) {
                        int[] replacement = new int[myArgv.length * 2];
                        System.arraycopy(myArgv, 0, replacement, 0, myArgv.length);
                        myArgv = replacement;
                    }
                    myArgv[myArgc] = 0;
                    digit = 0;
                }
            } else if ('0' <= b && b <= '9') {
                myArgv[myArgc] = myArgv[myArgc] * 10 + b - '0';
                digit++;
                seenDigit = 1;
            } else if (':' <= b && b <= '?') {
                addUnhandled(b);
            } else if (0x40 <= b && b <= 0x7E) {
                myFinalChar = b;
                break;
            } else {
                addUnhandled(b);
            }
        }
        myArgc += seenDigit;
    }

    private void addUnhandled(final char b) {
        if (myUnhandledChars == null) {
            myUnhandledChars = new ArrayList<>();
        }
        // More stray characters than fit into the push-back are never read again: don't keep them.
        if (myUnhandledChars.size() < MAX_PUSH_BACK_LENGTH) {
            myUnhandledChars.add(b);
        }
    }

    public boolean pushBackReordered(final TerminalDataStream channel) throws IOException {
        if (myUnhandledChars == null) return false;
        final char[] bytes = new char[MAX_PUSH_BACK_LENGTH];
        // The sequence is always pushed back terminated: ESC, '[', the marker and the final char
        // keep their room. Stray characters that do not fit are dropped, and so are the parameters
        // from the first one that does not fit, as xterm drops the parameters beyond its limit.
        final int markerLength =
                myStartsWithExclamationMark || myStartsWithQuestionMark || myStartsWithMoreMark ? 1 : 0;
        final int maxStrayChars = MAX_PUSH_BACK_LENGTH - 3 - markerLength;
        int i = 0;
        for (final char b : myUnhandledChars) {
            if (i == maxStrayChars) break;
            bytes[i++] = b;
        }
        bytes[i++] = (byte) Ascii.ESC;
        bytes[i++] = (byte) '[';
        if (myStartsWithExclamationMark) {
            bytes[i++] = (byte) '!';
        }
        if (myStartsWithQuestionMark) {
            bytes[i++] = (byte) '?';
        }
        if (myStartsWithMoreMark) {
            bytes[i++] = (byte) '>';
        }
        for (int argi = 0; argi < myArgc; argi++) {
            String s = Integer.toString(myArgv[argi]);
            if (i + (argi != 0 ? 1 : 0) + s.length() >= MAX_PUSH_BACK_LENGTH) break;
            if (argi != 0) bytes[i++] = ';';
            for (int j = 0; j < s.length(); j++) {
                bytes[i++] = s.charAt(j);
            }
        }
        bytes[i++] = myFinalChar;
        channel.pushBackBuffer(bytes, i);
        return true;
    }

    int getCount() {
        return myArgc;
    }

    final int getArg(final int index, final int defaultValue) {
        if (index >= myArgc) {
            return defaultValue;
        }
        return myArgv[index];
    }

    private void appendToBuffer(final StringBuilder sb) {
        sb.append("ESC[");
        if (myStartsWithExclamationMark) {
            sb.append("!");
        }
        if (myStartsWithQuestionMark) {
            sb.append("?");
        }
        if (myStartsWithMoreMark) {
            sb.append(">");
        }
        String sep = "";
        for (int i = 0; i < myArgc; i++) {
            sb.append(sep);
            sb.append(myArgv[i]);
            sep = ";";
        }
        sb.append(myFinalChar);
        if (myUnhandledChars != null) {
            sb.append(" Unhandled:");
            CharUtils.CharacterType last = CharUtils.CharacterType.NONE;
            for (final char b : myUnhandledChars) {
                last = CharUtils.appendChar(sb, last, b);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendToBuffer(sb);
        return sb.toString();
    }

    public char getFinalChar() {
        return myFinalChar;
    }

    public boolean startsWithExclamationMark() {
        return myStartsWithExclamationMark;
    }

    public boolean startsWithQuestionMark() {
        return myStartsWithQuestionMark;
    }

    public boolean startsWithMoreMark() {
        return myStartsWithMoreMark;
    }

    public @NotNull String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("parsed: ");
        appendToBuffer(sb);
        sb.append(", raw: ESC[");
        sb.append(mySequenceString);
        return sb.toString();
    }
}