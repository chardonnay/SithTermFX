package com.sithtermfx.core.emulator.hp;

import com.sithtermfx.core.DataStreamIteratingEmulator;
import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.Ascii;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Abstract base class for the HP terminal emulator family (HP 2392, HP 700/92).
 * <p>
 * HP terminals use ESC-based sequences that are distinct from VT/ANSI conventions.
 * The primary escape grammar is {@code ESC &} followed by a sub-command letter and
 * parameters, but a subset of ANSI CSI sequences ({@code ESC [}) is also recognised
 * for basic screen operations.
 *
 * @author Daniel Mengel
 */
public abstract class AbstractHpEmulator extends DataStreamIteratingEmulator {

    private static final Logger logger = LoggerFactory.getLogger(AbstractHpEmulator.class);

    /** Display enhancement flags used by {@code ESC &d<x>} sequences. */
    protected static final int ENH_NONE        = 0;
    protected static final int ENH_HALF_BRIGHT = 1;
    protected static final int ENH_UNDERLINE   = 1 << 1;
    protected static final int ENH_BLINK       = 1 << 2;
    protected static final int ENH_INVERSE     = 1 << 3;

    protected boolean myBlockMode;

    /** Current active display enhancements (bitmask of ENH_* constants). */
    private int myActiveEnhancements = ENH_NONE;

    /** Whether the cursor is currently visible. */
    private boolean myCursorVisible = true;

    /** Number of softkey labels (f1-f8 on classic HP). */
    protected static final int SOFTKEY_COUNT = 8;
    protected final String[] mySoftkeyLabels = new String[SOFTKEY_COUNT];
    protected final String[] mySoftkeyValues = new String[SOFTKEY_COUNT];

    protected AbstractHpEmulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
        myBlockMode = false;
        for (int i = 0; i < SOFTKEY_COUNT; i++) {
            mySoftkeyLabels[i] = "f" + (i + 1);
            mySoftkeyValues[i] = "";
        }
    }

    /**
     * Returns the HP model identifier for this emulator (e.g. "2392", "700/92").
     */
    @NotNull
    protected abstract String getHpModel();

    // ---------------------------------------------------------------
    //  processChar – main character dispatcher
    // ---------------------------------------------------------------

    @Override
    public void processChar(char ch, Terminal terminal) throws IOException {
        switch (ch) {
            case 0:
                break;
            case Ascii.BEL:
                terminal.beep();
                break;
            case Ascii.BS:
                terminal.backspace();
                break;
            case Ascii.HT:
                terminal.horizontalTab();
                break;
            case Ascii.LF:
            case Ascii.VT:
            case Ascii.FF:
                terminal.newLine();
                break;
            case Ascii.CR:
                terminal.carriageReturn();
                break;
            case Ascii.ESC:
                processHpEscape(terminal);
                break;
            default:
                if (ch <= Ascii.US) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("[HP-{}] Ignored control char 0x{}", getHpModel(),
                                Integer.toHexString(ch));
                    }
                } else {
                    myDataStream.pushChar(ch);
                    String text = myDataStream.readNonControlCharacters(terminal.distanceToLineEnd());
                    terminal.writeCharacters(text);
                }
                break;
        }
    }

    // ---------------------------------------------------------------
    //  HP escape sequence router
    // ---------------------------------------------------------------

    /**
     * Reads the character(s) following ESC and dispatches to the appropriate handler.
     */
    protected void processHpEscape(Terminal terminal) throws IOException {
        char second = myDataStream.getChar();
        switch (second) {
            case '&':
                processAmpersandSequence(terminal);
                break;
            case '[':
                processCsiSequence(terminal);
                break;
            case 'A':
                terminal.cursorUp(1);
                break;
            case 'B':
                terminal.cursorDown(1);
                break;
            case 'C':
                terminal.cursorForward(1);
                break;
            case 'D':
                terminal.cursorBackward(1);
                break;
            case 'F':
                terminal.cursorPosition(1, terminal.getTerminalHeight());
                break;
            case 'G':
                terminal.cursorForward(1);
                break;
            case 'H':
                terminal.cursorPosition(1, 1);
                break;
            case 'I':
                terminal.reverseIndex();
                break;
            case 'J':
                terminal.eraseInDisplay(0);
                break;
            case 'K':
                terminal.eraseInLine(0);
                break;
            case 'L':
                terminal.insertLines(1);
                break;
            case 'M':
                terminal.deleteLines(1);
                break;
            case 'P':
                terminal.deleteCharacters(1);
                break;
            case 'Q':
                terminal.insertBlankCharacters(1);
                break;
            case 'R':
                processInsertReplaceModeToggle(terminal);
                break;
            case 'S':
                // ESC S – roll up (scroll up)
                terminal.scrollUp(1);
                break;
            case 'T':
                // ESC T – roll down (scroll down)
                terminal.scrollDown(1);
                break;
            case 'U':
                // ESC U – next page (clear screen in stream mode)
                terminal.eraseInDisplay(2);
                terminal.cursorPosition(1, 1);
                break;
            case 'V':
                // ESC V – previous page (not directly supported, clear screen)
                terminal.eraseInDisplay(2);
                terminal.cursorPosition(1, 1);
                break;
            case 'h':
                // ESC h – home cursor + clear display
                terminal.cursorPosition(1, 1);
                terminal.eraseInDisplay(0);
                break;
            case 'i':
                // ESC i – back tab
                terminal.cursorBackward(1);
                break;
            case 'p':
            case 'q':
            case 'r':
            case 's':
            case 't':
            case 'u':
            case 'v':
            case 'w':
                processSoftkeyReturn(second);
                break;
            default:
                logger.warn("[HP-{}] Unsupported escape: ESC {}", getHpModel(), second);
                break;
        }
    }

    // ---------------------------------------------------------------
    //  ESC & – HP ampersand sub-commands
    // ---------------------------------------------------------------

    private void processAmpersandSequence(Terminal terminal) throws IOException {
        char subCmd = myDataStream.getChar();
        switch (subCmd) {
            case 'a':
                processAbsoluteCursorAddress(terminal);
                break;
            case 'd':
                processDisplayEnhancement(terminal);
                break;
            case 'f':
                processSoftkeyDefinition();
                break;
            case 'j':
                processRelativeCursorAddress(terminal);
                break;
            case 'k':
                processKeyboardControl();
                break;
            case 's':
                processDisplayFunctionSet(terminal);
                break;
            default:
                logger.warn("[HP-{}] Unsupported ESC & sequence: ESC &{}", getHpModel(), subCmd);
                skipToTerminator();
                break;
        }
    }

    // ---------------------------------------------------------------
    //  ESC &a – absolute cursor addressing  (ESC &a <col> c <row> R)
    //           or (ESC &a <col> C)  /  (ESC &a <row> R)
    // ---------------------------------------------------------------

    private void processAbsoluteCursorAddress(Terminal terminal) throws IOException {
        int col = -1;
        int row = -1;
        StringBuilder num = new StringBuilder();
        int safetyLimit = 256;

        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch >= '0' && ch <= '9') {
                num.append(ch);
            } else if (ch == 'c' || ch == 'C') {
                col = parseIntOrDefault(num.toString(), 0);
                num.setLength(0);
            } else if (ch == 'r' || ch == 'R' || ch == 'Y' || ch == 'y') {
                row = parseIntOrDefault(num.toString(), 0);
                num.setLength(0);
                break;
            } else {
                if (num.length() > 0 && col == -1) {
                    col = parseIntOrDefault(num.toString(), 0);
                }
                break;
            }
        }
        if (safetyLimit <= 0) {
            if (num.length() > 0 && col == -1) {
                col = parseIntOrDefault(num.toString(), 0);
            }
        }

        if (col >= 0 && row >= 0) {
            terminal.cursorPosition(col + 1, row + 1);
        } else if (col >= 0) {
            terminal.cursorHorizontalAbsolute(col + 1);
        } else if (row >= 0) {
            terminal.linePositionAbsolute(row + 1);
        }
    }

    // ---------------------------------------------------------------
    //  ESC &j – relative cursor movement (ESC &j <n> C / R / L / U)
    // ---------------------------------------------------------------

    private void processRelativeCursorAddress(Terminal terminal) throws IOException {
        StringBuilder num = new StringBuilder();
        int safetyLimit = 256;
        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch >= '0' && ch <= '9') {
                num.append(ch);
            } else {
                int count = parseIntOrDefault(num.toString(), 1);
                switch (ch) {
                    case 'C':
                    case 'c':
                        terminal.cursorForward(count);
                        break;
                    case 'L':
                    case 'l':
                        terminal.cursorBackward(count);
                        break;
                    case 'R':
                    case 'r':
                    case 'D':
                    case 'd':
                        terminal.cursorDown(count);
                        break;
                    case 'U':
                    case 'u':
                        terminal.cursorUp(count);
                        break;
                    default:
                        logger.warn("[HP-{}] Unknown relative cursor suffix: '{}'", getHpModel(), ch);
                        break;
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------
    //  ESC &d – display enhancements
    //  ESC &d@ = normal, ESC &dA = half-bright, ESC &dB = blink,
    //  ESC &dD = underline, ESC &dH = inverse, ESC &dL = dim+underline,
    //  ESC &dJ = inverse+blink, ESC &d@ = turn off enhancements
    // ---------------------------------------------------------------

    private void processDisplayEnhancement(Terminal terminal) throws IOException {
        char enhChar = myDataStream.getChar();
        switch (enhChar) {
            case '@':
                myActiveEnhancements = ENH_NONE;
                applyEnhancements(terminal);
                break;
            case 'A':
                myActiveEnhancements = ENH_HALF_BRIGHT;
                applyEnhancements(terminal);
                break;
            case 'B':
                myActiveEnhancements = ENH_BLINK;
                applyEnhancements(terminal);
                break;
            case 'D':
                myActiveEnhancements = ENH_UNDERLINE;
                applyEnhancements(terminal);
                break;
            case 'E':
                myActiveEnhancements = ENH_HALF_BRIGHT | ENH_UNDERLINE;
                applyEnhancements(terminal);
                break;
            case 'F':
                myActiveEnhancements = ENH_HALF_BRIGHT | ENH_BLINK;
                applyEnhancements(terminal);
                break;
            case 'H':
                myActiveEnhancements = ENH_INVERSE;
                applyEnhancements(terminal);
                break;
            case 'I':
                myActiveEnhancements = ENH_INVERSE | ENH_HALF_BRIGHT;
                applyEnhancements(terminal);
                break;
            case 'J':
                myActiveEnhancements = ENH_INVERSE | ENH_BLINK;
                applyEnhancements(terminal);
                break;
            case 'K':
                myActiveEnhancements = ENH_INVERSE | ENH_HALF_BRIGHT | ENH_BLINK;
                applyEnhancements(terminal);
                break;
            case 'L':
                myActiveEnhancements = ENH_INVERSE | ENH_UNDERLINE;
                applyEnhancements(terminal);
                break;
            default:
                logger.warn("[HP-{}] Unknown display enhancement: ESC &d{}", getHpModel(), enhChar);
                break;
        }
    }

    /**
     * Translates the HP enhancement bitmask into a {@link TextStyle} and applies
     * it to the terminal.
     */
    private void applyEnhancements(Terminal terminal) {
        TextStyle.Builder builder = new TextStyle.Builder();
        if ((myActiveEnhancements & ENH_HALF_BRIGHT) != 0) {
            builder.setOption(TextStyle.Option.DIM, true);
        }
        if ((myActiveEnhancements & ENH_UNDERLINE) != 0) {
            builder.setOption(TextStyle.Option.UNDERLINED, true);
        }
        if ((myActiveEnhancements & ENH_BLINK) != 0) {
            builder.setOption(TextStyle.Option.SLOW_BLINK, true);
        }
        if ((myActiveEnhancements & ENH_INVERSE) != 0) {
            builder.setOption(TextStyle.Option.INVERSE, true);
        }
        terminal.characterAttributes(builder.build());
    }

    // ---------------------------------------------------------------
    //  ESC &f – softkey definition
    //  ESC &f <key#> a <label_len> d <label> <value_len> L <value>
    // ---------------------------------------------------------------

    private void processSoftkeyDefinition() throws IOException {
        StringBuilder buf = new StringBuilder();
        int keyNum = -1;
        int labelLen = -1;
        String label = null;
        int valueLen = -1;
        int safetyLimit = 256;

        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch >= '0' && ch <= '9') {
                buf.append(ch);
            } else if (ch == 'a' || ch == 'A') {
                keyNum = parseIntOrDefault(buf.toString(), -1);
                buf.setLength(0);
            } else if (ch == 'd' || ch == 'D') {
                labelLen = parseIntOrDefault(buf.toString(), 0);
                buf.setLength(0);
                label = readExactChars(labelLen);
            } else if (ch == 'L' || ch == 'l') {
                valueLen = parseIntOrDefault(buf.toString(), 0);
                buf.setLength(0);
                String value = readExactChars(valueLen);
                if (keyNum >= 1 && keyNum <= SOFTKEY_COUNT) {
                    mySoftkeyLabels[keyNum - 1] = label != null ? label : "";
                    mySoftkeyValues[keyNum - 1] = value;
                    if (logger.isDebugEnabled()) {
                        logger.debug("[HP-{}] Softkey f{} defined: label='{}', value='{}'",
                                getHpModel(), keyNum, label, value);
                    }
                }
                break;
            } else {
                break;
            }
        }
    }

    // ---------------------------------------------------------------
    //  ESC &k – keyboard control
    //  ESC &k0A = disable keyboard, ESC &k1A = enable
    // ---------------------------------------------------------------

    private void processKeyboardControl() throws IOException {
        StringBuilder num = new StringBuilder();
        int safetyLimit = 256;
        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch >= '0' && ch <= '9') {
                num.append(ch);
            } else {
                int val = parseIntOrDefault(num.toString(), 0);
                if (ch == 'A' || ch == 'a') {
                    logger.debug("[HP-{}] Keyboard control: {}", getHpModel(), val == 0 ? "disabled" : "enabled");
                } else {
                    logger.warn("[HP-{}] Unknown keyboard control suffix: '{}'", getHpModel(), ch);
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------
    //  ESC &s – display functions mode
    //  ESC &s0A = display functions off, ESC &s1A = display functions on
    // ---------------------------------------------------------------

    private void processDisplayFunctionSet(Terminal terminal) throws IOException {
        StringBuilder num = new StringBuilder();
        int safetyLimit = 256;
        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch >= '0' && ch <= '9') {
                num.append(ch);
            } else {
                int val = parseIntOrDefault(num.toString(), 0);
                if (ch == 'A' || ch == 'a') {
                    if (logger.isDebugEnabled()) {
                        logger.debug("[HP-{}] Display functions mode: {}", getHpModel(),
                                val == 1 ? "ON" : "OFF");
                    }
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------
    //  ESC [ – CSI subset recognised by HP terminals
    // ---------------------------------------------------------------

    private void processCsiSequence(Terminal terminal) throws IOException {
        StringBuilder num = new StringBuilder();
        int safetyLimit = 256;
        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch >= '0' && ch <= '9') {
                num.append(ch);
            } else {
                int param = parseIntOrDefault(num.toString(), 0);
                switch (ch) {
                    case 'J':
                        terminal.eraseInDisplay(param);
                        break;
                    case 'K':
                        terminal.eraseInLine(param);
                        break;
                    case 'L':
                        terminal.insertLines(param == 0 ? 1 : param);
                        break;
                    case 'M':
                        terminal.deleteLines(param == 0 ? 1 : param);
                        break;
                    case 'P':
                        terminal.deleteCharacters(param == 0 ? 1 : param);
                        break;
                    case '@':
                        terminal.insertBlankCharacters(param == 0 ? 1 : param);
                        break;
                    case 'A':
                        terminal.cursorUp(param == 0 ? 1 : param);
                        break;
                    case 'B':
                        terminal.cursorDown(param == 0 ? 1 : param);
                        break;
                    case 'C':
                        terminal.cursorForward(param == 0 ? 1 : param);
                        break;
                    case 'D':
                        terminal.cursorBackward(param == 0 ? 1 : param);
                        break;
                    case 'm':
                        processCsiSgr(param, terminal);
                        break;
                    default:
                        logger.warn("[HP-{}] Unsupported CSI sequence: ESC [{}{}", getHpModel(),
                                num, ch);
                        break;
                }
                break;
            }
        }
    }

    /**
     * Minimal SGR subset for CSI m (used by HP 700/92 in ANSI-compatible mode).
     */
    protected void processCsiSgr(int param, Terminal terminal) {
        TextStyle.Builder builder = new TextStyle.Builder();
        switch (param) {
            case 0:
                break;
            case 1:
                builder.setOption(TextStyle.Option.BOLD, true);
                break;
            case 4:
                builder.setOption(TextStyle.Option.UNDERLINED, true);
                break;
            case 5:
                builder.setOption(TextStyle.Option.SLOW_BLINK, true);
                break;
            case 7:
                builder.setOption(TextStyle.Option.INVERSE, true);
                break;
            default:
                logger.warn("[HP-{}] Unsupported SGR parameter: {}", getHpModel(), param);
                return;
        }
        terminal.characterAttributes(builder.build());
    }

    // ---------------------------------------------------------------
    //  Insert/replace mode toggle  (ESC R)
    // ---------------------------------------------------------------

    private void processInsertReplaceModeToggle(Terminal terminal) {
        logger.debug("[HP-{}] Insert/replace mode toggled", getHpModel());
    }

    // ---------------------------------------------------------------
    //  Softkey return  (ESC p … ESC w  → f1 … f8)
    // ---------------------------------------------------------------

    private void processSoftkeyReturn(char keyChar) {
        int idx = keyChar - 'p';
        if (idx >= 0 && idx < SOFTKEY_COUNT) {
            String value = mySoftkeyValues[idx];
            if (value != null && !value.isEmpty()) {
                try {
                    char[] chars = value.toCharArray();
                    myDataStream.pushBackBuffer(chars, chars.length);
                } catch (IOException e) {
                    logger.error("[HP-{}] Error pushing softkey value for f{}", getHpModel(), idx + 1, e);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Utility methods
    // ---------------------------------------------------------------

    protected int getActiveEnhancements() {
        return myActiveEnhancements;
    }

    protected void setActiveEnhancements(int enhancements) {
        myActiveEnhancements = enhancements;
    }

    protected boolean isCursorVisible() {
        return myCursorVisible;
    }

    protected void setCursorVisible(boolean visible) {
        myCursorVisible = visible;
        myTerminal.setCursorVisible(visible);
    }

    private String readExactChars(int count) throws IOException {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(myDataStream.getChar());
        }
        return sb.toString();
    }

    private void skipToTerminator() throws IOException {
        int safetyLimit = 256;
        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (Character.isLetter(ch) || ch == Ascii.ESC) {
                break;
            }
        }
    }

    private static int parseIntOrDefault(String s, int defaultValue) {
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
