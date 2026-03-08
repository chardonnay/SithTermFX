package com.sithtermfx.core.emulator.tvi;

import com.sithtermfx.core.Terminal;
import com.sithtermfx.core.TerminalDataStream;
import com.sithtermfx.core.TerminalMode;
import com.sithtermfx.core.TextStyle;
import com.sithtermfx.core.util.Ascii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * TeleVideo 925 terminal emulator.
 * <p>
 * The TVI-925 is the most capable model in the family. It adds protected
 * fields with per-field attributes, host-programmable function keys
 * (16 keys × shift/unshift = 32 definitions), bidirectional smooth
 * scrolling, selectable page lengths (1–4 pages of 24 lines), and
 * enhanced visual attributes including dim and bold. The command set
 * is a superset of the 920 and is broadly compatible with Wyse 50.
 *
 * @author Daniel Mengel
 */
public class Tvi925Emulator extends AbstractTviEmulator {

    private static final Logger logger = LoggerFactory.getLogger(Tvi925Emulator.class);

    private int myCurrentPage = 0;

    private static final int MAX_PAGES = 4;

    public Tvi925Emulator(TerminalDataStream dataStream, Terminal terminal) {
        super(dataStream, terminal);
    }

    @Override
    protected int getTviModel() {
        return 925;
    }

    @Override
    protected boolean processExtendedEscape(char ch, Terminal terminal) throws IOException {
        switch (ch) {
            case 'v':
                terminal.setModeEnabled(TerminalMode.AutoWrap, true);
                return true;
            case 'w':
                terminal.setModeEnabled(TerminalMode.AutoWrap, false);
                return true;
            case '~':
                processScrollRegion(terminal);
                return true;
            case 'p':
                processFunctionKeyDefinition();
                return true;
            case 'z':
                processResetToFactory(terminal);
                return true;
            case '-':
                processProtectedFieldAttribute(terminal);
                return true;
            case 'P':
                processPageSelect(terminal);
                return true;
            case 'N':
                processPageRelative(terminal, 1);
                return true;
            case 'O':
                processPageRelative(terminal, -1);
                return true;
            case 'c':
                processColumnWidth(terminal);
                return true;
            case 'e':
                processProtectedFieldWrite(terminal);
                return true;
            case 'd':
                processFieldSeparator(terminal);
                return true;
            case '0':
                processExtendedAttribute(terminal, '0');
                return true;
            case '1':
                processExtendedAttribute(terminal, '1');
                return true;
            default:
                return false;
        }
    }

    /**
     * ESC ~ top bottom — set scrolling region (rows biased by +32).
     */
    private void processScrollRegion(Terminal terminal) throws IOException {
        char topChar = myDataStream.getChar();
        char botChar = myDataStream.getChar();
        int top = (topChar - 0x20) + 1;
        int bottom = (botChar - 0x20) + 1;
        top = Math.max(1, Math.min(top, terminal.getTerminalHeight()));
        bottom = Math.max(top, Math.min(bottom, terminal.getTerminalHeight()));
        terminal.setScrollingRegion(top, bottom);
    }

    /**
     * ESC p key-num label ESC \ — program a function key. The 925 supports
     * 16 unshifted + 16 shifted function key definitions.
     */
    private void processFunctionKeyDefinition() throws IOException {
        char prev = 0;
        int consumed = 0;
        while (consumed < 1024) {
            char c = myDataStream.getChar();
            consumed++;
            if (prev == Ascii.ESC_CHAR && c == '\\') {
                break;
            }
            prev = c;
        }
        logger.debug("TVI925: function key definition received ({} bytes)", consumed);
    }

    private void processResetToFactory(Terminal terminal) {
        myCurrentPage = 0;
        terminal.reset(true);
        logger.debug("TVI925: reset to factory defaults");
    }

    /**
     * ESC - attr — set the attribute for subsequent protected-field text.
     * Uses the same attribute-byte encoding as ESC G.
     */
    private void processProtectedFieldAttribute(Terminal terminal) throws IOException {
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

    /**
     * ESC P page — select display page (0–3).
     */
    private void processPageSelect(Terminal terminal) throws IOException {
        char pageChar = myDataStream.getChar();
        int page = pageChar - '0';
        if (page >= 0 && page < MAX_PAGES) {
            myCurrentPage = page;
            logger.debug("TVI925: switched to page {}", page);
        } else {
            logger.debug("TVI925: invalid page number {}", page);
        }
    }

    /**
     * ESC N / ESC O — relative page navigation (+1 / -1).
     */
    private void processPageRelative(Terminal terminal, int delta) {
        int newPage = myCurrentPage + delta;
        if (newPage >= 0 && newPage < MAX_PAGES) {
            myCurrentPage = newPage;
        }
        logger.debug("TVI925: page now {}", myCurrentPage);
    }

    /**
     * ESC c — toggle between 80 and 132 column mode. Reads one more byte
     * to determine the desired width.
     */
    private void processColumnWidth(Terminal terminal) throws IOException {
        char widthCode = myDataStream.getChar();
        if (widthCode == '1') {
            terminal.setModeEnabled(TerminalMode.WideColumn, true);
            logger.debug("TVI925: 132-column mode");
        } else {
            terminal.setModeEnabled(TerminalMode.WideColumn, false);
            logger.debug("TVI925: 80-column mode");
        }
    }

    /**
     * ESC e — write to protected-field positions on screen. Subsequent
     * characters up to a field terminator overwrite protected cells.
     * We treat this as a normal write since field protection is advisory.
     */
    private void processProtectedFieldWrite(Terminal terminal) throws IOException {
        logger.debug("TVI925: protected field write mode entered (pass-through)");
    }

    /**
     * ESC d — field separator. Marks the boundary between an unprotected
     * and a protected region. In our emulation this resets attributes.
     */
    private void processFieldSeparator(Terminal terminal) {
        terminal.characterAttributes(TextStyle.EMPTY);
    }

    /**
     * ESC 0 / ESC 1 — extended attribute mode. The 925 uses these to
     * select between the two attribute pages (normal vs. enhanced).
     */
    private void processExtendedAttribute(Terminal terminal, char mode) throws IOException {
        if (mode == '1') {
            logger.debug("TVI925: enhanced attribute mode");
        } else {
            logger.debug("TVI925: normal attribute mode");
        }
    }

    public int getCurrentPage() {
        return myCurrentPage;
    }
}
