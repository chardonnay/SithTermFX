package com.sithtermfx.core.emulator.hp;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalColor;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TerminalMode;
import com.sithtermfx.core.TextStyle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * HP 700/92 terminal emulator.
 * <p>
 * The 700/92 is a network terminal that operates in two modes:
 * <ul>
 *   <li><strong>HP-native mode</strong> – the classic HP escape grammar ({@code ESC &})</li>
 *   <li><strong>ANSI-compatible mode</strong> – a VT-like CSI grammar with extended colour</li>
 * </ul>
 * Colour is supported through both HP-native escape extensions and ANSI SGR parameters
 * (foreground 30-37 / background 40-47).
 *
 * @author Daniel Mengel
 */
public class Hp700_92Emulator extends AbstractHpEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Hp700_92Emulator.class);

    private static final String MODEL = "700/92";

    /**
     * When {@code true} the emulator processes ANSI/VT-style CSI sequences in
     * addition to HP-native sequences.  When {@code false} only HP-native mode is
     * active.
     */
    private boolean myAnsiMode;

    private int myForegroundIndex = 7;
    private int myBackgroundIndex = 0;

    public Hp700_92Emulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
        myAnsiMode = false;
    }

    @Override
    @NotNull
    protected String getHpModel() {
        return MODEL;
    }

    // ---------------------------------------------------------------
    //  processHpEscape – intercept 700/92-specific sequences before
    //  delegating to the base class.
    // ---------------------------------------------------------------

    @Override
    protected void processHpEscape(Terminal terminal) throws IOException {
        char second = peekNextChar();
        switch (second) {
            case '[':
                if (myAnsiMode) {
                    consumeChar();
                    processAnsiCsi(terminal);
                    return;
                }
                break;
            case ')':
                consumeChar();
                processAnsiModeSwitch(terminal);
                return;
            case '(':
                consumeChar();
                processHpModeSwitch(terminal);
                return;
            default:
                break;
        }
        super.processHpEscape(terminal);
    }

    // ---------------------------------------------------------------
    //  ANSI / HP mode switching
    //  ESC ) – switch to ANSI-compatible mode
    //  ESC ( – switch to HP-native mode
    // ---------------------------------------------------------------

    private void processAnsiModeSwitch(Terminal terminal) {
        myAnsiMode = true;
        logger.debug("[HP-{}] Switched to ANSI-compatible mode", MODEL);
    }

    private void processHpModeSwitch(Terminal terminal) {
        myAnsiMode = false;
        logger.debug("[HP-{}] Switched to HP-native mode", MODEL);
    }

    public boolean isAnsiMode() {
        return myAnsiMode;
    }

    public void setAnsiMode(boolean ansiMode) {
        myAnsiMode = ansiMode;
    }

    // ---------------------------------------------------------------
    //  Full ANSI CSI sequence parser (700/92 ANSI-compatible mode)
    //  Supports multi-parameter CSI sequences separated by ';'
    // ---------------------------------------------------------------

    private void processAnsiCsi(Terminal terminal) throws IOException {
        int[] params = new int[16];
        int paramCount = 0;
        StringBuilder num = new StringBuilder();
        boolean questionMark = false;
        int safetyLimit = 256;

        while (safetyLimit-- > 0) {
            char ch = myDataStream.getChar();
            if (ch == '?') {
                questionMark = true;
            } else if (ch >= '0' && ch <= '9') {
                num.append(ch);
            } else if (ch == ';') {
                if (paramCount < params.length) {
                    params[paramCount++] = parseIntOrDefault(num.toString(), 0);
                }
                num.setLength(0);
            } else {
                if (paramCount < params.length) {
                    params[paramCount++] = parseIntOrDefault(num.toString(), 0);
                }
                dispatchAnsiCsi(ch, params, paramCount, questionMark, terminal);
                break;
            }
        }
    }

    private void dispatchAnsiCsi(char finalChar, int[] params, int count,
                                  boolean questionMark, Terminal terminal) {
        int p0 = count > 0 ? params[0] : 0;
        int p1 = count > 1 ? params[1] : 0;
        switch (finalChar) {
            case 'A':
                terminal.cursorUp(p0 == 0 ? 1 : p0);
                break;
            case 'B':
                terminal.cursorDown(p0 == 0 ? 1 : p0);
                break;
            case 'C':
                terminal.cursorForward(p0 == 0 ? 1 : p0);
                break;
            case 'D':
                terminal.cursorBackward(p0 == 0 ? 1 : p0);
                break;
            case 'H':
            case 'f':
                terminal.cursorPosition(
                        p1 == 0 ? 1 : p1,
                        p0 == 0 ? 1 : p0
                );
                break;
            case 'J':
                terminal.eraseInDisplay(p0);
                break;
            case 'K':
                terminal.eraseInLine(p0);
                break;
            case 'L':
                terminal.insertLines(p0 == 0 ? 1 : p0);
                break;
            case 'M':
                terminal.deleteLines(p0 == 0 ? 1 : p0);
                break;
            case 'P':
                terminal.deleteCharacters(p0 == 0 ? 1 : p0);
                break;
            case '@':
                terminal.insertBlankCharacters(p0 == 0 ? 1 : p0);
                break;
            case 'r':
                if (count >= 2) {
                    terminal.setScrollingRegion(p0, p1);
                } else {
                    terminal.resetScrollRegions();
                }
                break;
            case 'm':
                processAnsiSgr(params, count, terminal);
                break;
            case 'h':
                if (questionMark) {
                    processDecPrivateMode(p0, true, terminal);
                }
                break;
            case 'l':
                if (questionMark) {
                    processDecPrivateMode(p0, false, terminal);
                }
                break;
            case 'n':
                processDeviceStatusReport(p0, terminal);
                break;
            default:
                logger.warn("[HP-{}] Unsupported ANSI CSI: ESC [{}{}", MODEL,
                        paramsToString(params, count), finalChar);
                break;
        }
    }

    // ---------------------------------------------------------------
    //  ANSI SGR with colour support
    // ---------------------------------------------------------------

    private void processAnsiSgr(int[] params, int count, Terminal terminal) {
        if (count == 0) {
            terminal.characterAttributes(TextStyle.EMPTY);
            return;
        }
        TextStyle.Builder builder = terminal.getStyleState().getCurrent().toBuilder();
        for (int i = 0; i < count; i++) {
            int p = params[i];
            switch (p) {
                case 0:
                    builder = new TextStyle.Builder();
                    myForegroundIndex = 7;
                    myBackgroundIndex = 0;
                    break;
                case 1:
                    builder.setOption(TextStyle.Option.BOLD, true);
                    break;
                case 2:
                    builder.setOption(TextStyle.Option.DIM, true);
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
                case 8:
                    builder.setOption(TextStyle.Option.HIDDEN, true);
                    break;
                case 22:
                    builder.setOption(TextStyle.Option.BOLD, false);
                    builder.setOption(TextStyle.Option.DIM, false);
                    break;
                case 24:
                    builder.setOption(TextStyle.Option.UNDERLINED, false);
                    break;
                case 25:
                    builder.setOption(TextStyle.Option.SLOW_BLINK, false);
                    break;
                case 27:
                    builder.setOption(TextStyle.Option.INVERSE, false);
                    break;
                case 30: case 31: case 32: case 33:
                case 34: case 35: case 36: case 37:
                    myForegroundIndex = p - 30;
                    builder.setForeground(TerminalColor.index(myForegroundIndex));
                    break;
                case 39:
                    myForegroundIndex = 7;
                    builder.setForeground(null);
                    break;
                case 40: case 41: case 42: case 43:
                case 44: case 45: case 46: case 47:
                    myBackgroundIndex = p - 40;
                    builder.setBackground(TerminalColor.index(myBackgroundIndex));
                    break;
                case 49:
                    myBackgroundIndex = 0;
                    builder.setBackground(null);
                    break;
                default:
                    logger.warn("[HP-{}] Unknown ANSI SGR attribute: {}", MODEL, p);
                    break;
            }
        }
        terminal.characterAttributes(builder.build());
    }

    // ---------------------------------------------------------------
    //  DEC private modes (subset relevant to 700/92)
    // ---------------------------------------------------------------

    private void processDecPrivateMode(int mode, boolean enabled, Terminal terminal) {
        switch (mode) {
            case 7:
                terminal.setModeEnabled(TerminalMode.AutoWrap, enabled);
                logger.debug("[HP-{}] Auto-wrap {}", MODEL, enabled ? "ON" : "OFF");
                break;
            case 25:
                terminal.setCursorVisible(enabled);
                break;
            default:
                logger.warn("[HP-{}] Unknown DEC private mode: {}", MODEL, mode);
                break;
        }
    }

    // ---------------------------------------------------------------
    //  Device status report  (CSI n)
    // ---------------------------------------------------------------

    private void processDeviceStatusReport(int param, Terminal terminal) {
        if (param == 5) {
            terminal.deviceStatusReport("\033[0n");
        } else if (param == 6) {
            int row = terminal.getCursorY();
            int col = terminal.getCursorX();
            terminal.deviceStatusReport("\033[" + row + ";" + col + "R");
        }
    }

    // ---------------------------------------------------------------
    //  Colour accessors
    // ---------------------------------------------------------------

    public int getForegroundIndex() {
        return myForegroundIndex;
    }

    public int getBackgroundIndex() {
        return myBackgroundIndex;
    }

    // ---------------------------------------------------------------
    //  Utility
    // ---------------------------------------------------------------

    private char peekNextChar() throws IOException {
        char ch = myDataStream.getChar();
        myDataStream.pushChar(ch);
        return ch;
    }

    private void consumeChar() throws IOException {
        myDataStream.getChar();
    }

    private static String paramsToString(int[] params, int count) {
        if (count == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(';');
            sb.append(params[i]);
        }
        return sb.toString();
    }

    private static int parseIntOrDefault(String s, int defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
