package com.sithtermfx.core.emulator;

import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.util.Ascii;
import com.sithtermfx.core.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;

/**
 * An Application Program Command, {@code ESC _ body ST}. It is read up to the same terminators as an OSC or a
 * DCS ({@link SystemCommandSequence}): BEL, C1 ST or {@code ESC \}, so a string ends at the same char whichever
 * of the three it is. Its use is the kitty graphics protocol, whose body is {@code G}, the control data and a
 * base64 payload.
 */
public final class ApplicationProgramCommand {

    /**
     * The longest body that is kept. A kitty graphics chunk carries 4096 base64 chars, but a program may send an
     * image in fewer, longer chunks. As with an OSC, the rest of a longer string is read and dropped: it still
     * ends at its terminator, and {@link #isTruncated()} tells that it must not be acted on.
     */
    public static final int MAX_BODY_LENGTH = 1024 * 1024;

    private static final char ST = '\u009c';

    private final String myBody;

    private final boolean myTruncated;

    public ApplicationProgramCommand(@NotNull TerminalDataStream stream) throws IOException {
        this(stream, MAX_BODY_LENGTH);
    }

    ApplicationProgramCommand(@NotNull TerminalDataStream stream, int maxBodyLength) throws IOException {
        StringBuilder body = new StringBuilder();
        int droppedChars = 0;
        char prev = 0;
        boolean prevKept = false;
        while (true) {
            char ch = stream.getChar();
            if (ch == Ascii.BEL_CHAR || ch == ST) {
                break;
            }
            if (ch == '\\' && prev == Ascii.ESC_CHAR) {
                // the ESC belongs to the terminator, not to the body
                if (prevKept) {
                    body.setLength(body.length() - 1);
                } else {
                    droppedChars--;
                }
                break;
            }
            prevKept = body.length() < maxBodyLength;
            if (prevKept) {
                body.append(ch);
            } else {
                droppedChars++;
            }
            prev = ch;
        }
        myTruncated = droppedChars > 0;
        myBody = body.toString();
    }

    /**
     * @return everything between {@code ESC _} and the terminator
     */
    public @NotNull String getBody() {
        return myBody;
    }

    /**
     * @return whether the string was longer than {@link #MAX_BODY_LENGTH} and only its start was kept
     */
    public boolean isTruncated() {
        return myTruncated;
    }

    @Override
    public String toString() {
        String body = myBody.length() > 64 ? myBody.substring(0, 64) + "..." : myBody;
        return CharUtils.toHumanReadableText(Ascii.ESC_CHAR + "_" + body);
    }
}
