
package com.sithtermfx.core.emulator;

import com.sithtermfx.core.util.Ascii;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.util.CharUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SystemCommandSequence {

    @NotNull
    private final String text;

    @NotNull
    private final List<String> args;

    private static final char ST = '\u009c';

    private static final char ARG_SEPARATOR = ';';

    public static final char OSC = '\u009d';

    /**
     * The longest body (everything between the introducer and the terminator) that is kept. A
     * sequence is as long as whoever writes to the terminal makes it, and it is read across buffer
     * refills, so the rest of a longer one is read and dropped: the sequence still ends at its
     * terminator, and {@link #isTruncated()} tells that it must not be acted on.
     */
    public static final int MAX_BODY_LENGTH = 64 * 1024;

    private final boolean truncated;

    public SystemCommandSequence(@NotNull TerminalDataStream stream) throws IOException {
        this(stream, MAX_BODY_LENGTH);
    }

    SystemCommandSequence(@NotNull TerminalDataStream stream, int maxBodyLength) throws IOException {
        super();
        Intrinsics.checkNotNullParameter(stream, "stream");
        var body = new StringBuilder();
        int droppedChars = 0;
        char prev = 0;
        boolean prevKept = false;
        String terminator;
        while (true) {
            char ch = stream.getChar();
            if (ch == Ascii.BEL_CHAR || ch == ST) {
                terminator = String.valueOf(ch);
                break;
            }
            if (ch == '\\' && prev == Ascii.ESC_CHAR) {
                // the ESC belongs to the terminator, not to the body
                if (prevKept) {
                    body.setLength(body.length() - 1);
                } else {
                    droppedChars--;
                }
                terminator = Ascii.ESC_CHAR + "\\";
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
        truncated = droppedChars > 0;
        text = body + terminator;
        var strArray = new String[]{String.valueOf(ARG_SEPARATOR)};
        var splits = StringsKt.split(body, strArray, false, 0);
        args = List.copyOf(splits);
    }

    /**
     * @return whether the sequence was longer than {@link #MAX_BODY_LENGTH} and only its start was kept
     */
    public boolean isTruncated() {
        return truncated;
    }

    @NotNull
    public final List<String> getArgs() {
        return this.args;
    }

    @Nullable
    public final String getStringAt(int index) {
        return (String) CollectionsKt.getOrNull(this.args, index);
    }

    public int getIntAt(int index, int defaultValue) {
        return parseInt(getStringAt(index), defaultValue);
    }

    private int parseInt(String value, int defaultValue) {
        if (value != null) {
            Integer v = StringsKt.toIntOrNull(value);
            if (v != null) {
                return v;
            }
        }
        return defaultValue;
    }

    public String format(List<String> args) {
        // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-Operating-System-Commands
        // XTerm accepts either BEL  or ST  for terminating OSC
        // sequences, and when returning information, uses the same
        // terminator used in a query.
        return Ascii.ESC_CHAR + "]" + String.join(String.valueOf(ARG_SEPARATOR), args) +
                StringsKt.takeLast(text, terminatorLength(text));
    }

    @Override
    public String toString() {
        return CharUtils.toHumanReadableText(Ascii.ESC_CHAR + "]" + text);
    }

    private static boolean isTwoBytesTerminator(CharSequence text, int length, char lastChar) {
        return lastChar == '\\' && length >= 2 && text.charAt(length - 2) == Ascii.ESC_CHAR;
    }

    private static int terminatorLength(CharSequence text) {
        var length = text.length();
        if (isTwoBytesTerminator(text, length, text.charAt(length - 1))) {
            return 2;
        } else {
            return 1;
        }
    }
}
