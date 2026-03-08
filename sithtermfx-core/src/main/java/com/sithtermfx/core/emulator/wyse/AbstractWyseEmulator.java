package com.sithtermfx.core.emulator.wyse;

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
 * Base emulator for the Wyse terminal family (WY-50, WY-60, WY-160).
 * <p>
 * Wyse terminals use their own ESC-based command set that is distinct from the
 * ANSI/VT family. Escape sequences are generally two or three bytes long
 * ({@code ESC <cmd>} or {@code ESC <cmd> <arg>}). Cursor addressing uses the
 * {@code ESC = <row+0x20> <col+0x20>} format. Video attributes are selected
 * through {@code ESC G <attrib>} where the attribute byte is an ASCII character
 * whose bit pattern selects dim, blink, blank, reverse, and underline.
 *
 * @author Daniel Mengel
 */
public abstract class AbstractWyseEmulator extends DataStreamIteratingEmulator {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWyseEmulator.class);

    private static final char CHAR_BEL = (char) Ascii.BEL;
    private static final char CHAR_BS  = (char) Ascii.BS;
    private static final char CHAR_HT  = (char) Ascii.HT;
    private static final char CHAR_LF  = (char) Ascii.LF;
    private static final char CHAR_CR  = (char) Ascii.CR;
    private static final char CHAR_ESC = (char) Ascii.ESC;
    private static final char CHAR_SO  = (char) Ascii.SO;
    private static final char CHAR_SI  = (char) Ascii.SI;

    private boolean myProtectMode = false;

    public AbstractWyseEmulator(@NotNull TerminalDataStream dataStream, @NotNull Terminal terminal) {
        super(dataStream, terminal);
    }

    /**
     * Returns the Wyse hardware level emulated (50, 60, or 160).
     */
    public abstract int getWyseLevel();

    @Override
    protected void processChar(char ch, Terminal terminal) throws IOException {
        if (ch < 0x20 || ch == 0x7F) {
            processControlChar(ch, terminal);
        } else {
            String nonControl = myDataStream.readNonControlCharacters(terminal.distanceToLineEnd());
            String text = nonControl.isEmpty() ? String.valueOf(ch) : ch + nonControl;
            terminal.writeCharacters(text);
        }
    }

    private void processControlChar(char ch, Terminal terminal) throws IOException {
        switch (ch) {
            case CHAR_BEL:
                if (logger.isTraceEnabled()) {
                    logger.trace("WY{}: BEL", getWyseLevel());
                }
                terminal.beep();
                break;
            case CHAR_BS:
                terminal.backspace();
                break;
            case CHAR_HT:
                terminal.horizontalTab();
                break;
            case CHAR_LF:
                terminal.newLine();
                break;
            case CHAR_CR:
                terminal.carriageReturn();
                break;
            case CHAR_SO:
                terminal.mapCharsetToGL(1);
                break;
            case CHAR_SI:
                terminal.mapCharsetToGL(0);
                break;
            case CHAR_ESC:
                processEscape(terminal);
                break;
            default:
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: ignoring control char 0x{}", getWyseLevel(),
                            Integer.toHexString(ch));
                }
                break;
        }
    }

    private void processEscape(Terminal terminal) throws IOException {
        char cmd = myDataStream.getChar();
        if (logger.isTraceEnabled()) {
            logger.trace("WY{}: ESC {}", getWyseLevel(), cmd);
        }
        processWyseEscape(cmd, terminal);
    }

    /**
     * Handles the character immediately following ESC. Subclasses may override
     * to extend the command set, but should call {@code super} for the common
     * Wyse sequences.
     */
    protected void processWyseEscape(char cmd, Terminal terminal) throws IOException {
        switch (cmd) {
            // --- Cursor positioning ---
            case '=':
                readCursorAddress(terminal);
                break;

            // --- Erase operations ---
            case 'T':
                terminal.eraseInLine(0);
                break;
            case 't':
                terminal.eraseInLine(1);
                break;
            case 'Y':
                terminal.eraseInDisplay(0);
                break;
            case 'y':
                terminal.eraseInDisplay(1);
                break;
            case '*':
            case '+':
                clearScreen(terminal);
                break;

            // --- Cursor movement ---
            case 0x0C: // Ctrl-L — cursor right
                terminal.cursorForward(1);
                break;
            case '.':
                terminal.cursorDown(1);
                break;
            case '^':
                terminal.cursorUp(1);
                break;

            // --- Line insert / delete ---
            case 'E':
                terminal.insertLines(1);
                break;
            case 'R':
                terminal.deleteLines(1);
                break;

            // --- Character insert / delete ---
            case 'Q':
                terminal.insertBlankCharacters(1);
                break;
            case 'W':
                terminal.deleteCharacters(1);
                break;

            // --- Video attributes ---
            case 'G':
                processAttribute(terminal);
                break;
            case ')':
                setHalfIntensity(terminal, true);
                break;
            case '(':
                setHalfIntensity(terminal, false);
                break;

            // --- Protect mode ---
            case '&':
                myProtectMode = true;
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: protect mode ON", getWyseLevel());
                }
                break;
            case '\'':
                myProtectMode = false;
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: protect mode OFF", getWyseLevel());
                }
                break;

            // --- Misc ---
            case '#':
                terminal.saveCursor();
                break;
            case '\"':
                terminal.restoreCursor();
                break;
            case '/':
                terminal.setCursorVisible(false);
                break;

            case 'r':
                processSetScrollRegion(terminal);
                break;

            case '`':
                processCursorVisibility(terminal);
                break;

            case '~':
                processColumnMode(terminal);
                break;

            default:
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: unhandled ESC '{}' (0x{})", getWyseLevel(), cmd,
                            Integer.toHexString(cmd));
                }
                break;
        }
    }

    /**
     * Reads a Wyse-style cursor address: {@code ESC = <row+0x20> <col+0x20>}.
     * Row and column values are biased by 0x20 (32), so a value of 0x20 means
     * row/column 0.
     */
    protected void readCursorAddress(Terminal terminal) throws IOException {
        char rowChar = myDataStream.getChar();
        char colChar = myDataStream.getChar();
        int row = rowChar - 0x20;
        int col = colChar - 0x20;
        if (logger.isTraceEnabled()) {
            logger.trace("WY{}: cursor address row={} col={}", getWyseLevel(), row, col);
        }
        terminal.cursorPosition(col + 1, row + 1);
    }

    /**
     * Processes {@code ESC G <attrib>} — Wyse video attribute selection.
     * The attribute byte encodes:
     * <ul>
     *   <li>Bit 0 (0x01): blank / invisible</li>
     *   <li>Bit 1 (0x02): blink</li>
     *   <li>Bit 2 (0x04): reverse video</li>
     *   <li>Bit 3 (0x08): underline</li>
     *   <li>Bit 4 (0x10): dim / half-intensity</li>
     * </ul>
     * The attribute byte is transmitted as an ASCII character, with '0' (0x30)
     * representing the base value (all attributes off). So the effective mask
     * is {@code (attrib - '0')}.
     */
    protected void processAttribute(Terminal terminal) throws IOException {
        char attrib = myDataStream.getChar();
        int bits = attrib - '0';
        if (logger.isTraceEnabled()) {
            logger.trace("WY{}: set attribute 0x{} (bits=0x{})", getWyseLevel(),
                    Integer.toHexString(attrib), Integer.toHexString(bits));
        }
        applyWyseAttributeBits(bits, terminal);
    }

    /**
     * Translates Wyse attribute bits into a {@link TextStyle} and applies it.
     */
    protected void applyWyseAttributeBits(int bits, Terminal terminal) {
        TextStyle.Builder builder = new TextStyle.Builder();
        builder.setOption(TextStyle.Option.HIDDEN, (bits & 0x01) != 0);
        builder.setOption(TextStyle.Option.SLOW_BLINK, (bits & 0x02) != 0);
        builder.setOption(TextStyle.Option.INVERSE, (bits & 0x04) != 0);
        builder.setOption(TextStyle.Option.UNDERLINED, (bits & 0x08) != 0);
        builder.setOption(TextStyle.Option.DIM, (bits & 0x10) != 0);
        terminal.characterAttributes(builder.build());
    }

    private void setHalfIntensity(Terminal terminal, boolean on) {
        TextStyle.Builder builder = terminal.getStyleState().getCurrent().toBuilder();
        builder.setOption(TextStyle.Option.DIM, on);
        terminal.characterAttributes(builder.build());
    }

    private void clearScreen(Terminal terminal) {
        terminal.clearScreen();
        terminal.cursorPosition(1, 1);
    }

    /**
     * Processes {@code ESC r} to define a scroll region. The format is
     * {@code ESC r <top+0x20> <bottom+0x20>}.
     */
    private void processSetScrollRegion(Terminal terminal) throws IOException {
        char topChar = myDataStream.getChar();
        char botChar = myDataStream.getChar();
        int top = topChar - 0x20;
        int bot = botChar - 0x20;
        if (logger.isTraceEnabled()) {
            logger.trace("WY{}: scroll region top={} bottom={}", getWyseLevel(), top, bot);
        }
        terminal.setScrollingRegion(top + 1, bot + 1);
    }

    /**
     * Processes {@code ESC `} followed by a subcommand for cursor visibility:
     * '0' = cursor off, '1' = cursor on (block), '5' = cursor on (underline).
     */
    private void processCursorVisibility(Terminal terminal) throws IOException {
        char sub = myDataStream.getChar();
        switch (sub) {
            case '0':
                terminal.setCursorVisible(false);
                break;
            case '1':
                terminal.setCursorVisible(true);
                break;
            case '5':
                terminal.setCursorVisible(true);
                break;
            default:
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: unknown cursor visibility sub-command '{}'", getWyseLevel(), sub);
                }
                break;
        }
    }

    /**
     * Processes {@code ESC ~} for 80/132 column mode switching.
     * '!' = 80 columns, '"' = 132 columns.
     */
    private void processColumnMode(Terminal terminal) throws IOException {
        char sub = myDataStream.getChar();
        switch (sub) {
            case '!':
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: switch to 80-column mode", getWyseLevel());
                }
                terminal.resize(
                        new com.sithtermfx.core.util.TermSize(80, terminal.getTerminalHeight()),
                        com.sithtermfx.core.RequestOrigin.Remote);
                break;
            case '"':
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: switch to 132-column mode", getWyseLevel());
                }
                terminal.resize(
                        new com.sithtermfx.core.util.TermSize(132, terminal.getTerminalHeight()),
                        com.sithtermfx.core.RequestOrigin.Remote);
                break;
            default:
                if (logger.isDebugEnabled()) {
                    logger.debug("WY{}: unknown column mode sub-command '{}'", getWyseLevel(), sub);
                }
                break;
        }
    }

    protected boolean isProtectMode() {
        return myProtectMode;
    }
}
