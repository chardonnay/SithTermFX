package com.sithtermfx.core.emulator.tvi;

import com.sithtermfx.core.DataStreamIteratingEmulator;
import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.Ascii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Base emulator for the TeleVideo (TVI) terminal family (910, 920, 925).
 * <p>
 * TVI terminals are stream-mode, ESC-based character terminals that share
 * many escape sequences with the Wyse 50/60 family due to historical
 * market compatibility. This abstract class implements the common TVI
 * command set; subclasses provide model-specific overrides.
 * <p>
 * Cursor addressing uses {@code ESC = row col} where row and col are
 * biased by +32 (space). Visual attributes are set with {@code ESC G attr}.
 *
 * @author Daniel Mengel
 */
public abstract class AbstractTviEmulator extends DataStreamIteratingEmulator {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTviEmulator.class);

    private static final char SUB = 0x1A;

    private boolean myProtectMode = false;

    private boolean myInsertMode = false;

    public AbstractTviEmulator(TerminalDataStream dataStream, Terminal terminal) {
        super(dataStream, terminal);
    }

    /**
     * Returns the TVI model number (910, 920, or 925).
     */
    protected abstract int getTviModel();

    @Override
    protected void processChar(char ch, Terminal terminal) throws IOException {
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
                terminal.newLine();
                break;
            case Ascii.CR:
                terminal.carriageReturn();
                break;
            case Ascii.SO:
                terminal.mapCharsetToGL(1);
                break;
            case Ascii.SI:
                terminal.mapCharsetToGL(0);
                break;
            case SUB:
                terminal.clearScreen();
                terminal.cursorPosition(1, 1);
                break;
            case Ascii.ESC:
                processEscapeSequence(terminal);
                break;
            default:
                if (ch < 0x20) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("TVI{}: unhandled control 0x{}", getTviModel(),
                                Integer.toHexString(ch));
                    }
                } else {
                    myDataStream.pushChar(ch);
                    String text = myDataStream.readNonControlCharacters(terminal.distanceToLineEnd());
                    if (myInsertMode) {
                        terminal.insertBlankCharacters(text.length());
                    }
                    terminal.writeCharacters(text);
                }
                break;
        }
    }

    /**
     * Processes the byte following ESC. Subclasses may override
     * {@link #processExtendedEscape(char, Terminal)} for model-specific sequences.
     */
    protected void processEscapeSequence(Terminal terminal) throws IOException {
        char ch = myDataStream.getChar();
        switch (ch) {
            case '=':
                cursorAddress(terminal);
                break;
            case 'T':
                terminal.eraseInLine(0);
                break;
            case 't':
                terminal.eraseInLine(0);
                break;
            case 'Y':
                eraseToEndOfPage(terminal);
                break;
            case 'y':
                eraseToEndOfPage(terminal);
                break;
            case '*':
                terminal.clearScreen();
                terminal.cursorPosition(1, 1);
                break;
            case ':':
                terminal.clearScreen();
                terminal.cursorPosition(1, 1);
                break;
            case ')':
                myProtectMode = true;
                break;
            case '(':
                myProtectMode = false;
                break;
            case 'G':
                processAttribute(terminal);
                break;
            case 'g':
                processAttribute(terminal);
                break;
            case '+':
                terminal.clearScreen();
                terminal.cursorPosition(1, 1);
                break;
            case ',':
                terminal.clearScreen();
                terminal.cursorPosition(1, 1);
                break;
            case '.':
                setCursorType(terminal);
                break;
            case '/':
                processComplementField(terminal);
                break;
            case 'A':
                terminal.setCursorVisible(true);
                break;
            case 'b':
                terminal.cursorPosition(1, terminal.getTerminalHeight());
                break;
            case 'j':
                reverseLineFeed(terminal);
                break;
            case 'E':
                insertLine(terminal);
                break;
            case 'R':
                deleteLine(terminal);
                break;
            case 'Q':
                insertCharacter(terminal);
                break;
            case 'W':
                deleteCharacter(terminal);
                break;
            case 'q':
                insertMode(true);
                break;
            case 'r':
                insertMode(false);
                break;
            case '#':
                terminal.saveCursor();
                break;
            case '"':
                terminal.restoreCursor();
                break;
            case '{':
                processKeyboardLock(true);
                break;
            case '}':
                processKeyboardLock(false);
                break;
            case 'I':
                backTab(terminal);
                break;
            case 'i':
                backTab(terminal);
                break;
            case '`':
                setDuplexMode(true);
                break;
            case 'F':
                setDuplexMode(false);
                break;
            case 'K':
                terminal.setAutoNewLine(true);
                break;
            case 'k':
                terminal.setAutoNewLine(false);
                break;
            case 'l':
                setLine25(terminal);
                break;
            case 'n':
                reportCursorPosition(terminal);
                break;
            case 'o':
                setLine25Protected(terminal);
                break;
            default:
                if (!processExtendedEscape(ch, terminal)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("TVI{}: unhandled ESC {} (0x{})", getTviModel(), ch,
                                Integer.toHexString(ch));
                    }
                }
                break;
        }
    }

    /**
     * Hook for model-specific escape sequences. Returns {@code true} if handled.
     */
    protected boolean processExtendedEscape(char ch, Terminal terminal) throws IOException {
        return false;
    }

    /**
     * ESC = row col — cursor address with +32 bias.
     */
    private void cursorAddress(Terminal terminal) throws IOException {
        char row = myDataStream.getChar();
        char col = myDataStream.getChar();
        int r = (row - 0x20) + 1;
        int c = (col - 0x20) + 1;
        r = Math.max(1, Math.min(r, terminal.getTerminalHeight()));
        c = Math.max(1, Math.min(c, terminal.getTerminalWidth()));
        terminal.cursorPosition(c, r);
    }

    /**
     * ESC G attr — set visual attribute. The attribute byte defines
     * the display characteristic for subsequent text:
     * <ul>
     *   <li>{@code 0} — normal</li>
     *   <li>{@code 1} — blank (hidden)</li>
     *   <li>{@code 2} — blink</li>
     *   <li>{@code 4} — reverse</li>
     *   <li>{@code 8} — underline</li>
     *   <li>Values can be combined (OR'd)</li>
     * </ul>
     */
    private void processAttribute(Terminal terminal) throws IOException {
        char attr = myDataStream.getChar();
        int attrValue = attr - '0';
        TextStyle.Builder builder = new TextStyle.Builder();
        if ((attrValue & 0x01) != 0) {
            builder.setOption(TextStyle.Option.HIDDEN, true);
        }
        if ((attrValue & 0x02) != 0) {
            builder.setOption(TextStyle.Option.SLOW_BLINK, true);
        }
        if ((attrValue & 0x04) != 0) {
            builder.setOption(TextStyle.Option.INVERSE, true);
        }
        if ((attrValue & 0x08) != 0) {
            builder.setOption(TextStyle.Option.UNDERLINED, true);
        }
        if ((attrValue & 0x10) != 0) {
            builder.setOption(TextStyle.Option.BOLD, true);
        }
        if ((attrValue & 0x20) != 0) {
            builder.setOption(TextStyle.Option.DIM, true);
        }
        terminal.characterAttributes(builder.build());
    }

    private void eraseToEndOfPage(Terminal terminal) {
        terminal.eraseInLine(0);
        int curY = terminal.getCursorY();
        int height = terminal.getTerminalHeight();
        if (curY < height) {
            int savedX = terminal.getCursorX();
            int savedY = curY;
            for (int row = curY + 1; row <= height; row++) {
                terminal.cursorPosition(1, row);
                terminal.eraseInLine(2);
            }
            terminal.cursorPosition(savedX, savedY);
        }
    }

    private void reverseLineFeed(Terminal terminal) {
        terminal.reverseIndex();
    }

    private void insertLine(Terminal terminal) {
        terminal.insertLines(1);
    }

    private void deleteLine(Terminal terminal) {
        terminal.deleteLines(1);
    }

    private void insertCharacter(Terminal terminal) {
        terminal.insertBlankCharacters(1);
    }

    private void deleteCharacter(Terminal terminal) {
        terminal.deleteCharacters(1);
    }

    private void insertMode(boolean enabled) {
        myInsertMode = enabled;
    }

    /**
     * ESC . n — set cursor type (visible/invisible, block/underline).
     */
    private void setCursorType(Terminal terminal) throws IOException {
        char type = myDataStream.getChar();
        switch (type) {
            case '0':
                terminal.setCursorVisible(false);
                break;
            case '1':
                terminal.setCursorVisible(true);
                break;
            case '2':
                terminal.setCursorVisible(true);
                break;
            case '3':
                terminal.setCursorVisible(true);
                break;
            case '4':
                terminal.setCursorVisible(false);
                break;
            default:
                terminal.setCursorVisible(true);
                break;
        }
    }

    /**
     * ESC / — complement-field toggle (read attribute byte, skip).
     */
    private void processComplementField(Terminal terminal) throws IOException {
        myDataStream.getChar();
    }

    private void processKeyboardLock(boolean locked) {
        if (logger.isDebugEnabled()) {
            logger.debug("TVI{}: keyboard {} (stub)", getTviModel(), locked ? "locked" : "unlocked");
        }
    }

    private void backTab(Terminal terminal) {
        int curX = terminal.getCursorX();
        if (curX > 1) {
            int newX = ((curX - 2) / 8) * 8 + 1;
            terminal.cursorHorizontalAbsolute(Math.max(1, newX));
        }
    }

    private void setDuplexMode(boolean full) {
        if (logger.isDebugEnabled()) {
            logger.debug("TVI{}: {} duplex mode (stub)", getTviModel(), full ? "full" : "half");
        }
    }

    /**
     * ESC l message CR — write message to status line (line 25).
     */
    private void setLine25(Terminal terminal) throws IOException {
        StringBuilder sb = new StringBuilder();
        try {
            char c;
            while ((c = myDataStream.getChar()) != Ascii.CR) {
                sb.append(c);
                if (sb.length() > terminal.getTerminalWidth()) {
                    break;
                }
            }
        } catch (TerminalDataStream.EOF e) {
            // stream ended before CR — use what we have
        }
        terminal.setWindowTitle(sb.toString());
    }

    /**
     * ESC n — report cursor position as ESC = row col.
     */
    private void reportCursorPosition(Terminal terminal) {
        int row = terminal.getCursorY() - 1 + 0x20;
        int col = terminal.getCursorX() - 1 + 0x20;
        String response = "" + Ascii.ESC_CHAR + '=' + (char) row + (char) col;
        terminal.deviceStatusReport(response);
    }

    /**
     * ESC o message CR — write protected status line.
     */
    private void setLine25Protected(Terminal terminal) throws IOException {
        setLine25(terminal);
    }

    protected boolean isProtectMode() {
        return myProtectMode;
    }

    protected boolean isInsertMode() {
        return myInsertMode;
    }
}
